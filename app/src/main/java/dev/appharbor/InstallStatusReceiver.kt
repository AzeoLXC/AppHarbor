package dev.appharbor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { runCatching { context.startActivity(it) } }
            return
        }
        val success = status == PackageInstaller.STATUS_SUCCESS
        val message = if (success) "Install completed."
        else intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed (status $status)."
        context.sendBroadcast(
            Intent(ACTION_FEEDBACK)
                .setPackage(context.packageName)
                .putExtra(EXTRA_SUCCESS, success)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "dev.appharbor.action.INSTALL_STATUS"
        const val ACTION_FEEDBACK = "dev.appharbor.action.INSTALL_FEEDBACK"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_MESSAGE = "message"
    }
}
