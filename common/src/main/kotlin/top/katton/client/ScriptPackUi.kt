package top.katton.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import top.katton.Katton
import top.katton.engine.ScriptIssue
import top.katton.engine.ScriptIssueReporter
import top.katton.engine.ScriptReloadManager
import top.katton.pack.ScriptPackManager
import top.katton.pack.ScriptPackScope
import top.katton.pack.ScriptPackView
import top.katton.pack.RemoteScriptSignatureVerifier
import top.katton.pack.ServerPackCacheManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

object ScriptPackUi {
    private val issueReporterInstalled = AtomicBoolean(false)

    @JvmStatic
    fun installErrorReporter() {
        if (!issueReporterInstalled.compareAndSet(false, true)) {
            return
        }
        ScriptIssueReporter.addListener { issue ->
            openScriptIssueScreen(issue)
        }
    }

    @JvmStatic
    fun openScriptIssueScreen(issue: ScriptIssue) {
        val mc = Minecraft.getInstance()
        mc.execute {
            val current = mc.screen
            if (current is ScriptIssueScreen && current.issue == issue) {
                return@execute
            }
            mc.gui.setOverlayMessage(Component.literal(issue.title), false)
            mc.setScreen(ScriptIssueScreen(current, issue))
        }
    }

    @JvmStatic
    fun openInWorldScreen() {
        val mc = Minecraft.getInstance()
        if (mc.level == null) {
            return
        }
        mc.setScreen(ScriptPackManagerScreen(mc.screen))
    }

    @JvmStatic
    fun openRemoteScriptTrustScreen(serverAddress: String, packs: List<top.katton.pack.ScriptPack>, callback: (Boolean) -> Unit) {
        val mc = Minecraft.getInstance()
        val sortedPacks = packs.sortedWith(compareBy<top.katton.pack.ScriptPack> { it.manifest.name.lowercase() }.thenBy { it.manifest.id.lowercase() })
        val views = sortedPacks
                .map { pack ->
                    ScriptPackView(
                        syncId = pack.syncId,
                        scope = ScriptPackScope.SERVER_CACHE,
                        kind = pack.kind,
                        id = pack.manifest.id,
                        name = pack.manifest.name,
                        version = pack.manifest.version,
                        description = pack.manifest.description,
                        authors = pack.manifest.authors,
                        hash = pack.hash,
                        enabled = true,
                        locked = true,
                        sourcePath = pack.location.toAbsolutePath().normalize().toString()
                    )
                }
        val signatureSummaries = sortedPacks.map { pack ->
            val signature = pack.manifest.signature
            val result = RemoteScriptSignatureVerifier.verify(pack)
            when {
                signature == null || !result.signed -> trString("katton.remote.signature.unsigned", pack.manifest.name)
                result.keyFingerprint != null -> trString(
                    "katton.remote.signature.signed_fingerprint",
                    pack.manifest.name,
                    signature.keyId,
                    result.keyFingerprint.take(23)
                )
                else -> trString("katton.remote.signature.signed", pack.manifest.name, signature.keyId)
            }
        }
        mc.setScreen(RemoteScriptTrustScreen(mc.screen, serverAddress, views, signatureSummaries, callback))
    }

    @JvmStatic
    fun disconnectRemoteScripts(reason: String) {
        Minecraft.getInstance().connection?.connection?.disconnect(Component.literal(reason))
    }
}

private fun tr(key: String, vararg args: Any): Component = Component.translatable(key, *args)

private fun trString(key: String, vararg args: Any): String = tr(key, *args).string

private fun ScriptPackScope.localizedName(): String = trString("katton.pack.scope.${serializedName}")

