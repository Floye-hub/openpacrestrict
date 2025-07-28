package com.floye.openpacrestrict.mixin;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.server.claims.ServerClaimsManager;
import com.floye.openpacrestrict.ClaimLogger;

import java.util.UUID;

@Mixin(ServerClaimsManager.class)
public abstract class ServerClaimsManagerMixin {

    private static final ClaimLogger LOGGER_CSV = new ClaimLogger();

    @Inject(method = "tryToClaimTyped", at = @At("RETURN"))
    private void onClaim(Identifier dimension, UUID playerId, int subConfigIndex, int fromX, int fromZ, int x, int z, boolean replace,
                         CallbackInfoReturnable<ClaimResult<PlayerChunkClaim>> cir) {
        ClaimResult<PlayerChunkClaim> result = cir.getReturnValue();
        if (result.getResultType() == ClaimResult.Type.SUCCESSFUL_CLAIM) {
            String playerName = playerId.toString(); // Optionnel : remplacer par nom du joueur réel si possible
            LOGGER_CSV.log(playerName, "CLAIM", x, z, dimension.toString());
        }
    }

    @Inject(method = "tryToUnclaimTyped", at = @At("RETURN"))
    private void onUnclaim(Identifier dimension, UUID playerId, int fromX, int fromZ, int x, int z, boolean replace,
                           CallbackInfoReturnable<ClaimResult<PlayerChunkClaim>> cir) {
        ClaimResult<PlayerChunkClaim> result = cir.getReturnValue();
        if (result.getResultType() == ClaimResult.Type.SUCCESSFUL_UNCLAIM) {
            String playerName = playerId.toString();
            LOGGER_CSV.log(playerName, "UNCLAIM", x, z, dimension.toString());
        }
    }
}
