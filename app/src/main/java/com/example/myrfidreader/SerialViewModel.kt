package com.example.myrfidreader

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class SerialViewModel(application: Application) : AndroidViewModel(application),
    SerialInputOutputManager.Listener {
    private val usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager

    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    var rfidData by mutableStateOf("Ожидание данных...\n")
        private set

    private var serialPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()

    // Буфер для накопления сообщения (как в терминале)
    private val accumulator = mutableListOf<Byte>()

    fun connectAndRead() {
        // Если уже подключены, не делаем ничего или показываем сообщение
        if (serialPort != null && usbIoManager != null) {
            synchronized(this) {
                rfidData += "Уже подключено\n"
            }
            return
        }
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            rfidData += "Устройство не найдено\n"
            return
        }

        val driver = availableDrivers[0]
        val connection = usbManager.openDevice(driver.device) ?: return

        val port = driver.ports[0]
        try {
            port.open(connection)
            // Скорость как в вашем ридере
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // Включаем линии управления (DTR/RTS)
            port.dtr = true
            port.rts = true

            serialPort = port

            // Создаем менеджер ввода-вывода (аналог SimpleUsbTerminal)
            usbIoManager = SerialInputOutputManager(port, this)
            executor.submit(usbIoManager)

            rfidData += "Подключено (Terminal Mode 115200)\n"
        } catch (e: Exception) {
            rfidData += "Ошибка: ${e.message}\n"
        }
    }

    // МЕТОД ОБРАБОТКИ ДАННЫХ (Вызывается автоматически при поступлении байтов)
    override fun onNewData(data: ByteArray) {
        // Добавляем новые байты в список
        for (byte in data) {
            accumulator.add(byte)
        }

        // Если данных много или наступила пауза (простейшая проверка пакета)
        // В терминале данные обычно выводятся сразу, но мы ждем заголовка 43 54
        if (accumulator.size >= 32) {
            val hexString = accumulator.joinToString(" ") { "%02X".format(it) }

            // Форматируем текущую дату и время // Результат будет: [22.02.2026 14:30:05]
            val timeStamp = dateFormatter.format(Date())

            // Формируем итоговую строку
            val finalLog = "READ $timeStamp RFID: $hexString\n"

            // Обновляем Compose State (Thread-safe)
            synchronized(this) {
                // Ограничим лог, чтобы приложение не тормозило со временем (например, 200 строк)
                val lines = rfidData.split("\n")
                if (lines.size > 200) {
                    rfidData = lines.takeLast(100).joinToString("\n") + "\n"
                }
                rfidData += finalLog
            }
            // 2. Записываем в файл (в фоне)
//            файл лежит во внутреннем хранилище:
//            Подключите телефон к Android Studio.
//            Откройте вкладку Device File Explorer (справа внизу).
//            Перейдите по пути: data -> data -> com.example.myrfidreader -> files.
//            Там вы увидите rfid_logs.txt. Его можно сохранить на компьютер (правой кнопкой -> Save As).

            saveLogToFile(finalLog)
            accumulator.clear()
        }
    }

    override fun onRunError(e: Exception) {
        synchronized(this) {
            rfidData += "Ошибка связи: ${e.message}\n"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    private fun stop() {
        usbIoManager?.stop()
        usbIoManager = null
        try {
            serialPort?.close()
        } catch (e: Exception) {
        }
        serialPort = null
    }

    //для вывода логов в файл
    private fun saveLogToFile(text: String) {
        // ВАЖНО: onNewData уже работает в фоновом потоке библиотеки,
        // дополнительный executor здесь может только мешать.
        try {
            val context = getApplication<Application>()
            val file = java.io.File(context.filesDir, "rfid_logs.txt")

            // Если файла нет, создаем его
            if (!file.exists()) {
                file.createNewFile()
            }

            // Записываем напрямую
            java.io.FileOutputStream(file, true).use { output ->
                output.write(text.toByteArray())
                // use автоматически закроет поток и сделает flush
            }
        } catch (e: Exception) {
            // Выводим ошибку в rfidData, чтобы вы видели её на экране, если запись сорвется
            synchronized(this) {
                rfidData += "!!! Ошибка записи файла: ${e.message}\n"
            }
        }
    }


    //функция очистки файла логов
//    fun clearLogs() {
//        executor.submit {
//            try {
//                // 1. Очищаем физический файл
//                val file = File(getApplication<Application>().filesDir, "rfid_logs.txt")
//                if (file.exists()) {
//                    // Открываем без флага append, чтобы перезаписать файл пустым местом
//                    //FileOutputStream(file).use { it.write("".toByteArray()) }
//                    file.writeText("") // Это короче и надежнее FileOutputStream
//                }
//
//                // 2. Обновляем UI (в основном потоке через synchronized или post)
//                synchronized(this) {
//                    rfidData = ""
//                    rfidData = "Лог очищен ${dateFormatter.format(Date())}\n"
//                }
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }
    fun clearLogs() {
        viewModelScope.launch {
            try {
                // Работа с файлом в фоновом потоке
                val result = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val file = File(context.filesDir, "rfid_logs.txt")

                    if (file.exists()) {
                        file.writeText("") // Очищаем файл
                    } else {
                        file.createNewFile() // Создаем новый
                    }

                    dateFormatter.format(Date()) // Возвращаем timestamp
                }

                // Обновление UI в главном потоке (уже автоматически)
                rfidData = ""
                rfidData = "Лог очищен $result\n"

            } catch (e: Exception) {
                e.printStackTrace()
                rfidData += "Ошибка очистки: ${e.message}\n"
            }
        }
    }
    }
