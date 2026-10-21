package avmos;

import avmos.asm.Assembler;
import avmos.asm.AssemblyException;
import avmos.asm.Program;
import avmos.io.SystemConsole;
import avmos.kernel.Kernel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {

    private static final String USAGE = "usage: avmOS [--dump] [--max-cycles N] <memory bytes> <program1.txt> <program2.txt> ...";

    private Main() {
    }

    public static void main(String[] args) {
        BootConfig config;
        try {
            config = BootConfig.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("avmOS: " + e.getMessage());
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        List<Program> programs = new ArrayList<>();
        for (Path path : config.programs()) {
            try {
                programs.add(Assembler.assemble(path.toString(), Files.readAllLines(path)));
            } catch (AssemblyException e) {
                System.err.println("avmOS: " + path + ": " + e.getMessage());
                System.exit(1);
                return;
            } catch (IOException e) {
                System.err.println("avmOS: cannot read " + path + ": " + e.getMessage());
                System.exit(1);
                return;
            }
        }

        if (config.dump()) {
            for (Program program : programs) {
                System.out.println("== " + program);
                System.out.print(program.listing());
            }
            return;
        }

        new Kernel(config, new SystemConsole()).run(programs);
    }
}
