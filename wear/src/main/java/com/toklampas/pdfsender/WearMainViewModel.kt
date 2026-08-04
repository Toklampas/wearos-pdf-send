package com.toklampas.pdfsender

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class WearMainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ReceiveUiState>(ReceiveUiState.Waiting)
    val uiState: StateFlow<ReceiveUiState> = _uiState.asStateFlow()

    private val channelClient = Wearable.getChannelClient(application)
    private val channelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            if (channel.path.startsWith("/pdf-transfer/")) {
                val parts = channel.path.split("/")
                val fileName = parts.getOrNull(2) ?: "document.pdf"
                val fileSizeStr = parts.getOrNull(3) ?: "-1"
                val fileSize = fileSizeStr.toLongOrNull() ?: -1L
                receiveFile(channel, fileName, fileSize)
            }
        }
    }

    init {
        channelClient.registerChannelCallback(channelCallback)
    }

    override fun onCleared() {
        super.onCleared()
        channelClient.unregisterChannelCallback(channelCallback)
    }

    private fun receiveFile(channel: ChannelClient.Channel, fileName: String, fileSize: Long) {
        viewModelScope.launch {
            _uiState.value = ReceiveUiState.Receiving(fileName, 0f, 0L, fileSize)
            
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = channelClient.getInputStream(channel).await()
                    
                    val context = getApplication<Application>()
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }
                    }

                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    } else {
                        // Fallback for older APIs if needed, though minSdk is 30
                        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    }

                    if (uri == null) {
                        _uiState.value = ReceiveUiState.Error("Failed to create file in Downloads.")
                        channelClient.close(channel)
                        return@withContext
                    }

                    val outputStream = resolver.openOutputStream(uri)
                    if (outputStream == null) {
                        _uiState.value = ReceiveUiState.Error("Failed to open file for writing.")
                        channelClient.close(channel)
                        return@withContext
                    }

                    try {
                        copyStreamWithProgress(inputStream, outputStream, fileSize) { progress, copied, total ->
                            _uiState.value = ReceiveUiState.Receiving(fileName, progress, copied, total)
                        }
                        _uiState.value = ReceiveUiState.Success(fileName)
                    } finally {
                        inputStream.close()
                        outputStream.close()
                        channelClient.close(channel)
                    }
                } catch (e: Exception) {
                    _uiState.value = ReceiveUiState.Error("Error receiving file: ${e.message}")
                    channelClient.close(channel)
                }
            }
        }
    }

    private suspend fun copyStreamWithProgress(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long,
        onProgress: (Float, Long, Long) -> Unit
    ) {
        val buffer = ByteArray(8 * 1024)
        var bytesCopied = 0L
        var bytesRead = inputStream.read(buffer)
        while (bytesRead >= 0) {
            outputStream.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead
            if (totalBytes > 0) {
                val progress = (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                onProgress(progress, bytesCopied, totalBytes)
            } else {
                onProgress(-1f, bytesCopied, totalBytes)
            }
            bytesRead = inputStream.read(buffer)
        }
        outputStream.flush()
    }
    
    fun resetState() {
        _uiState.value = ReceiveUiState.Waiting
    }
}

sealed interface ReceiveUiState {
    object Waiting : ReceiveUiState
    data class Receiving(val fileName: String, val progress: Float, val bytesCopied: Long, val totalBytes: Long) : ReceiveUiState
    data class Success(val fileName: String) : ReceiveUiState
    data class Error(val message: String) : ReceiveUiState
}
