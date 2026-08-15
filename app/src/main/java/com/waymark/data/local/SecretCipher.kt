package com.waymark.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM sealing backed by the Android Keystore.
 *
 * Confirmation codes, ticket numbers and barcode payloads are written to the
 * database sealed, so a copied database file — or an ADB backup — yields
 * nothing readable. The key never leaves the keystore, and on devices with a
 * secure element it never leaves hardware.
 *
 * The key is not bound to user authentication: the vault has to be readable by
 * the background delay watcher and by the boarding-pass screen on a phone with
 * a flat battery and no biometrics enrolled. Reveal in the UI is gated by
 * [com.waymark.ui.vault.VaultAuthenticator] instead.
 */
class SecretCipher(private val keyAlias: String = DEFAULT_ALIAS) {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }

    /** Base64 of IV ‖ ciphertext ‖ tag. */
    fun seal(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + cipherText
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /** Null when the token is corrupt or was sealed with a key that is gone. */
    fun open(token: String): String? {
        if (token.isBlank()) return null
        return runCatching {
            val packed = Base64.decode(token, Base64.NO_WRAP)
            if (packed.size <= IV_LENGTH) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_LENGTH_BITS, packed, 0, IV_LENGTH),
            )
            String(
                cipher.doFinal(packed, IV_LENGTH, packed.size - IV_LENGTH),
                Charsets.UTF_8,
            )
        }.onFailure { error ->
            Log.w(TAG, "Vault entry could not be opened", error)
        }.getOrNull()
    }

    fun sealOrNull(plainText: String?): String? = plainText?.let { seal(it) }

    private fun key(): SecretKey {
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEFAULT_ALIAS = "waymark.vault.v1"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val TAG = "WaymarkVault"
    }
}
