package top.katton.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import top.katton.Katton
import top.katton.pack.ScriptPackManager
import top.katton.pack.ScriptPackScope
import top.katton.pack.ScriptPackView
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
        minecraft?.setScreen(parent)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        graphics.fill(0, 0, width, height, 0xAA121212.toInt())
        graphics.text(font, title, 16, 10, 0xFFFFFF, false)

        val listWidth = (width * 0.44f).toInt()
        val listLeft = LIST_MARGIN
        val listTop = TOP_MARGIN
        val listBottom = height - BOTTOM_MARGIN
        val listHeight = listBottom - listTop
        val visibleRows = max(1, listHeight / ROW_HEIGHT)
        val start = scrollOffset.coerceIn(0, max(0, packs.size - visibleRows))

        graphics.fill(listLeft - 2, listTop - 2, listLeft + listWidth, listBottom, 0x55000000)

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
            graphics.text(font, label, listLeft + 4, rowTop + 5, 0xE0E0E0, false)
        }

        renderDetails(graphics, listLeft + listWidth + 12, listTop)
    }

    private fun renderDetails(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        if (selectedIndex !in packs.indices) {
            graphics.text(font, "Select a script pack to see details", x, y, 0xB0B0B0, false)
            return
        }

        val pack = packs[selectedIndex]
        var lineY = y

        fun line(text: String, color: Int = 0xF0F0F0) {
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
            line(pack.description, 0xCFCFCF)
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

        val inWorld = minecraft?.level != null
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
        reloadButton.active = true
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
        Katton.reloadClientScripts()
        val integratedServer = minecraft?.singleplayerServer
        if (integratedServer != null) {
            Katton.reloadScripts(integratedServer)
        }
    }
}
