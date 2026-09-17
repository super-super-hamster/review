package com.hamster.review.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamster.review.AddQuestion
import com.hamster.review.Route
import com.hamster.review.compose.ClickItem
import com.hamster.review.compose.ItemGroup
import com.hamster.review.compose.PageColumn
import com.hamster.review.compose.rememberSharedTiltState
import com.hamster.review.data.model.QuestionType
import com.hamster.review.viewModel.ManageQuestionsViewModel

@Composable
fun ManageQuestionsScreen(
    setTopbarTitle: (String) -> Unit,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: ManageQuestionsViewModel = viewModel()
    val sharedTiltState = rememberSharedTiltState()

    setTopbarTitle("管理题目")
    BackHandler { onBack() }

    // 一次性加载：每次进入本页（含从编辑页返回）都会刷新
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    PageColumn(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        sharedTiltState = sharedTiltState
    ) {
        when {
            viewModel.loading -> {
                Text(
                    modifier = Modifier.padding(24.dp),
                    text = "加载中...",
                    fontSize = 18.sp
                )
            }

            viewModel.questions.isEmpty() -> {
                ItemGroup(titleState = sharedTiltState) {
                    Text(
                        modifier = Modifier.padding(24.dp),
                        text = "该科目暂无题目",
                        fontSize = 16.sp
                    )
                }
            }

            else -> {
                viewModel.questions.forEach { detail ->
                    val question = detail.question
                    ItemGroup(
                        titleState = sharedTiltState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ClickItem(
                            title = question.content.take(10),
                            summary = typeLabel(question.type)
                        ) {
                            onNavigate(
                                AddQuestion(
                                    subjectId = viewModel.subjectId,
                                    typeName = question.type.name,
                                    questionId = question.id
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun typeLabel(type: QuestionType): String = when (type) {
    QuestionType.SINGLE_CHOICE -> "单选题"
    QuestionType.MULTIPLE_CHOICE -> "多选题"
    QuestionType.TRUE_FALSE -> "判断题"
    QuestionType.FILL_BLANK -> "填空题"
}
