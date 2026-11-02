#!/usr/bin/env python3
"""Нарезка арта для Yume Studio из макета art/mockup.png.

Каждый кусок: прямоугольник в координатах исходника, список прямоугольников,
где на макете «запечён» UI (плашки с названием и т.п.) — они закрашиваются
inpaint'ом, после чего картинка апскейлится и сохраняется в webp.

Запуск:  python3 tools/crop_art.py
Нужны: Pillow, numpy, opencv-python-headless.
Чтобы заменить арт на hi-res — просто положите свои файлы с теми же именами
в app/src/main/res/drawable-nodpi/.
"""
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "art" / "mockup.png"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

# name: (crop box, [inpaint boxes], scale)
SCENES = {
    "art_home": ((557, 44, 970, 208), [(656, 162, 870, 207)], 3),
    "art_piano": ((14, 380, 548, 484), [(194, 460, 362, 484)], 3),
    "art_guitar": ((569, 380, 1016, 478), [(717, 457, 892, 478)], 3),
    "art_drums": ((1128, 380, 1527, 552), [(1282, 460, 1434, 492)], 3),
    "art_bass": ((14, 610, 547, 700), [(213, 679, 360, 700)], 3),
    "art_synth": ((569, 610, 848, 700), [(690, 678, 848, 700)], 3),
    "art_vocal": ((1128, 610, 1525, 696), [], 3),
    "art_strings": ((14, 798, 303, 924), [], 3),
    "art_library": ((569, 797, 655, 925), [], 3),
}

# квадратные аватарки (центр, сторона) — лица персонажей
AVATARS = {
    "ava_piano": ((300, 424), 92),
    "ava_guitar": ((824, 414), 84),
    "ava_drums": ((1366, 418), 84),
    "ava_bass": ((222, 650), 84),
    "ava_synth": ((750, 650), 76),
    "ava_vocal": ((1362, 652), 80),
    "ava_strings": ((166, 857), 84),
    "ava_home": ((708, 106), 96),
}


def inpainted(img: Image.Image, boxes):
    if not boxes:
        return img
    arr = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    mask = np.zeros(arr.shape[:2], np.uint8)
    for (x0, y0, x1, y1) in boxes:
        mask[y0:y1, x0:x1] = 255
    res = cv2.inpaint(arr, mask, 9, cv2.INPAINT_TELEA)
    # чуть размываем залатанное место, чтобы не было «штриховки»
    blurred = cv2.GaussianBlur(res, (0, 0), 6)
    soft = cv2.GaussianBlur(mask, (0, 0), 4).astype(np.float32)[..., None] / 255.0
    res = (res * (1 - soft) + blurred * soft).astype(np.uint8)
    return Image.fromarray(cv2.cvtColor(res, cv2.COLOR_BGR2RGB))


def upscale(img: Image.Image, scale: int) -> Image.Image:
    big = img.resize((img.width * scale, img.height * scale), Image.LANCZOS)
    return big.filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    src = Image.open(SRC).convert("RGB")
    for name, (box, holes, scale) in SCENES.items():
        clean = inpainted(src, holes)
        upscale(clean.crop(box), scale).save(OUT / f"{name}.webp", quality=90, method=6)
    for name, ((cx, cy), side) in AVATARS.items():
        h = side // 2
        crop = src.crop((cx - h, cy - h, cx + h, cy + h))
        crop.resize((256, 256), Image.LANCZOS).filter(
            ImageFilter.UnsharpMask(radius=2, percent=50, threshold=2)
        ).save(OUT / f"{name}.webp", quality=90, method=6)

    # иконка: квадрат внутри скруглённой рамки макета → foreground адаптивной иконки
    icon = src.crop((82, 30, 286, 234)).resize((432, 432), Image.LANCZOS)
    icon = icon.filter(ImageFilter.UnsharpMask(radius=2, percent=50, threshold=2))
    icon.save(OUT / "ic_launcher_art.webp", quality=92, method=6)
    print("ok ->", OUT)


if __name__ == "__main__":
    main()