private class ScriptIssueScreen(
    private val parent: Screen?,
    val issue: ScriptIssue
) : Screen(Component.literal(issue.title)) {
    private var scrollOffset = 0
    private var maxScroll = 0

    override fun init() {
        addRenderableWidget(
            Button.builder(tr("katton.button.close")) {
                onClose()
            }.bounds(width / 2 - 40, height - 28, 80, 20).build()
        )
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        val panelWidth = (width - 28).coerceAtMost(760)
        val panelLeft = (width - panelWidth) / 2
        val panelRight = panelLeft + panelWidth
        val panelTop = 14
        val panelBottom = height - 38
        val contentLeft = panelLeft + 14
        val contentRight = panelRight - 14
        val contentWidth = contentRight - contentLeft

        graphics.fill(panelLeft + 4, panelTop + 5, panelRight + 4, panelBottom + 5, 0x66000000)
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE0181818.toInt())
        graphics.fill(panelLeft + 1, panelTop + 1, panelRight - 1, panelBottom - 1, 0xE8242424.toInt())
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFFFF5F5F.toInt())

        graphics.text(font, Component.literal(issue.title), contentLeft, panelTop + 12, 0xFFFFD0D0.toInt(), false)

        val bodyTop = panelTop + 30
        val bodyBottom = panelBottom - 12
        val lineHeight = 11
        val visibleLines = max(1, (bodyBottom - bodyTop) / lineHeight)
        val lines = wrapText(issue.detail, contentWidth - 8)
        maxScroll = max(0, lines.size - visibleLines)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        var y = bodyTop
        lines.drop(scrollOffset).take(visibleLines).forEach { line ->
            graphics.text(font, line, contentLeft, y, 0xFFE6E6E6.toInt(), false)
            y += lineHeight
        }

        if (maxScroll > 0) {
            val trackLeft = contentRight - 4
            val trackTop = bodyTop
            val trackBottom = bodyBottom
            val trackHeight = trackBottom - trackTop
            val thumbHeight = max(16, (trackHeight * visibleLines / lines.size).coerceAtMost(trackHeight))
            val thumbTop = trackTop + ((trackHeight - thumbHeight) * scrollOffset / maxScroll.coerceAtLeast(1))
            graphics.fill(trackLeft, trackTop, trackLeft + 2, trackBottom, 0x66333333)
            graphics.fill(trackLeft, thumbTop, trackLeft + 2, thumbTop + thumbHeight, 0xFFB0B0B0.toInt())
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxScroll)
        return true
    }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        if (maxWidth <= 0) return listOf(text)
        val lines = mutableListOf<String>()
        val whitespace = Regex("\\s+")
        text.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { raw ->
            if (raw.isBlank()) {
                lines += ""
                return@forEach
            }
            var current = ""
            raw.trim().split(whitespace).forEach { word ->
                breakLongToken(word, maxWidth).forEach { piece ->
                    val candidate = if (current.isEmpty()) piece else "$current $piece"
                    if (font.width(candidate) <= maxWidth) {
                        current = candidate
                    } else {
                        if (current.isNotEmpty()) {
                            lines += current
                        }
                        current = piece
                    }
                }
            }
            if (current.isNotEmpty()) {
                lines += current
            }
        }
        return lines.ifEmpty { listOf("") }
    }

    private fun breakLongToken(token: String, maxWidth: Int): List<String> {
        if (font.width(token) <= maxWidth) return listOf(token)
        val pieces = mutableListOf<String>()
        var current = ""
        token.forEach { char ->
            val candidate = current + char
            if (current.isNotEmpty() && font.width(candidate) > maxWidth) {
                pieces += current
                current = char.toString()
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) {
            pieces += current
        }
        return pieces
    }
}

