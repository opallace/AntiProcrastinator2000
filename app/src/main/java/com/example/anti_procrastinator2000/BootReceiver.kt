package com.example.anti_procrastinator2000

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "AntiProcrastinator2000"
private const val BLOCK_STATE_PREFS = "block_state"
private const val KEY_END_MILLIS = "end_millis"

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "BootReceiver chamado")

        val prefs = context.getSharedPreferences(BLOCK_STATE_PREFS, Context.MODE_PRIVATE)
        val endMillis = prefs.getLong(KEY_END_MILLIS, -1L)

        if (endMillis <= 0L) {
            Log.d(TAG, "Nenhum bloqueio ativo salvo")
            return
        }

        if (System.currentTimeMillis() >= endMillis) {
            Log.d(TAG, "Bloqueio expirado durante reboot")
            prefs.edit().remove(KEY_END_MILLIS).apply()
            return
        }

        Log.d(TAG, "Bloqueio ainda ativo após reboot. Abrindo MainActivity")

        val intentToMain = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        context.startActivity(intentToMain)
    }
}
