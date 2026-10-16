package com.example.adguardtv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class AdGuardVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeServers: ArrayList<String> = arrayListOf()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopVpn()
            return START_NOT_STICKY
        }

        val servers = intent?.getStringArrayListExtra(EXTRA_DNS_SERVERS)
        val customDns = intent?.getStringExtra(EXTRA_CUSTOM_DNS)?.trim()

        val dnsList = when {
            !servers.isNullOrEmpty() -> servers
            !customDns.isNullOrBlank() -> arrayListOf(customDns)
            else -> arrayListOf("94.140.14.14", "94.140.15.15")
        }

        activeServers = dnsList
        startVpn(dnsList)
        return START_STICKY
    }

    private fun startVpn(dnsServers: List<String>) {
        try {
            // Start as a foreground service on Android 8+ so Android TV does not kill the DNS tunnel
            startForeground(NOTIFICATION_ID, buildForegroundNotification())

            vpnInterface?.close()
            vpnInterface = null

            val builder = Builder()
                .setSession("Smart DNS TV")
                .addAddress("10.0.0.2", 32)

            for (server in dnsServers) {
                if (DnsManager.isValidDnsIp(server)) {
                    builder.addDnsServer(server)
                }
            }

            val pfd = builder.establish()
            if (pfd != null) {
                vpnInterface = pfd
                isServiceRunning = true
                DnsManager.setConnected(this, true)
                registerNetworkListener()
            } else {
                isServiceRunning = false
                DnsManager.setConnected(this, false)
                stopSelf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isServiceRunning = false
            DnsManager.setConnected(this, false)
            stopSelf()
        }
    }

    private fun registerNetworkListener() {
        if (networkCallback != null) return
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // When network returns after TV standby, ensure VPN state is verified
                    if (isServiceRunning && vpnInterface == null && activeServers.isNotEmpty()) {
                        startVpn(activeServers)
                    }
                }

                override fun onLost(network: Network) {
                    // Transient network loss on TV sleep
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterNetworkListener() {
        networkCallback?.let {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            networkCallback = null
        }
    }

    private fun stopVpn() {
        isServiceRunning = false
        unregisterNetworkListener()
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        DnsManager.setConnected(this, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_status)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_DISCONNECT = "com.example.adguardtv.STOP"
        const val EXTRA_CUSTOM_DNS = "extra_custom_dns"
        const val EXTRA_DNS_SERVERS = "extra_dns_servers"
        private const val CHANNEL_ID = "smart_dns_tv_service"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isServiceRunning: Boolean = false
    }
}