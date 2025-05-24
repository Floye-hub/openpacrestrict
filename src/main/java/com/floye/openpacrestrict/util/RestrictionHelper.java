// Fichier : src/main/java/com/floye/openpacrestrict/util/RestrictionHelper.java
package com.floye.openpacrestrict.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import java.util.Objects;
import java.util.Set;

/**
 * Classe utilitaire contenant la logique et les constantes partagées
 * pour les restrictions de pose et de destruction de blocs.
 */
public final class RestrictionHelper {

    // Empêche l'instanciation de cette classe utilitaire
    private RestrictionHelper() {}

    public static final Identifier EVENT_DIMENSION = Identifier.of("monde", "event");
    public static final Identifier AVENTURE_DIMENSION = Identifier.of("monde", "aventure");

    // Whitelist des blocs dont l'interaction est toujours autorisée
    public static final Set<Identifier> ALLOWED_BLOCKS = Set.of(
            Identifier.of("minecraft:air"),
            Identifier.of("cobblemon:pink_apricorn")
    );

    /**
     * Vérifie si le joueur est dans son propre claim.
     *
     * @param player Le joueur serveur à vérifier.
     * @param dimension L'identifiant de la dimension.
     * @param chunkPos La position du chunk.
     * @return true si le joueur est le propriétaire du claim à cette position, sinon false.
     */
    public static boolean isInPlayerClaim(ServerPlayerEntity player, Identifier dimension, ChunkPos chunkPos) {
        if (player.getServer() == null) {
            return false;
        }
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(player.getServer()).getServerClaimsManager();

        // Obtenir le claim à cette position
        IPlayerChunkClaimAPI claim = claimsManager.get(dimension, chunkPos.x, chunkPos.z);

        // Vérifier si le chunk est revendiqué et si le joueur en est le propriétaire
        return claim != null && claim.getPlayerId().equals(player.getUuid());
    }
}