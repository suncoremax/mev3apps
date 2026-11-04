const { app, BrowserWindow, Menu, shell } = require('electron');
const path = require('path'), fs = require('fs');
const cfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8'));
const origin = new URL(cfg.url).origin;
app.setName(cfg.name);
if (!app.requestSingleInstanceLock()) { app.quit(); }
let win;
function create() {
  Menu.setApplicationMenu(null);
  win = new BrowserWindow({ width: 1280, height: 800, minWidth: 800, minHeight: 560, title: cfg.name,
    icon: path.join(__dirname, 'icon.png'), autoHideMenuBar: true, backgroundColor: '#ffffff',
    webPreferences: { contextIsolation: true, sandbox: true } });
  win.loadURL(cfg.url);
  const wc = win.webContents;
  wc.on('did-fail-load', (e, code, d, url, isMain) => {
    if (isMain && code !== -3) win.loadFile(path.join(__dirname, 'offline.html'), { query: { u: cfg.url } });
  });
  wc.on('page-title-updated', e => e.preventDefault());
  wc.setWindowOpenHandler(({ url }) => {
    if (url.startsWith(origin)) return { action: 'allow' };
    shell.openExternal(url); return { action: 'deny' };
  });
  if (cfg.keyboard) wc.on('before-input-event', (ev, i) => {
    if (i.type !== 'keyDown') return;
    const k = i.key, c = i.control || i.meta, z = d => wc.setZoomLevel(wc.getZoomLevel() + d);
    let h = true;
    if (k === 'F5' && i.shift || k === 'F5' && c || c && i.shift && k.toLowerCase() === 'r') wc.reloadIgnoringCache();
    else if (k === 'F5' || c && k.toLowerCase() === 'r') wc.reload();
    else if (k === 'F11') win.setFullScreen(!win.isFullScreen());
    else if (k === 'Escape' && win.isFullScreen()) win.setFullScreen(false);
    else if (i.alt && k === 'ArrowLeft') { if (wc.canGoBack()) wc.goBack(); }
    else if (i.alt && k === 'ArrowRight') { if (wc.canGoForward()) wc.goForward(); }
    else if (i.alt && k === 'Home') win.loadURL(cfg.url);
    else if (c && (k === '+' || k === '=')) z(0.5);
    else if (c && (k === '-' || k === '_')) z(-0.5);
    else if (c && k === '0') wc.setZoomLevel(0);
    else if (c && k.toLowerCase() === 'p') wc.print();
    else if (c && k.toLowerCase() === 'q') app.quit();
    else h = false;
    if (h) ev.preventDefault();
  });
}
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(create);
app.on('window-all-closed', () => app.quit());
