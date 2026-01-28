package com.example.myapplication.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("""
        SELECT * FROM messages 
        WHERE (sender_id = :userId1 AND receiver_id = :userId2) 
           OR (sender_id = :userId2 AND receiver_id = :userId1) 
        ORDER BY timestamp ASC
    """)
    fun getConversation(userId1: Long, userId2: Long): Flow<List<MessageEntity>>

    @Query("""
        UPDATE messages 
        SET is_read = 1 
        WHERE sender_id = :senderId AND receiver_id = :receiverId AND is_read = 0
    """)
    suspend fun markConversationAsRead(senderId: Long, receiverId: Long)

    @Query("""
        SELECT COUNT(*) FROM messages 
        WHERE receiver_id = :userId AND is_read = 0
    """)
    suspend fun getUnreadCount(userId: Long): Int

    @Query("""
        SELECT * FROM messages 
        WHERE sender_id = :userId OR receiver_id = :userId 
        ORDER BY timestamp DESC
    """)
    fun getAllUserMessages(userId: Long): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages 
        WHERE id IN (
            SELECT MAX(id) 
            FROM messages 
            WHERE sender_id = :userId OR receiver_id = :userId 
            GROUP BY CASE 
                WHEN sender_id = :userId THEN receiver_id 
                ELSE sender_id 
            END
        )
        ORDER BY timestamp DESC
    """)
    fun getDialogsPreview(userId: Long): Flow<List<MessageEntity>>

    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    @Query("""
        SELECT * FROM messages 
        WHERE sender_id = :senderId 
        AND message = :message 
        AND timestamp = :timestamp 
        LIMIT 1
    """)
    suspend fun getMessageByContent(senderId: Long, message: String, timestamp: Long): MessageEntity?
    @Query("SELECT * FROM messages WHERE sender_id = :senderId AND receiver_id = :receiverId AND timestamp = :timestamp")
    suspend fun findMessage(senderId: Long, receiverId: Long, timestamp: Long): MessageEntity?

}
