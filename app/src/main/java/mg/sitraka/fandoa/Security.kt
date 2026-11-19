package mg.sitraka.fandoa

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.util.UUID

object Security {
    private fun sp(c: Context) = c.getSharedPreferences("security", Context.MODE_PRIVATE)

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun pinEnabled(c: Context) = sp(c).contains("hash")

    fun setPin(c: Context, pin: String) {
        val salt = UUID.randomUUID().toString()
        sp(c).edit().putString("salt", salt).putString("hash", hash(pin, salt)).apply()
    }

    fun checkPin(c: Context, pin: String): Boolean {
        val s = sp(c)
        val salt = s.getString("salt", null) ?: return false
        return s.getString("hash", null) == hash(pin, salt)
    }

    fun clearPin(c: Context) {
        sp(c).edit().remove("hash").remove("salt").putBoolean("bio", false).apply()
    }

    fun bioEnabled(c: Context) = sp(c).getBoolean("bio", false)
    fun setBio(c: Context, v: Boolean) = sp(c).edit().putBoolean("bio", v).apply()

    fun hideRecents(c: Context) = sp(c).getBoolean("hide", false)
    fun setHide(c: Context, v: Boolean) = sp(c).edit().putBoolean("hide", v).apply()

    /** Délai (en secondes) avant verrouillage après passage en arrière-plan. */
    fun graceSec(c: Context) = sp(c).getInt("grace", 0)
    fun setGraceSec(c: Context, v: Int) = sp(c).edit().putInt("grace", v).apply()

    fun canBio(c: Context): Boolean =
        BiometricManager.from(c).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(act: FragmentActivity, onOk: () -> Unit) {
        val p = BiometricPrompt(
            act, ContextCompat.getMainExecutor(act),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onOk()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fandoa")
            .setSubtitle("Confirmez votre identité")
            .setNegativeButtonText("Utiliser le code PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        p.authenticate(info)
    }
}
