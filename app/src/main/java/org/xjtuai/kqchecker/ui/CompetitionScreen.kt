package org.xjtuai.kqchecker.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.xjtuai.kqchecker.network.CompetitionItem
import org.xjtuai.kqchecker.repository.RepositoryProvider

@Composable
fun CompetitionScreen(
    onPostEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { RepositoryProvider.getCompetitionRepository() }

    var items by remember { mutableStateOf<List<CompetitionItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableStateOf<String?>(null) }
    var totalCount by remember { mutableStateOf(0) }

    // 0 -> Recent, 1 -> All
    var selectedTab by remember { mutableStateOf(0) }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember(items) {
        listOf("All") + items.map { it.category }.distinct().sorted()
    }

    val filteredItems = remember(items, selectedTab, selectedCategory) {
        items.filter { item ->
            val matchTab = when (selectedTab) {
                0 -> item.isNew
                1 -> true
                else -> true
            }
            val matchCategory = if (selectedCategory == "All") true else item.category == selectedCategory
            matchTab && matchCategory
        }
    }

    fun loadData(force: Boolean) {
        scope.launch {
            isLoading = true
            onPostEvent(if (force) "Refreshing competition data (API)..." else "Loading competition data...")
            
            try {
                val response = repo.getCompetitionData(forceRefresh = force)
                if (response != null && response.status == "success") {
                    items = response.data
                    lastUpdate = response.meta.updateTime
                    totalCount = response.meta.total
                    onPostEvent("Loaded ${items.size} competition items (Total: $totalCount)")
                } else {
                    onPostEvent("Failed to load competition data or invalid format.")
                }
            } catch (e: Exception) {
                onPostEvent("Error loading competitions: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // Load data initially (try cache first)
    LaunchedEffect(Unit) {
        loadData(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dean's Office Competitions") },
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 0.dp,
                actions = {
                    IconButton(onClick = { loadData(true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        backgroundColor = MaterialTheme.colors.background
    ) { padding ->
        Column(modifier = modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // 1. Main Tabs: Recent vs All
            TabRow(
                selectedTabIndex = selectedTab,
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Recent (NEW)") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("All") }
                )
            }

            // 2. Category Filter (Scrollable)
            if (categories.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                    backgroundColor = MaterialTheme.colors.surface, // Match surface for better look
                    contentColor = MaterialTheme.colors.primary,
                    edgePadding = 16.dp,
                    divider = { Divider(color = Color.Transparent) } // Clean look
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        Tab(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            text = { 
                                Text(
                                    text = category,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))

            if (items.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data loaded.\nTap refresh button.", color = Color.Gray, style = MaterialTheme.typography.body2)
                }
            } else if (filteredItems.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No items found in this category.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (lastUpdate != null && selectedTab == 1 && selectedCategory == "All") {
                        // Only show update time on the main "All" view to avoid clutter, or always show?
                        // Let's keep it always at top of list
                        item {
                            Text(
                                text = "Last successful update: $lastUpdate",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                            )
                        }
                    }
                    
                    items(filteredItems) { item ->
                        CompetitionCard(item = item, onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                onPostEvent("Cannot open URL: ${item.url}")
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun CompetitionCard(item: CompetitionItem, onClick: () -> Unit) {
    Card(
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContainerTag(text = item.category)
                if (item.isNew) {
                    Text(
                        text = "NEW",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = item.title,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.date,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                
                Text(
                    text = item.type.uppercase(),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun ContainerTag(text: String) {
    Surface(
        color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.Bold
        )
    }
}
