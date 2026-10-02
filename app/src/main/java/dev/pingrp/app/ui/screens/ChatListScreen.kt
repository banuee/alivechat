package dev.pingrp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.pingrp.app.data.model.Character
import dev.pingrp.app.data.model.CharacterOnlineStatus
import dev.pingrp.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    characters: List<Character>,
    onCharacterClick: (String) -> Unit,
    onAddCharacterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeleteCharacter: (String) -> Unit
) {
    var characterToDelete by remember { mutableStateOf<Character?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AliveChat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCharacterClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новый персонаж")
            }
        }
    ) { innerPadding ->
        if (characters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Нет активных персонажей",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onAddCharacterClick) {
                        Text("Создать персонажа")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(characters, key = { it.id }) { character ->
                    ChatItemRow(
                        character = character,
                        onClick = { onCharacterClick(character.id) },
                        onDeleteClick = { characterToDelete = character }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                }
            }
        }

        // Confirm Delete Dialog
        characterToDelete?.let { char ->
            AlertDialog(
                onDismissRequest = { characterToDelete = null },
                title = { Text("Удалить персонажа?") },
                text = { Text("Чат и вся история с «${char.name}» будут удалены навсегда.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteCharacter(char.id)
                            characterToDelete = null
                        }
                    ) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { characterToDelete = null }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}

@Composable
fun ChatItemRow(
    character: Character,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val statusColor = when (character.onlineStatus) {
        CharacterOnlineStatus.ONLINE -> StatusOnline
        CharacterOnlineStatus.TYPING -> StatusTyping
        CharacterOnlineStatus.AWAY -> StatusAway
        CharacterOnlineStatus.OFFLINE -> StatusOffline
        CharacterOnlineStatus.BUSY -> StatusBusy
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with Status Badge
        Box(modifier = Modifier.size(52.dp)) {
            if (character.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = character.avatarUrl,
                    contentDescription = character.name,
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
                        text = character.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Online status indicator dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Center: Name & Last message / Typing
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (character.lastMessageTime > 0) {
                    Text(
                        text = formatTime(character.lastMessageTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewText = when (character.onlineStatus) {
                    CharacterOnlineStatus.TYPING -> "печатает..."
                    else -> character.lastMessageText.ifBlank { "Начните общение..." }
                }

                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (character.onlineStatus == CharacterOnlineStatus.TYPING) {
                        StatusTyping
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (character.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = character.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val date = Date(millis)
    val now = Date()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date) ==
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now)

    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
    }
}
