// Fichier : src/main/java/com/floye/openpacrestrict/mixin/ServerPlayerInteractionManagerMixin.java
package com.floye.openpacrestrict.mixin;

import com.floye.openpacrestrict.util.RestrictionHelper;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class WorldBlockBreakMixin {

    @Inject(
            method = "tryBreakBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onTryBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity serverPlayer = ((ServerPlayerInteractionManagerAccessor) this).getPlayer();
        World world = serverPlayer.getWorld();

        if (world.isClient) {
            return;
        }
        if (serverPlayer.getCommandTags().contains("builder")) {
            return;
        }
        BlockState state = world.getBlockState(pos);
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());

        if (RestrictionHelper.ALLOWED_BLOCKS_BREAK.contains(blockId)) {
            return;
        }

        Identifier dimension = world.getRegistryKey().getValue();

        if (blockId.toString().startsWith("waystones:")) {
            serverPlayer.sendMessage(Text.literal("§cVous ne pouvez pas casser ce type de bloc dans monde:ressource."), true);
            cir.setReturnValue(false);
            return;
        }



        if (isRestrictedDimension(dimension, serverPlayer)) {
            serverPlayer.sendMessage(Text.literal("§cVous ne pouvez pas détruire dans cette dimension."), true);
            cir.setReturnValue(false);
        } else if (isInOverworld(dimension)) {
            if (!isInPlayerClaim(serverPlayer, dimension, pos)) {
                serverPlayer.sendMessage(Text.literal("§cVous ne pouvez pas détruire dans cette zone."), true);
                cir.setReturnValue(false);
            }
        }
    }

    private boolean isRestrictedDimension(Identifier dimension, ServerPlayerEntity serverPlayer) {
        return (dimension.equals(RestrictionHelper.EVENT_DIMENSION) || dimension.equals(RestrictionHelper.AVENTURE_DIMENSION))
                && !serverPlayer.getCommandTags().contains("builder");
    }

    private boolean isInOverworld(Identifier dimension) {
        return dimension.equals(World.OVERWORLD.getValue());
    }

    private boolean isInPlayerClaim(ServerPlayerEntity serverPlayer, Identifier dimension, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        return RestrictionHelper.isInPlayerClaim(serverPlayer, dimension, chunkPos);
    }
}
