package org.XYccWA.space_simulation.item;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.XYccWA.space_simulation.SpaceSimulationMod;
import org.XYccWA.space_simulation.fluid.ModFluids;

public class SpaceSimulationModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SpaceSimulationMod.MOD_ID);

    public static final RegistryObject<Item> spacesuit_helmet = ITEMS.register("spacesuit_helmet", () -> new ArmorItem(ArmorMaterials.LEATHER,ArmorItem.Type.HELMET,new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> spacesuit_chestplate = ITEMS.register("spacesuit_pants", () -> new ArmorItem(ArmorMaterials.LEATHER,ArmorItem.Type.CHESTPLATE,new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> spacesuit_leggings = ITEMS.register("spacesuit_leggings", () -> new ArmorItem(ArmorMaterials.LEATHER,ArmorItem.Type.LEGGINGS,new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> spacesuit_boots = ITEMS.register("spacesuit_boots", () -> new ArmorItem(ArmorMaterials.LEATHER,ArmorItem.Type.BOOTS,new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BucketItem> SOURCE_HYDROGRN_PEROXIDE_BUCKET = ITEMS.register("source_hydrogrn_peroxide_bucket", () -> new BucketItem(ModFluids.SOURCE_HYDROGRN_PEROXIDE, new Item.Properties().stacksTo(1)));
}
