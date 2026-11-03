package com.local.comfyuimobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.comfyuimobile.model.NodeMode

/**
 * 节点模式选择控件：始终执行 / 静音 / 绕过。
 *
 * 状态完全由外部传入，选择结果通过 [onModeChange] 回调，控件自身不持有状态。
 *
 * @param mode 当前模式。
 * @param onModeChange 用户选择新模式时的回调。
 * @param enabled 是否可交互。
 * @param label 控件上方的说明文字，为空时不显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeModeSelector(
    mode: NodeMode,
    onModeChange: (NodeMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = NodeMode.entries
            options.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { onModeChange(entry) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(entry.displayName) },
                )
            }
        }
    }
}
