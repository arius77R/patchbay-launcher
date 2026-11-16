import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Whether a mod can run on the user's Bitwig, worked out the way Patchbay does it (its docs/maps.md):
 * Bitwig's internal names change with every release, so Patchbay translates a mod from the build in
 * its manifest's built_against to the installed build with the installed map pack. A mod runs when it
 * was built against the installed build itself, or when the pack for the installed build lists the
 * mod's build in builds.json and holds remap/<its build>__<installed build>.tsv.gz. A remap can still
 * miss something a mod needs, which Patchbay only finds when it prepares; such a mod shows as
 * "translated" here and as not available in Patchbay.
 */
final class Compat {
    private Compat() { }

    enum State { NATIVE, TRANSLATED, UNAVAILABLE, UNKNOWN }

    /** One Bitwig build a pack knows: build key, Bitwig version, platform, architecture. */
    record Build(String key, String version, String platform, String arch) {
        String label() { return version + " · " + platformName(platform); }
    }

    /** The pack for the user's Bitwig: its target build, the builds it lists and the source builds it has remaps from. */
    record Target(String bitwig, String platform, String packId, Build build, List<Build> builds, Set<String> remapsFrom) { }

    /** A mod's standing on the target, and a sentence saying why (empty for NATIVE). */
    record Verdict(State state, String reason) { }

    static String platformName(String p) {
        if (p == null) return "?";
        return switch (p.toLowerCase(Locale.ROOT)) {
            case "macos", "mac", "darwin" -> "macOS";
            case "windows", "win" -> "Windows";
            case "linux" -> "Linux";
            default -> p;
        };
    }

    /** "6.1" and "6.1.0" are the same Bitwig: trailing ".0" parts don't count. */
    static String norm(String v) {
        if (v == null) return null;
        String s = v.strip();
        while (s.matches(".*\\.\\d+") && s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    /** The Bitwig versions this platform's packs in a Patchbay install target, newest first (for a manual choice). */
    static List<String> versions(Path patchbayRoot, String platform) {
        List<String> out = new ArrayList<>();
        for (Target t : packs(patchbayRoot)) if (t.platform().equalsIgnoreCase(platform)) out.add(t.bitwig());
        out.sort((a, b) -> PatchbayLauncher.compareVersions(b, a));
        return out;
    }

    /** The pack in patchbayRoot/maps for Bitwig bitwig on platform, or null when Patchbay has none for it. */
    static Target target(Path patchbayRoot, String bitwig, String platform) {
        if (patchbayRoot == null || bitwig == null) return null;
        for (Target t : packs(patchbayRoot))
            if (t.platform().equalsIgnoreCase(platform) && Objects.equals(norm(t.bitwig()), norm(bitwig))) return t;
        return null;
    }

    static List<Target> packs(Path patchbayRoot) {
        List<Target> out = new ArrayList<>();
        Path maps = patchbayRoot == null ? null : patchbayRoot.resolve("maps");
        if (maps == null || !Files.isDirectory(maps)) return out;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(maps, Files::isDirectory)) {
            for (Path dir : dirs) {
                Target t = readPack(dir);
                if (t != null) out.add(t);
            }
        } catch (IOException ignored) { }
        return out;
    }

    static Target readPack(Path dir) {
        try {
            Map<String, Object> map = Mods.Json.object(Mods.Json.parse(Files.readString(dir.resolve("map.json"))));
            Map<String, Object> builds = Mods.Json.object(Mods.Json.parse(Files.readString(dir.resolve("builds.json"))));
            String targetKey = Mods.Json.text(map, "target_build_key");
            List<Build> list = new ArrayList<>();
            Build target = null;
            if (builds.get("builds") instanceof List<?> items) {
                for (Object o : items) {
                    Map<String, Object> b = Mods.Json.object(o);
                    Build build = new Build(Mods.Json.text(b, "build_key"), Mods.Json.text(b, "bitwig_version"),
                        Mods.Json.text(b, "platform"), Mods.Json.text(b, "architecture"));
                    list.add(build);
                    if (Objects.equals(build.key(), targetKey)) target = build;
                }
            }
            if (target == null) return null;
            Set<String> from = new HashSet<>();
            Path remap = dir.resolve("remap");
            if (Files.isDirectory(remap)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(remap, "*__" + targetKey + ".tsv.gz")) {
                    for (Path f : files) {
                        String name = f.getFileName().toString();
                        from.add(name.substring(0, name.indexOf("__")));
                    }
                }
            }
            return new Target(target.version(), target.platform(), Mods.Json.text(map, "id"), target, List.copyOf(list), Set.copyOf(from));
        } catch (IOException | RuntimeException bad) {
            return null; // not a pack, or a damaged one: Patchbay reports that itself
        }
    }

    /**
     * Where a mod built against (version, platform, arch) stands on target. target null with a known
     * Bitwig means Patchbay has no map for it: then no mod can load.
     */
    static Verdict check(Target target, String bitwig, String platform, String builtVersion, String builtPlatform, String builtArch) {
        if (bitwig == null) return new Verdict(State.UNKNOWN, "");
        if (target == null) return new Verdict(State.UNAVAILABLE, fill(PatchbayLauncher.t(
            "Ten Patchbay nie ma mapy dla Bitwig {mine}, więc nie uruchomi żadnego moda. Zaktualizuj Patchbay albo Bitwig.",
            "This Patchbay has no map for Bitwig {mine}, so it can't load any mod. Update Patchbay or Bitwig."), "", bitwig + " · " + platformName(platform)));
        if (builtVersion == null || builtPlatform == null) return new Verdict(State.UNKNOWN, "");
        Build source = null;
        for (Build b : target.builds()) {
            if (Objects.equals(b.version(), builtVersion) && b.platform() != null && b.platform().equalsIgnoreCase(builtPlatform)
                && (builtArch == null || b.arch() == null || b.arch().equalsIgnoreCase(builtArch))) { source = b; break; }
        }
        String built = builtVersion + " · " + platformName(builtPlatform);
        String mine = target.build().label();
        if (source == null) return new Verdict(State.UNAVAILABLE, fill(PatchbayLauncher.t(
            "Mod jest zbudowany dla Bitwig {built}. Mapa Patchbay dla Twojego Bitwiga ({mine}) nie zna tej wersji, więc nie da się go uruchomić.",
            "The mod is built for Bitwig {built}. Patchbay's map for your Bitwig ({mine}) doesn't know that build, so it can't run."), built, mine));
        if (source.key().equals(target.build().key())) return new Verdict(State.NATIVE, "");
        if (target.remapsFrom().contains(source.key())) return new Verdict(State.TRANSLATED, fill(PatchbayLauncher.t(
            "Zbudowany dla Bitwig {built}. Patchbay przetłumaczy go na Twój Bitwig {mine}.",
            "Built for Bitwig {built}. Patchbay translates it to your Bitwig {mine}."), built, mine));
        return new Verdict(State.UNAVAILABLE, fill(PatchbayLauncher.t(
            "Mod jest zbudowany dla Bitwig {built}, a mapa Patchbay dla Twojego Bitwiga ({mine}) nie ma tłumaczenia z tej wersji.",
            "The mod is built for Bitwig {built}, and Patchbay's map for your Bitwig ({mine}) has no translation from that build."), built, mine));
    }

    private static String fill(String template, String built, String mine) {
        return template.replace("{built}", built).replace("{mine}", mine);
    }
}
