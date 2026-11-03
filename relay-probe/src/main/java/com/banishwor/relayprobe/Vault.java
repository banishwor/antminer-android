package com.banishwor.relayprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;

import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.security.auth.x500.X500Principal;

/**
 * The candidate Relay Lite credential vault for API 22.
 *
 * The AES-256-GCM AndroidKeyStore design used by the main app starts at
 * API 23, so API 22 needs the older pattern:
 *
 *   1. An RSA keypair lives in the AndroidKeyStore (never leaves it).
 *   2. A random AES-256 key is generated with the plain JCE KeyGenerator
 *      and immediately wrapped with the RSA public key.
 *   3. Secrets are encrypted with that AES key (AES/CBC/PKCS5Padding,
 *      random IV per save).
 *   4. SharedPreferences stores ONLY: wrapped AES key + IV + ciphertext,
 *      all Base64. The private key unwraps the AES key at read time.
 *
 * This class is deliberately stateless: it holds no key material in fields
 * between calls, so "verify" truly re-reads everything from
 * SharedPreferences + KeyStore — that is what the probe's
 * "Forget memory & verify" button proves.
 *
 * The dummy secret is a fixed internal constant; it is never displayed.
 */
final class Vault {

    private static final String KEY_ALIAS = "relayprobe_vault_rsa";
    private static final String VAULT_PREFS = "probe_vault";
    private static final String PREF_WRAPPED_KEY = "wrapped_key";
    private static final String PREF_IV = "iv";
    private static final String PREF_CIPHERTEXT = "ciphertext";

    private static final String RSA_TRANSFORM = "RSA/ECB/PKCS1Padding";
    private static final String AES_TRANSFORM = "AES/CBC/PKCS5Padding";

    private static final String DUMMY_SECRET = "relay-probe-dummy-secret-do-not-display";

    private Vault() {
    }

    static boolean hasSavedSecret(Context ctx) {
        return vaultPrefs(ctx).getString(PREF_CIPHERTEXT, null) != null;
    }

    /** Generates keys if needed and stores the dummy secret. Throws on any failure. */
    static void saveDummySecret(Context ctx) throws Exception {
        PublicKey publicKey = ensureRsaKeyPair(ctx);

        KeyGenerator aesGen = KeyGenerator.getInstance("AES");
        aesGen.init(256, new SecureRandom());
        SecretKey aesKey = aesGen.generateKey();

        Cipher wrapCipher = Cipher.getInstance(RSA_TRANSFORM);
        wrapCipher.init(Cipher.WRAP_MODE, publicKey);
        byte[] wrappedKey = wrapCipher.wrap(aesKey);

        Cipher encCipher = Cipher.getInstance(AES_TRANSFORM);
        encCipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] iv = encCipher.getIV();
        byte[] ciphertext = encCipher.doFinal(DUMMY_SECRET.getBytes("UTF-8"));

        vaultPrefs(ctx).edit()
                .putString(PREF_WRAPPED_KEY, Base64.encodeToString(wrappedKey, Base64.NO_WRAP))
                .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(PREF_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .commit();
    }

    /**
     * Re-reads wrapped key + IV + ciphertext from SharedPreferences, unwraps
     * the AES key with the KeyStore private key, decrypts, and reports
     * whether the plaintext matches the dummy secret. Nothing is returned
     * but the boolean — the secret itself never leaves this method.
     */
    static boolean verifyDummySecret(Context ctx) throws Exception {
        SharedPreferences p = vaultPrefs(ctx);
        String wrappedB64 = p.getString(PREF_WRAPPED_KEY, null);
        String ivB64 = p.getString(PREF_IV, null);
        String ctB64 = p.getString(PREF_CIPHERTEXT, null);
        if (wrappedB64 == null || ivB64 == null || ctB64 == null) {
            throw new IllegalStateException("no saved secret in SharedPreferences");
        }
        byte[] wrappedKey = Base64.decode(wrappedB64, Base64.NO_WRAP);
        byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(ctB64, Base64.NO_WRAP);

        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        Key key = ks.getKey(KEY_ALIAS, null);
        if (!(key instanceof PrivateKey)) {
            throw new IllegalStateException("RSA private key missing from AndroidKeyStore");
        }

        Cipher unwrapCipher = Cipher.getInstance(RSA_TRANSFORM);
        unwrapCipher.init(Cipher.UNWRAP_MODE, (PrivateKey) key);
        Key aesKey = unwrapCipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);

        Cipher decCipher = Cipher.getInstance(AES_TRANSFORM);
        decCipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
        String plain = new String(decCipher.doFinal(ciphertext), "UTF-8");
        return DUMMY_SECRET.equals(plain);
    }

    private static PublicKey ensureRsaKeyPair(Context ctx) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ks.getCertificate(KEY_ALIAS).getPublicKey();
        }
        Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_YEAR, -1);
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 30);

        // KeyPairGeneratorSpec is the API 18–22 way; API 23 replaced it with
        // KeyGenParameterSpec, which does not exist on the Y21L.
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(ctx)
                .setAlias(KEY_ALIAS)
                .setSubject(new X500Principal("CN=Relay Probe Vault"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(start.getTime())
                .setEndDate(end.getTime())
                .build();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        kpg.initialize(spec);
        return kpg.generateKeyPair().getPublic();
    }

    private static SharedPreferences vaultPrefs(Context ctx) {
        return ctx.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE);
    }
}
