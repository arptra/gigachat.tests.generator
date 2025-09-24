package com.example.tests.generator.reporting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesJacocoReport() throws IOException {
        Path report = tempDir.resolve("build/reports/jacoco/test");
        Files.createDirectories(report);
        Path file = report.resolve("jacocoTestReport.xml");
        String xml = "<report>" +
                "<counter type=\"LINE\" missed=\"1\" covered=\"9\"/>" +
                "<counter type=\"BRANCH\" missed=\"2\" covered=\"6\"/>" +
                "</report>";
        Files.writeString(file, xml);

        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        CoverageSummary summary = analyzer.analyze(tempDir).orElseThrow();

        assertEquals(file, summary.getReportPath());
        assertTrue(summary.getMetrics().containsKey("LINE"));
        assertEquals(10L, summary.getMetrics().get("LINE").total());
        assertEquals(8L, summary.getMetrics().get("BRANCH").total());
    }
}
