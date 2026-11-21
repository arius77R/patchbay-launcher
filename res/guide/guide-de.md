Patchbay Launcher installiert und startet Patchbay (BML) und lädt Mods, Geräte und Themes für Bitwig Studio, ganz ohne Terminal. Er ordnet die Listen nach Kategorien und Favoriten, lässt dich Dinge ausschalten und deinstallieren, bringt neue Quellen und warnt vor Mods, die ihre Autoren entfernt oder zurückgezogen haben.

Ein inoffizielles Projekt von Marius R, nicht verbunden mit Bitwig GmbH oder dem Autor von Patchbay.

## Patchbay

Im Tab Patchbay installierst, aktualisierst und wechselst du Patchbay-Versionen. Deine Mods und Themes ziehen von selbst in die neue Version um.

- **Patchbay-Mods wiederherstellen:** Die Schaltfläche erscheint, wenn ein Mod fehlt, der mit Patchbay kam, z. B. nachdem ein Framework deinstalliert wurde. Der Launcher lädt dieselbe Version erneut und ergänzt nur die fehlenden Mods, eingeschaltet. Nichts wird überschrieben. Bitwig und das Patchbay-Fenster müssen geschlossen sein.

## Mods

Unter Mods wählst du Mods für Patchbay aus, installierst, aktualisierst, schaltest sie aus und deinstallierst sie.

- **Seitenleiste:** Alle, Favoriten und jede Kategorie mit Symbol und Anzahl der Mods. Ein Klick filtert die Liste. « klappt die Leiste auf Symbole zusammen, » klappt sie wieder auf.
- **Favoriten:** Klicke auf den Stern neben einem Mod. Favoriten sind in der Sicherung der Einstellungen enthalten.
- **Kategorie:** ergibt sich aus dem Namen des Mods („Kategorie: Name“). Ein Mod ohne Kategorie aus der Liste landet unter Other.
- **Anzeigen:** Alle, Kompatibel, Installiert, Updates, Inkompatibel, Aus dem Repo entfernt und Nicht in Quellen. Um alle Mods, die nicht in Quellen sind, auf einmal loszuwerden: Anzeigen → Nicht in Quellen, „Alle auswählen“, dann „Deinstallieren“.
- **Installieren:** Mods auswählen und „Ausgewählte installieren“ klicken. „Alle auswählen“ wählt die Mods zum Installieren oder Aktualisieren. Wenn die Liste nichts zum Installieren hat (z. B. Anzeigen → Nicht in Quellen oder Aus dem Repo entfernt), wählt es alle installierten, außer Mods aus Patchbay. Häkchen bleiben beim Wechsel von Kategorie, Anzeigen oder Suchen, so kannst du Mods aus mehreren Kategorien wählen und zusammen installieren.
- **Ausschalten / Einschalten:** Der Mod bleibt auf dem Datenträger, Patchbay lädt ihn nicht. Markiere einen oder mehrere mit Häkchen und nutze die Schaltfläche unter der Liste.
- **Deinstallieren:** entfernt die Mods mit Häkchen nach Bestätigung vom Datenträger. Der Launcher warnt, wenn ein anderer eingeschalteter Mod sie braucht.
- „Ausschalten“ und „Deinstallieren“ sind nur unter der Liste. Sie bleiben grau, bis ein Mod ein Häkchen hat.

Zum Ausschalten und Deinstallieren müssen Bitwig und das Patchbay-Fenster geschlossen sein.

**Zustände, die du kennen solltest:**

| Zustand | Bedeutung | Was tun |
| --- | --- | --- |
| aus Patchbay | Der Mod kam mit Patchbay (Frameworks: Actions and Menus, Browser, Themes, Nitro Devices und andere). Andere Mods, Geräte und Themes brauchen ihn. | Behalten. Er wird mit Patchbay aktualisiert. |
| entfernt | Der Autor hat den Mod aus dem Repository entfernt. Er funktioniert weiter, bekommt aber keine Aktualisierungen. | Behalten oder deinstallieren. |
| zurückgezogen | Der Autor hat den Mod wegen Problemen zurückgezogen. Der Grund steht in den Details. Der Launcher fragt einmal, ob er ausgeschaltet werden soll. | Am besten ausschalten oder deinstallieren. |
| nicht in Quellen | Keine eingeschaltete Quelle bietet ihn an: Er wurde von Hand hinzugefügt, oder sein Repository gibt es nicht mehr. | Behalten oder deinstallieren. |
| inkompatibel | Er funktioniert nicht mit deinem Bitwig. | Auf eine neue Version warten. |

