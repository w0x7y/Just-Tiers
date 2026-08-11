#!/usr/bin/env python3
"""Generate assets/minecraft/font/default.json binding gamemode icons to codepoints.

Codepoints must match Gamemodes.java exactly:
  MCTiers  U+E101..U+E108   SubTiers U+E201..U+E20C   NovaTiers U+E301..U+E30C
each assigned in alphabetical slug order within its site.
"""
import json, os

SITES = {
    "mctiers": (0xE101, ["axe", "mace", "nethop", "pot", "smp", "sword", "uhc", "vanilla"]),
    "subtiers": (0xE201, ["bed", "bow", "creeper", "debuff", "dia_crystal", "dia_smp",
                          "elytra", "manhunt", "minecart", "og_vanilla", "speed", "trident"]),
    "novatiers": (0xE301, ["axe", "diamondcart", "diamondop", "elytra", "elytraspear",
                           "modernsmp", "pufferfish", "smp", "spearmace", "spleef",
                           "uhc", "vanilla"]),
}

OUT = "src/main/resources/assets/minecraft/font/default.json"


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
