package com.example.bookish.viewmodel

import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookish.models.*
import com.example.bookish.repository.*
import com.example.bookish.utils.FCMTokenManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    private val repository = AuthRepository()
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
            val result = repository.register(email, password, username)
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
            val result = repository.login(email, password)
            _authState.value = if (result.isSuccess) {
                loadCurrentUser()
                AuthState.Success("Login successful")
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        repository.logout()
        _authState.value = AuthState.Idle
        _currentUser.value = UserState.Loading
        _userShelves.value = emptyList()
    }

    fun getGoogleSignInClient(context: Context) = repository.getGoogleSignInClient(context)

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            Log.d(TAG, "Google Sign In called from UI")
            _authState.value = AuthState.Loading
            val result = repository.signInWithGoogle(idToken)
            _authState.value = if (result.isSuccess) {
                loadCurrentUser()
                AuthState.Success(result.getOrNull() ?: "")
            } else {
                Log.e(TAG, "Google Sign In FAILED: ${result.exceptionOrNull()?.message}")
                AuthState.Error(result.exceptionOrNull()?.message ?: "Google Sign In failed")
            }
        }
    }

    fun getCurrentUserId(): String? = repository.getCurrentUserId()

    fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                _currentUser.value = UserState.Loading
                val userId = repository.getCurrentUserId()

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
                val snapshot = db.collection("shelves")
                    .whereEqualTo("id_user", userId)
                    .get()
                    .await()
                val shelves = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Shelf::class.java)?.apply {
                        id_shelf = doc.id
                    }
                }
                _userShelves.value = shelves
                Log.d("AuthViewModel", "User shelves loaded: $shelves")
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
                        val storageRef = FirebaseStorage.getInstance().reference
                            .child("profileImages/$userId.jpg")
                        storageRef.putFile(it).await()
                        val downloadUrl = storageRef.downloadUrl.await().toString()

                        FirebaseFirestore.getInstance().collection("users")
                            .document(userId)
                            .update("profileImageUrl", downloadUrl)
                            .await()

                        val currentState = _currentUser.value
                        if (currentState is UserState.Success) {
                            _currentUser.value =
                                UserState.Success(currentState.user.copy(profileImageUrl = downloadUrl))
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
                val updatedUser = userState.user.copy(bio = it)
                viewModelScope.launch {
                    try {
                        FirebaseFirestore.getInstance().collection("users")
                            .document(updatedUser.id_user)
                            .update("bio", it)
                            .await()
                        _currentUser.value = UserState.Success(updatedUser)
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
                        FirebaseFirestore.getInstance().collection("users")
                            .document(updatedUser.id_user)
                            .update("username", it)
                            .await()
                        _currentUser.value = UserState.Success(updatedUser)
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
                val userId = repository.getCurrentUserId()
                if (userId != null) {
                    val shelf = Shelf(
                        name = name,
                        id_user = userId
                    )
                    db.collection("shelves").add(shelf).await()
                    loadUserShelves(userId)
                    Log.d("AuthViewModel", "Shelf created")
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error creating shelf", e)
            }
        }
    }
}

class BookViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

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

    private val _recommandedBooks = MutableStateFlow<List<Book>>(emptyList())
    val recommandedBooks: StateFlow<List<Book>> = _recommandedBooks.asStateFlow()

    private val _allShelves = MutableStateFlow<List<Shelf>>(emptyList())
    val allShelves: StateFlow<List<Shelf>> = _allShelves.asStateFlow()

    private val _allShelfBooks = MutableStateFlow<List<ShelfBook>>(emptyList())
    val allShelfBooks = _allShelfBooks.asStateFlow()

    fun loadBooks() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val snapshot = db.collection("books").get().await()
                val booksList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Book::class.java)?.apply {
                        id_book = doc.id
                    }
                }
                _books.value = booksList
                Log.d("BookViewModel", "Books loaded: $booksList")
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
                val snapshot = db.collection("authors").get().await()
                val authorsList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Author::class.java)?.apply {
                        id_author = doc.id
                    }
                }
                _authors.value = authorsList
                Log.d("BookViewModel", "Authors loaded: $authorsList")
            } catch (e: Exception) {
                _error.value = "Failed to load authors: ${e.message}"
                Log.e("BookViewModel", "Error loading authors", e)
            }
        }
    }

    fun loadGenres() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("genres").get().await()
                val genresList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Genre::class.java)?.apply {
                        id_genre = doc.id
                    }
                }
                _genres.value = genresList
                Log.d("BookViewModel", "Genres loaded: $genresList")
            } catch (e: Exception) {
                _error.value = "Failed to load genres: ${e.message}"
                Log.e("BookViewModel", "Error loading genres", e)
            }
        }
    }

    fun loadReviews() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("reviews").get().await()
                val reviewsList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Review::class.java)?.apply {
                        id_review = doc.id
                    }
                }
                _reviews.value = reviewsList
                Log.d("BookViewModel", "Reviews loaded: $reviewsList")
            } catch (e: Exception) {
                _error.value = "Failed to load reviews: ${e.message}"
                Log.e("BookViewModel", "Error loading reviews", e)
            }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").get().await()
                val usersList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.apply {
                        id_user = doc.id
                    }
                }
                _users.value = usersList
                Log.d("BookViewModel", "Users loaded: $usersList")
            } catch (e: Exception) {
                _error.value = "Failed to load users: ${e.message}"
                Log.e("BookViewModel", "Error loading users", e)
            }
        }
    }

    fun loadComments() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("comments").get().await()
                val commentsList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Comment::class.java)?.apply {
                        id_comment = doc.id
                    }
                }
                _comments.value = commentsList
                Log.d("BookViewModel", "Comments loaded: $commentsList")
            } catch (e: Exception) {
                _error.value = "Failed to load comments: ${e.message}"
                Log.e("BookViewModel", "Error loading comments", e)
            }
        }
    }

    fun loadAuthorById(authorId: String) {
        viewModelScope.launch {
            try {
                _currentAuthor.value = null
                val doc = db.collection("authors").document(authorId).get().await()
                _currentAuthor.value = doc.toObject(Author::class.java)?.apply {
                    id_author = doc.id
                }
            } catch (e: Exception) {
                _error.value = "Failed to load author: ${e.message}"
                Log.e("BookViewModel", "Error loading author", e)
            }
        }
    }

    fun loadGenreById(genreId: String) {
        viewModelScope.launch {
            try {
                _currentGenre.value = null
                val doc = db.collection("genres").document(genreId).get().await()
                _currentGenre.value = doc.toObject(Genre::class.java)?.apply {
                    id_genre = doc.id
                }
            } catch (e: Exception) {
                _error.value = "Failed to load genre: ${e.message}"
                Log.e("BookViewModel", "Error loading genre", e)
            }
        }
    }

    fun loadAllShelfData() {
        viewModelScope.launch {
            try {
                val shelvesSnapshot = db.collection("shelves").get().await()
                _allShelves.value = shelvesSnapshot.toObjects(Shelf::class.java)

                val shelfBooksSnapshot = db.collection("shelf_books").get().await()
                _allShelfBooks.value = shelfBooksSnapshot.toObjects(ShelfBook::class.java)
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
                val snapshot = db.collection("reviews")
                    .whereEqualTo("id_book", bookId)
                    .get()
                    .await()
                val reviewList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Review::class.java)?.apply {
                        id_review = doc.id
                    }
                }
                _currentBookReviews.value = reviewList
                Log.d("BookViewModel", "Reviews for book loaded: $reviewList")
            } catch (e: Exception) {
                _error.value = "Failed to load reviews for book: ${e.message}"
                Log.e("BookViewModel", "Error loading reviews for book", e)
            }
        }
    }

    fun addBookToShelf(userId: String, shelfId: String, bookId: String) {
        viewModelScope.launch {
            try {
                val shelfBook = ShelfBook(
                    id_shelf = shelfId,
                    id_book = bookId
                )
                db.collection("shelf_books").add(shelfBook).await()
                Log.d("AuthViewModel", "Book added to shelf")
            } catch (e: Exception) {
                _error.value = "Failed to add book to shelf: ${e.message}"
                Log.e("AuthViewModel", "Error adding book to shelf", e)
            }
        }
    }

    fun addReview(userId: String, bookId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            try {
                val review = Review(
                    id_user = userId,
                    id_book = bookId,
                    rating = rating,
                )
                val reviewRef = db.collection("reviews").add(review).await()
                if (comment.isNotEmpty()) {
                    val commentObj = Comment(
                        id_review = reviewRef.id,
                        content = comment
                    )
                    db.collection("comments").add(commentObj).await()

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
                val oldRelation = db.collection("shelf_books")
                    .whereEqualTo("id_shelf", oldShelfId)
                    .whereEqualTo("id_book", bookId)
                    .get()
                    .await()
                for (doc in oldRelation.documents) {
                    db.collection("shelf_books").document(doc.id).delete().await()
                }

                val newShlefBook = ShelfBook(
                    id_shelf = newShelfId,
                    id_book = bookId
                )
                db.collection("shelf_books").add(newShlefBook).await()
                Log.d("BookViewModel", "Book moved from ${oldShelfId} to ${newShelfId}")
            } catch (e: Exception) {
                _error.value = "Failed to move book: ${e.message}"
                Log.e("BookViewModel", "Error moving book to shelf")
            }
        }
    }


    suspend fun getShelfForBook(bookId: String, userShelfIds: List<String>): String? {
        return try {
            val snapshot = db.collection("shelf_books")
                .whereEqualTo("id_book", bookId)
                .whereIn("id_shelf", userShelfIds)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.getString("id_shelf")
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
}

class SocialViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

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
            val friendships1 = db.collection("friendships")
                .whereEqualTo("id_user1", currentUserId)
                .whereEqualTo("status", "accepted")
                .get()
                .await()

            val friendships2 = db.collection("friendships")
                .whereEqualTo("id_user2", currentUserId)
                .whereEqualTo("status", "accepted")
                .get()
                .await()

            val friendIds = mutableSetOf<String>()
            friendships1.documents.forEach { doc ->
                doc.toObject(Friendship::class.java)?.id_user2?.let {
                    friendIds.add(it)
                }
            }
            friendships2.documents.forEach { doc ->
                doc.toObject(Friendship::class.java)?.id_user1?.let {
                    friendIds.add(it)
                }
            }

            val friendsList = friendIds.mapNotNull { friendId ->
                db.collection("users")
                    .document(friendId)
                    .get()
                    .await()
                    .toObject(User::class.java)
            }
            _friends.value = friendsList

            val requests = db.collection("friendships")
                .whereEqualTo("id_user2", currentUserId)
                .whereEqualTo("status", "pending")
                .get()
                .await()

            val requesterIds = requests.documents.mapNotNull { doc ->
                doc.toObject(Friendship::class.java)?.id_user1
            }

            val requestersList = requesterIds.mapNotNull { requesterId ->
                db.collection("users")
                    .document(requesterId)
                    .get()
                    .await()
                    .toObject(User::class.java)
            }
            _pendingRequests.value = requestersList

            val memberships = db.collection("book_club_members")
                .whereEqualTo("id_user", currentUserId)
                .whereEqualTo("status", "active")
                .get()
                .await()

            val clubIds = memberships.documents.mapNotNull {
                it.getString("id_book_club")
            }

            if (clubIds.isNotEmpty()) {
                val clubsList = clubIds.mapNotNull { clubId ->
                    db.collection("book_clubs")
                        .document(clubId)
                        .get()
                        .await()
                        .toObject(BookClub::class.java)
                }
                _myBookClubs.value = clubsList
            }

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
                db.collection("private_messages").add(message).await()
                Log.d("SocialViewModel", "Private message send successfully")
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
            id_book = bookId
        )

        _groupMessages.value = _groupMessages.value + message

        viewModelScope.launch {
            try {
                db.collection("group_messages").add(message).await()
                Log.d("SocialViewModel", "Group message send successfully")
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error sending group message", e)
            }

        }
    }

    fun acceptFriendRequest(currentUserId: String, requesterId: String) {
        viewModelScope.launch {
            try {
                val query = db.collection("friendships")
                    .whereEqualTo("id_user1", requesterId)
                    .whereEqualTo("id_user2", currentUserId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                for (documn in query.documents) {
                    db.collection("friendships")
                        .document(documn.id)
                        .update("status", "accepted")
                        .await()
                }

                loadSocialData(currentUserId)
                Log.d("SocialViewModel", "Friend request accepted")
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error accepting friend request", e)
            }
        }
    }

    fun declineFriendRequest(currentUserId: String, requesterId: String) {
        viewModelScope.launch {
            try {
                val query = db.collection("friendships")
                    .whereEqualTo("id_user1", requesterId)
                    .whereEqualTo("id_user2", currentUserId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                for (document in query.documents) {
                    db.collection("friendships")
                        .document(document.id)
                        .delete()
                        .await()
                }
                loadSocialData(currentUserId)
                Log.d("SocialViewModel", "Friend request declined")
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
                val snapshot = db.collection("users")
                    .whereGreaterThanOrEqualTo("username", query)
                    .whereLessThanOrEqualTo("username", query + "\uf8ff")
                    .get()
                    .await()

                _searchResults.value = snapshot.toObjects(User::class.java)
                Log.d("SocialViewModel", "Search results: ${_searchResults.value}")
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error searching users")
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
                db.collection("friendships").add(newFriendship).await()
                _sentRequests.value = _sentRequests.value + receiverId
                Log.d("SocialViewModel", "Friend request sent")
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error sending friend request", e)
            }
        }
    }

    fun listenForMessages(currentUserId: String, chatPartnerId: String) {
        val convId = getConversationId(currentUserId, chatPartnerId)

        privateMessagesListener?.remove()
        privateMessagesListener = null
        _privateMessages.value = emptyList()


        privateMessagesListener = db.collection("private_messages")
            .whereEqualTo("conversationId", convId) // Filtrare ultra-rapidă
            .orderBy("sendingDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("CHAT_ERROR", "Listen failed: ${e.message}")
                    return@addSnapshotListener
                }

                val list = snapshot?.toObjects(PrivateMessage::class.java) ?: emptyList()
                _privateMessages.value = list
                Log.d("CHAT_DEBUG", "Am găsit ${list.size} mesaje pentru $convId")
            }
    }

    override fun onCleared() {
        super.onCleared()
        privateMessagesListener?.remove()
        groupMessagesListener?.remove()
    }

    fun listenForGroupMessages(clubId: String) {
        groupMessagesListener?.remove()

        groupMessagesListener = db.collection("group_messages")
            .whereEqualTo("id_bookClub", clubId)
            .orderBy("sendingDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("CHAT_ERROR", "Listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                _groupMessages.value = snapshot?.toObjects(GroupMessage::class.java) ?: emptyList()
            }
    }

    fun loadSentRequests(currentUserId: String) {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("friendships")
                    .whereEqualTo("id_user1", currentUserId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                val ids = snapshot.documents.mapNotNull {
                    it.toObject(Friendship::class.java)?.id_user2
                }.toSet()
                _sentRequests.value = ids
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Error loading sent requests", e)
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }
}
