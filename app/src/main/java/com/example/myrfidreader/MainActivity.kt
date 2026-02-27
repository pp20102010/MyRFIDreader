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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myrfidreader.ui.theme.MyRFIDreaderTheme
import java.io.File

class MainActivity : ComponentActivity() {
    // проект загружен на пgithab 24.02.2026 https://github.com/pp20102010/MyRFIDreader

    private val viewModel: SerialViewModel by viewModels()
    private val ACTION_USB_PERMISSION = "com.example.myrfidreader.USB_PERMISSION"

    // Приемник ответа от системы: разрешил ли пользователь доступ к USB
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    // Если разрешение получено — запускаем чтение
                    viewModel.connectAndRead()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Регистрируем ресивер один раз при запуске приложения
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
            viewModel.connectAndRead() // Там выведется ошибка "Устройство не найдено"
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (usbManager.hasPermission(device)) {
            // Если разрешение уже есть — сразу читаем
            viewModel.connectAndRead()
        } else {
            // Если разрешения нет — запрашиваем его у пользователя
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val permissionIntent =
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    // Удаляем ресивер при закрытии приложения, чтобы не было утечек
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

    //функция поделиться файлом лога
    fun shareLogFile() {
        val file = File(filesDir, "rfid_logs.txt")
        if (!file.exists() || file.length() == 0L) {
            android.widget.Toast.makeText(this, "Лог пуст", android.widget.Toast.LENGTH_SHORT)
                .show()
            return
        }

        try {
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // ВАЖНО: передаем shareIntent
            startActivity(Intent.createChooser(shareIntent, "Отправить лог через..."))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                this,
                "Ошибка: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RfidScreen(viewModel: SerialViewModel, onConnectClick: () -> Unit) {
    val context = LocalContext.current

    // Состояние для показа диалога
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Очистить лог?") },
            text = { Text("Все записи в приложении и в файле rfid_logs.txt будут безвозвратно удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLogs() // Вызываем очистку
                        showDialog = false    // Закрываем окно
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


    // Получаем список строк, новые сверху
    val logs = viewModel.rfidData.split("\n").filter { it.isNotBlank() }.reversed()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        // Иконка рядом с текстом (используем стандартную или вашу ic_rfid_chip)
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Nfc,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("RFID Reader 2026")
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

            // Кнопки управления
// Кнопки управления
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp) // Уменьшаем зазор между кнопками
            ) {
                // Кнопка подключения
                Button(
                    onClick = onConnectClick,
                    modifier = Modifier.weight(1.2f), // Даем больше места для длинного текста
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Подключиться и читать",
                        style = MaterialTheme.typography.labelSmall, // Используем мелкий шрифт
                        maxLines = 1
                    )
                }

                // Кнопка очистки (Красная)
                Button(
                    onClick = { showDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Очистить файл логов",
                        style = MaterialTheme.typography.labelSmall, // Используем мелкий шрифт
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка "Поделиться файлом"
            Button(
                onClick = { (context as MainActivity).shareLogFile() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Отправить лог (.txt)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Лог сообщений (HEX):",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
        }
    }

}


