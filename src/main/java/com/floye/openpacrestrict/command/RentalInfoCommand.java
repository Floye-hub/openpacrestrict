package com.floye.openpacrestrict.command;

import com.floye.openpacrestrict.RentalManager;
import com.floye.openpacrestrict.RentalManager.RentalInfo;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.literal;

public class RentalInfoCommand {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("locationinfo")
                        .requires(source -> source.hasPermissionLevel(0)) // accessible à tous
                        .executes(RentalInfoCommand::showInfoCurrentChunk)
        );
        // Alias pratique
        dispatcher.register(
                literal("louerinfo")
                        .requires(source -> source.hasPermissionLevel(0))
                        .executes(RentalInfoCommand::showInfoCurrentChunk)
        );
    }

    private static int showInfoCurrentChunk(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player == null) {
                context.getSource().sendError(Text.literal("Cette commande doit être exécutée par un joueur."));
                return 0;
            }

            ServerWorld world = (ServerWorld) player.getWorld();
            var dimension = world.getRegistryKey().getValue();
            ChunkPos chunkPos = new ChunkPos(player.getBlockPos());

            Map<Long, RentalInfo> map = RentalManager.getRentalChunks();
            RentalInfo info = map.get(chunkPos.toLong());

            if (info == null || !info.dimension.equals(dimension)) {
                player.sendMessage(Text.literal("§eCe chunk n'est pas géré par le système de location."), false);
                return 1;
            }

            if (info.renter == null) {
                player.sendMessage(Text.literal("§eCe chunk est disponible à la location. Utilisez §a/louer§e pour louer 14 jours (plafond 30 jours)."), false);
                return 1;
            }

            long now = System.currentTimeMillis();

            if (!info.renter.equals(player.getUuid())) {
                String expiryStrOther = info.expiresAtMillis > 0 ? DATE_FORMAT.format(Instant.ofEpochMilli(info.expiresAtMillis)) : "inconnue";
                player.sendMessage(Text.literal("§cCe chunk est loué par un autre joueur. Expire le: " + expiryStrOther), false);
                return 1;
            }

            if (info.expiresAtMillis <= 0) {
                player.sendMessage(Text.literal("§eAucune date d'expiration enregistrée pour cette location."), false);
                return 1;
            }

            long timeLeft = info.expiresAtMillis - now;
            String expiryStr = DATE_FORMAT.format(Instant.ofEpochMilli(info.expiresAtMillis));

            if (timeLeft <= 0) {
                player.sendMessage(Text.literal("§cLocation expirée le " + expiryStr + ". Les droits seront retirés au prochain reboot (01h/13h). Vous pouvez relancer §a/louer§c pour repartir sur 14 jours (plafond 30 jours)."), false);
            } else {
                player.sendMessage(Text.literal("§aTemps restant: " + formatDuration(timeLeft) + " (expire le " + expiryStr + ")."), false);
                player.sendMessage(Text.literal("§7Astuce: utilisez §a/louer§7 pour prolonger de 14 jours, sans dépasser 30 jours max."), false);
            }
            return 1;

        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("j ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m");
        return sb.toString().trim();
    }
}