private class RemoteScriptTrustScreen(
    private val parent: Screen?,
    private val serverAddress: String,
    private val packs: List<ScriptPackView>,
    private val signatureSummaries: List<String>,
    private val callback: (Boolean) -> Unit
) : Screen(tr("katton.screen.remote_scripts")) {

    private var decided = false

    override fun init() {
        val buttonY = height - 30
        addRenderableWidget(
            Button.builder(tr("katton.button.trust_and_run")) {
                decide(true)
            }.bounds(width / 2 - 154, buttonY, 146, 20).build()
        )
        addRenderableWidget(
            Button.builder(tr("katton.button.do_not_run")) {
                decide(false)
            }.bounds(width / 2 + 8, buttonY, 146, 20).build()
        )
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun onClose() {
        decide(false)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        val panelWidth = (width - 28).coerceAtMost(620)
        val panelLeft = (width - panelWidth) / 2
        val panelRight = panelLeft + panelWidth
        val panelTop = 14
        val panelBottom = height - 40
        val contentLeft = panelLeft + 14
        val contentRight = panelRight - 14
        val contentWidth = contentRight - contentLeft

        graphics.fill(panelLeft + 4, panelTop + 5, panelRight + 4, panelBottom + 5, 0x66000000)
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE0181818.toInt())
        graphics.fill(panelLeft + 1, panelTop + 1, panelRight - 1, panelBottom - 1, 0xE8242424.toInt())
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFFFFD166.toInt())

        var y = panelTop + 12
        fun line(text: String, color: Int = 0xFFE0E0E0.toInt(), gap: Int = 11) {
            if (y > panelBottom - 12) return
            graphics.text(font, text, contentLeft, y, color, false)
            y += gap
        }

        fun paragraph(text: String, color: Int = 0xFFE0E0E0.toInt()) {
            wrapText(text, contentWidth).forEach { wrapped ->
                line(wrapped, color)
            }
        }

        line(trString("katton.remote.blocked"), 0xFFFFD166.toInt())
        y += 2
        line(trimToWidth(trString("katton.remote.server", serverAddress), contentWidth))
        line(trString("katton.remote.sent_packs", packs.size))
        y += 3
        paragraph(trString("katton.remote.warning.jvm"), 0xFFFF7777.toInt())
        paragraph(trString("katton.remote.warning.access"), 0xFFFF9A9A.toInt())
        paragraph(trString("katton.remote.warning.trust"), 0xFFFFC2C2.toInt())
        y += 4
        line(trString("katton.remote.signature_status"), 0xFFFFFFFF.toInt())
        val signatureRows = ((panelBottom - y - 42) / 11).coerceIn(1, 4)
        signatureSummaries.take(signatureRows).forEach { summary ->
            line(trimToWidth("- $summary", contentWidth), 0xFFCFCFCF.toInt())
        }
        if (signatureSummaries.size > signatureRows) {
            line(trString("katton.ui.more", signatureSummaries.size - signatureRows), 0xFFCFCFCF.toInt())
        }
        y += 3
        line(trString("katton.remote.packs"), 0xFFFFFFFF.toInt())

        val maxRows = ((panelBottom - y - 4) / 11).coerceAtLeast(0)
        packs.take(maxRows).forEach { pack ->
            val label = "- ${pack.name} ${pack.version}"
            line(trimToWidth(label, contentWidth), 0xFFCFCFCF.toInt())
        }
        if (packs.size > maxRows) {
            line(trString("katton.ui.more", packs.size - maxRows), 0xFFCFCFCF.toInt())
        }
    }

    private fun decide(trusted: Boolean) {
        if (decided) {
            return
        }
        decided = true
        if (!trusted) {
            minecraft.connection?.connection?.disconnect(
                tr("katton.remote.disconnect.not_trusted")
            )
            minecraft.setScreen(parent)
        } else {
            minecraft.setScreen(null)
        }
        callback(trusted)
    }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        if (font.width(text) <= maxWidth) {
            return listOf(text)
        }
        val lines = mutableListOf<String>()
        var current = ""
        text.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (font.width(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) {
                    lines += current
                }
                current = word
            }
        }
        if (current.isNotEmpty()) {
            lines += current
        }
        return lines
    }

    private fun trimToWidth(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) {
            return text
        }
        var trimmed = text
        while (trimmed.length > 3 && font.width("$trimmed...") > maxWidth) {
            trimmed = trimmed.dropLast(1)
        }
        return "$trimmed..."
    }
}

