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
                val fileName = channel.path.substringAfterLast("/")
                receiveFile(channel, fileName)
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

    private fun receiveFile(channel: ChannelClient.Channel, fileName: String) {
        viewModelScope.launch {
            _uiState.value = ReceiveUiState.Receiving(fileName, 0f)
            
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
                        copyStreamWithProgress(inputStream, outputStream) { progress ->
                            // We don't know total size natively from channel unless we send it, 
                            // so we will just show indeterminate progress or byte count
                            // For simplicity, we just pass the bytes copied as a float for now
                            _uiState.value = ReceiveUiState.Receiving(fileName, progress)
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
        onProgress: (Float) -> Unit
    ) {
        val buffer = ByteArray(8 * 1024)
        var bytesCopied = 0L
        var bytesRead = inputStream.read(buffer)
        while (bytesRead >= 0) {
            outputStream.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead
            // Since we don't know the file size from the watch side immediately,
            // we will pass the MB downloaded to the UI instead of a percentage (0-1)
            onProgress(bytesCopied.toFloat() / (1024f * 1024f)) 
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
    data class Receiving(val fileName: String, val mbReceived: Float) : ReceiveUiState
    data class Success(val fileName: String) : ReceiveUiState
    data class Error(val message: String) : ReceiveUiState
}
