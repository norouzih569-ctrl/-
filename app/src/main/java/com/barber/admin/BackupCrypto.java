package com.barber.admin;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * رمزگذاری فایل پشتیبان (بدون وابستگی به اندروید).
 * قالب: "LBK1" + نمک ۱۶ بایت + IV ۱۲ بایت + متن رمز AES-256-GCM (با برچسب اصالت).
 * کلید از رمز کاربر با PBKDF2-HMAC-SHA256 و ۱۵۰٬۰۰۰ دور ساخته می‌شود؛ رمز اشتباه یا دستکاری فایل با خطا رد می‌شود.
 */
public final class BackupCrypto {

    private static final byte[] MAGIC = "LBK1".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT = 16;
    private static final int IV = 12;
    private static final int ITER = 150000;
    public static final int MIN_PASSWORD = 6;

    public static final class WrongPasswordException extends Exception {
        public WrongPasswordException() {
            super("رمز اشتباه است یا فایل خراب شده است");
        }
    }

    public static final class NotBackupException extends Exception {
        public NotBackupException() {
            super("این فایل، پشتیبان این برنامه نیست");
        }
    }

    private BackupCrypto() {
    }

    private static SecretKey key(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITER, 256);
        try {
            byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(k, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    public static byte[] encrypt(byte[] plain, char[] password) throws GeneralSecurityException {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[SALT];
        byte[] iv = new byte[IV];
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), new GCMParameterSpec(128, iv));
        c.updateAAD(MAGIC);
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[MAGIC.length + SALT + IV + ct.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        System.arraycopy(salt, 0, out, MAGIC.length, SALT);
        System.arraycopy(iv, 0, out, MAGIC.length + SALT, IV);
        System.arraycopy(ct, 0, out, MAGIC.length + SALT + IV, ct.length);
        return out;
    }

    public static byte[] decrypt(byte[] data, char[] password)
            throws GeneralSecurityException, WrongPasswordException, NotBackupException {
        int header = MAGIC.length + SALT + IV;
        if (data == null || data.length < header + 16
                || !Arrays.equals(Arrays.copyOfRange(data, 0, MAGIC.length), MAGIC)) {
            throw new NotBackupException();
        }
        byte[] salt = Arrays.copyOfRange(data, MAGIC.length, MAGIC.length + SALT);
        byte[] iv = Arrays.copyOfRange(data, MAGIC.length + SALT, header);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(password, salt), new GCMParameterSpec(128, iv));
        c.updateAAD(MAGIC);
        try {
            return c.doFinal(data, header, data.length - header);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new WrongPasswordException();
        }
    }
}
