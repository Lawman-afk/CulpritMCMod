"""Turns main's sources (Minecraft 26.3) into this branch's (Minecraft 1.21.1): docs/BACKPORT.md.

Reads every file of main at the commit in base.txt straight from git (so each run starts from main's own text) and writes
its port here: the Java sources through RULES (with this branch's stand-ins for 26.x's classes, dev.culprit.port),
the GameTests' structure saved as 1.21.1 reads it. Main's files that have
no place here (DROP) are left out; files edited by hand here (manual.txt) are kept as they are. This branch's own files
(the build files, this tool, docs/BACKPORT.md) are not main's and are never touched.

    python tools\\backport\\port.py              (everything)
    python tools\\backport\\port.py <path> ...   (just those of main's files)
    python tools\\backport\\port.py --show <path> (print one file's port)
    python tools\\backport\\port.py --sync <main's full commit hash>   (bring the branch up to it)
"""
from __future__ import annotations

import gzip
import io
import re
import struct
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
BASE = (HERE / "base.txt").read_text(encoding="utf-8").split()[0]
MANUAL = {line.strip() for line in (HERE / "manual.txt").read_text(encoding="utf-8").splitlines()
          if line.strip() and not line.startswith("#")} if (HERE / "manual.txt").exists() else set()

# 1.21.1's data version: what its structure templates are saved at.
DATA_VERSION = 3955

# Main's files with no place here.
DROP = [re.compile(p) for p in (
    r"docs/BACKPORT\.md", r"tools/backport/.*",
    # Main's photographer for the mod page (devworld/Showcase: real-world pictures, taken on main alone).
    r"src/main/java/dev/culprit/devworld/Showcase\.java",
    # 26.x's frame pipeline: the parts handed in (SubmitNodeCollector) and the prepared frame's passes (renderpearl).
    # 1.21.1 draws each thing as it goes, and this branch's own frame hooks time that (manual.txt).
    r"src/main/java/dev/culprit/mixin/(SubmitNodeFrameMixin|PreparedFrameMixin)\.java",
)]
# Main's paths that are this branch's own (build files changed here by hand): never overwritten.
OWN = {"build.gradle", "gradle.properties"}


def git(*args: str) -> bytes:
    return subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True).stdout


def main_files() -> list[str]:
    return git("ls-tree", "-r", "--name-only", BASE).decode("utf-8").splitlines()


_batch = None


