package it.nexu.forwarding;

import it.nexu.forwarding.config.PasswordRules;
import it.nexu.forwarding.i18n.I18n;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PasswordRulesTest {
    @Test void newVaultRequiresVisibleSpecificValidation() {
        I18n.setLanguage(I18n.Language.ENGLISH);
        assertEquals("Enter the master password.", PasswordRules.validate("", "", true));
        assertTrue(PasswordRules.validate("short", "short", true).contains("at least 12"));
        assertTrue(PasswordRules.validate("long-enough-password", "", true).contains("second field"));
        assertTrue(PasswordRules.validate("long-enough-password", "different-password", true).contains("do not match"));
        assertNull(PasswordRules.validate("long-enough-password", "long-enough-password", true));
    }
    @Test void italianValidationMessagesRemainAvailable() {
        I18n.setLanguage(I18n.Language.ITALIAN);
        assertEquals("Inserire la password principale.", PasswordRules.validate("", "", true));
        I18n.setLanguage(I18n.Language.ENGLISH);
    }
    @Test void unlockOnlyRequiresNonEmptyBoundedPassword() {
        I18n.setLanguage(I18n.Language.ENGLISH);
        assertEquals("Enter the master password.", PasswordRules.validate("", "", false));
        assertNull(PasswordRules.validate("old-password", "", false));
        assertTrue(PasswordRules.validate("x".repeat(1025), "", false).contains("too long"));
    }
}
