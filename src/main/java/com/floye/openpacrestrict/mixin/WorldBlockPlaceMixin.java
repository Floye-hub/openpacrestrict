// Fichier : src/main/java/com/floye/openpacrestrict/mixin/WorldBlockPlaceMixin.java
package com.floye.openpacrestrict.mixin;

import com.floye.openpacrestrict.util.RestrictionHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldBlockPlaceMixin {

    /**
     * Injecte du code lors de la placement d'un bloc pour appliquer les restrictions.
     */
    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onBlockPlace(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;

        if (world.isClient) {
            return;
        }

        // On ne peut vérifier que s'il y a un joueur à proximité pour attribuer l'action
        ServerPlayerEntity player = (ServerPlayerEntity) world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false);
        if (player == null) {
            return;
        }
        if (player.getCommandTags().contains("builder")) {
            return;
        }
        Block block = state.getBlock();
        Identifier blockId = Registries.BLOCK.getId(block);

        // Si le bloc est dans la whitelist, on autorise toujours
        if (RestrictionHelper.ALLOWED_BLOCKS.contains(blockId)) {
            return;
        }

        Identifier dimension = world.getRegistryKey().getValue();

        // Interdire dans les dimensions "event" et "aventure" sauf pour les "builder"
        if (dimension.equals(RestrictionHelper.EVENT_DIMENSION) || dimension.equals(RestrictionHelper.AVENTURE_DIMENSION)) {
            if (!player.getCommandTags().contains("builder")) {
                player.sendMessage(Text.literal("§cVous ne pouvez pas construire dans cette dimension."), true);
                cir.setReturnValue(false);
                player.currentScreenHandler.syncState();
            }
            return; // On ne vérifie pas les claims dans ces mondes
        }

        // Pour l'overworld, vérifier les claims
        if (dimension.equals(World.OVERWORLD.getValue())) {
            ChunkPos chunkPos = new ChunkPos(pos);

            if (!RestrictionHelper.isInPlayerClaim(player, World.OVERWORLD.getValue(), chunkPos)) {
                player.sendMessage(Text.literal("§cVous ne pouvez construire que dans vos claims."), true);
                cir.setReturnValue(false);
                player.currentScreenHandler.syncState();
            }
        }
    }
}