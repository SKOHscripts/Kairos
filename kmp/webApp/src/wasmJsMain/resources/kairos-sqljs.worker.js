// Worker SQLite de la version web (docs/spec/export-import.md § Version web).
//
// Même protocole que @cashapp/sqldelight-sqljs-worker (SQLDelight, Apache 2.0),
// réécrit pour une seule raison : celui-ci cherche sql-wasm.wasm à la RACINE du
// site (« /sql-wasm.wasm »), introuvable quand Kairos est servi dans un
// sous-dossier (GitHub Pages : /Kairos/app/). Ici, sql.js (sql-wasm.js et
// sql-wasm.wasm, copiés à côté par webpack.config.d/sqljs.js) est chargé à
// côté du worker.
//
// La base est en mémoire : la persistance est faite côté Kotlin (instantané
// JSON dans l'OPFS et le fichier lié), pas par ce worker.
importScripts("sql-wasm.js");

let db = null;
const ready = initSqlJs({ locateFile: (file) => file }).then((SQL) => {
  db = new SQL.Database();
});

function handle(data) {
  switch (data && data.action) {
    case "exec":
      if (!data.sql) throw new Error("exec: requête absente");
      return { id: data.id, results: db.exec(data.sql, data.params)[0] ?? { values: [] } };
    case "begin_transaction":
      return { id: data.id, results: db.exec("BEGIN TRANSACTION;") };
    case "end_transaction":
      return { id: data.id, results: db.exec("END TRANSACTION;") };
    case "rollback_transaction":
      return { id: data.id, results: db.exec("ROLLBACK TRANSACTION;") };
    default:
      throw new Error("Action non prise en charge : " + (data && data.action));
  }
}

self.onmessage = (event) =>
  ready
    .then(() => postMessage(handle(event.data)))
    .catch((err) => postMessage({ id: event.data && event.data.id, error: String(err && err.message ? err.message : err) }));
