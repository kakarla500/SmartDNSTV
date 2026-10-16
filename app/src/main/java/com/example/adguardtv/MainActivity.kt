package com.example.adguardtv

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var networkStatusBadge: TextView
    private lateinit var statusTitleText: TextView
    private lateinit var statusSubtitleText: TextView
    private lateinit var activeServerInfoText: TextView
    private lateinit var statusShieldIcon: ImageView
    private lateinit var actionConnectButton: Button

    private lateinit var cardAdGuard: LinearLayout
    private lateinit var cardCloudflare: LinearLayout
    private lateinit var cardGoogle: LinearLayout
    private lateinit var cardCustom: LinearLayout

    private lateinit var checkAdGuard: ImageView
    private lateinit var checkCloudflare: ImageView
    private lateinit var checkGoogle: ImageView
    private lateinit var checkCustom: ImageView

    private lateinit var customDnsContainer: LinearLayout
    private lateinit var customDnsInput: EditText
    private lateinit var autoReconnectCard: LinearLayout
    private lateinit var autoReconnectSwitch: SwitchCompat

    private var pulseAnimator: ObjectAnimator? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DnsManager.KEY_CONNECTED || key == DnsManager.KEY_SELECTED_PRESET) {
            runOnUiThread {
                updateUIState()
            }
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN permission is required to enforce secure DNS", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupFocusEffects()
        setupClickListeners()
        loadPersistedState()
        registerNetworkWatcher()

        // Handle auto-recovery if TV woke from sleep while connection was previously active
        handleWakeAutoRecovery()
        updateUIState()
    }

    private fun bindViews() {
        networkStatusBadge = findViewById(R.id.networkStatusBadge)
        statusTitleText = findViewById(R.id.statusTitleText)
        statusSubtitleText = findViewById(R.id.statusSubtitleText)
        activeServerInfoText = findViewById(R.id.activeServerInfoText)
        statusShieldIcon = findViewById(R.id.statusShieldIcon)
        actionConnectButton = findViewById(R.id.actionConnectButton)

        cardAdGuard = findViewById(R.id.cardAdGuard)
        cardCloudflare = findViewById(R.id.cardCloudflare)
        cardGoogle = findViewById(R.id.cardGoogle)
        cardCustom = findViewById(R.id.cardCustom)

        checkAdGuard = findViewById(R.id.checkAdGuard)
        checkCloudflare = findViewById(R.id.checkCloudflare)
        checkGoogle = findViewById(R.id.checkGoogle)
        checkCustom = findViewById(R.id.checkCustom)

        customDnsContainer = findViewById(R.id.customDnsContainer)
        customDnsInput = findViewById(R.id.customDnsInput)
        autoReconnectCard = findViewById(R.id.autoReconnectCard)
        autoReconnectSwitch = findViewById(R.id.autoReconnectSwitch)
    }

    private fun setupFocusEffects() {
        // Provide scale and elevation feedback for D-Pad remote control focus
        val focusableViews = listOf(
            actionConnectButton,
            cardAdGuard,
            cardCloudflare,
            cardGoogle,
            cardCustom,
            customDnsInput,
            autoReconnectCard
        )

        for (view in focusableViews) {
            view.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.04f else 1.0f
                v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }
        }
    }

    private fun setupClickListeners() {
        actionConnectButton.setOnClickListener {
            if (DnsManager.isActuallyConnected(this)) {
                disconnectDns()
            } else {
                handleConnectAction()
            }
        }

        cardAdGuard.setOnClickListener { selectPreset(DnsManager.PRESET_ADGUARD) }
        cardCloudflare.setOnClickListener { selectPreset(DnsManager.PRESET_CLOUDFLARE) }
        cardGoogle.setOnClickListener { selectPreset(DnsManager.PRESET_GOOGLE) }
        cardCustom.setOnClickListener {
            selectPreset(DnsManager.PRESET_CUSTOM)
            customDnsInput.requestFocus()
        }

        autoReconnectCard.setOnClickListener {
            val newState = !autoReconnectSwitch.isChecked
            autoReconnectSwitch.isChecked = newState
            DnsManager.setAutoReconnect(this, newState)
        }

        autoReconnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            DnsManager.setAutoReconnect(this, isChecked)
        }
    }

    private fun loadPersistedState() {
        val selectedPreset = DnsManager.getSelectedPreset(this)
        applyPresetSelectionUI(selectedPreset)

        val savedCustom = DnsManager.getCustomDns(this)
        if (!savedCustom.isNullOrBlank()) {
            customDnsInput.setText(savedCustom)
        }

        autoReconnectSwitch.isChecked = DnsManager.isAutoReconnectEnabled(this)
        updateNetworkBadge()
    }

    private fun selectPreset(presetId: String) {
        DnsManager.setSelectedPreset(this, presetId)
        applyPresetSelectionUI(presetId)

        // If already connected, smoothly hot-reload to the newly selected server
        if (DnsManager.isActuallyConnected(this)) {
            startVpnService()
        }
    }

    private fun applyPresetSelectionUI(presetId: String) {
        cardAdGuard.isSelected = (presetId == DnsManager.PRESET_ADGUARD)
        cardCloudflare.isSelected = (presetId == DnsManager.PRESET_CLOUDFLARE)
        cardGoogle.isSelected = (presetId == DnsManager.PRESET_GOOGLE)
        cardCustom.isSelected = (presetId == DnsManager.PRESET_CUSTOM)

        checkAdGuard.visibility = if (cardAdGuard.isSelected) View.VISIBLE else View.GONE
        checkCloudflare.visibility = if (cardCloudflare.isSelected) View.VISIBLE else View.GONE
        checkGoogle.visibility = if (cardGoogle.isSelected) View.VISIBLE else View.GONE
        checkCustom.visibility = if (cardCustom.isSelected) View.VISIBLE else View.GONE

        customDnsContainer.visibility = if (presetId == DnsManager.PRESET_CUSTOM) View.VISIBLE else View.GONE
        updateUIState()
    }

    private fun handleConnectAction() {
        val selectedPreset = DnsManager.getSelectedPreset(this)
        if (selectedPreset == DnsManager.PRESET_CUSTOM) {
            val customIp = customDnsInput.text.toString().trim()
            if (!DnsManager.isValidDnsIp(customIp) || customIp.isEmpty()) {
                Toast.makeText(this, "Please enter a valid numeric DNS IP address (e.g. 1.1.1.1)", Toast.LENGTH_LONG).show()
                customDnsInput.requestFocus()
                return
            }
            DnsManager.setCustomDns(this, customIp)
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val servers = ArrayList(DnsManager.getServersForCurrentSelection(this))
        val intent = Intent(this, AdGuardVpnService::class.java).apply {
            putStringArrayListExtra(AdGuardVpnService.EXTRA_DNS_SERVERS, servers)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun disconnectDns() {
        val intent = Intent(this, AdGuardVpnService::class.java).apply {
            action = AdGuardVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        DnsManager.setConnected(this, false)
        updateUIState()
    }

    private fun handleWakeAutoRecovery() {
        val wasConnected = DnsManager.isConnected(this)
        val actuallyRunning = DnsManager.isActuallyConnected(this)

        // TV was asleep / standby and connection was dropped, auto-reconnect
        if (wasConnected && !actuallyRunning && DnsManager.isAutoReconnectEnabled(this)) {
            if (VpnService.prepare(this) == null) {
                startVpnService()
            }
        }
    }

    private fun updateUIState() {
        val isConnected = DnsManager.isActuallyConnected(this)
        val selectedPreset = DnsManager.getSelectedPreset(this)
        val preset = DnsManager.PRESETS[selectedPreset]

        if (isConnected) {
            statusTitleText.text = getString(R.string.status_protected)
            statusTitleText.setTextColor(ContextCompat.getColor(this, R.color.status_connected))
            statusSubtitleText.text = getString(R.string.status_desc_connected)

            val serverText = if (selectedPreset == DnsManager.PRESET_CUSTOM) {
                "Active: Custom DNS (${DnsManager.getCustomDns(this) ?: "Default"})"
            } else {
                "Active: ${preset?.displayName ?: "AdGuard"} (${preset?.servers?.firstOrNull() ?: ""})"
            }
            activeServerInfoText.text = serverText
            activeServerInfoText.visibility = View.VISIBLE

            actionConnectButton.text = getString(R.string.btn_disconnect)
            actionConnectButton.setBackgroundResource(R.drawable.btn_disconnect_selector)

            startPulseAnimation()
        } else {
            statusTitleText.text = getString(R.string.status_unprotected)
            statusTitleText.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected))
            statusSubtitleText.text = getString(R.string.status_desc_disconnected)
            activeServerInfoText.visibility = View.GONE

            actionConnectButton.text = getString(R.string.btn_connect)
            actionConnectButton.setBackgroundResource(R.drawable.btn_connect_selector)

            stopPulseAnimation()
        }
        updateNetworkBadge()
    }

    private fun startPulseAnimation() {
        if (pulseAnimator?.isRunning == true) return
        stopPulseAnimation()

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            statusShieldIcon,
            PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.15f),
            PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.15f)
        ).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        statusShieldIcon.scaleX = 1.0f
        statusShieldIcon.scaleY = 1.0f
    }

    private fun updateNetworkBadge() {
        val type = DnsManager.getActiveNetworkType(this)
        val badgeText = when (type) {
            DnsManager.NetworkType.ETHERNET -> getString(R.string.network_ethernet)
            DnsManager.NetworkType.WIFI -> getString(R.string.network_wifi)
            DnsManager.NetworkType.NONE -> getString(R.string.network_none)
        }
        networkStatusBadge.text = badgeText
    }

    private fun registerNetworkWatcher() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread {
                        updateNetworkBadge()
                        handleWakeAutoRecovery()
                    }
                }

                override fun onLost(network: Network) {
                    runOnUiThread {
                        updateNetworkBadge()
                        updateUIState()
                    }
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterNetworkWatcher() {
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

    override fun onResume() {
        super.onResume()
        handleWakeAutoRecovery()
        updateUIState()
    }

    override fun onStart() {
        super.onStart()
        DnsManager.registerPreferenceListener(this, prefChangeListener)
        updateUIState()
    }

    override fun onStop() {
        super.onStop()
        DnsManager.unregisterPreferenceListener(this, prefChangeListener)
        stopPulseAnimation()
    }

    override fun onDestroy() {
        unregisterNetworkWatcher()
        stopPulseAnimation()
        super.onDestroy()
    }
}