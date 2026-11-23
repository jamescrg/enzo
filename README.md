# Meditation Timer (Android)

Android port of the desktop [Meditation Timer](../Meditation%20Timer): up to 4 simultaneous
timers, duration presets or custom (1–180 min), five chime sounds, per-timer chime and repeat,
system and chime volume, Everforest dark / Seoul256 light themes, persistent settings.

Running timers are kept alive by a foreground service, so they chime with the screen off.

## Build and install

Requires JDK 17 and the Android SDK (path in `local.properties`).

```bash
./gradlew assembleDebug                      # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                       # to a phone connected with USB debugging
./gradlew testDebugUnitTest
```

## Audio samples

All chime samples are CC0 (public domain) from [Freesound.org](https://freesound.org):

- Tingsha: [steffcaffrey](https://freesound.org/people/steffcaffrey/sounds/435074/)
- Chime: [LloydEvans09](https://freesound.org/people/LloydEvans09/sounds/185822/)
- Singing Bowl: [surly](https://freesound.org/people/surly/sounds/91196/)
- Singing Bowl 2: [inoshirodesign](https://freesound.org/people/inoshirodesign/sounds/271370/)
- Moktak: composite from [jonopodmore](https://freesound.org/people/jonopodmore/sounds/607215/)
