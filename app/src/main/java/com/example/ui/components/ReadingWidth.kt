package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The widest a column of text and controls gets: on a tablet the content sits centred instead of stretching. */
val ReadingWidth: Dp = 600.dp

/** Fills the width up to [ReadingWidth]; the parent centres it (a `Box` with `Alignment.TopCenter`, or a centred `Column`). */
fun Modifier.readingWidth(): Modifier = this.widthIn(max = ReadingWidth).fillMaxWidth()
