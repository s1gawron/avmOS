package avmos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What the OS needs to know at boot: how many bytes of memory the programs may use
 * (code, data, heap and stack together, not counting the OS's own structures such as PCBs)
 * and which program files to load.
 */
public record BootConfig(int memoryBytes, List<Path> programs) {

    public BootConfig {
        if (memoryBytes <= 0) {
            throw new IllegalArgumentException("memory size must be positive, got " + memoryBytes);
        }
        if (programs.isEmpty()) {
            throw new IllegalArgumentException("at least one program file is required");
        }
        programs = List.copyOf(programs);
    }

    /** Parses {@code <memory bytes> <program1.txt> <program2.txt> ...}. */
    public static BootConfig parse(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("expected a memory size and at least one program file");
        }

        int memoryBytes;
        try {
            memoryBytes = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("memory size is not a number: " + args[0]);
        }

        List<Path> programs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Path program = Path.of(args[i]);
            if (!Files.isRegularFile(program) || !Files.isReadable(program)) {
                throw new IllegalArgumentException("cannot read program file: " + program);
            }
            programs.add(program);
        }

        return new BootConfig(memoryBytes, programs);
    }
}
