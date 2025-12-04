package com.example.nlsdemoctr

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.nlsdemoctr.ui.theme.NLSDemoCtrTheme
import java.io.File
import java.util.Calendar
import android.os.Build
import android.os.Environment

class MainActivity : ComponentActivity() {
    private var otaExists: Boolean = false
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /**
     * Called when the activity is starting. First we call our super's implementation of
     * this method, and then we enable edge-to-edge mode and check if the OTA.zip file exists.
     * Finally, we set the content of the activity to be a [NLSDemoCtrTheme] with a
     * [FirmwareUpdateScreen] as its content. The [FirmwareUpdateScreen] is a composable function
     * that displays the firmware update screen with buttons to refresh the OTA.zip's existence,
     * update the system with the OTA.zip, reboot the device, factory reset the device, and
     * schedule a reboot for a later time.
     * @param savedInstanceState If the activity is being re-initialized after the activity has been
     * stopped, this Bundle contains the data it supplied in the onSaveInstanceState()
     * method.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        otaExists = checkOtaExists()
        setContent {
            NLSDemoCtrTheme {
                val title = buildDeviceTitle()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text(text = title) })
                    }
                ) { innerPadding ->
                    FirmwareUpdateScreen(
                        modifier = Modifier.padding(innerPadding),
                        initialExists = otaExists,
                        onRefresh = { checkOtaExists() },
                        onUpdateClick = {
                            val pathToSend = findOtaPackageFullPath()
                            if (pathToSend == null) {
                                val msg = "OTA.zip not found. Place it in the Downloads folder."
                                Log.i(TAG, msg)
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                                otaExists = false
                            } else {
                                try {
                                    val updateIntent = Intent(ACTION_RUN_SYSTEM_UPDATE).apply {
                                        putExtra(EXTRA_FILE_PATH, pathToSend)
                                    }
                                    val sent = sendVendorBroadcastSafely(this, updateIntent)
                                    if (sent) {
                                        val msg = "Update broadcast sent with file_path=$pathToSend"
                                        Log.i(TAG, msg)
                                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Log.w(TAG, "No receivers found for system update action")
                                        Toast.makeText(this, "No handler for update on this device", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (t: Throwable) {
                                    val err = "Failed to send update broadcast: ${t.message}"
                                    Log.e(TAG, err, t)
                                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onRebootClick = {
                            try {
                                val sent = sendVendorBroadcastSafely(this, Intent(ACTION_DEVICE_REBOOT))
                                if (sent) {
                                    Log.i(TAG, "Broadcast sent for device reboot")
                                    Toast.makeText(this, "Reboot broadcast sent", Toast.LENGTH_SHORT).show()
                                } else {
                                    Log.w(TAG, "No receivers found for device reboot action")
                                    Toast.makeText(this, "No handler for reboot on this device", Toast.LENGTH_SHORT).show()
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Failed to send reboot broadcast", t)
                                Toast.makeText(this, "Failed to send reboot", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onFactoryResetClick = {
                            try {
                                val sent = sendVendorBroadcastSafely(this, Intent(ACTION_DEVICE_FACTORY_RESET))
                                if (sent) {
                                    Log.i(TAG, "Broadcast sent for factory reset")
                                    Toast.makeText(this, "Factory reset broadcast sent", Toast.LENGTH_SHORT).show()
                                } else {
                                    Log.w(TAG, "No receivers found for factory reset action")
                                    Toast.makeText(this, "No handler for factory reset on this device", Toast.LENGTH_SHORT).show()
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Failed to send factory reset broadcast", t)
                                Toast.makeText(this, "Failed to send factory reset", Toast.LENGTH_SHORT).show()
                            }
                        },
                        scheduledAtMillis = prefs.getLong(KEY_SCHEDULED_AT, 0L),
                        onPickDateTimeAndSchedule = { showPickersAndSchedule() },
                        onCancelScheduled = { cancelScheduledReboot() }
                    )
                }
            }
        }
    }

    /**
     * Called after [onStart] when the activity is restarting from a
     * previous state saved by [onSaveInstanceState]. This method is always
     * called after [onStart], even if the activity is being re-initialized
     * after the activity has been stopped.
     *
     * We call the superclass's implementation of this method, and then check if the
     * OTA.zip file exists.
     *
     * @see android.app.Activity.onResume
     */
    override fun onResume() {
        super.onResume()
        otaExists = checkOtaExists()
    }

