package com.example.tests.generator.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import java.util.Locale;

/**
 * Utility class that converts a PEM encoded CA certificate into a PKCS12 truststore the JVM can consume.
 */
final class TrustStoreGenerator {

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String PASSWORD = "changeit";

    private TrustStoreGenerator() {
    }

    static Path generate(Path certificateFile, Path outputFile) throws IOException, GeneralSecurityException {
        if (!Files.exists(certificateFile)) {
            throw new IOException(String.format(Locale.ENGLISH, "CA certificate not found: %s", certificateFile));
        }

        Collection<? extends Certificate> certificates;
        try {
            certificates = loadCertificates(certificateFile);
        } catch (CertificateException exception) {
            throw new GeneralSecurityException(String.format(Locale.ENGLISH,
                    "Failed to parse certificates from %s", certificateFile), exception);
        }

        if (certificates.isEmpty()) {
            throw new GeneralSecurityException(String.format(Locale.ENGLISH,
                    "CA file %s does not contain any certificates", certificateFile));
        }

        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        keyStore.load(null, null);

        int index = 0;
        for (Certificate certificate : certificates) {
            String alias = String.format(Locale.ENGLISH, "gigachat-ca-%d", ++index);
            keyStore.setCertificateEntry(alias, certificate);
        }

        Path absoluteOutput = outputFile.toAbsolutePath();
        Path parent = absoluteOutput.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }

        try (OutputStream outputStream = Files.newOutputStream(absoluteOutput)) {
            keyStore.store(outputStream, PASSWORD.toCharArray());
        }

        return absoluteOutput;
    }

    static String defaultPassword() {
        return PASSWORD;
    }

    private static Collection<? extends Certificate> loadCertificates(Path certificateFile)
            throws IOException, CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream inputStream = Files.newInputStream(certificateFile)) {
            return factory.generateCertificates(inputStream);
        }
    }
}
