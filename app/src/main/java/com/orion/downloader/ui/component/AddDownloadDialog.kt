package com.orion.downloader.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orion.downloader.R

@Composable
fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var filename by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_download)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { 
                        url = it
                        if (filename.isEmpty() && it.isNotEmpty()) {
                            filename = it.substringAfterLast('/').substringBefore('?').ifEmpty { "download" }
                        }
                    },
                    label = { Text(stringResource(R.string.url)) },
                    placeholder = { Text(stringResource(R.string.enter_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
                
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text(stringResource(R.string.filename)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isNotEmpty() && filename.isNotEmpty()) {
                        onConfirm(url, filename)
                    }
                },
                enabled = url.isNotEmpty() && filename.isNotEmpty()
            ) {
                Text(stringResource(R.string.start_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
