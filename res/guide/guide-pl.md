Patchbay Launcher instaluje i uruchamia Patchbay (BML) oraz pobiera mody, urządzenia i motywy do Bitwig Studio, bez Terminala. Wersja 0.9.6 porządkuje listy kategoriami i ulubionymi, pozwala wyłączać i odinstalowywać, dodaje nowe Źródła i ostrzega przed modami usuniętymi albo wycofanymi przez autorów.

To nieoficjalny projekt Mariusa R, niezwiązany z Bitwig GmbH ani z autorem Patchbay.

## Mody

W zakładce Mody wybierasz, instalujesz, aktualizujesz, wyłączasz i odinstalowujesz mody do Patchbay.

- **Panel boczny:** Wszystkie, Ulubione i każda kategoria z ikoną i liczbą modów. Kliknięcie filtruje listę. « zwija panel do samych ikon, » rozwija go z powrotem.
- **Ulubione:** kliknij gwiazdkę przy modzie. Ulubione są w kopii zapasowej ustawień.
- **Kategoria:** wynika z nazwy moda („Kategoria: Nazwa”). Mod bez kategorii z listy trafia do Other.
- **Pokaż:** Wszystkie, Zgodne, Zainstalowane, Aktualizacje, Niezgodne i Usunięte z repo.
- **Instalacja:** zaznacz mody i kliknij „Zainstaluj zaznaczone”. „Zaznacz wszystkie” zaznacza tylko te do zainstalowania albo aktualizacji.
- **Wyłącz / Włącz:** mod zostaje na dysku, Patchbay go nie ładuje. Zaznacz kilka i użyj przycisku pod listą.
- **Odinstaluj:** usuwa mod z dysku, po potwierdzeniu. Launcher ostrzega, gdy inny włączony mod go potrzebuje.

Wyłączanie i odinstalowanie wymaga zamkniętego Bitwiga i okna Patchbay.

**Stany, które warto znać:**

| Stan | Co znaczy | Co zrobić |
| --- | --- | --- |
| usunięty | Autor usunął mod z repozytorium. Działa dalej, ale nie dostanie aktualizacji. | Zostaw albo odinstaluj. |
| wycofany | Autor wycofał mod z powodu problemów. Powód jest w szczegółach. Launcher raz zapyta, czy go wyłączyć. | Najlepiej wyłącz albo odinstaluj. |
| spoza źródeł | Żadne włączone źródło go nie oferuje: przyszedł z Patchbay, dodano go ręcznie albo jego repozytorium usunięto. | Zostaw albo odinstaluj. |
| niezgodny | Nie działa z Twoim Bitwigiem. | Poczekaj na nową wersję. |

## Urządzenia

Zakładka Urządzenia pobiera pliki urządzeń Bitwiga (.bwdevice, .bwmodulator, moduły Grid) i rejestruje je w Patchbay.

- **Wersja plików:** Launcher wybiera ją sam do Twojego Bitwiga. Możesz ją zmienić w polu po lewej u góry.
- **Panel boczny:** każdy typ ma swoją ikonę (Audio FX, Instrument, Note FX, Note generator, Modulator, Grid module). Gwiazdka dodaje do Ulubionych.
- **Pobierz:** zaznacz i kliknij „Pobierz zaznaczone”.
- **Wyłącz / Włącz:** plik zostaje na dysku z dopiskiem .off, a Bitwig go nie pokazuje.
- **Odinstaluj:** usuwa plik z dysku.
- **usunięte / wycofane:** jak w Modach. Plik, którego repozytorium już nie ma, zostaje na liście, żeby dało się go odinstalować.

Zmiany wymagają zamkniętego Bitwiga.

## Źródła

Źródła to repozytoria GitHub, z których Launcher pobiera Patchbay, mody, urządzenia i motywy. Są trzy zakładki:

