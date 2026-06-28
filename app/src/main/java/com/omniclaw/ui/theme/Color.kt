package com.omniclaw.ui.theme

import androidx.compose.ui.graphics.Color

val OmniClawBgDark = Color(0xFF0A0E1A)
val OmniClawSurfaceDark = Color(0xFF141A2E)
val OmniClawSurfaceElevated = Color(0xFF1C2340)

val OmniClawBgLight = Color(0xFFF8F9FC)
val OmniClawSurfaceLight = Color(0xFFFFFFFF)
val OmniClawSurfaceLightElevated = Color(0xFFF1F3F8)

val OmniClawTextPrimary = Color(0xFFE8ECF4)
val OmniClawTextSecondary = Color(0xFF9CA3B2)
val OmniClawTextTertiary = Color(0xFF6B7280)

val OmniClawTextPrimaryLight = Color(0xFF1F2937)
val OmniClawTextSecondaryLight = Color(0xFF6B7280)
val OmniClawTextTertiaryLight = Color(0xFF9CA3B2)

val OmniClawPrimary = Color(0xFF6C63FF)
val OmniClawPrimaryDark = Color(0xFF5046E5)
val OmniClawPrimaryLight = Color(0xFF8B85FF)
val OmniClawPrimaryGlow = Color(0x406C63FF)

val OmniClawSecondary = Color(0xFF38BDF8)
val OmniClawSecondaryDark = Color(0xFF0EA5E9)
val OmniClawSecondaryLight = Color(0xFF7DD3FC)
val OmniClawSecondaryGlow = Color(0x4038BDF8)

val OmniClawTertiary = Color(0xFFD946EF)
val OmniClawTertiaryDark = Color(0xFFC026D3)
val OmniClawTertiaryLight = Color(0xFFE879F9)
val OmniClawTertiaryGlow = Color(0x40D946EF)

val OmniClawGlassBorder = Color(0xFF2E3440)
val OmniClawGlassBorderLight = Color(0xFFD1D5DB)
val OmniClawGlassOverlay = Color(0x1AFFFFFF)
val OmniClawGlassOverlayDeep = Color(0x0DFFFFFF)
val OmniClawGlassOverlayPressed = Color(0x26FFFFFF)
val OmniClawGlassShadow = Color(0x40000000)

val OmniClawObsidianBase = Color(0xFF06080F)
val OmniClawObsidianSurface = Color(0xFF0A0E1A)
val OmniClawObsidianElevated = Color(0xFF0F1423)

// Light-mode glass tokens. Dark mode's glass effect is a white-alpha overlay on a near-black
// background; that same white overlay is invisible (and the dark border is wrong) on a white
// background, which is why light mode looked broken. These use a primary-tinted overlay instead
// so cards still read as "glass" against a light surface, with a light-appropriate border/shadow.
val OmniClawGlassOverlayLight = Color(0x0F6C63FF)
val OmniClawGlassOverlayDeepLight = Color(0x086C63FF)
val OmniClawGlassOverlayPressedLight = Color(0x1F6C63FF)
val OmniClawGlassShadowLight = Color(0x26000000)

val OmniClawSuccess = Color(0xFF22C55E)
val OmniClawWarning = Color(0xFFF59E0B)
val OmniClawError = Color(0xFFEF4444)

val OmniClawAccent = OmniClawSecondary
val OmniClawAccentSecondary = OmniClawPrimary
