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
- **Superséries** : menu ⋮ d'un exercice › « Supersérie avec l'exercice suivant ». Les exercices liés
  s'enchaînent sans repos ; le minuteur démarre après le dernier du bloc. Aussi dans les programmes.
- **Séries dégressives** : bouton « Dégressive » → la série est enregistrée sans repos (badge **D**)
  et le poids suivant est proposé à −20 %.
- **Calculateur de disques** : bouton « Disques » → quels disques mettre de chaque côté de la barre,
  avec un dessin de la barre chargée. Barre (20/15/10/7 kg ou sans) et disques disponibles réglables.

### Widget d'écran d'accueil
- Pendant le repos : **compte à rebours en direct** avec boutons « +15 s » et « Passer ».
- Pendant la séance : chrono de la séance, séries, volume et calories.
- Sinon : résumé de la semaine et bouton « Démarrer une séance ».
- Ajout : appui long sur l'écran d'accueil › Widgets › GymTracker.

### Coaching pendant la séance
- **Progression conseillée** : « 💡 82,5 kg × 8 » si tu as réussi toutes tes reps la dernière fois
  (+2,5 kg ; +1,25 kg en isolation ; +5 kg au-delà de 100 kg en jambes/dos), sinon même charge.
  Bouton « Appliquer » pour pré-remplir.
- **Échauffement** : bouton « Échauffement » (badge **É**, exclu du volume, des records et des stats) et
  **échauffement auto** (barre, 40 %, 60 %, 80 % de la charge, arrondis à tes disques).
- **Effort ressenti (RPE 6–10)** après chaque série ; il affine le conseil de progression.
- **Notes** : note de séance (sauvegardée automatiquement) et note fixe par exercice (« siège : 4 »).
- **Nouveau record** : bandeau 🏆 + vibration quand tu bats ton record de poids ou de 1RM estimé.

### Minuteur de repos
- Lancé automatiquement à chaque série validée (durée réglable par exercice).
- **Épinglé en bas de l'écran** sur tous les onglets : plus besoin de remonter pour voir le temps restant.
- **Notification avec compte à rebours** (et boutons −15 s / +15 s / Passer) : le minuteur reste visible
  quand tu changes d'application ou sur l'écran de verrouillage.
- À la fin : **bips + annonce vocale** (« Repos terminé. Série suivante : Squat ») joués **dans les écouteurs**
  (la musique baisse un instant), vibration et notification, **même écran éteint**.
