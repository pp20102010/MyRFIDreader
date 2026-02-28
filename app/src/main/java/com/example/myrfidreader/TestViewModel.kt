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

data class EpcItem(val epc: String, var count: Int)

class TestViewModel : ViewModel(), OnDataReceivedListener {

    private val _epcList = MutableStateFlow<List<EpcItem>>(emptyList())
    val epcList: StateFlow<List<EpcItem>> = _epcList

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

    private val _isTestRunning = MutableStateFlow(false)
    val isTestRunning: StateFlow<Boolean> = _isTestRunning

    var lastRawResponse by mutableStateOf("")
        private set

    var debugInfo by mutableStateOf("")
        private set

    private var startTime = 0L
    private val epcMap = mutableMapOf<String, EpcItem>()
    private val incomingBuffer = mutableListOf<Byte>()

    fun startTest() {
        if (_isTestRunning.value) return
        epcMap.clear()
        _epcList.value = emptyList()
        incomingBuffer.clear()
        lastRawResponse = ""
        debugInfo = ""
        startTime = System.currentTimeMillis()
        _isTestRunning.value = true

        viewModelScope.launch {
            while (_isTestRunning.value) {
                _elapsedSeconds.value = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }

        UsbDataDispatcher.registerListener(this)
        addDebugMessage("Тест запущен")
    }

    fun stopTest() {
        _isTestRunning.value = false
        UsbDataDispatcher.unregisterListener(this)
        addDebugMessage("Тест остановлен")
    }

    override fun onDataReceived(data: ByteArray) {
        addDebugMessage("Данные получены: ${data.size} байт")
        incomingBuffer.addAll(data.toList())
        addDebugMessage("Буфер: ${incomingBuffer.size} байт")
        processIncomingBuffer()
    }

    private fun processIncomingBuffer() {
        while (true) {
            val headerIndex = findHeader(incomingBuffer, byteArrayOf(0x43, 0x54))
            if (headerIndex == -1) {
                addDebugMessage("Заголовок не найден, ждём...")
                break
            }
            addDebugMessage("Заголовок найден на позиции $headerIndex")
            if (headerIndex > 0) {
                incomingBuffer.subList(0, headerIndex).clear()
                addDebugMessage("Удалён мусор до заголовка")
            }
            if (incomingBuffer.size < 8) {
                addDebugMessage("Меньше 8 байт, ждём...")
                break
            }
            val length = ((incomingBuffer[2].toInt() and 0xFF) shl 8) or (incomingBuffer[3].toInt() and 0xFF)
            val totalPacketLen = 4 + length
            addDebugMessage("Длина пакета: $length, всего байт: $totalPacketLen")
            if (incomingBuffer.size < totalPacketLen) {
                addDebugMessage("Не хватает данных: есть ${incomingBuffer.size}, нужно $totalPacketLen")
                break
            }
            val packet = incomingBuffer.subList(0, totalPacketLen).toByteArray()
            lastRawResponse = packet.joinToString(" ") { "%02X".format(it) }
            addDebugMessage("Пакет извлечён, первые 10 байт: ${packet.take(10).joinToString("") { "%02X".format(it) }}")

            // Проверка контрольной суммы
            val checkPos = totalPacketLen - 1
            val receivedChecksum = packet[checkPos]
            val dataForChecksum = packet.copyOfRange(0, checkPos)
            val calculatedChecksum = calculateChecksum(dataForChecksum)
            if (receivedChecksum != calculatedChecksum) {
                addDebugMessage("Ошибка контрольной суммы: получено ${"%02X".format(receivedChecksum)}, вычислено ${"%02X".format(calculatedChecksum)}")
                incomingBuffer.subList(0, totalPacketLen).clear()
                continue
            }
            addDebugMessage("Контрольная сумма OK")
            processResponsePacket(packet)
            incomingBuffer.subList(0, totalPacketLen).clear()
            addDebugMessage("Пакет удалён из буфера")
        }
    }

    private fun processResponsePacket(packet: ByteArray) {
        val cmd = packet[5].toInt() and 0xFF
        val status = packet[6].toInt() and 0xFF
        addDebugMessage("Команда: 0x${cmd.toString(16)}, статус: $status")
        val data = packet.copyOfRange(7, packet.size - 1)
        if (status != 0x01) {
            addDebugMessage("Статус не успех (0x01)")
            return
        }
        when (cmd) {
            0x01, 0x45 -> parseTags(data, cmd)
            else -> addDebugMessage("Неизвестная команда: 0x${cmd.toString(16)}")
        }
    }

    private fun parseTags(data: ByteArray, cmd: Int) {
        addDebugMessage("Парсинг тегов для команды 0x${cmd.toString(16)}")
        if (cmd == 0x45) {
            // Формат для CMD_ACTIVE_DATA
            if (data.size < 8) {
                addDebugMessage("Слишком мало данных для команды 0x45")
                return
            }
            var pos = 7
            val tagCount = data[pos].toInt() and 0xFF
            addDebugMessage("Количество тегов: $tagCount")
            pos += 1
            for (i in 0 until tagCount) {
                if (pos + 1 > data.size) {
                    addDebugMessage("Недостаточно данных для тега $i")
                    break
                }
                val tagLen = data[pos].toInt() and 0xFF
                addDebugMessage("Длина тега $i: $tagLen")
                if (pos + 1 + tagLen > data.size) {
                    addDebugMessage("Данные тега $i выходят за границы")
                    break
                }
                val tagData = data.copyOfRange(pos + 1, pos + 1 + tagLen)
                if (tagData.size < 3) {
                    addDebugMessage("Данные тега слишком короткие")
                    continue
                }
                val epcBytes = tagData.copyOfRange(2, tagData.size - 1)
                val epcHex = epcBytes.joinToString("") { "%02X".format(it) }
                addDebugMessage("Найден EPC: $epcHex")
                updateEpcCount(epcHex)
                pos += 1 + tagLen
            }
        } else {
            // Формат для CMD_INVENTORY_TAG (0x01)
            if (data.size < 2) {
                addDebugMessage("Слишком мало данных для команды 0x01")
                return
            }
            val tagCount = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            addDebugMessage("Количество тегов: $tagCount")
            var pos = 2
            for (i in 0 until tagCount) {
                if (pos + 1 > data.size) {
                    addDebugMessage("Недостаточно данных для тега $i")
                    break
                }
                val tagLen = data[pos].toInt() and 0xFF
                addDebugMessage("Длина тега $i: $tagLen")
                if (pos + 1 + tagLen > data.size) {
                    addDebugMessage("Данные тега $i выходят за границы")
                    break
                }
                val tagData = data.copyOfRange(pos + 1, pos + 1 + tagLen)
                if (tagData.size < 3) {
                    addDebugMessage("Данные тега слишком короткие")
                    continue
                }
                val epcBytes = tagData.copyOfRange(2, tagData.size - 1)
                val epcHex = epcBytes.joinToString("") { "%02X".format(it) }
                addDebugMessage("Найден EPC: $epcHex")
                updateEpcCount(epcHex)
                pos += 1 + tagLen
            }
        }
    }

    private fun updateEpcCount(epc: String) {
        val item = epcMap[epc]
        if (item != null) {
            item.count++
        } else {
            epcMap[epc] = EpcItem(epc, 1)
        }
        _epcList.value = epcMap.values.sortedByDescending { it.count }
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

    private fun addDebugMessage(msg: String) {
        debugInfo = "$msg\n$debugInfo".take(500)
    }

    override fun onCleared() {
        super.onCleared()
        stopTest()
    }
}