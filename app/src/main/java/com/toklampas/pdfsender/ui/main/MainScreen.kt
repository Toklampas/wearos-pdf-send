package com.toklampas.pdfsender.ui.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendPdfToWatch(context, it) }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PdfSender",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { pdfPickerLauncher.launch("application/pdf") },
            enabled = state is SendUiState.Idle || state is SendUiState.Success || state is SendUiState.Error
        ) {
            Text("Select PDF to Send")
        }

        Spacer(modifier = Modifier.height(32.dp))

        when (val s = state) {
            is SendUiState.Idle -> {
                Text("Select a PDF to send to your watch.")
            }
            is SendUiState.FindingNode -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Finding connected watch...")
            }
            is SendUiState.Connecting -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(s.message)
            }
            is SendUiState.Sending -> {
                Text("Sending: ${s.fileName}")
                Spacer(modifier = Modifier.height(16.dp))
                if (s.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    )
                    Text("${(s.progress * 100).toInt()}%")
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                }
            }
            is SendUiState.Success -> {
                Text(
                    text = "File sent successfully!",
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = { viewModel.resetState() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Send Another")
                }
            }
            is SendUiState.Error -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Button(onClick = { viewModel.resetState() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Try Again")
                }
            }
        }
    }
}
