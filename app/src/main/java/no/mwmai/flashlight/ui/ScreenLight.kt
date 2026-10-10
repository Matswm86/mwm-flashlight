package no.mwmai.flashlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The whole screen as a white light at full brightness. Tap anywhere to close. */
@Composable
fun ScreenLight(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semantics { contentDescription = "Screen light" },
    ) {
        Text(
            "TAP TO CLOSE",
            fontFamily = Mono,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            color = Color(0xFFB8BCC2),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 24.dp),
        )
    }
}
