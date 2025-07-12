package com.ner.wimap.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

/**
 * GDPR-compliant encryption utility for sensitive data
 * Uses Android Keystore for secure key management
 */
object EncryptionUtils {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "WiMapSecretKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128
    
    init {
        generateKey()
    }
    
    /**
     * Generate encryption key in Android Keystore
     */
    private fun generateKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    /**
     * Encrypt sensitive data (passwords, personal information) - Background thread version
     * @param plainText The text to encrypt
     * @return Base64 encoded encrypted string with IV prepended
     */
    suspend fun encryptAsync(plainText: String?): String? = withContext(Dispatchers.IO) {
        encrypt(plainText)
    }
    
    /**
     * Encrypt sensitive data (passwords, personal information)
     * @param plainText The text to encrypt
     * @return Base64 encoded encrypted string with IV prepended
     */
    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrEmpty()) return null
        
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryption = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            // Combine IV and encrypted data
            val combined = ByteArray(iv.size + encryption.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryption, 0, combined, iv.size, encryption.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("EncryptionUtils", "Encryption failed", e)
            null
        }
    }
    
    /**
     * Decrypt sensitive data - Background thread version
     * @param encryptedText Base64 encoded encrypted string with IV
     * @return Decrypted plain text
     */
    suspend fun decryptAsync(encryptedText: String?): String? = withContext(Dispatchers.IO) {
        decrypt(encryptedText)
    }
    
    /**
     * Decrypt sensitive data
     * @param encryptedText Base64 encoded encrypted string with IV
     * @return Decrypted plain text
     */
    fun decrypt(encryptedText: String?): String? {
        if (encryptedText.isNullOrEmpty()) return null
        
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            
            // Extract IV and encrypted data
            val iv = ByteArray(IV_LENGTH)
            val encrypted = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("EncryptionUtils", "Decryption failed", e)
            null
        }
    }
    
    /**
     * Hash data for privacy-preserving analytics
     * One-way hash that cannot be reversed
     */
    fun hashForAnalytics(data: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } catch (e: Exception) {
            "anonymous"
        }
    }
    
    /**
     * Anonymize location data for privacy
     * Reduces precision to approximate area
     */
    fun anonymizeLocation(latitude: Double, longitude: Double, precision: LocationPrecision = LocationPrecision.APPROXIMATE): Pair<Double, Double> {
        return when (precision) {
            LocationPrecision.PRECISE -> Pair(latitude, longitude)
            LocationPrecision.APPROXIMATE -> {
                // Round to 3 decimal places (~110m precision)
                val lat = (latitude * 1000).toInt() / 1000.0
                val lon = (longitude * 1000).toInt() / 1000.0
                Pair(lat, lon)
            }
            LocationPrecision.COARSE -> {
                // Round to 2 decimal places (~1.1km precision)
                val lat = (latitude * 100).toInt() / 100.0
                val lon = (longitude * 100).toInt() / 100.0
                Pair(lat, lon)
            }
            LocationPrecision.CITY -> {
                // Round to 1 decimal place (~11km precision)
                val lat = (latitude * 10).toInt() / 10.0
                val lon = (longitude * 10).toInt() / 10.0
                Pair(lat, lon)
            }
        }
    }
    
    enum class LocationPrecision {
        PRECISE,      // Exact location
        APPROXIMATE,  // ~100m accuracy
        COARSE,       // ~1km accuracy
        CITY          // ~10km accuracy
    }
    
    /**
     * Generate pseudonymized ID for analytics
     * Consistent but not reversible to original ID
     */
    fun pseudonymizeId(originalId: String, salt: String = "WiMap2024"): String {
        return hashForAnalytics("$originalId$salt").take(16)
    }
}