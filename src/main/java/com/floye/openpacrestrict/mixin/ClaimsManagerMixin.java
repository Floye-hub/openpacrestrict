package com.floye.openpacrestrict.mixin;

import com.floye.openpacrestrict.OpenpacRestrict;
import com.floye.openpacrestrict.ServerHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.pac.common.claims.ClaimsManager;
import xaero.pac.common.claims.player.PlayerChunkClaim;

import java.util.UUID;

@Mixin(ClaimsManager.class)
public class ClaimsManagerMixin {

	@Inject(method = "claim", at = @At("HEAD"), cancellable = true)
	private void onClaim(Identifier dimension, UUID id, int subConfigIndex, int x, int z, boolean forceload, CallbackInfoReturnable<PlayerChunkClaim> cir) {
		// Obtenir le serveur et le joueur
		if (ServerHelper.getServer() != null) {
			ServerPlayerEntity player = ServerHelper.getServer().getPlayerManager().getPlayer(id);
			if (player != null) {
				// Convertir en RegistryKey<World>
				RegistryKey<World> dimensionKey = RegistryKey.of(RegistryKeys.WORLD, dimension);

				// Vérifier si le claim est autorisé
				if (OpenpacRestrict.shouldBlockClaim(player, dimensionKey, new ChunkPos(x, z))) {
					// Annuler le claim
					cir.setReturnValue(null);
					cir.cancel();
				}
			}
		}
	}
}