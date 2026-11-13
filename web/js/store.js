// Persistance locale dans IndexedDB : tout reste sur le téléphone, aucun compte.
// Store "kv" : l'état complet (JSON). Store "photos" : photo de chaque exercice (data URL).

const DB_NAME = 'gymtracker';

export const OTHER = 'Autre';
export const DEFAULT_COLOR = '#9AA8B5';
export const DEFAULT_ICON = 'bolt';
export const DEFAULT_MET = 4.5;

export const DEFAULT_CATEGORIES = [
  ['Pectoraux', '#FF7A45', 'dumbbell', 5.0],
  ['Dos', '#4FC3F7', 'rowing', 5.0],
  ['Jambes', '#7CD992', 'run', 6.0],
  ['Épaules', '#C792EA', 'gym', 4.5],
  ['Bras', '#FFC857', 'arm', 3.5],
  ['Abdos', '#4DD0E1', 'yoga', 3.8],
  ['Cardio', '#FF5C7A', 'heart', 8.0],
  [OTHER, DEFAULT_COLOR, DEFAULT_ICON, DEFAULT_MET],
];

const DEFAULT_EXERCISES = [
  ['Développé couché', 'Pectoraux', 120],
  ['Développé incliné haltères', 'Pectoraux', 90],
  ['Écarté à la poulie', 'Pectoraux', 60],
  ['Pompes', 'Pectoraux', 60],
  ['Tractions', 'Dos', 120],
  ['Rowing barre', 'Dos', 90],
  ['Tirage vertical', 'Dos', 90],
  ['Soulevé de terre', 'Dos', 180],
  ['Squat', 'Jambes', 150],
  ['Presse à cuisses', 'Jambes', 120],
  ['Fentes', 'Jambes', 90],
  ['Leg curl', 'Jambes', 60],
  ['Leg extension', 'Jambes', 60],
  ['Mollets debout', 'Jambes', 60],
  ['Développé militaire', 'Épaules', 120],
  ['Élévations latérales', 'Épaules', 60],
  ['Curl biceps', 'Bras', 60],
  ['Curl marteau', 'Bras', 60],
  ['Extension triceps poulie', 'Bras', 60],
  ['Dips', 'Bras', 90],
  ['Crunch', 'Abdos', 45],
  ['Gainage (planche)', 'Abdos', 45],
];

export const DEFAULT_SETTINGS = {
  theme: 'system',       // system | light | dark
  pureBlack: false,      // fond noir intégral (OLED)
  sound: true,           // bips à la fin du repos
  voice: true,           // annonce vocale
  warning: true,         // signal 10 s avant la fin
  keepAwake: true,       // garder l'écran allumé pendant une séance
  profileWeight: 75,     // poids pour les calories sans pesée
};

export function defaultState() {
  let id = 1;
  return {
    version: 1,
    categories: DEFAULT_CATEGORIES.map(([name, color, icon, met], position) => ({ id: id++, name, color, icon, met, position })),
    exercises: DEFAULT_EXERCISES.map(([name, group, rest]) => ({ id: id++, name, group, rest })),
    sessions: [],   // { id, startedAt, endedAt, items: [{ id, exerciseId, targetSets, sets: [{ id, reps, weight, at, rest }] }] }
    programs: [],   // { id, name, items: [{ exerciseId, targetSets }] }
    bodyWeights: [],// { id, date, weight }
    settings: { ...DEFAULT_SETTINGS },
    timer: null,    // { endAt, total, label, next, warned }
    nextId: id,
  };
}

let dbPromise;
function db() {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = () => {
        req.result.createObjectStore('kv');
        req.result.createObjectStore('photos');
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }
  return dbPromise;
}

async function tx(store, mode, fn) {
  const d = await db();
  return new Promise((resolve, reject) => {
    const t = d.transaction(store, mode);
    const result = fn(t.objectStore(store));
    t.oncomplete = () => resolve(result && 'result' in result ? result.result : undefined);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error);
  });
}

export async function loadState() {
  let s = null;
  try { s = await tx('kv', 'readonly', (st) => st.get('state')); } catch (e) { console.warn(e); }
  if (!s) return defaultState();
  s.settings = { ...DEFAULT_SETTINGS, ...(s.settings || {}) };
  return s;
}

let saveTimer = null;
let pending = null;
/** Sauvegarde (regroupée sur 150 ms). */
export function saveState(state) {
  pending = state;
  clearTimeout(saveTimer);
  saveTimer = setTimeout(flush, 150);
}
export function flush() {
  clearTimeout(saveTimer);
  if (!pending) return Promise.resolve();
  const data = JSON.parse(JSON.stringify(pending));
  pending = null;
  return tx('kv', 'readwrite', (st) => st.put(data, 'state')).catch((e) => console.error('Sauvegarde', e));
}

export async function getAllPhotos() {
  const map = new Map();
  try {
    const d = await db();
    await new Promise((resolve, reject) => {
      const req = d.transaction('photos').objectStore('photos').openCursor();
      req.onsuccess = () => {
        const c = req.result;
        if (c) { map.set(Number(c.key), c.value); c.continue(); } else resolve();
      };
      req.onerror = () => reject(req.error);
    });
  } catch (e) { console.warn(e); }
  return map;
}
export const putPhoto = (id, dataUrl) => tx('photos', 'readwrite', (st) => st.put(dataUrl, id));
export const delPhoto = (id) => tx('photos', 'readwrite', (st) => st.delete(id));
export const clearPhotos = () => tx('photos', 'readwrite', (st) => st.clear());
