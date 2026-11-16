package com.example.testario.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/** Regression test for Nearby 8032 on Android 13+: normal Wi-Fi permissions must not expire at API 32. */
class NexumNearbyManifestPermissionsTest {
    @Test
    fun wifiPermissionsArePresentOnEverySupportedAndroidVersion() {
        val manifestFile = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.isFile } ?: error("AndroidManifest.xml not found")
        val manifest = manifestFile.readText()

        for (permission in listOf("ACCESS_WIFI_STATE", "CHANGE_WIFI_STATE")) {
            val node = Regex(
                """<uses-permission\b[^>]*android:name="android\.permission\.$permission"[^>]*/>"""
            ).find(manifest)
            assertNotNull("Missing $permission declaration", node)
            assertFalse("$permission must not be limited to API <= 32",
                requireNotNull(node).value.contains("android:maxSdkVersion"))
        }
    }
}
