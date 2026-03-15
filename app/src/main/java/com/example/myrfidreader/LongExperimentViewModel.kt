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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class LongExperimentViewModel(application: Application) : AndroidViewModel(application), OnDataReceivedListener {

    val experimentNumber = MutableStateFlow(1)
    var zone by mutableStateOf("А"); private set
    var mounting by mutableStateOf("M"); private set
    var distance by mutableStateOf(0.5); private set
    var angle by mutableStateOf(0); private set
    var pollution by mutableStateOf("нет"); private set
    var duration by mutableStateOf(100); private set
    var interval by mutableStateOf(2.0); private set
    var protocolType by mutableStateOf("итоги") // "итоги" или "полный"
        private set
    var note by mutableStateOf("") // примечание, свободный текст
        private set

    private val _isExperimentRunning = MutableStateFlow(false)
    val isExperimentRunning: StateFlow<Boolean> = _isExperimentRunning

    private val _currentTime = MutableStateFlow(0.0)
    val currentTime: StateFlow<Double> = _currentTime

    private val _statsList = MutableStateFlow<List<EPCStats>>(emptyList())
    val statsList: StateFlow<List<EPCStats>> = _statsList

    private val _totalIntervals = MutableStateFlow(0)
    val totalIntervals: StateFlow<Int> = _totalIntervals

    // Счетчики для текущего интервала (EPC -> количество считываний)
    private var intervalEpcCounts = mutableMapOf<String, Int>()
    // Для записи строк интервала (будем накапливать и записывать одной пачкой) – только в полном режиме
    private var intervalLines = mutableListOf<String>()

    private var intervalIndex = 0
    private var experimentStartTime = 0L

    private var timerJob: Job? = null

    private val prefs: SharedPreferences = application.getSharedPreferences("long_exp_prefs", Context.MODE_PRIVATE)
    private val dateFormatter = SimpleDateFormat("yyMMdd HH:mm:ss.SSS", Locale.getDefault())
    private val file = File(application.filesDir, "experiment_log.txt")
    private val incomingBuffer = mutableListOf<Byte>()

    // Поток для записи в файл, открытый на всё время эксперимента
    private var fileOutputStream: FileOutputStream? = null

    // Флаг, был ли уже записан CSV-заголовок
    private var isHeaderWritten: Boolean
        get() = prefs.getBoolean("is_header_written", false)
        set(value) = prefs.edit().putBoolean("is_header_written", value).apply()

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
    fun updateProtocolType(value: String) { protocolType = value }
    fun updateNote(value: String) { note = value }

    fun isInputValid(): Boolean = true

    fun startExperiment() {
        if (_isExperimentRunning.value) return

        closeFileStream()

        try {
            fileOutputStream = FileOutputStream(file, true)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        _isExperimentRunning.value = true
        _currentTime.value = 0.0
        _totalIntervals.value = 0
        intervalIndex = 0
        experimentStartTime = System.currentTimeMillis()
        incomingBuffer.clear()
        intervalEpcCounts.clear()
        intervalLines.clear()
        _statsList.value = emptyList()

        // Запуск таймера для обновления времени каждую секунду
        timerJob = viewModelScope.launch {
            while (_isExperimentRunning.value) {
                _currentTime.value = (System.currentTimeMillis() - experimentStartTime) / 1000.0
                delay(1000)
            }
        }

        viewModelScope.launch {
            // Если файл новый, запишем CSV-заголовок
            if (!isHeaderWritten) {
                val header = "CSV;Эксперимент серия №;Начат;Закончен;Зона;Крепление;Расстояние;Угол;Загрязнение;Длительность;Интервал;Примечание;EPC;Интервалов в эксперименте(I);Результативных интервалов(R);Результативность%(R%);Считываний среднее(N);Среднее отклонение считывания(S(N));Минимум считываний в результативном интервале(min);Средний уровень принятого сигнала(RSSI);Среднее отклонение (S(RSSI))"
                writeLinesSync(listOf(header))
                isHeaderWritten = true
            }
            // Пустая строка перед началом эксперимента
            writeLinesSync(listOf(""))
            val startLine = "Эксперимент серия: ${experimentNumber.value} ; начат ; ${formatDate()}"
            writeLinesSync(listOf(startLine))

            runExperiment()
        }
    }

    fun stopExperiment() {
        if (!_isExperimentRunning.value) return
        _isExperimentRunning.value = false
        timerJob?.cancel()
        viewModelScope.launch {
            // Если был полный режим, записываем остатки интервала
            if (protocolType == "полный") {
                flushInterval()
            }
            val abortLine = "Эксперимент серия: ${experimentNumber.value} ; прерван пользователем ; ${formatDate()}"
            writeLinesSync(listOf(abortLine, ""))
            closeFileStream()
        }
    }

    private suspend fun writeLinesSync(lines: List<String>) = withContext(Dispatchers.IO) {
        if (lines.isEmpty() || fileOutputStream == null) return@withContext
        synchronized(fileOutputStream!!) {
            try {
                for (line in lines) {
                    fileOutputStream!!.write(line.toByteArray())
                    fileOutputStream!!.write("\n".toByteArray())
                }
                fileOutputStream!!.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun flushInterval() {
        if (intervalLines.isNotEmpty()) {
            writeLinesSync(intervalLines)
            intervalLines.clear()
        }
    }

    private fun closeFileStream() {
        try {
            fileOutputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        fileOutputStream = null
    }

    private suspend fun runExperiment() {
        val totalDurationMs = (duration * 1000).toLong()
        val intervalMs = (interval * 1000).toLong()
        val pauseMs = 2000L
        val startTime = System.currentTimeMillis()
        var elapsed: Long

        while (true) {
            if (!_isExperimentRunning.value) break
            elapsed = System.currentTimeMillis() - startTime
            if (elapsed + intervalMs > totalDurationMs) break

            // Начало нового интервала
            intervalEpcCounts.clear()
            if (protocolType == "полный") {
                intervalLines.clear()
            }
            val readingStart = System.currentTimeMillis()
            val readingEndTime = readingStart + intervalMs

            while (System.currentTimeMillis() < readingEndTime && _isExperimentRunning.value) {
                delay(50)
            }

            intervalIndex++
            _totalIntervals.value = intervalIndex

            // Обработка накопленных счетчиков интервала (количество считываний) – всегда
            val currentStats = _statsList.value.toMutableList()
            for ((epc, count) in intervalEpcCounts) {
                var stat = currentStats.find { it.epc == epc }
                if (stat == null) {
                    stat = EPCStats(epc)
                    currentStats.add(stat)
                }
                stat.sumCount += count
                stat.sumSquares += count * count
                stat.totalCount += count
                if (count < stat.minCount) stat.minCount = count
                stat.successfulIntervals++
            }
            _statsList.value = currentStats.sortedByDescending { it.sumCount }

            // Если полный режим – записываем накопленные строки интервала
            if (protocolType == "полный" && intervalLines.isNotEmpty()) {
                writeLinesSync(intervalLines)
            }

            elapsed = System.currentTimeMillis() - startTime
            if (elapsed + pauseMs + intervalMs <= totalDurationMs) {
                if (protocolType == "полный") {
                    writeLinesSync(listOf("${formatDate()};пауза считывания"))
                }
                delay(pauseMs)
            } else {
                break
            }
            _currentTime.value = (System.currentTimeMillis() - startTime) / 1000.0
        }

        if (_isExperimentRunning.value) {
            _isExperimentRunning.value = false
            timerJob?.cancel()

            // Финальная статистика (текст) – без пустой строки в конце
            val summaryLines = mutableListOf<String>()
            val finishLine = "Эксперимент серия: ${experimentNumber.value} ; завершен ; ${formatDate()} ; Зона: $zone ; Крепление: $mounting ; Расстояние: $distance ; Угол: $angle ; Загрязнение: $pollution ; Длительность: $duration ; Интервал: $interval ; Примечание: $note"
            summaryLines.add(finishLine)
            summaryLines.add("Итоговая статистика:")
            _statsList.value.forEach { stat ->
                val totalInt = _totalIntervals.value
                val r = stat.successfulIntervals
                val percent = if (totalInt > 0) (r * 100.0) / totalInt else 0.0
                val avg = stat.avgCount(totalInt)
                val std = stat.stdDev(totalInt)
                val avgRssi = stat.avgRssi
                val stdRssi = stat.stdDevRssi()
                summaryLines.add("EPC: ${stat.epc}; I=$totalInt; R=$r; R%=${"%.2f".format(percent)}; N=${"%.2f".format(avg)}; S(N)=${"%.2f".format(std)}; min=${stat.minCount}; RSSI=${"%.1f".format(avgRssi)}; S(RSSI)=${"%.2f".format(stdRssi)}")
            }
            // Убрана пустая строка из summaryLines
            writeLinesSync(summaryLines)

            // CSV-строки для каждого EPC или пустая строка, если нет EPC
            val csvLines = mutableListOf<String>()
            val startDate = formatDate(experimentStartTime)
            val endDate = formatDate()

            if (_statsList.value.isEmpty()) {
                // Нет ни одного EPC – записываем строку с пустыми полями для статистики
                val commonPrefix = "CSV;${experimentNumber.value};$startDate;$endDate;$zone;$mounting;$distance;$angle;$pollution;$duration;$interval;$note"
                val emptyCsv = commonPrefix + ";".repeat(9) // 9 пустых полей (EPC, I, R, R%, N, S(N), min, RSSI, S(RSSI))
                csvLines.add(emptyCsv)
            } else {
                _statsList.value.forEach { stat ->
                    val totalInt = _totalIntervals.value
                    val r = stat.successfulIntervals
                    val percent = if (totalInt > 0) (r * 100.0) / totalInt else 0.0
                    val avg = stat.avgCount(totalInt)
                    val std = stat.stdDev(totalInt)
                    val avgRssi = stat.avgRssi
                    val stdRssi = stat.stdDevRssi()
                    val csvLine = "CSV;${experimentNumber.value};$startDate;$endDate;$zone;$mounting;$distance;$angle;$pollution;$duration;$interval;$note;${stat.epc};$totalInt;$r;${"%.2f".format(percent)};${"%.2f".format(avg)};${"%.2f".format(std)};${stat.minCount};${"%.1f".format(avgRssi)};${"%.2f".format(stdRssi)}"
                    csvLines.add(csvLine)
                }
            }
            if (csvLines.isNotEmpty()) {
                writeLinesSync(csvLines)
            }

            closeFileStream()

            experimentNumber.value++
            prefs.edit().putInt("last_exp_number", experimentNumber.value).apply()
        }
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
                val now = System.currentTimeMillis()

                // Если полный режим – сохраняем строку для записи в конце интервала
                if (protocolType == "полный") {
                    intervalLines.add("${formatDate(now)};$epcHex;$rssi")
                }

                // Обновляем счетчик интервала
                intervalEpcCounts[epcHex] = (intervalEpcCounts[epcHex] ?: 0) + 1

                // Обновляем глобальную статистику RSSI
                val currentStats = _statsList.value.toMutableList()
                var stat = currentStats.find { it.epc == epcHex }
                if (stat == null) {
                    stat = EPCStats(epcHex)
                    currentStats.add(stat)
                }
                stat.totalRssi += rssi
                stat.sumRssiSquares += rssi * rssi
                stat.rssiCount++
                _statsList.value = currentStats.sortedByDescending { it.sumCount }

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
                val now = System.currentTimeMillis()

                if (protocolType == "полный") {
                    intervalLines.add("${formatDate(now)};$epcHex;$rssi")
                }

                intervalEpcCounts[epcHex] = (intervalEpcCounts[epcHex] ?: 0) + 1

                val currentStats = _statsList.value.toMutableList()
                var stat = currentStats.find { it.epc == epcHex }
                if (stat == null) {
                    stat = EPCStats(epcHex)
                    currentStats.add(stat)
                }
                stat.totalRssi += rssi
                stat.sumRssiSquares += rssi * rssi
                stat.rssiCount++
                _statsList.value = currentStats.sortedByDescending { it.sumCount }

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

    fun clearProtocol() {
        viewModelScope.launch {
            closeFileStream()
            if (file.exists()) file.delete()
            isHeaderWritten = false
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
        closeFileStream()
    }
}