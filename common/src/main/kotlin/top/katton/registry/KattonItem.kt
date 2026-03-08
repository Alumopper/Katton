package top.katton.registry

import net.minecraft.core.component.DataComponentInitializers
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import top.katton.api.server

/**
 * Extended Item.Properties that supports custom name and model with hot-reload capabilities.
 * 
 * This class extends Minecraft's [Item.Properties] to provide additional functionality
 * for defining custom item names and models. It is designed to work with Katton's
 * hot-reload system by deferring the intrusive holder creation until registration time.
 * 
 * Does not call setId() in constructor to avoid triggering intrusive holder creation.
 * The resource key is set during registration time instead.
 * 
 * @param id The identifier for this item, used for registration and default naming
 * @see KattonRegistry.ITEMS for registration
 */
@Suppress("unused")
class KattonItemProperties(
    override val id: Identifier,
) : Item.Properties(), Identifiable {

    private var _name: Component? = null
    
    /**
     * The display name of the item.
     * 
     * If not explicitly set, defaults to a translatable component using the pattern
     * `item.{namespace}.{path}`. For example, an item with id "mymod:custom_sword"
     * would default to the translation key "item.mymod.custom_sword".
     */
    var name: Component
        get() = _name ?: Component.translatable("item.${id.namespace}.${id.path}")
        set(value) {
            _name = value
        }

    /**
     * Sets the display name of the item.
     * 
     * @param name The display name component to set
     * @return This properties instance for method chaining
     */
    fun setName(name: Component): KattonItemProperties {
        this.name = name
        return this
    }

    private var _model: Identifier? = null
    
    /**
     * The model identifier for the item.
     * 
     * If not explicitly set, defaults to "namespace:item/path" based on the item id.
     * For example, an item with id "mymod:custom_sword" would default to the model
     * identifier "mymod:item/custom_sword".
     * 
     * This identifier is used by the client to locate the item's model JSON file
     * in the resource pack.
     */
    var model: Identifier
        get() = _model ?: id.withPath("item/${id.path}")
        set(value) {
            _model = value
        }

    /**
     * Sets the model identifier for the item.
     * 
     * @param model The model identifier to set
     * @return This properties instance for method chaining
     */
    fun setModel(model: Identifier): KattonItemProperties {
        this.model = model
        return this
    }

    /**
     * Finalizes the componentInitializer to include custom ITEM_NAME and ITEM_MODEL.
     *
     * In Minecraft 26.1+, Item's constructor registers a componentInitializer into
     * BuiltInRegistries.DATA_COMPONENT_INITIALIZERS via finalizeInitializer().
     * When DataComponentInitializers.build() runs later (during registry binding),
     * it uses these initializers to set holder.components. This method ensures the
     * initializer produces the correct ITEM_NAME and ITEM_MODEL values.
     *
     * This method also adds a validator to ensure items cannot have both durability
     * (DAMAGE component) and be stackable (MAX_STACK_SIZE > 1) at the same time,
     * which would cause issues in gameplay.
     *
     * This method must be called before the Item is constructed.
     *
     * @return This properties instance for method chaining
     */
    internal fun finalizeComponentInitializer(): KattonItemProperties {
        val capturedName = name
        val capturedModel = model
        this.componentInitializer = this.componentInitializer.andThen { components, _, _ ->
            components
                .set(DataComponents.ITEM_NAME, capturedName)
                .set(DataComponents.ITEM_MODEL, capturedModel)
                .addValidator { map ->
                    check(
                        !(map.has(DataComponents.DAMAGE) && map.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1)
                    ) { "Item cannot have both durability and be stackable" }
                }
        }
        return this
    }

    /**
     * Builds the data component map for this item.
     *
     * This method constructs a [DataComponentMap] containing all the item's
     * data components. It always sets ITEM_NAME and ITEM_MODEL first, then
     * attempts to add additional components through the initializer.
     * 
     * If the itemId is not set (e.g., during hot-reload before registration),
     * the basic components are still preserved and the initializer is skipped
     * gracefully.
     *
     * @return The built DataComponentMap containing all configured components
     */
    internal fun buildComponent(): DataComponentMap {
        val mapBuilder = DataComponentMap.builder()
        
        mapBuilder.set(DataComponents.ITEM_NAME, name)
        mapBuilder.set(DataComponents.ITEM_MODEL, model)
        
        server?.let {
            try {
                this.componentInitializer.run(mapBuilder, it.registryAccess(), itemIdOrThrow())
            } catch (_: Throwable) {
            }
        }
        return mapBuilder.build()
    }

    companion object {
        /**
         * Creates a new KattonItemProperties with the given identifier.
         * 
         * This is a convenience factory method for creating properties instances.
         * 
         * @param id The identifier for the item
         * @return A new KattonItemProperties instance
         */
        internal fun components(id: Identifier) = KattonItemProperties(id)
    }
}
