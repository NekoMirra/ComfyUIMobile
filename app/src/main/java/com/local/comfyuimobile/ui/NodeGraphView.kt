package com.local.comfyuimobile.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.local.comfyuimobile.model.NodeMode
import com.local.comfyuimobile.model.WorkflowLink
import com.local.comfyuimobile.model.WorkflowNode
import kotlin.math.min
import kotlin.math.roundToInt

/** 节点在前端缺失尺寸时使用的兜底宽高（画布坐标单位）。 */
private const val FALLBACK_NODE_WIDTH = 180f
private const val FALLBACK_NODE_HEIGHT = 72f

/** 用户可缩放到的最小比例：再小就没有可读性了。 */
private const val USER_MIN_SCALE = 0.2f
private const val USER_MAX_SCALE = 3f

/** 自动适配时允许的最小比例，保证再大的工作流也能整体看见。 */
private const val FIT_MIN_SCALE = 0.05f

/** 低于该比例时只画色块，不再绘制标题与类型文本。 */
private const val DETAIL_SCALE = 0.45f

private const val FIT_PADDING = 32f
private const val NODE_CORNER = 10f
private const val NODE_PADDING = 8f
private const val PROGRESS_BAR_HEIGHT = 5f

/** 徽标出现所需的最小节点高度（屏幕像素）。 */
private const val BADGE_HOST_MIN_HEIGHT = 20f

/** 徽标与节点边缘的间距（屏幕像素）。 */
private const val BADGE_HOST_PADDING = 3f

private val NodeTitleStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
private val NodeTypeStyle = TextStyle(fontSize = 10.sp)

