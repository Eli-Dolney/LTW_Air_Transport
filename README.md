# LTW Air Transport (NeoForge 1.21.1)

Build your own hot-air-balloon airships out of any blocks, set up landing pads at your bases and fly
between them — by hand from the Captain's Chair, or on autopilot along a route. Made with zombie-apocalypse
packs in mind: move loot between bases without walking through the horde.

## Quick start
1. **Landing pads.** Place a **Pad Beacon** at each base and give it a name ("Main Base", "Mine").
   Surround it with **Landing Pad** tiles if you like. Pads are never pulled into a ship.
2. **Build a ship on Landing Pads.** Everything touching the **Ship Control Panel** becomes the ship,
   so build it on pad tiles, not on the ground. A ship needs:
   - **Balloon Envelopes** (16 colors) for lift. They're light, so make a big balloon.
   - At least one **Balloon Burner** with fuel. Any furnace fuel works.
   - A **Captain's Chair** if you want to steer. **Passenger Seats**, chests, furnaces, barrels and
     anything else you want are optional.
3. **Fly.**
   - **Steer it yourself:** right-click the Captain's Chair. W/S = forward/back, A/D = quarter turn,
     Space = climb, Sprint = descend, **H** = control panel, Shift = get up.
   - **Autopilot:** open the Control Panel and press **Go** next to a pad. The ship climbs above the
     terrain, flies there, lands on the pad and turns back into normal blocks.
   - **Routes:** press **+** next to pads to build a route, then **Start route**. The ship flies the route
     on its own and waits at each stop (loop or once). Chunks around it stay loaded so it keeps going
     while you're away.

## How it works
- **Lift:** each envelope lifts 4 weight, but only as much as the lit burners can heat (40 weight per
  burner). Envelopes weigh 0.25 and every other block weighs 1. The panel shows lift %.
- **Fuel:** burners only burn while flying. When a burner's slot runs empty, it pulls fuel from any chest
  on the ship, so a cargo hold full of dried kelp blocks or coal keeps it flying for a long time. If it
  runs out mid-air, it sinks slowly and lands where it touches down.
- **On board:** players, zombies and other mobs standing on the deck ride along. Walk up to a mob on a
  lead and right-click a Passenger Seat to seat it. While flying, you can open chests, barrels, furnaces
  and burners on the ship.
- **Ship Tracker:** right-click a Control Panel to link it. Hold it to see where that ship is, what it's
  doing and how far away it is.
- Ships always face one of the four compass directions (turns are animated), and they drift like a real
  balloon.

## Flying farms
Farms built on a ship keep growing while it flies, so a ship with a kelp farm can make its own fuel and
stay up forever.
- **Growth:** wheat, carrots, potatoes, beetroot, nether wart, sweet berries, kelp, sugar cane, cactus
  and bamboo grow in flight, as fast as they would in the world (it follows the `randomTickSpeed` game
  rule). Kelp, sugar cane and bamboo grow upward into empty space, as long as nothing in the world is in
  the way. Cane and cactus stop at 3 blocks, kelp and bamboo at 16. Crops grow faster on wet farmland.
  Grown blocks count toward the ship's weight, so let a harvester keep them short.
- **Harvester:** put it next to your plants with a chest touching it. Once a second in flight it picks
  ripe crops (and replants them) and cuts kelp, sugar cane, bamboo and cactus above the bottom block,
  putting everything into the chest. Put it at the second block of a column, like a piston farm. If the
  chest is full, it leaves the plants standing.
- **Kelp Dryer:** put it against a chest. Once a second in flight it turns 9 kelp in that chest into a
  dried kelp block. Burners already take fuel from chests on board, so harvester -> chest <- dryer
  keeps the burners fed.
- Fuel math: one kelp column grows about one block every 8 minutes at the default tick speed, and a
  dried kelp block burns for 200 seconds. That's about 22 kelp columns per burner to break even, so build
  a wide kelp bed, or raise `growthMultiplier` in the config.
- **Water tanks:** still water flies with the ship when it sits in a tank: every side and the bottom of
  the water touches ship blocks or more of the same water (the top can be open). The walls must join the
  ship through blocks, so fill in the corners. The water lands back where it was, has no collision, and
  each water block weighs 1 like any other block. Kelp grows up through tank water, and kelp cut inside
  the tank leaves water behind. Water that isn't walled in (a lake, the sea,
  a leaky tank) always stays in the world. If kelp or seagrass on the ship touches water like that, the
  ship won't take off and tells you which water block to wall in.
- Kelp grows on a ship without water too. Kelp outside a tank that lands somewhere with no water pops
  off the next time a block next to it changes, like it would in the world.
- Harvesters and dryers only work on a flying ship. Placed in the world, they do nothing.

## Config
`world/serverconfig/ltwairtransport-server.toml` sets max ship size, lift and weight values, fuel use,
speeds, cruise clearance, route wait time, chunk loading, and how fast and how tall farms grow in flight.

## Development
```
./gradlew build              # jar in build/libs
./gradlew runClient
./gradlew runGameTestServer  # assembly / landing / fuel / deck-riding / flying-farm tests
python tools/gen_assets.py . # regenerates textures, models, recipes, loot tables
```
