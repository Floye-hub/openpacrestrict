package com.floye.openpacrestrict.mixin;

import com.floye.openpacrestrict.util.RestrictionHelper;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class WorldBlockPlaceMixin {

    @Shadow public ServerPlayerEntity player;

    @Inject(
            method = "interactBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void onPlayerInteractBlock(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient) return;

        if (!(stack.getItem() instanceof BlockItem) || player.getCommandTags().contains("builder")) {
            return;
        }

        Identifier dimension = world.getRegistryKey().getValue();
        ChunkPos chunkPos = new ChunkPos(hitResult.getBlockPos());
        Block blockToPlace = ((BlockItem) stack.getItem()).getBlock();
        Identifier blockId = Registries.BLOCK.getId(blockToPlace);

        if (RestrictionHelper.ALLOWED_BLOCKS_BUILD.contains(blockId)) {
            return;
        }

        if (dimension.equals(World.OVERWORLD.getValue())) {
            if (!RestrictionHelper.isInPlayerClaim(player, dimension, chunkPos)) {
                player.sendMessage(Text.literal("§cVous ne pouvez construire que dans vos claims."), true);
                cir.setReturnValue(ActionResult.FAIL);
                cir.cancel();
            }
        } else {
            if (dimension.equals(RestrictionHelper.EVENT_DIMENSION) || dimension.equals(RestrictionHelper.AVENTURE_DIMENSION)) {
                player.sendMessage(Text.literal("§cVous ne pouvez pas construire dans cette dimension."), true);
                cir.setReturnValue(ActionResult.FAIL);
                cir.cancel();
            }
        }
    }
}
