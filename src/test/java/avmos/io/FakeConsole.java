package avmos.io;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A console for tests: collects the output and answers input requests from a queue. */
public final class FakeConsole implements Console {

    private final List<String> lines = new ArrayList<>();
    private final Deque<Integer> input = new ArrayDeque<>();

    public FakeConsole withInput(int... values) {
        for (int value : values) {
            input.add(value);
        }
        return this;
    }

    @Override
    public void println(String line) {
        lines.add(line);
    }

    /** Like a terminal: the prompt and the typed number end up on the same line. */
    @Override
    public int readInt(String prompt) {
        int value = input.remove();
        lines.add(prompt + value);
        return value;
    }

    public List<String> lines() {
        return lines;
    }

    /** All output, one line each with a trailing newline, as in the expected-output files. */
    public String output() {
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }
}
