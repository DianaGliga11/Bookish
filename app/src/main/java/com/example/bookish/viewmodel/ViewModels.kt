package com.example.bookish.viewmodel

import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookish.models.*
import com.example.bookish.repository.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.google.android.gms.common.util.CollectionUtils.mapOf
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.collections.mapOf

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class UserState {
    object Loading : UserState()
    data class Success(val user: User) : UserState()
    data class Error(val message: String) : UserState()
}

sealed class BooksState {
    object Loading : BooksState()
    data class Success(val books: List<Book>) : BooksState()
    data class Error(val message: String) : BooksState()
}

sealed class OperationState {
    object Idle : OperationState()
    object Loading : OperationState()
    data class Success(val message: String) : OperationState()
    data class Error(val message: String) : OperationState()
}

class AuthViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val shelfRepository = ShelfRepository()


    private val db = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)

    private val _currentUser = MutableStateFlow<UserState>(UserState.Loading)
    val currentUser: StateFlow<UserState> = _currentUser

    private val _userShelves = MutableStateFlow<List<Shelf>>(emptyList())
    val _userShalves: StateFlow<List<Shelf>> = _userShelves

    val authState: StateFlow<AuthState> = _authState

    init {
        loadCurrentUser()
    }

    fun register(email: String, password: String, username: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.register(email, password, username)
            _authState.value = if (result.isSuccess) {
                loadCurrentUser()
                AuthState.Success(result.getOrNull() ?: "")
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.login(email, password)
            _authState.value = if (result.isSuccess) {
                loadCurrentUser()
                AuthState.Success("Login successful")
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _authState.value = AuthState.Idle
        _currentUser.value = UserState.Loading
        _userShelves.value = emptyList()
    }

    fun getGoogleSignInClient(context: Context) = authRepository.getGoogleSignInClient(context)

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            Log.d(TAG, "Google Sign In called from UI")
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogle(idToken)
            _authState.value = if (result.isSuccess) {
                loadCurrentUser()
                AuthState.Success(result.getOrNull() ?: "")
            } else {
                Log.e(TAG, "Google Sign In FAILED: ${result.exceptionOrNull()?.message}")
                AuthState.Error(result.exceptionOrNull()?.message ?: "Google Sign In failed")
            }
        }
    }

    fun getCurrentUserId(): String? = authRepository.getCurrentUserId()

    fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                _currentUser.value = UserState.Loading
                val userId = authRepository.getCurrentUserId()

                if (userId != null) {
                    val doc = db.collection("users").document(userId).get().await()
                    val user = doc.toObject(User::class.java)?.apply {
                        id_user = doc.id
                    }
                    if (user != null) {
                        _currentUser.value = UserState.Success(user)
                        loadUserShelves(userId)
                    } else {
                        _currentUser.value = UserState.Error("User not found")
                    }
                } else {
                    _currentUser.value = UserState.Error("User not logged in")
                }
            } catch (e: Exception) {
                _currentUser.value = UserState.Error("Failed to load user: ${e.message}")
                Log.e("AuthViewModel", "Error loading current user", e)
            }
        }
    }

    private fun loadUserShelves(userId: String) {
        viewModelScope.launch {
            try {
                val shelves = shelfRepository.getUserShelves(userId)
                if (shelves.isSuccess) {
                    _userShelves.value = shelves.getOrNull() ?: emptyList()
                    Log.d("AuthViewModel", "User shelves loaded: $shelves")
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error loading user shelves", e)
            }
        }
    }

    fun uploadProfileImage(it: Uri) {
        _currentUser.value.let { userState ->
            if (userState is UserState.Success) {
                val userId = userState.user.id_user
                viewModelScope.launch {
                    try {
                        val result = userRepository.uploadProfileImage(userId, it)
                        if (result.isSuccess) {
                            val downloadUrl = result.getOrNull()!!
                            _currentUser.value =
                                UserState.Success(userState.user.copy(profileImageUrl = downloadUrl))
                        } else {
                            _currentUser.value = UserState.Error("Failed to upload profile image")
                        }
                    } catch (e: Exception) {
                        _currentUser.value =
                            UserState.Error(e.message ?: "Failed to upload profile image")
                        Log.e("AuthViewModel", "Error uploading profile image", e)
                    }
                }
            }
        }
    }

    fun updateUserBio(it: String) {
        _currentUser.value.let { userState ->
            if (userState is UserState.Success) {
                viewModelScope.launch {
                    try {
                        val result = userRepository.updateUser(
                            userState.user.id_user,
                            mapOf("bio" to it)
                        )
                        if (result.isSuccess) {
                            _currentUser.value = UserState.Success(
                                userState.user.copy(bio = it)
                            )
                        }
                    } catch (e: Exception) {
                        _currentUser.value = UserState.Error(e.message ?: "Failed to update bio")
                        Log.e("AuthViewModel", "Error updating bio", e)
                    }
                }
            }
        }
    }

    fun updateUserUsername(it: String) {
        _currentUser.value.let { userState ->
            if (userState is UserState.Success) {
                val updatedUser = userState.user.copy(username = it)
                viewModelScope.launch {
                    try {
                        val result = userRepository.updateUser(
                            userState.user.id_user,
                            mapOf("username" to it)
                        )
                        if (result.isSuccess) {
                            _currentUser.value = UserState.Success(
                                userState.user.copy(username = it)
                            )
                        }
                    } catch (e: Exception) {
                        _currentUser.value =
                            UserState.Error(e.message ?: "Failed to update username")
                        Log.e("AuthViewModel", "Error updating username", e)
                    }
                }
            }
        }
    }

    fun createShelf(name: String) {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    val shelf = Shelf(
                        name = name,
                        id_user = userId
                    )
                    val result = shelfRepository.createShelf(shelf)
                    if (result.isSuccess) {
                        Log.d("AuthViewModel", "Shelf created")
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error creating shelf", e)
            }
        }
    }
}

