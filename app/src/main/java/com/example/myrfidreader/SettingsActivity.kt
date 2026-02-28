package com.example.myrfidreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myrfidreader.ui.theme.MyRFIDreaderTheme
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()
    val isConnected by UsbConnectionHolder.isConnected

    var selectedBaudRate by remember { mutableIntStateOf(viewModel.baudRate.value) }
    var rfPower by remember { mutableIntStateOf(viewModel.rfPower.value) }
    var selectedWorkMode by remember { mutableIntStateOf(viewModel.workMode.value) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Настройки ридера",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (!isConnected) {
            Text("Устройство не подключено", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                // Открываем MainActivity для подключения
                context.startActivity(Intent(context, MainActivity::class.java))
            }) {
                Text("Подключиться")
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
                valueRange = 0f..26f,
                steps = 25,
                modifier = Modifier.fillMaxWidth()
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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.applySettings(selectedBaudRate, rfPower, selectedWorkMode) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Применить настройки")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        viewModel.readSettings()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Прочитать текущие настройки")
            }
        }
    }
}