private class ScriptPackManagerScreen(
    private val parent: Screen?
) : Screen(tr("katton.screen.script_packs")) {

    companion object {
        private const val LIST_MARGIN = 16
        private const val TOP_MARGIN = 28
        private const val BOTTOM_MARGIN = 48
        private const val ROW_HEIGHT = 18
    }

    private var packs: List<ScriptPackView> = emptyList()
    private var selectedIndex: Int = -1
    private var scrollOffset: Int = 0

    private lateinit var toggleButton: Button
    private lateinit var reloadButton: Button
    private var reloadQueued: Boolean = false

    override fun init() {
        refreshData()

        val buttonY = height - 28
        toggleButton = addRenderableWidget(
            Button.builder(tr("katton.button.enable_disable")) {
                toggleSelectedPack()
            }.bounds(16, buttonY, 120, 20).build()
        )

        reloadButton = addRenderableWidget(
            Button.builder(tr("katton.button.reload")) {
                triggerReload()
            }.bounds(146, buttonY, 90, 20).build()
        )

        addRenderableWidget(
            Button.builder(tr("katton.button.close")) {
                onClose()
            }.bounds(width - 96, buttonY, 80, 20).build()
        )

        updateButtons()
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Keep in-world scene visible without additional dim background.
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        val panelLeft = 10
        val panelTop = 6
        val panelRight = width - 10
        val panelBottom = height - 36
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0x8A000000.toInt())
        graphics.fill(panelLeft + 1, panelTop + 1, panelRight - 1, panelBottom - 1, 0x4D111111)

        graphics.text(font, title, 16, 10, 0xFFFFFFFF.toInt(), false)

        val listWidth = (width * 0.44f).toInt()
        val listLeft = LIST_MARGIN
        val listTop = TOP_MARGIN
        val listBottom = height - BOTTOM_MARGIN
        val listHeight = listBottom - listTop
        val visibleRows = max(1, listHeight / ROW_HEIGHT)
        val start = scrollOffset.coerceIn(0, max(0, packs.size - visibleRows))

        graphics.fill(listLeft - 2, listTop - 2, listLeft + listWidth, listBottom, 0x7A000000)
        graphics.fill(listLeft - 1, listTop - 1, listLeft + listWidth - 1, listBottom - 1, 0x4D202020)

        for (i in 0 until visibleRows) {
            val index = start + i
            if (index >= packs.size) break

            val pack = packs[index]
            val rowTop = listTop + i * ROW_HEIGHT
            val rowBottom = rowTop + ROW_HEIGHT - 2
            if (index == selectedIndex) {
                graphics.fill(listLeft, rowTop, listLeft + listWidth - 3, rowBottom, 0x664C8C2B)
            }

            val status = if (pack.enabled) trString("katton.pack.status.on") else trString("katton.pack.status.off")
            val lockText = if (pack.locked) trString("katton.pack.locked_suffix") else ""
            val label = trString("katton.pack.row", status, pack.scope.localizedName(), pack.name, lockText)
            graphics.text(font, label, listLeft + 4, rowTop + 5, 0xFFE0E0E0.toInt(), false)
        }

        val detailsLeft = listLeft + listWidth + 10
        val detailsTop = listTop - 2
        val detailsRight = width - 16
        val detailsBottom = listBottom
        graphics.fill(detailsLeft, detailsTop, detailsRight, detailsBottom, 0x70000000)
        graphics.fill(detailsLeft + 1, detailsTop + 1, detailsRight - 1, detailsBottom - 1, 0x40202020)

        renderDetails(graphics, listLeft + listWidth + 16, listTop + 4)
        ReloadProgressOverlay.renderExtractor(graphics)
    }

    private fun renderDetails(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        if (selectedIndex !in packs.indices) {
            graphics.text(font, trString("katton.pack.details.select"), x, y, 0xFFB0B0B0.toInt(), false)
            return
        }

        val pack = packs[selectedIndex]
        var lineY = y

        fun line(text: String, color: Int = 0xFFF0F0F0.toInt()) {
            graphics.text(font, text, x, lineY, color, false)
            lineY += 12
        }

        line(trString("katton.pack.details.name", pack.name))
        line(trString("katton.pack.details.id", pack.id))
        line(trString("katton.pack.details.version", pack.version))
        line(trString("katton.pack.details.scope", pack.scope.localizedName()))
        line(trString("katton.pack.details.enabled", if (pack.enabled) trString("katton.common.yes") else trString("katton.common.no")))
        line(trString("katton.pack.details.locked", if (pack.locked) trString("katton.common.yes") else trString("katton.common.no")))

        if (pack.authors.isNotEmpty()) {
            line(trString("katton.pack.details.authors", pack.authors.joinToString(", ")))
        }

        line(trString("katton.pack.details.hash", pack.hash.take(16)))
        if (pack.description.isNotBlank()) {
            line(trString("katton.pack.details.description"))
            line(pack.description, 0xFFCFCFCF.toInt())
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val listTop = TOP_MARGIN
        val listBottom = height - BOTTOM_MARGIN
        val listWidth = (width * 0.44f).toInt()

        if (mouseX < LIST_MARGIN || mouseX > LIST_MARGIN + listWidth || mouseY < listTop || mouseY > listBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }

        val visibleRows = max(1, (listBottom - listTop) / ROW_HEIGHT)
        val maxOffset = max(0, packs.size - visibleRows)
        scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxOffset)
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, primaryAction: Boolean): Boolean {
        if (event.button() == 0) {
            val mouseX = event.x()
            val mouseY = event.y()
            val listLeft = LIST_MARGIN
            val listTop = TOP_MARGIN
            val listBottom = height - BOTTOM_MARGIN
            val listWidth = (width * 0.44f).toInt()

            if (mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= listTop && mouseY <= listBottom) {
                val relativeY = (mouseY - listTop).toInt()
                val row = relativeY / ROW_HEIGHT
                val visibleRows = max(1, (listBottom - listTop) / ROW_HEIGHT)
                val start = scrollOffset.coerceIn(0, max(0, packs.size - visibleRows))
                val index = start + row
                if (index in packs.indices) {
                    selectedIndex = index
                    updateButtons()
                    return true
                }
            }
        }
        return super.mouseClicked(event, primaryAction)
    }

    private fun refreshData() {
        ScriptPackManager.refreshGlobalPacks()
        ScriptPackManager.refreshWorldPacks()

        val inWorld = minecraft.level != null
        val local = ScriptPackManager.listLocalPacksForGui(lockGlobalInWorld = inWorld)
        val remote = ServerPackCacheManager.listPacksForGui()
        packs = local + remote

        if (packs.isEmpty()) {
            selectedIndex = -1
            scrollOffset = 0
        } else {
            if (selectedIndex !in packs.indices) {
                selectedIndex = 0
            }
            scrollOffset = scrollOffset.coerceAtLeast(0)
        }
    }

    private fun updateButtons() {
        val selected = packs.getOrNull(selectedIndex)
        toggleButton.active = selected != null && !selected.locked && selected.scope != ScriptPackScope.SERVER_CACHE
        reloadButton.active = !reloadQueued && !ScriptReloadManager.isClientReloadRunning()
    }

    private fun toggleSelectedPack() {
        val selected = packs.getOrNull(selectedIndex) ?: return
        if (selected.locked || selected.scope == ScriptPackScope.SERVER_CACHE) {
            return
        }

        val updated = ScriptPackManager.setPackEnabled(selected.syncId, !selected.enabled)
        if (!updated) {
            return
        }

        refreshData()
        updateButtons()
        triggerReload()
    }

    private fun triggerReload() {
        val mc = minecraft
        if (reloadQueued || ScriptReloadManager.isClientReloadRunning()) {
            return
        }

        reloadQueued = true
        updateButtons()

        val integratedServer = mc.singleplayerServer
        if (integratedServer != null) {
            ScriptReloadManager.reloadScriptsAsync(integratedServer) { serverOk ->
                val clientOk = ScriptReloadManager.reloadClientScriptsAsync()
                mc.execute {
                    val message = if (serverOk && clientOk) {
                        tr("commands.katton.reload.started")
                    } else {
                        tr("commands.katton.reload.failed.logs")
                    }
                    mc.player?.sendSystemMessage(message)
                    mc.gui.setOverlayMessage(message, false)
                    reloadQueued = false
                    refreshData()
                    updateButtons()
                }
            }
            return
        }

        val clientOk = ScriptReloadManager.reloadClientScriptsAsync()
        val message = if (clientOk) {
            tr("commands.katton.reload.started")
        } else {
            tr("commands.katton.reload.in_progress")
        }
        mc.player?.sendSystemMessage(message)
        mc.gui.setOverlayMessage(message, false)
        reloadQueued = false
        refreshData()
        updateButtons()
    }
}
