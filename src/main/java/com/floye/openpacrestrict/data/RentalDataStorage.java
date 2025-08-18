package com.floye.openpacrestrict.data;

import com.floye.openpacrestrict.OpenpacRestrict;
import com.floye.openpacrestrict.RentalManager;
import com.floye.openpacrestrict.RentalManager.RentalInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RentalDataStorage {
    private static final String RENTAL_DATA_FILE = "rental_chunks.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void saveRentalData(MinecraftServer server) {
        File dataDirectory = new File(String.valueOf(server.getRunDirectory()), "world/data");
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }

        File dataFile = new File(dataDirectory, RENTAL_DATA_FILE);

        try (FileWriter writer = new FileWriter(dataFile)) {
            JsonArray rentalArray = new JsonArray();
            Map<Long, RentalInfo> rentalChunks = RentalManager.getRentalChunks();

            rentalChunks.forEach((chunkPos, info) -> {
                JsonObject rentalObject = new JsonObject();
                rentalObject.addProperty("chunkPos", chunkPos);
                rentalObject.addProperty("dimension", info.dimension.toString());
                rentalObject.addProperty("originalOwner", info.originalOwner.toString());
                rentalObject.addProperty("renter", info.renter != null ? info.renter.toString() : "");
                rentalObject.addProperty("rentedAtMillis", info.rentedAtMillis);
                rentalObject.addProperty("expiresAtMillis", info.expiresAtMillis);
                rentalArray.add(rentalObject);
            });

            JsonObject root = new JsonObject();
            root.add("rentalChunks", rentalArray);

            GSON.toJson(root, writer);
            OpenpacRestrict.LOGGER.info("Rental data saved successfully.");
        } catch (IOException e) {
            OpenpacRestrict.LOGGER.error("Failed to save rental data", e);
        }
    }

    public static void loadRentalData(MinecraftServer server) {
        File dataDirectory = new File(String.valueOf(server.getRunDirectory()), "world/data");
        File dataFile = new File(dataDirectory, RENTAL_DATA_FILE);

        if (!dataFile.exists()) {
            OpenpacRestrict.LOGGER.info("No rental data file found, starting with empty data.");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray rentalArray = root.getAsJsonArray("rentalChunks");

            Map<Long, RentalInfo> rentalChunks = new HashMap<>();

            rentalArray.forEach(element -> {
                JsonObject rentalObject = element.getAsJsonObject();
                long chunkPos = rentalObject.get("chunkPos").getAsLong();
                Identifier dimension = Identifier.of(rentalObject.get("dimension").getAsString());
                UUID originalOwner = UUID.fromString(rentalObject.get("originalOwner").getAsString());

                String renterStr = rentalObject.has("renter") ? rentalObject.get("renter").getAsString() : "";
                UUID renter = renterStr == null || renterStr.isEmpty() ? null : UUID.fromString(renterStr);

                long rentedAt = rentalObject.has("rentedAtMillis") ? rentalObject.get("rentedAtMillis").getAsLong() : 0L;
                long expiresAt = rentalObject.has("expiresAtMillis") ? rentalObject.get("expiresAtMillis").getAsLong() : 0L;

                rentalChunks.put(chunkPos, new RentalInfo(dimension, originalOwner, renter, rentedAt, expiresAt));
            });

            RentalManager.setRentalChunks(rentalChunks);
            OpenpacRestrict.LOGGER.info("Rental data loaded successfully.");
        } catch (IOException e) {
            OpenpacRestrict.LOGGER.error("Failed to load rental data", e);
        }
    }
}