class BookViewModel : ViewModel() {
    private val userRepository = UserRepository()
    private val shelfRepository = ShelfRepository()
    private val bookRepository = BookRepository()
    private val reviewRepository = ReviewRepository()
    private val commentRepository = CommentRepository()
    private val authorRepository = AuthorRepository()
    private val genreRepository = GenreRepository()

    private val aiChatRepository = AIChatRepository()

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _authors = MutableStateFlow<List<Author>>(emptyList())
    val authors: StateFlow<List<Author>> = _authors.asStateFlow()

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres.asStateFlow()

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _currentAuthor = MutableStateFlow<Author?>(null)
    val currentAuthor: StateFlow<Author?> = _currentAuthor.asStateFlow()

    private val _currentGenre = MutableStateFlow<Genre?>(null)
    val currentGenre: StateFlow<Genre?> = _currentGenre.asStateFlow()

    private val _currentBookReviews = MutableStateFlow<List<Review>>(emptyList())
    val currentBookReviews: StateFlow<List<Review>> = _currentBookReviews.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _allShelves = MutableStateFlow<List<Shelf>>(emptyList())
    val allShelves: StateFlow<List<Shelf>> = _allShelves.asStateFlow()

    private val _allShelfBooks = MutableStateFlow<List<ShelfBook>>(emptyList())
    val allShelfBooks = _allShelfBooks.asStateFlow()
    private val _aiMessages = MutableStateFlow<List<AiMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiMessage>> = _aiMessages.asStateFlow()

    private val _aiIsThinking = MutableStateFlow(false)
    val aiIsThinking: StateFlow<Boolean> = _aiIsThinking.asStateFlow()

    private val _guessedBook = MutableStateFlow<Book?>(null)
    val guessedBook: StateFlow<Book?> = _guessedBook.asStateFlow()

    private val _recommandedBooks = MutableStateFlow<List<Book>>(emptyList())
    val recommandedBooks: StateFlow<List<Book>> = _recommandedBooks.asStateFlow()

