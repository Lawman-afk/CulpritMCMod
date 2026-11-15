"""Turns the 1.21.1 branch's sources (Minecraft 1.21.1, NeoForge 21.1) into this branch's (Minecraft 1.20.1, Forge 47.4):
docs/BACKPORT.md.

Reads every file of the 1.21.1 branch at the commit in base.txt straight from git (so each run starts from that branch's
own text, and its worktree is never touched) and writes its port here: the Java through IMPORTS, PACKAGES and RULES
(Forge's names for NeoForge's, this branch's stand-ins in dev.culprit.legacy for what Forge 1.20.1 has not), FUNCTIONS
(NeoForge's tick events as Forge's with a phase; Java 21's patterns as Java 17 takes them) and per-file fixes; the
GameTests' structure saved at 1.20.1's data version, in 1.20.1's folder for it. Files with no place here (DROP) are
left out; files edited by hand here (manual.txt) are kept as they are. This branch's own files (the build files, this
tool, docs/BACKPORT.md, the stand-ins) are not the 1.21.1 branch's and are never touched.

    python tools\\backport\\port.py              (everything)
    python tools\\backport\\port.py <path> ...   (just those of the 1.21.1 branch's files)
    python tools\\backport\\port.py --show <path> (print one file's port)
    python tools\\backport\\port.py --sync <the 1.21.1 branch's full commit hash>   (bring this branch up to it)
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

# 1.20.1's data version: what its structure templates are saved at.
DATA_VERSION = 3465

# The 1.21.1 branch's files with no place here.
DROP = [re.compile(p) for p in (
    r"docs/BACKPORT\.md", r"tools/backport/.*",
    # NeoForge's mod file: Forge 1.20.1 reads META-INF/mods.toml (this branch's own).
    r"src/main/templates/META-INF/neoforge\.mods\.toml",
)]
# Paths that are this branch's own (build files and the mod file, made here by hand): never overwritten.
OWN = {"build.gradle", "gradle.properties", "settings.gradle", "src/main/templates/META-INF/mods.toml"}


def git(*args: str) -> bytes:
    return subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True).stdout


def source_files() -> list[str]:
    return git("ls-tree", "-r", "--name-only", BASE).decode("utf-8").splitlines()


_batch = None


def read_source(path: str) -> bytes:
    """A file of the 1.21.1 branch's at BASE (through one long-running `git cat-file --batch`)."""
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


L = "dev.culprit.legacy"                                    # this branch's stand-ins for NeoForge 21.1's and 1.21's API

IMPORTS = {
    # NeoForge's event bus and loading: Forge's own (NeoForge forked from it).
    "net.neoforged.api.distmarker.Dist": "net.minecraftforge.api.distmarker.Dist",
    "net.neoforged.bus.api.Event": "net.minecraftforge.eventbus.api.Event",
    "net.neoforged.bus.api.EventPriority": "net.minecraftforge.eventbus.api.EventPriority",
    "net.neoforged.bus.api.IEventBus": "net.minecraftforge.eventbus.api.IEventBus",
    "net.neoforged.bus.api.SubscribeEvent": "net.minecraftforge.eventbus.api.SubscribeEvent",
    "net.neoforged.fml.ModContainer": "net.minecraftforge.fml.ModContainer",
    "net.neoforged.fml.ModList": "net.minecraftforge.fml.ModList",
    "net.neoforged.fml.common.EventBusSubscriber": "net.minecraftforge.fml.common.Mod.EventBusSubscriber",
    "net.neoforged.fml.common.Mod": "net.minecraftforge.fml.common.Mod",
    "net.neoforged.fml.config.ModConfig": "net.minecraftforge.fml.config.ModConfig",
    "net.neoforged.fml.event.IModBusEvent": "net.minecraftforge.fml.event.IModBusEvent",
    "net.neoforged.fml.event.lifecycle.FMLClientSetupEvent": "net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent",
    "net.neoforged.neoforge.common.NeoForge": "net.minecraftforge.common.MinecraftForge",
    "net.neoforged.neoforge.common.ModConfigSpec": "net.minecraftforge.common.ForgeConfigSpec",
    "net.neoforged.neoforgespi.language.IModFileInfo": "net.minecraftforge.forgespi.language.IModFileInfo",
    "net.neoforged.neoforge.client.settings.KeyConflictContext": "net.minecraftforge.client.settings.KeyConflictContext",
    # Events Forge 1.20.1 has under the same names.
    "net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent": "net.minecraftforge.client.event.ClientPlayerNetworkEvent",
    "net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent": "net.minecraftforge.client.event.RegisterKeyMappingsEvent",
    "net.neoforged.neoforge.client.event.RenderLevelStageEvent": "net.minecraftforge.client.event.RenderLevelStageEvent",
    "net.neoforged.neoforge.event.RegisterCommandsEvent": "net.minecraftforge.event.RegisterCommandsEvent",
    "net.neoforged.neoforge.event.entity.player.PlayerEvent": "net.minecraftforge.event.entity.player.PlayerEvent",
    "net.neoforged.neoforge.event.server.ServerStoppingEvent": "net.minecraftforge.event.server.ServerStoppingEvent",
    "net.neoforged.neoforge.event.server.ServerStoppedEvent": "net.minecraftforge.event.server.ServerStoppedEvent",
    "net.neoforged.neoforge.gametest.GameTestHolder": "net.minecraftforge.gametest.GameTestHolder",
    # NeoForge's tick events (a class each, Pre and Post): Forge's, one with a phase (tick_events below).
    "net.neoforged.neoforge.event.tick.ServerTickEvent": "net.minecraftforge.event.TickEvent",
    "net.neoforged.neoforge.client.event.ClientTickEvent": "net.minecraftforge.event.TickEvent",
    "net.neoforged.neoforge.client.event.RenderFrameEvent": "net.minecraftforge.event.TickEvent",
    # NeoForge's systems as this branch's stand-ins over Forge's (dev.culprit.legacy).
    "net.neoforged.neoforge.network.PacketDistributor": f"{L}.net.PacketDistributor",
    "net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent": f"{L}.net.RegisterPayloadHandlersEvent",
    "net.neoforged.neoforge.network.handling.IPayloadContext": f"{L}.net.IPayloadContext",
    "net.neoforged.neoforge.network.handling.IPayloadHandler": f"{L}.net.IPayloadHandler",
    "net.neoforged.neoforge.network.registration.PayloadRegistrar": f"{L}.net.PayloadRegistrar",
    "net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent": f"{L}.RegisterTicketControllersEvent",
    "net.neoforged.neoforge.common.world.chunk.TicketController": f"{L}.TicketController",
    "net.neoforged.neoforge.common.world.chunk.ForcedChunkManager": "net.minecraftforge.common.world.ForgeChunkManager",
    "net.neoforged.neoforge.common.TranslatableEnum": f"{L}.TranslatableEnum",
    "net.neoforged.neoforge.client.event.RegisterGuiLayersEvent": f"{L}.client.RegisterGuiLayersEvent",
    "net.neoforged.neoforge.client.gui.VanillaGuiLayers": f"{L}.client.VanillaGuiLayers",
    # Vanilla 1.21 types 1.20.1 has not: this branch's stand-ins.
    "net.minecraft.client.DeltaTracker": f"{L}.DeltaTracker",
    "net.minecraft.network.RegistryFriendlyByteBuf": "net.minecraft.network.FriendlyByteBuf",
    "net.minecraft.network.codec.StreamCodec": f"{L}.net.StreamCodec",
    "net.minecraft.network.codec.ByteBufCodecs": f"{L}.net.ByteBufCodecs",
    "net.minecraft.network.protocol.common.custom.CustomPacketPayload": f"{L}.net.CustomPacketPayload",
}

