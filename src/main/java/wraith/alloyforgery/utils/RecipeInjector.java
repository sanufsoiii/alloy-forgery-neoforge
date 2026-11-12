package wraith.alloyforgery.utils;

import com.google.common.collect.*;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;
import wraith.alloyforgery.mixin.RecipeManagerAccessor;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to safety allow for injecting recipes into the Recipe Manager <b>without
 * overriding or modifying existing Recipes. </b>
 * <p/>
 * Primarily used to either add compatibility for existing recipes by converting to another form
 * or adding new recipes.
 */
public final class RecipeInjector {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Event called on {@link ServerStartedEvent} or {@link DataPackEvents#BEFORE_SYNC} which adds
     * new recipes to the RecipeManager before sync to Players
     */
    public static final SimpleEvent<AddRecipes> ADD_RECIPES = SimpleEvent.create(addRecipes -> (instance) -> {
        for (AddRecipes addRecipe : addRecipes) {
            addRecipe.addRecipes(instance);
        }
    });

    private final RecipeManager manager;

    private final Multimap<RecipeType<?>, RecipeHolder<?>> recipes = HashMultimap.create();
    private final Map<ResourceLocation, RecipeHolder<?>> recipesById = new HashMap<>();

    public RecipeInjector(RecipeManager manager) {
        this.manager = manager;
    }

    /**
     * Attempts to register a given recipe for addition to the recipe manager if
     * 1. Such recipe has a registered {@link RecipeType}
     * 2. Such is found to not have an existing Identifier within {@link RecipeManager}
     *
     * @param recipe The Recipe
     * @param <T>    Type of the given Recipe
     */
    public <R extends Recipe<T>, T extends RecipeInput> void addRecipe(ResourceLocation id, R recipe) {
        if (BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()) == null) {
            throw new IllegalStateException("Unable to add Recipe for a RecipeType not registered!");
        }

        var type = (RecipeType<R>) recipe.getType();

        var bl = manager.getAllRecipesFor(type)
                .stream()
                .anyMatch(recipeEntry -> id.equals(recipeEntry.id()));

        if (bl) {
            LOGGER.error("[RecipeInjector]: Unable to add a given recipe due to being the same Identifier with the given Type. [ID: {}]", id);

            return;
        }

        var recipeEntry = new RecipeHolder<>(id, recipe);

        recipes.put(recipe.getType(), recipeEntry);
        recipesById.put(id, recipeEntry);
    }

    /**
     * @return The current instance of the {@link RecipeManager}
     */
    public RecipeManager manager() {
        return this.manager;
    }

    public HolderLookup.Provider lookup() {
        return ((RecipeManagerAccessor) this.manager).af$getRegistryLookup();
    }

    /**
     * Primary Event for adding new Recipes
     */
    public interface AddRecipes {
        void addRecipes(RecipeInjector instance);
    }

    //--

    public static void initEvents() {
        NeoForge.EVENT_BUS.<ServerStartedEvent>addListener(event -> injectRecipes(event.getServer()));

        DataPackEvents.BEFORE_SYNC.register(RecipeInjector::injectRecipes);
    }

    public static void injectRecipes(MinecraftServer server) {
        var manager = server.getRecipeManager();
        var injector = new RecipeInjector(server.getRecipeManager());

        ADD_RECIPES.invoker().addRecipes(injector);

        var managerAccessor = (RecipeManagerAccessor) manager;

        injector.recipes.putAll(managerAccessor.af$getRecipes());
        injector.recipesById.putAll(managerAccessor.af$getRecipesById());

        managerAccessor.af$setRecipes(ImmutableMultimap.copyOf(injector.recipes));
        managerAccessor.af$setRecipesById(ImmutableMap.copyOf(injector.recipesById));

        injector.recipes.clear();
        injector.recipesById.clear();
    }
}
