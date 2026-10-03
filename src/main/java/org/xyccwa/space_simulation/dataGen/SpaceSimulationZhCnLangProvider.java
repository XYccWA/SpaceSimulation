package org.xyccwa.space_simulation.dataGen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;
import org.xyccwa.space_simulation.modItem.SpaceSimulationItem;

public class SpaceSimulationZhCnLangProvider extends LanguageProvider {
    public SpaceSimulationZhCnLangProvider(PackOutput output) {
        super(output, SpaceSimulation.MOD_ID, "zh_cn");
    }

    @Override
    protected void addTranslations() {

        add(SpaceSimulationBlock.DUST_BLOCK.get(), "浮土");

// ========== 金属矿石 ==========
        add(SpaceSimulationBlock.CHALCOCITE_ORE.get(), "辉铜矿石");
        add(SpaceSimulationBlock.KAMACITE_ORE.get(), "铁纹矿石");
        add(SpaceSimulationBlock.TAENITE_ORE.get(), "镍纹矿石");
        add(SpaceSimulationBlock.CHROMITE_ORE.get(), "铬铁矿石");
        add(SpaceSimulationBlock.ILMENITE_ORE.get(), "钛铁矿石");
        add(SpaceSimulationBlock.FORSTERITE_ORE.get(), "镁橄榄石矿石");
        add(SpaceSimulationBlock.WOLFRAMITE_ORE.get(), "钨锰矿石");
        add(SpaceSimulationBlock.COLUMBITE_ORE.get(), "铌铁矿石");
        add(SpaceSimulationBlock.MOLYBDENITE_ORE.get(), "辉钼矿石");
        add(SpaceSimulationBlock.TANTALITE_ORE.get(), "钽铁矿石");
        add(SpaceSimulationBlock.RHENIITE_ORE.get(), "辉铼矿石");

// ========== 硅质矿石 ==========
        add(SpaceSimulationBlock.OLIVINE_ORE.get(), "橄榄石矿石");
        add(SpaceSimulationBlock.PYROXENE_ORE.get(), "辉石矿石");
        add(SpaceSimulationBlock.PLAGIOCLASE_ORE.get(), "斜长石矿石");
        add(SpaceSimulationBlock.QUARTZ_ORE.get(), "石英矿石");

// ========== 碳质矿石 ==========
        add(SpaceSimulationBlock.CARBONACEOUS_ORE.get(), "碳质球粒矿石");
        add(SpaceSimulationBlock.PHYLLOSILICATE_ORE.get(), "层状硅酸盐矿石");
        add(SpaceSimulationBlock.CARBONATE_ORE.get(), "碳酸盐矿石");
        add(SpaceSimulationBlock.TROILITE_ORE.get(), "陨硫铁矿石");
        add(SpaceSimulationBlock.MAGNETITE_ORE.get(), "磁铁矿石");

// ========== 新增矿石（6种，科技树断链补齐） ==========
        add(SpaceSimulationBlock.COBALTITE_ORE.get(), "辉砷钴矿石");
        add(SpaceSimulationBlock.SPODUMENE_ORE.get(), "锂辉石矿石");
        add(SpaceSimulationBlock.ZIRCON_ORE.get(), "锆石矿石");
        add(SpaceSimulationBlock.MONAZITE_ORE.get(), "独居石矿石");
        add(SpaceSimulationBlock.URANINITE_ORE.get(), "沥青铀矿石");
        add(SpaceSimulationBlock.THORITE_ORE.get(), "钍石矿石");

        add(SpaceSimulationItem.DUST.get(), "浮土");
// 金属矿砂
        add(SpaceSimulationItem.CHALCOCITE_SAND.get(), "辉铜矿砂");
        add(SpaceSimulationItem.KAMACITE_SAND.get(), "铁纹矿砂");
        add(SpaceSimulationItem.TAENITE_SAND.get(), "镍纹矿砂");
        add(SpaceSimulationItem.CHROMITE_SAND.get(), "铬铁矿砂");
        add(SpaceSimulationItem.ILMENITE_SAND.get(), "钛铁矿砂");
        add(SpaceSimulationItem.FORSTERITE_SAND.get(), "镁橄榄石砂");
        add(SpaceSimulationItem.WOLFRAMITE_SAND.get(), "钨锰矿砂");
        add(SpaceSimulationItem.COLUMBITE_SAND.get(), "铌铁矿砂");
        add(SpaceSimulationItem.MOLYBDENITE_SAND.get(), "辉钼矿砂");
        add(SpaceSimulationItem.TANTALITE_SAND.get(), "钽铁矿砂");
        add(SpaceSimulationItem.RHENIITE_SAND.get(), "辉铼矿砂");

// 硅质矿砂
        add(SpaceSimulationItem.OLIVINE_SAND.get(), "橄榄石砂");
        add(SpaceSimulationItem.PYROXENE_SAND.get(), "辉石砂");
        add(SpaceSimulationItem.PLAGIOCLASE_SAND.get(), "斜长石砂");
        add(SpaceSimulationItem.QUARTZ_SAND.get(), "石英砂");

// 碳质矿砂
        add(SpaceSimulationItem.CARBONACEOUS_SAND.get(), "碳质球粒砂");
        add(SpaceSimulationItem.PHYLLOSILICATE_SAND.get(), "层状硅酸盐砂");
        add(SpaceSimulationItem.CARBONATE_SAND.get(), "碳酸盐砂");
        add(SpaceSimulationItem.TROILITE_SAND.get(), "陨硫铁矿砂");
        add(SpaceSimulationItem.MAGNETITE_SAND.get(), "磁铁矿砂");

// 新增矿砂（科技树断链补齐）
        add(SpaceSimulationItem.COBALTITE_SAND.get(), "辉砷钴矿砂");
        add(SpaceSimulationItem.SPODUMENE_SAND.get(), "锂辉石砂");
        add(SpaceSimulationItem.ZIRCON_SAND.get(), "锆石砂");
        add(SpaceSimulationItem.MONAZITE_SAND.get(), "独居石砂");
        add(SpaceSimulationItem.URANINITE_SAND.get(), "沥青铀矿砂");
        add(SpaceSimulationItem.THORITE_SAND.get(), "钍石砂");

// 矿粉
        add(SpaceSimulationItem.CHALCOCITE_POWDER.get(), "辉铜矿粉");
        add(SpaceSimulationItem.KAMACITE_POWDER.get(), "铁纹矿粉");
        add(SpaceSimulationItem.TAENITE_POWDER.get(), "镍纹矿粉");
        add(SpaceSimulationItem.CHROMITE_POWDER.get(), "铬铁矿粉");
        add(SpaceSimulationItem.ILMENITE_POWDER.get(), "钛铁矿粉");
        add(SpaceSimulationItem.FORSTERITE_POWDER.get(), "镁橄榄石粉");
        add(SpaceSimulationItem.WOLFRAMITE_POWDER.get(), "钨锰矿粉");
        add(SpaceSimulationItem.COLUMBITE_POWDER.get(), "铌铁矿粉");
        add(SpaceSimulationItem.MOLYBDENITE_POWDER.get(), "辉钼矿粉");
        add(SpaceSimulationItem.TANTALITE_POWDER.get(), "钽铁矿粉");
        add(SpaceSimulationItem.RHENIITE_POWDER.get(), "辉铼矿粉");
        add(SpaceSimulationItem.OLIVINE_POWDER.get(), "橄榄石粉");
        add(SpaceSimulationItem.PYROXENE_POWDER.get(), "辉石粉");
        add(SpaceSimulationItem.PLAGIOCLASE_POWDER.get(), "斜长石粉");
        add(SpaceSimulationItem.QUARTZ_POWDER.get(), "石英粉");
        add(SpaceSimulationItem.CARBONACEOUS_POWDER.get(), "碳质球粒粉");
        add(SpaceSimulationItem.PHYLLOSILICATE_POWDER.get(), "层状硅酸盐粉");
        add(SpaceSimulationItem.CARBONATE_POWDER.get(), "碳酸盐粉");
        add(SpaceSimulationItem.TROILITE_POWDER.get(), "陨硫铁矿粉");
        add(SpaceSimulationItem.MAGNETITE_POWDER.get(), "磁铁矿粉");
        add(SpaceSimulationItem.COBALTITE_POWDER.get(), "辉砷钴矿粉");
        add(SpaceSimulationItem.SPODUMENE_POWDER.get(), "锂辉石粉");
        add(SpaceSimulationItem.ZIRCON_POWDER.get(), "锆石粉");
        add(SpaceSimulationItem.MONAZITE_POWDER.get(), "独居石粉");
        add(SpaceSimulationItem.URANINITE_POWDER.get(), "沥青铀矿粉");
        add(SpaceSimulationItem.THORITE_POWDER.get(), "钍石粉");
// ========== 金属单质锭 ==========
        add(SpaceSimulationItem.COPPER_INGOT.get(), "铜锭");
        add(SpaceSimulationItem.IRON_INGOT.get(), "铁锭");
        add(SpaceSimulationItem.NICKEL_INGOT.get(), "镍锭");
        add(SpaceSimulationItem.CHROMIUM_INGOT.get(), "铬锭");
        add(SpaceSimulationItem.TITANIUM_INGOT.get(), "钛锭");
        add(SpaceSimulationItem.MAGNESIUM_INGOT.get(), "镁锭");
        add(SpaceSimulationItem.TUNGSTEN_INGOT.get(), "钨锭");
        add(SpaceSimulationItem.NIOBIUM_INGOT.get(), "铌锭");
        add(SpaceSimulationItem.MOLYBDENUM_INGOT.get(), "钼锭");
        add(SpaceSimulationItem.TANTALUM_INGOT.get(), "钽锭");
        add(SpaceSimulationItem.RHENIUM_INGOT.get(), "铼锭");
        add(SpaceSimulationItem.PLATINUM_INGOT.get(), "铂锭");
        add(SpaceSimulationItem.RHODIUM_INGOT.get(), "铑锭");

// ========== 合金锭 ==========
        add(SpaceSimulationItem.IRON_NICKEL_ALLOY_INGOT.get(), "铁镍合金锭");
        add(SpaceSimulationItem.CHROMIUM_NICKEL_IRON_ALLOY_INGOT.get(), "铬镍铁合金锭");
        add(SpaceSimulationItem.TUNGSTEN_RHENIUM_ALLOY_INGOT.get(), "钨铼合金锭");
        add(SpaceSimulationItem.NICKEL_RHENIUM_ALLOY_INGOT.get(), "镍铼合金锭");
        add(SpaceSimulationItem.PLATINUM_RHODIUM_ALLOY_INGOT.get(), "铂铑合金锭");
        add(SpaceSimulationItem.GH4061_ALLOY_INGOT.get(), "GH4061型合金锭");

        add("itemGroup.space_simulation.ore.creative_tab","太空模拟-矿物");

        addDeathMessage("high_g_force", "%s 被超高G力碾压");
        addDeathMessage("high_g_force.player", "%s 在试图逃离 %s 时被超高G力碾压");
        addDeathMessage("sustained_g_force", "%s 死于持续高G力");
        addDeathMessage("sustained_g_force.player", "%s 在与 %s 战斗时死于持续高G力");
        addDeathMessage("solar_heat", "%s 被太阳的高温烧成灰烬");
        addDeathMessage("solar_heat.player", "%s 在试图逃离 %s 时被太阳的高温烧成灰烬");

    }

    private void addDamageType(String key, String value) {
        add("damage_type." + SpaceSimulation.MOD_ID + "." + key, value);
    }

    private void addDeathMessage(String key, String value) {
        add("death.attack." + key, value);
    }
}