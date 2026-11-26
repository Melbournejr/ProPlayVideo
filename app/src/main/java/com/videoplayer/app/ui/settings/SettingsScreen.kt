package com.videoplayer.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.BuildConfig
import com.videoplayer.app.data.local.*
import com.videoplayer.app.ui.components.*
import com.videoplayer.app.ui.main.LanWebServerSettingsCard
import com.videoplayer.app.update.GitHubRelease
import com.videoplayer.app.update.UpdateManager
import com.videoplayer.app.update.VersionMetadata
import com.videoplayer.app.util.FileOperationHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val style = LocalGlassStyle.current
    val palette = style.palette
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    val activeEngineId by context.observeThemeEngineMode().collectAsState(initial = ThemeEngineId.LIQUID_GLASS.id)
    val activeSubVariantId by context.observeThemeSubVariant().collectAsState(initial = "cinema_imax")
    val currentThemeId by context.observeGlassTheme().collectAsState(initial = "apple_ice")
    val currentTintId by context.observeGlassTint().collectAsState(initial = "crystal_white")
    val currentFrostAlpha by context.observeGlassFrostAlpha().collectAsState(initial = 0.20f)
    val reduceTrans by context.observeReduceTransparency().collectAsState(initial = false)
    val isGlassLight by context.observeGlassLightMode().collectAsState(initial = false)

    var showThemeCustomize by remember { mutableStateOf(true) }
    var showAdvancedOta by remember { mutableStateOf(false) }

    val savedRepo by context.readGithubRepo("Melbournejr/ProPlayVideo").collectAsState(initial = "Melbournejr/ProPlayVideo")
    var repoInput by remember(savedRepo) { mutableStateOf(savedRepo) }
    var selectedMirror by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Hi\u1EC7n t\u1EA1i: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var latestMeta by remember { mutableStateOf<VersionMetadata?>(null) }
    var isNewerAvailable by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var speedInfo by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    val sectionCorner = if (style.isProMonitor) 6.dp else 20.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. NHOM 4 BO GIAO DIEN DOI TOAN BO PHONG CACH
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(sectionCorner))
                .background(style.cardSurfaceColor)
                .border(0.9.dp, style.cardBorderColor, RoundedCornerShape(sectionCorner))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Palette, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Giao di\u1EC7n & Phong c\u00E1ch", style = MaterialTheme.typography.titleSmall, color = style.textPrimary, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = if (showThemeCustomize) "Thu g\u1ECDn" else "T\u00F9y ch\u1EC9nh",
                    color = palette.primaryGlow,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showThemeCustomize = !showThemeCustomize }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ThemeEngineId.values(), key = { it.id }) { engine ->
                    val isSelected = (style.engineId == engine.id)
                    val cardR = if (engine == ThemeEngineId.PRO_MONITOR) 6.dp else 16.dp
                    Column(
                        modifier = Modifier
                            .width(156.dp)
                            .clip(RoundedCornerShape(cardR))
                            .background(
                                if (isSelected) palette.primaryGlow.copy(alpha = if (style.isLightMode) 0.16f else 0.20f)
                                else style.textPrimary.copy(alpha = 0.05f)
                            )
                            .border(
                                width = if (isSelected) 1.4.dp else 0.7.dp,
                                color = if (isSelected) palette.primaryGlow else style.cardBorderColor,
                                shape = RoundedCornerShape(cardR)
                            )
                            .clickable {
                                scope.launch {
                                    context.saveThemeEngineMode(engine.id)
                                    if (engine != ThemeEngineId.LIQUID_GLASS) {
                                        val firstVar = ThemeSubVariants.firstOrNull { it.engineId == engine.id }
                                        val matches = ThemeSubVariants.any { it.engineId == engine.id && it.id == activeSubVariantId }
                                        if (!matches && firstVar != null) {
                                            context.saveThemeSubVariant(firstVar.id)
                                        }
                                    }
                                }
                            }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = engine.title,
                            color = if (isSelected) palette.primaryGlow else style.textPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = engine.subtitle,
                            color = style.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showThemeCustomize) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (style.isLightMode) Color(0xFF0F172A).copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.30f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (style.isLiquidGlass) {
                        // Cong tac Chuyen che do Sang (Daylight) / Toi (Midnight)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ch\u1EBF \u0111\u1ED9 S\u00E1ng (Daylight Glass)", color = style.textPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text("Chuy\u1EC3n to\u00E0n b\u1ED9 giao di\u1EC7n sang n\u1EC1n k\u00EDnh s\u00E1ng", color = style.textSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(
                                checked = isGlassLight,
                                onCheckedChange = { enabled -> scope.launch { context.saveGlassLightMode(enabled) } }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gi\u1EA3m \u0111\u1ED9 trong su\u1ED1t", color = style.textPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text("T\u0103ng t\u01B0\u01A1ng ph\u1EA3n cho c\u00E1c thanh k\u00EDnh", color = style.textSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(
                                checked = reduceTrans,
                                onCheckedChange = { enabled -> scope.launch { context.saveReduceTransparency(enabled) } }
                            )
                        }

                        val pct = ((currentFrostAlpha / 0.45f) * 100).roundToInt().coerceIn(8, 100)
                        Text("\u0110\u1ED9 m\u1EDD k\u00EDnh: $pct%", style = MaterialTheme.typography.labelSmall, color = style.textSecondary)
                        Slider(
                            value = currentFrostAlpha,
                            onValueChange = { scope.launch { context.saveGlassFrostAlpha(it) } },
                            valueRange = 0.04f..0.45f,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.primaryGlow,
                                activeTrackColor = palette.primaryGlow,
                                inactiveTrackColor = style.textSecondary.copy(alpha = 0.25f)
                            )
                        )

                        Text("T\u00F4ng m\u00E0u nh\u1EA5n", style = MaterialTheme.typography.labelMedium, color = palette.primaryGlow, fontWeight = FontWeight.SemiBold)
                        GlassThemePresets.forEach { preset ->
                            val selected = (preset.id == currentThemeId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) preset.primaryGlow.copy(alpha = 0.16f) else style.textPrimary.copy(alpha = 0.04f))
                                    .border(
                                        width = if (selected) 1.2.dp else 0.5.dp,
                                        color = if (selected) preset.primaryGlow else style.cardBorderColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { scope.launch { context.saveGlassTheme(preset.id) } }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Brush.linearGradient(listOf(preset.primaryGlow, preset.secondaryGlow))))
                                    Spacer(Modifier.width(10.dp))
                                    Text(preset.name, color = style.textPrimary, style = MaterialTheme.typography.labelMedium)
                                }
                                if (selected) Text("\u2713", color = preset.primaryGlow, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!isGlassLight) {
                            Text("M\u00E0u ph\u1EE7 k\u00EDnh", style = MaterialTheme.typography.labelMedium, color = palette.primaryGlow, fontWeight = FontWeight.SemiBold)
                            GlassTintPresets.forEach { tintOpt ->
                                val selected = (tintOpt.id == currentTintId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) tintOpt.surfaceColor.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.04f))
                                        .border(
                                            width = if (selected) 1.2.dp else 0.5.dp,
                                            color = if (selected) tintOpt.rimTint else Color.White.copy(alpha = 0.10f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { scope.launch { context.saveGlassTint(tintOpt.id) } }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(tintOpt.rimTint))
                                        Spacer(Modifier.width(10.dp))
                                        Text(tintOpt.name, color = style.textPrimary, style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (selected) Text("\u2713", color = palette.primaryGlow, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        val subList = ThemeSubVariants.filter { it.engineId == style.engineId }
                        Text("Ph\u1ED1i m\u00E0u phong c\u00E1ch", style = MaterialTheme.typography.labelMedium, color = palette.primaryGlow, fontWeight = FontWeight.SemiBold)
                        subList.forEach { variant ->
                            val selected = (style.subVariantId == variant.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(if (style.isProMonitor) 5.dp else 10.dp))
                                    .background(if (selected) variant.primaryAccent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f))
                                    .border(
                                        width = if (selected) 1.2.dp else 0.5.dp,
                                        color = if (selected) variant.primaryAccent else style.cardBorderColor,
                                        shape = RoundedCornerShape(if (style.isProMonitor) 5.dp else 10.dp)
                                    )
                                    .clickable {
                                        scope.launch {
                                            context.saveThemeEngineMode(style.engineId)
                                            context.saveThemeSubVariant(variant.id)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Brush.linearGradient(listOf(variant.primaryAccent, variant.secondaryAccent))))
                                    Spacer(Modifier.width(10.dp))
                                    Text(variant.name, color = style.textPrimary, style = MaterialTheme.typography.labelMedium)
                                }
                                if (selected) Text("\u2713", color = variant.primaryAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. LOCAL LAN WEB SERVER
        LanWebServerSettingsCard()

        // 3. QUYEN QUAN LY TEP
        if (!FileOperationHelper.hasAllFilesAccess()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(sectionCorner))
                    .background(style.cardSurfaceColor)
                    .border(0.9.dp, palette.primaryGlow.copy(alpha = 0.55f), RoundedCornerShape(sectionCorner))
                    .clickable { FileOperationHelper.requestAllFilesAccess(context) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AppIcons.DriveFileMove, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("C\u1EA5p quy\u1EC1n Qu\u1EA3n l\u00FD t\u1EC7p", color = style.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("B\u1EADt 1 l\u1EA7n \u0111\u1EC3 di chuy\u1EC3n video t\u1EE9c th\u00EC kh\u00F4ng h\u1ECFi x\u00F3a", color = style.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 4. CAP NHAT OTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(sectionCorner))
                .background(style.cardSurfaceColor)
                .border(0.9.dp, style.cardBorderColor, RoundedCornerShape(sectionCorner))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.CloudDownload, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("C\u1EADp nh\u1EADt ph\u1EA7n m\u1EC1m (OTA)", style = MaterialTheme.typography.titleSmall, color = style.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = if (isNewerAvailable) palette.primaryGlow else style.textSecondary)
                    }
                }
                Text(
                    text = if (showAdvancedOta) "\u1EA8n" else "N\u00E2ng cao",
                    color = style.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showAdvancedOta = !showAdvancedOta }.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            LiquidGlassPill(
                active = true,
                onClick = {
                    if (!isDownloading) {
                        if (!updateManager.isValidRepoFormat(repoInput)) {
                            statusText = "\u0110\u1ECBnh d\u1EA1ng Repo ph\u1EA3i l\u00E0 owner/repo"
                            return@LiquidGlassPill
                        }
                        scope.launch {
                            context.saveGithubRepo(repoInput)
                            statusText = "\u0110ang ki\u1EC3m tra t\u1EEB GitHub..."
                            val result = updateManager.checkLatestRelease(repoInput)
                            result.onSuccess { (release, meta) ->
                                latestRelease = release
                                latestMeta = meta
                                val newer = updateManager.isRemoteVersionCodeNewer(BuildConfig.VERSION_CODE, meta)
                                isNewerAvailable = newer
                                statusText = if (newer) "C\u00F3 b\u1EA3n m\u1EDBi: v${meta.versionName} (${meta.versionCode})"
                                else "\u0110ang d\u00F9ng b\u1EA3n m\u1EDBi nh\u1EA5t (v${BuildConfig.VERSION_NAME})"
                            }.onFailure { err ->
                                statusText = "L\u1ED7i: ${err.message}"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text("Ki\u1EC3m tra c\u1EADp nh\u1EADt", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }

            val currentRel = latestRelease
            val currentMeta = latestMeta
            if (currentRel != null && currentMeta != null && isNewerAvailable) {
                val apkAsset = currentRel.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                if (apkAsset != null) {
                    if (downloadProgress in 0..100) {
                        LinearProgressIndicator(
                            progress = downloadProgress / 100f,
                            color = palette.primaryGlow,
                            trackColor = style.textSecondary.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                        Text("\u0110ang t\u1EA3i: $downloadProgress% ($speedInfo)", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall)
                    }
                    LiquidGlassPill(
                        active = true,
                        onClick = {
                            if (!isDownloading) {
                                if (!updateManager.canRequestPackageInstalls()) {
                                    updateManager.openManageUnknownAppSourcesSettings()
                                    return@LiquidGlassPill
                                }
                                scope.launch {
                                    isDownloading = true
                                    downloadProgress = 0
                                    val dl = updateManager.downloadAndVerifyApk(
                                        rawUrl = apkAsset.browser_download_url,
                                        expectedSize = apkAsset.size,
                                        expectedSha256 = currentMeta.sha256,
                                        mirrorIndex = selectedMirror
                                    ) { p, spd ->
                                        downloadProgress = p
                                        speedInfo = spd
                                    }
                                    isDownloading = false
                                    dl.onSuccess { verifiedFile ->
                                        statusText = "\u0110\u00E3 x\u00E1c th\u1EF1c SHA-256. \u0110ang c\u00E0i \u0111\u1EB7t..."
                                        updateManager.installVerifiedApk(verifiedFile)
                                    }.onFailure { err ->
                                        statusText = "L\u1ED7i: ${err.message}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text(if (isDownloading) "\u0110ang t\u1EA3i & x\u00E1c minh..." else "T\u1EA3i v\u00E0 C\u00E0i \u0111\u1EB7t ngay", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            AnimatedVisibility(visible = showAdvancedOta) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Divider(color = style.cardBorderColor)
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("GitHub Repo (owner/repo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LiquidGlassPill(active = selectedMirror == 0, onClick = { selectedMirror = 0 }) {
                            Text("GitHub", color = if (selectedMirror == 0) Color.White else style.textPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                        LiquidGlassPill(active = selectedMirror == 1, onClick = { selectedMirror = 1 }) {
                            Text("CDN 1", color = if (selectedMirror == 1) Color.White else style.textPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                        LiquidGlassPill(active = selectedMirror == 2, onClick = { selectedMirror = 2 }) {
                            Text("CDN 2", color = if (selectedMirror == 2) Color.White else style.textPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(
                        onClick = {
                            val freed = updateManager.cleanupOldApks()
                            val freedMb = "%.1f MB".format(freed / (1024f * 1024f))
                            Toast.makeText(context, "\u0110\u00E3 d\u1ECDn $freedMb g\u00F3i c\u1EADp nh\u1EADt c\u0169", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("D\u1ECDn g\u00F3i APK c\u0169 & b\u1ED9 nh\u1EDB \u0111\u1EC7m", color = style.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}