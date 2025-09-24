package com.example.agent.providers;

import com.example.tests.generator.config.GigachatClientConfig;
import com.example.tests.generator.util.JsonUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Client for GigaChat using mTLS authentication with certificate files.
 */
public class GigaChatCertificateClient implements LLMClient {

    private static final Logger LOGGER = Logger.getLogger(GigaChatCertificateClient.class.getName());
    private static final String CHAT_COMPLETIONS_PATH = "/api/v1/chat/completions";

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;

    public GigaChatCertificateClient(GigachatClientConfig config) {
        Objects.requireNonNull(config, "config");
        this.baseUrl = normalizeBaseUrl(config.getBaseUrl().toString());
        this.model = config.getModel();
        Path certificate = requirePath(config.getCertificateFile(), "certificate", "GIGACHAT_CERT_FILE");
        Path key = requirePath(config.getKeyFile(), "private key", "GIGACHAT_KEY_FILE");
        Path ca = requirePath(config.getCaFile(), "CA certificate", "GIGACHAT_CA_FILE");
        try {
            this.httpClient = buildClient(certificate, key, ca, config.getConnectTimeout());
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Unable to initialize mTLS Gigachat client", e);
        }
    }

    private String normalizeBaseUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private Path requirePath(String value, String description, String envKey) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + description + " path. Set " + envKey + ".");
        }
        return Path.of(value);
    }

    private HttpClient buildClient(Path certFile, Path keyFile, Path caFile, Duration connectTimeout)
            throws GeneralSecurityException, IOException {
        X509Certificate cert = readCert(certFile);
        PrivateKey key = readKey(keyFile);
        X509Certificate ca = readCert(caFile);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", key, new char[0], new Certificate[]{cert});

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", ca);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        HttpClient.Builder builder = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(connectTimeout == null || connectTimeout.isZero()
                        ? Duration.ofSeconds(10)
                        : connectTimeout);
        builder.sslParameters(sslContext.getDefaultSSLParameters());
        return builder.build();
    }

    private X509Certificate readCert(Path path) throws IOException, GeneralSecurityException {
        try (InputStream in = Files.newInputStream(path)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    private PrivateKey readKey(Path path) throws IOException, GeneralSecurityException {
        String pem = Files.readString(path);
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            return readRsaKey(der);
        } else if (pem.contains("BEGIN EC PRIVATE KEY")) {
            return readEcKey(der);
        } else {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            try {
                return KeyFactory.getInstance("RSA").generatePrivate(spec);
            } catch (GeneralSecurityException e) {
                return KeyFactory.getInstance("EC").generatePrivate(spec);
            }
        }
    }

    private PrivateKey readRsaKey(byte[] pkcs1) throws GeneralSecurityException, IOException {
        DerReader reader = new DerReader(pkcs1);
        reader.expect(0x30);
        reader.readLength();
        reader.expect(0x02);
        int verLen = reader.readLength();
        reader.skip(verLen);
        RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(
                reader.readInteger(),
                reader.readInteger(),
                reader.readInteger(),
                reader.readInteger(),
                reader.readInteger(),
                reader.readInteger(),
                reader.readInteger(),
                reader.readInteger());
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PrivateKey readEcKey(byte[] sec1) throws GeneralSecurityException, IOException {
        DerReader reader = new DerReader(sec1);
        reader.expect(0x30);
        reader.readLength();
        reader.expect(0x02);
        reader.readLength();
        reader.read();
        byte[] priv = reader.readOctetString();
        java.math.BigInteger s = new java.math.BigInteger(1, priv);
        ECParameterSpec params = null;
        if (reader.hasRemaining()) {
            int tag = reader.peek();
            if (tag == 0xA0) {
                reader.read();
                int len = reader.readLength();
                reader.expect(0x06);
                int oidLen = reader.readLength();
                byte[] oidBytes = reader.readBytes(oidLen);
                String oid = decodeOid(oidBytes);
                AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
                ap.init(new ECGenParameterSpec(oid));
                params = ap.getParameterSpec(ECParameterSpec.class);
                reader.skip(len - (2 + oidLen));
            }
        }
        ECPrivateKeySpec spec = new ECPrivateKeySpec(s, params);
        return KeyFactory.getInstance("EC").generatePrivate(spec);
    }

    private String decodeOid(byte[] oid) {
        StringBuilder builder = new StringBuilder();
        int first = oid[0] & 0xFF;
        builder.append(first / 40).append('.').append(first % 40);
        long value = 0;
        for (int i = 1; i < oid.length; i++) {
            int b = oid[i] & 0xFF;
            value = (value << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                builder.append('.').append(value);
                value = 0;
            }
        }
        return builder.toString();
    }

    @Override
    public String sendPrompt(String prompt, Map<String, Object> options) {
        Objects.requireNonNull(prompt, "prompt");
        HttpRequest request = buildRequest(prompt, options, false);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                handleRateLimits(response.statusCode(), response.body());
                throw new RateLimitException("Gigachat API error: " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RateLimitException("Gigachat request failed", e);
        }
    }

    @Override
    public Stream<String> streamResponses(String prompt, Map<String, Object> options) {
        boolean streamRequested = options != null && Boolean.TRUE.equals(options.get("stream"));
        if (streamRequested) {
            LOGGER.fine("Streaming is not supported for certificate client; returning full response");
        }
        String response = sendPrompt(prompt, options);
        return Stream.of(response);
    }

    @Override
    public void handleRateLimits(int statusCode, String responseBody) {
        if (statusCode == 429) {
            throw new RateLimitException("Gigachat rate limit exceeded: " + responseBody);
        }
        if (statusCode >= 500) {
            throw new RateLimitException("Gigachat service is unavailable: " + statusCode + " - " + responseBody);
        }
    }

    private HttpRequest buildRequest(String prompt, Map<String, Object> options, boolean stream) {
        String payload = buildPayload(prompt, options, stream);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(() -> "Gigachat request payload: " + payload);
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + CHAT_COMPLETIONS_PATH))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
    }

    private String buildPayload(String prompt, Map<String, Object> options, boolean stream) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"model\":\"").append(JsonUtils.escape(model)).append('\"');
        builder.append(',').append("\"stream\":").append(stream);
        if (options != null) {
            for (Map.Entry<String, Object> entry : options.entrySet()) {
                String key = entry.getKey();
                if ("model".equalsIgnoreCase(key) || "messages".equalsIgnoreCase(key)
                        || "stream".equalsIgnoreCase(key)) {
                    continue;
                }
                builder.append(',').append('"').append(JsonUtils.escape(entry.getKey())).append('"').append(':');
                JsonUtils.appendJsonValue(builder, entry.getValue());
            }
        }
        builder.append(',').append("\"messages\":");
        JsonUtils.appendJsonValue(builder, buildMessages(prompt));
        builder.append('}');
        return builder.toString();
    }

    private List<Map<String, String>> buildMessages(String prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", "You are a helpful assistant that writes Java tests.");
        messages.add(system);

        Map<String, String> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", prompt);
        messages.add(user);
        return messages;
    }

    private String parseResponse(String body) {
        if (body == null || body.isBlank()) {
            throw new RateLimitException("Gigachat API returned empty body");
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(() -> "Gigachat response payload: " + body);
        }
        String content = JsonUtils.extractChoiceContent(body);
        if (content == null || content.isBlank()) {
            content = JsonUtils.findString(body, "message");
        }
        if (content == null || content.isBlank()) {
            throw new RateLimitException("Gigachat API returned empty message content");
        }
        return content;
    }

    private static final class DerReader {
        private final byte[] data;
        private int pos;

        private DerReader(byte[] data) {
            this.data = data;
        }

        private boolean hasRemaining() {
            return pos < data.length;
        }

        private int peek() {
            return data[pos] & 0xFF;
        }

        private int read() {
            return data[pos++] & 0xFF;
        }

        private void expect(int tag) throws IOException {
            if (read() != tag) {
                throw new IOException("Unexpected DER tag");
            }
        }

        private void skip(int len) {
            pos += len;
        }

        private int readLength() {
            int b = read();
            if (b < 0x80) {
                return b;
            }
            int n = b & 0x7F;
            int len = 0;
            for (int i = 0; i < n; i++) {
                len = (len << 8) | read();
            }
            return len;
        }

        private byte[] readBytes(int len) {
            byte[] out = java.util.Arrays.copyOfRange(data, pos, pos + len);
            pos += len;
            return out;
        }

        private java.math.BigInteger readInteger() throws IOException {
            expect(0x02);
            int len = readLength();
            return new java.math.BigInteger(readBytes(len));
        }

        private byte[] readOctetString() throws IOException {
            expect(0x04);
            int len = readLength();
            return readBytes(len);
        }
    }
}

