NLSDemoCtr — Newland Demo Controller
====================================

NLSDemoCtr is a minimal Android app (Jetpack Compose + Material 3) to help operate Newland-based devices by broadcasting vendor intents for:

- Running a system update from a fixed OTA path
- Rebooting the device (immediately)
- Scheduling a reboot at a specific date/time
- Performing a factory reset (via broadcast)

It also enhances the app title with the device model and a vendor-specific serial number when available.


Features
--------
- System update broadcast: `nlscan.action.RUN_SYSTEM_UPDATE` with `file_path` extra
- Immediate reboot broadcast: `nlscan.intent.action.DEVICE_REBOOT`
- Factory reset broadcast: `nlscan.intent.action.DEVICE_FACTORY_RESET`
- Reboot scheduler using `AlarmManager` + `BroadcastReceiver`
- Device title shows `Build.MODEL` and S/N (prefers `/sys/bus/platform/devices/newland-misc/SN` when present)


Requirements
------------
- Android Studio Hedgehog+ with Kotlin and Compose
- Android 8.0+ device recommended; exact alarms require Android 12+ settings toggle
- A Newland device or compatible environment that listens to the vendor broadcasts


Build & Run
-----------
1. Open the project in Android Studio.
2. Sync Gradle and build the `app` module.
3. Run on a physical device (recommended, as broadcasts may be vendor-specific).


Usage
-----
- Place `OTA.zip` at `/storage/emulated/0/Documents/OTA.zip` on the device.
- Use the "Update" card to send the system update broadcast.
- Use the "Reboots" card to send immediate reboot or factory reset broadcasts.
- Use the "Schedule" card to pick a date/time and schedule a reboot. On Android 12+ you may need to allow exact alarms: Settings → Apps → Special app access → Alarms & reminders.


Serial Number Detection
-----------------------
The app title attempts to show a serial number:

1. Tries to read `/sys/bus/platform/devices/newland-misc/SN` and uses it if non-blank and not "unknown" (case-insensitive).
2. Falls back to `Build.SERIAL` if the vendor path is unavailable.


Project Structure
-----------------
- `MainActivity.kt`: Compose UI, broadcasts, reboot scheduler, title building with vendor S/N.
- `RebootSchedulerReceiver.kt`: Receives `AlarmManager` trigger and sends the reboot broadcast.
- `ui/theme/*`: Minimal Material 3 theme setup.


Notes
-----
- This app depends on vendor broadcast handlers. Without a compatible ROM, actions may no-op.
- Factory reset and reboot operations are destructive; use with caution.


License
-------
Add your preferred license here (e.g., MIT). If unsure, please confirm and update this section before publishing to GitHub.


Contributing
------------
Issues and pull requests are welcome. Please include device model/firmware details when reporting vendor-broadcast behavior.
