package it.nexu.forwarding;

import it.nexu.forwarding.config.PasswordRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PasswordRulesTest {
    @Test void newVaultRequiresVisibleSpecificValidation() {
        assertEquals("Inserire la password principale.", PasswordRules.validate("", "", true));
        assertTrue(PasswordRules.validate("short", "short", true).contains("almeno 12"));
        assertTrue(PasswordRules.validate("long-enough-password", "", true).contains("secondo campo"));
        assertTrue(PasswordRules.validate("long-enough-password", "different-password", true).contains("non coincidono"));
        assertNull(PasswordRules.validate("long-enough-password", "long-enough-password", true));
    }
    @Test void unlockOnlyRequiresNonEmptyBoundedPassword() {
        assertEquals("Inserire la password principale.", PasswordRules.validate("", "", false));
        assertNull(PasswordRules.validate("old-password", "", false));
        assertTrue(PasswordRules.validate("x".repeat(1025), "", false).contains("troppo lunga"));
    }
}
