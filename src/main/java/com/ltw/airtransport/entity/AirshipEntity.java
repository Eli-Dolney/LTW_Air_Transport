package com.ltw.airtransport.entity;

import com.ltw.airtransport.AirConfig;
import com.ltw.airtransport.AirTransport;
import com.ltw.airtransport.block.BurnerBlock;
import com.ltw.airtransport.block.BurnerBlockEntity;
import com.ltw.airtransport.block.ControlPanelBlock;
import com.ltw.airtransport.block.ControlPanelBlockEntity;
import com.ltw.airtransport.block.SeatBlock;
import com.ltw.airtransport.menu.BurnerMenu;
import com.ltw.airtransport.menu.ShipContainer;
import com.ltw.airtransport.network.AirNetwork;
import com.ltw.airtransport.pad.PadRegistry;
import com.ltw.airtransport.ship.ShipAssembler;
import com.ltw.airtransport.ship.ShipChunkLoading;
import com.ltw.airtransport.ship.ShipCollision;
import com.ltw.airtransport.ship.ShipFarming;
import com.ltw.airtransport.ship.ShipFlight;
import com.ltw.airtransport.ship.ShipRegistry;
import com.ltw.airtransport.ship.ShipStructure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A flying ship made of real blocks. It sits at the bottom-center of its Control Panel block and
 * only ever faces one of the four compass directions (turns are animated), which keeps its block
 * collision exact. Hot-air balloons drift, so it moves in any direction regardless of facing.
 */
public class AirshipEntity extends Entity implements IEntityWithComplexSpawn {
    public enum Mode { HOVER, MANUAL, AUTOPILOT, LANDING }

