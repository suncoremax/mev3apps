"""Applies the client settings (from env vars) to the android/ or windows/ template."""
import os, re, sys, json, urllib.request, io
from PIL import Image, ImageDraw

E = os.environ.get
target = sys.argv[1]
name, pkg, url, bg = E('APP_NAME', '').strip(), E('PKG', '').strip(), E('APP_URL', '').strip(), E('ICON_BG', '#FFFFFF').strip()
kb = E('KEYBOARD', 'true') == 'true'
if not name or len(name) > 40 or '\n' in name: sys.exit('Invalid app name')
if not re.fullmatch(r'[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+', pkg): sys.exit('Package must look like com.company.app (lowercase)')
if not re.fullmatch(r'https?://[^\s"\'\\]+', url): sys.exit('Link must start with https://')
if not re.fullmatch(r'#[0-9A-Fa-f]{6}', bg): bg = '#FFFFFF'

logo_url = E('LOGO_URL', '').strip()
raw = urllib.request.urlopen(urllib.request.Request(logo_url, headers={'User-Agent': 'gk'})).read() if logo_url else open('logo.png', 'rb').read()
logo = Image.open(io.BytesIO(raw)).convert('RGBA')

def fit(canvas, f):
    s = canvas.width * f / max(logo.size)
    l = logo.resize((max(1, int(logo.width * s)), max(1, int(logo.height * s))), Image.LANCZOS)
    canvas.alpha_composite(l, ((canvas.width - l.width) // 2, (canvas.height - l.height) // 2))
def make(size, mode):
    c = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    if mode == 'fg': fit(c, .6)
    elif mode == 'plain': fit(c, 1)
    else:
        m = Image.new('L', (size * 4, size * 4), 0); d = ImageDraw.Draw(m)
        (d.ellipse if mode == 'round' else lambda b, **k: d.rounded_rectangle(b, radius=size * 4 * .2))([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
        m = m.resize((size, size), Image.LANCZOS)
        c.paste(Image.new('RGBA', (size, size), bg), mask=m); fit(c, .72)
    return c
def edit(path, fn):
    s = open(path, encoding='utf8').read(); open(path, 'w', encoding='utf8').write(fn(s))

if target == 'android':
    M = 'android/app/src/main'
    edit('android/app/build.gradle', lambda s: re.sub(r'applicationId ".*?"', lambda m: f'applicationId "{pkg}"', s, 1))
    x = name.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '\\"').replace("'", "\\'")
    edit(f'{M}/res/values/strings.xml', lambda s: re.sub(r'(<string name="app_name">).*?(</string>)', lambda m: m[1] + x + m[2], s, 1))
    edit(f'{M}/java/com/axion/dms/MainActivity.java', lambda s: re.sub(r'APP_URL = ".*?";', lambda m: f'APP_URL = "{url}";', s, 1))
    edit(f'{M}/res/values/colors.xml', lambda s: re.sub(r'(name="ic_launcher_background">).*?(<)', lambda m: m[1] + bg + m[2], s, 1))
    for d, px in {'m': 48, 'h': 72, 'xh': 96, 'xxh': 144, 'xxxh': 192}.items():
        b = f'{M}/res/mipmap-{d}dpi/ic_launcher'
        make(px, 'sq').save(b + '.png'); make(px, 'round').save(b + '_round.png'); make(int(px * 2.25), 'fg').save(b + '_foreground.png')
    make(360, 'plain').save(f'{M}/assets/logo.png')
else:
    json.dump({'url': url, 'name': name, 'keyboard': kb}, open('windows/config.json', 'w'), indent=1)
    make(512, 'plain').save('windows/icon.png'); os.makedirs('windows/build', exist_ok=True); make(512, 'plain').save('windows/build/icon.png')
    p = json.load(open('windows/package.json')); slug = re.sub(r'[^a-z0-9]+', '-', pkg.lower()).strip('-')
    p['name'] = slug; p['productName'] = name; p['description'] = name
    p['build']['appId'] = pkg; p['build']['productName'] = name; p['build']['nsis']['shortcutName'] = name
    p['build']['artifactName'] = re.sub(r'[^A-Za-z0-9]+', '-', name).strip('-') + '-Setup.${ext}'
    json.dump(p, open('windows/package.json', 'w'), indent=2)
print('prepared', target, name, pkg)
