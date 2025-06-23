package com.ner.wimap.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PasswordManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "wifi_passwords", 
        Context.MODE_PRIVATE
    )
    
    private val _passwords = MutableStateFlow<List<String>>(emptyList())
    val passwords: StateFlow<List<String>> = _passwords
    
    private val _successfulPasswords = MutableStateFlow<Map<String, String>>(emptyMap())
    val successfulPasswords: StateFlow<Map<String, String>> = _successfulPasswords
    
    init {
        loadPasswords()
        loadSuccessfulPasswords()
    }
    
    private fun loadPasswords() {
        val passwordsSet = sharedPreferences.getStringSet("stored_passwords", emptySet()) ?: emptySet()
        _passwords.value = passwordsSet.toList().sorted()
    }
    
    private fun loadSuccessfulPasswords() {
        val successfulPasswordsMap = mutableMapOf<String, String>()
        val allPrefs = sharedPreferences.all
        
        for ((key, value) in allPrefs) {
            if (key.startsWith("successful_") && value is String) {
                val bssid = key.removePrefix("successful_")
                successfulPasswordsMap[bssid] = value
            }
        }
        
        _successfulPasswords.value = successfulPasswordsMap
    }
    
    fun addPassword(password: String) {
        if (password.isNotBlank() && !_passwords.value.contains(password)) {
            val updatedPasswords = (_passwords.value + password).sorted()
            _passwords.value = updatedPasswords
            savePasswordsToPrefs(updatedPasswords)
        }
    }
    
    fun removePassword(password: String) {
        val updatedPasswords = _passwords.value.filter { it != password }
        _passwords.value = updatedPasswords
        savePasswordsToPrefs(updatedPasswords)
    }
    
    fun addSuccessfulPassword(bssid: String, password: String) {
        val updatedSuccessfulPasswords = _successfulPasswords.value.toMutableMap()
        updatedSuccessfulPasswords[bssid] = password
        _successfulPasswords.value = updatedSuccessfulPasswords
        
        // Save to SharedPreferences
        sharedPreferences.edit()
            .putString("successful_$bssid", password)
            .apply()
        
        // Also add to general password list if not already there
        addPassword(password)
    }
    
    fun removeSuccessfulPassword(bssid: String) {
        val updatedSuccessfulPasswords = _successfulPasswords.value.toMutableMap()
        updatedSuccessfulPasswords.remove(bssid)
        _successfulPasswords.value = updatedSuccessfulPasswords
        
        // Remove from SharedPreferences
        sharedPreferences.edit()
            .remove("successful_$bssid")
            .apply()
    }
    
    private fun savePasswordsToPrefs(passwords: List<String>) {
        sharedPreferences.edit()
            .putStringSet("stored_passwords", passwords.toSet())
            .apply()
    }
    
    fun clearAllPasswords() {
        _passwords.value = emptyList()
        _successfulPasswords.value = emptyMap()
        sharedPreferences.edit().clear().apply()
    }
}