#!/usr/bin/env python3
"""Generate the 12 original NovaTiers gamemode icons as 8x8 RGBA PNGs."""
import struct, zlib, os, sys

PALETTE = {
    '.': (0, 0, 0, 0),         'k': (40, 40, 45, 255),    'w': (235, 235, 240, 255),
    'g': (150, 155, 165, 255), 'd': (85, 88, 95, 255),    'r': (205, 55, 60, 255),
    'o': (225, 135, 45, 255),  'y': (240, 205, 80, 255),  'l': (110, 190, 85, 255),
    'c': (110, 215, 225, 255), 'b': (70, 120, 220, 255),  'p': (170, 110, 210, 255),
    'n': (140, 95, 55, 255),
}

ICONS = {
    "axe":         ["...ggg..", "..gwwwg.", ".ggwwwg.", ".nnggg..", "..n.....", ".n......", "n.......", "........"],
    "smp":         [".rr..rr.", "rrrrrrrr", "rrrrrrrr", "rrrrrrrr", ".rrrrrr.", "..rrrr..", "...rr...", "........"],
    "vanilla":     ["......cc", ".....cc.", "....cc..", "...cc...", ".n.c....", "..n.....", ".n......", "........"],
    "uhc":         ["...l....", "..lyy...", ".yyyyyy.", "yyyyyyyy", "yyyyyyyy", "yyyyyyyy", ".yyyyyy.", "..yyyy.."],
    "elytra":      ["gg....gg", "ggg..ggg", "gggggggg", "gg.gg.gg", "g..gg..g", "...gg...", "...gg...", "........"],
    "elytraspear": ["gg....c.", "ggg..c..", "ggggc...", "gg.c....", "g.c.....", "..c.....", ".c......", "........"],
    "spearmace":   ["....kkk.", "...kwwwk", "...kwwwk", "....kkk.", "..n.....", ".n......", "n.......", "........"],
    "modernsmp":   ["kkk..kkk", "kkkkkkkk", "kkkwwkkk", "kkkwwkkk", "kkkkkkkk", ".kkkkkk.", ".kkkkkk.", "........"],
    "diamondop":   ["ccc..ccc", "cccccccc", "cccwwccc", "cccwwccc", "cccccccc", ".cccccc.", ".cccccc.", "........"],
    "diamondcart": ["........", "g......g", "g......g", "gg....gg", "gggggggg", "gggggggg", ".k....k.", ".k....k."],
    "spleef":      [".....ggg", ".....ggg", "....gg..", "...n....", "..n.....", ".n......", "n.......", "........"],
    "pufferfish":  ["..y..y..", ".yyyyyy.", "yyoyyoyy", "yyyyyyyy", "yyyyyyyy", ".yyyyyy.", "..y..y..", "........"],
}


def chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(PALETTE[ch]) for ch in row) for row in rows)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(png)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/justtiers/textures/novatiers"
    os.makedirs(out, exist_ok=True)
    for name, rows in ICONS.items():
        assert len(rows) == 8, f"{name}: expected 8 rows, got {len(rows)}"
        for i, row in enumerate(rows):
            assert len(row) == 8, f"{name} row {i}: expected 8 columns, got {len(row)}"
        write_png(os.path.join(out, name + ".png"), rows)
    print(f"wrote {len(ICONS)} icons to {out}")


if __name__ == "__main__":
    main()
