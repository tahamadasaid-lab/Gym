// GymTracker pour iPhone (application web installable).
// Même logique que la version Android : séances, programmes, minuteur de repos, historique,
// progression, poids de corps, catégories, calories, export/import CSV.

import {
  loadState, saveState, flush, getAllPhotos, putPhoto, delPhoto, clearPhotos, defaultState,
  OTHER, DEFAULT_COLOR, DEFAULT_ICON, DEFAULT_MET, DEFAULT_SETTINGS,
} from './store.js';
import * as CSV from './csv.js';
import {
  $, esc, fmtW, fmtNum, fmtVol, fmtKcal, fmtDur, fmtDurHuman, fmtLongDate, fmtMonth, fmtShortDate, fmtTime,
  dayKey, parseNum, e1rm, startOfWeek, isIOS, isStandalone,
} from './util.js';

let S;                       // état persistant
let photos = new Map();      // exerciseId -> data URL
const ui = {
  tab: 'workout',
  stack: [],                 // pages empilées au-dessus de l'onglet
  drafts: {},                // saisie en cours par exercice de séance : { w, r }
  hist: { y: new Date().getFullYear(), m: new Date().getMonth(), day: null },
  prog: { mode: 'ex', exId: null, metric: 'weight' },
  exSearch: '',
};

const MAX_TRACKED_REST = 30 * 60;
const ICONS = {
  dumbbell: '🏋️', rowing: '🚣', run: '🏃', gym: '🤸', mma: '🥊', yoga: '🧘', heart: '❤️', bolt: '⚡',
  fire: '🔥', body: '🧍', hiking: '🥾', bike: '🚴', pool: '🏊', handball: '🤾', star: '⭐', leg: '🦵', arm: '💪',
  boxing: '🥋', target: '🎯', snow: '⛷️',
};
const PALETTE = ['#FF7A45', '#FF5C7A', '#FFC857', '#7CD992', '#4FD8B8', '#4DD0E1', '#4FC3F7', '#7C9CFF',
  '#C792EA', '#F78FB3', '#A1887F', '#9AA8B5'];
const REST_CHOICES = [30, 45, 60, 75, 90, 120, 150, 180, 240, 300];

// ------------------------------------------------------------------ Accès aux données
const newId = () => S.nextId++;
const exById = (id) => S.exercises.find((e) => e.id === id);
const activeSession = () => S.sessions.find((s) => !s.endedAt);
const finishedSessions = () => S.sessions.filter((s) => s.endedAt).sort((a, b) => b.startedAt - a.startedAt);
const catByName = (name) => S.categories.find((c) => c.name.toLowerCase() === String(name || '').toLowerCase());
const sortedCats = () => [...S.categories].sort((a, b) => a.position - b.position);
const sortedSets = (it) => [...it.sets].sort((a, b) => a.at - b.at);

function catStyle(group) {
  const c = catByName(group);
  return { color: c?.color || DEFAULT_COLOR, icon: ICONS[c?.icon] || ICONS[DEFAULT_ICON] };
}

function ensureCategory(name) {
  const clean = String(name || '').trim() || OTHER;
  const existing = catByName(clean);
  if (existing) return existing.name;
  const pos = Math.max(-1, ...S.categories.map((c) => c.position)) + 1;
  S.categories.push({ id: newId(), name: clean, color: DEFAULT_COLOR, icon: DEFAULT_ICON, met: DEFAULT_MET, position: pos });
  return clean;
}

function findOrCreateExercise(name, group) {
  const e = S.exercises.find((x) => x.name.toLowerCase() === name.trim().toLowerCase());
  if (e) return e.id;
  const id = newId();
  S.exercises.push({ id, name: name.trim(), group: ensureCategory(group || OTHER), rest: 90 });
  return id;
}

/** Séries réalisées sur cet exercice lors de la séance précédente. */
function previousSets(exerciseId, excludeSessionId) {
  const list = S.sessions.filter((s) => s.id !== excludeSessionId).sort((a, b) => b.startedAt - a.startedAt);
  for (const s of list) {
    const it = s.items.find((i) => i.exerciseId === exerciseId && i.sets.length);
    if (it) return sortedSets(it);
  }
  return [];
}

// ------------------------------------------------------------------ Calories et statistiques
function bodyWeightKg() {
  const last = [...S.bodyWeights].sort((a, b) => b.date - a.date)[0];
  return last ? last.weight : (S.settings.profileWeight || 75);
}
const met = (group) => catByName(group)?.met ?? DEFAULT_MET;
/** Repos réel mesuré avant la série (plafonné à 10 min), sinon estimation. */
const setSeconds = (st) => (st.rest != null ? Math.min(600, Math.max(0, st.rest)) : st.reps * 4 + 60);
function itemKcal(it) {
  const e = exById(it.exerciseId);
  const secs = it.sets.reduce((a, s) => a + setSeconds(s), 0);
  return met(e?.group) * bodyWeightKg() * secs / 3600;
}
const sessionKcal = (s) => s.items.reduce((a, it) => a + itemKcal(it), 0);
function sessionStats(s) {
  const sets = s.items.flatMap((i) => i.sets);
  const rests = sets.map((x) => x.rest).filter((x) => x != null);
  return {
    sets: sets.length,
    volume: sets.reduce((a, x) => a + x.weight * x.reps, 0),
    avgRest: rests.length ? Math.round(rests.reduce((a, b) => a + b, 0) / rests.length) : null,
    duration: ((s.endedAt || Date.now()) - s.startedAt) / 1000,
    kcal: sessionKcal(s),
    names: s.items.filter((i) => i.sets.length).map((i) => exById(i.exerciseId)?.name).filter(Boolean),
  };
}

/** Performance d'un exercice par séance (ordre chronologique). */
function exerciseHistory(exerciseId) {
  const out = [];
  for (const s of [...S.sessions].sort((a, b) => a.startedAt - b.startedAt)) {
    const sets = s.items.filter((i) => i.exerciseId === exerciseId).flatMap((i) => i.sets);
    if (!sets.length) continue;
    const best = sets.reduce((b, x) => (x.weight > b.weight || (x.weight === b.weight && x.reps > b.reps) ? x : b));
    out.push({
      sessionId: s.id, date: s.startedAt,
      maxWeight: best.weight, bestReps: best.reps,
      oneRm: Math.max(...sets.map((x) => e1rm(x.weight, x.reps))),
      volume: sets.reduce((a, x) => a + x.weight * x.reps, 0),
      maxReps: Math.max(...sets.map((x) => x.reps)),
      setCount: sets.length,
    });
  }
  return out;
}

// ------------------------------------------------------------------ Persistance et rendu
function commit() { saveState(S); render(); }
function save() { saveState(S); }

function go(page) { ui.stack.push(page); render(); window.scrollTo(0, 0); }
function back() { ui.stack.pop(); render(); }

const TAB_ICONS = {
  workout: '<path d="M6.5 6.5v11M17.5 6.5v11M3 9.5v5M21 9.5v5M6.5 12h11"/>',
  history: '<rect x="3.5" y="5" width="17" height="15" rx="2.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  progress: '<path d="M4 19V5M4 19h16M7.5 15l3.5-4 3 2.5L19 8"/>',
  exercises: '<path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"/>',
};
const TABS = [['workout', 'Séance'], ['history', 'Historique'], ['progress', 'Progrès'], ['exercises', 'Exercices']];

