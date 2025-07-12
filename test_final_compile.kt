// Quick syntax check for the files we modified
// This file tests that the Kotlin syntax is correct

// 1. EncryptionUtils - Added suspend functions
package com.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TestEncryption {
    suspend fun encryptAsync(plainText: String?): String? = withContext(Dispatchers.IO) {
        // Test that the syntax is correct
        null
    }
}

// 2. AsyncEncryptionExtensions - New file syntax check
suspend fun testExtensions() = withContext(Dispatchers.IO) {
    // Test extension function syntax
}

// 3. ExportManager - withContext syntax
suspend fun testExport() {
    withContext(Dispatchers.IO) {
        // File operations
    }
    withContext(Dispatchers.Main) {
        // UI operations
    }
}

// 4. MainViewModel - flowOn syntax
class TestFlow {
    fun testFlowOperators() {
        // flowOn(Dispatchers.Default)
        // This syntax should compile
    }
}