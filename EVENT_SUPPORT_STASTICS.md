# NeoForge and Fabric Events Comparison

## Summary

| Module | Supported Event Fields |
| :--- | ---: |
| Fabric | 93 |
| NeoForge | 107 |

## Detailed Event List

| Event Category | Event Name | Fabric | NeoForge |
| :--- | :--- | :---: | :---: |
| **Chunk and Block** | onAfterBlockBreak | ✅ | - |
| | onBeforeBlockBreak | ✅ | - |
| | onBlockBreak | - | ✅ |
| | onBlockEntityLoad | ✅ | ✅ |
| | onBlockEntityUnload | ✅ | ✅ |
| | onBlockPlace | - | ✅ |
| | onCanceledBlockBreak | ✅ | - |
| | onChunkDataLoad | - | ✅ |
| | onChunkDataSave | - | ✅ |
| | onChunkLevelTypeChange | ✅ | ✅ |
| | onChunkLoad | ✅ | ✅ |
| | onChunkSent | - | ✅ |
| | onChunkUnload | ✅ | ✅ |
| | onChunkUnWatch | - | ✅ |
| | onChunkWatch | - | ✅ |
| | onExplosionDetonate | ✅ | ✅ |
| | onExplosionStart | ✅ | ✅ |
| **Item Component** | onAllowEnchanting | ✅ | ✅ |
| | onModifyComponent | ✅ | ✅ |
| | onModifyEnchantment | ✅ | ✅ |
| **Item** | onUse | ✅ | ✅ |
| | onUseOn | ✅ | ✅ |
| **Living Behavior** | onAllowBed | ✅ | ✅ |
| | onAllowNearbyMonsters | ✅ | ✅ |
| | onAllowResettingTime | ✅ | ✅ |
| | onAllowSettingSpawn | ✅ | ✅ |
| | onAllowSleeping | ✅ | ✅ |
| | onAnimalTame | - | ✅ |
| | onBabySpawn | - | ✅ |
| | onElytraAllow | ✅ | ✅ |
| | onElytraCustom | ✅ | ✅ |
| | onModifySleepingDirection | ✅ | ✅ |
| | onModifyWakeUpPosition | ✅ | ✅ |
| | onPlayerWakeUp | ✅ | ✅ |
| | onSetBedOccupationState | ✅ | ✅ |
| | onStartSleeping | ✅ | ✅ |
| | onStopSleeping | ✅ | ✅ |
| **Living Use Item** | onUseItemFinish | ✅ | ✅ |
| | onUseItemStart | ✅ | ✅ |
| | onUseItemStop | ✅ | ✅ |
| | onUseItemTick | ✅ | ✅ |
| **Loot Table** | onLootTableAllLoad | ✅ | ✅ |
| | onLootTableModify | ✅ | ✅ |
| | onLootTableModifyDrops | ✅ | ✅ |
| | onLootTableReplace | ✅ | ✅ |
| **Player** | onAttackBlock | ✅ | - |
| | onAttackEntity | ✅ | ✅ |
| | onBlockInteract | ✅ | ✅ |
| | onDestroyItem | ✅ | ✅ |
| | onEntityInteract | ✅ | ✅ |
| | onItemInteract | ✅ | ✅ |
| | onLeftClickBlock | - | ✅ |
| | onUseItemOn | ✅ | - |
| | onUseWithoutItem | ✅ | - |
| **Server Entity Combat** | onAfterKilledOtherEntity | ✅ | ✅ |
| | onCriticalHit | - | ✅ |
| | onShieldBlock | ✅ | ✅ |
| **Server Entity** | onAfterEntityChangeLevel | ✅ | ✅ |
| | onAfterEntityLoad | ✅ | - |
| | onAfterPlayerChangeLevel | ✅ | ✅ |
| | onEndermanAnger | ✅ | ✅ |
| | onEntityLoad | - | ✅ |
| | onEntityTeleport | - | ✅ |
| | onEntityUnload | ✅ | ✅ |
| | onEquipmentChange | ✅ | ✅ |
| **Server** | onAfterSave | ✅ | ✅ |
| | onBeforeSave | ✅ | ✅ |
| | onEndDatapackReload | ✅ | ✅ |
| | onEndServerTick | ✅ | ✅ |
| | onEndWorldTick | ✅ | ✅ |
| | onLevelLoad | - | ✅ |
| | onLevelSave | - | ✅ |
| | onLevelUnload | - | ✅ |
| | onServerStarted | ✅ | ✅ |
| | onServerStarting | ✅ | ✅ |
| | onServerStopped | ✅ | ✅ |
| | onServerStopping | ✅ | ✅ |
| | onStartDatapackReload | ✅ | ✅ |
| | onStartServerTick | ✅ | ✅ |
| | onStartWorldTick | ✅ | ✅ |
| | onSyncDatapackContents | ✅ | ✅ |
| **Server Living Entity** | onAfterDamage | ✅ | ✅ |
| | onAfterDeath | ✅ | ✅ |
| | onAllowDamage | ✅ | ✅ |
| | onAllowDeath | ✅ | ✅ |
| | onLivingDrops | - | ✅ |
| | onLivingFall | ✅ | ✅ |
| | onLivingHurt | ✅ | ✅ |
| | onLivingJump | - | ✅ |
| | onMobConversion | ✅ | ✅ |
| **Server Message** | onAllowChatMessage | ✅ | - |
| | onAllowCommandMessage | ✅ | - |
| | onAllowGameMessage | ✅ | - |
| | onChatMessage | ✅ | - |
| | onCommandMessage | ✅ | - |
| | onGameMessage | ✅ | - |
| | onServerChat | - | ✅ |
| **Server Mob Effect** | onAfterAdd | ✅ | - |
| | onAfterRemove | ✅ | - |
| | onAllowAdd | ✅ | - |
| | onAllowEarlyRemove | ✅ | - |
| | onBeforeAdd | ✅ | - |
| | onBeforeRemove | ✅ | - |
| | onMobEffectAdd | - | ✅ |
| | onMobEffectApplicable | - | ✅ |
| | onMobEffectExpire | - | ✅ |
| | onMobEffectRemove | - | ✅ |
| **Server Player** | onAfterPlayerRespawn | ✅ | ✅ |
| | onItemPickupPost | - | ✅ |
| | onItemPickupPre | - | ✅ |
| | onItemToss | - | ✅ |
| | onPickFromBlock | ✅ | ✅ |
| | onPickFromEntity | ✅ | ✅ |
| | onPlayerCopy | ✅ | ✅ |
| | onPlayerItemCrafted | - | ✅ |
| | onPlayerItemSmelted | - | ✅ |
| | onPlayerJoin | ✅ | ✅ |
| | onPlayerLeave | ✅ | ✅ |
| | onPlayerLoadFromFile | - | ✅ |
| | onPlayerPickupXp | ✅ | ✅ |
| | onPlayerSaveToFile | - | ✅ |
| | onPlayerSpawnPhantoms | - | ✅ |
| | onPlayerXpChange | ✅ | ✅ |
| | onPlayerXpLevelChange | ✅ | ✅ |
| | onStartTracking | - | ✅ |
| | onStopTracking | - | ✅ |
