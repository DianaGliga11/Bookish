package com.example.bookish.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.bookish.models.Book
import com.example.bookish.models.Shelf
import com.example.bookish.viewmodel.AuthViewModel
import com.example.bookish.viewmodel.BookViewModel
import com.example.bookish.viewmodel.UserState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Pink = Color(0xFFFF69B4)
private val PinkLight = Color(0xFFFFC0CB)
private val PinkPale = Color(0xFFFFF0F5)
private val Purple = Color(0xFF9C27B0)
private val PurpleLight = Color(0xFFE1BEE7)
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

    val db = FirebaseFirestore.getInstance()

    val defaultShelfNames = listOf("Want to Read", "Reading", "Read")

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
            }
        }
    }


    LaunchedEffect(Unit) {
        bookViewModel.loadBooks()
    }

    LaunchedEffect(selectedShelfId) {
        selectedShelfId?.let { shelfId ->
            isLoadingBooks = true
            try {
                val shelfBooksSnapshot = db.collection("shelf_books")
                    .whereEqualTo("id_shelf", shelfId)
                    .get()
                    .await()

                val bookIds = shelfBooksSnapshot.documents.mapNotNull {
                    it.getString("id_book")
                }

                booksInSelectedShelf = books.filter { it.id_book in bookIds }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Error while loading books: ${e.message}")
                }
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

                // Shelves list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(userShelves.sortedBy {
                        when (it.name) {
                            "Want to Read" -> 0
                            "Reading" -> 1
                            "Read" -> 2
                            else -> 3
                        }
                    }) { shelf ->
                        ShelfCard(
                            shelf = shelf,
                            isExpanded = selectedShelfId == shelf.id_shelf,
                            books = if (selectedShelfId == shelf.id_shelf) booksInSelectedShelf else emptyList(),
                            isLoadingBooks = isLoadingBooks && selectedShelfId == shelf.id_shelf,
                            onExpandToggle = {
                                selectedShelfId =
                                    if (selectedShelfId == shelf.id_shelf) null else shelf.id_shelf
                            },
                            onBookClick = onBookClick,
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
private fun ShelfCard(
    shelf: Shelf,
    isExpanded: Boolean,
    books: List<Book>,
    isLoadingBooks: Boolean,
    onExpandToggle: () -> Unit,
    onBookClick: (Book) -> Unit,
    db: FirebaseFirestore
) {
    var bookCount by remember { mutableStateOf(0) }

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
        "Reading" -> Pair(Teal, Icons.Default.MenuBook)
        "Read" -> Pair(Green, Icons.Default.CheckCircle)
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
                                    onClick = { onBookClick(book) }
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
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