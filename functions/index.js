/**
 * Cloud Functions de Teika Fusion — ÚNICO ESCRITOR DEL RANKING.
 *
 * Por qué existe esto: hasta 2026-07-28 el cliente escribía directamente en `leaderboard`. Las
 * reglas validaban el RANGO de cada campo (`level <= 2000`, `xp <= 100000000`) pero no la
 * LEGITIMIDAD: un APK modificado escribía `level = 2000, xp = 100000000` y era una escritura
 * perfectamente válida. Ahora las reglas niegan toda escritura a `leaderboard` y solo el Admin SDK
 * —que las ignora— puede tocarlo, desde aquí.
 *
 * Lo que el servidor comprueba y el cliente no podía:
 *   · MONOTONÍA — una marca nunca baja.
 *   · RITMO — el nivel no puede subir más rápido de lo humanamente posible; el exceso se RECORTA
 *     (no se rechaza) para que un jugador legítimo con un desfase de reloj nunca pierda su marca.
 *   · TOPES ABSOLUTOS — los mismos que tenían las reglas, aquí como última red.
 *
 * Despliegue:  firebase deploy --only functions
 */
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { defineBoolean } from 'firebase-functions/params';
import { logger } from 'firebase-functions';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';

initializeApp();
const db = getFirestore();

const COLLECTION = 'leaderboard';

/**
 * Enforcement de App Check, conmutable SIN tocar código.
 *
 * Arranca en `false` a propósito: activarlo antes de registrar el token de depuración en la consola
 * dejaría fuera a los builds de debug. La defensa real de esta función es la validación de abajo,
 * que funciona con o sin App Check; esto solo añade "y además, desde un binario legítimo".
 *
 *   firebase functions:config  →  usa el parámetro ENFORCE_APP_CHECK al desplegar.
 */
const ENFORCE_APP_CHECK = defineBoolean('ENFORCE_APP_CHECK', { default: false });

// ── Topes absolutos (espejo de los que tenían las reglas) ────────────────────────────────────
const MAX_LEVEL = 2000;
const MAX_TEIKAS = 1_000_000;
const MAX_XP = 100_000_000;
const MAX_INFINITE = 100_000_000;
const MAX_NAME = 40;

/**
 * Cuántos niveles puede subir una cuenta desde su última escritura.
 *
 * Un jugador no puede superar un nivel en menos de ~30 s de forma sostenida, pero sí puede
 * encadenar una sesión larga, o restaurar una cuenta antigua desde la nube y saltar de golpe. La
 * fórmula deja un margen fijo generoso (30) más medio nivel por minuto transcurrido, con techo:
 * absorbe cualquier caso legítimo y sigue haciendo imposible el 1 → 2000 instantáneo.
 */
function allowedLevelJump(minutesElapsed) {
  const generous = 30 + Math.floor(Math.max(0, minutesElapsed) * 0.5);
  return Math.min(generous, 400);
}

/** Recorta [value] al rango [0, max] y lo devuelve como entero. */
function clampInt(value, max) {
  const n = Number.isFinite(value) ? Math.floor(value) : 0;
  return Math.min(Math.max(n, 0), max);
}

function sanitizeName(raw, fallback) {
  if (typeof raw !== 'string') return fallback;
  const trimmed = raw.trim().slice(0, MAX_NAME);
  return trimmed.length > 0 ? trimmed : fallback;
}

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError('unauthenticated', 'Hace falta una sesión para enviar una marca.');
  }
  return uid;
}

const callableOptions = {
  region: 'us-central1',
  enforceAppCheck: ENFORCE_APP_CHECK,
  // El ranking es un extra social: si la función se satura, que falle rápido y no arrastre la
  // partida. El cliente ya traga el error en silencio.
  timeoutSeconds: 15,
  memory: '256MiB',
  maxInstances: 20,
};

/**
 * Sube el progreso de niveles del jugador. Fusiona por MÁXIMO y limita el ritmo.
 *
 * Entrada:  { level, teikas, xp, displayName, avatarType }
 * Salida:   { applied: { level, teikas, xp }, clamped: boolean }
 */
