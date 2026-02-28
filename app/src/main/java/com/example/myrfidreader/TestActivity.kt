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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyRFIDreaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TestScreen()
                }
            }
        }
    }
}

@Composable
fun TestScreen(viewModel: TestViewModel = viewModel()) {
    val context = LocalContext.current
    val isConnected by UsbConnectionHolder.isConnected
    val isTestRunning by viewModel.isTestRunning.collectAsState()
    val epcList by viewModel.epcList.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()

    var showConnectPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            showConnectPrompt = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Тестирование считывания",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
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
                        Text("Подключиться")
                    }
                }
            }
        } else {
            // Таймер и кнопка Пуск/Стоп
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Время: ${elapsedSeconds} с")
                Button(
                    onClick = {
                        if (isTestRunning) {
                            viewModel.stopTest()
                        } else {
                            viewModel.startTest()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTestRunning) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isTestRunning) "Стоп" else "Пуск")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

//            // Отображение последнего ответа (для отладки)
//            Text(
//                text = "Последний ответ:",
//                style = MaterialTheme.typography.labelLarge,
//                modifier = Modifier.padding(top = 8.dp)
//            )
//            Surface(
//                modifier = Modifier.fillMaxWidth(),
//                color = MaterialTheme.colorScheme.surfaceVariant,
//                shape = MaterialTheme.shapes.small
//            ) {
//                Text(
//                    text = viewModel.lastRawResponse,
//                    style = MaterialTheme.typography.bodySmall,
//                    modifier = Modifier.padding(8.dp)
//                )
//            }
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            // Отображение отладочной информации
//            Text(
//                text = "Отладка:",
//                style = MaterialTheme.typography.labelLarge,
//                modifier = Modifier.padding(top = 8.dp)
//            )
//            Surface(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(150.dp),
//                color = MaterialTheme.colorScheme.surfaceVariant,
//                shape = MaterialTheme.shapes.small
//            ) {
//                Text(
//                    text = viewModel.debugInfo,
//                    style = MaterialTheme.typography.bodySmall,
//                    modifier = Modifier
//                        .padding(8.dp)
//                        .verticalScroll(rememberScrollState())
//                )
//            }

            Spacer(modifier = Modifier.height(16.dp))

            // Заголовки таблицы
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("EPC", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(2f))
                Text("Кол-во", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Список меток
            // Список меток
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(epcList) { item ->
                    val percent = if (elapsedSeconds > 0) (item.count * 100) / elapsedSeconds else 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.epc,
                            style = MaterialTheme.typography.bodySmall,  // уменьшенный шрифт
                            modifier = Modifier.weight(4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis  // обрезаем с многоточием, если не влезает
                        )
                        Text(
                            text = "${item.count}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}