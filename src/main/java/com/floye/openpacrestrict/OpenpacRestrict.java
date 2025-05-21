package com.floye.openpacrestrict;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
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
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class OpenpacRestrict implements ModInitializer {

	public static final String MOD_ID = "openpacrestrict";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Code d'initialisation du mod
		LOGGER.info("OpenpacRestrict initialized");

		ServerLifecycleEvents.SERVER_STARTING.register(ServerHelper::setServer);

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ServerHelper.setServer(null);
		});
		// Ici vous pourriez enregistrer des événements pour intercepter les claims
		// et utiliser la méthode shouldBlockClaim
	}

	// Méthode pour obtenir le gestionnaire de claims - vous devrez l'implémenter selon votre architecture
	public static IServerClaimsManagerAPI getClaimsManager() {
		MinecraftServer server = getServer(); // Vous devez implémenter cette méthode
		if (server == null) {
			LOGGER.error("Impossible d'accéder au serveur Minecraft");
			return null;
		}

		return xaero.pac.common.server.api.OpenPACServerAPI.get(server).getServerClaimsManager();
	}

	private static MinecraftServer getServer() {
		// Dans Fabric, vous pouvez obtenir le serveur de différentes façons
		// Une option courante est via ServerLifecycleEvents
		return ServerHelper.getServer(); // Vous devrez créer cette classe helper
	}
	public static boolean shouldBlockClaim(ServerPlayerEntity player, RegistryKey<World> dimensionKey, ChunkPos chunkToClaim) {
		// Récupérer l'instance de ClaimsManager
		IServerClaimsManagerAPI claimsManager = getClaimsManager();
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
				int x = claimPos.x; // Méthode pour obtenir X du claim
				int z = claimPos.z; // Méthode pour obtenir Z du claim
				ownedChunks.add(new ChunkPos(x, z));
			});
		});

		for (ChunkPos ownedChunk : ownedChunks) {
			int deltaX = Math.abs(ownedChunk.x - chunkToClaim.x);
			int deltaZ = Math.abs(ownedChunk.z - chunkToClaim.z);

			if (deltaX + deltaZ == 1) { // Vérifie si le chunk est adjacent
				return false; // Autorisé : le chunk est adjacent à un claim existant
			}
		}

		// Si aucun claim adjacent n'est trouvé, on bloque le claim
		player.sendMessage(Text.literal("§cVous ne pouvez claim que des chunks adjacents."), false);
		return true;
	}
}