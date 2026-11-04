package com.banishwor.antminerrelay.storage

import android.content.Context
import android.security.KeyPairGeneratorSpec
import android.util.Base64
import java.math.BigInteger
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.security.auth.x500.X500Principal

/**
 * Relay Lite's credential vault for API 22 — the exact design the
 * :relay-probe module proved on the Vivo Y21L (Gate A, Test 3: save,
 * forget memory, genuine re-decrypt from storage alone).
 *
 * The main app's AES-256-GCM AndroidKeyStore scheme starts at API 23,
 * so API 22 uses the older pattern:
 *
 *   1. An RSA keypair lives in the AndroidKeyStore (never leaves it),
 *      created with [KeyPairGeneratorSpec] — the API 18–22 API.
 *   2. A fresh random AES-256 key is generated per save and immediately
 *      wrapped with the RSA public key (RSA/ECB/PKCS1Padding).
 *   3. The plaintext is encrypted with that AES key
 *      (AES/CBC/PKCS5Padding, fresh random IV per save).
 *   4. Only wrapped key + IV + ciphertext are stored, Base64, packed
 *      into the single prefixed string this object returns.
 *
 * Stateless by design, like the probe: no key material is held in
 * fields between calls, so a decrypt after process death re-reads
 * everything from the stored string + KeyStore for real.
 */
object LiteVault {

    private const val KEY_ALIAS = "antminer_relay_lite_vault_rsa"
    private const val RSA_TRANSFORM = "RSA/ECB/PKCS1Padding"
    private const val AES_TRANSFORM = "AES/CBC/PKCS5Padding"

    /** Marks a vault payload: prefix + Base64(wrapped) + ":" + Base64(IV) + ":" + Base64(ciphertext). */
    private const val PREFIX = "enc_rsa_v1:"

    fun isEncrypted(value: String?): Boolean = value != null && value.startsWith(PREFIX)

    /** Encrypts [plainText]; throws on any crypto failure. */
    fun encrypt(context: Context, plainText: String): String {
        val publicKey = ensureRsaKeyPair(context)

        val aesGen = KeyGenerator.getInstance("AES")
        aesGen.init(256, SecureRandom())
        val aesKey: SecretKey = aesGen.generateKey()

        val wrapCipher = Cipher.getInstance(RSA_TRANSFORM)
        wrapCipher.init(Cipher.WRAP_MODE, publicKey)
        val wrappedKey = wrapCipher.wrap(aesKey)

        val encCipher = Cipher.getInstance(AES_TRANSFORM)
        encCipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = encCipher.iv
        val ciphertext = encCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return PREFIX +
            Base64.encodeToString(wrappedKey, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /** Decrypts a string produced by [encrypt]; throws on any failure. */
    fun decrypt(context: Context, stored: String): String {
        val parts = stored.removePrefix(PREFIX).split(":")
        require(parts.size == 3) { "malformed vault payload" }
        val wrappedKey = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey(KEY_ALIAS, null)
        check(key is PrivateKey) { "RSA private key missing from AndroidKeyStore" }

        val unwrapCipher = Cipher.getInstance(RSA_TRANSFORM)
        unwrapCipher.init(Cipher.UNWRAP_MODE, key)
        val aesKey = unwrapCipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY)

        val decCipher = Cipher.getInstance(AES_TRANSFORM)
        decCipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))
        return String(decCipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    @Suppress("DEPRECATION") // KeyPairGeneratorSpec is THE API 22 way; its replacement starts at 23.
    private fun ensureRsaKeyPair(context: Context): PublicKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getCertificate(KEY_ALIAS).publicKey
        }
        val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
        val spec = KeyPairGeneratorSpec.Builder(context.applicationContext)
            .setAlias(KEY_ALIAS)
            .setSubject(X500Principal("CN=Antminer Relay Lite Vault"))
            .setSerialNumber(BigInteger.ONE)
            .setStartDate(start.time)
            .setEndDate(end.time)
            .build()
        val kpg = java.security.KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
        kpg.initialize(spec)
        return kpg.generateKeyPair().public
    }
}
