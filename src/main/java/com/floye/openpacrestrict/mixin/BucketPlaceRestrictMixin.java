package com.floye.openpacrestrict.mixin;

import com.floye.openpacrestrict.util.RestrictionHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public class BucketPlaceRestrictMixin {

    private static final String REQUIRED_TAG = "builder";
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (world.isClient) return;

        if (!(user instanceof ServerPlayerEntity player)) return;
        if (player.getCommandTags().contains(REQUIRED_TAG)) {
            return;
        }
        ItemStack stack = player.getStackInHand(hand);
        BlockHitResult hit = (BlockHitResult) world.raycast(new RaycastContext(
                player.getEyePos(),
                player.getEyePos().add(player.getRotationVec(1.0F).multiply(5.0D)),
                ShapeType.OUTLINE,
                FluidHandling.SOURCE_ONLY,
                player
        ));

        if (hit == null) return;

        Identifier dimension = world.getRegistryKey().getValue();
        ChunkPos chunkPos = new ChunkPos(hit.getBlockPos());

        if (dimension.equals(World.OVERWORLD.getValue())) {
            if (!RestrictionHelper.isInPlayerClaim(player, dimension, chunkPos)) {
                player.sendMessage(Text.literal("§cVous ne pouvez poser de seau que dans vos claims dans le royaume."), true);
                cir.setReturnValue(TypedActionResult.fail(stack));
                cir.cancel();
            }
        }
    }
}
