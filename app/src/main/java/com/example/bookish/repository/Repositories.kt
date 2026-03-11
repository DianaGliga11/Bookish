package com.example.bookish.repository


import com.example.bookish.R
import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.bookish.models.Book
import com.example.bookish.models.Friendship
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

    suspend fun getBookById(bookId: String): Result<Book> {
        return try {
            val doc = db.collection("books").document(bookId).get().await()
            val book = doc.toObject(Book::class.java)
            if (book != null) {
                Result.success(book)
            } else {
                Result.failure(Exception("Book not found"))
            }
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

    suspend fun getBooksInShelf(shelfId: String): Result<List<String>> {
        return try {
            val bookIds = db.collection("shelf_books")
                .whereEqualTo("id_shelf", shelfId)
                .get().await()
                .toObjects(ShelfBook::class.java)
                .map { it.id_book }
            Result.success(bookIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class ReviewRepository {
    private val db = FirebaseFirestore.getInstance()

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

    suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> {
        return try {
            db.collection("friendships").document(friendshipId)
                .update("status", "accepted").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFriends(userId: String): Result<List<String>> {
        return try {
            val friendships = db.collection("friendships")
                .whereEqualTo("status", "accepted")
                .get().await()
                .toObjects(Friendship::class.java)

            val friendIds = friendships.mapNotNull { friendship ->
                when {
                    friendship.id_user1 == userId -> friendship.id_user2
                    friendship.id_user2 == userId -> friendship.id_user1
                    else -> null
                }
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
}