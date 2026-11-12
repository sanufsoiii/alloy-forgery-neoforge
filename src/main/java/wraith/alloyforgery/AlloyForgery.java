package wraith.alloyforgery;

import io.wispforest.owo.moddata.ModDataLoader;
import io.wispforest.owo.network.OwoNetChannel;
import io.wispforest.owo.particles.ClientParticles;
import io.wispforest.owo.particles.systems.ParticleSystem;
import io.wispforest.owo.particles.systems.ParticleSystemController;
import io.wispforest.owo.util.OwoFreezer;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import wraith.alloyforgery.block.ForgeControllerBlockEntity;
import wraith.alloyforgery.compat.AlloyForgeryConfig;
import wraith.alloyforgery.data.AlloyForgeryGlobalRemaindersLoader;
import wraith.alloyforgery.data.RecipeTagLoader;
import wraith.alloyforgery.forges.ForgeRegistry;
import wraith.alloyforgery.forges.FuelDataLoader;
import wraith.alloyforgery.recipe.*;
import wraith.alloyforgery.utils.RecipeInjector;

@Mod(AlloyForgery.MOD_ID)
public class AlloyForgery {

    public static final OwoNetChannel CHANNEL = OwoNetChannel.create(id("main"));

    public static final String MOD_ID = "alloy_forgery";

    public static final AlloyForgeryConfig CONFIG = AlloyForgeryConfig.createAndLoad();

    // Created inside RegisterEvent: BlockEntityType creates an intrusive registry holder, which needs an unfrozen registry.
    // Its valid blocks are added in BlockEntityTypeAddBlocksEvent (replaces Fabric's addSupportedBlock).
    public static BlockEntityType<ForgeControllerBlockEntity> FORGE_CONTROLLER_BLOCK_ENTITY;
    public static MenuType<AlloyForgeScreenHandler> ALLOY_FORGE_SCREEN_HANDLER_TYPE;

    private static final ParticleSystemController CONTROLLER = new ParticleSystemController(id("particles"));
    public static final ParticleSystem<Direction> FORGE_PARTICLES = CONTROLLER.register(Direction.class, (world, pos, facing) -> {
        final Vec3 particleSide = pos.add(0.5 + facing.getStepX() * 0.515, 0.25, 0.5 + facing.getStepZ() * 0.515);
        ClientParticles.spawnPrecise(ParticleTypes.FLAME, world, particleSide,
                facing.getStepZ() * 0.65,
                0.175,
                facing.getStepX() * 0.65);

        ClientParticles.spawnPrecise(ParticleTypes.SMOKE, world, particleSide,
                facing.getStepZ() * 0.65,
                0.175,
                facing.getStepX() * 0.65);
    });

    public AlloyForgery(IEventBus modBus, ModContainer container) {
        // Reload listeners (Fabric: ResourceManagerHelper.registerReloadListener). NeoForge appends these after the
        // vanilla listeners (tags, recipes), so RecipeTagLoader still runs after tags as on Fabric.
        NeoForge.EVENT_BUS.<AddReloadListenerEvent>addListener(event -> {
            event.addListener(new AlloyForgeryGlobalRemaindersLoader());
            event.addListener(FuelDataLoader.INSTANCE);
            event.addListener(RECIPE_TAG_LOADER);
        });

        RECIPE_TAG_LOADER.initEvents();

        CHANNEL.registerClientboundDeferred(RecipeTagLoader.TagPacket.class);

        RecipeInjector.initEvents();
        RecipeInjector.ADD_RECIPES.register(new BlastFurnaceRecipeAdapter());

        // Forge definitions are mod data (data/<modid>/alloy_forges in every mod jar), read once at startup.
        // Must run before any mod registers its blocks so that owo's runWhenPresent sees materials of other mods
        // (e.g. Mythic Metals) being registered. Controllers for already present (vanilla) materials are queued
        // in ForgeRegistry and registered in the BLOCK RegisterEvent below.
        ModDataLoader.load(ForgeRegistry.Loader.INSTANCE);

        modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                ForgeRegistry.openRegistrationWindow();
            }
        });

        modBus.<RegisterEvent>addListener(event -> {
            event.register(Registries.MENU, helper -> {
                ALLOY_FORGE_SCREEN_HANDLER_TYPE = Registry.register(BuiltInRegistries.MENU, id("alloy_forge"), new MenuType<>(AlloyForgeScreenHandler::new, FeatureFlags.DEFAULT_FLAGS));
            });

            event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
                FORGE_CONTROLLER_BLOCK_ENTITY = BlockEntityType.Builder.<ForgeControllerBlockEntity>of(ForgeControllerBlockEntity::new).build(null);
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("forge_controller"), FORGE_CONTROLLER_BLOCK_ENTITY);
            });

            event.register(Registries.RECIPE_TYPE, helper -> Registry.register(BuiltInRegistries.RECIPE_TYPE, AlloyForgeRecipe.Type.ID, AlloyForgeRecipe.Type.INSTANCE));
            event.register(Registries.RECIPE_SERIALIZER, helper -> Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, AlloyForgeRecipe.Type.ID, AlloyForgeRecipeSerializer.INSTANCE));

            event.register(Registries.CREATIVE_MODE_TAB, helper -> AlloyForgeryItemGroup.GROUP.initialize());
        });

        modBus.<BlockEntityTypeAddBlocksEvent>addListener(event -> event.modify(FORGE_CONTROLLER_BLOCK_ENTITY, ForgeRegistry.drainBlockEntityBlocks()));

        modBus.<FMLCommonSetupEvent>addListener(event -> ForgeRegistry.closeRegistrationWindow());

        modBus.<RegisterCapabilitiesEvent>addListener(event -> {
            // Fabric: FluidStorage.SIDED.registerSelf(FORGE_CONTROLLER_BLOCK_ENTITY)
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FORGE_CONTROLLER_BLOCK_ENTITY, (controller, side) -> controller.getFluidHandler());

            // Fabric's ItemStorage was derived from the WorldlyContainer; expose the same sided view.
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FORGE_CONTROLLER_BLOCK_ENTITY, SidedInvWrapper::new);

            // Fabric: ItemStorage.SIDED.registerFallback -> the multiblock block directly under the controller,
            // queried from DOWN (by a hopper below it), exposes the controller's DOWN slots. NeoForge has no
            // fallback providers, so this provider is registered for every forge material block (the only
            // non-hopper blocks allowed under a controller). Hoppers keep their own capability, as on Fabric
            // where the fallback was only consulted when no other provider answered.
            event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, blockEntity, side) -> {
                if (side == Direction.DOWN && level.getBlockEntity(pos.above()) instanceof ForgeControllerBlockEntity froge) {
                    return new SidedInvWrapper(froge, Direction.DOWN);
                }
                return null;
            }, ForgeRegistry.getForgeEntries().stream()
                    .flatMap(entry -> java.util.stream.Stream.concat(java.util.stream.Stream.of(entry.getValue().material()), entry.getValue().additionalMaterials().stream()))
                    .distinct()
                    .toArray(net.minecraft.world.level.block.Block[]::new));
        });
    }

    public static final RecipeTagLoader RECIPE_TAG_LOADER = new RecipeTagLoader();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
