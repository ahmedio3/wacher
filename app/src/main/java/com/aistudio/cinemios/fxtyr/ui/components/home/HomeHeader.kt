package com.aistudio.cinemios.fxtyr.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.ui.components.seeplus.ProfileAvatar

@Composable
fun HomeHeader(
    photoUrl: String? = null,
    isSubscribed: Boolean = false,
    onAvatarClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "مرحباً بك",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        // Uses the same avatar treatment as Settings (animated seePlus ring,
        // inner gap, and scaled premium badge). In RTL this sits at top-left.
        ProfileAvatar(
            photoUrl = photoUrl,
            isPremium = isSubscribed,
            showOnlineDot = false,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onAvatarClick),
            size = 42.dp
        )
    }
}
