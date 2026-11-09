const { app, BrowserWindow, Menu, shell, ipcMain } = require('electron');
const path = require('path'), fs = require('fs');
const cfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8'));
const origin = new URL(cfg.url).origin;
app.setName(cfg.name);
if (!app.requestSingleInstanceLock()) { app.quit(); }
let win;
function create() {
  // Hidden menu keeps Ctrl+C/V/X/A/Z working in text boxes (a null menu breaks them on Windows)
  Menu.setApplicationMenu(Menu.buildFromTemplate([{ label: 'Edit', submenu: [
    { role: 'undo' }, { role: 'redo' }, { type: 'separator' }, { role: 'cut' }, { role: 'copy' }, { role: 'paste' }, { role: 'selectAll' }] }]));
  win = new BrowserWindow({ width: 1280, height: 800, minWidth: 800, minHeight: 560, title: cfg.name,
    icon: path.join(__dirname, 'icon.png'), backgroundColor: '#ffffff',
    webPreferences: { contextIsolation: true, sandbox: true, preload: path.join(__dirname, 'preload.js') } });
  win.setMenuBarVisibility(false);
  win.loadURL(cfg.url);
  const wc = win.webContents;
  wc.on('did-fail-load', (e, code, d, url, isMain) => {
    if (isMain && code !== -3) win.loadFile(path.join(__dirname, 'offline.html'), { query: { u: cfg.url } });
  });
  wc.on('page-title-updated', e => e.preventDefault());
  // --- keyboard focus recovery ---
  const refocus = () => { if (!win.isDestroyed() && win.isFocused() && !wc.isFocused()) wc.focus(); };
  win.on('focus', () => wc.focus());
  wc.on('did-finish-load', () => { wc.focus(); sendNav(); });
  ipcMain.on('refocus', refocus);
  // --- back navigation (mouse button on screen + Esc + Alt+Left + mouse back button) ---
  const sendNav = () => { if (!win.isDestroyed()) wc.send('nav-state', wc.canGoBack()); };
  const histBack = () => { if (wc.canGoBack()) wc.goBack(); };
  // Same order as the Android app: 1) web app's own window.__onAndroidBack()  2) browser history
  const goBack = () => wc.executeJavaScript(`(function(){try{return !!(window.__onAndroidBack&&window.__onAndroidBack());}catch(e){return false;}})()`, true)
    .then(handled => { if (!handled) histBack(); }).catch(histBack);
  ['did-navigate', 'did-navigate-in-page', 'did-frame-finish-load'].forEach(ev => wc.on(ev, sendNav));
  ipcMain.on('nav-ready', sendNav);
  ipcMain.on('nav-back', goBack);
  win.on('app-command', (e, cmd) => { if (cmd === 'browser-backward') goBack(); else if (cmd === 'browser-forward' && wc.canGoForward()) wc.goForward(); });
  // right-click menu: copy / paste etc.
  wc.on('context-menu', (e, p) => {
    const t = [];
    if (p.isEditable) t.push({ role: 'undo' }, { role: 'redo' }, { type: 'separator' }, { role: 'cut' });
    if (p.isEditable || p.selectionText) t.push({ role: 'copy' });
    if (p.isEditable) t.push({ role: 'paste' }, { role: 'selectAll' });
    if (t.length) Menu.buildFromTemplate(t).popup();
  });
  // Esc = Back, unless user is typing, a popup/modal is open, or fullscreen
  const escBack = () => wc.executeJavaScript(`(function(){var a=document.activeElement;
    if(a&&(/^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName)||a.isContentEditable))return 'typing';
    try{if(window.__onAndroidBack&&window.__onAndroidBack())return 'handled';}catch(e){}
    return document.querySelector('dialog[open],[role=dialog],[role=alertdialog],[aria-modal=true],.modal.show,.modal.open,.swal2-container,.ant-modal,.MuiDialog-root')?'modal':'free';})()`, true)
    .then(r => { if (r === 'free') histBack(); }).catch(() => {});
  wc.setWindowOpenHandler(({ url }) => {
    if (url.startsWith(origin)) return { action: 'allow' };
    shell.openExternal(url); return { action: 'deny' };
  });
  // Esc = Back is ALWAYS on (even if the other shortcuts are switched off), like the Android back button
  wc.on('before-input-event', (ev, i) => {
    if (i.type !== 'keyDown' || i.isAutoRepeat) return;   // auto-repeat: holding Esc must not skip many pages
    const k = i.key, c = i.control || i.meta, z = d => wc.setZoomLevel(wc.getZoomLevel() + d);
    if (k === 'Escape' && !i.alt && !c && !i.shift) {
      if (win.isFullScreen()) { win.setFullScreen(false); ev.preventDefault(); } else escBack();
      return;
    }
    if (i.alt && k === 'ArrowLeft') { ev.preventDefault(); goBack(); return; }
    if (i.alt && k === 'ArrowRight') { ev.preventDefault(); if (wc.canGoForward()) wc.goForward(); return; }
    if (!cfg.keyboard) return;
    let h = true;
    if (k === 'F5' && i.shift || k === 'F5' && c || c && i.shift && k.toLowerCase() === 'r') wc.reloadIgnoringCache();
    else if (k === 'F5' || c && k.toLowerCase() === 'r') wc.reload();
    else if (k === 'F11') win.setFullScreen(!win.isFullScreen());
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
