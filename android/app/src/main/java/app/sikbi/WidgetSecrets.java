package app.sikbi;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Credentials are encrypted by Android Keystore and excluded from all backups. No money is stored. */
final class WidgetSecrets {
    private static final String ALIAS = "sikbi.widget.v1";
    private static File file(Context context) { return new File(context.getNoBackupFilesDir(), "widget-auth"); }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    static synchronized JSONObject read(Context context) {
        try (FileInputStream input = new FileInputStream(file(context))) {
            byte[] iv = new byte[12];
            if (input.read(iv) != 12) throw new java.io.IOException("Invalid credential");
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[512]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new JSONObject(new String(cipher.doFinal(bytes.toByteArray()), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception error) { clear(context); return new JSONObject(); }
    }

    static synchronized void write(Context context, JSONObject data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        File temporary = new File(context.getNoBackupFilesDir(), "widget-auth.tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(cipher.getIV());
            out.write(cipher.doFinal(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            out.getFD().sync();
        }
        if (!temporary.renameTo(file(context))) throw new java.io.IOException("Cannot save credential");
    }

    static synchronized void clear(Context context) { file(context).delete(); }
    static String token(Context context) { return read(context).optString("token", ""); }

    static String newVerifier() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
    }
    static String challenge(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        StringBuilder text = new StringBuilder();
        for (byte value : hash) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }
}
