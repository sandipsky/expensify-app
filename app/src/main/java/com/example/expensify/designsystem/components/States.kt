package com.example.expensify.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.expensify.R
import com.example.expensify.designsystem.icons.AppIcons

/** While the session or a page is still unknown. */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** A read that failed, with the sentence for it and a way to try again. */
@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyState(
        icon = AppIcons.Unknown,
        title = stringResource(R.string.something_went_wrong),
        message = message,
        modifier = modifier,
        action = onRetry?.let { { Button(onClick = it) { Text(stringResource(R.string.action_retry)) } } },
    )
}
