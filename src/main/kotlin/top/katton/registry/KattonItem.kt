package top.katton.registry

import net.minecraft.core.component.DataComponentInitializers
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import top.katton.api.server

/**
 * Extended Item.Properties that supports custom name and model.
 * 
 * Does not call setId() in constructor to avoid triggering intrusive holder creation.
 * The resource key is set during registration time instead.
 * 
 * @param id The identifier for this item
 */
@Suppress("unused")
class KattonItemProperties(
    override val id: Identifier,
) : Item.Properties(), Identifiable {

    private var _name: Component? = null
    
    /**
     * The display name of the item.
     * Defaults to a translatable component based on the item id.
     */
    var name: Component
        get() = _name ?: Component.translatable("item.${id.namespace}.${id.path}")
        set(value) {
            _name = value
        }

    /**
     * Sets the display name of the item.
     * @return this for chaining
     */
    fun setName(name: Component): KattonItemProperties {
        this.name = name
        return this
    }

    private var _model: Identifier? = null
    
    /**
     * The model identifier for the item.
     * Defaults to "namespace:item/path" based on the item id.
     */
    var model: Identifier
        get() = _model ?: id.withPath("item/${id.path}")
        set(value) {
            _model = value
        }

    /**
     * Sets the model identifier for the item.
     * @return this for chaining
     */
    fun setModel(model: Identifier): KattonItemProperties {
        this.model = model
        return this
    }

    /**
     * Builds the data component map for this item.
     * 
     * Always sets ITEM_NAME and ITEM_MODEL first, then attempts to add
     * additional components through the initializer. If itemId is not set
     * (e.g., during hot-reload), the basic components are still preserved.
     * 
     * @return The built DataComponentMap
     */
    fun buildComponent(): DataComponentMap {
        val mapBuilder = DataComponentMap.builder()
        
        // Always set basic components first
        mapBuilder.set(DataComponents.ITEM_NAME, name)
        mapBuilder.set(DataComponents.ITEM_MODEL, model)
        
        // Try to add additional components via initializer
        server?.let {
            val initializer = createComponentInitializer(name, model)
            try {
                initializer.run(mapBuilder, it.registryAccess(), itemIdOrThrow())
            } catch (_: Throwable) {
                // itemId not set - basic components already applied
            }
        }
        return mapBuilder.build()
    }

    /**
     * Creates a component initializer that sets name, model, and validators.
     */
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
        /**
         * Creates a new KattonItemProperties with the given identifier.
         */
        fun components(id: Identifier) = KattonItemProperties(id)
    }
}
