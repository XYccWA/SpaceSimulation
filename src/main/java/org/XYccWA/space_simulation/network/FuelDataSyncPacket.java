package org.XYccWA.space_simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.XYccWA.space_simulation.capability.CapabilityHandler;

import java.util.function.Supplier;

public class FuelDataSyncPacket {
    private final float fuelRemaining;
    private final float maxFuel;

    public FuelDataSyncPacket(float fuelRemaining, float maxFuel) {
        this.fuelRemaining = fuelRemaining;
        this.maxFuel = maxFuel;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(fuelRemaining);
        buffer.writeFloat(maxFuel);
    }

    public static FuelDataSyncPacket decode(FriendlyByteBuf buffer) {
        return new FuelDataSyncPacket(buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(FuelDataSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 在客户端处理燃料数据同步
            if (context.getDirection().getReceptionSide().isClient()) {
                net.minecraft.client.Minecraft.getInstance().player.getCapability(CapabilityHandler.FUEL_REMAINING).ifPresent(fuel -> {
                    fuel.setFuelRemaining(packet.fuelRemaining);
                    fuel.setMaxFuel(packet.maxFuel);
                });
            }
        });
        context.setPacketHandled(true);
    }
}
