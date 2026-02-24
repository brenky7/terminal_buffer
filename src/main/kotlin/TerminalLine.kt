/**
 * Represents a single physical row in the terminal.
 *
 * Uses a Structure of Arrays pattern for data locality and memory efficiency:
 * - [content]   stores the visible characters.
 * - [style]     stores attributes (foreground, background, flags) packed into integers
 *               via [StylePacker].
 * - [isWrapped] marks whether this line was soft-wrapped into the next physical row.
 *               Used by the reflow algorithm in a later phase.
 *
 * @param width The number of columns in this line.
 */
class TerminalLine(val width: Int) {

    /** Character data for each cell, initialised to space. */
    val content: CharArray = CharArray(width) { ' ' }

    /** Style data for each cell, initialised to 0 (default/no style). */
    val style: IntArray = IntArray(width) { 0 }

    /**
     * True when this physical line was created by automatic line-wrapping (soft wrap),
     * meaning the logical line continues on the next physical row.
     * False when the line ends at a hard newline or is simply blank.
     */
    var isWrapped: Boolean = false

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
