package com.ner.wimap.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GDPR-compliant consent management system
 * Handles granular consent for different data processing purposes
 */
@Singleton
class GDPRConsentManager @Inject constructor(
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _consentState = MutableStateFlow(loadConsentState())
    val consentState: StateFlow<ConsentState> = _consentState.asStateFlow()
    
    companion object {
        private const val PREFS_NAME = "gdpr_consent"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
        private const val KEY_ESSENTIAL_CONSENT = "essential_consent"
        private const val KEY_ANALYTICS_CONSENT = "analytics_consent"
        private const val KEY_ADVERTISING_CONSENT = "advertising_consent"
        private const val KEY_LOCATION_CONSENT = "location_consent"
        private const val KEY_DATA_UPLOAD_CONSENT = "data_upload_consent"
        private const val KEY_AGE_VERIFIED = "age_verified"
        private const val KEY_USER_AGE = "user_age"
        
        const val CURRENT_CONSENT_VERSION = "2.0"
        const val CONSENT_EXPIRY_MONTHS = 13 // GDPR recommends re-consent every 13 months
    }
    
    data class ConsentState(
        val consentVersion: String = "",
        val consentTimestamp: Long = 0L,
        val essentialConsent: Boolean = false,        // Required for app functionality
        val analyticsConsent: Boolean = false,        // Anonymous usage analytics
        val advertisingConsent: Boolean = false,      // Personalized ads
        val locationConsent: Boolean = false,         // Precise location data
        val dataUploadConsent: Boolean = false,       // Upload data to cloud
        val ageVerified: Boolean = false,             // Age verification completed
        val userAge: Int = 0                         // User's declared age
    ) {
        val hasValidConsent: Boolean
            get() = consentVersion == CURRENT_CONSENT_VERSION && 
                   !isConsentExpired && 
                   essentialConsent &&
                   dataUploadConsent && // Required for app functionality
                   locationConsent && // Required for network mapping
                   ageVerified
        
        val isConsentExpired: Boolean
            get() {
                val expiryTime = consentTimestamp + (CONSENT_EXPIRY_MONTHS * 30L * 24 * 60 * 60 * 1000)
                return System.currentTimeMillis() > expiryTime
            }
        
        val needsLocationPermission: Boolean
            get() = locationConsent && essentialConsent
        
        val canShowPersonalizedAds: Boolean
            get() = advertisingConsent && hasValidConsent
        
        val canCollectAnalytics: Boolean
            get() = analyticsConsent && hasValidConsent
        
        val canUploadData: Boolean
            get() = dataUploadConsent && hasValidConsent
        
        val isChildUser: Boolean
            get() = userAge in 1..12 // Under 13 for COPPA compliance
    }
    
    private fun loadConsentState(): ConsentState {
        return ConsentState(
            consentVersion = prefs.getString(KEY_CONSENT_VERSION, "") ?: "",
            consentTimestamp = prefs.getLong(KEY_CONSENT_TIMESTAMP, 0L),
            essentialConsent = prefs.getBoolean(KEY_ESSENTIAL_CONSENT, false),
            analyticsConsent = prefs.getBoolean(KEY_ANALYTICS_CONSENT, false),
            advertisingConsent = prefs.getBoolean(KEY_ADVERTISING_CONSENT, false),
            locationConsent = prefs.getBoolean(KEY_LOCATION_CONSENT, false),
            dataUploadConsent = prefs.getBoolean(KEY_DATA_UPLOAD_CONSENT, false),
            ageVerified = prefs.getBoolean(KEY_AGE_VERIFIED, false),
            userAge = prefs.getInt(KEY_USER_AGE, 0)
        )
    }
    
    /**
     * Record user consent with granular options
     */
    fun recordConsent(
        essentialConsent: Boolean,
        analyticsConsent: Boolean = false,
        advertisingConsent: Boolean = false,
        locationConsent: Boolean = false,
        dataUploadConsent: Boolean = false,
        userAge: Int
    ) {
        val timestamp = System.currentTimeMillis()
        
        prefs.edit()
            .putString(KEY_CONSENT_VERSION, CURRENT_CONSENT_VERSION)
            .putLong(KEY_CONSENT_TIMESTAMP, timestamp)
            .putBoolean(KEY_ESSENTIAL_CONSENT, essentialConsent)
            .putBoolean(KEY_ANALYTICS_CONSENT, analyticsConsent)
            .putBoolean(KEY_ADVERTISING_CONSENT, advertisingConsent)
            .putBoolean(KEY_LOCATION_CONSENT, locationConsent)
            .putBoolean(KEY_DATA_UPLOAD_CONSENT, dataUploadConsent)
            .putBoolean(KEY_AGE_VERIFIED, true)
            .putInt(KEY_USER_AGE, userAge)
            .apply()
        
        _consentState.value = loadConsentState()
    }
    
    /**
     * Update specific consent preferences
     */
    fun updateConsent(
        analyticsConsent: Boolean? = null,
        advertisingConsent: Boolean? = null,
        locationConsent: Boolean? = null,
        dataUploadConsent: Boolean? = null
    ) {
        val editor = prefs.edit()
        
        analyticsConsent?.let { editor.putBoolean(KEY_ANALYTICS_CONSENT, it) }
        advertisingConsent?.let { editor.putBoolean(KEY_ADVERTISING_CONSENT, it) }
        locationConsent?.let { editor.putBoolean(KEY_LOCATION_CONSENT, it) }
        dataUploadConsent?.let { editor.putBoolean(KEY_DATA_UPLOAD_CONSENT, it) }
        
        editor.apply()
        _consentState.value = loadConsentState()
    }
    
    /**
     * Withdraw all consent (GDPR Right to Withdraw)
     */
    fun withdrawConsent() {
        prefs.edit()
            .putBoolean(KEY_ESSENTIAL_CONSENT, false)
            .putBoolean(KEY_ANALYTICS_CONSENT, false)
            .putBoolean(KEY_ADVERTISING_CONSENT, false)
            .putBoolean(KEY_LOCATION_CONSENT, false)
            .putBoolean(KEY_DATA_UPLOAD_CONSENT, false)
            .apply()
        
        _consentState.value = loadConsentState()
    }
    
    /**
     * Check if consent is needed (new user or expired consent)
     */
    fun needsConsent(): Boolean {
        val state = consentState.value
        return !state.hasValidConsent || state.isConsentExpired
    }
    
    /**
     * Get consent summary for user
     */
    fun getConsentSummary(): String {
        val state = consentState.value
        return buildString {
            appendLine("Consent Status:")
            appendLine("- Version: ${state.consentVersion}")
            appendLine("- Essential: ${if (state.essentialConsent) "✓" else "✗"}")
            appendLine("- Analytics: ${if (state.analyticsConsent) "✓" else "✗"}")
            appendLine("- Advertising: ${if (state.advertisingConsent) "✓" else "✗"}")
            appendLine("- Location: ${if (state.locationConsent) "✓" else "✗"}")
            appendLine("- Data Upload: ${if (state.dataUploadConsent) "✓" else "✗"}")
            appendLine("- Age Verified: ${if (state.ageVerified) "✓" else "✗"}")
            if (state.isConsentExpired) {
                appendLine("⚠️ Consent has expired and needs renewal")
            }
        }
    }
    
    /**
     * Clear all consent data (for data deletion requests)
     */
    fun clearAllData() {
        prefs.edit().clear().apply()
        _consentState.value = ConsentState()
    }
    
    /**
     * Export consent data (GDPR Right to Data Portability)
     */
    fun exportConsentData(): Map<String, Any> {
        val state = consentState.value
        return mapOf(
            "consent_version" to state.consentVersion,
            "consent_timestamp" to state.consentTimestamp,
            "consent_date" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(state.consentTimestamp)),
            "essential_consent" to state.essentialConsent,
            "analytics_consent" to state.analyticsConsent,
            "advertising_consent" to state.advertisingConsent,
            "location_consent" to state.locationConsent,
            "data_upload_consent" to state.dataUploadConsent,
            "age_verified" to state.ageVerified,
            "user_age" to state.userAge,
            "consent_valid" to state.hasValidConsent,
            "consent_expired" to state.isConsentExpired
        )
    }
}