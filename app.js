/* 교대 근무표 — 되풀이되는 근무 차례(패턴)를 설정에서 직접 정합니다.
 * 안드로이드 앱과 같은 방식이지만 저장소가 서로 달라 설정은 오가지 않습니다.
 * 두 곳에서 각각 한 번씩 맞춰 주세요. */

const SHIFTS = {
  '주': { k: '주', cls: 'ju',   name: '주간' },
  '야': { k: '야', cls: 'ya',   name: '야간' },
  '비': { k: '비', cls: 'bi',   name: '비번' },
  '휴': { k: '휴', cls: 'hyu',  name: '휴무' },
  '연': { k: '연', cls: 'yeon', name: '연차' },
  '지': { k: '지', cls: 'ji',   name: '지원근무' },
};

/** 패턴을 정하기 전까지 쓰는 차례 */
const DEFAULT_CYCLE = ['주', '야', '비', '휴'];
/** 패턴에 넣을 수 있는 근무 — 연차는 하루짜리라 되풀이되는 차례에 들어가지 않는다 */
const PATTERN_CHOICES = ['주', '야', '비', '휴', '지'];
/** 이보다 길면 만들다 지치고 화면에도 담기지 않는다 */
const MAX_PATTERN = 31;

const DAY = 86400000;
/** 세는 시작점. 어느 날이든 상관없다 — 사용자가 고른 offset 이 나머지를 맞춘다. */
const ANCHOR = Date.UTC(2026, 7, 30);

const store = {
  // 시크릿 모드나 사이트 데이터 차단 시 접근 자체가 예외를 던지므로 전부 감싼다
  read(k, d) { try { return localStorage.getItem(k) ?? d; } catch { return d; } },
  write(k, v) { try { localStorage.setItem(k, v); } catch { /* 저장 불가 - 이번 세션만 유지 */ } },
  drop(k) { try { localStorage.removeItem(k); } catch { /* 위와 같음 */ } },

  // 기준일을 코드에 박아두면 다른 조 사람에게 틀린 표가 그럴듯하게 보인다.
  // 사용자가 한 번 고르기 전까지는 어떤 근무도 만들어내지 않는다.
  get configured() { return this.read('shift.offset', null) !== null; },

  /** 되풀이되는 근무 차례. 정한 적이 없으면 기본값(주·야·비·휴). */
  get cycle() {
    const raw = this.read('shift.pattern', null);
    if (!raw) return DEFAULT_CYCLE.slice();
    const list = raw.split(',').filter((k) => SHIFTS[k]);
    return list.length ? list : DEFAULT_CYCLE.slice();
  },
  /** 차례를 바꾸면 길이가 달라져 기존 offset 이 가리키던 자리가 뜻을 잃는다.
   *  그래서 지워 두고, 사용자가 오늘 위치를 다시 고르게 한다. */
  set cycle(list) {
    this.write('shift.pattern', list.slice(0, MAX_PATTERN).join(','));
    this.drop('shift.offset');
  },

  get offset() { return +this.read('shift.offset', 0); },
  set offset(v) { const n = this.cycle.length; this.write('shift.offset', String(((v % n) + n) % n)); },

  get overrides() { try { return JSON.parse(this.read('shift.overrides', '{}')); } catch { return {}; } },
  set overrides(v) { this.write('shift.overrides', JSON.stringify(v)); },
};

