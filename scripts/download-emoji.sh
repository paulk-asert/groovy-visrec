#!/bin/sh
# Licensed under the Apache License, Version 2.0 (see LICENSE)
# Downloads fruit emoji PNGs from two emoji sets with different art styles:
#   Noto Color Emoji 2D and 3D images (Apache License 2.0)
#   Twemoji graphics (CC-BY 4.0)
set -e
cd "$(dirname "$0")/.."
mkdir -p data/noto2d data/noto3d data/twemoji
while read -r name code; do
    curl -sfL -o "data/noto2d/$name.png" \
        "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/2D/png/128/emoji_u$code.png"
    curl -sfL -o "data/noto3d/$name.png" \
        "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/3D/png/128/emoji_u$code.png"
    curl -sfL -o "data/twemoji/$name.png" \
        "https://raw.githubusercontent.com/jdecked/twemoji/main/assets/72x72/$code.png"
done <<EOF
apple 1f34e
cherry 1f352
orange 1f34a
peach 1f351
banana 1f34c
grape 1f347
strawberry 1f353
lemon 1f34b
EOF