def read_main(path: str) -> bytes:
    """A file of main's at BASE (through one long-running `git cat-file --batch`)."""
    global _batch
    if _batch is None:
        _batch = subprocess.Popen(["git", "-C", str(ROOT), "cat-file", "--batch"], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    _batch.stdin.write(f"{BASE}:{path}\n".encode("utf-8"))
    _batch.stdin.flush()
    header = _batch.stdout.readline().decode("utf-8").split()
    if len(header) < 3 or header[1] == "missing":
        raise FileNotFoundError(path)
    data = _batch.stdout.read(int(header[2]))
    _batch.stdout.read(1)                                   # the newline after each object
    return data


# --- Java -----------------------------------------------------------------------------------------------------------------
def sub(pattern: str, repl, flags: int = 0):
    return re.compile(pattern, flags), repl


# Whole lines removed.
DROP_LINES = [re.compile(p) for p in (
)]

# Imports: 26.3 class -> 1.21.1 class (or this branch's stand-in); None drops the import.
IMPORTS = {
    "net.minecraft.util.FileUtil": "net.minecraft.FileUtil",
    "net.minecraft.world.entity.EntitySpawnReason": "net.minecraft.world.entity.MobSpawnType",
    "net.minecraft.world.level.block.entity.SignTextSlot": None,
    "net.minecraft.server.permissions.PermissionCheck": None,
    "net.minecraft.server.permissions.PermissionSet": None,
    "net.minecraft.world.level.TicketStorage": None,
    "net.neoforged.neoforge.common.world.chunk.TicketSet": None,
    "net.minecraft.resources.Identifier": "net.minecraft.resources.ResourceLocation",
    "net.minecraft.world.entity.EntityTypes": "net.minecraft.world.entity.EntityType",
    "net.minecraft.client.renderer.rendertype.RenderTypes": "net.minecraft.client.renderer.RenderType",
    "net.minecraft.client.renderer.rendertype.RenderType": "net.minecraft.client.renderer.RenderType",
    "net.minecraft.util.LightCoordsUtil": "net.minecraft.client.renderer.LightTexture",
    "net.minecraft.client.gui.GuiGraphicsExtractor": "dev.culprit.port.GuiGraphicsExtractor",
    "net.neoforged.neoforge.client.network.ClientPacketDistributor": "net.neoforged.neoforge.network.PacketDistributor",
    "net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent": "dev.culprit.port.net.RegisterClientPayloadHandlersEvent",
    "net.minecraft.client.input.MouseButtonEvent": "dev.culprit.port.input.MouseButtonEvent",
    "net.minecraft.client.input.KeyEvent": "dev.culprit.port.input.KeyEvent",
    "net.minecraft.client.input.CharacterEvent": "dev.culprit.port.input.CharacterEvent",
    "net.minecraft.world.level.saveddata.SavedDataType": "dev.culprit.port.SavedDataType",
    "net.minecraft.world.entity.animal.pig.Pig": "net.minecraft.world.entity.animal.Pig",
    "net.minecraft.world.entity.animal.cow.Cow": "net.minecraft.world.entity.animal.Cow",
    "net.minecraft.world.entity.vehicle.minecart.Minecart": "net.minecraft.world.entity.vehicle.Minecart",
    "net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl": "net.minecraft.world.entity.projectile.ThrownEnderpearl",
    "net.minecraft.world.level.gamerules.GameRules": "net.minecraft.world.level.GameRules",
    "net.minecraft.util.Util": "net.minecraft.Util",
}

RULES = [
    # --- names ------------------------------------------------------------------------------------------------------------
    sub(r"\bIdentifier\b", "ResourceLocation"),
    sub(r"\bEntityTypes\.", "EntityType."),
    sub(r"\bRenderTypes\.", "RenderType."),
    sub(r"\bLightCoordsUtil\.", "LightTexture."),
    sub(r"\bnet\.minecraft\.util\.Util\b", "net.minecraft.Util"),
    sub(r"\bnet\.neoforged\.neoforge\.client\.network\.ClientPacketDistributor\.sendToServer\(",
        "net.neoforged.neoforge.network.PacketDistributor.sendToServer("),
    sub(r"(?<!\.)\bClientPacketDistributor\.sendToServer\(", "PacketDistributor.sendToServer("),
    sub(r"\bModConfig\.Type\.SYNCED\b", "ModConfig.Type.SERVER"),
    sub(r"\bModConfig\.Type\.LOCAL\b", "ModConfig.Type.COMMON"),
    sub(r"\.identifier\(\)", ".location()"),
    # ChunkPos: 26.x's is a record packed as one long; 1.21.1's a class with fields x and z.
    sub(r"\bChunkPos\.containing\(", "new ChunkPos("),
    sub(r"\bChunkPos\.pack\(", "ChunkPos.asLong("),
    sub(r"\.pack\(\)", ".toLong()"),                                # (all main's are chunk positions)
    sub(r"(\bchunkPosition\(\)|\bChunkPos\.containing\([^()]*\)|\bhome)\.([xz])\(\)", r"\1.\2"),
    # Chat's click and hover events: 26.x's are records by kind, 1.21.1's an action and a value.
    sub(r"\bnew (?:net\.minecraft\.network\.chat\.)?ClickEvent\.OpenFile\(((?:[^()]|\((?:[^()]|\([^()]*\))*\))*)\)",
        r"new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_FILE, \1)"),
    sub(r"\bnew (?:net\.minecraft\.network\.chat\.)?ClickEvent\.RunCommand\(((?:[^()]|\((?:[^()]|\([^()]*\))*\))*)\)",
        r"new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, \1)"),
    sub(r"\bnew (?:net\.minecraft\.network\.chat\.)?HoverEvent\.ShowText\(((?:[^()]|\((?:[^()]|\([^()]*\))*\))*)\)",
        r"new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, \1)"),
    # Permissions: 26.x's are sets checked against, 1.21.1's a level (config/CulpritServerConfig's mayScan takes the source).
    sub(r"\bmayScan\((\w+)\.permissions\(\)\)", r"mayScan(\1)"),
    sub(r"\bmayScan\(source\.permissions\(\)\)", "mayScan(source)"),
    # Registries: 26.x's getValue is 1.21.1's get.
    sub(r"\b(BuiltInRegistries\.\w+)\.getValue\(", r"\1.get("),
    # Game rules: 26.x's are read and set by key; 1.21.1's through the rule's value.
    sub(r"(\b\w+)\.get\(GameRules\.RANDOM_TICK_SPEED\)", r"\1.getInt(GameRules.RULE_RANDOMTICKING)"),
    sub(r"(\b\w+)\.set\(GameRules\.RANDOM_TICK_SPEED, ([^,()]+), (\w+)\)", r"\1.getRule(GameRules.RULE_RANDOMTICKING).set(\2, \3)"),
    # Riding: 26.x also says whether to send it to the client.
    sub(r"\.startRiding\(([^,()]+), (true|false), (?:true|false)\)", r".startRiding(\1, \2)"),
    sub(r"\.getForceLoadedChunks\(\)", ".getForcedChunks()"),
    # HUD layers: 26.x's draw through the extractor; 1.21.1's are given its graphics (wrapped: dev.culprit.port).
    sub(r"\.registerAbove\(([^,]+), ([^,]+(?:\([^()]*\))?), ([\w.]+)::(\w+)\)",
        r".registerAbove(\1, \2, (gg_, dt_) -> \3.\4(new dev.culprit.port.GuiGraphicsExtractor(gg_), dt_))"),
    # --- the Lab's names ----------------------------------------------------------------------------------------------------
    sub(r"\bnet\.minecraft\.util\.FileUtil\b", "net.minecraft.FileUtil"),
    sub(r"\bEntitySpawnReason\b", "MobSpawnType"),
    # --- the client ---------------------------------------------------------------------------------------------------------
    sub(r"\.gui\.screen\(\)", ".screen"),
    sub(r"\.gui\.setScreen\(", ".setScreen("),
    sub(r"\.gui\.hud\.isHidden\(\)", ".options.hideGui"),
    sub(r"(\b\w+)\.gui\.hud\.toggle\(\)", r"\1.options.hideGui = !\1.options.hideGui"),
    sub(r"\.gui\.hud\.getChat\(\)", ".gui.getChat()"),
    sub(r"\.mainCamera\(\)", ".getMainCamera()"),
    sub(r"(\b\w+)\.gameRenderer\.mainRenderTarget\(\)", r"\1.getMainRenderTarget()"),
    sub(r"\.getDeltaTracker\(\)", ".getTimer()"),
    sub(r"\bInputConstants\.Type\.KEYBOARD\b", "InputConstants.Type.KEYSYM"),
    # Key mappings: 1.21.1's categories are translation keys (26.x's KeyMapping.Category of an id: key.category.<ns>.<path>).
    sub(r"\bnew KeyMapping\.Category\(((?:[^()]|\([^()]*\))+)\)", r"dev.culprit.port.input.KeyEvent.category(\1)"),
    sub(r"\bKeyMapping\.Category\b", "String"),
    sub(r"^\s*e\.registerCategory\(\w+\);\s*$", "", re.M),
    # Client-bound payloads: 1.21.1 takes the handler with the codec (the client's, registered later: dev.culprit.port.net).
    sub(r"\.playToClient\(([\w.]+)\.TYPE, ([\w.]+)\.CODEC\)",
        r".playToClient(\1.TYPE, \2.CODEC, dev.culprit.port.net.RegisterClientPayloadHandlersEvent.handler(\1.TYPE))"),
    # --- screens: 26.x extracts what a screen draws, 1.21.1 renders it (the graphics wrapped: GuiGraphicsExtractor) --------
    sub(r"(\bpublic void )extractRenderState\(GuiGraphicsExtractor (\w+), (int \w+, int \w+, float \w+)\)(\s*\{)",
        r"\1render(net.minecraft.client.gui.GuiGraphics gg_, \3)\4 GuiGraphicsExtractor \2 = new GuiGraphicsExtractor(gg_);"),
    sub(r"\bsuper\.extractRenderState\((\w+), (\w+, \w+, \w+)\)", r"super.render(\1.unwrap(), \2)"),
    sub(r"(\bpublic void )extractBackground\(GuiGraphicsExtractor (\w+), (int \w+, int \w+, float \w+)\)(\s*\{)",
        r"\1renderBackground(net.minecraft.client.gui.GuiGraphics gg_, \3)\4 GuiGraphicsExtractor \2 = new GuiGraphicsExtractor(gg_);"),
    sub(r"\bsuper\.extractBackground\((\w+), (\w+, \w+, \w+)\)", r"super.renderBackground(\1.unwrap(), \2)"),
    # Screens' input: 26.x hands an event, 1.21.1 the position and button (or key, or character); the event made from them.
    sub(r"(\bpublic boolean )mouseClicked\(MouseButtonEvent (\w+), boolean (\w+)\)(\s*\{)",
        r"\1mouseClicked(double mouseX_, double mouseY_, int button_)\4 MouseButtonEvent \2 = new MouseButtonEvent(mouseX_, mouseY_, button_); boolean \3 = false;"),
    sub(r"\bsuper\.mouseClicked\((\w+), \w+\)", r"super.mouseClicked(\1.x(), \1.y(), \1.button())"),
    sub(r"(\bpublic boolean )keyPressed\(KeyEvent (\w+)\)(\s*\{)",
        r"\1keyPressed(int key_, int scancode_, int modifiers_)\3 KeyEvent \2 = new KeyEvent(key_, scancode_, modifiers_);"),
    sub(r"\bsuper\.keyPressed\((\w+)\)", r"super.keyPressed(\1.key(), \1.scancode(), \1.modifiers())"),
    sub(r"(\bpublic boolean )charTyped\(CharacterEvent (\w+)\)(\s*\{)",
        r"\1charTyped(char char_, int modifiers_)\3 CharacterEvent \2 = new CharacterEvent(char_, modifiers_);"),
    sub(r"\bsuper\.charTyped\((\w+)\)", r"super.charTyped(\1.character(), \1.modifiers())"),
    # A widget handed main's event: 1.21.1's take its parts.
    sub(r"(\b\w+)\.charTyped\((\w+)\)", r"\1.charTyped(\2.character(), \2.modifiers())"),
    sub(r"(\b\w+)\.keyPressed\((\w+)\)", r"\1.keyPressed(\2.key(), \2.scancode(), \2.modifiers())"),
    # Saved data: 26.x's storage takes the type; the stand-in builds 1.21.1's factory with the level (dev.culprit.port).
    sub(r"((?:\w|\.|\(\))+)\.getDataStorage\(\)\.computeIfAbsent\((\w+(?:\.\w+)?)\)", r"\2.get(\1)"),
    sub(r"((?:\w|\.|\(\))+)\.getDataStorage\(\)\.get\((\w+(?:\.\w+)?)\)", r"\2.find(\1)"),
]


def method_bodies(text: str, signature: str, fn) -> str:
    """{fn} applied to each method whose declaration matches {signature} (up to its opening brace), body and all."""
    out, pos = [], 0
    for m in re.compile(signature, re.S).finditer(text):
        if m.start() < pos:
            continue
        depth, i = 0, m.end() - 1
        while i < len(text):
            ch = text[i]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    break
            elif ch == '"':                               # skip string literals
                i += 1
                while i < len(text) and text[i] != '"':
                    i += 2 if text[i] == "\\" else 1
            i += 1
        out.append(text[pos:m.start()])
        out.append(fn(text[m.start():i + 1]))
        pos = i + 1
    out.append(text[pos:])
    return "".join(out)


FUNCTIONS = []


def port_imports(text: str) -> str:
    def repl(m: re.Match) -> str:
        name = m.group(2)
        static = m.group(1) or ""
        if name in IMPORTS:
            new = IMPORTS[name]
            return "" if new is None else f"import {static}{new};"
        return m.group(0)
    text = re.sub(r"import (static )?([\w.]+);", repl, text)
    # One import, once (two 26.3 classes may both become the same 1.21.1 one).
    seen, out = set(), []
    for line in text.split("\n"):
        if line.startswith("import ") and line in seen:
            continue
        if line.startswith("import "):
            seen.add(line)
        out.append(line)
    return "\n".join(out)


def port_java(text: str, path: str) -> str:
    lines = [ln for ln in text.split("\n") if not any(p.match(ln) for p in DROP_LINES)]
    text = "\n".join(lines)
    text = port_imports(text)
    for pattern, repl in RULES:
        text = pattern.sub(repl, text)
    for fn in FUNCTIONS:
        text = fn(text)
    for fix in FILE_FIXES.get(path, []):
        text = fix(text)
    return text


# Per-file fixes (a function of the text each), for what only one file does.
def after(anchor: str, insert: str):
    def fix(text: str) -> str:
        assert anchor in text, f"port.py: '{anchor}' is gone from main: update FILE_FIXES"
        return text.replace(anchor, anchor + insert, 1)
    return fix


def swap(old: str, new: str):
    def fix(text: str) -> str:
        assert old in text, f"port.py: '{old}' is gone from main: update FILE_FIXES"
        return text.replace(old, new)
    return fix


def resub(pattern: str, repl: str, count: int = 0):
    def fix(text: str) -> str:
        new, n = re.subn(pattern, repl, text, count=count)
        assert n, f"port.py: /{pattern}/ is gone from main: update FILE_FIXES"
        return new
    return fix


def replace_method(signature: str, new: str):
    """Replaces the method whose declaration begins with {signature} (to its closing brace) with {new}."""
    def fix(text: str) -> str:
        assert signature in text, f"port.py: '{signature}' is gone from main: update FILE_FIXES"
        return method_bodies(text, re.escape(signature) + r"[^{]*\{", lambda method: new)
    return fix


def drop_method(signature: str):
    """Removes the method whose declaration begins with {signature}."""
    def fix(text: str) -> str:
        assert signature in text, f"port.py: '{signature}' is gone from main: update FILE_FIXES"
        return method_bodies(text, r"\n[ \t]*" + re.escape(signature) + r"[^{]*\{", lambda method: "\n")
    return fix


def member_span(text: str, marker: str) -> tuple[int, int]:
    """Where the member whose declaration holds {marker} lies: from the end of the member before it (its annotations,
    comments and blank lines with it) to its closing brace."""
    i = text.index(marker)
    line_start = text.rfind("\n", 0, i) + 1
    prev = max(text.rfind("}\n", 0, line_start), text.rfind(";\n", 0, line_start), text.rfind("{\n", 0, line_start))
    start = prev + 2
    semi, brace = text.find(";", i), text.find("{", i)
    if semi != -1 and (brace == -1 or semi < brace):
        return start, semi + 1                              # a field: its declaration ends at the semicolon
    depth, j = 0, brace
    while j < len(text):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                break
        elif text[j] == '"':
            j += 1
            while j < len(text) and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
        j += 1
    return start, j + 1


def drop_member(marker: str):
    """Removes the member (method or field with its annotations and comments) whose declaration holds {marker}."""
    def fix(text: str) -> str:
        assert marker in text, f"port.py: '{marker}' is gone from main: update FILE_FIXES"
        start, end = member_span(text, marker)
        return text[:start] + text[end + 1:]
    return fix


def replace_member(marker: str, new: str):
    """Replaces the member whose declaration holds {marker} (with its annotations and comments) with {new}."""
    def fix(text: str) -> str:
        assert marker in text, f"port.py: '{marker}' is gone from main: update FILE_FIXES"
        start, end = member_span(text, marker)
        return text[:start] + "\n" + new.strip("\n") + text[end:]
    return fix


def timed(name: str, annotation: str, params: str, args: str, phase: str) -> str:
    """A frame hook of the main's kind: the call it wraps timed as {phase} while a frame scan runs."""
    return f"""
    {annotation}
    private void culprit${name}({params}Operation<Void> original) {{
        if (!FrameProbe.measuring()) {{
            original.call({args});
            return;
        }}
        long start = FrameProbe.begin();
        try {{
            original.call({args});
        }} finally {{
            FrameProbe.phase(FramePhase.{phase}, FrameProbe.end(start));
        }}
    }}"""


MAY_SCAN = """    public static int scanLevel() {
        return SPEC.isLoaded() ? SCAN_LEVEL.get() : 2;
    }

    /** Whether a command's source may scan: 1.21.1's permission levels (main checks 26.x's permission sets). */
    public static boolean mayScan(net.minecraft.commands.CommandSourceStack source) {
        return source.hasPermission(scanLevel());
    }

    public static boolean mayScan(net.minecraft.world.entity.player.Player player) {
        return player.hasPermissions(scanLevel());
    }"""

REASON_OF = """    static byte reasonOf(TicketType<?> type) {
        if (type == TicketType.PLAYER) {
            return ChunkData.PLAYERS;
        }
        if (type == TicketType.FORCED) {
            return ChunkData.FORCED;
        }
        if (type == TicketType.PORTAL) {
            return ChunkData.PORTALS;
        }
        if (type == TicketType.START) {
            return ChunkData.SPAWN;
        }
        if (type == TicketType.UNKNOWN) {
            return ChunkData.REQUESTS;
        }
        // 1.21.1's ticket types are named, not registered: the game's own by plain names, NeoForge's and mods' as they choose.
        return VANILLA.contains(type.toString()) ? ChunkData.OTHER : ChunkData.MODS;
    }

    private static final java.util.Set<String> VANILLA = java.util.Set.of("start", "dragon", "player", "forced", "light", "portal",
            "post_teleport", "unknown");"""

OWNER_OF = """    /** Who or what is behind a ticket, in a few words. */
    private static String ownerOf(ServerLevel level, long chunk, TicketType<?> type, byte reason, Long2ObjectMap<List<String>> modOwners) {
        String typeName = type.toString();
        int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
        return switch (reason) {
            case ChunkData.PLAYERS -> {
                // A player's area is held by tickets across it, not one at their feet: the ticket is the nearest player's.
                ServerPlayer nearest = null;
                int best = Integer.MAX_VALUE;
                for (ServerPlayer player : level.players()) {
                    int d = Math.max(Math.abs(player.chunkPosition().x - cx), Math.abs(player.chunkPosition().z - cz));
                    if (d < best) {
                        best = d;
                        nearest = player;
                    }
                }
                yield nearest == null ? "a player" : nearest.getName().getString();
            }
            case ChunkData.PORTALS -> "a portal";
            // 1.21.1 keeps the spawn chunks loaded (26.x has none): named apart from their reason, "the world spawn".
            case ChunkData.SPAWN -> "the spawn chunks";
            // The game asked for the chunk just now (and lets go of it a tick later): who asked, if a request loaded it.
            case ChunkData.REQUESTS -> PassingRequests.describe(PassingRequests.of(level, chunk));
            case ChunkData.MODS -> {
                // A NeoForge chunk loader is the mod's and the block or entity that owns it; a mod's own kind of ticket its.
                List<String> owners = modOwners.get(chunk);
                if (typeName.startsWith("neoforge:")) {
                    yield owners != null && !owners.isEmpty() ? String.join("; ", owners) : "a mod's chunk loader";
                }
                yield typeName;
            }
            default -> typeName.equals("dragon") ? "the dragon fight" : typeName;
        };
    }"""

MOD_OWNERS = """    /**
     * The NeoForge chunk loaders of a level, by chunk: their controller (a mod's id and name) and the block or entity
     * that owns each, as 1.21.1 keeps them in the level's forced-chunk data.
     */
    private static Long2ObjectMap<List<String>> modOwners(ServerLevel level) {
        Long2ObjectMap<List<String>> owners = new Long2ObjectOpenHashMap<>();
        try {
            var data = level.getDataStorage().get(net.minecraft.world.level.ForcedChunksSavedData.factory(), "chunks");
            if (data == null) {
                return owners;
            }
            // "FTB Chunks: team Steve's Party", "Mekanism: Digital Miner at 10 64 -5": the mod, then what owns the loader.
            java.util.function.BiFunction<ResourceLocation, Object, String> block = (id, owner) ->
                    OwnerNames.mod(id.getNamespace()) + ": " + OwnerNames.block(level, (net.minecraft.core.BlockPos) owner);
            java.util.function.BiFunction<ResourceLocation, Object, String> entity = (id, owner) ->
                    OwnerNames.mod(id.getNamespace()) + ": " + OwnerNames.uuid(level.getServer(), level, (java.util.UUID) owner);
            add(owners, data.getBlockForcedChunks().getChunks(), block);
            add(owners, data.getBlockForcedChunks().getTickingChunks(), block);
            add(owners, data.getEntityForcedChunks().getChunks(), entity);
            add(owners, data.getEntityForcedChunks().getTickingChunks(), entity);
        } catch (RuntimeException e) {
            Culprit.LOGGER.debug("Culprit: could not read the mods' chunk loaders", e);
        }
        return owners;
    }"""

ADD = """    private static void add(Long2ObjectMap<List<String>> owners, java.util.Map<?, LongSet> byOwner,
                            java.util.function.BiFunction<ResourceLocation, Object, String> name) {
        for (var entry : byOwner.entrySet()) {
            var key = (dev.culprit.mixin.TicketOwnerAccessor) entry.getKey();
            String owner = name.apply(key.culprit$id(), key.culprit$owner());
            for (long chunk : entry.getValue()) {
                List<String> list = owners.computeIfAbsent(chunk, k -> new ArrayList<>());
                if (!list.contains(owner)) {
                    list.add(owner);
                }
            }
        }
    }"""

# The world overlay, 1.21.1's way: 26.x hands its shapes and labels to a feature dispatcher of its own and draws them in a
# render pass; 1.21.1 draws them at NeoForge's after-level stage through the game's buffers (Ground Burst's way there).
OVERLAY_FIELDS = """    /** 1.21.1 has no line width per vertex: the lines are drawn a width at a time, each width its own render type. */
    private static final float[] WIDTHS = {1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f};
    private static final RenderType[] LINE_TYPES = new RenderType[WIDTHS.length];
    private static final RenderType FILL_TYPE = Types.fills();
    private static final RenderType BACKING = Types.backing(false), BACKING_SEE_THROUGH = Types.backing(true);
    private static int drawingWidth;
    // Drawing has begun this frame: the view, projection and fog to put back.
    private static boolean begun;
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static VertexSorting sorting = VertexSorting.DISTANCE_TO_ORIGIN;
    private static float fogStart;

    static {
        for (int i = 0; i < WIDTHS.length; i++) {
            LINE_TYPES[i] = Types.lines(WIDTHS[i]);
        }
    }
"""

OVERLAY_DRAWING = """    /**
     * Drawn after the level, at NeoForge's after-level stage, which 1.21.1 fires once the level's drawing has returned
     * (after a shader pack's last passes and Distant Horizons' far terrain): into the finished picture, depth tested
     * against it, the way Ground Burst draws its effects there. The stage's own view and projection (Iris moves the
     * view's bobbing between them), and no fog: the boxes and labels are to be read, not seen in the world.
     */
    private static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        try {
            draw(event);
        } finally {
            end();
        }
    }

    private static MultiBufferSource.BufferSource buffers() {
        return Minecraft.getInstance().renderBuffers().bufferSource();
    }

    private static void begin(RenderLevelStageEvent event) {
        if (begun) {
            return;
        }
        begun = true;
        PROJECTION.set(RenderSystem.getProjectionMatrix());
        sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        fogStart = RenderSystem.getShaderFogStart();
        FogRenderer.setupNoFog();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    /** This frame's boxes, outlines and beam: the faint fills first, then the lines a width at a time. */
    private static void shapes() {
        MultiBufferSource.BufferSource buffers = buffers();
        fills(IDENTITY.last(), buffers.getBuffer(FILL_TYPE));
        for (drawingWidth = 0; drawingWidth < WIDTHS.length; drawingWidth++) {
            lines(IDENTITY.last(), buffers.getBuffer(LINE_TYPES[drawingWidth]));
        }
    }

    /**
     * A label's line: its backing first, then its text. (1.21.1's font draws a line's backing after its text, a hair
     * behind it, which at a label's distance from the camera covered the text in part.)
     */
    private static void labelLine(Font font, FormattedCharSequence line, float x, float y, Font.DisplayMode mode, int backing) {
        Matrix4f pose = LABEL.last().pose();
        float x1 = x + font.width(line) + 1;
        VertexConsumer b = buffers().getBuffer(mode == Font.DisplayMode.SEE_THROUGH ? BACKING_SEE_THROUGH : BACKING);
        b.addVertex(pose, x - 1, y - 1, 0).setColor(backing);
        b.addVertex(pose, x - 1, y + 9, 0).setColor(backing);
        b.addVertex(pose, x1, y + 9, 0).setColor(backing);
        b.addVertex(pose, x1, y - 1, 0).setColor(backing);
        font.drawInBatch(line, x, y, 0xFFFFFFFF, false, pose, buffers(), mode, 0, LightTexture.FULL_BRIGHT);
    }

    private static void end() {
        if (!begun) {
            return;
        }
        begun = false;
        try {
            buffers().endBatch();
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(PROJECTION, sorting);
            RenderSystem.setShaderFogStart(fogStart);
        }
    }

    /** The overlay's render types: the lines a width each, growing with the window as the game's own; fills writing no depth. */
    private abstract static class Types extends RenderType {
        private Types() {
            super("", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }

        static RenderType lines(float width) {
            return create(dev.culprit.Culprit.MODID + "_lines_" + width, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
                    false, false, CompositeState.builder()
                            .setShaderState(RENDERTYPE_LINES_SHADER)
                            .setLineState(new Width(width))
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }

        static RenderType fills() {
            return create(dev.culprit.Culprit.MODID + "_fills", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
                    CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }

        /** A label's backing, seen through walls with its label or not. */
        static RenderType backing(boolean seeThrough) {
            return create(dev.culprit.Culprit.MODID + (seeThrough ? "_label_backing_see_through" : "_label_backing"), DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(seeThrough ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }

        /** A line width in pixels at 1920 across, wider on a wider window (the game's own lines' rule). */
        private static final class Width extends LineStateShard {
            private final float width;

            Width(float width) {
                super(java.util.OptionalDouble.of(width));
                this.width = width;
            }

            @Override
            public void setupRenderState() {
                RenderSystem.lineWidth(Math.max(this.width, Minecraft.getInstance().getWindow().getWidth() / 1920f * this.width));
            }
        }
    }"""

OVERLAY_LINE = """    private static void line(VertexConsumer b, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int c, float w) {
        if (Mth.clamp(Math.round(w * 2) - 3, 0, WIDTHS.length - 1) != drawingWidth) {
            return;                                     // (drawn with its width's render type)
        }
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        b.addVertex(p, x0, y0, z0).setColor(c).setNormal(p, dx, dy, dz);
        b.addVertex(p, x1, y1, z1).setColor(c).setNormal(p, dx, dy, dz);
    }"""

SUBMITS = """        STORAGE.order(0).afterTerrain.submit(new CustomFeatureRenderer.Submit(IDENTITY.last(), RenderType.debugQuads(), FILLS));
        STORAGE.order(0).afterTerrain.submit(new CustomFeatureRenderer.Submit(IDENTITY.last(), RenderType.linesTranslucent(), LINES));
"""

# The tick hooks whose places differ on 1.21.1: mob spawning is in tickChunks; the connections and the autosave are calls
# in the server's tick, not methods of their own.
SPAWNING = """    @WrapOperation(method = "tickChunks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V"))
    private void culprit$timeSpawning(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, boolean friendlies, boolean monsters,
                                      boolean persistent, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(level, chunk, state, friendlies, monsters, persistent);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(level, chunk, state, friendlies, monsters, persistent);
        } finally {
            Probe.spawning(level, chunk, Probe.end(start));
        }
    }"""

CONNECTIONS = """    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;tick()V"))
    private void culprit$timeConnections(net.minecraft.server.network.ServerConnectionListener connections, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(connections);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(connections);
        } finally {
            Probe.phase(Phase.CONNECTIONS, Probe.end(start));
        }
    }"""

AUTOSAVE = """    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;saveEverything(ZZZ)Z"))
    private boolean culprit$timeAutosave(MinecraftServer server, boolean quietly, boolean flush, boolean forced, Operation<Boolean> original) {
        if (!Probe.measuring()) {
            return original.call(server, quietly, flush, forced);
        }
        long start = Probe.begin();
        try {
            return original.call(server, quietly, flush, forced);
        } finally {
            Probe.phase(Phase.AUTOSAVE, Probe.end(start));
        }
    }"""

FILE_FIXES: dict[str, list] = {
    # A class's mod: 1.21.1's mod files find a resource by its path.
    "src/main/java/dev/culprit/chunks/Callers.java": [
        swap("file.getFile().getContents().containsFile(path)", "java.nio.file.Files.exists(file.getFile().findResource(path))"),
    ],
    # 1.21.1's saved data saves itself (26.x's storage uses the type's codec).
    "src/main/java/dev/culprit/chunks/ForcedBy.java": [
        after("    public ForcedBy() {}", """

    @Override
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        return TYPE.save(this, tag, registries);
    }"""),
        # The game's test structures load their chunks from StructureUtils on 1.21.1 (26.x: their test instance block).
        swap('by.equals(Callers.GAME + "TestInstanceBlockEntity")', 'by.equals(Callers.GAME + "StructureUtils")'),
    ],
    # The names the server has seen: 1.21.1's profile cache.
    "src/main/java/dev/culprit/chunks/OwnerNames.java": [
        swap("var known = server.services().nameToIdCache().get(id);\n        return known.isPresent() ? known.get().name() :",
             "var cache = server.getProfileCache();\n        var known = cache == null ? java.util.Optional.<com.mojang.authlib.GameProfile>empty() : cache.get(id);\n"
             "        return known.isPresent() ? known.get().getName() :"),
    ],
    # The chunk census: 1.21.1's tickets are in the distance manager, its types named rather than registered, NeoForge's
    # chunk loaders kept in the level's forced-chunk data; no ender pearl tickets (pearls load no chunks on 1.21.1).
    "src/main/java/dev/culprit/chunks/ChunkCensus.java": [
        swap("TicketStorage storage = TicketStorage.TYPE.get(level);\n        Long2ObjectMap<List<String>> modOwners = modOwners(level, storage);",
             "var tickets = ((dev.culprit.mixin.DistanceManagerAccessor) level.getChunkSource().chunkMap.getDistanceManager()).culprit$tickets();\n"
             "        Long2ObjectMap<List<String>> modOwners = modOwners(level);"),
        swap("            for (Ticket ticket : storage.getTickets(key)) {\n                TicketType type = ticket.getType();\n"
             "                if (!type.doesLoad()) {\n                    continue;\n                }\n",
             "            var held = tickets.get(key);\n            for (Ticket<?> ticket : held == null ? List.<Ticket<?>>of() : held) {\n"
             "                TicketType<?> type = ticket.getType();\n"),
        replace_member("static byte reasonOf(TicketType type)", REASON_OF),
        drop_member("private static final ResourceLocation GENERATE ="),
        replace_member("private static String ownerOf(ServerLevel level, long chunk, TicketType type,", OWNER_OF),
        replace_member("private static Long2ObjectMap<List<String>> modOwners(ServerLevel level, TicketStorage storage)", MOD_OWNERS),
        replace_member("private static void add(Long2ObjectMap<List<String>> owners, TicketSet set, String owner)", ADD),
    ],
    # The GameTests' chunk positions: 1.21.1's ChunkPos has fields.
    "src/main/java/dev/culprit/gametest/ScanTests.java": [
        resub(r"\bchunk\.([xz])\(\)", r"chunk.\1"),
    ],
    # The frame scan's particles: 1.21.1's engine moves each one itself (26.x's particle groups do).
    "src/main/java/dev/culprit/mixin/ParticleFrameMixin.java": [
        swap("@Mixin(ParticleGroup.class)", "@Mixin(ParticleEngine.class)"),
        swap("import net.minecraft.client.particle.ParticleGroup;", "import net.minecraft.client.particle.ParticleEngine;"),
    ],
    # Sodium 0.8 for 1.21.1 takes in the finished chunk meshes in uploadChunks (0.9 for 26.x: processChunkBuilds).
    "src/main/java/dev/culprit/mixin/SodiumChunksFrameMixin.java": [
        swap('method = "processChunkBuilds"', 'method = "uploadChunks"'),
    ],
    # The frame's parts as 1.21.1 draws them (the language file says the same: TEXT_FIXES).
    "src/main/java/dev/culprit/scan/FramePhase.java": [
        swap('"particles, handed in"', '"particles, sent to the GPU"'),
        swap('"drawing the terrain"', '"drawing the world"'),
        swap('"drawing the handed-in parts"', '"drawing the buffered parts"'),
        swap('"the world\'s blocks, sky and weather in view"', '"which of the world\'s chunk sections are in view"'),
        swap('"all of them handed in, and the rarer kinds made ready"', '"their batches drawn (each kind\'s own share is with it)"'),
        swap('"terrain, sky and clouds sent to the GPU"', '"terrain, sky, clouds, weather and your hand sent to the GPU"'),
        swap('"the sorted parts sent to the GPU (the things\' shares are with them)"', '"the things\' parts sent to the GPU, a batch at a time"'),
    ],
    "src/main/java/dev/culprit/report/Breakdown.java": [
        swap('"made ready, handed in, their share of the drawing"', '"each one drawn, its parts buffered for the GPU"'),
        swap('"chests, signs: made ready, handed in, drawn"', '"chests, signs: each one drawn"'),
        swap('"each kind, moved and made ready"', '"each kind, moved and drawn"'),
    ],
    # The world overlay drawn 1.21.1's way (OVERLAY_* above).
    "src/main/java/dev/culprit/client/Overlay.java": [
        swap("import java.util.Optional;\nimport java.util.OptionalDouble;\n", ""),
        swap("import com.mojang.blaze3d.pipeline.RenderTarget;\n", ""),
        swap("import com.mojang.blaze3d.vertex.VertexConsumer;\nimport com.mojang.renderpearl.api.commands.RenderPass;\n",
             "import com.mojang.blaze3d.vertex.DefaultVertexFormat;\nimport com.mojang.blaze3d.vertex.VertexConsumer;\n"
             "import com.mojang.blaze3d.vertex.VertexFormat;\nimport com.mojang.blaze3d.vertex.VertexSorting;\n"),
        swap("import net.minecraft.client.renderer.SubmitNodeCollector;\nimport net.minecraft.client.renderer.SubmitNodeStorage;\n"
             "import net.minecraft.client.renderer.feature.CustomFeatureRenderer;\nimport net.minecraft.client.renderer.feature.FeatureRenderDispatcher;\n",
             "import net.minecraft.client.renderer.FogRenderer;\nimport net.minecraft.client.renderer.MultiBufferSource;\n"),
        swap("    private static final SubmitNodeStorage STORAGE = new SubmitNodeStorage();\n    private static @Nullable FeatureRenderDispatcher dispatcher;\n",
             OVERLAY_FIELDS),
        swap("    private static final SubmitNodeCollector.CustomGeometryRenderer LINES = Overlay::lines;\n"
             "    private static final SubmitNodeCollector.CustomGeometryRenderer FILLS = Overlay::fills;\n\n", ""),
        swap("NeoForge.EVENT_BUS.addListener(Overlay::draw);", "NeoForge.EVENT_BUS.addListener(Overlay::stage);"),
        resub(r"RenderLevelStageEvent\.AfterLevel\b", "RenderLevelStageEvent"),
        swap("var camera = event.getLevelRenderState().cameraRenderState;\n        Vec3 cam = camera.pos;\n        camera.orientation.transform(",
             "var camera = event.getCamera();\n        Vec3 cam = camera.getPosition();\n        camera.rotation().transform("),
        swap("labels(r, dim, mc, cam, camera.orientation);", "labels(r, dim, mc, cam, camera.rotation());"),
        swap("Quaternionf facing = camera.orientation;", "Quaternionf facing = camera.rotation();"),
        swap(SUBMITS, "        begin(event);\n        shapes();\n"),
        swap("        render(mc, event);\n", ""),
        swap("STORAGE.order(0).submitText(LABEL, -font.width(first) / 2f, second == null ? 0 : -10, first, false, mode, LightTexture.FULL_BRIGHT,\n"
             "                0xFFFFFFFF, bg, 0);",
             "labelLine(font, first, -font.width(first) / 2f, second == null ? 0 : -10, mode, bg);"),
        swap("STORAGE.order(0).submitText(LABEL, -font.width(second) / 2f, 0, second, false, mode, LightTexture.FULL_BRIGHT, 0xFFFFFFFF, bg, 0);",
             "labelLine(font, second, -font.width(second) / 2f, 0, mode, bg);"),
        replace_member("private static void render(Minecraft mc, RenderLevelStageEvent event)", OVERLAY_DRAWING),
        replace_member("private static void line(VertexConsumer b, PoseStack.Pose p,", OVERLAY_LINE),
        swap(" * arrays each frame and drawn by two geometry renderers made once.", " * arrays each frame and drawn through the game's buffers, a render type per line width."),
    ],
    # A fluid's random tick is given the level as a Level on 1.21.1 (a block's as a ServerLevel).
    "src/main/java/dev/culprit/mixin/ServerLevelTickMixin.java": [
        swap('"Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;',
             '"Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/world/level/Level;'),
        swap("culprit$timeFluidRandomTick(FluidState state, ServerLevel level,", "culprit$timeFluidRandomTick(FluidState state, net.minecraft.world.level.Level level,"),
        swap("Probe.randomTick(level, pos, state.getType(), Probe.end(start));", "Probe.randomTick((ServerLevel) level, pos, state.getType(), Probe.end(start));"),
    ],
    # Mob spawning a chunk at a time, in tickChunks on 1.21.1 (SPAWNING above).
    "src/main/java/dev/culprit/mixin/ServerChunkCacheMixin.java": [
        replace_member("private void culprit$timeSpawning(", SPAWNING),
    ],
    # The connections and the autosave, as calls in the server's tick on 1.21.1 (CONNECTIONS, AUTOSAVE above).
    "src/main/java/dev/culprit/mixin/MinecraftServerMixin.java": [
        replace_member("private void culprit$timeConnections(", CONNECTIONS),
        replace_member("private void culprit$timeAutosave(", AUTOSAVE),
    ],
    # The client's handlers for the server's payloads: 26.x fires its event for them itself; 1.21.1 has none, so the
    # client fires this branch's stand-in at its setup (port/net/RegisterClientPayloadHandlersEvent). Without it every
    # report, status and HUD line the server sent was dropped.
    "src/main/java/dev/culprit/client/ClientNetwork.java": [
        swap("    public static void register(IEventBus modBus) {\n",
             "    public static void register(IEventBus modBus) {\n        RegisterClientPayloadHandlersEvent.post(modBus);\n"),
    ],
    # Permissions by level.
    "src/main/java/dev/culprit/config/CulpritServerConfig.java": [
        drop_member("public static PermissionCheck scanCheck()"),
        replace_member("public static boolean mayScan(PermissionSet permissions)", MAY_SCAN),
    ],
    # The Lab's signs: a sign's side by a flag, its text changed by copies (as on 26.1.2).
    "src/main/java/dev/culprit/devworld/Lab.java": [
        swap("SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();", "SignText text = sign.getText(true);"),
        swap("text.setLine(i, Component.literal(lines.get(i)));", "text = text.setMessage(i, Component.literal(lines.get(i)));"),
        swap("sign.setText(text.asImmutable(), SignTextSlot.FRONT);", "sign.setText(text, true);"),
        # 1.21.1's world settings: hardcore and difficulty apart, the game rules with them.
        swap("GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true,\n                        WorldDataConfiguration.DEFAULT)",
             "GameType.CREATIVE, false, net.minecraft.world.Difficulty.NORMAL, true,\n"
             "                        new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT)"),
        # The flat world made from the registries as 1.21.1 hands them over, on its normal dimensions.
        swap("private static WorldDimensions flat(HolderLookup.Provider registries)", "private static WorldDimensions flat(net.minecraft.core.RegistryAccess registries)"),
        swap("WorldPresets.createTestWorldDimensions(registries)", "WorldPresets.createNormalWorldDimensions(registries)"),
        # A screenshot at the window's size (1.21.1 has no scale), the chat opened empty, a player moved by position and look.
        swap('".png", mc.getMainRenderTarget(), 1,', '".png", mc.getMainRenderTarget(),'),
        swap('new ChatScreen("", false)', 'new ChatScreen("")'),
        resub(r"\.teleportTo\(([^;]*?), Set\.of\(\), ([^,;]+), ([^,;]+), true\)", r".teleportTo(\1, \2, \3)"),
        # A mob made as 1.21.1 makes one for a command.
        resub(r"\.create\(level, MobSpawnType\.COMMAND\)", ".create(level)"),
        swap(".snapTo(", ".moveTo("),
        # The game rules by 1.21.1's names (26.x renamed them): without them the command blocks' output filled the chat.
        swap('"gamerule advance_time false", "gamerule advance_weather false"', '"gamerule doDaylightCycle false", "gamerule doWeatherCycle false"'),
        swap('"gamerule spawn_mobs false"', '"gamerule doMobSpawning false"'),
        swap('"gamerule command_block_output false"', '"gamerule commandBlockOutput false"'),
    ],
}


# --- structure templates (NBT) --------------------------------------------------------------------------------------------
def _nbt_read(buf: io.BytesIO, tag: int):
    if tag == 1:
        return struct.unpack(">b", buf.read(1))[0]
    if tag == 2:
        return struct.unpack(">h", buf.read(2))[0]
    if tag == 3:
        return struct.unpack(">i", buf.read(4))[0]
    if tag == 4:
        return struct.unpack(">q", buf.read(8))[0]
    if tag == 5:
        return struct.unpack(">f", buf.read(4))[0]
    if tag == 6:
        return struct.unpack(">d", buf.read(8))[0]
    if tag == 7:
        n = struct.unpack(">i", buf.read(4))[0]
        return buf.read(n)
    if tag == 8:
        n = struct.unpack(">H", buf.read(2))[0]
        return buf.read(n).decode("utf-8")
    if tag == 9:
        elem, n = struct.unpack(">bi", buf.read(5))
        return (elem, [_nbt_read(buf, elem) for _ in range(n)])
    if tag == 10:
        out = {}
        while True:
            t = struct.unpack(">b", buf.read(1))[0]
            if t == 0:
                return out
            n = struct.unpack(">H", buf.read(2))[0]
            name = buf.read(n).decode("utf-8")
            out[name] = (t, _nbt_read(buf, t))
    if tag == 11:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(f">{n}i", buf.read(4 * n)))
    if tag == 12:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(f">{n}q", buf.read(8 * n)))
    raise ValueError(f"NBT tag {tag}")


