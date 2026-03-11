package com.example.myrfidreader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class EPCStats(
    val epc: String,
    var totalCount: Int = 0,
    var sumCount: Int = 0,
    var sumSquares: Double = 0.0,
    var minCount: Int = Int.MAX_VALUE,
    var zeroIntervals: Int = 0,
    var totalRssi: Int = 0,
    var rssiCount: Int = 0
) {
    val avgRssi: Double get() = if (rssiCount > 0) totalRssi.toDouble() / rssiCount else 0.0
    fun avgCount(intervals: Int): Double = if (intervals > 0) sumCount.toDouble() / intervals else 0.0
    fun stdDev(intervals: Int): Double {
        if (intervals <= 1) return 0.0
        val mean = avgCount(intervals)
        return Math.sqrt((sumSquares / intervals) - (mean * mean))
    }
}

class LongExperimentViewModel(application: Application) : AndroidViewModel(application), OnDataReceivedListener {

    val experimentNumber = MutableStateFlow(1)
    var zone by mutableStateOf("А"); private set
    var mounting by mutableStateOf("M"); private set
    var distance by mutableStateOf(0.5); private set
    var angle by mutableStateOf(0); private set
    var pollution by mutableStateOf("нет"); private set
    var duration by mutableStateOf(100); private set
    var interval by mutableStateOf(2.0); private set

    private val _isExperimentRunning = MutableStateFlow(false)
    val isExperimentRunning: StateFlow<Boolean> = _isExperimentRunning

    private val _currentTime = MutableStateFlow(0.0)
    val currentTime: StateFlow<Double> = _currentTime

    private val _statsList = MutableStateFlow<List<EPCStats>>(emptyList())
    val statsList: StateFlow<List<EPCStats>> = _statsList

    private val _totalIntervals = MutableStateFlow(0)
    val totalIntervals: StateFlow<Int> = _totalIntervals

    private var currentIntervalReadings = mutableListOf<Pair<String, Int>>()
    private var intervalIndex = 0
    private var experimentStartTime = 0L

    private val prefs: SharedPreferences = application.getSharedPreferences("long_exp_prefs", Context.MODE_PRIVATE)
    private val dateFormatter = SimpleDateFormat("MMdd HH:mm:ss.SSS", Locale.getDefault())
    private val file = File(application.filesDir, "experiment_log.txt")
    private val incomingBuffer = mutableListOf<Byte>()

    init {
        experimentNumber.value = prefs.getInt("last_exp_number", 1)
        UsbDataDispatcher.registerListener(this)
    }

    fun updateZone(value: String) { zone = value }
    fun updateMounting(value: String) { mounting = value }
    fun updateDistance(value: Double) { distance = value }
    fun updateAngle(value: Int) { angle = value }
    fun updatePollution(value: String) { pollution = value }
    fun updateDuration(value: Int) { duration = value }
    fun updateInterval(value: Double) { interval = value }

    fun isInputValid(): Boolean = true

    fun startExperiment() {
        if (_isExperimentRunning.value) return
        _isExperimentRunning.value = true
        _currentTime.value = 0.0
        _totalIntervals.value = 0
        intervalIndex = 0
        experimentStartTime = System.currentTimeMillis()
        incomingBuffer.clear()
        currentIntervalReadings.clear()
        _statsList.value = emptyList()

        viewModelScope.launch {
            writeToFile("Начало эксперимента;${formatDate()}")
            writeToFile("Номер эксперимента: ${experimentNumber.value};Зона: $zone;Крепление: $mounting;Расстояние: $distance;Угол: $angle;Загрязнение: $pollution;Длительность: $duration;Интервал: $interval")
        }

        viewModelScope.launch { runExperiment() }
    }

    fun stopExperiment() { _isExperimentRunning.value = false }

