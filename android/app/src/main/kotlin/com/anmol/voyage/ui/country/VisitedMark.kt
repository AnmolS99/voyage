package com.anmol.voyage.ui.country

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * The mark for a visited country: a disc in the globe's visited yellow with a
 * dark check — iOS `VisitedCheckmark`. Yellow alone is lost on a light
 * background, so the check carries the shape, as the dock card's yellow Visited
 * button carries black text.
 */
@Composable
internal fun VisitedMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(VoyagePalette.visited, CircleShape),
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(size * 0.72f),
        )
    }
}
