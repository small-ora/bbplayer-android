package com.bbplayer.android.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 可注入的加解密接口：生产环境用 AndroidKeyStore 的 AES-GCM，
 * 纯 JVM 单测注入 fake 实现（不依赖 AndroidKeyStore）。
 */
internal interface CookieCipher {
    /** 加密明文，返回 `iv(12B) || ciphertext+tag`。 */
    fun encrypt(plaintext: ByteArray): ByteArray

    /** 解密 [encrypt] 的返回值；密钥失效 / 数据损坏时抛异常。 */
    fun decrypt(payload: ByteArray): ByteArray

    /** 丢弃密钥材料（退出登录 / 密文损坏时调用）；默认无操作，AndroidKeyStore 实现会删除 key。 */
    fun reset() {}
}

/** 可注入的加密串存储（生产用 SharedPreferences，测试用内存实现）。 */
internal interface SecureStorage {
    fun read(): String?
    fun write(value: String)
    fun erase()
}

/** `SharedPreferences("auth_secure")` 的单值存储。 */
internal class SharedPreferencesSecureStorage(context: Context) : SecureStorage {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = runCatching { preferences.getString(KEY_CIPHERTEXT, null) }.getOrNull()

    override fun write(value: String) {
        // commit() 而不是 apply()：登录成功后凭据必须在下一次读之前落盘。
        preferences.edit().putString(KEY_CIPHERTEXT, value).commit()
    }

    override fun erase() {
        preferences.edit().remove(KEY_CIPHERTEXT).commit()
    }

    internal companion object {
        const val PREFERENCES_NAME = "auth_secure"
        const val KEY_CIPHERTEXT = "cookies_aes_gcm_v1"
    }
}

/**
 * AndroidKeyStore AES/GCM/NoPadding 实现。
 *
 * - key alias 固定为 [KEY_ALIAS]（只创建一次，后续复用）；
 * - 每次加密由系统生成 12 字节 IV，输出 `iv || ciphertext`（GCM tag 128 bit）；
 * - 密钥被系统吊销 / 数据被篡改时 `doFinal` 抛 [javax.crypto.AEADBadTagException]，
 *   由 [SecureCookieStore] 统一按“损坏”处理（清空并返回空）。
 */
internal class KeystoreCookieCipher(
    private val keyAlias: String = KEY_ALIAS,
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) },
) : CookieCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    override fun decrypt(payload: ByteArray): ByteArray {
        if (payload.size <= IV_LENGTH) throw IllegalArgumentException("encrypted cookie payload too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH)
        cipher.init(Cipher.DECRYPT_MODE, existingKey(), spec)
        return cipher.doFinal(payload, IV_LENGTH, payload.size - IV_LENGTH)
    }

    private fun existingKey(): SecretKey =
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw IllegalStateException("auth key missing")

    override fun reset() {
        runCatching { keyStore.deleteEntry(keyAlias) }
    }

    private fun loadOrCreateKey(): SecretKey {
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    internal companion object {
        const val KEY_ALIAS = "bbplayer_auth_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}

/**
 * 登录 Cookie 的本地加密存储（凭据红线实现）。
 *
 * - Cookie map 序列化成 JSON → [CookieCipher] 加密 → `Base64(iv + ciphertext)` → SharedPreferences；
 * - **绝不打印 Cookie 值**，本类没有任何日志；
 * - 读取时任何异常（Base64 损坏 / GCM 校验失败 / 密钥丢失 / JSON 损坏）都会清空存储并返回空 map；
 * - 构造器允许注入 [SecureStorage] + [CookieCipher] 以便纯 JVM 单测。
 */
class SecureCookieStore internal constructor(
    private val storage: SecureStorage,
    private val cipher: CookieCipher,
) {

    /** 生产构造：Keystore 加密 + `auth_secure` SharedPreferences。 */
    constructor(context: Context) : this(
        storage = SharedPreferencesSecureStorage(context),
        cipher = KeystoreCookieCipher(),
    )

    private val lock = Any()

    private val serializer = MapSerializer(String.serializer(), String.serializer())

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 加密并持久化 [cookies]；空 map 等价于 [clear]。
     *
     * @return true 表示已安全落盘；false 表示加密/写入失败（已清空旧数据，调用方可继续用内存态）。
     */
    fun save(cookies: Map<String, String>): Boolean = synchronized(lock) {
        if (cookies.isEmpty()) {
            eraseLocked()
            return true
        }
        return try {
            val plaintext = json.encodeToString(serializer, cookies).toByteArray(Charsets.UTF_8)
            val payload = cipher.encrypt(plaintext)
            storage.write(ENCODER.encodeToString(payload))
            true
        } catch (e: Exception) {
            eraseLocked()
            false
        }
    }

    /** 读取并解密；没有数据、损坏或密钥失效时返回空 map（并清空存储）。 */
    fun load(): Map<String, String> = synchronized(lock) {
        val encoded = runCatching { storage.read() }.getOrNull()
        if (encoded.isNullOrBlank()) return emptyMap()
        return try {
            val payload = DECODER.decode(encoded)
            val plaintext = cipher.decrypt(payload)
            val text = String(plaintext, Charsets.UTF_8)
            json.decodeFromString(serializer, text).filterKeys { it.isNotBlank() }
        } catch (e: Exception) {
            eraseLocked()
            emptyMap()
        }
    }

    /** 退出登录：删除本地密文与 Keystore 密钥（内存态由调用方清理）。 */
    fun clear() {
        synchronized(lock) { eraseLocked() }
    }

    private fun eraseLocked() {
        runCatching { storage.erase() }
        runCatching { cipher.reset() }
    }

    private companion object {
        val ENCODER: Base64.Encoder = Base64.getEncoder().withoutPadding()
        val DECODER: Base64.Decoder = Base64.getDecoder()
    }
}
