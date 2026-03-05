package com.example.myrfidreader

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PendingSettings(
    val baudRate: Int,
    val rfPower: Int,
    val workMode: Int,
    val filterTime: Int,
    val buzzerEnabled: Boolean
)

class SettingsViewModel(application: Application) : AndroidViewModel(application), OnDataReceivedListener {

    private val _baudRate = MutableStateFlow(4)   // индекс скорости: 0=9600,1=19200,2=38400,3=57600,4=115200
    val baudRate: StateFlow<Int> = _baudRate

    private val _rfPower = MutableStateFlow(26)   // мощность 0..26 dB
    val rfPower: StateFlow<Int> = _rfPower

    private val _workMode = MutableStateFlow(1)   // 0=Answer,1=Active,2=Trigger
    val workMode: StateFlow<Int> = _workMode

    private val _filterTime = MutableStateFlow(1) // периодичность 0..255 секунд
    val filterTime: StateFlow<Int> = _filterTime

    private val _buzzerEnabled = MutableStateFlow(true) // зуммер включён/выключен
    val buzzerEnabled: StateFlow<Boolean> = _buzzerEnabled

    var logData by mutableStateOf("Лог настроек:\n")
        private set

    private val dateFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Буфер для входящих пакетов
    private val incomingBuffer = mutableListOf<Byte>()

    // Храним запрошенные новые настройки до получения текущих параметров
    private var pendingSettings: PendingSettings? = null

    init {
        UsbDataDispatcher.registerListener(this)
        addLogEntry("SYSTEM", "ViewModel инициализирован")
    }

    // ------------------ Публичные методы ------------------
    fun applySettings(baudRate: Int, rfPower: Int, workMode: Int, filterTime: Int, buzzerEnabled: Boolean) {
        val safeRfPower = rfPower.coerceIn(0, 30)
        pendingSettings = PendingSettings(baudRate, rfPower, workMode, filterTime, buzzerEnabled)
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

            val checkPos = totalPacketLen - 1
            val receivedChecksum = packet[checkPos]
            val dataForChecksum = packet.copyOfRange(0, checkPos)
            val calculatedChecksum = calculateChecksum(dataForChecksum)
            if (receivedChecksum != calculatedChecksum) {
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
        if (data.size < 33) {
            addLogEntry("RX", "Недостаточно данных в ответе на 0x20: размер ${data.size}")
            return
        }

        val devType = data[0]
        val flag = data[1]
        val params = data.copyOfRange(2, data.size) // параметры без DevType и Flag

        // Обновляем UI значениями из прочитанных параметров
        _workMode.value = params[1].toInt() and 0xFF
        _rfPower.value = params[4].toInt() and 0xFF
        _baudRate.value = params[6].toInt() and 0xFF
        _filterTime.value = params[3].toInt() and 0xFF   // bFilterTime (смещение 3)
        _buzzerEnabled.value = (params[5].toInt() and 0xFF) != 0 // bBeepEnable (смещение 5)

        addLogEntry("RX", "Текущие настройки: режим ${_workMode.value}, мощность ${_rfPower.value}, скорость ${_baudRate.value}, фильтр ${_filterTime.value}, зуммер ${_buzzerEnabled.value}")

        // Если есть ожидающие новые настройки, модифицируем и отправляем
        pendingSettings?.let { pending ->
            val modified = params.clone()
            modified[1] = pending.workMode.toByte()      // workMode
            modified[3] = pending.filterTime.toByte()    // filterTime
            modified[4] = pending.rfPower.toByte()       // rfPower
            modified[5] = if (pending.buzzerEnabled) 1.toByte() else 0.toByte() // buzzer
            modified[6] = pending.baudRate.toByte()      // baudRate

            val fullParams = byteArrayOf(devType, flag) + modified
            sendSetDeviceParams(fullParams)
            pendingSettings = null
        }
    }

    private fun handleWriteResponse() {
        addLogEntry("RX", "Параметры успешно записаны")
        pendingSettings = null // сбрасываем, чтобы следующее чтение не пыталось отправить снова
        viewModelScope.launch {
            delay(500) // небольшая задержка для применения настроек ридером
            readDeviceParams()
        }
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

        viewModelScope.launch(Dispatchers.Main) {
            val lines = logData.lines()
            logData = if (lines.size > 200) {
                lines.takeLast(150).joinToString("\n") + "\n" + entry
            } else {
                logData + entry
            }
        }

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