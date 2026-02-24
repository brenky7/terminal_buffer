/**
 * Core terminal text buffer.
 *
 * Implements the full streaming terminal logic:
 * - Pen attributes (foreground color, background color, bold/italic/underline)
 *   packed per-cell via [StylePacker].
 * - Automatic line-wrapping with [TerminalLine.isWrapped] tracking.
 * - Scrollback history via an [ArrayDeque], bounded by [maxScrollback].
 *
 * @param width         Number of columns (characters per line).
 * @param height        Number of rows visible on screen.
 * @param maxScrollback Maximum number of lines stored in history (default 1000).
 */
class TerminalBuffer(
    val width:         Int,
    val height:        Int,
    val maxScrollback: Int = 1000
) {

    // ── Active Screen ─────────────────────────────────────────────────────────
    // Fixed-capacity list, always contains exactly [height] lines.
    private val screen: ArrayList<TerminalLine> = ArrayList<TerminalLine>(height).also { list ->
        repeat(height) { list.add(TerminalLine(width)) }
    }

    // ── Scrollback ────────────────────────────────────────────────────────────
    // Lines that have scrolled off the top of the screen.
    // Oldest entries are at the front; newest at the back.
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
}
