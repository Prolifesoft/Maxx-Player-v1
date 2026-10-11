package com.example.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.FavoritesManager
import com.example.model.PlayerRepository
import com.example.model.ResumeChoice
import com.example.model.db.AppDatabase
import com.example.model.db.PlaybackProgressEntity
import com.example.parser.M3uItem
import com.example.ui.theme.RedPrimary
import com.example.util.KeepSystemBarsHidden
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MovieDetailDialog(
    movie: M3uItem,
    currentLang: String,
    onDismiss: () -> Unit,
    onPlayMovie: (M3uItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val favoriteUrls by FavoritesManager.favoriteUrls.collectAsState()
    val isFavorite = favoriteUrls.contains(movie.url)

    var backdropUrl by remember(movie.url) { mutableStateOf<String?>(null) }
    var trailerKey by remember(movie.url) { mutableStateOf<String?>(null) }
    var tmdbDetail by remember(movie.url) { mutableStateOf<TmdbFullDetail?>(null) }
    var isLoadingTmdb by remember(movie.url) { mutableStateOf(true) }
    var isOverviewExpanded by remember(movie.url) { mutableStateOf(false) }
    var resumeProgress by remember(movie.url) { mutableStateOf<PlaybackProgressEntity?>(null) }

    // TMDB Detayları & Fragman Getir
    LaunchedEffect(movie.url, movie.title) {
        isLoadingTmdb = true
        try {
            val (bg, trailer) = fetchTmdbMovieBackdrop(movie.title)
            backdropUrl = bg
            trailerKey = trailer

            val detail = fetchTmdbFullDetail(
                query = movie.title,
                isSeries = false
            )
            tmdbDetail = detail
            if (backdropUrl.isNullOrBlank() && !detail?.backdropUrl.isNullOrBlank()) {
                backdropUrl = detail?.backdropUrl
            }
            if (trailerKey.isNullOrBlank() && !detail?.trailerKey.isNullOrBlank()) {
                trailerKey = detail?.trailerKey
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingTmdb = false
        }
    }

    // Kaldığı yer (Resume) kontrolü
    LaunchedEffect(movie.url) {
        try {
            val dao = AppDatabase.getDatabase(context).iptvDao()
            val cached = PlayerRepository.lastPositions[movie.url]
            val direct = dao.getProgressForUrl(movie.url)
            if (cached != null && cached.first > 1000L) {
                resumeProgress = PlaybackProgressEntity(
                    url = movie.url,
                    title = movie.title,
                    logo = movie.logo,
                    type = "MOVIE",
                    positionMs = cached.first,
                    durationMs = cached.second.coerceAtLeast(cached.first + 60_000L)
                )
            } else if (direct != null && direct.positionMs > 0L && direct.durationMs > 0L) {
                resumeProgress = direct
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Çoklu arka plan slayt desteği (TMDB'den dönen sahne fotoğrafları)
    val backdropList = remember(tmdbDetail, backdropUrl, movie.logo) {
        val fromTmdb = tmdbDetail?.backdropUrls?.filter { it.isNotBlank() }.orEmpty()
        when {
            fromTmdb.isNotEmpty() -> fromTmdb
            !backdropUrl.isNullOrBlank() -> listOf(backdropUrl!!)
            !tmdbDetail?.backdropUrl.isNullOrBlank() -> listOf(tmdbDetail!!.backdropUrl!!)
            !tmdbDetail?.posterUrl.isNullOrBlank() -> listOf(tmdbDetail!!.posterUrl!!)
            !movie.logo.isNullOrBlank() -> listOf(movie.logo!!)
            else -> emptyList()
        }
    }
    var currentBackdropIndex by remember(backdropList) { mutableIntStateOf(0) }
    LaunchedEffect(backdropList) {
        currentBackdropIndex = 0
        if (backdropList.size > 1) {
            while (true) {
                delay(5000L)
                currentBackdropIndex = (currentBackdropIndex + 1) % backdropList.size
            }
        }
    }

    val displayBackdrop = backdropList.getOrNull(currentBackdropIndex)
        ?: backdropUrl
        ?: tmdbDetail?.backdropUrl
        ?: tmdbDetail?.posterUrl
        ?: movie.logo

    val displayTitle = tmdbDetail?.title?.takeIf { it.isNotBlank() } ?: movie.title
    val ratingText = tmdbDetail?.rating?.takeIf { it > 0.0 }?.let {
        String.format(Locale.US, "%.1f", it)
    }
    val releaseYearText = tmdbDetail?.releaseDate?.take(4)?.takeIf { it.isNotBlank() }
    val runtimeMinutes = tmdbDetail?.runtimeMinutes
    val directorText = tmdbDetail?.directors?.firstOrNull()?.takeIf { it.isNotBlank() }
    val overviewText = tmdbDetail?.overview?.takeIf { it.isNotBlank() }
    val castList = tmdbDetail?.cast ?: emptyList()
    val genresList = tmdbDetail?.genres ?: emptyList()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler { onDismiss() }
        KeepSystemBarsHidden()

        com.example.ui.ProvideAppLocale(currentLang) {
            if (!isLandscape) {
                // ==================== DİKEY MOD (PORTRAIT) ====================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0D10))
                ) {
                    // Arka Plan Görseli
                    if (!displayBackdrop.isNullOrBlank()) {
                        Crossfade(
                            targetState = displayBackdrop,
                            animationSpec = tween(700),
                            label = "movie_backdrop_fade"
                        ) { bgUrl ->
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(bgUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFF0B0D10).copy(alpha = 0.75f),
                                            Color(0xFF0B0D10)
                                        ),
                                        startY = 60f
                                    )
                                )
                        )
                    }

                    // Kapatma ("X") butonu
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp)
                            .size(38.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_btn),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // İçerik Listesi (Kaydırılabilir)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 32.dp)
                    ) {
                        Spacer(modifier = Modifier.height(if (!displayBackdrop.isNullOrBlank()) 140.dp else 24.dp))

                        // Film Başlığı
                        Text(
                            text = displayTitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Rozetler: Puan, Çıkış Yılı, Süre, Yönetmen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!ratingText.isNullOrBlank()) {
                                Surface(
                                    color = Color(0xFFF5C518),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = ratingText,
                                            color = Color.Black,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            if (!releaseYearText.isNullOrBlank()) {
                                Surface(
                                    color = Color(0xFF222834),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFF384358))
                                ) {
                                    Text(
                                        text = releaseYearText,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (runtimeMinutes != null && runtimeMinutes > 0) {
                                Surface(
                                    color = Color(0xFF222834),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFF384358))
                                ) {
                                    Text(
                                        text = "$runtimeMinutes dk",
                                        color = Color(0xFFFFCA28),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            val groupBadge = movie.group?.takeIf { it.isNotBlank() }
                            if (groupBadge != null) {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFF334155))
                                ) {
                                    Text(
                                        text = groupBadge,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Yönetmen
                        if (!directorText.isNullOrBlank()) {
                            Text(
                                text = "Yönetmen: $directorText",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                            )
                        }

                        // Tür Etiketleri (Genres)
                        if (genresList.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(genresList) { genre ->
                                    Surface(
                                        color = Color(0xFF1A2333),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(0.5.dp, Color(0xFF2C3E5B))
                                    ) {
                                        Text(
                                            text = genre,
                                            color = Color(0xFF90CAF9),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ================= AKSİYON BUTONLARI =================
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var isPlayFocused by remember { mutableStateOf(false) }
                            var isTrailerFocused by remember { mutableStateOf(false) }
                            var isFavFocused by remember { mutableStateOf(false) }

                            // 1. Oynat Butonu
                            Button(
                                onClick = {
                                    PlayerRepository.preselectedResumeChoice = null
                                    onPlayMovie(movie)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .onFocusChanged { isPlayFocused = it.isFocused }
                                    .border(
                                        width = if (isPlayFocused) 2.5.dp else 0.dp,
                                        color = if (isPlayFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .focusable()
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.play),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }

                            // 2. Fragman Butonu
                            if (!trailerKey.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://www.youtube.com/watch?v=$trailerKey")
                                            )
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(
                                        if (isTrailerFocused) 2.dp else 1.dp,
                                        if (isTrailerFocused) Color.White else Color.White.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .onFocusChanged { isTrailerFocused = it.isFocused }
                                        .focusable()
                                ) {
                                    Icon(
                                        Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = Color(0xFFFF3344),
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.info_watch_trailer),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }

                            // 3. Favori Butonu
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        FavoritesManager.toggleFavorite(movie.url)
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isFavorite) RedPrimary else Color.White
                                ),
                                border = BorderStroke(
                                    if (isFavFocused) 2.dp else 1.dp,
                                    if (isFavFocused) Color.White else if (isFavorite) RedPrimary else Color.White.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .onFocusChanged { isFavFocused = it.isFocused }
                                    .focusable()
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isFavorite) RedPrimary else Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isFavorite) stringResource(R.string.favorited_btn) else stringResource(R.string.favorite_btn),
                                    color = if (isFavorite) RedPrimary else Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }

                        // Kaldığı yerden devam et çubuğu (Varsa)
                        val prog = resumeProgress
                        if (prog != null && prog.positionMs > 0L && prog.durationMs > 0L) {
                            val ratio = (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                            val percent = (ratio * 100f).toInt().coerceIn(1, 100)

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Color(0xFF151922),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF283142)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "İzleme Durumu: %$percent tamamlandı",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { ratio },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = RedPrimary,
                                            trackColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                PlayerRepository.preselectedResumeChoice = ResumeChoice.RESUME
                                                onPlayMovie(movie)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Devam Et", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                PlayerRepository.preselectedResumeChoice = ResumeChoice.RESTART
                                                onPlayMovie(movie)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Baştan", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // ================= KISA AÇIKLAMA =================
                        if (!overviewText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Özet / Açıklama",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = overviewText,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                maxLines = if (isOverviewExpanded) 15 else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .clickable { isOverviewExpanded = !isOverviewExpanded }
                            )
                        } else if (isLoadingTmdb) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = RedPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "TMDB bilgileri yükleniyor...",
                                    color = Color.Gray,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        // ================= OYUNCULAR (CAST) =================
                        if (castList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.info_cast_section_title),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(castList.take(15)) { member ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(58.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1E232E)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!member.photoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(member.photoUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = member.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = member.name,
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        if (member.character.isNotBlank()) {
                                            Text(
                                                text = member.character,
                                                color = Color.Gray,
                                                fontSize = 8.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ================= SAHNE GÖRSELLERİ (BACKDROP GALLERY) =================
                        if (backdropList.size > 1) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Sahne Görselleri",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(backdropList.take(8)) { imgUrl ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (imgUrl == displayBackdrop) RedPrimary else Color(0xFF263238)
                                        ),
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(75.dp)
                                            .clickable {
                                                val idx = backdropList.indexOf(imgUrl)
                                                if (idx >= 0) currentBackdropIndex = idx
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imgUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== YATAY MOD (LANDSCAPE / ANDROID TV) ====================
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0D10))
                ) {
                    // Sol Kolon (390.dp): Arka Plan Görseli, Başlık, Rozetler, Butonlar, Açıklama
                    Box(
                        modifier = Modifier
                            .width(390.dp)
                            .fillMaxHeight()
                    ) {
                        if (!displayBackdrop.isNullOrBlank()) {
                            Crossfade(
                                targetState = displayBackdrop,
                                animationSpec = tween(700),
                                label = "movie_landscape_backdrop"
                            ) { bgUrl ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(bgUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.35f),
                                                Color(0xFF0B0D10).copy(alpha = 0.88f),
                                                Color(0xFF0B0D10)
                                            )
                                        )
                                    )
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF14171C)))
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Başlık
                            Text(
                                text = displayTitle,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Rozetler
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!ratingText.isNullOrBlank()) {
                                    Surface(
                                        color = Color(0xFFF5C518),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = ratingText,
                                                color = Color.Black,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }

                                if (!releaseYearText.isNullOrBlank()) {
                                    Surface(
                                        color = Color(0xFF222834),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, Color(0xFF384358))
                                    ) {
                                        Text(
                                            text = releaseYearText,
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (runtimeMinutes != null && runtimeMinutes > 0) {
                                    Surface(
                                        color = Color(0xFF222834),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, Color(0xFF384358))
                                    ) {
                                        Text(
                                            text = "$runtimeMinutes dk",
                                            color = Color(0xFFFFCA28),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (!directorText.isNullOrBlank()) {
                                Text(
                                    text = "Yönetmen: $directorText",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Butonlar
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isPlayFocused by remember { mutableStateOf(false) }
                                var isTrailerFocused by remember { mutableStateOf(false) }
                                var isFavFocused by remember { mutableStateOf(false) }

                                Button(
                                    onClick = {
                                        PlayerRepository.preselectedResumeChoice = null
                                        onPlayMovie(movie)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .onFocusChanged { isPlayFocused = it.isFocused }
                                        .border(
                                            width = if (isPlayFocused) 2.dp else 0.dp,
                                            color = if (isPlayFocused) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .focusable()
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.play),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }

                                if (!trailerKey.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://www.youtube.com/watch?v=$trailerKey")
                                                )
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(
                                            if (isTrailerFocused) 2.dp else 1.dp,
                                            if (isTrailerFocused) Color.White else Color.White.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .onFocusChanged { isTrailerFocused = it.isFocused }
                                            .focusable()
                                    ) {
                                        Icon(
                                            Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = Color(0xFFFF3344),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.info_watch_trailer),
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            FavoritesManager.toggleFavorite(movie.url)
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isFavorite) RedPrimary else Color.White
                                    ),
                                    border = BorderStroke(
                                        if (isFavFocused) 2.dp else 1.dp,
                                        if (isFavFocused) Color.White else if (isFavorite) RedPrimary else Color.White.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .onFocusChanged { isFavFocused = it.isFocused }
                                        .focusable()
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isFavorite) RedPrimary else Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isFavorite) stringResource(R.string.favorited_btn) else stringResource(R.string.favorite_btn),
                                        color = if (isFavorite) RedPrimary else Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            // Kısa Açıklama
                            if (!overviewText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = overviewText,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    maxLines = if (isOverviewExpanded) 8 else 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { isOverviewExpanded = !isOverviewExpanded }
                                )
                            }
                        }
                    }

                    // Sağ Kolon: Oyuncular, Türler, Sahne Galerisi, Kaldığın Yer, Kapatma Butonu
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF0F1217))
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Üst Bar: Kapat Butonu
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close_btn),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Kaldığı yerden devam et çubuğu (Landscape)
                        val prog = resumeProgress
                        if (prog != null && prog.positionMs > 0L && prog.durationMs > 0L) {
                            val ratio = (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                            val percent = (ratio * 100f).toInt().coerceIn(1, 100)

                            Surface(
                                color = Color(0xFF1A212E),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF2E3E5B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Kaldığınız Yer: %$percent tamamlandı",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { ratio },
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = RedPrimary,
                                            trackColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                PlayerRepository.preselectedResumeChoice = ResumeChoice.RESUME
                                                onPlayMovie(movie)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Devam Et", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                PlayerRepository.preselectedResumeChoice = ResumeChoice.RESTART
                                                onPlayMovie(movie)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Baştan", fontSize = 11.5.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Oyuncular (Cast with Photos)
                        if (castList.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.info_cast_section_title),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(castList.take(15)) { member ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(58.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1E232E)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!member.photoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(member.photoUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = member.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = member.name,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        if (member.character.isNotBlank()) {
                                            Text(
                                                text = member.character,
                                                color = Color.Gray,
                                                fontSize = 8.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Film Detay Kartı (Türler, Çözünürlük, Oynatma Listesi Bilgisi)
                        Surface(
                            color = Color(0xFF141820),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, Color(0xFF263244)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Film Bilgileri",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (genresList.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "Türler: ", color = Color.Gray, fontSize = 11.sp)
                                        Text(
                                            text = genresList.joinToString(", "),
                                            color = Color(0xFF90CAF9),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                if (movie.group != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "Kategori: ", color = Color.Gray, fontSize = 11.sp)
                                        Text(
                                            text = movie.group!!,
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Yayın Formatı: ", color = Color.Gray, fontSize = 11.sp)
                                    val ext = movie.url.substringAfterLast('.', "").uppercase().take(4)
                                    Text(
                                        text = if (ext.isNotBlank()) ext else "VOD / MP4",
                                        color = Color(0xFF81C784),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Sahne Görselleri Galerisi (Landscape)
                        if (backdropList.size > 1) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Sahne Görselleri",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(backdropList.take(8)) { imgUrl ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (imgUrl == displayBackdrop) RedPrimary else Color(0xFF263238)
                                        ),
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(80.dp)
                                            .clickable {
                                                val idx = backdropList.indexOf(imgUrl)
                                                if (idx >= 0) currentBackdropIndex = idx
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imgUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
