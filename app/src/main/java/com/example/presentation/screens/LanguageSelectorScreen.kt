package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Language
import com.example.presentation.MainViewModel
import com.example.ui.theme.VoxCyanPrimary
import com.example.ui.theme.VoxDarkCanvas
import com.example.ui.theme.VoxDarkCard
import com.example.ui.theme.VoxDarkCardBorder
import com.example.ui.theme.VoxDarkSurface
import com.example.ui.theme.VoxEmeraldActive
import com.example.ui.theme.VoxTextMuted
import com.example.ui.theme.VoxTextPrimary
import com.example.ui.theme.VoxTextSecondary

enum class LanguageSelectionTarget {
    RECOGNITION_STT,
    SPEECH_OUTPUT_TTS
}

@Composable
fun LanguageSelectorScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectionTarget by remember { mutableStateOf(LanguageSelectionTarget.RECOGNITION_STT) }

    val activeSttLang by viewModel.languageManager.sttLanguage.collectAsState()
    val activeTtsLang by viewModel.languageManager.ttsLanguage.collectAsState()
    val languagePacks by viewModel.languageManager.packs.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoxDarkCanvas)
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = VoxTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "LANGUAGE PACKS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = VoxTextPrimary
                    )
                )
                Text(
                    text = "10 Indian Languages • Offline Modular Packs",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = VoxCyanPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Target Selector: STT vs TTS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoxDarkSurface, RoundedCornerShape(10.dp))
                .border(1.dp, VoxDarkCardBorder, RoundedCornerShape(10.dp))
                .padding(4.dp)
        ) {
            val isStt = selectionTarget == LanguageSelectionTarget.RECOGNITION_STT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isStt) VoxCyanPrimary else Color.Transparent)
                    .clickable { selectionTarget = LanguageSelectionTarget.RECOGNITION_STT }
                    .padding(vertical = 10.dp)
                    .testTag("target_stt"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RECOGNITION (STT): ${activeSttLang.nativeName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isStt) Color.Black else VoxTextSecondary
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!isStt) VoxCyanPrimary else Color.Transparent)
                    .clickable { selectionTarget = LanguageSelectionTarget.SPEECH_OUTPUT_TTS }
                    .padding(vertical = 10.dp)
                    .testTag("target_tts"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OUTPUT (TTS): ${activeTtsLang.nativeName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (!isStt) Color.Black else VoxTextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Active RAM Footprint Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoxDarkCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "DYNAMIC RAM MANAGEMENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = VoxTextSecondary
                        )
                    )
                    Text(
                        text = "Active Pack RAM: ~${viewModel.languageManager.getEstimatedRamUsageMb()} MB (Zero unnecessary memory churn)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = VoxEmeraldActive
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .background(VoxEmeraldActive.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "OPTIMIZED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = VoxEmeraldActive
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 10 Languages List
        Text(
            text = "ALL 10 SUPPORTED LANGUAGES",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = VoxTextSecondary
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Language.entries) { lang ->
                val isSelected = if (selectionTarget == LanguageSelectionTarget.RECOGNITION_STT) {
                    activeSttLang == lang
                } else {
                    activeTtsLang == lang
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (selectionTarget == LanguageSelectionTarget.RECOGNITION_STT) {
                                viewModel.setSttLanguage(lang)
                            } else {
                                viewModel.setTtsLanguage(lang)
                            }
                        }
                        .testTag("lang_item_${lang.code}"),
                    colors = CardDefaults.cardColors(containerColor = VoxDarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) VoxCyanPrimary else VoxDarkCardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) VoxCyanPrimary else VoxTextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${lang.englishName})",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = VoxTextSecondary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"${lang.sampleSentence}\"",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = VoxTextMuted
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${lang.modelSizeMb}MB",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = VoxTextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        if (isSelected) VoxCyanPrimary else VoxDarkCard,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
