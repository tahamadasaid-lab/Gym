// Export / import CSV, compatible avec la version Android (même format, mêmes colonnes).
// Séparateur « ; », décimales « , ». Une ligne par série, plus une ligne par pesée.

const SEP = ';';
export const HEADER = 'type;debut_seance;fin_seance;exercice;groupe;serie;poids_kg;reps;repos_s;heure_serie';

const pad = (n) => String(n).padStart(2, '0');
function fmtDate(ms) {
  if (ms == null) return '';
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
function parseDate(s) {
  const m = String(s || '').trim().match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/);
  if (!m) return null;
  return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0)).getTime();
}
const num = (v) => (v % 1 === 0 ? String(v) : String(v).replace('.', ','));
const escape = (s) => (/[;"\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s);
const row = (...f) => f.map((x) => escape(String(x))).join(SEP);

/** sessions : séances terminées triées ; exName(id) / exGroup(id) donnent le nom et la catégorie. */
export function encode(sessions, bodyWeights, exById) {
  const lines = ['﻿' + HEADER];
  for (const s of sessions) {
    for (const it of s.items) {
      const e = exById(it.exerciseId);
      if (!e) continue;
      [...it.sets].sort((a, b) => a.at - b.at).forEach((st, i) => {
        lines.push(row('SERIE', fmtDate(s.startedAt), fmtDate(s.endedAt), e.name, e.group,
          i + 1, num(st.weight), st.reps, st.rest ?? '', fmtDate(st.at)));
      });
    }
  }
  for (const w of bodyWeights) lines.push(row('POIDS_CORPS', fmtDate(w.date), '', '', '', '', num(w.weight), '', '', ''));
  return lines.join('\n') + '\n';
}

export function parseLine(line) {
  const out = [];
  let sb = '', quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quoted && c === '"' && line[i + 1] === '"') { sb += '"'; i++; }
    else if (c === '"') quoted = !quoted;
    else if (!quoted && c === SEP) { out.push(sb); sb = ''; }
    else sb += c;
  }
  out.push(sb);
  return out;
}

/** Retourne { sessions: [{ startedAt, endedAt, exercises: [{ name, group, sets: [...] }] }], bodyWeights: [...] } */
export function decode(text) {
  const sessions = new Map();
  const weights = [];
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.replace(/^﻿/, '');
    if (!line.trim()) continue;
    const f = parseLine(line);
    const g = (i) => (f[i] ?? '').trim();
    const type = g(0).toUpperCase();
    if (type === 'SERIE') {
      const start = parseDate(g(1));
      const name = g(3);
      const reps = parseInt(g(7), 10);
      if (start == null || !name || !Number.isFinite(reps)) continue;
      const weight = parseFloat(g(6).replace(',', '.')) || 0;
      if (!sessions.has(start)) sessions.set(start, { startedAt: start, endedAt: parseDate(g(2)), exercises: [] });
      const s = sessions.get(start);
      let ex = s.exercises.find((x) => x.name.toLowerCase() === name.toLowerCase());
      if (!ex) { ex = { name, group: g(4), sets: [] }; s.exercises.push(ex); }
      const rest = parseInt(g(8), 10);
      ex.sets.push({
        weight, reps,
        rest: Number.isFinite(rest) ? rest : null,
        at: parseDate(g(9)) ?? start + ex.sets.length * 1000,
      });
    } else if (type === 'POIDS_CORPS') {
      const d = parseDate(g(1));
      const w = parseFloat(g(6).replace(',', '.'));
      if (d != null && Number.isFinite(w)) weights.push({ date: d, weight: w });
    }
  }
  // Séance non terminée dans le fichier : on la considère finie à sa dernière série.
  const list = [...sessions.values()].map((s) => {
    if (s.endedAt == null) {
      const ats = s.exercises.flatMap((e) => e.sets.map((x) => x.at));
      s.endedAt = ats.length ? Math.max(...ats) : s.startedAt;
    }
    return s;
  });
  return { sessions: list, bodyWeights: weights };
}