private data class NodeBox(
    val node: WorkflowNode,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

private data class NodeGraphColors(
    val nodeFill: Color,
    val nodeBorder: Color,
    val selectedBorder: Color,
    val executingBorder: Color,
    val titleText: Color,
    val typeText: Color,
    val mutedText: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val linkPalette: List<Color>,
)

/**
 * 工作流节点图。
 *
 * 只负责展示与交互，不依赖 ViewModel：调用方把节点、连线、选中项、执行进度传进来，
 * 点击节点通过 [onSelectNode] 回调。
 *
 * @param nodes 节点列表，位置与尺寸来自前端工作流。
 * @param links 连线列表。
 * @param selectedNodeId 当前选中的节点 id。
 * @param executingNodeIds 正在执行的节点 id。
 * @param nodeProgress 节点执行进度（0f..1f），缺失时画不确定态进度条。
 * @param nodeModes 各节点的执行模式，静音 / 绕过的节点会被淡化并加上角标。
 * @param onSelectNode 点击节点时的回调。
 */
@Composable
fun NodeGraphView(
    nodes: List<WorkflowNode>,
    links: List<WorkflowLink>,
    selectedNodeId: String?,
    executingNodeIds: Set<String>,
    nodeProgress: Map<String, Float>,
    onSelectNode: (String) -> Unit,
    modifier: Modifier = Modifier,
    nodeModes: Map<String, NodeMode> = emptyMap(),
) {
    val glass = glasmioColors()
    val app = glasmioAppColors()
    val colors = remember(glass, app) {
        NodeGraphColors(
            nodeFill = glass.elevatedBackground,
            nodeBorder = glass.separator,
            selectedBorder = glass.accent,
            executingBorder = app.success,
            titleText = glass.content,
            typeText = glass.secondaryContent,
            mutedText = glass.tertiaryContent,
            progressTrack = glass.separator,
            progressFill = glass.accent,
            // 连线按节点类型取色：同一类型始终得到同一种颜色，颜色全部来自 Glasmio 调色板。
            // Glasmio 是单色调色板，所以用主色的透明度梯度区分节点类型。
            linkPalette = listOf(
                glass.accent,
                glass.accent.copy(alpha = 0.78f),
                glass.accent.copy(alpha = 0.6f),
                glass.accent.copy(alpha = 0.44f),
                glass.secondaryContent,
                glass.tertiaryContent,
            ),
        )
    }
    val textMeasurer = rememberTextMeasurer()
    val boxes = remember(nodes) { layoutNodes(nodes) }
    val byId = remember(boxes) { boxes.associateBy { it.node.id } }

    // 缩放与平移保存在状态对象里：画布绘制阶段读取它们只触发重绘，
    // 徽标图层也只在绘制阶段读取，因此平移缩放不会引起节点重组。
    val scaleState = remember { mutableFloatStateOf(1f) }
    val offsetState = remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    // 手势回调里必须读到最新的节点与回调，否则拖拽会用旧值跳回去。
    val latestBoxes by rememberUpdatedState(boxes)
    val latestOnSelectNode by rememberUpdatedState(onSelectNode)

    val fitKey = remember(nodes) {
        nodes.joinToString("|") { "${it.id}:${it.posX}:${it.posY}:${it.sizeX}:${it.sizeY}" }
    }
    LaunchedEffect(fitKey, viewport) {
        if (viewport.width <= 0 || viewport.height <= 0) return@LaunchedEffect
        val fit = fitTransform(boxes, viewport.width.toFloat(), viewport.height.toFloat())
        scaleState.floatValue = fit.first
        offsetState.value = fit.second
    }

    // 只有「执行中且还没有进度值」的节点才需要不确定态的滑动段。
    // 空闲时不启动无限动画：否则画布会一直占用帧时钟，设备上空转耗电，
    // 在没有真实图形管线的测试环境里也会让帧时钟永远无法进入空闲。
    val needsIndeterminate = boxes.any { box ->
        executingNodeIds.contains(box.node.id) && nodeProgress[box.node.id] == null
    }
    val indeterminatePhase = if (needsIndeterminate) {
        val indeterminate = rememberInfiniteTransition(label = "node-graph-progress")
        val phase by indeterminate.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
            label = "node-graph-indeterminate",
        )
        phase
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val currentScale = if (scaleState.floatValue > 0f) scaleState.floatValue else 1f
                    val currentOffset = offsetState.value
                    val graphX = (tap.x - currentOffset.x) / currentScale
                    val graphY = (tap.y - currentOffset.y) / currentScale
                    val hit = latestBoxes.firstOrNull { box ->
                        graphX >= box.left && graphX <= box.right && graphY >= box.top && graphY <= box.bottom
                    }
                    if (hit != null) latestOnSelectNode(hit.node.id)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val current = if (scaleState.floatValue > 0f) scaleState.floatValue else 1f
                    val currentOffset = offsetState.value
                    val next = (current * zoom).coerceIn(USER_MIN_SCALE, USER_MAX_SCALE)
                    val graphX = (centroid.x - currentOffset.x) / current
                    val graphY = (centroid.y - currentOffset.y) / current
                    scaleState.floatValue = next
                    offsetState.value = Offset(
                        centroid.x - graphX * next + pan.x,
                        centroid.y - graphY * next + pan.y,
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasScale = if (scaleState.floatValue > 0f) scaleState.floatValue else 1f
            val canvasOffset = offsetState.value
            val toScreenX: (Float) -> Float = { value -> value * canvasScale + canvasOffset.x }
            val toScreenY: (Float) -> Float = { value -> value * canvasScale + canvasOffset.y }

            // 连线画在节点下面。
            links.forEach { link ->
                val origin = byId[link.originId] ?: return@forEach
                val target = byId[link.targetId] ?: return@forEach
                val start = Offset(toScreenX(origin.right), toScreenY((origin.top + origin.bottom) / 2f))
                val end = Offset(toScreenX(target.left), toScreenY((target.top + target.bottom) / 2f))
                val controlOffset = ((end.x - start.x) / 2f).coerceAtLeast(24f)
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(
                        start.x + controlOffset,
                        start.y,
                        end.x - controlOffset,
                        end.y,
                        end.x,
                        end.y,
                    )
                }
                drawPath(
                    path = path,
                    color = colors.linkColor(origin.node.type),
                    style = Stroke(width = if (canvasScale >= DETAIL_SCALE) 2f else 1f),
                )
            }

            boxes.forEach { box ->
                val node = box.node
                val left = toScreenX(box.left)
                val top = toScreenY(box.top)
                val width = box.width * canvasScale
                val height = box.height * canvasScale
                if (width <= 0f || height <= 0f) return@forEach
                val executing = executingNodeIds.contains(node.id)
                val selected = selectedNodeId == node.id
                val mode = nodeModes[node.id] ?: NodeMode.ALWAYS
                val dimmed = mode != NodeMode.ALWAYS
                val accent = colors.linkColor(node.type)
                val radius = CornerRadius(
                    NODE_CORNER.coerceAtMost(min(width, height) / 3f),
                    NODE_CORNER.coerceAtMost(min(width, height) / 3f),
                )

                drawRoundRect(
                    color = if (dimmed) colors.nodeFill.copy(alpha = 0.45f) else colors.nodeFill,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = radius,
                )
                if (executing) {
                    drawRoundRect(
                        color = colors.executingBorder,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = radius,
                        style = Stroke(width = 4f),
                    )
                }
                if (selected) {
                    drawRoundRect(
                        color = colors.selectedBorder,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = radius,
                        style = Stroke(width = 3f),
                    )
                }
                if (!executing && !selected) {
                    drawRoundRect(
                        color = colors.nodeBorder,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = radius,
                        style = Stroke(width = 1.5f),
                    )
                }

                if (canvasScale >= DETAIL_SCALE) {
                    val title = textMeasurer.measure(node.title.ifBlank { node.type }, NodeTitleStyle)
                    drawText(
                        textLayoutResult = title,
                        color = if (dimmed) colors.mutedText else colors.titleText,
                        topLeft = Offset(left + NODE_PADDING, top + NODE_PADDING),
                    )
                    if (height >= 46f && node.type.isNotBlank() && node.type != node.title) {
                        val type = textMeasurer.measure(node.type, NodeTypeStyle)
                        drawText(
                            textLayoutResult = type,
                            color = if (dimmed) colors.mutedText else colors.typeText,
                            topLeft = Offset(left + NODE_PADDING, top + NODE_PADDING + title.size.height + 2f),
                        )
                    }
                } else {
                    // 缩得太小时只留一个色块与标题首字，避免糊成一团。
                    drawRect(
                        color = accent,
                        topLeft = Offset(left, top),
                        size = Size(width.coerceAtMost(18f), height.coerceAtMost(18f)),
                    )
                    val initial = node.title.ifBlank { node.type }.take(1)
                    if (initial.isNotBlank() && width >= 14f && height >= 14f) {
                        val initialLayout = textMeasurer.measure(initial, NodeTitleStyle)
                        drawText(
                            textLayoutResult = initialLayout,
                            color = if (dimmed) colors.mutedText else colors.titleText,
                            topLeft = Offset(
                                left + width / 2f - initialLayout.size.width / 2f,
                                top + height / 2f - initialLayout.size.height / 2f,
                            ),
                        )
                    }
                }

                if (executing) {
                    val barTop = top + height + 4f
                    val barWidth = width
                    drawRoundRect(
                        color = colors.progressTrack,
                        topLeft = Offset(left, barTop),
                        size = Size(barWidth, PROGRESS_BAR_HEIGHT),
                        cornerRadius = CornerRadius(PROGRESS_BAR_HEIGHT / 2f, PROGRESS_BAR_HEIGHT / 2f),
                    )
                    val progress = nodeProgress[node.id]
                    if (progress != null) {
                        val ratio = progress.coerceIn(0f, 1f)
                        if (ratio > 0f) {
                            drawRoundRect(
                                color = colors.progressFill,
                                topLeft = Offset(left, barTop),
                                size = Size(barWidth * ratio, PROGRESS_BAR_HEIGHT),
                                cornerRadius = CornerRadius(PROGRESS_BAR_HEIGHT / 2f, PROGRESS_BAR_HEIGHT / 2f),
                            )
                        }
                    } else {
                        // 没有进度值：画不确定态的一段滑动条。
                        val segment = barWidth * 0.3f
                        val travel = (barWidth + segment) * indeterminatePhase - segment
                        drawRoundRect(
                            color = colors.progressFill,
                            topLeft = Offset(left + travel, barTop),
                            size = Size(segment, PROGRESS_BAR_HEIGHT),
                            cornerRadius = CornerRadius(PROGRESS_BAR_HEIGHT / 2f, PROGRESS_BAR_HEIGHT / 2f),
                        )
                    }
                }
            }
        }

        // 静音 / 绕过徽标用真正的 GlasmioBadge 组件绘制：徽标不采样背景，
        // 因此放在画布之上依然安全，也不会为每个节点采样一次背景。
        NodeModeBadgeLayer(
            boxes = boxes,
            nodeModes = nodeModes,
            scale = scaleState,
            offset = offsetState,
            mutedColor = glass.tertiaryContent,
            badgeColor = app.danger,
        )
    }
}

