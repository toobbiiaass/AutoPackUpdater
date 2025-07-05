package tobias.Utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import tobias.Objects.ArmorCategory;
import tobias.Objects.GuiSplitCategory;
import tobias.Objects.ParticleEntry;
import tobias.Objects.RenameEntry;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JsonUtil {
    public static List<RenameEntry> loadRenames(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(RenameEntry.class, new RenameEntryDeserializer())
                    .create();

            JsonObject root = gson.fromJson(reader, JsonObject.class);
            JsonArray renameArray = root.getAsJsonArray("rename");

            Type listType = new TypeToken<List<RenameEntry>>() {}.getType();
            List<RenameEntry> list = gson.fromJson(renameArray, listType);
            return list;

        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Fehler beim Lesen der JSON-Datei: " + e.getMessage());
            return null;
        }
    }

    public static List<ParticleEntry> loadParticles(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject root = gson.fromJson(reader, JsonObject.class);

            JsonObject allParticles = root.getAsJsonObject("allParticles");
            JsonArray particleArray = allParticles.getAsJsonArray("particle");

            Type listType = new TypeToken<List<ParticleEntry>>() {}.getType();
            List<ParticleEntry> particles = gson.fromJson(particleArray, listType);
            return particles;

        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Fehler beim Lesen der Partikel-JSON: " + e.getMessage());
            return null;
        }
    }
    public static Map<String, GuiSplitCategory> loadGuiSplits(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, GuiSplitCategory>>(){}.getType();
            return gson.fromJson(reader, type);
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Fehler beim Lesen der GUI-Splits-Datei: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    public static Map<String, ArmorCategory> loadArmorMappings(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, ArmorCategory>>() {}.getType();
            return gson.fromJson(reader, type);
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Fehler beim Lesen der Armor-Mapping-Datei: " + e.getMessage());
            return Collections.emptyMap();
        }
    }



}
