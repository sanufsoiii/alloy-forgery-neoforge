package wraith.alloyforgery.forges;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import wraith.alloyforgery.AlloyForgery;
import java.util.Map;

public class FuelDataLoader extends SimpleJsonResourceReloadListener {

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final FuelDataLoader INSTANCE = new FuelDataLoader();

    private FuelDataLoader() {
        super(new Gson(), "alloy_forge_fuels");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        ForgeFuelRegistry.clear();

        prepared.forEach((identifier, jsonElement) -> {
            try {
                for (var entry : jsonElement.getAsJsonObject().get("fuels").getAsJsonArray()) {
                    ForgeFuelRegistry.register(GsonHelper.getAsItem(entry.getAsJsonObject(), "item").value(), ForgeFuelRegistry.ForgeFuelDefinition.fromJson(entry.getAsJsonObject()));
                }
            } catch (JsonSyntaxException e){
                LOGGER.error("An error has occurred during FuelDataLoader stage:", e);
            }
        });
    }

    public ResourceLocation getId() {
        return AlloyForgery.id("forge_fuel_loader");
    }
}