# Fully qualified names written in the code (and imports not in IMPORTS): moved by prefix, the longest first.
PACKAGES = [
    ("net.neoforged.neoforge.common.NeoForge", "net.minecraftforge.common.MinecraftForge"),
    ("net.neoforged.neoforge.common.ModConfigSpec", "net.minecraftforge.common.ForgeConfigSpec"),
    ("net.neoforged.neoforge.network.PacketDistributor", f"{L}.net.PacketDistributor"),
    ("net.neoforged.neoforge.network.handling.IPayloadContext", f"{L}.net.IPayloadContext"),
    ("net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent", f"{L}.net.RegisterPayloadHandlersEvent"),
    ("net.neoforged.neoforge.network.registration.PayloadRegistrar", f"{L}.net.PayloadRegistrar"),
    ("net.neoforged.neoforge.network.handling.IPayloadHandler", f"{L}.net.IPayloadHandler"),
    ("net.neoforged.neoforge.common.world.chunk.TicketController", f"{L}.TicketController"),
    ("net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent", f"{L}.RegisterTicketControllersEvent"),
    ("net.neoforged.neoforge.common.world.chunk.ForcedChunkManager", "net.minecraftforge.common.world.ForgeChunkManager"),
    ("net.neoforged.neoforge.common.TranslatableEnum", f"{L}.TranslatableEnum"),
    ("net.neoforged.neoforge.client.event.RegisterGuiLayersEvent", f"{L}.client.RegisterGuiLayersEvent"),
    ("net.neoforged.neoforge.client.gui.VanillaGuiLayers", f"{L}.client.VanillaGuiLayers"),
    ("net.neoforged.neoforge.event.tick.ServerTickEvent", "net.minecraftforge.event.TickEvent.ServerTickEvent"),
    ("net.neoforged.neoforge.client.event.ClientTickEvent", "net.minecraftforge.event.TickEvent.ClientTickEvent"),
    ("net.neoforged.neoforge.client.event.RenderFrameEvent", "net.minecraftforge.event.TickEvent.RenderTickEvent"),
    ("net.neoforged.neoforge.client.ClientHooks", "net.minecraftforge.client.ForgeHooksClient"),
    ("net.neoforged.neoforge.event.EventHooks", "net.minecraftforge.event.ForgeEventFactory"),
    ("net.neoforged.neoforge.client.event.", "net.minecraftforge.client.event."),
    ("net.neoforged.neoforge.event.", "net.minecraftforge.event."),
    ("net.neoforged.neoforge.client.", "net.minecraftforge.client."),
    ("net.neoforged.neoforge.common.", "net.minecraftforge.common."),
    ("net.neoforged.neoforge.gametest.", "net.minecraftforge.gametest."),
    ("net.neoforged.neoforgespi.", "net.minecraftforge.forgespi."),
    ("net.neoforged.fml.", "net.minecraftforge.fml."),
    ("net.neoforged.bus.api.", "net.minecraftforge.eventbus.api."),
    ("net.neoforged.api.distmarker.", "net.minecraftforge.api.distmarker."),
    ("net.minecraft.client.DeltaTracker", f"{L}.DeltaTracker"),
    ("net.minecraft.network.RegistryFriendlyByteBuf", "net.minecraft.network.FriendlyByteBuf"),
    ("net.minecraft.network.codec.StreamCodec", f"{L}.net.StreamCodec"),
    ("net.minecraft.network.codec.ByteBufCodecs", f"{L}.net.ByteBufCodecs"),
    ("net.minecraft.network.protocol.common.custom.CustomPacketPayload", f"{L}.net.CustomPacketPayload"),
]
PACKAGES.sort(key=lambda p: -len(p[0]))

# Plain textual rules, in order.
RULES = [
    # Forge 1.20.1's ResourceLocation is made with new (1.21's factories).
    sub(r"\b(net\.minecraft\.resources\.)?ResourceLocation\.fromNamespaceAndPath\(", r"new \1ResourceLocation("),
    sub(r"\b(net\.minecraft\.resources\.)?ResourceLocation\.withDefaultNamespace\(", r"new \1ResourceLocation("),
    sub(r"\b(net\.minecraft\.resources\.)?ResourceLocation\.parse\(", r"new \1ResourceLocation("),
    # Java 21's Math.clamp: this branch's, with the same overloads (Java 17 has none).
    sub(r"\bMath\.clamp\(", f"{L}.J21.clamp("),
    sub(r"\bNeoForge\.EVENT_BUS\b", "MinecraftForge.EVENT_BUS"),
    sub(r"\bModConfigSpec\b", "ForgeConfigSpec"),
    sub(r"\bClientHooks\b", "ForgeHooksClient"),
    sub(r"\bRegistryFriendlyByteBuf\b", "FriendlyByteBuf"),
    # A mod's settings are registered with Forge's loading context (NeoForge: its container).
    sub(r"\b\w+\.registerConfig\(", "net.minecraftforge.fml.ModLoadingContext.get().registerConfig("),
    # NeoForge's hasChannel: whether the other side has the mod's Forge channel (dev.culprit.legacy.net.Network).
    sub(r"\bplayer\.connection\.hasChannel\([\w.]+\)", f"{L}.net.Network.clientHas(player.connection)"),
    sub(r"([\w.]+(?:\(\))?)\.hasChannel\([\w.]+\)", rf"{L}.client.ClientNet.serverHas(\1)"),
]

