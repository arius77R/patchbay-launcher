Patchbay Launcher installe et démarre Patchbay (BML) et récupère des mods, des appareils et des thèmes pour Bitwig Studio, sans Terminal. La version 0.9.6 range les listes par catégories et favoris, permet de désactiver et de désinstaller, ajoute de nouvelles Sources et prévient quand des mods ont été supprimés ou retirés par leurs auteurs.

Un projet non officiel de Marius R, sans lien avec Bitwig GmbH ni avec l'auteur de Patchbay.

## Mods

Dans Mods, vous choisissez, installez, mettez à jour, désactivez et désinstallez les mods de Patchbay.

- **Panneau latéral :** Tous, Favoris et chaque catégorie avec son icône et son nombre de mods. Un clic filtre la liste. « replie le panneau en icônes, » le rouvre.
- **Favoris :** cliquez sur l'étoile à côté d'un mod. Les favoris font partie de la sauvegarde des réglages.
- **Catégorie :** elle vient du nom du mod (« Catégorie : Nom »). Un mod sans catégorie de la liste va dans Other.
- **Afficher :** Tous, Compatibles, Installés, Mises à jour, Incompatibles et Supprimés du dépôt.
- **Installer :** cochez des mods et cliquez sur « Installer la sélection ». « Tout cocher » ne coche que ceux à installer ou à mettre à jour.
- **Désactiver / Activer :** le mod reste sur le disque, Patchbay ne le charge pas. Cochez-en plusieurs et utilisez le bouton sous la liste.
- **Désinstaller :** supprime le mod du disque, après confirmation. Le lanceur prévient quand un autre mod activé en a besoin.

Pour désactiver et désinstaller, Bitwig et la fenêtre de Patchbay doivent être fermés.

**Les états à connaître :**

| État | Ce que cela veut dire | Que faire |
| --- | --- | --- |
| supprimé | L'auteur a supprimé le mod du dépôt. Il fonctionne toujours, mais ne recevra plus de mises à jour. | Le garder ou le désinstaller. |
| retiré | L'auteur a retiré le mod à cause de problèmes. La raison est dans les détails. Le lanceur demande une fois s'il faut le désactiver. | Mieux vaut le désactiver ou le désinstaller. |
| hors sources | Aucune source activée ne le propose : il est venu avec Patchbay, a été ajouté à la main, ou son dépôt n'existe plus. | Le garder ou le désinstaller. |
| incompatible | Il ne fonctionne pas avec votre Bitwig. | Attendre une nouvelle version. |

## Appareils

Appareils récupère les fichiers d'appareils Bitwig (.bwdevice, .bwmodulator, modules Grid) et les enregistre auprès de Patchbay.

- **Version des fichiers :** le lanceur la choisit pour votre Bitwig. Vous pouvez la changer dans le champ en haut à gauche.
- **Panneau latéral :** chaque type a son icône (Audio FX, Instrument, Note FX, Note generator, Modulator, Grid module). L'étoile ajoute aux Favoris.
- **Télécharger :** cochez et cliquez sur « Télécharger la sélection ».
- **Désactiver / Activer :** le fichier reste sur le disque avec .off ajouté, et Bitwig ne l'affiche pas.
- **Désinstaller :** supprime le fichier du disque.
- **supprimé / retiré :** comme dans Mods. Un fichier que son dépôt n'a plus reste dans la liste, pour pouvoir le désinstaller.

Les changements demandent que Bitwig soit fermé.

## Sources

Les sources sont les dépôts GitHub d'où le lanceur récupère Patchbay, les mods, les appareils et les thèmes. Il y a trois onglets :

