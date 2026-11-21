#!/usr/bin/env python3
"""Generate assets/justtiers/font/icons.json binding gamemode icons to codepoints.

Codepoints must match Gamemodes.java exactly:
  PvPTiers U+E101..U+E108   PvPHQ U+E401..U+E40B   SubTiers U+E201..U+E20C   NovaTiers U+E301..U+E30C
each assigned in alphabetical slug order within its site.
"""
import json, os

SITES = {
    "pvptiers": (0xE101, ["axe", "crystal", "mace", "neth_pot", "pot", "smp", "sword", "uhc"]),
    "pvphq": (0xE401, ["axe", "cart", "diamond_smp", "mace", "netherite_pot", "pot",
                       "smp", "spear_mace", "sword", "uhc", "vanilla"]),
    "subtiers": (0xE201, ["bed", "bow", "creeper", "debuff", "dia_crystal", "dia_smp",
                          "elytra", "manhunt", "minecart", "og_vanilla", "speed", "trident"]),
    "novatiers": (0xE301, ["axe", "diamondcart", "diamondop", "elytra", "elytraspear",
                           "modernsmp", "pufferfish", "smp", "spearmace", "spleef",
                           "uhc", "vanilla"]),
}

# Gamemodes that map directly to built-in vanilla Minecraft textures (referenced by
# resource identifier, never copied into the mod JAR). Every target texture here
# exists across all supported Minecraft versions (1.21.10+).
VANILLA_TEXTURES = {
    "axe": "minecraft:item/diamond_axe.png",
    "bed": "minecraft:block/red_wool.png",
    "bow": "minecraft:item/bow.png",
    "cart": "minecraft:item/tnt_minecart.png",
    "creeper": "minecraft:item/creeper_spawn_egg.png",
    "crystal": "minecraft:item/end_crystal.png",
    "dia_crystal": "minecraft:item/diamond.png",
    "dia_smp": "minecraft:item/diamond_chestplate.png",
    "diamond_smp": "minecraft:item/diamond_chestplate.png",
    "diamondcart": "minecraft:item/minecart.png",
    "diamondop": "minecraft:item/diamond_helmet.png",
    "elytra": "minecraft:item/elytra.png",
    "mace": "minecraft:item/mace.png",
    "manhunt": "minecraft:item/compass_19.png",
    "minecart": "minecraft:item/tnt_minecart.png",
    "modernsmp": "minecraft:item/netherite_chestplate.png",
    "neth_pot": "minecraft:item/netherite_helmet.png",
    "netherite_pot": "minecraft:item/netherite_helmet.png",
    "og_vanilla": "minecraft:item/golden_apple.png",
    "pufferfish": "minecraft:item/pufferfish.png",
    "smp": "minecraft:item/ender_pearl.png",
    "spleef": "minecraft:item/diamond_shovel.png",
    "sword": "minecraft:item/diamond_sword.png",
    "trident": "minecraft:item/trident.png",
    "uhc": "minecraft:gui/sprites/hud/heart/full.png",
    "vanilla": "minecraft:item/end_crystal.png",
}

# Gamemodes synthesized at runtime from the player's active vanilla textures
# (see render/CompositeIcons.java and render/model/PixelArt.java).
COMPOSITE_TEXTURES = {
    "pot": "justtiers:composite/pot.png",
    "debuff": "justtiers:composite/debuff.png",
    "speed": "justtiers:composite/speed.png",
    "spear_mace": "justtiers:composite/spear_mace.png",
    "spearmace": "justtiers:composite/spear_mace.png",
    "elytraspear": "justtiers:composite/elytra_spear.png",
}

# Our own font, not an override of the vanilla one. A mod that writes
# assets/minecraft/font/default.json is fighting every resource pack and every other mod
# that adds a glyph; a private font is ours alone and cannot collide.
OUT = "src/main/resources/assets/justtiers/font/icons.json"


def main():
    providers = []
    for site, (start, slugs) in SITES.items():
        for offset, slug in enumerate(slugs):
            texture = VANILLA_TEXTURES.get(slug) or COMPOSITE_TEXTURES.get(slug)
            if texture is None:
                raise KeyError(f"no texture mapping for {site}/{slug}")
            providers.append({
                "type": "bitmap",
                "file": texture,
                "ascent": 8,
                "height": 8,
                "chars": [chr(start + offset)],
            })

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump({"providers": providers}, handle, indent=2, ensure_ascii=True)
        handle.write("\n")
    print(f"wrote {len(providers)} providers to {OUT}")


if __name__ == "__main__":
    main()
