package com.example.myrfidreader

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.myrfidreader.ui.theme.MyRFIDreaderTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: SerialViewModel by viewModels()
    private val ACTION_USB_PERMISSION = "com.example.myrfidreader.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    viewModel.connectAndRead()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            MyRFIDreaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RfidScreen(viewModel) {
                        requestUsbPermission()
                    }
                }
            }
        }
    }

    private fun requestUsbPermission() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = com.hoho.android.usbserial.driver.UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)

        if (availableDrivers.isEmpty()) {
            viewModel.connectAndRead()
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (usbManager.hasPermission(device)) {
            viewModel.connectAndRead()
        } else {
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val permissionIntent =
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

    fun shareLogFile() {
        val file = File(filesDir, "rfid_logs.txt")
        if (!file.exists() || file.length() == 0L) {
            android.widget.Toast.makeText(this, "Лог пуст", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Отправить лог через..."))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RfidScreen(viewModel: SerialViewModel, onConnectClick: () -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var expandedBaud by remember { mutableStateOf(false) }
    val currentBaud = viewModel.baudRate

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Очистить лог?") },
            text = { Text("Все записи в приложении и в файле rfid_logs.txt будут безвозвратно удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLogs()
                        showDialog = false
                    }
                ) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    val logs = viewModel.rfidData.split("\n").filter { it.isNotBlank() }.reversed()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки программы") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, LauncherActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "На главный экран")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            // Первая строка: подключение и очистка
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onConnectClick,
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Подключиться и читать",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { showDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Очистить файл логов",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Вторая строка: отключение и отправка лога
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.disconnect()
                    },
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Отключиться",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { (context as MainActivity).shareLogFile() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Отправить лог",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Строка с заголовком лога и кнопкой скорости
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Лог сообщений (HEX):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // Кнопка скорости (белая, уменьшенная)
                Box {
                    Button(
                        onClick = { expandedBaud = true },
                        modifier = Modifier
                            .height(32.dp)
                            .widthIn(min = 120.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Скорость: ${baudRateToString(currentBaud)}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expandedBaud,
                        onDismissRequest = { expandedBaud = false }
                    ) {
                        listOf(0, 1, 2, 3, 4).forEach { value ->
                            DropdownMenuItem(
                                text = { Text(baudRateToString(value)) },
                                onClick = {
                                    viewModel.updateBaudRate(value)
                                    expandedBaud = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Лог сообщений
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(logs) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = log,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка во всю ширину для возврата на главный экран
            Button(
                onClick = {
                    // Запускаем LauncherActivity и очищаем стек
                    val intent = Intent(context, LauncherActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("На главный экран")
            }
        }
    }
}

// baudRateToString уже есть в Utils.kt, поэтому отдельно не определяем.