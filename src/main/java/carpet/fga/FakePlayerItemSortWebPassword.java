//#if MC == 26.3
package carpet.fga;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Passwords never enter dashboard snapshots; only salted password hashes are persisted. */
public final class FakePlayerItemSortWebPassword {
    private static final int ITERATIONS = 120_000;
    private FakePlayerItemSortWebPassword() {}

    public static String hash(String password) {
        if (password.length() < 12 || password.length() > 128
                || password.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException("password must contain 12-128 printable ASCII characters without spaces");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derive(password, salt));
    }

    public static boolean validHash(String encoded) {
        try {
            String[] parts = encoded.split(":", -1);
            return parts.length == 2 && Base64.getDecoder().decode(parts[0]).length == 16
                    && Base64.getDecoder().decode(parts[1]).length == 32;
        } catch (IllegalArgumentException exception) { return false; }
    }

    public static boolean verify(String password, String encoded) {
        if (password.length() > 128 || !validHash(encoded)) return false;
        String[] parts = encoded.split(":", -1);
        return MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(parts[0])),
                Base64.getDecoder().decode(parts[1]));
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException(exception); }
        finally { spec.clearPassword(); }
    }
}
//#endif
