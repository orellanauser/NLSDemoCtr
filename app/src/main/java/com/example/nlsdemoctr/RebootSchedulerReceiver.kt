package com.example.nlsdemoctr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class RebootSchedulerReceiver : BroadcastReceiver() {
    /**
     * Called when the BroadcastReceiver is receiving an Intent broadcast.
     * This method will be called when a scheduled reboot is triggered.
     * It will send a broadcast with the ACTION_DEVICE_REBOOT action to trigger the reboot.
     * If the broadcast is successful, it will log a message and show a Toast.
     * If the broadcast fails, it will log an error message.
     * Finally, it will attempt to remove the scheduled reboot from SharedPreferences.
     * If the removal fails, it will log an error message.
     * @param context the Context in which the receiver is running.
     * @param intent the Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val sent = sendVendorBroadcastSafely(context, Intent(ACTION_DEVICE_REBOOT))
            if (sent) {
                Log.i(TAG, "Scheduled reboot broadcast sent")
                Toast.makeText(context, "Scheduled reboot broadcast sent", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "No receivers found for scheduled reboot action")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to send scheduled reboot", t)
        } finally {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove(KEY_SCHEDULED_AT).apply()
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        private const val TAG = "NLSRebootSched"
        private const val ACTION_DEVICE_REBOOT = "nlscan.intent.action.DEVICE_REBOOT"
        private const val PREFS_NAME = "reboot_scheduler"
        private const val KEY_SCHEDULED_AT = "scheduled_at"
    }
}
