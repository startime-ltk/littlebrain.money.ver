package com.example.xiaonao.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout

/**
 * 底部 Banner 广告位（变现改造 v1.25）。
 *
 * 行为约定：
 *  - 已买断会员（pro）：完全不占位
 *  - SDK 未就绪：不渲染，就绪后自动出现
 *  - SDK 就绪但广告加载/渲染失败：容器隐藏，不留空白
 *
 * 尺寸为固定 60dp 通栏；加载逻辑全部封装在 [AdGuard.loadBanner]。
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPro = remember { AdGuard.isPro(context) }
    var ready by remember { mutableStateOf(AdGuard.isSdkReady()) }

    DisposableEffect(Unit) {
        val cancel = AdGuard.onSdkReady { ready = true }
        onDispose { cancel() }
    }

    // 会员用户直接不渲染；SDK 未就绪时等待就绪回调
    if (isPro || !ready) return

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                AdGuard.loadBanner(container)
            }
        },
        onRelease = { view ->
            AdGuard.destroyBanner(view as FrameLayout)
        }
    )
}
