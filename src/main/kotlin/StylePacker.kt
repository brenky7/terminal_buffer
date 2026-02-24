/**
 * Bit-packing utilities for terminal cell style attributes.
 *
 * A single Int encodes all per-cell style information in a compact layout:
 *
 *   Bit range  │ Field
 *   ───────────┼────────────────────────────────────────
 *    0 –  7    │ Foreground color (8-bit ANSI index, 0-255)
 *    8 – 15    │ Background color (8-bit ANSI index, 0-255)
 *   16         │ Bold flag
 *   17         │ Italic flag
 *   18         │ Underline flag
 */
object StylePacker {

    // ── Flag masks ────────────────────────────────────────────────────────────
    const val BOLD_FLAG:      Int = 1 shl 16
    const val ITALIC_FLAG:    Int = 1 shl 17
    const val UNDERLINE_FLAG: Int = 1 shl 18

    // ── Packing ───────────────────────────────────────────────────────────────
    /**
     * Pack all style fields into a single Int.
     *
     * @param fg        Foreground ANSI color index (0-255).
     * @param bg        Background ANSI color index (0-255).
     * @param bold      Bold text flag.
     * @param italic    Italic text flag.
     * @param underline Underline text flag.
     */
    fun pack(
        fg:        Int     = 0,
        bg:        Int     = 0,
        bold:      Boolean = false,
        italic:    Boolean = false,
        underline: Boolean = false
    ): Int {
        var style = (fg and 0xFF) or ((bg and 0xFF) shl 8)
        if (bold)      style = style or BOLD_FLAG
        if (italic)    style = style or ITALIC_FLAG
        if (underline) style = style or UNDERLINE_FLAG
        return style
    }

    // ── Unpacking ─────────────────────────────────────────────────────────────
    /** Extract the foreground color index from a packed style Int. */
    fun unpackFg(style: Int): Int = style and 0xFF

    /** Extract the background color index from a packed style Int. */
    fun unpackBg(style: Int): Int = (style ushr 8) and 0xFF

    /** Returns true when the Bold flag is set. */
    fun isBold(style: Int):      Boolean = (style and BOLD_FLAG)      != 0

    /** Returns true when the Italic flag is set. */
    fun isItalic(style: Int):    Boolean = (style and ITALIC_FLAG)    != 0

    /** Returns true when the Underline flag is set. */
    fun isUnderline(style: Int): Boolean = (style and UNDERLINE_FLAG) != 0
}
