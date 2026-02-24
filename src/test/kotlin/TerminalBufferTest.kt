import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalBufferTest {

    // ── testInitialState ──────────────────────────────────────────────────────
    /**
     * A freshly created buffer should:
     * - report the correct dimensions,
     * - have the cursor at (0, 0),
     * - contain only whitespace in its visible area.
     */
    @Test
    fun testInitialState() {
        val buffer = TerminalBuffer(80, 24)

        assertEquals(80, buffer.width, "width should be 80")
        assertEquals(24, buffer.height, "height should be 24")
        assertEquals(0, buffer.cursorX, "initial cursorX should be 0")
        assertEquals(0, buffer.cursorY, "initial cursorY should be 0")

        val content = buffer.getVisibleContent()
        // Content must consist solely of spaces (cells) and newline separators.
        assertTrue(content.all { it == ' ' || it == '\n' },
            "initial content should be all spaces/newlines but was: \"$content\"")

        // Correct number of rows (24 rows → 23 newlines separating them).
        assertEquals(23, content.count { it == '\n' },
            "getVisibleContent() should produce 24 lines separated by 23 newlines")
    }

    // ── testSimpleWrite ───────────────────────────────────────────────────────
    /**
     * After writing "Hello" the first visible line must start with "Hello".
     */
    @Test
    fun testSimpleWrite() {
        val buffer = TerminalBuffer(80, 24)
        buffer.write("Hello")

        val lines = buffer.getVisibleContent().lines()
        assertTrue(lines[0].startsWith("Hello"),
            "first line should start with 'Hello' but was: \"${lines[0]}\"")

        // Remaining lines must still be blank.
        for (i in 1 until lines.size) {
            assertTrue(lines[i].all { it == ' ' },
                "line $i should still be blank after writing only to line 0")
        }
    }

    // ── testCursorMovement ────────────────────────────────────────────────────
    /**
     * cursorX must advance by exactly one for each character written.
     */
    @Test
    fun testCursorMovement() {
        val buffer = TerminalBuffer(80, 24)

        assertEquals(0, buffer.cursorX, "cursor should start at column 0")

        buffer.write("A")
        assertEquals(1, buffer.cursorX, "after writing 1 char cursorX should be 1")

        buffer.write("BC")
        assertEquals(3, buffer.cursorX, "after writing 2 more chars cursorX should be 3")
    }

    // ── testLineWrapping ──────────────────────────────────────────────────────
    /**
     * Writing more characters than the line width must:
     * - Fill the first physical line completely.
     * - Set isWrapped = true on that line.
     * - Continue writing on the next physical line.
     */
    @Test
    fun testLineWrapping() {
        // width=10 so "Hello World!" (12 chars) must spill onto line 1.
        val buffer = TerminalBuffer(width = 10, height = 5)
        buffer.write("Hello World!")

        val line0 = buffer.getLine(0)
        val line1 = buffer.getLine(1)

        assertEquals("Hello Worl", line0.toString(),
            "first physical line should be exactly filled (10 chars)")
        assertTrue(line0.isWrapped,
            "line 0 should be marked isWrapped=true after a soft wrap")

        assertTrue(line1.toString().startsWith("d!"),
            "overflow characters 'd!' should appear at the start of line 1 " +
            "but line 1 was: \"${line1}\"")

        assertEquals(1, buffer.cursorY, "cursor should be on row 1 after wrap")
        assertEquals(2, buffer.cursorX, "cursorX should be 2 (past 'd' and '!')")
    }

    // ── testScrollback ────────────────────────────────────────────────────────
    /**
     * Writing more lines than [height] must push the oldest screen line into
     * the scrollback history.
     */
    @Test
    fun testScrollback() {
        // 3-row screen; writing 4 lines forces 1 line into scrollback.
        val buffer = TerminalBuffer(width = 20, height = 3, maxScrollback = 100)

        // Each \n triggers newLine(); the 3rd \n causes a scroll because cursorY is
        // already at the bottom row (2).
        buffer.write("Line1\nLine2\nLine3\nLine4")

        // Scrollback should contain exactly the one evicted line.
        assertEquals(1, buffer.getScrollbackSize(),
            "exactly 1 line should have scrolled into history")
        assertTrue(buffer.getScrollbackLine(0).toString().startsWith("Line1"),
            "the evicted line should be 'Line1...' but was: " +
            "\"${buffer.getScrollbackLine(0)}\"")

        // The visible screen should now show Lines 2-4.
        val visibleLines = buffer.getVisibleContent().lines()
        assertTrue(visibleLines[0].startsWith("Line2"), "screen row 0 should be Line2")
        assertTrue(visibleLines[1].startsWith("Line3"), "screen row 1 should be Line3")
        assertTrue(visibleLines[2].startsWith("Line4"), "screen row 2 should be Line4")
    }

    // ── testAttributes ────────────────────────────────────────────────────────
    /**
     * Characters written after [setAttributes] must store the correct packed
     * style integer in the style array, while unrelated cells keep style 0.
     */
    @Test
    fun testAttributes() {
        val buffer = TerminalBuffer(width = 20, height = 5)

        // Set pen: fg=2 (green), bg=5 (magenta), bold=true.
        buffer.setAttributes(fg = 2, bg = 5, bold = true)
        buffer.write("X")

        val expectedStyle = StylePacker.pack(fg = 2, bg = 5, bold = true)
        val line0 = buffer.getLine(0)

        assertEquals('X', line0.content[0],
            "character at (0,0) should be 'X'")
        assertEquals(expectedStyle, line0.style[0],
            "style at (0,0) should match packed(fg=2, bg=5, bold=true)=" +
            "$expectedStyle but was ${line0.style[0]}")

        // Cells not yet written must retain the default style (0).
        assertEquals(0, line0.style[1],
            "adjacent unwritten cell should still have style=0")

        // Verify individual fields parse back correctly.
        assertEquals(2, StylePacker.unpackFg(line0.style[0]),  "fg should be 2")
        assertEquals(5, StylePacker.unpackBg(line0.style[0]),  "bg should be 5")
        assertTrue(StylePacker.isBold(line0.style[0]),         "bold flag should be set")
        assertFalse(StylePacker.isItalic(line0.style[0]),      "italic flag should not be set")
        assertFalse(StylePacker.isUnderline(line0.style[0]),   "underline flag should not be set")
    }
}
