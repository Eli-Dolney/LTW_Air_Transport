package com.ltw.airtransport.client;

import com.ltw.airtransport.network.AirNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only reactions to server packets. Only touched from client packet handlers. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openPadName(BlockPos pos, String name) {
        Minecraft.getInstance().setScreen(new PadNameScreen(pos, name));
    }

    public static void showPanel(AirNetwork.PanelData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ControlPanelScreen screen && screen.sameTarget(data)) screen.update(data);
        else mc.setScreen(new ControlPanelScreen(data));
    }

    public static void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ControlPanelScreen) mc.setScreen(null);
    }
}
