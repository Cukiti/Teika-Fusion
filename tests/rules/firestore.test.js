/**
 * Tests de las reglas de seguridad de Firestore (`firestore.rules`).
 *
 * Por qué existen: las reglas son ~230 líneas con ocho funciones auxiliares y lógica de
 * `diff().affectedKeys()`, y hasta 2026-07-28 se desplegaban a ciegas. Un cambio que rompiera, por
 * ejemplo, `claimsLegacyStatsDocument` no se habría notado hasta que los jugadores empezaran a
 * fallar en producción.
 *
 * Cómo se ejecutan (requiere Java para el emulador):
 *     cd tests/rules && npm install && npm test
 *
 * `firebase emulators:exec` levanta el emulador de Firestore, carga `firestore.rules` (según
 * `firebase.json` de la raíz), corre esto y lo apaga. No toca ningún proyecto real.
 */
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs, limit, orderBy, query, setLogLevel } from 'firebase/firestore';

// La MITAD de esta suite comprueba que una operación se DENIEGA, y tanto el emulador como el SDK
// gritan por cada denegación (stack traces de Java + `PERMISSION_DENIED` del cliente). Ese ruido no
// indica nada malo —es la prueba de que las reglas funcionan— pero sepulta el resultado real.
// Se silencia el SDK aquí y el emulador con `--log-verbosity QUIET` en el script de npm.
// Para depurar una regla concreta: `npm run test:verbose`.
setLogLevel('silent');

const ALICE = 'uid_alice';
const BOB = 'uid_bob';

let testEnv;

/** Firestore con la sesión de [uid], o sin autenticar si se omite. */
const as = (uid) =>
  (uid ? testEnv.authenticatedContext(uid) : testEnv.unauthenticatedContext()).firestore();

/** Escribe saltándose las reglas (para preparar el estado previo de un test). */
const seed = (fn) => testEnv.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'teika-fusion-test',
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

// ════════════════════════════════════════════════════════════════════════════════
describe('leaderboard', () => {
  const validEntry = {
    displayName: 'Alice',
    level: 42,
    teikas: 100,
    xp: 5000,
    infiniteScore: 12,
    avatarType: 'TEIKA_1',
    lastUpdated: new Date(),
  };

  // El ranking es SOLO LECTURA para los clientes desde 2026-07-28: escribe únicamente la Cloud
  // Function (Admin SDK). Estos tests son la garantía de que nadie puede escribir su propia marca.
  it('NI SIQUIERA el dueño puede escribir su entrada', async () => {
    await assertFails(setDoc(doc(as(ALICE), 'leaderboard', ALICE), validEntry));
  });

  it('OTRO usuario tampoco, obviamente', async () => {
    await assertFails(setDoc(doc(as(BOB), 'leaderboard', ALICE), validEntry));
  });

  it('sin autenticar no se puede escribir', async () => {
    await assertFails(setDoc(doc(as(null), 'leaderboard', ALICE), validEntry));
  });

  it('no se puede ACTUALIZAR una entrada existente (el caso de la trampa)', async () => {
    await seed((db) => setDoc(doc(db, 'leaderboard', ALICE), validEntry));
    // Esto es exactamente lo que hacía un APK modificado y las reglas viejas aceptaban.
    await assertFails(updateDoc(doc(as(ALICE), 'leaderboard', ALICE), { level: 2000 }));
    await assertFails(updateDoc(doc(as(ALICE), 'leaderboard', ALICE), { xp: 100000000 }));
    await assertFails(updateDoc(doc(as(ALICE), 'leaderboard', ALICE), { infiniteScore: 999999 }));
  });

  it('tampoco se puede cambiar solo el nombre visible', async () => {
    await seed((db) => setDoc(doc(db, 'leaderboard', ALICE), validEntry));
    await assertFails(updateDoc(doc(as(ALICE), 'leaderboard', ALICE), { displayName: 'Tramposo' }));
  });

  it('cualquier autenticado puede LEER una entrada (ranking público)', async () => {
    await seed((db) => setDoc(doc(db, 'leaderboard', ALICE), validEntry));
    await assertSucceeds(getDoc(doc(as(BOB), 'leaderboard', ALICE)));
    await assertFails(getDoc(doc(as(null), 'leaderboard', ALICE)));
  });

  it('list acotado: se permite limit <= 100 y se rechaza por encima', async () => {
    const col = collection(as(ALICE), 'leaderboard');
    await assertSucceeds(getDocs(query(col, orderBy('level', 'desc'), limit(50))));
    await assertFails(getDocs(query(col, orderBy('level', 'desc'), limit(500))));
  });

  it('barrer la colección entera SIN límite está prohibido', async () => {
    await assertFails(getDocs(collection(as(ALICE), 'leaderboard')));
  });

  it('el BORRADO sigue siendo del cliente (borrado de cuenta / reset); otro no puede', async () => {
    await seed((db) => setDoc(doc(db, 'leaderboard', ALICE), validEntry));
    await assertFails(deleteDoc(doc(as(BOB), 'leaderboard', ALICE)));
    await assertSucceeds(deleteDoc(doc(as(ALICE), 'leaderboard', ALICE)));
  });
});

