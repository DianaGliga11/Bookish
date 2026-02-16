package com.example.bookish.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    var id_user: String = "",
    var email: String = "",
    var username: String = "",
    var password: String = "",
    var bio: String = "",
    var profileImageUrl: String = "",
    var createdAt: Timestamp = Timestamp.now()
)

data class Author(
    @DocumentId
    var id_author: String = "",
    var name: String = "",
)

data class Genre(
    @DocumentId
    var id_genre: String = "",
    var type: String = ""
)

data class Book(
    @DocumentId
    var id_book: String = "",
    var title: String = "",
    var description: String = "",
    var id_author: String = "",
    var id_genre: String = "",
    var coverImageUrl: String = ""
)

data class Shelf(
    @DocumentId
    var id_shelf: String = "",
    var name: String = "",
    var id_user: String = ""
)

data class ShelfBook(
    @DocumentId
    var id_shelf_book: String = "",
    var id_shelf: String = "",
    var id_book: String = ""
)

data class Review(
    @DocumentId
    var id_review: String = "",
    var id_user: String = "",
    var id_book: String = "",
    var rating: Int = 0
)

data class Comment(
    @DocumentId
    var id_comment: String = "",
    var id_review: String = "",
    var content: String = ""
)

data class Friendship(
    @DocumentId
    var id_friendship: String = "",
    var id_user1: String = "",
    var id_user2: String = "",
    var status: String = ""
)

data class BookClub(
    @DocumentId
    var id_book_club: String = "",
    var name: String = "",
    var description: String = ""
)

data class BookClubMember (
    @DocumentId
    var id_book_club_member: String = "",
    var id_book_club: String = "",
    var id_user: String = "",
    var role: String = "",
    var status: String = ""
)

data class PrivateMessage(
    @DocumentId
    var id_private_message: String = "",
    var id_sender: String = "",
    var id_receiver: String = "",
    var content: String = "",
    var sendingDate: Timestamp = Timestamp.now(),
    var id_book: String = ""
)

data class GroupMessage(
    @DocumentId
    var id_group_message: String = "",
    var id_bookClub: String = "",
    var id_user: String = "",
    var id_book: String = ""
)

data class WeeklyRecommandation(
    @DocumentId
    var id_weekly_recommandation: String = "",
    var id_user: String = "",
    var id_book: String = "",
    var content: String = "",
    var sendingDate: Timestamp = Timestamp.now()
)

data class Challenge(
    @DocumentId
    var id_challenge: String = "",
    var id_user: String = "",
    var title: String = "",
    var description: String = "",
    var date_start: Timestamp = Timestamp.now(),
    var date_finish: Timestamp = Timestamp.now(),
    var progress: Double = 0.0
)

data class ChatInteractions(
    @DocumentId
    var id_chat_interactions: String = "",
    var id_user: String = "",
    var messageUser: String = "",
    var messageChatbot: String = "",
    var generationDate: Timestamp = Timestamp.now()
)

