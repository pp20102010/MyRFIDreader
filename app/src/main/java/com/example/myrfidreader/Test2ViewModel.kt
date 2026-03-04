package com.example.myrfidreader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class Test2ViewModel : ViewModel(), OnDataReceivedListener {

    private val _epcList = MutableStateFlow<List<EpcItem>>(emptyList())
    val epcList: StateFlow<List<EpcItem>> = _epcList

    private val _elapsedSeconds = MutableStateFlow(0.0)
    val elapsedSeconds: StateFlow<Double> = _elapsedSeconds

    private val _testDuration = MutableStateFlow(1.0)
    val testDuration: StateFlow<Double> = _testDuration

    private val _isTestRunning = MutableStateFlow(false)
    val isTestRunning: StateFlow<Boolean> = _isTestRunning

    var lastRawResponse by mutableStateOf("")
        private set

    var debugLog by mutableStateOf("")
        private set

    private val epcMap = mutableMapOf<String, Int>() // храним только счётчики
    private val incomingBuffer = mutableListOf<Byte>()
    private var startTime = 0L
    private var targetDurationMs = 0L

    fun setTestDuration(duration: Double) {
        _testDuration.value = duration
    }

    fun startTest(duration: Double) {
        if (_isTestRunning.value) return
        epcMap.clear()
        _epcList.value = emptyList()
        incomingBuffer.clear()
        lastRawResponse = ""
        debugLog = ""
        _elapsedSeconds.value = 0.0
        _testDuration.value = duration
        targetDurationMs = (duration * 1000).toLong()
        startTime = System.currentTimeMillis()
        _isTestRunning.value = true

        addDebug("Тест запущен на ${duration} с")

        viewModelScope.launch {
            while (_isTestRunning.value) {
                val elapsed = System.currentTimeMillis() - startTime
                _elapsedSeconds.value = elapsed / 1000.0
                if (elapsed >= targetDurationMs) {
                    stopTest()
                    break
                }
                delay(100)
            }
        }

        UsbDataDispatcher.registerListener(this)
    }

    fun stopTest() {
        _isTestRunning.value = false
        UsbDataDispatcher.unregisterListener(this)
        addDebug("Тест остановлен")
    }

    override fun onDataReceived(data: ByteArray) {
        if (!_isTestRunning.value) return
        addDebug("Получено ${data.size} байт")
        incomingBuffer.addAll(data.toList())
        processIncomingBuffer()
    }

    private fun processIncomingBuffer() {
        while (true) {
            val headerIndex = findHeader(incomingBuffer, byteArrayOf(0x43, 0x54))
            if (headerIndex == -1) break
            if (headerIndex > 0) {
                incomingBuffer.subList(0, headerIndex).clear()
                addDebug("Удалён мусор до заголовка")
            }
            if (incomingBuffer.size < 8) break

            val length = ((incomingBuffer[2].toInt() and 0xFF) shl 8) or (incomingBuffer[3].toInt() and 0xFF)
            val totalPacketLen = 4 + length
            if (incomingBuffer.size < totalPacketLen) break

            val packet = incomingBuffer.subList(0, totalPacketLen).toByteArray()
            lastRawResponse = packet.joinToString(" ") { "%02X".format(it) }

            val checkPos = totalPacketLen - 1
            val receivedChecksum = packet[checkPos]
            val dataForChecksum = packet.copyOfRange(0, checkPos)
            val calculatedChecksum = calculateChecksum(dataForChecksum)
            if (receivedChecksum != calculatedChecksum) {
                addDebug("Ошибка контрольной суммы: получено ${"%02X".format(receivedChecksum)}, вычислено ${"%02X".format(calculatedChecksum)}")
                incomingBuffer.subList(0, totalPacketLen).clear()
                continue
            }

            addDebug("Пакет корректен, команда 0x${packet[5].toString(16)}")
            processResponsePacket(packet)
            incomingBuffer.subList(0, totalPacketLen).clear()
        }
    }

    private fun processResponsePacket(packet: ByteArray) {
        val cmd = packet[5].toInt() and 0xFF
        val status = packet[6].toInt() and 0xFF
        val data = packet.copyOfRange(7, packet.size - 1)

        if (status != 0x01) {
            addDebug("Статус ошибки: $status")
            return
        }

        when (cmd) {
            0x01, 0x45 -> parseTags(data, cmd)
            else -> addDebug("Неизвестная команда: 0x${cmd.toString(16)}")
        }
    }

    private fun parseTags(data: ByteArray, cmd: Int) {
        if (cmd == 0x45) {
            if (data.size < 8) {
                addDebug("Слишком мало данных для 0x45")
                return
            }
            var pos = 7
            val tagCount = data[pos].toInt() and 0xFF
            addDebug("Количество тегов: $tagCount")
            pos += 1
            for (i in 0 until tagCount) {
                if (pos + 1 > data.size) break
                val tagLen = data[pos].toInt() and 0xFF
                if (pos + 1 + tagLen > data.size) break
                val tagData = data.copyOfRange(pos + 1, pos + 1 + tagLen)
                if (tagData.size < 3) continue
                val epcBytes = tagData.copyOfRange(2, tagData.size - 1)
                val epcHex = epcBytes.joinToString("") { "%02X".format(it) }
                addDebug("Найден EPC: $epcHex")
                updateEpcCount(epcHex)
                pos += 1 + tagLen
            }
        } else {
            if (data.size < 2) {
                addDebug("Слишком мало данных для 0x01")
                return
            }
            val tagCount = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            addDebug("Количество тегов: $tagCount")
            var pos = 2
            for (i in 0 until tagCount) {
                if (pos + 1 > data.size) break
                val tagLen = data[pos].toInt() and 0xFF
                if (pos + 1 + tagLen > data.size) break
                val tagData = data.copyOfRange(pos + 1, pos + 1 + tagLen)
                if (tagData.size < 3) continue
                val epcBytes = tagData.copyOfRange(2, tagData.size - 1)
                val epcHex = epcBytes.joinToString("") { "%02X".format(it) }
                addDebug("Найден EPC: $epcHex")
                updateEpcCount(epcHex)
                pos += 1 + tagLen
            }
        }
    }

    private fun updateEpcCount(epc: String) {
        val newCount = (epcMap[epc] ?: 0) + 1
        epcMap[epc] = newCount
        // Создаём новый список с неизменяемыми объектами
        _epcList.value = epcMap.map { (epc, count) -> EpcItem(epc, count) }
            .sortedByDescending { it.count }
        addDebug("Счётчик для $epc увеличен до $newCount, всего элементов: ${epcMap.size}")
    }

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

    private fun addDebug(msg: String) {
        debugLog = "$msg\n$debugLog".take(500)
    }

    override fun onCleared() {
        super.onCleared()
        stopTest()
    }
}