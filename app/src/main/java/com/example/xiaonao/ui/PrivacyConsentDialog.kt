package com.example.xiaonao.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.example.xiaonao.R

/**
 * 首次启动的隐私政策同意弹窗（变现改造 v1.25）。
 *
 * 合规要求：用户点「同意」之前，不允许初始化/启动广告 SDK；
 * 点「拒绝」时广告功能保持停用，但 App 其余功能完全可用。
 */
@Composable
fun PrivacyConsentDialog(
    onAgree: () -> Unit,
    onReject: () -> Unit,
    onViewPolicy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* 必须做出选择，不允许点外部关闭 */ },
        title = { Text("隐私政策") },
        text = {
            Column {
                Text(stringResource(R.string.privacy_message))
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onViewPolicy) {
                    Text(stringResource(R.string.privacy_view), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) { Text("同意并继续") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("拒绝") }
        },
    )
}
