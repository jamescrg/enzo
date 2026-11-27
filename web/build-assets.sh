#!/usr/bin/env bash
# Makes the web app's sounds, pictures and icons from the Android app's resources.
# Run again after changing a chime or a picture there. Needs an ffmpeg with libmp3lame:
#
#     FFMPEG=/path/to/ffmpeg web/build-assets.sh
#
# The chimes become MP3, which every phone browser plays (Safari does not play Ogg Vorbis
# everywhere). Each chime's own level (maxVolume in Chimes.kt) is baked into its file, because
# an iPhone ignores the volume a page asks for: the files have to be level with one another
# as they stand. Keep the levels here in step with Chimes.kt and with web/js/audio.js.
set -euo pipefail
cd "$(dirname "$0")"
FFMPEG=${FFMPEG:-ffmpeg}
RES=../app/src/main/res

mkdir -p audio img icons

chime() {  # source file, output name, level
    "$FFMPEG" -hide_banner -loglevel error -y -i "$RES/raw/$1" \
        -af "volume=$3" -ar 44100 -codec:a libmp3lame -q:a 2 "audio/$2.mp3"
}
chime tingsha.ogg       tingsha       0.2
chime chime.ogg         chime         0.12
chime bowl_bright.ogg   bowl_bright   0.2
chime bowl_deep.ogg     bowl_deep     0.2
chime keisu.ogg         keisu         0.12
chime bell_chapel.ogg   bell_chapel   0.5
chime moktak.wav        moktak        1
chime moktak_short.wav  moktak_short  1
chime clappers.wav      clappers      1
chime beep.wav          beep          1

for picture in beads candle candle_dish candle_flame incense incense_dish moon; do
    cp "$RES/drawable-nodpi/$picture.png" img/
done

# The launcher icon: the moon on the dark theme's background. The Android picture is the
# foreground of an adaptive icon, of which the middle two thirds show; a maskable icon keeps
# more room round the moon, since a phone may cut it to a circle.
icon() {  # crop of the 432px picture, output size, output name
    "$FFMPEG" -hide_banner -loglevel error -y \
        -f lavfi -i "color=0x2D353B:s=432x432" -i "$RES/drawable-nodpi/ic_launcher_moon.png" \
        -filter_complex "[0][1]overlay,crop=$1:$1,scale=$2:$2:flags=lanczos" -frames:v 1 "icons/$3.png"
}
icon 288 192 icon-192
icon 288 512 icon-512
icon 360 512 icon-maskable-512
icon 320 180 apple-touch-icon
icon 288 48  favicon
