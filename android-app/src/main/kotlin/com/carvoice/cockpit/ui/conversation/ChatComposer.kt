package com.carvoice.cockpit.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme

@Composable
fun ChatComposer(
    text: String,//输入框当前显示的文字
    onTextChange: (String) -> Unit,//用户修改文字时，通知外部更新状态
    onSend: () -> Unit,//用户点击发送或按键盘发送键时，执行的操作
    isReplying: Boolean,//助手是否正在回复
    modifier: Modifier = Modifier,//允许外部设置组件的大小、间距等
) {
    //输入不为空/当前没有回复才可以发送信息
    val canSend = text.isNotBlank() && !isReplying
    Surface(//外层 Surface：输入栏的整体容器
        modifier = modifier
            .fillMaxWidth()//在父布局允许的范围内占满宽度。
            .imePadding(),//根据软键盘占用的空间添加内边距
        color = MaterialTheme.colorScheme.surface,//输入栏的背景颜色
        shape = RoundedCornerShape(30.dp),//设置四个角的圆角半径为 30.dp。
        tonalElevation = 2.dp,//通过 Material 主题的色调变化体现表面层次
    ) {
        Row(//输入栏内容的水平布局
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),//输入栏内容的内边距
            horizontalArrangement = Arrangement.spacedBy(8.dp),//子组件之间间隔 8dp
            verticalAlignment = Alignment.Bottom,//子组件沿底部对齐
        ) {
            TextButton(//语音按钮
                enabled = false,//语音功能暂未启用
                onClick = {},//预留语音输入的处理逻辑
                modifier = Modifier.defaultMinSize(minWidth = 54.dp, minHeight = 48.dp),//设置最小宽度 54dp、最小高度 48dp，保证可点击区域足够大
            ) {
                Text("语音")//显示文字"语音"
            }

            BasicTextField(//多行文本输入框，最多显示 4 行
                value = text,//当前输入的文字
                onValueChange = onTextChange,//输入内容变化时的回调
                modifier = Modifier
                    .weight(1f)//占据剩余的全部可用宽度
                    .heightIn(min = 48.dp, max = 132.dp)//设置最小高度 48dp、最大高度 132dp
                    .testTag("chat-input"),//测试标识，便于 UI 测试定位
                enabled = !isReplying,//助手回复期间禁止输入
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,//输入文字的颜色
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),//光标颜色
                minLines = 1,//最少显示 1 行
                maxLines = 4,//输入文字过多时最多显示 4 行
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),//软键盘显示"发送"按钮
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),//点击软键盘发送键时触发发送
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 12.dp),//文本输入框内容的内边距
                    ) {
                        if (text.isEmpty()) { // 未输入内容时显示占位提示
                            Text(
                                text = "和车载助手聊点什么…",//占位提示文字
                                color = MaterialTheme.colorScheme.onSurfaceVariant,//提示文字颜色
                                style = MaterialTheme.typography.bodyLarge,//提示文字样式
                            )
                        }
                        innerTextField()//显示实际的输入框内容
                    }
                },
            )

            Surface(//发送按钮
                onClick = onSend,//点击时执行发送操作
                enabled = canSend,//条件满足时才可点击
                modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp),//设置最小宽度 64dp、最小高度 48dp
                color = if (canSend) { //可发送时使用主色调，否则使用浅色
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canSend) { //文字颜色与背景色对应
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = CircleShape,//圆形形状
            ) {
                Box(
                    modifier = Modifier
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 13.dp),//按钮文字的内边距
                    contentAlignment = Alignment.Center,//内容居中显示
                ) {
                    Text("发送", style = MaterialTheme.typography.labelLarge) //显示文字"发送"
                }
            }
        }
    }
}

@Preview(widthDp = 900)
@Composable
private fun ChatComposerPreview() {
    CarVoiceCockpitTheme {
        ChatComposer(
            text = "",
            onTextChange = {},
            onSend = {},
            isReplying = false,
        )
    }
}
