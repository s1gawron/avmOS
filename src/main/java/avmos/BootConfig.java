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
 * @param dump      print a hex listing of each assembled program and stop
 * @param maxCycles stop the whole run after this many clock cycles (0 = no limit)
 * @param memoryMap print where each process is loaded and how the free memory looks
 */
public record BootConfig(int memoryBytes, List<Path> programs, boolean dump, int maxCycles, boolean memoryMap) {

    public BootConfig {
        if (memoryBytes <= 0) {
            throw new IllegalArgumentException("memory size must be positive, got " + memoryBytes);
        }
        if (programs.isEmpty()) {
            throw new IllegalArgumentException("at least one program file is required");
        }
        if (maxCycles < 0) {
            throw new IllegalArgumentException("--max-cycles must not be negative, got " + maxCycles);
        }
        programs = List.copyOf(programs);
    }

    /** Parses {@code [--dump] [--max-cycles N] [--memory-map] <memory bytes> <program1.txt> <program2.txt> ...}. */
    public static BootConfig parse(String[] args) {
        boolean dump = false;
        int maxCycles = 0;
        boolean memoryMap = false;

        int first = 0;
        while (first < args.length && args[first].startsWith("--")) {
            String option = args[first++];
            switch (option) {
                case "--dump" -> dump = true;
                case "--memory-map" -> memoryMap = true;
                case "--max-cycles" -> {
                    if (first >= args.length) {
                        throw new IllegalArgumentException("--max-cycles needs a value");
                    }
                    maxCycles = parseNumber("--max-cycles", args[first++]);
                }
                default -> throw new IllegalArgumentException("unknown option: " + option);
            }
        }

        if (args.length - first < 2) {
            throw new IllegalArgumentException("expected a memory size and at least one program file");
        }

        int memoryBytes = parseNumber("memory size", args[first]);

        List<Path> programs = new ArrayList<>();
        for (int i = first + 1; i < args.length; i++) {
            Path program = Path.of(args[i]);
            if (!Files.isRegularFile(program) || !Files.isReadable(program)) {
                throw new IllegalArgumentException("cannot read program file: " + program);
            }
            programs.add(program);
        }

        return new BootConfig(memoryBytes, programs, dump, maxCycles, memoryMap);
    }

    private static int parseNumber(String what, String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " is not a number: " + text);
        }
    }
}
