import re

with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'r') as f:
    content = f.read()

# Define the replacement block
replacement = """    var isSearchExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (seriesToUnlock != null) {
        com.example.ui.components.PinUnlockDialog(
            onUnlock = {
                val s = seriesToUnlock
                seriesToUnlock = null
                if (s != null) selectedSeries = s
            },
            onDismiss = { seriesToUnlock = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 16.dp)) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 10.sp)
                }

                val tabIcons = listOf(Icons.Default.Home, Icons.Default.Movie, Icons.Default.Tv, Icons.Default.LiveTv, Icons.Default.Favorite)
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(tabIcons[index], contentDescription = title, modifier = Modifier.size(20.dp)) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = Color.Gray
                        )
                    }
                }

                IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_placeholder), tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else Color.White)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_desc), tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_desc)) },
                            onClick = { showProfileSheet = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.support_tickets_title)) },
                            onClick = { showSupportSheet = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Default.SupportAgent, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_issue_btn)) },
                            onClick = { showGeneralReportDialog = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_desc)) },
                            onClick = { showSettingsSheet = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                    }
                }
            }

            val notification by com.example.model.SupportRepository.unreadNotification.collectAsState()
            if (notification != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            showSupportSheet = true
                            com.example.model.SupportRepository.dismissNotification()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.notification_banner_title), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.notification_banner_msg), color = Color.LightGray, fontSize = 11.sp)
                        }
                        TextButton(onClick = {
                            showSupportSheet = true
                            com.example.model.SupportRepository.dismissNotification()
                        }) {
                            Text(stringResource(R.string.view_ticket_btn), color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { com.example.model.SupportRepository.dismissNotification() }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isSearchExpanded || (selectedTabIndex in 1..3 && displayGroups.isNotEmpty())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_placeholder), tint = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    if (selectedTabIndex in 1..3 && displayGroups.isNotEmpty()) {
                        IconButton(
                            onClick = { showCategoryDrawer = true },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (selectedGroup != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.categories),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (selectedTabIndex == 0) {"""

# Find the block to replace
start_marker = "    if (seriesToUnlock != null) {"
end_marker = "                if (selectedTabIndex == 0) {"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker, start_idx) + len(end_marker)

if start_idx != -1 and end_idx != -1:
    new_content = content[:start_idx] + replacement + content[end_idx:]
    with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'w') as f:
        f.write(new_content)
    print("Replaced block successfully.")
else:
    print("Could not find markers.")
    print("Start:", start_idx)
    print("End:", end_idx)
