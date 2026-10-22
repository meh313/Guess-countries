# Third-party notices

This file is packaged into the app (its `assets/` folder) so the licence travels with the flag images.

## Flag artwork

The flag images in `app/src/main/res/drawable-nodpi/flag_*.webp` were rendered from the SVG files of
[flag-icons](https://github.com/lipis/flag-icons) 7.5.0 (`flags/4x3`), unstretched at 600x450 pixels (4:3) and
saved as lossless WebP (`tools/render-flags.mjs` reproduces them). One colour differs from flag-icons: Eritrea's
lower triangle is drawn in the flag's blue, #418fde (Pantone 279 C), where flag-icons has a pale #b4d7f4 (`RECOLOUR`
in `tools/render-flags.mjs`). flag-icons is distributed under the MIT License:

```
The MIT License (MIT)

Copyright (c) 2013 Panayiotis Lipiridis

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Map data

The country outlines, background land, label points and capitals in `app/src/main/assets/world.json`, and the
bounding boxes in `app/src/main/java/com/example/data/model/Geo.kt`, are derived from
[Natural Earth](https://www.naturalearthdata.com/) 5.2.0-pre data (the 1:50m Admin 0 countries and breakaway and
disputed areas and the 1:10m populated places, as published in the GitHub mirror `nvkelso/natural-earth-vector`),
simplified and adjusted to the catalog by `tools/gen-worldmap.py`, which reproduces them. The adjustments:
Somaliland, Northern Cyprus and Åland are merged into Somalia, Cyprus and Finland, and the ISR feature into
Palestine, which is drawn as one outline; Crimea is moved into Ukraine and Natural Earth's own Golan Heights polygon
into Syria; Morocco is cut at 27°40′ N, the part south of the line going to Western Sahara; Western Sahara, the
dependencies and every other feature that is no catalog country are kept only as background land, drawn as land but
never as a country; and Palestine's capital is set by hand in East Jerusalem.
Natural Earth is in the public domain: no permission is needed and crediting the authors is unnecessary, so this
notice is a courtesy. Its terms of use (https://www.naturalearthdata.com/about/terms-of-use/) say:

```
All versions of Natural Earth raster + vector map data found on this website are in the public domain. You may use
the maps in any manner, including modifying the content and design, electronic dissemination, and offset printing.
The primary authors, Tom Patterson and Nathaniel Vaughn Kelso, and all other contributors renounce all financial
claim to the maps and invites you to use them for personal, educational, and commercial purposes. No permission is
needed to use Natural Earth. Crediting the authors is unnecessary.
```
