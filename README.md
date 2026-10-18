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

## Config
`world/serverconfig/ltwairtransport-server.toml` sets max ship size, lift and weight values, fuel use,
speeds, cruise clearance, route wait time and chunk loading.

## Development
```
./gradlew build              # jar in build/libs
./gradlew runClient
./gradlew runGameTestServer  # assembly / landing / fuel / deck-riding tests
python tools/gen_assets.py . # regenerates textures, models, recipes, loot tables
```
