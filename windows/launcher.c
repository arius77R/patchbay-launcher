/*
 * Patchbay Launcher.exe: starts app\launcher.jar on the Java that ships with Bitwig Studio
 * (javaw.exe, so no console window), the way Patchbay's own bml-ui.cmd finds it. It passes its own
 * path as -Dlauncher.exe so the launcher can make Start menu and desktop shortcuts to it.
 * The Java's output goes to %LOCALAPPDATA%\PatchbayLauncher\logs\launcher.log.
 *
 * Java, in order: PATCHBAY_LAUNCHER_JAVA; the Bitwig chosen in Patchbay's settings
 * (%LOCALAPPDATA%\BML\bml.properties, bitwig.executable); %ProgramFiles%\Bitwig Studio; JAVA_HOME.
 *
 * Build: x86_64-w64-mingw32-gcc -municode -mwindows -O2 -s launcher.c resources.o -o "Patchbay Launcher.exe"
 */
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <windows.h>
#include <stdio.h>
#include <wchar.h>

#define LONG_PATH 4096

static int is_file(const wchar_t *p) {
    DWORD a = GetFileAttributesW(p);
    return a != INVALID_FILE_ATTRIBUTES && !(a & FILE_ATTRIBUTE_DIRECTORY);
}

static int lang(void) { return PRIMARYLANGID(GetUserDefaultUILanguage()); }

static void fail(const wchar_t *pl, const wchar_t *de, const wchar_t *fr, const wchar_t *en) {
    const wchar_t *m = lang() == LANG_POLISH ? pl : lang() == LANG_GERMAN ? de : lang() == LANG_FRENCH ? fr : en;
    MessageBoxW(NULL, m, L"Patchbay Launcher", MB_OK | MB_ICONERROR);
}

/* The folder part of a path, without the trailing backslash. */
static void parent(wchar_t *p) {
    wchar_t *s = wcsrchr(p, L'\\');
    if (s) *s = 0;
}

static int env(const wchar_t *name, wchar_t *out, DWORD size) {
    DWORD n = GetEnvironmentVariableW(name, out, size);
    return n > 0 && n < size;
}

/* bitwig.executable from a Java properties file (ISO-8859-1 with \ escapes); 1 when found. */
static int bitwig_from_properties(const wchar_t *file, wchar_t *out, size_t size) {
    FILE *f = _wfopen(file, L"rb");
    if (!f) return 0;
    char line[8192];
    int found = 0;
    while (!found && fgets(line, sizeof line, f)) {
        char *p = line;
        while (*p == ' ' || *p == '\t') p++;
        const char *key = "bitwig.executable";
        size_t k = strlen(key);
        if (strncmp(p, key, k) != 0) continue;
        p += k;
        while (*p == ' ' || *p == '\t') p++;
        if (*p != '=' && *p != ':') continue;
        p++;
        while (*p == ' ' || *p == '\t') p++;
        size_t o = 0;
        for (; *p && *p != '\r' && *p != '\n' && o + 1 < size; p++) {
            unsigned char c = (unsigned char) *p;
            if (c == '\\' && p[1]) {
                p++;
                if (*p == 'u' && p[1] && p[2] && p[3] && p[4]) {
                    char hex[5] = { p[1], p[2], p[3], p[4], 0 };
                    out[o++] = (wchar_t) strtol(hex, NULL, 16);
                    p += 4;
                } else if (*p == 't') out[o++] = L'\t';
                else out[o++] = (wchar_t) (unsigned char) *p;
            } else {
                out[o++] = (wchar_t) c;
            }
        }
        out[o] = 0;
        found = o > 0;
    }
    fclose(f);
    return found;
}

/* jre\bin\javaw.exe next to a Bitwig Studio.exe; 1 when it exists. */
static int java_beside(const wchar_t *bitwig_exe, wchar_t *java, size_t size) {
    wchar_t dir[LONG_PATH];
    wcsncpy(dir, bitwig_exe, LONG_PATH - 1);
    dir[LONG_PATH - 1] = 0;
    parent(dir);
    _snwprintf(java, size, L"%ls\\jre\\bin\\javaw.exe", dir);
    java[size - 1] = 0;
    return is_file(java);
}

static int find_java(wchar_t *java, size_t size) {
    wchar_t buf[LONG_PATH], file[LONG_PATH];
    if (env(L"PATCHBAY_LAUNCHER_JAVA", buf, LONG_PATH) && is_file(buf)) {
        wcsncpy(java, buf, size - 1);
        java[size - 1] = 0;
        return 1;
    }
    if (env(L"LOCALAPPDATA", buf, LONG_PATH)) {
        _snwprintf(file, LONG_PATH, L"%ls\\BML\\bml.properties", buf);
        file[LONG_PATH - 1] = 0;
        wchar_t chosen[LONG_PATH];
        if (bitwig_from_properties(file, chosen, LONG_PATH) && java_beside(chosen, java, size)) return 1;
    }
    if (env(L"ProgramFiles", buf, LONG_PATH)) {
        _snwprintf(file, LONG_PATH, L"%ls\\Bitwig Studio\\Bitwig Studio.exe", buf);
        file[LONG_PATH - 1] = 0;
        if (java_beside(file, java, size)) return 1;
    }
    if (env(L"JAVA_HOME", buf, LONG_PATH)) {
        _snwprintf(java, size, L"%ls\\bin\\javaw.exe", buf);
        java[size - 1] = 0;
        if (is_file(java)) return 1;
    }
    return 0;
}

