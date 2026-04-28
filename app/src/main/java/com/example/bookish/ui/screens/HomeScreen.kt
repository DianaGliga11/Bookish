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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.bookish.models.Book
import com.example.bookish.models.BookClub
import com.example.bookish.viewmodel.AuthViewModel
import com.example.bookish.viewmodel.BookViewModel
import com.example.bookish.viewmodel.UserState

data class BookDisplayData(
    val book: Book,
    val authorName: String = "",
    val genreName: String = "",
    val averagerating: Float = 0f,
    val reviewCount: Int = 0
)

@Composable
fun HomeScreen(
    onBookClick: (Book) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToShelf: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAI: () -> Unit,
    onClubClick: (BookClub) -> Unit,
    bookViewModel: BookViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()

) {
    var selectedTab by remember { mutableStateOf(0) }
    val currentUserState by authViewModel.currentUser.collectAsState()
    val username = when (currentUserState) {
        is UserState.Success ->
            (currentUserState as UserState.Success).user.username

        else -> "Reader"
    }

    val books by bookViewModel.books.collectAsState()
    val authors by bookViewModel.authors.collectAsState()
    val genres by bookViewModel.genres.collectAsState()
    val reviews by bookViewModel.reviews.collectAsState()
    val aiRecommendations by bookViewModel.recommandedBooks.collectAsState()
    val shelves by bookViewModel.allShelves.collectAsState()
    val shelfBooks by bookViewModel.allShelfBooks.collectAsState()

    val booksDisplay = remember(books, authors, genres, reviews) {
        books.map { book ->
            val author = authors.find { it.id_author == book.id_author }
            val genre = genres.find { it.id_genre == book.id_genre }
            val booksReviews = reviews.filter { it.id_book == book.id_book }
            val avgRating = if (booksReviews.isNotEmpty()) {
                booksReviews.map { it.rating }.average().toFloat()
            } else {
                0f
            }

            BookDisplayData(
                book = book,
                authorName = author?.name ?: "",
                genreName = genre?.type ?: "",
                averagerating = avgRating,
                reviewCount = booksReviews.size
            )
        }
    }

    val trendingBooks = remember(booksDisplay, shelves, shelfBooks) {
        val readShelfIds = shelves.filter { it.name.equals("Completed", ignoreCase = true) }
            .map { it.id_shelf }
        val bookReadCounts = shelfBooks.filter { it.id_shelf in readShelfIds }
            .groupBy { it.id_book }
            .mapValues { it.value.size }

        booksDisplay.sortedByDescending { bookDisplayData ->
            bookReadCounts[bookDisplayData.book.id_book] ?: 0
        }.take(10)
    }

    val popularClubs by bookViewModel.popularClubs.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    var selectedClubForJoin by remember { mutableStateOf<BookClub?>(null) }

    if (showJoinDialog && selectedClubForJoin != null) {
        JoinClubDialog(
            clubName = selectedClubForJoin!!.name,
            onConfirm = {
                val userState = currentUserState as? UserState.Success
                if (userState != null) {
                    bookViewModel.joinBookClub(
                        userId = userState.user.id_user,
                        clubId = selectedClubForJoin!!.id_book_club
                    )
                }
                showJoinDialog = false
            },
            onDismiss = { showJoinDialog = false }
        )
    }

    LaunchedEffect(Unit) {
        bookViewModel.loadBooks()
        bookViewModel.loadAuthors()
        bookViewModel.loadGenres()
        bookViewModel.loadReviews()
        bookViewModel.loadAllShelfData()
        bookViewModel.loadPopularClubs()
    }

    LaunchedEffect(currentUserState, books, reviews) {
        val userSuccess = currentUserState as? UserState.Success
        if (userSuccess != null && books.isNotEmpty()) {
            // Schimbăm apelul de la AI la manual
            bookViewModel.generateManualRecommendations(
                user = userSuccess.user,
                allBooks = books,
                userReviews = reviews.filter { it.id_user == userSuccess.user.id_user }
            )
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    selectedTab = index
                    when (index) {
                        0 -> {}
                        1 -> onNavigateToSearch()
                        2 -> onNavigateToShelf()
                        3 -> onNavigateToProfile()
                        4 -> onNavigateToAI()
                        5 -> onNavigateToFriends()
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFC0CB), Color.White),
                        startY = 0f,
                        endY = 400f
                    )
                )
                .verticalScroll(rememberScrollState())
        ) {
            HomeHeader(username = username)

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(title = "Trending Now")

            if (trendingBooks.isEmpty()) {
                EmptyStateText("No trending books yet")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(trendingBooks) { bookData ->
                        BookCard(bookData = bookData, onClick = { onBookClick(bookData.book) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            SectionTitle(title = "Popular Book Clubs")

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(popularClubs) { club ->
                    ClubCard(
                        name = club.name,
                        description = club.description,
                        onClick = {
                            selectedClubForJoin = club
                            showJoinDialog = true
                        }
                    )
                }
            }

            if (aiRecommendations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                SectionTitle(title = "Recommended For You", color = Color(0xFF9C27B0))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(aiRecommendations) { book ->
                        BookCard(
                            bookData = BookDisplayData(book = book),
                            onClick = { onBookClick(book) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun JoinClubDialog(clubName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Club") },
        text = { Text("Do you want to join \"$clubName\" and participate in the group chat?") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text("Join", color = Color(0xFFFF69B4))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ClubCard(
    name: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(220.dp)
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF69B4).copy(alpha = 0.1f)), // Roz pal
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.take(1).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF69B4),
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.LightGray.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "About this club:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFF424242),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Tap to join →",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF69B4),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun EmptyStateText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.Gray
        )
    }
}

@Composable
fun SectionTitle(title: String, color: Color = Color.Black) {
    Text(
        text = title,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun HomeHeader(
    username: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Welcome Back, $username!",
                fontSize = 16.sp,
                color = Color.Gray
            )
            Text(
                text = "Bookish",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF69B4)
            )
        }

        IconButton(
            onClick = {/*TODO: Notifications */ }
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = Color(0xFFFF69B4)
            )
        }
    }
}

@Composable
fun BookCard(
    bookData: BookDisplayData,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (bookData.book.coverImageUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(bookData.book.coverImageUrl),
                        contentDescription = bookData.book.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFFC0CB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFFFF69B4)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp)
            ) {
                Text(
                    text = bookData.book.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookData.authorName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (bookData.averagerating > 0)
                            String.format("%.1f", bookData.averagerating) else "N/A",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                    if (bookData.reviewCount > 0) {
                        Text(
                            text = "(${bookData.reviewCount})",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        contentColor = Color.White,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            BottomNavItem("Home", Icons.Default.Home),
            BottomNavItem("Search", Icons.Default.Search),
            BottomNavItem("Shelf", Icons.Default.Book),
            BottomNavItem("Profile", Icons.Default.Star),
            BottomNavItem("AI", Icons.Default.Star),
            BottomNavItem("Friends", Icons.Default.Person)
        )

        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 10.sp
                    )
                },
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFFF69B4),
                    selectedTextColor = Color(0xFFFF69B4),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color(0xFFFFC0CB).copy(alpha = 0.3f)
                )
            )
        }
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector
)