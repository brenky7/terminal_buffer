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
        // 3-row screen, writing 4 lines forces 1 line into scrollback.
        val buffer = TerminalBuffer(width = 20, height = 3, maxScrollback = 100)

        // Each \n triggers newLine(), the 3rd \n causes a scroll because cursorY is
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

    // ── testReflowWider ───────────────────────────────────────────────────────
    /**
     * Widening the terminal must merge two wrapped physical lines back into one
     * logical line when the new width can accommodate all the content.
     *
     * Setup (width=40, height=5):
     *   Write 50 'A's → line 0 filled (40 A's, isWrapped=true), 10 A's on line 1.
     * Resize to width=80:
     *   The single logical line (80 cells) fits in one new physical line.
     *   Expect: getLine(0) has all 50 A's, isWrapped=false.
     */
    @Test
    fun testReflowWider() {
        val buffer = TerminalBuffer(width = 40, height = 5)
        buffer.write("A".repeat(50))

        // Sanity-check pre-resize state.
        assertTrue(buffer.getLine(0).isWrapped, "pre-resize: line 0 should be wrapped")

        buffer.resize(80, 5)

        assertEquals(80, buffer.width,  "width should be 80 after resize")
        assertEquals(5,  buffer.height, "height should remain 5")

        val line0 = buffer.getLine(0)
        assertEquals("A".repeat(50) + " ".repeat(30), line0.toString(),
            "all 50 A's should fit on one line after widening")
        assertFalse(line0.isWrapped,
            "merged line should NOT be marked isWrapped (it is the end of its logical line)")

        // Line 1 should now be blank (old overflow was pulled up).
        assertTrue(buffer.getLine(1).toString().all { it == ' ' },
            "line 1 should be blank after the two physical lines merged")
    }

    // ── testReflowNarrower ────────────────────────────────────────────────────
    /**
     * Narrowing the terminal must split one long physical line into two wrapped ones.
     *
     * Setup (width=80, height=5):
     *   Write 80 'A's → fills line 0 exactly.
     * Resize to width=40:
     *   The 80-cell logical line re-slices into two chunks of 40.
     *   Expect: line 0 = "A"*40 with isWrapped=true, line 1 starts with "A"*40.
     */
    @Test
    fun testReflowNarrower() {
        val buffer = TerminalBuffer(width = 80, height = 5)
        buffer.write("A".repeat(80))

        buffer.resize(40, 5)

        assertEquals(40, buffer.width,  "width should be 40 after resize")
        assertEquals(5,  buffer.height, "height should remain 5")

        val line0 = buffer.getLine(0)
        val line1 = buffer.getLine(1)

        assertEquals("A".repeat(40), line0.toString(),
            "first physical line should contain the first 40 A's")
        assertTrue(line0.isWrapped,
            "first physical line should be marked isWrapped=true (content continues)")

        assertEquals("A".repeat(40), line1.toString(),
            "second physical line should contain the remaining 40 A's")
        assertFalse(line1.isWrapped,
            "second physical line is the last chunk, so isWrapped must be false")
    }

    // ── testSimultaneousResize ────────────────────────────────────────────────
    /**
     * Changing both width and height simultaneously must preserve all text content
     * and correctly redistribute lines between screen and scrollback.
     *
     * Setup (width=80, height=24):
     *   Write 24 lines of 79 A's (each terminated with \n), then write "B".
     *   → scrollback contains 1 line (the first A-line that was evicted on the 24th \n).
     *   → screen[0..22] = A-lines, screen[23] = "B" + spaces.
     *   → cursor at row 23, col 1.
     *
     * Resize to (width=40, height=10):
     *   Each 80-cell logical line re-slices into two 40-cell chunks → 50 new lines total.
     *   Last 10 go to screen, 40 go to scrollback.
     *   The "B" line (logical 24) maps to screen[8] (chunk 0) and screen[9] (chunk 1).
     */
    @Test
    fun testSimultaneousResize() {
        val buffer = TerminalBuffer(width = 80, height = 24, maxScrollback = 1000)

        // Write 24 hard lines of 79 A's + a single "B".
        repeat(24) { buffer.write("A".repeat(79) + "\n") }
        buffer.write("B")

        // Verify pre-resize: one line evicted from screen.
        assertEquals(1, buffer.getScrollbackSize(),
            "pre-resize: exactly 1 line should be in scrollback")
        assertEquals(23, buffer.cursorY, "pre-resize: cursor should be on last row")
        assertEquals(1,  buffer.cursorX, "pre-resize: cursor should be at column 1")

        buffer.resize(40, 10)

        // Dimensions updated.
        assertEquals(40, buffer.width,  "width should be 40 after resize")
        assertEquals(10, buffer.height, "height should be 10 after resize")

        // 25 logical lines × 2 chunks each = 50 physical lines.
        // Last 10 go to screen, 40 go to scrollback.
        assertEquals(40, buffer.getScrollbackSize(),
            "scrollback should contain 40 lines after resize")

        // Screen line 0 is the first chunk of logical line 20 (all A content).
        assertTrue(buffer.getLine(0).toString().startsWith("A".repeat(40)),
            "screen[0] should start with 40 A's")

        // The 'B' line is screen[8] (first 40-cell chunk of the last logical line).
        val bLine = buffer.getLine(8)
        assertEquals('B', bLine.content[0],
            "screen[8] should start with 'B'")
        assertTrue(bLine.isWrapped,
            "screen[8] should be isWrapped=true (content spills to screen[9])")

        // screen[9] is the trailing-spaces chunk of the 'B' logical line.
        assertTrue(buffer.getLine(9).toString().all { it == ' ' },
            "screen[9] should be all spaces (trailing half of B-line after reflow)")

        // Cursor should be on screen[8] at column 1 (right after 'B').
        assertEquals(8, buffer.cursorY, "cursor row should be 8 after resize")
        assertEquals(1, buffer.cursorX, "cursor column should remain at 1 after resize")
    }
}
