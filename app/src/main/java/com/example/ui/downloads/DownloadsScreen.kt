package com.example.ui.downloads

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.data.local.DownloadTaskEntity
import com.example.data.model.DownloadStatus
import com.example.domain.ZipStorageManager
import com.example.ui.components.FormatUtils
import com.example.ui.theme.AxionError
import com.example.ui.theme.AxionSuccess
import com.example.ui.theme.AxionWarning

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onGoToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    val activeCount = tasks.count { it.status.isActive }
                    Text(
                        text = if (activeCount > 0) "$activeCount active chapter transfer(s)" else "${tasks.size} task(s) in queue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tasks.isEmpty()) {
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
                            painter = painterResource(id = R.drawable.img_empty_downloads),
                            contentDescription = "No active downloads",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(220.dp, 165.dp)
                                .clip(RoundedCornerShape(22.dp))
                        )
                        Text(
                            text = "No Active Downloads",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Browse Thunder Scans, open any Manga or Manhwa chapter, and tap Download Chapter to build a sequential ZIP archive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onGoToBrowser,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("empty_downloads_browser_btn")
                        ) {
                            Text("Open Thunder Scans Browser")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            onCancel = { viewModel.cancelDownload(task.id) },
                            onResume = { viewModel.resumeDownload(task.id) },
                            onRetry = { viewModel.retryDownload(task.id) },
                            onOpenZip = {
                                val uri = task.outputUri
                                if (uri != null && ZipStorageManager.verifyZipExists(context, uri)) {
                                    runCatching {
                                        context.startActivity(ZipStorageManager.createOpenZipIntent(uri))
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            "No ZIP viewer app installed. Share or open from your Files app.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "ZIP file is no longer available on storage.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onDismiss = { viewModel.dismissTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onOpenZip: () -> Unit,
    onDismiss: () -> Unit
) {
    val total = task.totalPages.coerceAtLeast(1)
    val done = task.downloadedPages.coerceIn(0, total)
    val remaining = (total - done).coerceAtLeast(0)
    val rawProgress = when (task.status) {
        DownloadStatus.CREATING_ZIP -> 1f
        DownloadStatus.COMPLETED -> 1f
        else -> (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(targetValue = rawProgress, label = "dl_progress")
    val percentInt = (rawProgress * 100).toInt()

    val (statusLabel, statusColor, statusIcon) = when (task.status) {
        DownloadStatus.QUEUED -> Triple("Queued", MaterialTheme.colorScheme.secondary, Icons.Rounded.FolderZip)
        DownloadStatus.DETECTING -> Triple("Detecting Pages", MaterialTheme.colorScheme.secondary, Icons.Rounded.FolderZip)
        DownloadStatus.DOWNLOADING -> Triple("Downloading $percentInt%", MaterialTheme.colorScheme.primary, Icons.Rounded.FolderZip)
        DownloadStatus.CREATING_ZIP -> Triple("Creating ZIP ${task.zipCreationProgress}%", MaterialTheme.colorScheme.secondary, Icons.Rounded.Archive)
        DownloadStatus.COMPLETED -> Triple("Completed", AxionSuccess, Icons.Rounded.CheckCircle)
        DownloadStatus.FAILED -> Triple("Failed", AxionError, Icons.Rounded.ErrorOutline)
        DownloadStatus.CANCELLED -> Triple("Paused / Cancelled", AxionWarning, Icons.Rounded.PauseCircleOutline)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_task_card_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Title & Status Badge
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
                        text = task.chapterTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    if (!task.status.isActive) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dismiss_download_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Dismiss from queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pages: $done / ${task.totalPages}  •  Remaining: $remaining",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (task.status == DownloadStatus.DOWNLOADING && task.speedBytesPerSec > 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "Download speed",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = FormatUtils.formatSpeed(task.speedBytesPerSec),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Text(
                        text = "$percentInt%",
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }
            }

            // Progress Bar
            if (task.status == DownloadStatus.CREATING_ZIP) {
                val zipFrac = (task.zipCreationProgress / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { if (task.zipCreationProgress > 0) zipFrac else 0.1f },
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Text(
                    text = "Compressing & verifying ZIP archive (${task.zipCreationProgress}%)...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            // Skipped Duplicates Report
            if (task.skippedDuplicates > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AxionWarning.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Skipped ${task.skippedDuplicates} duplicate image(s): ${task.skippedDuplicateInfo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AxionWarning,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Output Path / Size on Completion
            if (task.status == DownloadStatus.COMPLETED) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AxionSuccess.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Saved: ${task.outputDisplayPath} (${FormatUtils.formatBytes(task.outputSizeBytes)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AxionSuccess,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            // Error Message
            if (!task.errorMessage.isNullOrBlank()) {
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == DownloadStatus.CANCELLED) AxionWarning else AxionError
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    task.status.isActive -> {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AxionError.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AxionError),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("cancel_download_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel Download")
                        }
                    }

                    task.status.canResume -> {
                        Button(
                            onClick = onResume,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("resume_download_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Resume")
                        }
                    }

                    task.status.canRetry -> {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("retry_download_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Retry Failed")
                        }
                    }

                    task.status == DownloadStatus.COMPLETED -> {
                        Button(
                            onClick = onOpenZip,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_zip_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Open ZIP Archive")
                        }
                    }
                }
            }
        }
    }
}