## Geräte

Unter Geräte lädst du Bitwig-Gerätedateien (.bwdevice, .bwmodulator, Grid-Module) und registrierst sie bei Patchbay.

- **Dateiversion:** Der Launcher wählt ihn passend zu deinem Bitwig. Du kannst ihn im Feld oben links ändern.
- **Seitenleiste:** Jeder Typ hat sein eigenes Symbol (Audio FX, Instrument, Note FX, Note generator, Modulator, Grid module). Der Stern fügt zu den Favoriten hinzu.
- **Laden:** auswählen und „Ausgewählte laden“ klicken.
- **Ausschalten / Einschalten:** Die Datei bleibt mit der Endung .off auf dem Datenträger, und Bitwig zeigt sie nicht an. Die Schaltflächen sind unter der Liste und wirken auf die Geräte mit Häkchen.
- **Deinstallieren:** entfernt die Datei vom Datenträger.
- **entfernt / zurückgezogen:** wie bei Mods. Eine Datei, die ihr Repository nicht mehr hat, bleibt in der Liste, damit sie deinstalliert werden kann.

Für Änderungen muss Bitwig geschlossen sein.

## Dateien, die nicht mehr im Repository sind

Der Launcher zeigt auch Mods und Geräte auf deinem Datenträger, die keine Quelle mehr anbietet:

- **entfernt:** Der Autor hat sie aus dem Repository entfernt;
- **nicht in Quellen:** Sie wurden von Hand hinzugefügt, oder die Quelle gibt es nicht mehr.

Sie funktionieren weiter, bekommen aber keine Updates. Wenn du sie nicht willst:

**Mods**

1. Beende Bitwig und schließe das Patchbay-Fenster.
2. Anzeigen → Aus dem Repo entfernt oder Nicht in Quellen. Um nur einige zu wählen, tippe einen Namen in Suchen.
3. Setze Häkchen oder klicke „Alle auswählen“.
4. Klicke „Deinstallieren“ unter der Liste und bestätige. Der Launcher listet auf, was er entfernt, und warnt, wenn ein anderer Mod etwas davon braucht.

Mods aus Patchbay erscheinen nicht in diesen Ansichten. Setzt du selbst ein Häkchen bei einem, warnt der Launcher vor dem Entfernen.

**Geräte**

1. Beende Bitwig.
2. Sortiere nach „Status“: Entfernte stehen oben.
3. Setze Häkchen und klicke „Deinstallieren“ unter der Liste.

## Quellen

Quellen sind die GitHub-Repositorys, aus denen der Launcher Patchbay, Mods, Geräte und Themes lädt. Es gibt drei Reiter:

| Reiter | Was er zeigt | Was du tun kannst |
| --- | --- | --- |
| Aktiv | Die Quellen, die du hast: eingebaute und eigene. | Ein- und ausschalten, bearbeiten, entfernen, eigene hinzufügen. |
| Quellenliste | Repositorys aus der Liste der Empfehlungen und solche, die auf GitHub über das Thema patchbay-mod, patchbay-devices oder patchbay-themes gefunden wurden. Bereits vorhandene sind als „aktiv“ markiert. | Ein Repository anklicken und zu den aktiven hinzufügen. |
| Empfohlen | Repositorys aus der Liste arius77R/bitwig-mods-list, die du noch nicht hast. Die rote Zahl zählt die neuen. | Ein Repository anklicken und zu den aktiven hinzufügen. |

- **Karte eines Repositorys:** Ein Klick zeigt Autor, Beschreibung, Inhalt (z. B. „Mods: 7“), letzte Änderung, Lizenz und Sterne. „Zu den aktiven hinzufügen“ geht, wenn es etwas zu laden gibt. „Repository-Seite“ öffnet es auf GitHub.
- **Neu laden:** Auf jedem Reiter werden die Quellen, die Liste der Empfehlungen und die Suche nach Themen neu von GitHub geholt, und es wird geprüft, ob die Repositorys noch existieren. Eine Änderung an der Liste kann ein paar Minuten brauchen, bis sie erscheint.
- **existiert nicht:** ein rotes Abzeichen neben einer Quelle unter Aktiv, wenn ihr Repository gelöscht, privat gemacht oder umbenannt wurde. Die Quelle verschwindet nicht von selbst. „Bearbeiten“ hat „Seite des Autors“ und „Quelle entfernen“.

