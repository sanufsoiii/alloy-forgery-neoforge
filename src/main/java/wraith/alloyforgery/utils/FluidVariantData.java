package wraith.alloyforgery.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * NeoForge stand-in for Fabric's {@code FluidVariant}: a fluid plus its component patch.
 * The codec matches Fabric's {@code VariantCodecs.FLUID_CODEC} field for field
 * ({@code fluid}, optional {@code components}) so saved forge controllers keep their NBT layout.
 */
public record FluidVariantData(Holder<Fluid> fluid, DataComponentPatch components) {

    public static final FluidVariantData BLANK = new FluidVariantData(Fluids.EMPTY.builtInRegistryHolder(), DataComponentPatch.EMPTY);

    public static final Codec<FluidVariantData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("fluid").forGetter(FluidVariantData::fluid),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(FluidVariantData::components)
    ).apply(instance, FluidVariantData::new));

    public static FluidVariantData of(Fluid fluid, DataComponentPatch components) {
        return new FluidVariantData(fluid.builtInRegistryHolder(), components);
    }

    public boolean isBlank() {
        return this.fluid.value() == Fluids.EMPTY;
    }

    public boolean isOf(Fluid fluid) {
        return this.fluid.value() == fluid;
    }
}
