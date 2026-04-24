package com.ytbg.play

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ytbg.play.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var prefs: SharedPreferences

    companion object {
        const val REQUEST_OVERLAY = 1001
        const val REQUEST_DEVICE_ADMIN = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        prefs = getSharedPreferences("ytbg_prefs", Context.MODE_PRIVATE)

        setupPermissionButtons()
        setupMethodSelector()
        setupServiceButtons()
        updateStatus()
    }

    private fun setupPermissionButtons() {
        binding.btnEnableOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    REQUEST_OVERLAY
                )
            } else {
                Toast.makeText(this, "Already granted ✓", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEnableAdmin.setOnClickListener {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Required to lock screen while YouTube plays in background")
                }
                startActivityForResult(intent, REQUEST_DEVICE_ADMIN)
            } else {
                Toast.makeText(this, "Already active ✓", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEnableAccessibility.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Service")
                .setMessage("1. Find 'YT Background Play' in the list\n2. Tap it\n3. Toggle ON\n4. Press Allow")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupMethodSelector() {
        // Set current selection
        val current = prefs.getString(FloatingButtonService.PREF_METHOD, FloatingButtonService.METHOD_A)
        when (current) {
            FloatingButtonService.METHOD_A -> binding.radioMethodA.isChecked = true
            FloatingButtonService.METHOD_B -> binding.radioMethodB.isChecked = true
            FloatingButtonService.METHOD_C -> binding.radioMethodC.isChecked = true
        }

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val method = when (checkedId) {
                R.id.radioMethodA -> FloatingButtonService.METHOD_A
                R.id.radioMethodB -> FloatingButtonService.METHOD_B
                R.id.radioMethodC -> FloatingButtonService.METHOD_C
                else -> FloatingButtonService.METHOD_A
            }
            prefs.edit().putString(FloatingButtonService.PREF_METHOD, method).apply()
            Toast.makeText(this, "Method changed! Restart service to apply.", Toast.LENGTH_SHORT).show()

            // Notify running service to refresh
            if (FloatingButtonService.isRunning) {
                startService(Intent(this, FloatingButtonService::class.java))
            }
        }
    }

    private fun setupServiceButtons() {
        binding.btnStartService.setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) ->
                    Toast.makeText(this, "Grant Overlay permission first", Toast.LENGTH_SHORT).show()
                !devicePolicyManager.isAdminActive(adminComponent) ->
                    Toast.makeText(this, "Grant Device Admin first", Toast.LENGTH_SHORT).show()
                !isAccessibilityEnabled() ->
                    Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_SHORT).show()
                else -> {
                    val intent = Intent(this, FloatingButtonService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                    else startService(intent)
                    Toast.makeText(this, "Service started! Open YouTube 🎬", Toast.LENGTH_LONG).show()
                    updateStatus()
                }
            }
        }

        binding.btnStopService.setOnClickListener {
            stopService(Intent(this, FloatingButtonService::class.java))
            Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
            updateStatus()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${YouTubeDetectorService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(service)
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val adminOk = devicePolicyManager.isAdminActive(adminComponent)
        val accessibilityOk = isAccessibilityEnabled()

        binding.statusOverlay.text = if (overlayOk) "✅ Overlay: Granted" else "❌ Overlay: Not Granted"
        binding.statusAdmin.text = if (adminOk) "✅ Device Admin: Active" else "❌ Device Admin: Inactive"
        binding.statusAccessibility.text = if (accessibilityOk) "✅ Accessibility: Enabled" else "❌ Accessibility: Disabled"
        binding.statusService.text = if (FloatingButtonService.isRunning) "🟢 Service: Running" else "⚫ Service: Stopped"

        val allReady = overlayOk && adminOk && accessibilityOk
        binding.btnStartService.isEnabled = allReady
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateStatus()
    }
}