def _nbt_write(out: io.BytesIO, tag: int, v) -> None:
    if tag == 1:
        out.write(struct.pack(">b", v))
    elif tag == 2:
        out.write(struct.pack(">h", v))
    elif tag == 3:
        out.write(struct.pack(">i", v))
    elif tag == 4:
        out.write(struct.pack(">q", v))
    elif tag == 5:
        out.write(struct.pack(">f", v))
    elif tag == 6:
        out.write(struct.pack(">d", v))
    elif tag == 7:
        out.write(struct.pack(">i", len(v)))
        out.write(v)
    elif tag == 8:
        b = v.encode("utf-8")
        out.write(struct.pack(">H", len(b)))
        out.write(b)
    elif tag == 9:
        elem, items = v
        out.write(struct.pack(">bi", elem, len(items)))
        for x in items:
            _nbt_write(out, elem, x)
    elif tag == 10:
        for name, (t, x) in v.items():
            out.write(struct.pack(">b", t))
            b = name.encode("utf-8")
            out.write(struct.pack(">H", len(b)))
            out.write(b)
            _nbt_write(out, t, x)
        out.write(b"\0")
    elif tag == 11:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}i", *v))
    elif tag == 12:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}q", *v))
    else:
        raise ValueError(f"NBT tag {tag}")


