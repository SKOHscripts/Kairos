// Copie sql.js (sql-wasm.js et sql-wasm.wasm, paquet npm déclaré dans
// webApp/build.gradle.kts) à côté de kairos.js : le worker
// kairos-sqljs.worker.js les charge par chemin relatif
// (docs/spec-v3/export-import.md § Version web).
const CopyWebpackPlugin = require("copy-webpack-plugin");
const path = require("path");
const sqljs = path.dirname(require.resolve("sql.js/dist/sql-wasm.js"));
config.plugins.push(
  new CopyWebpackPlugin({
    patterns: [
      { from: path.join(sqljs, "sql-wasm.js") },
      { from: path.join(sqljs, "sql-wasm.wasm") },
    ],
  }),
);