function svgIcon(path, size = 24) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${path}</svg>`;
}

function render() {
  applyTheme();
  const page = ui.stack[ui.stack.length - 1];
  const html = page ? PAGES[page.type](page) : ROOTS[ui.tab]();
  $('#main').innerHTML = html;
  $('#tabbar').innerHTML = TABS.map(([id, label]) =>
    `<button class="tab ${ui.tab === id ? 'on' : ''}" data-act="tab" data-tab="${id}">${svgIcon(TAB_ICONS[id])}<span>${label}</span></button>`
  ).join('');
  renderTimer();
  syncWakeLock();
}

function header(title, { backBtn = false, actions = '', sub = '' } = {}) {
  return `<header class="top">
    ${backBtn ? `<button class="icon-btn back" data-act="back" aria-label="Retour">${svgIcon('<path d="M15 18l-6-6 6-6"/>')}</button>` : ''}
    <div class="top-title"><h1>${title}</h1>${sub ? `<div class="top-sub">${sub}</div>` : ''}</div>
    <div class="top-actions">${actions}</div>
  </header>`;
}
const iconBtn = (act, path, label, extra = '') =>
  `<button class="icon-btn" data-act="${act}" ${extra} aria-label="${label}">${svgIcon(path)}</button>`;
const MORE = '<circle cx="5" cy="12" r="1.3"/><circle cx="12" cy="12" r="1.3"/><circle cx="19" cy="12" r="1.3"/>';
const GEAR = '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>';
const PLUS = '<path d="M12 5v14M5 12h14"/>';

function thumb(e, size = 44) {
  const st = catStyle(e?.group);
  const p = e && photos.get(e.id);
  const dim = `width:${size}px;height:${size}px`;
  if (p) return `<img class="thumb" style="${dim}" src="${p}" alt="">`;
  return `<div class="thumb" style="${dim};background:${st.color}26;border-color:${st.color}55"><span style="font-size:${Math.round(size * 0.5)}px">${st.icon}</span></div>`;
}
const chip = (group) => { const st = catStyle(group); return `<span class="chip" style="color:${st.color};background:${st.color}1f">${esc(group)}</span>`; };
const tile = (label, value, accent = '') => `<div class="tile"><div class="tile-v ${accent}">${value}</div><div class="tile-l">${label}</div></div>`;
const empty = (icon, title, text = '') => `<div class="empty"><div class="empty-i">${icon}</div><div class="empty-t">${title}</div>${text ? `<div class="empty-s">${text}</div>` : ''}</div>`;

// ------------------------------------------------------------------ Onglet Séance
function viewWorkout() {
  const s = activeSession();
  return s ? viewActive(s) : viewHome();
}

function viewHome() {
  const weekStart = startOfWeek();
  const week = finishedSessions().filter((s) => s.startedAt >= weekStart);
  const weekKcal = week.reduce((a, s) => a + sessionKcal(s), 0);
  const weekVol = week.reduce((a, s) => a + sessionStats(s).volume, 0);
  const last = finishedSessions()[0];
  const installHint = isIOS() && !isStandalone() && !S.hideInstallHint;

  return `${header('GymTracker', { actions: iconBtn('settings', GEAR, 'Réglages') })}
  <div class="content">
    ${installHint ? `<div class="card hint">
      <div class="hint-t">📲 Installer sur l'iPhone</div>
      <div class="hint-s">Dans Safari, touche <b>Partager</b> <span class="share-i">⬆︎</span> puis <b>« Sur l'écran d'accueil »</b>. L'app s'ouvrira en plein écran et fonctionnera hors ligne.</div>
      <button class="link" data-act="hide-hint">Masquer</button>
    </div>` : ''}
    <div class="card hero">
      <div class="hero-l">Cette semaine</div>
      <div class="tiles">
        ${tile('séances', week.length, 'accent')}
        ${tile('calories', fmtNum(weekKcal), 'accent2')}
        ${tile('volume', fmtVol(weekVol))}
      </div>
    </div>
    <button class="btn primary big" data-act="start-free">${svgIcon(PLUS)} Démarrer une séance libre</button>

    <div class="section-h"><h2>Mes programmes</h2><button class="link" data-act="new-program">+ Nouveau</button></div>
    ${S.programs.length ? S.programs.map((p) => `
      <div class="card program">
        <div class="program-main" data-act="edit-program" data-id="${p.id}">
          <div class="program-name">${esc(p.name)}</div>
          <div class="muted small">${p.items.length} exercice${p.items.length > 1 ? 's' : ''} · ${esc(p.items.map((i) => exById(i.exerciseId)?.name).filter(Boolean).join(' · ') || 'vide')}</div>
        </div>
        <button class="btn primary sm" data-act="start-program" data-id="${p.id}">Démarrer</button>
      </div>`).join('') : `<div class="card muted small">Crée un programme (ex. « Push », « Pull », « Legs ») pour démarrer une séance avec tes exercices déjà prêts.</div>`}

    ${last ? `<div class="section-h"><h2>Dernière séance</h2></div>${sessionCard(last)}` : ''}
  </div>`;
}

function viewActive(s) {
  const st = sessionStats(s);
  const items = s.items;
  return `${header('Séance en cours', {
    sub: `<span id="elapsed">${fmtDur(st.duration)}</span> · ${st.sets} série${st.sets > 1 ? 's' : ''} · ${fmtKcal(st.kcal)}`,
    actions: iconBtn('session-menu', MORE, 'Options'),
  })}
  <div class="content">
    ${items.length ? items.map((it, idx) => itemCard(s, it, idx)).join('') : empty('🏋️', 'Aucun exercice', 'Ajoute ton premier exercice pour commencer.')}
    <button class="btn tonal big" data-act="add-exercise">${svgIcon(PLUS)} Ajouter un exercice</button>
    <button class="btn primary big" data-act="finish">Terminer la séance</button>
  </div>`;
}

function draft(it, s) {
  if (!ui.drafts[it.id]) {
    const sets = sortedSets(it);
    const prev = previousSets(it.exerciseId, s.id);
    const p = sets[sets.length - 1] || prev[0];
    ui.drafts[it.id] = { w: p ? p.weight : 0, r: p ? p.reps : 10 };
  }
  return ui.drafts[it.id];
}

function itemCard(s, it, idx) {
  const e = exById(it.exerciseId);
  if (!e) return '';
  const sets = sortedSets(it);
  const prev = previousSets(it.exerciseId, s.id);
  const d = draft(it, s);
  const target = it.targetSets;
  const pct = target ? Math.min(100, (sets.length / target) * 100) : 0;
  const rows = Math.max(sets.length, Math.min(prev.length, target || prev.length));
  let table = '';
  if (rows) {
    table = `<div class="sets"><div class="set-row head"><span>Série</span><span>Précédent</span><span>kg × reps</span></div>`;
    for (let i = 0; i < rows; i++) {
      const cur = sets[i], p = prev[i];
      table += `<div class="set-row ${cur ? 'done' : 'todo'}" ${cur ? `data-act="edit-set" data-item="${it.id}" data-set="${cur.id}"` : ''}>
        <span class="set-n">${i + 1}</span>
        <span class="muted">${p ? `${fmtW(p.weight)} × ${p.reps}` : '—'}</span>
        <span>${cur ? `<b>${fmtW(cur.weight)}</b> × <b>${cur.reps}</b> <span class="check">✓</span>` : '<span class="muted">—</span>'}</span>
      </div>`;
    }
    table += '</div>';
  }
  const done = target && sets.length >= target;
  return `<div class="card item ${done ? 'complete' : ''}" id="item-${it.id}">
    <div class="item-h">
      ${thumb(e)}
      <div class="item-title">
        <div class="item-name">${esc(e.name)}</div>
        <div class="item-meta">${chip(e.group)}
          <button class="pill" data-act="rest" data-ex="${e.id}">⏱ ${fmtDur(e.rest)}</button>
          ${sets.length ? `<span class="muted small">${fmtKcal(itemKcal(it))}</span>` : ''}
        </div>
      </div>
      ${iconBtn('item-menu', MORE, 'Options', `data-item="${it.id}" data-idx="${idx}"`)}
    </div>
    ${target ? `<div class="target"><div class="bar"><div style="width:${pct}%"></div></div><span class="small ${done ? 'ok' : 'muted'}">${sets.length}/${target} séries</span></div>` : ''}
    ${table}
    <div class="inputs">
      ${stepper(it.id, 'w', d.w, 'kg', 2.5)}
      ${stepper(it.id, 'r', d.r, 'reps', 1)}
    </div>
    <button class="btn ${done ? 'tonal' : 'primary'} full" data-act="log-set" data-item="${it.id}">✓ Valider la série ${sets.length + 1}</button>
  </div>`;
}

function stepper(item, field, value, unit, step) {
  return `<div class="stepper">
    <button class="step" data-act="step" data-item="${item}" data-f="${field}" data-d="${-step}" aria-label="moins">−</button>
    <label class="step-v"><input class="num" inputmode="decimal" data-in="${field}" data-item="${item}" value="${field === 'w' ? fmtW(value) : value}"><span>${unit}</span></label>
    <button class="step" data-act="step" data-item="${item}" data-f="${field}" data-d="${step}" aria-label="plus">+</button>
  </div>`;
}

function nextLabel(s, it) {
  const e = exById(it.exerciseId);
  if (it.targetSets && it.sets.length >= it.targetSets) {
    const idx = s.items.indexOf(it);
    const nxt = s.items.slice(idx + 1).concat(s.items.slice(0, idx))
      .find((x) => !x.targetSets || x.sets.length < x.targetSets);
    if (nxt) return exById(nxt.exerciseId)?.name || e.name;
    return null;
  }
  return e.name;
}

// ------------------------------------------------------------------ Historique
function sessionCard(s) {
  const st = sessionStats(s);
  return `<div class="card session" data-act="open-session" data-id="${s.id}">
    <div class="session-h"><div><div class="session-d">${fmtLongDate(s.startedAt)}</div>
      <div class="muted small">${fmtTime(s.startedAt)} · ${fmtDurHuman(st.duration)}</div></div>
      <div class="kcal">${fmtNum(st.kcal)}<span> kcal</span></div></div>
    <div class="session-s">${st.sets} séries · ${fmtVol(st.volume)}${st.avgRest != null ? ` · repos moy. ${fmtDur(st.avgRest)}` : ''}</div>
    <div class="muted small ellipsis">${esc(st.names.join(' · ') || 'Aucune série')}</div>
  </div>`;
}

function viewHistory() {
  const all = finishedSessions();
  const { y, m, day } = ui.hist;
  const days = new Set(all.map((s) => dayKey(s.startedAt)));
  const first = new Date(y, m, 1);
  const offset = (first.getDay() + 6) % 7;
  const n = new Date(y, m + 1, 0).getDate();
  const today = dayKey(Date.now());
  let cells = '';
  for (let i = 0; i < offset; i++) cells += '<span></span>';
  for (let d = 1; d <= n; d++) {
    const k = `${y}-${m}-${d}`;
    cells += `<button class="day ${days.has(k) ? 'trained' : ''} ${k === day ? 'sel' : ''} ${k === today ? 'today' : ''}" data-act="day" data-k="${k}">${d}</button>`;
  }
  const monthCount = all.filter((s) => { const d = new Date(s.startedAt); return d.getFullYear() === y && d.getMonth() === m; }).length;
  const list = day ? all.filter((s) => dayKey(s.startedAt) === day) : all;
  return `${header('Historique', { actions: iconBtn('data-menu', MORE, 'Export / import') })}
  <div class="content">
    <div class="card cal">
      <div class="cal-h">
        <button class="icon-btn" data-act="month" data-d="-1">${svgIcon('<path d="M15 18l-6-6 6-6"/>')}</button>
        <div><b>${fmtMonth(first)}</b><div class="muted small">${monthCount} séance${monthCount > 1 ? 's' : ''}</div></div>
        <button class="icon-btn" data-act="month" data-d="1">${svgIcon('<path d="M9 18l6-6-6-6"/>')}</button>
      </div>
      <div class="cal-g">${['L', 'M', 'M', 'J', 'V', 'S', 'D'].map((x) => `<span class="dow">${x}</span>`).join('')}${cells}</div>
    </div>
    ${day ? `<div class="section-h"><h2>${fmtLongDate(list[0]?.startedAt || dateFromKey(day))}</h2><button class="link" data-act="day" data-k="${day}">Tout afficher</button></div>` : ''}
    ${list.length ? list.map(sessionCard).join('') : empty('📅', day ? 'Pas de séance ce jour-là' : 'Aucune séance terminée', day ? '' : 'Tes séances apparaîtront ici.')}
  </div>`;
}
function dateFromKey(k) { const [y, m, d] = k.split('-').map(Number); return new Date(y, m, d).getTime(); }

function viewSession(page) {
  const s = S.sessions.find((x) => x.id === page.id);
  if (!s) { ui.stack.pop(); return ROOTS[ui.tab](); }
  const st = sessionStats(s);
  return `${header(fmtLongDate(s.startedAt), { backBtn: true, sub: `${fmtTime(s.startedAt)} – ${s.endedAt ? fmtTime(s.endedAt) : 'en cours'}`,
    actions: iconBtn('delete-session', '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>', 'Supprimer', `data-id="${s.id}"`) })}
  <div class="content">
    <div class="tiles card">
      ${tile('durée', fmtDurHuman(st.duration))}
      ${tile('séries', st.sets)}
      ${tile('volume', fmtVol(st.volume))}
      ${tile('calories', fmtNum(st.kcal), 'accent2')}
      ${tile('repos moyen', st.avgRest != null ? fmtDur(st.avgRest) : '—')}
    </div>
    ${s.items.map((it) => {
      const e = exById(it.exerciseId);
      if (!e) return '';
      const sets = sortedSets(it);
      return `<div class="card">
        <div class="item-h">${thumb(e, 36)}<div class="item-title"><div class="item-name">${esc(e.name)}</div>
          <div class="muted small">${sets.length} séries · ${fmtVol(sets.reduce((a, x) => a + x.weight * x.reps, 0))} · ${fmtKcal(itemKcal(it))}</div></div></div>
        <div class="sets">${sets.map((x, i) => `<div class="set-row done" data-act="edit-set" data-session="${s.id}" data-item="${it.id}" data-set="${x.id}">
          <span class="set-n">${i + 1}</span><span><b>${fmtW(x.weight)} kg</b> × <b>${x.reps}</b></span>
          <span class="muted small">1RM ${fmtW(Math.round(e1rm(x.weight, x.reps) * 10) / 10)}${x.rest != null ? ` · repos ${fmtDur(x.rest)}` : ''}</span></div>`).join('')}</div>
        <button class="link" data-act="add-set-after" data-session="${s.id}" data-item="${it.id}">+ Ajouter une série</button>
      </div>`;
    }).join('')}
  </div>`;
}

// ------------------------------------------------------------------ Progression
function chart(points, color, fmtY) {
  if (points.length < 2) return `<div class="chart-empty muted small">Il faut au moins 2 séances pour tracer la courbe.</div>`;
  const W = 340, H = 180, pl = 44, pr = 12, pt = 14, pb = 26;
  const ys = points.map((p) => p.y);
  let lo = Math.min(...ys), hi = Math.max(...ys);
  if (lo === hi) { lo -= 1; hi += 1; }
  const padY = (hi - lo) * 0.12; lo -= padY; hi += padY;
  const x = (i) => pl + (i * (W - pl - pr)) / (points.length - 1);
  const y = (v) => pt + (1 - (v - lo) / (hi - lo)) * (H - pt - pb);
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.y).toFixed(1)}`).join(' ');
  const area = `${line} L${x(points.length - 1).toFixed(1)},${H - pb} L${pl},${H - pb} Z`;
  let grid = '';
  for (let k = 0; k <= 3; k++) {
    const v = lo + ((hi - lo) * k) / 3, yy = y(v);
    grid += `<line x1="${pl}" x2="${W - pr}" y1="${yy}" y2="${yy}" class="grid"/><text x="${pl - 6}" y="${yy + 4}" text-anchor="end" class="axis">${fmtY(v)}</text>`;
  }
  const dots = points.map((p, i) => `<circle cx="${x(i)}" cy="${y(p.y)}" r="${points.length > 30 ? 2 : 3.5}" fill="${color}"/>`).join('');
  return `<svg class="chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">
    <defs><linearGradient id="cg" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="${color}" stop-opacity=".35"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></linearGradient></defs>
    ${grid}<path d="${area}" fill="url(#cg)"/><path d="${line}" fill="none" stroke="${color}" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"/>${dots}
    <text x="${pl}" y="${H - 6}" class="axis">${fmtShortDate(points[0].x)}</text>
    <text x="${W - pr}" y="${H - 6}" text-anchor="end" class="axis">${fmtShortDate(points[points.length - 1].x)}</text>
  </svg>`;
}

