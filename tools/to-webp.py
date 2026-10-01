"""Converts ./out/<code>.png (see render-flags.mjs) into the app's lossless WebP flag resources."""
from PIL import Image

CODES = 'fr de it gb es gr se no ua us ca mx br ar co cl jp cn in kr vn th sa eg ke za ng ma tz au nz fj aq'.split()
for code in CODES:
    image = Image.open(f'out/{code}.png').convert('RGB')  # drops the alpha channel
    assert image.size == (600, 450), (code, image.size)
    image.save(f'app/src/main/res/drawable-nodpi/flag_{code}.webp', 'WEBP', lossless=True, method=6)
print(len(CODES), 'flags written')
