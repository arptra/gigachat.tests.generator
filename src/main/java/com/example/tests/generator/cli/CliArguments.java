package com.example.tests.generator.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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
    private final Duration requestDelay;
    private final boolean useTokenAuth;
    private final boolean infoLogging;
    private final boolean compileSuccessEnabled;
    private final boolean executionSuccessEnabled;

    private CliArguments(Path projectRoot, List<String> targetClasses, int maxRetries, int limit,
            Duration requestDelay, boolean useTokenAuth, boolean infoLogging,
            boolean compileSuccessEnabled, boolean executionSuccessEnabled) {
        this.projectRoot = projectRoot;
        this.targetClasses = List.copyOf(targetClasses);
        this.maxRetries = maxRetries;
        this.limit = limit;
        this.requestDelay = requestDelay;
        this.useTokenAuth = useTokenAuth;
        this.infoLogging = infoLogging;
        this.compileSuccessEnabled = compileSuccessEnabled;
        this.executionSuccessEnabled = executionSuccessEnabled;
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

    public Duration requestDelay() {
        return requestDelay;
    }

    public boolean useTokenAuth() {
        return useTokenAuth;
    }

    public boolean infoLogging() {
        return infoLogging;
    }

    public boolean compileSuccessEnabled() {
        return compileSuccessEnabled;
    }

    public boolean executionSuccessEnabled() {
        return executionSuccessEnabled;
    }

    public static CliArguments parse(String[] args) {
        Path project = Paths.get("").toAbsolutePath();
        List<String> targets = new ArrayList<>();
        int maxRetries = 2;
        int limit = Integer.MAX_VALUE;
        Duration requestDelay = Duration.ZERO;
        boolean useToken = false;
        boolean infoLogging = false;
        boolean compileSuccessEnabled = true;
        boolean executionSuccessEnabled = true;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--project":
                case "-p":
                    project = Paths.get(requireValue(arg, args, ++i));
                    break;
                case "--class":
                case "-c":
                    targets.add(normalizeTarget(requireValue(arg, args, ++i)));
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
                case "--gigachat-delay":
                    long seconds = Long.parseLong(requireValue(arg, args, ++i));
                    if (seconds < 0) {
                        throw new IllegalArgumentException("--gigachat-delay must be >= 0");
                    }
                    requestDelay = Duration.ofSeconds(seconds);
                    break;
                case "--token":
                    useToken = true;
                    break;
                case "--info":
                    infoLogging = true;
                    break;
                case "--compileSuccess":
                    compileSuccessEnabled = parseBooleanFlag(arg, requireValue(arg, args, ++i));
                    break;
                case "--executionSuccess":
                    executionSuccessEnabled = parseBooleanFlag(arg, requireValue(arg, args, ++i));
                    break;
                case "--help":
                case "-h":
                    throw new HelpRequestedException();
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        return new CliArguments(project.toAbsolutePath().normalize(), targets, maxRetries, limit,
                requestDelay, useToken, infoLogging, compileSuccessEnabled, executionSuccessEnabled);
    }

    public static void printUsage() {
        System.out.println("Usage: java -jar <path-to-generator-jar> [options]");
        System.out.println("Options:");
        System.out.println("  -p, --project <path>      Path to the project root (default: current directory)");
        System.out.println("  -c, --class <name>        Fully qualified or simple class name (may be repeated)");
        System.out.println("      --max-retries <n>     Maximum prompt retries when validation fails (default: 2)");
        System.out.println("      --limit <n>           Limit the number of classes to process");
        System.out.println("      --gigachat-delay <s>  Delay between Gigachat requests in seconds");
        System.out.println("      --compileSuccess <b>  Enable (true) or disable (false) the compilation rerun step");
        System.out.println("      --executionSuccess <b> Enable (true) or disable (false) the test execution rerun step");
        System.out.println("      --token               Use OAuth token authentication instead of mTLS certificates");
        System.out.println("      --info                Enable detailed agent logging");
        System.out.println("  -h, --help               Show this help message");
    }

    private static String requireValue(String flag, String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    private static String normalizeTarget(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String normalized = trimmed.replace('\\', '/');
        if (normalized.endsWith(".java")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        int srcIndex = normalized.indexOf("src/main/java/");
        if (srcIndex >= 0) {
            normalized = normalized.substring(srcIndex + "src/main/java/".length());
        }
        int testSrcIndex = normalized.indexOf("src/test/java/");
        if (testSrcIndex >= 0) {
            normalized = normalized.substring(testSrcIndex + "src/test/java/".length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replace('/', '.');
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static boolean parseBooleanFlag(String flag, String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException(flag + " must be 'true' or 'false'");
    }

    static final class HelpRequestedException extends RuntimeException {
        private HelpRequestedException() {
            super("help");
        }
    }
}
