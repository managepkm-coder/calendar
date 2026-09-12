/* 교대 근무표 — 주 → 야 → 비 → 휴 4일 주기
 * 기준: 2026-08-30 = 주간 (offset 0). 본인 조가 다르면 설정에서 "오늘 근무"만 고르면 맞춰집니다. */

const CYCLE = [
  { k: '주', cls: 'ju',  name: '주간' },
  { k: '야', cls: 'ya',  name: '야간' },
  { k: '비', cls: 'bi',  name: '비번' },
  { k: '휴', cls: 'hyu', name: '휴무' },
];
const EXTRA = { '연': { k: '연', cls: 'off', name: '연차' } };

const DAY = 86400000;
const ANCHOR = Date.UTC(2026, 7, 30); // 2026-08-30

const store = {
  // 시크릿 모드나 사이트 데이터 차단 시 접근 자체가 예외를 던지므로 전부 감싼다
  read(k, d) { try { return localStorage.getItem(k) ?? d; } catch { return d; } },
  write(k, v) { try { localStorage.setItem(k, v); } catch { /* 저장 불가 - 이번 세션만 유지 */ } },

  // 기준일을 코드에 박아두면 다른 조 사람에게 틀린 표가 그럴듯하게 보인다.
  // 사용자가 한 번 고르기 전까지는 어떤 근무도 만들어내지 않는다.
  get configured() { return this.read('shift.offset', null) !== null; },

  get offset() { return +this.read('shift.offset', 0); },
  set offset(v) { this.write('shift.offset', String(((v % 4) + 4) % 4)); },
  get overrides() { try { return JSON.parse(this.read('shift.overrides', '{}')); } catch { return {}; } },
  set overrides(v) { this.write('shift.overrides', JSON.stringify(v)); },
};

