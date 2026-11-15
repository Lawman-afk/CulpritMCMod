# Culprit for Minecraft 1.20.1 (branch `1.20.1`, the Forge port)

Culprit is made on Minecraft 26.3 (branch `main`) and carried back to 1.21.1 on NeoForge 21.1 (branch `1.21.1`). This
branch carries the 1.21.1 build back once more, to Minecraft 1.20.1 on Forge 47, as Ground Burst's 1.20.1 branch does
(its tool and its stand-ins were the start of this one's). It is brought up to the 1.21.1 branch as that branch is
brought up to main.

- **Last brought up to:** the `1.21.1` branch at `d3755e50` (main at `22c7c560`, 2026-11-15: the chunk inspector names
  every kind of loader, passing requests by the code that asked; the frame scan with Sodium, Iris's shadow pass and
  Distant Horizons; settings, the HUD line, reports as files; the lag spikes tick box; light updates as their own part
  of the frame), where it was begun.
- **Toolchain:** Minecraft 1.20.1, Forge 47.4.28 (the mod asks for 47.4 or later), Java 17, MixinExtras 0.5.5 packed in
  the jar (Forge 1.20.1 carries none: started by `legacy/mixin/MixinPlugin`). Built with ModDevGradle's legacy plugin
  (`net.neoforged.moddev.legacyforge` 2.0.148): the dev runs on official names; the jar for players turned back to
  Forge's SRG names (`reobfJar`, build/libs; the dev jar is build/devlibs'); the mixins have a refmap made as the code
  compiles (`culprit.refmap.json`) and are listed in the jar's manifest (`MixinConfigs`).
- **Test instance:** "Culprit1.20.1" (forge-47.4.28), named by `culprit_instance_dir_1_20_1` in your own
  `%USERPROFILE%\.gradle\gradle.properties`. Unset, the build says so and deploys nothing.

## How it is made

`tools\backport\port.py` reads every file of the 1.21.1 branch at the commit in `tools\backport\base.txt` straight from
git (the 1.21.1 worktree is never touched) and writes its port here:

- **Java** through `IMPORTS`, `PACKAGES` and `RULES` (Forge's names for NeoForge's; `new ResourceLocation`; Java 21's
  `Math.clamp` through `legacy/J21`; a mod's settings registered with Forge's loading context; NeoForge's `hasChannel`
  as whether the other side has the mod's Forge channel), `FUNCTIONS` (Ground Burst's 1.20.1 port's: NeoForge's tick
  events as Forge's one with a phase; Java 21's first and last of a list; screens, whose background 1.20.1's
  `Screen.render` does not draw and whose scroll takes one amount; a component's colour through its style; 1.21's vertex
  calls as 1.20.1's; Java 17's patterns) and per-file fixes (`FILE_FIXES`):
  - the mod class made with no arguments (its bus and container from Forge's loading context), this branch's stand-ins
    started first, and the client half started from it (Forge 1.20.1 has one `@Mod` class a mod);
  - the settings: the mod list's Config and the results screen's Settings open this branch's settings page;
  - the passing-request hook on 1.20.1's chunk futures (an `Either`), the level tick events as Forge's
    (`ForgeEventFactory.onPreLevelTick`, not remapped: Forge's own methods keep their names in players' games), the
    stack walk passing over Forge's and its loader's frames;
  - the chunk census's mod chunk loaders as Forge keeps them: an owner's mod by its id, Forge's ticket types
    (`forge:block`, `forge:entity`), the level's forced-chunk data by its own reader; saved data saved into a tag alone;
  - Sodium and Iris as Embeddium and Oculus (their mod ids; Embeddium in Sodium's old packages, `me.jellysquid`);
  - the overlay: line of sight with no entity, the label turned by the camera's quaternion, the view's turn from the
    render stage's pose (1.20.1's stage poses carry it), the model view a pose stack;
  - the results screen's tick box made as 1.20.1 makes one (no builder, a 20-pixel box, its press doing what the
    builder's listener did); Forge's single render tick event timed by two listeners (NeoForge's Pre and Post both
    became it: four would have started the clock twice); 1.20.1's test functions; a block entity type's blocks through
    `legacy/mixin/BlockEntityTypeAccessor`; the Lab's world made without a screen to go back to, Oculus by its id.
- **Resources:** the GameTests' structure saved at 1.20.1's data version (3465), in 1.20.1's `structures` folder; the
  mixin lists at Java 17 with the refmap. This branch's own: `META-INF/mods.toml` (Forge's mod file; either side may be
  without Culprit, so the server list shows no mismatch for it), `pack.mcmeta` (format 15), `culprit.legacy.mixins.json`.
