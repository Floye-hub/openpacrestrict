package com.floye.openpacrestrict;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import xaero.pac.common.claims.player.IPlayerChunkClaim;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.claims.IServerClaimsManager;
import xaero.pac.common.server.claims.IServerDimensionClaimsManager;
import xaero.pac.common.server.claims.IServerRegionClaims;
import xaero.pac.common.server.claims.player.IServerPlayerClaimInfo;
import xaero.pac.common.claims.player.IPlayerClaimPosList;
import xaero.pac.common.claims.player.IPlayerDimensionClaims;
import xaero.pac.common.parties.party.IPartyPlayerInfo;
import xaero.pac.common.parties.party.ally.IPartyAlly;
import xaero.pac.common.parties.party.member.IPartyMember;
import xaero.pac.common.server.parties.party.IServerParty;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RentalManager {
    public static final UUID RENTAL_ADMIN_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long2ObjectMap<RentalInfo> rentalChunks = new Long2ObjectOpenHashMap<>();

    public static boolean setChunkForRent(ServerPlayerEntity admin, ServerWorld level) {
        ChunkPos chunkPos = new ChunkPos(admin.getBlockPos());
        long chunkPosLong = chunkPos.toLong();
        Identifier dimension = level.getRegistryKey().getValue();

        IServerData<
                IServerClaimsManager<
                        IPlayerChunkClaim,
                        IServerPlayerClaimInfo<IPlayerDimensionClaims<IPlayerClaimPosList>>,
                        IServerDimensionClaimsManager<IServerRegionClaims>
                        >,
                IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>
                > serverData = ServerData.from(admin.getServer());

        if (serverData == null) {
            admin.sendMessage(Text.literal("Erreur: Impossible d'accéder aux données du serveur OPAC."), false);
            return false;
        }

        IServerClaimsManager<
                IPlayerChunkClaim,
                IServerPlayerClaimInfo<IPlayerDimensionClaims<IPlayerClaimPosList>>,
                IServerDimensionClaimsManager<IServerRegionClaims>
                > claimsManager = serverData.getServerClaimsManager();

        IPlayerChunkClaim existingClaim = claimsManager.get(dimension, chunkPos);
        if (existingClaim != null) {
            claimsManager.unclaim(dimension, chunkPos.x, chunkPos.z);
        }

        claimsManager.claim(dimension, RENTAL_ADMIN_UUID, 0, chunkPos.x, chunkPos.z, false);
        rentalChunks.put(chunkPosLong, new RentalInfo(dimension, admin.getUuid()));
        admin.sendMessage(Text.literal("§aChunk mis en location! Position: " + chunkPos.x + ", " + chunkPos.z), false);
        return true;
    }

    public static boolean rentChunk(ServerPlayerEntity player, ServerWorld level) {
        ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
        long chunkPosLong = chunkPos.toLong();

        if (!rentalChunks.containsKey(chunkPosLong)) {
            player.sendMessage(Text.literal("§cCe chunk n'est pas disponible à la location."), false);
            return false;
        }

        RentalInfo info = rentalChunks.get(chunkPosLong);

        IServerData<
                IServerClaimsManager<
                        IPlayerChunkClaim,
                        IServerPlayerClaimInfo<IPlayerDimensionClaims<IPlayerClaimPosList>>,
                        IServerDimensionClaimsManager<IServerRegionClaims>
                        >,
                IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>
                > serverData = ServerData.from(player.getServer());

        if (serverData == null) {
            player.sendMessage(Text.literal("§cErreur: Impossible d'accéder aux données du serveur OPAC."), false);
            return false;
        }

        IServerClaimsManager<
                IPlayerChunkClaim,
                IServerPlayerClaimInfo<IPlayerDimensionClaims<IPlayerClaimPosList>>,
                IServerDimensionClaimsManager<IServerRegionClaims>
                > claimsManager = serverData.getServerClaimsManager();

        claimsManager.unclaim(info.dimension, chunkPos.x, chunkPos.z);
        claimsManager.claim(info.dimension, player.getUuid(), 0, chunkPos.x, chunkPos.z, false);
        rentalChunks.remove(chunkPosLong);

        player.sendMessage(Text.literal("§aVous avez loué ce chunk!"), false);
        return true;
    }

    public static Map<Long, RentalInfo> getRentalChunks() {
        Map<Long, RentalInfo> copy = new HashMap<>();
        rentalChunks.forEach(copy::put);
        return copy;
    }

    public static void setRentalChunks(Map<Long, RentalInfo> chunks) {
        rentalChunks.clear();
        chunks.forEach(rentalChunks::put);
    }

    public static class RentalInfo {
        public Identifier dimension;
        public UUID originalOwner;

        public RentalInfo(Identifier dimension, UUID originalOwner) {
            this.dimension = dimension;
            this.originalOwner = originalOwner;
        }
    }
}