const METRICS = {
  weight: ['Poids max', (h) => h.maxWeight, (v) => fmtW(Math.round(v * 10) / 10)],
  orm: ['1RM estimé', (h) => h.oneRm, (v) => fmtW(Math.round(v))],
  volume: ['Volume', (h) => h.volume, (v) => fmtNum(v)],
  reps: ['Reps max', (h) => h.maxReps, (v) => fmtNum(v)],
};

function viewProgress() {
  const seg = `<div class="seg">
    <button class="${ui.prog.mode === 'ex' ? 'on' : ''}" data-act="prog-mode" data-m="ex">Exercices</button>
    <button class="${ui.prog.mode === 'body' ? 'on' : ''}" data-act="prog-mode" data-m="body">Poids de corps</button></div>`;
  return `${header('Progression')}<div class="content">${seg}${ui.prog.mode === 'ex' ? progressExercises() : progressBody()}</div>`;
}

function progressExercises() {
  const trained = new Set(S.sessions.flatMap((s) => s.items.filter((i) => i.sets.length).map((i) => i.exerciseId)));
  const list = S.exercises.filter((e) => trained.has(e.id)).sort((a, b) => a.name.localeCompare(b.name, 'fr'));
  if (!list.length) return empty('📈', 'Pas encore de données', 'Termine quelques séries pour voir ta progression.');
  if (!trained.has(ui.prog.exId)) ui.prog.exId = list[0].id;
  const e = exById(ui.prog.exId);
  const h = exerciseHistory(e.id);
  const record = h.reduce((b, x) => (x.maxWeight > b.maxWeight || (x.maxWeight === b.maxWeight && x.bestReps > b.bestReps) ? x : b), h[0]);
  const [label, get, fmt] = METRICS[ui.prog.metric];
  const color = catStyle(e.group).color;
  return `<label class="select">${thumb(e, 32)}<select data-change="prog-ex">${list.map((x) =>
      `<option value="${x.id}" ${x.id === e.id ? 'selected' : ''}>${esc(x.name)}</option>`).join('')}</select><span class="chev">▾</span></label>
    <div class="tiles card">
      ${tile('record', `${fmtW(record.maxWeight)} kg <small>× ${record.bestReps}</small>`, 'accent')}
      ${tile('1RM estimé', `${fmtW(Math.round(Math.max(...h.map((x) => x.oneRm))))} kg`)}
      ${tile('meilleur volume', fmtVol(Math.max(...h.map((x) => x.volume))))}
      ${tile('séances', h.length)}
    </div>
    <div class="card">
      <div class="chips">${Object.entries(METRICS).map(([k, [l]]) =>
        `<button class="fchip ${k === ui.prog.metric ? 'on' : ''}" data-act="metric" data-k="${k}">${l}</button>`).join('')}</div>
      ${chart(h.map((x) => ({ x: x.date, y: get(x) })), color, fmt)}
    </div>
    <div class="section-h"><h2>Par séance</h2></div>
    <div class="card list">${[...h].reverse().map((x) => `<div class="row" data-act="open-session" data-id="${x.sessionId}">
      <div><b>${fmtLongDate(x.date)}</b><div class="muted small">${x.setCount} séries · volume ${fmtVol(x.volume)}</div></div>
      <div class="right"><b>${fmtW(x.maxWeight)} × ${x.bestReps}</b><div class="muted small">1RM ${fmtW(Math.round(x.oneRm))}</div></div></div>`).join('')}</div>`;
}