/**
 * 在画布之上叠加静音 / 绕过徽标。
 *
 * 徽标的位置在布局阶段读取画布状态，因此平移缩放只会重新布局这一层，
 * 不会让节点画布重组。
 */
@Composable
private fun NodeModeBadgeLayer(
    boxes: List<NodeBox>,
    nodeModes: Map<String, NodeMode>,
    scale: FloatState,
    offset: State<Offset>,
    mutedColor: Color,
    badgeColor: Color,
) {
    if (nodeModes.isEmpty()) return
    val muted = remember(boxes, nodeModes) {
        boxes.mapNotNull { box ->
            val mode = nodeModes[box.node.id] ?: return@mapNotNull null
            if (mode == NodeMode.ALWAYS) return@mapNotNull null
            box to mode
        }
    }
    if (muted.isEmpty()) return

    val canvasScale = scale.floatValue
    val canvasOffset = offset.value

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            muted.forEach { (box, mode) ->
                key(box.node.id) {
                    GlasmioBadge(
                        label = if (mode == NodeMode.MUTE) "静" else "绕",
                        color = if (mode == NodeMode.MUTE) mutedColor else badgeColor,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        // 徽标按自身尺寸测量，再按节点在画布上的绝对位置摆放。
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val box = muted[index].first
                val nodeHeight = box.height * canvasScale
                // 缩得太小或节点太矮时不绘制徽标，避免遮挡节点内容。
                if (canvasScale < DETAIL_SCALE || nodeHeight < BADGE_HOST_MIN_HEIGHT) return@forEachIndexed
                val nodeRight = box.right * canvasScale + canvasOffset.x
                val nodeTop = box.top * canvasScale + canvasOffset.y
                placeable.placeRelative(
                    x = (nodeRight - BADGE_HOST_PADDING - placeable.width).roundToInt(),
                    y = nodeTop.roundToInt(),
                )
            }
        }
    }
}

