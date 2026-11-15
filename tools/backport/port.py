"""Turns main's sources (Minecraft 26.3) into this branch's (Minecraft 26.1.2): docs/BACKPORT.md.

Reads every file of main at the commit in base.txt straight from git (so each run starts from main's own text) and writes
its port here: the Java sources through RULES, the GameTests' structure saved as 26.1.2 reads it. Main's files that have
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

# 26.1.2's data version: what its structure templates are saved at.
DATA_VERSION = 4790

# Main's files with no place here.
DROP = [re.compile(p) for p in (
    r"docs/BACKPORT\.md", r"tools/backport/.*",
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

# Imports: 26.3 class -> 26.1.2 class; None drops the import.
IMPORTS = {
    "net.minecraft.world.entity.EntityTypes": "net.minecraft.world.entity.EntityType",
    # 26.3's new GPU interface (its surface, command encoder and passes) and its order-independent transparency: the hooks
    # on them are 26.1.2's own here (FILE_FIXES).
    "com.mojang.renderpearl.api.commands.RenderPass": None,
    "com.mojang.renderpearl.api.commands.CommandEncoder": None,
    "com.mojang.renderpearl.api.device.GpuSurface": None,
    # 26.3 gathers the level's render state in a class of its own; 26.1.2 in the level renderer.
    "net.minecraft.client.renderer.extract.LevelExtractor": "net.minecraft.client.renderer.LevelRenderer",
    "net.minecraft.world.level.block.entity.SignTextSlot": None,
}

RULES = [
    # --- names ------------------------------------------------------------------------------------------------------------
    sub(r"\bEntityTypes\.", "EntityType."),
    sub(r"\bModConfig\.Type\.SYNCED\b", "ModConfig.Type.SERVER"),
    sub(r"\bModConfig\.Type\.LOCAL\b", "ModConfig.Type.COMMON"),
    # --- the client ---------------------------------------------------------------------------------------------------------
    sub(r"\.gui\.screen\(\)", ".screen"),
    sub(r"\.gui\.setScreen\(", ".setScreen("),
    sub(r"\.gui\.hud\.isHidden\(\)", ".options.hideGui"),
    sub(r"(\b\w+)\.gui\.hud\.toggle\(\)", r"\1.options.hideGui = !\1.options.hideGui"),
    sub(r"\.gui\.hud\.getChat\(\)", ".gui.getChat()"),
    sub(r"\.mainCamera\(\)", ".getMainCamera()"),
    sub(r"(\b\w+)\.gameRenderer\.mainRenderTarget\(\)", r"\1.getMainRenderTarget()"),
    sub(r"\bInputConstants\.Type\.KEYBOARD\b", "InputConstants.Type.KEYSYM"),
    sub(r"\.gameRenderer\.gameRenderState\(\)", ".gameRenderer.getGameRenderState()"),
    # --- the Lab and the GameTests ------------------------------------------------------------------------------------------
    sub(r"\bWorldPresets([.:]{1,2})createTestWorldDimensions\b", r"WorldPresets\1createFlatWorldDimensions"),
    # 26.1.2's tests have no dimension of their own (the overworld).
    sub(r"\bnew TestData<>\((\w+), (?:net\.minecraft\.world\.level\.)?Level\.OVERWORLD,\s*", r"new TestData<>(\1, "),
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
    # One import, once (two 26.3 classes may both become the same 26.1.2 one).
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


OVERLAY_RENDER = """    /** Draws what was submitted into the finished picture (Ground Burst's way, DESIGN §6); 26.1.2's features go into the
     * game's buffers, drawn as the batch ends. */
    private static void render(Minecraft mc, RenderLevelStageEvent.AfterLevel event) {
        var buffers = mc.renderBuffers();
        if (dispatcher == null) {
            dispatcher = new FeatureRenderDispatcher(STORAGE, mc.getModelManager(), buffers.bufferSource(), mc.getAtlasManager(),
                    buffers.outlineBufferSource(), buffers.crumblingBufferSource(), mc.font, mc.gameRenderer.getGameRenderState());
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        try {
            dispatcher.renderAllFeatures();
            buffers.bufferSource().endBatch();
        } finally {
            modelView.popMatrix();
        }
    }"""

PRESENT_HOOKS = (
    "    // 26.1.2 copies the frame to the window and swaps the window's buffers: where the wait for the screen falls."
    + timed("blitToScreen", '@WrapOperation(require = 0, method = "renderFrame(Z)V", at = @At(value = "INVOKE", '
            'target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V"))',
            "com.mojang.blaze3d.pipeline.RenderTarget target, ", "target", "PRESENT")
    + "\n" + timed("flipFrame", '@WrapOperation(require = 0, method = "renderFrame(Z)V", at = @At(value = "INVOKE", '
                    'target = "Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))',
                    "com.mojang.blaze3d.TracyFrameCapture capture, ", "capture", "PRESENT"))

FILE_FIXES: dict[str, list] = {
    # The results on the world: 26.1.2 hands custom geometry in by a call, and its feature dispatcher draws a storage of
    # its own into the game's buffers (26.3's prepares a frame and draws it in a pass of its own).
    "src/main/java/dev/culprit/client/Overlay.java": [
        resub(r"STORAGE\.order\(0\)\.afterTerrain\.submit\(new CustomFeatureRenderer\.Submit\(IDENTITY\.last\(\), ([^,]+), (\w+)\)\);",
              r"STORAGE.order(0).submitCustomGeometry(IDENTITY, \1, \2);"),
        replace_member("private static void render(Minecraft mc, RenderLevelStageEvent.AfterLevel event)", OVERLAY_RENDER),
    ],
    # The handed-in parts are not prepared as a frame on 26.1.2: no sorting step of its own to time (PreparedFrameMixin
    # times the drawing, manual.txt).
    "src/main/java/dev/culprit/mixin/LevelRendererFrameMixin.java": [
        drop_member("culprit$sort("),
        # 26.1.2 builds the changed chunks' meshes from the camera itself (26.3 from its render state).
        swap("compileSections(Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", "compileSections(Lnet/minecraft/client/Camera;)V"),
        swap("culprit$chunks(CameraRenderState camera,", "culprit$chunks(net.minecraft.client.Camera camera,"),
        # 26.1.2 hands the level's parts in inside its main pass (a lambda) with no method of their own to time as one;
        # its particles go into the storage itself.
        drop_member("culprit$submit("),
        swap('method = "submitFeatures", at = @At(value = "INVOKE",\n'
             '            target = "Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;submit(Lnet/minecraft/client/renderer/SubmitNodeCollector;',
             'method = "lambda$addMainPass$0", at = @At(value = "INVOKE",\n'
             '            target = "Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;submit(Lnet/minecraft/client/renderer/SubmitNodeStorage;'),
        swap("culprit$particles(ParticlesRenderState state, SubmitNodeCollector collector,", "culprit$particles(ParticlesRenderState state, SubmitNodeStorage collector,"),
        # The level is drawn by renderLevel here (26.3's render).
        swap('@WrapOperation(require = 0, method = "render", at = @At(value = "INVOKE",\n'
             '            target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute',
             '@WrapOperation(require = 0, method = "renderLevel", at = @At(value = "INVOKE",\n'
             '            target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute'),
    ],
    # 26.1.2 gathers the level's render state in the level renderer, and a block entity's without the "global" flag.
    "src/main/java/dev/culprit/mixin/LevelExtractorFrameMixin.java": [
        swap("@Mixin(LevelExtractor.class)", "@Mixin(LevelRenderer.class)"),
        swap("ModelFeatureRenderer$CrumblingOverlay;ZLnet/minecraft/client/renderer/culling/Frustum;)",
             "ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/renderer/culling/Frustum;)"),
        swap("ModelFeatureRenderer.CrumblingOverlay breaking, boolean global, Frustum frustum,",
             "ModelFeatureRenderer.CrumblingOverlay breaking, Frustum frustum,"),
        swap("original.call(dispatcher, blockEntity, partial, breaking, global, frustum)", "original.call(dispatcher, blockEntity, partial, breaking, frustum)"),
        swap('@WrapOperation(require = 0, method = "extract", at', '@WrapOperation(require = 0, method = "extractLevel", at'),
        # Two methods of that name here (the short one hands on to the other): the one that gathers, by its signature.
        swap('@WrapOperation(require = 0, method = "extractVisibleBlockEntities", at',
             '@WrapOperation(require = 0, method = "extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/level/'
             'LevelRenderState;Lnet/minecraft/client/renderer/culling/Frustum;)V", at'),
    ],
    # The game renderer: the level gathered by the level renderer, the HUD gathered in a method of its own, and drawn with
    # the fog's buffer.
    "src/main/java/dev/culprit/mixin/GameRendererFrameMixin.java": [
        swap("Lnet/minecraft/client/renderer/extract/LevelExtractor;extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V",
             "Lnet/minecraft/client/renderer/LevelRenderer;extractLevel(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V"),
        swap("culprit$extractLevel(LevelExtractor extractor,", "culprit$extractLevel(LevelRenderer extractor,"),
        swap("Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
             "Lnet/minecraft/client/renderer/GameRenderer;extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V"),
        swap("culprit$extractGui(Gui gui,", "culprit$extractGui(GameRenderer gui,"),
        swap('method = "render()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))',
             'method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE",\n'
             '            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))'),
        swap("culprit$renderGui(GuiRenderer renderer, Operation<Void> original)",
             "culprit$renderGui(GuiRenderer renderer, com.mojang.blaze3d.buffers.GpuBufferSlice fog, Operation<Void> original)"),
        swap("original.call(renderer);", "original.call(renderer, fog);"),
    ],
    # The frame's end: 26.1.2 has no GPU surface or command encoder to submit; it copies the frame to the window and swaps.
    "src/main/java/dev/culprit/mixin/MinecraftFrameMixin.java": [
        drop_member("culprit$submitToGpu("),
        drop_member("culprit$acquire("),
        drop_member("culprit$blit("),
        replace_member("culprit$present(", PRESENT_HOOKS),
    ],
    # Sodium for 26.1.2 asks for a block entity's state without the "global" flag.
    "src/main/java/dev/culprit/mixin/SodiumFrameMixin.java": [
        swap("ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
             "ModelFeatureRenderer$CrumblingOverlay;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"),
        swap("ModelFeatureRenderer.CrumblingOverlay breaking, boolean global,", "ModelFeatureRenderer.CrumblingOverlay breaking,"),
        swap("original.call(dispatcher, blockEntity, partial, breaking, global)", "original.call(dispatcher, blockEntity, partial, breaking)"),
    ],
    # The Lab's signs: 26.1.2 names a sign's side by a flag, and its text changes by copies.
    "src/main/java/dev/culprit/devworld/Lab.java": [
        swap("SignText.Mutable text = sign.getText(SignTextSlot.FRONT).asMutable();", "SignText text = sign.getText(true);"),
        swap("text.setLine(i, Component.literal(lines.get(i)));", "text = text.setMessage(i, Component.literal(lines.get(i)));"),
        swap("sign.setText(text.asImmutable(), SignTextSlot.FRONT);", "sign.setText(text, true);"),
        # Its check of the frame's parts: 26.1.2 has no handing-in step of its own to time (LevelRendererFrameMixin).
        swap("(p != dev.culprit.scan.FramePhase.SHADOWS ||", "p != dev.culprit.scan.FramePhase.SUBMIT && p != dev.culprit.scan.FramePhase.BATCHING\n"
             "                        && (p != dev.culprit.scan.FramePhase.SHADOWS ||"),
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
    """A structure template: 26.3 names a palette's block states "id" and "properties", 26.1.2 "Name" and "Properties"
    (26.1.2 reads a lowercase entry as air); saved at 26.1.2's data version, so nothing is updated as it loads."""
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
