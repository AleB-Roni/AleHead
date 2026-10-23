package de.roni.alehead

import android.app.admin.DeviceAdminReceiver

/**
 * Optional: Wenn AleHead als Geräteadministrator aktiv ist, darf es das Display
 * nach der Fahrt sofort ausschalten (DevicePolicyManager.lockNow).
 * Freischalten per adb:  adb shell dpm set-active-admin de.roni.alehead/.AdminReceiver
 * Einziges Recht: Display sperren/ausschalten ("force-lock").
 */
class AdminReceiver : DeviceAdminReceiver()
