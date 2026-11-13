// Formatage et petits utilitaires (équivalent de util/Format.kt et data/Stats.kt).

export const $ = (sel, root = document) => root.querySelector(sel);

export const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const pad = (n) => String(n).padStart(2, '0');
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

/** 80 -> "80", 82.5 -> "82,5" */
export function fmtW(kg) {
  const v = Number(kg) || 0;
  if (v % 1 === 0) return String(v);
  return v.toFixed(2).replace(/0+$/, '').replace(/\.$/, '').replace('.', ',');
}

export const fmtNum = (v, digits = 0) =>
  Number(v).toLocaleString('fr-FR', { maximumFractionDigits: digits, minimumFractionDigits: 0 });

export const fmtVol = (kg) => Math.round(kg).toLocaleString('fr-FR') + ' kg';
export const fmtKcal = (k) => Math.round(k).toLocaleString('fr-FR') + ' kcal';

/** 95 -> "1:35", 3725 -> "1:02:05" */
export function fmtDur(totalSeconds) {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
  return h > 0 ? `${h}:${pad(m)}:${pad(x)}` : `${m}:${pad(x)}`;
}

export function fmtDurHuman(totalSeconds) {
  const h = Math.floor(totalSeconds / 3600), m = Math.floor((totalSeconds % 3600) / 60);
  return h > 0 ? `${h} h ${pad(m)}` : `${m} min`;
}

const longDF = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
const monthDF = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' });
export const fmtLongDate = (ms) => cap(longDF.format(new Date(ms)));
export const fmtMonth = (d) => cap(monthDF.format(d));
export const fmtShortDate = (ms) => { const d = new Date(ms); return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}`; };
export const fmtTime = (ms) => { const d = new Date(ms); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
export const dayKey = (ms) => { const d = new Date(ms); return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`; };

/** Accepte "82,5" ou "82.5". */
export function parseNum(text) {
  const v = parseFloat(String(text ?? '').trim().replace(',', '.'));
  return Number.isFinite(v) ? v : null;
}

/** Estimation du 1RM (formule d'Epley). */
export function e1rm(weight, reps) {
  if (reps <= 0) return 0;
  if (reps === 1) return weight;
  return weight * (1 + reps / 30);
}

/** Lundi 00:00 de la semaine en cours. */
export function startOfWeek(now = new Date()) {
  const d = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return d.getTime();
}

export const isIOS = () => /iphone|ipad|ipod/i.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
export const isStandalone = () => window.navigator.standalone === true ||
  window.matchMedia('(display-mode: standalone)').matches;
