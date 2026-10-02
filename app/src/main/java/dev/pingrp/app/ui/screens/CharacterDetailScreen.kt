package dev.pingrp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.pingrp.app.data.model.ApiProvider
import dev.pingrp.app.data.model.Character

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    character: Character?,
    onBackClick: () -> Unit,
    onSaveCharacter: (Character) -> Unit
) {
    var name by remember { mutableStateOf(character?.name ?: "") }
    var avatarUrl by remember { mutableStateOf(character?.avatarUrl ?: "") }
    var personality by remember { mutableStateOf(character?.personality ?: "") }
    var scenario by remember { mutableStateOf(character?.scenario ?: "") }
    var patienceLevel by remember { mutableIntStateOf(character?.patienceLevel ?: 3) }
    var replySpeed by remember { mutableFloatStateOf(character?.replyDelayMultiplier ?: 1.0f) }
    var provider by remember { mutableStateOf(character?.provider ?: ApiProvider.GROQ) }
    var modelName by remember { mutableStateOf(character?.modelName ?: "qwen/qwen3.8-27b") }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Text(
                        text = if (character == null) "Новый персонаж" else "Редактировать",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                val updatedChar = (character ?: Character(
                                    name = name.trim(),
                                    personality = personality.trim()
                                )).copy(
                                    name = name.trim(),
                                    avatarUrl = avatarUrl.trim(),
                                    personality = personality.trim(),
                                    scenario = scenario.trim(),
                                    patienceLevel = patienceLevel,
                                    replyDelayMultiplier = replySpeed,
                                    provider = provider,
                                    modelName = modelName.trim()
                                )
                                onSaveCharacter(updatedChar)
                            }
                        },
                        enabled = name.isNotBlank() && personality.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Avatar Preview
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (name.isNotBlank()) name.take(1).uppercase() else "?",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя персонажа *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Avatar URL
            OutlinedTextField(
                value = avatarUrl,
                onValueChange = { avatarUrl = it },
                label = { Text("URL картинки-аватарки (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Personality / Character prompt
            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                label = { Text("Личность и стиль общения *") },
                placeholder = { Text("Например: Цундере, подкалывает, любит аниме, часто пишет капсом...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Scenario / Context
            OutlinedTextField(
                value = scenario,
                onValueChange = { scenario = it },
                label = { Text("Сюжет / Текущая ситуация") },
                placeholder = { Text("Например: Вы только что вернулись с вечеринки, вы коллеги на дежурстве...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Patience Level Slider
            Text(
                text = "Настойчивость (как часто пишет первым): $patienceLevel / 5",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when (patienceLevel) {
                    1 -> "Очень спокойный: почти не пишет первым, может молчать часами"
                    2 -> "Ненавязчивый: вспомнит о тебе только спустя долгое время"
                    3 -> "Обычный собеседник: напишет через 5-20 минут при игноре"
                    4 -> "Внимательный: заметит игнор и спросит, куда пропал"
                    else -> "Навязчивый / ревнивый: будет строчить сообщения и напоминать о себе!"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = patienceLevel.toFloat(),
                onValueChange = { patienceLevel = it.toInt() },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Provider selection
            Text(
                text = "ИИ Модель:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = provider == ApiProvider.GROQ,
                    onClick = {
                        provider = ApiProvider.GROQ
                        modelName = "qwen/qwen3.8-27b"
                    },
                    label = { Text("Groq (Qwen 3.8)") }
                )
                FilterChip(
                    selected = provider == ApiProvider.GEMINI,
                    onClick = {
                        provider = ApiProvider.GEMINI
                        modelName = "gemini-2.5-flash"
                    },
                    label = { Text("Gemini Flash") }
                )
            }

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Идентификатор модели") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val updatedChar = (character ?: Character(
                            name = name.trim(),
                            personality = personality.trim()
                        )).copy(
                            name = name.trim(),
                            avatarUrl = avatarUrl.trim(),
                            personality = personality.trim(),
                            scenario = scenario.trim(),
                            patienceLevel = patienceLevel,
                            replyDelayMultiplier = replySpeed,
                            provider = provider,
                            modelName = modelName.trim()
                        )
                        onSaveCharacter(updatedChar)
                    }
                },
                enabled = name.isNotBlank() && personality.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Сохранить персонажа", fontSize = 16.sp)
            }
        }
    }
}
