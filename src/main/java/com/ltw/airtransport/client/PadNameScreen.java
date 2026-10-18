package com.ltw.airtransport.client;

import com.ltw.airtransport.network.AirNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Names a Pad Beacon ("North Base", "Mine", ...). */
public class PadNameScreen extends Screen {
    private final BlockPos pos;
    private final String initial;
    private EditBox name;

    public PadNameScreen(BlockPos pos, String initial) {
        super(Component.translatable("screen.ltwairtransport.pad_name"));
        this.pos = pos;
        this.initial = initial;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        name = new EditBox(font, cx - 100, cy - 10, 200, 20, Component.translatable("screen.ltwairtransport.pad_name"));
        name.setMaxLength(32);
        name.setValue(initial);
        addRenderableWidget(name);
        setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save()).bounds(cx - 50, cy + 18, 100, 20).build());
    }

    private void save() {
        PacketDistributor.sendToServer(new AirNetwork.RenamePad(pos, name.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, height / 2 - 36, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.ltwairtransport.pad_name_hint"), width / 2, height / 2 - 24, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