const pad = (n) => String(n).padStart(2, '0');
const key = (ts) => { const d = new Date(ts); return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`; };
const todayTs = () => { const n = new Date(); return Date.UTC(n.getFullYear(), n.getMonth(), n.getDate()); };

/** 하루 지정 값을 근무로 읽는다.
 *  예전에는 4일 주기의 자리 번호('0'~'3')로 저장했는데, 주기 길이가 달라지면
 *  번호는 뜻을 잃으므로 그때 값은 기본 주기를 거쳐 글자로 바꿔 읽는다. */
function overrideOf(ts) {
  const v = store.overrides[key(ts)];
  if (v == null) return null;
  if (SHIFTS[v]) return SHIFTS[v];
  const i = Number(v);
  return Number.isInteger(i) && DEFAULT_CYCLE[i] ? SHIFTS[DEFAULT_CYCLE[i]] : null;
}

/** ANCHOR 로부터 센, 차례 안의 자리 */
const baseIndex = (ts, n) => ((Math.round((ts - ANCHOR) / DAY) % n) + n) % n;

/** 해당 날짜의 근무. 하루 지정이 있으면 그것을 우선한다.
 *  차례를 아직 맞추지 않았으면 null — 빈 칸으로 표시된다. */
function shiftOf(ts) {
  const ov = overrideOf(ts);
  if (ov) return ov;
  if (!store.configured) return null;
  const c = store.cycle;
  return SHIFTS[c[(baseIndex(ts, c.length) + store.offset) % c.length]];
}

let view = (() => { const n = new Date(); return { y: n.getFullYear(), m: n.getMonth() }; })();
let picked = null;

const $ = (s) => document.querySelector(s);

/** 고를 수 있는 근무를 동그라미 버튼으로 깔아 준다 */
function badgeRow(box, keys, onPick, marked) {
  box.innerHTML = '';
  keys.forEach((k, i) => {
    const s = SHIFTS[k];
    const b = document.createElement('button');
    b.className = `badge ${s.cls} pick` + (i === marked ? ' on' : '');
    b.textContent = s.k;
    b.title = s.name;
    b.onclick = () => onPick(k, i);
    box.appendChild(b);
  });
}

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
  // 교대로 대신 서는 근무는 지금 차례에 든 것들, 거기에 연차·지원근무
  badgeRow($('#dayBtns'), [...new Set([...store.cycle, '연', '지'])], setDay);
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

/* ── 설정: 근무 패턴을 정하고, 오늘이 그 차례의 몇 번째 날인지 고른다 ── */
function openSettings() {
  const c = store.cycle;
  $('#patternText').textContent = `${c.join(' · ')}  (${c.length}일)`;

  const t = todayTs();
  const base = baseIndex(t, c.length);
  const cur = store.configured ? (base + store.offset) % c.length : -1;

  badgeRow($('#offsetBtns'), c, (k, i) => {
    store.offset = i - base;
    $('#settings').close();
    render();
  }, cur);

  $('#settings').showModal();
}

/* ── 근무 패턴 만들기: 하루씩 골라 쌓는다 ── */
let building = [];

function openPattern() {
  building = [];
  drawPattern();
  $('#settings').close();
  $('#pattern').showModal();
}

function drawPattern() {
  $('#patternSoFar').textContent = building.length
    ? `${building.join(' · ')}  (${building.length}일)`
    : '아직 고른 것 없음';
  $('#patternStep').textContent = `${building.length + 1}일째 근무`;

  badgeRow($('#patternBtns'), PATTERN_CHOICES, (k) => {
    building.push(k);
    // 더 담을 자리가 없으면 거기서 끝낸다
    if (building.length >= MAX_PATTERN) savePattern(); else drawPattern();
  });

  // 이틀 이상 쌓여야 주기가 된다
  $('#patternSave').hidden = building.length < 2;
  $('#patternSave').textContent = `여기까지 — ${building.length}일 주기로 저장`;
  $('#patternUndo').hidden = building.length === 0;
}

function savePattern(list) {
  store.cycle = list || building;
  $('#pattern').close();
  render();
  // 차례가 바뀌면 오늘이 어느 자리인지 다시 정해야 근무가 나온다
  openSettings();
}

/* ── 이벤트 ── */
$('#prev').onclick = () => move(-1);
$('#next').onclick = () => move(1);
$('#today').onclick = () => { const n = new Date(); view = { y: n.getFullYear(), m: n.getMonth() }; picked = null; render(); };
$('#btnSettings').onclick = openSettings;
$('#grid').onclick = (e) => { const c = e.target.closest('.cell'); if (c) openDay(+c.dataset.ts); };
$('[data-set="clear"]').onclick = () => setDay(null);
$('#btnPattern').onclick = openPattern;
$('#patternSave').onclick = () => savePattern();
$('#patternUndo').onclick = () => { building.pop(); drawPattern(); };
$('#patternDefault').onclick = () => savePattern(DEFAULT_CYCLE.slice());
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
