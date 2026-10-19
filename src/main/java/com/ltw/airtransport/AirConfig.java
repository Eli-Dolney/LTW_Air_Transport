package com.ltw.airtransport;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side balance settings. */
public final class AirConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_SHIP_BLOCKS;
    public static final ModConfigSpec.DoubleValue ENVELOPE_LIFT;
    public static final ModConfigSpec.DoubleValue BURNER_HEAT;
    public static final ModConfigSpec.DoubleValue ENVELOPE_WEIGHT;
    public static final ModConfigSpec.DoubleValue FUEL_USE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BASE_SPEED;
    public static final ModConfigSpec.DoubleValue PROPELLER_SPEED;
    public static final ModConfigSpec.DoubleValue MAX_SPEED;
    public static final ModConfigSpec.IntValue CRUISE_CLEARANCE;
    public static final ModConfigSpec.IntValue ROUTE_WAIT_SECONDS;
    public static final ModConfigSpec.BooleanValue CHUNK_LOAD_AUTOPILOT;
    public static final ModConfigSpec.DoubleValue FARM_GROWTH_MULTIPLIER;
    public static final ModConfigSpec.IntValue FARM_PLANT_HEIGHT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("ships");
        MAX_SHIP_BLOCKS = b.comment("Most blocks one airship may contain. Hitting this usually means the ship is touching the ground.")
            .defineInRange("maxShipBlocks", 2048, 16, 32768);
        ENVELOPE_WEIGHT = b.comment("Weight of one balloon envelope block (every other block weighs 1).")
            .defineInRange("envelopeWeight", 0.25, 0.0, 4.0);
        ENVELOPE_LIFT = b.comment("Weight one balloon envelope block can lift when heated.")
            .defineInRange("envelopeLift", 4.0, 0.0, 1000.0);
        BURNER_HEAT = b.comment("Weight one burning Balloon Burner can keep heated (lift is limited by both envelopes and burners).")
            .defineInRange("burnerHeat", 40.0, 0.0, 10000.0);
        FUEL_USE_MULTIPLIER = b.comment("Fuel burn speed multiplier while flying (1 = same as a furnace).")
            .defineInRange("fuelUseMultiplier", 1.0, 0.0, 20.0);
        b.pop().push("flight");
        BASE_SPEED = b.comment("Horizontal cruise speed in blocks per tick without propellers.")
            .defineInRange("baseSpeed", 0.25, 0.01, 4.0);
        PROPELLER_SPEED = b.comment("Extra blocks per tick each propeller adds.")
            .defineInRange("propellerSpeed", 0.05, 0.0, 4.0);
        MAX_SPEED = b.comment("Speed cap in blocks per tick.")
            .defineInRange("maxSpeed", 0.7, 0.05, 4.0);
        CRUISE_CLEARANCE = b.comment("Blocks of clearance the autopilot keeps above terrain.")
            .defineInRange("cruiseClearance", 10, 2, 128);
        ROUTE_WAIT_SECONDS = b.comment("Seconds a ship waits at each stop of a route before leaving again.")
            .defineInRange("routeWaitSeconds", 30, 0, 3600);
        CHUNK_LOAD_AUTOPILOT = b.comment("Keep chunks loaded around ships on autopilot and ships waiting on a route.")
            .define("chunkLoadAutopilot", true);
        b.pop().push("farms");
        FARM_GROWTH_MULTIPLIER = b.comment("How fast plants on a flying ship grow, relative to the randomTickSpeed game rule (1 = same as in the world, 0 = frozen).")
            .defineInRange("growthMultiplier", 1.0, 0.0, 100.0);
        FARM_PLANT_HEIGHT = b.comment("Tallest kelp or bamboo column that grows on a flying ship (sugar cane and cactus stop at 3).")
            .defineInRange("plantHeight", 16, 2, 26);
        b.pop();
        SPEC = b.build();
    }

    private AirConfig() {}
}
