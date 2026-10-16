package com.example.adguardtv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val wasConnected = DnsManager.isConnected(context)
            val autoReconnect = DnsManager.isAutoReconnectEnabled(context)

            if (wasConnected && autoReconnect) {
                // If VpnService.prepare returns null, permission is already active
                if (VpnService.prepare(context) == null) {
                    val servers = ArrayList(DnsManager.getServersForCurrentSelection(context))
                    val serviceIntent = Intent(context, AdGuardVpnService::class.java).apply {
                        putStringArrayListExtra(AdGuardVpnService.EXTRA_DNS_SERVERS, servers)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }
}

