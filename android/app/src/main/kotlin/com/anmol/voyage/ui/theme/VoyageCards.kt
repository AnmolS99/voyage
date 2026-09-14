package com.anmol.voyage.ui.theme

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The card color shared by the list screens.
 *
 * Material's default card container is `surfaceContainerLow`, which in this
 * theme is a shade of the same warm paper the page background is — the cards
 * disappeared into it. `surface` is the app's card color on both platforms:
 * white on the light page, the dark card grey on the dark one, exactly what
 * iOS's `AppColors.cardBackground` returns.
 */
@Composable
fun voyageCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface,
)

/** A soft shadow, standing in for iOS's `.shadow(radius: 8, y: 2)`. */
@Composable
fun voyageCardElevation() = CardDefaults.cardElevation(defaultElevation = 2.dp)
