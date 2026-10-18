package com.ltw.airtransport;

import com.ltw.airtransport.block.BurnerBlockEntity;
import com.ltw.airtransport.entity.AirshipEntity;
import com.ltw.airtransport.ship.ShipAssembler;
import com.ltw.airtransport.ship.ShipCollision;
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
}
