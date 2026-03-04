package com.example.myrfidreader

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application), OnDataReceivedListener {

    private val _baudRate = MutableStateFlow(4)   // индекс скорости: 0=9600,1=19200,2=38400,3=57600,4=115200
    val baudRate: StateFlow<Int> = _baudRate

    private val _rfPower = MutableStateFlow(26)   // мощность 0..26 dB
    val rfPower: StateFlow<Int> = _rfPower

    private val _workMode = MutableStateFlow(1)   // 0=Answer,1=Active,2=Trigger
    val workMode: StateFlow<Int> = _workMode

    var logData by mutableStateOf("Лог настроек:\n")
        private set

    private val dateFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Буфер для входящих пакетов
    private val incomingBuffer = mutableListOf<Byte>()

    // Храним запрошенные новые настройки до получения текущих параметров
    private var pendingSettings: Triple<Int, Int, Int>? = null

    init {
        UsbDataDispatcher.registerListener(this)
        addLogEntry("SYSTEM", "ViewModel инициализирован")
    }

    // ------------------ Публичные методы ------------------
    fun applySettings(baudRate: Int, rfPower: Int, workMode: Int) {
        pendingSettings = Triple(baudRate, rfPower, workMode)
        readDeviceParams()
    }

    fun readSettings() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!UsbConnectionHolder.isConnected.value) {
                    addLogEntry("ERROR", "Устройство не подключено")
                    return@withContext
                }
                readDeviceParams()
            }
        }
    }

    fun clearLog() {
        logData = "Лог настроек:\n"
        // Очищаем файл лога
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(getApplication<Application>().filesDir, "settings_logs.txt")
                if (file.exists()) file.writeText("")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ------------------ Отправка команд ------------------
    private fun readDeviceParams() {
        val cmd = byteArrayOf(
            0x53, 0x57,
            0x00, 0x03,       // длина 3 (Addr + Cmd + Checksum)
            0x01.toByte(),    // адрес устройства (из ответов)
            0x20,             // команда чтения параметров
            0x00              // контрольная сумма (временная)
        )
        var sum = 0
        for (i in 0 until cmd.size - 1) {
            sum += cmd[i].toInt() and 0xFF
        }
        cmd[cmd.size - 1] = ((sum.inv() + 1) and 0xFF).toByte()
        UsbConnectionHolder.write(cmd)
        addLogEntry("TX", cmd.joinToString(" ") { "%02X".format(it) })
    }

    private fun sendSetDeviceParams(params: ByteArray) {
        // params содержит DevType (1), Flag (1) и 31 байт параметров (всего 33 байта)
        val length = 3 + params.size   // Addr + Cmd + params + Checksum = 3 + params.size
        val cmd = ByteArray(4 + length) // Head(2) + Length(2) + тело
        cmd[0] = 0x53
        cmd[1] = 0x57
        cmd[2] = ((length shr 8) and 0xFF).toByte()
        cmd[3] = (length and 0xFF).toByte()
        cmd[4] = 0x01.toByte()           // адрес устройства
        cmd[5] = 0x21                     // команда записи
        params.copyInto(cmd, 6)            // копируем параметры

        var sum = 0
        for (i in 0 until cmd.size - 1) {
            sum += cmd[i].toInt() and 0xFF
        }
        cmd[cmd.size - 1] = ((sum.inv() + 1) and 0xFF).toByte()

        UsbConnectionHolder.write(cmd)
        addLogEntry("TX", cmd.joinToString(" ") { "%02X".format(it) })
    }

    // ------------------ Обработка входящих данных ------------------
    override fun onDataReceived(data: ByteArray) {
        addLogEntry("RAW", data.joinToString(" ") { "%02X".format(it) })
        incomingBuffer.addAll(data.toList())
        processIncomingBuffer()
    }

    private fun processIncomingBuffer() {
        while (true) {
            val headerIndex = findHeader(incomingBuffer, byteArrayOf(0x43, 0x54))
            if (headerIndex == -1) break
            if (headerIndex > 0) {
                incomingBuffer.subList(0, headerIndex).clear()
            }
            if (incomingBuffer.size < 8) break

            val length = ((incomingBuffer[2].toInt() and 0xFF) shl 8) or (incomingBuffer[3].toInt() and 0xFF)
            val totalPacketLen = 4 + length
            if (incomingBuffer.size < totalPacketLen) break

            val packet = incomingBuffer.subList(0, totalPacketLen).toByteArray()

            // Проверка контрольной суммы (по всем байтам до последнего)
            val checkPos = totalPacketLen - 1
            val receivedChecksum = packet[checkPos]
            val dataForChecksum = packet.copyOfRange(0, checkPos) // весь пакет до контрольной суммы
            val calculatedChecksum = calculateChecksum(dataForChecksum)
            if (receivedChecksum != calculatedChecksum) {
                addLogEntry("RX", "Ошибка контрольной суммы: получено ${"%02X".format(receivedChecksum)}, вычислено ${"%02X".format(calculatedChecksum)}")
                incomingBuffer.subList(0, totalPacketLen).clear()
                continue
            }

            processResponsePacket(packet)
            incomingBuffer.subList(0, totalPacketLen).clear()
        }
    }

    private fun processResponsePacket(packet: ByteArray) {
        val cmd = packet[5].toInt() and 0xFF
        val status = packet[6].toInt() and 0xFF
        val data = packet.copyOfRange(7, packet.size - 1)

        addLogEntry("RX", "Команда: 0x${cmd.toString(16)}, статус: $status")

        if (status != 0x01) {
            addLogEntry("RX", "Ошибка выполнения команды")
            return
        }

        when (cmd) {
            0x20 -> handleReadResponse(data)
            0x21 -> handleWriteResponse()
            else -> addLogEntry("RX", "Необработанная команда: 0x${cmd.toString(16)}")
        }
    }

    private fun handleReadResponse(data: ByteArray) {
        // data содержит DevType, Flag и 31 байт параметров (всего 33 байта)
        if (data.size < 33) {
            addLogEntry("RX", "Недостаточно данных в ответе на 0x20: размер ${data.size}")
            return
        }

        val params = data // полный блок параметров

        // Если есть ожидающие новые настройки, модифицируем и отправляем
        pendingSettings?.let { (newBaud, newPower, newMode) ->
            val modified = params.clone()
            // Смещения внутри params (после DevType(0), Flag(1)):
            // [2] bTransport
            // [3] bWorkMode   <-- меняем
            // [4] bDeviceAddr
            // [5] bFilterTime
            // [6] bRFPower     <-- меняем
            // [7] bBeepEnable
            // [8] bUartBaudRate <-- меняем
            modified[3] = newMode.toByte()
            modified[6] = newPower.toByte()
            modified[8] = newBaud.toByte()

            sendSetDeviceParams(modified)
            pendingSettings = null
        } ?: run {
            // Просто чтение – обновляем UI
            _workMode.value = params[3].toInt() and 0xFF
            _rfPower.value = params[6].toInt() and 0xFF
            _baudRate.value = params[8].toInt() and 0xFF
            addLogEntry("RX", "Текущие настройки: режим ${_workMode.value}, мощность ${_rfPower.value}, скорость ${_baudRate.value}")
        }
    }

    private fun handleWriteResponse() {
        addLogEntry("RX", "Параметры успешно записаны")
    }

    // ------------------ Вспомогательные функции ------------------
    private fun findHeader(buffer: List<Byte>, header: ByteArray): Int {
        for (i in 0 until buffer.size - header.size + 1) {
            var match = true
            for (j in header.indices) {
                if (buffer[i + j] != header[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun calculateChecksum(data: ByteArray): Byte {
        var sum = 0
        for (byte in data) {
            sum += byte.toInt() and 0xFF
        }
        return ((sum.inv() + 1) and 0xFF).toByte()
    }

    private fun addLogEntry(prefix: String, message: String) {
        val time = dateFormatter.format(Date())
        val entry = "[$time][$prefix] $message\n"

        // Обновляем UI в главном потоке
        viewModelScope.launch(Dispatchers.Main) {
            val lines = logData.lines()
            logData = if (lines.size > 200) {
                lines.takeLast(150).joinToString("\n") + "\n" + entry
            } else {
                logData + entry
            }
        }

        // Сохраняем в файл (для кнопки "Поделиться логом")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val file = File(context.filesDir, "settings_logs.txt")
                if (!file.exists()) file.createNewFile()
                FileOutputStream(file, true).use { output ->
                    output.write(entry.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Ошибка записи лога в файл", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        UsbDataDispatcher.unregisterListener(this)
    }
}