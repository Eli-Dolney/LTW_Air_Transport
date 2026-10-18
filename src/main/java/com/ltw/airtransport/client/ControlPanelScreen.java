package com.ltw.airtransport.client;

import com.ltw.airtransport.network.AirNetwork;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Pick a destination, build a route between pads, rename the ship, land it. */
public class ControlPanelScreen extends Screen {
    private static final int W = 340, H = 220, ROWS = 6;
    private static final UUID NONE = new UUID(0, 0);

    private AirNetwork.PanelData data;
    private EditBox nameBox;
    private int scroll;
    private int left, top;

    public ControlPanelScreen(AirNetwork.PanelData data) {
        super(Component.translatable("screen.ltwairtransport.panel"));
        this.data = data;
    }

    public boolean sameTarget(AirNetwork.PanelData other) {
        return other.entityId() == data.entityId() && (data.entityId() >= 0 || other.panelPos().equals(data.panelPos()));
    }

    public void update(AirNetwork.PanelData next) {
        String typed = nameBox == null ? null : nameBox.getValue();
        boolean renamed = !next.shipName().equals(data.shipName());
        data = next;
        rebuildWidgets();
        if (typed != null && !renamed) nameBox.setValue(typed);
    }

    private void send(int action, UUID pad, String text) {
        PacketDistributor.sendToServer(new AirNetwork.PanelAction(data.entityId(), data.panelPos(), action, pad, text));
    }

    private void send(int action) {
        send(action, NONE, "");
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        nameBox = new EditBox(font, left + 8, top + 8, 160, 18, Component.translatable("screen.ltwairtransport.ship_name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(data.shipName());
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.ltwairtransport.rename"),
            b -> send(AirNetwork.RENAME, NONE, nameBox.getValue())).bounds(left + 172, top + 7, 56, 20).build());

        // Destinations
        List<AirNetwork.PadEntry> pads = data.pads();
        scroll = Math.max(0, Math.min(scroll, pads.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < pads.size(); i++) {
            AirNetwork.PadEntry pad = pads.get(scroll + i);
            int y = top + 78 + i * 20;
            addRenderableWidget(Button.builder(Component.translatable("screen.ltwairtransport.go"), b -> send(AirNetwork.FLY_TO, pad.id(), ""))
                .bounds(left + 122, y, 28, 18).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("screen.ltwairtransport.go_tip", pad.name()))).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> send(AirNetwork.ADD_STOP, pad.id(), ""))
                .bounds(left + 152, y, 18, 18).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("screen.ltwairtransport.add_stop_tip"))).build());
        }
        if (pads.size() > ROWS) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll--; rebuildWidgets(); })
                .bounds(left + 152, top + 62, 18, 14).build()).active = scroll > 0;
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll++; rebuildWidgets(); })
                .bounds(left + 152, top + 78 + ROWS * 20, 18, 14).build()).active = scroll + ROWS < pads.size();
        }

        // Route
        int rx = left + 184;
        addRenderableWidget(Button.builder(Component.translatable(data.routeActive() ? "screen.ltwairtransport.stop_route" : "screen.ltwairtransport.start_route"),
            b -> send(data.routeActive() ? AirNetwork.STOP_ROUTE : AirNetwork.START_ROUTE)).bounds(rx, top + 176, 70, 18).build())
            .active = data.routeActive() || !data.route().isEmpty();
        addRenderableWidget(Button.builder(Component.translatable(data.loop() ? "screen.ltwairtransport.loop_on" : "screen.ltwairtransport.loop_off"),
            b -> send(AirNetwork.TOGGLE_LOOP)).bounds(rx + 72, top + 176, 40, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.ltwairtransport.clear"),
            b -> send(AirNetwork.CLEAR_ROUTE)).bounds(rx + 114, top + 176, 36, 18).build());

        // Flight
        if (data.entityId() >= 0) {
            addRenderableWidget(Button.builder(Component.translatable("screen.ltwairtransport.land"), b -> send(AirNetwork.LAND))
                .bounds(left + 8, top + H - 24, 80, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.ltwairtransport.hover"), b -> send(AirNetwork.HOVER))
                .bounds(left + 92, top + H - 24, 80, 18).build());
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, 0xFF8B5A2B);
        g.fill(left, top, left + W, top + H, 0xEE1E1A16);
        g.fill(left + 178, top + 58, left + 179, top + H - 30, 0xFF5A4630);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int y = top + 32;
        g.drawString(font, Component.literal(data.status()).withStyle(data.ready() ? ChatFormatting.GREEN : ChatFormatting.GOLD), left + 8, y, 0xFFFFFF);
        int liftPct = Math.round(data.lift() * 100);
        String fuel = data.entityId() >= 0 ? "  Fuel: " + time(data.fuelSeconds()) : "";
        g.drawString(font, Component.literal("Lift: " + liftPct + "%" + fuel + "   Blocks: " + data.blocks()
            + "  Balloon: " + data.envelopes() + "  Burners: " + data.burners()), left + 8, y + 12, liftPct >= 100 ? 0xB0E0A0 : 0xE08080);

        g.drawString(font, Component.translatable("screen.ltwairtransport.destinations").withStyle(ChatFormatting.YELLOW), left + 8, top + 64, 0xFFFFFF);
        Minecraft mc = Minecraft.getInstance();
        List<AirNetwork.PadEntry> pads = data.pads();
        if (pads.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("screen.ltwairtransport.no_pads"), left + 8, top + 80, 160, 0x909090);
        }
        for (int i = 0; i < ROWS && scroll + i < pads.size(); i++) {
            AirNetwork.PadEntry pad = pads.get(scroll + i);
            int ry = top + 78 + i * 20;
            int dist = mc.player == null ? 0 : (int) Math.sqrt(mc.player.distanceToSqr(pad.pos().getCenter()));
            String name = font.plainSubstrByWidth(pad.name(), 80);
            g.drawString(font, name, left + 8, ry + 5, 0xFFFFFF);
            g.drawString(font, dist + "m", left + 92, ry + 5, 0x909090);
        }

        int rx = left + 184;
        g.drawString(font, Component.translatable("screen.ltwairtransport.route").withStyle(ChatFormatting.YELLOW), rx, top + 64, 0xFFFFFF);
        Map<UUID, String> names = new HashMap<>();
        for (AirNetwork.PadEntry p : pads) names.put(p.id(), p.name());
        List<UUID> route = data.route();
        if (route.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("screen.ltwairtransport.route_hint"), rx, top + 80, 148, 0x909090);
        }
        for (int i = 0; i < route.size() && i < 8; i++) {
            boolean current = data.routeActive() && i == Math.floorMod(data.routeIndex(), route.size());
            String label = (current ? "▶ " : (i + 1) + ". ") + names.getOrDefault(route.get(i), "(missing pad)");
            g.drawString(font, font.plainSubstrByWidth(label, 148), rx, top + 80 + i * 11, current ? 0x80FF80 : 0xDDDDDD);
        }
        if (route.size() > 8) g.drawString(font, "+" + (route.size() - 8) + " more", rx, top + 80 + 8 * 11, 0x909090);
        if (data.entityId() < 0) {
            g.drawString(font, Component.translatable("screen.ltwairtransport.parked_hint"), left + 8, top + H - 18, 0x909090);
        }
    }

    private static String time(int seconds) {
        return seconds >= 60 ? (seconds / 60) + "m" + (seconds % 60) + "s" : seconds + "s";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
