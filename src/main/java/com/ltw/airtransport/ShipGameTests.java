package com.ltw.airtransport;

import com.ltw.airtransport.block.BurnerBlockEntity;
import com.ltw.airtransport.entity.AirshipEntity;
import com.ltw.airtransport.ship.ShipAssembler;
import com.ltw.airtransport.network.AirNetwork;
import com.ltw.airtransport.ship.ShipCollision;
import com.ltw.airtransport.ship.ShipStructure;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AirTransport.ID)
@PrefixGameTestTemplate(false)
public final class ShipGameTests {
    private static final BlockPos PANEL = new BlockPos(4, 2, 4);

    private ShipGameTests() {}

    /** Pads at y=1 under a 3x3 ship: panel, chest, burner, chair, two layers of envelopes. */
    private static void buildShip(GameTestHelper h, boolean fuel) {
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) h.setBlock(new BlockPos(x, 1, z), AirTransport.LANDING_PAD.get());
        }
        h.setBlock(PANEL, AirTransport.CONTROL_PANEL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        h.setBlock(PANEL.west(), Blocks.CHEST);
        h.setBlock(PANEL.east(), AirTransport.BALLOON_BURNER.get());
        h.setBlock(PANEL.north(), AirTransport.CAPTAINS_CHAIR.get());
        for (int y = 3; y <= 4; y++) {
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) h.setBlock(new BlockPos(x, y, z), AirTransport.ENVELOPES.get(DyeColor.RED).get());
            }
        }
        ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(PANEL.west());
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        if (fuel) {
            BurnerBlockEntity burner = (BurnerBlockEntity) h.getBlockEntity(PANEL.east());
            burner.setItem(0, new ItemStack(Items.COAL, 4));
        }
    }

    private static AirshipEntity assemble(GameTestHelper h) {
        return ShipAssembler.assemble(h.getLevel(), h.absolutePos(PANEL), null);
    }

    @GameTest(template = "empty9")
    public static void assembleAndLandKeepsEverything(GameTestHelper h) {
        buildShip(h, true);
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Ship should assemble");
        h.assertBlockPresent(Blocks.AIR, PANEL.west());
        h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 4, 4));
        h.assertBlockPresent(AirTransport.LANDING_PAD.get(), new BlockPos(4, 1, 4));
        h.assertTrue(ship.structure().size() == 22, "Expected 22 ship blocks, got " + ship.structure().size());

        h.assertTrue(ShipAssembler.disassemble(ship), "Ship should land where it took off");
        h.assertBlockPresent(Blocks.CHEST, PANEL.west());
        h.assertBlockPresent(AirTransport.BALLOON_BURNER.get(), PANEL.east());
        ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(PANEL.west());
        h.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 3, "Chest contents must survive the flight");
        h.assertTrue(ship.isRemoved(), "Ship entity should be gone after landing");
        h.succeed();
    }

    @GameTest(template = "empty9")
    public static void noFuelNoTakeoff(GameTestHelper h) {
        buildShip(h, false);
        h.assertTrue(assemble(h) == null, "A ship without fuel must not take off");
        h.assertBlockPresent(Blocks.CHEST, PANEL.west());
        h.succeed();
    }

    @GameTest(template = "empty9")
    public static void cargoFuelCounts(GameTestHelper h) {
        buildShip(h, false);
        ((ChestBlockEntity) h.getBlockEntity(PANEL.west())).setItem(1, new ItemStack(Items.DRIED_KELP_BLOCK, 8));
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Fuel in a chest on board should be enough to take off");
        h.succeedWhen(() -> h.assertTrue(ship.isBurning(), "Burner should light from cargo fuel"));
    }

    @GameTest(template = "empty9")
    public static void landingRefusesBlockedSpot(GameTestHelper h) {
        buildShip(h, true);
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Ship should assemble");
        h.setBlock(PANEL.west(), Blocks.STONE);
        h.assertFalse(ShipAssembler.disassemble(ship), "Landing on top of a block must fail");
        h.assertFalse(ship.isRemoved(), "Ship must stay flying when it can't land");
        h.getLevel().setBlockAndUpdate(h.absolutePos(PANEL.west()), Blocks.AIR.defaultBlockState());
        h.assertTrue(ShipAssembler.disassemble(ship), "Ship lands once the spot is clear");
        h.succeed();
    }

    @GameTest(template = "empty9", timeoutTicks = 200)
    public static void deckRidersClimbWithShip(GameTestHelper h) {
        buildShip(h, true);
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Ship should assemble");
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 5.0, 4.5));
        double startY = ship.getY();
        ship.flyTo(new com.ltw.airtransport.pad.PadRegistry.Pad(java.util.UUID.randomUUID(), "Far",
            h.absolutePos(new BlockPos(4, 1, 200))));
        h.runAfterDelay(60, () -> {
            h.assertTrue(ship.getY() > startY + 3, "Ship should climb on autopilot, rose " + (ship.getY() - startY));
            double onDeck = zombie.getY() - ship.getY();
            h.assertTrue(Math.abs(onDeck - 3.0) < 0.3, "Zombie should ride on top of the balloon, offset " + onDeck);
            ship.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty9")
    public static void zombieStandsOnDeck(GameTestHelper h) {
        buildShip(h, true);
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Ship should assemble");
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new Vec3(4.5, 5.2, 4.5));
        h.assertTrue(ShipCollision.ships(h.getLevel()).contains(ship), "Ship should be tracked for collisions");
        h.runAfterDelay(20, () -> {
            double feet = zombie.getY() - h.absolutePos(BlockPos.ZERO).getY();
            h.assertTrue(Math.abs(feet - 5.0) < 0.1, "Zombie should stand on the balloon at y=5, was " + feet);
            h.succeed();
        });
    }

    // --- Flying farms -------------------------------------------------------------------------
    private static BlockPos local(BlockPos testPos) {
        return testPos.subtract(PANEL);
    }

    /** Wheat on farmland south of the panel, a harvester next to it and a chest under the harvester. */
    private static void buildWheatFarm(GameTestHelper h) {
        h.setBlock(PANEL.south(), Blocks.FARMLAND);
        h.setBlock(new BlockPos(4, 2, 6), Blocks.FARMLAND);
        h.setBlock(new BlockPos(4, 3, 6), Blocks.WHEAT);
        h.setBlock(new BlockPos(5, 2, 6), Blocks.CHEST);
        h.setBlock(new BlockPos(5, 3, 6), AirTransport.HARVESTER.get());
    }

    @GameTest(template = "empty9")
    public static void cropsGrowAndGetHarvestedInFlight(GameTestHelper h) {
        buildShip(h, true);
        buildWheatFarm(h);
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Ship should assemble");
        BlockPos wheat = local(new BlockPos(4, 3, 6));
        h.assertTrue(ship.structure().farmPositions().contains(wheat), "Wheat should be tracked as a plant");

        ship.growPlants(600);
        BlockState grown = ship.structure().blocks().get(wheat);
        h.assertTrue(grown.getBlock() instanceof CropBlock crop && crop.isMaxAge(grown), "Wheat should be ripe, was " + grown);
        h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 3, 6)); // grown on the ship, not in the world

        ship.runFarmMachines();
        BlockState picked = ship.structure().blocks().get(wheat);
        h.assertTrue(picked.getBlock() instanceof CropBlock crop && crop.getAge(picked) == 0, "Harvester should replant, was " + picked);
        ChestBlockEntity chest = (ChestBlockEntity) ship.blockEntities().get(local(new BlockPos(5, 2, 6)));
        h.assertTrue(count(chest, Items.WHEAT) == 1, "Chest should hold the wheat, had " + count(chest, Items.WHEAT));
        h.succeed();
    }

    /** Kelp on sand, a harvester at the second block, a chest under it and a dryer next to the chest. */
    @GameTest(template = "empty9")
    public static void kelpFarmMakesFuel(GameTestHelper h) {
        buildShip(h, true);
        h.setBlock(PANEL.south(), Blocks.STONE);
        h.setBlock(new BlockPos(4, 2, 6), Blocks.SAND);
        h.setBlock(new BlockPos(4, 3, 6), Blocks.KELP);
        h.setBlock(new BlockPos(5, 3, 6), Blocks.CHEST);
        h.setBlock(new BlockPos(5, 4, 6), AirTransport.HARVESTER.get());
        h.setBlock(new BlockPos(6, 3, 6), AirTransport.KELP_DRYER.get());
        ((ChestBlockEntity) h.getBlockEntity(new BlockPos(5, 3, 6))).setItem(0, new ItemStack(Items.KELP, 8));
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Ship should assemble");
        BlockPos base = local(new BlockPos(4, 3, 6));
        double topBefore = ship.structure().bounds(0).maxY;

        ship.growPlants(400);
        h.assertTrue(ship.structure().blocks().get(base).is(Blocks.KELP_PLANT), "Kelp should have grown up from its base");
        h.assertTrue(ship.structure().blocks().get(base.above()) != null, "Kelp should fill the space above");
        h.assertTrue(ship.structure().bounds(0).maxY > topBefore, "Ship bounds should grow with the kelp");
        h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 4, 6));

        ship.runFarmMachines();
        h.assertTrue(ship.structure().blocks().get(base).is(Blocks.KELP), "The base stays and becomes the growing tip");
        h.assertTrue(ship.structure().blocks().get(base.above()) == null, "Everything above the base is cut");
        ChestBlockEntity chest = (ChestBlockEntity) ship.blockEntities().get(local(new BlockPos(5, 3, 6)));
        h.assertTrue(count(chest, Items.DRIED_KELP_BLOCK) == 1, "Dryer should make a dried kelp block, chest had " + count(chest, Items.DRIED_KELP_BLOCK));
        h.assertTrue(ShipAssembler.disassemble(ship), "Ship should still land");
        h.succeed();
    }

    /**
     * A 2x2 glass tank (walls 4x3 with corners) south of the balloon, open on top: kelp on dirt in one corner, water in the other
     * three. The harvester is part of the east wall, with a chest outside it.
     */
    private static void buildKelpTank(GameTestHelper h) {
        h.setBlock(new BlockPos(4, 2, 5), Blocks.STONE);
        h.setBlock(new BlockPos(4, 2, 6), Blocks.STONE);
        h.setBlock(new BlockPos(3, 2, 7), Blocks.GLASS);
        h.setBlock(new BlockPos(4, 2, 7), Blocks.DIRT); // not sand: nothing holds it up after landing
        for (int y = 3; y <= 4; y++) {
            for (int x = 3; x <= 4; x++) {
                h.setBlock(new BlockPos(x, y, 6), Blocks.GLASS);
                h.setBlock(new BlockPos(x, y, 8), Blocks.GLASS);
            }
            for (int x = 2; x <= 5; x += 3) {
                for (int z = 6; z <= 8; z++) h.setBlock(new BlockPos(x, y, z), Blocks.GLASS); // corners join the walls
            }
        }
        h.setBlock(new BlockPos(5, 4, 7), AirTransport.HARVESTER.get());
        h.setBlock(new BlockPos(6, 4, 7), Blocks.CHEST);
        h.setBlock(new BlockPos(4, 3, 7), Blocks.KELP);
        h.setBlock(new BlockPos(3, 3, 7), Blocks.WATER);
        h.setBlock(new BlockPos(3, 4, 7), Blocks.WATER);
        h.setBlock(new BlockPos(4, 4, 7), Blocks.WATER);
    }

    @GameTest(template = "empty9")
    public static void kelpTankWaterFliesWithShip(GameTestHelper h) {
        buildShip(h, true);
        buildKelpTank(h);
        ShipAssembler.Preflight pre = ShipAssembler.preflight(h.getLevel(), h.absolutePos(PANEL));
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Ship with a closed kelp tank should assemble: " + pre.problem());
        ShipStructure s = ship.structure();
        BlockPos[] water = {new BlockPos(3, 3, 7), new BlockPos(3, 4, 7), new BlockPos(4, 4, 7)};
        for (BlockPos w : water) {
            h.assertTrue(Blocks.WATER.equals(s.blocks().get(local(w)).getBlock()), "Tank water at " + w + " should be on the ship");
            h.assertBlockPresent(Blocks.AIR, w); // and gone from the world
        }
        for (ShipStructure.Box b : s.boxes(0)) {
            h.assertFalse(s.blocks().get(b.local()).is(Blocks.WATER), "Water must not have collision boxes");
        }

        ship.growPlants(400);
        BlockPos kelp = local(new BlockPos(4, 4, 7));
        h.assertTrue(s.blocks().get(kelp).is(Blocks.KELP) || s.blocks().get(kelp).is(Blocks.KELP_PLANT), "Kelp should grow into the tank water");
        ship.runFarmMachines();
        h.assertTrue(s.blocks().get(kelp).is(Blocks.WATER), "Kelp cut inside the tank leaves water, was " + s.blocks().get(kelp));
        h.assertTrue(s.blocks().get(kelp.above()) == null, "Kelp cut above the tank leaves air");

        h.assertTrue(ShipAssembler.disassemble(ship), "Ship should land where it took off");
        for (BlockPos w : water) h.assertBlockPresent(Blocks.WATER, w);
        h.assertBlockPresent(Blocks.KELP, new BlockPos(4, 3, 7));
        h.runAfterDelay(20, () -> {
            h.assertBlockPresent(Blocks.KELP, new BlockPos(4, 3, 7));
            h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 5, 7)); // the tank doesn't spill
            h.succeed();
        });
    }

    @GameTest(template = "empty9")
    public static void kelpInOpenWaterRefusesTakeoff(GameTestHelper h) {
        buildShip(h, true);
        h.setBlock(new BlockPos(4, 2, 5), Blocks.STONE);
        h.setBlock(new BlockPos(4, 2, 6), Blocks.SAND);
        h.setBlock(new BlockPos(4, 3, 6), Blocks.KELP);
        h.setBlock(new BlockPos(4, 3, 7), Blocks.WATER); // nothing walls it in: a pond, not a tank
        ShipAssembler.Scan scan = ShipAssembler.scan(h.getLevel(), h.absolutePos(PANEL));
        h.assertTrue(scan.error() != null && scan.error().getContents() instanceof TranslatableContents t
            && t.getKey().equals("message.ltwairtransport.open_water"), "Kelp in open water should block takeoff, got " + scan.error());
        h.assertTrue(assemble(h) == null, "Ship must not take off");
        h.assertBlockPresent(Blocks.KELP, new BlockPos(4, 3, 6));

        // Without the kelp the pond is just water next to the hull: the ship leaves it where it is.
        h.setBlock(new BlockPos(4, 3, 6), Blocks.AIR);
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Ship should take off next to open water");
        h.assertBlockPresent(Blocks.WATER, new BlockPos(4, 3, 7));
        h.assertFalse(ship.structure().blocks().containsKey(local(new BlockPos(4, 3, 7))), "Pond water must stay in the world");
        h.succeed();
    }

    @GameTest(template = "empty9")
    public static void blockChangesReachClients(GameTestHelper h) {
        buildShip(h, true);
        buildWheatFarm(h);
        AirshipEntity ship = assemble(h);
        h.assertTrue(ship != null, "Ship should assemble");
        // A client copy made from the spawn data, then updated by the payload the server sends.
        ShipStructure client = ShipStructure.load(ship.structure().save(), ShipStructure.blockLookup(h.getLevel()));
        BlockPos wheat = local(new BlockPos(4, 3, 6));
        Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        changes.put(wheat, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        changes.put(local(new BlockPos(4, 5, 6)), Blocks.WHEAT.defaultBlockState());
        changes.put(local(new BlockPos(5, 3, 6)), Blocks.AIR.defaultBlockState());

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        AirNetwork.ShipBlocks.CODEC.encode(buf, new AirNetwork.ShipBlocks(ship.getId(), changes));
        AirNetwork.ShipBlocks decoded = AirNetwork.ShipBlocks.CODEC.decode(buf);
        h.assertTrue(decoded.entityId() == ship.getId() && decoded.changes().equals(changes), "Payload should round-trip");

        int before = client.size();
        double topBefore = client.bounds(0).maxY;
        decoded.changes().forEach(client::set);
        client.recompute();
        h.assertTrue(client.size() == before, "One block added and one removed, size " + client.size());
        h.assertTrue(client.blocks().get(wheat).getValue(CropBlock.AGE) == 7, "Client should see ripe wheat");
        h.assertTrue(!client.harvesters().contains(local(new BlockPos(5, 3, 6))), "Removed harvester is gone");
        h.assertTrue(client.bounds(0).maxY > topBefore, "Collision bounds should be rebuilt");
        h.succeed();
    }

    private static int count(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
        }
        return n;
    }
}
