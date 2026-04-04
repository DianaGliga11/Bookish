package com.example.bookish.ui.screens

import android.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.bookish.models.Book
import com.example.bookish.models.Challenge
import com.example.bookish.models.Shelf
import com.example.bookish.models.ShelfBook
import com.example.bookish.viewmodel.AuthViewModel
import com.example.bookish.viewmodel.BookViewModel
import com.example.bookish.viewmodel.UserState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Pink = Color(0xFFFF69B4)
private val PinkLight = Color(0xFFFFC0CB)
private val PinkPale = Color(0xFFFFF0F5)
private val Purple = Color(0xFF9C27B0)
private val PurpleLight = Color(0xFFE1BEE7)

private val Gold = Color(0xFFFFD740)
private val Teal = Color(0xFF00BCD4)
private val TealLight = Color(0xFFB2EBF2)
private val Green = Color(0xFF4CAF50)
private val GreenLight = Color(0xFFC8E6C9)
private val TextPrimary = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onBackClick: () -> Unit,
    onBookClick: (Book) -> Unit,
    authViewModel: AuthViewModel,
    bookViewModel: BookViewModel
) {
    val currentUserState by authViewModel.currentUser.collectAsState()
    val userShelves by authViewModel._userShalves.collectAsState()
    val books by bookViewModel.books.collectAsState()

    val currentUser = (currentUserState as? UserState.Success)?.user
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var selectedShelfId by remember { mutableStateOf<String?>(null) }
    var booksInSelectedShelf by remember { mutableStateOf<List<Book>>(emptyList()) }
    var isLoadingBooks by remember { mutableStateOf(false) }

    var showMoveBookDialog by remember { mutableStateOf(false) }
    var sourceShelfId by remember { mutableStateOf<String?>(null) }
    var bookToMove by remember { mutableStateOf<Book?>(null) }

    var isLoadingChallenges by remember { mutableStateOf(false) }
    var challenges by remember { mutableStateOf<List<Challenge>>(emptyList()) }
    var showCreateChallengeDialog by remember { mutableStateOf(false) }
    var showUpdateProgressDialog by remember { mutableStateOf(false) }
    var selectedChallenge by remember { mutableStateOf<Challenge?>(null) }

    var refreshTrigger by remember { mutableStateOf(0) }

    val db = FirebaseFirestore.getInstance()

    val defaultShelfNames = listOf("Want to Read", "On Going", "Completed")

    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            try {
                val existingShelvesSnapshot = db.collection("shelves")
                    .whereEqualTo("id_user", user.id_user)
                    .get()
                    .await()

                val existingShelfNames = existingShelvesSnapshot.documents.mapNotNull {
                    it.getString("name")
                }

                defaultShelfNames.forEach { shelfName ->
                    if (shelfName !in existingShelfNames) {
                        authViewModel.createShelf(shelfName)
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Error while loading shelves: ${e.message}")
                }
            }
        }
    }


    LaunchedEffect(Unit) {
        bookViewModel.loadBooks()
    }

    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            isLoadingChallenges = true
            try {
                val snapshot = db.collection("challenges")
                    .whereEqualTo("id_user", user.id_user)
                    .get()
                    .await()

                challenges = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Challenge::class.java)?.apply {
                        id_challenge = doc.id
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Error while loading challenges: ${e.message}")
                }
            } finally {
                isLoadingChallenges = false
            }
        }
    }
    LaunchedEffect(selectedShelfId) {
        selectedShelfId?.let { shelfId ->
            isLoadingBooks = true
            try {
                val shelfBooksSnapshot = db.collection("shelf_books")
                    .whereEqualTo("id_shelf", shelfId).get().await()
                val bookIds = shelfBooksSnapshot.documents.mapNotNull { it.getString("id_book") }
                booksInSelectedShelf = books.filter { it.id_book in bookIds }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Error: ${e.message}") }
            } finally {
                isLoadingBooks = false
            }
        }
    }

    if (showCreateShelfDialog) {
        CreateShelfDialog(
            onDismiss = { showCreateShelfDialog = false },
            onConfirm = { shelfName ->
                authViewModel.createShelf(shelfName)
                showCreateShelfDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Shelf \"$shelfName\" created!")
                }
            }
        )
    }

    if (showCreateChallengeDialog && currentUser != null) {
        CreateChallengeDialog(
            userId = currentUser.id_user,
            onDismiss = { showCreateChallengeDialog = false },
            onChallengeCreated = {
                showCreateChallengeDialog = false
                scope.launch {
                    try {
                        val snapshot = db.collection("challenges")
                            .whereEqualTo("id_user", currentUser.id_user)
                            .get()
                            .await()
                        challenges = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(Challenge::class.java)?.apply {
                                id_challenge = doc.id
                            }
                        }
                        snackbarHostState.showSnackbar("Challenge created successfully!")
                    } catch (e: Exception) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Error while loading challenges: ${e.message}")
                        }
                    }
                }
            },
            db = db
        )
    }

    if (showUpdateProgressDialog && selectedChallenge != null) {
        UpdateProgressDialog(
            currentProgress = selectedChallenge!!.progress,
            onDismiss = {
                showUpdateProgressDialog = false
                selectedChallenge = null
            },
            onConfirm = { newProgress ->
                val challengeId = selectedChallenge?.id_challenge
                scope.launch {
                    try {
                        if(challengeId != null) {
                            db.collection("challenges")
                                .document(challengeId)
                                .update("progress", newProgress)
                                .await()

                            challenges = challenges.map {
                                if (it.id_challenge == challengeId) {
                                    it.copy(progress = newProgress)
                                } else it
                            }
                            snackbarHostState.showSnackbar("Progress updated successfully!")
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error while updating progress: ${e.message}")
                    }
                }
                showUpdateProgressDialog = false
                selectedChallenge = null
            }
        )
    }

    if (showMoveBookDialog && bookToMove != null && sourceShelfId != null) {
        MoveBookDialog(
            book = bookToMove!!,
            currentShelfId = sourceShelfId!!,
            allShelves = userShelves,
            onDismiss = {
                showMoveBookDialog = false
                bookToMove = null
                sourceShelfId = null
            },
            onMoveBook = { targetShelfId ->
                scope.launch {
                    try {
                        val deleteQuery = db.collection("shelf_books")
                            .whereEqualTo("id_shelf", sourceShelfId)
                            .whereEqualTo("id_book", bookToMove!!.id_book)
                            .get()
                            .await()
                        deleteQuery.documents.forEach { it.reference.delete().await() }

                        val shelfBook = ShelfBook(
                            id_shelf = targetShelfId,
                            id_book = bookToMove!!.id_book
                        )
                        db.collection("shelf_books").add(shelfBook).await()

                        val targetShelfName =
                            userShelves.find { it.id_shelf == targetShelfId }?.name
                        snackbarHostState.showSnackbar(
                            "\"${bookToMove!!.title}\" moved in ${targetShelfName}"
                        )

                        showMoveBookDialog = false
                        bookToMove = null
                        sourceShelfId = null

                        val currentId = selectedShelfId
                        selectedShelfId = null
                        selectedShelfId = currentId

                        refreshTrigger++

                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error while moving book: ${e.message}")
                    }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My library",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Pink,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateShelfDialog = true },
                containerColor = Pink,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create new shelf",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            if (currentUser == null) {
                // User not logged in
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = PinkLight,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Log in for more features",
                            fontSize = 16.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Header stats
                ShelfHeaderStats(
                    shelves = userShelves,
                    books = books,
                    db = db
                )

                Spacer(modifier = Modifier.height(16.dp))

                ChallengeSection(
                    challenges = challenges,
                    isLoading = isLoadingChallenges,
                    onCreateChallenge = { showCreateChallengeDialog = true },
                    onChallengeClick = { challenge ->
                        if (challenge.progress < 100.0) {
                            selectedChallenge = challenge
                            showUpdateProgressDialog = true
                        }
                    },
                    onDeleteChallenge = { challenge ->
                        scope.launch {
                            try {
                                db.collection("challenges")
                                    .document(challenge.id_challenge)
                                    .delete()
                                    .await()
                                challenges =
                                    challenges.filter { it.id_challenge != challenge.id_challenge }
                                snackbarHostState.showSnackbar("Challenge deleted successfully!")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Error while deleting challenge: ${e.message}")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item{
                        Text(
                            text = "Shelves",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                    }
                    items(userShelves.sortedBy {
                        when (it.name) {
                            "Want to Read" -> 0
                            "On Going" -> 1
                            "Completed" -> 2
                            else -> 3
                        }
                    }) { shelf ->
                        ShelfCard(
                            shelf = shelf,
                            refreshCounter = refreshTrigger,
                            isExpanded = selectedShelfId == shelf.id_shelf,
                            books = if (selectedShelfId == shelf.id_shelf) booksInSelectedShelf else emptyList(),
                            isLoadingBooks = isLoadingBooks && selectedShelfId == shelf.id_shelf,
                            onExpandToggle = {
                                selectedShelfId =
                                    if (selectedShelfId == shelf.id_shelf) null else shelf.id_shelf
                            },
                            onBookClick = onBookClick,
                            onBookLongClick = { book, shelfId ->
                                bookToMove = book
                                sourceShelfId = shelfId
                                showMoveBookDialog = true
                            },
                            db = db
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}


@Composable
private fun ShelfHeaderStats(
    shelves: List<Shelf>,
    books: List<Book>,
    db: FirebaseFirestore
) {
    var totalBooks by remember { mutableStateOf(0) }

    LaunchedEffect(shelves) {
        try {
            val allShelfBooks = shelves.flatMap { shelf ->
                db.collection("shelf_books")
                    .whereEqualTo("id_shelf", shelf.id_shelf)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.getString("id_book") }
            }.distinct()
            totalBooks = allShelfBooks.size
        } catch (e: Exception) {
            totalBooks = 0
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = PinkPale,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.Book,
                value = totalBooks.toString(),
                label = "Cărți",
                color = Pink
            )
            StatItem(
                icon = Icons.Default.MenuBook,
                value = shelves.size.toString(),
                label = "Shelves",
                color = Purple
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun ChallengeSection(
    challenges: List<Challenge>,
    isLoading: Boolean,
    onCreateChallenge: () -> Unit,
    onChallengeClick: (Challenge) -> Unit,
    onDeleteChallenge: (Challenge) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Challenges",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
        }

        Surface(
            onClick = onCreateChallenge,
            shape = CircleShape,
            color = Pink,
            shadowElevation = 4.dp
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add challenge",
                tint = Color.White,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Pink)
        }
    } else if (challenges.isEmpty()) {
        EmptyChallengesState()
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(challenges) { challenge ->
                CompactChallengeCard(
                    challenge,
                    { onChallengeClick(challenge) },
                    { onDeleteChallenge(challenge) })
            }
        }
    }
}

@Composable
fun EmptyChallengesState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PinkPale),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = Pink,
                modifier = Modifier.size(32.dp)
            )

            Column {
                Text(
                    text = "No challenges yet",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Push \"New\" button to create a new challenge",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

//nu-mi place cum arata
@Composable
fun CompactChallengeCard(challenge: Challenge, onClick: () -> Unit, onDelete: () -> Unit) {
    val progress = (challenge.progress / 100.0).coerceIn(0.0, 1.0).toFloat()
    val isCompleted = challenge.progress >= 100.0
    val statusColor = if (isCompleted) Green else Pink
    val backgroundColor = if (isCompleted) Color(0xFFF1FBF1) else Color(0xFFFFF5F8)

    Card(
        modifier = Modifier
            .width(280.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = if (isCompleted) "Done" else "In Progress",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = challenge.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (challenge.description.isNotEmpty()) {
                Text(
                    text = challenge.description,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${challenge.progress.toInt()}%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = statusColor
                )

                Text(
                    text = " completed",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(12.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "Ends on ${
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(
                            challenge.date_finish.toDate()
                        )
                    }",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun CompactDateChip(label: String, date: Date, modifier: Modifier) {
    val dateFormat = SimpleDateFormat("dd MMM", Locale("ro"))
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Purple.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextSecondary
            )
            Text(
                text = dateFormat.format(date),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun CreateChallengeDialog(
    userId: String,
    onDismiss: () -> Unit,
    onChallengeCreated: () -> Unit,
    db: FirebaseFirestore
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var durationMonths by remember { mutableStateOf("3") }

    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Challenge",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Title (ex: Read 10 books", fontSize = 14.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Pink,
                        unfocusedBorderColor = PinkLight
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    placeholder = { Text("Description (optional)", fontSize = 14.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Pink,
                        unfocusedBorderColor = PinkLight
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = durationMonths,
                    onValueChange = { if (it.all { char -> char.isDigit() }) durationMonths = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Duration (months)", fontSize = 14.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Pink,
                        unfocusedBorderColor = PinkLight
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                scope.launch {
                                    try {
                                        val now = Timestamp.now()
                                        val calendar = Calendar.getInstance()
                                        calendar.add(
                                            Calendar.MONTH,
                                            durationMonths.toIntOrNull() ?: 3
                                        )
                                        val endDate = Timestamp(calendar.time)
                                        val challenge = com.example.bookish.models.Challenge(
                                            id_user = userId,
                                            title = title.trim(),
                                            description = description.trim(),
                                            date_start = now,
                                            date_finish = endDate,
                                            progress = 0.0
                                        )


                                        db.collection("challenges").add(challenge).await()
                                        onChallengeCreated()
                                    } catch (e: Exception) {
                                        //??
                                    }
                                }
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Pink,
                            disabledContentColor = PinkLight
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Create",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

//nu functioneaza
@Composable
private fun UpdateProgressDialog(
    currentProgress: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var newProgress by remember { mutableStateOf(currentProgress) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Update progress",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Pink.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${newProgress.toInt()}%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Pink
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Slider(
                    value = newProgress.toFloat(),
                    onValueChange = { newProgress = it.toDouble() },
                    valueRange = 0f..100f,
                    steps = 100,
                    colors = SliderDefaults.colors(
                        thumbColor = Pink,
                        activeTrackColor = Pink,
                        inactiveTrackColor = Pink.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0%", fontSize = 12.sp, color = TextSecondary)
                    Text("100%", fontSize = 12.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { onConfirm(newProgress.toDouble()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Save Progress",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(
    shelf: Shelf,
    isExpanded: Boolean,
    books: List<Book>,
    isLoadingBooks: Boolean,
    refreshCounter: Int,
    onExpandToggle: () -> Unit,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book, String) -> Unit,
    db: FirebaseFirestore
) {
    var bookCount by remember { mutableStateOf(0) }

    LaunchedEffect(shelf.id_shelf, refreshCounter) {
        try {
            val count = db.collection("shelf_books")
                .whereEqualTo("id_shelf", shelf.id_shelf)
                .get()
                .await()
                .size()
            bookCount = count
        } catch (e: Exception) {
            bookCount = 0
        }
    }

    LaunchedEffect(shelf.id_shelf) {
        try {
            val count = db.collection("shelf_books")
                .whereEqualTo("id_shelf", shelf.id_shelf)
                .get()
                .await()
                .size()
            bookCount = count
        } catch (e: Exception) {
            bookCount = 0
        }
    }

    val (shelfColor, shelfIcon) = when (shelf.name) {
        "Want to Read" -> Pair(Pink, Icons.Default.BookmarkAdd)
        "On Going" -> Pair(Teal, Icons.Default.MenuBook)
        "Completed" -> Pair(Green, Icons.Default.CheckCircle)
        else -> Pair(Purple, Icons.Default.Star)
    }

    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shelf_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable { onExpandToggle() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isExpanded) 8.dp else 4.dp
        )
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(shelfColor.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(shelfColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = shelfIcon,
                            contentDescription = null,
                            tint = shelfColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = shelf.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "$bookCount ${if (bookCount == 1) "book" else "books"}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (isExpanded) "Close" else "Open",
                    tint = shelfColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isLoadingBooks) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = shelfColor)
                        }
                    } else if (books.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(shelfColor.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = shelfColor.copy(alpha = 0.3f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No book added",
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(books) { book ->
                                ShelfBookCard(
                                    book = book,
                                    onClick = { onBookClick(book) },
                                    onLongClick = {
                                        onBookLongClick(book, shelf.id_shelf)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ShelfBookCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(100.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                if (book.coverImageUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(book.coverImageUrl),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(PinkLight, Pink))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Text(
                    text = book.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = TextPrimary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun CreateShelfDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var shelfName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New shelf",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = shelfName,
                    onValueChange = { shelfName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Shelf name", fontSize = 14.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Pink,
                        unfocusedBorderColor = PinkLight
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            if (shelfName.isNotBlank()) {
                                onConfirm(shelfName.trim())
                            }
                        },
                        enabled = shelfName.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Pink,
                            disabledContainerColor = PinkLight
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Create",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveBookDialog(
    book: Book,
    currentShelfId: String,
    allShelves: List<Shelf>,
    onDismiss: () -> Unit,
    onMoveBook: (targetShelfId: String) -> Unit
) {
    val availableShalves = allShelves.filter { it.id_shelf != currentShelfId }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Move book",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PinkPale)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (book.coverImageUrl.isNotEmpty()) Color.Transparent else Pink)
                    ) {
                        if (book.coverImageUrl.isNotEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(book.coverImageUrl),
                                contentDescription = book.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (availableShalves.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No shelf available",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableShalves.forEach { shelf ->
                            val (shelfColor, shelfIcon) = when (shelf.name) {
                                "Want to Read" -> Pair(Pink, Icons.Default.BookmarkAdd)
                                "On Going..." -> Pair(Teal, Icons.Default.MenuBook)
                                "Completed" -> Pair(Green, Icons.Default.CheckCircle)
                                else -> Pair(Purple, Icons.Default.Star)
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMoveBook(shelf.id_shelf) },
                                shape = RoundedCornerShape(12.dp),
                                color = shelfColor.copy(alpha = 0.1f),
                                border = BorderStroke(
                                    1.dp,
                                    shelfColor.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(shelfColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = shelfIcon,
                                            contentDescription = null,
                                            tint = shelfColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Text(
                                        text = shelf.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = shelfColor,
                                        modifier = Modifier.size(20.dp)
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

