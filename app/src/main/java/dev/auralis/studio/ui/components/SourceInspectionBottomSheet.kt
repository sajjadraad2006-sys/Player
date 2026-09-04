package dev.auralis.studio.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.auralis.studio.R
import dev.auralis.studio.online.RecordingSource
import dev.auralis.studio.resolver.api.AudioQualityPreference
import dev.auralis.studio.resolver.api.DeliveryBlockReason
import dev.auralis.studio.resolver.api.DownloadCapability
import dev.auralis.studio.resolver.api.DrmState
import dev.auralis.studio.resolver.api.MediaResolver
import dev.auralis.studio.resolver.api.ResolutionMode
import dev.auralis.studio.resolver.api.ResolveIntent
import dev.auralis.studio.resolver.api.ResolvedFormatDescriptor
import dev.auralis.studio.resolver.api.ResolvedMedia
import dev.auralis.studio.resolver.api.ResolverDownloadDescriptor
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceInspectionBottomSheet(
    source: RecordingSource,
    resolver: MediaResolver,
    onDismiss: () -> Unit,
    onEnqueueDownload: (ResolverDownloadDescriptor) -> Unit,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var inspectedMedia by remember { mutableStateOf<ResolvedMedia?>(null) }
    var selectedFormatId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme

    val canonicalWatchUrl = source.canonicalUrl?.takeIf { it.isNotBlank() } ?: run {
        if (source.provider.equals("youtube", ignoreCase = true)) {
            "https://www.youtube.com/watch?v=${source.providerTrackId}"
        } else ""
    }

    LaunchedEffect(canonicalWatchUrl) {
        if (canonicalWatchUrl.isBlank()) {
            errorMessage = "No valid source URL available for inspection"
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        val req = ResolverRequest(
            sourceUrl = canonicalWatchUrl,
            sourceId = source.providerTrackId.takeIf { it.isNotBlank() },
            intent = ResolveIntent.INSPECT,
        )
        when (val res = resolver.inspect(req)) {
            is ResolverResult.Success -> {
                inspectedMedia = res.media
                val bestFmt = res.media.formats.firstOrNull {
                    it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                        it.drmState == DrmState.NONE &&
                        it.blockReason == DeliveryBlockReason.NONE
                } ?: res.media.formats.firstOrNull()
                selectedFormatId = bestFmt?.formatId
                isLoading = false
            }
            is ResolverResult.Failure -> {
                val err = res.error
                errorMessage = when (err.technicalCode) {
                    ResolverErrorCode.AuthenticationRequired ->
                        "This source requires account authentication and is not available right now."
                    ResolverErrorCode.AccessRestricted ->
                        "Access to this source is restricted by provider policy."
                    ResolverErrorCode.ChallengeFailed, ResolverErrorCode.JsRuntimeUnavailable ->
                        "Source verification challenge could not be completed."
                    else -> err.safeUserMessage
                }
                isLoading = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (source.artworkUrl?.isNotBlank() == true) {
                    AsyncImage(
                        model = source.artworkUrl,
                        contentDescription = source.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Audiotrack, contentDescription = null, tint = colors.primary)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inspectedMedia?.title ?: source.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = inspectedMedia?.artist ?: source.artist ?: source.provider,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Provider: ${source.provider.replaceFirstChar(Char::uppercase)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.outline,
                    )
                }
            }

            // ── Loading state ──────────────────────────────────────────────
            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Inspecting source with LRR engine...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            // ── Error state ────────────────────────────────────────────────
            errorMessage?.let { err ->
                Surface(
                    color = colors.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onErrorContainer,
                        )
                    }
                }
            }

            // ── Inspected formats and capability ───────────────────────────
            inspectedMedia?.let { media ->
                val directPermittedFormats = media.formats.filter {
                    it.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                        it.downloadCapability == DownloadCapability.PERMITTED &&
                        it.drmState == DrmState.NONE &&
                        it.blockReason == DeliveryBlockReason.NONE
                }

                // Capability status banner
                if (directPermittedFormats.isNotEmpty()) {
                    Surface(
                        color = colors.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Direct audio download ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onPrimaryContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else {
                    Surface(
                        color = colors.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    media.formats.any { it.drmState != DrmState.NONE } ->
                                        "DRM protected — downloading not permitted"
                                    media.formats.any { it.resolutionMode == ResolutionMode.SEGMENTED_RESOURCE } ->
                                        "Stream only (segmented media format)"
                                    else ->
                                        "Source is stream-only — download permission not verified"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Format candidates list
                if (media.formats.isNotEmpty()) {
                    Text(
                        text = "Available Formats",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.outline,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        media.formats.take(4).forEach { fmt ->
                            val isSelected = fmt.formatId == selectedFormatId
                            val isPermitted = fmt.resolutionMode == ResolutionMode.DIRECT_RESOURCE &&
                                fmt.downloadCapability == DownloadCapability.PERMITTED &&
                                fmt.drmState == DrmState.NONE

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.secondaryContainer else colors.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFormatId = fmt.formatId },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(
                                            text = formatLabel(fmt),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        Text(
                                            text = "${fmt.resolutionMode.name.replace('_', ' ')} · ${fmt.mediaKind.name.replace('_', ' ')}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.outline,
                                        )
                                    }
                                    if (isPermitted) {
                                        Text(
                                            text = "Permitted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (canonicalWatchUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(canonicalWatchUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Link")
                        }
                    }

                    if (directPermittedFormats.isNotEmpty()) {
                        Button(
                            onClick = {
                                val chosenFmt = media.formats.firstOrNull { it.formatId == selectedFormatId }
                                    ?: directPermittedFormats.first()
                                val descriptor = ResolverDownloadDescriptor(
                                    source = media.source,
                                    sourceId = media.sourceId ?: source.providerTrackId,
                                    canonicalUrl = canonicalWatchUrl,
                                    title = media.title ?: source.title,
                                    artist = media.artist ?: source.artist,
                                    album = media.album ?: source.album,
                                    artworkUrl = media.artworkUrl ?: source.artworkUrl,
                                    expectedDurationMs = media.durationMs ?: source.durationMs,
                                    qualityPreference = AudioQualityPreference.BEST,
                                    formatHint = chosenFmt.formatId,
                                    downloadCapability = DownloadCapability.PERMITTED,
                                )
                                onEnqueueDownload(descriptor)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatLabel(fmt: ResolvedFormatDescriptor): String {
    val containerStr = fmt.extension ?: fmt.container ?: "audio"
    val bitrateStr = fmt.bitrateKbps?.let { "${it.toInt()} kbps" }
    return listOfNotNull(containerStr.uppercase(), bitrateStr).joinToString(" · ")
}