def port_structure(data: bytes) -> bytes:
    """A structure template: 26.3 names a palette's block states "id" and "properties", 1.21.1 "Name" and "Properties"
    (it reads a lowercase entry as air); saved at 1.21.1's data version, so nothing is updated as it loads."""
    raw = gzip.decompress(data)
    buf = io.BytesIO(raw)
    tag = struct.unpack(">b", buf.read(1))[0]
    n = struct.unpack(">H", buf.read(2))[0]
    root_name = buf.read(n).decode("utf-8")
    root = _nbt_read(buf, tag)
    palettes = []
    if "palette" in root:
        palettes.append(root["palette"][1][1])
    if "palettes" in root:
        palettes.extend(p for p in root["palettes"][1][1])
    for palette in palettes:
        for i, entry in enumerate(palette):
            new = {}
            for key, val in entry.items():
                new[{"id": "Name", "properties": "Properties"}.get(key, key)] = val
            palette[i] = new
    root["DataVersion"] = (3, DATA_VERSION)
    out = io.BytesIO()
    out.write(struct.pack(">b", tag))
    b = root_name.encode("utf-8")
    out.write(struct.pack(">H", len(b)))
    out.write(b)
    _nbt_write(out, tag, root)
    return gzip.compress(out.getvalue(), mtime=0)


