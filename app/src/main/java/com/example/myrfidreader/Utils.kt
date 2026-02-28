package com.example.myrfidreader

fun baudRateToString(value: Int): String = when (value) {
    0 -> "9600"
    1 -> "19200"
    2 -> "38400"
    3 -> "57600"
    4 -> "115200"
    else -> "Неизвестно"
}

fun workModeToString(value: Int): String = when (value) {
    0 -> "Answer (опрос)"
    1 -> "Active (постоянно)"
    2 -> "Trigger (триггер)"
    else -> "Неизвестно"
}