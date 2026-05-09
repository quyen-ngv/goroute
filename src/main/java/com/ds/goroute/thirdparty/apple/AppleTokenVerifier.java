package com.ds.goroute.thirdparty.apple;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppleTokenVerifier {

    private static final String APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private Map<String, PublicKey> publicKeysCache = new HashMap<>();
    private long cacheExpiry = 0;
    private static final long CACHE_TTL = 3600000; // 1 hour

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
            
            // Extract email verification status
            Boolean emailVerified = claims.get("email_verified", Boolean.class);
            if (emailVerified == null) {
                // Fallback to string check for compatibility
                emailVerified = "true".equals(claims.get("email_verified", String.class));
            }
            
            return AppleTokenInfo.builder()
                    .sub(claims.getSubject())
                    .email(claims.get("email", String.class))
                    .emailVerified(Boolean.TRUE.equals(emailVerified))
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to verify Apple ID token", e);
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid Apple ID token");
        }
    }
    
    private PublicKey getPublicKey(String kid) throws Exception {
        // Check cache
        if (System.currentTimeMillis() < cacheExpiry && publicKeysCache.containsKey(kid)) {
            return publicKeysCache.get(kid);
        }
        
        // Fetch Apple public keys
        String response = restTemplate.getForObject(APPLE_PUBLIC_KEYS_URL, String.class);
        JsonNode keysNode = objectMapper.readTree(response).get("keys");
        
        publicKeysCache.clear();
        
        for (JsonNode keyNode : keysNode) {
            String keyId = keyNode.get("kid").asText();
            String n = keyNode.get("n").asText();
            String e = keyNode.get("e").asText();
            
            PublicKey publicKey = createPublicKey(n, e);
            publicKeysCache.put(keyId, publicKey);
        }
        
        cacheExpiry = System.currentTimeMillis() + CACHE_TTL;
        
        if (!publicKeysCache.containsKey(kid)) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Apple public key not found");
        }
        
        return publicKeysCache.get(kid);
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
