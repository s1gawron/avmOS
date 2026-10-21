package avmos.io;

/** The machine's only I/O device: text out, numbers in. */
public interface Console {

    void println(String line);

    /** Shows the prompt (without a newline) and reads the next 32-bit number. */
    int readInt(String prompt);
}
