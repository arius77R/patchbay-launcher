Patchbay Launcher installe et démarre Patchbay (BML) et récupère des mods, des appareils et des thèmes pour Bitwig Studio, sans Terminal. Il range les listes par catégories et favoris, permet de désactiver et de désinstaller, ajoute de nouvelles Sources et prévient quand des mods ont été supprimés ou retirés par leurs auteurs.

Un projet non officiel de Marius R, sans lien avec Bitwig GmbH ni avec l'auteur de Patchbay.

## Patchbay

Dans l'onglet Patchbay, vous installez, mettez à jour et changez de version de Patchbay. Vos mods et thèmes passent seuls à la nouvelle version.

- **Restaurer les mods de Patchbay :** le bouton apparaît quand un mod venu avec Patchbay manque, par ex. après la désinstallation d'un framework. Le lanceur télécharge à nouveau la même version et n'ajoute que les mods manquants, activés. Rien n'est écrasé. Bitwig et la fenêtre Patchbay doivent être fermés.

## Mods

Dans Mods, vous choisissez, installez, mettez à jour, désactivez et désinstallez les mods de Patchbay.

- **Panneau latéral :** Tous, Favoris et chaque catégorie avec son icône et son nombre de mods. Un clic filtre la liste. « replie le panneau en icônes, » le rouvre.
- **Favoris :** cliquez sur l'étoile à côté d'un mod. Les favoris font partie de la sauvegarde des réglages.
- **Catégorie :** elle vient du nom du mod (« Catégorie : Nom »). Un mod sans catégorie de la liste va dans Other.
- **Afficher :** Tous, Compatibles, Installés, Mises à jour, Incompatibles, Supprimés du dépôt et Hors sources. Pour retirer d'un coup tous les mods hors sources : Afficher → Hors sources, « Tout cocher », puis « Désinstaller ».
- **Installer :** cochez des mods et cliquez sur « Installer la sélection ». « Tout cocher » coche les mods à installer ou à mettre à jour. Quand la liste n'a rien à installer (par ex. Afficher → Hors sources ou Supprimés du dépôt), il coche tous les mods installés, sauf ceux de Patchbay. Les coches restent quand vous changez de catégorie, d'affichage ou de recherche : vous pouvez choisir des mods de plusieurs catégories et les installer ensemble.
- **Désactiver / Activer :** le mod reste sur le disque, Patchbay ne le charge pas. Cochez-en un ou plusieurs et utilisez le bouton sous la liste.
- **Désinstaller :** supprime les mods cochés du disque, après confirmation. Le lanceur prévient quand un autre mod activé en a besoin.
- « Désactiver » et « Désinstaller » sont seulement sous la liste. Ils restent gris tant qu'aucun mod n'est coché.

Pour désactiver et désinstaller, Bitwig et la fenêtre de Patchbay doivent être fermés.

**Les états à connaître :**

