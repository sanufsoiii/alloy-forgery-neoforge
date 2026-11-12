package wraith.alloyforgery.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wraith.alloyforgery.AlloyForgery;
import wraith.alloyforgery.data.RecipeTagLoader;

import java.util.List;

@Mod(value = AlloyForgery.MOD_ID, dist = Dist.CLIENT)
public class AlloyForgeryClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("alloyforgery-port");
    private static boolean titleScreenReported = false;

    public AlloyForgeryClient(IEventBus modBus) {
        modBus.<RegisterMenuScreensEvent>addListener(event -> event.register(AlloyForgery.ALLOY_FORGE_SCREEN_HANDLER_TYPE, AlloyForgeScreen::new));

        AlloyForgery.CHANNEL.registerClientbound(RecipeTagLoader.TagPacket.class, RecipeTagLoader.TagPacket::handlePacket);

        NeoForge.EVENT_BUS.<ScreenEvent.Opening>addListener(AlloyForgeryClient::onScreenOpening);
    }

    // Port boot self-check: reports reaching the title screen and the registered forge controllers.
    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (titleScreenReported || !(event.getNewScreen() instanceof TitleScreen)) return;
        titleScreenReported = true;

        List<ResourceLocation> controllers = BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> id.getNamespace().equals(AlloyForgery.MOD_ID) && id.getPath().endsWith("_forge_controller"))
                .sorted()
                .toList();

        LOGGER.info("[alloyforgery-port] TITLE_SCREEN_REACHED");
        LOGGER.info("[alloyforgery-port] FORGE_CONTROLLERS={} {}", controllers.size(), controllers);

        if (Boolean.getBoolean("alloyforgery.port.exitOnTitle")) {
            scheduleStop(Minecraft.getInstance(), 20);
        }
    }

    private static void scheduleStop(Minecraft client, int ticksLeft) {
        client.execute(() -> {
            if (ticksLeft <= 0) {
                client.stop();
            } else {
                scheduleStop(client, ticksLeft - 1);
            }
        });
    }
}