def port_resource(path: str, data: bytes) -> tuple[str, bytes] | None:
    if path.endswith(".nbt") and "/structure/" in path:
        return path, port_structure(data)
    return path, data


# --- driver ---------------------------------------------------------------------------------------------------------------
def checkout_eol(path: str, data: bytes) -> bytes:
    """Line endings as git checks the file out here (core.autocrlf: CRLF for text), so a file the port leaves as main
    has it shows no change."""
    if b"\0" in data[:8000] or path.endswith((".nbt", ".png", ".ogg")):
        return data
    crlf = subprocess.run(["git", "-C", str(ROOT), "config", "core.autocrlf"], capture_output=True, text=True).stdout.strip() == "true" \
        if not hasattr(checkout_eol, "crlf") else checkout_eol.crlf
    checkout_eol.crlf = crlf
    lf = data.replace(b"\r\n", b"\n")
    if not crlf:
        return lf
    return lf.replace(b"\n", b"\r\n")


# Main's other text files changed here (path -> [(old, new)]).
TEXT_FIXES = {
    # This branch's own accessors for 1.21.1's tickets and NeoForge's chunk-loader owners.
    "src/main/resources/culprit.mixins.json": [('    "ChunkMapAccessor",\n', '    "ChunkMapAccessor",\n    "DistanceManagerAccessor",\n    "TicketOwnerAccessor",\n'),
                                               # 26.x's frame pipeline only (DROP).
                                               ('    "SubmitNodeFrameMixin",\n    "PreparedFrameMixin",\n', '')],
    # 1.21.1's mod loader reads which loader the mod is for (26.x's needs no such line).
    # ... and this branch's own dev-run mixins (culprit.port.mixins.json: the test server's helpers), their entry marked
    # "dev runs only" so the released jar's mod file leaves it out with them (build.gradle).
    "src/main/templates/META-INF/neoforge.mods.toml": [('license="${mod_license}"', 'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="${mod_license}"'),
                                                       ('config="${mod_id}.mixins.json"', 'config="${mod_id}.mixins.json"\n\n[[mixins]] # dev runs only: the test server\'s helpers, left out of the released jar\nconfig="${mod_id}.port.mixins.json"')],
    # The frame's parts in 1.21.1's words (scan/FramePhase, report/Breakdown: FILE_FIXES).
    "src/main/resources/assets/culprit/lang/en_us.json": [('"culprit.frame.particles": "Particles, handed in"', '"culprit.frame.particles": "Particles, sent to the GPU"'), ('"culprit.frame.drawing": "Drawing the terrain"', '"culprit.frame.drawing": "Drawing the world"'), ('"culprit.frame.features": "Drawing the handed-in parts"', '"culprit.frame.features": "Drawing the buffered parts"'), ('": "the world\'s blocks, sky and weather in view"', '": "which of the world\'s chunk sections are in view"'), ('": "all of them handed in, and the rarer kinds made ready"', '": "their batches drawn (each kind\'s own share is with it)"'), ('": "terrain, sky and clouds sent to the GPU"', '": "terrain, sky, clouds, weather and your hand sent to the GPU"'), ('": "the sorted parts sent to the GPU (the things\' shares are with them)"', '": "the things\' parts sent to the GPU, a batch at a time"'), ('": "made ready, handed in, their share of the drawing"', '": "each one drawn, its parts buffered for the GPU"'), ('": "chests, signs: made ready, handed in, drawn"', '": "chests, signs: each one drawn"'), ('": "each kind, moved and made ready"', '": "each kind, moved and drawn"')],
}


