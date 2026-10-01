package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.CategoryManager
import com.example.model.PlaylistRepository
import com.example.parser.ItemType

data class CategoryInfo(
    val name: String,
    val type: ItemType,
    val itemCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementDialog(onDismiss: () -> Unit) {
    val allItems by PlaylistRepository.playlist.collectAsState()
    val hiddenCategories by CategoryManager.hiddenCategories.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: All, 1: Live, 2: Movie, 3: Series

    val categories = remember(allItems) {
        allItems
            .filter { !it.group.isNullOrBlank() }
            .groupBy { it.group ?: "Kategorisiz" }
            .map { (groupName, items) ->
                val primaryType = items.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.key ?: ItemType.LIVE
                CategoryInfo(
                    name = groupName,
                    type = primaryType,
                    itemCount = items.size
                )
            }
            .sortedBy { it.name }
    }

    val filteredCategories = remember(categories, selectedTab, searchQuery) {
        categories.filter { category ->
            val matchesTab = when (selectedTab) {
                1 -> category.type == ItemType.LIVE
                2 -> category.type == ItemType.MOVIE
                3 -> category.type == ItemType.SERIES
                else -> true
            }
            val matchesSearch = category.name.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.example.util.KeepSystemBarsHidden()
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E2C)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header and Search Bar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.category_mgmt_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            val hiddenCount = categories.count { hiddenCategories.contains(it.name) }
                            Text(
                                text = stringResource(R.string.category_mgmt_count, categories.size, hiddenCount),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF12121D),
                            unfocusedContainerColor = Color(0xFF12121D),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_desc),
                            tint = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category Type Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    val tabs = listOf(
                        stringResource(R.string.category_tab_all),
                        stringResource(R.string.category_tab_live),
                        stringResource(R.string.category_tab_movies),
                        stringResource(R.string.category_tab_series)
                    )
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Bulk Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.category_count_fmt, filteredCategories.size),
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    Row {
                        TextButton(
                            onClick = {
                                val names = filteredCategories.map { it.name }
                                CategoryManager.showCategoriesInList(names)
                            }
                        ) {
                            Text(stringResource(R.string.category_show_all), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(
                            onClick = {
                                val names = filteredCategories.map { it.name }
                                CategoryManager.hideCategoriesInList(names)
                            }
                        ) {
                            Text(stringResource(R.string.category_hide_all), fontSize = 12.sp, color = Color(0xFFFF7043))
                        }
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(8.dp))

                // List of Categories
                if (filteredCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Kategori bulunamadı", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCategories, key = { it.name }) { category ->
                            val isHidden = hiddenCategories.contains(category.name)
                            val isVisible = !isHidden

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        CategoryManager.toggleCategoryVisibility(category.name)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isVisible) Color(0xFF28293D) else Color(0xFF181824)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val (icon, iconColor) = when (category.type) {
                                            ItemType.LIVE -> Icons.Default.Tv to Color(0xFF66BB6A)
                                            ItemType.MOVIE -> Icons.Default.Movie to Color(0xFF42A5F5)
                                            ItemType.SERIES -> Icons.Default.VideoLibrary to Color(0xFFAB47BC)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(iconColor.copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = iconColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = category.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = if (isVisible) Color.White else Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${category.itemCount} içerik",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isVisible) "Görünür" else "Gizli",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isVisible) Color(0xFF66BB6A) else Color(0xFFE53935),
                                            modifier = Modifier.padding(end = 8.dp)
                                        )

                                        Switch(
                                            checked = isVisible,
                                            onCheckedChange = {
                                                CategoryManager.toggleCategoryVisibility(category.name)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = Color.Gray,
                                                uncheckedTrackColor = Color.DarkGray
                                            )
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
