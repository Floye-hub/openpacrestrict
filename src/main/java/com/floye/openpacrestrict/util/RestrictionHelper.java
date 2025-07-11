package com.floye.openpacrestrict.util;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.server.network.ServerPlayerEntity;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.IServerParty;
import xaero.pac.common.server.parties.party.ServerParty;
import xaero.pac.common.parties.party.member.PartyMember;

import java.util.Set;
import java.util.UUID;

public final class RestrictionHelper {

    // Empêche l'instanciation de cette classe utilitaire
    private RestrictionHelper() {}

    public static final Identifier EVENT_DIMENSION = Identifier.of("monde", "event");
    public static final Identifier AVENTURE_DIMENSION = Identifier.of("monde", "aventure");

    // Liste des blocs autorisés pour détruire (break)
    public static final Set<Identifier> ALLOWED_BLOCKS_BREAK = Set.of(
            Identifier.of("minecraft:air")
    );

    // Liste des blocs autorisés pour construire (place)
    public static final Set<Identifier> ALLOWED_BLOCKS_BUILD = Set.of(
            Identifier.of("minecraft:air"),
            Identifier.of("cobblemon:pink_apricorn"),
            Identifier.of("cobblemon:black_apricorn"),
            Identifier.of("cobblemon:blue_apricorn"),
            Identifier.of("cobblemon:white_apricorn"),
            Identifier.of("cobblemon:yellow_apricorn"),
            Identifier.of("cobblemon:green_apricorn"),
            Identifier.of("cobblemon:red_apricorn")
            // Tu peux ajouter d'autres blocs spécifiques à la construction ici
    );

    /**
     * Vérifie si le joueur est dans son propre claim ou dans le claim d'un membre de son équipe.
     *
     * @param player Le joueur serveur à vérifier.
     * @param dimension L'identifiant de la dimension.
     * @param chunkPos La position du chunk.
     * @return true si le joueur est autorisé à interagir avec ce claim, sinon false.
     */
    public static boolean isInPlayerClaim(ServerPlayerEntity player, Identifier dimension, ChunkPos chunkPos) {
        if (player.getServer() == null) {
            return false;
        }

        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(player.getServer()).getServerClaimsManager();

        // Obtenir le claim à cette position
        IPlayerChunkClaimAPI claim = claimsManager.get(dimension, chunkPos.x, chunkPos.z);

        // Si pas de claim ou si le joueur est le propriétaire direct
        if (claim == null || claim.getPlayerId().equals(player.getUuid())) {
            return claim != null;
        }

        // Vérifier si le joueur est dans la même équipe que le propriétaire du claim
        return isInSameParty(player, claim.getPlayerId(), player.getServer());
    }

    /**
     * Vérifie si deux joueurs sont dans la même équipe (party).
     *
     * @param player Le joueur à vérifier.
     * @param claimOwnerId L'UUID du propriétaire du claim.
     * @param server Le serveur Minecraft.
     * @return true si les joueurs sont dans la même équipe, sinon false.
     */
    private static boolean isInSameParty(ServerPlayerEntity player, UUID claimOwnerId, net.minecraft.server.MinecraftServer server) {
        // Obtenir le gestionnaire de parties
        var partyManager = OpenPACServerAPI.get(server).getPartyManager();

        // Vérifier si le joueur est dans une équipe
        IServerParty<?, ?, ?> playerParty = (IServerParty<?, ?, ?>) partyManager.getPartyByMember(player.getUuid());
        if (playerParty == null) {
            return false;
        }

        // Vérifier si le propriétaire du claim est dans la même équipe
        PartyMember memberInfo = (PartyMember) playerParty.getMemberInfo(claimOwnerId);
        return memberInfo != null || playerParty.getOwner().getUUID().equals(claimOwnerId);
    }
}