package com.rubab.simplewaypoints;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class SimpleWaypointsClient implements ClientModInitializer {
    private KeyMapping openKey;
    @Override public void onInitializeClient() {
        WaypointStore.load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.simple_waypoints.open", GLFW.GLFW_KEY_B, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) if (client.player != null) client.setScreen(new WaypointScreen());
        });
    }
}