- Avertissement 10 secondes avant la fin ; option « sonner même en mode silencieux » (canal alarme).
- Tout se règle dans **Réglages** (⚙ sur l'accueil), avec un bouton « Tester l'alerte ».

### Calories
- Dépense estimée **par exercice**, **par séance**, sur la semaine (accueil) et dans l'historique.
- Calcul : intensité (MET) de la catégorie × ton poids × temps passé (séries + repos).
- Poids : ta dernière pesée, sinon celui saisi dans les Réglages. C'est une estimation.
- Le **temps de repos réel** entre deux séries est enregistré.

### Historique
- **Calendrier mensuel** : les jours d'entraînement sont mis en évidence ; touche un jour pour filtrer.
- Détail d'une séance : chaque série (poids × reps, 1RM estimé, repos), repos moyen, volume.
- **Correction des séances passées** : modifier, supprimer ou ajouter une série.
- **Export / import CSV** (menu ⋮) : sauvegarde, ou analyse dans Excel / Google Sheets.

### Progression (onglets)
- **Records** : poids max, 1RM estimé, meilleur volume et meilleure charge à 1, 3, 5, 8, 10 et 12 reps.
- **Bilan** semaine / mois : séances, durée, séries, volume, calories (comparés à la période précédente),
  **carte du corps** (muscles travaillés), séries par catégorie, régularité (semaines d'affilée).
- **Objectifs** : charge sur un exercice ou poids de corps, avec échéance et barre de progression
  (aussi affichés sur l'accueil).
- **Corps** : poids, **mensurations** (bras, taille, cuisse…) et **photos de progression** (avant / après).
- Pour chaque exercice : record de poids, 1RM estimé (formule d'Epley), meilleur volume, nombre de séances.
- Graphique de l'évolution par séance : poids max, 1RM estimé, volume ou reps max.
- **Poids de corps** : pesées, graphique et évolution.

### Sauvegarde complète
- Réglages › **Sauvegarde complète** : un fichier ZIP avec toutes les données (séances, programmes,
  catégories, objectifs, mensurations, **photos** et réglages). Choisis Google Drive comme emplacement
  pour l'avoir en ligne. **Restaurer** remplace les données par celles du fichier.
- **Sauvegarde automatique hebdomadaire** dans un dossier du téléphone (garde les 5 dernières).

### Apparence
- Thème **Système / Clair / Sombre** au choix (⚙ Réglages), et option **noir intégral** pour écrans OLED.

### Exercices et catégories
- Bibliothèque pré-remplie (~20 exercices classiques), recherche, classement par catégorie.
- **Catégories personnalisables** (Exercices › Catégories) : ajoute Fessiers, Mollets, Avant-bras…,
  choisis couleur, pictogramme et intensité. Renommer une catégorie met à jour ses exercices ;
  la supprimer les déplace dans « Autre ».
- **Photo par exercice** (ex. la machine ou son réglage), sinon pictogramme coloré du groupe musculaire.
- Temps de repos par défaut par exercice.

## Installer l'application

### Option 1 : télécharger l'APK compilé par GitHub
1. Onglet **Actions** du dépôt → dernier run « Build APK » → section *Artifacts* → `GymTracker-debug-apk`.
2. Dézippe et copie `app-debug.apk` sur ton téléphone.
3. Ouvre-le et autorise l'installation depuis des sources inconnues.

**Clé de signature** : pour que chaque nouvel APK s'installe par-dessus le précédent (sans perdre les
données), le workflow signe l'APK avec une clé personnelle stockée dans deux secrets GitHub
(*Settings › Secrets and variables › Actions*) :

| Secret | Contenu |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | fichier de clé encodé en base64 |
| `SIGNING_PASSWORD` | mot de passe du fichier et de la clé |
| `SIGNING_KEY_ALIAS` | alias de la clé dans le fichier (facultatif, `gymtracker` par défaut) |

La clé n'est plus versionnée dans le dépôt. Sans ces secrets, l'APK est signé avec une clé temporaire
(un avertissement s'affiche dans le run). Pour mettre à jour une installation faite avec les versions 2 et 3
(signées avec l'ancienne clé de debug du dépôt), il faut réutiliser cette clé : alias `androiddebugkey`.

### Option 2 : compiler avec Android Studio
1. Ouvre le dossier du projet dans Android Studio (Ladybug ou plus récent).
2. Branche ton téléphone (mode développeur + débogage USB activés) et clique sur ▶ *Run*.

En ligne de commande (avec le SDK Android installé) :
```bash
./gradlew assembleDebug      # APK dans app/build/outputs/apk/debug/
./gradlew installDebug       # installe directement sur le téléphone branché
```

## Version iPhone 🍏

Le dossier [`web/`](web/) contient une version **iPhone** de GymTracker : une application web installable
(PWA) qui reprend les fonctionnalités de la version Android. Pas besoin de Mac, d'Xcode ni de compte
développeur Apple.

### Mettre l'application en ligne (une seule fois)
1. Sur GitHub : **Settings › Pages › Build and deployment › Source : « GitHub Actions »**.
2. Si besoin, **Settings › Environments › github-pages** : autorise la branche utilisée pour le déploiement
   (par défaut, seule la branche principale peut publier).
3. Le workflow « Publier la version iPhone » se lance à chaque modification de `web/`
   (ou manuellement depuis l'onglet **Actions**). L'adresse s'affiche dans le run, du type
   `https://<utilisateur>.github.io/<dépôt>/`.

### Installer sur l'iPhone
1. Ouvre cette adresse dans **Safari**.
2. Touche **Partager** (carré avec une flèche) › **« Sur l'écran d'accueil »** › **Ajouter**.
3. Lance GymTracker depuis l'icône : plein écran, fonctionne **hors ligne**, données stockées sur le téléphone.

### Récupérer les données de la version Android
Android : Historique › ⋮ › Exporter en CSV → envoie le fichier sur l'iPhone (AirDrop impossible, mais
mail, Drive, iCloud…) → iPhone : Réglages ⚙ › **Importer un CSV**. Le format est identique dans les deux sens.

### Différences avec la version Android
- **Le minuteur ne sonne pas quand l'écran est verrouillé ou l'app en arrière-plan** : iOS met en pause
  les applications web. Le minuteur reste juste (il repart du bon temps à la réouverture), et l'option
  « Garder l'écran allumé pendant la séance » (activée par défaut) évite le problème.
- Pas de vibration (non disponible pour les apps web sur iPhone). Les bips et l'annonce vocale passent dans
  les écouteurs et baissent la musique un instant.
- En plus : **sauvegarde complète** (JSON avec programmes, catégories, photos), à garder dans Fichiers / iCloud :
  supprimer l'icône de l'écran d'accueil efface les données.

Tester en local : `cd web && python3 -m http.server 8000`, puis http://localhost:8000.

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

### Modèle de données (v5)
| Table | Contenu |
|---|---|
| `exercises` | nom, groupe musculaire, repos par défaut, photo |
| `sessions` | début, fin |
| `session_exercises` | exercice réalisé dans une séance (ordre, séries visées, supersérie) |
| `sets` | poids, répétitions, heure, repos réel, dégressive, échauffement, RPE |
| `programs` / `program_exercises` | programmes et leurs exercices |
| `body_weights` | pesées |
| `muscle_groups` | catégories : nom, couleur, pictogramme, intensité (MET) |
| `goals` | objectifs (exercice ou poids de corps), échéance |
| `measurements` / `progress_photos` | mensurations et photos de progression |

Les mises à jour (v1 → v2 → v3 → v4 → v5) conservent toutes les données (migrations testées dans `MigrationTest`).
Tests : `./gradlew testDebugUnitTest`.
