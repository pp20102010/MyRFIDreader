package com.example.myrfidreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myrfidreader.ui.theme.MyRFIDreaderTheme

class LongExperimentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyRFIDreaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LongExperimentScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongExperimentScreen(viewModel: LongExperimentViewModel = viewModel()) {
    val context = LocalContext.current
    val isExperimentRunning by viewModel.isExperimentRunning.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val statsList by viewModel.statsList.collectAsState()
    val experimentNumber by viewModel.experimentNumber.collectAsState()
    val totalIntervals by viewModel.totalIntervals.collectAsState()

    // Cостояние подключения ридера
    val isConnected by UsbConnectionHolder.isConnected

    // Состояния выпадающих списков
    var expandedZone by remember { mutableStateOf(false) }
    var expandedMounting by remember { mutableStateOf(false) }
    var expandedDistance by remember { mutableStateOf(false) }
    var expandedAngle by remember { mutableStateOf(false) }
    var expandedPollution by remember { mutableStateOf(false) }
    var expandedDuration by remember { mutableStateOf(false) }
    var expandedInterval by remember { mutableStateOf(false) }

    // Локальные переменные для редактирования
    var selectedZone by remember { mutableStateOf(viewModel.zone) }
    var selectedMounting by remember { mutableStateOf(viewModel.mounting) }
    var selectedDistance by remember { mutableDoubleStateOf(viewModel.distance) }
    var selectedAngle by remember { mutableIntStateOf(viewModel.angle) }
    var selectedPollution by remember { mutableStateOf(viewModel.pollution) }
    var selectedDuration by remember { mutableIntStateOf(viewModel.duration) }
    var selectedInterval by remember { mutableDoubleStateOf(viewModel.interval) }

    var showClearDialog by remember { mutableStateOf(false) }

    val distanceOptions = (1..20).map { it * 0.5 }       // 0.5 ... 10.0
    val angleOptions = (0..180 step 15).toList()
    val durationOptions = listOf(50, 100, 200)
    val intervalOptions = listOf(0.5, 1.0, 2.0)
    val zoneOptions = listOf("А", "Б", "В", "Г", "Д")
    val mountingOptions = listOf("M", "C")
    val pollutionOptions = listOf("нет", "вода", "масло")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Длительный эксперимент") },
                navigationIcon = {
                    IconButton(onClick = { (context as? LongExperimentActivity)?.finish() }) {
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
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Блок "Параметр серии"
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Параметр серии", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Строка 1: Номер эксперимента (только чтение), Длительность, Интервал (по 1/3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Номер эксперимента
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = "%04d".format(experimentNumber),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("№") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp) // вместо фиксированной высоты
                            )
                        }

                        // Длительность
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedDuration,
                                onExpandedChange = { expandedDuration = it }
                            ) {
                                OutlinedTextField(
                                    value = "$selectedDuration с",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Длит.") },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDuration) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedDuration,
                                    onDismissRequest = { expandedDuration = false }
                                ) {
                                    durationOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("$value с") },
                                            onClick = {
                                                selectedDuration = value
                                                expandedDuration = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Интервал
                        // Интервал
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedInterval,
                                onExpandedChange = { expandedInterval = it }
                            ) {
                                OutlinedTextField(
                                    value = "%.1f с".format(selectedInterval),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Инт.") },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedInterval) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedInterval,
                                    onDismissRequest = { expandedInterval = false }
                                ) {
                                    intervalOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("%.1f с".format(value)) },
                                            onClick = {
                                                selectedInterval = value
                                                expandedInterval = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Строка 2: Зона, Крепление, Загрязнение (по 1/3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Зона
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedZone,
                                onExpandedChange = { expandedZone = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedZone,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Зона") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedZone) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedZone,
                                    onDismissRequest = { expandedZone = false }
                                ) {
                                    zoneOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                selectedZone = value
                                                expandedZone = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Крепление
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedMounting,
                                onExpandedChange = { expandedMounting = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedMounting,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Креп.") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMounting) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedMounting,
                                    onDismissRequest = { expandedMounting = false }
                                ) {
                                    mountingOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                selectedMounting = value
                                                expandedMounting = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Загрязнение
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedPollution,
                                onExpandedChange = { expandedPollution = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedPollution,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Загр.") },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPollution) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedPollution,
                                    onDismissRequest = { expandedPollution = false }
                                ) {
                                    pollutionOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                selectedPollution = value
                                                expandedPollution = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Строка 3: Расстояние, Угол (по 1/2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Расстояние
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedDistance,
                                onExpandedChange = { expandedDistance = it }
                            ) {
                                OutlinedTextField(
                                    value = "%.1f м".format(selectedDistance),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Расст.") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDistance) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedDistance,
                                    onDismissRequest = { expandedDistance = false }
                                ) {
                                    distanceOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("%.1f м".format(value)) },
                                            onClick = {
                                                selectedDistance = value
                                                expandedDistance = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Угол
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedAngle,
                                onExpandedChange = { expandedAngle = it }
                            ) {
                                OutlinedTextField(
                                    value = "$selectedAngle°",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Угол") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAngle) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedAngle,
                                    onDismissRequest = { expandedAngle = false }
                                ) {
                                    angleOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("$value°") },
                                            onClick = {
                                                selectedAngle = value
                                                expandedAngle = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Строка кнопок "Отправить протокол" и "Очистить протокол"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.shareProtocol(context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отправить протокол")
                }
                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Очистить протокол")
                }
            }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("Очистить протокол?") },
                    text = { Text("Все записи в файле протокола будут безвозвратно удалены, номер эксперимента сбросится.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearProtocol()
                                showClearDialog = false
                            }
                        ) {
                            Text("Очистить", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Блок "Течение эксперимента"
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Течение эксперимента", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (isExperimentRunning) {
                                    viewModel.stopExperiment()
                                } else {
                                    // Применяем выбранные значения к ViewModel
                                    viewModel.updateZone(selectedZone)
                                    viewModel.updateMounting(selectedMounting)
                                    viewModel.updateDistance(selectedDistance)
                                    viewModel.updateAngle(selectedAngle)
                                    viewModel.updatePollution(selectedPollution)
                                    viewModel.updateDuration(selectedDuration)
                                    viewModel.updateInterval(selectedInterval)
                                    viewModel.startExperiment()
                                }
                            },
                            enabled = isConnected, // активна всегда при подключении
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isExperimentRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isExperimentRunning) "Стоп" else "Пуск")
                        }
                        Text("Инт: $totalIntervals")
                        Text("Время: %.1f с".format(currentTime))
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (statsList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("EPC", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(2f))
                            Text("R", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("N", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("S", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("min", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("RSSI", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            items(statsList) { stat ->
                                val totalInt = totalIntervals
                                val r = stat.successfulIntervals
                                val percent = if (totalInt > 0) (r * 100) / totalInt else 0
                                val avg = stat.avgCount(totalInt)
                                val std = stat.stdDev(totalInt)
                                val avgRssi = stat.avgRssi
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stat.epc, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$r", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text("$percent%", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text("%.2f".format(avg), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text("%.2f".format(std), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text("${stat.minCount}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text("%.1f".format(avgRssi), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        Text("Нет данных", modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}