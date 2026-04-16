package com.xy.welllog.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    private final Key key;
    private final long accessExpirationSeconds;
    private final long refreshExpirationSeconds;

    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.access-expiration}") long accessExpirationSeconds,
                    @Value("${jwt.refresh-expiration}") long refreshExpirationSeconds) {
        this.key = Keys.hmacShaKeyFor(resolveSecretBytes(secret));
        this.accessExpirationSeconds = accessExpirationSeconds;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    public String generateAccessToken(String username, Long userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessExpirationSeconds * 1000);
        return Jwts.builder()
                .setSubject(username)
                .claim("uid", userId)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.getSubject() : null;
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        if (claims == null) {
            return null;
        }
        Object uid = claims.get("uid");
        if (uid instanceof Number number) {
            return number.longValue();
        }
        if (uid instanceof String uidStr) {
            try {
                return Long.parseLong(uidStr);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public boolean validateToken(String token) {
        return getClaims(token) != null;
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    private Claims getClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] resolveSecretBytes(String secret) {
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret 长度不足，至少需要 32 字节");
        }
        return bytes;
    }
}
