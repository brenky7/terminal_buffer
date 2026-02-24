import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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

     // ── Invalid construction ──────────────────────────────────────────────────
    @Test fun testInvalidConstructorWidth()        { assertThrows<IllegalArgumentException> { TerminalBuffer(0,  5) } }
    @Test fun testInvalidConstructorHeight()       { assertThrows<IllegalArgumentException> { TerminalBuffer(5,  0) } }
    @Test fun testInvalidConstructorNegWidth()     { assertThrows<IllegalArgumentException> { TerminalBuffer(-1, 5) } }
    @Test fun testInvalidConstructorScrollback()   { assertThrows<IllegalArgumentException> { TerminalBuffer(10, 5, maxScrollback = -1) } }

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
   
    // ── Out-of-bounds access ──────────────────────────────────────────────────
    /**
     * All coordinate-access methods must throw [IndexOutOfBoundsException]
     * for coordinates that are outside the valid screen / scrollback range.
     */
    @Test
    fun testOutOfBoundsAccess() {
        val buffer = TerminalBuffer(10, 5)
        buffer.write("Hello")

        // getChar — invalid column
        assertThrows<IndexOutOfBoundsException> { buffer.getChar(10, 0) }
        assertThrows<IndexOutOfBoundsException> { buffer.getChar(-1, 0) }
        // getChar — invalid screen row
        assertThrows<IndexOutOfBoundsException> { buffer.getChar(0, 5) }
        assertThrows<IndexOutOfBoundsException> { buffer.getChar(0, 99) }
        // getChar — scrollback access when scrollback is empty
        assertThrows<IndexOutOfBoundsException> { buffer.getChar(0, -1) }

        // getStyle mirrors getChar
        assertThrows<IndexOutOfBoundsException> { buffer.getStyle(10, 0) }
        assertThrows<IndexOutOfBoundsException> { buffer.getStyle(0, 5) }

        // getLine — out of screen range
        assertThrows<IndexOutOfBoundsException> { buffer.getLine(-1) }
        assertThrows<IndexOutOfBoundsException> { buffer.getLine(5) }

        // getScrollbackLine — out of range when empty
        assertThrows<IndexOutOfBoundsException> { buffer.getScrollbackLine(0) }

        // fillLine — out of range
        assertThrows<IllegalArgumentException>  { buffer.fillLine(-1) }
        assertThrows<IllegalArgumentException>  { buffer.fillLine(5) }

        // resize — zero / negative dimensions
        assertThrows<IllegalArgumentException>  { buffer.resize(0, 5) }
        assertThrows<IllegalArgumentException>  { buffer.resize(10, 0) }
    }

    // ── Unified coordinate access (getChar / getStyle / getLineAt) ────────────
    /**
     * Verify that the unified coordinate system correctly reaches both the
     * screen (y >= 0) and the scrollback (y < 0).
     */
    @Test
    fun testUnifiedCoordinateAccess() {
        val buffer = TerminalBuffer(width = 20, height = 3, maxScrollback = 10)
        buffer.write("Scrolled\nScreen0\nScreen1\nScreen2")
        // After 4 lines on a 3-row screen: "Scrolled" is in scrollback[-1 / index 0].

        // Screen access (y >= 0)
        assertEquals('S', buffer.getChar(0, 0), "getChar(0,0) should be 'S' from Screen0")
        assertEquals('0', buffer.getChar(6, 0), "getChar(6,0) from Screen0 — index 6 of 'Screen0' is '0'")
        assertEquals('S', buffer.getChar(0, 1), "getChar(0,1) from Screen1")
        assertEquals('S', buffer.getChar(0, 2), "getChar(0,2) from Screen2")

        // Scrollback access (y < 0)
        // -1 is the newest scrollback = "Scrolled" line
        assertEquals('S', buffer.getChar(0, -1), "getChar(0,-1) should reach newest scrollback")
        assertEquals('c', buffer.getChar(1, -1))

        // getLineAt mirrors behaviour
        assertEquals("Scrolled" + " ".repeat(12), buffer.getLineAt(-1).toString())
        assertTrue(buffer.getLineAt(0).toString().startsWith("Screen0"))

        // getStyle round-trip
        buffer.setAttributes(fg = 7, bg = 0, bold = false)
        buffer.setCursor(0, 0)
        buffer.write("X")
        val packed = StylePacker.pack(fg = 7)
        assertEquals(packed, buffer.getStyle(0, 0), "getStyle should return the packed style at (0,0)")
    }

    // ── setCursor & clamp ─────────────────────────────────────────────────────
    @Test
    fun testSetCursorClamps() {
        val buffer = TerminalBuffer(10, 5)
        buffer.setCursor(0, 0)
        assertEquals(0, buffer.cursorX); assertEquals(0, buffer.cursorY)

        buffer.setCursor(9, 4) // last valid cell
        assertEquals(9, buffer.cursorX); assertEquals(4, buffer.cursorY)

        // Clamp high
        buffer.setCursor(100, 100)
        assertEquals(9, buffer.cursorX, "x clamped to width-1")
        assertEquals(4, buffer.cursorY, "y clamped to height-1")

        // Clamp low
        buffer.setCursor(-5, -5)
        assertEquals(0, buffer.cursorX, "x clamped to 0")
        assertEquals(0, buffer.cursorY, "y clamped to 0")
    }

    // ── moveCursor* ───────────────────────────────────────────────────────────
    @Test
    fun testMoveCursorBounds() {
        val buffer = TerminalBuffer(10, 5)
        buffer.setCursor(5, 2)

        buffer.moveCursorUp(1)
        assertEquals(1, buffer.cursorY, "up by 1")
        buffer.moveCursorUp(100)
        assertEquals(0, buffer.cursorY, "up clamped at 0")

        buffer.moveCursorDown(2)
        assertEquals(2, buffer.cursorY, "down by 2")
        buffer.moveCursorDown(100)
        assertEquals(4, buffer.cursorY, "down clamped at height-1")

        buffer.moveCursorLeft(1)
        assertEquals(4, buffer.cursorX, "left by 1 from 5")
        buffer.moveCursorLeft(100)
        assertEquals(0, buffer.cursorX, "left clamped at 0")

        buffer.moveCursorRight(3)
        assertEquals(3, buffer.cursorX, "right by 3")
        buffer.moveCursorRight(100)
        assertEquals(9, buffer.cursorX, "right clamped at width-1")
    }

    // ── fillLine ─────────────────────────────────────────────────────────────
    @Test
    fun testFillLine() {
        val buffer = TerminalBuffer(10, 5)
        buffer.write("Hello")

        val style = StylePacker.pack(fg = 1, bg = 2)
        buffer.fillLine(0, 'X', style)

        val line = buffer.getLine(0)
        assertTrue(line.content.all { it == 'X' }, "all cells should be 'X'")
        assertTrue(line.style.all   { it == style }, "all style cells should match")
        assertFalse(line.isWrapped, "isWrapped should be reset to false by fillLine")

        // Cursor should not have moved.
        assertEquals(5, buffer.cursorX, "cursor should not move after fillLine")
    }

    // ── insertLine ───────────────────────────────────────────────────────────
    @Test
    fun testInsertLine() {
        val buffer = TerminalBuffer(width = 20, height = 3, maxScrollback = 10)
        buffer.write("Line0\nLine1\nLine2")
        buffer.setCursor(0, 2)

        buffer.insertLine()

        // Line0 should now be in scrollback.
        assertEquals(1, buffer.getScrollbackSize(), "Line0 should be evicted to scrollback")
        assertTrue(buffer.getScrollbackLine(0).toString().startsWith("Line0"))

        // Screen should be: Line1, Line2, blank.
        assertTrue(buffer.getLine(0).toString().startsWith("Line1"), "screen[0] = Line1")
        assertTrue(buffer.getLine(1).toString().startsWith("Line2"), "screen[1] = Line2")
        assertTrue(buffer.getLine(2).toString().all { it == ' '},    "screen[2] = blank")
    }

    // ── insertText ───────────────────────────────────────────────────────────
    @Test
    fun testInsertText() {
        // Width=30 is wide enough to hold "Hello Beautiful World" (21 chars) + spaces.
        val buffer = TerminalBuffer(width = 30, height = 5)
        buffer.write("Hello World")
        buffer.setCursor(6, 0)    // position 6 is 'W' in "Hello World" (after the space)

        buffer.insertText("Beautiful ")

        val line = buffer.getLine(0).toString()
        assertTrue(line.startsWith("Hello Beautiful World"),
            "inserted text should appear at cursor position 6 but was: \"$line\"")
    }

    @Test
    fun testInsertTextWraps() {
        // Width=10: inserting 10 chars at col 5 will push 5 tail chars to next line.
        val buffer = TerminalBuffer(width = 10, height = 5)
        buffer.write("AAAAABBBBB")  // fills line 0 exactly
        buffer.setCursor(5, 0)
        buffer.insertText("CCCCC")

        // line0: AAAAACCCCC (isWrapped=true), line1: BBBBB + spaces
        assertEquals("AAAAACCCCC", buffer.getLine(0).toString(),
            "line 0 should be AAAAACCCCC after insert")
        assertTrue(buffer.getLine(0).isWrapped,
            "line 0 should be wrapped after overflow")
        assertTrue(buffer.getLine(1).toString().startsWith("BBBBB"),
            "tail BBBBB should have wrapped onto line 1")
    }

    // ── clearScreen ──────────────────────────────────────────────────────────
    /**
     * clearScreen must wipe all screen cells and reset the cursor,
     * but leave the scrollback history intact.
     */
    @Test
    fun testClearScreen() {
        val buffer = TerminalBuffer(width = 20, height = 3, maxScrollback = 100)
        buffer.write("Line1\nLine2\nLine3\nLine4") // pushes 1 line into scrollback
        val scrollbackBefore = buffer.getScrollbackSize()

        buffer.clearScreen()

        // Cursor reset.
        assertEquals(0, buffer.cursorX, "cursorX should be 0 after clearScreen")
        assertEquals(0, buffer.cursorY, "cursorY should be 0 after clearScreen")

        // Screen is blank.
        val content = buffer.getVisibleContent()
        assertTrue(content.all { it == ' ' || it == '\n' },
            "screen should be all spaces after clearScreen but was: \"$content\"")

        // Scrollback unchanged.
        assertEquals(scrollbackBefore, buffer.getScrollbackSize(),
            "scrollback should be unaffected by clearScreen")
        assertTrue(buffer.getScrollbackLine(0).toString().startsWith("Line1"),
            "scrollback content should still be 'Line1...'")
    }

    // ── clearAll ─────────────────────────────────────────────────────────────
    @Test
    fun testClearAll() {
        val buffer = TerminalBuffer(width = 20, height = 3, maxScrollback = 100)
        buffer.write("Line1\nLine2\nLine3\nLine4")

        buffer.clearAll()

        assertEquals(0, buffer.cursorX, "cursorX should be 0 after clearAll")
        assertEquals(0, buffer.cursorY, "cursorY should be 0 after clearAll")
        assertEquals(0, buffer.getScrollbackSize(), "scrollback should be empty after clearAll")
        assertTrue(buffer.getVisibleContent().all { it == ' ' || it == '\n' },
            "screen should be blank after clearAll")
    }

    // ── getAllContent ─────────────────────────────────────────────────────────
    @Test
    fun testGetAllContent() {
        val buffer = TerminalBuffer(width = 20, height = 3, maxScrollback = 10)
        buffer.write("Alpha\nBravo\nCharlie\nDelta")

        val all = buffer.getAllContent()
        val lines = all.lines()
        assertEquals(4, lines.size, "all content should have 4 lines (1 scrollback + 3 screen)")
        assertTrue(lines[0].startsWith("Alpha"),   "line 0 (oldest scrollback) = Alpha")
        assertTrue(lines[1].startsWith("Bravo"),   "line 1 = Bravo")
        assertTrue(lines[2].startsWith("Charlie"), "line 2 = Charlie")
        assertTrue(lines[3].startsWith("Delta"),   "line 3 = Delta")
    }

    // ── scrollback limit ─────────────────────────────────────────────────────
    @Test
    fun testScrollbackLimit() {
        val maxSb = 5
        val buffer = TerminalBuffer(width = 10, height = 3, maxScrollback = maxSb)
        // Write 10 lines → 7 should be evicted to scrollback, but capped at maxSb=5.
        repeat(10) { i -> buffer.write("Line${i}\n") }
        assertEquals(maxSb, buffer.getScrollbackSize(),
            "scrollback should never exceed maxScrollback=$maxSb")
    }

    // ── resize: empty buffer ──────────────────────────────────────────────────
    @Test
    fun testResizeEmptyBuffer() {
        val buffer = TerminalBuffer(width = 80, height = 24)
        buffer.resize(40, 12)
        assertEquals(40, buffer.width)
        assertEquals(12, buffer.height)
        assertEquals(0, buffer.cursorX, "cursor X should stay 0")
        assertEquals(0, buffer.cursorY, "cursor Y should stay 0")
        assertTrue(buffer.getVisibleContent().all { it == ' ' || it == '\n' },
            "empty buffer should still be all spaces after resize")
    }

    // ── resize: no-op if dimensions unchanged ────────────────────────────────
    @Test
    fun testResizeNoOp() {
        val buffer = TerminalBuffer(width = 80, height = 24)
        buffer.write("Hello")
        buffer.resize(80, 24)   // same dimensions
        assertEquals(5, buffer.cursorX, "cursor should not change on no-op resize")
        assertTrue(buffer.getLine(0).toString().startsWith("Hello"),
            "content should be unchanged by no-op resize")
    }

    // ── resize: grow height only ──────────────────────────────────────────────
    @Test
    fun testResizeGrowHeightOnly() {
        val buffer = TerminalBuffer(width = 20, height = 3, maxScrollback = 10)
        buffer.write("Alpha\nBravo\nCharlie")
        buffer.resize(20, 6)    // double height

        assertEquals(6, buffer.height)
        assertEquals(20, buffer.width)
        // Content should still be on the same rows.
        assertTrue(buffer.getLine(0).toString().startsWith("Alpha"))
        assertTrue(buffer.getLine(1).toString().startsWith("Bravo"))
        assertTrue(buffer.getLine(2).toString().startsWith("Charlie"))
        // Extra rows at bottom are blank.
        assertTrue((3..5).all { buffer.getLine(it).toString().all { c -> c == ' ' } },
            "extra rows after height growth should be blank")
    }

    // ── resize: shrink height — excess content goes to scrollback ────────────
    @Test
    fun testResizeShrinkHeightPushesToScrollback() {
        val buffer = TerminalBuffer(width = 20, height = 6, maxScrollback = 100)
        // Fill all 6 rows with distinct content.
        buffer.write("Row0\nRow1\nRow2\nRow3\nRow4\nRow5")
        assertEquals(0, buffer.getScrollbackSize(), "pre-resize: no scrollback")

        buffer.resize(20, 3)   // shrink to 3 rows

        assertEquals(3, buffer.height)
        // Top 3 rows → scrollback
        assertEquals(3, buffer.getScrollbackSize(),
            "top 3 rows should move to scrollback on height shrink")
        assertTrue(buffer.getScrollbackLine(0).toString().startsWith("Row0"))
        assertTrue(buffer.getScrollbackLine(1).toString().startsWith("Row1"))
        assertTrue(buffer.getScrollbackLine(2).toString().startsWith("Row2"))
        // Bottom 3 rows remain on screen
        assertTrue(buffer.getLine(0).toString().startsWith("Row3"))
        assertTrue(buffer.getLine(1).toString().startsWith("Row4"))
        assertTrue(buffer.getLine(2).toString().startsWith("Row5"))
    }

    // ── resize: width to 1 (extreme narrowing) ───────────────────────────────
    @Test
    fun testResizeWidthToOne() {
        val buffer = TerminalBuffer(width = 5, height = 5, maxScrollback = 100)
        buffer.write("Hello")   // 5 chars exactly fill line 0

        buffer.resize(1, 5)    // width=1: each char becomes its own physical line

        assertEquals(1, buffer.width)
        // "Hello" = 5 logical chars → 5 physical lines at width=1.
        // They need to fit in the screen (height=5) so no scrollback.
        assertEquals(0, buffer.getScrollbackSize(),
            "5 chars on a height-5 screen at width=1 should fit without scrollback")
        assertEquals('H', buffer.getLine(0).content[0])
        assertEquals('e', buffer.getLine(1).content[0])
        assertEquals('l', buffer.getLine(2).content[0])
        assertEquals('l', buffer.getLine(3).content[0])
        assertEquals('o', buffer.getLine(4).content[0])
    }

    // ── resize: multiple distinct logical lines across scrollback boundary ────
    @Test
    fun testResizeMultipleLogicalLinesAcrossScrollback() {
        // 3-row screen, fill 5 lines (2 into scrollback), then resize wider.
        val buffer = TerminalBuffer(width = 10, height = 3, maxScrollback = 50)
        buffer.write("AAAAAAAAAA\nBBBBBBBBBB\nCCCCCCCCCC\nDDDDDDDDDD\nEEEEEEEEEE")
        // Scrollback: AAA, BBB. Screen: CCC, DDD, EEE.
        assertEquals(2, buffer.getScrollbackSize())

        buffer.resize(20, 3)   // double width

        // Each 10-char line is one logical line → one 20-cell physical line.
        // Total 5 logical lines, last 3 go to screen, first 2 to scrollback.
        assertEquals(2, buffer.getScrollbackSize(),
            "scrollback count should remain 2 when content lines don't change")
        assertTrue(buffer.getScrollbackLine(0).toString().startsWith("AAAAAAAAAA"),
            "scrollback[0] should be A-line")
        assertTrue(buffer.getScrollbackLine(1).toString().startsWith("BBBBBBBBBB"),
            "scrollback[1] should be B-line")
        assertTrue(buffer.getLine(0).toString().startsWith("CCCCCCCCCC"))
        assertTrue(buffer.getLine(1).toString().startsWith("DDDDDDDDDD"))
        assertTrue(buffer.getLine(2).toString().startsWith("EEEEEEEEEE"))
    }

    // ── resize: style data preserved through reflow ───────────────────────────
    @Test
    fun testResizePreservesStyles() {
        val buffer = TerminalBuffer(width = 40, height = 5)
        buffer.setAttributes(fg = 3, bg = 0, bold = true)
        buffer.write("A".repeat(40))   // fills line 0, no wrap needed here
        buffer.setAttributes()          // reset to default

        buffer.resize(80, 5)   // widen: line should merge into one physical line

        val packedExpected = StylePacker.pack(fg = 3, bold = true)
        for (col in 0 until 40) {
            assertEquals(packedExpected, buffer.getStyle(col, 0),
                "style at column $col should survive reflow")
        }
        // Cells beyond the 40 written ones should have style=0.
        assertEquals(0, buffer.getStyle(40, 0), "unwritten cells should have style=0")
    }

    // ── resize: cursor preserved through reflow ───────────────────────────────
    @Test
    fun testResizePreservesCursorPosition() {
        val buffer = TerminalBuffer(width = 10, height = 5)
        buffer.write("ABCDE")   // cursor at (5, 0)
        assertEquals(5, buffer.cursorX); assertEquals(0, buffer.cursorY)

        buffer.resize(5, 5)    // halve width: "ABCDE" fits in one chunk of 5

        // The logical line has 5 chars. chunk index = 5/5 = 1 (second chunk).
        // But the second chunk is just trailing spaces → cursor at (0, 1).
        assertEquals(0, buffer.cursorX, "cursor X after narrow resize")
        assertEquals(1, buffer.cursorY, "cursor Y should be on the second chunk")
    }

    // ── resize: scrollback stays bounded after reflow ─────────────────────────
    @Test
    fun testResizeScrollbackBoundedAfterReflow() {
        val maxSb = 3
        val buffer = TerminalBuffer(width = 20, height = 5, maxScrollback = maxSb)
        // Push 6 lines into scrollback (write 11 hard lines on a 5-row screen).
        repeat(11) { i -> buffer.write("Line$i\n") }
        assertEquals(maxSb, buffer.getScrollbackSize(), "pre-resize scrollback capped at $maxSb")

        buffer.resize(10, 5)   // narrow: each 20-cell line → 2 chunks

        // After reflow the scrollback is rebuilt, it must not exceed maxSb.
        assertTrue(buffer.getScrollbackSize() <= maxSb,
            "scrollback must not exceed maxScrollback=$maxSb after resize but was ${buffer.getScrollbackSize()}")
    }

    // ── integration scenario ─────────────────────────────────────────────────
    /**
     * A realistic sequence: writes, colour changes, scrollback evictions, and
     * a resize.  Verifies overall stability and consistency.
     */
    @Test
    fun testIntegrationScenario() {
        val buffer = TerminalBuffer(width = 30, height = 10, maxScrollback = 20)

        // 1. Write 12 lines.  A 10-row screen fills after 10 newlines. Each
        //    subsequent newline evicts one row. Writes i=0..11 emit 12 newlines,
        //    so the 10th, 11th, and 12th newlines cause 3 evictions.
        repeat(12) { i ->
            buffer.setAttributes(fg = i % 16)
            buffer.write("Content of row $i\n")
        }
        assertEquals(3, buffer.getScrollbackSize(), "3 lines should be in scrollback after 12 writes on a 10-row screen")

        // 2. setCursor and overwrite part of current line 9.
        buffer.setCursor(0, 9)
        buffer.setAttributes(fg = 5, bg = 2, bold = true)
        buffer.write("REPLACED")
        assertEquals('R', buffer.getLine(9).content[0])

        // 3. clearScreen — scrollback intact.
        val sbSize = buffer.getScrollbackSize()
        buffer.clearScreen()
        assertEquals(sbSize, buffer.getScrollbackSize(), "scrollback should survive clearScreen")
        assertEquals(0, buffer.cursorX)
        assertEquals(0, buffer.cursorY)

        // 4. Write fresh content and resize simultaneously.
        buffer.write("FinalLine")
        buffer.resize(15, 5)

        // After resize dimensions are updated.
        assertEquals(15, buffer.width)
        assertEquals(5,  buffer.height)

        // Screen still has content.
        assertTrue(buffer.getLine(0).toString().startsWith("FinalLine") ||
            buffer.getAllContent().contains("FinalLine"),
            "FinalLine should be present somewhere after resize")

        // Cursor is within bounds.
        assertTrue(buffer.cursorX in 0 until buffer.width,  "cursorX in bounds")
        assertTrue(buffer.cursorY in 0 until buffer.height, "cursorY in bounds")

        // getAllContent produces exactly height + scrollback lines.
        val allLines = buffer.getAllContent().split('\n')
        assertEquals(buffer.getScrollbackSize() + buffer.height, allLines.size,
            "getAllContent should have scrollback + screen lines")
    }
}
