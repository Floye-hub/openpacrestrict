package com.floye.openpacrestrict;

import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Import OPAC API
import xaero.pac.common.claims.api.IClaimsManagerAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class OpenpacRestrictClient {

	public static final Logger LOGGER = LoggerFactory.getLogger(OpenpacRestrict.MOD_ID);

	// Méthode pour obtenir le gestionnaire de claims - vous devrez l'implémenter selon votre architecture
	private static IClaimsManagerAPI getClaimsManager() {
		// Cette partie dépend de comment vous accédez aux services du serveur
		// Voici quelques approches possibles :

		// 1. Si vous avez un singleton ou un accès à l'instance du serveur
		// return YourServerAccessClass.getClaimsManager();

		// 2. Si vous utilisez un système d'événements pour intercepter les claims
		// return eventContext.getClaimsManager();

		// Pour le moment, retournons null et vous pourrez adapter cette méthode
		LOGGER.warn("La méthode getClaimsManager() doit être implémentée correctement");
		return null;
	}

	public static boolean shouldBlockClaim(ServerPlayerEntity player, RegistryKey<World> dimensionKey, ChunkPos chunkToClaim) {
		// Récupérer l'instance de ClaimsManager
		IClaimsManagerAPI claimsManager = getClaimsManager();
		if (claimsManager == null) {
			LOGGER.error("Impossible de récupérer ClaimsManager. Vérifiez l'intégration.");
			player.sendMessage(Text.literal("§cErreur interne : API OPAC non disponible."), false);
			return true; // Par sécurité, on bloque le claim
		}

		UUID playerId = player.getUuid(); // Récupérer l'UUID du joueur

		// Vérifie si le joueur a des informations de claim
		if (!claimsManager.hasPlayerInfo(playerId)) {
			return false; // Aucun claim précédent, autorisé
		}

		IPlayerClaimInfoAPI playerInfoAPI = claimsManager.getPlayerInfo(playerId);
		if (!(playerInfoAPI instanceof IServerPlayerClaimInfoAPI serverPlayerInfo)) {
			LOGGER.warn("L'API PlayerInfo pour {} n'est pas une instance de IServerPlayerClaimInfoAPI.", playerId);
			player.sendMessage(Text.literal("§cErreur : Type d'information de claim inattendu."), false);
			return true; // Par sécurité, on bloque le claim
		}

		// Convertir RegistryKey<World> en Identifier
		Identifier dimensionId = dimensionKey.getValue();

		// Récupérer les claims dans la dimension actuelle
		IPlayerDimensionClaimsAPI dimensionClaimsAPI = serverPlayerInfo.getDimension(dimensionId);
		if (dimensionClaimsAPI == null) {
			return false; // Aucun claim dans cette dimension, autorisé
		}

		// Obtenir les positions des chunks déjà claimés par le joueur
		Set<ChunkPos> ownedChunks = new HashSet<>();
		dimensionClaimsAPI.getStream().forEach(posList -> {
			// Pour chaque liste de positions, extraire les coordonnées
			posList.getStream().forEach(claimPos -> {
				// Dans le contexte de l'API OPAC, nous devons obtenir les coordonnées X et Z
				// Nous supposons que l'objet retourné par posList.getStream() contient ces informations
				int x = claimPos.getX(); // Méthode pour obtenir X du claim
				int z = claimPos.getZ(); // Méthode pour obtenir Z du claim
				ownedChunks.add(new ChunkPos(x, z));
			});
		});

		for (ChunkPos ownedChunk : ownedChunks) {
			int deltaX = Math.abs(ownedChunk.getX() - chunkToClaim.getX());
			int deltaZ = Math.abs(ownedChunk.getZ() - chunkToClaim.getZ());

			if (deltaX + deltaZ == 1) { // Vérifie si le chunk est adjacent
				return false; // Autorisé : le chunk est adjacent à un claim existant
			}
		}

		// Si aucun claim adjacent n'est trouvé, on bloque le claim
		player.sendMessage(Text.literal("§cVous ne pouvez claim que des chunks adjacents."), false);
		return true;
	}
}