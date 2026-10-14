// Relleno generativo de Firefly desde ExtendScript (Photoshop 2026), probado el 2026-09-24 para
// los fondos del mapa de mundos. Se lanza con la herramienta `photoshop_execute_script` del MCP
// `photoshop` (sus herramientas generativas propias fallan: usan sintaxis ES6 `{ wait }` que
// ExtendScript no entiende, y nombres de acción que Photoshop no reconoce).
//
// REGLAS APRENDIDAS (cada fallo gasta créditos):
//  - El mensaje tiene que ser EXACTAMENTE este: flujo `in_painting`, opciones en `clio` con la
//    clase `target` y `gi_ADVANCED`. Sin `gi_PROMPT` en las opciones, Firefly ignora el texto.
//  - Si Firefly rechaza el prompt NO da error: rellena sin prompt y sale una TEXTURA genérica
//    (ladrillo, yeso). Lo rechaza con la palabra «map» y con negaciones («no horizon», «no text»).
//  - Vista cenital: «seen from directly above, bird's eye view». Sin eso sale vista frontal.
//  - Continuidad: ampliar lienzo con ancla abajo, seleccionar la zona nueva + ~320 px de solape
//    con la imagen, y rellenar. La unión no se nota. Tramos de 640 px.
//  - «cave» / «cueva» deriva a perspectiva de PASILLO con luz al fondo (el suelo se estrecha):
//    usar «orthographic top-down view of wide flat … ground like a board game tile».
//
// CADENA DE BIOMAS (art/mapa/NN_bioma.png):
//  1. Terminado un bioma, el documento se recorta a sus 700 px de ARRIBA: la SEMILLA del siguiente.
//  2. El siguiente bioma crece encima (1 tramo de transición + los suyos).
//  3. Se exporta la franja nueva SIN la semilla (documento menos sus 700 px de abajo)…
//  4. …y la SEMILLA TAL COMO QUEDÓ (sus 700 px de abajo, con el solape ya repintado por el primer
//     tramo) se pega encima de los 700 px de arriba de la franja ANTERIOR. Si se olvida este paso,
//     queda una costura visible: la franja anterior se exportó antes de que el solape se repintara.

function s(x) { return stringIDToTypeID(x); }

function genFill(doc, prompt) {
  var desc = new ActionDescriptor();
  var ref = new ActionReference();
  ref.putEnumerated(charIDToTypeID("Dcmn"), charIDToTypeID("Ordn"), charIDToTypeID("Trgt"));
  desc.putReference(charIDToTypeID("null"), ref);
  desc.putInteger(s("documentID"), doc.id);
  desc.putInteger(s("layerID"), doc.activeLayer.id);
  desc.putString(s("prompt"), prompt);
  desc.putString(s("serviceID"), "clio");
  desc.putEnumerated(s("workflowType"), s("genWorkflow"), s("in_painting"));
  var d2 = new ActionDescriptor();
  var d3 = new ActionDescriptor();
  d3.putString(s("gi_PROMPT"), prompt);
  d3.putString(s("gi_MODE"), "ginp");
  d3.putInteger(s("gi_SEED"), -1);
  d3.putInteger(s("gi_NUM_STEPS"), -1);
  d3.putInteger(s("gi_GUIDANCE"), (typeof GUIDANCE !== "undefined") ? GUIDANCE : 6);
  d3.putInteger(s("gi_SIMILARITY"), 0);
  d3.putBoolean(s("gi_CROP"), false);
  d3.putBoolean(s("gi_DILATE"), false);
  d3.putInteger(s("gi_CONTENT_PRESERVE"), 0);
  d3.putBoolean(s("gi_ENABLE_PROMPT_FILTER"), true);
  d3.putBoolean(s("dualCrop"), true);
  d3.putString(s("gi_ADVANCED"), '{"enable_mts":true}');
  d2.putObject(s("clio"), s("clio"), d3);
  desc.putObject(s("serviceOptionsList"), s("target"), d2);
  executeAction(s("syntheticFill"), desc, DialogModes.NO);
}

/** Amplía el lienzo [add] px hacia ARRIBA y rellena la zona nueva + [overlap] px de solape. */
function extendUp(doc, add, overlap, prompt) {
  app.preferences.rulerUnits = Units.PIXELS;
  doc.flatten();
  var W = doc.width.as("px"), H = doc.height.as("px");
  doc.resizeCanvas(UnitValue(W, "px"), UnitValue(H + add, "px"), AnchorPosition.BOTTOMCENTER);
  try { doc.backgroundLayer.isBackgroundLayer = false; } catch (e0) {}
  doc.selection.select([[0, 0], [W, 0], [W, add + overlap], [0, add + overlap]]);
  genFill(doc, prompt);
  try { doc.selection.deselect(); } catch (e1) {}
}
