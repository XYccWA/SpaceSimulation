package org.xyccwa.space_simulation.modBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.modItem.SpaceSimulationItem;

import java.util.function.Supplier;


public class SpaceSimulationBlock {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SpaceSimulation.MOD_ID);

    public static final DeferredBlock<Block> DUST_BLOCK = registerBlocks(
            "dust_block", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5f,0)));

// ========== 金属矿石（10种） ==========

    /** 辉铜矿 */
    public static final DeferredBlock<Block> CHALCOCITE_ORE = registerBlocks(
            "chalcocite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0f, 4.5f)));

    /** 铁纹矿石 - 铁镍金属矿，质地较坚韧，硬度中等 */
    public static final DeferredBlock<Block> KAMACITE_ORE = registerBlocks(
            "kamacite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.5f, 6.0f)));

    /** 镍纹矿石 - 镍含量更高，比铁纹矿稍硬 */
    public static final DeferredBlock<Block> TAENITE_ORE = registerBlocks(
            "taenite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0f, 6.5f)));

    /** 铬铁矿石 - 铬铁矿，硬度较高，较脆 */
    public static final DeferredBlock<Block> CHROMITE_ORE = registerBlocks(
            "chromite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.5f, 5.0f)));

    /** 钛铁矿石 - 钛铁矿，硬度中等偏上 */
    public static final DeferredBlock<Block> ILMENITE_ORE = registerBlocks(
            "ilmenite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0f, 5.5f)));

    /** 镁橄榄石矿石 - 硅酸盐矿，硬度较高但性脆 */
    public static final DeferredBlock<Block> FORSTERITE_ORE = registerBlocks(
            "forsterite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(6.5f, 4.5f)));

    /** 钨锰矿石 - 钨矿，密度大且坚硬 */
    public static final DeferredBlock<Block> WOLFRAMITE_ORE = registerBlocks(
            "wolframite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(7.0f, 8.0f)));

    /** 铌铁矿石 - 铌矿，硬度较高 */
    public static final DeferredBlock<Block> COLUMBITE_ORE = registerBlocks(
            "columbite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(6.5f, 7.0f)));

    /** 辉钼矿石 - 钼矿，较软，易碎 */
    public static final DeferredBlock<Block> MOLYBDENITE_ORE = registerBlocks(
            "molybdenite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f, 3.5f)));

    /** 钽铁矿石 - 钽矿，坚硬致密 */
    public static final DeferredBlock<Block> TANTALITE_ORE = registerBlocks(
            "tantalite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(7.5f, 9.0f)));

    /** 辉铼矿石 - 铼矿，极稀有，硬度中等 */
    public static final DeferredBlock<Block> RHENIITE_ORE = registerBlocks(
            "rheniite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0f, 10.0f)));  // 高爆炸抗性体现稀有


// ========== 硅质矿石（4种） ==========

    /** 橄榄石矿石 - 硅酸盐，硬度较高但易风化 */
    public static final DeferredBlock<Block> OLIVINE_ORE = registerBlocks(
            "olivine_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(6.0f, 4.0f)));

    /** 辉石矿石 - 硅酸盐，硬度中等 */
    public static final DeferredBlock<Block> PYROXENE_ORE = registerBlocks(
            "pyroxene_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.5f, 4.5f)));

    /** 斜长石矿石 - 硅酸盐，硬度中等偏低 */
    public static final DeferredBlock<Block> PLAGIOCLASE_ORE = registerBlocks(
            "plagioclase_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0f, 4.0f)));

    /** 石英矿石 - 二氧化硅，硬度高但脆 */
    public static final DeferredBlock<Block> QUARTZ_ORE = registerBlocks(
            "quartz_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(7.0f, 3.5f)));


// ========== 碳质矿石（5种） ==========

    /** 碳质球粒矿石 - 碳质，软且疏松 */
    public static final DeferredBlock<Block> CARBONACEOUS_ORE = registerBlocks(
            "carbonaceous_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.0f, 2.5f)));

    /** 层状硅酸盐矿石 - 黏土类，质地软 */
    public static final DeferredBlock<Block> PHYLLOSILICATE_ORE = registerBlocks(
            "phyllosilicate_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.5f, 3.0f)));

    /** 碳酸盐矿石 - 石灰岩类，硬度中等偏低 */
    public static final DeferredBlock<Block> CARBONATE_ORE = registerBlocks(
            "carbonate_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0f, 3.5f)));

    /** 陨硫铁矿石 - 硫化铁，中等硬度 */
    public static final DeferredBlock<Block> TROILITE_ORE = registerBlocks(
            "troilite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0f, 5.0f)));

    /** 磁铁矿石 - 氧化铁，硬度较高，磁性 */
    public static final DeferredBlock<Block> MAGNETITE_ORE = registerBlocks(
            "magnetite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .strength(6.0f, 6.5f)));


    private static <T extends Block> void registerBlockItems(String name,DeferredBlock<T> block){
        SpaceSimulationItem.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static <T extends Block> DeferredBlock<T> registerBlocks(String name, Supplier<T> block){
        DeferredBlock<T> blocks = BLOCKS.register(name, block);
        registerBlockItems(name, blocks);
        return blocks;
    }
}
