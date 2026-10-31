package vn.edu.taskmind.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/** Stateless bearer tokens signed with the account's stored password hash. */
@Component
public final class SignedBearerTokens {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
            .getBytes(StandardCharsets.UTF_8));
    private final ObjectMapper json;

    public SignedBearerTokens(ObjectMapper json) { this.json = json; }

    public record Claims(int userId, String email, long expiresAt) { }

    public String issue(int userId, String email, String passwordHash, long expiresAt) {
        try {
            String payload = encode(json.writeValueAsBytes(new Claims(userId, email, expiresAt)));
            String unsigned = HEADER + "." + payload;
            return unsigned + "." + ENCODER.encodeToString(sign(unsigned, passwordHash));
        } catch (Exception error) {
            throw new IllegalStateException("Could not create bearer token", error);
        }
    }

    public Claims claims(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String[] parts = authorization.substring(7).split("\\.", -1);
        if (parts.length != 3 || parts[0].length() > 100 || parts[1].length() > 2000 || parts[2].length() > 100)
            return null;
        try {
            Claims claims = json.readValue(DECODER.decode(parts[1]), Claims.class);
            if (claims.userId() < 1 || claims.email() == null || claims.expiresAt() <= System.currentTimeMillis() / 1000L)
                return null;
            return claims;
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean verify(String authorization, String passwordHash) {
        Claims claims = claims(authorization);
        if (claims == null || passwordHash == null) return false;
        String token = authorization.substring(7);
        String[] parts = token.split("\\.", -1);
        try {
            byte[] actual = DECODER.decode(parts[2]);
            byte[] expected = sign(parts[0] + "." + parts[1], passwordHash);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ignored) {
            return false;
        }
    }

    private byte[] sign(String value, String passwordHash) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(passwordHash.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) { return ENCODER.encodeToString(value); }
}
