# GymTracker 🏋️

Application Android pour t'accompagner à la salle de sport : suivi des séries, répétitions, poids,
temps de repos, et de ta progression au fil des séances. Tout est stocké localement sur le téléphone
(aucun compte, aucune connexion requise).

## Fonctionnalités

### Séance en cours
- Démarre une **séance libre** ou un de tes **programmes** (exercices et nombre de séries déjà prêts).
- Pour chaque exercice : saisie du **poids** et des **répétitions** avec boutons −/+ (pas de 2,5 kg / 1 rep).
- Les valeurs sont **pré-remplies** avec ta dernière série, ou avec celles de la séance précédente.
- Colonne **« Précédent »** : ce que tu as fait la dernière fois sur la même série, pour savoir quoi battre.
- Objectif de séries (ex. 3/4) avec barre de progression quand la séance vient d'un programme.
- **Touche une série pour la modifier ou la supprimer.**
- « Enregistrer comme programme » depuis le menu ⋮ pour réutiliser la séance.

### Minuteur de repos
- Lancé automatiquement à chaque série validée (durée réglable par exercice).
- **Épinglé en bas de l'écran** sur tous les onglets : plus besoin de remonter pour voir le temps restant.
- **Notification avec compte à rebours** (et boutons −15 s / +15 s / Passer) : le minuteur reste visible
  quand tu changes d'application ou sur l'écran de verrouillage.
- Vibration, bip et notification « Repos terminé » à la fin, **même écran éteint**.
- Le **temps de repos réel** entre deux séries est enregistré.

### Historique
- **Calendrier mensuel** : les jours d'entraînement sont mis en évidence ; touche un jour pour filtrer.
- Détail d'une séance : chaque série (poids × reps, 1RM estimé, repos), repos moyen, volume.
- **Correction des séances passées** : modifier, supprimer ou ajouter une série.
- **Export / import CSV** (menu ⋮) : sauvegarde, ou analyse dans Excel / Google Sheets.

### Progression
- Pour chaque exercice : record de poids, 1RM estimé (formule d'Epley), meilleur volume, nombre de séances.
- Graphique de l'évolution par séance : poids max, 1RM estimé, volume ou reps max.
- **Poids de corps** : pesées, graphique et évolution.

### Exercices
- Bibliothèque pré-remplie (~20 exercices classiques), recherche.
- **Photo par exercice** (ex. la machine ou son réglage), sinon pictogramme coloré du groupe musculaire.
- Temps de repos par défaut par exercice.

## Installer l'application

### Option 1 : télécharger l'APK compilé par GitHub
1. Onglet **Actions** du dépôt → dernier run « Build APK » → section *Artifacts* → `GymTracker-debug-apk`.
2. Dézippe et copie `app-debug.apk` sur ton téléphone.
3. Ouvre-le et autorise l'installation depuis des sources inconnues.

### Option 2 : compiler avec Android Studio
1. Ouvre le dossier du projet dans Android Studio (Ladybug ou plus récent).
2. Branche ton téléphone (mode développeur + débogage USB activés) et clique sur ▶ *Run*.

En ligne de commande (avec le SDK Android installé) :
```bash
./gradlew assembleDebug      # APK dans app/build/outputs/apk/debug/
./gradlew installDebug       # installe directement sur le téléphone branché
```

## Architecture technique

- **Kotlin** + **Jetpack Compose** (Material 3), minSdk 26 (Android 8.0).
- **Room** (SQLite) pour la persistance, exposée en `Flow` pour une UI réactive.
- **ViewModel** + `StateFlow`, navigation avec **Navigation Compose**.
- Graphiques dessinés avec le `Canvas` de Compose (aucune librairie externe).

```
app/src/main/java/com/gymtracker/
├── data/            # Entités Room, DAO, base, repository, calculs de stats
├── timer/           # Minuteur de repos, service au premier plan, notifications
├── util/            # Formatage (dates, poids, durées)
└── ui/
    ├── workout/     # Accueil et séance en cours
    ├── programs/    # Éditeur de programmes
    ├── history/     # Historique et détail d'une séance
    ├── progress/    # Graphiques et records
    ├── exercises/   # Gestion des exercices
    ├── components/  # Composants partagés (graphique, stepper, dialogues)
    └── theme/
```

### Modèle de données (v2)
| Table | Contenu |
|---|---|
| `exercises` | nom, groupe musculaire, repos par défaut, photo |
| `sessions` | début, fin |
| `session_exercises` | exercice réalisé dans une séance (ordre, séries visées) |
| `sets` | poids, répétitions, heure, repos réel avant la série |
| `programs` / `program_exercises` | programmes et leurs exercices |
| `body_weights` | pesées |

La mise à jour v1 → v2 conserve toutes les données (migration testée dans `MigrationTest`).
Tests : `./gradlew testDebugUnitTest`.

## Idées pour la suite
- Superséries et séries dégressives.
- Calcul automatique des disques à charger sur la barre.
- Widget d'écran d'accueil.