GitHub erlaubt 60 Anfragen pro Stunde von einer Adresse. Sind sie aufgebraucht, zeigt der Launcher die zuletzt geladenen Daten und markiert nichts als entfernt.

## Der Launcher, Meldungen und das Fenster

- **Seite des Launchers:** Klicke oben links auf „Patchbay Launcher“. Du siehst deine Version, „Neu in“ und die Systeminformationen. Wenn eine neue Version da ist, gibt es dort „Aktualisieren“ und neben der Versionsnummer in der oberen Leiste „Neue Version“.
- **Systeminformationen:** die Versionen von Launcher, System, Java, Bitwig, Patchbay und die Liste der Mods, ohne Pfade und persönliche Daten. „Kopieren“ legt sie in die Zwischenablage.
- **Problem melden** (untere Leiste): öffnet eine neue Meldung auf GitHub mit bereits eingetragenen Systeminformationen. Ergänze, was passiert ist und was hätte passieren sollen.
- **Bitwig-Warnung:** Vor „Patchbay starten“ prüft der Launcher, ob Patchbay dein Bitwig unterstützt. Wenn nicht, zeigt er die unterstützten Versionen und fragt: Abbrechen oder Trotzdem starten.
- **Fenster:** Beim ersten Mal passt es sich dem Bildschirm an, danach öffnet es sich dort und so groß, wie es beim Schließen war.

## Wenn etwas nicht stimmt

1. **Neu laden:** in Mods, Geräte, Themes oder Quellen. Das reicht meistens.
2. **Patchbay-Mods wiederherstellen** (Tab Patchbay), wenn Mods, Geräte oder Themes nach einer Deinstallation nicht mehr funktionieren.
3. **GitHub-Speicher leeren** (Einstellungen). Der letzte Ausweg, wenn der Launcher nach „Neu laden“ immer noch einen Mod, ein Gerät oder ein Theme zeigt, das es im Repository nicht mehr gibt, eine neue Version nicht sieht, die schon auf GitHub ist, oder Fehler beim Laden der Listen zeigt. Er löscht die gemerkten Listen und lädt alles neu. Installierte Mods, Geräte, Themes und Einstellungen bleiben. Das verbraucht einen Teil des GitHub-Limits (60 Anfragen pro Stunde), also nicht oft nutzen. Dateien, die nicht in Quellen sind, entfernt es nicht; dafür gibt es „Deinstallieren“.
4. **Problem melden,** wenn nichts davon geholfen hat.

## Für Autoren von Mods, Geräten und Themes

Die vollständigen Regeln stehen in [PUBLISHING](https://github.com/arius77R/patchbay-launcher/blob/main/docs/PUBLISHING.md). Das Wichtigste:

1. **Ein ZIP pro Mod** für alle Systeme: `<mod-id>-<version>.zip` in einem Release und `<mod-id>/mod.json` im Repository.
2. **Der Name des Mods beginnt mit einer Kategorie** aus der festen Liste, z. B. „Arranger: Add Automation Points“.
3. **Ein Thema für das Repository,** damit der Launcher es findet: ⚙ neben About → Topics → `patchbay-mod`, `patchbay-devices` oder `patchbay-themes`.
4. **Die Liste der Empfehlungen:** Bitte über Issues oder einen Pull Request darum, dein Repository in [arius77R/bitwig-mods-list](https://github.com/arius77R/bitwig-mods-list) aufzunehmen.
5. **Zurückziehen wegen Problemen:** eine `withdrawn.json` im Hauptordner des Repositorys, mit der `id` des Mods oder dem Dateinamen des Geräts und dem Grund. Der Launcher zeigt „zurückgezogen“ und fragt den Nutzer einmal, ob er ausschalten soll.
6. **Einfach den Ordner** `<mod-id>/` zu löschen genügt, wenn ein Mod schlicht ausläuft. Nutzer sehen dann „entfernt“.
