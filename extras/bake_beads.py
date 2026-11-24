"""Bakes the bead sprite sheet from the rosary photo: real wood grain and real lighting,
greyscale, one tile per bead variant. Usage: bake_beads.py photo.jpg out_sheet.png preview.png"""
import sys, math
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
T=128; VARIANTS=10
src=np.asarray(Image.open(sys.argv[1]).convert('L')).astype(float)/255
def disc(cx,cy,r,size):
    """The square around a bead, resampled so the bead's radius r spans the tile."""
    ys,xs=np.mgrid[0:size,0:size]; u=(xs+0.5-size/2)/(size/2); v=(ys+0.5-size/2)/(size/2)
    return ndi.map_coordinates(src,[cy+v*r,cx+u*r],order=3,mode='nearest'),u,v
S=256
# Lighting: the broad light and shade across one whole bead, grain smoothed away.
light,u,v=disc(1845,900,97,S)
rr=np.hypot(u,v); inside=rr<=1
w=(rr<0.93).astype(float)
light=ndi.gaussian_filter(light*w,S*0.07)/np.maximum(ndi.gaussian_filter(w,S*0.07),1e-3)
light*=1-0.35*np.clip((rr-0.80)/0.20,0,1)**2          # the rim turns away from the light
# Grain: fine detail from the middles of three beads, free of their own lighting.
grains=[]
for cx,cy,r,keep in [(1845,900,97,0.86),(1567,498,86,0.84),(1378,625,90,0.74)]:
    g,_,_=disc(cx,cy,r*keep,S)
    low=ndi.gaussian_filter(g,S*0.06)
    grains.append(g/np.maximum(low,1e-3))
tiles=[]
rng=np.random.default_rng(7)
for i in range(VARIANTS):
    g=ndi.rotate(grains[i%3],angle=(i//3)*97+i*31+rng.uniform(0,20),reshape=False,order=3,mode='reflect')
    L=light*(1+(g-1)*1.25)
    tiles.append(L)
allv=np.concatenate([t[inside] for t in tiles]); mean=allv.mean(); sd=allv.std()
sheet=np.zeros((T,T*VARIANTS,2),np.uint8)
for i,L in enumerate(tiles):
    L=np.clip(0.5+(L-mean)/sd*0.17,0,1)                 # mid-grey on average, modest contrast
    a=np.clip((1-rr)*S/2/2+0.5,0,1)                      # soft edge
    im=Image.merge('LA',[Image.fromarray((L*255).astype(np.uint8)),Image.fromarray((a*255).astype(np.uint8))]).resize((T,T),Image.LANCZOS)
    sheet[:,i*T:(i+1)*T]=np.asarray(im)
Image.fromarray(sheet,'LA').save(sys.argv[2],optimize=True)

# Preview of the ring as the app draws it, in both themes, at three stages.
def ring(f,bg,track,ink,box=840,pad=60):
    N=27; MARK=1.35; slots=N+MARK+0.15; sa=2*math.pi/slots
    half=box/2; R=half/(1+MARK*0.47*sa); slot=R*sa; d=slot*0.94
    W=box+2*pad; out=np.zeros((W,W,3))+np.array(bg)/255.0; c=W/2
    ys,xs=np.mgrid[0:W,0:W]; rad=np.hypot(xs-c,ys-c); cord=np.clip(1-abs(rad-R)/(d*0.035),0,1)[...,None]
    out=out*(1-cord)+np.array(track)/255.0*cord
    def put(i,x,y,dia,col,k,alpha):
        s=max(2,round(dia)); t=np.asarray(Image.fromarray(sheet[:,i*T:(i+1)*T],'LA').resize((s,s),Image.LANCZOS)).astype(float)/255
        L=t[...,0:1]; a=t[...,1:2]*alpha; colr=np.clip(np.array(col)/255.0+k*(L-0.5),0,1)
        x0=round(x-s/2); y0=round(y-s/2); out[y0:y0+s,x0:x0+s]=out[y0:y0+s,x0:x0+s]*(1-a)+colr*a
    cnt=f*N
    for j in range(N):
        ang=-math.pi/2+((MARK+0.15)/2+0.5+j)*sa; x=c+R*math.cos(ang); y=c+R*math.sin(ang)
        put(j%9,x,y,d,track,0.22,1); amt=min(1,max(0,cnt-j))
        if amt>0: put(j%9,x,y,d,ink,0.5,amt)
    put(9,c,c-R,d*MARK,ink if f>=1 else track,0.5 if f>=1 else 0.22,1)
    return Image.fromarray((out*255).astype(np.uint8))
dark=((0x2D,0x35,0x3B),(0x3D,0x48,0x4D),(0xD3,0xC6,0xAA)); lightT=((0xD3,0xC6,0xAA),(0xC3,0xB8,0x9F),(0x2D,0x35,0x3B))
rows=[[ring(f,*th) for f in (0,0.4,1)] for th in (dark,lightT)]
w=rows[0][0].width; o=Image.new('RGB',(w*3,w*2))
for r,row in enumerate(rows):
    for i,im in enumerate(row): o.paste(im,(i*w,r*w))
o.resize((o.width//2,o.height//2),Image.LANCZOS).save(sys.argv[3]); rows[0][1].crop((w//2,0,w,w//2)).save(sys.argv[3].replace('.png','_zoom.png'))
