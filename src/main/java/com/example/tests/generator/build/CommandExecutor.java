package com.example.tests.generator.build;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Abstraction over command execution to simplify testing of the build integration.
 */
public interface CommandExecutor {

    CommandResult execute(List<String> command, Path workingDirectory) throws IOException, InterruptedException;
}
