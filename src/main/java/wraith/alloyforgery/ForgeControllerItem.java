package wraith.alloyforgery;

import wraith.alloyforgery.block.ForgeControllerBlock;
import wraith.alloyforgery.forges.ForgeDefinition;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ForgeControllerItem extends BlockItem {

    public ForgeControllerItem(ForgeControllerBlock block, Properties settings) {
        super(block, settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("tooltip.alloy_forgery.forge_tier", getForgeDefinition().forgeTier()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.alloy_forgery.fuel_capacity", getForgeDefinition().fuelCapacity()).withStyle(ChatFormatting.GRAY));
    }

    public ForgeDefinition getForgeDefinition() {
        return ((ForgeControllerBlock) getBlock()).forgeDefinition;
    }
}
