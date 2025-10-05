package com.orion.downloader.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orion.downloader.R
import com.orion.downloader.model.DownloadItem
import com.orion.downloader.model.DownloadStatus
import com.orion.downloader.viewmodel.DownloadViewModel

@Composable
fun DownloadsScreen(
    viewModel: DownloadViewModel
) {
    val downloads by viewModel.downloads.collectAsState()
    
    var selectedFilter by remember { mutableStateOf(FilterType.ALL) }
    
    val filteredDownloads = when (selectedFilter) {
        FilterType.ALL -> downloads
        FilterType.ACTIVE -> downloads.filter { 
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED 
        }
        FilterType.COMPLETED -> downloads.filter { it.status == DownloadStatus.COMPLETED }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterChips(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
            modifier = Modifier.padding(16.dp)
        )

        if (filteredDownloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No downloads",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredDownloads, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        onStart = { viewModel.startDownload(item.id) },
                        onPause = { viewModel.pauseDownload(item.id) },
                        onResume = { viewModel.resumeDownload(item.id) },
                        onCancel = { viewModel.cancelDownload(item.id) },
                        onDelete = { viewModel.deleteDownload(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChips(
    selectedFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == FilterType.ALL,
            onClick = { onFilterSelected(FilterType.ALL) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selectedFilter == FilterType.ACTIVE,
            onClick = { onFilterSelected(FilterType.ACTIVE) },
            label = { Text(stringResource(R.string.active)) }
        )
        FilterChip(
            selected = selectedFilter == FilterType.COMPLETED,
            onClick = { onFilterSelected(FilterType.COMPLETED) },
            label = { Text(stringResource(R.string.completed)) }
        )
    }
}

@Composable
fun DownloadItemCard(
    item: DownloadItem,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = item.filename,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = item.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED) {
                LinearProgressIndicator(
                    progress = { item.percentage / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.percentage.toInt()}% - ${String.format("%.2f", item.downloadedMB)} / ${String.format("%.2f", item.totalMB)} MB",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${String.format("%.2f", item.speedMbps)} MB/s",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    text = when (item.status) {
                        DownloadStatus.PENDING -> "Pending"
                        DownloadStatus.COMPLETED -> stringResource(R.string.download_complete)
                        DownloadStatus.FAILED -> stringResource(R.string.download_failed)
                        DownloadStatus.CANCELLED -> "Cancelled"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (item.status) {
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (item.status) {
                    DownloadStatus.PENDING -> {
                        FilledTonalButton(
                            onClick = onStart,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.start_download))
                        }
                    }
                    DownloadStatus.DOWNLOADING -> {
                        FilledTonalButton(
                            onClick = onPause,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.pause))
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.cancel))
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        FilledTonalButton(
                            onClick = onResume,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.resume))
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.cancel))
                        }
                    }
                    DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

enum class FilterType {
    ALL, ACTIVE, COMPLETED
}