    private val conversationHistory = mutableListOf<Content>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val filteredBooks = _searchQuery.combine(_books){ query, allBooks ->
        if(query.isBlank()){
            emptyList()
        }else{
            allBooks.filter{
                it.title.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadBooks()
        loadAuthors()
        loadGenres()
        loadReviews()
        loadUsers()
        loadComments()
        loadAllShelfData()
    }

    fun loadBooks() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = bookRepository.getAllBooks()
                if (result.isSuccess) {
                    _books.value = result.getOrNull() ?: emptyList()
                    Log.d("BookViewModel", "Books loaded: ${_books.value}")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load books: ${e.message}"
                Log.e("BookViewModel", "Error loading books", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAuthors() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = authorRepository.getAllAuthors()
                if (result.isSuccess) {
                    _authors.value = result.getOrNull() ?: emptyList()
                    Log.d("BookViewModel", "Authors loaded: ${_authors.value}")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load authors: ${e.message}"
                Log.e("BookViewModel", "Error loading authors", e)
            }
        }
    }

    fun loadGenres() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = genreRepository.getAllGenres()
                if (result.isSuccess) {
                    _genres.value = result.getOrNull() ?: emptyList()
                    Log.d("BookViewModel", "Genres loaded: ${_genres.value}")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load genres: ${e.message}"
                Log.e("BookViewModel", "Error loading genres", e)
            }
        }
    }

