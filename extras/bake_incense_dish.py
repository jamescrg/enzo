"""Bakes the incense bowl from a product photo of a cast iron incense bowl on white: the bowl
cut from its background, without the stick, greyscale with a soft-edged mask, mid-grey on
average. Usage: bake_incense_dish.py photo.webp out.png"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
L0, T0, R0, B0 = 190, 505, 605, 752                    # the bowl in the photo
STICK_X, SOCKET_Y = 398, 572                           # where the stick goes into the holder
grey = (np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(float) @ [0.299, 0.587, 0.114]) / 255
# The stick stands in front of the bowl's far side: painted over with the bowl on either side.
for y in range(T0, SOCKET_Y + 3):
    x = STICK_X + round((SOCKET_Y - y) * 0.049)
    a, b = x - 9, x + 10
    grey[y, a:b] = np.linspace(grey[y, a - 3:a].mean(), grey[y, b:b + 3].mean(), b - a)
g = grey[T0:B0, L0:R0]
# Dark iron on white paper; the soft shadow under the bowl is lighter than either and is left out.
m = ndi.binary_closing(ndi.gaussian_filter(g, 0.7) < 0.64, iterations=2)
m[730 - T0:] &= ndi.gaussian_filter(g, 0.7)[730 - T0:] < 0.3   # under the bowl only its feet, not their shadow
lab, n = ndi.label(m)
m = lab == 1 + int(np.argmax(ndi.sum(m, lab, range(1, n + 1))))
alpha = np.clip(ndi.gaussian_filter(m.astype(float), 1.0) * 2 - 0.5, 0, 1)
inside = alpha > 0.9
lo, hi = np.percentile(g[inside], [1, 99.9])
L = np.clip((g - lo) / (hi - lo), 0, 1) ** 0.9          # the iron's faint texture and rim light, brought out
L = np.clip(0.5 + (L - L[inside].mean()) * 1.0, 0, 1)
S = 2                                                   # the photo is small: enlarged smoothly
size = (g.shape[1] * S, g.shape[0] * S)
up = lambda v: Image.fromarray((v * 255).astype(np.uint8)).resize(size, Image.LANCZOS)
Image.merge("LA", [up(L), up(alpha)]).save(sys.argv[2], optimize=True)
print("size", size, "socket at", (STICK_X - L0) / (R0 - L0), (SOCKET_Y - T0) / (B0 - T0))
