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
}
