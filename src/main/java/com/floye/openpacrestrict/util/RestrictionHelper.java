package com.floye.openpacrestrict.util;

import com.floye.openpacrestrict.RentalManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.parties.party.member.PartyMember;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.IServerParty;

import java.util.Set;
import java.util.UUID;

public final class RestrictionHelper {

    private RestrictionHelper() {}

    public static final Identifier EVENT_DIMENSION = Identifier.of("monde", "event");
    public static final Identifier AVENTURE_DIMENSION = Identifier.of("monde", "aventure");

    public static final Set<Identifier> ALLOWED_BLOCKS_BREAK = Set.of(
            Identifier.of("minecraft:air")
    );

    public static final Set<Identifier> ALLOWED_BLOCKS_BUILD = Set.of(
            Identifier.of("minecraft:air"),
            Identifier.of("cobblemon:pink_apricorn"),
            Identifier.of("cobblemon:black_apricorn"),
            Identifier.of("cobblemon:blue_apricorn"),
            Identifier.of("cobblemon:white_apricorn"),
            Identifier.of("cobblemon:yellow_apricorn"),
            Identifier.of("cobblemon:green_apricorn"),
            Identifier.of("cobblemon:red_apricorn")
    );

    /**
     * Autorise l'action si:
     * - le joueur est proprio du claim OPAC,
     * - le joueur est dans la même party que le proprio du claim,
     * - OU le chunk est loué par ce joueur (même sans claim OPAC),
     * - OU (cas de compatibilité) le claim OPAC appartient à RENTAL_ADMIN_UUID et le joueur est locataire.
     */
    public static boolean isInPlayerClaim(ServerPlayerEntity player, Identifier dimension, ChunkPos chunkPos) {
        if (player.getServer() == null) {
            return false;
        }

        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(player.getServer()).getServerClaimsManager();
        IPlayerChunkClaimAPI claim = claimsManager.get(dimension, chunkPos.x, chunkPos.z);

        UUID playerId = player.getUuid();

        // Aucun claim OPAC: chunk potentiellement "réservé"/loué via notre système
        if (claim == null) {
            return RentalManager.isRentedBy(dimension, chunkPos, playerId);
        }

        UUID owner = claim.getPlayerId();

        // 1) Proprio direct
        if (owner.equals(playerId)) {
            return true;
        }

        // 2) Même party que le propriétaire
        if (isInSameParty(player, owner, player.getServer())) {
            return true;
        }

        // 3) Compat: si on conservait un claim "admin technique"
        if (owner.equals(RentalManager.RENTAL_ADMIN_UUID) && RentalManager.isRentedBy(dimension, chunkPos, playerId)) {
            return true;
        }

        return false;
    }

    private static boolean isInSameParty(ServerPlayerEntity player, UUID claimOwnerId, net.minecraft.server.MinecraftServer server) {
        var partyManager = OpenPACServerAPI.get(server).getPartyManager();

        IServerParty<?, ?, ?> playerParty = (IServerParty<?, ?, ?>) partyManager.getPartyByMember(player.getUuid());
        if (playerParty == null) {
            return false;
        }

        PartyMember memberInfo = (PartyMember) playerParty.getMemberInfo(claimOwnerId);
        return memberInfo != null || playerParty.getOwner().getUUID().equals(claimOwnerId);
    }
}