// ════════════════════════════════════════════════════════════════════════════════
describe('cloud_saves', () => {
  const validSave = { json: '{"maxLevel":10}', updatedAtEpochMs: Date.now() };

  it('el dueño escribe y lee su propio respaldo', async () => {
    await assertSucceeds(setDoc(doc(as(ALICE), 'cloud_saves', ALICE), validSave));
    await assertSucceeds(getDoc(doc(as(ALICE), 'cloud_saves', ALICE)));
  });

  it('NADIE más puede leer ni escribir tu respaldo', async () => {
    await seed((db) => setDoc(doc(db, 'cloud_saves', ALICE), validSave));
    await assertFails(getDoc(doc(as(BOB), 'cloud_saves', ALICE)));
    await assertFails(setDoc(doc(as(BOB), 'cloud_saves', ALICE), validSave));
    await assertFails(getDoc(doc(as(null), 'cloud_saves', ALICE)));
  });

  it('rechaza un json por encima de 900 KB', async () => {
    await assertFails(
      setDoc(doc(as(ALICE), 'cloud_saves', ALICE), { ...validSave, json: 'x'.repeat(900_001) }),
    );
  });

  it('acepta exactamente 900 KB (el límite es inclusivo)', async () => {
    await assertSucceeds(
      setDoc(doc(as(ALICE), 'cloud_saves', ALICE), { ...validSave, json: 'x'.repeat(900_000) }),
    );
  });

  it('rechaza campos extra y tipos equivocados', async () => {
    await assertFails(
      setDoc(doc(as(ALICE), 'cloud_saves', ALICE), { ...validSave, evil: 1 }),
    );
    await assertFails(
      setDoc(doc(as(ALICE), 'cloud_saves', ALICE), { json: 123, updatedAtEpochMs: Date.now() }),
    );
    await assertFails(
      setDoc(doc(as(ALICE), 'cloud_saves', ALICE), { ...validSave, updatedAtEpochMs: 0 }),
    );
  });

  it('el borrado de cuenta puede eliminar el propio respaldo', async () => {
    await seed((db) => setDoc(doc(db, 'cloud_saves', ALICE), validSave));
    await assertFails(deleteDoc(doc(as(BOB), 'cloud_saves', ALICE)));
    await assertSucceeds(deleteDoc(doc(as(ALICE), 'cloud_saves', ALICE)));
  });
});

