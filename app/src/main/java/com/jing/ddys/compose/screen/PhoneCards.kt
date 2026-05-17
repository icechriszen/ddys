package com.jing.ddys.compose.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jing.ddys.repository.VideoCardInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneVideoPosterCard(
    video: VideoCardInfo,
    modifier: Modifier = Modifier,
    width: Dp = 132.dp,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .width(width)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 1.dp,
            shadowElevation = 1.dp
        ) {
            AsyncImage(
                model = video.imageUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!video.subTitle.isNullOrBlank()) {
            Text(
                text = video.subTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
