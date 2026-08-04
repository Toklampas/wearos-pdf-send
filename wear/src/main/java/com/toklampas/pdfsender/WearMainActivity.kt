package com.toklampas.pdfsender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

class WearMainActivity : ComponentActivity() {

    private val viewModel: WearMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent watch screen from sleeping while this app is open
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PdfSender",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    when (val s = state) {
                        is ReceiveUiState.Waiting -> {
                            Text(
                                "Waiting for PDF from phone...",
                                textAlign = TextAlign.Center
                            )
                        }
                        is ReceiveUiState.Receiving -> {
                            val copiedMb = s.bytesCopied / (1024f * 1024f)
                            val totalMb = s.totalBytes / (1024f * 1024f)
                            
                            if (s.progress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Receiving ${s.fileName}",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${(s.progress * 100).toInt()}% (${String.format("%.2f", copiedMb)} / ${String.format("%.2f", totalMb)} MB)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Receiving ${s.fileName}",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${String.format("%.2f", copiedMb)} MB",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        is ReceiveUiState.Success -> {
                            Text(
                                "Saved ${s.fileName} to Downloads!",
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.resetState() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("OK")
                            }
                        }
                        is ReceiveUiState.Error -> {
                            Text(
                                s.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.resetState() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }
    }
}
