package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*

@Composable
fun ProfileScreen(authViewModel: AuthViewModel, onSignedOut: () -> Unit) {
    DashboardScaffold(title = "System") {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(AccentGradient, CircleShape)
                            .padding(2.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = BackgroundBase
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(20.dp),
                                tint = Color.White
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Flow User", style = Typography.headlineMedium, color = Color.White)
                    Text("Protocol: Active", style = Typography.labelLarge, color = Color(0xFF30D158))
                }
            }
        }

        item {
            ProfileSection(title = "Core Configuration") {
                ProfileItem(Icons.Default.Settings, "Interface", "Synchronize visual state") {}
                ProfileItem(Icons.Default.Notifications, "Frequency", "Define nudge protocol") {}
                ProfileItem(Icons.Default.Security, "Privacy", "Manage encryption keys") {}
            }
        }

        item {
            ProfileSection(title = "Protocol Termination") {
                ProfileItem(
                    icon = Icons.AutoMirrored.Filled.Logout, 
                    title = "Terminate Session", 
                    subtitle = "Safe exit to standby",
                    onClick = { 
                        authViewModel.signOut()
                        onSignedOut()
                    }
                )
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
fun ProfileSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color(0xFFA0A0B0))
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF12121A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun ProfileItem(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0A0B0))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
    }
}
