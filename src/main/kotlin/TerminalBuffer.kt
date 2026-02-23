/**
 * Core terminal text buffer.
 *
 * Phase 1 – skeleton implementation.
 * Contains only the Active Screen and the most basic write/cursor functionality.
 * Scrollback, wrapping, colours, reflow, etc. are deferred to later phases.
 *
 * @param width  Number of columns (characters per line).
 * @param height Number of rows (visible lines on screen).
 */
class TerminalBuffer(val width: Int, val height: Int) {

    // ── Active Screen ────────────────────────────────────────────────────────
    // Fixed-capacity list initialised with [height] blank lines.
    private val screen: ArrayList<TerminalLine> = ArrayList<TerminalLine>(height).also { list ->
        repeat(height) { list.add(TerminalLine(width)) }
    }

    // ── Cursor state ─────────────────────────────────────────────────────────
    /** Current cursor column (0-based). */
    var cursorX: Int = 0
        private set

    /** Current cursor row (0-based). */
    var cursorY: Int = 0
        private set

    // ── Writing ──────────────────────────────────────────────────────────────
    /**
     * Writes [text] at the current cursor position on the current line,
     * advancing [cursorX] for each character written.
     *
     */
    fun write(text: String) {
        val line = screen[cursorY]
        for (char in text) {
            if (cursorX >= width) break
            line.setChar(cursorX, char)
            cursorX++
        }
    }

    // ── Content access ───────────────────────────────────────────────────────
    /**
     * Returns the entire visible screen as a single string.
     * Each row is separated by a newline character.
     */
    fun getVisibleContent(): String =
        screen.joinToString("\n") { it.toString() }
}
