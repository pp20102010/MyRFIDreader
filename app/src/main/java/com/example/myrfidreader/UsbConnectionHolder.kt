package com.example.myrfidreader

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

object UsbConnectionHolder : SerialInputOutputManager.Listener {

    private var serialPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    fun openConnection(port: UsbSerialPort) {
        closeConnection() // закрываем предыдущее, если есть
        serialPort = port
        usbIoManager = SerialInputOutputManager(port, this)
        executor.submit(usbIoManager)
        _isConnected.value = true
    }

    fun closeConnection() {
        usbIoManager?.stop()
        usbIoManager = null
        try {
            serialPort?.close()
        } catch (e: Exception) { }
        serialPort = null
        _isConnected.value = false
    }

    override fun onNewData(data: ByteArray) {
        // Получены новые данные от ридера — отправляем всем слушателям
        UsbDataDispatcher.dispatchData(data)
    }

    override fun onRunError(e: Exception) {
        // При ошибке можно закрыть соединение и уведомить
        closeConnection()
    }

    fun write(data: ByteArray, timeout: Int = 1000): Boolean {
        return try {
            serialPort?.write(data, timeout) != null
        } catch (e: Exception) {
            false
        }
    }
}