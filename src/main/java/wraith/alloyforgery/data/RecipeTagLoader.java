package wraith.alloyforgery.data;

import io.wispforest.owo.network.ClientAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import wraith.alloyforgery.AlloyForgery;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tag Loader used to load Recipe Based tags with the resolving
 * process being delayed till Data Pack load has ended
 */
public class RecipeTagLoader extends SimplePreparableReloadListener<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> {

    private static final Map<ResourceLocation, Set<ResourceLocation>> RESOLVED_ENTRIES = new HashMap<>();

    private static final Map<ResourceLocation, List<TagLoader.EntryWithSource>> RAW_TAG_DATA = new HashMap<>();

    private final DelayedTagGroupLoader<RecipeHolder<Recipe<?>>> tagGroupLoader = new DelayedTagGroupLoader<>("tags/recipe");

    @Override
    protected Map<ResourceLocation, List<TagLoader.EntryWithSource>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return this.tagGroupLoader.load(manager);
    }

    @Override
    protected void apply(Map<ResourceLocation, List<TagLoader.EntryWithSource>> prepared, ResourceManager manager, ProfilerFiller profiler) {
        RAW_TAG_DATA.clear();

        RAW_TAG_DATA.putAll(prepared);
    }

    public ResourceLocation getId() {
        return AlloyForgery.id("recipe_tag");
    }

    //--

    /**
     * @param tag   Identifier for the given Tag
     * @param entry Recipe Entry to check
     * @return true if the tag exists and if the given entry exists within the Tag group
     */
    public static boolean isWithinTag(ResourceLocation tag, RecipeHolder<?> entry) {
        return isWithinTag(tag, entry.id());
    }

    /**
     * @param tag      Identifier for the given Tag
     * @param recipeID Recipe identifier
     * @return true if the tag exists and if the given entry exists within the Tag group
     */
    public static boolean isWithinTag(ResourceLocation tag, ResourceLocation recipeID) {
        if (!RESOLVED_ENTRIES.containsKey(tag)) return false;

        return RESOLVED_ENTRIES.get(tag).contains(recipeID);
    }

    //--

    public void initEvents() {
        // Fabric END_DATA_PACK_RELOAD -> OnDatapackSyncEvent without a target player (fired by /reload only)
        NeoForge.EVENT_BUS.<OnDatapackSyncEvent>addListener(event -> {
            if (event.getPlayer() == null) endDataPackReload(event.getPlayerList().getServer());
        });
        NeoForge.EVENT_BUS.<ServerStartedEvent>addListener(event -> onServerStarted(event.getServer()));

        NeoForge.EVENT_BUS.<PlayerEvent.PlayerLoggedInEvent>addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                AlloyForgery.CHANNEL.serverHandle(player).send(TagPacket.of(RESOLVED_ENTRIES));
            }
        });
    }

    public void endDataPackReload(MinecraftServer server) {
        resolveEntries(server);

        AlloyForgery.CHANNEL.serverHandle(server).send(TagPacket.of(RESOLVED_ENTRIES));
    }

    public void onServerStarted(MinecraftServer server) {
        resolveEntries(server);
    }

    public void resolveEntries(MinecraftServer server) {
        var recipeManager = server.getRecipeManager();

        Map<ResourceLocation, Collection<RecipeHolder<Recipe<?>>>> map = tagGroupLoader.setGetter(identifier -> {
                    return Optional.ofNullable((RecipeHolder<Recipe<?>>) recipeManager.byKey(identifier).orElse(null));
                })
                .build(RAW_TAG_DATA);

        RESOLVED_ENTRIES.clear();

        map.forEach((id, recipes) -> RESOLVED_ENTRIES.put(id, recipes.stream().map(RecipeHolder::id).collect(Collectors.toSet())));
    }

    // Packet that acts as a sync packet for the Recipe Based Tag Entries
    public record TagPacket(List<TagEntry> entries) {
        public static TagPacket of(Map<ResourceLocation, Set<ResourceLocation>> tagEntries) {
            return new TagPacket(tagEntries.entrySet().stream()
                    .map(entry -> new TagEntry(entry.getKey(), List.copyOf(entry.getValue())))
                    .toList());
        }

        public static void handlePacket(TagPacket packet, ClientAccess access) {
            RESOLVED_ENTRIES.clear();

            RESOLVED_ENTRIES.putAll(
                    packet.entries.stream().collect(Collectors.toMap(TagEntry::id, e -> new HashSet<>(e.entries())))
            );
        }
    }

    public record TagEntry(ResourceLocation id, List<ResourceLocation> entries) {
    }

    ;

    //--
}
