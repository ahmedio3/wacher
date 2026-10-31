package com.aistudio.cinemios.fxtyr.ui.components.seeplus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteSuccess

object SeePlusColors {
    val Gold = Color(0xFFE7C27D)
    val Champagne = Color(0xFFF9EBCB)
    val DeepGold = Color(0xFFB9853D)
    val Espresso = Color(0xFF1B1511)
    val EspressoSoft = Color(0xFF2B211A)
    val Cream = Color(0xFFFFF8EC)
}

@Composable
fun SeePlusWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    onDark: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "see",
            color = if (onDark) SeePlusColors.Cream else MaterialTheme.colorScheme.onBackground,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = (-0.6).sp,
            lineHeight = fontSize,
            maxLines = 1
        )
        Text(
            text = "Plus",
            color = if (onDark) SeePlusColors.Gold else SeePlusColors.DeepGold,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.6).sp,
            lineHeight = fontSize,
            maxLines = 1
        )
    }
}

@Composable
fun SeePlusChip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = "seePlus",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
fun ProfileAvatar(
    photoUrl: String?,
    isPremium: Boolean,
    showOnlineDot: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(62.dp)) {
        if (isPremium) {
            val transition = rememberInfiniteTransition(label = "seeplus_ring")
            val rotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 7000, easing = LinearEasing)
                ),
                label = "seeplus_ring_rotation"
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .rotate(rotation)
                    .background(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                SeePlusColors.Gold,
                                SeePlusColors.Champagne,
                                SeePlusColors.DeepGold,
                                SeePlusColors.Gold
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            )

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AvatarContent(photoUrl = photoUrl, iconSize = 28.dp)
            }

            PremiumBadge(
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                AvatarContent(photoUrl = photoUrl, iconSize = 28.dp)
            }

            if (showOnlineDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(PaletteSuccess)
                )
            }
        }
    }
}

@Composable
private fun AvatarContent(photoUrl: String?, iconSize: Dp) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "صورة الحساب",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun PremiumBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        SeePlusColors.Champagne,
                        SeePlusColors.Gold,
                        SeePlusColors.DeepGold
                    )
                )
            )
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.WorkspacePremium,
            contentDescription = "عضو seePlus",
            tint = SeePlusColors.Espresso,
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
fun SeePlusSettingsCard(
    isPremium: Boolean,
    planTitle: String?,
    expiryLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(SeePlusColors.EspressoSoft, SeePlusColors.Espresso)
                )
            )
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            SeePlusColors.Gold.copy(alpha = 0.75f),
                            SeePlusColors.DeepGold.copy(alpha = 0.15f)
                        )
                    )
                ),
                shape = shape
            )
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = SeePlusColors.Gold.copy(alpha = 0.07f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 36.dp, y = (-36).dp)
                .size(150.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 60.dp)
                .size(170.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SeePlusColors.Gold.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeePlusWordmark(fontSize = 24.sp, onDark = true)
                StatusPill(
                    text = if (isPremium) "PREMIUM" else "جديد",
                    active = isPremium
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isPremium) "أنت عضو seePlus" else "ارتقِ بتجربتك مع seePlus",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SeePlusColors.Cream
                )
                Text(
                    text = if (isPremium && planTitle != null) {
                        "خطة $planTitle • ساري حتى $expiryLabel"
                    } else {
                        "تحميل بلا حدود، جودة أعلى، وتجربة مشاهدة بلا إعلانات."
                    },
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = SeePlusColors.Cream.copy(alpha = 0.65f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPremium) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PaletteSuccess)
                        )
                        Text(
                            text = "عضوية نشطة",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = SeePlusColors.Cream.copy(alpha = 0.75f)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SeePlusColors.Gold.copy(alpha = 0.7f),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "إلغاء في أي وقت",
                            fontSize = 10.sp,
                            color = SeePlusColors.Cream.copy(alpha = 0.55f)
                        )
                    }
                }

                CtaPill(text = if (isPremium) "إدارة الاشتراك" else "انضم الآن")
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (active) SeePlusColors.Gold else SeePlusColors.Gold.copy(alpha = 0.14f),
        contentColor = if (active) SeePlusColors.Espresso else SeePlusColors.Gold
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun CtaPill(text: String) {
    Surface(
        shape = CircleShape,
        color = SeePlusColors.Gold,
        contentColor = SeePlusColors.Espresso
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
