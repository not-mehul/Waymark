package com.waymark.ui.vault

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The gate in front of a plaintext code.
 *
 * The vault key itself is not bound to user authentication — background delay
 * checks and the boarding-pass screen have to work on a phone nobody is
 * holding. What is gated is the moment a code becomes readable on screen, in
 * a taxi queue, over a stranger's shoulder.
 *
 * Where no biometric or device credential is enrolled, revealing proceeds:
 * refusing to show a traveler their own confirmation code because they have no
 * screen lock would be theatre, not security.
 */
class VaultAuthenticator(private val activity: FragmentActivity?) {

    fun isAvailable(): Boolean {
        val context = activity ?: return false
        return BiometricManager.from(context).canAuthenticate(allowed()) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Device credential can only join the allowed set from API 30; below that
     * the prompt needs its own negative button instead.
     */
    private fun allowed(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    }

    fun authenticate(
        reason: String,
        onResult: (Boolean) -> Unit,
    ) {
        val host = activity
        if (host == null || !isAvailable()) {
            onResult(true)
            return
        }

        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }

                override fun onAuthenticationFailed() = Unit
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Open the vault")
            .setSubtitle(reason)
            .setAllowedAuthenticators(allowed())
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText("Cancel")
            }
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun rememberVaultAuthenticator(): VaultAuthenticator {
    val context = LocalContext.current
    return VaultAuthenticator(context as? FragmentActivity)
}
