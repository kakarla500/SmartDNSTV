package com.example.adguardtv

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object DnsManager {
    private const val PREFS_NAME = "dns_prefs"
    const val KEY_CONNECTED = "is_connected"
    const val KEY_CUSTOM_DNS = "custom_dns"
    const val KEY_SELECTED_PRESET = "selected_preset"
    const val KEY_AUTO_RECONNECT = "auto_reconnect"

    const val PRESET_ADGUARD = "ADGUARD"
    const val PRESET_CLOUDFLARE = "CLOUDFLARE"
    const val PRESET_GOOGLE = "GOOGLE"
    const val PRESET_CUSTOM = "CUSTOM"

    enum class NetworkType {
        ETHERNET,
        WIFI,
        NONE
    }

    data class DnsPreset(
        val id: String,
        val displayName: String,
        val description: String,
        val servers: List<String>
    )

    val PRESETS = mapOf(
        PRESET_ADGUARD to DnsPreset(
            id = PRESET_ADGUARD,
            displayName = "AdGuard DNS",
            description = "Ad & Tracker Blocking",
            servers = listOf("94.140.14.14", "94.140.15.15")
        ),
        PRESET_CLOUDFLARE to DnsPreset(
            id = PRESET_CLOUDFLARE,
            displayName = "Cloudflare",
            description = "High Speed & Privacy (1.1.1.1)",
            servers = listOf("1.1.1.1", "1.0.0.1")
        ),
        PRESET_GOOGLE to DnsPreset(
            id = PRESET_GOOGLE,
            displayName = "Google Public",
            description = "Global & Stable (8.8.8.8)",
            servers = listOf("8.8.8.8", "8.8.4.4")
        ),
        PRESET_CUSTOM to DnsPreset(
            id = PRESET_CUSTOM,
            displayName = "Custom DNS",
            description = "User-defined Resolver",
            servers = emptyList()
        )
    )

    fun getSelectedPreset(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_PRESET, PRESET_ADGUARD) ?: PRESET_ADGUARD
    }

    fun setSelectedPreset(context: Context, presetId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_PRESET, presetId)
            .apply()
    }

    fun getServersForCurrentSelection(context: Context): List<String> {
        val preset = getSelectedPreset(context)
        if (preset == PRESET_CUSTOM) {
            val custom = getCustomDns(context)
            if (!custom.isNullOrBlank() && isValidDnsIp(custom)) {
                return listOf(custom.trim())
            }
            return PRESETS[PRESET_ADGUARD]!!.servers
        }
        return PRESETS[preset]?.servers ?: PRESETS[PRESET_ADGUARD]!!.servers
    }

    /**
     * Verifies if the VPN tunnel is genuinely running on the system by checking
     * both the service process flag and the active Android network capabilities.
     * If the TV was put to sleep or the network reset, this self-heals any stale state.
     */
    fun isActuallyConnected(context: Context): Boolean {
        val prefsConnected = isConnected(context)
        val vpnActive = isVpnNetworkActive(context) || AdGuardVpnService.isServiceRunning

        if (prefsConnected && !vpnActive) {
            // TV was powered off, slept, or network reset: heal the stale state
            setConnected(context, false)
            return false
        }
        return vpnActive && prefsConnected
    }

    fun isVpnNetworkActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            cm.allNetworks.any { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getActiveNetworkType(context: Context): NetworkType {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkType.NONE
            val activeNetwork = cm.activeNetwork ?: return NetworkType.NONE
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkType.NONE

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                else -> NetworkType.NONE
            }
        } catch (e: Exception) {
            NetworkType.NONE
        }
    }

    fun isAutoReconnectEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RECONNECT, true)
    }

    fun setAutoReconnect(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_RECONNECT, enabled)
            .apply()
    }

    fun isConnected(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONNECTED, false)
    }

    fun setConnected(context: Context, connected: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONNECTED, connected)
            .apply()
    }

    fun getCustomDns(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_DNS, null)
    }

    fun setCustomDns(context: Context, dns: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_DNS, dns?.trim())
            .apply()
    }

    fun registerPreferenceListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPreferenceListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Validates whether a given DNS IP is a valid numeric IPv4 or IPv6 address.
     * Null or blank strings are considered valid (default DNS will be used).
     */
    fun isValidDnsIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return true
        val trimmed = ip.trim()
        val parts = trimmed.split(".")
        if (parts.size == 4) {
            return parts.all { part ->
                part.toIntOrNull()?.let { it in 0..255 && (part == "0" || !part.startsWith("0")) } ?: false
            }
        }
        if (trimmed.contains(":") && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }) {
            val colons = trimmed.count { it == ':' }
            if (colons in 2..7) return true
        }
        return false
    }
}