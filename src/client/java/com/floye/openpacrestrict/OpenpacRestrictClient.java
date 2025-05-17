// src/main/java/com/votrenomdemod/ClaimAdjacencyLogic.java
package com.floye.openpacrestrict; // Remplacez par votre package

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xaero.pac.common.claims.api.IClaimsManagerAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI; // Nouvelle interface
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;   // Interface pour les claims d'une dimension
// Pas besoin d'importer XaeroPlayerClaimInfo ou XaeroPlayerDimensionClaimInfo directement
// si les interfaces fournissent tout ce dont nous avons besoin.
// CEPENDANT, pour obtenir les ChunkPos, il est probable que l'implémentation de IPlayerDimensionClaimsAPI (XaeroPlayerDimensionClaimInfo)
// soit nécessaire pour appeler getClaims().keySet(), car l'interface elle-même ne spécifie pas cette méthode.
// Si IPlayerDimensionClaimsAPI avait une méthode comme getClaimedChunkPositions(), ce serait idéal.
// Pour l'instant, nous allons supposer que nous devrons peut-être caster vers l'implémentation connue si l'interface est limitée.


import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class OpenpacRestrictClient {

	public static final Logger LOGGER = LoggerFactory.getLogger(YourModMainClass.MOD_ID); // Utilisez votre MOD_ID

	public static boolean shouldBlockClaim(ServerPlayer player, ResourceKey<Level> dimensionKey, ChunkPos chunkToClaim) {
		IClaimsManagerAPI claimsManager = OpenPartiesAndClaimsAPI.getClaimsManager();
		if (claimsManager == null) {
			LOGGER.error("OpenPartiesAndClaimsAPI.getClaimsManager() returned null! Adjacency check cannot proceed.");
			player.sendSystemMessage(Component.literal("§cErreur interne: Impossible de vérifier les claims (OPAC API non disponible)."), false);
			return true; // Bloquer par sécurité
		}

		UUID playerId = player.getUUID();
		ResourceLocation dimensionLocation = dimensionKey.location();

		if (!claimsManager.hasPlayerInfo(playerId)) {
			// Le joueur n'a aucune info de claim, donc aucun claim nulle part.
			// C'est son premier claim (ou le premier après une réinitialisation). On autorise.
			return false;
		}

		IPlayerClaimInfoAPI playerInfoAPI = claimsManager.getPlayerInfo(playerId);

		// Sur le serveur, playerInfoAPI devrait être une instance de IServerPlayerClaimInfoAPI.
		if (!(playerInfoAPI instanceof IServerPlayerClaimInfoAPI serverPlayerInfo)) {
			LOGGER.warn("PlayerInfoAPI for player {} is not an instance of IServerPlayerClaimInfoAPI. Actual type: {}. Adjacency check might be unreliable.",
					playerId, playerInfoAPI.getClass().getName());
			player.sendSystemMessage(Component.literal("§cErreur: Type d'information de claim inattendu."), false);
			return true; // Bloquer si l'API ne correspond pas aux attentes.
		}

		// Utiliser la méthode directe pour obtenir les claims de la dimension
		IPlayerDimensionClaimsAPI dimensionClaimsAPI = serverPlayerInfo.getDimension(dimensionLocation);

		if (dimensionClaimsAPI == null) {
			// Le joueur a des infos de claim (vérifié par hasPlayerInfo),
			// mais aucune pour CETTE dimension spécifique.
			// C'est donc son premier claim dans cette dimension. On autorise.
			LOGGER.debug("Player {} has no claims in dimension {}, allowing first claim at {}.", playerId, dimensionLocation, chunkToClaim);
			return false;
		}

		// Maintenant, nous devons obtenir l'ensemble des ChunkPos à partir de dimensionClaimsAPI.
		// L'interface IPlayerDimensionClaimsAPI ne spécifie pas de méthode pour obtenir directement Set<ChunkPos>.
		// Nous nous attendons à ce que l'implémentation soit XaeroPlayerDimensionClaimInfo,
		// qui a une méthode getClaims() retournant Map<ChunkPos, XaeroPlayerChunkClaim>.
		Set<ChunkPos> ownedChunksInDimension;
		if (dimensionClaimsAPI instanceof XaeroPlayerDimensionClaimInfo dimensionClaimsImpl) {
			// C'est l'implémentation concrète, on peut appeler ses méthodes.
			Map<ChunkPos, ?> claimsMap = dimensionClaimsImpl.getClaims(); // Le type de la valeur n'importe pas ici
			if (claimsMap == null || claimsMap.isEmpty()) {
				ownedChunksInDimension = Collections.emptySet();
			} else {
				ownedChunksInDimension = claimsMap.keySet();
			}
		} else {
			LOGGER.warn("IPlayerDimensionClaimsAPI for player {} in dimension {} is not an instance of XaeroPlayerDimensionClaimInfo. Actual type: {}. Cannot retrieve chunk positions.",
					playerId, dimensionLocation, dimensionClaimsAPI.getClass().getName());
			player.sendSystemMessage(Component.literal("§eAvertissement: Format de claim de dimension inconnu. Contactez un admin."), false);
			return true; // Bloquer si on ne peut pas obtenir les positions des chunks de manière fiable.
		}


		if (ownedChunksInDimension.isEmpty()) {
			// Ce cas devrait être couvert par dimensionClaimsAPI == null, mais double vérification.
			LOGGER.debug("Player {} has an empty claim set in dimension {} (after checks), allowing claim at {}.", playerId, dimensionLocation, chunkToClaim);
			return false;
		}

		LOGGER.debug("Player {} attempting to claim {} in {}. Existing claims: {}", playerId, chunkToClaim, dimensionLocation, ownedChunksInDimension.size());

		// Le joueur a déjà des claims dans cette dimension. Vérifions l'adjacence.
		for (ChunkPos ownedChunk : ownedChunksInDimension) {
			int deltaX = Math.abs(ownedChunk.x - chunkToClaim.x);
			int deltaZ = Math.abs(ownedChunk.z - chunkToClaim.z);

			if (deltaX + deltaZ == 1) { // Adjacence cardinale
				LOGGER.debug("Allowing claim for {} at {} due to adjacency with {}.", playerId, chunkToClaim, ownedChunk);
				return false; // Ne pas bloquer, un claim adjacent a été trouvé
			}
		}

		// Aucun claim adjacent trouvé.
		LOGGER.info("Blocking claim for {} at {} in {}: no adjacent claims found.", playerId, chunkToClaim, dimensionLocation);
		player.sendSystemMessage(Component.literal("§cVous ne pouvez claim que des chunks adjacents à vos claims existants dans cette dimension."), false);
		return true; // Bloquer le claim
	}
}