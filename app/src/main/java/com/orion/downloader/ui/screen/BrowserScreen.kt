package com.orion.downloader.ui.screen

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.orion.downloader.viewmodel.DownloadViewModel

@Composable
fun BrowserScreen(viewModel: DownloadViewModel) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("https://www.google.com") }
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadUrl by remember { mutableStateOf("") }
    var downloadFilename by remember { mutableStateOf("") }
    
    var webView: WebView? by remember { mutableStateOf(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { webView?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = if (canGoBack) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            IconButton(
                onClick = { webView?.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Avançar",
                    tint = if (canGoForward) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                placeholder = { Text("Digite URL ou pesquise...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Pesquisar", modifier = Modifier.size(20.dp))
                    }
                },
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { url = "" }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        val finalUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                            url
                        } else if (url.contains(".")) {
                            "https://$url"
                        } else {
                            "https://www.google.com/search?q=${Uri.encode(url)}"
                        }
                        webView?.loadUrl(finalUrl)
                    }
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            IconButton(
                onClick = { webView?.reload() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Recarregar", modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = {
                    url = "https://www.google.com"
                    webView?.loadUrl("https://www.google.com")
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = "Início", modifier = Modifier.size(20.dp))
            }
        }

        if (isLoading && loadingProgress > 0f) {
            LinearProgressIndicator(
                progress = { loadingProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportMultipleWindows(false)
                        userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            currentUrl = url ?: ""
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            currentUrl = url ?: ""
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?
                        ): Boolean {
                            return false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadingProgress = newProgress / 100f
                            if (newProgress == 100) {
                                isLoading = false
                            }
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                        }
                    }

                    setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                        downloadUrl = url
                        downloadFilename = filename
                        showDownloadDialog = true
                    }

                    loadUrl("https://www.google.com")
                    webView = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            update = { view ->
                webView = view
            }
        )
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Baixar Arquivo") },
            text = {
                Column {
                    Text("Deseja baixar este arquivo?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Arquivo: $downloadFilename",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "URL: ${downloadUrl.take(50)}${if (downloadUrl.length > 50) "..." else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addDownload(downloadUrl, downloadFilename)
                        showDownloadDialog = false
                    }
                ) {
                    Text("Baixar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}

@Composable
private fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled) {
        onBack()
    }
}