# Whole lines removed: NeoForge's that have no counterpart on Forge 1.20.1.
DROP_LINES = [re.compile(p) for p in (
    # The client half is not a mod of its own on Forge 1.20.1 (one @Mod a mod): the mod starts it (FILE_FIXES).
    r"^@Mod\(value = Culprit\.MODID, dist = Dist\.CLIENT\)\s*$",
)]


def skip(text: str, i: int) -> int:
    """If a string, character literal, text block or comment starts at {i}, the index just past it; else {i}."""
    if text.startswith('"""', i):
        end = text.find('"""', i + 3)
        return len(text) if end < 0 else end + 3
    ch = text[i]
    if ch in "\"'":
        j = i + 1
        while j < len(text) and text[j] != ch and text[j] != "\n":
            j += 2 if text[j] == "\\" else 1
        return j + 1
    if text.startswith("//", i):
        end = text.find("\n", i)
        return len(text) if end < 0 else end
    if text.startswith("/*", i):
        end = text.find("*/", i + 2)
        return len(text) if end < 0 else end + 2
    return i


def find_close(text: str, i: int) -> int:
    """The index just past the bracket that closes the one opened just before {i} (past strings and comments)."""
    depth = 1
    while i < len(text) and depth:
        j = skip(text, i)
        if j != i:
            i = j
            continue
        ch = text[i]
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        i += 1
    return i


def calls(text: str, start: str, fn) -> str:
    """Each call matching {start} (up to its opening parenthesis), its whole argument list found by matching brackets
    (over lines, past strings), replaced by fn(match, args)."""
    out, pos = [], 0
    for m in re.compile(start).finditer(text):
        if m.start() < pos:
            continue
        i = find_close(text, m.end())
        out.append(text[pos:m.start()])
        out.append(fn(m, text[m.end():i - 1]))
        pos = i
    out.append(text[pos:])
    return "".join(out)


def split_args(args: str) -> list[str]:
    """A call's arguments, split at its own commas."""
    parts, cur, depth, i = [], [], 0, 0
    while i < len(args):
        j = skip(args, i)
        if j != i:
            cur.append(args[i:j])
            i = j
            continue
        ch = args[i]
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(ch)
        i += 1
    parts.append("".join(cur))
    return parts


def receiver_start(text: str, dot: int) -> int:
    """Where the expression that ends at {dot} begins: walking back over names, dots, calls, indexing and generic
    arguments, to the first character that cannot be part of it."""
    i = dot - 1
    while i >= 0:
        ch = text[i]
        if ch in ")]":
            close, open_ = ch, "(" if ch == ")" else "["
            depth = 0
            while i >= 0:
                if text[i] == close:
                    depth += 1
                elif text[i] == open_:
                    depth -= 1
                    if depth == 0:
                        break
                elif text[i] == '"':
                    i -= 1
                    while i >= 0 and text[i] != '"':
                        i -= 1
                i -= 1
            i -= 1
            continue
        if ch.isalnum() or ch in "_$.":
            i -= 1
            continue
        if ch == '"':
            i -= 1
            while i >= 0 and text[i] != '"':
                i -= 1
            i -= 1
            continue
        if ch in " \t\r\n":
            j = i
            while j >= 0 and text[j] in " \t\r\n":
                j -= 1
            k = i + 1
            while k < len(text) and text[k] in " \t\r\n":
                k += 1
            if k < len(text) and text[k] == "." and j >= 0 and (text[j].isalnum() or text[j] in "_)]>"):
                i = j
                continue
            break
        break
    start = i + 1
    m = re.search(r"\bnew\s+$", text[max(0, start - 12):start])
    if m:
        start -= len(m.group(0))
    return start


def tick_events(text: str) -> str:
    """NeoForge's tick events (Pre and Post, a class each) as Forge's (one, with a phase): a guard at the top of each
    listener for its phase, and the getters as Forge's fields (Ground Burst's 1.20.1 port's)."""
    def accessors(body: str, name: str, kind: str) -> str:
        if kind == "RenderTickEvent":
            body = re.sub(rf"\b{name}\.getPartialTick\(\)", f"{L}.DeltaTracker.of({name}.renderTickTime)", body)
        return body

    pat = re.compile(r"\((?:final\s+)?(?:net\.minecraftforge\.event\.TickEvent\.|TickEvent\.)?(ServerTickEvent|LevelTickEvent|PlayerTickEvent"
                     r"|ClientTickEvent|RenderFrameEvent|RenderTickEvent)\.(Pre|Post)\s+(\w+)\)")
    out, pos = [], 0
    for m in pat.finditer(text):
        if m.start() < pos:
            continue
        kind, phase, name = m.group(1), m.group(2), m.group(3)
        if kind in ("RenderFrameEvent", "RenderTickEvent"):
            kind = "RenderTickEvent"
        guard = f"if ({name}.phase != net.minecraftforge.event.TickEvent.Phase.{'START' if phase == 'Pre' else 'END'}) return;"
        head = f"(net.minecraftforge.event.TickEvent.{kind} {name})"
        after_ = text[m.end():]
        lam = re.match(r"\s*->\s*", after_)
        brace = re.match(r"\s*(?:throws [\w., ]+)?\s*\{", after_)
        if lam:
            body_start = m.end() + lam.end()
            if text[body_start] == "{":
                end = find_close(text, body_start + 1)
                body = accessors(text[body_start + 1:end - 1], name, kind)
                out.append(text[pos:m.start()] + head + " -> {" + guard + body + "}")
            else:
                depth, i = 0, body_start
                while i < len(text):
                    ch = text[i]
                    if ch in "([{":
                        depth += 1
                    elif ch in ")]}":
                        if depth == 0:
                            break
                        depth -= 1
                    elif ch in "\"'":
                        q = ch
                        i += 1
                        while i < len(text) and text[i] != q:
                            i += 2 if text[i] == "\\" else 1
                    elif ch == ";" and depth == 0:
                        break
                    i += 1
                end = i
                body = accessors(text[body_start:end].rstrip(), name, kind)
                out.append(text[pos:m.start()] + head + " -> { " + guard + " " + body + "; }")
            pos = end
        elif brace:
            body_start = m.end() + brace.end()
            end = find_close(text, body_start)
            body = accessors(text[body_start:end - 1], name, kind)
            out.append(text[pos:m.start()] + head + text[m.end():body_start] + " " + guard + body + "}")
            pos = end
        else:
            out.append(text[pos:m.end()])
            pos = m.end()
    out.append(text[pos:])
    text = "".join(out)
    # Bare references to the classes (an addListener's class argument): Forge's.
    text = re.sub(r"(net\.minecraftforge\.event\.TickEvent\.)(?:RenderFrameEvent|RenderTickEvent)\.(?:Pre|Post)\b", r"\1RenderTickEvent", text)
    text = re.sub(r"(net\.minecraftforge\.event\.TickEvent\.\w+)\.(?:Pre|Post)\b", r"\1", text)
    text = re.sub(r"(?<![\w.])(?:RenderFrameEvent)\.(?:Pre|Post)\b", "net.minecraftforge.event.TickEvent.RenderTickEvent", text)
    text = re.sub(r"(?<![\w.])(ServerTickEvent|LevelTickEvent|PlayerTickEvent|ClientTickEvent)\.(?:Pre|Post)\b",
                  r"net.minecraftforge.event.TickEvent.\1", text)
    return text