- **Left out** (`DROP`): NeoForge's mod file, the 1.21.1 branch's tool and notes.
- **Kept by hand** (`tools\backport\manual.txt`): the frame scan's hooks into 1.20.1's drawing (`MinecraftFrameMixin`,
  `GameRendererFrameMixin`, `LevelExtractorFrameMixin`, `LevelRendererFrameMixin`, `QuadParticleFrameMixin`,
  `SodiumFrameMixin`, `IrisShadowFrameMixin`: 1.20.1's methods as the 1.21.1 branch's hooks into 1.21.1's; Forge's own
  methods in them not remapped, the game's calls in Embeddium's and Oculus's methods remapped; the two on the level
  renderer at priority 1100, above Embeddium's, which replaces `setupRender` and `renderChunkLayer`), and
  `port/SavedDataType` (1.20.1's data storage takes a reader and a maker).
- **This branch's own:** the build files, the tool (`port.py`, `errs.py`, `prod_client.py`), this file, the stand-ins in
  `dev.culprit.legacy` (from Ground Burst's 1.20.1 branch: NeoForge's payloads over one Forge channel, which either side
  may lack, as NeoForge's optional payloads; `DeltaTracker`; `J21`; `TranslatableEnum`; the ticket controllers; the HUD
  layers over Forge's overlays; `Verts`; the MixinExtras start-up and the dev window; and Culprit's own `Lifecycle`,
  `ClientLifecycle`, `ClientNet` and `SettingsScreen`, the settings page Forge 1.20.1 does not draw), and
  `port/mixin/GameTestPlaceMixin` (dev runs only): 1.20.1's test server sets its tests out at the world's origin, inside
  the spawn chunks it always keeps loaded, where a test of what keeps a chunk loaded found the spawn chunks first;
  they are set out 100 000 blocks off, as far from spawn as 1.21.1's.

Bring the branch up to a newer 1.21.1 branch with `python tools\backport\port.py --sync <its full commit hash>`, then
`python tools\backport\port.py`; compile and sort the errors with `gradlew compileJava` and
`python tools\backport\errs.py <log>`. A file named twice in `FILE_FIXES` or `TEXT_FIXES` drops the earlier entry
without a word: add to a file's entry, never a second one.

## What differs from main for players

As the 1.21.1 build (its BACKPORT.md): the frame scan as 1.21.1 draws, spawn chunks loaded, chunks random-ticking only
near players or with a ticking chunk loader, no ender pearl tickets, who may scan by operator level. Besides:

- **Shader packs** come through Oculus (Iris for Forge) on Embeddium (Sodium for Forge).
- **Settings:** the mod's own page (the same five settings as NeoForge's page shows on the other versions).
- **The HUD line's TPS:** 1.20.1 has no tick rate to set: ticks a second are worked out from the time a tick takes, 20
  at most.
- **Mods' chunk loaders** are Forge's, by mod.

## Checks

- `gradlew runGameTestServer`: all 8 of Culprit's GameTests pass (2026-11-15). Read the log's "All 8 required tests
  passed": a server that fails to start still ends the task without an error. Each run starts on a new flat world
  (build.gradle).
- `gradlew runLab`, with `-PwithIris` (Oculus 1.8.0, Embeddium 0.3.31, Complementary) and `-PwithDH` (Distant Horizons
  3.3.3): every frame part 1.20.1 has timed; the results on the world and the screens.
- **The release jar in a real client** (`reobfJar`'s, the one players get): `python tools\backport\prod_client.py <game
  dir> build\libs\culprit-mc1.20.1-0.1.0.jar run\saves\culprit_lab <seconds>` starts the Forge 47.4.28 the CurseForge
  app installed on a throwaway folder with the jar, opens the Lab's world at once, counts every hook's injections and
  closes after the seconds given; its log must show no hook without a place.
- The build deploys the release jar to the folder in `culprit_instance_dir_1_20_1`.
