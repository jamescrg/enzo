"""Bakes the hourglass from a photograph by Rocco Stoppoloni on Unsplash
(https://unsplash.com/photos/8lvbTjLai34): the empty glass as the light it catches, and a tile
of its sand.

The glass is in focus and what is behind it is not, so taking the blurred picture away from
itself leaves the glass's edges and reflections and little else. The sand is then taken out so
the app can pour its own: the stream and the maker's mark are smoothed away, the sand lying on
the floor is covered with the glass just above it, and the upper bulb below its sand line is
replaced with the lower bulb, which is empty, turned over and stretched to fit (the camera
looks down a little, so the two are not quite the same height).

Usage: bake_hourglass.py photo.jpg glass.png sand.png"""
import sys
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi

# The glass in the photograph, and the size it is worked on at. Every position below is in
# those working pixels.
CROP = (2700, 150, 4560, 3880)
W, H = 620, 1243
MIDDLE = 316
NECK = 616                       # the narrowest row
TOP_SHOULDER, BOTTOM_SHOULDER = 387, 800   # where each bulb's straight sides start to narrow
SAND_LINE = 195                  # the top of the sand in the upper bulb
FLOOR_SAND, FLOOR = 1092, 1194   # the sand lying in the lower bulb, down to the glass's foot
MARK = (311, 1097, 66)           # the maker's mark: its middle and radius
STREAM = (308, 344, 640, 1100)   # the falling sand: left, right, top, bottom
# The outline of the glass, down its left side from the top; the right side mirrors it.
OUTLINE = [(40, 247), (53, 247), (387, 247), (606, 22), (626, 22), (800, 245), (1160, 245), (1200, 135), (1206, 0)]
OUT = 0.8                        # the baked picture's size beside the working one

photo = Image.open(sys.argv[1]).crop(CROP).resize((W, H), Image.LANCZOS)
grey = np.asarray(photo.convert("L")).astype(float) / 255
light = np.clip((grey - ndi.gaussian_filter(grey, 14)) * 4, 0, 1)
ys, xs = np.mgrid[:H, :W].astype(float)

# The stream: each row bridged from one side of it to the other.
l, r, t, b = STREAM
across = (np.arange(l, r + 1) - l) / (r - l)
light[t:b, l:r + 1] = light[t:b, [l]] * (1 - across) + light[t:b, [r]] * across

# The maker's mark and the sand on the floor: the clear glass higher up the same straight
# sides, brought down over them.
mx, my, mr = MARK
lift = np.clip((mr + 14 - np.hypot(xs - mx, ys - my)) / 14, 0, 1)
light = light * (1 - lift) + np.roll(light, 2 * mr + 30, axis=0) * lift
lift = np.clip((ys - FLOOR_SAND + 10) / 20, 0, 1) * np.clip((FLOOR - ys) / 8, 0, 1)
light = light * (1 - lift) + np.roll(light, FLOOR - FLOOR_SAND + 20, axis=0) * lift

# The upper bulb below its sand line: the lower bulb turned over. Its narrowing part is
# stretched to the upper one's length, and the whole of it to the upper one's width.
src_y = np.where(
    ys > TOP_SHOULDER,
    NECK + (NECK - ys) * (BOTTOM_SHOULDER - NECK) / (NECK - TOP_SHOULDER),
    BOTTOM_SHOULDER + (TOP_SHOULDER - ys),
)
src_x = MIDDLE + (xs - MIDDLE) / 1.014
turned = ndi.map_coordinates(light, [src_y, src_x], order=1, mode="nearest")
blend = np.clip((ys - SAND_LINE + 12) / 24, 0, 1) * np.clip((NECK - 14 - ys) / 16, 0, 1)
light = light * (1 - blend) + turned * blend

# Nothing outside the glass.
mask = Image.new("L", (W, H), 0)
left = [(MIDDLE - half, y) for y, half in OUTLINE]
ImageDraw.Draw(mask).polygon(left + [(2 * MIDDLE - x, y) for x, y in reversed(left)], fill=255)
inside = ndi.gaussian_filter(ndi.binary_dilation(np.asarray(mask) > 0, iterations=6).astype(float), 3)
light *= inside

size = (round(W * OUT), round(H * OUT))
alpha = Image.fromarray((light * 255).astype(np.uint8)).resize(size, Image.LANCZOS)
Image.merge("LA", [Image.new("L", size, 255), alpha]).save(sys.argv[2], optimize=True)

# The sand: a patch from the middle of the upper bulb, evenly lit, mid-grey on average, and
# folded over at its edges so that it repeats without a seam.
TILE, FOLD = 128, 32
patch = np.asarray(
    Image.open(sys.argv[1]).crop((3420, 1080, 3900, 1560)).convert("L").resize((TILE + FOLD, TILE + FOLD), Image.LANCZOS)
).astype(float) / 255
patch = patch / ndi.gaussian_filter(patch, 6)
fold = (np.arange(FOLD) + 0.5) / FOLD
keep = 1 / np.sqrt(fold ** 2 + (1 - fold) ** 2)        # two patches mixed are flatter than either: put the grain back
patch -= patch.mean()
patch[:, :FOLD] = (patch[:, :FOLD] * fold[None, :] + patch[:, TILE:] * (1 - fold[None, :])) * keep[None, :]
patch[:FOLD, :] = (patch[:FOLD, :] * fold[:, None] + patch[TILE:, :] * (1 - fold[:, None])) * keep[:, None]
tile = patch[:TILE, :TILE]
tile = np.clip(0.5 + (tile - tile.mean()) / (5 * tile.std()), 0, 1)
Image.fromarray((tile * 255).astype(np.uint8)).save(sys.argv[3], optimize=True)
