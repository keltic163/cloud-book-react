package com.krendstudio.cloudledger.ui.theme

// Light mode: matches web theme
// Dark mode: Midnight Deep

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),       
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF0D9488),     
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFE11D48),      
    background = Color(0xFFEFE9DD),    
    onBackground = Color(0xFF0F172A),  
    surface = Color(0xFFFBF8F2),       
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF5F2EB), 
    onSurfaceVariant = Color(0xFF64748B), 
    outline = Color(0xFFD7CFBF),       
    outlineVariant = Color(0xFFD7CFBF), 
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),       
    onPrimary = Color(0xFF0F172A),
    secondary = Color(0xFF2DD4BF),     
    onSecondary = Color(0xFF0F172A),
    tertiary = Color(0xFFFB7185),      
    background = Color(0xFF020617),    
    onBackground = Color(0xFFEFE9DD),  
    surface = Color(0xFF0F172A),       
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B), 
    onSurfaceVariant = Color(0xFF94A3B8), 
    outline = Color(0xFF334155),       
    outlineVariant = Color(0xFF1F2937), 
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF)
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
)

@Composable
fun CloudLedgerTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        shapes = AppShapes,
        content = content
    )
}



