"""Bakes the incense dish from the photo by Mathilde Langevin (Unsplash, B-qFF8W0hog): the
ceramic dish cut from its background, without the stick, greyscale with a soft-edged mask,
mid-grey on average. Usage: bake_dish.py photo.jpg out.png"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
X0, Y0, N = 1100, 2900, 1600                       # the square of the photo that holds the dish
OUT_W = 512
rgb = np.asarray(Image.open(sys.argv[1]).convert("RGB").crop((X0, Y0, X0 + N, Y0 + N))).astype(float)
# The wall and table change down the frame but hardly across it, so each row's background is
# what its two ends show; the dish is whatever differs from that.
ends = np.concatenate([rgb[:, :120], rgb[:, -120:]], axis=1)
bg = ndi.gaussian_filter1d(np.median(ends, axis=1)[:, None, :], 4, axis=0)
d = ndi.gaussian_filter(np.sqrt(((rgb - bg) ** 2).sum(-1)), 1.5)
m = ndi.binary_fill_holes(ndi.binary_closing(d > 9, iterations=6))
ys, xs = np.mgrid[0:N, 0:N]
# Not the stick, which stands above the rim...
m &= ~((ys < 940) | ((xs > 920) & (xs < 1012) & (ys < 1040)))
# ...nor the shadow under the dish: the foot's lower edge, traced.
bx = [0, 470, 520, 600, 700, 800, 900, 1000, 1100, 1180, 1235, 1280, 1330, 1380, 1420, N]
by = [1370, 1380, 1408, 1425, 1435, 1438, 1436, 1430, 1420, 1408, 1392, 1372, 1332, 1272, 1210, 1210]
m &= ys < np.interp(xs, bx, by)
lab, n = ndi.label(m)
m = lab == 1 + int(np.argmax(ndi.sum(m, lab, range(1, n + 1))))
soft = ndi.gaussian_filter(m.astype(float), 5)        # rounds off the ragged shadow side
m = soft > 0.5
box = np.argwhere(m); (t, l), (b, r) = box.min(0) - 6, box.max(0) + 7
grey = (rgb @ [0.299, 0.587, 0.114]) / 255
# The few pixels of stick left on the rim: covered with the rim just to their left.
patch = (xs > 925) & (xs < 1010) & (ys > 1025) & (ys < 1066)
grey[patch] = grey[:, :-90][patch[:, 90:]]
m |= patch & (ys > 1042)
alpha = np.clip(ndi.gaussian_filter(m.astype(float), 1.5) * 2 - 0.5, 0, 1)
grey, alpha = grey[t:b, l:r], alpha[t:b, l:r]
h = round(OUT_W * grey.shape[0] / grey.shape[1])
def small(a): return np.asarray(Image.fromarray((a * 255).astype(np.uint8)).resize((OUT_W, h), Image.LANCZOS)).astype(float) / 255
L, A = small(grey), small(alpha)
mean = (L * A).sum() / A.sum()
L = np.clip(0.5 + (L - mean) * 1.15, 0, 1)
Image.merge("LA", [Image.fromarray((v * 255).astype(np.uint8)) for v in (L, A)]).save(sys.argv[2], optimize=True)
s = OUT_W / (r - l)
print("size", OUT_W, h, "foot at", (963 - l) * s / OUT_W, (1048 - t) * s / h, "crop", l, t, r, b)
