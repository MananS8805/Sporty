package com.sportshub.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.sportshub.app.notify.Background
import com.sportshub.app.notify.Notifier

/**
 * Returns a function that asks for the notification permission when it is needed (Android 13+) and then
 * schedules reminders. Safe to call repeatedly: it does nothing once granted.
 */
@Composable
fun rememberNotificationPermission(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Background.checkNow(context)
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canPost(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Background.checkNow(context)
        }
    }
}