function progressBody() {
  const list = [...S.bodyWeights].sort((a, b) => a.date - b.date);
  const last = list[list.length - 1];
  const diff = list.length > 1 ? last.weight - list[0].weight : null;
  return `<div class="card">
      <div class="row-input">
        <label class="field grow"><span>Nouvelle pesée (kg)</span><input id="bw" inputmode="decimal" placeholder="${last ? fmtW(last.weight) : '75'}"></label>
        <button class="btn primary" data-act="add-bw">Ajouter</button>
      </div>
    </div>
    ${list.length ? `<div class="tiles card">
      ${tile('actuel', `${fmtW(last.weight)} kg`, 'accent')}
      ${tile('évolution', diff == null ? '—' : `${diff > 0 ? '+' : ''}${fmtW(Math.round(diff * 10) / 10)} kg`)}
      ${tile('pesées', list.length)}
    </div>
    <div class="card">${chart(list.map((x) => ({ x: x.date, y: x.weight })), '#4FD8B8', (v) => fmtW(Math.round(v * 10) / 10))}</div>
    <div class="card list">${[...list].reverse().map((x) => `<div class="row">
      <div><b>${fmtW(x.weight)} kg</b><div class="muted small">${fmtLongDate(x.date)} · ${fmtTime(x.date)}</div></div>
      ${iconBtn('del-bw', '<path d="M6 6l12 12M18 6L6 18"/>', 'Supprimer', `data-id="${x.id}"`)}</div>`).join('')}</div>`
    : empty('⚖️', 'Aucune pesée', 'Ton poids sert aussi à estimer les calories dépensées.')}`;
}

// ------------------------------------------------------------------ Exercices et catégories
function viewExercises() {
  return `${header('Exercices', { actions: `<button class="btn tonal sm" data-act="categories">Catégories</button>${iconBtn('new-exercise', PLUS, 'Nouvel exercice')}` })}
  <div class="content">
    <label class="search"><span>🔍</span><input id="ex-search" placeholder="Rechercher" value="${esc(ui.exSearch)}" autocomplete="off"></label>
    <div id="ex-list">${exerciseList(ui.exSearch, 'edit-exercise')}</div>
  </div>`;
}

function exerciseList(q, act) {
  const query = q.trim().toLowerCase();
  const list = S.exercises.filter((e) => !query || e.name.toLowerCase().includes(query) || e.group.toLowerCase().includes(query));
  if (!list.length) return query ? `<div class="muted small pad">Aucun exercice ne correspond.</div>` : empty('🏋️', 'Aucun exercice');
  const groups = new Map();
  const order = sortedCats().map((c) => c.name);
  for (const e of list) { if (!groups.has(e.group)) groups.set(e.group, []); groups.get(e.group).push(e); }
  const keys = [...groups.keys()].sort((a, b) => {
    const ia = order.indexOf(a), ib = order.indexOf(b);
    return (ia < 0 ? 999 : ia) - (ib < 0 ? 999 : ib) || a.localeCompare(b, 'fr');
  });
  return keys.map((g) => `<div class="group-h" style="color:${catStyle(g).color}">${catStyle(g).icon} ${esc(g)}</div>
    <div class="card list">${groups.get(g).sort((a, b) => a.name.localeCompare(b.name, 'fr')).map((e) => `
      <div class="row" data-act="${act}" data-id="${e.id}">${thumb(e, 40)}<div class="grow"><b>${esc(e.name)}</b><div class="muted small">Repos ${fmtDur(e.rest)}</div></div><span class="chev">›</span></div>`).join('')}</div>`).join('');
}

function viewCategories() {
  return `${header('Catégories', { backBtn: true, actions: iconBtn('new-category', PLUS, 'Nouvelle catégorie') })}
  <div class="content">
    <div class="muted small pad">L'intensité (MET) sert à estimer les calories : environ 3–4 pour des bras/abdos, 5–6 pour des gros groupes musculaires, 8 pour du cardio.</div>
    <div class="card list">${sortedCats().map((c) => {
      const n = S.exercises.filter((e) => e.group.toLowerCase() === c.name.toLowerCase()).length;
      return `<div class="row" data-act="edit-category" data-id="${c.id}">
        <div class="thumb" style="width:40px;height:40px;background:${c.color}26;border-color:${c.color}55"><span style="font-size:20px">${ICONS[c.icon] || ICONS.bolt}</span></div>
        <div class="grow"><b style="color:${c.color}">${esc(c.name)}</b><div class="muted small">${n} exercice${n > 1 ? 's' : ''} · MET ${fmtW(c.met)}</div></div><span class="chev">›</span></div>`;
    }).join('')}</div>
  </div>`;
}

// ------------------------------------------------------------------ Programmes
function viewProgram(page) {
  const d = page.draft;
  return `${header(page.id ? 'Modifier le programme' : 'Nouveau programme', { backBtn: true,
    actions: page.id ? iconBtn('delete-program', '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>', 'Supprimer') : '' })}
  <div class="content">
    <label class="field"><span>Nom</span><input id="pg-name" value="${esc(d.name)}" placeholder="ex. Push, Pull, Legs…"></label>
    ${d.items.length ? `<div class="card list">${d.items.map((it, i) => {
      const e = exById(it.exerciseId);
      return `<div class="row">
        <div class="reorder"><button class="mini" data-act="pg-move" data-i="${i}" data-d="-1" ${i === 0 ? 'disabled' : ''}>▲</button><button class="mini" data-act="pg-move" data-i="${i}" data-d="1" ${i === d.items.length - 1 ? 'disabled' : ''}>▼</button></div>
        <div class="grow"><b>${esc(e?.name || '?')}</b><div class="muted small">${esc(e?.group || '')}</div></div>
        <div class="mini-stepper"><button data-act="pg-sets" data-i="${i}" data-d="-1">−</button><span>${it.targetSets} séries</span><button data-act="pg-sets" data-i="${i}" data-d="1">+</button></div>
        ${iconBtn('pg-remove', '<path d="M6 6l12 12M18 6L6 18"/>', 'Retirer', `data-i="${i}"`)}
      </div>`;
    }).join('')}</div>` : `<div class="card muted small">Ajoute les exercices du programme et le nombre de séries visées.</div>`}
    <button class="btn tonal big" data-act="pg-add">${svgIcon(PLUS)} Ajouter un exercice</button>
    <button class="btn primary big" data-act="pg-save">Enregistrer</button>
  </div>`;
}

// ------------------------------------------------------------------ Réglages
function toggle(key, label, desc = '') {
  return `<label class="row toggle"><div class="grow"><b>${label}</b>${desc ? `<div class="muted small">${desc}</div>` : ''}</div>
    <input type="checkbox" data-setting="${key}" ${S.settings[key] ? 'checked' : ''}><span class="switch"></span></label>`;
}

function viewSettings() {
  const st = S.settings;
  return `${header('Réglages', { backBtn: true })}
  <div class="content">
    <div class="section-h"><h2>Apparence</h2></div>
    <div class="card">
      <div class="seg">${[['system', 'Système'], ['light', 'Clair'], ['dark', 'Sombre']].map(([k, l]) =>
        `<button class="${st.theme === k ? 'on' : ''}" data-act="theme" data-k="${k}">${l}</button>`).join('')}</div>
      ${toggle('pureBlack', 'Noir intégral', 'Fond noir en thème sombre (économise la batterie sur écran OLED).')}
    </div>

    <div class="section-h"><h2>Fin du repos</h2></div>
    <div class="card list">
      ${toggle('sound', 'Bips sonores')}
      ${toggle('voice', 'Annonce vocale', '« Repos terminé. Série suivante : Squat »')}
      ${toggle('warning', 'Signal 10 secondes avant la fin')}
      ${toggle('keepAwake', 'Garder l\'écran allumé pendant la séance', 'Recommandé sur iPhone : une app web ne peut pas sonner quand l\'écran est verrouillé.')}
      <div class="row"><button class="btn tonal full" data-act="test-alert">🔔 Tester l'alerte</button></div>
    </div>
    <div class="muted small pad">Astuce : si l'interrupteur « silencieux » de l'iPhone est activé, les bips peuvent être coupés. L'annonce vocale et les bips passent dans les écouteurs s'ils sont connectés.</div>

    <div class="section-h"><h2>Calories</h2></div>
    <div class="card">
      <label class="field"><span>Ton poids (kg) — utilisé si aucune pesée n'est enregistrée</span>
        <input id="profile-weight" inputmode="decimal" value="${fmtW(st.profileWeight)}"></label>
      <div class="muted small">Calcul : intensité (MET) de la catégorie × poids × temps passé (séries + repos). C'est une estimation.</div>
    </div>

    <div class="section-h"><h2>Données</h2></div>
    <div class="card list">
      <div class="row" data-act="export-csv"><div class="grow"><b>Exporter en CSV</b><div class="muted small">Séries et pesées · lisible dans Excel / Numbers</div></div><span class="chev">›</span></div>
      <div class="row" data-act="import-csv"><div class="grow"><b>Importer un CSV</b><div class="muted small">Compatible avec l'export de la version Android</div></div><span class="chev">›</span></div>
      <div class="row" data-act="export-json"><div class="grow"><b>Sauvegarde complète</b><div class="muted small">Tout : programmes, catégories, photos, réglages</div></div><span class="chev">›</span></div>
      <div class="row" data-act="import-json"><div class="grow"><b>Restaurer une sauvegarde</b><div class="muted small">Remplace toutes les données actuelles</div></div><span class="chev">›</span></div>
    </div>
    <div class="muted small pad">Les données restent uniquement sur ce téléphone. Fais une sauvegarde de temps en temps (dans Fichiers ou iCloud Drive) : supprimer l'app de l'écran d'accueil efface ses données.</div>
    <div class="muted small pad center">GymTracker · version web 1.0</div>
  </div>`;
}

