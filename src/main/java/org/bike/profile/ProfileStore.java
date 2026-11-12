package org.bike.profile;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Stores non-secret profile data in the current Windows user's preferences. */
public final class ProfileStore {
    private static final String PROFILE_NODE = "profiles";
    private static final String PASSWORD_KEY = "protectedPassword";
    private static final int MAX_TRUSTED_SERVER_CERTIFICATES = 2;
    private final Preferences root = Preferences.userNodeForPackage(ProfileStore.class).node(PROFILE_NODE);

    public synchronized List<ServerProfile> profiles() {
        try {
            List<ServerProfile> profiles = new ArrayList<>();
            for (String id : root.childrenNames()) {
                Preferences node = root.node(id);
                profiles.add(new ServerProfile(
                        id,
                        node.get("name", "Профиль"),
                        node.get("serverUrl", "http://localhost:8080"),
                        node.get("login", ""),
                        readRoles(node.get("roles", "")),
                        node.getBoolean("rememberPassword", true),
                        node.getBoolean("defaultProfile", false)));
            }
            profiles.sort(profileComparator());
            return List.copyOf(profiles);
        } catch (BackingStoreException exception) {
            throw new IllegalStateException("Не удалось прочитать список профилей.", exception);
        }
    }

    public synchronized Optional<char[]> savedPassword(ServerProfile profile) {
        String encrypted = root.node(profile.id()).get(PASSWORD_KEY, null);
        if (encrypted == null || encrypted.isBlank()) {
            return Optional.empty();
        }
        byte[] protectedBytes = Base64.getDecoder().decode(encrypted);
        byte[] clearBytes = Crypt32Util.cryptUnprotectData(protectedBytes);
        try {
            return Optional.of(new String(clearBytes, StandardCharsets.UTF_8).toCharArray());
        } finally {
            Arrays.fill(clearBytes, (byte) 0);
            Arrays.fill(protectedBytes, (byte) 0);
        }
    }

    public synchronized ServerProfile save(ServerProfile profile, char[] password, boolean rememberPassword) {
        validate(profile);
        String id = profile.id() == null || profile.id().isBlank()
                ? UUID.randomUUID().toString() : profile.id();
        ServerProfile saved = new ServerProfile(id, profile.name().trim(), normalizeUrl(profile.serverUrl()),
                profile.login().trim(), profile.roleCodes(), rememberPassword, profile.defaultProfile());

        try {
            if (saved.defaultProfile()) {
                for (String otherId : root.childrenNames()) {
                    if (!otherId.equals(id)) {
                        root.node(otherId).putBoolean("defaultProfile", false);
                    }
                }
            }

            Preferences node = root.node(id);
            node.put("name", saved.name());
            node.put("serverUrl", saved.serverUrl());
            node.put("login", saved.login());
            node.put("roles", String.join(",", saved.roleCodes().stream().sorted().toList()));
            node.putBoolean("rememberPassword", rememberPassword);
            node.putBoolean("defaultProfile", saved.defaultProfile());

            if (!rememberPassword) {
                node.remove(PASSWORD_KEY);
            } else if (password != null && password.length > 0) {
                byte[] clearBytes = new String(password).getBytes(StandardCharsets.UTF_8);
                byte[] protectedBytes = Crypt32Util.cryptProtectData(clearBytes);
                try {
                    node.put(PASSWORD_KEY, Base64.getEncoder().encodeToString(protectedBytes));
                } finally {
                    Arrays.fill(clearBytes, (byte) 0);
                    Arrays.fill(protectedBytes, (byte) 0);
                }
            }
            root.flush();
            return saved;
        } catch (BackingStoreException exception) {
            throw new IllegalStateException("Не удалось сохранить профиль.", exception);
        }
    }

    public synchronized void delete(ServerProfile profile) {
        try {
            root.node(profile.id()).removeNode();
            root.flush();
        } catch (BackingStoreException exception) {
            throw new IllegalStateException("Не удалось удалить профиль.", exception);
        }
    }

    public synchronized void importServerCertificate(ServerProfile profile, Path certificateFile) {
        try (InputStream input = Files.newInputStream(certificateFile)) {
            X509Certificate certificate = readCertificate(input);
            importServerCertificate(profile, certificate);
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalArgumentException("Не удалось добавить сертификат. Проверьте файл X.509 и его срок действия.", exception);
        }
    }

    public synchronized void importServerCertificate(ServerProfile profile, X509Certificate certificate) {
        Path destination = trustedCertificatePath(profile);
        try {
            certificate.checkValidity();
            Files.createDirectories(destination.getParent());
            List<X509Certificate> certificates = readTrustedCertificates(destination);
            if (certificates.stream().noneMatch(existing -> sameCertificate(existing, certificate))) {
                certificates.add(certificate);
            }
            if (certificates.size() > MAX_TRUSTED_SERVER_CERTIFICATES) {
                certificates = new ArrayList<>(certificates.subList(
                        certificates.size() - MAX_TRUSTED_SERVER_CERTIFICATES, certificates.size()));
            }
            String pem = certificates.stream().map(this::toPem).collect(java.util.stream.Collectors.joining());
            Files.writeString(destination, pem, StandardCharsets.US_ASCII);
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalArgumentException("Не удалось добавить сертификат. Проверьте файл X.509 и его срок действия.", exception);
        }
    }