def java17(text: str) -> str:
    """Java 17 refuses a type pattern its expression's type already is (Java 21 allows it): the expression seen as an Object."""
    out, pos = [], 0
    for m in re.finditer(r"\binstanceof\s+(?:final\s+)?[\w.]+(?:<[^>]*>)?\s+[a-z_]\w*\b(?!\s*[.(])", text):
        if m.start() < pos:
            continue
        end = m.start()
        while end > 0 and text[end - 1] in " \t":
            end -= 1
        start = receiver_start(text, end)
        expr = text[start:end]
        if not expr.strip() or expr.strip().startswith("(Object)"):
            continue
        out.append(text[pos:start] + "((Object) " + expr.strip() + ") ")
        pos = m.start()
    out.append(text[pos:])
    return "".join(out)


def receiver_calls(text: str, method: str, make, accept=lambda recv, args: True) -> str:
    """Each call {recv}.{method}({args}) (recv the whole expression before the dot) as make(recv, args), where accept says,
    nested and chained ones too. make returns (prefix, rest): the text scanned again starts after the prefix."""
    pat = re.compile(r"\.\s*" + method + r"\s*\(")
    pos = 0
    while True:
        m = pat.search(text, pos)
        if not m:
            return text
        dot = m.start()
        start = receiver_start(text, dot)
        recv = text[start:dot].strip()
        end = find_close(text, m.end())
        args = text[m.end():end - 1]
        if not recv or recv in ("super", "this") or not accept(recv, args):
            pos = m.end()
            continue
        prefix, rest = make(recv, args)
        text = text[:start] + prefix + rest + text[end:]
        pos = start + len(prefix)


def sequenced(text: str) -> str:
    """Java 21's first and last of a list (Java 17 has them on a deque only: J21 takes both)."""
    def ok(recv: str, args: str) -> bool:
        return not args.strip() and not re.search(r"(?i)pair|either|entry$", recv)
    for op, fn in (("getFirst", "first"), ("getLast", "last")):
        text = receiver_calls(text, op, lambda recv, args, fn=fn: (f"{L}.J21.{fn}(", f"{recv})"), ok)
    return text


def param(type_re: str) -> str:
    """A parameter of a type matching {type_re}, its name captured (annotations and final allowed before it)."""
    return r"(?:final\s+)?(?:@[\w.]+\s+)*(?:" + type_re + r")\s+(\w+)"


C = r"\s*,\s*"
QN = r"(?:[\w.]+\.)?"
VANILLA_SCREEN = r"(?:net\.minecraft\.client\.gui\.screens\.(?:inventory\.)?)?(?:Screen|AbstractContainerScreen)"


def top_level(body: str, pattern: str) -> list:
    """The matches of {pattern} in a class's {body} that are its own members' (outside any nested braces)."""
    depth, i, marks = 0, 0, {}
    while i < len(body):
        j = skip(body, i)
        if j != i:
            i = j
            continue
        marks[i] = depth
        if body[i] == "{":
            depth += 1
        elif body[i] == "}":
            depth -= 1
        i += 1
    return [m for m in re.finditer(pattern, body) if marks.get(m.start(), -1) == 0]


def vanilla_screen_body(body: str) -> str:
    """The body of a screen that extends the game's own: its 1.21 background drawn from its render, where 1.21's
    Screen.render draws it (just before the game's render), and its own background's call to the game's as 1.20.1's
    (Ground Burst's 1.20.1 port's)."""
    four = r"public void renderBackground\(\s*" + param(QN + "GuiGraphics") + C + param("int") + C + param("int") + C + param("float") + r"\s*\)\s*\{"
    own = top_level(body, four)
    if own:
        m = own[0]
        end = find_close(body, m.end())
        inner = calls(body[m.end():end], r"\bsuper\.renderBackground\(",
                      lambda c, a: f"super.renderBackground({split_args(a)[0].strip()})" if len(split_args(a)) == 4 else c.group(0) + a + ")")
        body = body[:m.end()] + inner + body[end:]
    render = top_level(body, r"public void render\(\s*" + param(QN + "GuiGraphics") + C + param("int") + C + param("int") + C + param("float")
                       + r"\s*\)\s*\{")
    if render:
        m = render[0]
        end = find_close(body, m.end())
        method = body[m.end():end]
        call = re.search(r"([ \t]*)super\.render\(", method)
        assert call, "port.py: a screen's render that never calls the game's"
        args = method[call.end():find_close(method, call.end()) - 1]
        background = f"this.renderBackground({args if own else split_args(args)[0].strip()});"
        method = method[:call.start()] + f"{call.group(1)}{background}   // (1.20.1's Screen.render draws no background)\n" + method[call.start():]
        return body[:m.end()] + method + body[end:]
    background = "this.renderBackground(graphics, mouseX, mouseY, partial);" if own else "this.renderBackground(graphics);"
    close = len(body.rstrip())
    return (body[:close] + "\n\n    // 1.20.1's Screen.render draws no background (1.20.2's and later draw it there).\n    @Override\n"
            "    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partial) {\n"
            f"        {background}\n        super.render(graphics, mouseX, mouseY, partial);\n    }}" + body[close:])


