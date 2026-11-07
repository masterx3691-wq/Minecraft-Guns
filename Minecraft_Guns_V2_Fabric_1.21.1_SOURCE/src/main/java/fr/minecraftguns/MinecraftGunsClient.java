package fr.minecraftguns;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class MinecraftGunsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        KeyBinding reload = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.minecraftguns.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.minecraftguns"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (reload.wasPressed()) {
                if (client.player != null && client.player.getMainHandStack().isOf(MinecraftGuns.PISTOL))
                    ClientPlayNetworking.send(new ReloadPayload());
            }
        });
    }
}
