---
name: kairos-spec
description: Applique le workflow « spécification d'abord » du dépôt Kairos à tout changement (feature, correctif, refactor) - rédiger ou compléter la spec de domaine dans docs/spec/ avant de coder, puis la remettre bijective avec le code, consigner les micro-décisions, et décider si le README est concerné. À utiliser avant d'implémenter quoi que ce soit dans kmp/, quand on écrit ou relit une spec, ou quand on vérifie qu'une spec et le code disent la même chose.
---

# Workflow spécification d'abord (Kairos)

La règle complète est dans `CLAUDE.md` à la racine. Cette skill la rend
opérationnelle. Suivre les étapes **dans l'ordre**.

## 0. Chercher avant de concevoir

Ne jamais re-trancher une décision déjà consignée.

1. `docs/spec/README.md` : trouver le ou les domaines concernés.
2. Chercher le sujet dans les specs et dans le code :
   `grep -rn "<mot-clé>" docs/spec/ kmp/*/src/commonMain/`
   (les commentaires de code portent le « pourquoi » local).
3. Lire les sections « Décisions et pièges tracés » et « Hors périmètre » du
   domaine. Si la demande contredit une décision, **le dire** à
   l'utilisateur et proposer de rouvrir explicitement la décision ; ne pas
   la contourner en silence.

## 1. Spécifier

- Petit changement : compléter la spec existante du domaine.
- Chantier : nouvelle spec `docs/spec/<domaine>.md`, ajoutée à l'index
  `docs/spec/README.md`.
- Gabarit obligatoire (deux parties, dans cet ordre) :

```markdown
# <Titre du domaine>

_Rôle : <une phrase>. Fichiers couverts : <chemins>. Tests : <classes>._

État : <jalon / issue>, <implémentée | spécifiée le AAAA-MM-JJ, non implémentée>.

## 1. Besoin métier (cahier des charges)
### Objectif / problème
### Comportement attendu (utilisateur)
### Critères de succès        <- vérifiables, idéalement nommés par leur test
### Hors périmètre / différé

## 2. Solution technique
### <Blocs : modèle, calcul (core), stockage (data), interface (ui)…>
### Décisions et pièges tracés   <- avec les alternatives écartées
```

- Partie 1 : le « quoi » et le « pourquoi », sans nom de classe. Textes
  d'interface cités entre guillemets français.
- Partie 2 : le « comment » réel : fichiers, fonctions, invariants.
- Une spec **non encore implémentée** porte « spécifiée le …, non
  implémentée » et ne modifie **pas** les specs existantes : elle liste en
  fin de document « Impacts sur les specs existantes (à reporter à
  l'implémentation) ». C'est ce qui garde le registre bijectif.

## 2. Implémenter

Respecter `CLAUDE.md` (pureté de `core`, textes FR + EN, design system) ;
pour un écran, suivre la skill `kairos-ecran`.

## 3. Tracer dans la spec (bijectivité)

Checklist avant de pousser :

- [ ] Chaque comportement ajouté ou modifié est décrit (partie 1 **et**
      partie 2).
- [ ] Rien dans la spec ne décrit du code inexistant : relire chaque
      affirmation touchée et vérifier le nom de fichier, de fonction, la
      valeur par défaut, la borne (`grep` dans `kmp/`).
- [ ] L'« État » est à jour (non implémentée → jalon livré).
- [ ] Les « Impacts sur les specs existantes » ont été reportés dans ces
      specs, puis retirés de la liste.
- [ ] Les micro-décisions prises en codant (seuil, arrondi, cas limite,
      piège de bibliothèque) sont dans « Décisions et pièges tracés », et le
      commentaire de code correspondant porte le « pourquoi » local.
- [ ] Les critères de succès nomment les tests qui les prouvent, et ces
      tests existent.
- [ ] L'index `docs/spec/README.md` est à jour.
- [ ] Un écart spec/code constaté en passant est corrigé dans le même
      changement (spec ou code, selon ce qui fait foi : le code fait foi
      pour un comportement voulu et testé).

## 4. README : seulement si c'est une feature

- Fonctionnalité visible par l'utilisateur → `README.md` (FR), en termes
  d'usage.
- Correctif, refactor, décision technique → **pas** de README, seulement la
  spec.
- Version publiée → notes Fastlane FR et EN
  (`fastlane/metadata/android/*/changelogs/<versionCode>.txt`).

## Style des specs

- Français, phrases courtes, pas de remplissage. Apostrophe droite dans les
  docs ; les textes d'interface cités suivront l'apostrophe typographique
  `’` dans les ressources.
- Valeurs par défaut et bornes toujours écrites (« 5 par défaut, ≥ 1 »).
- Une décision = ce qui a été choisi + pourquoi + ce qui a été écarté.
