// Fichier : src/main/java/com/floye/openpacrestrict/mixin/ServerPlayerInteractionManagerBlockPlaceMixin.java
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

    /**
     * Injecte du code lors de la tentative d'interaction avec un bloc par un joueur (inclut la pose).
     */
    @Inject(
            method = "interactBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;",
                    shift = At.Shift.BEFORE // Avant l'appel à useOnBlock
            ),
            cancellable = true
    )
    private void onPlayerInteractBlock(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient) {
            return;
        }

        // Si l'objet tenu n'est pas un bloc à placer, ou si le joueur est un "builder", on laisse passer
        if (!(stack.getItem() instanceof BlockItem) || player.getCommandTags().contains("builder")) {
            return;
        }

        Block blockToPlace = ((BlockItem) stack.getItem()).getBlock();
        Identifier blockId = Registries.BLOCK.getId(blockToPlace);

        // Si le bloc est dans la whitelist de construction, on autorise toujours
        if (RestrictionHelper.ALLOWED_BLOCKS_BUILD.contains(blockId)) {
            return;
        }

        Identifier dimension = world.getRegistryKey().getValue();

        // Interdire la construction dans les dimensions "event" et "aventure"
        if (dimension.equals(RestrictionHelper.EVENT_DIMENSION) || dimension.equals(RestrictionHelper.AVENTURE_DIMENSION)) {
            player.sendMessage(Text.literal("§cVous ne pouvez pas construire dans cette dimension."), true);
            cir.setReturnValue(ActionResult.FAIL); // Annule l'action
            return;
        }

        // Pour l'overworld, vérifier les claims
        if (dimension.equals(World.OVERWORLD.getValue())) {
            ChunkPos chunkPos = new ChunkPos(hitResult.getBlockPos()); // Position du bloc ciblé pour la pose

            if (!RestrictionHelper.isInPlayerClaim(player, World.OVERWORLD.getValue(), chunkPos)) {
                player.sendMessage(Text.literal("§cVous ne pouvez construire que dans vos claims."), true);
                cir.setReturnValue(ActionResult.FAIL); // Annule l'action
            }
        }
    }
}