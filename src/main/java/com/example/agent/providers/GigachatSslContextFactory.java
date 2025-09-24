package com.example.agent.providers;

import com.example.tests.generator.config.GigachatClientConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

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
            if (isLikelyKeyStore(path)) {
                path = generateTrustStoreFromServer(path, config);
            } else {
                throw new IOException(String.format(Locale.ENGLISH, "CA file does not exist: %s", path));
            }
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

    private static boolean isLikelyKeyStore(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ENGLISH);
        return filename.endsWith(".p12") || filename.endsWith(".pfx");
    }

    private static Path generateTrustStoreFromServer(Path output, GigachatClientConfig config)
            throws IOException, GeneralSecurityException {
        if (config.getBaseUrl() == null) {
            throw new IOException("Gigachat base URL is not configured");
        }

        if (!"https".equalsIgnoreCase(config.getBaseUrl().getScheme())) {
            throw new IOException(String.format(Locale.ENGLISH,
                    "Cannot generate truststore automatically: %s is not HTTPS",
                    config.getBaseUrl()));
        }

        X509Certificate[] chain = fetchServerCertificates(config);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        for (int i = 0; i < chain.length; i++) {
            String alias = String.format(Locale.ENGLISH, "gigachat-auto-%d", i + 1);
            keyStore.setCertificateEntry(alias, chain[i]);
        }

        Path absolute = output.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }

        char[] password = Optional.ofNullable(config.getTrustStorePassword())
                .filter(value -> !value.isBlank())
                .map(String::toCharArray)
                .orElse(DEFAULT_TRUSTSTORE_PASSWORD.toCharArray());

        try (OutputStream outputStream = Files.newOutputStream(absolute)) {
            keyStore.store(outputStream, password);
        }

        if (log.isLoggable(Level.INFO)) {
            log.log(Level.INFO, "Generated Gigachat truststore at {0}", absolute);
        }

        return absolute;
    }

    private static X509Certificate[] fetchServerCertificates(GigachatClientConfig config)
            throws GeneralSecurityException, IOException {
        String host = config.getBaseUrl().getHost();
        if (host == null || host.isBlank()) {
            throw new IOException(String.format(Locale.ENGLISH,
                    "Unable to determine host from base URL %s",
                    config.getBaseUrl()));
        }

        int port = determinePort(config);

        SavingTrustManager trustManager = new SavingTrustManager();
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trustManager}, new SecureRandom());
        SSLSocketFactory factory = context.getSocketFactory();

        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
            SSLParameters parameters = socket.getSSLParameters();
            try {
                parameters.setServerNames(List.of(new SNIHostName(host)));
            } catch (IllegalArgumentException ignored) {
                // Hostname is not valid for SNI; continue without it.
            }
            socket.setSSLParameters(parameters);
            socket.startHandshake();
        } catch (IOException exception) {
            throw new IOException(String.format(Locale.ENGLISH,
                    "Failed to download certificates from %s:%d", host, port), exception);
        }

        X509Certificate[] chain = trustManager.getChain();
        if (chain.length == 0) {
            throw new GeneralSecurityException(String.format(Locale.ENGLISH,
                    "No certificates were presented by %s:%d", host, port));
        }
        return chain;
    }

    private static int determinePort(GigachatClientConfig config) {
        int port = config.getBaseUrl().getPort();
        if (port > 0) {
            return port;
        }
        return "https".equalsIgnoreCase(config.getBaseUrl().getScheme()) ? 443 : 80;
    }

    private static final class SavingTrustManager implements X509TrustManager {

        private X509Certificate[] chain = new X509Certificate[0];

        @Override
        public void checkClientTrusted(X509Certificate[] certificates, String authType) {
            // Not used for outbound TLS validation.
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certificates, String authType)
                throws CertificateException {
            if (certificates == null || certificates.length == 0) {
                throw new CertificateException("Server did not provide any certificates");
            }
            this.chain = certificates.clone();
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        X509Certificate[] getChain() {
            return chain.clone();
        }
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
