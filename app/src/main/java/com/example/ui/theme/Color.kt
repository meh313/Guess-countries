package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Geography & Explorer Vibrant Palette
val OceanBluePrimary = Color(0xFF1E56A0)
val OceanBlueSecondary = Color(0xFF163172)
val GoldAccent = Color(0xFFF6C90E)
val TerraCotta = Color(0xFFD96B27)
val EmeraldGreen = Color(0xFF2E7D32)
val OceanSurface = Color(0xFFF4F7FC)

// Dark Theme Colors
val DarkNavyBackground = Color(0xFF0F172A)
val DarkNavySurface = Color(0xFF1E293B)
val DarkNavyCard = Color(0xFF334155)
val DarkGold = Color(0xFFFFD54F)

// The dark theme's primary: the brand blue is 2.5:1 on navy, unreadable as text, so text buttons, "Score:" and the
// icons use this lighter blue (9:1 on the background) with navy text on top of it.
val DarkPrimary = Color(0xFF9CC2FF)
val DarkOnPrimary = Color(0xFF0B2A52)

// Continent Colors for badges & filters
val AfricaColor = Color(0xFFB0611A)      // Warm Amber / Sunset (darkened so white text is >= 4.5:1)
val AmericasColor = Color(0xFF1E864A)    // Emerald Rainforest
val AsiaColor = Color(0xFFD04436)        // Crimson / Silk
val EuropeColor = Color(0xFF277BB1)      // Ocean Royal Blue
val OceaniaColor = Color(0xFF12856F)     // Lagoon Turquoise
val AntarcticaColor = Color(0xFF8E44AD)  // Glacial Violet / Ice

// Accents for white text on coloured pills and buttons (each is >= 4.5:1 against white)
val StreakColor = AfricaColor
val NeedsPracticeColor = AsiaColor
val MasteredColor = AmericasColor

// The "You picked" and "Correct" labels of a miss, on the primary container: the answer list's red and green are too
// light for the light container and far too dark for the dark one, so each container gets a pair of its own (both at
// least 4.5:1 there, ThemeContrastTest).
val MissPickedOnLight = Color(0xFFB3261E)
val MissCorrectOnLight = Color(0xFF146C2E)
val MissPickedOnDark = Color(0xFFFFB4AB)
val MissCorrectOnDark = Color(0xFFA5D6A7)

// Container roles, so Material does not fall back to its default lavender palette
val LightPrimaryContainer = Color(0xFFD7E5F8)
val LightOnPrimaryContainer = Color(0xFF0B2A52)
val LightSecondaryContainer = Color(0xFFDDE4F4)
val LightOnSecondaryContainer = Color(0xFF111F4D)
val LightTertiaryContainer = Color(0xFFFBDDCC)
val LightOnTertiaryContainer = Color(0xFF4A1F05)
// The five surface-container tones (dialogs, switches, menus and sheets pick among them); unset ones fall back to lavender.
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF0F4FA)
val LightSurfaceContainer = Color(0xFFEAF0F9)
val LightSurfaceContainerHigh = Color(0xFFE3EAF5)
val LightSurfaceContainerHighest = Color(0xFFDCE4F1)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFD9E0EB)

val DarkPrimaryContainer = Color(0xFF1D3F73)
val DarkOnPrimaryContainer = Color(0xFFD7E5F8)
val DarkSecondaryContainer = Color(0xFF5C4B0B)
val DarkOnSecondaryContainer = Color(0xFFFFE9A6)
val DarkTertiaryContainer = Color(0xFF1F4D2C)
val DarkOnTertiaryContainer = Color(0xFFCBF1D5)
val DarkSurfaceContainerLowest = Color(0xFF0B1222)
val DarkSurfaceContainerLow = Color(0xFF121A2C)
val DarkSurfaceContainer = Color(0xFF172033)
val DarkSurfaceContainerHigh = Color(0xFF1E293B)
val DarkSurfaceContainerHighest = Color(0xFF263246)
val DarkSurfaceBright = Color(0xFF334155)
val DarkSurfaceDim = Color(0xFF0F172A)
