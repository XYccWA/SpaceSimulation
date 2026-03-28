package org.XYccWA.space_simulation.item.fuel;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.XYccWA.space_simulation.capability.CapabilityHandler;
import org.XYccWA.space_simulation.fluid.ModFluids;

public class FuelBucketItem extends BucketItem {
    public FuelBucketItem(Properties properties) {
        super(ModFluids.SOURCE_HYDROGRN_PEROXIDE, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 检查是否按下Alt键
        if (player.isShiftKeyDown()) {
            // 获取玩家燃料能力
            player.getCapability(CapabilityHandler.FUEL_REMAINING).ifPresent(fuel -> {
                // 添加1000点燃料
                fuel.addFuel(1000);

                // 消耗一个过氧化氢桶
                stack.shrink(1);

                // 返还一个空桶
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                } else {
                    player.getInventory().add(new ItemStack(Items.BUCKET));
                }
            });

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // 如果没有按下Alt键，执行默认的桶行为
        return super.use(level, player, hand);
    }
}