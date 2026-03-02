import net.minecraft.network.chat.Component
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.material.MapColor
import top.katton.api.*

Events.onServerStarting {
    once("modify_items_and_blocks") {
        modifyItem("minecraft:diamond") {
            maxStackSize = 16
            rarity = Rarity.EPIC
            name(Component.literal("超级钻石"))
            addTooltip(Component.literal("这是一个非常稀有的钻石！"))
        }
        
        modifyItem("minecraft:apple") {
            maxStackSize = 32
            foodProperties = FoodProperties.Builder()
                .nutrition(8)
                .saturationModifier(0.8f)
                .alwaysEat()
                .build()
        }
        
        modifyItem("minecraft:iron_pickaxe") {
            maxDamage = 500
            rarity = Rarity.RARE
            name(Component.literal("强化铁镐"))
        }
        
        modifyBlock("minecraft:stone") {
            hardness = 1.0f
            resistance = 10.0f
            lightEmission = 5
            friction = 0.8f
        }
        
        modifyBlock("minecraft:dirt") {
            hardness = 0.5f
            resistance = 2.0f
            requiresCorrectTool = false
            speedFactor = 1.2f
            jumpFactor = 1.5f
        }
        
        modifyBlock("minecraft:glowstone") {
            lightEmission = 15
            hardness = 0.5f
            resistance = 1.0f
        }
        
        modifyBlock("minecraft:obsidian") {
            strength(50.0f)
            lightEmission = 3
        }
        
        modifyBlock("minecraft:glass") {
            hardness = 0.3f
            resistance = 0.5f
            canOcclude = false
            hasCollision = false
        }
        
        modifyBlock("minecraft:grass_block") {
            mapColor = MapColor.PLANT
            hardness = 0.8f
            resistance = 4.0f
        }
        
        println("物品和方块修改完成！")
    }
}
