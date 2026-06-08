@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * %en
 * Block registration API for custom blocks.
 *
 * This module provides functions to register custom Blocks with hot-reload support.
 * Blocks registered through this API can be reloaded during development without
 * restarting the game.
 *
 * Note: Block registration is experimental and may have limitations with
 * complex block behaviors or block entities.
 *
 * %zh
 * 用于自定义方块的注册 API。
 * 该模块提供一组函数，可在热重载支持下注册自定义方块。
 * 通过这个 API 注册的方块可以在开发过程中重新加载，无需重启游戏。
 * 注意：方块注册处于实验阶段，复杂的方块行为或方块实体可能存在限制。
 */

/**
 * %en
 * Registers a native Block with hot-reload support (String overload).
 *
 * This is the primary API for registering custom Block subclasses from scripts.
 * The block will be registered in the global Minecraft registry with full
 * hot-reload capability.
 *
 * %zh
 * 注册原生方块，并支持热重载（String 重载）。
 * 这是脚本中注册自定义方块子类的主要 API。
 * 方块会注册到全局 Minecraft 注册表，并具备完整的热重载能力。
 * @param id
 * %en Block identifier (e.g., "mymod:custom_block")
 * %zh 方块标识符，例如 "mymod:custom_block"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param blockFactory
 * %en Factory function to create the Block instance, receives Properties
 * %zh 创建方块实例的工厂函数，接收 Properties。
 * @return
 * %en registered KattonBlockEntry
 * %zh 已注册的 KattonBlockEntry。
 * @example
 * %en
 * ```kotlin
 * registerNativeBlock("mymod:custom_block") { properties ->
 *     object : Block(properties) {
 *         // Custom block behavior
 *     }
 * }
 * ```
 * %zh 示例代码见英文部分。
 */
@ApiStatus.Experimental
fun registerNativeBlock(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    blockFactory: (BlockBehaviour.Properties) -> Block
): KattonRegistry.KattonBlockEntry = registerNativeBlock(id(id), registerMode, blockFactory)

/**
 * %en
 * Registers a native Block with hot-reload support (Identifier overload).
 *
 * %zh
 * 注册原生方块，并支持热重载（Identifier 重载）。
 * @param id
 * %en Block identifier
 * %zh 方块标识符。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param blockFactory
 * %en Factory function to create the Block instance
 * %zh 创建方块实例的工厂函数。
 * @return
 * %en registered KattonBlockEntry
 * %zh 已注册的 KattonBlockEntry。
 */
@ApiStatus.Experimental
fun registerNativeBlock(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    blockFactory: (BlockBehaviour.Properties) -> Block
): KattonRegistry.KattonBlockEntry {
    return KattonRegistry.BLOCKS.newNative(id, registerMode, blockFactory)
}

/**
 * %en
 * Utility factory for quickly creating a simple custom block.
 *
 * Creates a basic Block with the specified properties.
 * For more complex blocks, use the full factory pattern with registerNativeBlock.
 *
 * %zh
 * 用于快速创建简单自定义方块的工厂函数。
 * 会按指定属性创建一个基础方块。
 * 如果需要更复杂的方块，请使用 registerNativeBlock 的完整工厂模式。
 * @param properties
 * %en Block behavior properties (default: basic properties)
 * %zh 方块行为属性（默认：基础属性）。
 * @return
 * %en new Block instance
 * %zh 新建的方块实例。
 */
fun createSimpleBlock(properties: BlockBehaviour.Properties = BlockBehaviour.Properties.of()): Block {
    return Block(properties)
}
