package com.example.agent.providers;

import com.example.tests.generator.config.GigachatClientConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * Builds an {@link SSLContext} configured with the CA certificate or truststore provided via client properties.
 */
final class GigachatSslContextFactory {

    private static final Logger log = Logger.getLogger(GigachatSslContextFactory.class.getName());
    private static final String DEFAULT_TRUSTSTORE_PASSWORD = "changeit";

    private GigachatSslContextFactory() {
    }

    static Optional<SSLContext> create(GigachatClientConfig config) {
        try {
            TrustManager[] trustManagers = createTrustManagers(config);
            if (trustManagers == null) {
                return Optional.empty();
            }

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, null);
            return Optional.of(context);
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to initialize TLS for Gigachat client", exception);
        }
    }

    private static TrustManager[] createTrustManagers(GigachatClientConfig config)
            throws IOException, GeneralSecurityException {
        String caFile = config.getCaFile();
        if (caFile == null || caFile.isBlank()) {
            return null;
        }

        Path path = Path.of(caFile);
        if (!Files.exists(path)) {
            throw new IOException(String.format(Locale.ENGLISH, "CA file does not exist: %s", path));
        }

        if (looksLikePem(path)) {
            return trustManagersFromPem(path);
        }
        return trustManagersFromKeyStore(path, config.getTrustStorePassword());
    }

    private static boolean looksLikePem(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 5; i++) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.contains("-----BEGIN")) {
                    return true;
                }
            }
        } catch (MalformedInputException ignore) {
            return false;
        }
        return false;
    }

    private static TrustManager[] trustManagersFromPem(Path path)
            throws IOException, GeneralSecurityException {
        Collection<? extends Certificate> certificates = loadCertificates(path);
        if (certificates.isEmpty()) {
            throw new GeneralSecurityException(String.format(Locale.ENGLISH,
                    "CA file %s does not contain any certificates", path));
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        int index = 0;
        for (Certificate certificate : certificates) {
            String alias = String.format(Locale.ENGLISH, "gigachat-ca-%d", ++index);
            keyStore.setCertificateEntry(alias, certificate);
        }
        return createTrustManagers(keyStore);
    }

    private static TrustManager[] trustManagersFromKeyStore(Path path, String explicitPassword)
            throws IOException, GeneralSecurityException {
        List<char[]> passwordCandidates = buildPasswordCandidates(explicitPassword);
        List<String> types = guessKeyStoreTypes(path);

        Exception lastError = null;
        for (String type : types) {
            for (char[] candidate : passwordCandidates) {
                try (InputStream inputStream = Files.newInputStream(path)) {
                    KeyStore keyStore = KeyStore.getInstance(type);
                    keyStore.load(inputStream, candidate);
                    return createTrustManagers(keyStore);
                } catch (IOException | GeneralSecurityException exception) {
                    lastError = exception;
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Unable to load truststore {0} as {1}: {2}",
                                new Object[]{path, type, exception.getMessage()});
                    }
                }
            }
        }

        if (lastError instanceof GeneralSecurityException securityException) {
            throw securityException;
        }
        if (lastError instanceof IOException ioException) {
            throw ioException;
        }
        throw new GeneralSecurityException(String.format(Locale.ENGLISH,
                "Unable to read truststore %s", path), lastError);
    }

    private static List<char[]> buildPasswordCandidates(String explicitPassword) {
        List<char[]> candidates = new ArrayList<>();
        if (explicitPassword != null) {
            candidates.add(explicitPassword.toCharArray());
        }
        candidates.add(DEFAULT_TRUSTSTORE_PASSWORD.toCharArray());
        candidates.add(new char[0]);
        candidates.add(null);
        return candidates;
    }

    private static List<String> guessKeyStoreTypes(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ENGLISH);
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (filename.endsWith(".p12") || filename.endsWith(".pfx")) {
            types.add("PKCS12");
        }
        if (filename.endsWith(".jks")) {
            types.add("JKS");
        }
        types.add(KeyStore.getDefaultType());
        types.add("PKCS12");
        types.add("JKS");
        return new ArrayList<>(types);
    }

    private static Collection<? extends Certificate> loadCertificates(Path certificateFile)
            throws IOException, CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream inputStream = Files.newInputStream(certificateFile)) {
            return factory.generateCertificates(inputStream);
        }
    }

    private static TrustManager[] createTrustManagers(KeyStore keyStore)
            throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        return factory.getTrustManagers();
    }
}
