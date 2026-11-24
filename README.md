# Enzo (Android)

A meditation timer: Android port of the desktop [Meditation Timer](../Meditation%20Timer): a single timer with
duration presets or custom (1–180 min), chime sounds, vibrate-only or silent, repeat, system and chime volume,
Everforest dark / Seoul256 light themes, persistent settings.

A running timer is kept alive by a foreground service, so it chimes with the screen off.

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
`extras/bake_incense.py`. The dish it stands in is cut from a
[photograph by Mathilde Langevin](https://unsplash.com/photos/B-qFF8W0hog) on Unsplash, used
under the [Unsplash License](https://unsplash.com/license), by `extras/bake_incense_dish.py`.
The smoke is drawn by the app.
