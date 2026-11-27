# Quiet (Android)

A meditation timer: Android port of the desktop [Meditation Timer](../Meditation%20Timer): a single timer with
duration presets or custom (1–180 min), chime sounds, vibrate-only or silent, repeat, system and chime volume,
Everforest dark / Seoul256 light themes, persistent settings.

A running timer is kept alive by a foreground service, so it chimes with the screen off.

The same app as a web page, for phones without the Android app, is in [`web/`](web/README.md).

## Build and install

Requires JDK 17 and the Android SDK (path in `local.properties`).

```bash
./gradlew assembleDebug                      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                       # to a phone connected with USB debugging
./gradlew testDebugUnitTest
```

## Audio samples

Chime samples are CC0 (public domain) from [Freesound.org](https://freesound.org), except where noted:

- Tingsha: [steffcaffrey](https://freesound.org/people/steffcaffrey/sounds/435074/)
- Chime: [LloydEvans09](https://freesound.org/people/LloydEvans09/sounds/185822/)
- Bright Bowl: [mttvn](https://freesound.org/people/mttvn/sounds/535950/)
- Keisu: our own recording of a Japanese bronze keisu (a spare take, not used by the app, is in `extras/`)
- Deep Bowl: [Rimych](https://freesound.org/people/Rimych/sounds/616335/)
- Chapel Bell: [JakobGille](https://freesound.org/people/JakobGille/sounds/693772/)
- Moktak: composite from [jonopodmore](https://freesound.org/people/jonopodmore/sounds/607215/)
- Clappers: [bergentroll](https://freesound.org/people/bergentroll/sounds/717371/)
- Beep: generated (groups of three short 1 kHz tones), not a sample

The optional brushstroke ring is "Enso.svg" by Kendrick Shaw, CC0, from
[Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Enso.svg).

The beads ring is made from real wooden beads in the photograph "Rosary 2006-01-16.jpg" by
Daniel Tibi, public domain, from
[Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Rosary_2006-01-16.jpg). The script
that cuts the beads out of the photo is `extras/bake_beads.py`.

The incense stick and its ash come from the photograph "Incense in India.jpg", CC0, from
[Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Incense_in_India.jpg), cut out by
`extras/bake_incense.py`. The cast iron bowl it stands in is cut from a product photograph by
`extras/bake_incense_dish.py`; **its source and licence are not yet established**, so it must
be cleared or replaced before the app is published.
The smoke is drawn by the app.

The labyrinth is the path of the one laid in the floor of Chartres cathedral around 1200. Its
route was read from the diagram "Labyrinth 1 (from Nordisk familjebok).svg", public domain,
from [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Labyrinth_1_(from_Nordisk_familjebok).svg),
and the app draws it from that list of turns.

The candle and its dish are cut from a
[photograph by Tim Umphreys](https://unsplash.com/photos/An_j14lRl5k) on Unsplash, used under
the [Unsplash License](https://unsplash.com/license), and its flame from "candle flame
closeup" by lisafree54, CC0, on
[Flickr](https://www.flickr.com/photos/136594255@N06/24857435811). `extras/bake_candle.py`
cuts them out.

The moon is the photograph "Moon nearside LRO.jpg" by NASA/GSFC/Arizona State University,
public domain, from
[Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Moon_nearside_LRO.jpg), cut out by
`extras/bake_moon.py`; the app puts the phase's light and shadow on it.

The hourglass, which is shelved for now and not offered in the app, is cut from a
[photograph by Rocco Stoppoloni](https://unsplash.com/photos/8lvbTjLai34) on Unsplash, used
under the [Unsplash License](https://unsplash.com/license). `extras/bake_hourglass.py` takes
the empty glass from it, as the light it catches, and a tile of its sand; the app pours the
sand.

The kaleidoscope is drawn by the app.
