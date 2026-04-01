package com.example.bookish.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.bookish.models.AiMessage
import com.example.bookish.models.Book
import com.example.bookish.viewmodel.AuthViewModel
import com.example.bookish.viewmodel.BookViewModel
import com.example.bookish.viewmodel.UserState

private val Pink = Color(0xFFFF69B4)
private val PinkLight = Color(0xFFFFC0CB)
private val PinkPale = Color(0xFFFFF0F5)
private val PurpleAi = Color(0xFF9C27B0)
private val TextSecondary = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    bookViewModel: BookViewModel,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBook: (Book) -> Unit
) {
    val currentUser = (authViewModel.currentUser.collectAsState().value as? UserState.Success)?.user
    val userId = currentUser?.id_user ?: ""

    val messages by bookViewModel.aiMessages.collectAsState()
    val isThinking by bookViewModel.aiIsThinking.collectAsState()
    val guessedBook by bookViewModel.guessedBook.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            bookViewModel.loadAiHistory(userId)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(PurpleAi, Pink)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Book Detective",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Descrie o carte, eu ghicesc!",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (userId.isNotEmpty()) {
                                bookViewModel.resetAiGame(userId)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Joc Nou",
                            tint = PurpleAi
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            AiInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                isThinking = isThinking,
                onSend = {
                    if (inputText.isNotBlank() && !isThinking && userId.isNotEmpty()) {
                        bookViewModel.sendAiMessage(inputText, userId)
                        inputText = ""
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PinkPale)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    AiChatBubble(message = message)
                }

                if (isThinking && messages.none { it.isStreaming }) {
                    item {
                        ThinkingIndicator()
                    }
                }

                guessedBook?.let { book ->
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        GuessedBookCard(
                            book = book,
                            onClick = { onNavigateToBook(book) },
                            onNewGame = {
                                if (userId.isNotEmpty()) {
                                    bookViewModel.resetAiGame(userId)
                                }
                            }
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun AiInputBar(text: String, onTextChange: (String) -> Unit, isThinking: Boolean, onSend: () -> Unit) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ){
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ){
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp)),
                placeholder = {
                    Text(
                        if (isThinking) "AI is thinking..." else "Describe a book."
                    )
                },
                enabled = !isThinking,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                enabled = !isThinking && text.isNotEmpty()
            ){
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (!isThinking && text.isNotBlank()) PurpleAi else Color.Gray
                )
            }
        }
    }
}

@Composable
fun GuessedBookCard(book: Book, onClick: () -> Unit, onNewGame: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ){
        Column(modifier = Modifier.padding(16.dp)){
            Text(
                text = "Book Guessed!",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = PurpleAi
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() },
                verticalAlignment = Alignment.CenterVertically
            ){
                if(book.coverImageUrl.isNotEmpty()){
                    Image(
                        painter = rememberAsyncImagePainter(book.coverImageUrl),
                        contentDescription = null,
                        modifier = Modifier
                            .size(60.dp, 90.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }else{
                    Box(
                        modifier = Modifier
                            .size(60.dp, 90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PinkLight),
                        contentAlignment = Alignment.Center
                    ){
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            tint = Pink,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Press for details",
                        fontSize = 12.sp,
                        color = Pink,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onNewGame,
                modifier =  Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAi),
                shape = RoundedCornerShape(12.dp)
            ){
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))
                Text("New Game")
            }
        }
    }
}

@Composable
fun ThinkingIndicator() {
    Row(verticalAlignment = Alignment.Bottom){
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    brush = Brush.radialGradient(colors = listOf(PurpleAi, Pink)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ){
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ){
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ){
                repeat(3){index ->
                    val dotAlpha by rememberInfiniteTransition(label = "dot_$index")
                        .animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, delayMillis = index * 150),
                                repeatMode = RepeatMode.Reverse
                        ),
                            label = "dotAlpha_$index"
                        )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(dotAlpha)
                            .background(PurpleAi, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun AiChatBubble(message: AiMessage) {
    val isAi = message.isFromAi
    val cursorAlpha by rememberInfiniteTransition(label = "cursor_${message.id}")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursorAlpha"
        )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (isAi) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        brush = Brush.radialGradient(colors = listOf(PurpleAi, Pink)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isAi) 4.dp else 16.dp,
                        bottomEnd = if (isAi) 16.dp else 4.dp
                    )
                )
                .background(if (isAi) Color.White else Pink)
                .padding(12.dp)
        ) {
            val displayContent = if (message.isStreaming) {
                message.content + if (cursorAlpha > 0.5f) "|" else " "
            } else {
                message.content
            }

            Text(
                text = displayContent,
                fontSize = 14.sp,
                color = if (isAi) Color.Black else Color.White,
                lineHeight = 20.sp
            )
        }

        if (!isAi) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Pink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
