"""Draws simple shape pictures (pure Python, no libraries) and zips them with a
.qzx file as the Quiz app's "Shapes" sample. Usage: make_shapes.py <out.zip>"""
import math
import struct
import sys
import zipfile
import zlib

SIZE = 256
SS = 3  # supersampling per axis, for smooth edges


def png(pixels):
    raw = b"".join(b"\x00" + bytes(row) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def polygon(n, radius=0.8, rotation=-math.pi / 2, inner=None):
    points = []
    steps = n * 2 if inner else n
    for i in range(steps):
        r = radius if not inner or i % 2 == 0 else inner
        angle = rotation + 2 * math.pi * i / steps
        points.append((r * math.cos(angle), r * math.sin(angle)))
    return points


def inside_polygon(x, y, points):
    hit = False
    j = len(points) - 1
    for i in range(len(points)):
        xi, yi = points[i]
        xj, yj = points[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            hit = not hit
        j = i
    return hit


SHAPES = {
    "circle": ((0x1E, 0x88, 0xE5), lambda x, y: x * x + y * y <= 0.8 ** 2),
    "square": ((0xE5, 0x39, 0x35), lambda x, y: abs(x) <= 0.65 and abs(y) <= 0.65),
    "triangle": ((0x43, 0xA0, 0x47), lambda x, y, p=polygon(3, 0.85, -math.pi / 2): inside_polygon(x, y - 0.21, p)),
    "pentagon": ((0x8E, 0x24, 0xAA), lambda x, y, p=polygon(5): inside_polygon(x, y, p)),
    "hexagon": ((0xFB, 0x8C, 0x00), lambda x, y, p=polygon(6, rotation=0): inside_polygon(x, y, p)),
    "star": ((0xFD, 0xD8, 0x35), lambda x, y, p=polygon(5, 0.85, inner=0.36): inside_polygon(x, y, p)),
}


def draw(color, test):
    rows = []
    for py in range(SIZE):
        row = []
        for px in range(SIZE):
            hits = 0
            for sy in range(SS):
                for sx in range(SS):
                    x = ((px + (sx + 0.5) / SS) / SIZE) * 2 - 1
                    y = ((py + (sy + 0.5) / SS) / SIZE) * 2 - 1
                    hits += test(x, y)
            a = hits / (SS * SS)
            row += [round(255 * (1 - a) + c * a) for c in color]
        rows.append(row)
    return png(rows)


with zipfile.ZipFile(sys.argv[1], "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("Shapes.qzx", "".join(f"!{name}.png\n{name}\n" for name in SHAPES))
    for name, (color, test) in SHAPES.items():
        z.writestr(f"{name}.png", draw(color, test))
