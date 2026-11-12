package org.bike.profile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HexFormat;
import javax.net.ssl.SSLContext;

/** Downloads only the public server certificate used to bootstrap profile trust. */
public final class ServerCertificateClient {
    private static final int BOOTSTRAP_HTTP_PORT = 8080;

    private ServerCertificateClient() {}

    public static DownloadedCertificate download(ServerProfile profile, SSLContext trustedContext)
            throws IOException, InterruptedException {
        URI base = URI.create(profile.serverUrl().replaceAll("/+$", ""));
        HttpClient.Builder clientBuilder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6));
        if (trustedContext != null) clientBuilder.sslContext(trustedContext);
        HttpClient client = clientBuilder.build();
        URI endpoint = certificateEndpoint(base);
        try {
            return download(client, endpoint);
        } catch (IOException primaryFailure) {
            if (!"https".equalsIgnoreCase(base.getScheme())) throw primaryFailure;
            URI bootstrapEndpoint;
            try {
                bootstrapEndpoint = new URI("http", null, base.getHost(), BOOTSTRAP_HTTP_PORT,
                        "/api/tls/certificate", null, null);
            } catch (java.net.URISyntaxException exception) {
                throw new IOException("Не удалось подготовить адрес загрузки сертификата.", exception);
            }
            try {
                return download(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build(), bootstrapEndpoint);
            } catch (IOException bootstrapFailure) {
                bootstrapFailure.addSuppressed(primaryFailure);
                throw bootstrapFailure;
            }
        }
    }

    public static X509Certificate readCertificate(byte[] content) throws IOException {
        try {
            var certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(content));
            if (!(certificate instanceof X509Certificate x509)) {
                throw new IOException("Сервер вернул сертификат неподдерживаемого типа.");
            }
            x509.checkValidity();
            return x509;
        } catch (java.security.GeneralSecurityException exception) {
            throw new IOException("Не удалось прочитать X.509 сертификат.", exception);
        }
    }

    public static String fingerprint(X509Certificate certificate) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IOException("Не удалось вычислить отпечаток сертификата.", exception);
        }
    }

    private static DownloadedCertificate download(HttpClient client, URI endpoint)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/x-pem-file")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Сервер не вернул сертификат (HTTP %d).".formatted(response.statusCode()));
        }
        X509Certificate certificate = readCertificate(response.body());
        String calculatedFingerprint = fingerprint(certificate);
        String responseFingerprint = response.headers().firstValue("X-Certificate-SHA256").orElse("");
        if (!normalizeFingerprint(calculatedFingerprint).equals(normalizeFingerprint(responseFingerprint))) {
            throw new IOException("Отпечаток сертификата в ответе сервера не совпал с файлом.");
        }
        return new DownloadedCertificate(certificate, calculatedFingerprint,
                "http".equalsIgnoreCase(endpoint.getScheme()));
    }

    private static URI certificateEndpoint(URI base) {
        String authority = base.getRawAuthority();
        return URI.create(base.getScheme() + "://" + authority + "/api/tls/certificate");
    }

    private static String normalizeFingerprint(String value) {
        return value.replace(":", "").replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
    }

    public record DownloadedCertificate(X509Certificate certificate, String fingerprint, boolean downloadedOverHttp) {
    }
}
