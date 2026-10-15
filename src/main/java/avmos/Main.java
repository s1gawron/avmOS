package avmos;

public final class Main {

    private static final String USAGE = "usage: avmOS <memory bytes> <program1.txt> <program2.txt> ...";

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

        System.out.println("avmOS booting with " + config.memoryBytes() + " bytes of memory");
        for (var program : config.programs()) {
            System.out.println("  program: " + program);
        }
    }
}