const ROOTS = { workout: viewWorkout, history: viewHistory, progress: viewProgress, exercises: viewExercises };
const PAGES = { session: viewSession, categories: viewCategories, program: viewProgram, settings: viewSettings };

// ------------------------------------------------------------------ Feuilles (dialogues)
let sheetEl = null;
function openSheet(html, { onClose } = {}) {
  closeSheet(true);
  const back = document.createElement('div');
  back.className = 'backdrop';
  back.innerHTML = `<div class="sheet" role="dialog"><div class="grabber"></div>${html}</div>`;
  back.addEventListener('click', (e) => { if (e.target === back) closeSheet(); });
  document.body.appendChild(back);
  sheetEl = back;
  sheetEl._onClose = onClose;
  requestAnimationFrame(() => back.classList.add('open'));
  return back;
}
function closeSheet(immediate = false) {
  if (!sheetEl) return;
  const el = sheetEl;
  sheetEl = null;
  el._onClose?.();
  if (immediate) { el.remove(); return; }
  el.classList.remove('open');
  setTimeout(() => el.remove(), 250);
}

function confirmSheet(title, text, okLabel = 'Confirmer', danger = true) {
  return new Promise((resolve) => {
    let answered = false;
    const el = openSheet(`<h3>${title}</h3>${text ? `<p class="muted">${text}</p>` : ''}
      <div class="sheet-actions"><button class="btn tonal" data-x="no">Annuler</button><button class="btn ${danger ? 'danger' : 'primary'}" data-x="ok">${okLabel}</button></div>`,
    { onClose: () => { if (!answered) resolve(false); } });
    el.querySelectorAll('[data-x]').forEach((b) => b.addEventListener('click', () => {
      answered = true; resolve(b.dataset.x === 'ok'); closeSheet();
    }));
  });
}

function menuSheet(title, entries) {
  const el = openSheet(`${title ? `<h3>${title}</h3>` : ''}<div class="menu">${entries.map((e, i) =>
    `<button class="menu-i ${e.danger ? 'danger-t' : ''}" data-i="${i}">${e.label}</button>`).join('')}</div>
    <button class="btn tonal full" data-i="-1">Annuler</button>`);
  el.querySelectorAll('[data-i]').forEach((b) => b.addEventListener('click', () => {
    closeSheet();
    const e = entries[+b.dataset.i];
    if (e) setTimeout(e.run, 50);
  }));
}

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toast._t);
  toast._t = setTimeout(() => t.classList.remove('show'), 2600);
}

/** Sélecteur d'exercice (recherche + création). */
function pickExercise(onPick, title = 'Ajouter un exercice') {
  const el = openSheet(`<h3>${title}</h3>
    <label class="search"><span>🔍</span><input id="pick-q" placeholder="Rechercher ou créer" autocomplete="off"></label>
    <div id="pick-list" class="pick-list"></div>`);
  const q = el.querySelector('#pick-q');
  const draw = () => {
    const v = q.value.trim();
    const exact = S.exercises.some((e) => e.name.toLowerCase() === v.toLowerCase());
    el.querySelector('#pick-list').innerHTML =
      (v && !exact ? `<button class="btn tonal full create" data-new="1">+ Créer « ${esc(v)} »</button>` : '') +
      exerciseList(v, 'pick');
  };
  draw();
  q.addEventListener('input', draw);
  el.querySelector('#pick-list').addEventListener('click', (e) => {
    const row = e.target.closest('[data-act="pick"]');
    if (row) { e.stopPropagation(); closeSheet(); onPick(+row.dataset.id); return; }
    if (e.target.closest('[data-new]')) {
      e.stopPropagation();
      editExercise(null, q.value.trim(), (id) => onPick(id));
    }
  });
}

function numberSheet(title, fields, onSave, extra = '') {
  const el = openSheet(`<h3>${title}</h3>${fields.map((f) =>
    `<label class="field"><span>${f.label}</span><input id="f-${f.id}" inputmode="${f.mode || 'decimal'}" value="${esc(f.value)}"></label>`).join('')}
    <div class="sheet-actions">${extra}<button class="btn primary" data-x="save">Enregistrer</button></div>`);
  el.querySelector('[data-x="save"]').addEventListener('click', () => {
    const vals = {};
    for (const f of fields) vals[f.id] = parseNum(el.querySelector(`#f-${f.id}`).value);
    if (onSave(vals) !== false) closeSheet();
  });
  return el;
}

// ------------------------------------------------------------------ Séances : actions
function startSession(programId) {
  const now = Date.now();
  const s = { id: newId(), startedAt: now, endedAt: null, items: [] };
  const p = programId && S.programs.find((x) => x.id === programId);
  if (p) for (const it of p.items) if (exById(it.exerciseId)) s.items.push({ id: newId(), exerciseId: it.exerciseId, targetSets: it.targetSets, sets: [] });
  S.sessions.push(s);
  ui.drafts = {};
  unlockAudio();
  commit();
  window.scrollTo(0, 0);
}

function logSet(itemId) {
  const s = activeSession();
  const it = s?.items.find((i) => i.id === itemId);
  if (!it) return;
  syncDraftFromInputs(itemId);
  const d = draft(it, s);
  if (!(d.r > 0)) { toast('Indique un nombre de répétitions'); return; }
  const now = Date.now();
  const lastAt = Math.max(0, ...s.items.flatMap((i) => i.sets.map((x) => x.at)));
  let rest = lastAt ? Math.round((now - lastAt) / 1000) : null;
  if (rest != null && (rest < 0 || rest > MAX_TRACKED_REST)) rest = null;
  it.sets.push({ id: newId(), reps: Math.round(d.r), weight: Math.max(0, d.w), at: now, rest });
  const e = exById(it.exerciseId);
  startTimer(e.rest, e.name, nextLabel(s, it));
  unlockAudio();
  commit();
}

function syncDraftFromInputs(itemId) {
  document.querySelectorAll(`input[data-item="${itemId}"]`).forEach((inp) => {
    const v = parseNum(inp.value);
    if (v != null && ui.drafts[itemId]) ui.drafts[itemId][inp.dataset.in] = v;
  });
}

function findSet(sessionId, itemId, setId) {
  const s = sessionId ? S.sessions.find((x) => x.id === sessionId) : activeSession();
  const it = s?.items.find((i) => i.id === itemId);
  const st = it?.sets.find((x) => x.id === setId);
  return { s, it, st };
}

function editSet(sessionId, itemId, setId) {
  const { it, st } = findSet(sessionId, itemId, setId);
  if (!st) return;
  const el = numberSheet(`Modifier la série ${sortedSets(it).indexOf(st) + 1}`, [
    { id: 'w', label: 'Poids (kg)', value: fmtW(st.weight) },
    { id: 'r', label: 'Répétitions', value: st.reps, mode: 'numeric' },
  ], ({ w, r }) => {
    if (!(r > 0)) { toast('Nombre de répétitions invalide'); return false; }
    st.weight = Math.max(0, w ?? 0); st.reps = Math.round(r);
    commit();
  }, '<button class="btn danger" data-x="del">Supprimer</button>');
  el.querySelector('[data-x="del"]').addEventListener('click', () => {
    it.sets = it.sets.filter((x) => x.id !== st.id);
    closeSheet(); commit(); toast('Série supprimée');
  });
}

function addSetAfterwards(sessionId, itemId) {
  const s = S.sessions.find((x) => x.id === sessionId);
  const it = s?.items.find((i) => i.id === itemId);
  if (!it) return;
  const last = sortedSets(it).pop();
  numberSheet('Ajouter une série', [
    { id: 'w', label: 'Poids (kg)', value: last ? fmtW(last.weight) : '' },
    { id: 'r', label: 'Répétitions', value: last ? last.reps : '', mode: 'numeric' },
  ], ({ w, r }) => {
    if (!(r > 0)) { toast('Nombre de répétitions invalide'); return false; }
    const base = last ? last.at : s.startedAt;
    it.sets.push({ id: newId(), reps: Math.round(r), weight: Math.max(0, w ?? 0), at: base + 1000, rest: null });
    commit();
  });
}

function chooseRest(exId) {
  const e = exById(exId);
  const el = openSheet(`<h3>Repos — ${esc(e.name)}</h3>
    <div class="rest-grid">${REST_CHOICES.map((s) => `<button class="fchip ${s === e.rest ? 'on' : ''}" data-s="${s}">${fmtDur(s)}</button>`).join('')}</div>
    <label class="field"><span>Autre durée (secondes)</span><input id="rest-custom" inputmode="numeric" value="${e.rest}"></label>
    <div class="sheet-actions"><button class="btn primary" data-x="save">Enregistrer</button></div>`);
  const set = (v) => { if (v > 0) { e.rest = Math.round(v); closeSheet(); commit(); } };
  el.querySelectorAll('[data-s]').forEach((b) => b.addEventListener('click', () => set(+b.dataset.s)));
  el.querySelector('[data-x="save"]').addEventListener('click', () => set(parseNum(el.querySelector('#rest-custom').value)));
}

