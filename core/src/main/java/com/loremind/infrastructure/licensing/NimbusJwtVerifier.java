package com.loremind.infrastructure.licensing;

import com.loremind.domain.licensing.LicenseClaims;
import com.loremind.domain.licensing.ports.JwtVerifier;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;

/**
 * Verifie les JWT EdDSA/Ed25519 emis par le relais Patreon.
 * <p>
 * La cle publique est fournie en PEM SPKI via la propriete
 * {@code licensing.jwt.public-key} (env {@code LICENSING_JWT_PUBLIC_KEY}).
 * Si la cle est absente ou invalide, {@link #isConfigured()} retourne false
 * et {@link #verify} echoue systematiquement — la feature licensing est
 * desactivee silencieusement.
 */
@Component
public class NimbusJwtVerifier implements JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(NimbusJwtVerifier.class);

    private final String expectedIssuer;
    private final String expectedAudience;
    private final OctetKeyPair publicKey;

    public NimbusJwtVerifier(
            @Value("${licensing.jwt.public-key:}") String publicKeyPemFromEnv,
            @Value("${licensing.jwt.expected-issuer:loremind-auth}") String expectedIssuer,
            @Value("${licensing.jwt.expected-audience:loremind-instance}") String expectedAudience) {
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        // Strategie : env var en priorite (rotation possible sans rebuild),
        // sinon ressource classpath embarquee dans le binaire.
        String pem = (publicKeyPemFromEnv != null && !publicKeyPemFromEnv.isBlank())
                ? publicKeyPemFromEnv
                : loadEmbeddedKey();
        this.publicKey = parsePemSpki(pem);
        if (publicKey == null) {
            log.info("Licensing JWT verifier disabled (no public key found)");
        } else {
            String source = (publicKeyPemFromEnv != null && !publicKeyPemFromEnv.isBlank()) ? "env" : "embedded";
            log.info("Licensing JWT verifier enabled (issuer={}, audience={}, key source={})",
                    expectedIssuer, expectedAudience, source);
        }
    }

    /**
     * Charge la cle publique embarquee dans le binaire (resource classpath).
     * Le fichier est un PEM SPKI standard, fourni a la build pour chaque
     * release. Si absent, la feature licensing est desactivee.
     */
    private static String loadEmbeddedKey() {
        ClassPathResource resource = new ClassPathResource("licensing/jwt-public-key.pem");
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Cannot read embedded JWT public key: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isConfigured() {
        return publicKey != null;
    }

    @Override
    public LicenseClaims verify(String rawJwt) throws JwtVerificationException {
        if (publicKey == null) {
            throw new JwtVerificationException("JWT verifier not configured");
        }
        if (rawJwt == null || rawJwt.isBlank()) {
            throw new JwtVerificationException("JWT is empty");
        }

        SignedJWT signed;
        try {
            signed = SignedJWT.parse(rawJwt);
        } catch (ParseException e) {
            throw new JwtVerificationException("JWT parse error: " + e.getMessage(), e);
        }

        JWSAlgorithm alg = signed.getHeader().getAlgorithm();
        if (!JWSAlgorithm.EdDSA.equals(alg)) {
            throw new JwtVerificationException("Unexpected JWT algorithm: " + alg);
        }

        try {
            JWSVerifier verifier = new Ed25519Verifier(publicKey);
            if (!signed.verify(verifier)) {
                throw new JwtVerificationException("JWT signature invalid");
            }
        } catch (Exception e) {
            throw new JwtVerificationException("JWT signature verification failed: " + e.getMessage(), e);
        }

        JWTClaimsSet claims;
        try {
            claims = signed.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new JwtVerificationException("JWT claims parse error", e);
        }

        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw new JwtVerificationException("JWT issuer mismatch: " + claims.getIssuer());
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(expectedAudience)) {
            throw new JwtVerificationException("JWT audience mismatch");
        }

        Date exp = claims.getExpirationTime();
        Date iat = claims.getIssueTime();
        String sub = claims.getSubject();
        if (exp == null || iat == null || sub == null) {
            throw new JwtVerificationException("JWT missing required claims");
        }

        // Note : on ne refuse pas un JWT expire ici. C'est au LicenseService
        // de decider ce qu'il fait d'un JWT expire (grace period, refresh, etc.).
        // La verification de signature reste valide tant que la cle existe.

        String tierId;
        String instanceId;
        try {
            tierId = claims.getStringClaim("tier_id");
            instanceId = claims.getStringClaim("instance_id");
        } catch (ParseException e) {
            throw new JwtVerificationException("JWT custom claim parse error", e);
        }
        if (tierId == null || tierId.isBlank() || instanceId == null || instanceId.isBlank()) {
            throw new JwtVerificationException("JWT missing tier_id or instance_id");
        }

        return new LicenseClaims(
                sub,
                tierId,
                instanceId,
                iat.toInstant(),
                exp.toInstant()
        );
    }

    /**
     * Parse une cle publique Ed25519 au format PEM SPKI vers un Nimbus
     * {@link OctetKeyPair} (forme JWK utilisee pour la verification).
     */
    private static OctetKeyPair parsePemSpki(String pem) {
        if (pem == null || pem.isBlank()) return null;
        try {
            String base64 = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(base64);
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(ASN1Sequence.fromByteArray(der));
            byte[] keyBytes = spki.getPublicKeyData().getOctets();
            String x = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
            return new OctetKeyPair.Builder(com.nimbusds.jose.jwk.Curve.Ed25519, com.nimbusds.jose.util.Base64URL.from(x))
                    .build();
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Cannot parse licensing JWT public key: {}", e.getMessage());
            return null;
        }
    }

}
