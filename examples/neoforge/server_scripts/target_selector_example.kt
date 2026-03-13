import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import top.katton.api.dpcaller.addEffect
import top.katton.api.requireServer
import top.katton.util.EntitySelectorBuilder

fun targetSelectorExample(){

    // Build a target selector
    val selector = EntitySelectorBuilder.allEntities()  //@e
        .type(EntityType.CREEPER)   //type = creeper
        .tag("qwq", false)  //tag = qwq
        .distanceBelow(16.0)    //distance = ..16
        .create()
    // So we get a target selector like this: @e[type=creeper,tag=qwq,distance=..16]

    // And then we need to build a command execution source, which is required by the selector to get the world and the position for distance calculation
    val source = requireServer().createCommandSourceStack()
        .withLevel(requireServer().overworld()) // Set the dimension for the command source
        .withPosition(Vec3(50.0, 70.0, 50.0)) // Set the position for distance calculation

    // Get entities selected by the selector
    val entities = selector.findEntities(source)

    // Once you get the references of the entities, you access those entities at anywhere in your code, and do whatever you want with them
    for (entity in entities) {
        if(entity is LivingEntity){
            // add some effect to the entity
            addEffect(entity,
                MobEffects.GLOWING, // effect
                200,    // effect duration in **ticks**
                0,      // effect amplifier level
                false,  // show particles
                false   // show icon
            )
        }
    }
}

// Remember to call the function
val targetSelectorExample = targetSelectorExample()