async function finishSession() {
  const s = activeSession();
  if (!s) return;
  const n = s.items.reduce((a, i) => a + i.sets.length, 0);
  if (!n) {
    if (!(await confirmSheet('Séance vide', 'Aucune série enregistrée : la séance sera supprimée.', 'Supprimer'))) return;
    S.sessions = S.sessions.filter((x) => x !== s);
  } else {
    if (!(await confirmSheet('Terminer la séance ?', `${n} série${n > 1 ? 's' : ''} · ${fmtKcal(sessionKcal(s))}`, 'Terminer', false))) return;
    s.endedAt = Date.now();
    toast('Séance enregistrée 💪');
  }
  S.timer = null;
  ui.drafts = {};
  commit();
}

function saveSessionAsProgram() {
  const s = activeSession();
  if (!s?.items.length) { toast('Ajoute d\'abord des exercices'); return; }
  const el = openSheet(`<h3>Enregistrer comme programme</h3>
    <label class="field"><span>Nom du programme</span><input id="np-name" placeholder="ex. Push"></label>
    <div class="sheet-actions"><button class="btn primary" data-x="save">Enregistrer</button></div>`);
  el.querySelector('[data-x="save"]').addEventListener('click', () => {
    const name = el.querySelector('#np-name').value.trim();
    if (!name) { toast('Donne un nom au programme'); return; }
    S.programs.push({ id: newId(), name, items: s.items.map((i) => ({ exerciseId: i.exerciseId, targetSets: i.sets.length || i.targetSets || 3 })) });
    closeSheet(); commit(); toast('Programme enregistré');
  });
}

