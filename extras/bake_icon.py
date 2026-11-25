"""Bakes the launcher icon's foreground: the app's moon (res/drawable-nodpi/moon.png, see
bake_moon.py) as a waxing crescent in the dark theme's moon colour, the rest of the disc barely
lit, on a transparent 108dp square for an adaptive icon. Usage: bake_icon.py moon.png out.png"""
import sys
import numpy as np
from PIL import Image
SIZE = 432                    # 108dp at xxxhdpi
RADIUS = SIZE * 26 / 108      # well inside the 33dp safe zone
TERMINATOR = 0.42             # the terminator's bulge to the right, as a part of the radius
SOFT = 0.07                   # the width of the terminator's shade, as a part of the radius
PAPER = np.array([0x2D, 0x35, 0x3B]) / 255
LIT = np.array([0xC9, 0xBD, 0x9F]) / 255
DARK_TONE, DARK_SHADING, LIT_SHADING = 0.06, 0.1, 0.6

d = round(RADIUS * 2)
moon = np.asarray(Image.open(sys.argv[1]).convert("LA").resize((d, d), Image.LANCZOS)).astype(float) / 255
detail, alpha = moon[..., 0] - 0.5, moon[..., 1]
y, x = (np.mgrid[:d, :d] + 0.5) / (d / 2) - 1
edge = TERMINATOR * np.sqrt(np.clip(1 - y * y, 0, 1))
light = np.clip((x - edge) / SOFT + 0.5, 0, 1)
light = light * light * (3 - 2 * light)
lit = LIT[None, None] * (1 + 2 * LIT_SHADING * detail[..., None])
dark = (PAPER + (LIT - PAPER) * DARK_TONE)[None, None] * (1 + 2 * DARK_SHADING * detail[..., None])
rgb = np.clip(dark + (lit - dark) * light[..., None], 0, 1)
out = np.zeros((SIZE, SIZE, 4))
o = (SIZE - d) // 2
out[o:o + d, o:o + d, :3] = rgb
out[o:o + d, o:o + d, 3] = alpha
Image.fromarray((out * 255).round().astype(np.uint8), "RGBA").save(sys.argv[2], optimize=True)
