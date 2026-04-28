package com.example.bookish.repository


import com.example.bookish.R
import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.bookish.models.Author
import com.example.bookish.models.Book
import com.example.bookish.models.BookClub
import com.example.bookish.models.ChatInteractions
import com.example.bookish.models.Comment
import com.example.bookish.models.Friendship
import com.example.bookish.models.Genre
import com.example.bookish.models.GroupMessage
import com.example.bookish.models.PrivateMessage
import com.example.bookish.models.Review
import com.example.bookish.models.Shelf
import com.example.bookish.models.ShelfBook
import com.example.bookish.models.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun register(email: String, password: String, username: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("User ID is null")
            val user = User(
                id_user = userId,
                email = email,
                username = username
            )
            db.collection("users").document(userId).set(user).await()
            val defaultShelves = listOf("Reading", "Completed", "Want to Read")
            defaultShelves.forEach { shelfName ->
                val shelf = Shelf(
                    name = shelfName,
                    id_user = userId
                )
                db.collection("shelves").add(shelf).await()
            }
            Result.success("User registered successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success("User logged in successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    suspend fun signInWithGoogle(idToken: String): Result<String> {
        return try{
            Log.d(TAG, "Starting Google Sign In token")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val userId = result.user?.uid ?: throw Exception("User ID is null")

            Log.d(TAG, "Google Sign In successful. User ID: $userId")
            val userDoc = db.collection("users").document(userId).get().await()
            if (!userDoc.exists()){
                val user = User(
                    id_user = userId,
                    email = result.user?.email ?: "",
                    username = result.user?.displayName ?: "User",
                    profileImageUrl = result.user?.photoUrl.toString() ?: ""
                )
                db.collection("users").document(userId).set(user).await()
                val defaultShelves = listOf("Reading", "Completed", "Want to Read")
                defaultShelves.forEach { shelfName ->
                    val shelf = Shelf(
                        name = shelfName,
                        id_user = userId
                    )
                    db.collection("shelves").add(shelf).await()
                }
                Log.d(TAG, "New Google user saved to Firestore")
            }else{
                Log.d(TAG, "Google user already logged in Firestore")
            }
            Result.success("Google Sign In successful")
        }catch(e: Exception){
            Log.e(TAG, "Google Sign In failed: ${e.message}")
            Result.failure(e)
        }
    }
}

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun getUserById(userId: String): Result<User> {
        return try {
            val doc = db.collection("users").document(userId).get().await()
            val user = doc.toObject(User::class.java)
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUser(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            db.collection("users").document(userId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfileImage(userId: String, imageUri: Uri): Result<String> {
        return try {
            val ref = storage.reference.child("profile_images/$userId.jpg")
            ref.putFile(imageUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            db.collection("users").document(userId)
                .update("profileImageUrl", downloadUrl).await()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            val users = db.collection("users")
                .whereGreaterThanOrEqualTo("username", query)
                .whereLessThanOrEqualTo("username", query + "\uf8ff")
                .get().await()
                .toObjects(User::class.java)
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val snapshot = db.collection("users").get().await()
            val users = snapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)?.apply { id_user = doc.id }
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class BookRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getAllBooks(): Result<List<Book>> {
        return try {
            val books = db.collection("books")
                .get().await()
                .toObjects(Book::class.java)
            Result.success(books)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBooksByIds(bookIds: List<String>): Result<List<Book>> {
        return try {
            val books = bookIds.mapNotNull { bookId ->
                db.collection("books").document(bookId).get().await()
                    .toObject(Book::class.java)?.apply { id_book = bookId }
            }
            Result.success(books)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchBooks(query: String): Result<List<Book>> {
        return try {
            val books = db.collection("books")
                .whereGreaterThanOrEqualTo("title", query)
                .whereLessThanOrEqualTo("title", query + "\uf8ff")
                .get().await()
                .toObjects(Book::class.java)
            Result.success(books)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBooksByAuthor(authorId: String): Result<List<Book>> {
        return try {
            val books = db.collection("books")
                .whereEqualTo("id_author", authorId)
                .get().await()
                .toObjects(Book::class.java)
            Result.success(books)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class AuthorRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getAllAuthors(): Result<List<Author>> {
        return try {
            val snapshot = db.collection("authors").get().await()
            val authors = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Author::class.java)?.apply { id_author = doc.id }
            }
            Result.success(authors)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAuthorById(authorId: String): Result<Author> {
        return try {
            val doc = db.collection("authors").document(authorId).get().await()
            val author = doc.toObject(Author::class.java)?.apply { id_author = doc.id }
                ?: throw Exception("Author not found")
            Result.success(author)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class GenreRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getAllGenres(): Result<List<Genre>> {
        return try {
            val snapshot = db.collection("genres").get().await()
            val genres = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Genre::class.java)?.apply { id_genre = doc.id }
            }
            Result.success(genres)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGenreById(genreId: String): Result<Genre> {
        return try {
            val doc = db.collection("genres").document(genreId).get().await()
            val genre = doc.toObject(Genre::class.java)?.apply { id_genre = doc.id }
                ?: throw Exception("Genre not found")
            Result.success(genre)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class ShelfRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getUserShelves(userId: String): Result<List<Shelf>> {
        return try {
            val shelves = db.collection("shelves")
                .whereEqualTo("id_user", userId)
                .get().await()
                .toObjects(Shelf::class.java)
            Result.success(shelves)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createShelf(shelf: Shelf): Result<String> {
        return try {
            val docRef = db.collection("shelves").add(shelf).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addBookToShelf(shelfId: String, bookId: String): Result<Unit> {
        return try {
            val shelfBook = ShelfBook(
                id_shelf = shelfId,
                id_book = bookId
            )
            db.collection("shelf_books").add(shelfBook).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeBookFromShelf(shelfId: String, bookId: String): Result<Unit> {
        return try {
            val query = db.collection("shelf_books")
                .whereEqualTo("id_shelf", shelfId)
                .whereEqualTo("id_book", bookId)
                .get().await()
            query.documents.forEach { it.reference.delete().await() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllShelves(): Result<List<Shelf>> {
        return try {
            val snapshot = db.collection("shelves").get().await()
            val shelves = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Shelf::class.java)?.apply { id_shelf = doc.id }
            }
            Result.success(shelves)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllShelfBooks(): Result<List<ShelfBook>> {
        return try {
            val snapshot = db.collection("shelf_books").get().await()
            Result.success(snapshot.toObjects(ShelfBook::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getShelfForBook(bookId: String, userShelfIds: List<String>): Result<String?> {
        return try {
            val snapshot = db.collection("shelf_books")
                .whereEqualTo("id_book", bookId)
                .whereIn("id_shelf", userShelfIds)
                .get()
                .await()
            Result.success(snapshot.documents.firstOrNull()?.getString("id_shelf"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class ReviewRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getAllReviews(): Result<List<Review>> {
        return try {
            val snapshot = db.collection("reviews").get().await()
            val reviews = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Review::class.java)?.apply { id_review = doc.id }
            }
            Result.success(reviews)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBookReviews(bookId: String): Result<List<Review>> {
        return try {
            val reviews = db.collection("reviews")
                .whereEqualTo("id_book", bookId)
                .get().await()
                .toObjects(Review::class.java)
            Result.success(reviews)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addReview(review: Review): Result<String> {
        return try {
            val docRef = db.collection("reviews").add(review).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateReview(reviewId: String, rating: Int, reviewText: String): Result<Unit> {
        return try {
            db.collection("reviews").document(reviewId)
                .update(mapOf("rating" to rating))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteReview(reviewId: String): Result<Unit> {
        return try {
            db.collection("reviews").document(reviewId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class CommentRepository{
    private val db = FirebaseFirestore.getInstance()

    suspend fun getAllComments(): Result<List<Comment>> {
        return try {
            val snapshot = db.collection("comments").get().await()
            val comments = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.apply { id_comment = doc.id }
            }
            Result.success(comments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addComment(comment: Comment): Result<String> {
        return try {
            val docRef = db.collection("comments").add(comment).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommentsForReview(reviewId: String): Result<List<Comment>> {
        return try {
            val snapshot = db.collection("comments")
                .whereEqualTo("id_review", reviewId)
                .get()
                .await()
            val comments = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.apply { id_comment = doc.id }
            }
            Result.success(comments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class FriendshipRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> {
        return try {
            val friendship = Friendship(
                id_user1 = fromUserId,
                id_user2 = toUserId,
                status = "pending"
            )
            db.collection("friendships").add(friendship).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequestByUsers(
        requesterId: String,
        currentUserId: String
    ): Result<Unit> {
        return try {
            val query = db.collection("friendships")
                .whereEqualTo("id_user1", requesterId)
                .whereEqualTo("id_user2", currentUserId)
                .whereEqualTo("status", "pending")
                .get()
                .await()
            for (doc in query.documents) {
                db.collection("friendships")
                    .document(doc.id)
                    .update("status", "accepted")
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun declineFriendRequest(requesterId: String, currentUserId: String): Result<Unit> {
        return try {
            val query = db.collection("friendships")
                .whereEqualTo("id_user1", requesterId)
                .whereEqualTo("id_user2", currentUserId)
                .whereEqualTo("status", "pending")
                .get()
                .await()
            for (document in query.documents) {
                db.collection("friendships").document(document.id).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendingRequestsForUser(userId: String): Result<List<String>> {
        return try {
            val snapshot = db.collection("friendships")
                .whereEqualTo("id_user2", userId)
                .whereEqualTo("status", "pending")
                .get()
                .await()
            val requesterIds = snapshot.documents.mapNotNull {
                it.toObject(Friendship::class.java)?.id_user1
            }
            Result.success(requesterIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSentRequestsForUser(userId: String): Result<Set<String>> {
        return try {
            val snapshot = db.collection("friendships")
                .whereEqualTo("id_user1", userId)
                .whereEqualTo("status", "pending")
                .get()
                .await()
            val ids = snapshot.documents.mapNotNull {
                it.toObject(Friendship::class.java)?.id_user2
            }.toSet()
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAcceptedFriendIds(userId: String): Result<Set<String>> {
        return try {
            val friendships1 = db.collection("friendships")
                .whereEqualTo("id_user1", userId)
                .whereEqualTo("status", "accepted")
                .get().await()

            val friendships2 = db.collection("friendships")
                .whereEqualTo("id_user2", userId)
                .whereEqualTo("status", "accepted")
                .get().await()

            val friendIds = mutableSetOf<String>()
            friendships1.documents.forEach { doc ->
                doc.toObject(Friendship::class.java)?.id_user2?.let { friendIds.add(it) }
            }
            friendships2.documents.forEach { doc ->
                doc.toObject(Friendship::class.java)?.id_user1?.let { friendIds.add(it) }
            }
            Result.success(friendIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class MessageRepository {
    val db = FirebaseFirestore.getInstance()

    suspend fun sendPrivateMessage(message: PrivateMessage): Result<Unit> {
        return try {
            db.collection("private_messages").add(message).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(message: GroupMessage): Result<Unit> {
        return try {
            db.collection("group_messages").add(message).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getConversation(user1Id: String, user2Id: String): Result<List<PrivateMessage>> {
        return try {
            val messages = db.collection("private_messages")
                .orderBy("sendingDate", Query.Direction.ASCENDING)
                .get().await()
                .toObjects(PrivateMessage::class.java)
                .filter {
                    (it.id_sender == user1Id && it.id_receiver == user2Id) ||
                            (it.id_sender == user2Id && it.id_receiver == user1Id)
                }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenForPrivateMessages(
        conversationId: String,
        onUpdate: (List<PrivateMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("private_messages")
            .whereEqualTo("conversationId", conversationId)
            .orderBy("sendingDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.toObjects(PrivateMessage::class.java) ?: emptyList())
            }
    }

    fun listenForGroupMessages(
        clubId: String,
        onUpdate: (List<GroupMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("group_messages")
            .whereEqualTo("id_bookClub", clubId)
            .orderBy("sendingDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.toObjects(GroupMessage::class.java) ?: emptyList())
            }
    }
}

class BookClubRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getClubsForUser(userId: String): Result<List<BookClub>> {
        return try {
            val memberships = db.collection("book_club_members")
                .whereEqualTo("id_user", userId)
                .whereEqualTo("status", "active")
                .get()
                .await()

            val clubIds = memberships.documents.mapNotNull {
                it.getString("id_book_club")
            }

            if (clubIds.isEmpty()) return Result.success(emptyList())

            val clubs = clubIds.mapNotNull { clubId ->
                db.collection("book_clubs")
                    .document(clubId)
                    .get()
                    .await()
                    .toObject(BookClub::class.java)
            }
            Result.success(clubs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getClubById(clubId: String): Result<BookClub> {
        return try {
            val doc = db.collection("book_clubs").document(clubId).get().await()
            val club = doc.toObject(BookClub::class.java)
                ?: throw Exception("Club not found")
            Result.success(club)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMembersForClub(clubId: String): Result<List<String>> {
        return try {
            val snapshot = db.collection("book_club_members")
                .whereEqualTo("id_book_club", clubId)
                .whereEqualTo("status", "active")
                .get()
                .await()
            val memberIds = snapshot.documents.mapNotNull {
                it.getString("id_user")
            }
            Result.success(memberIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularBookClubs(limit: Int = 10): Result<List<BookClub>> {
        return try {
            val snapshot = db.collection("book_clubs")
                // .orderBy("memberCount", Query.Direction.DESCENDING) // Opțional, dacă ai câmpul
                .limit(limit.toLong())
                .get()
                .await()

            val clubs = snapshot.documents.mapNotNull { doc ->
                doc.toObject(BookClub::class.java)?.apply { id_book_club = doc.id }

            }
            Result.success(clubs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMemberCount(clubId: String): Int {
        return try {
            val snapshot = db.collection("book_club_members")
                .whereEqualTo("id_book_club", clubId)
                .whereEqualTo("status", "active")
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            0
        }
    }

    suspend fun addUserToClub(userId: String, clubId: String): Result<Unit> {
        return try {
            val memberData = hashMapOf(
                "id_user" to userId,
                "id_book_club" to clubId,
                "role" to "member",
                "status" to "active",
                "joinedAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("book_club_members").add(memberData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class AIChatRepository{
    private val db = FirebaseFirestore.getInstance()

    suspend fun saveInteractions(interaction: ChatInteractions): Result<Unit>{
        return try{
            db.collection("chat_interactions")
                .add(interaction)
                .await()
            Log.d("AIChatRepository", "Interaction saved successfully")
            Result.success(Unit)
        }catch(e: java.lang.Exception){
            Log.e("AIChatRepository", "Error saving interaction", e)
            Result.failure(e)
        }
    }

    suspend fun loadHistory(userId: String): Result<List<ChatInteractions>>{
        return try{
            val snapshot = db.collection("chat_interactions")
                .whereEqualTo("id_user", userId)
                .orderBy("generationDate", Query.Direction.ASCENDING)
                .get()
                .await()
            val interactions = snapshot.documents.mapNotNull { documentSnapshot ->
                documentSnapshot.toObject(ChatInteractions::class.java)
            }
            Log.d("AIChatRepository", "History loaded successfully")
            Result.success(interactions)
            }catch(e: Exception){
            Log.e("AIChatRepository", "Error loading history", e)
            Result.failure(e)
        }
    }

    suspend fun clearHistory(userId: String): Result<Unit> {
        return try {
            val snapshot = db.collection("chat_interactions")
                .whereEqualTo("id_user", userId)
                .get()
                .await()
            val batch = db.batch()
            snapshot.documents.forEach { documentSnapshot ->
                batch.delete(documentSnapshot.reference)
            }

            batch.commit().await()
            Log.d("AIChatRepository", "History cleared successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AIChatRepository", "Error clearing history", e)
            Result.failure(e)
        }
    }
}