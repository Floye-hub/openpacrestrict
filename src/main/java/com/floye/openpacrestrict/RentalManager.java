package com.floye.openpacrestrict;

import com.floye.openpacrestrict.util.EconomyHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RentalManager {
    public static final UUID RENTAL_ADMIN_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long2ObjectMap<RentalInfo> rentalChunks = new Long2ObjectOpenHashMap<>();

    // Durées
    private static final long RENT_DURATION_MS = Duration.ofDays(14).toMillis(); // +14 jours par /louer
    private static final long MAX_RENT_DURATION_MS = Duration.ofDays(30).toMillis(); // plafond à 30 jours depuis maintenant

    // Prix (configurable) — coût par JOUR (arrondi à l’entier)
    private static final int PRICE_PER_DAY = 100; // <-- MODIFIE ICI ton prix par jour
    private static final String CURRENCY = "$";   // symbole affiché

    private static final long MILLIS_PER_DAY = Duration.ofDays(1).toMillis();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

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

        // Si un claim existe déjà, on l'enlève (le chunk devient non-claim OPAC).
        IPlayerChunkClaim existingClaim = claimsManager.get(dimension, chunkPos);
        if (existingClaim != null) {
            claimsManager.unclaim(dimension, chunkPos.x, chunkPos.z);
        }

        // On ne reclâme PAS (on laisse non-claim OPAC), notre système gère les droits via mixins.
        rentalChunks.put(chunkPosLong, new RentalInfo(dimension, admin.getUuid(), null, 0L, 0L));

        admin.sendMessage(Text.literal("§aChunk mis en location (réservé). Position: " + chunkPos.x + ", " + chunkPos.z), false);
        return true;
    }

    /**
     * Loue ou prolonge la location avec paiement.
     * - Première location: +14j (coût = 14j).
     * - Prolongation: +14j, mais capée à (maintenant + 30j). On facture SEULEMENT le delta effectivement ajouté.
     * - Paiement arrondi à l’entier (pas de virgule).
     */
    public static void rentChunk(ServerPlayerEntity player, ServerWorld level) {
        var server = player.getServer();
        if (server == null) {
            player.sendMessage(Text.literal("§cErreur: serveur indisponible."), false);
            return;
        }

        ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
        long key = chunkPos.toLong();
        Identifier dimension = level.getRegistryKey().getValue();

        RentalInfo info = rentalChunks.get(key);
        if (info == null || !info.dimension.equals(dimension)) {
            player.sendMessage(Text.literal("§cCe chunk n'est pas disponible à la location."), false);
            return;
        }

        long now = System.currentTimeMillis();

        // Détermine l'expiration cible et le delta réellement ajoutable
        long base = (info.renter == null) ? now : Math.max(info.expiresAtMillis, now);
        long maxExpiry = now + MAX_RENT_DURATION_MS;
        long proposed = base + RENT_DURATION_MS;
        long newExpiry = Math.min(proposed, maxExpiry);

        long addedMs;
        if (info.renter == null) {
            // Première location: delta = newExpiry - now (normalement 14j)
            addedMs = newExpiry - now;
        } else {
            // Prolongation: delta = newExpiry - max(expire, now)
            addedMs = newExpiry - Math.max(info.expiresAtMillis, now);
        }

        if (info.renter != null && !info.renter.equals(player.getUuid())) {
            player.sendMessage(Text.literal("§cCe chunk est déjà loué par un autre joueur."), false);
            return;
        }

        if (addedMs <= 0) {
            String maxStr = DATE_FORMAT.format(Instant.ofEpochMilli(maxExpiry));
            player.sendMessage(Text.literal("§eDurée maximale atteinte (30 jours). Vous ne pouvez pas dépasser le " + maxStr + "."), false);
            return;
        }

        int cost = computeCostForMillis(addedMs);

        // Récupère le compte puis effectue le retrait et l'application en thread serveur
        EconomyHandler.getAccount(player.getUuid()).thenAccept(account -> {
            server.execute(() -> {
                if (account == null) {
                    player.sendMessage(Text.literal("§cEconomie indisponible. Réessayez plus tard."), false);
                    return;
                }

                double balance = EconomyHandler.getBalance(account);
                if (balance < cost) {
                    player.sendMessage(Text.literal("§cSolde insuffisant. Coût: §6" + cost + " " + CURRENCY + "§c, Solde: §6" + ((long) balance) + " " + CURRENCY), false);
                    return;
                }

                boolean ok = EconomyHandler.remove(account, cost);
                if (!ok) {
                    player.sendMessage(Text.literal("§cLe paiement a échoué. Réessayez plus tard."), false);
                    return;
                }

                // Revalider et appliquer (simple; faible risque de concurrence)
                RentalInfo current = rentalChunks.get(key);
                if (current == null || !current.dimension.equals(dimension)) {
                    // Situation anormale → rembourser
                    EconomyHandler.add(account, cost);
                    player.sendMessage(Text.literal("§cCe chunk n'est plus disponible. Remboursé."), false);
                    return;
                }
                if (current.renter != null && !current.renter.equals(player.getUuid())) {
                    EconomyHandler.add(account, cost);
                    player.sendMessage(Text.literal("§cCe chunk a été loué par un autre joueur entre-temps. Remboursé."), false);
                    return;
                }

                // Applique l'opération
                current.renter = player.getUuid();
                if (current.expiresAtMillis <= now) {
                    current.rentedAtMillis = now;
                }
                current.expiresAtMillis = newExpiry;
                rentalChunks.put(key, current);

                String expiryStr = DATE_FORMAT.format(Instant.ofEpochMilli(current.expiresAtMillis));
                player.sendMessage(Text.literal("§aPaiement: §6" + cost + " " + CURRENCY + "§a. Nouvelle expiration: " + expiryStr + "."), false);
            });
        });
    }

    // Appelé au démarrage du serveur: purge les locations expirées
    public static void expireOldRentals(MinecraftServer server) {
        long now = System.currentTimeMillis();
        int expiredCount = 0;

        for (RentalInfo info : rentalChunks.values()) {
            if (info.renter != null && info.expiresAtMillis > 0 && now >= info.expiresAtMillis) {
                info.renter = null;
                info.rentedAtMillis = 0L;
                info.expiresAtMillis = 0L;
                expiredCount++;
            }
        }

        if (expiredCount > 0) {
            OpenpacRestrict.LOGGER.info("Location: {} chunk(s) expiré(s) au démarrage. Libérés pour une nouvelle location.", expiredCount);
        } else {
            OpenpacRestrict.LOGGER.info("Location: aucune location expirée au démarrage.");
        }
    }

    public static boolean isRentalChunk(Identifier dimension, ChunkPos chunkPos) {
        RentalInfo info = rentalChunks.get(chunkPos.toLong());
        return info != null && info.dimension.equals(dimension);
    }

    public static boolean isRentedBy(Identifier dimension, ChunkPos chunkPos, UUID playerId) {
        RentalInfo info = rentalChunks.get(chunkPos.toLong());
        return info != null && info.dimension.equals(dimension) && playerId.equals(info.renter);
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

    private static int computeCostForMillis(long millis) {
        double days = (double) millis / MILLIS_PER_DAY;
        // Arrondi à l'entier (pas de virgule)
        long rounded = Math.round(days * PRICE_PER_DAY);
        return (int) Math.max(0, rounded);
    }

    public static class RentalInfo {
        public Identifier dimension;
        public UUID originalOwner;   // l’admin qui a mis en location
        public UUID renter;          // null si disponible
        public long rentedAtMillis;  // 0 si non loué
        public long expiresAtMillis; // 0 si non loué

        public RentalInfo(Identifier dimension, UUID originalOwner) {
            this(dimension, originalOwner, null, 0L, 0L);
        }

        public RentalInfo(Identifier dimension, UUID originalOwner, UUID renter) {
            this(dimension, originalOwner, renter, 0L, 0L);
        }

        public RentalInfo(Identifier dimension, UUID originalOwner, UUID renter, long rentedAtMillis, long expiresAtMillis) {
            this.dimension = dimension;
            this.originalOwner = originalOwner;
            this.renter = renter;
            this.rentedAtMillis = rentedAtMillis;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}