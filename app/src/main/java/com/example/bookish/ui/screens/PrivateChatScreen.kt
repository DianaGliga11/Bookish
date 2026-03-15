package com.example.bookish.ui.screens

import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.bookish.models.Book
import com.example.bookish.viewmodel.AuthViewModel
import com.example.bookish.viewmodel.BookViewModel
import com.example.bookish.viewmodel.SocialViewModel
import com.example.bookish.viewmodel.UserState
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale


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
fun ChatScreen(
    chatId: String,
    socialViewModel: SocialViewModel,
    authViewModel: AuthViewModel,
    bookViewModel: BookViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBook: (Book) -> Unit
) {
    val currentUserState by authViewModel.currentUser.collectAsState()
    val messages by socialViewModel.privateMessages.collectAsState()
    val currentUser = (currentUserState as? UserState.Success)?.user

    var messageText by remember { mutableStateOf("") }
    var showBookPicker by remember { mutableStateOf(false) }

    val allShelfBooks by bookViewModel.allShelfBooks.collectAsState()
    val allShelves by bookViewModel.allShelves.collectAsState()
    val allBooks by bookViewModel.books.collectAsState()

    val myReadBooks = remember(allShelfBooks, allShelves, allBooks) {
        val readShelfId =
            allShelves.find { it.id_user == currentUser?.id_user && it.name == "Read" }?.id_shelf
        allShelfBooks.filter { it.id_shelf == readShelfId }
            .mapNotNull { sb -> allBooks.find { it.id_book == sb.id_book } }
    }


    LaunchedEffect(chatId, currentUserState) {
        val userId = (currentUserState as? UserState.Success)?.user?.id_user
        Log.d("CHAT_DEBUG", "LaunchedEffect rulat! chatId=$chatId, userId=$userId")
        if (userId != null) {
            socialViewModel.listenForMessages(userId, chatId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                        socialViewModel.sendPrivateMessage(
                            senderId = currentUser.id_user,
                            receiverId = chatId,
                            text = messageText
                        )
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
                contentPadding = PaddingValues(16.dp),
                reverseLayout = false
            ) {
                items(messages) { msg ->
                    ChatBubble(
                        content = msg.content,
                        bookId = msg.id_book,
                        isMine = msg.id_sender == currentUser?.id_user,
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
                            socialViewModel.sendPrivateMessage(
                                it.id_user,
                                chatId,
                                "I recommend this book",
                                book.id_book
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPickerSheet(
    userReadBooks: List<Book>,
    onBookSelected: (Book) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Share a book from your library",
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            if (userReadBooks.isEmpty()) {
                Text(
                    text = "You haven't finished any books yet",
                    modifier = Modifier.padding(16.dp),
                    color = TextSecondary
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(userReadBooks) { book ->
                        Column(
                            modifier = Modifier
                                .width(100.dp)
                                .clickable { onBookSelected(book) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(book.coverImageUrl),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(90.dp, 130.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Text(
                                text = book.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    content: String,
    bookId: String,
    isMine: Boolean,
    timestamp: Timestamp,
    allBooks: List<Book>,
    onNavigateToBook: (Book) -> Unit
) {
    val bubbleColor = if (isMine) Pink else PinkPale
    val textColor = if (isMine) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 0.dp,
                        bottomEnd = if (isMine) 0.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Column {
                if (bookId.isNotEmpty()) {
                    SharedBookPreview(bookId = bookId, allBooks = allBooks, onNavigateToBook)
                }
                if (content.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = content,
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp.toDate()),
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun SharedBookPreview(
    bookId: String,
    allBooks: List<Book>,
    onNaigateToBook: (Book) -> Unit
) {
    val book = allBooks.find { it.id_book == bookId }

    if (book != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNaigateToBook(book) },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (book.coverImageUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(book.coverImageUrl),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp, 60.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp, 70.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(PinkLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = Pink,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {

                    Text(
                        text = book.title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = Pink,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Tap to view",
                            fontSize = 11.sp,
                            color = Pink,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit
) {
    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachClick) {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = Pink
                )
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp)),
                placeholder = { Text("Type a message...") },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                maxLines = 3
            )
            IconButton(onClick = onSend) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    tint = Pink
                )
            }
        }
    }
}

