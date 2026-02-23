# Terminal Text Buffer Implementation

This repository contains the solution for **Task #2: Implement a terminal text buffer**. It was developed as part of the selection process for the JetBrains Internship project: _"Integration of an external terminal emulator into the IntelliJ Terminal"_ .

## Project Overview

The goal of this project is to implement the core data structure of a terminal emulator. The Terminal Buffer is responsible for storing the state of the terminal, handling cursor movements, and managing the history. This implementation focuses on performance, memory efficiency, and correct handling of dynamic terminal resizing.

The chosen technologies are Kotlin with Maven for dependencies and JUnit5 for tests. No other external libraries are used.

## Specification

### 1. Terminal lines

This implementation uses a Structure of Arrays pattern encapsulated within a TerminalLine class. TerminalLine holds primitive arrays:

- **Content:** CharArray - Stores the text characters.

* **Style:** IntArray - Stores attributes (foreground, background, flags) packed into integers.

This ensures data locality and reduces the memory footprint.

### 2. Buffer Strategy

The buffer is logically and physically divided into two components:

**Active Screen**

- **Structure:** ArrayList `<TerminalLine>` (Fixed capacity = Window Height).
- **Usage:** Random access, frequent in-place updates, cursor movement.
- **Behavior:** Represents the editable part of the terminal where applications like Vim render their interface.

**Scrollback:**

- **Structure:** ArrayDeque `<TerminalLine>`.
- **Usage:** Append-only (FIFO), circular buffer semantics.
- **Behavior:** Stores lines that have scrolled off the top of the screen. Handles history limits without array copying.

### 3. Functional Requirements

#### 3.1 Initialization and Configuration

- The buffer must be configurable: initial width, height, and history limit.
- Initialization creates an empty Screen filled with blank lines.

#### 3.2 Cursor and Attribute Management

**Cursor**

- Tracks cursorX and cursorY positions, cursor movement must not exit the Screen area.

**Attributes**

- The buffer maintains the current pen state - Foreground color, Background color, Styles: Bold, Italic, Underline).
- These attributes apply to all subsequent character writes until changed.

#### 3.3 Editing and Writing

**Write Text**

- Writing text at the cursor position overwrites existing content.
- Writing updates the attributes in the attributes array.
- If text exceeds the line width, it must automatically wrap to the next line.
- During automatic wrapping, the isWrapped = true flag must be set on the original line.

**Clear Operations**

- Clear Screen (Active Screen)
- Clear All (Screen + Scrollback)

**Insert/Delete Lines:**

- Inserting an empty line at the bottom pushes the top line of the Screen into the Scrollback.

#### 3.4 Resize & Reflow

The implementation must support dynamic resizing of both dimensions simultaneously.

**Logical vs. Physical Lines:**
To support reflow (changing width), the buffer needs to track Logical lines. A logical line can span multiple physical rows.

- **Metadata:** Each TerminalLine has an isWrapped: Boolean flag.
- **True:** This line continues to the next physical line (it was not ended by a newline character).
- **False:** This line is the end of a logical line (ended by \n or empty space).

**Reflow Algorithm:**
When resize(newWidth, newHeight) is called:

1. **Merge:** The Scrollback and Screen are conceptually merged.
2. **Re-flow:** Content is reconstructed into logical lines (based on isWrapped) and then re-sliced into new physical lines according to newWidth.
3. **Split:** The new list of lines is distributed back into Scrollback and Screen based on newHeight.

#### 3.5 Content Access

- Methods to retrieve the character and attributes at specific coordinates including history.
- Method to retrieve the entire text content (Screen + Scrollback) as a string.