    /**
     * Checks if the OTA.zip file exists at the fixed path.
     *
     * @return true if the OTA.zip file exists at the fixed path, false otherwise
     */
    private fun checkOtaExists(): Boolean = findOtaPackageFullPath() != null

    /**
     * Finds the full path to OTA.zip. Prefers public Downloads, falls back to legacy Documents path.
     */
    private fun findOtaPackageFullPath(): String? {
        // Preferred: Downloads/OTA.zip
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val preferred = File(downloadsDir, OTA_FILE_NAME)
        if (preferred.exists()) return preferred.absolutePath

        // Legacy fallback: Documents/OTA.zip used on older builds
        val legacy = File(FIXED_OTA_PATH)
        return if (legacy.exists()) legacy.absolutePath else null
    }

    /**
     * Shows a date picker dialog and then a time picker dialog to allow the user
     * to pick a future time to schedule a reboot. The date picker dialog shows
     * the current year, month, and day as the initial date. The time picker dialog
     * shows the current hour and minute as the initial time. If the user picks a
     * time that is not in the future (i.e. within the next 5 seconds), a toast will be
     * shown to ask the user to pick a future time. Otherwise, the picked time will be
     * scheduled for a reboot.
     */
    private fun showPickersAndSchedule() {
        val now = Calendar.getInstance()
        val initialYear = now.get(Calendar.YEAR)
        val initialMonth = now.get(Calendar.MONTH)
        val initialDay = now.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val timeNow = Calendar.getInstance()
            val initHour = timeNow.get(Calendar.HOUR_OF_DAY)
            val initMinute = timeNow.get(Calendar.MINUTE)

            TimePickerDialog(this, { _, hourOfDay, minute ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val target = cal.timeInMillis
                val nowMs = System.currentTimeMillis()
                if (target <= nowMs + 5_000) {
                    Toast.makeText(this, "Please pick a future time", Toast.LENGTH_SHORT).show()
                } else {
                    scheduleRebootAt(target)
                }
            }, initHour, initMinute, true).show()
        }, initialYear, initialMonth, initialDay).show()
    }

    /**
     * Schedules a reboot to occur at the given time in milliseconds.
     *
     * @param whenMillis the time in milliseconds when the reboot should occur
     */
    private fun scheduleRebootAt(whenMillis: Long) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, RebootSchedulerReceiver::class.java).apply {
            action = ACTION_SCHEDULED_REBOOT
        }
        val pi = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val usedAlarmClockFallback: Boolean
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // Fallback path: use AlarmClock which is allowed without SCHEDULE_EXACT_ALARM
                val showIntent = PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val acInfo = AlarmManager.AlarmClockInfo(whenMillis, showIntent)
                am.setAlarmClock(acInfo, pi)
                usedAlarmClockFallback = true
                Toast.makeText(this, "Scheduled via Alarm Clock fallback (enable exact alarms for quieter scheduling)", Toast.LENGTH_LONG).show()
                Log.w(TAG, "Exact alarms not allowed; using setAlarmClock fallback for time=$whenMillis")
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
                usedAlarmClockFallback = false
                Log.i(TAG, "Using setExactAndAllowWhileIdle for time=$whenMillis")
            }

            prefs.edit().putLong(KEY_SCHEDULED_AT, whenMillis).apply()
            if (!usedAlarmClockFallback) {
                Toast.makeText(this, "Reboot scheduled", Toast.LENGTH_SHORT).show()
            }
            Log.i(TAG, "Reboot scheduled at $whenMillis (fallback=$usedAlarmClockFallback)")
            recreate()
        } catch (se: SecurityException) {
            Log.e(TAG, "Missing permission to schedule exact alarms on Android 12+", se)
            Toast.makeText(this, "No permission for exact alarms on this device", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to schedule reboot", t)
            Toast.makeText(this, "Failed to schedule reboot", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Cancels a previously scheduled reboot.
     *
     * This function cancels the scheduled reboot by removing the scheduled alarm and
     * removing the scheduled time from the shared preferences. If the operation is
     * successful, a toast is shown and the log is written. If the operation fails,
     * an error toast is shown and the error is logged.
     */
    private fun cancelScheduledReboot() {
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, RebootSchedulerReceiver::class.java).apply {
                action = ACTION_SCHEDULED_REBOOT
            }
            val pi = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            prefs.edit().remove(KEY_SCHEDULED_AT).apply()
            Toast.makeText(this, "Scheduled reboot canceled", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Scheduled reboot canceled")
            recreate()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to cancel scheduled reboot", t)
            Toast.makeText(this, "Failed to cancel scheduled reboot", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "NLSUpdateUI"
        private const val ACTION_RUN_SYSTEM_UPDATE = "nlscan.action.RUN_SYSTEM_UPDATE"
        private const val ACTION_DEVICE_REBOOT = "nlscan.intent.action.DEVICE_REBOOT"
        private const val ACTION_DEVICE_FACTORY_RESET = "nlscan.intent.action.DEVICE_FACTORY_RESET"
        private const val ACTION_SCHEDULED_REBOOT = "com.example.nlsdemoctr.ACTION_SCHEDULED_REBOOT"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val OTA_FILE_NAME = "OTA.zip"
        private const val FIXED_OTA_PATH = "/storage/emulated/0/Documents/OTA.zip"
        private const val PREFS_NAME = "reboot_scheduler"
        private const val KEY_SCHEDULED_AT = "scheduled_at"
    }
}

/**
 * Attempts to deliver a vendor broadcast on modern Android by preferring explicit broadcasts.
 * Strategy:
 * 1) Try implicit broadcast (for older ROMs still allowing it).
 * 2) Query receivers for the action and send an explicit broadcast to each resolved component.
 * Returns true if at least one send was attempted.
 */
fun sendVendorBroadcastSafely(context: Context, intent: Intent): Boolean {
    var attempted = false
    val action = intent.action
    try {
        // Try legacy implicit broadcast
        context.applicationContext.sendBroadcast(intent)
        attempted = true
    } catch (_: Throwable) {
    }

    return try {
        val pm = context.packageManager
        val receivers = pm.queryBroadcastReceivers(intent, 0)
        if (!receivers.isNullOrEmpty()) {
            receivers.forEach { ri ->
                val pkg = ri.activityInfo?.packageName
                val name = ri.activityInfo?.name
                if (!pkg.isNullOrBlank() && !name.isNullOrBlank()) {
                    val explicit = Intent(intent) // Clone the original intent
                    explicit.setClassName(pkg, name)
                    try {
                        Log.i("NLSBcast", "Sending explicit broadcast to $pkg/$name for action=$action")
                        context.applicationContext.sendBroadcast(explicit)
                        attempted = true
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        attempted
    } catch (_: Throwable) {
        attempted
    }
}


/**
 * A composable function that displays a screen for firmware updates and reboots.
 *
 * This function displays three cards: one for reboots, one for updates, and one for scheduling reboots.
 * The reboots card displays two buttons: one for rebooting the device and one for factory resetting the device.
 * The updates card displays a text describing the existence of the OTA.zip file, a button to refresh the existence of the OTA.zip file,
 * and a button to run the system update with the OTA.zip file.
 * The schedule card displays a text describing the scheduled reboot time, a button to reschedule the reboot, and a button to cancel the scheduled reboot.
 *
 * @param modifier The modifier to be applied to the composable function.
 * @param initialExists Whether the OTA.zip file exists at the fixed path.
 * @param onRefresh A function to be called when the refresh button is clicked. The function should return true if the OTA.zip file exists at the fixed path, false otherwise.
 * @param onUpdateClick A function to be called when the update button is clicked. The function should take the path of the OTA.zip file as a parameter.
 * @param onRebootClick A function to be called when the reboot button is clicked.
 * @param onFactoryResetClick A function to be called when the factory reset button is clicked.
 * @param scheduledAtMillis The scheduled time of the reboot in milliseconds.
 * @param onPickDateTimeAndSchedule A function to be called when the schedule button is clicked.
 * @param onCancelScheduled A function to be called when the cancel scheduled button is clicked.
 */
@Composable
fun FirmwareUpdateScreen(
    modifier: Modifier = Modifier,
    initialExists: Boolean,
    onRefresh: () -> Boolean,
    onUpdateClick: () -> Unit,
    onRebootClick: () -> Unit,
    onFactoryResetClick: () -> Unit,
    scheduledAtMillis: Long,
    onPickDateTimeAndSchedule: () -> Unit,
    onCancelScheduled: () -> Unit,
) {
    var exists by remember { mutableStateOf(initialExists) }
    var updateSent by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Reboots", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onRebootClick) { Text(text = "Reboot Device") }
                Button(onClick = onFactoryResetClick) { Text(text = "Factory Reset Device") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Update", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        updateSent -> "Upgrade Broadcast Sent"
                        exists -> "OTA.zip found"
                        else -> "OTA.zip not found. Place it in Downloads"
                    }
                )
                Button(
                    enabled = exists && !updateSent,
                    onClick = {
                        updateSent = true
                        onUpdateClick()
                    }
                ) { Text(text = "Run System Update") }
                Button(onClick = { exists = onRefresh() }) { Text(text = "Refresh") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Schedule", style = MaterialTheme.typography.titleMedium)
                val hasSchedule = scheduledAtMillis > 0
                val scheduleText = if (hasSchedule) {
                    "Scheduled reboot at: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(scheduledAtMillis))
                } else {
                    "No reboot scheduled"
                }
                Text(text = scheduleText)
                Button(onClick = onPickDateTimeAndSchedule) {
                    Text(text = if (hasSchedule) "Reschedule Reboot" else "Schedule Reboot")
                }
                if (hasSchedule) {
                    Button(onClick = onCancelScheduled) {
                        Text(text = "Cancel Scheduled Reboot")
                    }
                }
            }
        }

        
    }
}

@Preview(showBackground = true)
@Composable
fun FirmwareUpdatePreview() {
    NLSDemoCtrTheme {
        FirmwareUpdateScreen(
            initialExists = true,
            onRefresh = { true },
            onUpdateClick = {},
            onRebootClick = {},
            onFactoryResetClick = {},
            scheduledAtMillis = 0L,
            onPickDateTimeAndSchedule = {},
            onCancelScheduled = {}
        )
    }
}

private fun readFirstLine(path: String): String? = try {
    File(path).useLines { seq -> seq.firstOrNull() }
} catch (_: Throwable) {
    null
}

private fun buildDeviceTitle(): String {
    val model = Build.MODEL ?: "Unknown Model"

    // 0) Newland vendor path
    val vendorSerial = readFirstLine("/sys/bus/platform/devices/newland-misc/SN")?.let { v ->
        if (v.isNotBlank() && !v.equals("unknown", ignoreCase = true)) v.trim() else null
    }

    val fallbackSerial = try {
        Build.SERIAL
    } catch (_: Throwable) {
        "UNKNOWN"
    }

    val serial = vendorSerial ?: fallbackSerial
    val serialPart = if (serial.isNullOrBlank() || serial.equals("UNKNOWN", ignoreCase = true)) "S/N: Unknown" else "$serial"
    return "$model • $serialPart"
}