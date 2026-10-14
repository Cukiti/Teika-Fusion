// Un eslabón de la cadena de biomas del mapa (ver las reglas en ps_relleno_generativo.jsx).
// Requiere haber cargado antes ps_relleno_generativo.jsx con $.evalFile.
//
// doBiome(tag, trans, main, tramos):
//  1. recorta el documento a la SEMILLA (sus 700 px de arriba = cima del bioma anterior);
//  2. crece 1 tramo de TRANSICIÓN y (tramos - 1) tramos del bioma, de 640 px con 320 de solape;
//  3. exporta art/mapa/<tag>.png = el bioma SIN la semilla;
//  4. exporta art/mapa/seed_<tag>.png = la semilla tal como quedó (con el solape repintado), que
//     hay que pegar sobre los 700 px de arriba de la franja anterior (lo hace un script Python).

var MAPA_DIR = "%USERPROFILE%/AndroidStudioProjects/Teikafusion/art/mapa/";
var SEED = 700;

function savePngCrop(doc, name, top, bottom) {
  var W = doc.width.as("px");
  var dup = doc.duplicate(name, true);
  dup.crop([0, top, W, bottom]);
  var o = new PNGSaveOptions(); o.compression = 6;
  dup.saveAs(new File(MAPA_DIR + name + ".png"), o, true, Extension.LOWERCASE);
  dup.close(SaveOptions.DONOTSAVECHANGES);
  app.activeDocument = doc;
}

/**
 * EL TINTE DEL BIOMA. Firefly copia la paleta del solape, así que el color del bioma anterior se
 * contagia hacia arriba y ningún prompt lo corrige del todo (aprendido con el Pasaje, 2026-09-24).
 * Se tiñe el bioma en modo COLOR (cambia el tono, conserva luces y sombras) con una rampa: 0 % en la
 * frontera de abajo —la unión con el bioma anterior no se toca— y pleno a ~RAMP px por encima.
 * Va ANTES de sacar la semilla: el bioma siguiente ya nace de estos colores.
 * [tint] = [r, g, b, opacidad %].
 */
function tintBiome(doc, tint, H) {
  var W = doc.width.as("px"), RAMP = 900;
  var bottom = H - SEED - RAMP / 2;
  doc.selection.select([[0, 0], [W, 0], [W, bottom], [0, bottom]]);
  doc.selection.feather(UnitValue(RAMP / 4, "px"));
  var c = new SolidColor(); c.rgb.red = tint[0]; c.rgb.green = tint[1]; c.rgb.blue = tint[2];
  doc.selection.fill(c, ColorBlendMode.COLOR, tint[3], false);
  doc.selection.deselect();
}

/**
 * UN BIOMA ENTERO (versión 2, 2026-09-24, tras el Pasaje y el Jardín).
 *
 *  1. Recorta a la SEMILLA y crece [tramos] tramos de 640 px con el prompt del bioma [main] (el
 *     primero con 200 px de solape).
 *  2. PASADA DE FUNDIDO: rellena una franja alta (~1240 px) A CABALLO de la frontera con [blend],
 *     que describe qué hay arriba (este bioma) y abajo (el anterior). Con un tramo corto de
 *     transición, un salto grande de ambiente (noche → día) salía como un MURO de piedra
 *     horizontal; con la franja alta sale gradual.
 *  3. TINTE opcional ([tint], ver tintBiome) si la paleta del bioma anterior se contagió.
 *  4. Exporta la franja (sin semilla) y la semilla repintada (para mapa_coser.py).
 *
 * La paleta va AL PRINCIPIO de los prompts: es lo que más pesa. Sin «map», «cave», «bare» ni
 * negaciones (Firefly los rechaza en silencio o deriva a pasillo).
 */
/**
 * v3: [stages] = prompts de TRANSICIÓN POR ETAPAS para los primeros tramos (puede ser []). Con
 * saltos grandes de ambiente (jardín soleado → tormenta) la pasada de fundido sigue dibujando dos
 * zonas con una línea recta entre ellas; subir de etapa en etapa, un paso pequeño por tramo, es
 * lo que Firefly continúa bien. [blend] = null salta la pasada de fundido.
 */
