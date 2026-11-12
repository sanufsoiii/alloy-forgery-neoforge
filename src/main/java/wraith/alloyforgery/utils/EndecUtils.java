package wraith.alloyforgery.utils;

import com.mojang.serialization.MapCodec;
import io.wispforest.endec.Endec;
import io.wispforest.endec.StructEndec;
import io.wispforest.owo.serialization.CodecUtils;
import net.minecraft.world.item.crafting.Ingredient;

public class EndecUtils {
    public static StructEndec<Ingredient> STRUCT_INGREDIENT = new MapCodecStructEndec<>(MapCodec.assumeMapUnsafe(Ingredient.CODEC));
    public static Endec<Ingredient> INGREDIENT = CodecUtils.toEndec(Ingredient.CODEC);
    public static Endec<FluidVariantData> FLUID_VARIANT = CodecUtils.toEndec(FluidVariantData.CODEC).catchErrors((ctx, deserializer, e) -> FluidVariantData.BLANK);
}
