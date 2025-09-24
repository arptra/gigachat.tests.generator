package com.example.tests.generator.util;

import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Configures {@link java.util.logging} levels based on environment variables.
 */
public final class LoggingConfigurator {

    private static final String ENV_LOG_LEVEL = "GIGACHAT_AGENT_LOG_LEVEL";
    private static final Level DEFAULT_LEVEL = Level.INFO;

    private LoggingConfigurator() {
    }

    /**
     * Configures the root logger level from the {@value #ENV_LOG_LEVEL} environment variable.
     */
    public static void configure() {
        Level level = parseLevel(System.getenv(ENV_LOG_LEVEL));
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        if (rootLogger != null) {
            rootLogger.setLevel(level);
            for (Handler handler : rootLogger.getHandlers()) {
                handler.setLevel(level);
            }
        }
        Logger.getLogger(LoggingConfigurator.class.getName()).log(Level.FINE,
                () -> "Logging configured with level " + level);
    }

    private static Level parseLevel(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_LEVEL;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Level.parse(normalized);
        } catch (IllegalArgumentException ex) {
            System.err.println("Unknown log level '" + value + "'. Falling back to " + DEFAULT_LEVEL + '.');
            return DEFAULT_LEVEL;
        }
    }
}
