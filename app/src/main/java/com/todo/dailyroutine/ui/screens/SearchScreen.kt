package com.todo.dailyroutine.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.todo.dailyroutine.data.local.entity.LocalMemory
import com.todo.dailyroutine.ui.components.DashboardScaffold
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(viewModel: SearchViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val types = listOf("fact", "task", "goal", "preference", "context")

    DashboardScaffold(
        title = "Omni-Search",
        onBackClick = onBack
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                // Command Palette Search Input
                SearchInput(
                    query = state.query,
                    onQueryChange = { viewModel.onQueryChanged(it) }
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Filter Chips
                ScrollableRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip(
                        name = "All",
                        isSelected = state.selectedType == null,
                        onClick = { viewModel.onTypeSelected(null) }
                    )
                    types.forEach { type ->
                        TypeChip(
                            name = type.capitalize(),
                            isSelected = state.selectedType == type,
                            onClick = { viewModel.onTypeSelected(type) }
                        )
                    }
                }
            }
        }

        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentPrimary)
                }
            }
        } else if (state.results.isEmpty() && state.query.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No neural matches found.", color = TextSecondary)
                }
            }
        } else {
            items(state.results) { memory ->
                SearchResultItem(memory)
            }
        }
    }
}

@Composable
fun SearchInput(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search your second brain...", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentPrimary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
    }
}

@Composable
fun TypeChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AccentPrimary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.02f),
        border = BorderStroke(1.dp, if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = Typography.labelSmall,
            color = if (isSelected) Color.White else TextSecondary
        )
    }
}

@Composable
fun SearchResultItem(memory: LocalMemory) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when(memory.type) {
                            "task" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                            "goal" -> Color(0xFFFF9800).copy(alpha = 0.1f)
                            "preference" -> Color(0xFF2196F3).copy(alpha = 0.1f)
                            else -> Color.White.copy(alpha = 0.05f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(memory.type) {
                        "task" -> Icons.Default.CheckCircle
                        "goal" -> Icons.Default.Flag
                        "preference" -> Icons.Default.Favorite
                        else -> Icons.Default.History
                    },
                    contentDescription = null,
                    tint = when(memory.type) {
                        "task" -> Color(0xFF4CAF50)
                        "goal" -> Color(0xFFFF9800)
                        "preference" -> Color(0xFF2196F3)
                        else -> Color.White.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    memory.text,
                    style = Typography.bodyMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        memory.type.capitalize(),
                        style = Typography.labelSmall,
                        color = AccentPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "•  ${java.time.Instant.ofEpochMilli(memory.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}",
                        style = Typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
