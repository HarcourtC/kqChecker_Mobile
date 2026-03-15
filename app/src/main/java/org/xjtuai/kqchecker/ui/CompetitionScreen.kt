package org.xjtuai.kqchecker.ui

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.xjtuai.kqchecker.network.CompetitionItem
import org.xjtuai.kqchecker.repository.RepositoryProvider

/**
 * 竞赛信息屏幕
 * 展示教务处竞赛列表，支持分类筛选和刷新
 */
@Composable
fun CompetitionScreen(
    onPostEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val repo = remember { RepositoryProvider.getCompetitionRepository() }

    var items by remember { mutableStateOf<List<CompetitionItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableStateOf<String?>(null) }
    var totalCount by remember { mutableStateOf(0) }

    // 0 -> Recent, 1 -> All
    var selectedTab by remember { mutableStateOf(0) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var openedUrl by remember { mutableStateOf<String?>(null) }
    var openedTitle by remember { mutableStateOf("竞赛详情") }
    var inAppWebView by remember { mutableStateOf<WebView?>(null) }

    val categories = remember(items) {
        listOf("全部") + items.map { it.category }.distinct().sorted()
    }

    val filteredItems = remember(items, selectedTab, selectedCategory) {
        items.filter { item ->
            val matchTab = when (selectedTab) {
                0 -> item.isNew
                1 -> true
                else -> true
            }
            val matchCategory = if (selectedCategory == "全部") true else item.category == selectedCategory
            matchTab && matchCategory
        }
    }

    /**
     * 加载竞赛数据
     * @param force 是否强制刷新
     */
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

    BackHandler(enabled = openedUrl != null) {
        val webView = inAppWebView
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            openedUrl = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (openedUrl == null) "教务处竞赛" else openedTitle) },
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 0.dp,
                navigationIcon = if (openedUrl != null) {
                    {
                        IconButton(onClick = {
                            val webView = inAppWebView
                            if (webView != null && webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                openedUrl = null
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                } else {
                    null
                },
                actions = {
                    IconButton(onClick = {
                        if (openedUrl == null) {
                            loadData(true)
                        } else {
                            inAppWebView?.reload()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        backgroundColor = MaterialTheme.colors.background
    ) { padding ->
        if (openedUrl != null) {
            AndroidView(
                modifier = modifier
                    .padding(padding)
                    .fillMaxSize(),
                factory = { context ->
                    val initialUrl = openedUrl
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        webViewClient = object : WebViewClient() {}
                        if (!initialUrl.isNullOrBlank()) {
                            loadUrl(initialUrl)
                        }
                        inAppWebView = this
                    }
                },
                update = { webView ->
                    val currentUrl = webView.url
                    val targetUrl = openedUrl
                    if (!targetUrl.isNullOrBlank() && currentUrl != targetUrl) {
                        webView.loadUrl(targetUrl)
                    }
                    inAppWebView = webView
                }
            )
            return@Scaffold
        }

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
                    text = { Text("近期（新）") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("全部") }
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
                    Text("暂无数据。\n请点击刷新按钮。", color = Color.Gray, style = MaterialTheme.typography.body2)
                }
            } else if (filteredItems.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("当前分类下暂无内容。", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (lastUpdate != null && selectedTab == 1 && selectedCategory == "全部") {
                        // Only show update time on the main "All" view to avoid clutter, or always show?
                        // Let's keep it always at top of list
                        item {
                            Text(
                                text = "最近成功更新时间：$lastUpdate",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                            )
                        }
                    }

                    items(filteredItems) { item ->
                        CompetitionCard(item = item, onClick = {
                            if (item.url.isBlank()) {
                                onPostEvent("Cannot open URL: ${item.url}")
                            } else {
                                openedUrl = item.url
                                openedTitle = item.title
                            }
                        })
                    }
                }
            }
        }
    }
}

/**
 * 竞赛卡片组件
 * 展示单个竞赛信息的卡片样式
 */
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
                Column {
                    Text(
                        text = "发布: ${item.date}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )

                    if (!item.deadline.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "截止: ${item.deadline}",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = item.type.uppercase(),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 分类标签组件
 * 用于显示竞赛分类的标签样式
 */
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
