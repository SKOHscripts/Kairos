// Fonctions navigateur de la version web de Kairos (docs/spec-v3/export-import.md
// § Version web), appelées depuis Kotlin/Wasm (WebInterop.kt). Toutes les
// fonctions asynchrones rendent une Promise ; aucune ne lève d'exception vers
// Kotlin sauf mention contraire.
//
// - OPFS (Origin Private File System) : copie de travail des données, privée au
//   site, sans autorisation à demander.
// - Fichier lié (File System Access, Edge et Chrome) : copie des données dans un
//   vrai fichier choisi par l'utilisateur, à l'abri d'un nettoyage du
//   navigateur. Sa poignée est gardée dans IndexedDB entre deux visites ; le
//   navigateur redemande l'accord à chaque session (sauf « Autoriser à chaque
//   visite »).
(function () {
  "use strict";

  const DB_NAME = "kairos";
  const STORE = "handles";
  const LINK_KEY = "linked-file";
  const JSON_TYPES = [{ description: "Kairos", accept: { "application/json": [".json"] } }];

  async function opfsDir(sub) {
    const root = await navigator.storage.getDirectory();
    return sub ? root.getDirectoryHandle(sub, { create: true }) : root;
  }

  async function writeHandle(handle, text) {
    const w = await handle.createWritable();
    await w.write(text);
    await w.close();
  }

  function idb() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = () => req.result.createObjectStore(STORE);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async function idbDo(mode, action) {
    const db = await idb();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, mode);
      const req = action(tx.objectStore(STORE));
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  const getLink = () => idbDo("readonly", (s) => s.get(LINK_KEY)).catch(() => undefined);
  const setLink = (h) => idbDo("readwrite", (s) => s.put(h, LINK_KEY));

  async function permission(handle, request) {
    const opts = { mode: "readwrite" };
    let state = await handle.queryPermission(opts);
    if (state !== "granted" && request) state = await handle.requestPermission(opts);
    return state;
  }

  globalThis.KairosWeb = {
    // --- OPFS -------------------------------------------------------------------
    async opfsRead(name) {
      try {
        const h = await (await opfsDir()).getFileHandle(name);
        return await (await h.getFile()).text();
      } catch (e) {
        return null;
      }
    },
    // Lève en cas d'échec : l'appelant le signale.
    async opfsWrite(name, text) {
      const h = await (await opfsDir()).getFileHandle(name, { create: true });
      await writeHandle(h, text);
    },
    // Sauvegarde datée dans backups/, garde les `keep` plus récentes. Lève en cas d'échec.
    async opfsBackup(name, text, keep) {
      const dir = await opfsDir("backups");
      await writeHandle(await dir.getFileHandle(name, { create: true }), text);
      const names = [];
      for await (const key of dir.keys()) names.push(key);
      names.sort().reverse();
      for (const old of names.slice(keep)) await dir.removeEntry(old);
    },
    async persist() {
      try {
        return navigator.storage && navigator.storage.persist ? await navigator.storage.persist() : false;
      } catch (e) {
        return false;
      }
    },

    // --- Fichiers ponctuels (export, import) ------------------------------------
    download(name, text) {
      const url = URL.createObjectURL(new Blob([text], { type: "application/json" }));
      const a = document.createElement("a");
      a.href = url;
      a.download = name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    },
    pickText() {
      return new Promise((resolve) => {
        const input = document.createElement("input");
        input.type = "file";
        input.accept = ".json,application/json";
        input.onchange = async () => {
          const f = input.files && input.files[0];
          resolve(f ? await f.text() : null);
        };
        input.oncancel = () => resolve(null);
        input.click();
      });
    },

    // --- Fichier lié ------------------------------------------------------------
    linkSupported() {
      return typeof window.showSaveFilePicker === "function" && typeof window.showOpenFilePicker === "function";
    },
    // "none" | "granted:<nom>" | "prompt:<nom>"
    async linkState() {
      const h = await getLink();
      if (!h) return "none";
      try {
        return (await permission(h, false)) === "granted" ? "granted:" + h.name : "prompt:" + h.name;
      } catch (e) {
        return "none";
      }
    },
    // Nouveau fichier : écrit `text`, garde la poignée. Rend le nom, ou null si annulé.
    async linkCreate(suggestedName, text) {
      let h;
      try {
        h = await window.showSaveFilePicker({ suggestedName, types: JSON_TYPES });
      } catch (e) {
        return null;
      }
      await writeHandle(h, text);
      await setLink(h);
      return h.name;
    },
    // Fichier existant : rend "<nom>\n<contenu>", ou null si annulé. La poignée
    // n'est gardée qu'après confirmation (linkAdopt).
    async linkOpen() {
      let handles;
      try {
        handles = await window.showOpenFilePicker({ types: JSON_TYPES, multiple: false });
      } catch (e) {
        return null;
      }
      const h = handles[0];
      globalThis.KairosWeb._pending = h;
      return h.name + "\n" + (await (await h.getFile()).text());
    },
    async linkAdopt() {
      const h = globalThis.KairosWeb._pending;
      globalThis.KairosWeb._pending = null;
      if (!h) return false;
      if ((await permission(h, true)) !== "granted") return false;
      await setLink(h);
      return true;
    },
    // Demande l'accord (geste de l'utilisateur requis). Rend true si accordé.
    async linkAuthorize() {
      const h = await getLink();
      if (!h) return false;
      try {
        return (await permission(h, true)) === "granted";
      } catch (e) {
        return false;
      }
    },
    async linkRead() {
      const h = await getLink();
      if (!h || (await permission(h, false)) !== "granted") return null;
      return await (await h.getFile()).text();
    },
    // Rend true si écrit, false si l'accord manque ou si l'écriture échoue.
    async linkWrite(text) {
      const h = await getLink();
      if (!h) return false;
      try {
        if ((await permission(h, false)) !== "granted") return false;
        await writeHandle(h, text);
        return true;
      } catch (e) {
        return false;
      }
    },
  };
})();
