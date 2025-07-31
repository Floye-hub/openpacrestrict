package com.floye.openpacrestrict.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.pac.common.claims.player.PlayerChunkClaim;
import xaero.pac.common.server.claims.ServerClaimsManager;
import com.floye.openpacrestrict.ClaimLogger;
import com.floye.openpacrestrict.ServerHelper;

import java.util.UUID;

@Mixin(ServerClaimsManager.class)
public abstract class ServerClaimsManagerMixin {

    private static final ClaimLogger LOGGER_CSV = new ClaimLogger();

    @Inject(method = "claim", at = @At("RETURN"))
    private void onClaimSuccess(Identifier dimension, UUID playerId, int subConfigIndex, int x, int z, boolean forceload,
                                CallbackInfoReturnable<PlayerChunkClaim> cir) {
        if (cir.getReturnValue() != null) {
            logPlayerAction(dimension, playerId, x, z, "CLAIM");
        }
    }

    @Inject(method = "unclaim", at = @At("HEAD"))
    private void onBeforeUnclaim(Identifier dimension, int x, int z, CallbackInfo ci) {
        PlayerChunkClaim previousClaim = ((ServerClaimsManager)(Object)this).get(dimension, x, z);
        if (previousClaim != null) {
            logPlayerAction(dimension, previousClaim.getPlayerId(), x, z, "UNCLAIM");
        }
    }

    private void logPlayerAction(Identifier dimension, UUID playerId, int chunkX, int chunkZ, String action) {
        MinecraftServer server = ServerHelper.getServer();
        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            String playerName = player != null ? player.getName().getString() : playerId.toString();
            LOGGER_CSV.log(playerName, action, chunkX, chunkZ, dimension.toString());
        }
    }
}