/** 同一节点类型始终得到同一种颜色，颜色全部来自 Glasmio 主题，不写死色值。 */
private fun NodeGraphColors.linkColor(type: String): Color {
    if (linkPalette.isEmpty()) return progressFill
    if (type.isEmpty()) return linkPalette.last()
    var hash = 0
    type.forEach { char -> hash = hash * 31 + char.code }
    val index = ((hash % linkPalette.size) + linkPalette.size) % linkPalette.size
    return linkPalette[index]
}

private fun layoutNodes(nodes: List<WorkflowNode>): List<NodeBox> = nodes.map { node ->
    val width = if (node.sizeX > 1f) node.sizeX else FALLBACK_NODE_WIDTH
    val height = if (node.sizeY > 1f) node.sizeY else FALLBACK_NODE_HEIGHT
    NodeBox(
        node = node,
        left = node.posX,
        top = node.posY,
        right = node.posX + width,
        bottom = node.posY + height,
    )
}

/** 把全部节点缩放进视口，返回比例与偏移。 */
private fun fitTransform(boxes: List<NodeBox>, width: Float, height: Float): Pair<Float, Offset> {
    if (boxes.isEmpty()) return 1f to Offset.Zero
    val minX = boxes.minOf { it.left }
    val maxX = boxes.maxOf { it.right }
    val minY = boxes.minOf { it.top }
    val maxY = boxes.maxOf { it.bottom }
    val contentWidth = (maxX - minX).coerceAtLeast(1f)
    val contentHeight = (maxY - minY).coerceAtLeast(1f)
    val usableWidth = (width - FIT_PADDING * 2f).coerceAtLeast(1f)
    val usableHeight = (height - FIT_PADDING * 2f).coerceAtLeast(1f)
    val fitScale = min(usableWidth / contentWidth, usableHeight / contentHeight)
        .coerceIn(FIT_MIN_SCALE, 1.5f)
    val offsetX = (width - contentWidth * fitScale) / 2f - minX * fitScale
    val offsetY = (height - contentHeight * fitScale) / 2f - minY * fitScale
    return fitScale to Offset(offsetX, offsetY)
}
