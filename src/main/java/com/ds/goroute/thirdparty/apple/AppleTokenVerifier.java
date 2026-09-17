package com.ds.goroute.thirdparty.apple;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppleTokenVerifier {

    private static final String APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    /**
     * The iOS bundle id Apple issues native Sign in with Apple tokens for. Used when
     * {@code apple.allowed-audiences} is absent or blank so a missing key cannot turn
     * the check off.
     */
    private static final List<String> DEFAULT_ALLOWED_AUDIENCES = List.of("app.ondetour");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Client ids this server accepts an Apple identity token for. Apple puts the client
     * id a token was minted for in {@code aud}; without comparing it, a token issued to
     * a completely different app would pass signature and issuer checks and then be
     * linked to whichever GoRoute account holds the same email. Comma separated, so a
     * second client (an Apple Service ID for Android/web sign-in) is added by
     * configuration, not by code.
     */
    @Value("${apple.allowed-audiences:app.ondetour}")
    private List<String> allowedAudiences;

    /**
     * Apple's signing keys, replaced wholesale on refresh. The map is never mutated in
     * place: a login running concurrently with a refresh either sees the whole previous
     * generation or the whole next one, never a half-filled map.
     */
    private volatile Map<String, PublicKey> publicKeysCache = Map.of();
    private volatile long cacheExpiry = 0;
    private static final long CACHE_TTL = 3600000; // 1 hour

    /** Shortest gap between two JWKS fetches, however many unknown kids arrive. */
    private static final long MIN_REFRESH_INTERVAL = 60000; // 1 minute

    private volatile long nextRefreshAllowedAt = 0L;

    public AppleTokenInfo verify(String identityToken) {
        try {
            // Parse JWT header to get kid
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid Apple token format");
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode header = objectMapper.readTree(headerJson);
            String kid = header.get("kid").asText();

            // Get public key
            PublicKey publicKey = getPublicKey(kid);

            // Verify and parse token
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .build()
                    .parseClaimsJws(identityToken)
                    .getBody();

            // A valid Apple signature only proves Apple minted the token, not that it was
            // minted for this app. Reject anything issued to another client id.
            List<String> allowed = effectiveAllowedAudiences();
            List<String> audiences = audiencesOf(claims);
            if (audiences.stream().noneMatch(allowed::contains)) {
                log.warn("Apple identity token rejected: audience {} is not one of {}", audiences, allowed);
                throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid Apple ID token");
            }

            // Apple sends email_verified as a boolean or as the string "true"/"false", and
            // omits it entirely on later sign-ins. Absent has to read as verified: the flag
            // now gates account linking, and treating silence as "unverified" would refuse
            // sign-ins that work today.
            Boolean emailVerified = claims.get("email_verified", Boolean.class);
            if (emailVerified == null) {
                String raw = claims.get("email_verified", String.class);
                emailVerified = raw == null || !"false".equalsIgnoreCase(raw);
            }

            return AppleTokenInfo.builder()
                    .sub(claims.getSubject())
                    .email(claims.get("email", String.class))
                    .emailVerified(emailVerified)
                    .build();

        } catch (Exception e) {
            log.error("Failed to verify Apple ID token", e);
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid Apple ID token");
        }
    }

    /**
     * @return every value of the {@code aud} claim; Apple sends a single string, but the
     *         JWT spec allows an array and jjwt would throw on the typed accessor then.
     */
    private List<String> audiencesOf(Claims claims) {
        Object raw = claims.get("aud");
        List<String> audiences = new ArrayList<>();
        if (raw instanceof Collection<?> values) {
            for (Object value : values) {
                if (value != null) {
                    audiences.add(value.toString());
                }
            }
        } else if (raw != null) {
            audiences.add(raw.toString());
        }
        return audiences;
    }

    private List<String> effectiveAllowedAudiences() {
        List<String> configured = allowedAudiences;
        if (configured == null || configured.isEmpty()) {
            return DEFAULT_ALLOWED_AUDIENCES;
        }
        List<String> cleaned = new ArrayList<>(configured.size());
        for (String value : configured) {
            if (value != null && !value.isBlank()) {
                cleaned.add(value.trim());
            }
        }
        return cleaned.isEmpty() ? DEFAULT_ALLOWED_AUDIENCES : cleaned;
    }

    private PublicKey getPublicKey(String kid) throws Exception {
        Map<String, PublicKey> cached = publicKeysCache;
        if (System.currentTimeMillis() < cacheExpiry && cached.containsKey(kid)) {
            return cached.get(kid);
        }

        // A kid nobody has seen before forces a refresh, and the caller is unauthenticated
        // at this point: anyone can post a token carrying a made-up kid. So the fetch runs
        // OUTSIDE the monitor (holding it across a 30s HTTP call would queue every other
        // sign-in behind the attacker), and refreshes are rate limited so a stream of bogus
        // kids cannot turn into a stream of requests to Apple.
        synchronized (this) {
            cached = publicKeysCache;
            if (System.currentTimeMillis() < cacheExpiry && cached.containsKey(kid)) {
                return cached.get(kid);
            }
            if (System.currentTimeMillis() < nextRefreshAllowedAt) {
                throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Apple public key not found");
            }
            nextRefreshAllowedAt = System.currentTimeMillis() + MIN_REFRESH_INTERVAL;
        }

        Map<String, PublicKey> refreshed = fetchPublicKeys();

        synchronized (this) {
            publicKeysCache = Collections.unmodifiableMap(refreshed);
            cacheExpiry = System.currentTimeMillis() + CACHE_TTL;
        }

        PublicKey publicKey = refreshed.get(kid);
        if (publicKey == null) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Apple public key not found");
        }
        return publicKey;
    }

    private Map<String, PublicKey> fetchPublicKeys() throws Exception {
        String response = restTemplate.getForObject(APPLE_PUBLIC_KEYS_URL, String.class);
        JsonNode keysNode = objectMapper.readTree(response).get("keys");

        Map<String, PublicKey> refreshed = new HashMap<>();
        for (JsonNode keyNode : keysNode) {
            String keyId = keyNode.get("kid").asText();
            String n = keyNode.get("n").asText();
            String e = keyNode.get("e").asText();

            refreshed.put(keyId, createPublicKey(n, e));
        }
        return refreshed;
    }

    private PublicKey createPublicKey(String n, String e) throws Exception {
        byte[] nBytes = Base64.getUrlDecoder().decode(n);
        byte[] eBytes = Base64.getUrlDecoder().decode(e);

        BigInteger modulus = new BigInteger(1, nBytes);
        BigInteger exponent = new BigInteger(1, eBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");

        return factory.generatePublic(spec);
    }
}
