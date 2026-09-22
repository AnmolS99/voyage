package com.anmol.voyage.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The widest a column of cards or text grows, from Material's medium window
 * size class up: 640 dp, where a line of body text stops being comfortable to
 * read. The globe and the map are never held to it — they are full-bleed on
 * every window, a foldable's inner screen included.
 */
val ReadableWidth = 640.dp

/**
 * Fills the available width up to [ReadableWidth], centered in anything wider,
 * so the list tabs read as a column on a tablet or an unfolded foldable instead
 * of stretching cards edge to edge. On a phone it is just `fillMaxWidth()`.
 */
fun Modifier.readableWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = ReadableWidth)
    .fillMaxWidth()
