package com.loremind.application.remoteaccess;

import com.loremind.domain.remoteaccess.RemoteAccessConfig;
import com.loremind.domain.remoteaccess.RemoteDevice;
import com.loremind.domain.remoteaccess.ports.LanConnector;
import com.loremind.domain.remoteaccess.ports.RemoteAccessConfigRepository;
import com.loremind.domain.remoteaccess.ports.RemoteDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Accès distant à l'application de bureau (tablette / téléphone sur le réseau local).
 * <p>
 * Modèle de confiance : le connecteur habituel reste limité à la machine (127.0.0.1) et
 * n'exige rien, comme avant. Quand le MJ active l'accès distant, un SECOND connecteur
 * s'ouvre sur le réseau local ; tout ce qui y arrive doit présenter un jeton d'appareil,
 * obtenu en saisissant un code à 6 chiffres affiché sur le PC (usage unique, 5 minutes,
 * 5 essais). Les Paramètres restent réservés à la machine (filtre {@code RemoteAccessFilter}).
 * <p>
 * Uniquement en mode bureau (profil {@code local}) : en Docker, rien de tout ceci n'existe.
 */
@Service
@Profile("local")
public class RemoteAccessService {

    private static final Logger log = LoggerFactory.getLogger(RemoteAccessService.class);

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final int CODE_MAX_ATTEMPTS = 5;
    /** Garde-fou anti force brute tous codes confondus : au-delà, plus aucun essai pendant 1 min. */
    static final int GLOBAL_MAX_FAILURES_PER_MINUTE = 20;
    /** On ne réécrit « vu pour la dernière fois » qu'au plus une fois par minute et par appareil. */
    static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);
    static final int DEVICE_NAME_MAX = 100;
    static final int USER_AGENT_MAX = 300;

    private final RemoteAccessConfigRepository configRepository;
    private final RemoteDeviceRepository deviceRepository;
    private final LanConnector lanConnector;
    private final int localPort;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private PairingCode currentCode;
    private int attemptsLeft;
    private final Deque<Instant> recentFailures = new ArrayDeque<>();
    private String lastError;

    @Autowired
    public RemoteAccessService(RemoteAccessConfigRepository configRepository,
                               RemoteDeviceRepository deviceRepository,
                               LanConnector lanConnector,
                               @Value("${server.port:8080}") int localPort) {
        this(configRepository, deviceRepository, lanConnector, localPort, Clock.systemUTC());
    }

    RemoteAccessService(RemoteAccessConfigRepository configRepository,
                        RemoteDeviceRepository deviceRepository,
                        LanConnector lanConnector,
                        int localPort,
                        Clock clock) {
        this.configRepository = configRepository;
        this.deviceRepository = deviceRepository;
        this.lanConnector = lanConnector;
        this.localPort = localPort;
        this.clock = clock;
    }

    // --- Réglage -----------------------------------------------------------------------

    /** Au démarrage : rouvre le connecteur si l'accès distant était activé. Ne bloque jamais le lancement. */
    public synchronized void applySavedConfig() {
        RemoteAccessConfig config = currentConfig();
        if (!config.enabled()) return;
        try {
            lanConnector.start(config.port());
            lastError = null;
        } catch (IllegalStateException e) {
            lastError = e.getMessage();
            log.warn("Accès distant activé mais connecteur impossible à ouvrir : {}", e.getMessage());
        }
    }

    /**
     * Active ou désactive l'accès distant. À l'activation, le connecteur est ouvert AVANT
     * d'enregistrer le réglage : un port occupé laisse l'accès désactivé.
     *
     * @throws IllegalArgumentException port hors plage ou identique au port local
     * @throws IllegalStateException    port déjà occupé
     */
    public synchronized RemoteAccessStatus configure(boolean enabled, int port) {
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Le port doit être compris entre 1024 et 65535.");
        }
        if (port == localPort) {
            throw new IllegalArgumentException("Choisissez un port différent de celui de l'application (" + localPort + ").");
        }
        if (enabled) {
            lanConnector.start(port);
        } else {
            lanConnector.stop();
            currentCode = null;
        }
        lastError = null;
        configRepository.save(new RemoteAccessConfig(enabled, port));
        return status();
    }

    public synchronized RemoteAccessStatus status() {
        RemoteAccessConfig config = currentConfig();
        int running = lanConnector.runningPort();
        List<String> urls = running > 0
                ? lanConnector.lanAddresses().stream().map(ip -> "http://" + ip + ":" + running).toList()
                : List.of();
        List<PairedDevice> devices = deviceRepository.findAll().stream()
                .sorted(Comparator.comparing(RemoteDevice::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(PairedDevice::of)
                .toList();
        return new RemoteAccessStatus(config.enabled(), config.port(), running > 0, urls, lastError, devices);
    }

    private RemoteAccessConfig currentConfig() {
        return configRepository.find().orElseGet(RemoteAccessConfig::disabledByDefault);
    }

    // --- Appairage -----------------------------------------------------------------------

    /**
     * Nouveau code d'appairage (remplace le précédent).
     *
     * @throws IllegalStateException si l'accès distant n'est pas ouvert
     */
    public synchronized PairingCode newPairingCode() {
        if (lanConnector.runningPort() <= 0) {
            throw new IllegalStateException("Activez d'abord l'accès depuis le réseau local.");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        currentCode = new PairingCode(code, clock.instant().plus(CODE_TTL));
        attemptsLeft = CODE_MAX_ATTEMPTS;
        return currentCode;
    }

    /**
     * Échange un code valide contre un jeton d'appareil (à poser en cookie). Le code est
     * consommé : un second appareil demande un nouveau code.
     *
     * @throws PairingRejectedException code absent, faux, expiré ou trop d'essais
     */
    public synchronized PairingResult pair(String code, String deviceName, String userAgent) {
        Instant now = clock.instant();
        pruneFailures(now);
        if (recentFailures.size() >= GLOBAL_MAX_FAILURES_PER_MINUTE) {
            throw new PairingRejectedException("Trop d'essais. Patientez une minute.");
        }
        if (currentCode == null || now.isAfter(currentCode.expiresAt())) {
            currentCode = null;
            recordFailure(now);
            throw new PairingRejectedException("Aucun code valide : affichez un nouveau code sur le PC.");
        }
        if (code == null || !constantTimeEquals(code.trim(), currentCode.code())) {
            recordFailure(now);
            if (--attemptsLeft <= 0) {
                currentCode = null;
                throw new PairingRejectedException("Trop d'essais : affichez un nouveau code sur le PC.");
            }
            throw new PairingRejectedException("Code incorrect.");
        }
        currentCode = null;

        String token = newToken();
        RemoteDevice device = deviceRepository.save(RemoteDevice.builder()
                .name(clean(deviceName, DEVICE_NAME_MAX, "Appareil"))
                .tokenHash(sha256(token))
                .userAgent(clean(userAgent, USER_AGENT_MAX, null))
                .createdAt(now)
                .lastSeenAt(now)
                .build());
        log.info("Accès distant : appareil appairé « {} »", device.getName());
        return new PairingResult(token, PairedDevice.of(device));
    }

    /** Appareil correspondant au jeton présenté, s'il est connu (sinon : appairage requis). */
    public Optional<RemoteDevice> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Optional<RemoteDevice> device = deviceRepository.findByTokenHash(sha256(token));
        device.ifPresent(d -> {
            Instant now = clock.instant();
            if (d.getLastSeenAt() == null || d.getLastSeenAt().plus(TOUCH_INTERVAL).isBefore(now)) {
                deviceRepository.touch(d.getId(), now);
            }
        });
        return device;
    }

    /** Coupe l'accès d'un appareil. */
    public boolean revoke(String deviceId) {
        return deviceRepository.deleteById(deviceId);
    }

    /** Port du connecteur réseau ouvert, ou -1 (pour le filtre : qui vient « de l'extérieur » ?). */
    public int lanPort() {
        return lanConnector.runningPort();
    }

    // --- Outils -----------------------------------------------------------------------------

    private void recordFailure(Instant now) {
        recentFailures.addLast(now);
    }

    private void pruneFailures(Instant now) {
        Instant limit = now.minus(Duration.ofMinutes(1));
        while (!recentFailures.isEmpty() && recentFailures.peekFirst().isBefore(limit)) {
            recentFailures.removeFirst();
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String clean(String value, int max, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.strip();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