| Onglet | Ce qu'il montre | Ce que vous pouvez faire |
| --- | --- | --- |
| Actives | Les sources que vous avez : intégrées et les vôtres. | Activer, désactiver, modifier, retirer, ajouter les vôtres. |
| Liste des sources | Les dépôts de la liste des recommandations et ceux trouvés sur GitHub par le sujet patchbay-mod, patchbay-devices ou patchbay-themes. Ceux que vous avez déjà sont marqués « active ». | Cliquer sur un dépôt et l'ajouter aux actives. |
| Recommandées | Les dépôts de la liste arius77R/bitwig-mods-list que vous n'avez pas encore. Le nombre rouge compte les nouveaux. | Cliquer sur un dépôt et l'ajouter aux actives. |

- **Fiche d'un dépôt :** un clic montre l'auteur, la description, ce qu'il contient (par ex. « Mods : 7 »), la dernière modification, la licence et les étoiles. « Ajouter aux actives » fonctionne quand il y a quelque chose à récupérer. « Page du dépôt » l'ouvre sur GitHub.
- **Actualiser :** sur chaque onglet, les sources, la liste des recommandations et la recherche par sujet sont reprises depuis GitHub, et le lanceur vérifie que les dépôts existent toujours. Un changement de la liste peut mettre quelques minutes à apparaître.
- **n'existe pas :** un badge rouge à côté d'une source dans Actives quand son dépôt a été supprimé, rendu privé ou renommé. La source ne disparaît pas d'elle-même. « Modifier » propose « Page de l'auteur » et « Supprimer la source ».

GitHub autorise 60 requêtes par heure depuis une même adresse. Quand elles sont épuisées, le lanceur montre ce qu'il a obtenu en dernier et ne marque rien comme supprimé.

## Le lanceur, les signalements et la fenêtre

- **La page du lanceur :** cliquez sur « Patchbay Launcher » en haut à gauche. Vous voyez votre version, « Nouveautés » et les informations système. Quand une nouvelle version est sortie, « Mettre à jour » s'y trouve, et « Nouvelle version » à côté du numéro de version dans la barre du haut.
- **Informations système :** les versions du lanceur, du système, de Java, de Bitwig, de Patchbay et la liste des mods, sans chemins ni données personnelles. « Copier » les met dans le presse-papiers.
- **Signaler un problème** (barre du bas) : ouvre un nouveau signalement sur GitHub avec les informations système déjà remplies. Ajoutez ce qui s'est passé et ce qui aurait dû se passer.
- **Avertissement Bitwig :** avant « Lancer Patchbay », le lanceur vérifie que Patchbay prend en charge votre Bitwig. Sinon, il montre les versions prises en charge et demande : Annuler ou Démarrer quand même.
- **Fenêtre :** la première fois, elle s'adapte à l'écran, ensuite elle s'ouvre là où elle était, à la même taille qu'à la fermeture.

## Pour les auteurs de mods, d'appareils et de thèmes

Les règles complètes sont dans [PUBLISHING](https://github.com/arius77R/patchbay-launcher/blob/main/docs/PUBLISHING.md). L'essentiel :

1. **Un ZIP par mod** pour tous les systèmes : `<mod-id>-<version>.zip` dans une release, et `<mod-id>/mod.json` dans le dépôt.
2. **Le nom du mod commence par une catégorie** de la liste fixe, par ex. « Arranger: Add Automation Points ».
3. **Un sujet pour le dépôt,** pour que le lanceur le trouve : ⚙ à côté de About → Topics → `patchbay-mod`, `patchbay-devices` ou `patchbay-themes`.
4. **La liste des recommandations :** demandez l'ajout de votre dépôt à [arius77R/bitwig-mods-list](https://github.com/arius77R/bitwig-mods-list) via Issues ou une pull request.
5. **Retirer à cause de problèmes :** un fichier `withdrawn.json` à la racine du dépôt, avec l'`id` du mod ou le nom du fichier de l'appareil et la raison. Le lanceur affiche « retiré » et demande une fois à l'utilisateur s'il faut le désactiver.
6. **Supprimer simplement** le dossier `<mod-id>/` suffit quand un mod arrive en fin de vie. Les utilisateurs voient alors « supprimé ».
