// LongExperimentActivity.kt
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
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
    val view = LocalView.current
    val isConnected by UsbConnectionHolder.isConnected
    val isExperimentRunning by viewModel.isExperimentRunning.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val statsList by viewModel.statsList.collectAsState()
    val experimentNumber by viewModel.experimentNumber.collectAsState()
    val totalIntervals by viewModel.totalIntervals.collectAsState()

    // Удержание экрана во время эксперимента
    DisposableEffect(isExperimentRunning) {
        view.keepScreenOn = isExperimentRunning
        onDispose { view.keepScreenOn = false }
    }

    // Состояния выпадающих списков
    var expandedZone by remember { mutableStateOf(false) }
    var expandedMounting by remember { mutableStateOf(false) }
    var expandedDistance by remember { mutableStateOf(false) }
    var expandedAngle by remember { mutableStateOf(false) }
    var expandedPollution by remember { mutableStateOf(false) }
    var expandedDuration by remember { mutableStateOf(false) }
    var expandedInterval by remember { mutableStateOf(false) }
    var expandedProtocolType by remember { mutableStateOf(false) }

    // Локальные переменные для редактирования (синхронизируются с ViewModel через collectAsState)
    var selectedZone by remember { mutableStateOf(viewModel.zone) }
    var selectedMounting by remember { mutableStateOf(viewModel.mounting) }
    var selectedDistance by remember { mutableDoubleStateOf(viewModel.distance) }
    var selectedAngle by remember { mutableIntStateOf(viewModel.angle) }
    var selectedPollution by remember { mutableStateOf(viewModel.pollution) }
    var selectedDuration by remember { mutableIntStateOf(viewModel.duration) }
    var selectedInterval by remember { mutableDoubleStateOf(viewModel.interval) }
    var selectedProtocolType by remember { mutableStateOf(viewModel.protocolType) }
    var selectedNote by remember { mutableStateOf(viewModel.note) }

    var showClearDialog by remember { mutableStateOf(false) }

    val distanceOptions = (1..20).map { it * 0.5 }       // 0.5 ... 10.0
    val angleOptions = (0..180 step 30).toList()
    val durationOptions = listOf(50, 100, 200, -1)       // -1 для "авто"
    val intervalOptions = listOf(0.5, 1.0, 2.0)
    val zoneOptions = listOf("А", "Б", "В0", "В30", "В45", "В60", "В90", "Д")
    val mountingOptions = listOf("M", "C")
    val pollutionOptions = listOf("нет", "вода", "масло")
    val protocolTypeOptions = listOf("итоги", "полный")

    fun durationDisplay(value: Int): String = when (value) {
        -1 -> "авто"
        else -> "$value с"
    }

    fun intervalDisplay(value: Double): String = "%.1f с".format(value)

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

                    // Строка 1: № (редактируемый), Зона, Крепление
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Номер эксперимента (редактируемый)
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = viewModel.editableExperimentNumber.toString(),
                                onValueChange = { newValue ->
                                    val intValue = newValue.toIntOrNull()
                                    if (intValue != null && intValue > 0) {
                                        viewModel.setExperimentNumber(intValue)
                                    }
                                },
                                label = { Text("№") },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 32.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
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
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedZone) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 32.dp)
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
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMounting) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 32.dp)
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
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Строка 2: Длит, Инт, Вид
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Длительность
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedDuration,
                                onExpandedChange = { expandedDuration = it }
                            ) {
                                OutlinedTextField(
                                    value = durationDisplay(selectedDuration),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Длит.") },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDuration) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 32.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedDuration,
                                    onDismissRequest = { expandedDuration = false }
                                ) {
                                    durationOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(durationDisplay(value)) },
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
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedInterval,
                                onExpandedChange = { expandedInterval = it }
                            ) {
                                OutlinedTextField(
                                    value = intervalDisplay(selectedInterval),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Инт.") },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedInterval) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 32.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedInterval,
                                    onDismissRequest = { expandedInterval = false }
                                ) {
                                    intervalOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(intervalDisplay(value)) },
                                            onClick = {
                                                selectedInterval = value
                                                expandedInterval = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        // Вид протокола
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedProtocolType,
                                onExpandedChange = { expandedProtocolType = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedProtocolType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Вид") },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProtocolType) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 32.dp)
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                DropdownMenu(
                                    expanded = expandedProtocolType,
                                    onDismissRequest = { expandedProtocolType = false }
                                ) {
                                    protocolTypeOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                selectedProtocolType = value
                                                expandedProtocolType = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Строка 3: Расст, Угол, Загр
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
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDistance) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 32.dp)
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
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAngle) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 32.dp)
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
                                        .defaultMinSize(minHeight = 32.dp)
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

                    // Строка 4: Примечание
                    OutlinedTextField(
                        value = selectedNote,
                        onValueChange = { selectedNote = it },
                        label = { Text("Примечание", style = MaterialTheme.typography.labelLarge) },
                        textStyle = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 32.dp),
                        singleLine = true
                    )
                }
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
                                    // Применяем выбранные значения к ViewModel (кроме номера, он уже в editableExperimentNumber)
                                    viewModel.updateZone(selectedZone)
                                    viewModel.updateMounting(selectedMounting)
                                    viewModel.updateDistance(selectedDistance)
                                    viewModel.updateAngle(selectedAngle)
                                    viewModel.updatePollution(selectedPollution)
                                    viewModel.updateDuration(selectedDuration)
                                    viewModel.updateInterval(selectedInterval)
                                    viewModel.updateProtocolType(selectedProtocolType)
                                    viewModel.updateNote(selectedNote)
                                    viewModel.startExperiment()
                                }
                            },
                            enabled = isConnected,
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
                            Text("R", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.8f))
                            Text("%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("N", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("S", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("CV", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
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
                                val cv = if (avg > 0) std / avg else 0.0
                                val avgRssi = stat.avgRssi
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stat.epc,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(2f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$r",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(0.8f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "$percent%",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "%.2f".format(avg),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "%.2f".format(std),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "%.2f".format(cv),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${stat.minCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "%.1f".format(avgRssi),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
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