    public synchronized boolean hasTrustedServerCertificate(ServerProfile profile) {
        return Files.isRegularFile(trustedCertificatePath(profile));
    }

    public synchronized boolean hasTrustedServerCertificate(
            ServerProfile profile, X509Certificate candidate) throws IOException, GeneralSecurityException {
        Path path = trustedCertificatePath(profile);
        if (!Files.isRegularFile(path)) return false;
        return readTrustedCertificates(path).stream().anyMatch(existing -> sameCertificate(existing, candidate));
    }

    public synchronized SSLContext trustedServerSslContext(ServerProfile profile)
            throws IOException, GeneralSecurityException {
        Path path = trustedCertificatePath(profile);
        if (!Files.isRegularFile(path)) return null;

        List<X509Certificate> certificates = readTrustedCertificates(path);
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        for (int index = 0; index < certificates.size(); index++) {
            trustStore.setCertificateEntry("craft-bike-server-" + index, certificates.get(index));
        }
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers.getTrustManagers(), null);
        return context;
    }

    private Path trustedCertificatePath(ServerProfile profile) {
        URI uri = URI.create(normalizeUrl(profile.serverUrl()));
        if (uri.getHost() == null || (!"https".equalsIgnoreCase(uri.getScheme())
                && !"http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Укажите адрес HTTP или HTTPS профиля.");
        }
        String origin = uri.getHost().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(origin.getBytes(StandardCharsets.UTF_8));
            return Path.of(System.getProperty("user.home"), ".craft-biker", "trusted-server-certificates",
                    HexFormat.of().formatHex(digest) + ".pem");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Не удалось подготовить хранилище сертификатов.", exception);
        }
    }

    private X509Certificate readCertificate(InputStream input) throws GeneralSecurityException, IOException {
        var certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
        if (!(certificate instanceof X509Certificate x509)) {
            throw new GeneralSecurityException("Expected an X.509 certificate");
        }
        return x509;
    }

    private List<X509Certificate> readTrustedCertificates(Path path) throws IOException, GeneralSecurityException {
        if (!Files.isRegularFile(path)) return new ArrayList<>();
        try (InputStream input = Files.newInputStream(path)) {
            return CertificateFactory.getInstance("X.509").generateCertificates(input).stream()
                    .map(certificate -> (X509Certificate) certificate)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
    }

    private boolean sameCertificate(X509Certificate first, X509Certificate second) {
        try {
            return MessageDigest.isEqual(first.getEncoded(), second.getEncoded());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Не удалось сравнить сертификаты.", exception);
        }
    }

    private String toPem(X509Certificate certificate) {
        return "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded(certificate))
                + "\n-----END CERTIFICATE-----\n";
    }

    private byte[] encoded(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Не удалось сохранить сертификат.", exception);
        }
    }

    public synchronized Optional<ServerProfile> startupProfile() {
        List<ServerProfile> profiles = profiles();
        if (profiles.size() <= 1) {
            return profiles.stream().findFirst();
        }
        return profiles.stream().filter(ServerProfile::defaultProfile).findFirst()
                .or(() -> profiles.stream().findFirst());
    }

    private Comparator<ServerProfile> profileComparator() {
        Collator russianCollator = Collator.getInstance(Locale.forLanguageTag("ru-RU"));
        return (left, right) -> {
            int byName = russianCollator.compare(left.name(), right.name());
            if (byName != 0) return byName;
            int byLogin = String.CASE_INSENSITIVE_ORDER.compare(left.login(), right.login());
            return byLogin != 0 ? byLogin : left.id().compareTo(right.id());
        };
    }

    private Set<String> readRoles(String value) {
        if (value.isBlank()) return Set.of();
        return Set.copyOf(Arrays.asList(value.split(",")));
    }

    private String normalizeUrl(String value) {
        String normalized = value.trim().replaceAll("/+$", "");
        return normalized;
    }

    private void validate(ServerProfile profile) {
        if (profile.name() == null || profile.name().isBlank()) {
            throw new IllegalArgumentException("Укажите название профиля.");
        }
        if (profile.login() == null || profile.login().isBlank()) {
            throw new IllegalArgumentException("Укажите логин пользователя.");
        }
        try {
            URI uri = URI.create(normalizeUrl(profile.serverUrl()));
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Укажите полный адрес сервера, например http://localhost:8080.");
        }
    }
}
