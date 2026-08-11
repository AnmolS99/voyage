package com.anmol.voyage.ui.country

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.anmol.voyage.R

/**
 * A country's flag emoji, labelled for screen readers.
 *
 * Without a description TalkBack announces the raw regional-indicator pair,
 * which is not a country name in every locale — so the label spells out whose
 * flag it is.
 */
@Composable
internal fun FlagText(
    flag: String,
    country: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 30.sp,
) {
    val description = stringResource(R.string.country_flag, country)
    Text(
        text = flag,
        fontSize = fontSize,
        modifier = modifier.semantics { contentDescription = description },
    )
}
