package com.example.bookish.ui.screens

import android.R.attr.onClick
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.bookish.models.BookClub
import com.example.bookish.models.User
import com.example.bookish.viewmodel.AuthViewModel
import com.example.bookish.viewmodel.SocialViewModel
import com.example.bookish.viewmodel.UserState

private val Pink = Color(0xFFFF69B4)
private val PinkLight = Color(0xFFFFC0CB)
private val PinkPale = Color(0xFFFFF0F5)
private val Purple = Color(0xFF9C27B0)
private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFFF5252)
private val TextPrimary = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToChat: (String) -> Unit,
    onNavigateToBookClub: (String) -> Unit,
    socialViewModel: SocialViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val currentUserState by authViewModel.currentUser.collectAsState()
    val currentUser = (currentUserState as? UserState.Success)?.user
    val friends by socialViewModel.friends.collectAsState()
    val pendingRequests by socialViewModel.pendingRequests.collectAsState()
    val clubs by socialViewModel.myBookClubs.collectAsState()
    val searchResults by socialViewModel.searchResults.collectAsState()
    val sentRequests by socialViewModel.sentRequests.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val isSearching = searchQuery.isNotEmpty()

    LaunchedEffect(currentUserState) {
        val userId = (currentUserState as? UserState.Success)?.user?.id_user
        userId?.let { socialViewModel.loadSocialData(it) }
    }

    // Declanșează search când se schimbă query-ul
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            socialViewModel.searchUsers(searchQuery)
        } else {
            socialViewModel.clearSearchResults()
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(PinkLight.copy(alpha = 0.5f))) {
                TopAppBar(
                    title = {
                        Text(
                            "Community",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Pink
                        )
                    },
                    navigationIcon = {
                        onNavigateBack?.let {
                            IconButton(onClick = it) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PinkLight.copy(alpha = 0.5f)
                    )
                )
                SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSearching) {
                // ── MOD CĂUTARE ──
                item {
                    SectionHeader(
                        if (searchResults.isEmpty()) "No results for \"$searchQuery\""
                        else "Results for \"$searchQuery\""
                    )
                }

                items(searchResults) { user ->
                    val isFriend = friends.any { it.id_user == user.id_user }
                    val isMe = user.id_user == currentUser?.id_user
                    val requestSent = sentRequests.contains(user.id_user)
                    val hasPendingFromThem = pendingRequests.any { it.id_user == user.id_user }

                    if (!isMe) {
                        SearchResultItem(
                            user = user,
                            isFriend = isFriend,
                            requestSent = requestSent,
                            hasPendingRequest = hasPendingFromThem,
                            onSendRequest = {
                                currentUser?.let {
                                    socialViewModel.sendFriendRequest(it.id_user, user.id_user)
                                }
                            },
                            onAcceptRequest = {
                                currentUser?.let {
                                    socialViewModel.acceptFriendRequest(it.id_user, user.id_user)
                                }
                            },
                            onOpenChat = { onNavigateToChat(user.id_user) }
                        )
                    }
                }
            } else {
                // ── MOD NORMAL ──
                if (pendingRequests.isNotEmpty()) {
                    item { SectionHeader("Friend Requests (${pendingRequests.size})") }
                    items(pendingRequests) { user ->
                        RequestItem(
                            user,
                            onAccept = {
                                val myId =
                                    (currentUserState as? UserState.Success)?.user?.id_user ?: ""
                                socialViewModel.acceptFriendRequest(myId, user.id_user)
                            },
                            onDecline = {
                                val myId =
                                    (currentUserState as? UserState.Success)?.user?.id_user ?: ""
                                socialViewModel.declineFriendRequest(myId, user.id_user)
                            }
                        )
                    }
                }

                item { SectionHeader("My Book Clubs") }
                item {
                    if (clubs.isEmpty()) {
                        Text(
                            text = "You haven't joined any book clubs yet",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(clubs.filter {
                                it.name.contains(searchQuery, true)
                            }) { club ->
                                BookClubCard(club) { onNavigateToBookClub(club.id_book_club) }
                            }
                        }
                    }
                }

                item { SectionHeader("Friends (${friends.size})") }
                items(friends) { friend ->
                    FriendChatItem(friend) { onNavigateToChat(friend.id_user) }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    user: User,
    isFriend: Boolean,
    requestSent: Boolean,
    hasPendingRequest: Boolean,
    onSendRequest: () -> Unit,
    onAcceptRequest: () -> Unit?,
    onOpenChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(user.profileImageUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(45.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = when {
                        isFriend -> "Already friends"
                        requestSent -> "Request sent"
                        hasPendingRequest -> "Wants to be your friend"
                        else -> "Tap to add friend"
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            when {
                isFriend -> {
                    IconButton(onClick = onOpenChat) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Accept",
                            tint = Green
                        )
                    }
                }

                requestSent -> {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "Pending...",
                        tint = TextSecondary,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp)
                    )
                }

                else -> {
                    IconButton(onClick = onSendRequest) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add friend",
                            tint = Pink
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendChatItem(
    user: User,
    onCLick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCLick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Image(
                painter = rememberAsyncImagePainter(user.profileImageUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(55.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Green, CircleShape)
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Tap to chat about books!",
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun BookClubCard(
    club: BookClub,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PinkLight.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Pink,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = club.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RequestItem(
    user: User,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(user.profileImageUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(45.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = user.username,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onAccept) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Accept",
                    tint = Green
                )
            }

            IconButton(onClick = onDecline) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Decline",
                    tint = Red
                )
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(25.dp)),
        placeholder = { Text("Search friends or clubs...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        singleLine = true
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