// ------------------------------------------------------------------ Exercices : actions
function editExercise(id, presetName = '', onCreated) {
  const e = id ? exById(id) : null;
  const cats = sortedCats();
  const group = e?.group || (ui.lastGroup && catByName(ui.lastGroup) ? ui.lastGroup : cats[0]?.name || OTHER);
  let photo = e ? photos.get(e.id) || null : null;
  const el = openSheet(`<h3>${e ? 'Modifier l\'exercice' : 'Nouvel exercice'}</h3>
    <div class="photo-row"><div id="ph-prev"></div>
      <div class="photo-btns"><button class="btn tonal sm" data-x="photo">📷 Photo</button>
      <button class="btn tonal sm" data-x="nophoto" ${photo ? '' : 'hidden'}>Retirer</button></div></div>
    <label class="field"><span>Nom</span><input id="ex-name" value="${esc(e?.name || presetName)}" placeholder="ex. Hip thrust"></label>
    <label class="field"><span>Catégorie</span><select id="ex-group">${cats.map((c) =>
      `<option ${c.name === group ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}</select></label>
    <label class="field"><span>Repos par défaut (secondes)</span><input id="ex-rest" inputmode="numeric" value="${e?.rest ?? 90}"></label>
    <div class="sheet-actions">${e ? '<button class="btn danger" data-x="del">Supprimer</button>' : ''}<button class="btn primary" data-x="save">Enregistrer</button></div>`);
  const prev = () => {
    const fake = { id: -1, group: el.querySelector('#ex-group').value };
    el.querySelector('#ph-prev').innerHTML = photo ? `<img class="thumb" style="width:72px;height:72px" src="${photo}" alt="">` : thumb(fake, 72);
    el.querySelector('[data-x="nophoto"]').hidden = !photo;
  };
  prev();
  el.querySelector('#ex-group').addEventListener('change', prev);
  el.querySelector('[data-x="photo"]').addEventListener('click', async () => {
    const f = await pickFile('image/*');
    if (!f) return;
    try { photo = await resizeImage(f, 640); prev(); } catch { toast('Image illisible'); }
  });
  el.querySelector('[data-x="nophoto"]').addEventListener('click', () => { photo = null; prev(); });
  el.querySelector('[data-x="save"]').addEventListener('click', async () => {
    const name = el.querySelector('#ex-name').value.trim();
    if (!name) { toast('Donne un nom à l\'exercice'); return; }
    if (S.exercises.some((x) => x.name.toLowerCase() === name.toLowerCase() && x.id !== e?.id)) { toast('Cet exercice existe déjà'); return; }
    const g = el.querySelector('#ex-group').value;
    ui.lastGroup = g;
    const rest = Math.max(5, Math.round(parseNum(el.querySelector('#ex-rest').value) || 90));
    let target = e;
    if (target) Object.assign(target, { name, group: g, rest });
    else { target = { id: newId(), name, group: g, rest }; S.exercises.push(target); }
    if (photo) { photos.set(target.id, photo); putPhoto(target.id, photo); }
    else if (photos.has(target.id)) { photos.delete(target.id); delPhoto(target.id); }
    closeSheet(); commit();
    onCreated?.(target.id);
  });
  el.querySelector('[data-x="del"]')?.addEventListener('click', async () => {
    const used = S.sessions.some((s) => s.items.some((i) => i.exerciseId === e.id && i.sets.length));
    if (!(await confirmSheet(`Supprimer « ${esc(e.name)} » ?`, used ? 'Son historique (séries de toutes les séances) sera aussi supprimé.' : '', 'Supprimer'))) return;
    S.exercises = S.exercises.filter((x) => x.id !== e.id);
    S.sessions.forEach((s) => { s.items = s.items.filter((i) => i.exerciseId !== e.id); });
    S.programs.forEach((p) => { p.items = p.items.filter((i) => i.exerciseId !== e.id); });
    if (photos.has(e.id)) { photos.delete(e.id); delPhoto(e.id); }
    commit();
  });
}

function editCategory(id) {
  const c = id ? S.categories.find((x) => x.id === id) : null;
  let color = c?.color || PALETTE[0];
  let icon = c?.icon || 'dumbbell';
  const isOther = c && c.name.toLowerCase() === OTHER.toLowerCase();
  const el = openSheet(`<h3>${c ? 'Modifier la catégorie' : 'Nouvelle catégorie'}</h3>
    <label class="field"><span>Nom</span><input id="c-name" value="${esc(c?.name || '')}" placeholder="ex. Fessiers" ${isOther ? 'readonly' : ''}></label>
    <div class="field"><span>Couleur</span><div class="swatches">${PALETTE.map((p) => `<button class="sw" data-c="${p}" style="background:${p}"></button>`).join('')}</div></div>
    <div class="field"><span>Pictogramme</span><div class="icons">${Object.entries(ICONS).map(([k, v]) => `<button class="ic" data-ic="${k}">${v}</button>`).join('')}</div></div>
    <label class="field"><span>Intensité (MET)</span><input id="c-met" inputmode="decimal" value="${fmtW(c?.met ?? DEFAULT_MET)}"></label>
    <div class="sheet-actions">${c && !isOther ? '<button class="btn danger" data-x="del">Supprimer</button>' : ''}<button class="btn primary" data-x="save">Enregistrer</button></div>`);
  const sync = () => {
    el.querySelectorAll('.sw').forEach((b) => b.classList.toggle('on', b.dataset.c === color));
    el.querySelectorAll('.ic').forEach((b) => { b.classList.toggle('on', b.dataset.ic === icon); b.style.background = b.dataset.ic === icon ? color + '33' : ''; });
  };
  sync();
  el.querySelectorAll('.sw').forEach((b) => b.addEventListener('click', () => { color = b.dataset.c; sync(); }));
  el.querySelectorAll('.ic').forEach((b) => b.addEventListener('click', () => { icon = b.dataset.ic; sync(); }));
  el.querySelector('[data-x="save"]').addEventListener('click', () => {
    const name = el.querySelector('#c-name').value.trim();
    if (!name) { toast('Donne un nom à la catégorie'); return; }
    const dup = catByName(name);
    if (dup && dup.id !== c?.id) { toast('Cette catégorie existe déjà'); return; }
    const m = parseNum(el.querySelector('#c-met').value);
    const metV = m && m > 0 ? m : DEFAULT_MET;
    if (c) {
      if (c.name !== name) S.exercises.forEach((e) => { if (e.group.toLowerCase() === c.name.toLowerCase()) e.group = name; });
      Object.assign(c, { name, color, icon, met: metV });
    } else {
      S.categories.push({ id: newId(), name, color, icon, met: metV, position: Math.max(-1, ...S.categories.map((x) => x.position)) + 1 });
    }
    closeSheet(); commit();
  });
  el.querySelector('[data-x="del"]')?.addEventListener('click', async () => {
    if (!(await confirmSheet(`Supprimer « ${esc(c.name)} » ?`, `Ses exercices seront déplacés dans « ${OTHER} ».`, 'Supprimer'))) return;
    const other = ensureCategory(OTHER);
    S.exercises.forEach((e) => { if (e.group.toLowerCase() === c.name.toLowerCase()) e.group = other; });
    S.categories = S.categories.filter((x) => x.id !== c.id);
    commit();
  });
}

// ------------------------------------------------------------------ Fichiers
function pickFile(accept) {
  return new Promise((resolve) => {
    const old = $('#file-input');
    const inp = old.cloneNode();
    old.replaceWith(inp);
    inp.accept = accept;
    inp.addEventListener('change', () => resolve(inp.files[0] || null), { once: true });
    inp.click();
  });
}

function resizeImage(file, max) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const k = Math.min(1, max / Math.max(img.width, img.height));
      const c = document.createElement('canvas');
      c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
      c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
      URL.revokeObjectURL(url);
      resolve(c.toDataURL('image/jpeg', 0.82));
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('image')); };
    img.src = url;
  });
}

async function shareFile(name, text, type) {
  const file = new File([text], name, { type });
  try {
    if (navigator.canShare?.({ files: [file] })) { await navigator.share({ files: [file], title: name }); return; }
  } catch (e) { if (e.name === 'AbortError') return; }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(file);
  a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 5000);
}

const today = () => new Date().toISOString().slice(0, 10);

function exportCsv() {
  const sessions = S.sessions.filter((s) => s.endedAt).sort((a, b) => a.startedAt - b.startedAt);
  const text = CSV.encode(sessions, [...S.bodyWeights].sort((a, b) => a.date - b.date), exById);
  shareFile(`gymtracker-${today()}.csv`, text, 'text/csv');
}

async function importCsv() {
  const f = await pickFile('.csv,text/csv,text/plain,text/comma-separated-values');
  if (!f) return;
  let parsed;
  try { parsed = CSV.decode(await f.text()); } catch { toast('Fichier illisible'); return; }
  let imported = 0, skipped = 0, weights = 0;
  for (const ps of parsed.sessions) {
    if (S.sessions.some((s) => s.startedAt >= ps.startedAt && s.startedAt <= ps.startedAt + 999)) { skipped++; continue; }
    S.sessions.push({
      id: newId(), startedAt: ps.startedAt, endedAt: ps.endedAt,
      items: ps.exercises.map((ex) => ({
        id: newId(), exerciseId: findOrCreateExercise(ex.name, ex.group), targetSets: null,
        sets: ex.sets.map((x) => ({ id: newId(), ...x })),
      })),
    });
    imported++;
  }
  for (const w of parsed.bodyWeights) {
    if (!S.bodyWeights.some((x) => x.date === w.date)) { S.bodyWeights.push({ id: newId(), ...w }); weights++; }
  }
  commit();
  openSheet(`<h3>Import terminé</h3><p>${imported} séance${imported > 1 ? 's' : ''} importée${imported > 1 ? 's' : ''}${skipped ? `, ${skipped} déjà présente${skipped > 1 ? 's' : ''}` : ''}${weights ? `, ${weights} pesée${weights > 1 ? 's' : ''}` : ''}.</p>
    <div class="sheet-actions"><button class="btn primary" onclick="this.closest('.backdrop').click()">OK</button></div>`);
}

async function exportJson() {
  await flush();
  const data = { format: 'gymtracker-backup', version: 1, exportedAt: Date.now(), state: { ...S, timer: null }, photos: Object.fromEntries(photos) };
  shareFile(`gymtracker-sauvegarde-${today()}.json`, JSON.stringify(data), 'application/json');
}

async function importJson() {
  const f = await pickFile('.json,application/json');
  if (!f) return;
  let data;
  try { data = JSON.parse(await f.text()); } catch { toast('Fichier illisible'); return; }
  if (data?.format !== 'gymtracker-backup' || !data.state) { toast('Ce n\'est pas une sauvegarde GymTracker'); return; }
  if (!(await confirmSheet('Restaurer la sauvegarde ?', 'Toutes les données actuelles seront remplacées.', 'Restaurer'))) return;
  S = { ...defaultState(), ...data.state, settings: { ...DEFAULT_SETTINGS, ...(data.state.settings || {}) } };
  await clearPhotos();
  photos = new Map();
  for (const [k, v] of Object.entries(data.photos || {})) { photos.set(+k, v); await putPhoto(+k, v); }
  ui.stack = []; ui.drafts = {};
  commit();
  toast('Sauvegarde restaurée');
}

// ------------------------------------------------------------------ Minuteur de repos et alertes
function startTimer(seconds, label, next) {
  S.timer = { endAt: Date.now() + seconds * 1000, total: seconds, label, next, warned: false };
  save();
}

function renderTimer() {
  const el = $('#timerbar');
  const t = S.timer;
  document.body.classList.toggle('has-timer', !!t);
  if (!t) { el.classList.add('hidden'); el.innerHTML = ''; return; }
  const rem = Math.max(0, (t.endAt - Date.now()) / 1000);
  if (!el.firstChild) {
    el.innerHTML = `<div class="tb-bar"><div id="tb-fill"></div></div>
      <div class="tb-row">
        <div class="tb-info"><div class="tb-l">Repos · ${esc(t.label)}</div><div class="tb-t" id="tb-time"></div></div>
        <button class="tb-btn" data-act="timer-add" data-d="-15">−15</button>
        <button class="tb-btn" data-act="timer-add" data-d="15">+15</button>
        <button class="tb-btn skip" data-act="timer-skip">Passer</button>
      </div>`;
  }
  el.classList.remove('hidden');
  el.classList.toggle('warn', rem <= 10);
  $('#tb-time').textContent = fmtDur(Math.ceil(rem));
  $('#tb-fill').style.width = `${Math.min(100, (rem / Math.max(1, t.total)) * 100)}%`;
}

function tick() {
  const t = S?.timer;
  if (t) {
    const rem = (t.endAt - Date.now()) / 1000;
    if (rem <= 10.5 && rem > 1 && !t.warned) {
      t.warned = true; save();
      if (S.settings.warning && document.visibilityState === 'visible') playWarning();
    }
    if (rem <= 0) {
      const late = -rem > 5;           // l'app était en arrière-plan
      const next = t.next;
      S.timer = null; save();
      $('#timerbar').innerHTML = '';
      if (!late) playEnd(next);
      toast('Repos terminé' + (next ? ` · ${next}` : ''));
    }
    renderTimer();
  }
  const el = document.getElementById('elapsed');
  const s = activeSession();
  if (el && s) el.textContent = fmtDur((Date.now() - s.startedAt) / 1000);
}

let ac = null;
let speechUnlocked = false;
function unlockAudio() {
  try {
    if (!ac) ac = new (window.AudioContext || window.webkitAudioContext)();
    if (ac.state !== 'running') ac.resume();
    if (!speechUnlocked && 'speechSynthesis' in window) {
      const u = new SpeechSynthesisUtterance(' ');
      u.volume = 0;
      speechSynthesis.speak(u);
      speechUnlocked = true;
    }
  } catch { /* audio indisponible */ }
}

function setAudioSession(type) {
  try { if (navigator.audioSession) navigator.audioSession.type = type; } catch { /* Safari < 16.4 */ }
}

function beep(freq, start, dur, vol = 0.35) {
  const o = ac.createOscillator(), g = ac.createGain();
  o.type = 'sine'; o.frequency.value = freq;
  const t0 = ac.currentTime + start;
  g.gain.setValueAtTime(0.0001, t0);
  g.gain.exponentialRampToValueAtTime(vol, t0 + 0.02);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  o.connect(g).connect(ac.destination);
  o.start(t0); o.stop(t0 + dur + 0.05);
}

function playWarning() {
  if (!S.settings.sound || !ac) return;
  setAudioSession('transient');
  beep(660, 0, 0.18, 0.25);
  setTimeout(() => setAudioSession('ambient'), 800);
}

function playEnd(next) {
  setAudioSession('transient');
  if (S.settings.sound && ac) { ac.resume(); beep(880, 0, 0.22); beep(880, 0.3, 0.22); beep(1175, 0.6, 0.45); }
  if (S.settings.voice && 'speechSynthesis' in window) {
    setTimeout(() => {
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(next ? `Repos terminé. Série suivante : ${next}` : 'Repos terminé.');
      u.lang = 'fr-FR';
      const v = speechSynthesis.getVoices().find((x) => x.lang?.startsWith('fr'));
      if (v) u.voice = v;
      speechSynthesis.speak(u);
    }, S.settings.sound ? 1100 : 0);
  }
  navigator.vibrate?.([300, 150, 300]);
  setTimeout(() => setAudioSession('ambient'), 5000);
}

let wakeLock = null;
async function syncWakeLock() {
  const want = !!activeSession() && S.settings.keepAwake && document.visibilityState === 'visible';
  if (want && !wakeLock && 'wakeLock' in navigator) {
    try {
      wakeLock = await navigator.wakeLock.request('screen');
      wakeLock.addEventListener('release', () => { wakeLock = null; });
    } catch { wakeLock = null; }
  } else if (!want && wakeLock) {
    wakeLock.release().catch(() => {});
    wakeLock = null;
  }
}

// ------------------------------------------------------------------ Thème
const darkMQ = window.matchMedia('(prefers-color-scheme: dark)');
function applyTheme() {
  const t = S.settings.theme;
  const dark = t === 'dark' || (t === 'system' && darkMQ.matches);
  const root = document.documentElement;
  root.dataset.theme = dark ? 'dark' : 'light';
  root.dataset.black = dark && S.settings.pureBlack ? '1' : '';
  document.querySelector('meta[name="theme-color"]').content = dark ? (S.settings.pureBlack ? '#000000' : '#0E1014') : '#F6F4F2';
}
darkMQ.addEventListener?.('change', () => S && applyTheme());

// ------------------------------------------------------------------ Actions (délégation d'événements)
const A = {
  tab: (d) => { ui.tab = d.tab; ui.stack = []; render(); window.scrollTo(0, 0); },
  back,
  settings: () => go({ type: 'settings' }),
  'hide-hint': () => { S.hideInstallHint = true; commit(); },
  'start-free': () => startSession(null),
  'start-program': (d) => {
    if (activeSession()) { toast('Une séance est déjà en cours'); return; }
    startSession(+d.id);
  },
  'new-program': () => go({ type: 'program', id: null, draft: { name: '', items: [] } }),
  'edit-program': (d) => {
    const p = S.programs.find((x) => x.id === +d.id);
    go({ type: 'program', id: p.id, draft: { name: p.name, items: p.items.map((i) => ({ ...i })) } });
  },
  'pg-add': () => {
    const page = ui.stack[ui.stack.length - 1];
    page.draft.name = $('#pg-name').value;
    pickExercise((id) => { page.draft.items.push({ exerciseId: id, targetSets: 3 }); render(); });
  },
  'pg-move': (d) => {
    const page = ui.stack[ui.stack.length - 1];
    page.draft.name = $('#pg-name').value;
    const i = +d.i, j = i + +d.d, items = page.draft.items;
    if (j < 0 || j >= items.length) return;
    [items[i], items[j]] = [items[j], items[i]];
    render();
  },
  'pg-sets': (d) => {
    const page = ui.stack[ui.stack.length - 1];
    page.draft.name = $('#pg-name').value;
    const it = page.draft.items[+d.i];
    it.targetSets = Math.max(1, Math.min(20, it.targetSets + +d.d));
    render();
  },
  'pg-remove': (d) => {
    const page = ui.stack[ui.stack.length - 1];
    page.draft.name = $('#pg-name').value;
    page.draft.items.splice(+d.i, 1);
    render();
  },
  'pg-save': () => {
    const page = ui.stack[ui.stack.length - 1];
    const name = $('#pg-name').value.trim();
    if (!name) { toast('Donne un nom au programme'); return; }
    if (page.id) Object.assign(S.programs.find((p) => p.id === page.id), { name, items: page.draft.items });
    else S.programs.push({ id: newId(), name, items: page.draft.items });
    ui.stack.pop(); commit(); toast('Programme enregistré');
  },
  'delete-program': async () => {
    const page = ui.stack[ui.stack.length - 1];
    if (!(await confirmSheet('Supprimer ce programme ?', 'Les séances déjà faites sont conservées.', 'Supprimer'))) return;
    S.programs = S.programs.filter((p) => p.id !== page.id);
    ui.stack.pop(); commit();
  },
  'session-menu': () => menuSheet('Séance en cours', [
    { label: 'Enregistrer comme programme', run: saveSessionAsProgram },
    { label: 'Abandonner la séance', danger: true, run: async () => {
      if (!(await confirmSheet('Abandonner la séance ?', 'Toutes les séries de cette séance seront supprimées.', 'Abandonner'))) return;
      S.sessions = S.sessions.filter((x) => x !== activeSession());
      S.timer = null; ui.drafts = {}; commit();
    } },
  ]),
  'add-exercise': () => pickExercise((id) => {
    const s = activeSession();
    s.items.push({ id: newId(), exerciseId: id, targetSets: null, sets: [] });
    commit();
    setTimeout(() => document.getElementById(`item-${s.items[s.items.length - 1].id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50);
  }),
  'item-menu': (d) => {
    const s = activeSession();
    const idx = +d.idx;
    const it = s.items.find((i) => i.id === +d.item);
    const e = exById(it.exerciseId);
    const entries = [];
    if (idx > 0) entries.push({ label: '▲ Monter', run: () => { [s.items[idx - 1], s.items[idx]] = [s.items[idx], s.items[idx - 1]]; commit(); } });
    if (idx < s.items.length - 1) entries.push({ label: '▼ Descendre', run: () => { [s.items[idx + 1], s.items[idx]] = [s.items[idx], s.items[idx + 1]]; commit(); } });
    entries.push({ label: it.targetSets ? `Séries visées : ${it.targetSets} (modifier)` : 'Fixer un objectif de séries', run: () => numberSheet('Séries visées', [{ id: 'n', label: 'Nombre de séries (0 = aucun objectif)', value: it.targetSets || 3, mode: 'numeric' }], ({ n }) => { it.targetSets = n > 0 ? Math.round(n) : null; commit(); }) });
    entries.push({ label: 'Temps de repos', run: () => chooseRest(e.id) });
    entries.push({ label: 'Retirer de la séance', danger: true, run: async () => {
      if (it.sets.length && !(await confirmSheet('Retirer l\'exercice ?', `Ses ${it.sets.length} série(s) seront supprimées.`, 'Retirer'))) return;
      s.items = s.items.filter((x) => x !== it); commit();
    } });
    menuSheet(esc(e.name), entries);
  },
  rest: (d) => chooseRest(+d.ex),
  step: (d) => {
    const id = +d.item;
    const s = activeSession();
    const it = s.items.find((i) => i.id === id);
    syncDraftFromInputs(id);
    const dr = draft(it, s);
    const f = d.f;
    dr[f] = Math.max(0, Math.round((dr[f] + +d.d) * 100) / 100);
    const inp = document.querySelector(`input[data-item="${id}"][data-in="${f}"]`);
    if (inp) inp.value = f === 'w' ? fmtW(dr[f]) : dr[f];
  },
  'log-set': (d) => logSet(+d.item),
  'edit-set': (d) => editSet(d.session ? +d.session : null, +d.item, +d.set),
  'add-set-after': (d) => addSetAfterwards(+d.session, +d.item),
  finish: finishSession,
  'timer-add': (d) => {
    const t = S.timer; if (!t) return;
    t.endAt += +d.d * 1000;
    const rem = (t.endAt - Date.now()) / 1000;
    if (rem > t.total) t.total = Math.ceil(rem);
    if (rem > 10.5) t.warned = false;
    save(); renderTimer();
  },
  'timer-skip': () => { S.timer = null; save(); renderTimer(); },
  'open-session': (d) => go({ type: 'session', id: +d.id }),
  'delete-session': async (d) => {
    if (!(await confirmSheet('Supprimer cette séance ?', 'Cette action est définitive.', 'Supprimer'))) return;
    S.sessions = S.sessions.filter((s) => s.id !== +d.id);
    ui.stack.pop(); commit();
  },
  month: (d) => {
    const dt = new Date(ui.hist.y, ui.hist.m + +d.d, 1);
    ui.hist.y = dt.getFullYear(); ui.hist.m = dt.getMonth(); render();
  },
  day: (d) => { ui.hist.day = ui.hist.day === d.k ? null : d.k; render(); },
  'data-menu': () => menuSheet('Données', [
    { label: 'Exporter en CSV', run: exportCsv },
    { label: 'Importer un CSV', run: importCsv },
    { label: 'Sauvegarde complète', run: exportJson },
    { label: 'Restaurer une sauvegarde', run: importJson },
  ]),
  'prog-mode': (d) => { ui.prog.mode = d.m; render(); },
  metric: (d) => { ui.prog.metric = d.k; render(); },
  'add-bw': () => {
    const v = parseNum($('#bw').value);
    if (!(v > 0 && v < 500)) { toast('Poids invalide'); return; }
    S.bodyWeights.push({ id: newId(), date: Date.now(), weight: v });
    commit(); toast('Pesée enregistrée');
  },
  'del-bw': async (d) => {
    if (!(await confirmSheet('Supprimer cette pesée ?', '', 'Supprimer'))) return;
    S.bodyWeights = S.bodyWeights.filter((x) => x.id !== +d.id); commit();
  },
  'new-exercise': () => editExercise(null),
  'edit-exercise': (d) => editExercise(+d.id),
  categories: () => go({ type: 'categories' }),
  'new-category': () => editCategory(null),
  'edit-category': (d) => editCategory(+d.id),
  theme: (d) => { S.settings.theme = d.k; commit(); },
  'test-alert': () => { unlockAudio(); playEnd('Squat'); },
  'export-csv': exportCsv,
  'import-csv': importCsv,
  'export-json': exportJson,
  'import-json': importJson,
};

