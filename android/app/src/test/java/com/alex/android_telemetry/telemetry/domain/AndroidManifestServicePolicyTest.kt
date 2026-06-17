package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AndroidManifestServicePolicyTest {

    @Test
    fun manifest_declares_required_telemetry_permissions() {
        val manifest = androidManifest()

        assertTrue(
            "ACCESS_FINE_LOCATION permission must be declared for telemetry capture",
            manifest.hasUsesPermission("android.permission.ACCESS_FINE_LOCATION"),
        )
        assertTrue(
            "ACTIVITY_RECOGNITION permission must be declared for day monitoring",
            manifest.hasUsesPermission("android.permission.ACTIVITY_RECOGNITION"),
        )
        assertTrue(
            "FOREGROUND_SERVICE permission must be declared for foreground telemetry service",
            manifest.hasUsesPermission("android.permission.FOREGROUND_SERVICE"),
        )
    }

    @Test
    fun manifest_declares_location_foreground_service_permission_on_android_14_plus() {
        val manifest = androidManifest()

        assertTrue(
            "FOREGROUND_SERVICE_LOCATION must be declared for Android 14+ location foreground service policy",
            manifest.hasUsesPermission("android.permission.FOREGROUND_SERVICE_LOCATION"),
        )
    }

    @Test
    fun telemetry_foreground_service_is_declared_and_not_exported() {
        val manifest = androidManifest()

        val service = manifest.findServiceContaining("Telemetry")

        assertTrue(
            "Telemetry foreground service must be declared in AndroidManifest.xml",
            service != null,
        )
        assertTrue(
            "Telemetry foreground service must not be exported",
            service?.androidAttribute("exported") == "false",
        )
    }

    @Test
    fun telemetry_foreground_service_declares_location_service_type() {
        val manifest = androidManifest()

        val service = manifest.findServiceContaining("Telemetry")

        assertTrue(
            "Telemetry foreground service must declare android:foregroundServiceType=\"location\" or include location",
            service?.androidAttribute("foregroundServiceType")
                ?.split("|")
                ?.map { it.trim() }
                ?.contains("location") == true,
        )
    }
}

private fun androidManifest(): ParsedAndroidManifest {
    val candidates = listOf(
        File("src/main/AndroidManifest.xml"),
        File("app/src/main/AndroidManifest.xml"),
    )

    val file = candidates.firstOrNull { it.exists() }
        ?: error("AndroidManifest.xml not found. Checked: ${candidates.joinToString { it.path }}")

    val document = DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(file)

    return ParsedAndroidManifest(document)
}

private class ParsedAndroidManifest(
    private val document: org.w3c.dom.Document,
) {
    fun hasUsesPermission(permission: String): Boolean {
        val nodes = document.getElementsByTagName("uses-permission")

        for (index in 0 until nodes.length) {
            val node = nodes.item(index)

            if (node.androidAttribute("name") == permission) {
                return true
            }
        }

        return false
    }

    fun findServiceContaining(namePart: String): org.w3c.dom.Node? {
        val nodes = document.getElementsByTagName("service")

        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val name = node.androidAttribute("name") ?: continue

            if (name.contains(namePart, ignoreCase = true)) {
                return node
            }
        }

        return null
    }
}

private fun org.w3c.dom.Node.androidAttribute(name: String): String? {
    val attributes = attributes ?: return null

    return attributes.getNamedItem("android:$name")?.nodeValue
        ?: attributes.getNamedItem(name)?.nodeValue
}