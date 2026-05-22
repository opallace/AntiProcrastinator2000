package com.example.anti_procrastinator2000

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BlockSession(
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long
)

object BlockHistoryRepository {
    private const val PREFS_NAME = "block_history"
    private const val KEY_TOTAL_SAVED_MILLIS = "total_saved_millis"
    private const val KEY_COMPLETED_SESSIONS = "completed_sessions"
    private const val KEY_HISTORY_JSON = "history_json"

    fun addCompletedSession(
        context: Context,
        startMillis: Long,
        endMillis: Long
    ) {
        if (startMillis <= 0L || endMillis <= startMillis) {
            return
        }

        val durationMillis = endMillis - startMillis

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val currentTotal = prefs.getLong(KEY_TOTAL_SAVED_MILLIS, 0L)
        val currentCount = prefs.getInt(KEY_COMPLETED_SESSIONS, 0)

        val history = JSONArray(
            prefs.getString(KEY_HISTORY_JSON, "[]")
        )

        val sessionJson = JSONObject().apply {
            put("startMillis", startMillis)
            put("endMillis", endMillis)
            put("durationMillis", durationMillis)
        }

        history.put(sessionJson)

        prefs.edit()
            .putLong(KEY_TOTAL_SAVED_MILLIS, currentTotal + durationMillis)
            .putInt(KEY_COMPLETED_SESSIONS, currentCount + 1)
            .putString(KEY_HISTORY_JSON, history.toString())
            .apply()
    }

    fun getTotalSavedMillis(context: Context): Long {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_TOTAL_SAVED_MILLIS, 0L)
    }

    fun getCompletedSessionsCount(context: Context): Int {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_COMPLETED_SESSIONS, 0)
    }

    fun getLastSession(context: Context): BlockSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val history = JSONArray(
            prefs.getString(KEY_HISTORY_JSON, "[]")
        )

        if (history.length() == 0) {
            return null
        }

        val last = history.getJSONObject(history.length() - 1)

        return BlockSession(
            startMillis = last.getLong("startMillis"),
            endMillis = last.getLong("endMillis"),
            durationMillis = last.getLong("durationMillis")
        )
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}