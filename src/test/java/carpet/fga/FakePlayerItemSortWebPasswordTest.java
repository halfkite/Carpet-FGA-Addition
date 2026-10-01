//#if MC == 26.3
package carpet.fga;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FakePlayerItemSortWebPasswordTest {
    @Test void passwordsAreSaltedAndWrongPasswordsAreRejected() {
        String password = "Fga-Test-Password-2026";
        String first = FakePlayerItemSortWebPassword.hash(password);
        String second = FakePlayerItemSortWebPassword.hash(password);
        assertNotEquals(first, second);
        assertFalse(first.contains(password));
        assertTrue(FakePlayerItemSortWebPassword.verify(password, first));
        assertFalse(FakePlayerItemSortWebPassword.verify("wrong-password", first));
        assertFalse(FakePlayerItemSortWebPassword.verify(password, "malformed"));
        assertThrows(IllegalArgumentException.class, () -> FakePlayerItemSortWebPassword.hash("short"));
    }
}
//#endif
