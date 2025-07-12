package com.ner.wimap.utils

import com.ner.wimap.data.database.PinnedNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Async encryption extensions to avoid blocking the main thread
 * These provide background thread alternatives to the synchronous encryption operations
 */

/**
 * Get decrypted password asynchronously to avoid blocking main thread
 */
suspend fun PinnedNetwork.getSavedPasswordAsync(): String? = withContext(Dispatchers.IO) {
    EncryptionUtils.decrypt(encryptedPassword)
}

/**
 * Create a copy with encrypted password asynchronously
 */
suspend fun PinnedNetwork.withPasswordAsync(password: String?): PinnedNetwork = withContext(Dispatchers.IO) {
    copy(encryptedPassword = EncryptionUtils.encrypt(password))
}

/**
 * Helper for bulk password operations
 */
suspend fun List<PinnedNetwork>.mapWithDecryptedPasswords(): List<Pair<PinnedNetwork, String?>> = withContext(Dispatchers.Default) {
    map { network ->
        val password = withContext(Dispatchers.IO) {
            EncryptionUtils.decrypt(network.encryptedPassword)
        }
        Pair(network, password)
    }
}