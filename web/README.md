# Quiet (web)

The same timer as the Android app, as a web page that a phone can keep on its home screen and
open with no connection. Plain HTML, CSS and JavaScript modules: no build step, no server-side
code. Any static web server over HTTPS will do (the offline copy needs HTTPS, or `localhost`).

    cd web && python3 -m http.server 8899      # then http://localhost:8899/
    cd web && npm test                         # the timer's rules, as in TimerStateTest.kt

## Layout

- `js/state.js`: the timer's rules (TimerState.kt), with nothing of the browser in them.
- `js/engine.js`: the state, its saving, and the settings (TimerEngine.kt).
- `js/audio.js`: the chimes, and keeping the page awake until the chime (TimerService.kt, Chimes.kt).
- `js/draw.js`, `js/enso-data.js`: the eight styles, drawn on a canvas with the Android app's numbers.
- `js/app.js`, `index.html`, `style.css`: the screen (MainActivity.kt).
- `sw.js`, `manifest.webmanifest`: the offline copy and the home-screen app.
- `audio/`, `img/`, `icons/`: made from the Android resources by `build-assets.sh`. Run it
  again after changing a chime or a picture there.

## What is different from Android

- **Staying awake.** There is no foreground service on the web. From Start to the chime the
  page plays one piece of audio (an inaudible hush, then the chime in the same player), which
  is what a phone lets a page go on doing with the screen off. The phone shows it as a media
  player on the lock screen, with a pause button. "Keep screen on" uses the browser's screen
  wake lock.
- **No Do Not Disturb switch, and no System volume slider:** a page can do neither.
- **Chime volume:** an iPhone plays a page's audio at the level of its own buttons whatever
  the page asks, so the Volume tab is left out there. Each chime's own level is baked into its
  MP3 so they stay level with one another everywhere.
- **Vibrate endings** are offered only where the browser can vibrate (not on an iPhone).
- **The Silent ending** shows its "Session complete" notice only if the user allows the page
  notifications; the end shows in the app either way.
- **The system bars** are not hidden when the controls fade. On the home screen the app has no
  browser bars in any case.

## Updating

`sw.js` answers from its kept copy and fetches fresh files in the background, so a new version
shows the second time the app is opened after it is put on the server. When the app gains a
file, add it to the list in `sw.js`.

## Where it runs

- **https://quiettimer.app** — the prod droplet serves the checkout at `/home/james/tacet`
  (`web/`) as static files. Set up once with `sudo deploy/setup-site.sh`, which gets the
  certificate and installs `deploy/nginx-quiettimer`. To put out a new version:
  `ssh prod git -C tacet pull`.
- **https://darmok.link/quiet/** — the working copy on the dev server, behind its login.
