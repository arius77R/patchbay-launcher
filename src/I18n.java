import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Interface languages: Polish, English, German, French. The language follows the system unless
 * the user picks one; the pick is kept in settings.properties in the launcher's folder.
 * Polish and English texts are written at the call site (PatchbayLauncher.t); German and French
 * are looked up here by the English text, and fall back to English when a text is missing.
 */
final class I18n {
    private I18n() { }

    static final List<String> CODES = List.of("pl", "en", "de", "fr");
    static final String AUTO = "auto";
    private static String lang;

    static Path settingsFile() { return PatchbayLauncher.data().resolve("settings.properties"); }

    /** The user's pick: auto, pl, en, de or fr. */
    static String choice() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(settingsFile())) {
            p.load(in);
        } catch (IOException ignored) { }
        String v = p.getProperty("language", AUTO);
        return CODES.contains(v) ? v : AUTO;
    }

    static void choose(String code) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(settingsFile())) {
            p.load(in);
        } catch (IOException ignored) { }
        p.setProperty("language", CODES.contains(code) ? code : AUTO);
        Files.createDirectories(settingsFile().getParent());
        try (OutputStream out = Files.newOutputStream(settingsFile())) {
            p.store(out, "Patchbay Launcher");
        }
        lang = null;
    }

    /** Any other remembered setting; null when it was never set. */
    static String setting(String key) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(settingsFile())) {
            p.load(in);
        } catch (IOException ignored) { }
        return p.getProperty(key);
    }

    static void remember(String key, String value) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(settingsFile())) {
            p.load(in);
        } catch (IOException ignored) { }
        p.setProperty(key, value);
        try {
            Files.createDirectories(settingsFile().getParent());
            try (OutputStream out = Files.newOutputStream(settingsFile())) {
                p.store(out, "Patchbay Launcher");
            }
        } catch (IOException ignored) { }
    }

    /** The language in use: the pick, else the system language if it is one of ours, else English. */
    static String lang() {
        if (lang == null) {
            String c = choice();
            if (c.equals(AUTO)) {
                String system = Locale.getDefault().getLanguage();
                c = CODES.contains(system) ? system : "en";
            }
            lang = c;
        }
        return lang;
    }

    static String t(String pl, String en) {
        return switch (lang()) {
            case "pl" -> pl;
            case "de" -> other(en, 0);
            case "fr" -> other(en, 1);
            default -> en;
        };
    }

    private static String other(String en, int index) {
        String[] found = TEXTS.get(en);
        return found == null ? en : found[index];
    }

    private static final Map<String, String[]> TEXTS = new HashMap<>();
    private static void add(String en, String de, String fr) { TEXTS.put(en, new String[] { de, fr }); }

    static {
        // first tab
        add("Start Patchbay", "Patchbay starten", "Lancer Patchbay");
        add("Mods", "Mods", "Mods");
        add("Patchbay isn't installed yet.", "Patchbay ist noch nicht installiert.", "Patchbay n'est pas encore installé.");
        add("Patchbay isn't installed.", "Patchbay ist nicht installiert.", "Patchbay n'est pas installé.");
        add("Installed version: ", "Installierte Version: ", "Version installée : ");
        add("Latest version: ", "Neueste Version: ", "Dernière version : ");
        add("Update available: ", "Update verfügbar: ", "Mise à jour disponible : ");
        add("You have the latest version.", "Du hast die neueste Version.", "Vous avez la dernière version.");
        add("Install", "Installieren", "Installer");
        add("Installed", "Installiert", "Installée");
        add("Update", "Aktualisieren", "Mettre à jour");
        add("Switch to this version", "Zu dieser Version wechseln", "Passer à cette version");
        add("Checking for updates…", "Suche nach Updates…", "Recherche de mises à jour…");
        add("Couldn't check for updates.", "Updates konnten nicht geprüft werden.", "Impossible de vérifier les mises à jour.");
        add("A new Patchbay version is available: ", "Eine neue Patchbay-Version ist verfügbar: ", "Une nouvelle version de Patchbay est disponible : ");
        add("Quit Bitwig Studio and the Patchbay window, then try again.",
            "Beende Bitwig Studio und das Patchbay-Fenster und versuche es dann erneut.",
            "Quittez Bitwig Studio et la fenêtre Patchbay, puis réessayez.");
        add("Downloading ", "Download ", "Téléchargement de ");
        add("Unpacking…", "Entpacken…", "Décompression…");
        add("Carrying over mods and themes…", "Mods und Themes werden übernommen…", "Reprise des mods et des thèmes…");
        add("Done. Active version: ", "Fertig. Aktive Version: ", "Terminé. Version active : ");
        add("Patchbay is already running.", "Patchbay läuft bereits.", "Patchbay est déjà en cours d'exécution.");
        add("Starting Patchbay…", "Patchbay wird gestartet…", "Lancement de Patchbay…");
        add("Language:", "Sprache:", "Langue :");
        add("System language", "Systemsprache", "Langue du système");
        add("Add to Dock", "Zum Dock hinzufügen", "Ajouter au Dock");
        add("Add Patchbay Launcher to the Dock, so it's one click away?",
            "Patchbay Launcher zum Dock hinzufügen, damit er mit einem Klick erreichbar ist?",
            "Ajouter Patchbay Launcher au Dock pour l'ouvrir en un clic ?");
        add("Patchbay Launcher is in the Dock now.", "Patchbay Launcher ist jetzt im Dock.", "Patchbay Launcher est maintenant dans le Dock.");
        add("Couldn't add it to the Dock. Drag Patchbay Launcher from Applications onto the Dock instead.",
            "Das Hinzufügen zum Dock hat nicht geklappt. Ziehe Patchbay Launcher stattdessen aus Programme ins Dock.",
            "Impossible de l'ajouter au Dock. Faites plutôt glisser Patchbay Launcher depuis Applications vers le Dock.");
        add("Move Patchbay Launcher to the Applications folder first, then add it to the Dock.",
            "Verschiebe Patchbay Launcher zuerst in den Ordner Programme und füge ihn dann zum Dock hinzu.",
            "Déplacez d'abord Patchbay Launcher dans le dossier Applications, puis ajoutez-le au Dock.");
        // errors
        add("No macOS download in the release list.", "In der Release-Liste gibt es keinen Download für macOS.", "Aucun téléchargement pour macOS dans la liste des versions.");
        add("GitHub is rate-limiting this address. Try again in an hour.",
            "GitHub begrenzt gerade die Anfragen von dieser Adresse. Versuche es in einer Stunde erneut.",
            "GitHub limite temporairement les requêtes depuis cette adresse. Réessayez dans une heure.");
        add("Download failed: HTTP ", "Download fehlgeschlagen: HTTP ", "Échec du téléchargement : HTTP ");
        add("The download is incomplete.", "Der Download ist unvollständig.", "Le téléchargement est incomplet.");
        add("The archive is damaged (unzip: ", "Das Archiv ist beschädigt (unzip: ", "L'archive est endommagée (unzip : ");
        add("No Patchbay in the archive (bml-ui.sh missing).", "Im Archiv ist kein Patchbay (bml-ui.sh fehlt).", "Pas de Patchbay dans l'archive (bml-ui.sh manquant).");
        add("the SHA-256 doesn't match, the file was refused.", "die SHA-256-Prüfsumme stimmt nicht, die Datei wurde abgelehnt.", "la somme SHA-256 ne correspond pas, le fichier a été refusé.");
        // devices tab
        add("Devices", "Geräte", "Périphériques");
        add("Device", "Gerät", "Périphérique");
        add("Type", "Typ", "Type");
        add("What it does", "Was es macht", "Ce qu'il fait");
        add("downloaded", "geladen", "téléchargé");
        add("Build:", "Dateiversion:", "Version des fichiers :");
        add("Show folder", "Ordner zeigen", "Afficher le dossier");
        add("Project page", "Projektseite", "Page du projet");
        add("Loading the device list…", "Geräteliste wird geladen…", "Chargement de la liste des périphériques…");
        add("Bitwig's version wasn't detected, pick the matching one.", "Die Bitwig-Version wurde nicht erkannt, wähle die passende.", "La version de Bitwig n'a pas été détectée, choisissez celle qui convient.");
        add("Detected Bitwig: ", "Erkanntes Bitwig: ", "Bitwig détecté : ");
        add("Download ticked (", "Ausgewählte laden (", "Télécharger la sélection (");
        add("Quit Bitwig Studio, then try again.", "Beende Bitwig Studio und versuche es dann erneut.", "Quittez Bitwig Studio, puis réessayez.");
        add("Downloading: ", "Download: ", "Téléchargement : ");
        add("Done. In Patchbay tick \"Framework: Nitro Devices\" and click Load.",
            "Fertig. Aktiviere in Patchbay „Framework: Nitro Devices“ und klicke auf Load.",
            "Terminé. Dans Patchbay, cochez « Framework: Nitro Devices » et cliquez sur Load.");
        add("the file differs from the listed version. Click Refresh and try again.",
            "die Datei weicht von der gelisteten Version ab. Klicke auf Neu laden und versuche es erneut.",
            "le fichier diffère de la version listée. Cliquez sur Actualiser et réessayez.");
        add("Patchbay's folder list can't be read: ", "Patchbays Ordnerliste kann nicht gelesen werden: ", "La liste des dossiers de Patchbay est illisible : ");
        add("Author: ", "Autor: ", "Auteur : ");
        add("Installed: ", "Installiert: ", "Installée : ");
        add("Available: ", "Verfügbar: ", "Disponible : ");
        add("Source: ", "Quelle: ", "Source : ");
        add("Needs:", "Benötigt:", "Nécessite :");
        // launcher 0.8.0: menu, sections and sources
        add("Sources", "Quellen", "Sources");
        add("Active Patchbay", "Aktives Patchbay", "Patchbay actif");
        add("Patchbay version", "Patchbay-Version", "Version de Patchbay");
        add("Settings", "Einstellungen", "Réglages");
        add("Language", "Sprache", "Langue");
        add("Show the data folder", "Datenordner zeigen", "Afficher le dossier des données");
        add("About", "Über", "À propos");
        add("Report a problem", "Problem melden", "Signaler un problème");
        add("All", "Alle", "Tous");
        add("Compatible", "Kompatibel", "Compatibles");
        add("Updates", "Updates", "Mises à jour");
        add("Incompatible", "Inkompatibel", "Incompatibles");
        add("incompatible", "inkompatibel", "incompatible");
        add("compatible: {n} of {all}", "kompatibel: {n} von {all}", "compatibles : {n} sur {all}");
        add("Compatible with your Bitwig", "Kompatibel mit deinem Bitwig", "Compatible avec votre Bitwig");
        add("Not compatible with your Bitwig", "Nicht kompatibel mit deinem Bitwig", "Incompatible avec votre Bitwig");
        add("Your Bitwig:", "Dein Bitwig:", "Votre Bitwig :");
        add("Your Bitwig", "Dein Bitwig", "Votre Bitwig");
        add("Your Bitwig: ", "Dein Bitwig: ", "Votre Bitwig : ");
        add("Show", "Anzeigen", "Afficher");
        add("don't know", "weiß nicht", "je ne sais pas");
        add("Built for exactly your Bitwig.", "Genau für dein Bitwig gebaut.", "Compilé exactement pour votre Bitwig.");
        add("Install anyway", "Trotzdem installieren", "Installer quand même");
        add("This mod won't run on your Bitwig, and Patchbay will show it as not available. Install it anyway?", "Dieser Mod läuft auf deinem Bitwig nicht, und Patchbay zeigt ihn als nicht verfügbar. Trotzdem installieren?", "Ce mod ne fonctionnera pas sur votre Bitwig, et Patchbay l'affichera comme indisponible. L'installer quand même ?");
        add("This Patchbay has no map for Bitwig {mine}, so it can't load any mod. Update Patchbay or Bitwig.", "Dieses Patchbay hat keine Map für Bitwig {mine} und kann daher keinen Mod laden. Aktualisiere Patchbay oder Bitwig.", "Ce Patchbay n'a pas de carte pour Bitwig {mine} et ne peut donc charger aucun mod. Mettez à jour Patchbay ou Bitwig.");
        add("The mod is built for Bitwig {built}. Patchbay's map for your Bitwig ({mine}) doesn't know that build, so it can't run.", "Der Mod ist für Bitwig {built} gebaut. Die Patchbay-Map für dein Bitwig ({mine}) kennt diesen Build nicht, daher kann er nicht laufen.", "Le mod est compilé pour Bitwig {built}. La carte Patchbay de votre Bitwig ({mine}) ne connaît pas cette version, il ne peut donc pas fonctionner.");
        add("Built for Bitwig {built}. Patchbay translates it to your Bitwig {mine}.", "Gebaut für Bitwig {built}. Patchbay übersetzt ihn für dein Bitwig {mine}.", "Compilé pour Bitwig {built}. Patchbay le traduit pour votre Bitwig {mine}.");
        add("The mod is built for Bitwig {built}, and Patchbay's map for your Bitwig ({mine}) has no translation from that build.", "Der Mod ist für Bitwig {built} gebaut, und die Patchbay-Map für dein Bitwig ({mine}) hat keine Übersetzung von diesem Build.", "Le mod est compilé pour Bitwig {built}, et la carte Patchbay de votre Bitwig ({mine}) n'a pas de traduction depuis cette version.");
        add("Add to the applications menu", "Zum Anwendungsmenü hinzufügen", "Ajouter au menu des applications");
        add("Add Patchbay Launcher to the applications menu?", "Patchbay Launcher zum Anwendungsmenü hinzufügen?", "Ajouter Patchbay Launcher au menu des applications ?");
        add("Patchbay Launcher is in the applications menu now.", "Patchbay Launcher ist jetzt im Anwendungsmenü.", "Patchbay Launcher est maintenant dans le menu des applications.");
        add("Couldn't add it to the applications menu.", "Konnte nicht zum Anwendungsmenü hinzugefügt werden.", "Impossible de l'ajouter au menu des applications.");
        add("Patchbay Launcher is an unofficial project made and supported by Marius R. It is not part of Patchbay: please report problems with the launcher on its GitHub page, not to the authors of Patchbay.",
            "Patchbay Launcher ist ein inoffizielles Projekt, das Marius R entwickelt und betreut. Es ist kein Teil von Patchbay: Probleme mit dem Launcher bitte auf seiner GitHub-Seite melden, nicht bei den Autoren von Patchbay.",
            "Patchbay Launcher est un projet non officiel créé et maintenu par Marius R. Il ne fait pas partie de Patchbay : signalez les problèmes du lanceur sur sa page GitHub, pas aux auteurs de Patchbay.");
        add("Click a mod in the list to see what it does and needs.", "Klicke auf einen Mod in der Liste, um zu sehen, was er macht und braucht.", "Cliquez sur un mod de la liste pour voir ce qu'il fait et ce qu'il nécessite.");
        add("Author", "Autor", "Auteur");
        add("Built for", "Gebaut für", "Compilé pour");
        add("Needs", "Benötigt", "Nécessite");
        add("will be added", "wird mitgeladen", "sera ajouté");
        add("missing", "fehlt", "manquant");
        add("too old", "zu alt", "trop ancien");
        add("Install Patchbay in the Patchbay section first.", "Installiere zuerst Patchbay im Bereich Patchbay.", "Installez d'abord Patchbay dans la section Patchbay.");
        add("In the catalogue: ", "Im Katalog: ", "Au catalogue : ");
        add("installed: ", "installiert: ", "installés : ");
        add("updates: ", "Updates: ", "mises à jour : ");
        add("Build", "Dateiversion", "Version des fichiers");
        add("Listed: ", "Gelistet: ", "Listés : ");
        add("downloaded: ", "geladen: ", "téléchargés : ");
        add("Bitwig not detected", "Bitwig nicht erkannt", "Bitwig non détecté");
        add("detected Bitwig ", "erkanntes Bitwig ", "Bitwig détecté ");
        add("Click a device in the list to see its panel and description.", "Klicke auf ein Gerät in der Liste, um sein Panel und seine Beschreibung zu sehen.", "Cliquez sur un périphérique de la liste pour voir son panneau et sa description.");
        add("Panel preview: ", "Panel-Vorschau: ", "Aperçu du panneau : ");
        add("Size", "Größe", "Taille");
        add("all", "alle", "toutes");
        add("Files in the repository", "Dateien im Repository", "Fichiers du dépôt");
        add("Releases", "Releases", "Versions publiées");
        add("Added by you · ", "Von dir hinzugefügt · ", "Ajouté par vous · ");
        add("The repository exists, but its releases have no files matching the pattern.", "Das Repository existiert, aber seine Releases enthalten keine Dateien, die zum Muster passen.", "Le dépôt existe, mais ses versions publiées ne contiennent aucun fichier correspondant au motif.");
        add("Patchbay versions found: ", "Gefundene Patchbay-Versionen: ", "Versions de Patchbay trouvées : ");
        add(". Latest: ", ". Neueste: ", ". Dernière : ");
        add("The repository exists, but its releases have no mod ZIPs (name-version.zip).", "Das Repository existiert, aber seine Releases enthalten keine Mod-ZIPs (name-version.zip).", "Le dépôt existe, mais ses versions publiées ne contiennent aucun ZIP de mod (nom-version.zip).");
        add("Mods found: ", "Gefundene Mods: ", "Mods trouvés : ");
        add("The repository exists, but it holds no .bwdevice, .bwmodulator or .bwmodule files.", "Das Repository existiert, enthält aber keine .bwdevice-, .bwmodulator- oder .bwmodule-Dateien.", "Le dépôt existe, mais il ne contient aucun fichier .bwdevice, .bwmodulator ou .bwmodule.");
        add("Device files found: ", "Gefundene Gerätedateien: ", "Fichiers de périphériques trouvés : ");
        add("There is no such repository, or it is private.", "Dieses Repository gibt es nicht, oder es ist privat.", "Ce dépôt n'existe pas ou il est privé.");
        add("Add source", "Quelle hinzufügen", "Ajouter une source");
        add("GitHub repositories the launcher gets Patchbay, mods, devices and themes from.", "GitHub-Repositories, aus denen der Launcher Patchbay, Mods, Geräte und Themes lädt.", "Dépôts GitHub d'où le lanceur récupère Patchbay, les mods, les périphériques et les thèmes.");
        add("one active source", "eine aktive Quelle", "une seule source active");
        add("mod ZIPs from releases", "Mod-ZIPs aus Releases", "ZIP de mods des versions publiées");
        add(".bwdevice and .bwmodulator files", ".bwdevice- und .bwmodulator-Dateien", "fichiers .bwdevice et .bwmodulator");
        add("+ Add", "+ Hinzufügen", "+ Ajouter");
        add("Add source: ", "Quelle hinzufügen: ", "Ajouter une source : ");
        add("Turn off ", "Ausschalten: ", "Désactiver ");
        add("Turn on ", "Einschalten: ", "Activer ");
        add("built-in", "eingebaut", "intégrée");
        add("Edit", "Bearbeiten", "Modifier");
        add("Edit ", "Bearbeiten: ", "Modifier ");
        add("Patchbay needs one source switched on.", "Patchbay braucht eine eingeschaltete Quelle.", "Patchbay a besoin d'une source activée.");
        add("New source: ", "Neue Quelle: ", "Nouvelle source : ");
        add("Edit source", "Quelle bearbeiten", "Modifier la source");
        add("Group", "Gruppe", "Groupe");
        add("GitHub repository (owner/name)", "GitHub-Repository (Besitzer/Name)", "Dépôt GitHub (propriétaire/nom)");
        add("GitHub repository", "GitHub-Repository", "Dépôt GitHub");
        add("Where files come from", "Woher die Dateien kommen", "Provenance des fichiers");
        add("Releases (ZIP files)", "Releases (ZIP-Dateien)", "Versions publiées (fichiers ZIP)");
        add("Release file (* = version)", "Release-Datei (* = Version)", "Fichier de version (* = version)");
        add("Which release files", "Welche Release-Dateien", "Quels fichiers de version");
        add("Folder in the repository (empty = all)", "Ordner im Repository (leer = alle)", "Dossier du dépôt (vide = tous)");
        add("A built-in source can't be removed or moved. You can switch it off.", "Eine eingebaute Quelle lässt sich nicht entfernen oder verschieben. Du kannst sie ausschalten.", "Une source intégrée ne peut être ni supprimée ni déplacée. Vous pouvez la désactiver.");
        add("Patchbay comes from one source at a time. Switching this one on switches the others off.", "Patchbay kommt immer aus einer Quelle. Wenn du diese einschaltest, werden die anderen ausgeschaltet.", "Patchbay provient d'une seule source à la fois. Activer celle-ci désactive les autres.");
        add("Check", "Prüfen", "Vérifier");
        add("Add", "Hinzufügen", "Ajouter");
        add("Save", "Speichern", "Enregistrer");
        add("Remove source", "Quelle entfernen", "Supprimer la source");
        add("Type the repository as owner/name, for example polarity/bitwig-mods.", "Gib das Repository als Besitzer/Name ein, zum Beispiel polarity/bitwig-mods.", "Saisissez le dépôt sous la forme propriétaire/nom, par exemple polarity/bitwig-mods.");
        add("Checking…", "Wird geprüft…", "Vérification…");
        add("Mods and devices are code that runs inside Bitwig. Only add repositories you trust.\n\nAdd ", "Mods und Geräte sind Code, der in Bitwig läuft. Füge nur Repositories hinzu, denen du vertraust.\n\nHinzufügen: ", "Les mods et les périphériques sont du code exécuté dans Bitwig. N'ajoutez que des dépôts de confiance.\n\nAjouter ");
        add("Added.", "Hinzugefügt.", "Ajoutée.");
        add("Saved.", "Gespeichert.", "Enregistrée.");
        add("Removed ", "Entfernt: ", "Supprimée : ");
        add("Open the list", "Liste öffnen", "Ouvrir la liste");
        // launcher 0.9.0: Windows
        add("No Patchbay for this system in the archive (", "Im Archiv ist kein Patchbay für dieses System (", "Pas de Patchbay pour ce système dans l'archive (");
        add(" missing).", " fehlt).", " manquant).");
        add("Bitwig Studio's Java wasn't found.", "Die Java von Bitwig Studio wurde nicht gefunden.", "Le Java de Bitwig Studio est introuvable.");
        add("Add to the Start menu", "Zum Startmenü hinzufügen", "Ajouter au menu Démarrer");
        add("Add Patchbay Launcher to the Start menu and the desktop?", "Patchbay Launcher zum Startmenü und zum Desktop hinzufügen?", "Ajouter Patchbay Launcher au menu Démarrer et au bureau ?");
        add("The shortcut is in the Start menu and on the desktop.", "Die Verknüpfung ist im Startmenü und auf dem Desktop.", "Le raccourci est dans le menu Démarrer et sur le bureau.");
        add("Couldn't create the shortcut. Right-click Patchbay Launcher.exe and choose \"Pin to Start\".", "Die Verknüpfung konnte nicht erstellt werden. Klicke mit der rechten Maustaste auf Patchbay Launcher.exe und wähle „An Start anheften“.", "Impossible de créer le raccourci. Faites un clic droit sur Patchbay Launcher.exe et choisissez « Épingler à l'écran de démarrage ».");
        // mods tab
        add("Available", "Verfügbar", "Disponible");
        add("Source", "Quelle", "Source");
        add("Status", "Status", "État");
        add("update", "Update", "mise à jour");
        add("installed", "installiert", "installé");
        add("new", "neu", "nouveau");
        add("Refresh", "Neu laden", "Actualiser");
        add("Release page", "Release-Seite", "Page de la version");
        add("Install Patchbay on the first tab first.", "Installiere zuerst Patchbay auf dem ersten Tab.", "Installez d'abord Patchbay dans le premier onglet.");
        add("Loading the mod list…", "Mod-Liste wird geladen…", "Chargement de la liste des mods…");
        add("Update all (", "Alle aktualisieren (", "Tout mettre à jour (");
        add("Mod updates: ", "Mod-Updates: ", "Mises à jour de mods : ");
        add("Tick all", "Alle auswählen", "Tout cocher");
        add("Untick all", "Auswahl aufheben", "Tout décocher");
        add("Install ticked (", "Ausgewählte installieren (", "Installer la sélection (");
        add("Installing: ", "Installation: ", "Installation : ");
        add("Done. Tick the mods in the Patchbay window and click Load.",
            "Fertig. Aktiviere die Mods im Patchbay-Fenster und klicke auf Load.",
            "Terminé. Cochez les mods dans la fenêtre Patchbay et cliquez sur Load.");

        // Themes
        add("Themes", "Themes", "Thèmes");
        add("Theme", "Theme", "Thème");
        add("active", "aktiv", "actif");
        add("Loading the theme list…", "Theme-Liste wird geladen…", "Chargement de la liste des thèmes…");
        add("Install Patchbay first.", "Installiere zuerst Patchbay.", "Installez d'abord Patchbay.");
        add("no active theme", "kein aktives Theme", "aucun thème actif");
        add("active: ", "aktiv: ", "actif : ");
        add("A theme changes Bitwig's colours. Click a theme in the list to see a preview.",
            "Ein Theme ändert die Farben von Bitwig. Klicke auf ein Theme in der Liste, um eine Vorschau zu sehen.",
            "Un thème change les couleurs de Bitwig. Cliquez sur un thème de la liste pour voir un aperçu.");
        add("Theme preview: ", "Theme-Vorschau: ", "Aperçu du thème : ");
        add("active in Patchbay", "in Patchbay aktiv", "actif dans Patchbay");
        add("not installed", "nicht installiert", "non installé");
        add("a newer version is out", "eine neuere Version ist da", "une version plus récente existe");
        add("After changing the theme, start Bitwig again from Patchbay (Load).",
            "Starte Bitwig nach dem Theme-Wechsel erneut aus Patchbay (Load).",
            "Après un changement de thème, relancez Bitwig depuis Patchbay (Load).");
        add("Use this theme", "Dieses Theme verwenden", "Utiliser ce thème");
        add("Turn themes off", "Themes ausschalten", "Désactiver les thèmes");
        add("Remove", "Entfernen", "Supprimer");
        add("Close the Patchbay window, then try again.", "Schließe das Patchbay-Fenster und versuche es erneut.", "Fermez la fenêtre Patchbay, puis réessayez.");
        add("Theme set: \"", "Theme gesetzt: „", "Thème choisi : « ");
        add("\". Start Patchbay and click Load.", "“. Starte Patchbay und klicke auf Load.", " ». Lancez Patchbay et cliquez sur Load.");
        add("Themes are off. Bitwig gets its own colours back at the next start.",
            "Themes sind aus. Bitwig bekommt beim nächsten Start seine eigenen Farben zurück.",
            "Les thèmes sont désactivés. Bitwig retrouve ses couleurs au prochain démarrage.");
        add("Remove the theme \"", "Das Theme „", "Supprimer le thème « ");
        add("\" from Patchbay?", "“ aus Patchbay entfernen?", " » de Patchbay ?");
        add("Removed: ", "Entfernt: ", "Supprimé : ");
        add("Installed. Click \"Use this theme\" to turn it on.",
            "Installiert. Klicke auf „Dieses Theme verwenden“, um es einzuschalten.",
            "Installé. Cliquez sur « Utiliser ce thème » pour l'activer.");
        add("Installed. Pick a theme and click \"Use this theme\".",
            "Installiert. Wähle ein Theme und klicke auf „Dieses Theme verwenden“.",
            "Installé. Choisissez un thème et cliquez sur « Utiliser ce thème ».");
        add("The repository exists, but it holds no .bte theme files.",
            "Das Repository existiert, enthält aber keine .bte-Theme-Dateien.",
            "Le dépôt existe, mais ne contient aucun fichier de thème .bte.");
        add("Themes found: ", "Gefundene Themes: ", "Thèmes trouvés : ");
        add(".bte theme files", ".bte-Theme-Dateien", "fichiers de thème .bte");

        // New look
        add("Update available", "Update verfügbar", "Mise à jour disponible");
        add("Updates available", "Updates verfügbar", "Mises à jour disponibles");
        add("Updates: ", "Updates: ", "Mises à jour : ");
        add("Sections", "Bereiche", "Sections");
        add("Version", "Version", "Version");
        add("Mods and themes move to the new version.", "Mods und Themes werden in die neue Version übernommen.", "Les mods et les thèmes passent à la nouvelle version.");
    }

    /** "<mod> needs <id> [min version]" in the language in use; installedVersion is null when the needed mod is absent. */
    static String needs(String mod, String id, String minVersion, String installedVersion) {
        boolean min = minVersion != null, absent = installedVersion == null;
        return switch (lang()) {
            case "pl" -> mod + " wymaga „" + id + "”" + (min ? " w wersji co najmniej " + minVersion : "")
                + (absent ? ", którego nie ma w tej instalacji." : "; zainstalowana jest " + installedVersion + ". Zaktualizuj Patchbay.");
            case "de" -> mod + " benötigt „" + id + "“" + (min ? " in Version " + minVersion + " oder neuer" : "")
                + (absent ? ", das in dieser Installation fehlt." : "; installiert ist " + installedVersion + ". Aktualisiere Patchbay.");
            case "fr" -> mod + " nécessite « " + id + " »" + (min ? " en version " + minVersion + " ou plus récente" : "")
                + (absent ? ", absent de cette installation." : " ; la version installée est " + installedVersion + ". Mettez Patchbay à jour.");
            default -> mod + " needs \"" + id + "\"" + (min ? " " + minVersion + " or newer" : "")
                + (absent ? ", which isn't in this install." : "; " + installedVersion + " is installed. Update Patchbay.");
        };
    }
}