| Zakładka | Co pokazuje | Co możesz zrobić |
| --- | --- | --- |
| Aktywne | Źródła już dodane: wbudowane i Twoje. | Włączyć, wyłączyć, edytować, usunąć, dodać własne. |
| Lista źródeł | Repozytoria z listy polecanych i znalezione na GitHubie po temacie patchbay-mod, patchbay-devices albo patchbay-themes. Dodane już mają plakietkę „w aktywnych”. | Kliknąć repozytorium i dodać je do aktywnych. |
| Polecane | Repozytoria z listy arius77R/bitwig-mods-list, których jeszcze nie masz. Czerwona liczba to nowe. | Kliknąć repozytorium i dodać je do aktywnych. |

- **Karta repozytorium:** po kliknięciu widzisz autora, opis, co zawiera (np. „Mody: 7”), ostatnią zmianę, licencję i gwiazdki. „Dodaj do aktywnych” działa, gdy jest co pobrać. „Strona repozytorium” otwiera je na GitHubie.
- **Odśwież:** na każdej zakładce pobiera z GitHuba od nowa źródła, listę polecanych i wyszukiwanie po tematach, i sprawdza, czy repozytoria nadal istnieją. Zmiana na liście może pojawić się po kilku minutach.
- **nie istnieje:** czerwona plakietka przy źródle w Aktywnych, gdy jego repozytorium usunięto, ukryto albo zmieniło nazwę. Źródło nie znika samo. W „Edytuj” jest „Strona autora” i „Usuń źródło”.

GitHub pozwala na 60 zapytań na godzinę z jednego adresu. Gdy limit się skończy, Launcher pokazuje ostatnio pobrane dane i niczego nie oznacza jako usuniętego.

## Launcher, zgłoszenia i okno

- **Strona Launchera:** kliknij „Patchbay Launcher” w lewym górnym rogu. Zobaczysz swoją wersję, „Co nowego” i informacje o systemie. Gdy wyjdzie nowa wersja, jest tam przycisk „Aktualizuj”, a obok numeru wersji w górnym pasku napis „Nowa wersja”.
- **Informacje o systemie:** wersje Launchera, systemu, Javy, Bitwiga, Patchbay i lista modów, bez ścieżek i danych osobistych. „Kopiuj” kopiuje je do schowka.
- **Zgłoś problem** (dolny pasek): otwiera nowe zgłoszenie na GitHubie z już wpisanymi informacjami o systemie. Dopisz, co się stało i co miało się stać.
- **Ostrzeżenie o Bitwigu:** przed „Uruchom Patchbay” Launcher sprawdza, czy Patchbay obsługuje Twój Bitwig. Jeśli nie, pokazuje obsługiwane wersje i pyta: Anuluj albo Uruchom mimo to.
- **Okno:** przy pierwszym uruchomieniu dopasowuje się do ekranu, potem otwiera się tam i tak duże, jak było przy zamknięciu.

## Dla autorów modów, urządzeń i motywów

Pełne zasady są w [PUBLISHING](https://github.com/arius77R/patchbay-launcher/blob/main/docs/PUBLISHING.md). Najważniejsze:

1. **Jeden ZIP na mod** dla wszystkich systemów: `<mod-id>-<wersja>.zip` w wydaniu, oraz `<mod-id>/mod.json` w repozytorium.
2. **Nazwa moda zaczyna się od kategorii** ze stałej listy, np. „Arranger: Add Automation Points”.
3. **Temat repozytorium,** żeby Launcher je znalazł: ⚙ obok About → Topics → `patchbay-mod`, `patchbay-devices` albo `patchbay-themes`.
4. **Lista polecanych:** poproś o dopisanie repozytorium do [arius77R/bitwig-mods-list](https://github.com/arius77R/bitwig-mods-list) przez Issues albo Pull Request.
5. **Wycofanie z powodu problemów:** plik `withdrawn.json` w głównym folderze repozytorium, z `id` moda albo nazwą pliku urządzenia i powodem. Launcher pokaże „wycofany” i raz zapyta użytkownika, czy wyłączyć.
6. **Samo usunięcie** folderu `<mod-id>/` wystarczy, gdy mod po prostu kończy życie. Użytkownicy zobaczą „usunięty”.
