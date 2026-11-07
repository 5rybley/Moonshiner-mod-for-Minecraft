package ru.samogon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import ru.samogon.block.CondenserBlockEntity;
import ru.samogon.block.ProcessBlockEntity;

import java.util.List;
import java.util.Locale;

/** Block item for the condenser: explains the reservoir and shows the water it carries. */
public final class CondenserItem extends BlockItem {
    public CondenserItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        int water = data == null ? 0 : data.copyTag().getInt(CondenserBlockEntity.WATER_KEY);
        if (water > 0) {
            lines.add(Component.translatable("tooltip.samogon.condenser.contents",
                    String.format(Locale.ROOT, "%.1f",
                            water / (double) ProcessBlockEntity.COOLANT_PER_BUCKET))
                    .withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("tooltip.samogon.condenser")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.samogon.condenser.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}