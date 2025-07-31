package com.floye.openpacrestrict.command;

import com.floye.openpacrestrict.RentalManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class SetRentalCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("location")
                        .requires(source -> source.hasPermissionLevel(2)) // Niveau admin
                        .executes(SetRentalCommand::setRental)
        );
    }

    private static int setRental(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();

            if (player == null) {
                context.getSource().sendError(Text.literal("Cette commande doit être exécutée par un joueur."));
                return 0;
            }

            ServerWorld world = (ServerWorld) player.getWorld();

            boolean success = RentalManager.setChunkForRent(player, world);
            return success ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }
}
