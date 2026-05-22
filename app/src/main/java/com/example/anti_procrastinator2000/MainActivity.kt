package com.example.anti_procrastinator2000

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val TAG = "AntiProcrastinator2000"

private const val BLOCK_STATE_PREFS = "block_state"
private const val KEY_START_MILLIS = "start_millis"
private const val KEY_END_MILLIS = "end_millis"
private const val KEY_PREVIOUS_HOME_PACKAGE = "previous_home_package"
private const val KEY_PREVIOUS_HOME_CLASS = "previous_home_class"

private const val END_ALARM_REQUEST_CODE = 200

data class HistoryStats(
    val totalSavedMillis: Long = 0L,
    val completedSessions: Int = 0
)

class MainActivity : ComponentActivity() {

    private var historyStats by mutableStateOf(HistoryStats())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        clearHomeIfThereIsNoActiveBlock()
        resumeActiveBlockIfNeeded()
        refreshHistoryStats()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        historyStats = historyStats,
                        onStartBlockNow = { durationMillis ->
                            startBlockNow(durationMillis)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHistoryStats()
    }

    private fun refreshHistoryStats() {
        historyStats = HistoryStats(
            totalSavedMillis = BlockHistoryRepository.getTotalSavedMillis(this),
            completedSessions = BlockHistoryRepository.getCompletedSessionsCount(this)
        )
    }

    private fun startBlockNow(durationMillis: Long) {
        val startMillis = System.currentTimeMillis()
        val endMillis = startMillis + durationMillis

        Log.d(TAG, "Iniciando bloqueio. durationMillis=$durationMillis")

        val alarmScheduled = scheduleEndAlarm(endMillis)
        if (!alarmScheduled) {
            Log.d(TAG, "Bloqueio cancelado porque o alarme não foi agendado")
            return
        }

        saveBlockTimeRange(startMillis, endMillis)
        setAntiProcrastinator2000AsHome()
        openBlockActivity(startMillis, endMillis)
    }

    private fun resumeActiveBlockIfNeeded() {
        val startMillis = getSavedBlockStartTime()
        val endMillis = getSavedBlockEndTime()

        if (endMillis <= 0L) {
            return
        }

        val now = System.currentTimeMillis()

        if (now >= endMillis) {
            Log.d(TAG, "Bloqueio salvo já expirou. Limpando estado.")
            clearBlockState()
            clearAntiProcrastinator2000Home()
            return
        }

        Log.d(TAG, "Bloqueio ativo detectado. Retomando BlockActivity.")

        scheduleEndAlarm(endMillis)
        openBlockActivity(startMillis, endMillis)
    }

    private fun openBlockActivity(startMillis: Long, endMillis: Long) {
        val intent = Intent(this, BlockActivity::class.java).apply {
            putExtra(BlockAction.EXTRA_START_TIME_MILLIS, startMillis)
            putExtra(BlockAction.EXTRA_END_TIME_MILLIS, endMillis)
        }

        startActivity(intent)
    }

    private fun scheduleEndAlarm(endMillis: Long): Boolean {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        Log.d(TAG, "canScheduleExactAlarms=${alarmManager.canScheduleExactAlarms()}")

        if (!alarmManager.canScheduleExactAlarms()) {
            Log.d(TAG, "Sem permissão para alarmes exatos")
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return false
        }

        val intent = Intent(this, BlockReceiver::class.java).apply {
            action = BlockAction.ACTION_END_BLOCK
            putExtra(BlockAction.EXTRA_END_TIME_MILLIS, endMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            END_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val secondsUntilEnd = (endMillis - System.currentTimeMillis()) / 1000L

        Log.d(TAG, "Agendando fim para endMillis=$endMillis")
        Log.d(TAG, "Fim em aproximadamente $secondsUntilEnd segundos")

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endMillis,
            pendingIntent
        )

        Log.d(TAG, "Alarme de fim agendado")

        return true
    }

    private fun setAntiProcrastinator2000AsHome() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(packageName)) {
            Log.d(TAG, "App não é Device Owner. HOME não foi alterado.")
            return
        }

        saveCurrentHomeActivity()

        val filter = createHomeIntentFilter()
        val antiProcrastinatorHome = ComponentName(this, MainActivity::class.java)

        dpm.addPersistentPreferredActivity(
            admin,
            filter,
            antiProcrastinatorHome
        )

