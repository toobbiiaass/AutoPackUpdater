package tobias.Utils;

import com.google.gson.*;
import tobias.Objects.RenameEntry;

import java.lang.reflect.Type;

public class RenameEntryDeserializer implements JsonDeserializer<RenameEntry> {

    @Override
    public RenameEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        RenameEntry entry = new RenameEntry();
        entry.old = obj.get("old").getAsString();
        entry.neww = obj.get("new").getAsString();
        return entry;
    }
}
