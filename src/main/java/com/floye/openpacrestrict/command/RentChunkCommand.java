package com.floye.openpacrestrict.command;

import com.floye.openpacrestrict.RentalManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class RentChunkCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("louer")
                        .requires(source -> source.hasPermissionLevel(0)) // autorisé pour tous
                        .executes(RentChunkCommand::rentChunk)
        );
    }

    private static int rentChunk(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();

            if (player == null) {
                context.getSource().sendError(Text.literal("Cette commande doit être exécutée par un joueur."));
                return 0;
            }

            ServerWorld world = (ServerWorld) player.getWorld();

            // Traitement asynchrone (paiement + location)
            RentalManager.rentChunk(player, world);

            // On renvoie OK tout de suite, les messages de résultat arrivent ensuite
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }
}