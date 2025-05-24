// Fichier : src/main/java/com/floye/openpacrestrict/mixin/ServerPlayerInteractionManagerAccessor.java
package com.floye.openpacrestrict.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerInteractionManager.class)
public interface ServerPlayerInteractionManagerAccessor {
    @Accessor("player")
    ServerPlayerEntity getPlayer();
}
