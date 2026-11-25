"""Bakes the moon from the photograph "Moon nearside LRO.jpg" (NASA/GSFC/Arizona State
University, public domain): the disc cut from its black sky, greyscale with a soft-edged round
mask, mid-grey on average. The picture is a mosaic lit evenly all over, so the app can put the
phase's light and shadow on it itself. Usage: bake_moon.py photo.jpg out.png"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
SIZE = 720
g = np.asarray(Image.open(sys.argv[1]).convert("L")).astype(float) / 255
lit = ndi.gaussian_filter(g, 2) > 0.08
ys, xs = np.where(lit)
cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
r = min(xs.max() - xs.min(), ys.max() - ys.min()) / 2 - 3   # a little inside the limb, clear of the sky
print("disc", cx, cy, r)
y, x = np.mgrid[:g.shape[0], :g.shape[1]]
d = np.hypot(x - cx, y - cy)
alpha = np.clip(r - d + 0.5, 0, 1)
inside = d < r * 0.98
lo, hi = np.percentile(g[inside], [0.5, 99.8])
L = np.clip((g - lo) / (hi - lo), 0, 1) ** 0.8
L = np.clip(0.5 + (L - L[inside].mean()), 0, 1)
box = (round(cx - r), round(cy - r), round(cx + r), round(cy + r))
cut = lambda v: Image.fromarray((v * 255).astype(np.uint8)).crop(box).resize((SIZE, SIZE), Image.LANCZOS)
Image.merge("LA", [cut(L), cut(alpha)]).save(sys.argv[2], optimize=True)
