import re

with open("app/src/main/java/com/example/model/db/IptvDao.kt", "r") as f:
    content = f.read()

content = content.replace(
    "fun getPlaylistsForUser(userId: String): Flow<List<PlaylistEntity>>", 
    "fun getPlaylistsForUser(userId: String): Flow<List<PlaylistEntity>>\n\n    @Query(\"SELECT * FROM playlists WHERE id = :id LIMIT 1\")\n    suspend fun getPlaylistById(id: Int): PlaylistEntity?"
)

with open("app/src/main/java/com/example/model/db/IptvDao.kt", "w") as f:
    f.write(content)
print("Updated IptvDao.kt")
