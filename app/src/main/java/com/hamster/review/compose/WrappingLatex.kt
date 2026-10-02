package com.hamster.review.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import com.hrm.latex.parser.LatexParser
import com.hrm.latex.parser.model.LatexNode
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.font.MathFont
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LineBreakingConfig
import java.text.BreakIterator
import java.util.Locale

/** 题面和答案中的公式：优先换行，无法拆分的数学结构在块内横向滚动。 */
@Composable
internal fun WrappingLatex(
    latex: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val prepared = remember(latex) { prepareLatexForWrapping(latex) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // latex-renderer 1.3.0 的 Canvas 左右各有 0.15em 留白。
        val lineWidth = with(density) {
            (maxWidth.toPx() - fontSize.toPx() * 0.3f).coerceAtLeast(1f)
        }
        key(latex, lineWidth, fontSize) {
            val scrollState = rememberScrollState()
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .horizontalScroll(scrollState)
                ) {
                    // 在滚动容器之外取得有限宽度，避免无限宽约束禁用自动换行。
                    Latex(
                        latex = prepared,
                        config = LatexConfig(
                            fontSize = fontSize,
                            // 1.3.0 默认 OTF 模式在多行公式中会漏绘重复符号。
                            mathFont = MathFont.KaTeXTTF,
                            lineBreaking = LineBreakingConfig(enabled = true, maxWidth = lineWidth),
                            accessibilityEnabled = true
                        )
                    )
                }
                if (scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE) {
                    Text(
                        text = "左右滑动查看完整公式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 库只在 AST 节点之间换行，连续数字/文字却是单个 Text 节点。
 * 将顶层原始文字分成等价的单字符分组，使库能按实测宽度断行。
 * 不进入分数、上下标、根式、矩阵或命令参数，避免改变数学结构。
 */
internal fun prepareLatexForWrapping(latex: String): String {
    val document = try {
        LatexParser().parse(latex)
    } catch (_: Exception) {
        // 不完整的编辑输入仍交给库原有的容错渲染；滚动容器负责防止越界。
        return latex
    }
    val textNodes = document.children.filterIsInstance<LatexNode.Text>()
        .filter { node ->
            val range = node.sourceRange
            range != null && range.start >= 0 && range.end <= latex.length &&
                node.content.length > 1 &&
                latex.substring(range.start, range.end) == node.content
        }
        .sortedBy { it.sourceRange!!.start }
    if (textNodes.isEmpty()) return latex

    return buildString {
        var copiedUntil = 0
        for (node in textNodes) {
            val range = node.sourceRange!!
            if (range.start < copiedUntil) continue
            append(latex, copiedUntil, range.start)
            // 使用字符边界，保留代理对和组合字符。
            val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT)
            boundaries.setText(node.content)
            var start = boundaries.first()
            var end = boundaries.next()
            while (end != BreakIterator.DONE) {
                append('{')
                append(node.content, start, end)
                append('}')
                start = end
                end = boundaries.next()
            }
            copiedUntil = range.end
        }
        append(latex, copiedUntil, latex.length)
    }
}
