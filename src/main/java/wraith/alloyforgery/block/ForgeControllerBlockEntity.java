package wraith.alloyforgery.block;

import com.google.common.collect.ImmutableList;
import io.wispforest.owo.ops.ItemOps;
import io.wispforest.owo.serialization.format.nbt.NbtDeserializer;
import io.wispforest.owo.serialization.format.nbt.NbtSerializer;
import io.wispforest.owo.util.ImplementedInventory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import wraith.alloyforgery.AlloyForgeScreenHandler;
import wraith.alloyforgery.AlloyForgery;
import wraith.alloyforgery.forges.ForgeDefinition;
import wraith.alloyforgery.forges.ForgeFuelRegistry;
import wraith.alloyforgery.mixin.HopperBlockEntityAccessor;
import wraith.alloyforgery.recipe.AlloyForgeRecipe;
import wraith.alloyforgery.recipe.AlloyForgeRecipeInput;
import wraith.alloyforgery.utils.EndecUtils;
import wraith.alloyforgery.utils.FluidVariantData;
import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public class ForgeControllerBlockEntity extends BlockEntity implements ImplementedInventory, WorldlyContainer, MenuProvider {

    private static final int[] DOWN_SLOTS = new int[]{10, 11};
    private static final int[] RIGHT_SLOTS = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final int[] LEFT_SLOTS = new int[]{11};

    public static final int INVENTORY_SIZE = 12;
    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

    private final NonNullList<ItemStack> previousItems = NonNullList.create();
    private boolean checkForRecipes = true;

    private Optional<RecipeHolder<AlloyForgeRecipe>> recipeCache = Optional.empty();

    private int requiredTierToCraft = -1;

    private final FluidHolder fluidHolder = new FluidHolder();

    private final ForgeDefinition forgeDefinition;
    private final ImmutableList<BlockPos> multiblockPositions;
    private final Direction facing;

    private float fuel;
    private int currentSmeltTime;

    private int smeltProgress;
    private int fuelProgress;
    private int lavaProgress;

    public ForgeControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AlloyForgery.FORGE_CONTROLLER_BLOCK_ENTITY, pos, state);
        forgeDefinition = ((ForgeControllerBlock) state.getBlock()).forgeDefinition;
        facing = state.getValue(ForgeControllerBlock.FACING);

        multiblockPositions = generateMultiblockPositions(pos.immutable(), state.getValue(ForgeControllerBlock.FACING));
    }

    private final ContainerData properties = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> smeltProgress;
                case 1 -> fuelProgress;
                case 2 -> lavaProgress;
                default -> requiredTierToCraft;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 4;
        }
    };

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        ContainerHelper.loadAllItems(nbt, items, registryLookup);

        this.currentSmeltTime = nbt.getInt("CurrentSmeltTime");
        this.fuel = nbt.getInt("Fuel");

        final var fluidNbt = nbt.getCompound("FuelFluidInput");
        this.fluidHolder.amount = fluidNbt.getLong("Amount");
        this.fluidHolder.variant = EndecUtils.FLUID_VARIANT.decodeFully(NbtDeserializer::of, nbt.getCompound("Variant"));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        ContainerHelper.saveAllItems(nbt, items, registryLookup);

        nbt.putInt("Fuel", Math.round(fuel));
        nbt.putInt("CurrentSmeltTime", currentSmeltTime);

        final var fluidNbt = new CompoundTag();
        fluidNbt.putLong("Amount", this.fluidHolder.amount);
        fluidNbt.put("Variant", EndecUtils.FLUID_VARIANT.encodeFully(NbtSerializer::of, this.fluidHolder.variant));
        nbt.put("FuelFluidInput", fluidNbt);
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public ItemStack getFuelStack() {
        return getItem(11);
    }

    public boolean canAddFuel(int fuel) {
        return this.fuel + fuel <= forgeDefinition.fuelCapacity();
    }

    public void addFuel(int fuel) {
        this.fuel += fuel;
    }

    public int getSmeltProgress() {
        return smeltProgress;
    }

    public int getCurrentSmeltTime() {
        return currentSmeltTime;
    }

    public ForgeDefinition getForgeDefinition() {
        return this.forgeDefinition;
    }

    @Override
    public void setChanged() {
        if (ItemStackComparisonUtil.itemsChanged(items, previousItems)) {
            this.previousItems.clear();
            this.previousItems.addAll(items.stream().map(ItemStack::copy).toList());

            this.checkForRecipes = true;
        }

        super.setChanged();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ImplementedInventory.super.setItem(slot, stack);

        this.setChanged();
    }

    public void tick() {
        this.smeltProgress = Math.round((this.currentSmeltTime / (float) forgeDefinition.maxSmeltTime()) * 19);
        this.fuelProgress = Math.round((this.fuel / (float) forgeDefinition.fuelCapacity()) * 48);
        this.lavaProgress = Math.round((this.fluidHolder.amount / (float) FluidHolder.BUCKET) * 50);

        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());

        if (!this.verifyMultiblock()) {
            this.currentSmeltTime = 0;

            final var currentState = level.getBlockState(worldPosition);
            if (currentState.getValue(ForgeControllerBlock.LIT)) {
                level.setBlockAndUpdate(worldPosition, currentState.setValue(ForgeControllerBlock.LIT, false));
            }

            return;
        }

        if (!this.getFuelStack().isEmpty()) {
            final var fuelStack = this.getFuelStack();
            final var fuelDefinition = ForgeFuelRegistry.getFuelForItem(fuelStack.getItem());

            if (fuelDefinition != ForgeFuelRegistry.ForgeFuelDefinition.EMPTY && canAddFuel(fuelDefinition.fuel())) {
                this.getFuelStack().shrink(1);

                attemptInsertOnIndex(11, fuelDefinition.hasReturnType() ? new ItemStack(fuelDefinition.returnType()) : ItemStack.EMPTY);

                this.fuel += fuelDefinition.fuel();
            }
        }

        final var emptyFuelSpace = this.forgeDefinition.fuelCapacity() - this.fuel;

        if (this.fluidHolder.amount >= 81 && emptyFuelSpace > 0f) {
            final float fuelInsertAmount = Math.min((this.fluidHolder.amount / 81f) * 24, ((emptyFuelSpace) / 24) * 24);

            this.fuel += fuelInsertAmount;
            this.fluidHolder.amount -= (fuelInsertAmount / 24) * 81;
        }

        final var currentBlockState = this.level.getBlockState(worldPosition);
        if (this.fuel > 100 && !currentBlockState.getValue(ForgeControllerBlock.LIT)) {
            this.level.setBlockAndUpdate(worldPosition, currentBlockState.setValue(ForgeControllerBlock.LIT, true));
        } else if (fuel < 100 && currentBlockState.getValue(ForgeControllerBlock.LIT)) {
            this.level.setBlockAndUpdate(worldPosition, currentBlockState.setValue(ForgeControllerBlock.LIT, false));
        }

        // 1: Check if the inventory is full
        // 2: Prevent crafting when we know that there is not enough fuel to craft at all
        // 3: Prevent recipe checking if the inventory has not changed
        if (this.isEmpty()) {
            this.currentSmeltTime = 0;

            return;
        }

        if (this.fuel < 5 || !this.checkForRecipes) {
            this.currentSmeltTime = 0;

            return;
        }

        //--

        var recipeInput = new AlloyForgeRecipeInput(this);

        if (this.recipeCache.isEmpty() || !this.recipeCache.get().value().matches(recipeInput, this.level)) {
            this.recipeCache = this.level.getRecipeManager().getRecipeFor(AlloyForgeRecipe.Type.INSTANCE, recipeInput, this.level);
        }

        if (this.recipeCache.isEmpty() && this.requiredTierToCraft != -1) {
            this.requiredTierToCraft = -1;
        }

        if (this.recipeCache.isEmpty() || !canSmelt(this.recipeCache.get().value())) {
            this.checkForRecipes = false;
            this.currentSmeltTime = 0;
            return;
        }

        //--

        var recipe = recipeCache.get().value();

        if (this.currentSmeltTime < this.forgeDefinition.maxSmeltTime()) {
            final float fuelRequirement = recipe.getFuelPerTick() * this.forgeDefinition.speedMultiplier();

            if (this.fuel - fuelRequirement < 0) {
                this.currentSmeltTime = 0;
                return;
            }

            this.currentSmeltTime++;
            this.fuel -= fuelRequirement;

            if (this.level.random.nextDouble() > 0.75) {
                AlloyForgery.FORGE_PARTICLES.spawn(this.level, Vec3.atLowerCornerOf(this.worldPosition), this.facing);
            }
        } else {
            var remainderList = AlloyForgeRecipe.gatherRemainders(recipeCache.get(), recipeInput);

            if (remainderList != null) this.handleForgingRemainders(remainderList);

            var outputStack = this.getItem(10);
            var recipeOutput = recipe.assemble(recipeInput, this.level.registryAccess());

            recipe.consumeIngredients(recipeInput);

            if (outputStack.isEmpty()) {
                this.setItem(10, recipeOutput);
            } else {
                outputStack.grow(recipeOutput.getCount());
            }

            this.currentSmeltTime = 0;
        }
    }

    private boolean canSmelt(AlloyForgeRecipe recipe) {
        final var outputStack = this.getItem(10);
        final var recipeOutput = recipe.getResult(this.forgeDefinition.forgeTier());

        if (recipe.getMinForgeTier() > this.forgeDefinition.forgeTier()) {
            this.requiredTierToCraft = recipe.getMinForgeTier();

            return false;
        } else if (requiredTierToCraft != -1) {
            this.requiredTierToCraft = -1;
        }

        return outputStack.isEmpty() || ItemOps.canStack(outputStack, recipeOutput);
    }

    private void handleForgingRemainders(NonNullList<ItemStack> remainderList) {
        for (int i = 0; i < remainderList.size(); ++i) {
            attemptInsertOnIndex(i, remainderList.get(i));
        }
    }

    public void attemptInsertOnIndex(int i, ItemStack itemstack) {
        if (itemstack.isEmpty()) return;

        var slotStack = this.getItem(i);

        if (slotStack.isEmpty()) {
            this.setItem(i, itemstack);
        } else if (ItemStack.isSameItem(slotStack, itemstack) && ItemStack.isSameItemSameComponents(slotStack, itemstack)) {
            itemstack.grow(slotStack.getCount());

            if (itemstack.getCount() > itemstack.getMaxStackSize()) {
                int excess = itemstack.getCount() - itemstack.getMaxStackSize();
                itemstack.shrink(excess);

                var insertStack = itemstack.copy();
                insertStack.setCount(excess);

                if (!this.attemptToInsertIntoHopper(insertStack)) {
                    var frontForgePos = worldPosition.relative(getBlockState().getValue(ForgeControllerBlock.FACING));

                    level.playSound(null, frontForgePos.getX(), frontForgePos.getY(), frontForgePos.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.2F);
                    Containers.dropItemStack(level, frontForgePos.getX(), frontForgePos.getY(), frontForgePos.getZ(), insertStack);
                }
            }

            this.setItem(i, itemstack);
        } else if (!this.attemptToInsertIntoHopper(itemstack)) {
            var frontForgePos = worldPosition.relative(getBlockState().getValue(ForgeControllerBlock.FACING));

            level.playSound(null, frontForgePos.getX(), frontForgePos.getY(), frontForgePos.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.2F);
            Containers.dropItemStack(level, frontForgePos.getX(), frontForgePos.getY(), frontForgePos.getZ(), itemstack);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean attemptToInsertIntoHopper(ItemStack remainderStack) {
        if (remainderStack.isEmpty()) return true;

        HopperBlockEntity blockEntity = null;

        for (int y = 1; y <= 2; y++) {
            if (level.getBlockEntity(this.worldPosition.below(y)) instanceof HopperBlockEntity hopperBlockEntity) {
                blockEntity = hopperBlockEntity;

                break;
            }
        }

        if (blockEntity != null) {
            var isHopperEmpty = blockEntity.isEmpty();

            for (int slotIndex = 0; slotIndex < blockEntity.getContainerSize(); ++slotIndex) {
                if (remainderStack.isEmpty()) break;

                if (!blockEntity.getItem(slotIndex).isEmpty()) {
                    final var itemStack = blockEntity.getItem(slotIndex);

                    if (itemStack.isEmpty()) {
                        blockEntity.setItem(slotIndex, remainderStack);
                        remainderStack = ItemStack.EMPTY;
                    } else if (ItemOps.canStack(itemStack, remainderStack)) {
                        int availableSpace = itemStack.getMaxStackSize() - itemStack.getCount();
                        int j = Math.min(itemStack.getCount(), availableSpace);
                        remainderStack.shrink(j);
                        itemStack.grow(j);
                    }
                } else {
                    blockEntity.setItem(slotIndex, remainderStack);
                    break;
                }
            }

            if (isHopperEmpty && !((HopperBlockEntityAccessor) blockEntity).alloyForge$isDisabled()) {
                ((HopperBlockEntityAccessor) blockEntity).alloyForge$setTransferCooldown(8);
            }

            blockEntity.setChanged();

            return true;
        }

        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean verifyMultiblock() {
        final BlockState belowController = level.getBlockState(multiblockPositions.get(0));
        if (!(belowController.is(Blocks.HOPPER) || forgeDefinition.isBlockValid(belowController.getBlock())))
            return false;

        for (int i = 1; i < multiblockPositions.size(); i++) {
            if (!forgeDefinition.isBlockValid(level.getBlockState(multiblockPositions.get(i)).getBlock())) return false;
        }

        return true;
    }

    private static ImmutableList<BlockPos> generateMultiblockPositions(BlockPos controllerPos, Direction controllerFacing) {
        final List<BlockPos> posses = new ArrayList<>();
        final BlockPos center = controllerPos.relative(controllerFacing.getOpposite());

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(1, -1, 1), center.offset(-1, -1, -1))) {
            posses.add(pos.immutable());
        }

        posses.remove(controllerPos.below());
        posses.add(0, controllerPos.below());

        for (int i = 0; i < 2; i++) {
            final var newCenter = center.offset(0, i, 0);

            posses.add(newCenter.east());
            posses.add(newCenter.west());
            posses.add(newCenter.north());
            posses.add(newCenter.south());
        }

        posses.remove(controllerPos);
        return ImmutableList.copyOf(posses);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return DOWN_SLOTS;
        } else if (side == facing.getClockWise()) {
            return LEFT_SLOTS;
        } else if (side == facing.getCounterClockWise() && this.currentSmeltTime == 0) {
            return RIGHT_SLOTS;
        } else {
            return new int[0];
        }
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot == 11) return ForgeFuelRegistry.hasFuel(stack.getItem());

        var slotStack = getItem(slot);

        return slotStack.isEmpty() || ItemOps.canStack(slotStack, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return slot == 10 || (slot == 11 && !ForgeFuelRegistry.hasFuel(stack.getItem()));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alloy_forgery.forge_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new AlloyForgeScreenHandler(syncId, inv, this, properties);
    }

    /**
     * Fluid capability of the controller (registered in {@code AlloyForgery} through {@code RegisterCapabilitiesEvent}).
     */
    public IFluidHandler getFluidHandler() {
        return this.fluidHolder;
    }

    /**
     * Port of the Fabric {@code SingleVariantStorage<FluidVariant>} lava tank (insertion only).
     * <p>
     * The tank is still stored and ticked in Fabric droplets (81000 per bucket) so the NBT format and
     * all tick math stay identical to the original. NeoForge works in millibuckets (1000 per bucket);
     * the only conversion happens here: 1 mB = {@link #DROPLETS_PER_MB} droplets.
     */
    private class FluidHolder implements IFluidHandler {
        static final long BUCKET = 81000;
        static final long DROPLETS_PER_MB = BUCKET / 1000;
        static final long CAPACITY = BUCKET + 81;

        FluidVariantData variant = FluidVariantData.BLANK;
        long amount = 0;

        private boolean canInsert(FluidStack stack) {
            return stack.is(Fluids.LAVA);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (variant.isBlank() || amount <= 0) return FluidStack.EMPTY;
            return new FluidStack(variant.fluid(), (int) (amount / DROPLETS_PER_MB), variant.components());
        }

        @Override
        public int getTankCapacity(int tank) {
            return (int) (CAPACITY / DROPLETS_PER_MB);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return canInsert(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !canInsert(resource)) return 0;

            var resourceVariant = FluidVariantData.of(resource.getFluid(), resource.getComponentsPatch());
            // SingleVariantStorage: insert only into a blank tank, or into one holding the same variant
            if (!(variant.isBlank() || amount == 0 || variant.equals(resourceVariant))) return 0;

            long insertedMb = Math.min(resource.getAmount(), (CAPACITY - amount) / DROPLETS_PER_MB);
            if (insertedMb <= 0) return 0;

            if (action.execute()) {
                variant = resourceVariant;
                amount += insertedMb * DROPLETS_PER_MB;
                ForgeControllerBlockEntity.this.setChanged();
            }

            return (int) insertedMb;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            // Insertion-only storage (Fabric original: canExtract -> false)
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            // Insertion-only storage (Fabric original: canExtract -> false)
            return FluidStack.EMPTY;
        }
    }
}