/* An append-only handle to the launcher's log for the child's output; INVALID_HANDLE_VALUE when it can't be made. */
static HANDLE open_log(void) {
    wchar_t base[LONG_PATH], path[LONG_PATH];
    if (!env(L"LOCALAPPDATA", base, LONG_PATH)) return INVALID_HANDLE_VALUE;
    _snwprintf(path, LONG_PATH, L"%ls\\PatchbayLauncher", base);
    CreateDirectoryW(path, NULL);
    _snwprintf(path, LONG_PATH, L"%ls\\PatchbayLauncher\\logs", base);
    CreateDirectoryW(path, NULL);
    _snwprintf(path, LONG_PATH, L"%ls\\PatchbayLauncher\\logs\\launcher.log", base);
    path[LONG_PATH - 1] = 0;
    SECURITY_ATTRIBUTES sa = { sizeof sa, NULL, TRUE };
    return CreateFileW(path, FILE_APPEND_DATA, FILE_SHARE_READ | FILE_SHARE_WRITE, &sa, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
}

int WINAPI wWinMain(HINSTANCE h, HINSTANCE prev, PWSTR args, int show) {
    (void) h; (void) prev; (void) args; (void) show;
    wchar_t exe[LONG_PATH], dir[LONG_PATH], jar[LONG_PATH], java[LONG_PATH];
    DWORD n = GetModuleFileNameW(NULL, exe, LONG_PATH);
    if (n == 0 || n >= LONG_PATH) return 1;
    wcscpy(dir, exe);
    parent(dir);
    _snwprintf(jar, LONG_PATH, L"%ls\\app\\launcher.jar", dir);
    jar[LONG_PATH - 1] = 0;
    if (!is_file(jar)) {
        fail(L"Brakuje pliku app\\launcher.jar obok programu.\n\nRozpakuj cały plik ZIP do jednego folderu i uruchom Patchbay Launcher.exe stamtąd (nie z podglądu ZIP).",
             L"Die Datei app\\launcher.jar fehlt neben dem Programm.\n\nEntpacke die ganze ZIP-Datei in einen Ordner und starte Patchbay Launcher.exe von dort (nicht aus der ZIP-Vorschau).",
             L"Le fichier app\\launcher.jar est absent à côté du programme.\n\nDécompressez tout le fichier ZIP dans un dossier et lancez Patchbay Launcher.exe depuis celui-ci (pas depuis l'aperçu du ZIP).",
             L"app\\launcher.jar is missing next to the program.\n\nUnpack the whole ZIP file into one folder and start Patchbay Launcher.exe from there (not from the ZIP preview).");
        return 2;
    }
    if (!find_java(java, LONG_PATH)) {
        fail(L"Nie znaleziono Bitwig Studio.\n\nPatchbay Launcher działa na Javie wbudowanej w Bitwig Studio. Zainstaluj Bitwig Studio (domyślnie C:\\Program Files\\Bitwig Studio) i spróbuj ponownie.",
             L"Bitwig Studio wurde nicht gefunden.\n\nPatchbay Launcher läuft mit der Java von Bitwig Studio. Installiere Bitwig Studio (standardmäßig C:\\Program Files\\Bitwig Studio) und versuche es erneut.",
             L"Bitwig Studio est introuvable.\n\nPatchbay Launcher utilise le Java fourni avec Bitwig Studio. Installez Bitwig Studio (par défaut C:\\Program Files\\Bitwig Studio) et réessayez.",
             L"Bitwig Studio wasn't found.\n\nPatchbay Launcher runs on the Java that comes with Bitwig Studio. Install Bitwig Studio (by default C:\\Program Files\\Bitwig Studio) and try again.");
        return 3;
    }
    static wchar_t cmd[3 * LONG_PATH + 128];
    _snwprintf(cmd, sizeof cmd / sizeof cmd[0], L"\"%ls\" \"-Dlauncher.exe=%ls\" -jar \"%ls\"", java, exe, jar);
    cmd[sizeof cmd / sizeof cmd[0] - 1] = 0;

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof si);
    ZeroMemory(&pi, sizeof pi);
    si.cb = sizeof si;
    HANDLE log = open_log();
    BOOL inherit = FALSE;
    if (log != INVALID_HANDLE_VALUE) {
        si.dwFlags = STARTF_USESTDHANDLES;
        si.hStdInput = NULL;
        si.hStdOutput = log;
        si.hStdError = log;
        inherit = TRUE;
    }
    if (!CreateProcessW(java, cmd, NULL, NULL, inherit, CREATE_NO_WINDOW, NULL, dir, &si, &pi)) {
        wchar_t msg[LONG_PATH + 256];
        _snwprintf(msg, sizeof msg / sizeof msg[0], L"Java could not be started (error %lu):\n%ls", GetLastError(), java);
        msg[sizeof msg / sizeof msg[0] - 1] = 0;
        MessageBoxW(NULL, msg, L"Patchbay Launcher", MB_OK | MB_ICONERROR);
        if (log != INVALID_HANDLE_VALUE) CloseHandle(log);
        return 4;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    if (log != INVALID_HANDLE_VALUE) CloseHandle(log);
    return 0;
}