    fun loadReviews() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = reviewRepository.getAllReviews()
                if (result.isSuccess) {
                    _reviews.value = result.getOrNull() ?: emptyList()
                    Log.d("BookViewModel", "Reviews loaded: ${_reviews.value}")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load reviews: ${e.message}"
                Log.e("BookViewModel", "Error loading reviews", e)
            }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = userRepository.getAllUsers()
                if (result.isSuccess) {
                    _users.value = result.getOrNull() ?: emptyList()
                    Log.d("BookViewModel", "Users loaded: ${_users.value}")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load users: ${e.message}"
                Log.e("BookViewModel", "Error loading users", e)
            }
        }
    }

    fun loadComments() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = commentRepository.getAllComments()
                if (result.isSuccess) {
                    _comments.value = result.getOrNull() ?: emptyList()
                    Log.d("BookViewModel", "Comments loaded: ${_comments.value}")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load comments: ${e.message}"
                Log.e("BookViewModel", "Error loading comments", e)
            }
        }
    }

    fun loadAllShelfData() {
        viewModelScope.launch {
            try {
                val shelvesResult = shelfRepository.getAllShelves()
                val shelfBooksResult = shelfRepository.getAllShelfBooks()
                if (shelvesResult.isSuccess) {
                    _allShelves.value = shelvesResult.getOrNull() ?: emptyList()
                }
                if (shelfBooksResult.isSuccess) {
                    _allShelfBooks.value = shelfBooksResult.getOrNull() ?: emptyList()
                }
            } catch (e: Exception) {
                _error.value = "Failed to load shelf data: ${e.message}"
                Log.e("BookViewModel", "Error while loading shelf data", e)
            }
        }
    }

    fun loadReviewsForBook(bookId: String) {
        viewModelScope.launch {
            try {
                _currentBookReviews.value = emptyList()
                val result = reviewRepository.getBookReviews(bookId)
                if (result.isSuccess) {
                    _currentBookReviews.value = result.getOrNull() ?: emptyList()
                    Log.d("BookViewModel", "Reviews loaded: ${_currentBookReviews.value}")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load reviews for book: ${e.message}"
                Log.e("BookViewModel", "Error loading reviews for book", e)
            }
        }
    }

    fun addBookToShelf(userId: String, shelfId: String, bookId: String) {
        viewModelScope.launch {
            try {
                val result = shelfRepository.addBookToShelf(shelfId, bookId)
                if (result.isSuccess) {
                    Log.d("BookViewModel", "Book added to shelf")
                }
            } catch (e: Exception) {
                _error.value = "Failed to add book to shelf: ${e.message}"
                Log.e("AuthViewModel", "Error adding book to shelf", e)
            }
        }
    }

    fun addReview(userId: String, bookId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            try {
                val review = Review(id_user = userId, id_book = bookId, rating = rating)
                val result = reviewRepository.addReview(review)
                if (result.isSuccess && comment.isNotEmpty()) {
                    val commentObj = Comment(
                        id_review = result.getOrNull()!!,
                        content = comment
                    )
                    commentRepository.addComment(commentObj)
                }
                loadReviewsForBook(bookId)
                loadComments()
                Log.d("BookViewModel", "Review added")
            } catch (e: Exception) {
                _error.value = "Failed to add review: ${e.message}"
                Log.e("BookViewModel", "Error adding review", e)
            }
        }
    }

    fun moveBookToShelf(
        userId: String,
        oldShelfId: String,
        newShelfId: String,
        bookId: String
    ) {
        viewModelScope.launch {
            try {
                shelfRepository.removeBookFromShelf(oldShelfId, bookId)
                shelfRepository.addBookToShelf(newShelfId, bookId)
                Log.d("BookViewModel", "Book moved from ${oldShelfId} to ${newShelfId}")
            } catch (e: Exception) {
                _error.value = "Failed to move book: ${e.message}"
                Log.e("BookViewModel", "Error moving book to shelf")
            }
        }
    }


    suspend fun getShelfForBook(bookId: String, userShelfIds: List<String>): String? {
        return try {
            return shelfRepository.getShelfForBook(bookId, userShelfIds).getOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun generateAIRecommendations(user: User, allBooks: List<Book>, allReviews: List<Review>) {
        if (allBooks.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userLickedBookIds = allReviews
                    .filter { it.id_user == user.id_user && it.rating >= 4 }
                    .map { it.id_book }

                val lickedTitles = allBooks
                    .filter { it.id_book in userLickedBookIds }
                    .joinToString { it.title }

                val catalog = allBooks.joinToString(";") { "${it.title} (ID: ${it.id_book})" }
                val prompt = """
                    You are a book recommendation assistant for the app 'Bookish'.
                    User Bio: "${user.bio}"
                    Books the user licked: $lickedTitles
                    Available Catalog: $catalog
                    Based on the user's bio and licked books, select the 3 best books from the Available Catalog.
                    Return only the IDs of the books, separated by commas. Do not write prose.
                """.trimIndent()

                val generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = com.example.bookish.BuildConfig.GEMINI_API_KEY,
                )

                val response = generativeModel.generateContent(prompt)
                val rawResponse = response.text ?: ""
                Log.d("AI_DEBUG:", "$rawResponse")

                val recommendedIds = response.text?.split(",")?.map { it.trim() } ?: emptyList()
                _recommandedBooks.value = allBooks.filter { it.id_book in recommendedIds }
            } catch (e: Exception) {
                _error.value = "AI Error: ${e.message}"
                Log.e("AI_DEBUG", "Error generating AI recommendations", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAiHistory(userId: String) {
        viewModelScope.launch {
            conversationHistory.clear()
            val result = aiChatRepository.loadHistory(userId)
            if (result.isSuccess) {
                val interactions = result.getOrNull() ?: emptyList()
                if (interactions.isEmpty()) {
                    startNewAiGame(userId)
                } else {
                    val messages = mutableListOf<AiMessage>()
                    messages.add(
                        AiMessage(
                            id = "Welcome",
                            content = "Bună! 📚 Gândește-te la o carte și descrie-mi-o — " +
                                    "personaje, poveste, atmosferă, orice vrei. " +
                                    "Eu voi încerca să ghicesc despre ce carte e vorba!",
                            isFromAi = true,
                            timestamp = 0L
                        )
                    )

                    interactions.forEach { interaction ->
                        messages.add(
                            AiMessage(
                                id = interaction.id_chat_interactions + "_user",
                                content = interaction.messageUser,
                                isFromAi = false,
                                timestamp = interaction.generationDate.toDate().time
                            )
                        )
                        messages.add(
                            AiMessage(
                                id = interaction.id_chat_interactions + "_ai",
                                content = interaction.messageChatbot,
                                isFromAi = true,
                                timestamp = interaction.generationDate.toDate().time
                            )
                        )

                        conversationHistory.add(
                            content(role = "user") { text(interaction.messageUser) }
                        )
                        conversationHistory.add(
                            content(role = "model") { text(interaction.messageChatbot) }
                        )
                    }
                    _aiMessages.value = messages
                }
            } else {
                startNewAiGame(userId)
            }
        }
    }

    fun startNewAiGame(userId: String) {
        viewModelScope.launch {
            aiChatRepository.clearHistory(userId)
            conversationHistory.clear()
            _guessedBook.value = null
            _aiMessages.value = listOf(
                AiMessage(
                    id = "welcome",
                    content = "Bună! 📚 Gândește-te la o carte și descrie-mi-o — " +
                            "personaje, poveste, atmosferă, orice vrei. " +
                            "Eu voi încerca să ghicesc despre ce carte e vorba!",
                    isFromAi = true,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun sendAiMessage(userMessage: String, userId: String) {
        if (userMessage.isBlank()) return

        val userMsg = AiMessage(
            id = System.currentTimeMillis().toString(),
            content = userMessage,
            isFromAi = false,
            timestamp = System.currentTimeMillis()
        )

        _aiMessages.value = _aiMessages.value + userMsg

        viewModelScope.launch {
            _aiIsThinking.value = true
            val streamingId = (System.currentTimeMillis() + 1).toString()
            _aiMessages.value = _aiMessages.value + AiMessage(
                id = streamingId,
                content = "",
                isFromAi = true,
                timestamp = System.currentTimeMillis() + 1
            )

            try {
                val catalog = _books.value.joinToString("\n") {
                    "- Titlu: ${it.title}, ID ${it.id_book}"
                }

                val systemPrompt = """
                    Ești un detectiv de cărți strict limitat la catalogul pus la dispoziție.
                    
                    CATALOG DISPONIBIL:
                    $catalog
                    
                    REGULI CRITICE:
                    1. NU AI VOIE să ghicești nicio carte care NU se află în lista de mai sus.
                    2. Dacă utilizatorul descrie o carte care nu este în catalogul meu, răspunde: "Din păcate, această carte nu se află în biblioteca mea momentan. Încearcă să descrii o altă carte!"
                    3. Analizează indiciile (gen, atmosferă) și compară-le DOAR cu elementele din catalog.
                    4. Când ești sigur, răspunde exact: "Am ghicit! Cred că este: [Titlu] 🎉\nBOOK_ID:[ID]"
                    5. Dacă sunt mai multe variante posibile din catalog, pune întrebări suplimentare pentru a elimina opțiunile greșite.
                """.trimIndent()

                val generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = com.example.bookish.BuildConfig.GEMINI_API_KEY,
                    systemInstruction = content { text(systemPrompt) }
                )

                val chat = generativeModel.startChat(history = conversationHistory)

                var fullResponse = ""
                chat.sendMessageStream(userMessage).collect { chunk ->
                    chunk.text?.let { chunkText ->
                        fullResponse += chunkText
                        val displayText = fullResponse
                            .replace(Regex("BOOK_ID:.*"), "")
                            .trim()
                        _aiMessages.value = _aiMessages.value.map { msg ->
                            if (msg.id == streamingId) {
                                msg.copy(content = displayText, isStreaming = true)
                            } else msg
                        }
                    }
                }

                val finalDisplayText = fullResponse
                    .replace(Regex("BOOK+ID:.*"), "")
                    .trim()
                _aiMessages.value = _aiMessages.value.map { msg ->
                    if (msg.id == streamingId) {
                        msg.copy(content = finalDisplayText, isStreaming = true)
                    } else msg
                }

                conversationHistory.add(content(role = "user") { text(userMessage) })
                conversationHistory.add(content(role = "model") { text(fullResponse) })

                aiChatRepository.saveInteractions(
                    ChatInteractions(
                        id_user = userId,
                        messageUser = userMessage,
                        messageChatbot = finalDisplayText,
                        generationDate = Timestamp.now()
                    )
                )

                if (fullResponse.contains("BOOK_ID:")) {
                    val bookId = fullResponse
                        .substringAfter("BOOK_ID:")
                        .trim()
                        .split("\n")[0]
                        .trim()
                    val foundBook = _books.value.find { it.id_book == bookId }
                    _guessedBook.value = foundBook
                }
                Log.d("AI_DEBUG", "Full response: $fullResponse")
            } catch (e: Exception) {
                _aiMessages.value = _aiMessages.value.map { msg ->
                    if (msg.id == streamingId) {
                        msg.copy(
                            content = "Am întâmpinat o eroare. Încearcă din nou!",
                            isStreaming = false
                        )
                    } else msg
                }
                Log.e("AI_CHAT", "Streaming error: ${e.message}", e)
            } finally {
                _aiIsThinking.value = false
            }
        }
    }

    fun resetAiGame(userId: String) {
        startNewAiGame(userId)
    }

    fun onSearchQueryChanged(newQuery: String){
        _searchQuery.value = newQuery
    }
}

class SocialViewModel : ViewModel() {
    private val friendshipRepository = FriendshipRepository()
    private val messageRepository = MessageRepository()
    private val userRepository = UserRepository()
    private val bookClubRepository = BookClubRepository()

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends = _friends.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<User>>(emptyList())
    val pendingRequests = _pendingRequests.asStateFlow()

    private val _myBookClubs = MutableStateFlow<List<BookClub>>(emptyList())
    val myBookClubs = _myBookClubs.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _privateMessages = MutableStateFlow<List<PrivateMessage>>(emptyList())
    val privateMessages = _privateMessages.asStateFlow()
    private var privateMessagesListener: ListenerRegistration? = null

    private val _groupMessages = MutableStateFlow<List<GroupMessage>>(emptyList())
    val groupMessages = _groupMessages.asStateFlow()
    private var groupMessagesListener: ListenerRegistration? = null

    private val _sentRequests = MutableStateFlow<Set<String>>(emptySet())
    val sentRequests = _sentRequests.asStateFlow()


    fun loadSocialData(currentUserId: String) {
        viewModelScope.launch {
            val friendIdsResult = friendshipRepository.getAcceptedFriendIds(currentUserId)
            val friendIds = friendIdsResult.getOrNull() ?: emptySet()
            val friendsList = friendIds.mapNotNull { friendId ->
                userRepository.getUserById(friendId).getOrNull()
            }
            _friends.value = friendsList

            val requesterIdsResult = friendshipRepository.getPendingRequestsForUser(currentUserId)
            val requesterIds = requesterIdsResult.getOrNull() ?: emptyList()
            val requestersList = requesterIds.mapNotNull { requesterId ->
                userRepository.getUserById(requesterId).getOrNull()
            }
            _pendingRequests.value = requestersList

            val clubsResult = bookClubRepository.getClubsForUser(currentUserId)
            _myBookClubs.value = clubsResult.getOrNull() ?: emptyList()

            loadSentRequests(currentUserId)

            Log.d(
                "SocialViewModel",
                "Social data loaded: ${friendsList.size} friends, ${requestersList.size} requests"
            )
        }
    }

    private fun getConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_$userId2" else "${userId2}_$userId1"
    }

    fun sendPrivateMessage(
        senderId: String,
        receiverId: String,
        text: String,
        bookId: String = ""
    ) {

        val conversationId = getConversationId(senderId, receiverId)

        val message = PrivateMessage(
            id_sender = senderId,
            id_receiver = receiverId,
            content = text,
            id_book = bookId,
            sendingDate = Timestamp.now(),
            conversationId = conversationId
        )

        _privateMessages.value = _privateMessages.value + message

        viewModelScope.launch {
            try {
                val result = messageRepository.sendPrivateMessage(message)
                if (!result.isSuccess) {
                    _privateMessages.value = _privateMessages.value.dropLast(1)
                    Log.e("SocialViewModel", "Error sending private message")
                }
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error sending private message", e)
            }
        }
    }

    fun sendGroupMessage(
        clubId: String,
        userId: String,
        text: String,
        bookId: String = ""
    ) {
        val message = GroupMessage(
            id_bookClub = clubId,
            id_user = userId,
            content = text,
            id_book = bookId,
            sendingDate = Timestamp.now()
        )
        _groupMessages.value = _groupMessages.value + message

        viewModelScope.launch {
            try {
                val result = messageRepository.sendGroupMessage(message)
                if (!result.isSuccess) {
                    _groupMessages.value = _groupMessages.value.dropLast(1)
                    Log.e("SocialViewModel", "Error sending group message")
                }
            } catch (e: Exception) {
                _groupMessages.value = _groupMessages.value.dropLast(1)
                Log.e("SocialViewModel", "Error sending group message", e)
            }
        }
    }

    fun acceptFriendRequest(currentUserId: String, requesterId: String) {
        viewModelScope.launch {
            try {
                val result = friendshipRepository.acceptFriendRequestByUsers(
                    requesterId, currentUserId
                )
                if (result.isSuccess) {
                    loadSocialData(currentUserId)
                    Log.d("SocialViewModel", "Friend request accepted")
                }
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error accepting friend request", e)
            }
        }
    }

    fun declineFriendRequest(currentUserId: String, requesterId: String) {
        viewModelScope.launch {
            try {
                val result = friendshipRepository.declineFriendRequest(requesterId, currentUserId)
                if (result.isSuccess) {
                    loadSocialData(currentUserId)
                    Log.d("SocialViewModel", "Friend request declined")
                }
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error declining friend request", e)
            }
        }
    }

    fun searchUsers(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val result = userRepository.searchUsers(query)
                if (result.isSuccess) {
                    _searchResults.value = result.getOrNull() ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error searching users", e)
            }
        }
    }

    fun sendFriendRequest(senderId: String, receiverId: String) {
        val newFriendship = Friendship(
            id_user1 = senderId,
            id_user2 = receiverId,
            status = "pending"
        )

        viewModelScope.launch {
            try {
                val result = friendshipRepository.sendFriendRequest(senderId, receiverId)
                if (result.isSuccess) {
                    _sentRequests.value = _sentRequests.value + receiverId
                    Log.d("SocialViewModel", "Friend request sent")
                }
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error sending friend request", e)
            }
        }
    }

    fun listenForMessages(currentUserId: String, chatPartnerId: String) {
        val convId = if (currentUserId < chatPartnerId)
            "${currentUserId}_$chatPartnerId"
        else
            "${chatPartnerId}_$currentUserId"

        privateMessagesListener?.remove()
        privateMessagesListener = null
        _privateMessages.value = emptyList()

        privateMessagesListener = messageRepository.listenForPrivateMessages(
            conversationId = convId,
            onUpdate = { list -> _privateMessages.value = list },
            onError = { e -> Log.e("CHAT_ERROR", "Listen failed: ${e.message}") }
        )
    }

    override fun onCleared() {
        super.onCleared()
        privateMessagesListener?.remove()
        groupMessagesListener?.remove()
    }

    fun listenForGroupMessages(clubId: String) {
        groupMessagesListener?.remove()
        groupMessagesListener = null
        _groupMessages.value = emptyList()

        groupMessagesListener = messageRepository.listenForGroupMessages(
            clubId = clubId,
            onUpdate = { list -> _groupMessages.value = list },
            onError = { e -> Log.e("GROUP_CHAT_ERROR", "Listen failed: ${e.message}") }
        )
    }

    fun loadSentRequests(currentUserId: String) {
        viewModelScope.launch {
            try {
                val result = friendshipRepository.getSentRequestsForUser(currentUserId)
                _sentRequests.value = result.getOrNull() ?: emptySet()
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error loading sent requests", e)
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }
}
