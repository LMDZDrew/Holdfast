// Headless check of the preview page's logic with stubbed DOM and three.js (no WebGL, no rendering).
// Proves the page script runs every mode and state without exceptions or non-finite transforms.
// It does not prove anything looks right. Usage: node test_preview.js [path/to/preview.html]
const fs = require("fs");
const path = require("path");
const file = process.argv[2] || path.join(__dirname, "..", "preview", "holdfast_character_preview.html");
const html = fs.readFileSync(file, "utf8");
const dataTxt = html.split('<script type="application/json" id="d">')[1].split("</script>")[0];
const scripts = html.match(/<script>([\s\S]*?)<\/script>/g);
const code = scripts[scripts.length - 1].replace(/^<script>/, "").replace(/<\/script>$/, "");

const els = {};
function el(id) {
  if (!els[id]) els[id] = { id, hidden: false, style: {}, children: [], innerHTML: "", textContent: "", value: "", dataset: {},
    classList: { toggle() {}, add() {}, remove() {} }, setAttribute() {}, getAttribute() { return null; },
    appendChild(c) { this.children.push(c); }, prepend(c) { this.children.unshift(c); },
    get lastChild() { return { remove: () => this.children.pop() }; }, selectedOptions: [{ textContent: "option" }],
    clientWidth: 800, clientHeight: 500 };
  return els[id];
}
el("d").textContent = dataTxt; el("speed").value = "1"; el("sel-guard").value = "2"; el("scrub").value = "0";
el("sel-attacker").value = "upright";
let n = 0;
const document = { getElementById: el, querySelectorAll: () => [], querySelector: () => null,
  createElement: t => t === "canvas" ? { getContext: () => ({ fillRect() {}, set fillStyle(v) {} }) } : el("tmp" + n++) };
const listeners = {};
const window = { addEventListener: (k, f) => (listeners[k] = f), devicePixelRatio: 1 };
let raf = null;
class V3 { constructor(x = 0, y = 0, z = 0) { this.x = x; this.y = y; this.z = z; } set(x, y, z) { this.x = x; this.y = y; this.z = z; return this; }
  copy(v) { return this.set(v.x, v.y, v.z); } clone() { return new V3(this.x, this.y, this.z); } setScalar(s) { return this.set(s, s, s); } }
class Obj { constructor() { this.position = new V3(); this.rotation = new V3(); this.rotation.order = "XYZ"; this.scale = new V3(1, 1, 1); this.children = []; this.visible = true; }
  add(c) { this.children.push(c); } remove(c) { this.children = this.children.filter(x => x !== c); } }
let bad = 0, frames = 0;
const THREE = { Group: Obj, Scene: class extends Obj {}, Mesh: class extends Obj { constructor(g) { super(); this.geometry = g; this.isMesh = true; } },
  Vector3: V3, Color: class {}, Fog: class {}, HemisphereLight: Obj,
  DirectionalLight: class extends Obj { constructor() { super(); this.shadow = { mapSize: new V3(), camera: {} }; } },
  PerspectiveCamera: class extends Obj { updateProjectionMatrix() {} },
  WebGLRenderer: class { constructor() { this.domElement = {}; this.shadowMap = {}; } setPixelRatio() {} setSize() {}
    render(s, cam) { frames++; const chk = v => { if (!isFinite(v)) bad++; };
      [cam.position.x, cam.position.y, cam.position.z, cam.rotation.x, cam.rotation.y, cam.rotation.z].forEach(chk);
      (function walk(o) { for (const c of o.children) { [c.position.x, c.position.y, c.position.z, c.rotation.x, c.rotation.y, c.rotation.z].forEach(chk); walk(c); } })(s); } },
  OrbitControls: class { constructor() { this.target = new V3(); } update() {} }, PlaneGeometry: class {}, MeshLambertMaterial: class {},
  CanvasTexture: class { constructor() { this.repeat = new V3(); } }, TextureLoader: class { load() { return {}; } },
  BufferGeometry: class { setAttribute() {} setIndex() {} computeVertexNormals() {} }, Float32BufferAttribute: class {},
  NearestFilter: 1, RepeatWrapping: 1, PCFSoftShadowMap: 1, DoubleSide: 2 };
class ResizeObserver { observe() {} }
new Function("document", "window", "requestAnimationFrame", "ResizeObserver", "performance", "THREE", code)(
  document, window, f => (raf = f), ResizeObserver, { now: () => 0 }, THREE);

const api = window.__holdfast;
let now = 0;
const step = (k, dt = 1000 / 60) => { for (let i = 0; i < k; i++) { now += dt; raf(now); } };
const results = [];
function check(label, fn) { try { fn(); results.push(["ok", label]); } catch (e) { results.push(["FAIL", label + ": " + e.message]); } }

check("herd", () => { api.setMode("herd"); step(300); });
check("library walker layers", () => { api.setMode("library"); step(200); });
for (const k of ["walker:prone_rise", "walker:prone_ankle_grab", "crawler:crawler_lunge", "player:move_crawl_legless",
                 "player:move_crutch_improvised", "player:bite_confirm_neck", "player:bat_charged_miss"])
  check("library " + k, () => { api.setLib(k); step(200); });
api.setMode("exec");
for (const k of ["knife", "bat_quick", "bat_charged", "axe", "machete", "knife_ground", "knife_ground_prone", "bat_charged_miss"])
  check("exec " + k, () => { api.setExec(k); step(330); });
check("exec first person", () => { api.setExec("axe"); api.setFp(true); step(330); api.setFp(false); });

// grab: for each attacker, let one bite get blocked, one break the guard, one penetrate, then escape and confirm
for (const atk of ["upright", "prone", "crawler"]) {
  check("grab " + atk, () => {
    api.setMode("grab"); api.setAttacker(atk); api.setFp(true);
    const seen = new Set(); let escaped = false;
    for (let i = 0; i < 4000; i++) {
      step(1); seen.add(api.G.state);
      if (!escaped && api.G.bitten && api.G.state === "struggle" && api.G.t > 0.2) { listeners.keydown({ code: "Space", preventDefault() {} }); escaped = true; }
      if (escaped && seen.has("confirm") && api.G.state === (atk === "prone" ? "dormant" : "approach")) break;
    }
    const need = ["seize", "struggle", "bite", "free", "confirm"];
    const missing = need.filter(s => !seen.has(s));
    if (missing.length) throw new Error("never reached " + missing.join(", "));
  });
}
check("teammate rescue", () => { api.setMode("grab"); api.setAttacker("upright");
  for (let i = 0; i < 600 && api.G.state !== "struggle"; i++) step(1);
  el("g-mate").onclick(); if (api.G.state !== "mate") throw new Error("teammate did not end the grab"); step(100); });

for (const [s, l] of results) console.log(s.padEnd(4), l);
console.log(`frames rendered: ${frames}; non-finite transforms: ${bad}`);
process.exit(results.some(r => r[0] === "FAIL") || bad ? 1 : 0);
