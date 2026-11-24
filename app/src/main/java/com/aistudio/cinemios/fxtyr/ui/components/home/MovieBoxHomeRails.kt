package com.aistudio.cinemios.fxtyr.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.data.remote.moviebox.models.HomeSection
import com.aistudio.cinemios.fxtyr.data.remote.moviebox.models.SearchResult
import com.aistudio.cinemios.fxtyr.ui.components.IosIcons
import com.aistudio.cinemios.fxtyr.ui.components.SkeletonItem
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExplorePosterCard
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import com.aistudio.cinemios.fxtyr.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.cinemios.fxtyr.utils.isLatinText

/**
 * Compact "powered by MovieBox" label shown once above the curated home rails.
 * Keeps the TMDB home content visually distinct from the MovieBox catalogue.
 */
@Composable
fun MovieBoxRailsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(9.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.MovieBox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "مختارات MovieBox",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "محتوى قابل للمشاهدة والتحميل مباشرة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

/** One horizontal MovieBox home rail (title + poster cards). */
@Composable
fun MovieBoxHomeRail(
    section: HomeSection,
    onItemClick: (SearchResult) -> Unit
) {
    if (section.items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = section.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = if (isLatinText(section.title)) JetBrainsMonoFontFamily else null
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(section.items, key = { it.subjectId }) { item ->
                Box(modifier = Modifier.width(118.dp)) {
                    ExplorePosterCard(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}

/**
 * "More like this" rail used at the bottom of the MovieBox detail screen.
 * Caller already applies horizontal padding, so the rail starts at 0.
 */
@Composable
fun MovieBoxSimilarRail(
    items: List<SearchResult>,
    onItemClick: (SearchResult) -> Unit
) {
    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = IosIcons.Sparkles,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = "عروض مشابهة",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.subjectId }) { item ->
                Box(modifier = Modifier.width(112.dp)) {
                    ExplorePosterCard(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}

/** Lightweight shimmer shown while the curated home rails are loading. */
@Composable
fun MovieBoxHomeRailsSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonItem(width = 28.dp, height = 28.dp, cornerRadius = 9.dp)
            Column {
                SkeletonItem(width = 150.dp, height = 16.dp, cornerRadius = 6.dp)
                Spacer(modifier = Modifier.height(4.dp))
                SkeletonItem(width = 210.dp, height = 10.dp, cornerRadius = 4.dp)
            }
        }

        repeat(2) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                items(5) {
                    SkeletonItem(width = 118.dp, height = 177.dp, cornerRadius = 14.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "جارٍ تحميل مختارات MovieBox…",
            modifier = Modifier.padding(horizontal = 20.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
