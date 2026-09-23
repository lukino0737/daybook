package dev.lukino.daybook.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AiSettingsStore(context: Context, name: String = "daybook-ai") : AiCredentials {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val alias = "$name-key"
    override fun config() = AiConfig(
        preferences.getString("model", null).takeIf { it in AiProtocol.models } ?: AiProtocol.models.first(),
        preferences.getBoolean("thinking", false), preferences.contains("ciphertext"))

    override fun key(): String {
        val encrypted = preferences.getString("ciphertext", null) ?: return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(preferences.getString("iv", ""), Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { throw AiFailure("本机密钥无法解密，请删除并重新填写API Key。") }
    }

    override fun save(key: String?, model: String, thinking: Boolean) {
        require(model in AiProtocol.models)
        val edit = preferences.edit().putString("model", model).putBoolean("thinking", thinking)
        if (key != null) {
            val value = key.trim()
            require(value.length in 8..512 && value.none { it.isWhitespace() || it.code < 32 }) { "API Key 格式不正确" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secret())
            edit.putString("ciphertext", Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            edit.putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
        if (!edit.commit()) throw AiFailure("AI设置未保存，请重试。")
    }
    override fun delete() {
        if (!preferences.edit().remove("ciphertext").remove("iv").commit()) throw AiFailure("密钥未删除，请重试。")
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