const pad = (n) => String(n).padStart(2, '0');
const key = (ts) => { const d = new Date(ts); return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`; };
const todayTs = () => { const n = new Date(); return Date.UTC(n.getFullYear(), n.getMonth(), n.getDate()); };

/** 해당 날짜의 근무. 하루 지정이 있으면 그것을 우선한다.
 *  주기를 아직 정하지 않았으면 null — 빈 칸으로 표시된다. */
function shiftOf(ts) {
  const ov = store.overrides[key(ts)];
  if (ov) return EXTRA[ov] || CYCLE[+ov] || null;
  if (!store.configured) return null;
  const n = Math.round((ts - ANCHOR) / DAY);
  return CYCLE[(((n + store.offset) % 4) + 4) % 4];
}

let view = (() => { const n = new Date(); return { y: n.getFullYear(), m: n.getMonth() }; })();
let picked = null;

const $ = (s) => document.querySelector(s);

function render() {
  const { y, m } = view;
  $('#ym').textContent = `${y}. ${pad(m + 1)}`;

  const t = todayTs();
  const s = shiftOf(t);
  $('#sub').textContent = s
    ? `오늘 ${key(t).slice(5).replace('-', '.')} · ${s.name}`
    : '⚙ 를 눌러 오늘의 근무를 먼저 정하세요';

  const first = Date.UTC(y, m, 1);
  const startDow = new Date(first).getUTCDay();
  const inMonth = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
  const cells = Math.ceil((startDow + inMonth) / 7) * 7;
  const start = first - startDow * DAY;

  const grid = $('#grid');
  grid.innerHTML = '';
  for (let i = 0; i < cells; i++) {
    const ts = start + i * DAY;
    const d = new Date(ts);
    const dd = d.getUTCDate(), dow = d.getUTCDay();
    const holiday = window.HOLIDAYS[key(ts)];
    const own = d.getUTCMonth() === m && d.getUTCFullYear() === y;

    const cell = document.createElement('button');
    cell.className = 'cell';
    cell.dataset.ts = ts;
    if (!own) cell.classList.add('dim');
    if (ts === t) cell.classList.add('today');
    if (ts === picked) cell.classList.add('picked');

    const label = document.createElement('div');
    label.className = 'date' + (holiday || dow === 0 ? ' sun' : dow === 6 ? ' sat' : '');
    // 달이 바뀌는 첫 칸에는 월까지 표시
    label.textContent = (i === 0 || dd === 1 ? `${pad(d.getUTCMonth() + 1)}.${pad(dd)}` : pad(dd))
      + (holiday ? ` ${holiday}` : '');
    cell.appendChild(label);

    const sh = shiftOf(ts);
    const badge = document.createElement('div');
    badge.className = 'badge' + (sh ? ` ${sh.cls}` : '');
    badge.textContent = sh ? sh.k : '';
    if (store.overrides[key(ts)]) badge.classList.add('manual');
    cell.appendChild(badge);

    grid.appendChild(cell);
  }
}

function move(delta) {
  const d = new Date(Date.UTC(view.y, view.m + delta, 1));
  view = { y: d.getUTCFullYear(), m: d.getUTCMonth() };
  render();
}

/* ── 하루 근무 직접 바꾸기 ── */
function openDay(ts) {
  picked = ts;
  const d = new Date(ts);
  $('#dayTitle').textContent = `${d.getUTCFullYear()}. ${pad(d.getUTCMonth() + 1)}. ${pad(d.getUTCDate())}`;
  // 손댄 적 없는 날에는 "지정 해제"가 아무 일도 하지 않으므로 숨긴다
  $('[data-set="clear"]').hidden = !store.overrides[key(ts)];
  $('#daySheet').showModal();
  render();
}

function setDay(val) {
  const ov = store.overrides;
  if (val === null) delete ov[key(picked)]; else ov[key(picked)] = val;
  store.overrides = ov;
  $('#daySheet').close();
  render();
}

/* ── 설정: "오늘 근무"를 고르면 주기가 맞춰진다 ── */
function openSettings() {
  const t = todayTs();
  const base = ((Math.round((t - ANCHOR) / DAY) % 4) + 4) % 4;
  const cur = shiftOf(t);
  $('#offsetBtns').innerHTML = '';
  CYCLE.forEach((c, i) => {
    const b = document.createElement('button');
    b.className = `badge ${c.cls} pick` + (cur && cur.k === c.k ? ' on' : '');
    b.textContent = c.k;
    b.onclick = () => { store.offset = i - base; $('#settings').close(); render(); };
    $('#offsetBtns').appendChild(b);
  });
  $('#settings').showModal();
}

/* ── 이벤트 ── */
$('#prev').onclick = () => move(-1);
$('#next').onclick = () => move(1);
$('#today').onclick = () => { const n = new Date(); view = { y: n.getFullYear(), m: n.getMonth() }; picked = null; render(); };
$('#btnSettings').onclick = openSettings;
$('#grid').onclick = (e) => { const c = e.target.closest('.cell'); if (c) openDay(+c.dataset.ts); };
document.querySelectorAll('[data-set]').forEach((b) => {
  b.onclick = () => setDay(b.dataset.set === 'clear' ? null : b.dataset.set);
});
$('#resetAll').onclick = () => {
  if (confirm('직접 지정한 날짜를 모두 지울까요?')) { store.overrides = {}; $('#settings').close(); render(); }
};

// 좌우 스와이프로 달 이동
let sx = 0, sy = 0;
document.addEventListener('touchstart', (e) => { sx = e.touches[0].clientX; sy = e.touches[0].clientY; }, { passive: true });
document.addEventListener('touchend', (e) => {
  const dx = e.changedTouches[0].clientX - sx, dy = e.changedTouches[0].clientY - sy;
  if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 2) move(dx < 0 ? 1 : -1);
}, { passive: true });

render();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('./sw.js').catch(() => {});

// 아직 근무를 정하지 않았다면 바로 설정을 띄운다
if (!store.configured) openSettings();
