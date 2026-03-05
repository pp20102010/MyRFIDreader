package com.example.myrfidreader

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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

class Test2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyRFIDreaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Test2Screen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Test2Screen(viewModel: Test2ViewModel = viewModel()) {
    val context = LocalContext.current
    val isConnected by UsbConnectionHolder.isConnected
    val isTestRunning by viewModel.isTestRunning.collectAsState()
    val epcList by viewModel.epcList.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val testDuration by viewModel.testDuration.collectAsState()

    var showConnectPrompt by remember { mutableStateOf(false) }
    var expandedDuration by remember { mutableStateOf(false) }
    val durationOptions = listOf(0.5, 1.0, 2.0, 3.0, 4.0, 5.0)
    var selectedDuration by remember { mutableDoubleStateOf(testDuration) }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            showConnectPrompt = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тест 2") },
                navigationIcon = {
                    IconButton(onClick = { (context as? Test2Activity)?.finish() }) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Тест 2",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Сколько раз за заданное количество секунд каких меток (EPC - номер) " +
                        "и сколько раз было считано с нажатия кнопки Пуск " +
                        "(предварительно необходимо установить Active режим и периодичность " +
                        "считывания 0сек в настройках ридера).",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            if (showConnectPrompt) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expandedDuration,
                        onExpandedChange = { expandedDuration = it }
                    ) {
                        OutlinedTextField(
                            value = "${selectedDuration} с",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Время теста") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDuration) },
                            modifier = Modifier
                                .width(120.dp)
                                .menuAnchor()
                        )
                        DropdownMenu(
                            expanded = expandedDuration,
                            onDismissRequest = { expandedDuration = false }
                        ) {
                            durationOptions.forEach { duration ->
                                DropdownMenuItem(
                                    text = { Text("$duration с") },
                                    onClick = {
                                        selectedDuration = duration
                                        viewModel.setTestDuration(duration)
                                        expandedDuration = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.startTest(selectedDuration) },
                        enabled = !isTestRunning
                    ) {
                        Text("Пуск")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isTestRunning) {
                    Text("Прошло: ${"%.1f".format(elapsedSeconds)} с / ${"%.1f".format(testDuration)} с")
                } else {
                    Text("Время теста: ${"%.1f".format(testDuration)} с")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("EPC", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(2f))
                    Text("Кол-во", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(epcList) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.epc,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(2f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.count}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}