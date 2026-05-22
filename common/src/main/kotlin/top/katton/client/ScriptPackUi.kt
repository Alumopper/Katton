package top.katton.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import top.katton.Katton
import top.katton.engine.ScriptReloadManager
import top.katton.pack.ScriptPackManager
import top.katton.pack.ScriptPackScope
import top.katton.pack.ScriptPackView
import top.katton.pack.RemoteScriptSignatureVerifier
import top.katton.pack.ServerPackCacheManager
import kotlin.math.max

object ScriptPackUi {
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
                signature == null || !result.signed -> "${pack.manifest.name}: unsigned"
                result.keyFingerprint != null -> "${pack.manifest.name}: signed by ${signature.keyId} (${result.keyFingerprint.take(23)}...)"
                else -> "${pack.manifest.name}: signed by ${signature.keyId}"
            }
        }
        mc.setScreen(RemoteScriptTrustScreen(mc.screen, serverAddress, views, signatureSummaries, callback))
    }

    @JvmStatic
    fun disconnectRemoteScripts(reason: String) {
        Minecraft.getInstance().connection?.connection?.disconnect(Component.literal(reason))
    }
}

private class RemoteScriptTrustScreen(
    private val parent: Screen?,
    private val serverAddress: String,
    private val packs: List<ScriptPackView>,
    private val signatureSummaries: List<String>,
    private val callback: (Boolean) -> Unit
) : Screen(Component.literal("Katton Remote Scripts")) {

    private var decided = false

    override fun init() {
        val buttonY = height - 30
        addRenderableWidget(
            Button.builder(Component.literal("Trust and Run")) {
                decide(true)
            }.bounds(width / 2 - 154, buttonY, 146, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Do Not Run")) {
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

        line("Katton blocked remote script execution", 0xFFFFD166.toInt())
        y += 2
        line(trimToWidth("Server: $serverAddress", contentWidth))
        line("This server sent ${packs.size} Katton script pack(s).")
        y += 3
        paragraph("Warning: these scripts execute arbitrary JVM code inside your Minecraft client.", 0xFFFF7777.toInt())
        paragraph("They can access Minecraft internals and may access files, network, or other JVM APIs.", 0xFFFF9A9A.toInt())
        paragraph("Only trust scripts from servers and modpacks you fully trust.", 0xFFFFC2C2.toInt())
        y += 4
        line("Signature status:", 0xFFFFFFFF.toInt())
        val signatureRows = ((panelBottom - y - 42) / 11).coerceIn(1, 4)
        signatureSummaries.take(signatureRows).forEach { summary ->
            line(trimToWidth("- $summary", contentWidth), 0xFFCFCFCF.toInt())
        }
        if (signatureSummaries.size > signatureRows) {
            line("- ... and ${signatureSummaries.size - signatureRows} more", 0xFFCFCFCF.toInt())
        }
        y += 3
        line("Packs:", 0xFFFFFFFF.toInt())

        val maxRows = ((panelBottom - y - 4) / 11).coerceAtLeast(0)
        packs.take(maxRows).forEach { pack ->
            val label = "- ${pack.name} ${pack.version}"
            line(trimToWidth(label, contentWidth), 0xFFCFCFCF.toInt())
        }
        if (packs.size > maxRows) {
            line("- ... and ${packs.size - maxRows} more", 0xFFCFCFCF.toInt())
        }
    }

    private fun decide(trusted: Boolean) {
        if (decided) {
            return
        }
        decided = true
        if (!trusted) {
            minecraft.connection?.connection?.disconnect(
                Component.literal("Katton remote scripts were not trusted by the client.")
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
) : Screen(Component.literal("Katton Script Packs")) {

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
            Button.builder(Component.literal("Enable / Disable")) {
                toggleSelectedPack()
            }.bounds(16, buttonY, 120, 20).build()
        )

        reloadButton = addRenderableWidget(
            Button.builder(Component.literal("Reload")) {
                triggerReload()
            }.bounds(146, buttonY, 90, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Close")) {
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

            val status = if (pack.enabled) "ON" else "OFF"
            val lockText = if (pack.locked) " [Locked]" else ""
            val label = "[$status] ${pack.scope.displayName} - ${pack.name}$lockText"
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
            graphics.text(font, "Select a script pack to see details", x, y, 0xFFB0B0B0.toInt(), false)
            return
        }

        val pack = packs[selectedIndex]
        var lineY = y

        fun line(text: String, color: Int = 0xFFF0F0F0.toInt()) {
            graphics.text(font, text, x, lineY, color, false)
            lineY += 12
        }

        line("Name: ${pack.name}")
        line("Id: ${pack.id}")
        line("Version: ${pack.version}")
        line("Scope: ${pack.scope.displayName}")
        line("Enabled: ${if (pack.enabled) "Yes" else "No"}")
        line("Locked: ${if (pack.locked) "Yes" else "No"}")

        if (pack.authors.isNotEmpty()) {
            line("Authors: ${pack.authors.joinToString(", ")}")
        }

        line("Hash: ${pack.hash.take(16)}...")
        if (pack.description.isNotBlank()) {
            line("Description:")
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
                        Component.literal("[Katton] Script reload started.")
                    } else {
                        Component.literal("[Katton] Reload failed. Check logs for details.")
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
            Component.literal("[Katton] Script reload started.")
        } else {
            Component.literal("[Katton] Reload already in progress.")
        }
        mc.player?.sendSystemMessage(message)
        mc.gui?.setOverlayMessage(message, false)
        reloadQueued = false
        refreshData()
        updateButtons()
    }
}
