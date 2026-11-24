"""Bakes the incense strip from the photo "Incense in India.jpg": the ash column on top and a
length of the coated stick below it, greyscale with a soft-edged mask, mid-grey on average.
Usage: bake_incense.py photo.jpg out.png"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
W=24; ASH=96; BODY=256
src=np.asarray(Image.open(sys.argv[1]).convert('L')).astype(float)/255
def strip(x0,x1,y0,y1,w,h):
    ys,xs=np.mgrid[0:h,0:w]
    return ndi.map_coordinates(src,[y0+(ys+0.5)/h*(y1-y0),x0+(xs+0.5)/w*(x1-x0)],order=3,mode='nearest')
def norm(a,spread):
    return np.clip(0.5+(a-a.mean())/a.std()*spread,0,1)
# The coated stick, mirrored end to end so the tile repeats without a seam.
half=strip(1637,1680,1300,1556,W,BODY//2)
body=norm(np.concatenate([half,half[::-1]]),0.2)
# The ash still standing on the burning end.
ash=norm(strip(1642,1677,1123,1272,W,ASH),0.2)
x=(np.arange(W)+0.5)/W*2-1
edge=np.clip((1-np.abs(x))*W/2/1.5,0,1)                       # soft sides
a_body=np.tile(edge,(BODY,1))
a_ash=np.tile(edge,(ASH,1))
yy=np.arange(ASH)[:,None]; top=np.clip((yy+0.5-(W/2-np.sqrt(np.clip((W/2)**2-((np.arange(W)+0.5-W/2)**2)[None,:],0,None))*0.5))/1.5,0,1)
a_ash=a_ash*top                                               # gently rounded top
L=np.concatenate([ash,body]); A=np.concatenate([a_ash,a_body])
Image.merge('LA',[Image.fromarray((L*255).astype(np.uint8)),Image.fromarray((A*255).astype(np.uint8))]).save(sys.argv[2],optimize=True)
print(L.shape)
