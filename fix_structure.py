import re

with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'r') as f:
    content = f.read()

# Let's find where we put Scaffold before
scaffold_start = content.find("    Scaffold(")
if scaffold_start == -1:
    print("Scaffold not found")
    exit(1)

# Find the start of selectedTabIndex == 0
tab_start = content.find("                if (selectedTabIndex == 0) {")
if tab_start == -1:
    print("Tab start not found")
    exit(1)

# We want to insert `Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {` right inside the Scaffold block.
# Wait, let's just do a string replacement on the block I inserted earlier.

new_block = """    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
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
"""
# find the old block to replace
old_block_start = content.find("    Scaffold(\n        containerColor = MaterialTheme.colorScheme.background\n    ) { paddingValues ->\n        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {")

if old_block_start != -1:
    end_of_box = content.find("                if (selectedTabIndex == 0) {", old_block_start)
    if end_of_box != -1:
        new_content = content[:old_block_start] + new_block + content[end_of_box:]
        
        # We need to remove ONE brace before Category Drawer.
        # But wait, my previous brace check printed:
        # 529-            }
        # 530-            }
        # 531-            }
        # 532-            }
        # In my new layout: Box A, Column B, Box C. So three levels. We need to remove one brace.
        # Let's find "Category Drawer Overlay"
        cat_index = new_content.find("// Category Drawer Overlay")
        if cat_index != -1:
            braces_area = new_content[cat_index-100:cat_index]
            # remove the last '}' found before the drawer
            last_brace_idx = new_content.rfind('}', 0, cat_index)
            if last_brace_idx != -1:
                # remove the line containing the brace, or just the brace
                line_start = new_content.rfind('\n', 0, last_brace_idx) + 1
                line_end = new_content.find('\n', last_brace_idx)
                # Let's just remove the brace
                new_content = new_content[:last_brace_idx] + new_content[last_brace_idx+1:]
        
        with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'w') as f:
            f.write(new_content)
        print("Structure fixed successfully.")
    else:
        print("End of box not found")
else:
    print("Old block not found")

