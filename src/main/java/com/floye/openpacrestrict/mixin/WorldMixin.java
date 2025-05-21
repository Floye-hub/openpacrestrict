package com.floye.openpacrestrict.mixin;

import com.floye.openpacrestrict.OpenpacRestrict;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
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
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import java.util.Objects;

@Mixin(World.class)
public class WorldMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void onBlockPlace(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;

        // Vérifier si nous sommes dans l'overworld
        if (!world.isClient && world.getRegistryKey() == World.OVERWORLD) {
            if (world.getBlockState(pos) != state) { // S'assurer qu'il s'agit d'un changement de bloc
                // Trouver le joueur responsable (peut nécessiter d'autres mécanismes selon votre implémentation)
                if (world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false) instanceof ServerPlayerEntity player) {
                    ChunkPos chunkPos = new ChunkPos(pos);

                    if (!isInPlayerClaim(player, World.OVERWORLD.getValue(), chunkPos)) {
                        player.sendMessage(Text.literal("§cVous ne pouvez pas construire en dehors d'un claim."), true);
                        cir.setReturnValue(false);
                        cir.cancel();
                    }
                }
            }
        }
    }

    @Inject(method = "breakBlock",
            at = @At("HEAD"),
            cancellable = true)
    private void onBlockBreak(BlockPos pos, boolean drop, CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;

        // Vérifier si nous sommes dans l'overworld
        if (!world.isClient && world.getRegistryKey() == World.OVERWORLD) {
            // Trouver le joueur responsable
            if (world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false) instanceof ServerPlayerEntity player) {
                ChunkPos chunkPos = new ChunkPos(pos);

                if (!isInPlayerClaim(player, World.OVERWORLD.getValue(), chunkPos)) {
                    player.sendMessage(Text.literal("§cVous ne pouvez pas casser de blocs en dehors d'un claim."), true);
                    cir.setReturnValue(false);
                    cir.cancel();
                }
            }
        }
    }

    private boolean isInPlayerClaim(ServerPlayerEntity player, Identifier dimension, ChunkPos chunkPos) {
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(Objects.requireNonNull(player.getServer())).getServerClaimsManager();

        // Obtenir le claim à cette position
        IPlayerChunkClaimAPI claim = claimsManager.get(dimension, chunkPos.x, chunkPos.z);

        // Vérifier si le chunk est revendiqué et si le joueur en est le propriétaire
        return claim != null && claim.getPlayerId().equals(player.getUuid());
    }
}