def screens(text: str) -> str:
    """Screens: 1.20.1's background (the game's renderBackground) takes the graphics alone, and its Screen.render draws
    none (1.20.2 and later draw a screen's background from render). A screen's own 1.21 background (the four-argument
    renderBackground) stays its own method, drawn from render; its scroll takes one amount (1.20.2 split it in two)."""
    for m in reversed(list(re.finditer(r"\bclass\s+\w+(?:<[^{]*?>)?\s+extends\s+" + VANILLA_SCREEN + r"\b(?:<[^{]*?>)?[^{;]*\{", text))):
        end = find_close(text, m.end())
        text = text[:m.end()] + vanilla_screen_body(text[m.end():end - 1]) + text[end - 1:]
    text = re.sub(r"[ \t]*@Override[ \t]*\n([ \t]*public void renderBackground\(\s*" + QN + r"GuiGraphics\s+\w+\s*,\s*int\b)", r"\1", text)
    text = re.sub(r"(\bpublic boolean mouseScrolled\(\s*" + param("double") + C + param("double") + r")" + C + param("double") + C + param("double")
                  + r"\s*\)(\s*\{)", r"\1, double \5)\6 double \4 = 0;", text)
    text = calls(text, r"\bsuper\.mouseScrolled\(",
                 lambda m, a: "super.mouseScrolled(" + ", ".join(x.strip() for x in (split_args(a)[0], split_args(a)[1], split_args(a)[3])) + ")"
                 if len(split_args(a)) == 4 else m.group(0) + a + ")")
    return text


def misc_calls(text: str) -> str:
    """Calls 1.20.1 has otherwise."""
    # A component's colour by a number: 1.20.1 sets it through its style (not on a style itself).
    # (The whole call given as the prefix, so the scan goes on after it: the style's own withColor in it is not taken again.)
    # (A style merged on, not a lambda: a lambda could not take a loop's variable in the colour.)
    text = receiver_calls(text, "withColor", lambda recv, args: (f"{recv}.withStyle(net.minecraft.network.chat.Style.EMPTY.withColor({args}))", ""),
                          lambda recv, args: not re.search(r"(?i)style", recv))
    text = re.sub(r"\.getOrThrow\(\)", ".getOrThrow(false, error_ -> {})", text)
    text = re.sub(r"\.getServerDirectory\(\)", ".getServerDirectory().toPath()", text)
    text = re.sub(r"\bBlockPos\.(min|max)\(", rf"{L}.J21.\1(", text)
    text = re.sub(r"\bStringUtil\.isAllowedChatCharacter\(", "net.minecraft.SharedConstants.isAllowedChatCharacter(", text)
    # A tick's time: 1.20.1 keeps its average in milliseconds; it always ticks 20 a second (no tick rate to set).
    text = re.sub(r"\b(\w+)\.getAverageTickTimeNanos\(\)", r"(long) (\1.getAverageTickTime() * 1_000_000L)", text)
    text = re.sub(r"\b\w+\.tickRateManager\(\)\.tickrate\(\)", "20f", text)
    text = re.sub(r"\b(\w+)\.getDebugOverlay\(\)\.showDebugScreen\(\)", r"\1.options.renderDebug", text)
    return text


def vertex_call(args: str) -> str:
    p = [x.strip() for x in split_args(args)]
    if len(p) == 4:
        return f".vertex({L}.Verts.matrix({p[0]}), {p[1]}, {p[2]}, {p[3]})"
    return f".vertex({', '.join(p)})"


def normal_call(args: str) -> str:
    p = [x.strip() for x in split_args(args)]
    if len(p) == 4:
        return f".normal({L}.Verts.normal({p[0]}), {p[1]}, {p[2]}, {p[3]})"
    return f".normal({', '.join(p)})"


def vertices(text: str) -> str:
    """1.21's vertices (addVertex, then any of setColor, setUv, setOverlay, setLight, setNormal) as 1.20.1's (vertex, color,
    uv, overlayCoords, uv2, normal, and endVertex), Ground Burst's 1.20.1 port's."""
    if "addVertex(" not in text:
        return text
    out, pos = [], 0
    for m in re.finditer(r"\.\s*addVertex\(", text):
        if m.start() < pos:
            continue
        i, depth = m.start(), 0
        while i < len(text):
            j = skip(text, i)
            if j != i:
                i = j
                continue
            ch = text[i]
            if ch in "([{":
                depth += 1
            elif ch in ")]}":
                if depth == 0:
                    break
                depth -= 1
            elif ch == ";" and depth == 0:
                break
            i += 1
        chain = text[m.start():i]
        chain = calls(chain, r"\.\s*addVertex\(", lambda mm, a: vertex_call(a))
        for old, new in (("setColor", "color"), ("setUv1", "overlayCoords"), ("setUv2", "uv2"), ("setUv", "uv"),
                         ("setOverlay", "overlayCoords"), ("setLight", "uv2")):
            chain = re.sub(rf"\.\s*{old}\(", f".{new}(", chain)
        chain = calls(chain, r"\.\s*setNormal\(", lambda mm, a: normal_call(a))
        out.append(text[pos:m.start()] + chain.rstrip() + ".endVertex()")
        pos = i
    out.append(text[pos:])
    return "".join(out)


FUNCTIONS = [tick_events, sequenced, screens, misc_calls, vertices, java17]


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


def port_imports(text: str) -> str:
    def repl(m: re.Match) -> str:
        name = m.group(2)
        static = m.group(1) or ""
        if name in IMPORTS:
            new = IMPORTS[name]
            return "" if new is None else f"import {static}{new};"
        for old, new in PACKAGES:
            if name.startswith(old):
                return f"import {static}{new}{name[len(old):]};"
        return m.group(0)
    text = re.sub(r"import (static )?([\w.]+);", repl, text)
    # One import, once (two 1.21.1 classes may both become the same 1.20.1 one).
    seen, out = set(), []
    for line in text.split("\n"):
        key = line.strip()
        if key.startswith("import ") and key in seen:
            continue
        if key.startswith("import "):
            seen.add(key)
        out.append(line)
    return "\n".join(out)


