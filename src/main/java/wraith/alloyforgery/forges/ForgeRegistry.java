package wraith.alloyforgery.forges;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.wispforest.endec.Endec;
import io.wispforest.owo.moddata.ModDataConsumer;
import io.wispforest.owo.serialization.endec.MinecraftEndecs;
import io.wispforest.owo.util.TagInjector;
import wraith.alloyforgery.AlloyForgery;
import wraith.alloyforgery.ForgeControllerItem;
import wraith.alloyforgery.block.ForgeControllerBlock;
import java.util.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ForgeRegistry {

    public static Endec<ForgeDefinition> FORGE_DEFINITION = MinecraftEndecs.IDENTIFIER.xmap(
            identifier -> {
                return getForgeDefinition(identifier)
                        .orElseThrow(() -> new IllegalStateException("Unable to locate ForgerDefinition with Identifier: [ID: " + identifier + "]"));
            }, forgeDefinition -> {
                for (var entry : getForgeEntries()) {
                    if(entry.getValue() == forgeDefinition) return entry.getKey();
                }

                throw new IllegalStateException();
            }
    );

    public static final Gson GSON = new Gson();
    private static final ResourceLocation MINEABLE_PICKAXE = ResourceLocation.parse("mineable/pickaxe");

    private static final Map<ResourceLocation, ForgeDefinition> FORGE_DEFINITION_REGISTRY = new HashMap<>();
    private static final Map<ResourceLocation, Block> CONTROLLER_BLOCK_REGISTRY = new HashMap<>();

    // --- NeoForge: registries are only writable while RegisterEvent is being fired ---
    // Definitions whose material is already present when ModDataLoader runs (vanilla blocks, during mod construction)
    // are queued and registered at the start of the BLOCK RegisterEvent. Materials registered later by other mods
    // (e.g. mythicmetals:adamantite_block) trigger owo's runWhenPresent inside the RegisterEvent window and are
    // registered immediately.
    private static boolean registrationWindowOpen = false;
    private static final List<Runnable> PENDING_REGISTRATIONS = new ArrayList<>();
    private static final List<Block> PENDING_BLOCK_ENTITY_BLOCKS = new ArrayList<>();

    public static void openRegistrationWindow() {
        if (registrationWindowOpen) return;
        registrationWindowOpen = true;

        var pending = new ArrayList<>(PENDING_REGISTRATIONS);
        PENDING_REGISTRATIONS.clear();
        pending.forEach(Runnable::run);
    }

    public static void closeRegistrationWindow() {
        registrationWindowOpen = false;
        if (!PENDING_REGISTRATIONS.isEmpty()) {
            throw new IllegalStateException("[Alloy Forgery] " + PENDING_REGISTRATIONS.size() + " forge controller(s) could not be registered before registries froze");
        }
    }

    /**
     * Blocks to add to the forge controller block entity type ({@code BlockEntityTypeAddBlocksEvent}),
     * replaces Fabric's {@code BlockEntityType#addSupportedBlock}.
     */
    public static Block[] drainBlockEntityBlocks() {
        var blocks = PENDING_BLOCK_ENTITY_BLOCKS.toArray(Block[]::new);
        PENDING_BLOCK_ENTITY_BLOCKS.clear();
        return blocks;
    }

    static void registerDefinition(ResourceLocation forgeDefinitionId, ForgeDefinition definition) {
        if (!registrationWindowOpen) {
            PENDING_REGISTRATIONS.add(() -> registerDefinition(forgeDefinitionId, definition));
            return;
        }

        final var controllerBlock = new ForgeControllerBlock(definition);
        final var controllerBlockRegistryId = AlloyForgery.id(BuiltInRegistries.BLOCK.getKey(definition.material()).getPath() + "_forge_controller");

        Registry.register(BuiltInRegistries.BLOCK, controllerBlockRegistryId, controllerBlock);
        Registry.register(BuiltInRegistries.ITEM, controllerBlockRegistryId, new ForgeControllerItem(controllerBlock, new Item.Properties()));

        TagInjector.inject(BuiltInRegistries.BLOCK, MINEABLE_PICKAXE, controllerBlock);

        store(forgeDefinitionId, definition, controllerBlock);
    }

    public static Optional<ForgeDefinition> getForgeDefinition(ResourceLocation id) {
        return FORGE_DEFINITION_REGISTRY.containsKey(id) ? Optional.of(FORGE_DEFINITION_REGISTRY.get(id)) : Optional.empty();
    }

    public static Optional<Block> getControllerBlock(ResourceLocation id) {
        return FORGE_DEFINITION_REGISTRY.containsKey(id) ? Optional.of(CONTROLLER_BLOCK_REGISTRY.get(id)) : Optional.empty();
    }

    public static Set<Map.Entry<ResourceLocation, ForgeDefinition>> getForgeEntries(){
        return FORGE_DEFINITION_REGISTRY.entrySet();
    }

    public static Set<ResourceLocation> getForgeIds() {
        return FORGE_DEFINITION_REGISTRY.keySet();
    }

    public static List<Block> getControllerBlocks() {
        return CONTROLLER_BLOCK_REGISTRY.values().stream().toList();
    }

    private static void store(ResourceLocation id, ForgeDefinition definition, ForgeControllerBlock block) {
        FORGE_DEFINITION_REGISTRY.put(id, definition);
        CONTROLLER_BLOCK_REGISTRY.put(id, block);
        PENDING_BLOCK_ENTITY_BLOCKS.add(block);
    }

    public static final class Loader implements ModDataConsumer {

        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public String getDataSubdirectory() {
            return "alloy_forges";
        }

        @Override
        public void acceptParsedFile(ResourceLocation id, JsonObject object) {
            ForgeDefinition.loadAndEnqueue(id, object);
        }
    }

}
