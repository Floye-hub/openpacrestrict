package com.floye.openpacrestrict;

import com.floye.openpacrestrict.command.RentChunkCommand;
import com.floye.openpacrestrict.command.RentalInfoCommand;
import com.floye.openpacrestrict.command.SetRentalCommand;
import com.floye.openpacrestrict.data.RentalDataStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class OpenpacRestrict implements ModInitializer {

	public static final String MOD_ID = "openpacrestrict";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("OpenpacRestrict initialized");
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ServerHelper.setServer(server);
			ClaimLogGate.start(60); // 60 secondes de délai
			LOGGER.info("[OpenpacRestrict] Claim logs are delayed for 60s after startup.");
		});

		// Décrémenter le délai à chaque tick serveur
		ServerTickEvents.START_SERVER_TICK.register(server -> ClaimLogGate.tick());

		// A l’arrêt: on nettoie
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ServerHelper.setServer(null);
			ClaimLogGate.reset();
		});

		ServerLifecycleEvents.SERVER_STARTING.register(ServerHelper::setServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerHelper.setServer(null));

		// Chargement + purge des locations expirées au démarrage
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RentalDataStorage.loadRentalData(server);
			RentalManager.expireOldRentals(server);
			// Sauvegarde immédiate après purge (optionnel mais conseillé)
			RentalDataStorage.saveRentalData(server);
		});
		// Sauvegarde à l'arrêt
		ServerLifecycleEvents.SERVER_STOPPING.register(RentalDataStorage::saveRentalData);

		// Enregistrement des commandes
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			RentChunkCommand.register(dispatcher);   // /louer (tous)
			SetRentalCommand.register(dispatcher);   // /location (OP niveau 2)
			RentalInfoCommand.register(dispatcher);
		});
		LOGGER.info("OpenpacRestrict: commands registered.");
	}

	// Méthode pour obtenir le gestionnaire de claims
	public static IServerClaimsManagerAPI getClaimsManager() {
		MinecraftServer server = getServer();
		if (server == null) {
			LOGGER.error("Impossible d'accéder au serveur Minecraft");
			return null;
		}
		return OpenPACServerAPI.get(server).getServerClaimsManager();
	}

	private static MinecraftServer getServer() {
		return ServerHelper.getServer();
	}

	// Bloque le claim si non adjacent ou si le chunk est réservé/location
	public static boolean shouldBlockClaim(ServerPlayerEntity player, RegistryKey<World> dimensionKey, ChunkPos chunkToClaim) {
		IServerClaimsManagerAPI claimsManager = getClaimsManager();
		if (claimsManager == null) {
			LOGGER.error("Impossible de récupérer ClaimsManager. Vérifiez l'intégration.");
			player.sendMessage(Text.literal("§cErreur interne : API OPAC non disponible."), false);
			return true;
		}

		Identifier dimensionId = dimensionKey.getValue();

		// Interdire le claim sur un chunk réservé/à louer par notre système
		if (com.floye.openpacrestrict.RentalManager.isRentalChunk(dimensionId, chunkToClaim)) {
			player.sendMessage(Text.literal("§cCe chunk est réservé à la location. Vous ne pouvez pas le claim."), false);
			return true;
		}

		UUID playerId = player.getUuid();

		// Si le joueur n'a aucun claim -> autoriser
		if (!claimsManager.hasPlayerInfo(playerId)) {
			return false;
		}

		var playerInfoAPI = claimsManager.getPlayerInfo(playerId);
		if (!(playerInfoAPI instanceof IServerPlayerClaimInfoAPI serverPlayerInfo)) {
			LOGGER.warn("L'API PlayerInfo pour {} n'est pas une instance de IServerPlayerClaimInfoAPI.", playerId);
			player.sendMessage(Text.literal("§cErreur : Type d'information de claim inattendu."), false);
			return true;
		}

		IPlayerDimensionClaimsAPI dimensionClaimsAPI = serverPlayerInfo.getDimension(dimensionId);
		if (dimensionClaimsAPI == null) {
			return false;
		}

		Set<ChunkPos> ownedChunks = new HashSet<>();
		dimensionClaimsAPI.getStream().forEach((IPlayerClaimPosListAPI posList) -> {
			posList.getStream().forEach(claimPos -> {
				int x = claimPos.x;
				int z = claimPos.z;
				ownedChunks.add(new ChunkPos(x, z));
			});
		});

		for (ChunkPos ownedChunk : ownedChunks) {
			int deltaX = Math.abs(ownedChunk.x - chunkToClaim.x);
			int deltaZ = Math.abs(ownedChunk.z - chunkToClaim.z);
			if (deltaX + deltaZ == 1) {
				return false; // adjacent -> autorisé
			}
		}

		player.sendMessage(Text.literal("§cVous ne pouvez claim que des chunks adjacents."), false);
		return true;
	}
}