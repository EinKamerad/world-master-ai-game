package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.HiddenPlotPlan
import com.example.data.local.CampaignEntity
import com.example.data.local.WorldEntity
import com.example.data.remote.GoogleSignInButton
import com.example.data.remote.OnlineRoom
import com.example.data.remote.OnlineRoomPlayer
import com.example.data.remote.OnlineRoundEntry
import com.example.data.remote.attemptAutoSignIn
import com.example.data.remote.signOutFromMultiplayer
import com.example.data.transfer.DiceRoller
import com.example.ui.ActiveRoomSnapshot
import com.example.ui.AiTurnState
import com.example.ui.MultiplayerFeedState
import com.example.ui.OnlineMultiplayerUiState
import com.example.ui.OnlineMultiplayerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

@Composable
fun OnlineMultiplayerScreen(
    worlds: List<WorldEntity>,
    activeSoloCampaign: CampaignEntity?,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var authErrorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    authErrorText = null
                },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    if (currentUser == null) {
        OnlineAuthGateCard(
            authError = authErrorText,
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
                authErrorText = null
            },
            onAuthError = { msg -> authErrorText = msg },
            modifier = modifier
        )
    } else {
        val user = currentUser!!
        val uid = user.uid
        val displayName = user.displayName
            ?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "Игрок"

        key(uid) {
            val multiplayerVm: OnlineMultiplayerViewModel = viewModel(
                key = uid,
                factory = OnlineMultiplayerViewModel.provideFactory(
                    context = context,
                    currentUserId = uid,
                    currentUserDisplayName = displayName
                )
            )
            val uiState by multiplayerVm.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(multiplayerVm) {
                multiplayerVm.snackbarEvents.collect { msg ->
                    onShowSnackbar(msg)
                }
            }

            AuthenticatedMultiplayerContent(
                uiState = uiState,
                userEmailOrName = user.email ?: displayName,
                worlds = worlds,
                activeSoloCampaign = activeSoloCampaign,
                viewModel = multiplayerVm,
                onSignOutClick = {
                    signOutFromMultiplayer(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun OnlineAuthGateCard(
    authError: String?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "Совместная онлайн-ролевая",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Создавайте общие комнаты или подключайтесь к друзьям по 6-значному коду. В каждом раунде система собирает заявки всех участников партии, после чего ИИ-Мастер вплетает действия каждого героя в единую живую сцену.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Одиночные миры, локальные кампании, инвентарь и JSON-бэкапы работают офлайн без входа. Авторизация Google нужна только для синхронизации онлайн-комнат между игроками.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (!authError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                GoogleSignInButton(
                    onAuthSuccess = onAuthSuccess,
                    onAuthError = onAuthError,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedMultiplayerContent(
    uiState: OnlineMultiplayerUiState,
    userEmailOrName: String,
    worlds: List<WorldEntity>,
    activeSoloCampaign: CampaignEntity?,
    viewModel: OnlineMultiplayerViewModel,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var showJoinRoomDialog by remember { mutableStateOf(false) }

    if (uiState.activeRoomCode != null) {
        BackHandler {
            viewModel.leaveRoomToLobby()
        }
        ActiveOnlineRoomView(
            uiState = uiState,
            onBackToLobby = viewModel::leaveRoomToLobby,
            onSubmitAction = viewModel::submitMyAction,
            onResolveRoundAi = { viewModel.resolveCurrentRoundWithAi(forceEvenIfIncomplete = true) },
            onUpdateHeroProfile = viewModel::updateMyHeroInRoom,
            onSaveHostSettings = viewModel::saveRoomHiddenPlotAndModules,
            onGenerateHiddenPlotAi = viewModel::generateRoomHiddenPlotWithAi,
            onDeleteRoom = viewModel::deleteActiveRoom,
            onDismissGmError = viewModel::dismissGmError,
            modifier = modifier
        )
    } else {
        OnlineLobbyView(
            uiState = uiState,
            userEmailOrName = userEmailOrName,
            onOpenCreateDialog = { showCreateRoomDialog = true },
            onOpenJoinDialog = { showJoinRoomDialog = true },
            onSelectRoom = viewModel::openRoom,
            onSignOutClick = onSignOutClick,
            modifier = modifier
        )
    }

    if (showCreateRoomDialog && worlds.isNotEmpty()) {
        CreateOnlineRoomDialog(
            worlds = worlds,
            defaultHeroName = activeSoloCampaign?.heroName.orEmpty(),
            onDismiss = { showCreateRoomDialog = false },
            onCreate = { world, title, heroName, arch, stats, opening, useDice, useInv, useSpells ->
                viewModel.createOnlineRoom(
                    world = world,
                    roomTitle = title,
                    heroName = heroName,
                    heroArchetype = arch,
                    heroStats = stats,
                    openingScene = opening,
                    useDiceAndStats = useDice,
                    useInventory = useInv,
                    useSpells = useSpells
                )
                showCreateRoomDialog = false
            }
        )
    }

    if (showJoinRoomDialog) {
        JoinOnlineRoomDialog(
            defaultHeroName = activeSoloCampaign?.heroName.orEmpty(),
            defaultArchetype = activeSoloCampaign?.heroArchetype.orEmpty(),
            defaultStats = activeSoloCampaign?.heroStatsSummary.orEmpty(),
            onDismiss = { showJoinRoomDialog = false },
            onJoin = { code, heroName, arch, stats ->
                viewModel.joinRoomByInviteCode(code, heroName, arch, stats)
                showJoinRoomDialog = false
            }
        )
    }
}

@Composable
private fun OnlineLobbyView(
    uiState: OnlineMultiplayerUiState,
    userEmailOrName: String,
    onOpenCreateDialog: () -> Unit,
    onOpenJoinDialog: () -> Unit,
    onSelectRoom: (String) -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("online_lobby_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "lobby_header_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Лобби совместных игр",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Аккаунт: $userEmailOrName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onSignOutClick,
                            modifier = Modifier.testTag("online_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Выйти из аккаунта онлайн-режима"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenCreateDialog,
                            enabled = !uiState.isCreatingOrJoining,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_online_room_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Создать комнату")
                        }
                        OutlinedButton(
                            onClick = onOpenJoinDialog,
                            enabled = !uiState.isCreatingOrJoining,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("join_online_room_button")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("По коду")
                        }
                    }
                }
            }
        }

        when (val roomsState = uiState.myRoomsState) {
            MultiplayerFeedState.Loading -> {
                item(key = "rooms_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is MultiplayerFeedState.Error -> {
                item(key = "rooms_error") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = roomsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is MultiplayerFeedState.Success -> {
                if (roomsState.data.isEmpty()) {
                    item(key = "rooms_empty") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "У вас пока нет активных онлайн-комнат",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Создайте комнату в любом из ваших миров и отправьте 6-значный код друзьям, либо введите код приглашения.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(roomsState.data, key = { it.roomCode }) { room ->
                        OnlineRoomLobbyCard(
                            room = room,
                            isHost = room.hostUid == uiState.currentUserId,
                            onClick = { onSelectRoom(room.roomCode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineRoomLobbyCard(
    room: OnlineRoom,
    isHost: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("online_room_card_${room.roomCode}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = room.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Код: ${room.roomCode}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = "Мир: ${room.worldName} (${room.worldGenre})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Раунд №${room.currentRound} • Игроков: ${room.memberUids.size}/8",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = if (room.useDiceAndStats) "• С кубиками" else "• Словеска без ДнД",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                if (isHost) {
                    Text(
                        text = "• Вы хост",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveOnlineRoomView(
    uiState: OnlineMultiplayerUiState,
    onBackToLobby: () -> Unit,
    onSubmitAction: (String, String?) -> Unit,
    onResolveRoundAi: () -> Unit,
    onUpdateHeroProfile: (String, String, String) -> Unit,
    onSaveHostSettings: (HiddenPlotPlan, Boolean, Boolean, Boolean) -> Unit,
    onGenerateHiddenPlotAi: (String) -> Unit,
    onDeleteRoom: () -> Unit,
    onDismissGmError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snapState = uiState.activeRoomState

    when (snapState) {
        null, MultiplayerFeedState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is MultiplayerFeedState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = snapState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(onClick = onBackToLobby) {
                            Text("Вернуться в лобби")
                        }
                    }
                }
            }
        }

        is MultiplayerFeedState.Success -> {
            val snap: ActiveRoomSnapshot = snapState.data
            val room = snap.room
            val isHost = room.hostUid == uiState.currentUserId
            val myPlayer = snap.players.firstOrNull { it.userId == uiState.currentUserId }
            val mySubmittedThisRound = myPlayer?.submittedRound == room.currentRound

            var actionInput by rememberSaveable(room.roomCode, room.currentRound) {
                mutableStateOf(if (mySubmittedThisRound) myPlayer?.pendingAction.orEmpty() else "")
            }
            var attachedDice by rememberSaveable(room.roomCode, room.currentRound) {
                mutableStateOf<String?>(
                    if (mySubmittedThisRound) myPlayer?.pendingDiceRoll?.takeIf { it.isNotBlank() } else null
                )
            }
            var showDiceRow by rememberSaveable { mutableStateOf(false) }
            var showHiddenPlotDialog by remember { mutableStateOf(false) }
            var showEditHeroDialog by remember { mutableStateOf(false) }
            var showDeleteConfirm by remember { mutableStateOf(false) }

            val listState = rememberLazyListState()

            LaunchedEffect(snap.rounds.size, uiState.gmRoundTurnState) {
                val extra = if (uiState.gmRoundTurnState is AiTurnState.Loading || uiState.gmRoundTurnState is AiTurnState.Streaming) 1 else 0
                val total = snap.rounds.size + extra
                if (total > 0) {
                    listState.animateScrollToItem(total - 1)
                }
            }

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // 1. Room Header & Party Readiness Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = onBackToLobby,
                                    modifier = Modifier.testTag("leave_room_to_lobby_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Вернуться в лобби комнат"
                                    )
                                }
                                Column {
                                    Text(
                                        text = room.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${room.worldName} • Раунд №${room.currentRound}" +
                                            if (!room.useDiceAndStats) " • Без ДнД" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AssistChip(
                                    onClick = {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Присоединяйся к нашей ролевой «${room.title}» в Мастере Миров! Код комнаты: ${room.roomCode}"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(share, "Пригласить игрока"))
                                    },
                                    label = { Text(room.roomCode, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                    },
                                    modifier = Modifier.testTag("share_room_code_chip")
                                )
                                IconButton(onClick = { showEditHeroDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Изменить моего героя")
                                }
                                if (isHost) {
                                    IconButton(
                                        onClick = { showHiddenPlotDialog = true },
                                        modifier = Modifier.testTag("room_hidden_plot_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VisibilityOff,
                                            contentDescription = "Закулисье ИИ комнаты"
                                        )
                                    }
                                    IconButton(onClick = { showDeleteConfirm = true }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Удалить комнату",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        // Party Round Status & Player Readiness Badges
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            snap.players.forEach { p ->
                                val isReady = p.submittedRound == room.currentRound
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isReady) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                                            contentDescription = null,
                                            tint = if (isReady) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${p.heroName} (${if (isReady) "готов" else "пишет..."})",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        // Host Round Trigger Bar
                        if (isHost) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val isAiRunning = uiState.gmRoundTurnState is AiTurnState.Loading ||
                                uiState.gmRoundTurnState is AiTurnState.Streaming ||
                                room.roundPhase == "GM_RESPONDING"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Сбор заявок: ${snap.readyPlayersCount}/${snap.totalMembersCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Button(
                                    onClick = onResolveRoundAi,
                                    enabled = !isAiRunning && snap.readyPlayersCount >= 1,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("host_resolve_round_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isAiRunning) "ИИ пишет сцену..." else "Ход ИИ-Мастера")
                                }
                            }
                        } else if (room.roundPhase == "GM_RESPONDING") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Мастер объединяет заявки партии и создаёт сцену раунда №${room.currentRound}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 2. Round Story Feed
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("online_rounds_feed"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(snap.rounds, key = { "round_${it.roundNumber}" }) { roundEntry ->
                        OnlineRoundStoryCard(entry = roundEntry)
                    }

                    when (val turn = uiState.gmRoundTurnState) {
                        is AiTurnState.Loading -> {
                            item(key = "online_gm_loading") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("ИИ-Мастер сводит заявки всех героев в единую сцену...")
                                    }
                                }
                            }
                        }
                        is AiTurnState.Streaming -> {
                            item(key = "online_gm_streaming") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "Мастер пишет Раунд №${room.currentRound}...",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(turn.partialText, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                        is AiTurnState.Error -> {
                            item(key = "online_gm_error") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = turn.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = onDismissGmError) { Text("Скрыть") }
                                    }
                                }
                            }
                        }
                        AiTurnState.Idle -> Unit
                    }
                }

                // 3. Optional Dice Bar (only when room.useDiceAndStats == true)
                AnimatedVisibility(visible = room.useDiceAndStats && showDiceRow) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(4, 6, 8, 10, 12, 20, 100).forEach { sides ->
                                OutlinedButton(
                                    onClick = {
                                        attachedDice = DiceRoller.rollStandard(sides = sides, count = 1, modifier = 0).formattedSummary
                                    }
                                ) {
                                    Text("d$sides")
                                }
                            }
                        }
                    }
                }

                // 4. Player Action Submission Composer
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (mySubmittedThisRound) {
                                    "Ваша заявка на Раунд №${room.currentRound} принята (можно дополнить до ответа ИИ)"
                                } else {
                                    "Ваш ход в Раунде №${room.currentRound} за «${myPlayer?.heroName ?: "Герой"}»"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mySubmittedThisRound) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (room.useDiceAndStats) {
                                FilterChip(
                                    selected = showDiceRow,
                                    onClick = { showDiceRow = !showDiceRow },
                                    label = { Text("Кубики") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }

                        if (room.useDiceAndStats && !attachedDice.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = attachedDice.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { attachedDice = null }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Убрать бросок", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = actionInput,
                                onValueChange = { actionInput = it },
                                placeholder = { Text("Опишите заявку вашего персонажа на этот раунд...") },
                                maxLines = 4,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("online_round_action_input")
                            )
                            FilledIconButton(
                                onClick = {
                                    onSubmitAction(actionInput, attachedDice)
                                },
                                enabled = !uiState.isSubmittingAction &&
                                    room.roundPhase == "COLLECTING" &&
                                    (actionInput.isNotBlank() || !attachedDice.isNullOrBlank()),
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("submit_online_action_button")
                            ) {
                                if (uiState.isSubmittingAction) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Отправить заявку в раунд"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showHiddenPlotDialog && isHost) {
                HiddenPlotSpoilerDialog(
                    initialPlan = HiddenPlotPlan(
                        plotArc = room.hiddenPlotArc,
                        npcKnowledgeBoundaries = room.hiddenNpcKnowledge,
                        triggersAndConsequences = room.hiddenPlotTriggers
                    ),
                    isGeneratingAi = uiState.isGeneratingHiddenPlot,
                    onGenerateWithAi = onGenerateHiddenPlotAi,
                    onSaveManual = { plan ->
                        onSaveHostSettings(
                            plan,
                            room.useDiceAndStats,
                            room.useInventory,
                            room.useSpells
                        )
                    },
                    onDismiss = { showHiddenPlotDialog = false }
                )
            }

            if (showEditHeroDialog && myPlayer != null) {
                EditRoomHeroDialog(
                    player = myPlayer,
                    useDiceAndStats = room.useDiceAndStats,
                    onDismiss = { showEditHeroDialog = false },
                    onSave = { name, arch, stats ->
                        onUpdateHeroProfile(name, arch, stats)
                        showEditHeroDialog = false
                    }
                )
            }

            if (showDeleteConfirm && isHost) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Удалить онлайн-комнату?") },
                    text = { Text("Комната «${room.title}» (${room.roomCode}) будет закрыта для всех участников.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirm = false
                                onDeleteRoom()
                            }
                        ) {
                            Text("Удалить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OnlineRoundStoryCard(entry: OnlineRoundEntry) {
    val isPrologue = entry.roundNumber == 0L
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPrologue) "Пролог приключения" else "Раунд №${entry.roundNumber} • Сцена Мастера",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (entry.partyActionsSummary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Заявки героев в этом раунде:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.partyActionsSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Text(
                text = entry.gmNarrative,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CreateOnlineRoomDialog(
    worlds: List<WorldEntity>,
    defaultHeroName: String,
    onDismiss: () -> Unit,
    onCreate: (
        world: WorldEntity,
        roomTitle: String,
        heroName: String,
        heroArchetype: String,
        heroStats: String,
        openingScene: String,
        useDiceAndStats: Boolean,
        useInventory: Boolean,
        useSpells: Boolean
    ) -> Unit
) {
    var selectedWorldId by remember(worlds) { mutableLongStateOf(worlds.firstOrNull()?.id ?: 0L) }
    val chosenWorld = worlds.firstOrNull { it.id == selectedWorldId } ?: worlds.firstOrNull()

    var roomTitle by remember(chosenWorld?.id) {
        mutableStateOf(chosenWorld?.let { "${it.name}: Онлайн-рейд" }.orEmpty())
    }
    var heroName by remember { mutableStateOf(defaultHeroName.ifBlank { "Странник" }) }
    var heroArchetype by remember(chosenWorld?.id) {
        mutableStateOf(chosenWorld?.archetypesList()?.firstOrNull() ?: "Искатель приключений")
    }
    var heroStats by remember { mutableStateOf("ОЗ 25/25 | Защита 14") }
    var openingScene by remember(chosenWorld?.id) {
        mutableStateOf(chosenWorld?.defaultOpeningHook.orEmpty())
    }
    var useDiceAndStats by remember { mutableStateOf(true) }
    var useInventory by remember { mutableStateOf(true) }
    var useSpells by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать онлайн-комнату") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("1. Мир приключения:", style = MaterialTheme.typography.labelLarge)
                worlds.forEach { w ->
                    val selected = w.id == chosenWorld?.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedWorldId = w.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(w.genre, style = MaterialTheme.typography.labelSmall)
                            }
                            if (selected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("2. Формат ролевой:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useDiceAndStats,
                        onClick = {
                            useDiceAndStats = true
                            useInventory = true
                            useSpells = true
                        },
                        label = { Text("РПГ с кубиками") }
                    )
                    FilterChip(
                        selected = !useDiceAndStats,
                        onClick = {
                            useDiceAndStats = false
                            useInventory = false
                            useSpells = false
                        },
                        label = { Text("Словеска без ДнД") }
                    )
                }

                OutlinedTextField(
                    value = roomTitle,
                    onValueChange = { roomTitle = it },
                    label = { Text("Название комнаты") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = heroName,
                    onValueChange = { heroName = it },
                    label = { Text("Имя вашего героя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = heroArchetype,
                    onValueChange = { heroArchetype = it },
                    label = { Text("Класс / Роль / Образ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (useDiceAndStats) {
                    OutlinedTextField(
                        value = heroStats,
                        onValueChange = { heroStats = it },
                        label = { Text("Характеристики героя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = openingScene,
                    onValueChange = { openingScene = it },
                    label = { Text("Стартовая сцена (Пролог)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (chosenWorld != null) {
                        onCreate(
                            chosenWorld,
                            roomTitle,
                            heroName,
                            heroArchetype,
                            if (useDiceAndStats) heroStats else "Свободный литературный отыгрыш",
                            openingScene,
                            useDiceAndStats,
                            useInventory,
                            useSpells
                        )
                    }
                },
                enabled = chosenWorld != null && heroName.isNotBlank()
            ) {
                Text("Открыть комнату")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun JoinOnlineRoomDialog(
    defaultHeroName: String,
    defaultArchetype: String,
    defaultStats: String,
    onDismiss: () -> Unit,
    onJoin: (code: String, heroName: String, archetype: String, stats: String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var heroName by remember { mutableStateOf(defaultHeroName.ifBlank { "Странник" }) }
    var archetype by remember { mutableStateOf(defaultArchetype.ifBlank { "Искатель приключений" }) }
    var stats by remember { mutableStateOf(defaultStats.ifBlank { "ОЗ 25/25 | Защита 14" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Войти в комнату по коду") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { raw ->
                        code = raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(6)
                    },
                    label = { Text("6-значный код комнаты") },
                    placeholder = { Text("Например: K7P9M2") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_room_code_input")
                )
                OutlinedTextField(
                    value = heroName,
                    onValueChange = { heroName = it },
                    label = { Text("Имя вашего персонажа") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = archetype,
                    onValueChange = { archetype = it },
                    label = { Text("Роль / Архетип") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stats,
                    onValueChange = { stats = it },
                    label = { Text("Характеристики или черты образа") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code, heroName, archetype, stats) },
                enabled = code.length == 6 && heroName.isNotBlank(),
                modifier = Modifier.testTag("confirm_join_room_button")
            ) {
                Text("Присоединиться")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun EditRoomHeroDialog(
    player: OnlineRoomPlayer,
    useDiceAndStats: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember(player.heroName) { mutableStateOf(player.heroName) }
    var arch by remember(player.heroArchetype) { mutableStateOf(player.heroArchetype) }
    var stats by remember(player.heroStats) { mutableStateOf(player.heroStats) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Мой герой в комнате") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя героя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = arch,
                    onValueChange = { arch = it },
                    label = { Text("Роль / Класс") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stats,
                    onValueChange = { stats = it },
                    label = { Text(if (useDiceAndStats) "Характеристики (ОЗ, КД, бонусы)" else "Черты характера и внешность") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, arch, stats) }, enabled = name.isNotBlank()) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