def port_file(path: str) -> tuple[str, bytes] | None:
    if any(p.fullmatch(path) for p in DROP) or path in OWN:
        return None
    data = read_main(path)
    if path in TEXT_FIXES:
        text = data.decode("utf-8")
        for old, new in TEXT_FIXES[path]:
            assert old in text, f"port.py: '{old}' is gone from main's {path}: update TEXT_FIXES"
            text = text.replace(old, new)
        data = text.encode("utf-8")
    if path.endswith(".java"):
        return path, port_java(data.decode("utf-8"), path).encode("utf-8")
    if path.startswith("src/main/resources/") or path.startswith("src/generated/resources/"):
        return port_resource(path, data)
    return path, data


def sync(new: str) -> None:
    """Brings this branch up to main's commit {new} from the one in base.txt: each file main changed since is ported again;
    one edited by hand here (manual.txt) gets main's change merged into it (a three-way merge of ported text: the old
    port, the new port, and the file as it is here), conflicts left marked for a person; a file main deleted goes."""
    global BASE
    old = BASE
    new = git("rev-parse", new).decode("utf-8").strip()
    changes = git("diff", "--name-status", "--no-renames", old, new).decode("utf-8").splitlines()
    conflicts = []
    for line in changes:
        status, path = line.split("\t", 1)
        if status.startswith("D"):
            BASE = old
            out = port_file(path)
            if out is not None and (ROOT / out[0]).exists() and out[0] not in MANUAL:
                (ROOT / out[0]).unlink()
            continue
        BASE = new
        out_new = port_file(path)
        if out_new is None:
            continue
        dest, data_new = out_new
        target = ROOT / dest
        if dest in MANUAL and target.exists():
            BASE = old
            try:
                out_old = port_file(path)
            except FileNotFoundError:
                out_old = None
            base_file = ROOT / "build" / "port-merge" / (dest + ".base")
            theirs_file = ROOT / "build" / "port-merge" / (dest + ".main")
            base_file.parent.mkdir(parents=True, exist_ok=True)
            base_file.write_bytes(checkout_eol(dest, out_old[1]) if out_old else b"")
            theirs_file.write_bytes(checkout_eol(dest, data_new))
            r = subprocess.run(["git", "merge-file", "-L", "here", "-L", "port of old main", "-L", "port of new main",
                                str(target), str(base_file), str(theirs_file)], capture_output=True)
            if r.returncode != 0:
                conflicts.append(dest)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(checkout_eol(dest, data_new))
    BASE = new
    (HERE / "base.txt").write_text(f"{new}   main's commit this branch is brought up to (docs/BACKPORT.md)\n", encoding="utf-8")
    print(f"{len(changes)} of main's files changed since {old[:8]}; now at {new[:8]}")
    for c in conflicts:
        print("  conflict to resolve by hand:", c)


