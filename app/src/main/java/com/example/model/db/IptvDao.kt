package com.example.model.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUser(userId: String): UserEntity?

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getFirstUser(): UserEntity?

    @Query("DELETE FROM users")
    suspend fun clearUsers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists WHERE userId = :userId ORDER BY timestamp DESC")
    fun getPlaylistsForUser(userId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getPlaylistsForUserSync(userId: String): List<PlaylistEntity>

    @Query("SELECT * FROM playlists ORDER BY timestamp DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY timestamp DESC")
    suspend fun getAllPlaylistsSync(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Int): PlaylistEntity?

    @androidx.room.Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsForPlaylist(playlistId: Int)

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    fun getChannelsForPlaylist(playlistId: Int): Flow<List<ChannelEntity>>
    
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND type = :type")
    fun getChannelsForPlaylistAndType(playlistId: Int, type: String): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT `group` FROM channels WHERE playlistId = :playlistId AND type = :type")
    fun getGroupsForPlaylistAndType(playlistId: Int, type: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackProgress(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress ORDER BY timestamp DESC LIMIT 20")
    fun getAllRecentProgress(): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM playback_progress WHERE type = :type ORDER BY timestamp DESC LIMIT 20")
    fun getRecentProgressForType(type: String): Flow<List<PlaybackProgressEntity>>
    
    @Query("SELECT * FROM playback_progress WHERE url = :url")
    suspend fun getProgressForUrl(url: String): PlaybackProgressEntity?

    @Query("DELETE FROM playback_progress")
    suspend fun clearRecentProgress()

    @Transaction
    suspend fun replaceChannelsForPlaylist(playlistId: Int, channels: List<ChannelEntity>) {
        deleteChannelsForPlaylist(playlistId)
        insertChannels(channels)
    }
}
