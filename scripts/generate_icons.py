#!/usr/bin/env python3
"""
JIN App Icon Generator
Generates all Android mipmap resolutions, adaptive icon assets, and splash logos
from the master 3D robot mascot icon.
Supports Pillow (when available) with pure Python fallback.
Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)
"""

import os
import sys
import zlib
import struct

def pure_python_resize(src_path, dst_path, target_w, target_h):
    with open(src_path, 'rb') as f:
        data = f.read()
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise ValueError(f"Not a PNG: {src_path}")
        
    idx = 8
    idat = bytearray()
    w, h = 0, 0
    while idx < len(data):
        length = struct.unpack('>I', data[idx:idx+4])[0]
        ctype = data[idx+4:idx+8]
        cdata = data[idx+8:idx+8+length]
        idx += 8 + length + 4
        if ctype == b'IHDR':
            w, h, depth, color_type = struct.unpack('>IIBB', cdata[:10])
        elif ctype == b'IDAT':
            idat.extend(cdata)
        elif ctype == b'IEND':
            break

    decompressed = zlib.decompress(bytes(idat))
    stride = 1 + w * 4
    raw_pixels = bytearray(w * h * 4)
    prev_row = bytearray(w * 4)
    for y in range(h):
        filter_type = decompressed[y * stride]
        row_raw = bytearray(decompressed[y * stride + 1 : (y + 1) * stride])
        if filter_type == 0:
            pass
        elif filter_type == 1:
            for x in range(4, len(row_raw)):
                row_raw[x] = (row_raw[x] + row_raw[x - 4]) & 0xFF
        elif filter_type == 2:
            for x in range(len(row_raw)):
                row_raw[x] = (row_raw[x] + prev_row[x]) & 0xFF
        elif filter_type == 3:
            for x in range(len(row_raw)):
                left = row_raw[x - 4] if x >= 4 else 0
                row_raw[x] = (row_raw[x] + ((left + prev_row[x]) >> 1)) & 0xFF
        elif filter_type == 4:
            for x in range(len(row_raw)):
                a = row_raw[x - 4] if x >= 4 else 0
                b = prev_row[x]
                c = prev_row[x - 4] if x >= 4 else 0
                p = a + b - c
                pa = abs(p - a)
                pb = abs(p - b)
                pc = abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                row_raw[x] = (row_raw[x] + pr) & 0xFF
        prev_row = row_raw
        raw_pixels[y * w * 4 : (y + 1) * w * 4] = row_raw

    out_pixels = bytearray(target_w * target_h * 4)
    x_ratio = w / float(target_w)
    y_ratio = h / float(target_h)
    for ty in range(target_h):
        sy = int(ty * y_ratio)
        for tx in range(target_w):
            sx = int(tx * x_ratio)
            src_idx = (sy * w + sx) * 4
            dst_idx = (ty * target_w + tx) * 4
            out_pixels[dst_idx : dst_idx + 4] = raw_pixels[src_idx : src_idx + 4]

    out_stride = 1 + target_w * 4
    out_scanlines = bytearray(target_h * out_stride)
    for ty in range(target_h):
        out_scanlines[ty * out_stride] = 0
        out_scanlines[ty * out_stride + 1 : (ty + 1) * out_stride] = out_pixels[ty * target_w * 4 : (ty + 1) * target_w * 4]

    compressed = zlib.compress(bytes(out_scanlines), level=9)
    def chunk(tag, payload):
        crc = zlib.crc32(tag + payload)
        return struct.pack('>I', len(payload)) + tag + payload + struct.pack('>I', crc)

    ihdr = struct.pack('>IIBBBBB', target_w, target_h, 8, 6, 0, 0, 0)
    png_out = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', compressed) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(dst_path), exist_ok=True)
    with open(dst_path, 'wb') as f:
        f.write(png_out)

def resize_icon(src_path, dst_path, size):
    try:
        from PIL import Image
        with Image.open(src_path) as img:
            img = img.convert('RGBA')
            resized = img.resize((size, size), Image.Resampling.LANCZOS)
            os.makedirs(os.path.dirname(dst_path), exist_ok=True)
            resized.save(dst_path, 'PNG')
            print(f"[Pillow] Generated {size}x{size} -> {dst_path}")
            return
    except ImportError:
        pass
    pure_python_resize(src_path, dst_path, size, size)
    print(f"[Pure Python] Generated {size}x{size} -> {dst_path}")

def main():
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    master_icon = os.path.join(root_dir, "repo_icon.png")
    
    if not os.path.exists(master_icon):
        print(f"Error: Master icon not found at {master_icon}")
        sys.exit(1)
        
    print(f"Generating JIN Android icons from {master_icon}...")
    
    # Target sizes for Android
    icon_specs = [
        ("app/src/main/res/mipmap-mdpi/ic_launcher.png", 48),
        ("app/src/main/res/mipmap-mdpi/ic_launcher_round.png", 48),
        ("app/src/main/res/mipmap-hdpi/ic_launcher.png", 72),
        ("app/src/main/res/mipmap-hdpi/ic_launcher_round.png", 72),
        ("app/src/main/res/mipmap-xhdpi/ic_launcher.png", 96),
        ("app/src/main/res/mipmap-xhdpi/ic_launcher_round.png", 96),
        ("app/src/main/res/mipmap-xxhdpi/ic_launcher.png", 144),
        ("app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png", 144),
        ("app/src/main/res/mipmap-xxxhdpi/ic_launcher.png", 192),
        ("app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png", 192),
        ("app/src/main/res/drawable-xxxhdpi/splash_logo.png", 512),
    ]
    
    for rel_path, size in icon_specs:
        target_path = os.path.join(root_dir, rel_path)
        resize_icon(master_icon, target_path, size)
        
    print("✅ All JIN app icons successfully generated!")

if __name__ == "__main__":
    main()
