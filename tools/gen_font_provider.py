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

# Our own font, not an override of the vanilla one. A mod that writes
# assets/minecraft/font/default.json is fighting every resource pack and every other mod
# that adds a glyph; a private font is ours alone and cannot collide.
OUT = "src/main/resources/assets/justtiers/font/icons.json"


def main():
    providers = []
    for site, (start, slugs) in SITES.items():
        for offset, slug in enumerate(slugs):
            providers.append({
                "type": "bitmap",
                "file": f"justtiers:{site}/{slug}.png",
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
