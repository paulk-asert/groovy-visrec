#!/bin/sh
# Builds the blog hero card: inline card/cherry.json into the template, then screenshot it.
# cherry.json is extracted from build/card-data.json (sh gradlew run -Pmain=fruit.CardData).
cd "$(dirname "$0")"
python3 -I -c "
import sys
t = open('visrec_card.html').read()
open('build_card.html', 'w').write(t.replace('/*DATA*/null', open('cherry.json').read()))
"
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu \
  --screenshot="$PWD/visrec_hero.png" --window-size=1176,660 --force-device-scale-factor=2 \
  --hide-scrollbars "file://$PWD/build_card.html" 2>/dev/null
echo "wrote $PWD/visrec_hero.png"
