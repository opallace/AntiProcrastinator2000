package com.example.anti_procrastinator2000

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "AntiProcrastinator2000"

class BlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BlockReceiver chamado. action=${intent.action}")

        if (intent.action != BlockAction.ACTION_END_BLOCK) return

        val endIntent = Intent(BlockAction.ACTION_END_BLOCK).apply {
            setPackage(context.packageName)
        }

        context.sendBroadcast(endIntent)

        Log.d(TAG, "Broadcast interno de fim enviado")
    }
}
