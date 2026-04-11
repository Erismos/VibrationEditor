package com.example.vibrationeditor.ui.screens.shared

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Presentation model for an installed launchable application. */
data class AppListItem(val name: String, val packageName: String, val icon: Drawable)

/**
 * Loads installed launchable apps on a background dispatcher.
 *
 * @param context Context used to query [PackageManager].
 * @return Alphabetically sorted list of launchable applications.
 */
suspend fun getInstalledApps(context: Context): List<AppListItem> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    packages.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        .map {
            AppListItem(
                name = it.loadLabel(pm).toString(),
                packageName = it.packageName,
                icon = it.loadIcon(pm)
            )
        }
        .sortedBy { it.name.lowercase() }
}

/**
 * Returns whether this app is currently enabled as a notification listener.
 *
 * @param context Context used to read secure settings.
 */
fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!TextUtils.isEmpty(flat)) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && TextUtils.equals(pkgName, cn.packageName)) return true
        }
    }
    return false
}

/**
 * One row in the applications list.
 *
 * @param app App displayed by the row.
 * @param onClick Called when the row is tapped.
 */
@Composable
fun AppRow(app: AppListItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = app.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