def qualified_names(text: str) -> str:
    """Fully qualified names in the code (not in imports): moved as their packages are."""
    lines = text.split("\n")
    for n, line in enumerate(lines):
        if line.startswith("import ") or line.startswith("package "):
            continue
        for old, new in PACKAGES:
            if old in line:
                line = re.sub(re.escape(old) + (r"\b" if old[-1].isalnum() else ""), new, line)
        lines[n] = line
    return "\n".join(lines)


def port_java(text: str, path: str) -> str:
    text = text.replace("\r\n", "\n")
    text = "\n".join(ln for ln in text.split("\n") if not any(p.match(ln) for p in DROP_LINES))
    text = port_imports(text)
    text = qualified_names(text)
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
        assert anchor in text, f"port.py: '{anchor}' is gone from the 1.21.1 branch: update FILE_FIXES"
        return text.replace(anchor, anchor + insert, 1)
    return fix


def swap(old: str, new: str):
    def fix(text: str) -> str:
        assert old in text, f"port.py: '{old}' is gone from the 1.21.1 branch: update FILE_FIXES"
        return text.replace(old, new)
    return fix


def resub(pattern: str, repl: str, count: int = 0):
    def fix(text: str) -> str:
        new, n = re.subn(pattern, repl, text, count=count)
        assert n, f"port.py: /{pattern}/ is gone from the 1.21.1 branch: update FILE_FIXES"
        return new
    return fix


def replace_method(signature: str, new: str):
    """Replaces the method whose declaration begins with {signature} (to its closing brace) with {new}."""
    def fix(text: str) -> str:
        assert signature in text, f"port.py: '{signature}' is gone from the 1.21.1 branch: update FILE_FIXES"
        return method_bodies(text, re.escape(signature) + r"[^{]*\{", lambda method: new)
    return fix


def member_span(text: str, marker: str) -> tuple[int, int]:
    """Where the member whose declaration holds {marker} lies: from the end of the member before it (its annotations,
    comments and blank lines with it) to its closing brace (a field's: its semicolon)."""
    i = text.index(marker)
    line_start = text.rfind("\n", 0, i) + 1
    prev = max(text.rfind("}\n", 0, line_start), text.rfind(";\n", 0, line_start), text.rfind("{\n", 0, line_start))
    start = prev + 2
    semi, brace = text.find(";", i), text.find("{", i)
    if semi != -1 and (brace == -1 or semi < brace):
        return start, semi + 1
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
        assert marker in text, f"port.py: '{marker}' is gone from the 1.21.1 branch: update FILE_FIXES"
        start, end = member_span(text, marker)
        return text[:start] + text[end + 1:]
    return fix


def replace_member(marker: str, new: str):
    """Replaces the member whose declaration holds {marker} (with its annotations and comments) with {new}."""
    def fix(text: str) -> str:
        assert marker in text, f"port.py: '{marker}' is gone from the 1.21.1 branch: update FILE_FIXES"
        start, end = member_span(text, marker)
        return text[:start] + "\n" + new.strip("\n") + text[end:]
    return fix


# The chunk census's mod chunk loaders as Forge 1.20.1 keeps them: an owner's mod by its id (NeoForge's: a controller's
# id), the level's forced-chunk data loaded by its own reader.
ADD_1201 = """    private static void add(Long2ObjectMap<List<String>> owners, java.util.Map<?, LongSet> byOwner,
                            java.util.function.BiFunction<ResourceLocation, Object, String> name) {
        for (var entry : byOwner.entrySet()) {
            var key = (dev.culprit.mixin.TicketOwnerAccessor) entry.getKey();
            String owner = name.apply(new ResourceLocation(key.culprit$modId(), "chunks"), key.culprit$owner());
            for (long chunk : entry.getValue()) {
                List<String> list = owners.computeIfAbsent(chunk, k -> new ArrayList<>());
                if (!list.contains(owner)) {
                    list.add(owner);
                }
            }
        }
    }"""

def checkbox_1201(text: str) -> str:
    """The results screen's tick box as 1.20.1 makes one: no builder, a fixed 20-pixel box, no listener (its press does
    what the builder's listener did); its tooltip set after."""
    m = re.search(r"Checkbox viewSwitch = addRenderableWidget\(Checkbox\.builder\(viewText, this\.font\)\.pos\(right - 8 - viewW, barY\)\.selected\(layoutSpikes\)\s*"
                  r"\.tooltip\(", text)
    assert m, "port.py: the results screen's tick box is gone from the 1.21.1 branch: update checkbox_1201"
    tip_end = find_close(text, m.end())
    tooltip = text[m.end():tip_end - 1]
    v = re.compile(r"\s*\.onValueChange\(\(box, on\) -> \{").match(text, tip_end)
    assert v, "port.py: the tick box's listener is gone: update checkbox_1201"
    body_end = find_close(text, v.end())
    body = text[v.end():body_end - 1].replace("showSpikes(on)", "showSpikes(this.selected())")
    tail = re.compile(r"\)\.build\(\)\);").match(text, body_end)
    assert tail, "port.py: the tick box's build is gone: update checkbox_1201"
    new = ("Checkbox viewSwitch = addRenderableWidget(new Checkbox(right - 8 - viewW, barY - 2, viewW, 20, viewText, layoutSpikes) {\n"
           "            @Override\n            public void onPress() {\n                super.onPress();" + body.rstrip() + "\n            }\n        });\n"
           f"        viewSwitch.setTooltip({tooltip});")
    return text[:m.start()] + new + text[tail.end():]


