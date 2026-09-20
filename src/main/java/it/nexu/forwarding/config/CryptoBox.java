package it.nexu.forwarding.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/** Versioned authenticated envelope. No encryption key is stored alongside the ciphertext. */
public final class CryptoBox {
    public static final byte VAULT = 1, BACKUP = 2;
    public static final int LIMIT = 8_000_000;
    private static final byte[] MAGIC = "NPFSEC01".getBytes(StandardCharsets.US_ASCII);
    private static final int ROUNDS = 600_000, HEADER = 41;
    private static final SecureRandom RANDOM = new SecureRandom();
    private CryptoBox() { }
    public static byte[] encrypt(byte[] plain, char[] password, byte purpose) throws IOException {
        if (plain.length > LIMIT) throw new IOException("Archivio troppo grande.");
        if (password.length < 12 || password.length > 1024)
            throw new IOException("Usare una password principale da 12 a 1024 caratteri.");
        byte[] salt = new byte[16], iv = new byte[12]; RANDOM.nextBytes(salt); RANDOM.nextBytes(iv);
        byte[] header = ByteBuffer.allocate(HEADER).put(MAGIC).put(purpose).putInt(ROUNDS).put(salt).put(iv).array();
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, plain, password, salt, iv, header);
        return ByteBuffer.allocate(header.length + encrypted.length).put(header).put(encrypted).array();
    }
    public static byte[] decrypt(byte[] encrypted, char[] password, byte purpose) throws IOException {
        if (encrypted.length < HEADER + 16 || encrypted.length > LIMIT + HEADER + 16 || password.length > 1024)
            throw new IOException("Dimensione archivio non valida.");
        ByteBuffer b = ByteBuffer.wrap(encrypted); byte[] magic = new byte[8]; b.get(magic);
        if (!Arrays.equals(magic, MAGIC) || b.get() != purpose || b.getInt() != ROUNDS)
            throw new IOException("Formato archivio non supportato. Nessun dato modificato.");
        byte[] salt = new byte[16], iv = new byte[12]; b.get(salt); b.get(iv);
        return crypt(Cipher.DECRYPT_MODE, Arrays.copyOfRange(encrypted, HEADER, encrypted.length), password,
            salt, iv, Arrays.copyOf(encrypted, HEADER));
    }
    private static byte[] crypt(int mode, byte[] input, char[] password, byte[] salt, byte[] iv, byte[] aad) throws IOException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ROUNDS, 256); byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128,iv)); cipher.updateAAD(aad);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IOException("Password errata, archivio alterato o cifratura non disponibile.",e);
        } finally { spec.clearPassword(); if (key != null) Arrays.fill(key,(byte)0); }
    }
}