        Log.d(TAG, "AntiProcrastinator2000 definido como HOME durante bloqueio")
    }

    private fun saveCurrentHomeActivity() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val resolvedHome = packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo ?: return

        if (resolvedHome.packageName == packageName) {
            Log.d(TAG, "HOME atual já é o AntiProcrastinator2000. Mantendo HOME anterior salvo.")
            return
        }

        getSharedPreferences(BLOCK_STATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREVIOUS_HOME_PACKAGE, resolvedHome.packageName)
            .putString(KEY_PREVIOUS_HOME_CLASS, resolvedHome.name)
            .apply()

        Log.d(TAG, "HOME anterior salvo: ${resolvedHome.packageName}/${resolvedHome.name}")
    }

    private fun clearHomeIfThereIsNoActiveBlock() {
        val endMillis = getSavedBlockEndTime()
        val hasActiveBlock = endMillis > System.currentTimeMillis()

        if (!hasActiveBlock) {
            clearAntiProcrastinator2000Home()
        }
    }

    private fun clearAntiProcrastinator2000Home() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(packageName)) {
            Log.d(TAG, "App não é Device Owner. HOME não foi limpo.")
            return
        }

        dpm.clearPackagePersistentPreferredActivities(
            admin,
            packageName
        )

        Log.d(TAG, "Preferências persistentes do AntiProcrastinator2000 limpas")

        restorePreviousHomeActivity(dpm, admin)
    }

    private fun restorePreviousHomeActivity(
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        val prefs = getSharedPreferences(BLOCK_STATE_PREFS, Context.MODE_PRIVATE)

        val previousHomePackage = prefs.getString(KEY_PREVIOUS_HOME_PACKAGE, null)
        val previousHomeClass = prefs.getString(KEY_PREVIOUS_HOME_CLASS, null)

        if (previousHomePackage.isNullOrBlank() || previousHomeClass.isNullOrBlank()) {
            Log.d(TAG, "Nenhum HOME anterior salvo para restaurar")
            return
        }

        try {
            val previousHome = ComponentName(
                previousHomePackage,
                previousHomeClass
            )

            dpm.addPersistentPreferredActivity(
                admin,
                createHomeIntentFilter(),
                previousHome
            )

            Log.d(TAG, "HOME anterior restaurado: $previousHomePackage/$previousHomeClass")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao restaurar HOME anterior", e)
        }
    }

    private fun createHomeIntentFilter(): IntentFilter {
        return IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
    }

    private fun saveBlockTimeRange(startMillis: Long, endMillis: Long) {
        getSharedPreferences(BLOCK_STATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_START_MILLIS, startMillis)
            .putLong(KEY_END_MILLIS, endMillis)
            .apply()
    }

    private fun getSavedBlockStartTime(): Long {
        return getSharedPreferences(BLOCK_STATE_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_START_MILLIS, -1L)
    }

    private fun getSavedBlockEndTime(): Long {
        return getSharedPreferences(BLOCK_STATE_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_END_MILLIS, -1L)
    }

    private fun clearBlockState() {
        getSharedPreferences(BLOCK_STATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_START_MILLIS)
            .remove(KEY_END_MILLIS)
            .remove(KEY_PREVIOUS_HOME_PACKAGE)
            .remove(KEY_PREVIOUS_HOME_CLASS)
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    historyStats: HistoryStats,
    onStartBlockNow: (Long) -> Unit
) {
    var selectedHours by remember { mutableIntStateOf(0) }
    var selectedMinutes by remember { mutableIntStateOf(30) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val durationText = formatDuration(
        hours = selectedHours,
        minutes = selectedMinutes
    )

    val durationMillis =
        (selectedHours * 60L + selectedMinutes) * 60L * 1000L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Anti-Procrastinator 2000",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Bloqueie o celular e recupere seu foco.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        StatsCard(
            totalSavedMillis = historyStats.totalSavedMillis,
            completedSessions = historyStats.completedSessions
        )

        Spacer(modifier = Modifier.height(20.dp))

        DurationCard(
            durationText = durationText,
            onClick = {
                showDurationPicker = true
                message = ""
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (durationMillis <= 0L) {
                    message = "Selecione uma duração maior que zero."
                } else {
                    onStartBlockNow(durationMillis)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Bloquear agora",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showDurationPicker) {
        DurationTimePickerDialog(
            initialHour = selectedHours,
            initialMinute = selectedMinutes,
            onDismiss = {
                showDurationPicker = false
            },
            onConfirm = { hour, minute ->
                selectedHours = hour
                selectedMinutes = minute
                showDurationPicker = false
            }
        )
    }
}

@Composable
fun StatsCard(
    totalSavedMillis: Long,
    completedSessions: Int
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Tempo economizado",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formatSavedTime(totalSavedMillis),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (completedSessions) {
                    0 -> "Nenhum bloqueio concluído ainda"
                    1 -> "1 bloqueio concluído"
                    else -> "$completedSessions bloqueios concluídos"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DurationCard(
    durationText: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Tempo de bloqueio",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = durationText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Alterar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun formatDuration(hours: Int, minutes: Int): String {
    return when {
        hours == 0 && minutes == 0 -> "Selecionar duração"
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours h"
        else -> "$hours h $minutes min"
    }
}

fun formatSavedTime(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L

    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}min"
        else -> "0min"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Selecionar duração")
        },
        text = {
            TimePicker(
                state = timePickerState
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        timePickerState.hour,
                        timePickerState.minute
                    )
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancelar")
            }
        }
    )
}