def main() -> None:
    args = sys.argv[1:]
    if args and args[0] == "--show":
        out = port_file(args[1])
        sys.stdout.buffer.write(b"(dropped)\n" if out is None else out[1])
        return
    if args and args[0] == "--sync":
        if len(args) < 2 or not re.fullmatch(r"[0-9a-f]{40}", args[1]):
            sys.exit("port.py --sync needs main's full commit hash (never HEAD: here it is this branch's own commit)")
        sync(args[1])
        return
    files = args or main_files()
    written = kept = dropped = 0
    for path in files:
        out = port_file(path)
        if out is None:
            if path in OWN:                               # this branch's own (its build files): never touched
                kept += 1
                continue
            stale = ROOT / path
            if stale.exists() and (path.startswith("src/") or path.startswith("tools/")) and not path.startswith("tools/backport"):
                stale.unlink()
            dropped += 1
            continue
        dest, data = out
        if dest in MANUAL and (ROOT / dest).exists():
            kept += 1
            continue
        target = ROOT / dest
        target.parent.mkdir(parents=True, exist_ok=True)
        data = checkout_eol(dest, data)
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
            written += 1
    print(f"{len(files)} of main's files: {written} written, {kept} kept (edited by hand), {dropped} left out")


if __name__ == "__main__":
    main()
