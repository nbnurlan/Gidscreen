package com.example.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-only credential: encrypted with a non-exportable Android Keystore key. */
object GeminiKeyStore {
    private const val ALIAS = "gidscreen_gemini_local"
    private lateinit var file: AtomicFile
    @Volatile private var value = ""
    private val status = MutableStateFlow(false)
    val configured = status.asStateFlow()

    @Synchronized fun init(context: Context) {
        file = AtomicFile(File(context.noBackupFilesDir, "gemini-key.enc"))
        value = try {
            val bytes = file.readFully()
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
        status.value = value.isNotBlank()
    }

    fun get(): String = value

    @Synchronized fun save(input: String) {
        val clean = input.trim()
        require(clean.isNotEmpty() && clean != "MY_GEMINI_API_KEY" && clean.none { it.isWhitespace() })
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val bytes = cipher.iv + cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        value = clean
        status.value = true
    }

    @Synchronized fun clear() {
        file.delete()
        value = ""
        status.value = false
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
