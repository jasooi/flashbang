package com.flashbang.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Small, testable predicates + intent factories for the two permission flows
 * (FR-010b, FR-011). Compose UI over these lives in MainActivity for Phase 1;
 * Phase 5/7 give them real homes in Settings and onboarding.
 */
object Permissions {

    /** POST_NOTIFICATIONS is only a runtime permission on API 33+. */
    fun notificationPermissionRequired(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= Build.VERSION_CODES.TIRAMISU

    fun notificationPermissionGranted(context: Context): Boolean =
        !notificationPermissionRequired() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * FR-010b: warn at alarm creation when notifications are denied — the alarm
     * still rings (audio + full-screen intent), but loses the re-entry notification.
     */
    fun shouldWarnAboutNotifications(context: Context): Boolean =
        !notificationPermissionGranted(context)

    fun overlayPermissionGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
}
