/**
 * Core terminal text buffer.
 *
 * Implements the full streaming terminal logic:
 * - Pen attributes (foreground color, background color, bold/italic/underline)
 *   packed per-cell via [StylePacker].
 * - Automatic line-wrapping with [TerminalLine.isWrapped] tracking.
 * - Scrollback history via an [ArrayDeque], bounded by [maxScrollback].
 * 
 * Adds simultaneous resize + reflow on top of the core logic:
 * - [resize] merges scrollback + screen into logical lines (using [TerminalLine.isWrapped]),
 *   re-slices them at the new width, and distributes them back anchored to the bottom.
 * - The cursor is tracked through the reflow so its visual position is preserved.
 *
 * @param width         Number of columns (characters per line).
 * @param height        Number of rows visible on screen.
 * @param maxScrollback Maximum number of lines stored in history (default 1000).
 */
class TerminalBuffer(
    width:              Int,
    height:             Int,
    val maxScrollback:  Int = 1000
) {
    // Mutable so resize() can update them.
    var width:  Int = width;  private set
    var height: Int = height; private set


    // ── Active Screen ─────────────────────────────────────────────────────────
    // Fixed-capacity list, always contains exactly [height] lines.
    private val screen: ArrayList<TerminalLine> = ArrayList<TerminalLine>(height).also { list ->
        repeat(height) { list.add(TerminalLine(width)) }
    }

    // ── Scrollback ────────────────────────────────────────────────────────────
    // Lines that have scrolled off the top of the screen.
    // Oldest entries are at the front, newest at the back.
    private val scrollback: ArrayDeque<TerminalLine> = ArrayDeque()

    // ── Cursor state ──────────────────────────────────────────────────────────
    /** Current cursor column (0-based). */
    var cursorX: Int = 0
        private set

    /** Current cursor row (0-based). */
    var cursorY: Int = 0
        private set

    // ── Pen / attribute state ─────────────────────────────────────────────────
    /** Foreground ANSI color index applied to new characters (0-255). */
    var penFg: Int = 0
        private set

    /** Background ANSI color index applied to new characters (0-255). */
    var penBg: Int = 0
        private set

    /** Current style flags (Bold / Italic / Underline) applied to new characters. */
    var penFlags: Int = 0
        private set

    /**
     * Set the current pen attributes.
     * All subsequent [write] calls will use these values until changed again.
     *
     * @param fg        Foreground ANSI color index (0-255).
     * @param bg        Background ANSI color index (0-255).
     * @param bold      Apply bold styling.
     * @param italic    Apply italic styling.
     * @param underline Apply underline styling.
     */
    fun setAttributes(
        fg:        Int     = penFg,
        bg:        Int     = penBg,
        bold:      Boolean = false,
        italic:    Boolean = false,
        underline: Boolean = false
    ) {
        penFg    = fg
        penBg    = bg
        penFlags = 0
        if (bold)      penFlags = penFlags or StylePacker.BOLD_FLAG
        if (italic)    penFlags = penFlags or StylePacker.ITALIC_FLAG
        if (underline) penFlags = penFlags or StylePacker.UNDERLINE_FLAG
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** The packed style Int built from the current pen state. */
    private val currentStyle: Int
        get() = StylePacker.pack(penFg, penBg,
            StylePacker.isBold(penFlags),
            StylePacker.isItalic(penFlags),
            StylePacker.isUnderline(penFlags))

    /**
     * Write a single character at the current cursor position using the current
     * pen style, then advance [cursorX].
     */
    private fun writeChar(char: Char) {
        screen[cursorY].setChar(cursorX, char, currentStyle)
        cursorX++
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Advance to a new line.
     *
     * - If not yet at the bottom row: simply move [cursorY] down and reset [cursorX].
     * - If at the bottom row (scroll needed): push the top screen line into the
     *   scrollback, enforce [maxScrollback] by dropping the oldest entry if required,
     *   then append a fresh blank line at the bottom of the screen.
     */
    fun newLine() {
        if (cursorY < height - 1) {
            cursorY++
        } else {
            // Scroll: move the topmost screen line into history.
            val evicted = screen.removeAt(0)
            scrollback.addLast(evicted)
            if (scrollback.size > maxScrollback) scrollback.removeFirst()
            screen.add(TerminalLine(width))
            // cursorY stays at height - 1 (still the last row).
        }
        cursorX = 0
    }

    /**
     * Write [text] at the current cursor position, applying the current pen
     * attributes to every character.
     *
     * - A `\n` character triggers [newLine] (hard line break, [isWrapped] stays false).
     * - When writing would exceed the line width the current line is marked
     *   [TerminalLine.isWrapped] = true and [newLine] is called automatically
     *   before writing the next character (soft wrap).
     */
    fun write(text: String) {
        for (char in text) {
            if (char == '\n') {
                newLine()
                continue
            }
            // Soft-wrap: spill onto the next line if we have reached the right edge.
            if (cursorX >= width) {
                screen[cursorY].isWrapped = true
                newLine()
            }
            writeChar(char)
        }
    }

    // ── Content access ────────────────────────────────────────────────────────

    /**
     * Returns a reference to the physical [TerminalLine] at screen row [row].
     * Row 0 is the topmost visible line.
     */
    fun getLine(row: Int): TerminalLine = screen[row]

    /**
     * Returns the number of lines currently stored in the scrollback history.
     */
    fun getScrollbackSize(): Int = scrollback.size

    /**
     * Returns a reference to the scrollback [TerminalLine] at [index].
     * Index 0 is the oldest (topmost) history line.
     */
    fun getScrollbackLine(index: Int): TerminalLine = scrollback[index]

    /**
     * Returns the entire visible screen as a single string.
     * Each row is separated by a newline character.
     */
    fun getVisibleContent(): String =
        screen.joinToString("\n") { it.toString() }

    /**
     * Returns the entire scrollback history as a single string.
     * Each row is separated by a newline character.
     * Index 0 (oldest) appears first.
     */
    fun getScrollbackContent(): String =
        scrollback.joinToString("\n") { it.toString() }

    // ── Resize & Reflow ───────────────────────────────────────────────────────

    /**
     * Resize the terminal to [newWidth] × [newHeight], reflowing all content.
     *
     * **Algorithm overview:**
     * 1. Merge 
     *      Scrollback and screen are concatenated into one ordered list of
     *      physical lines (oldest first).
     * 2. Build logical lines
     *      Consecutive physical lines where [TerminalLine.isWrapped]
     *      is `true` on the preceding line are joined into a single logical line represented
     *      as flat cell arrays (char + style).
     * 3. Re-slice 
     *      Each logical line is chopped into chunks of [newWidth] cells.
     *      Every chunk except the last is marked `isWrapped = true`.
     * 4. Distribute - anchor to bottom 
     *      The last [newHeight] new physical lines fill the screen.
     *      Everything before them goes to scrollback (trimmed to [maxScrollback]).
     *      Blank lines are appended to the screen when the total is less than [newHeight].
     * 5. Cursor 
     *      The cursor's absolute cell offset within its logical line is computed
     *      before re-slicing, then mapped back to new (cursorY, cursorX) coordinates.
     */
    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth  >= 1) { "newWidth must be >= 1" }
        require(newHeight >= 1) { "newHeight must be >= 1" }
        if (newWidth == width && newHeight == height) return

        // ── 1. Merge ──────────────────────────────────────────────────────────
        val merged = ArrayList<TerminalLine>(scrollback.size + screen.size)
        merged.addAll(scrollback)
        merged.addAll(screen)

        val cursorMergedIdx = scrollback.size + cursorY

        // Find the last meaningful physical line 
        // Blank lines sitting below the cursor add no content, reflowing them would
        // inflate newPhysical and push real content into scrollback unnecessarily.
        // The reflow is stopped at lastMeaningfulIdx, blank tail rows are re-appended
        // as fresh TerminalLine(newWidth) blanks during screen padding (step 4).
        var lastMeaningfulIdx = cursorMergedIdx
        for (j in cursorMergedIdx + 1 until merged.size) {
            if (merged[j].content.any { it != ' ' }) lastMeaningfulIdx = j
        }
        // Never split a soft-wrapped logical group at the boundary.
        while (lastMeaningfulIdx < merged.size - 1 && merged[lastMeaningfulIdx].isWrapped) {
            lastMeaningfulIdx++
        }

        // ── 2. Build logical lines (merged[0..lastMeaningfulIdx]) ─────────────
        var cursorLogicalLine     = 0
        var cursorOffsetInLogical = 0

        class Segment(val chars: CharArray, val styles: IntArray)
        val logicals = ArrayList<Segment>(lastMeaningfulIdx + 1)

        var idx = 0
        while (idx <= lastMeaningfulIdx) {
            val groupChars  = ArrayList<Char>(width  * 2)
            val groupStyles = ArrayList<Int>(width * 2)
            while (true) {
                val phys = merged[idx]
                if (idx == cursorMergedIdx) {
                    cursorLogicalLine     = logicals.size
                    cursorOffsetInLogical = groupChars.size + cursorX
                }
                for (col in 0 until phys.width) {
                    groupChars.add(phys.content[col])
                    groupStyles.add(phys.style[col])
                }
                val cont = phys.isWrapped
                idx++
                if (!cont || idx > lastMeaningfulIdx) break
            }
            logicals.add(Segment(groupChars.toCharArray(), groupStyles.toIntArray()))
        }

        // ── 3. Re-slice ───────────────────────────────────────────────────────
        val newPhysical  = ArrayList<TerminalLine>(logicals.size * 2)
        var newCursorPhysIdx = 0
        var newCursorX       = 0

        for ((logIdx, seg) in logicals.withIndex()) {
            val total     = seg.chars.size
            val startPhys = newPhysical.size

            if (logIdx == cursorLogicalLine) {
                val chunkIdx     = cursorOffsetInLogical / newWidth
                newCursorPhysIdx = startPhys + chunkIdx
                newCursorX       = cursorOffsetInLogical % newWidth
            }

            var offset = 0
            while (offset < total) {
                val chunkSize = minOf(newWidth, total - offset)
                val newLine   = TerminalLine(newWidth)
                for (col in 0 until chunkSize) {
                    newLine.content[col] = seg.chars[offset + col]
                    newLine.style[col]   = seg.styles[offset + col]
                }
                newLine.isWrapped = (offset + chunkSize < total)
                newPhysical.add(newLine)
                offset += chunkSize
            }
        }

        // ── 4. Distribute - anchor to bottom ─────────────────────────────────
        val totalNew    = newPhysical.size
        val screenStart = maxOf(0, totalNew - newHeight)

        scrollback.clear()
        val sbFrom = maxOf(0, screenStart - maxScrollback)
        for (j in sbFrom until screenStart) scrollback.addLast(newPhysical[j])

        screen.clear()
        for (j in screenStart until totalNew) screen.add(newPhysical[j])
        // Blank-tail rows excluded from reflow come back as fresh blank lines.
        while (screen.size < newHeight) screen.add(TerminalLine(newWidth))

        // ── 5. Update cursor ──────────────────────────────────────────────────
        cursorY = (newCursorPhysIdx - screenStart).coerceIn(0, newHeight - 1)
        cursorX = newCursorX.coerceIn(0, newWidth  - 1)

        // ── 6. Update dimensions ──────────────────────────────────────────────
        width  = newWidth
        height = newHeight
    }
}
