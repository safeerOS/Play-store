package com.aurora.store.data.safeer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.aurora.store.R

/** Izid namestitve Safeer aplikacije; ce sistem zeli potrditev, pokazemo njegovo okno. */
class SafeerNamestitevReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val paket = intent.getStringExtra(SafeerIzdaje.EXTRA_PAKET).orEmpty()
        when (
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            )
        ) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val potrdi: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (potrdi != null) {
                    potrdi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(potrdi)
                        SafeerIzdaje.sporoci(paket, context.getString(R.string.safeer_confirm_install))
                    } catch (e: Exception) {
                        SafeerIzdaje.sporoci(
                            paket,
                            context.getString(R.string.safeer_install_failed_no_confirm)
                        )
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> SafeerIzdaje.namestitevUspela(
                paket,
                context.getString(R.string.safeer_installed)
            )
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Log.w("SafeerIzdaje", "Namestitev $paket: status $status $msg")
                SafeerIzdaje.sporoci(
                    paket,
                    if (status ==
                        PackageInstaller.STATUS_FAILURE_ABORTED
                    ) {
                        context.getString(R.string.safeer_install_cancelled)
                    } else {
                        context.getString(R.string.safeer_install_failed)
                    }
                )
            }
        }
    }
}
