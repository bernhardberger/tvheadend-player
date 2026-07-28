package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

private val SyntheticSky = Color(0xFF7EC8E3)
private val SyntheticSun = Color(0xFFFFF0A6)
private val SyntheticField = Color(0xFF356B48)
private val SyntheticShadow = Color(0xFF101820)

@Composable
internal fun DebugVideoBackdrop(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .testTag("debug-video-backdrop"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(SyntheticSun, SyntheticSky, SyntheticShadow),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.88f),
                radius = size.minDimension * 0.105f,
                center = Offset(size.width * 0.78f, size.height * 0.25f),
            )
            drawRect(
                color = SyntheticField,
                topLeft = Offset(0f, size.height * 0.58f),
                size = Size(size.width, size.height * 0.42f),
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent),
                ),
                topLeft = Offset(0f, size.height * 0.69f),
                size = Size(size.width * 0.58f, size.height * 0.16f),
            )
            repeat(9) { index ->
                val x = size.width * index / 8f
                drawLine(
                    color = Color.White.copy(alpha = 0.18f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2f,
                )
            }
            repeat(6) { index ->
                val y = size.height * index / 5f
                drawLine(
                    color = Color.Black.copy(alpha = 0.16f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f,
                )
            }
        }
        Text(
            text = "SYNTHETIC DEBUG FRAME",
            color = Color.White.copy(alpha = 0.74f),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
