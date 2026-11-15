# Culprit for Minecraft 1.21.1 (branch `1.21.1`)

Main is written for Minecraft 26.3 (NeoForge 26.3). This branch is the same mod for Minecraft 1.21.1 (NeoForge
21.1.266, Java 21), made from main's sources by a tool, not kept by hand. The Forge 1.20.1 branch is made from this one.

- **Last brought up to:** `main` at `22c7c560` (2026-11-15: the chunk inspector names every kind of loader, passing
  requests by the code that asked; the frame scan with Sodium, Iris's shadow pass and Distant Horizons; settings, the
  HUD line, reports as files; the lag spikes tick box; light updates as their own part of the frame).

## How it is made

`tools\backport\port.py` reads every file of main at the commit in `tools\backport\base.txt` straight from git and
writes its port here:

- **Java** through `IMPORTS` and `RULES` (`Identifier` as `ResourceLocation`; 26.x's GUI graphics, input events, client
  payload registration and saved-data types through this branch's stand-ins in `dev.culprit.port`; registries' `get`,
  game rules through their values, riding without the client flag, the HUD layers given 1.21.1's graphics) and per-file
  fixes (`FILE_FIXES`):
  - the chunk census on 1.21.1's tickets (in the distance manager, through `mixin/DistanceManagerAccessor`), its ticket
    types named rather than registered, NeoForge's chunk loaders read from the level's forced-chunk data (their owners
    through `mixin/TicketOwnerAccessor`), and no ender pearl tickets (pearls load no chunks on 1.21.1);
  - the tick hooks where 1.21.1's calls differ: a fluid's random tick is given a `Level`, mob spawning a chunk at a
    time is in `tickChunks`, the connections and the autosave are calls in the server's tick (`tickChildren`,
    `tickServer`), not methods of their own;
  - the frame scan's particle moves in `ParticleEngine` (26.x: its particle groups), Sodium 0.8's finished meshes taken
    in by `uploadChunks`, and the frame's parts in 1.21.1's words (`FramePhase`, `Breakdown`, the language file);
  - the world overlay drawn at NeoForge's after-level stage through the game's buffers, with the stage's view and
    projection and no fog, a line render type per width (1.21.1 has no line width per vertex), each label line's
    backing drawn before its text (1.21.1's font draws it after, a hair behind, which at a label's distance covered the
    text in part) (main hands all of it to a feature dispatcher of its own);
  - the client's handlers for the server's payloads: 26.x fires an event for them; here the client fires this
    branch's stand-in for it at its setup (without it every report, status and HUD line was dropped on arrival);
  - who may scan by operator level (`CulpritServerConfig`: 1.21.1 has no permission sets), the forced-chunk record
    saving itself, the names the server has seen from its profile cache, a class's mod by its file's path;
  - the Lab's world (hardcore and difficulty apart, the game rules with them, on the normal dimensions), signs by their
    side's flag, a screenshot at the window's size, the chat opened empty, players moved by position and look, mobs
    made and placed as 1.21.1 does, the game rules by 1.21.1's names (`doDaylightCycle`, `commandBlockOutput`: 26.x
    renamed them, and the command blocks' output had filled the chat);
  - the spawn chunks named "the spawn chunks" (1.21.1 keeps them loaded; 26.x has none), apart from their reason.
- **Resources:** the GameTests' structure saved at 1.21.1's data version (3955), its palette named as 1.21.1 reads it;
  the mixin list with this branch's accessors and without 26.x's frame pipeline; the mod file with the loader lines
  1.21.1's loader requires (`modLoader`, `loaderVersion`) and this branch's own mixin list.
- **Left out** (`DROP`): `mixin/SubmitNodeFrameMixin` and `mixin/PreparedFrameMixin`, 26.x's frame pipeline (the parts
  handed in, the prepared frame's passes); 1.21.1 draws each thing as it goes.
