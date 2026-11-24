"""Bakes the candle's three pictures.
The candle and its dish, from a photo by Tim Umphreys (Unsplash, An_j14lRl5k). The candle is
its dripped top on the first CAP rows and below that a length of its side that repeats without
a seam; the glow of the flame through the wax is evened out, since the app puts it back where
the flame is. The dish is cut out along its traced outline. Both are greyscale with a
soft-edged mask, mid-grey on average.
The flame, from the photo "candle flame closeup" (lisafree54, CC0): white, with the photo's
brightness as its mask, so the bright body of the flame is solid and its edges and foot fade.
Usage: bake_candle.py candle.jpg flame.jpg candle_out.png dish_out.png flame_out.png"""
import sys
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi

def save(L, A, path):
    Image.merge("LA", [Image.fromarray((np.clip(v, 0, 1) * 255).astype(np.uint8)) for v in (L, A)]).save(path, optimize=True)

def half(a):
    h, w = a.shape[0] // 2 * 2, a.shape[1] // 2 * 2
    return a[:h, :w].reshape(h // 2, 2, w // 2, 2).mean((1, 3))

photo = Image.open(sys.argv[1]).convert("RGB")
grey = (np.asarray(photo).astype(float) @ [0.299, 0.587, 0.114]) / 255

# ── The candle ──
X0, X1, TOP, SIDE, END = 1730, 2140, 2930, 3600, 4400       # the candle in the photo
BODY0, BODY1 = 1800, 2114                                    # its straight sides
g = ndi.gaussian_filter(grey[TOP:END, X0:X1], 1.5)
inner = g[:, BODY0 - X0 + 40:BODY1 - X0 - 40].mean(1)        # how bright the wax is, row by row
inner = ndi.gaussian_filter1d(inner, 25, mode="nearest")
flat = g / inner[:, None]                                    # the glow evened out
alpha = np.clip((flat - 0.35) / 0.25, 0, 1)
ys, xs = np.mgrid[0:END - TOP, 0:X1 - X0]
# The flame and wick in this photo stand above the wax: masked off.
wick = (ys < 45) & (np.abs(xs - (1957 - X0)) < 70) & (ys < 45 - 0.3 * np.abs(xs - (1957 - X0)))
alpha[wick] = 0
alpha[(ys > 700) & ((xs < BODY0 - X0 - 12) | (xs > BODY1 - X0 + 12))] = 0     # nothing beside the plain side
alpha = ndi.gaussian_filter(alpha, 1.2)
CAP = SIDE - TOP
cap, cap_a = flat[:CAP], alpha[:CAP]
side, side_a = flat[CAP:], alpha[CAP:]
side = ndi.gaussian_filter(side, 3)                           # the dim lower wax is grainy
side = np.concatenate([side, side[::-1]])
side_a = np.tile(side_a.mean(0), (side.shape[0], 1))
L, A = half(np.concatenate([cap, side])), half(np.concatenate([cap_a, side_a]))
mean = (L * A).sum() / A.sum()
save(0.5 + (L - mean) * 1.4, A, sys.argv[3])
print("candle", L.shape, "cap rows", CAP // 2, "side rows", side.shape[0] // 2)

# ── The dish ──
DX0, DX1, DY0, DY1 = 1110, 2820, 4415, 4925
cx, cy, a, b = 1965, 4560, 842, 132                          # the rim: an ellipse seen nearly edge on
outline = [(cx + a * np.cos(t), cy - b * np.sin(t)) for t in np.linspace(0, np.pi, 60)]            # far edge
outline += [(1125, 4580), (1210, 4700), (1380, 4810), (1620, 4885), (1965, 4912), (2310, 4885), (2550, 4810),
            (2720, 4700), (2805, 4580)]                                                           # underside
mask = Image.new("L", photo.size, 0)
ImageDraw.Draw(mask).polygon(outline, fill=255)
m = np.asarray(mask)[DY0:DY1, DX0:DX1] > 0
d = ndi.gaussian_filter(grey[DY0:DY1, DX0:DX1] ** 0.5, 4)    # shadows lifted, grain smoothed
ys, xs = np.mgrid[DY0:DY1, DX0:DX1]
# The foot of the candle stands in the dish: painted over with the dish on either side.
for y in range(d.shape[0]):
    l, r = BODY0 - 30 - DX0, BODY1 + 30 - DX0
    if DY0 + y < 4700:
        d[y, l:r] = np.linspace(d[y, l - 8:l].mean(), d[y, r:r + 8].mean(), r - l)
da = np.clip(ndi.gaussian_filter(m.astype(float), 3) * 2 - 0.5, 0, 1)
W = 512; H = round(W * d.shape[0] / d.shape[1])
small = lambda v: np.asarray(Image.fromarray((np.clip(v, 0, 1) * 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)).astype(float) / 255
L, A = small(d), small(da)
mean = (L * A).sum() / A.sum()
save(0.5 + (L - mean) * 1.6, A, sys.argv[4])
print("dish", L.shape, "candle centre at", (1957 - DX0) / (DX1 - DX0), "foot at", (4666 - DY0) / (DY1 - DY0),
      "candle picture width / dish width", (X1 - X0) / (DX1 - DX0), "body centre in candle picture", (1957 - X0) / (X1 - X0))

# ── The flame ──
f = np.asarray(Image.open(sys.argv[2]).convert("L")).astype(float) / 255
FX0, FX1, FY0, FY1 = 420, 612, 50, 572
f = f[FY0:FY1, FX0:FX1]
a = np.clip((f - 0.35) / 0.5, 0, 1)
ys, xs = np.mgrid[0:f.shape[0], 0:f.shape[1]]
# Only the flame: the lit wax around its foot is outside this outline, a pointed top over a
# rounded foot.
BELLY, FOOT = 425, 520                                     # its widest row and its lowest
halfw = np.where(ys < BELLY, 22 + 66 * np.clip(ys / 380.0, 0, 1) ** 0.6,
                 88 * np.sqrt(np.clip(1 - ((ys - BELLY) / float(FOOT - BELLY)) ** 2, 0, 1)))
window = np.clip((halfw - np.abs(xs - 96 - 3 * ys / f.shape[0])) / 12, 0, 1)
a = ndi.gaussian_filter(a * window, 1.0)
save(np.ones_like(a), a, sys.argv[5])
print("flame", a.shape)
