plugins {
    // Make sure these versions match what your project actually uses
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.devtools.ksp") version "1.9.23-1.0.19" apply false // Add KSP plugin
}