package io.github.n252g2.beyonder.item;

import io.github.n252g2.beyonder.brewing.Formula;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A handwritten potion formula. Hold it and use an Apothecary Bench to brew. Never used up. */
public class FormulaItem extends Item {
    private final Formula formula;

    public FormulaItem(Formula formula, Properties properties) {
        super(properties);
        this.formula = formula;
    }

    public Formula formula() {
        return formula;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Sequence " + formula.sequence()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Water bottle as the base, then:").withStyle(ChatFormatting.GRAY));
        for (Map.Entry<Supplier<? extends Item>, Integer> e : formula.ingredients().entrySet()) {
            tooltip.add(Component.literal("  " + e.getValue() + " × ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(e.getKey().get().getDescription().copy().withStyle(ChatFormatting.GRAY)));
        }
        tooltip.add(Component.literal("Hold it and use an Apothecary Bench.").withStyle(ChatFormatting.DARK_AQUA));
    }
}
