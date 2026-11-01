# GymTracker 🏋️

Application Android pour t'accompagner à la salle de sport : suivi des séries, répétitions, poids,
temps de repos, et de ta progression au fil des séances. Tout est stocké localement sur le téléphone
(aucun compte, aucune connexion requise).

## Fonctionnalités

### Séance en cours
- Démarre une séance et ajoute tes exercices (recherche, ou création à la volée).
- Pour chaque exercice : saisie du **poids** et des **répétitions** avec boutons −/+ (pas de 2,5 kg / 1 rep).
- Les valeurs sont **pré-remplies** avec ta dernière série, ou avec celles de la séance précédente.
- Colonne **« Précédent »** : ce que tu as fait la dernière fois sur la même série, pour savoir quoi battre.
- **Minuteur de repos** lancé automatiquement à chaque série validée (durée réglable par exercice),
  avec −15 s / +15 s / Passer, vibration et bip à la fin.
- Le **temps de repos réel** entre deux séries est enregistré et affiché.
- Chrono de la séance, nombre de séries et volume total en direct. L'écran reste allumé pendant la séance.

### Historique
- Liste des séances terminées : date, durée, nombre de séries, volume, exercices.
- Détail d'une séance : chaque série (poids × reps, 1RM estimé, repos), repos moyen, volume.

### Progression
- Pour chaque exercice : record de poids, 1RM estimé (formule d'Epley), meilleur volume, nombre de séances.
- Graphique de l'évolution par séance : poids max, 1RM estimé, volume ou reps max.
- Tableau séance par séance avec écart (▲/▼) par rapport à la séance précédente.

### Exercices
- Bibliothèque pré-remplie (~20 exercices classiques, groupés par muscle).
- Ajout / modification / suppression, temps de repos par défaut par exercice.

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
├── timer/           # Minuteur de repos (vibration + bip)
├── util/            # Formatage (dates, poids, durées)
└── ui/
    ├── workout/     # Séance en cours
    ├── history/     # Historique et détail d'une séance
    ├── progress/    # Graphiques et records
    ├── exercises/   # Gestion des exercices
    ├── components/  # Composants partagés (graphique, stepper, dialogues)
    └── theme/
```

### Modèle de données
| Table | Contenu |
|---|---|
| `exercises` | nom, groupe musculaire, repos par défaut |
| `sessions` | début, fin |
| `session_exercises` | exercice réalisé dans une séance (ordre) |
| `sets` | poids, répétitions, heure, repos réel avant la série |

## Idées pour la suite
- Notification quand le repos est terminé avec l'écran éteint (service au premier plan).
- Programmes / modèles de séance (ex. « Push », « Pull », « Legs »).
- Export / import (CSV) et sauvegarde.
- Suivi du poids de corps et des mensurations.