document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-act]');
  if (!el || el.disabled) return;
  const fn = A[el.dataset.act];
  if (fn) { e.preventDefault(); fn(el.dataset, el, e); }
});

document.addEventListener('input', (e) => {
  const t = e.target;
  if (t.dataset.in && t.dataset.item) {
    const v = parseNum(t.value);
    const s = activeSession();
    const it = s?.items.find((i) => i.id === +t.dataset.item);
    if (it && v != null) draft(it, s)[t.dataset.in] = v;
  } else if (t.id === 'ex-search') {
    ui.exSearch = t.value;
    $('#ex-list').innerHTML = exerciseList(ui.exSearch, 'edit-exercise');
  }
});

document.addEventListener('change', (e) => {
  const t = e.target;
  if (t.dataset.setting) { S.settings[t.dataset.setting] = t.checked; commit(); }
  else if (t.dataset.change === 'prog-ex') { ui.prog.exId = +t.value; render(); }
  else if (t.id === 'profile-weight') {
    const v = parseNum(t.value);
    if (v > 0 && v < 500) { S.settings.profileWeight = v; save(); toast('Poids enregistré'); }
  }
});

// Sélectionne tout le contenu d'un champ numérique au toucher (saisie plus rapide).
document.addEventListener('focusin', (e) => {
  if (e.target.matches('input.num')) setTimeout(() => e.target.select(), 0);
});

// Le premier toucher débloque le son (exigence de Safari).
document.addEventListener('pointerdown', unlockAudio, { passive: true });

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') { tick(); render(); }
  else flush();
  syncWakeLock();
});
window.addEventListener('pagehide', () => flush());

// ------------------------------------------------------------------ Démarrage
async function init() {
  S = await loadState();
  photos = await getAllPhotos();
  try { await navigator.storage?.persist?.(); } catch { /* ignoré */ }
  render();
  setInterval(tick, 250);
  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    navigator.serviceWorker.register('sw.js').catch((e) => console.warn('SW', e));
  }
}
init();