    private suspend fun runExperiment() {
        val totalDurationMs = (duration * 1000).toLong()
        val intervalMs = (interval * 1000).toLong()
        val pauseMs = 2000L
        val startTime = System.currentTimeMillis()
        var elapsed: Long

        while (true) {
            elapsed = System.currentTimeMillis() - startTime
            if (elapsed + intervalMs > totalDurationMs) break

            val readingStart = System.currentTimeMillis()
            currentIntervalReadings.clear()
            val readingEndTime = readingStart + intervalMs

            while (System.currentTimeMillis() < readingEndTime && _isExperimentRunning.value) {
                delay(50)
            }

            intervalIndex++
            _totalIntervals.value = intervalIndex

            val intervalStats = mutableMapOf<String, MutableList<Int>>()
            for ((epc, rssi) in currentIntervalReadings) {
                intervalStats.getOrPut(epc) { mutableListOf() }.add(rssi)
            }

            val currentStats = _statsList.value.toMutableList()
            for ((epc, rssiList) in intervalStats) {
                val count = rssiList.size
                val avgRssi = rssiList.average().toInt()
                var stat = currentStats.find { it.epc == epc }
                if (stat == null) {
                    stat = EPCStats(epc)
                    currentStats.add(stat)
                }
                stat.sumCount += count
                stat.sumSquares += count * count
                stat.totalCount += count
                if (count < stat.minCount) stat.minCount = count
                stat.totalRssi += avgRssi * count
                stat.rssiCount += count
            }
            currentStats.forEach { stat ->
                if (!intervalStats.containsKey(stat.epc)) {
                    stat.zeroIntervals++
                }
            }
            _statsList.value = currentStats.sortedByDescending { it.sumCount }

            val intervalTime = System.currentTimeMillis()
            for ((epc, rssiList) in intervalStats) {
                for (rssi in rssiList) {
                    writeToFile("${formatDate(intervalTime)};$epc;$rssi")
                }
            }

            elapsed = System.currentTimeMillis() - startTime
            if (elapsed + pauseMs + intervalMs <= totalDurationMs) {
                val pauseStart = System.currentTimeMillis()
                var pauseElapsed = 0L
                while (pauseElapsed < pauseMs && _isExperimentRunning.value) {
                    writeToFile("${formatDate()};пауза считывания")
                    delay(1000)
                    pauseElapsed = System.currentTimeMillis() - pauseStart
                }
            } else {
                break
            }
        }

        _isExperimentRunning.value = false
        viewModelScope.launch {
            writeToFile("Эксперимент завершен, количество интервалов чтения - ${_totalIntervals.value}")
            writeToFile("Номер эксперимента: ${experimentNumber.value};Зона: $zone;Крепление: $mounting;Расстояние: $distance;Угол: $angle;Загрязнение: $pollution;Длительность: $duration;Интервал: $interval")
            writeToFile("Итоговая статистика:")
            _statsList.value.forEach { stat ->
                val avg = stat.avgCount(_totalIntervals.value)
                val std = stat.stdDev(_totalIntervals.value)
                writeToFile("EPC: ${stat.epc}; N=%.2f; S=%.2f; min=${stat.minCount}; Z=${stat.zeroIntervals}; RSSI=%.1f".format(avg, std, stat.avgRssi))
            }
            writeToFile("")
        }
        experimentNumber.value++
        prefs.edit().putInt("last_exp_number", experimentNumber.value).apply()
    }

    override fun onDataReceived(data: ByteArray) {
        if (!_isExperimentRunning.value) return
        incomingBuffer.addAll(data.toList())
        processIncomingBuffer()
    }

    private fun processIncomingBuffer() {
        while (true) {
            val headerIndex = findHeader(incomingBuffer, byteArrayOf(0x43, 0x54))
            if (headerIndex == -1) break
            if (headerIndex > 0) incomingBuffer.subList(0, headerIndex).clear()
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
        if (status != 0x01) return
        when (cmd) {
            0x01, 0x45 -> parseTags(data, cmd)
        }
    }

    private fun parseTags(data: ByteArray, cmd: Int) {
        if (cmd == 0x45) {
            if (data.size < 8) return
            var pos = 7
            val tagCount = data[pos].toInt() and 0xFF
            pos += 1
            for (i in 0 until tagCount) {
                if (pos + 1 > data.size) break
                val tagLen = data[pos].toInt() and 0xFF
                if (pos + 1 + tagLen > data.size) break
                val tagData = data.copyOfRange(pos + 1, pos + 1 + tagLen)
                if (tagData.size < 3) continue
                val epcBytes = tagData.copyOfRange(2, tagData.size - 1)
                val epcHex = epcBytes.joinToString("") { "%02X".format(it) }
                val rssi = tagData[tagData.size - 1].toInt() and 0xFF
                currentIntervalReadings.add(epcHex to rssi)
                pos += 1 + tagLen
            }
        } else {
            if (data.size < 2) return
            val tagCount = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            var pos = 2
            for (i in 0 until tagCount) {
                if (pos + 1 > data.size) break
                val tagLen = data[pos].toInt() and 0xFF
                if (pos + 1 + tagLen > data.size) break
                val tagData = data.copyOfRange(pos + 1, pos + 1 + tagLen)
                if (tagData.size < 3) continue
                val epcBytes = tagData.copyOfRange(2, tagData.size - 1)
                val epcHex = epcBytes.joinToString("") { "%02X".format(it) }
                val rssi = tagData[tagData.size - 1].toInt() and 0xFF
                currentIntervalReadings.add(epcHex to rssi)
                pos += 1 + tagLen
            }
        }
    }

    private fun findHeader(buffer: List<Byte>, header: ByteArray): Int {
        for (i in 0 until buffer.size - header.size + 1) {
            var match = true
            for (j in header.indices) if (buffer[i + j] != header[j]) { match = false; break }
            if (match) return i
        }
        return -1
    }

    private fun calculateChecksum(data: ByteArray): Byte {
        var sum = 0
        for (byte in data) sum += byte.toInt() and 0xFF
        return ((sum.inv() + 1) and 0xFF).toByte()
    }

    private fun formatDate(time: Long = System.currentTimeMillis()): String = dateFormatter.format(Date(time))

    private fun writeToFile(line: String) {
        viewModelScope.launch {
            try {
                if (!file.exists()) file.createNewFile()
                FileOutputStream(file, true).use { it.write((line + "\n").toByteArray()) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun clearProtocol() {
        viewModelScope.launch {
            if (file.exists()) file.delete()
            experimentNumber.value = 1
            prefs.edit().putInt("last_exp_number", 1).apply()
        }
    }

    fun shareProtocol(context: Context) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Протокол пуст", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Отправить протокол через..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        UsbDataDispatcher.unregisterListener(this)
    }
}