package it.nexu.forwarding.config;

/** Pure validation rules shared by vault/backup master-password dialogs. */
public final class PasswordRules {
    public static final int MIN_NEW_PASSWORD_LENGTH = 12;
    public static final int MAX_PASSWORD_LENGTH = 1024;
    private PasswordRules() { }

    public static String validate(CharSequence first, CharSequence second, boolean creating) {
        int firstLength = first == null ? 0 : first.length();
        int secondLength = second == null ? 0 : second.length();
        if (firstLength == 0) return "Inserire la password principale.";
        if (firstLength > MAX_PASSWORD_LENGTH) return "La password principale è troppo lunga (massimo 1024 caratteri).";
        if (!creating) return null;
        if (firstLength < MIN_NEW_PASSWORD_LENGTH) return "La password principale deve contenere almeno 12 caratteri.";
        if (secondLength == 0) return "Ripetere la password nel secondo campo.";
        if (secondLength > MAX_PASSWORD_LENGTH) return "La conferma della password è troppo lunga (massimo 1024 caratteri).";
        if (!first.toString().contentEquals(second)) return "Le due password non coincidono.";
        return null;
    }
}
