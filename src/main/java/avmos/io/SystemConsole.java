package avmos.io;

import avmos.Fault;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** The console of the real terminal: stdout and stdin. */
public final class SystemConsole implements Console {

    private final PrintStream out;
    private final Scanner in;

    public SystemConsole() {
        this(System.out, System.in);
    }

    SystemConsole(PrintStream out, InputStream in) {
        this.out = out;
        this.in = new Scanner(in);
    }

    @Override
    public void println(String line) {
        out.println(line);
    }

    @Override
    public int readInt(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            return Integer.parseInt(in.next());
        } catch (NoSuchElementException e) {
            throw new Fault("no more input");
        } catch (NumberFormatException e) {
            throw new Fault("input is not a 32-bit number");
        }
    }
}
