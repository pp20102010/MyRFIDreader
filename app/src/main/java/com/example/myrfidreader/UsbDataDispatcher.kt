package com.example.myrfidreader

interface OnDataReceivedListener {
    fun onDataReceived(data: ByteArray)
}

object UsbDataDispatcher {
    private val listeners = mutableListOf<OnDataReceivedListener>()

    fun registerListener(listener: OnDataReceivedListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: OnDataReceivedListener) {
        listeners.remove(listener)
    }

    fun dispatchData(data: ByteArray) {
        listeners.forEach { it.onDataReceived(data) }
    }
}