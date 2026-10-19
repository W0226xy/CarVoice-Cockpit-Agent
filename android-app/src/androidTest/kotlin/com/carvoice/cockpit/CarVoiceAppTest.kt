package com.carvoice.cockpit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CarVoiceAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun localConversationCanSendAndStartNewSession() {
        composeRule.onNodeWithText("+ 新建会话").performClick()
        composeRule.onNodeWithText("和车载助手聊点什么…").assertIsDisplayed()

        composeRule.onNodeWithTag("chat-input").performTextInput("你好，座舱")
        composeRule.onNodeWithText("发送").performClick()

        assertTrue(
            composeRule.onAllNodesWithText("你好，座舱").fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule
                .onAllNodesWithText("当前为界面演示模式，Agent 服务将在下一阶段接入。")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("+ 新建会话").performClick()
        composeRule.onNodeWithText("今天想在车里做点什么？").assertIsDisplayed()
    }

    @Test
    fun airConditionerQuickActionShowsAndClosesConfirmation() {
        composeRule.onNodeWithText("+ 新建会话").performClick()
        composeRule.onNodeWithText("打开空调").performClick()

        composeRule.onNodeWithText("确认执行车辆操作").assertIsDisplayed()
        composeRule.onNodeWithText("确认执行").performClick()

        assertTrue(
            composeRule
                .onAllNodesWithText("确认执行车辆操作")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(
            composeRule
                .onAllNodesWithText("已确认：将空调设置为 24 ℃。当前为界面演示模式，未执行车辆操作。")
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    @Test
    fun historyConversationCanBeSelected() {
        composeRule.onNodeWithText("座舱温度设置").performClick()

        composeRule
            .onNodeWithText("车内有点热，帮我设置到 24 ℃。")
            .assertIsDisplayed()
    }

    @Test
    fun knowledgeBaseCanOpenAndReturnToChat() {
        composeRule.onNodeWithText("知识库").performClick()

        composeRule.onNodeWithText("车机使用手册").assertIsDisplayed()
        composeRule.onNodeWithText("车辆控制说明").assertIsDisplayed()
        composeRule.onNodeWithText("导航与出行").assertIsDisplayed()

        composeRule.onNodeWithText("返回对话").performClick()
        composeRule
            .onNodeWithText("帮我看看今天出发前需要注意什么？")
            .assertIsDisplayed()
    }
}
