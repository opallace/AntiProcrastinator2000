package com.example.anti_procrastinator2000

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private const val TAG = "AntiProcrastinator2000"
private const val BLOCK_STATE_PREFS = "block_state"
private const val KEY_END_MILLIS = "end_millis"
private const val KEY_PREVIOUS_HOME_PACKAGE = "previous_home_package"
private const val KEY_PREVIOUS_HOME_CLASS = "previous_home_class"

class BlockActivity : ComponentActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private var alreadyStopping = false

    private val endBlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BlockAction.ACTION_END_BLOCK) {
                Log.d(TAG, "BlockActivity recebeu ACTION_END_BLOCK")
                stopBlocking()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        adminComponent = ComponentName(
            this,
            MyDeviceAdminReceiver::class.java
        )

        registerEndBlockReceiver()

        if (intent?.action == BlockAction.ACTION_END_BLOCK) {
            stopBlocking()
            return
        }

        val endTimeMillis = intent.getLongExtra(
            BlockAction.EXTRA_END_TIME_MILLIS,
            -1L
        )

        if (endTimeMillis <= System.currentTimeMillis()) {
            stopBlocking()
            return
        }

        startBlocking()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BlockScreen(
                        endTimeMillis = endTimeMillis,
                        onTimeFinished = {
                            Log.d(TAG, "Cronômetro chegou ao fim")
                            stopBlocking()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == BlockAction.ACTION_END_BLOCK) {
            stopBlocking()
        }
    }

    override fun onBackPressed() {
        // Intencionalmente vazio: impede voltar enquanto o bloqueio está ativo.
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(endBlockReceiver)
        } catch (_: Exception) {
            // Receiver já removido ou Activity encerrada antes do registro completar.
        }

        super.onDestroy()
    }

    private fun registerEndBlockReceiver() {
        val filter = IntentFilter(BlockAction.ACTION_END_BLOCK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                endBlockReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(endBlockReceiver, filter)
        }
    }

    private fun startBlocking() {
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            devicePolicyManager.setLockTaskPackages(
                adminComponent,
                arrayOf(packageName)
            )

            devicePolicyManager.setLockTaskFeatures(
                adminComponent,
                DevicePolicyManager.LOCK_TASK_FEATURE_NONE
            )

            Log.d(TAG, "Iniciando Lock Task como Device Owner")
            startLockTask()
        } else {
            Log.d(TAG, "Iniciando Lock Task sem Device Owner")
            startLockTask()
        }
    }

    private fun stopBlocking() {
        if (alreadyStopping) return
        alreadyStopping = true

        Log.d(TAG, "Tentando parar Lock Task")

        try {
            stopLockTask()
            Log.d(TAG, "Lock Task parado com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar Lock Task", e)
        }

        clearAntiProcrastinator2000AsHome()
        clearBlockState()
        openMainActivity()
        finish()
    }

    private fun clearAntiProcrastinator2000AsHome() {
        if (!devicePolicyManager.isDeviceOwnerApp(packageName)) {
            Log.d(TAG, "Não é Device Owner. Não limpou HOME.")
            return
        }

        devicePolicyManager.clearPackagePersistentPreferredActivities(
            adminComponent,
            packageName
        )

        Log.d(TAG, "Preferências persistentes do AntiProcrastinator2000 limpas")

        restorePreviousHomeActivity()
    }

    private fun restorePreviousHomeActivity() {
        val prefs = getSharedPreferences(BLOCK_STATE_PREFS, Context.MODE_PRIVATE)
        val previousHomePackage = prefs.getString(KEY_PREVIOUS_HOME_PACKAGE, null)
        val previousHomeClass = prefs.getString(KEY_PREVIOUS_HOME_CLASS, null)

        if (previousHomePackage.isNullOrBlank() || previousHomeClass.isNullOrBlank()) {
            Log.d(TAG, "Nenhum HOME anterior salvo para restaurar")
            return
        }

        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        val previousHomeComponent = ComponentName(
            previousHomePackage,
            previousHomeClass
        )

        try {
            devicePolicyManager.addPersistentPreferredActivity(
                adminComponent,
                filter,
                previousHomeComponent
            )

            Log.d(TAG, "HOME anterior restaurado: $previousHomePackage/$previousHomeClass")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao restaurar HOME anterior", e)
        }
    }

    private fun clearBlockState() {
        getSharedPreferences(BLOCK_STATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_END_MILLIS)
            .remove(KEY_PREVIOUS_HOME_PACKAGE)
            .remove(KEY_PREVIOUS_HOME_CLASS)
            .apply()
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        startActivity(intent)
    }
}

@Composable
fun BlockScreen(
    endTimeMillis: Long,
    onTimeFinished: () -> Unit
) {
    var remainingMillis by remember {
        mutableLongStateOf(
            max(0L, endTimeMillis - System.currentTimeMillis())
        )
    }

    LaunchedEffect(endTimeMillis) {
        while (true) {
            val remaining = max(0L, endTimeMillis - System.currentTimeMillis())
            remainingMillis = remaining

            if (remaining <= 0L) {
                onTimeFinished()
                break
            }

            delay(1000L)
        }
    }

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    val endText = if (endTimeMillis > 0L) {
        Instant
            .ofEpochMilli(endTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter)
    } else {
        "horário definido"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Celular bloqueado",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Respire. O bloqueio termina em:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = formatRemainingTime(remainingMillis),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Liberação: $endText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatRemainingTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return when {
        hours > 0 -> "%02d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%02d:%02d".format(minutes, seconds)
    }
}
