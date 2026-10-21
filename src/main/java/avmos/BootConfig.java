package avmos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What the OS needs to know at boot: how many bytes of memory the programs may use
 * (code, data, heap and stack together, not counting the OS's own structures such as PCBs),
 * which program files to load and which debug options are on.
 *
 * @param dump print a hex listing of each assembled program and stop
 */
public record BootConfig(int memoryBytes, List<Path> programs, boolean dump) {

    public BootConfig {
        if (memoryBytes <= 0) {
            throw new IllegalArgumentException("memory size must be positive, got " + memoryBytes);
        }
        if (programs.isEmpty()) {
            throw new IllegalArgumentException("at least one program file is required");
        }
        programs = List.copyOf(programs);
    }

    /** Parses {@code [--dump] <memory bytes> <program1.txt> <program2.txt> ...}. */
    public static BootConfig parse(String[] args) {
        boolean dump = false;

        int first = 0;
        while (first < args.length && args[first].startsWith("--")) {
            switch (args[first]) {
                case "--dump" -> dump = true;
                default -> throw new IllegalArgumentException("unknown option: " + args[first]);
            }
            first++;
        }

        if (args.length - first < 2) {
            throw new IllegalArgumentException("expected a memory size and at least one program file");
        }

        int memoryBytes;
        try {
            memoryBytes = Integer.parseInt(args[first]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("memory size is not a number: " + args[first]);
        }

        List<Path> programs = new ArrayList<>();
        for (int i = first + 1; i < args.length; i++) {
            Path program = Path.of(args[i]);
            if (!Files.isRegularFile(program) || !Files.isReadable(program)) {
                throw new IllegalArgumentException("cannot read program file: " + program);
            }
            programs.add(program);
        }

        return new BootConfig(memoryBytes, programs, dump);
    }
}
