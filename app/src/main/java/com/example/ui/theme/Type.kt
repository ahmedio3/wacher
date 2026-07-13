package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.R

// Define Google Font provider with proper certificates config
val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Declare "IBM Plex Sans Arabic" font name
val ibmPlexArabicFont = GoogleFont("IBM Plex Sans Arabic")

// Map various weights to Google Fonts
val IBMPlexSansArabicFontFamily = FontFamily(
    Font(googleFont = ibmPlexArabicFont, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = ibmPlexArabicFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = ibmPlexArabicFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = ibmPlexArabicFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = ibmPlexArabicFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

// Declare "JetBrains Mono" font name (for Latin/numeric technical labels)
val jetBrainsMonoFont = GoogleFont("JetBrains Mono")

// Monospace family for whole-Latin labels (quality, file sizes, transfer rates)
val JetBrainsMonoFontFamily = FontFamily(
    Font(googleFont = jetBrainsMonoFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = jetBrainsMonoFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = jetBrainsMonoFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

// Compose M3 Typography definitions utilizing IBM Plex Sans Arabic
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    displayMedium = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    displaySmall = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = IBMPlexSansArabicFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
