"""Checks a release jar in a real client of any version, outside the dev runs: the loader the CurseForge app installed
(its version files, libraries and assets), a throwaway game folder holding the jar, the mods given and a copy of a
world to open, offline, muted, its window behind the others and the mouse left alone (the jar's own culprit.keepMouse).
The world is opened at once (quick play), so the server's hooks apply as well as the client's, and Mixin counts every
hook's injections: a hook that finds no place in players' games (an optional one is otherwise silent) stops the game
with its name in the log. The client is closed after the given seconds; before that, its threads and the screens it
holds are written beside its log (a client waiting at a question shows which). Its log is the game folder's
logs/latest.log.

    python tools/prod_client.py <game dir> <release jar> <world folder> <seconds> [<mod jar> ...]

PROD_VERSION names the loader's version folder (default forge-47.4.28: neoforge-26.3.0.103, neoforge-26.1.2.126,
neoforge-21.1.266 for the others) and PROD_JAVA the java.exe to run it on (the CurseForge runtimes: java-runtime-epsilon
for 26.x, java-runtime-delta for 1.21.1; Gradle's JDK 17 by default). The world's copy has its experimental-settings
question answered (the Lab's flat world is "experimental", and 26.x would wait at it).
"""
import gzip
import json
import os
import shutil
import struct
import subprocess
import sys
from pathlib import Path

HOME = Path(os.path.expanduser("~"))
INSTALL = HOME / "curseforge" / "minecraft" / "Install"
LOADER = os.environ.get("PROD_VERSION", "forge-47.4.28")
LIBS = INSTALL / "libraries"


def java() -> Path:
    if os.environ.get("PROD_JAVA"):
        return Path(os.environ["PROD_JAVA"])
    for d in sorted((HOME / ".gradle" / "jdks").glob("eclipse_adoptium-17-amd64-windows*")):
        if (d / "bin" / "java.exe").exists():
            return d / "bin" / "java.exe"
    raise SystemExit("no JDK 17 in ~/.gradle/jdks (a Gradle build of the 1.20.1 branch fetches one)")


def jcmd() -> Path | None:
    for d in sorted((HOME / ".gradle" / "jdks").glob("eclipse_adoptium-25-amd64-windows*")):
        if (d / "bin" / "jcmd.exe").exists():
            return d / "bin" / "jcmd.exe"
    return None


def allowed(rules) -> bool:
    """A library's or an argument's rules, for Windows on x86-64 with no launcher features."""
    if not rules:
        return True
    ok = False
    for r in rules:
        os_rule = r.get("os")
        match = "features" not in r and (os_rule is None or (os_rule.get("name") in (None, "windows")
                                                             and os_rule.get("arch") in (None, "x86_64", "amd64")))
        if match:
            ok = r["action"] == "allow"
    return ok


def lib_path(lib) -> Path:
    art = lib.get("downloads", {}).get("artifact")
    if art and art.get("path"):
        return LIBS / art["path"]
    group, name, version, *rest = lib["name"].split(":")
    classifier = ("-" + rest[0]) if rest else ""
    return LIBS / group.replace(".", "/") / name / version / f"{name}-{version}{classifier}.jar"


def args_of(entries, subs) -> list[str]:
    out = []
    for e in entries:
        if isinstance(e, str):
            out.append(e)
        elif allowed(e.get("rules")):
            out.extend(e["value"] if isinstance(e["value"], list) else [e["value"]])
    for k, v in subs.items():
        out = [a.replace("${" + k + "}", v) for a in out]
    return out


def confirm_experimental(world: Path):
    """Marks the world's experimental-settings warning as answered, as a player does the first time."""
    p = world / "level.dat"
    raw = gzip.decompress(p.read_bytes())
    name = b"confirmedExperimentalSettings"
    tag = b"\x01" + struct.pack(">H", len(name)) + name
    i = raw.find(tag)
    if i >= 0:
        j = i + len(tag)
        p.write_bytes(gzip.compress(raw[:j] + b"\x01" + raw[j + 1:]))


def main():
    game, jar, world, seconds = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]), int(sys.argv[4])
    mods = [Path(m) for m in sys.argv[5:]]
    if game.exists():
        shutil.rmtree(game)
    (game / "mods").mkdir(parents=True)
    (game / "config").mkdir()
    for m in [jar, *mods]:
        shutil.copy2(m, game / "mods" / m.name)
    shutil.copytree(world, game / "saves" / world.name, ignore=shutil.ignore_patterns("session.lock"))
    confirm_experimental(game / "saves" / world.name)
    (game / "options.txt").write_text("soundCategory_master:0.0\nonboardAccessibility:false\npauseOnLostFocus:false\n", encoding="utf-8")
    (game / "config" / "fml.toml").write_text("earlyWindowControl = false\n", encoding="utf-8")   # (made before any mod: it would take the focus)

    loader = json.loads((INSTALL / "versions" / LOADER / f"{LOADER}.json").read_text(encoding="utf-8"))
    vanilla = json.loads((INSTALL / "versions" / loader["inheritsFrom"] / f"{loader['inheritsFrom']}.json").read_text(encoding="utf-8"))
    cp, seen = [], set()
    for lib in loader["libraries"] + vanilla["libraries"]:      # the loader's first: its newer libraries win
        key = lib["name"].rsplit(":", 1)[0] if lib["name"].count(":") == 2 else lib["name"]
        if allowed(lib.get("rules")) and key not in seen and lib_path(lib).exists():
            seen.add(key)
            cp.append(str(lib_path(lib)))
    subs = {
        "natives_directory": str(game / "natives"), "launcher_name": "prod_client", "launcher_version": "1",
        "classpath": os.pathsep.join(cp), "classpath_separator": os.pathsep, "library_directory": str(LIBS),
        "version_name": LOADER, "auth_player_name": "Dev", "game_directory": str(game), "assets_root": str(INSTALL / "assets"),
        "assets_index_name": vanilla["assetIndex"]["id"], "auth_uuid": "00000000000000000000000000000000",
        "auth_access_token": "0", "clientid": "0", "auth_xuid": "0", "user_type": "legacy", "version_type": "release",
    }
    (game / "natives").mkdir()
    jvm = ["-Xmx4G", "-Dculprit.keepMouse=true", "-Dmixin.debug.countInjections=true"]
    jvm += args_of(loader["arguments"]["jvm"], subs) + args_of(vanilla["arguments"]["jvm"], subs)
    game_args = args_of(vanilla["arguments"]["game"], subs) + args_of(loader["arguments"]["game"], subs)
    game_args += ["--width", "1280", "--height", "720", "--quickPlaySingleplayer", world.name]
    with open(game / "launch.log", "w", encoding="utf-8") as out:
        proc = subprocess.Popen([str(java()), *jvm, loader["mainClass"], *game_args], cwd=game, stdout=out, stderr=subprocess.STDOUT)
        try:
            proc.wait(timeout=seconds)
        except subprocess.TimeoutExpired:
            tool = jcmd()
            if tool:
                for what, name in (("Thread.print", "threads.txt"), ("GC.class_histogram", "histogram.txt")):
                    with open(game / name, "w", encoding="utf-8") as t:
                        subprocess.run([str(tool), str(proc.pid), what], stdout=t, stderr=subprocess.STDOUT, timeout=60)
            proc.kill()
            proc.wait()
    print("exit", proc.returncode, "- log:", game / "logs" / "latest.log")


main()