FILE_FIXES: dict[str, list] = {
    # Forge 1.20.1's mod class is made with no arguments: its bus and container from Forge's loading context; this
    # branch's stand-ins started first (dev.culprit.legacy.Lifecycle); the client half started from here (Forge 1.20.1
    # has one @Mod class a mod).
    "src/main/java/dev/culprit/Culprit.java": [
        swap("    public Culprit(IEventBus modBus, ModContainer modContainer) {\n",
             "    public Culprit() {\n"
             "        IEventBus modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();\n"
             "        ModContainer modContainer = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();\n"
             "        dev.culprit.legacy.Lifecycle.install(modBus, modContainer);\n"),
        swap("        dev.culprit.net.LoadSender.register();             // the HUD line's numbers, for players who ask\n",
             "        dev.culprit.net.LoadSender.register();             // the HUD line's numbers, for players who ask\n"
             "        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,\n"
             "                () -> () -> new CulpritClient(modBus, modContainer));\n"),
    ],
    # The settings in the mod list (Config): this branch's settings page (Forge 1.20.1 draws none for a mod).
    "src/main/java/dev/culprit/CulpritClient.java": [
        swap("        container.registerExtensionPoint(net.minecraftforge.client.gui.IConfigScreenFactory.class,\n"
             "                net.minecraftforge.client.gui.ConfigurationScreen::new);",
             "        container.registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,\n"
             "                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(\n"
             "                        (mc, parent) -> new dev.culprit.legacy.client.SettingsScreen(parent)));"),
    ],
    # The results screen's Settings: this branch's settings page.
    "src/main/java/dev/culprit/client/CulpritScreen.java": [
        swap("int viewW = Checkbox.getBoxSize(this.font) + 4 + this.font.width(viewText);", "int viewW = 20 + 4 + this.font.width(viewText);"),
        checkbox_1201,
        replace_method("public static @Nullable Screen settings(Screen parent)", """public static @Nullable Screen settings(Screen parent) {
        return new dev.culprit.legacy.client.SettingsScreen(parent);
    }"""),
    ],
    # 1.20.1's chunk futures carry an Either (1.21's a ChunkResult), and its chunk statuses another package.
    "src/main/java/dev/culprit/mixin/PassingRequestMixin.java": [
        swap("import net.minecraft.server.level.ChunkResult;\n", "import com.mojang.datafixers.util.Either;\nimport net.minecraft.server.level.ChunkHolder;\n"),
        swap("import net.minecraft.world.level.chunk.status.ChunkStatus;", "import net.minecraft.world.level.chunk.ChunkStatus;"),
        swap("CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir",
             "CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir"),
    ],
    # The levels' tick events: Forge's (not remapped: Forge's own methods keep their names in players' games).
    "src/main/java/dev/culprit/mixin/MinecraftServerMixin.java": [
        swap('target = "Lnet/neoforged/neoforge/event/EventHooks;fireLevelTickPre(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"))',
             'target = "Lnet/minecraftforge/event/ForgeEventFactory;onPreLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V",\n'
             '            remap = false))'),
        swap('target = "Lnet/neoforged/neoforge/event/EventHooks;fireLevelTickPost(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"))',
             'target = "Lnet/minecraftforge/event/ForgeEventFactory;onPostLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V",\n'
             '            remap = false))'),
    ],
    # The stack walk passes over Forge's and its loader's frames as the game's own (NeoForge's on 1.21.1).
    "src/main/java/dev/culprit/chunks/Callers.java": [
        swap('name.startsWith("net.neoforged.")', 'name.startsWith("net.minecraftforge.") || name.startsWith("cpw.mods.")'),
    ],
    # Forge 1.20.1's ticket owners name their mod by its id.
    "src/main/java/dev/culprit/mixin/TicketOwnerAccessor.java": [
        swap('@Mixin(targets = "net.minecraftforge.common.world.ForgeChunkManager$TicketOwner", remap = false)',
             '@Mixin(value = net.minecraftforge.common.world.ForgeChunkManager.TicketOwner.class, remap = false)'),
        swap('    @Accessor("id")\n    ResourceLocation culprit$id();', '    @Accessor("modId")\n    String culprit$modId();'),
        swap("import net.minecraft.resources.ResourceLocation;\n", ""),
    ],
    "src/main/java/dev/culprit/chunks/ChunkCensus.java": [
        swap('typeName.startsWith("neoforge:")', 'typeName.startsWith("forge:")'),
        swap("level.getDataStorage().get(net.minecraft.world.level.ForcedChunksSavedData.factory(), \"chunks\")",
             "level.getDataStorage().get(net.minecraft.world.level.ForcedChunksSavedData::load, \"chunks\")"),
        replace_member("private static void add(Long2ObjectMap<List<String>> owners, java.util.Map<?, LongSet> byOwner,", ADD_1201),
    ],
    # 1.20.1's saved data saves itself into a tag alone.
    "src/main/java/dev/culprit/chunks/ForcedBy.java": [
        swap("public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {\n"
             "        return TYPE.save(this, tag, registries);",
             "public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) {\n"
             "        return TYPE.save(this, tag);"),
    ],
    # Sodium and Iris on Forge 1.20.1 are Embeddium and Oculus (by those mod ids; Embeddium in Sodium's old packages).
    "src/main/java/dev/culprit/mixin/CulpritMixinPlugin.java": [
        swap('java.util.Map.of("SodiumFrameMixin", "sodium", "SodiumChunksFrameMixin", "sodium",\n            "IrisShadowFrameMixin", "iris",',
             'java.util.Map.of("SodiumFrameMixin", "embeddium", "SodiumChunksFrameMixin", "embeddium",\n            "IrisShadowFrameMixin", "oculus",'),
    ],
    "src/main/java/dev/culprit/mixin/SodiumChunksFrameMixin.java": [
        swap('targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager"',
             'targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager"'),
    ],
    "src/main/java/dev/culprit/devworld/Lab.java": [
        swap('ModList.get().isLoaded("iris")', 'ModList.get().isLoaded("oculus")'),
        # 1.20.1 makes a world without a screen to go back to.
        swap("Lab::flat,\n                        mc.screen);", "Lab::flat);"),
    ],
    # Forge 1.20.1 has one render tick event, with a phase: the clock started before all its other listeners and stopped
    # after them, at each phase (NeoForge's Pre and Post, a class each, both became it: four listeners would start it twice).
    "src/main/java/dev/culprit/client/FrameEvents.java": [
        replace_method("public static void register()", """public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, net.minecraftforge.event.TickEvent.RenderTickEvent.class, e -> begin());
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, net.minecraftforge.event.TickEvent.RenderTickEvent.class, e -> end());
    }"""),
    ],
    # The overlay on 1.20.1: line of sight with no entity (1.21's collision context), the label turned by the camera's
    # quaternion; the camera's turn in 1.21's terms (1.20.1's camera faces +z, 1.21's -z) and the view's turn made as
    # 1.20.1's game renderer makes it (Forge 1.20.1's after-level stage gives the projection as its pose):
    # dev.culprit.legacy.CameraTurn; the model view a pose stack.
    "src/main/java/dev/culprit/client/Overlay.java": [
        swap("CollisionContext.empty()", "(net.minecraft.world.entity.Entity) null"),
        swap("LABEL.mulPose(FACING.rotation(facing));", "LABEL.mulPose(facing);"),
        swap("camera.rotation()", "dev.culprit.legacy.CameraTurn.of(camera)"),
        swap("Matrix4fStack modelView = RenderSystem.getModelViewStack();\n        modelView.pushMatrix();\n        modelView.mul(event.getModelViewMatrix());",
             "PoseStack modelView = RenderSystem.getModelViewStack();\n        modelView.pushPose();\n"
             "        modelView.mulPoseMatrix(dev.culprit.legacy.CameraTurn.view(event.getCamera()));"),
        swap("RenderSystem.getModelViewStack().popMatrix();", "RenderSystem.getModelViewStack().popPose();"),
        swap("import org.joml.Matrix4fStack;\n", ""),
    ],
    # 1.20.1's test functions have neither a manual-only flag nor sky access.
    "src/main/java/dev/culprit/gametest/GameTestRegistration.java": [
        swap("Rotation.NONE, spec.maxTicks(), 0L, true, false,\n                    1, 1, spec.sky(), spec.body())",
             "Rotation.NONE, spec.maxTicks(), 0L, true,\n                    1, 1, spec.body())"),
    ],
    # A block entity type's blocks: private on 1.20.1 (dev.culprit.legacy.mixin.BlockEntityTypeAccessor).
    "src/main/java/dev/culprit/report/Names.java": [
        swap("blockEntity.getValidBlocks()", "((dev.culprit.legacy.mixin.BlockEntityTypeAccessor) blockEntity).culprit$validBlocks()"),
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
    """A structure template, saved at 1.20.1's data version so nothing is updated as it loads (its palette's block states
    are named as 1.20.1 reads them already: "Name" and "Properties")."""
    raw = gzip.decompress(data)
    buf = io.BytesIO(raw)
    tag = struct.unpack(">b", buf.read(1))[0]
    n = struct.unpack(">H", buf.read(2))[0]
    root_name = buf.read(n).decode("utf-8")
    root = _nbt_read(buf, tag)
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
        # 1.20.1 reads structures from data/<namespace>/structures (1.21 made the data folders singular).
        return path.replace("/structure/", "/structures/"), port_structure(data)
    return path, data


# --- driver ---------------------------------------------------------------------------------------------------------------
def checkout_eol(path: str, data: bytes) -> bytes:
    """Line endings as git checks the file out here (core.autocrlf: CRLF for text), so a file the port leaves as the
    1.21.1 branch has it shows no change."""
    if b"\0" in data[:8000] or path.endswith((".nbt", ".png", ".ogg")):
        return data
    crlf = subprocess.run(["git", "-C", str(ROOT), "config", "core.autocrlf"], capture_output=True, text=True).stdout.strip() == "true" \
        if not hasattr(checkout_eol, "crlf") else checkout_eol.crlf
    checkout_eol.crlf = crlf
    lf = data.replace(b"\r\n", b"\n")
    if not crlf:
        return lf
    return lf.replace(b"\n", b"\r\n")


# The 1.21.1 branch's other text files changed here (path -> [(old, new)]).
TEXT_FIXES = {
    # The mixins on Java 17, with the refmap the build makes (Forge 1.20.1 players' games use Forge's SRG names).
    "src/main/resources/culprit.mixins.json": [('"compatibilityLevel": "JAVA_21",', '"compatibilityLevel": "JAVA_17",\n  "refmap": "culprit.refmap.json",')],
    # ... and this branch's own dev-run mixin: the test server's tests set out far from the spawn chunks.
    "src/main/resources/culprit.port.mixins.json": [('"compatibilityLevel": "JAVA_21",', '"compatibilityLevel": "JAVA_17",\n  "refmap": "culprit.refmap.json",'),
                                                    ('    "GameTestChunkTicksMixin"\n', '    "GameTestChunkTicksMixin",\n    "GameTestPlaceMixin"\n')],
}


def port_file(path: str) -> tuple[str, bytes] | None:
    if any(p.fullmatch(path) for p in DROP) or path in OWN:
        return None
    data = read_source(path)
    if path in TEXT_FIXES:
        text = data.decode("utf-8")
        for old, new in TEXT_FIXES[path]:
            assert old in text, f"port.py: '{old}' is gone from the 1.21.1 branch's {path}: update TEXT_FIXES"
            text = text.replace(old, new)
        data = text.encode("utf-8")
    if path.endswith(".java"):
        return path, port_java(data.decode("utf-8"), path).encode("utf-8")
    if path.startswith("src/main/resources/") or path.startswith("src/generated/resources/"):
        return port_resource(path, data)
    return path, data


def sync(new: str) -> None:
    """Brings this branch up to the 1.21.1 branch's commit {new} from the one in base.txt: each file changed since is ported
    again; one edited by hand here (manual.txt) gets the change merged into it (a three-way merge of ported text: the old
    port, the new port, and the file as it is here), conflicts left marked for a person; a file deleted there goes."""
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
            theirs_file = ROOT / "build" / "port-merge" / (dest + ".new")
            base_file.parent.mkdir(parents=True, exist_ok=True)
            base_file.write_bytes(checkout_eol(dest, out_old[1]) if out_old else b"")
            theirs_file.write_bytes(checkout_eol(dest, data_new))
            r = subprocess.run(["git", "merge-file", "-L", "here", "-L", "port of the old 1.21.1 branch", "-L", "port of the new",
                                str(target), str(base_file), str(theirs_file)], capture_output=True)
            if r.returncode != 0:
                conflicts.append(dest)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(checkout_eol(dest, data_new))
    BASE = new
    (HERE / "base.txt").write_text(f"{new}   the 1.21.1 branch's commit this branch is brought up to (docs/BACKPORT.md)\n",
                                   encoding="utf-8")
    print(f"{len(changes)} of the 1.21.1 branch's files changed since {old[:8]}; now at {new[:8]}")
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
            sys.exit("port.py --sync needs the 1.21.1 branch's full commit hash (never HEAD: here it is this branch's own commit)")
        sync(args[1])
        return
    files = args or source_files()
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
        if dest != path and (ROOT / path).exists():       # moved here (a data folder's 1.20.1 name): the old place goes
            (ROOT / path).unlink()
        if dest in MANUAL and (ROOT / dest).exists():
            kept += 1
            continue
        target = ROOT / dest
        target.parent.mkdir(parents=True, exist_ok=True)
        data = checkout_eol(dest, data)
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
            written += 1
    print(f"{len(files)} of the 1.21.1 branch's files: {written} written, {kept} kept (edited by hand), {dropped} left out")


if __name__ == "__main__":
    main()
