with open("app/src/main/java/com/example/model/db/IptvDao.kt", "r") as f:
    content = f.read()

content = content.replace("fun getPlaylistsForUser(userId: String): Flow<List<PlaylistEntity>>", "fun getPlaylistsForUser(userId: String): Flow<List<PlaylistEntity>>\n\n    @androidx.room.Delete\n    suspend fun deletePlaylist(playlist: PlaylistEntity)")

with open("app/src/main/java/com/example/model/db/IptvDao.kt", "w") as f:
    f.write(content)
