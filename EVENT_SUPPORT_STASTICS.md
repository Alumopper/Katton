# NeoForge and Fabric Events Comparison

## Summary

| Module | Supported Event Fields |
| :--- | ---: |
| Fabric | 93 |
| NeoForge | 89 |

## Detailed Event List

| Event Category | Event Name | Fabric | NeoForge |
| :--- | :--- | :---: | :---: |
| **Chunk and Block** | onChunkLoad | ✅ | ✅ |
| | onChunkUnload | ✅ | ✅ |
| | onChunkLevelTypeChange | ✅ | ✅ |
| | onBlockEntityLoad | ✅ | ✅ |
| | onBlockEntityUnload | ✅ | ✅ |
| | onBeforeBlockBreak | ✅ | - |
| | onAfterBlockBreak | ✅ | - |
| | onCanceledBlockBreak | ✅ | - |
| | onBlockBreak | - | ✅ |
| | onBlockPlace | - | ✅ |
| | onExplosionStart | ✅ | ✅ |
| | onExplosionDetonate | ✅ | ✅ |
| **Item Component** | onModifyComponent | ✅ | ✅ |
| | onAllowEnchanting | ✅ | ✅ |
| | onModifyEnchantment | ✅ | ✅ |
| **Item** | onUseOn | ✅ | ✅ |
| | onUse | ✅ | ✅ |
| **Living Behavior** | onAnimalTame | - | ✅ |
| | onBabySpawn | - | ✅ |
| | onElytraAllow | ✅ | ✅ |
| | onElytraCustom | ✅ | ✅ |
| | onAllowSleeping | ✅ | ✅ |
| | onStartSleeping | ✅ | ✅ |
| | onStopSleeping | ✅ | ✅ |
| | onAllowBed | ✅ | ✅ |
| | onAllowNearbyMonsters | ✅ | ✅ |
| | onAllowResettingTime | ✅ | ✅ |
| | onModifySleepingDirection | ✅ | ✅ |
| | onAllowSettingSpawn | ✅ | ✅ |
| | onSetBedOccupationState | ✅ | ✅ |
| | onModifyWakeUpPosition | ✅ | ✅ |
| | onPlayerWakeUp | ✅ | ✅ |
| **Living Use Item** | onUseItemStart | ✅ | ✅ |
| | onUseItemTick | ✅ | ✅ |
| | onUseItemStop | ✅ | ✅ |
| | onUseItemFinish | ✅ | ✅ |
| **Loot Table** | onLootTableReplace | ✅ | ✅ |
| | onLootTableModify | ✅ | ✅ |
| | onLootTableAllLoad | ✅ | ✅ |
| | onLootTableModifyDrops | ✅ | ✅ |
| **Player** | onUseItemOn | ✅ | - |
| | onUseWithoutItem | ✅ | - |
| | onAttackBlock | ✅ | - |
| | onAttackEntity | ✅ | ✅ |
| | onBlockInteract | ✅ | ✅ |
| | onEntityInteract | ✅ | ✅ |
| | onItemInteract | ✅ | ✅ |
| | onLeftClickBlock | - | ✅ |
| | onDestroyItem | ✅ | ✅ |
| **Server Entity Combat** | onAfterKilledOtherEntity | ✅ | ✅ |
| | onCriticalHit | - | ✅ |
| | onShieldBlock | ✅ | ✅ |
| **Server Entity** | onAfterEntityLoad | ✅ | - |
| | onEntityLoad | - | ✅ |
| | onEntityUnload | ✅ | ✅ |
| | onEquipmentChange | ✅ | ✅ |
| | onAfterEntityChangeLevel | ✅ | ✅ |
| | onAfterPlayerChangeLevel | ✅ | ✅ |
| | onEntityTeleport | - | ✅ |
| | onEndermanAnger | ✅ | ✅ |
| **Server** | onServerStarting | ✅ | ✅ |
| | onServerStarted | ✅ | ✅ |
| | onServerStopping | ✅ | ✅ |
| | onServerStopped | ✅ | ✅ |
| | onSyncDatapackContents | ✅ | ✅ |
| | onStartDatapackReload | ✅ | ✅ |
| | onEndDatapackReload | ✅ | ✅ |
| | onBeforeSave | ✅ | ✅ |
| | onAfterSave | ✅ | ✅ |
| | onStartServerTick | ✅ | ✅ |
| | onEndServerTick | ✅ | ✅ |
| | onStartWorldTick | ✅ | ✅ |
| | onEndWorldTick | ✅ | ✅ |
| **Server Living Entity** | onLivingHurt | ✅ | ✅ |
| | onAllowDamage | ✅ | ✅ |
| | onAfterDamage | ✅ | ✅ |
| | onAllowDeath | ✅ | ✅ |
| | onAfterDeath | ✅ | ✅ |
| | onLivingDrops | - | ✅ |
| | onLivingFall | ✅ | ✅ |
| | onLivingJump | - | ✅ |
| | onMobConversion | ✅ | ✅ |
| **Server Message** | onAllowChatMessage | ✅ | - |
| | onAllowGameMessage | ✅ | - |
| | onAllowCommandMessage | ✅ | - |
| | onChatMessage | ✅ | - |
| | onGameMessage | ✅ | - |
| | onCommandMessage | ✅ | - |
| | onServerChat | - | ✅ |
| **Server Mob Effect** | onAllowAdd | ✅ | - |
| | onBeforeAdd | ✅ | - |
| | onAfterAdd | ✅ | - |
| | onAllowEarlyRemove | ✅ | - |
| | onBeforeRemove | ✅ | - |
| | onAfterRemove | ✅ | - |
| | onMobEffectApplicable | - | ✅ |
| | onMobEffectAdd | - | ✅ |
| | onMobEffectRemove | - | ✅ |
| | onMobEffectExpire | - | ✅ |
| **Server Player** | onPlayerJoin | ✅ | ✅ |
| | onPlayerLeave | ✅ | ✅ |
| | onAfterPlayerRespawn | ✅ | ✅ |
| | onPlayerCopy | ✅ | ✅ |
| | onPlayerXpChange | ✅ | ✅ |
| | onPlayerXpLevelChange | ✅ | ✅ |
| | onPlayerPickupXp | ✅ | ✅ |
| | onPickFromBlock | ✅ | ✅ |
| | onPickFromEntity | ✅ | ✅ |
