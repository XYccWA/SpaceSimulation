package org.XYccWA.space_simulation.damege;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;
import org.XYccWA.space_simulation.player.PlayerPositionTracker;
import org.intellij.lang.annotations.Identifier;

import static org.intellij.lang.annotations.Identifier.*;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID)
public class HighGForceDamage {

    public static final ResourceKey<DamageType> HIGH_G_FORCE_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(SpaceSimulationMod.MOD_ID, "high_g_force"));

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event){
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        player.sendSystemMessage(Component.literal(String.format(
                "速度: %.2f 格/秒 | 加速度: %.2f 格/秒²",
                PlayerPositionTracker.speed,
                PlayerPositionTracker.acceleration
        )));
        if(PlayerPositionTracker.acceleration > 200||PlayerPositionTracker.acceleration < -200){

            DamageSource damageSource = new DamageSource(
                    player.level().registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(HIGH_G_FORCE_DAMAGE)
            );
            event.player.hurt(damageSource, 2);
        }
    }
}