// ════════════════════════════════════════════════════════════════════════════════
describe('player_stats', () => {
  const validStats = (owner) => ({
    owner_uid: owner,
    player_id: owner,
    coins: 500,
    max_level: 12,
    display_name: 'Alice',
    updated_at: new Date(),
  });

  it('se crea el documento propio con owner_uid correcto', async () => {
    await assertSucceeds(
      setDoc(doc(as(ALICE), 'player_stats', ALICE), validStats(ALICE)),
    );
  });

  it('NO se puede crear un documento a nombre de otro', async () => {
    await assertFails(setDoc(doc(as(BOB), 'player_stats', ALICE), validStats(ALICE)));
  });

  it('el dueño lee su documento; otro no, y listar está prohibido para todos', async () => {
    await seed((db) => setDoc(doc(db, 'player_stats', ALICE), validStats(ALICE)));
    await assertSucceeds(getDoc(doc(as(ALICE), 'player_stats', ALICE)));
    await assertFails(getDoc(doc(as(BOB), 'player_stats', ALICE)));
    await assertFails(getDocs(collection(as(ALICE), 'player_stats')));
  });

  it('NO se puede secuestrar el documento pisando owner_uid', async () => {
    await seed((db) => setDoc(doc(db, 'player_stats', ALICE), validStats(ALICE)));
    await assertFails(updateDoc(doc(as(BOB), 'player_stats', ALICE), { owner_uid: BOB }));
    await assertFails(updateDoc(doc(as(ALICE), 'player_stats', ALICE), { owner_uid: BOB }));
  });

  it('el jugador NO puede auto-concederse un comando de admin', async () => {
    await seed((db) => setDoc(doc(db, 'player_stats', ALICE), validStats(ALICE)));
    // Este es el corazón del backdoor: si esto pasara, cualquiera se regala monedas y niveles.
    await assertFails(updateDoc(doc(as(ALICE), 'player_stats', ALICE), { set_coins: 999999 }));
    await assertFails(updateDoc(doc(as(ALICE), 'player_stats', ALICE), { set_level: 300 }));
    await assertFails(updateDoc(doc(as(ALICE), 'player_stats', ALICE), { set_crystal_4: 999 }));
  });

  it('la app SÍ puede inicializar el esquema de comandos con valores neutros', async () => {
    await seed((db) => setDoc(doc(db, 'player_stats', ALICE), validStats(ALICE)));
    await assertSucceeds(
      updateDoc(doc(as(ALICE), 'player_stats', ALICE), {
        set_coins: 0,
        set_level: 0,
        unlock_companions: [],
        force_companion: '',
        set_crystal_1: -1,
        set_crystal_2: -1,
        set_crystal_3: -1,
        set_crystal_4: -1,
      }),
    );
  });

  it('la app puede RESETEAR un comando ya aplicado', async () => {
    await seed((db) =>
      setDoc(doc(db, 'player_stats', ALICE), { ...validStats(ALICE), set_coins: 3000 }),
    );
    await assertSucceeds(
      updateDoc(doc(as(ALICE), 'player_stats', ALICE), {
        set_coins: 0,
        cmd_applied_at: new Date(),
      }),
    );
  });

  it('rechaza coins/max_level fuera de rango', async () => {
    await seed((db) => setDoc(doc(db, 'player_stats', ALICE), validStats(ALICE)));
    await assertFails(updateDoc(doc(as(ALICE), 'player_stats', ALICE), { coins: -5 }));
    await assertFails(updateDoc(doc(as(ALICE), 'player_stats', ALICE), { max_level: 99999 }));
  });

  it('un documento LEGADO sin owner_uid lo puede reclamar quien esté autenticado', async () => {
    await seed((db) =>
      setDoc(doc(db, 'player_stats', 'android_id_viejo'), {
        player_id: 'android_id_viejo',
        coins: 100,
        max_level: 3,
        display_name: 'Alice',
      }),
    );
    await assertSucceeds(
      updateDoc(doc(as(ALICE), 'player_stats', 'android_id_viejo'), {
        owner_uid: ALICE,
        coins: 150,
      }),
    );
  });

  it('reclamar un legado a nombre de OTRO está prohibido', async () => {
    await seed((db) =>
      setDoc(doc(db, 'player_stats', 'android_id_viejo'), { player_id: 'android_id_viejo' }),
    );
    await assertFails(
      updateDoc(doc(as(ALICE), 'player_stats', 'android_id_viejo'), { owner_uid: BOB }),
    );
  });

  it('el borrado de cuenta puede eliminar el propio perfil', async () => {
    await seed((db) => setDoc(doc(db, 'player_stats', ALICE), validStats(ALICE)));
    await assertFails(deleteDoc(doc(as(BOB), 'player_stats', ALICE)));
    await assertSucceeds(deleteDoc(doc(as(ALICE), 'player_stats', ALICE)));
  });
});

// ════════════════════════════════════════════════════════════════════════════════
describe('colecciones no declaradas', () => {
  it('todo lo que no esté en las reglas está prohibido (deny por defecto)', async () => {
    await assertFails(setDoc(doc(as(ALICE), 'admin_config', 'x'), { a: 1 }));
    await assertFails(getDoc(doc(as(ALICE), 'secrets', 'x')));
  });
});
