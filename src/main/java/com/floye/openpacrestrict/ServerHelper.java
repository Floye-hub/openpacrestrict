package com.floye.openpacrestrict;

import net.minecraft.server.MinecraftServer;

public class ServerHelper {
    private static MinecraftServer server;

    public static void setServer(MinecraftServer serverInstance) {
        server = serverInstance;
    }

    public static MinecraftServer getServer() {
        return server;
    }
}