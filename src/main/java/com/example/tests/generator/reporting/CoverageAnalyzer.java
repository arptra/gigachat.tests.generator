package com.example.tests.generator.reporting;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads Jacoco XML reports and converts them into {@link CoverageSummary} instances.
 */
public class CoverageAnalyzer {

    private static final List<String> DEFAULT_REPORT_LOCATIONS = List.of(
            "build/reports/jacoco/test/jacocoTestReport.xml",
            "build/reports/jacoco/jacocoTestReport.xml"
    );

    public Optional<CoverageSummary> analyze(Path projectRoot) {
        for (String location : DEFAULT_REPORT_LOCATIONS) {
            Path candidate = projectRoot.resolve(location);
            if (Files.exists(candidate)) {
                try {
                    return Optional.of(parse(candidate));
                } catch (Exception ignored) {
                    // Ignore malformed coverage files; try the next location.
                }
            }
        }
        return Optional.empty();
    }

    private CoverageSummary parse(Path reportPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream inputStream = Files.newInputStream(reportPath)) {
            Document document = builder.parse(inputStream);
            NodeList counters = document.getElementsByTagName("counter");
            Map<String, CoverageMetric> metrics = new LinkedHashMap<>();
            for (int i = 0; i < counters.getLength(); i++) {
                String type = counters.item(i).getAttributes().getNamedItem("type").getNodeValue();
                long missed = Long.parseLong(counters.item(i).getAttributes().getNamedItem("missed").getNodeValue());
                long covered = Long.parseLong(counters.item(i).getAttributes().getNamedItem("covered").getNodeValue());
                metrics.put(type, new CoverageMetric(type, missed, covered));
            }
            return new CoverageSummary(reportPath, metrics);
        }
    }
}