function doBiome(tag, main, tramos, blend, tint, stages) {
  var st = stages || [];
  app.displayDialogs = DialogModes.NO;
  app.preferences.rulerUnits = Units.PIXELS;
  var doc = app.activeDocument;
  doc.flatten();
  var W = doc.width.as("px");
  doc.crop([0, 0, W, SEED]);
  var errs = [];
  for (var k = 0; k < tramos; k++) {
    var pr = k < st.length ? st[k] : main;
    var ov = k === 0 ? 200 : (k < st.length ? 220 : 320);
    try { extendUp(doc, 640, ov, pr); } catch (e) { errs.push(k + ": " + e.message); }
  }
  doc.flatten();
  try { doc.backgroundLayer.isBackgroundLayer = false; } catch (e0) {}
  var H = doc.height.as("px");
  if (blend) {
    var top = H - SEED - 940, bot = H - SEED + 300;
    doc.selection.select([[0, top], [W, top], [W, bot], [0, bot]]);
    try { genFill(doc, blend); } catch (e3) { errs.push("fundido: " + e3.message); }
    try { doc.selection.deselect(); } catch (e4) {}
    doc.flatten();
  }
  if (tint) tintBiome(doc, tint, H);
  savePngCrop(doc, tag, 0, H - SEED);
  savePngCrop(doc, "seed_" + tag, H - SEED, H);
  return { tag: tag, h: H - SEED, errs: errs.join(" | ") };
}

/**
 * v4 · EL PUENTE (2026-09-24, tras el Volcán). Hacer crecer un bioma DESDE el anterior tiene un
 * límite: Firefly continúa lo que ve (el río con rayos de la Tormenta siguió saliendo en el centro
 * del «volcán» ni con adherencia 14). Así que cada bioma nace de su PROPIA base:
 *
 *  1. Base de 1792 px generada desde cero con [main] → personalidad 100 % suya.
 *  2. Documento compuesto: base arriba, hueco de 600 px, y abajo la cima (700 px) del bioma
 *     anterior [prevTag]. El hueco (+360 px en la base, +180 en el anterior) se rellena con [bridge], que
 *     describe qué hay abajo, qué hay arriba y cómo se pasa de uno a otro: al ver los dos lados,
 *     Firefly dibuja una transición diseñada (orilla, cresta, niebla…), no un muro.
 *  3. Crece [extra] tramos más hacia arriba desde su propia base (sin contagio).
 *  «night» / «noche» en el prompt hace que al crecer pinte CIELO estrellado, no suelo: usar
 *     «dark indigo palette».
 *  4. Exporta <tag>.png y seed_<tag>.png y cierra el documento (acumular documentos grandes
 *     bloquea los scripts de Photoshop: pasó con cuatro de ~5000 px abiertos).
 */
function doBiomePuente(tag, prevTag, main, bridge, extra) {
  app.displayDialogs = DialogModes.NO;
  app.preferences.rulerUnits = Units.PIXELS;
  var BASE = 1792, GAP = 600, W = 1024, errs = [];
  var base = app.documents.add(UnitValue(W, "px"), UnitValue(BASE, "px"), 72, "base_" + tag, NewDocumentMode.RGB, DocumentFill.WHITE);
  try { base.backgroundLayer.isBackgroundLayer = false; } catch (e0) {}
  base.selection.selectAll();
  try { genFill(base, main); } catch (e) { errs.push("base: " + e.message); }
  base.selection.deselect();
  base.flatten();
  var comp = app.documents.add(UnitValue(W, "px"), UnitValue(BASE + GAP + SEED, "px"), 72, "cadena_" + tag, NewDocumentMode.RGB, DocumentFill.TRANSPARENT);
  app.activeDocument = base;
  var lb = base.activeLayer.duplicate(comp, ElementPlacement.PLACEATBEGINNING);
  base.close(SaveOptions.DONOTSAVECHANGES);
  var prev = app.open(new File(MAPA_DIR + prevTag + ".png"));
  prev.crop([0, 0, W, SEED]);
  prev.flatten();
  var lp = prev.activeLayer.duplicate(comp, ElementPlacement.PLACEATBEGINNING);
  prev.close(SaveOptions.DONOTSAVECHANGES);
  app.activeDocument = comp;
  var b1 = lb.bounds; lb.translate(UnitValue(-b1[0].as("px"), "px"), UnitValue(-b1[1].as("px"), "px"));
  var b2 = lp.bounds; lp.translate(UnitValue(-b2[0].as("px"), "px"), UnitValue(BASE + GAP - b2[1].as("px"), "px"));
  comp.flatten();
  try { comp.backgroundLayer.isBackgroundLayer = false; } catch (e1) {}
  var top = BASE - 360, bot = BASE + GAP + 180;
  comp.selection.select([[0, top], [W, top], [W, bot], [0, bot]]);
  try { genFill(comp, bridge); } catch (e2) { errs.push("puente: " + e2.message); }
  comp.selection.deselect();
  for (var k = 0; k < extra; k++) {
    try { extendUp(comp, 640, 320, main); } catch (e3) { errs.push(k + ": " + e3.message); }
  }
  comp.flatten();
  var H = comp.height.as("px");
  savePngCrop(comp, tag, 0, H - SEED);
  savePngCrop(comp, "seed_" + tag, H - SEED, H);
  comp.close(SaveOptions.DONOTSAVECHANGES);
  return { tag: tag, h: H - SEED, errs: errs.join(" | ") };
}
