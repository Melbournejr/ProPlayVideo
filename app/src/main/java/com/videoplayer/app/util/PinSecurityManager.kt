package com.videoplayer.app.util

import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinSecurityManager {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    const val MIN_PIN_LENGTH = 6

    fun isValidNewPin(pin: String): Boolean = pin.trim().length >= MIN_PIN_LENGTH

    fun generateSaltBase64(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPinPbkdf2(pin: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.trim().toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = skf.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * So sánh mảng byte bằng Constant-Time chống tấn công đo thời gian (Timing Attack)
     */
    fun verifyPinConstantTime(inputPin: String, storedHashBase64: String, storedSaltBase64: String): Boolean {
        return try {
            val computedHashB64 = hashPinPbkdf2(inputPin, storedSaltBase64)
            val a = Base64.decode(computedHashB64, Base64.NO_WRAP)
            val b = Base64.decode(storedHashBase64, Base64.NO_WRAP)
            MessageDigest.isEqual(a, b)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Tính thời gian khóa (ms) sau khi nhập sai >= 5 lần:
     * Lần 5: 30s | Lần 6: 1p | Lần 7: 5p | Lần 8: 15p | Lần >=9: 30p
     */
    fun computeLockoutDurationMs(failedAttempts: Int): Long {
        return when {
            failedAttempts < 5 -> 0L
            failedAttempts == 5 -> 30_000L
            failedAttempts == 6 -> 60_000L
            failedAttempts == 7 -> 5 * 60_000L
            failedAttempts == 8 -> 15 * 60_000L
            else -> 30 * 60_000L
        }
    }

    fun canUseBiometric(activity: FragmentActivity): Boolean {
        val bm = BiometricManager.from(activity)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onUsePinFallback: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Mở khóa Kho Video Riêng Tư")
            .setSubtitle("Xác thực sinh trắc học hoặc chọn nhập Mã PIN")
            .setNegativeButtonText("Dùng Mã PIN")
            .build()

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        onUsePinFallback()
                    }
                }
            }
        )
        prompt.authenticate(promptInfo)
    }
}