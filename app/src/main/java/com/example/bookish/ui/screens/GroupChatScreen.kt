package com.example.bookish.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.room.util.copy
import androidx.room3.util.copy
import com.example.bookish.models.Book
import com.example.bookish.viewmodel.AuthViewModel
import com.example.bookish.viewmodel.BookViewModel
import com.example.bookish.viewmodel.SocialViewModel
import com.example.bookish.viewmodel.UserState

private val Pink = Color(0xFFFF69B4)
private val PinkLight = Color(0xFFFFC0CB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    clubId: String,
    socialViewModel: SocialViewModel,
    authViewModel: AuthViewModel,
    bookViewModel: BookViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBook: (Book) -> Unit
) {
    val currentUser = (authViewModel.currentUser.collectAsState().value as? UserState.Success)?.user
    val messages by socialViewModel.groupMessages.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var showBookPicker by remember { mutableStateOf(false) }

    val allShelfBooks by bookViewModel.allShelfBooks.collectAsState()
    val allShelves by bookViewModel.allShelves.collectAsState()
    val allBooks by bookViewModel.books.collectAsState()

    val myReadBooks = remember(allShelfBooks, allShelves, allBooks) {
        val readShelfId = allShelves
            .find { it.id_user == currentUser?.id_user && it.name == "Completed" }?.id_shelf
        allShelfBooks.filter { it.id_shelf == readShelfId }
            .mapNotNull { sb -> allBooks.find { it.id_book == sb.id_book } }
    }

    val currentUserId = currentUser?.id_user

    LaunchedEffect(clubId) {
        if (currentUserId != null) {
            socialViewModel.listenForGroupMessages( clubId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Group Chat",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PinkLight.copy(alpha = 0.3f))
            )
        },
        bottomBar = {
            ChatInputBar(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank() && currentUser != null) {
                        socialViewModel.sendGroupMessage(clubId, currentUser.id_user, messageText)
                        messageText = ""
                    }
                },
                onAttachClick = { showBookPicker = true }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(
                        content = msg.content,
                        bookId = msg.id_book,
                        isMine = msg.id_user == currentUser?.id_user,
                        timestamp = msg.sendingDate,
                        allBooks = allBooks,
                        onNavigateToBook = onNavigateToBook
                    )
                }
            }
            if (showBookPicker) {
                BookPickerSheet(
                    userReadBooks = myReadBooks,
                    onBookSelected = { book ->
                        currentUser?.let {
                            socialViewModel.sendGroupMessage(
                                clubId = clubId,
                                userId = it.id_user,
                                text = "I recommend this book",
                                bookId = book.id_book
                            )
                        }
                        showBookPicker = false
                    },
                    onDismiss = { showBookPicker = false }
                )
            }
        }
    }
}
