package com.example.bookish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bookish.models.Book
import com.example.bookish.navigation.BookishNavigation
import com.example.bookish.ui.screens.BookDetailsScreen
import com.example.bookish.ui.screens.HomeScreen
import com.example.bookish.ui.screens.LoginScreen
import com.example.bookish.ui.screens.ProfileScreen
import com.example.bookish.ui.screens.RegisterScreen
import com.example.bookish.ui.screens.ShelfScreen
import com.example.bookish.ui.screens.WelcomeScreen
import com.example.bookish.ui.theme.BooklyTheme
import com.example.bookish.viewmodel.AuthViewModel
import com.example.bookish.viewmodel.BookViewModel
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        setContent {
            BookishApp()
        }
    }
}

@Composable
fun BookishApp() {
    val navController = rememberNavController()
    var selectedBook by remember { mutableStateOf<Book?>(null) }

    // ViewModels partajate pentru toată aplicația
    val authViewModel: AuthViewModel = viewModel()
    val bookViewModel: BookViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        // Welcome Screen
        composable("welcome") {
            WelcomeScreen(
                onStartReading = {
                    navController.navigate("login")
                }
            )
        }

        // Login Screen
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate("register")
                },
                onBackPressed = {
                    navController.navigate("welcome") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        // Register Screen
        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBackPressed = {
                    navController.popBackStack()
                },
                viewModel = authViewModel
            )
        }

        // Home Screen
        composable("home") {
            HomeScreen(
                onBookClick = { book ->
                    selectedBook = book
                    navController.navigate("book_details")
                },
                onNavigateToSearch = {
                    navController.navigate("search")
                },
                onNavigateToShelf = {
                    navController.navigate("shelf")
                },
                onNavigateToFriends = {
                    navController.navigate("friends")
                },
                onNavigateToAI = {
                    navController.navigate("ai")
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                bookViewModel = bookViewModel,
                authViewModel = authViewModel
            )
        }

        // Book Details Screen
        composable("book_details") {
            selectedBook?.let { book ->
                key(book.id_book) {
                    BookDetailsScreen(
                        book = book,
                        onBackClick = {
                            navController.popBackStack()
                        },
                        bookViewModel = bookViewModel,
                        authViewModel = authViewModel
                    )
                }
            }
        }

        // Search Screen
        composable("search") {
            PlaceholderScreen(
                title = "Search",
                subtitle = "Search for books, authors, and genres",
                onBack = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        // Shelf Screen
        composable("shelf") {
            ShelfScreen(
                onBackClick = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onBookClick = { book ->
                    selectedBook = book
                    navController.navigate("book_details")
                },
                authViewModel = authViewModel,
                bookViewModel = bookViewModel
            )
        }

        // Friends Screen
        composable("friends") {
            PlaceholderScreen(
                title = "Friends",
                subtitle = "Connect with fellow book lovers",
                onBack = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                icon = Icons.Default.People
            )
        }

        // AI Assistant Screen
        composable("ai") {
            PlaceholderScreen(
                title = "AI Assistant",
                subtitle = "Get personalized book recommendations",
                onBack = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                icon = Icons.Default.AutoAwesome
            )
        }

        // Profile Screen
        composable("profile") {
            ProfileScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Book,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFF69B4),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFFF69B4),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Coming Soon!",
                    fontSize = 16.sp,
                    color = Color(0xFFFF69B4),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
