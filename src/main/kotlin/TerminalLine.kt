/**
 * Represents a single physical row in the terminal.
 *
 * Uses a Structure of Arrays pattern for data locality and memory efficiency:
 * - [content] stores the visible characters.
 * - [style]   stores packed style attributes (fg color, bg color, flags) per cell.
 *
 * @param width The number of columns in this line.
 */
class TerminalLine(val width: Int) {

    /** Character data for each cell, initialised to space. */
    val content: CharArray = CharArray(width) { ' ' }

    /** Style data for each cell, initialised to 0 (default/no style). */
    val style: IntArray = IntArray(width) { 0 }

    /**
     * Writes a character and its style attribute at the given column [index].
     * Out-of-bounds indices are silently ignored.
     *
     * @param index  Column position (0-based).
     * @param char   Character to store.
     * @param style  Packed style integer (default 0 = no style).
     */
    fun setChar(index: Int, char: Char, style: Int = 0) {
        if (index in 0 until width) {
            content[index] = char
            this.style[index] = style
        }
    }

    /** Returns the line content as a plain string – useful for debugging. */
    override fun toString(): String = String(content)
}
