package com.example.ui.history

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DownloadStatus
import com.example.domain.ZipStorageManager
import com.example.ui.components.FormatUtils
import com.example.ui.theme.AxionError
import com.example.ui.theme.AxionSuccess
import com.example.ui.theme.AxionWarning

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.historyItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingDeleteItem by remember { mutableStateOf<HistoryItemUi?>(null) }
    var alsoDeleteZipFile by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Download History",
                    style = MaterialTheme.typography.headlineMedium
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = { Text("Search manga, chapter, or ZIP...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search history"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { viewModel.updateFilter(filter) },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_history),
                            contentDescription = "Empty download history",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(220.dp, 165.dp)
                                .clip(RoundedCornerShape(22.dp))
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "No Archived Chapters Yet" else "No Matching Chapters",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "Completed chapter ZIP archives will appear here with instant Open and Share shortcuts."
                            } else {
                                "Try a different manga title or filter."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(items, key = { it.task.id }) { item ->
                        HistoryEntryCard(
                            item = item,
                            onOpen = {
                                val uri = item.task.outputUri
                                if (uri != null && item.fileExistsOnDisk) {
                                    runCatching {
                                        context.startActivity(ZipStorageManager.createOpenZipIntent(uri))
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            "No ZIP viewer app found. Use Share or your Files app.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "ZIP file is missing from storage.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onShare = {
                                val uri = item.task.outputUri
                                if (uri != null && item.fileExistsOnDisk) {
                                    runCatching {
                                        context.startActivity(
                                            ZipStorageManager.createShareZipIntent(
                                                uriString = uri,
                                                title = item.task.zipFilename
                                            )
                                        )
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            "Unable to share ZIP file.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "ZIP file is missing from storage.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onDeleteClick = {
                                alsoDeleteZipFile = false
                                pendingDeleteItem = item
                            }
                        )
                    }
                }
            }
        }
    }

    val deleteTarget = pendingDeleteItem
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text("Remove History Entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Remove \"${deleteTarget.task.mangaTitle} • ${deleteTarget.task.chapterTitle}\" from Download History?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (deleteTarget.fileExistsOnDisk) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { alsoDeleteZipFile = !alsoDeleteZipFile }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = alsoDeleteZipFile,
                                onCheckedChange = { alsoDeleteZipFile = it }
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Also delete ZIP file from device",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Leave unchecked to keep ${deleteTarget.task.zipFilename} in Downloads.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEntry(deleteTarget.task.id, alsoDeleteZipFile)
                        pendingDeleteItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AxionError)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text("Keep")
                }
            }
        )
    }
}

@Composable
private fun HistoryEntryCard(
    item: HistoryItemUi,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val task = item.task
    val (statusLabel, statusColor, statusIcon) = when {
        task.status == DownloadStatus.COMPLETED && item.fileExistsOnDisk ->
            Triple("Saved", AxionSuccess, Icons.Rounded.CheckCircle)
        task.status == DownloadStatus.COMPLETED && !item.fileExistsOnDisk ->
            Triple("File Missing", AxionWarning, Icons.Rounded.WarningAmber)
        else ->
            Triple("Failed", AxionError, Icons.Rounded.ErrorOutline)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.mangaTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Chapter ${task.chapterNumber} • ${task.chapterTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = statusLabel,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
            }

            // ZIP File Info
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderZip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.zipFilename,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sizeText = if (task.outputSizeBytes > 0) " • ${FormatUtils.formatBytes(task.outputSizeBytes)}" else ""
                        val dupText = if (task.skippedDuplicates > 0) " • ${task.skippedDuplicates} dup skipped" else ""
                        Text(
                            text = "${task.downloadedPages} pages$sizeText$dupText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = FormatUtils.formatDateTime(task.completedAt ?: task.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!task.errorMessage.isNullOrBlank() && task.status == DownloadStatus.FAILED) {
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = AxionError
                )
            }

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status == DownloadStatus.COMPLETED && item.fileExistsOnDisk) {
                    Button(
                        onClick = onOpen,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("history_open_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Open")
                    }

                    OutlinedButton(
                        onClick = onShare,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("history_share_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("history_delete_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete history entry",
                        tint = AxionError
                    )
                }
            }
        }
    }
}