- **Kept by hand** (`tools\backport\manual.txt`): the frame scan's hooks into 1.21.1's drawing
  (`MinecraftFrameMixin`, `GameRendererFrameMixin`, `LevelExtractorFrameMixin`, `LevelRendererFrameMixin`,
  `QuadParticleFrameMixin`, `SodiumFrameMixin`, `IrisShadowFrameMixin`; the two on the level renderer at priority 1100,
  above Sodium's: Sodium replaces `setupRender` and `renderSectionLayer`, and a mixin may hook another's replacement
  only from a higher priority, an error at load even for an optional hook otherwise), `gametest/GameTestRegistration` (main's
  GameTests as 1.21.1's test functions, a generator in a test holder) and `client/DevRuns` (the test runs' window behind
  the user's, on GLFW). A sync merges main's changes into them, conflicts marked.
- **This branch's own:** the build files (`build.gradle`, `gradle.properties`), the tool, this file, the stand-ins
  (`dev.culprit.port`), the two accessors above, and `culprit.port.mixins.json` with `dev.culprit.port.mixin`: the test
  helper counting a test's blocks from its structure's corner (1.21.1's counts from the structure block it sets a block
  under it, as Ground Burst's 1.21.1 branch found), and the test server's chunks ticking as 26.x's do (1.21.1 ticks a
  chunk only near a player, and the test server has none). Both are dev runs only (`PortMixinPlugin`).

Bring the branch up to a newer main with `python tools\backport\port.py --sync <main's full commit hash>` (never
`HEAD`: in this worktree it is the branch's own commit), then `python tools\backport\port.py`; compile and sort the
errors with `gradlew compileJava` and `python tools\backport\errs.py <log>`. A file named twice in `FILE_FIXES` or
`TEXT_FIXES` drops the earlier entry without a word (a dict keeps the last): add to a file's entry, never a second one.

## What differs from main for players

- **The frame scan.** 1.21.1 draws each entity and block entity as it goes (26.x gathers their render states first and
  hands their parts in), so each one's drawing is timed whole and charged to it; the GPU's batches are drawn apart,
  "Drawing the buffered parts", and not shared out to the things. There is no "Gathering what to draw" or "Sorting what
  to draw" (no such steps here). "Drawing the world" is the terrain, sky, clouds, weather and the hand; "Finding what is
  in view" the chunk sections in view; particles are drawn a kind at a time; NeoForge's level stage events count as
  mods' frame events. Light updates are their own part, as on main.
- **Chunk ticks.** 1.21.1 gives random ticks and the chunk's own tick only to chunks within 128 blocks of a player or
  held by a ticking chunk loader; a chunk kept loaded by `/forceload` with nobody near does not random-tick. Culprit
  measures what runs.
- **Ender pearls** load no chunks on 1.21.1, so none are listed as their loader.
- **Who may scan** is by operator level (`culprit-server.toml`); the world settings are that file (main's are synced
  settings).

## Checks

- `gradlew runGameTestServer`: all of main's GameTests (8 of Culprit's own at `22c7c560`; main also runs the game's
  built-in one). Read the log's "All 8 required tests passed": a server that fails to start still ends the task without
  an error.
- `gradlew runLab`, with `-PwithIris` (Iris 1.8.14-beta.1, Sodium 0.8.13, Complementary), `-PwithIris -PirisStable`
  (Iris 1.8.12 and Sodium 0.6.13, the released pair most 1.21.1 shader players have) and `-PwithDH` (Distant Horizons
  3.3.3): every frame part 1.21.1 has timed; the results on the world and the screens as main's. Sodium for 1.21.1
  comes as a wrapper (its start-up classes, the mod packed inside): on the dev runs' classpath the launcher took the
  wrapper for the mod and loaded none of Sodium, so the runs with Iris put it in run/mods (`sodiumToMods`, as Ground
  Burst's 1.21.1 build does).
- The build deploys to the folder in `culprit_instance_dir_1_21_1` (in `%USERPROFILE%\.gradle\gradle.properties`).