    private static final EntityDataAccessor<Boolean> DATA_BURNING = SynchedEntityData.defineId(AirshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_LIFT = SynchedEntityData.defineId(AirshipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(AirshipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> DATA_SEATS = SynchedEntityData.defineId(AirshipEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<String> DATA_STATUS = SynchedEntityData.defineId(AirshipEntity.class, EntityDataSerializers.STRING);

    private static final float TURN_SPEED = 2.5f;
    private static final double CLIMB_SPEED = 0.18;

    /** Set by the client setup: returns the local player for a client level, so decks can carry them. */
    public static Function<Level, Entity> localPlayer = level -> null;

    private ShipStructure structure = new ShipStructure();
    private final Map<BlockPos, BlockEntity> blockEntities = new LinkedHashMap<>();
    private final Map<UUID, BlockPos> seats = new HashMap<>();

    // Flight state (server)
    private Mode mode = Mode.HOVER;
    private float targetYaw;
    @Nullable private PadRegistry.Pad target;
    @Nullable private PadRegistry.Pad arrivalPad;
    private double landX, landZ;
    private double cruiseY;
    private Vec3 velocity = Vec3.ZERO;
    private float inForward, inStrafe, inVertical;
    private int inTurn;
    private int inputAge = 100;
    private int litBurners;
    private int groundedTicks;
    private int blockedTicks;
    private Set<Long> heldChunks = new HashSet<>();
    private double growBudget;

    // Deck entities held in place while the ship turns
    private final Map<Entity, Vec3> turnLocks = new HashMap<>();
    private int stillTicks;

    // Client interpolation
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw;
    private int lerpSteps;

    public AirshipEntity(EntityType<? extends AirshipEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public AirshipEntity(Level level, ShipStructure structure) {
        this(AirTransport.AIRSHIP.get(), level);
        this.structure = structure;
        createBlockEntities();
        refreshDimensions();
    }

    // --- Setup / sync -------------------------------------------------------------------------
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BURNING, false);
        builder.define(DATA_LIFT, 0f);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_SEATS, new CompoundTag());
        builder.define(DATA_STATUS, "");
    }

    private void createBlockEntities() {
        blockEntities.clear();
        HolderLookup.Provider registries = level().registryAccess();
        for (Map.Entry<BlockPos, CompoundTag> e : structure.blockEntities().entrySet()) {
            BlockState state = structure.blocks().get(e.getKey());
            if (state == null) continue;
            BlockEntity be = BlockEntity.loadStatic(e.getKey(), state, e.getValue(), registries);
            if (be == null) continue;
            if (level().isClientSide) be.setLevel(level()); // block entity renderers want a level
            blockEntities.put(e.getKey(), be);
        }
    }

    /** Copies live block entity data (inventories, fuel, route) back into the structure. */
    public void syncBlockEntities() {
        if (level().isClientSide) return;
        HolderLookup.Provider registries = level().registryAccess();
        blockEntities.forEach((pos, be) -> structure.blockEntities().put(pos, be.saveWithFullMetadata(registries)));
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        syncBlockEntities();
        buf.writeNbt(structure.save());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            structure = ShipStructure.load(tag, ShipStructure.blockLookup(level()));
            createBlockEntities();
            setBoundingBox(makeBoundingBox());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        structure = ShipStructure.load(tag.getCompound("structure"), ShipStructure.blockLookup(level()));
        createBlockEntities();
        try {
            mode = Mode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.HOVER;
        }
        targetYaw = tag.getFloat("targetYaw");
        cruiseY = tag.getDouble("cruiseY");
        landX = tag.getDouble("landX");
        landZ = tag.getDouble("landZ");
        target = readPad(tag.getCompound("target"));
        arrivalPad = readPad(tag.getCompound("arrival"));
        seats.clear();
        CompoundTag seatTag = tag.getCompound("seats");
        for (String key : seatTag.getAllKeys()) seats.put(UUID.fromString(key), BlockPos.of(seatTag.getLong(key)));
        syncSeats();
        setBoundingBox(makeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        syncBlockEntities();
        tag.put("structure", structure.save());
        tag.putString("mode", mode.name());
        tag.putFloat("targetYaw", targetYaw);
        tag.putDouble("cruiseY", cruiseY);
        tag.putDouble("landX", landX);
        tag.putDouble("landZ", landZ);
        if (target != null) tag.put("target", writePad(target));
        if (arrivalPad != null) tag.put("arrival", writePad(arrivalPad));
        CompoundTag seatTag = new CompoundTag();
        seats.forEach((id, pos) -> seatTag.putLong(id.toString(), pos.asLong()));
        tag.put("seats", seatTag);
    }

    private static CompoundTag writePad(PadRegistry.Pad pad) {
        CompoundTag c = new CompoundTag();
        c.putUUID("id", pad.id());
        c.putString("name", pad.name());
        c.put("pos", NbtUtils.writeBlockPos(pad.pos()));
        return c;
    }

    @Nullable
    private static PadRegistry.Pad readPad(CompoundTag c) {
        if (!c.hasUUID("id")) return null;
        return new PadRegistry.Pad(c.getUUID("id"), c.getString("name"), NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO));
    }

    // --- Basic entity behavior ----------------------------------------------------------------
    public ShipStructure structure() {
        return structure;
    }

    /** Live block entities of the ship, by ship-local position. */
    public Map<BlockPos, BlockEntity> blockEntities() {
        return blockEntities;
    }

    public int quadrant() {
        return Math.floorMod(Math.round(getYRot() / 90f), 4);
    }

    @Override
    protected AABB makeBoundingBox() {
        if (structure == null || structure.size() == 0) return super.makeBoundingBox();
        return structure.bounds(quadrant()).move(position());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (structure == null || structure.size() == 0) return super.getBoundingBoxForCulling();
        double r = structure.radius() + 1;
        AABB b = structure.bounds(0);
        return new AABB(getX() - r, getY() + b.minY - 1, getZ() - r, getX() + r, getY() + b.maxY + 1, getZ() + r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double r = 64 + (structure == null ? 0 : structure.radius()) * 2;
        return distance < r * r * 4;
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isNoGravity() { return true; }
    @Override public boolean fireImmune() { return true; }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        ShipCollision.track(this);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        ShipCollision.untrack(this);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) releaseChunks();
        super.remove(reason);
    }

    public void discardAfterLanding() {
        releaseChunks();
        discard();
    }

    private void releaseChunks() {
        if (level() instanceof ServerLevel server && !heldChunks.isEmpty()) {
            heldChunks = ShipChunkLoading.update(server, getUUID(), heldChunks, chunkPosition(), false);
        }
    }

    @Nullable
    public ControlPanelBlockEntity panel() {
        return blockEntities.get(BlockPos.ZERO) instanceof ControlPanelBlockEntity p ? p : null;
    }

    public String shipName() {
        ControlPanelBlockEntity panel = panel();
        return panel == null ? "Airship" : panel.shipName();
    }

    public Mode mode() { return mode; }
    public boolean isBurning() { return entityData.get(DATA_BURNING); }
    public float liftRatio() { return entityData.get(DATA_LIFT); }
    public int fuelSeconds() { return entityData.get(DATA_FUEL); }
    public String status() { return entityData.get(DATA_STATUS); }
    @Nullable public PadRegistry.Pad arrivalPad() { return arrivalPad; }
    @Nullable public PadRegistry.Pad target() { return target; }

    // --- Seats --------------------------------------------------------------------------------
    /** Sits {@code entity} in the seat block at ship-local {@code local}. */
    public boolean seat(Entity entity, BlockPos local) {
        if (!(structure.blocks().get(local) != null && structure.blocks().get(local).getBlock() instanceof SeatBlock)) return false;
        if (seats.containsValue(local)) return false;
        if (entity.isPassenger()) entity.stopRiding();
        seats.put(entity.getUUID(), local.immutable());
        syncSeats();
        if (!entity.startRiding(this, true)) {
            seats.remove(entity.getUUID());
            syncSeats();
            return false;
        }
        // Face the way the seat faces.
        float yaw = structure.blocks().get(local).getValue(SeatBlock.FACING).toYRot() + getYRot();
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        return true;
    }

    @Nullable
    public BlockPos seatOf(Entity entity) {
        if (!level().isClientSide) return seats.get(entity.getUUID());
        CompoundTag tag = entityData.get(DATA_SEATS);
        String key = entity.getUUID().toString();
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    private void syncSeats() {
        CompoundTag tag = new CompoundTag();
        seats.forEach((id, pos) -> tag.putLong(id.toString(), pos.asLong()));
        entityData.set(DATA_SEATS, tag);
    }

    @Nullable
    public Player captain() {
        BlockPos helm = structure.captainSeat();
        if (helm == null) return null;
        for (Entity p : getPassengers()) {
            if (p instanceof Player player && helm.equals(seatOf(p))) return player;
        }
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return seats.containsKey(passenger.getUUID());
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && seats.remove(passenger.getUUID()) != null) syncSeats();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null; // steering goes through our own packets, never vanilla vehicle control
    }

    private Vec3 seatPoint(BlockPos seat, double up) {
        return position().add(ShipStructure.rotate(seat.getX(), seat.getY() + up, seat.getZ(), getYRot()));
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        BlockPos seat = seatOf(passenger);
        if (seat == null) {
            super.positionRider(passenger, move);
            return;
        }
        Vec3 p = seatPoint(seat, 0.45).subtract(passenger.getVehicleAttachmentPoint(this));
        move.accept(passenger, p.x, p.y, p.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos seat = seatOf(passenger);
        return seat == null ? super.getDismountLocationForPassenger(passenger) : seatPoint(seat, 1.05);
    }

    // --- Commands from the control panel / captain ---------------------------------------------
    public void flyTo(PadRegistry.Pad pad) {
        target = pad;
        arrivalPad = null;
        mode = Mode.AUTOPILOT;
        cruiseY = getY();
    }

    public void landHere() {
        target = null;
        arrivalPad = null;
        mode = Mode.LANDING;
        targetYaw = quadrant() * 90f;
        landX = Math.floor(getX()) + 0.5;
        landZ = Math.floor(getZ()) + 0.5;
    }

    public void hover() {
        target = null;
        mode = Mode.HOVER;
    }

    /** Captain keys: forward/back, strafe, turn (-1 left, 1 right), up/down. */
    public void handleInput(ServerPlayer player, float forward, float strafe, int turn, float vertical) {
        if (captain() != player) return;
        inForward = Mth.clamp(forward, -1, 1);
        inStrafe = Mth.clamp(strafe, -1, 1);
        inTurn = Mth.clamp(turn, -1, 1);
        inVertical = Mth.clamp(vertical, -1, 1);
        inputAge = 0;
        boolean any = inForward != 0 || inStrafe != 0 || inTurn != 0 || inVertical != 0;
        if (any && mode != Mode.MANUAL) {
            if (mode == Mode.AUTOPILOT || mode == Mode.LANDING) {
                player.displayClientMessage(Component.translatable("message.ltwairtransport.manual_override"), true);
                ControlPanelBlockEntity panel = panel();
                if (panel != null) panel.stopRoute();
            }
            target = null;
            mode = Mode.MANUAL;
        }
    }

    // --- Ticking ------------------------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();
        if (structure.size() == 0) {
            if (!level().isClientSide) discard();
            return;
        }
        if (level().isClientSide) clientTick();
        else serverTick();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yRot;
        lerpSteps = Math.min(steps, 3);
    }

    private void clientTick() {
        Vec3 oldPos = position();
        float oldYaw = getYRot();
        int oldQuadrant = quadrant();
        List<Entity> deck = new ArrayList<>();
        Entity local = localPlayer.apply(level());
        if (local != null && !local.isPassenger() && !local.isSpectator() && ShipCollision.isOnDeck(this, local, oldPos, oldQuadrant)) deck.add(local);

        if (lerpSteps > 0) {
            double nx = getX() + (lerpX - getX()) / lerpSteps;
            double ny = getY() + (lerpY - getY()) / lerpSteps;
            double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
            setYRot(getYRot() + Mth.wrapDegrees(lerpYaw - getYRot()) / lerpSteps);
            lerpSteps--;
            setPos(nx, ny, nz);
        }
        carry(deck, oldPos, oldYaw);

        if (isBurning() && random.nextInt(3) == 0) {
            for (BlockPos burner : structure.burnerPositions()) {
                Vec3 p = position().add(ShipStructure.rotate(burner.getX(), burner.getY() + 1.0, burner.getZ(), getYRot()));
                BurnerBlock.flameParticles(level(), p.x, p.y, p.z, random);
            }
        }
    }

    private void serverTick() {
        ServerLevel level = (ServerLevel) level();
        Vec3 oldPos = position();
        float oldYaw = getYRot();
        int oldQuadrant = quadrant();
        List<Entity> deck = level.getEntities(this, getBoundingBox().inflate(0.5, 3.0, 0.5),
            e -> !(e instanceof AirshipEntity) && !(e instanceof Player) && !e.isPassenger() && !e.noPhysics
                && ShipCollision.isOnDeck(this, e, oldPos, oldQuadrant));

        burnFuel();
        farmTick(level);
        double lift = ShipFlight.liftCapacity(structure.envelopes(), litBurners);
        double ratio = structure.weight() <= 0 ? 1 : lift / structure.weight();
        entityData.set(DATA_BURNING, litBurners > 0);
        entityData.set(DATA_LIFT, (float) ratio);

        if (inputAge < 100) inputAge++;
        if (inputAge > 10) inForward = inStrafe = inVertical = inTurn = 0;

        Vec3 desired = switch (mode) {
            case HOVER -> Vec3.ZERO;
            case MANUAL -> manualMotion();
            case AUTOPILOT -> autopilotMotion(level);
            case LANDING -> landingMotion(level);
        };
        boolean falling = ratio < 1.0;
        if (falling) {
            double sink = ratio > 0.75 ? -0.04 : -0.12;
            desired = new Vec3(desired.x * 0.5, Math.min(desired.y, sink), desired.z * 0.5);
        }

        // Smooth acceleration; landing moves are exact so they are not smoothed.
        if (mode == Mode.LANDING) velocity = desired;
        else velocity = new Vec3(Mth.lerp(0.1, velocity.x, desired.x), Mth.lerp(0.25, velocity.y, desired.y), Mth.lerp(0.1, velocity.z, desired.z));

        turnToward(level);
        Vec3 moved = ShipFlight.move(level, structure, quadrant(), position(), velocity);
        setPos(position().add(moved));
        boolean groundHit = velocity.y < -1.0E-4 && moved.y > velocity.y + 1.0E-4;
        boolean sideHit = Math.abs(moved.x - velocity.x) > 1.0E-4 || Math.abs(moved.z - velocity.z) > 1.0E-4;
        if (groundHit || sideHit) velocity = moved;
        blockedTicks = sideHit ? blockedTicks + 1 : 0;

        carry(deck, oldPos, oldYaw);
        boolean resting = groundHit || (mode == Mode.HOVER && tickCount % 40 == 0 && captain() == null
            && ShipFlight.groundBelow(level, structure, quadrant(), position(), 0.1));
        handleGround(level, resting, falling);
        if (isRemoved()) return;

        String status = statusText(falling);
        entityData.set(DATA_STATUS, status);
        if (tickCount % 20 == 0) {
            entityData.set(DATA_FUEL, (int) (fuelTicks() / Math.max(1, structure.burners()) / 20));
            ControlPanelBlockEntity panel = panel();
            if (panel != null) {
                ShipRegistry.get(level.getServer()).update(panel.shipId(), panel.shipName(), level, blockPosition(), status,
                    target == null ? null : target.pos());
            }
            boolean wantChunks = AirConfig.CHUNK_LOAD_AUTOPILOT.get() && (mode == Mode.AUTOPILOT || mode == Mode.LANDING);
            if (wantChunks || !heldChunks.isEmpty()) heldChunks = ShipChunkLoading.update(level, getUUID(), heldChunks, chunkPosition(), wantChunks);
        }
        if (getY() < level.getMinBuildHeight() - 32) discard();
    }

    private String statusText(boolean falling) {
        if (falling && mode != Mode.LANDING) return litBurners == 0 ? "Out of fuel - descending" : "Too heavy - descending";
        return switch (mode) {
            case HOVER -> "Hovering";
            case MANUAL -> "Manual flight";
            case AUTOPILOT -> target == null ? "Autopilot" : "Flying to " + target.name();
            case LANDING -> arrivalPad != null ? "Landing at " + arrivalPad.name() : "Landing";
        };
    }

    // --- Fuel ---------------------------------------------------------------------------------
    private void burnFuel() {
        litBurners = 0;
        double mult = AirConfig.FUEL_USE_MULTIPLIER.get();
        for (BlockPos pos : structure.burnerPositions()) {
            if (blockEntities.get(pos) instanceof BurnerBlockEntity burner && burner.flightTick(mult, this::takeCargoFuel)) litBurners++;
        }
    }

    /** One fuel item from any container on board (dried kelp blocks, coal, logs, ...), or empty. */
    private ItemStack takeCargoFuel(BurnerBlockEntity requester) {
        for (BlockEntity be : blockEntities.values()) {
            if (be == requester || be instanceof BurnerBlockEntity || !(be instanceof Container c)) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (BurnerBlockEntity.fuelValue(c.getItem(i)) > 0) return c.removeItem(i, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    private long fuelTicks() {
        long total = 0;
        for (BlockEntity be : blockEntities.values()) {
            if (be instanceof BurnerBlockEntity burner) total += burner.fuelTicks();
            else if (be instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    total += (long) BurnerBlockEntity.fuelValue(s) * s.getCount();
                }
            }
        }
        return total;
    }

    // --- Flying farms -------------------------------------------------------------------------
    /**
     * Random-ticks plants on board as densely as the world does (randomTickSpeed per 16^3 blocks)
     * and runs harvesters and kelp dryers once a second.
     */
    private void farmTick(ServerLevel level) {
        Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        int plants = structure.farmPositions().size();
        if (plants > 0) {
            int speed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            growBudget = Math.min(growBudget + plants * speed * AirConfig.FARM_GROWTH_MULTIPLIER.get() / 4096.0, 64);
            for (; growBudget >= 1; growBudget--) growRandomPlant(level, changes);
        } else {
            growBudget = 0;
        }
        if (tickCount % 20 == 0) runFarmMachines(level, changes);
        commitBlockChanges(changes);
    }

    private void growRandomPlant(ServerLevel level, Map<BlockPos, BlockState> changes) {
        List<BlockPos> plants = structure.farmPositions();
        if (plants.isEmpty()) return;
        BlockPos local = plants.get(random.nextInt(plants.size()));
        ShipFarming.randomTick(structure, local, random, p -> roomFor(level, p), changes);
    }

    /** Can a plant grow into ship-local {@code local}? It must be empty on the ship and clear in the world. */
    private boolean roomFor(ServerLevel level, BlockPos local) {
        if (structure.blocks().containsKey(local)) return false;
        BlockPos r = local.rotate(ShipStructure.rotation(quadrant()));
        AABB box = new AABB(r.getX() - 0.5, r.getY(), r.getZ() - 0.5, r.getX() + 0.5, r.getY() + 1, r.getZ() + 0.5)
            .move(position()).deflate(1.0E-3);
        for (AABB solid : ShipFlight.worldSolids(level, box)) {
            if (solid.intersects(box)) return false;
        }
        return true;
    }

    /** Harvesters put ripe crops into a chest next to them; kelp dryers turn kelp in a chest next to them into dried kelp blocks. */
    private void runFarmMachines(ServerLevel level, Map<BlockPos, BlockState> changes) {
        for (BlockPos harvester : structure.harvesters()) {
            Container out = adjacentContainer(harvester);
            if (out == null) continue;
            BlockPos origin = BlockPos.containing(position().add(ShipStructure.rotate(harvester.getX(), harvester.getY(), harvester.getZ(), getYRot())));
            for (Direction dir : Direction.values()) {
                ShipFarming.Harvest harvest = ShipFarming.planHarvest(structure, harvester.relative(dir));
                if (harvest == null) continue;
                ItemStack[] items = ShipFarming.snapshot(out);
                boolean fits = true;
                for (BlockState cut : harvest.cut()) {
                    for (ItemStack drop : Block.getDrops(cut, level, origin, null)) fits &= ShipFarming.insert(out, items, drop);
                }
                if (!fits) break; // chest is full: leave the crop standing
                ShipFarming.writeBack(out, items);
                ShipFarming.apply(structure, harvest.result(), changes);
            }
        }
        for (BlockPos dryer : structure.dryers()) {
            for (Direction dir : Direction.values()) {
                if (blockEntities.get(dryer.relative(dir)) instanceof Container c && !(c instanceof BurnerBlockEntity)) ShipFarming.dryKelp(c);
            }
        }
    }

    @Nullable
    private Container adjacentContainer(BlockPos local) {
        for (Direction dir : Direction.values()) {
            if (blockEntities.get(local.relative(dir)) instanceof Container c && !(c instanceof BurnerBlockEntity)) return c;
        }
        return null;
    }

    /** After blocks changed in flight: rebuild counts and collision, then tell watching clients. */
    private void commitBlockChanges(Map<BlockPos, BlockState> changes) {
        if (changes.isEmpty()) return;
        changes.forEach((pos, state) -> { if (state.isAir()) blockEntities.remove(pos); });
        structure.recompute();
        setBoundingBox(makeBoundingBox());
        PacketDistributor.sendToPlayersTrackingEntity(this, new AirNetwork.ShipBlocks(getId(), new LinkedHashMap<>(changes)));
    }

    /** Client side of {@link #commitBlockChanges}. */
    public void applyBlockChanges(Map<BlockPos, BlockState> changes) {
        changes.forEach((pos, state) -> {
            structure.set(pos, state);
            if (state.isAir()) blockEntities.remove(pos);
        });
        structure.recompute();
        setBoundingBox(makeBoundingBox());
    }

    /** Gives {@code count} random ticks to plants on board right away (the ship does this by itself in flight). */
    public void growPlants(int count) {
        if (!(level() instanceof ServerLevel level)) return;
        for (int i = 0; i < count; i++) {
            Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
            growRandomPlant(level, changes);
            commitBlockChanges(changes);
        }
    }

    /** Runs harvesters and kelp dryers once right away (the ship does this every second in flight). */
    public void runFarmMachines() {
        if (!(level() instanceof ServerLevel level)) return;
        Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        runFarmMachines(level, changes);
        commitBlockChanges(changes);
    }

    // --- Steering -----------------------------------------------------------------------------
    /** World yaw the bow points at for a given ship yaw. */
    private float bowYaw(float shipYaw) {
        return structure.bow().toYRot() + shipYaw;
    }

    private static Vec3 horizontal(float yaw, double speed) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r) * speed, 0, Math.cos(r) * speed);
    }

    private boolean turning() {
        return Math.abs(Mth.wrapDegrees(targetYaw - getYRot())) > 0.01f;
    }

    private void turnToward(ServerLevel level) {
        float diff = Mth.wrapDegrees(targetYaw - getYRot());
        if (Math.abs(diff) <= 0.01f) {
            if (getYRot() != targetYaw) setYRot(targetYaw);
            return;
        }
        int before = quadrant();
        float step = Mth.clamp(diff, -TURN_SPEED, TURN_SPEED);
        float old = getYRot();
        setYRot(old + step);
        if (quadrant() != before) {
            // The collision footprint swaps at the halfway point: make sure the new one fits.
            List<net.minecraft.world.phys.AABB> solids = ShipFlight.worldSolids(level, structure.bounds(quadrant()).move(position()).inflate(0.1));
            if (ShipFlight.collides(structure.boxes(quadrant()), position(), solids)) {
                setYRot(before * 90f);
                targetYaw = before * 90f;
            }
        }
        setBoundingBox(makeBoundingBox());
    }

    private Vec3 manualMotion() {
        if (captain() == null) {
            mode = Mode.HOVER;
            return Vec3.ZERO;
        }
        if (inTurn != 0 && !turning()) targetYaw = Math.round(getYRot() / 90f) * 90f + 90f * inTurn;
        double speed = ShipFlight.cruiseSpeed(structure.propellers());
        float bow = bowYaw(targetYaw);
        Vec3 h = horizontal(bow, speed * inForward).add(horizontal(bow - 90f, speed * 0.6 * inStrafe));
        return new Vec3(h.x, inVertical * CLIMB_SPEED, h.z);
    }

    private void computeLandingSpot(PadRegistry.Pad pad) {
        AABB b = structure.bounds(Math.floorMod(Math.round(targetYaw / 90f), 4));
        double cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2;
        landX = Math.floor(pad.pos().getX() + 0.5 - cx) + 0.5;
        landZ = Math.floor(pad.pos().getZ() + 0.5 - cz) + 0.5;
    }

    private Vec3 autopilotMotion(ServerLevel level) {
        if (target == null) {
            mode = Mode.HOVER;
            return Vec3.ZERO;
        }
        double dx0 = target.pos().getX() + 0.5 - getX(), dz0 = target.pos().getZ() + 0.5 - getZ();
        if (dx0 * dx0 + dz0 * dz0 > 24 * 24) {
            // Point the bow roughly at the destination, in quarter turns.
            float bearing = (float) Math.toDegrees(Math.atan2(-dx0, dz0));
            float want = bearing - structure.bow().toYRot();
            float snapped = Math.round(want / 90f) * 90f;
            float current = Math.round(getYRot() / 90f) * 90f;
            targetYaw = current + Math.round(Mth.wrapDegrees(snapped - current) / 90f) * 90f;
        }
        computeLandingSpot(target);
        double dx = landX - getX(), dz = landZ - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (tickCount % 10 == 0 || cruiseY == 0) cruiseY = Math.max(cruiseY, requiredAltitude(level, dx, dz, dist));
        if (blockedTicks > 2) cruiseY = Math.max(cruiseY, getY() + 2);
        cruiseY = Math.min(cruiseY, level.getMaxBuildHeight() - structure.bounds(quadrant()).maxY - 2);

        double vy = Mth.clamp((cruiseY - getY()) * 0.1, -0.1, CLIMB_SPEED);
        if (getY() < cruiseY - 4) return new Vec3(0, CLIMB_SPEED, 0); // take off straight up first
        if (dist < 0.05 && !turning()) {
            setPos(landX, getY(), landZ);
            arrivalPad = target;
            mode = Mode.LANDING;
            return Vec3.ZERO;
        }
        double speed = Math.min(ShipFlight.cruiseSpeed(structure.propellers()), dist * 0.08 + 0.02);
        speed = Math.min(speed, dist);
        return new Vec3(dx / dist * speed, vy, dz / dist * speed);
    }

    /** Lowest safe Y for the entity: terrain under and ahead of the ship plus the cruise clearance. */
    private double requiredAltitude(ServerLevel level, double dx, double dz, double dist) {
        AABB b = structure.bounds(quadrant()).move(position());
        int ground = level.getMinBuildHeight();
        double[][] points = {
            {b.minX, b.minZ}, {b.maxX, b.minZ}, {b.minX, b.maxZ}, {b.maxX, b.maxZ}, {getX(), getZ()}
        };
        for (double[] p : points) ground = Math.max(ground, height(level, p[0], p[1]));
        if (dist > 1) {
            for (int ahead = 8; ahead <= 40; ahead += 8) {
                double t = Math.min(ahead, dist) / dist;
                ground = Math.max(ground, height(level, getX() + dx * t, getZ() + dz * t));
            }
        }
        return ground + AirConfig.CRUISE_CLEARANCE.get() - structure.bounds(quadrant()).minY;
    }

    private static int height(ServerLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return level.getMinBuildHeight();
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    private Vec3 landingMotion(ServerLevel level) {
        targetYaw = Math.round(targetYaw / 90f) * 90f;
        if (arrivalPad != null) computeLandingSpot(arrivalPad);
        double dx = landX - getX(), dz = landZ - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.01 || turning()) {
            double speed = Math.min(0.1, dist);
            return dist < 1.0E-6 ? Vec3.ZERO : new Vec3(dx / dist * speed, 0, dz / dist * speed);
        }
        if (getX() != landX || getZ() != landZ) setPos(landX, getY(), landZ);
        boolean near = ShipFlight.groundBelow(level, structure, quadrant(), position(), 3.0);
        return new Vec3(0, near ? -0.06 : -0.15, 0);
    }

    private void handleGround(ServerLevel level, boolean groundHit, boolean falling) {
        if (!groundHit) {
            groundedTicks = 0;
            return;
        }
        groundedTicks++;
        if (mode == Mode.LANDING) {
            if (!ShipAssembler.disassemble(this)) {
                mode = Mode.HOVER;
                Player captain = captain();
                if (captain != null) captain.displayClientMessage(Component.translatable("message.ltwairtransport.landing_blocked"), false);
            }
            return;
        }
        // Down on the ground with no one at the helm (or out of fuel on autopilot): park it.
        boolean unattended = captain() == null;
        if ((unattended || (falling && mode == Mode.AUTOPILOT)) && groundedTicks % 20 == 1) {
            setYRot(quadrant() * 90f);
            targetYaw = getYRot();
            setPos(Math.floor(getX()) + 0.5, getY(), Math.floor(getZ()) + 0.5);
            ShipAssembler.disassemble(this);
        }
    }

    /**
     * Moves deck entities with the ship. While the ship turns, deck entities are pinned to their
     * spot on the deck so the rotating footprint cannot drop them.
     */
    private void carry(List<Entity> deck, Vec3 oldPos, float oldYaw) {
        float yawDelta = Mth.wrapDegrees(getYRot() - oldYaw);
        Vec3 delta = position().subtract(oldPos);
        boolean rotating = Math.abs(yawDelta) > 1.0E-4;
        if (rotating) {
            stillTicks = 0;
            if (turnLocks.isEmpty()) {
                for (Entity e : deck) turnLocks.put(e, ShipStructure.unrotate(e.position().subtract(oldPos), oldYaw));
            }
            for (Map.Entry<Entity, Vec3> lock : turnLocks.entrySet()) {
                Entity e = lock.getKey();
                if (e.isRemoved() || e.isPassenger()) continue;
                Vec3 local = lock.getValue();
                Vec3 p = position().add(ShipStructure.rotate(local.x, local.y, local.z, getYRot()));
                e.setPos(p.x, p.y, p.z);
                e.setDeltaMovement(Vec3.ZERO);
                e.setYRot(e.getYRot() + yawDelta);
                e.setYHeadRot(e.getYHeadRot() + yawDelta);
                e.resetFallDistance();
                e.setOnGround(true);
            }
        } else {
            if (++stillTicks > 1) turnLocks.clear();
            if (delta.lengthSqr() > 1.0E-10) {
                for (Entity e : deck) {
                    e.setPos(e.getX() + delta.x, e.getY() + delta.y, e.getZ() + delta.z);
                    if (delta.y < 0) e.resetFallDistance();
                }
            }
        }
        if (rotating) {
            for (Entity p : getPassengers()) {
                p.setYRot(p.getYRot() + yawDelta);
                p.setYHeadRot(p.getYHeadRot() + yawDelta);
            }
        }
    }

    // --- Using blocks on the ship -------------------------------------------------------------
    public void interactBlock(ServerPlayer player, BlockPos local) {
        BlockState state = structure.blocks().get(local);
        if (state == null) return;
        if (state.getBlock() instanceof SeatBlock) {
            if (player.getVehicle() == this && local.equals(seatOf(player))) return;
            List<Mob> leashed = level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(10),
                m -> m.getLeashHolder() == player);
            if (!leashed.isEmpty() && !structure.isCaptainSeat(local)) {
                Mob mob = leashed.get(0);
                if (seat(mob, local)) {
                    mob.dropLeash(true, !player.getAbilities().instabuild);
                    return;
                }
            }
            if (!seat(player, local)) player.displayClientMessage(Component.translatable("message.ltwairtransport.seat_taken"), true);
            return;
        }
        if (state.getBlock() instanceof ControlPanelBlock) {
            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof com.ltw.airtransport.item.ShipTrackerItem && panel() != null) {
                com.ltw.airtransport.item.ShipTrackerItem.link(held, panel(), player);
                return;
            }
            AirNetwork.openPanelForShip(player, this);
            return;
        }
        BlockEntity be = blockEntities.get(local);
        Component title = state.getBlock().getName();
        if (be instanceof BurnerBlockEntity burner) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BurnerMenu(id, inv, new ShipContainer(burner, this), burner.data), title));
        } else if (be instanceof AbstractFurnaceBlockEntity furnace) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new FurnaceMenu(id, inv, new ShipContainer(furnace, this), new SimpleContainerData(4)), title));
        } else if (be instanceof Container c) {
            ShipContainer proxy = new ShipContainer(c, this);
            int size = c.getContainerSize();
            if (size == 5) {
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new HopperMenu(id, inv, proxy), title));
            } else if (size % 9 == 0 && size >= 9 && size <= 54) {
                MenuType<?> type = switch (size / 9) {
                    case 1 -> MenuType.GENERIC_9x1;
                    case 2 -> MenuType.GENERIC_9x2;
                    case 3 -> MenuType.GENERIC_9x3;
                    case 4 -> MenuType.GENERIC_9x4;
                    case 5 -> MenuType.GENERIC_9x5;
                    default -> MenuType.GENERIC_9x6;
                };
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(type, id, inv, proxy, size / 9), title));
            } else {
                player.displayClientMessage(Component.translatable("message.ltwairtransport.land_to_use"), true);
            }
        } else {
            player.displayClientMessage(Component.translatable("message.ltwairtransport.land_to_use"), true);
        }
    }

    /** Is the player close enough to use the block at {@code local}? */
    public boolean canReach(Player player, BlockPos local) {
        Vec3 c = position().add(ShipStructure.rotate(local.getX(), local.getY() + 0.5, local.getZ(), getYRot()));
        double range = player.blockInteractionRange() + 2;
        return player.getEyePosition().distanceToSqr(c) < range * range;
    }
}
