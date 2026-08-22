package com.hamster.review.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamster.review.Main
import com.hamster.review.Route
import com.hamster.review.compose.ItemGroup
import com.hamster.review.compose.PageColumn
import com.hamster.review.compose.rememberSharedTiltState
import com.hrm.latex.renderer.Latex
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.intellij.markdown.ast.getTextInNode

@Composable
fun ReviewScreen(
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit
) {
    val sharedTiltState = rememberSharedTiltState()

    BackHandler {
        setTopbarTitle("首页")
        onNavigate(Main)
    }

    PageColumn(sharedTiltState = sharedTiltState) {
        ItemGroup(titleState = sharedTiltState, modifier = Modifier.weight(1f)) {
            Text(modifier = Modifier.padding(24.dp), text = "题目名称", fontSize = 36.sp, fontWeight = FontWeight.Bold)

            Markdown(
                modifier = Modifier.padding(12.dp),
                content = "# h1\n## h2\n1. \n**bold**\n",
                components = markdownComponents(
                    codeFence = { model ->
                        val blockText = model.node.getTextInNode(model.content).toString()

                        if (blockText.startsWith("```math") || blockText.startsWith("```latex")) {
                            val formula = blockText
                                .replace(Regex("^```(math|latex)"), "")
                                .removeSuffix("```")
                                .trim()

                            Latex(latex = formula)
                        } else {
                            Text(
                                text = blockText,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color.LightGray)
                            )
                        }
                    }
                ),
                typography = markdownTypography(
                    h1 = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    h2 = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    h3 = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                )
            )
        }
    }
}