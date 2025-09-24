package com.example.tests.generator.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Command line argument holder for the test generation CLI.
 */
final class CliArguments {

    private final Path projectRoot;
    private final List<String> targetClasses;
    private final int maxRetries;
    private final int limit;

    private CliArguments(Path projectRoot, List<String> targetClasses, int maxRetries, int limit) {
        this.projectRoot = projectRoot;
        this.targetClasses = List.copyOf(targetClasses);
        this.maxRetries = maxRetries;
        this.limit = limit;
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public List<String> targetClasses() {
        return targetClasses;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public int limit() {
        return limit;
    }

    public static CliArguments parse(String[] args) {
        Path project = Paths.get("").toAbsolutePath();
        List<String> targets = new ArrayList<>();
        int maxRetries = 2;
        int limit = Integer.MAX_VALUE;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--project":
                case "-p":
                    project = Paths.get(requireValue(arg, args, ++i));
                    break;
                case "--class":
                case "-c":
                    targets.add(requireValue(arg, args, ++i));
                    break;
                case "--max-retries":
                    maxRetries = Integer.parseInt(requireValue(arg, args, ++i));
                    if (maxRetries < 0) {
                        throw new IllegalArgumentException("--max-retries must be >= 0");
                    }
                    break;
                case "--limit":
                    limit = Integer.parseInt(requireValue(arg, args, ++i));
                    if (limit <= 0) {
                        throw new IllegalArgumentException("--limit must be > 0");
                    }
                    break;
                case "--help":
                case "-h":
                    throw new HelpRequestedException();
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        return new CliArguments(project.toAbsolutePath().normalize(), targets, maxRetries, limit);
    }

    public static void printUsage() {
        System.out.println("Usage: java -jar <path-to-generator-jar> [options]");
        System.out.println("Options:");
        System.out.println("  -p, --project <path>      Path to the project root (default: current directory)");
        System.out.println("  -c, --class <fqcn>        Fully qualified class name to target (may be repeated)");
        System.out.println("      --max-retries <n>     Maximum prompt retries when validation fails (default: 2)");
        System.out.println("      --limit <n>           Limit the number of classes to process");
        System.out.println("  -h, --help               Show this help message");
    }

    private static String requireValue(String flag, String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    static final class HelpRequestedException extends RuntimeException {
        private HelpRequestedException() {
            super("help");
        }
    }
}
