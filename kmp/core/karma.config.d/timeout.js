// Tests wasm de core dans Chrome sans tête (docs/spec/estimateur-modele.md § Déterminisme) : les tests de l'estimateur
// construisent des modèles sur des centaines de tâches ; le délai de 2 s par test de Mocha y est trop court sur un runner.
config.set({
  client: {
    mocha: {
      timeout: 30000
    }
  }
});
