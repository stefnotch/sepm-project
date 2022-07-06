package at.ac.tuwien.sepm.groupphase.backend.codegenerator;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes indented lines of code to a String.
 * Reasonably similar in design to a StringBuilder.
 */
public class CodeWriter {
    private static final String TAB = "  ";
    private int indentLevel = 0;
    private final List<CodeLine> lines = new LinkedList<>();

    /**
     * Increases the indent level.
     */
    public void beginIndent() {
        indentLevel += 1;
    }

    /**
     * Decreases the indent level.
     */
    public void endIndent() {
        indentLevel -= 1;
    }

    /**
     * Appends another line terminated by a \n to the {@link CodeWriter}.
     */
    public void writeLine(String... line) {
        lines.add(new CodeLine(indentLevel, String.join("", line)));
    }

    /**
     * Appends all lines from another {@link CodeWriter} to this {@link CodeWriter}.
     *
     * @param writer the other {@link CodeWriter}.
     */
    public void writeLines(CodeWriter writer) {
        for (CodeLine line : writer.lines) {
            lines.add(new CodeLine(indentLevel + line.indent(), line.line()));
        }
    }

    /**
     * Gets all the lines of code as a String.
     *
     * @param baseIndent an additional indent that gets added to every line.
     * @return a String containing all the lines.
     */
    public String toCode(int baseIndent) {
        return lines.stream()
            .map(v -> TAB.repeat(baseIndent + v.indent()) + v.line() + "\n")
            .collect(Collectors.joining());
    }

    /**
     * One line of text, usually used for code.
     */
    private record CodeLine(int indent, String line) {
    }
}
