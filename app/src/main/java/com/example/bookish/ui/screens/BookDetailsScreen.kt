package com.example.bookish.ui.screens

import android.graphics.Color.alpha
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.bookish.models.Book
import com.example.bookish.models.Review
import com.example.bookish.models.Shelf
import com.example.bookish.models.User
import com.example.bookish.viewmodel.AuthViewModel
import com.example.bookish.viewmodel.BookViewModel
import com.example.bookish.viewmodel.UserState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Pink = Color(0xFFFF69B4)
private val PinkLight = Color(0xFFFFC0CB)
private val PinkPale = Color(0xFFFFF0F5)
private val Gold = Color(0xFFFFD700)
private val GoldDark = Color(0xFFFFA000)
private val TextPrimary = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    book: Book,
    onBackClick: () -> Unit,
    bookViewModel: BookViewModel,
    authViewModel: AuthViewModel
) {
    val currentUserState by authViewModel.currentUser.collectAsState()
    val userShelves by authViewModel._userShalves.collectAsState()
    val reviews by bookViewModel.reviews.collectAsState()
    val comments by bookViewModel.comments.collectAsState()
    val authors by bookViewModel.authors.collectAsState()
    val genres by bookViewModel.genres.collectAsState()
    val users by bookViewModel.users.collectAsState()
    val currentUser = (currentUserState as? UserState.Success)?.user
    val author = authors.find { it.id_author == book.id_author }
    val genre = genres.find { it.id_genre == book.id_genre }
    val wantToReadShelf = userShelves.find { it.name.equals("Want to Read", ignoreCase = true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showReviewDialog by remember { mutableStateOf(false) }
    var isAddedToShelf by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val averageRating = if (reviews.isNotEmpty())
        reviews.map { it.rating }.average().toFloat() else 0f

    LaunchedEffect(book.id_book) {
        bookViewModel.loadReviewsForBook(book.id_book)
        bookViewModel.loadAuthors()
        bookViewModel.loadGenres()
        bookViewModel.loadUsers()
        bookViewModel.loadComments()
    }

    if (showReviewDialog) {
        ReviewDialog(
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, comment ->
                currentUser?.let { user ->
                    bookViewModel.addReview(user.id_user, book.id_book, rating, comment)
                    scope.launch {
                        snackbarHostState.showSnackbar("Review added successfully")
                    }
                }
                showReviewDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = book.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Pink,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
        ) {
            BookHeroSection(book = book)

            BookInfoCard(
                book = book,
                authorName = author?.name ?: "Unknown Author",
                genreName = genre?.type ?: "Unknown Genre",
                averageRating = averageRating,
                reviewCount = reviews.size
            )

            DescriptionSection(
                description = book.description,
                isExpanded = isExpanded,
                onToggle = { isExpanded = !isExpanded }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = PinkLight.copy(alpha = 0.5f)
            )

            ActionButtons(
                isAddedToShelf = isAddedToShelf,
                currentUser = currentUser,
                wantToReadShelf = wantToReadShelf,
                book = book,
                authViewModel = authViewModel,
                bookViewModel = bookViewModel,
                onShelfAdded = {
                    isAddedToShelf = true
                    scope.launch {
                        snackbarHostState.showSnackbar("Added to \"Want To Read\" shelf successfully")
                    }
                },
                onWriteReview = { showReviewDialog = true },
                onError = { msg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = PinkLight.copy(alpha = 0.5f)
            )

            ReviewsSection(
                reviews = reviews,
                users = users,
                comments = comments
            )

            Spacer(modifier = Modifier.height(40.dp))

        }
    }
}

@Composable
private fun ReviewsSection(
    reviews: List<Review>,
    users: List<User>,
    comments: List<com.example.bookish.models.Comment>
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text(
            text = "Recenzii${if (reviews.isNotEmpty()) " (${reviews.size})" else ""}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (reviews.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PinkPale),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = null,
                        tint = PinkLight,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nicio recenzie încă.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Fii primul care recenzează!",
                        color = Pink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                reviews.take(10).forEach { review ->
                    val reviewer = users.find { it.id_user == review.id_user }
                    val reviewComment = comments.find { it.id_review == review.id_review }
                    ReviewCard(
                        review = review,
                        reviewer = reviewer,
                        comment = reviewComment
                    )
                }
            }
        }
    }
}


@Composable
private fun ActionButtons(
    isAddedToShelf: Boolean,
    currentUser: User?,
    wantToReadShelf: Shelf?,
    book: Book,
    authViewModel: AuthViewModel,
    bookViewModel: BookViewModel,
    onShelfAdded: () -> Unit,
    onWriteReview: () -> Unit,
    onError: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Want to Read ────────────────────────────────────────────────
        Button(
            onClick = {
                if (currentUser == null) {
                    onError("You have to be authenticated!")
                    return@Button
                }
                if (isAddedToShelf) return@Button

                if (wantToReadShelf != null) {
                    // Raftul există → adaugă direct
                    bookViewModel.addBookToShelf(
                        userId = currentUser.id_user,
                        shelfId = wantToReadShelf.id_shelf,
                        bookId = book.id_book
                    )
                    onShelfAdded()
                } else {
                    // Creează raftul "Want to Read" și adaugă cartea
                    authViewModel.createShelf("Want to Read")
                    // Reîncarcă rafturile și apoi adaugă — simplificat prin callback
                    onError("Shelf \"Want to Read\" created. Push again to add to shelf \"Want To Read\".")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAddedToShelf) Color(0xFF4CAF50) else Pink
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(6.dp)
        ) {
            Icon(
                imageVector = if (isAddedToShelf) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isAddedToShelf) "Added in library ✓" else "Add to Want to Read",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        // ── Write Review ────────────────────────────────────────────────
        Button(
            onClick = {
                if (currentUser == null) {
                    onError("You have to be authenticated for writing a review!")
                } else {
                    onWriteReview()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, Pink),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.RateReview,
                contentDescription = null,
                tint = Pink,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Write a review",
                color = Pink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}


@Composable
private fun DescriptionSection(
    description: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    if (description.isBlank()) return

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            text = "Description",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = TextSecondary,
            lineHeight = 22.sp,
            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
            overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
        )
        if (description.length > 200) {
            TextButton(onClick = onToggle) {
                Text(
                    text = if (isExpanded) "Show less " else "Show more",
                    color = Pink,
                    fontSize = 13.sp
                )
            }
        }
    }
}


@Composable
private fun BookInfoCard(
    book: Book,
    authorName: String,
    genreName: String,
    averageRating: Float,
    reviewCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = book.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "de $authorName",
            fontSize = 15.sp,
            color = Pink,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gen badge
            if (genreName.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PinkLight.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = genreName,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color = Pink,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Rating badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Gold.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (averageRating > 0) String.format(
                            "%.1f",
                            averageRating
                        ) else "N/A",
                        fontSize = 13.sp,
                        color = GoldDark,
                        fontWeight = FontWeight.Bold
                    )
                    if (reviewCount > 0) {
                        Text(
                            text = "($reviewCount)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Stele vizuale
        if (averageRating > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            StarRatingDisplay(rating = averageRating)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Stele vizuale (read-only)
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun StarRatingDisplay(rating: Float, maxStars: Int = 5, starSize: Int = 22) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..maxStars) {
            val filled = rating >= i
            val half = !filled && rating >= i - 0.5f
            Icon(
                imageVector = when {
                    filled -> Icons.Default.Star
                    half -> Icons.Default.StarHalf
                    else -> Icons.Default.StarBorder
                },
                contentDescription = null,
                tint = if (filled || half) Gold else Color.LightGray,
                modifier = Modifier.size(starSize.dp)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Stele interactive (pentru review)
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun StarRatingInput(
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit,
    maxStars: Int = 5
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..maxStars) {
            val scale by animateFloatAsState(
                targetValue = if (selectedRating >= i) 1.2f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "star_scale_$i"
            )
            Icon(
                imageVector = if (selectedRating >= i) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "$i stars",
                tint = if (selectedRating >= i) Gold else Color.LightGray,
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .clickable { onRatingSelected(i) }
            )
        }
    }
}


@Composable
fun BookHeroSection(book: Book) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(PinkLight, Pink.copy(alpha = 0.4f)))
                )
        )
    }
    if (book.coverImageUrl.isNotEmpty()) {
        Image(
            painter = rememberAsyncImagePainter(model = book.coverImageUrl),
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                        startY = 100f
                    )
                )
        )
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(130.dp)
                .height(190.dp)
                .shadow(16.dp, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            if (book.coverImageUrl.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(book.coverImageUrl),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(PinkLight, Pink))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewDialog(
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, comment: String) -> Unit
) {
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var showComment by remember { mutableStateOf(false) }
    val ratingLabels = listOf("", "Weak", "Acceptable", "Good", "Very Good", "Excellent!")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your review",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
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

                Text(
                    text = "How much did you enjoy the book?",
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Stele interactive
                StarRatingInput(
                    selectedRating = rating,
                    onRatingSelected = { rating = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Label rating
                AnimatedVisibility(visible = rating > 0) {
                    Text(
                        text = ratingLabels.getOrElse(rating) { "" },
                        fontSize = 15.sp,
                        color = Pink,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle comentariu
                TextButton(
                    onClick = { showComment = !showComment }
                ) {
                    Text(
                        text = if (showComment) "No comment" else "+ Add comment",
                        color = Pink,
                        fontSize = 13.sp
                    )
                }

                // Câmp comentariu expandabil
                AnimatedVisibility(
                    visible = showComment,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            placeholder = { Text("Write your review...", fontSize = 13.sp) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Pink,
                                unfocusedBorderColor = PinkLight,
                                focusedLabelColor = Pink
                            ),
                            maxLines = 5
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Butoane
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
                            if (rating > 0) onSubmit(rating, comment)
                        },
                        enabled = rating > 0,
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Pink,
                            disabledContainerColor = PinkLight
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Send review",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    review: Review,
    reviewer: User?,
    comment: com.example.bookish.models.Comment?
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = PinkPale),
        elevation = CardDefaults.cardElevation(0.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, PinkLight.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.spacedBy(10.dp)
            ) {
                // Avatar
                Box(
                    modifier         = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(listOf(PinkLight, Pink))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = reviewer?.username?.firstOrNull()?.uppercase() ?: "?",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = reviewer?.username ?: "User",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        color      = TextPrimary
                    )
                }

                // Stars
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { i ->
                        Icon(
                            imageVector    = if (i < review.rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint           = if (i < review.rating) Gold else Color.LightGray,
                            modifier       = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (!comment?.content.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text      = "\"${comment!!.content}\"",
                    fontSize  = 13.sp,
                    color     = TextSecondary,
                    lineHeight = 20.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

