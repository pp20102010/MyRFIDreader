package com.example.myrfidreader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RssiRecord(val epc: String, val rssi: Int)

class RssiTestViewModel : ViewModel(), OnDataReceivedListener {

    private val _records = MutableStateFlow<List<RssiRecord>>(emptyList())
    val records: StateFlow<List<RssiRecord>> = _records

    private val _isTestRunning = MutableStateFlow(false)
    val isTestRunning: StateFlow<Boolean> = _isTestRunning

    private val incomingBuffer = mutableListOf<Byte>()

    fun startTest() {
        if (_isTestRunning.value) return
        _records.value = emptyList()
        incomingBuffer.clear()
        _isTestRunning.value = true
        UsbDataDispatcher.registerListener(this)
    }

    fun stopTest() {
        _isTestRunning.value = false
        UsbDataDispatcher.unregisterListener(this)
    }

    override fun onDataReceived(data: ByteArray) {
        if (!_isTestRunning.value) return
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
                // Добавляем запись в начало списка
                addRecord(epcHex, rssi)
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
                addRecord(epcHex, rssi)
                pos += 1 + tagLen
            }
        }
    }

    private fun addRecord(epc: String, rssi: Int) {
        viewModelScope.launch {
            _records.value = listOf(RssiRecord(epc, rssi)) + _records.value
        }
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

    override fun onCleared() {
        super.onCleared()
        stopTest()
    }
}