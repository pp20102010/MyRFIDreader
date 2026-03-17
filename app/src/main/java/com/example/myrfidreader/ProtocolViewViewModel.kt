package com.example.myrfidreader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ProtocolViewViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private val file = File(context.filesDir, "experiment_log.txt")

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch(Dispatchers.IO) {
            val text = if (file.exists()) file.readText() else ""
            _content.value = text
        }
    }

    fun updateContent(newContent: String) {
        _content.value = newContent
    }

    fun saveContent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                file.writeText(_content.value)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Протокол сохранён", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importProtocol(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val text = BufferedReader(InputStreamReader(inputStream)).readText()
                    _content.value = text
                    // Сразу сохраняем в наш файл
                    file.writeText(text)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Протокол импортирован", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun clearProtocol() {
        viewModelScope.launch(Dispatchers.IO) {
            _content.value = ""
            if (file.exists()) file.delete()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Протокол очищен", Toast.LENGTH_SHORT).show()
            }
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
}