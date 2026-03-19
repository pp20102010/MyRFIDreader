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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SerialViewModel(application: Application) : AndroidViewModel(application), OnDataReceivedListener {

    private val usbManager = application.getSystemService(Context.USB_SERVICE) as UsbManager
    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    var rfidData by mutableStateOf("Ожидание данных...\n")
        private set

    var baudRate by mutableStateOf(4) // 4 = 115200
        private set

    private val accumulator = mutableListOf<Byte>()

    init {
        UsbDataDispatcher.registerListener(this)
    }

    fun updateBaudRate(value: Int) {
        baudRate = value
    }

    fun connectAndRead() {
        // Если уже подключены, ничего не делаем
        if (UsbConnectionHolder.isConnected.value) {
            rfidData += "Уже подключено\n"
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
            port.setParameters(
                when (baudRate) {
                    0 -> 9600
                    1 -> 19200
                    2 -> 38400
                    3 -> 57600
                    4 -> 115200
                    else -> 115200
                },
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            port.dtr = true
            port.rts = true

            // Передаём порт холдеру, который будет управлять чтением
            UsbConnectionHolder.openConnection(port)
            rfidData += "Подключено (Terminal Mode)\n"
        } catch (e: Exception) {
            rfidData += "Ошибка: ${e.message}\n"
        }
    }

    fun disconnect() {
        UsbConnectionHolder.closeConnection()
        rfidData += "Отключено\n"
    }

    override fun onDataReceived(data: ByteArray) {
        // Обработка данных для лога (как было раньше)
        for (byte in data) {
            accumulator.add(byte)
        }
        if (accumulator.size >= 32) {
            val hexString = accumulator.joinToString(" ") { "%02X".format(it) }
            val timeStamp = dateFormatter.format(Date())
            val finalLog = "READ $timeStamp RFID: $hexString\n"
            synchronized(this) {
                val lines = rfidData.split("\n")
                if (lines.size > 200) {
                    rfidData = lines.takeLast(100).joinToString("\n") + "\n"
                }
                rfidData += finalLog
            }
            saveLogToFile(finalLog)
            accumulator.clear()
        }
    }

    private fun saveLogToFile(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val file = File(context.filesDir, "rfid_logs.txt")
                if (!file.exists()) file.createNewFile()
                FileOutputStream(file, true).use { output ->
                    output.write(text.toByteArray())
                }
            } catch (e: Exception) {
                synchronized(this@SerialViewModel) {
                    rfidData += "!!! Ошибка записи файла: ${e.message}\n"
                }
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val file = File(context.filesDir, "rfid_logs.txt")
                    if (file.exists()) file.writeText("") else file.createNewFile()
                    dateFormatter.format(Date())
                }
                rfidData = ""
                rfidData = "Лог очищен $result\n"
            } catch (e: Exception) {
                e.printStackTrace()
                rfidData += "Ошибка очистки: ${e.message}\n"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        UsbDataDispatcher.unregisterListener(this)
        // Не закрываем соединение, оно живёт в холдере
    }

}