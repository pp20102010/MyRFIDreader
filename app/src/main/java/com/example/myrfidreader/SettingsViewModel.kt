package com.example.myrfidreader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {

    private val _baudRate = MutableStateFlow(4) // по умолчанию 115200
    val baudRate: StateFlow<Int> = _baudRate

    private val _rfPower = MutableStateFlow(26) // максимум
    val rfPower: StateFlow<Int> = _rfPower

    private val _workMode = MutableStateFlow(1) // Active
    val workMode: StateFlow<Int> = _workMode

    fun applySettings(baudRate: Int, rfPower: Int, workMode: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!UsbConnectionHolder.isConnected.value) return@withContext
                sendOneParamCommand(0x07, baudRate.toByte())
                sendOneParamCommand(0x05, rfPower.toByte())
                sendOneParamCommand(0x02, workMode.toByte())

                _baudRate.value = baudRate
                _rfPower.value = rfPower
                _workMode.value = workMode
            }
        }
    }

    fun readSettings() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!UsbConnectionHolder.isConnected.value) return@withContext
                // Здесь можно реализовать запрос текущих параметров (CMD_READ_DEVICE_ONEPARAM)
                // Ответ будет приходить асинхронно. Пока оставим заглушку.
            }
        }
    }

    private fun sendOneParamCommand(paramAddr: Byte, value: Byte) {
        val cmd = buildCommand(paramAddr, value)
        UsbConnectionHolder.write(cmd)
    }

    private fun buildCommand(paramAddr: Byte, value: Byte): ByteArray {
        // Формируем команду как раньше (без порта)
        val data = byteArrayOf(paramAddr, value)
        val length = 3 + data.size // Addr + Cmd + Data + Checksum = 3 + data.size
        val cmd = ByteArray(4 + length) // Head(2) + Length(2) + тело
        cmd[0] = 0x53
        cmd[1] = 0x57
        cmd[2] = ((length shr 8) and 0xFF).toByte()
        cmd[3] = (length and 0xFF).toByte()
        cmd[4] = 0xFF.toByte() // broadcast
        cmd[5] = 0x24 // CMD_SET_DEVICE_ONEPARAM
        data.copyInto(cmd, 6)
        val checkPos = 4 + length - 1
        cmd[checkPos] = calculateChecksum(cmd, 2, checkPos - 2)
        return cmd
    }

    private fun calculateChecksum(data: ByteArray, start: Int, len: Int): Byte {
        var sum = 0
        for (i in start until start + len) {
            sum += data[i].toInt() and 0xFF
        }
        return ((sum.inv() + 1) and 0xFF).toByte()
    }
}