export const submitLeaderboardEntry = onCall(callableOptions, async (request) => {
  const uid = requireAuth(request);
  const data = request.data ?? {};

  const wantLevel = clampInt(data.level, MAX_LEVEL);
  const wantTeikas = clampInt(data.teikas, MAX_TEIKAS);
  const wantXp = clampInt(data.xp, MAX_XP);
  const avatarType = typeof data.avatarType === 'string' ? data.avatarType.slice(0, 20) : 'TEIKA_1';

  const ref = db.collection(COLLECTION).doc(uid);

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const prev = snap.exists ? snap.data() : {};

    const prevLevel = clampInt(prev.level, MAX_LEVEL);
    const prevTeikas = clampInt(prev.teikas, MAX_TEIKAS);
    const prevXp = clampInt(prev.xp, MAX_XP);

    // Minutos desde la última escritura confirmada por el SERVIDOR (no por el cliente: un reloj
    // manipulado en el dispositivo no puede ampliar su propio margen).
    const prevAt = prev.lastUpdated instanceof Timestamp ? prev.lastUpdated.toMillis() : null;
    const minutesElapsed = prevAt === null ? Number.MAX_SAFE_INTEGER : (Date.now() - prevAt) / 60000;

    const jumpAllowed = allowedLevelJump(minutesElapsed);
    const levelCeiling = snap.exists ? prevLevel + jumpAllowed : MAX_LEVEL;
    const level = Math.min(Math.max(wantLevel, prevLevel), levelCeiling);
    const clamped = wantLevel > levelCeiling;

    if (clamped) {
      // No se rechaza: se recorta y se deja rastro. Un pico así puede ser trampa o un caso raro
      // legítimo, y en ninguno de los dos conviene que el jugador pierda su marca real.
      logger.warn('Salto de nivel recortado', {
        uid, wantLevel, prevLevel, levelCeiling, minutesElapsed: Math.round(minutesElapsed),
      });
    }

    // El XP se ata al nivel: sin esto, un cliente manipulado escalaría por XP en vez de por nivel.
    const xpCeiling = snap.exists ? prevXp + 50_000 + 20_000 * jumpAllowed : MAX_XP;
    const xp = Math.min(Math.max(wantXp, prevXp), xpCeiling);

    const name = sanitizeName(data.displayName, prev.displayName ?? 'Player');

    tx.set(ref, {
      displayName: name,
      level,
      teikas: Math.max(wantTeikas, prevTeikas),
      xp,
      avatarType,
      lastUpdated: Timestamp.now(),
    }, { merge: true });   // merge: `infiniteScore` lo escribe la otra función

    return { applied: { level, teikas: Math.max(wantTeikas, prevTeikas), xp }, clamped };
  });
});

/**
 * Sube la marca del modo Infinito (PROFUNDIDAD en filas de muro, pese al nombre histórico del
 * campo `infiniteScore`). Solo sube si supera la anterior.
 *
 * Entrada:  { depth, displayName, avatarType }
 * Salida:   { applied: number, improved: boolean }
 */
export const submitInfiniteScore = onCall(callableOptions, async (request) => {
  const uid = requireAuth(request);
  const data = request.data ?? {};

  const depth = clampInt(data.depth, MAX_INFINITE);
  if (depth <= 0) return { applied: 0, improved: false };

  const avatarType = typeof data.avatarType === 'string' ? data.avatarType.slice(0, 20) : 'TEIKA_1';
  const ref = db.collection(COLLECTION).doc(uid);

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const prev = snap.exists ? snap.data() : {};
    const prevDepth = clampInt(prev.infiniteScore, MAX_INFINITE);

    if (depth <= prevDepth) return { applied: prevDepth, improved: false };

    // La profundidad no necesita límite de ritmo: una partida de Infinito es una sola sesión y no
    // hay progresión acumulada que proteger — el tope absoluto basta.
    tx.set(ref, {
      infiniteScore: depth,
      displayName: sanitizeName(data.displayName, prev.displayName ?? 'Player'),
      avatarType,
      lastUpdated: Timestamp.now(),
    }, { merge: true });

    return { applied: depth, improved: true };
  });
});

/**
 * P-438 · El puesto EXACTO del jugador en el ranking por nivel (2026-10-01).
 *
 * El cliente solo puede contar hasta 100 (las reglas limitan las listas a `limit <= 100` para que
 * nadie barra la colección), así que más allá enseñaba «100+». Aquí el Admin SDK cuenta sin límite
 * con una agregación (`count()`): una lectura por cada 1000 documentos contados, nunca la descarga
 * de la colección. Empatados en nivel comparten puesto (cuántos tienen MÁS nivel, + 1), igual que la
 * tabla del cliente (`LeaderboardRanking`).
 *
 * Entrada: nada (el uid sale de la sesión).   Salida: { rank } — 0 si el jugador no tiene entrada.
 */
export const getMyRank = onCall(callableOptions, async (request) => {
  const uid = requireAuth(request);
  const snap = await db.collection(COLLECTION).doc(uid).get();
  if (!snap.exists) return { rank: 0 };
  const level = clampInt(snap.data().level, MAX_LEVEL);
  const ahead = await db.collection(COLLECTION).where('level', '>', level).count().get();
  return { rank: ahead.data().count + 1 };
});
