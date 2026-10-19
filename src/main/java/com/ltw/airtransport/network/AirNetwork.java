package com.ltw.airtransport.network;

import com.ltw.airtransport.AirTransport;
import com.ltw.airtransport.block.ControlPanelBlockEntity;
import com.ltw.airtransport.block.PadBeaconBlockEntity;
import com.ltw.airtransport.client.ClientHooks;
import com.ltw.airtransport.entity.AirshipEntity;
import com.ltw.airtransport.pad.PadRegistry;
import com.ltw.airtransport.ship.ShipAssembler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AirTransport.ID)
public final class AirNetwork {
    public static final int FLY_TO = 0, ADD_STOP = 1, CLEAR_ROUTE = 2, START_ROUTE = 3, STOP_ROUTE = 4,
        TOGGLE_LOOP = 5, RENAME = 6, LAND = 7, HOVER = 8;

    private AirNetwork() {}

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(OpenPadName.TYPE, OpenPadName.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientHooks.openPadName(p.pos(), p.name())));
        r.playToClient(PanelData.TYPE, PanelData.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientHooks.showPanel(p)));
        r.playToClient(CloseScreen.TYPE, CloseScreen.CODEC, (p, ctx) -> ctx.enqueueWork(ClientHooks::closeScreen));
        r.playToClient(ShipBlocks.TYPE, ShipBlocks.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player().level().getEntity(p.entityId()) instanceof AirshipEntity ship) ship.applyBlockChanges(p.changes());
        }));
        r.playToServer(RenamePad.TYPE, RenamePad.CODEC, AirNetwork::handleRenamePad);
        r.playToServer(PanelAction.TYPE, PanelAction.CODEC, AirNetwork::handlePanelAction);
        r.playToServer(ShipInteract.TYPE, ShipInteract.CODEC, AirNetwork::handleShipInteract);
        r.playToServer(CaptainInput.TYPE, CaptainInput.CODEC, AirNetwork::handleCaptainInput);
        r.playToServer(OpenHelm.TYPE, OpenHelm.CODEC, AirNetwork::handleOpenHelm);
    }

    // --- Payloads -----------------------------------------------------------------------------
    public record OpenPadName(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<OpenPadName> TYPE = new Type<>(AirTransport.id("open_pad_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPadName> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeUtf(p.name, 64); },
            buf -> new OpenPadName(buf.readBlockPos(), buf.readUtf(64)));
        @Override public Type<OpenPadName> type() { return TYPE; }
    }

    public record RenamePad(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<RenamePad> TYPE = new Type<>(AirTransport.id("rename_pad"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RenamePad> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeUtf(p.name, 64); },
            buf -> new RenamePad(buf.readBlockPos(), buf.readUtf(64)));
        @Override public Type<RenamePad> type() { return TYPE; }
    }

    public record PadEntry(UUID id, String name, BlockPos pos) {}

    /** Everything the Control Panel screen shows. {@code entityId} is -1 for a parked ship. */
    public record PanelData(int entityId, BlockPos panelPos, String shipName, String status, boolean ready,
                            float lift, int fuelSeconds, int blocks, int envelopes, int burners,
                            List<PadEntry> pads, List<UUID> route, int routeIndex, boolean routeActive, boolean loop)
        implements CustomPacketPayload {
        public static final Type<PanelData> TYPE = new Type<>(AirTransport.id("panel_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PanelData> CODEC = StreamCodec.of(PanelData::write, PanelData::read);

        private static void write(RegistryFriendlyByteBuf buf, PanelData p) {
            buf.writeVarInt(p.entityId);
            buf.writeBlockPos(p.panelPos);
            buf.writeUtf(p.shipName, 64);
            buf.writeUtf(p.status, 256);
            buf.writeBoolean(p.ready);
            buf.writeFloat(p.lift);
            buf.writeVarInt(p.fuelSeconds);
            buf.writeVarInt(p.blocks);
            buf.writeVarInt(p.envelopes);
            buf.writeVarInt(p.burners);
            buf.writeVarInt(p.pads.size());
            for (PadEntry e : p.pads) {
                buf.writeUUID(e.id());
                buf.writeUtf(e.name(), 64);
                buf.writeBlockPos(e.pos());
            }
            buf.writeVarInt(p.route.size());
            for (UUID id : p.route) buf.writeUUID(id);
            buf.writeVarInt(p.routeIndex);
            buf.writeBoolean(p.routeActive);
            buf.writeBoolean(p.loop);
        }

        private static PanelData read(RegistryFriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            String name = buf.readUtf(64);
            String status = buf.readUtf(256);
            boolean ready = buf.readBoolean();
            float lift = buf.readFloat();
            int fuel = buf.readVarInt();
            int blocks = buf.readVarInt();
            int envelopes = buf.readVarInt();
            int burners = buf.readVarInt();
            int n = buf.readVarInt();
            List<PadEntry> pads = new ArrayList<>();
            for (int i = 0; i < n; i++) pads.add(new PadEntry(buf.readUUID(), buf.readUtf(64), buf.readBlockPos()));
            int m = buf.readVarInt();
            List<UUID> route = new ArrayList<>();
            for (int i = 0; i < m; i++) route.add(buf.readUUID());
            return new PanelData(entityId, pos, name, status, ready, lift, fuel, blocks, envelopes, burners, pads, route,
                buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }

        @Override public Type<PanelData> type() { return TYPE; }
    }

    public record PanelAction(int entityId, BlockPos panelPos, int action, UUID pad, String text) implements CustomPacketPayload {
        public static final Type<PanelAction> TYPE = new Type<>(AirTransport.id("panel_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PanelAction> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.entityId); buf.writeBlockPos(p.panelPos); buf.writeVarInt(p.action); buf.writeUUID(p.pad); buf.writeUtf(p.text, 64); },
            buf -> new PanelAction(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readUUID(), buf.readUtf(64)));
        @Override public Type<PanelAction> type() { return TYPE; }
    }

    public record ShipInteract(int entityId, BlockPos local) implements CustomPacketPayload {
        public static final Type<ShipInteract> TYPE = new Type<>(AirTransport.id("ship_interact"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShipInteract> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.entityId); buf.writeBlockPos(p.local); },
            buf -> new ShipInteract(buf.readVarInt(), buf.readBlockPos()));
        @Override public Type<ShipInteract> type() { return TYPE; }
    }

    public record CaptainInput(int entityId, float forward, float strafe, int turn, float vertical) implements CustomPacketPayload {
        public static final Type<CaptainInput> TYPE = new Type<>(AirTransport.id("captain_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CaptainInput> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.entityId); buf.writeFloat(p.forward); buf.writeFloat(p.strafe); buf.writeByte(p.turn); buf.writeFloat(p.vertical); },
            buf -> new CaptainInput(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readFloat()));
        @Override public Type<CaptainInput> type() { return TYPE; }
    }

    /** Ship blocks that changed in flight (crops grew, a harvester cut kelp). Air means removed. */
    public record ShipBlocks(int entityId, Map<BlockPos, BlockState> changes) implements CustomPacketPayload {
        public static final Type<ShipBlocks> TYPE = new Type<>(AirTransport.id("ship_blocks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShipBlocks> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeVarInt(p.changes.size());
                p.changes.forEach((pos, state) -> { buf.writeBlockPos(pos); buf.writeVarInt(Block.getId(state)); });
            },
            buf -> {
                int id = buf.readVarInt();
                int n = buf.readVarInt();
                Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) changes.put(buf.readBlockPos(), Block.stateById(buf.readVarInt()));
                return new ShipBlocks(id, changes);
            });
        @Override public Type<ShipBlocks> type() { return TYPE; }
    }

    public record CloseScreen() implements CustomPacketPayload {
        public static final Type<CloseScreen> TYPE = new Type<>(AirTransport.id("close_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CloseScreen> CODEC = StreamCodec.unit(new CloseScreen());
        @Override public Type<CloseScreen> type() { return TYPE; }
    }

    public record OpenHelm() implements CustomPacketPayload {
        public static final Type<OpenHelm> TYPE = new Type<>(AirTransport.id("open_helm"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenHelm> CODEC = StreamCodec.unit(new OpenHelm());
        @Override public Type<OpenHelm> type() { return TYPE; }
    }

    // --- Server -> client helpers -------------------------------------------------------------
    public static void openPadName(ServerPlayer player, BlockPos pos, String name) {
        PacketDistributor.sendToPlayer(player, new OpenPadName(pos, name));
    }

    private static List<PadEntry> pads(ServerLevel level, BlockPos from) {
        List<PadEntry> list = new ArrayList<>();
        for (PadRegistry.Pad p : PadRegistry.get(level).sorted(from)) list.add(new PadEntry(p.id(), p.name(), p.pos()));
        return list;
    }

    public static void openPanelForBlock(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof ControlPanelBlockEntity panel)) return;
        ShipAssembler.Preflight check = ShipAssembler.preflight(level, pos);
        String status = check.problem() == null ? "Parked - ready for takeoff" : "Parked - " + check.problem().getString();
        PacketDistributor.sendToPlayer(player, new PanelData(-1, pos, panel.shipName(), status, check.problem() == null,
            (float) check.liftRatio(), 0, check.blocks(), check.envelopes(), check.burners(), pads(level, pos),
            new ArrayList<>(panel.route()), panel.routeIndex(), panel.routeActive(), panel.loop()));
    }

    public static void openPanelForShip(ServerPlayer player, AirshipEntity ship) {
        ControlPanelBlockEntity panel = ship.panel();
        if (panel == null) return;
        ServerLevel level = player.serverLevel();
        PacketDistributor.sendToPlayer(player, new PanelData(ship.getId(), ship.blockPosition(), panel.shipName(), ship.status(), true,
            ship.liftRatio(), ship.fuelSeconds(), ship.structure().size(), ship.structure().envelopes(), ship.structure().burners(),
            pads(level, ship.blockPosition()), new ArrayList<>(panel.route()), panel.routeIndex(), panel.routeActive(), panel.loop()));
    }

    // --- Client -> server handlers ------------------------------------------------------------
    private static void handleRenamePad(RenamePad p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.distanceToSqr(p.pos().getCenter()) > 64) return;
            if (player.serverLevel().getBlockEntity(p.pos()) instanceof PadBeaconBlockEntity be) {
                be.setName(p.name().trim());
                be.register(player.serverLevel());
            }
        });
    }

    @Nullable
    private static AirshipEntity ship(ServerPlayer player, int entityId) {
        if (entityId < 0) return null;
        if (!(player.level().getEntity(entityId) instanceof AirshipEntity ship)) return null;
        double r = ship.structure().radius() + 16;
        return player.distanceToSqr(ship) < r * r ? ship : null;
    }

    private static void handlePanelAction(PanelAction p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            PadRegistry.Pad pad = PadRegistry.get(level).get(p.pad());
            if (p.entityId() >= 0) {
                AirshipEntity ship = ship(player, p.entityId());
                if (ship == null || ship.panel() == null) return;
                ControlPanelBlockEntity panel = ship.panel();
                switch (p.action()) {
                    case FLY_TO -> { if (pad != null) { panel.stopRoute(); ship.flyTo(pad); } }
                    case ADD_STOP -> { if (pad != null) panel.addStop(pad.id()); }
                    case CLEAR_ROUTE -> panel.clearRoute();
                    case START_ROUTE -> {
                        panel.startRoute();
                        UUID next = panel.currentStop();
                        PadRegistry.Pad stop = next == null ? null : PadRegistry.get(level).get(next);
                        if (stop != null) ship.flyTo(stop);
                    }
                    case STOP_ROUTE -> panel.stopRoute();
                    case TOGGLE_LOOP -> panel.setLoop(!panel.loop());
                    case RENAME -> panel.setShipName(p.text().trim());
                    case LAND -> { panel.stopRoute(); ship.landHere(); }
                    case HOVER -> { panel.stopRoute(); ship.hover(); }
                    default -> {}
                }
                openPanelForShip(player, ship);
                return;
            }
            BlockPos pos = p.panelPos();
            if (player.distanceToSqr(pos.getCenter()) > 100) return;
            if (!(level.getBlockEntity(pos) instanceof ControlPanelBlockEntity panel)) return;
            switch (p.action()) {
                case FLY_TO -> {
                    if (pad != null) {
                        panel.stopRoute();
                        if (ShipAssembler.launch(level, pos, pad, player)) {
                            closeAfterLaunch(player);
                            return;
                        }
                    }
                }
                case ADD_STOP -> { if (pad != null) panel.addStop(pad.id()); }
                case CLEAR_ROUTE -> panel.clearRoute();
                case START_ROUTE -> {
                    panel.startRoute();
                    closeAfterLaunch(player);
                    return;
                }
                case STOP_ROUTE -> panel.stopRoute();
                case TOGGLE_LOOP -> panel.setLoop(!panel.loop());
                case RENAME -> panel.setShipName(p.text().trim());
                default -> {}
            }
            openPanelForBlock(player, pos);
        });
    }

    private static void handleShipInteract(ShipInteract p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || player.isSpectator()) return;
            if (!(player.level().getEntity(p.entityId()) instanceof AirshipEntity ship)) return;
            if (!ship.canReach(player, p.local())) return;
            ship.interactBlock(player, p.local());
        });
    }

    private static void handleCaptainInput(CaptainInput p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.getVehicle() instanceof AirshipEntity ship && ship.getId() == p.entityId()) {
                ship.handleInput(player, p.forward(), p.strafe(), p.turn(), p.vertical());
            }
        });
    }

    private static void handleOpenHelm(OpenHelm p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.getVehicle() instanceof AirshipEntity ship) {
                openPanelForShip(player, ship);
            }
        });
    }

    /** Closes the panel screen once the parked ship it showed has taken off. */
    private static void closeAfterLaunch(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.ltwairtransport.launched"), true);
        PacketDistributor.sendToPlayer(player, new CloseScreen());
    }
}
