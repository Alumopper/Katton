package top.katton.registry

import net.minecraft.core.component.DataComponentInitializers
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import top.katton.api.server

/**
 * 扩展的 Item.Properties，支持自定义名称和模型。
 * 不在构造时调用 setId()，避免触发 intrusive holder 创建。
 */
@Suppress("unused")
class KattonItemProperties(
    override val id: Identifier,
) : Item.Properties(), Identifiable {

    private var _name: Component? = null
    var name: Component
        get() = _name ?: Component.translatable("item.${id.namespace}.${id.path}")
        set(value) {
            _name = value
        }

    fun setName(name: Component): KattonItemProperties {
        this.name = name
        return this
    }

    private var _model: Identifier? = null
    var model: Identifier
        get() = _model ?: id.withPath("item/${id.path}")
        set(value) {
            _model = value
        }

    fun setModel(model: Identifier): KattonItemProperties {
        this.model = model
        return this
    }

    /**
     * 构建物品组件映射。
     * 始终首先设置 ITEM_NAME 和 ITEM_MODEL，然后尝试通过初始化器添加其他组件。
     */
    fun buildComponent(): DataComponentMap {
        val mapBuilder = DataComponentMap.builder()
        
        mapBuilder.set(DataComponents.ITEM_NAME, name)
        mapBuilder.set(DataComponents.ITEM_MODEL, model)
        
        server?.let {
            val initializer = createComponentInitializer(name, model)
            try {
                initializer.run(mapBuilder, it.registryAccess(), itemIdOrThrow())
            } catch (_: Throwable) {
                // itemId 未设置时忽略，基本组件已设置
            }
        }
        return mapBuilder.build()
    }

    private fun createComponentInitializer(
        name: Component,
        model: Identifier
    ): DataComponentInitializers.Initializer<Item> {
        return this.componentInitializer.andThen { components, _, _ ->
            components
                .set(DataComponents.ITEM_NAME, name)
                .set(DataComponents.ITEM_MODEL, model)
                .addValidator { map ->
                    check(
                        !(map.has(DataComponents.DAMAGE) && map.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1)
                    ) { "Item cannot have both durability and be stackable" }
                }
        }
    }

    companion object {
        fun components(id: Identifier) = KattonItemProperties(id)
    }
}