| État | Ce que cela veut dire | Que faire |
| --- | --- | --- |
| de Patchbay | Le mod est venu avec Patchbay (frameworks : Actions and Menus, Browser, Themes, Nitro Devices et d'autres). D'autres mods, appareils et thèmes en ont besoin. | Le garder. Il est mis à jour avec Patchbay. |
| supprimé | L'auteur a supprimé le mod du dépôt. Il fonctionne toujours, mais ne recevra plus de mises à jour. | Le garder ou le désinstaller. |
| retiré | L'auteur a retiré le mod à cause de problèmes. La raison est dans les détails. Le lanceur demande une fois s'il faut le désactiver. | Mieux vaut le désactiver ou le désinstaller. |
| hors sources | Aucune source activée ne le propose : il a été ajouté à la main, ou son dépôt n'existe plus. | Le garder ou le désinstaller. |
| incompatible | Il ne fonctionne pas avec votre Bitwig. | Attendre une nouvelle version. |

## Appareils

Appareils récupère les fichiers d'appareils Bitwig (.bwdevice, .bwmodulator, modules Grid) et les enregistre auprès de Patchbay.

- **Version des fichiers :** le lanceur la choisit pour votre Bitwig. Vous pouvez la changer dans le champ en haut à gauche.
- **Panneau latéral :** chaque type a son icône (Audio FX, Instrument, Note FX, Note generator, Modulator, Grid module). L'étoile ajoute aux Favoris.
- **Télécharger :** cochez et cliquez sur « Télécharger la sélection ».
- **Désactiver / Activer :** le fichier reste sur le disque avec .off ajouté, et Bitwig ne l'affiche pas. Les boutons sont sous la liste et agissent sur les appareils cochés.
- **Désinstaller :** supprime le fichier du disque.
- **supprimé / retiré :** comme dans Mods. Un fichier que son dépôt n'a plus reste dans la liste, pour pouvoir le désinstaller.

Les changements demandent que Bitwig soit fermé.

## Fichiers qui ne sont plus dans le dépôt

Le lanceur montre aussi les mods et appareils présents sur votre disque qu'aucune source ne propose plus :

- **supprimé :** l'auteur les a retirés du dépôt ;
- **hors sources :** ils ont été ajoutés à la main, ou la source n'existe plus.

Ils fonctionnent toujours, mais ne recevront pas de mises à jour. Si vous n'en voulez pas :

**Mods**

1. Quittez Bitwig et la fenêtre Patchbay.
2. Afficher → Supprimés du dépôt ou Hors sources. Pour n'en choisir que certains, tapez un nom dans Rechercher.
3. Cochez-les, ou cliquez sur « Tout cocher ».
4. Cliquez sur « Désinstaller » sous la liste et confirmez. Le lanceur liste ce qu'il supprime et prévient si un autre mod en a besoin.

Les mods de Patchbay n'apparaissent pas dans ces vues. Si vous en cochez un vous-même, le lanceur prévient avant de le supprimer.

**Appareils**

1. Quittez Bitwig.
2. Triez par « État » : les supprimés sont en haut.
3. Cochez-les et cliquez sur « Désinstaller » sous la liste.

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

## Quand quelque chose ne va pas

1. **Actualiser :** dans Mods, Appareils, Thèmes ou Sources. Cela suffit en général.
2. **Restaurer les mods de Patchbay** (onglet Patchbay), quand des mods, appareils ou thèmes ne marchent plus après une désinstallation.
3. **Vider la mémoire GitHub** (Réglages). En dernier recours, quand après « Actualiser » le lanceur continue à afficher un mod, un appareil ou un thème que le dépôt n'a plus, ne voit pas une nouvelle version déjà sur GitHub, ou affiche des erreurs en chargeant les listes. Il efface les listes gardées et télécharge tout à nouveau. Les mods, appareils, thèmes et réglages installés restent. Il consomme une partie de la limite de GitHub (60 requêtes par heure), donc ne l'utilisez pas souvent. Il ne supprime pas les fichiers hors sources ; c'est le rôle de « Désinstaller ».
4. **Signaler un problème,** si rien de tout cela n'a aidé.

## Pour les auteurs de mods, d'appareils et de thèmes

Les règles complètes sont dans [PUBLISHING](https://github.com/arius77R/patchbay-launcher/blob/main/docs/PUBLISHING.md). L'essentiel :

1. **Un ZIP par mod** pour tous les systèmes : `<mod-id>-<version>.zip` dans une release, et `<mod-id>/mod.json` dans le dépôt.
2. **Le nom du mod commence par une catégorie** de la liste fixe, par ex. « Arranger: Add Automation Points ».
3. **Un sujet pour le dépôt,** pour que le lanceur le trouve : ⚙ à côté de About → Topics → `patchbay-mod`, `patchbay-devices` ou `patchbay-themes`.
4. **La liste des recommandations :** demandez l'ajout de votre dépôt à [arius77R/bitwig-mods-list](https://github.com/arius77R/bitwig-mods-list) via Issues ou une pull request.
5. **Retirer à cause de problèmes :** un fichier `withdrawn.json` à la racine du dépôt, avec l'`id` du mod ou le nom du fichier de l'appareil et la raison. Le lanceur affiche « retiré » et demande une fois à l'utilisateur s'il faut le désactiver.
6. **Supprimer simplement** le dossier `<mod-id>/` suffit quand un mod arrive en fin de vie. Les utilisateurs voient alors « supprimé ».
