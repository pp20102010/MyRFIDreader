package com.example.myrfidreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myrfidreader.ui.theme.MyRFIDreaderTheme
import java.io.File


class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyRFIDreaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val isConnected by UsbConnectionHolder.isConnected

    var selectedBaudRate by remember { mutableIntStateOf(viewModel.baudRate.value) }
    var rfPower by remember { mutableIntStateOf(viewModel.rfPower.value) }
    var selectedWorkMode by remember { mutableIntStateOf(viewModel.workMode.value) }

    val logLines = viewModel.logData.split("\n").filter { it.isNotBlank() }.reversed()
    val filterTime by viewModel.filterTime.collectAsState()
    val buzzerEnabled by viewModel.buzzerEnabled.collectAsState()

    var selectedFilterTime by remember { mutableIntStateOf(filterTime) }
    var selectedBuzzer by remember { mutableStateOf(buzzerEnabled) }

    LaunchedEffect(filterTime) {
        selectedFilterTime = filterTime
    }
    LaunchedEffect(buzzerEnabled) {
        selectedBuzzer = buzzerEnabled
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Настройки ридера")
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? SettingsActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Ридер не подключён")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(context, MainActivity::class.java))
                            }
                        ) {
                            Text("Подключиться (в настройках программы)")
                        }
                    }
                }
            } else {
                // === Скорость (Baud rate) ===
                var expandedBaud by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedBaud,
                    onExpandedChange = { expandedBaud = it }
                ) {
                    OutlinedTextField(
                        value = baudRateToString(selectedBaudRate),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Скорость") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBaud) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    DropdownMenu(
                        expanded = expandedBaud,
                        onDismissRequest = { expandedBaud = false }
                    ) {
                        listOf(0, 1, 2, 3, 4).forEach { value ->
                            DropdownMenuItem(
                                text = { Text(baudRateToString(value)) },
                                onClick = {
                                    selectedBaudRate = value
                                    expandedBaud = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // === Мощность (RF Power) ===
                Text("Мощность: $rfPower dB")
                Slider(
                    value = rfPower.toFloat(),
                    onValueChange = { rfPower = it.toInt() },
                    valueRange = 0f..30f, // Изменено на 30
                    steps = 29,            // 30 - 0 - 1 = 29 шагов между делениями
                    // ...
                )

                Spacer(modifier = Modifier.height(8.dp))

                // === Режим работы (Work Mode) ===
                var expandedMode by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedMode,
                    onExpandedChange = { expandedMode = it }
                ) {
                    OutlinedTextField(
                        value = workModeToString(selectedWorkMode),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Режим") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    DropdownMenu(
                        expanded = expandedMode,
                        onDismissRequest = { expandedMode = false }
                    ) {
                        listOf(0, 1, 2).forEach { value ->
                            DropdownMenuItem(
                                text = { Text(workModeToString(value)) },
                                onClick = {
                                    selectedWorkMode = value
                                    expandedMode = false
                                }
                            )
                        }
                    }
                }

                // Чекбокс для зуммера
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedBuzzer,
                        onCheckedChange = { selectedBuzzer = it }
                    )
                    Text("Включить зуммер")
                }


                Spacer(modifier = Modifier.height(8.dp))

                // Поле ввода FilterTime
                OutlinedTextField(
                    value = selectedFilterTime.toString(),
                    onValueChange = { newValue ->
                        // Разрешаем временно пустую строку (чтобы можно было стереть всё)
                        if (newValue.isEmpty()) {
                            selectedFilterTime = 0
                            return@OutlinedTextField
                        }
                        // Проверяем, что строка состоит только из цифр и число в допустимом диапазоне
                        val intValue = newValue.toIntOrNull()
                        if (intValue != null && intValue in 0..255) {
                            selectedFilterTime = intValue
                        }
                        // Если введено что-то недопустимое (буквы, спецсимволы), просто игнорируем
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Периодичность считывания (сек)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = selectedFilterTime !in 0..255
                )

                Spacer(modifier = Modifier.height(16.dp))

                // === Кнопки управления в две строки (2x2) ===
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.applySettings(
                                    selectedBaudRate,
                                    rfPower,
                                    selectedWorkMode,
                                    selectedFilterTime,
                                    selectedBuzzer
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Применить")
                        }
                        Button(
                            onClick = { viewModel.readSettings() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Прочитать")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { viewModel.clearLog() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Очистить лог")
                        }
                        Button(
                            onClick = {
                                val logContent = viewModel.logData
                                if (logContent.isBlank() || logContent.lines().size <= 1) {
                                    Toast.makeText(context, "Лог пуст", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                try {
                                    val file = File(context.filesDir, "settings_log.txt")
                                    file.writeText(logContent)
                                    val contentUri: Uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            "Отправить лог настроек через..."
                                        )
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Ошибка: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Отправить лог")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === Заголовок лога ===
                Text(
                    text = "Лог команд и ответов:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // === Лог, занимающий всё оставшееся пространство ===
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(logLines) { line ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = line,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

