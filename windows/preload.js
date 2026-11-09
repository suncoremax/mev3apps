// Runs inside the web page (sandboxed). Adds: on-screen Back button for mouse users + keyboard focus recovery.
const { ipcRenderer } = require('electron');
let canBack = false, btn;
function build() {
  if (btn || !document.documentElement) return;
  const host = document.createElement('div');
  host.style.cssText = 'all:initial;position:fixed;top:8px;left:8px;z-index:2147483647;';
  const root = host.attachShadow({ mode: 'closed' });
  root.innerHTML = '<style>button{all:initial;box-sizing:border-box;width:38px;height:38px;border-radius:50%;background:rgba(30,30,30,.72);color:#fff;font:22px/38px "Segoe UI",sans-serif;text-align:center;cursor:pointer;box-shadow:0 1px 6px rgba(0,0,0,.35);opacity:.85;transition:.15s;display:none}button:hover{opacity:1;background:rgba(0,0,0,.9)}</style><button title="Back (Esc)">←</button>';
  btn = root.querySelector('button');
  btn.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); ipcRenderer.send('nav-back'); });
  btn.addEventListener('mousedown', e => e.preventDefault());   // never steal focus from a text box
  document.documentElement.appendChild(host);
  show();
}
function show() { if (btn) btn.style.display = canBack ? 'block' : 'none'; }
ipcRenderer.on('nav-state', (_e, v) => { canBack = v; show(); });
window.addEventListener('DOMContentLoaded', () => { build(); ipcRenderer.send('nav-ready'); });
// any click -> make sure the page really owns keyboard focus (fixes "can't type in box" after dialogs/popups)
window.addEventListener('mousedown', () => ipcRenderer.send('refocus'), true);
