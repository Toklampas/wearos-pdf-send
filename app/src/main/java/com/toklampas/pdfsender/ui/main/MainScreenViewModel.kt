package com.toklampas.pdfsender.ui.main

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class MainScreenViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<SendUiState>(SendUiState.Idle)
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    fun sendPdfToWatch(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SendUiState.FindingNode
            try {
                // Find connected node
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = nodeClient.connectedNodes.await()
                val watchNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()

                if (watchNode == null) {
                    _uiState.value = SendUiState.Error("No connected watch found.")
                    return@launch
                }

                // Get file name and size
                var fileName = "document.pdf"
                var fileSize = -1L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                _uiState.value = SendUiState.Connecting("Connecting to ${watchNode.displayName}...")

                // Open channel
                val channelClient = Wearable.getChannelClient(context)
                // We pass the filename in the path so the watch knows what to name it
                val channel = channelClient.openChannel(watchNode.id, "/pdf-transfer/$fileName").await()

                _uiState.value = SendUiState.Sending(fileName, 0f)

                // Send data with progress
                withContext(Dispatchers.IO) {
                    val outputStream = channelClient.getOutputStream(channel).await()
                    val inputStream = context.contentResolver.openInputStream(uri)

                    if (inputStream == null) {
                        _uiState.value = SendUiState.Error("Could not open selected file.")
                        channelClient.close(channel)
                        return@withContext
                    }

                    try {
                        copyStreamWithProgress(inputStream, outputStream, fileSize) { progress ->
                            _uiState.value = SendUiState.Sending(fileName, progress)
                        }
                        _uiState.value = SendUiState.Success
                    } catch (e: Exception) {
                        _uiState.value = SendUiState.Error("Transfer failed: ${e.message}")
                    } finally {
                        inputStream.close()
                        outputStream.close()
                        channelClient.close(channel)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SendUiState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun copyStreamWithProgress(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ) {
        val buffer = ByteArray(8 * 1024)
        var bytesCopied = 0L
        var bytesRead = inputStream.read(buffer)
        while (bytesRead >= 0) {
            outputStream.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead
            if (totalBytes > 0) {
                val progress = (bytesCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                onProgress(progress)
            } else {
                onProgress(-1f) // Indeterminate
            }
            bytesRead = inputStream.read(buffer)
        }
        outputStream.flush()
    }
    
    fun resetState() {
        _uiState.value = SendUiState.Idle
    }
}

sealed interface SendUiState {
    object Idle : SendUiState
    object FindingNode : SendUiState
    data class Connecting(val message: String) : SendUiState
    data class Sending(val fileName: String, val progress: Float) : SendUiState
    object Success : SendUiState
    data class Error(val message: String) : SendUiState
}
