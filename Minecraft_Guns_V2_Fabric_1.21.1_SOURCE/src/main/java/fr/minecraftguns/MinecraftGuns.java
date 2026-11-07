package fr.minecraftguns;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class MinecraftGuns implements ModInitializer {
    public static final String MOD_ID = "minecraftguns";
    public static final Item AMMO = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "ammo"), new Item(new Item.Settings()));
    public static final PistolItem PISTOL = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "pistol"), new PistolItem(new Item.Settings().maxCount(1)));

    @Override public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> { entries.add(PISTOL); entries.add(AMMO); });
        PayloadTypeRegistry.playC2S().register(ReloadPayload.ID, ReloadPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReloadPayload.ID, (payload, context) ->
            context.server().execute(() -> PistolItem.tryReload(context.player())));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) {
                var stack = player.getMainHandStack();
                if (stack.isOf(PISTOL)) PistolItem.tick(player, stack);
            }
        });
    }
}
