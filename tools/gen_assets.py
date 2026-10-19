"""Generates textures, models, blockstates, loot tables, recipes, tags and lang for LTW Air Transport."""
import json
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1]
NS = "ltwairtransport"
A = os.path.join(ROOT, "src/main/resources/assets", NS)
D = os.path.join(ROOT, "src/main/resources/data", NS)
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def png(name, fn, size=16):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    rnd = random.Random(name)
    for y in range(size):
        for x in range(size):
            px[x, y] = fn(x, y, rnd)
    path = os.path.join(A, "textures", name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def jitter(c, rnd, amt=8):
    return tuple(max(0, min(255, v + rnd.randint(-amt, amt))) for v in c[:3]) + (255,)


# --- Textures ------------------------------------------------------------------------------------
def pad_top(x, y, rnd):
    if x in (0, 15) or y in (0, 15):
        return jitter((60, 60, 64), rnd, 4)
    stripe = ((x + y) // 3) % 2 == 0
    return jitter((230, 180, 30) if stripe else (40, 40, 44), rnd, 6)


def pad_side(x, y, rnd):
    if y in (0, 1):
        return jitter((230, 180, 30), rnd, 6)
    return jitter((120, 122, 128) if (x % 8) else (95, 97, 102), rnd, 5)


def beacon_top(x, y, rnd):
    if x in (0, 15) or y in (0, 15):
        return jitter((60, 60, 64), rnd, 4)
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    if 5.5 <= d <= 7.0:
        return jitter((240, 240, 240), rnd, 4)
    # Helipad "H"
    if (x in (5, 6, 9, 10) and 4 <= y <= 11) or (y in (7, 8) and 5 <= x <= 10):
        return jitter((240, 200, 40), rnd, 4)
    return jitter((45, 45, 50), rnd, 5)


def beacon_side(x, y, rnd):
    if 6 <= x <= 9 and 3 <= y <= 6:
        return (255, 230, 120, 255)
    if y in (0, 1):
        return jitter((230, 180, 30), rnd, 6)
    return jitter((110, 112, 118), rnd, 5)


def panel_top(x, y, rnd):
    if x in (0, 15) or y in (0, 15):
        return jitter((90, 60, 30), rnd, 6)
    # brass dials
    for cx, cy in ((4, 5), (11, 5)):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if d < 1.2:
            return (40, 30, 20, 255)
        if d < 2.6:
            return jitter((215, 175, 70), rnd, 8)
    if 3 <= x <= 12 and 10 <= y <= 12:  # map strip
        return jitter((220, 210, 170), rnd, 6) if (x + y) % 4 else (150, 40, 40, 255)
    return jitter((60, 42, 25), rnd, 5)


def burner_side(x, y, rnd):
    if y in (0, 15) or x in (0, 15):
        return jitter((70, 70, 75), rnd, 4)
    if (x in (2, 13)) and y % 4 == 2:
        return (200, 200, 205, 255)  # rivets
    return jitter((150, 95, 60) if (y // 4) % 2 else (135, 85, 55), rnd, 6)  # copper bands


def burner_top(x, y, rnd):
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    if d < 4 and (x + y) % 2 == 0:
        return (30, 30, 32, 255)
    if d < 4:
        return (70, 70, 75, 255)
    return jitter((120, 120, 128), rnd, 6)


def burner_top_lit(x, y, rnd):
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    if d < 2.5:
        return (255, 240, 160, 255)
    if d < 4:
        return jitter((255, 140, 30), rnd, 20)
    return jitter((120, 120, 128), rnd, 6)


def envelope(x, y, rnd):
    # light fabric with stitched seams so tinting gives a balloon look
    base = 225
    if x in (0, 8) or y == 0:
        base = 175
    elif x in (1, 9):
        base = 240
    shade = int(base - abs(y - 7.5) * 1.5)
    v = max(0, min(255, shade + rnd.randint(-5, 5)))
    return (v, v, v, 255)


def tracker(x, y, rnd):
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    if d > 7:
        return (0, 0, 0, 0)
    if d > 5.8:
        return jitter((90, 92, 98), rnd, 6)
    if (x, y) in ((10, 5), (10, 6), (11, 5)):
        return (230, 40, 40, 255)  # the ship blip
    if x == 7 or y == 7:
        return (40, 140, 60, 255)
    if abs(d - 3) < 0.5:
        return (40, 140, 60, 255)
    return (15, 40, 20, 255)


def propeller_blade(x, y, rnd):
    return jitter((150, 110, 65) if x % 5 else (120, 85, 50), rnd, 6)


def harvester_side(x, y, rnd):
    if x in (0, 15) or y in (0, 15):
        return jitter((70, 70, 75), rnd, 4)
    # three rows of saw teeth
    row = (y - 1) % 5
    if row < 3 and abs((x % 4) - 1.5) <= 1.5 - row * 0.75:
        return jitter((215, 218, 225), rnd, 6)
    return jitter((95, 100, 92), rnd, 5)


def harvester_top(x, y, rnd):
    if x in (0, 15) or y in (0, 15):
        return jitter((70, 70, 75), rnd, 4)
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    if d < 1.5:
        return (50, 50, 55, 255)
    if d < 6 and (abs(x - 7.5) < 1 or abs(y - 7.5) < 1):
        return jitter((215, 218, 225), rnd, 6)  # spinning blades
    return jitter((110, 140, 70), rnd, 6)


def dryer_side(x, y, rnd):
    if x in (0, 15) or y in (0, 15):
        return jitter((70, 70, 75), rnd, 4)
    if y % 3 == 1 and 2 <= x <= 13:
        return (35, 35, 38, 255)  # vents
    return jitter((60, 90, 45), rnd, 6)


def dryer_top(x, y, rnd):
    if x in (0, 15) or y in (0, 15):
        return jitter((70, 70, 75), rnd, 4)
    if (x + y) % 4 == 0:
        return (35, 35, 38, 255)  # grate
    return jitter((50, 70, 35), rnd, 6)


png("block/harvester_side", harvester_side)
png("block/harvester_top", harvester_top)
png("block/kelp_dryer_side", dryer_side)
png("block/kelp_dryer_top", dryer_top)
png("block/landing_pad_top", pad_top)
png("block/landing_pad_side", pad_side)
png("block/pad_beacon_top", beacon_top)
png("block/pad_beacon_side", beacon_side)
png("block/control_panel_top", panel_top)
png("block/burner_side", burner_side)
png("block/burner_top", burner_top)
png("block/burner_top_lit", burner_top_lit)
png("block/balloon_envelope", envelope)
png("block/propeller_blade", propeller_blade)
png("item/ship_tracker", tracker)

# --- Block models --------------------------------------------------------------------------------
M = os.path.join(A, "models")
BS = os.path.join(A, "blockstates")


def model(name, obj):
    write(os.path.join(M, "block", name + ".json"), obj)


def item_parent(name, parent=None):
    write(os.path.join(M, "item", name + ".json"), {"parent": parent or f"{NS}:block/{name}"})


def face(tex, uv=None, tint=None, cull=None):
    f = {"texture": tex}
    if uv:
        f["uv"] = uv
    if tint is not None:
        f["tintindex"] = tint
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, tex, faces="nsewud", uvs=None):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    t = tex if isinstance(tex, dict) else {k: tex for k in names}
    return {"from": frm, "to": to, "faces": {names[k]: face(t[k]) for k in faces}}


model("landing_pad", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{NS}:block/landing_pad_top", "bottom": f"{NS}:block/landing_pad_side", "side": f"{NS}:block/landing_pad_side"}})
model("pad_beacon", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{NS}:block/pad_beacon_top", "bottom": f"{NS}:block/landing_pad_side", "side": f"{NS}:block/pad_beacon_side"}})
model("harvester", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{NS}:block/harvester_top", "bottom": f"{NS}:block/harvester_top", "side": f"{NS}:block/harvester_side"}})
model("kelp_dryer", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{NS}:block/kelp_dryer_top", "bottom": f"{NS}:block/kelp_dryer_top", "side": f"{NS}:block/kelp_dryer_side"}})
for c in COLORS:
    model(f"{c}_balloon_envelope", {"parent": "minecraft:block/leaves", "textures": {"all": f"{NS}:block/balloon_envelope"}})

wood = "minecraft:block/spruce_planks"
model("control_panel", {"parent": "minecraft:block/block", "textures": {"particle": wood, "wood": wood, "top": f"{NS}:block/control_panel_top"},
    "elements": [
        box([2, 0, 2], [14, 10, 14], "#wood"),
        box([0, 10, 0], [16, 14, 16], {"n": "#wood", "s": "#wood", "e": "#wood", "w": "#wood", "u": "#top", "d": "#wood"}),
        box([7, 14, 1], [9, 16, 3], "minecraft:block/gold_block"),
    ]})


def seat_model(name, cushion, frame):
    model(name, {"parent": "minecraft:block/block", "textures": {"particle": frame, "frame": frame, "cushion": cushion},
        "elements": [
            box([2, 0, 2], [4, 6, 4], "#frame"), box([12, 0, 2], [14, 6, 4], "#frame"),
            box([2, 0, 12], [4, 6, 14], "#frame"), box([12, 0, 12], [14, 6, 14], "#frame"),
            box([1, 6, 1], [15, 8, 15], "#cushion"),
            box([1, 8, 13], [15, 16, 15], "#cushion"),
            box([1, 8, 1], [2, 11, 13], "#frame"), box([14, 8, 1], [15, 11, 13], "#frame"),
        ]})


seat_model("captains_chair", "minecraft:block/red_wool", "minecraft:block/dark_oak_planks")
seat_model("passenger_seat", "minecraft:block/brown_wool", "minecraft:block/spruce_planks")


def burner_model(name, top):
    model(name, {"parent": "minecraft:block/block", "textures": {"particle": f"{NS}:block/burner_side", "side": f"{NS}:block/burner_side",
        "top": top, "iron": "minecraft:block/iron_block"},
        "elements": [
            box([2, 0, 2], [14, 10, 14], {"n": "#side", "s": "#side", "e": "#side", "w": "#side", "u": "#iron", "d": "#iron"}),
            box([4, 10, 4], [12, 16, 12], {"n": "#iron", "s": "#iron", "e": "#iron", "w": "#iron", "u": "#top", "d": "#iron"}),
        ]})


burner_model("balloon_burner", f"{NS}:block/burner_top")
burner_model("balloon_burner_lit", f"{NS}:block/burner_top_lit")

model("propeller", {"parent": "minecraft:block/block", "textures": {"particle": f"{NS}:block/propeller_blade",
    "blade": f"{NS}:block/propeller_blade", "hub": "minecraft:block/iron_block", "frame": wood},
    "elements": [
        box([5, 5, 8], [11, 11, 16], "#frame"),
        box([6, 6, 4], [10, 10, 8], "#hub"),
        box([7, 0, 5], [9, 16, 6], "#blade"),
        box([0, 7, 5], [16, 9, 6], "#blade"),
    ]})

# --- Blockstates ---------------------------------------------------------------------------------
def simple_state(name, model_name=None):
    write(os.path.join(BS, name + ".json"), {"variants": {"": {"model": f"{NS}:block/{model_name or name}"}}})


def facing_state(name, extra=""):
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for f, r in rot.items():
        v = {"model": f"{NS}:block/{name}"}
        if r:
            v["y"] = r
        variants[f"facing={f}"] = v
    write(os.path.join(BS, name + ".json"), {"variants": variants})


simple_state("landing_pad")
simple_state("pad_beacon")
simple_state("harvester")
simple_state("kelp_dryer")
for c in COLORS:
    simple_state(f"{c}_balloon_envelope")
# Seat models have the backrest on the south side, so a seat facing north (player looks north) uses y=0.
facing_state("captains_chair")
facing_state("passenger_seat")
# Control panel: dials face the player who placed it.
facing_state("control_panel")
# Propeller: blades on the north side by default; FACING is toward the placer, blades point away.
facing_state("propeller")
write(os.path.join(BS, "balloon_burner.json"), {"variants": {
    "lit=false": {"model": f"{NS}:block/balloon_burner"}, "lit=true": {"model": f"{NS}:block/balloon_burner_lit"}}})

# --- Item models ---------------------------------------------------------------------------------
for b in ["landing_pad", "pad_beacon", "control_panel", "captains_chair", "passenger_seat", "balloon_burner", "propeller",
          "harvester", "kelp_dryer"]:
    item_parent(b)
for c in COLORS:
    item_parent(f"{c}_balloon_envelope")
write(os.path.join(M, "item", "ship_tracker.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/ship_tracker"}})

# --- Loot tables ---------------------------------------------------------------------------------
blocks = ["landing_pad", "pad_beacon", "control_panel", "captains_chair", "passenger_seat", "balloon_burner", "propeller",
          "harvester", "kelp_dryer"] + [f"{c}_balloon_envelope" for c in COLORS]
for b in blocks:
    write(os.path.join(D, "loot_table", "blocks", b + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

# --- Tags ----------------------------------------------------------------------------------------
write(os.path.join(D, "tags", "block", "ship_blacklist.json"), {"values": ["minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:nether_portal", "minecraft:end_portal"]})
MC = os.path.join(ROOT, "src/main/resources/data/minecraft/tags/block/mineable")
write(os.path.join(MC, "pickaxe.json"), {"values": [f"{NS}:landing_pad", f"{NS}:pad_beacon", f"{NS}:balloon_burner",
                                                  f"{NS}:harvester", f"{NS}:kelp_dryer"]})
write(os.path.join(MC, "axe.json"), {"values": [f"{NS}:control_panel", f"{NS}:captains_chair", f"{NS}:passenger_seat", f"{NS}:propeller"]})
write(os.path.join(D, "tags", "item", "balloon_envelopes.json"), {"values": [f"{NS}:{c}_balloon_envelope" for c in COLORS]})

# --- Recipes -------------------------------------------------------------------------------------
R = os.path.join(D, "recipe")


def shaped(name, pattern, key, result, count=1):
    write(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
        "result": {"id": result, "count": count}})


def shapeless(name, ingredients, result, count=1):
    write(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [({"tag": v[1:]} if v.startswith("#") else {"item": v}) for v in ingredients],
        "result": {"id": result, "count": count}})


shaped("landing_pad", ["SSS", "SYS", "SSS"], {"S": "minecraft:smooth_stone", "Y": "minecraft:yellow_dye"}, f"{NS}:landing_pad", 8)
shapeless("pad_beacon", [f"{NS}:landing_pad", "minecraft:compass", "minecraft:glowstone_dust"], f"{NS}:pad_beacon")
shaped("control_panel", [" C ", "RPR", "PPP"], {"C": "minecraft:compass", "R": "minecraft:redstone", "P": "#minecraft:planks"}, f"{NS}:control_panel")
shaped("passenger_seat", ["P  ", "PWP", "S S"], {"P": "#minecraft:planks", "W": "#minecraft:wool", "S": "minecraft:stick"}, f"{NS}:passenger_seat", 2)
shapeless("captains_chair", [f"{NS}:passenger_seat", "minecraft:gold_ingot", "minecraft:compass"], f"{NS}:captains_chair")
shaped("balloon_burner", ["IBI", "IFI", "III"], {"I": "minecraft:iron_ingot", "B": "minecraft:iron_bars", "F": "minecraft:furnace"}, f"{NS}:balloon_burner")
shaped("propeller", [" P ", "PIP", " P "], {"P": "#minecraft:planks", "I": "minecraft:iron_ingot"}, f"{NS}:propeller")
shaped("white_balloon_envelope", ["SWS", "W W", "SWS"], {"S": "minecraft:string", "W": "minecraft:white_wool"}, f"{NS}:white_balloon_envelope", 4)
for c in COLORS:
    if c != "white":
        shapeless(f"{c}_balloon_envelope", [f"#{NS}:balloon_envelopes", f"minecraft:{c}_dye"], f"{NS}:{c}_balloon_envelope")
shaped("harvester", [" S ", "IRI", "IHI"], {"S": "minecraft:shears", "I": "minecraft:iron_ingot", "R": "minecraft:redstone",
    "H": "minecraft:hopper"}, f"{NS}:harvester")
shaped("kelp_dryer", ["IBI", "KSK", "III"], {"I": "minecraft:iron_ingot", "B": "minecraft:iron_bars", "K": "minecraft:kelp",
    "S": "minecraft:smoker"}, f"{NS}:kelp_dryer")
shapeless("ship_tracker", ["minecraft:compass", "minecraft:redstone", "minecraft:iron_ingot"], f"{NS}:ship_tracker")

print("assets generated")
