# Culprit for Minecraft 26.1.2 (branch `26.1.2`)

Main is written for Minecraft 26.3 (NeoForge 26.3). This branch is the same mod for Minecraft 26.1.2 (NeoForge
26.1.2.126, Java 25), made from main's sources by a tool, not kept by hand.

- **Last brought up to:** `main` at `23f70eeb` (2026-11-15: the chunk inspector names every kind of loader, passing
  requests by the code that asked; the frame scan with Sodium, Iris's shadow pass and Distant Horizons; settings, the
  HUD line, reports as files; the lag spikes tick box).

## How it is made

`tools\backport\port.py` reads every file of main at the commit in `tools\backport\base.txt` straight from git and
writes its port here:

- **Java** through `RULES` (names 26.3 changed: the screen and HUD accessors, `EntityTypes`, synced settings as server
  settings, the key mappings' keyboard type, the GameTests' data without a dimension, the flat world preset) and per-file
  fixes (`FILE_FIXES`): the frame scan's hooks on 26.1.2's methods (the level gathered by the level renderer's
  `extractLevel`, its block entities in the gathering method with a frustum, the HUD gathered in `extractGui` and drawn
  with the fog's buffer, the terrain drawn in `renderLevel`, the chunks' meshes built from the camera, the particles
  handed in inside the main pass, the frame shown by `blitToScreen` and `flipFrame`); Sodium's block entity call
  without the "global" flag; the overlay's geometry handed in by a call and drawn into the game's buffers; the Lab's
  signs by their side's flag.
- **Resources:** the GameTests' structure saved at 26.1.2's data version, its palette named as 26.1.2 reads it.
- **Kept by hand** (`tools\backport\manual.txt`): `mixin/PreparedFrameMixin` (26.1.2's feature dispatcher draws what
  was handed in at once, with no frame prepared and sorted first) and `client/DevRuns` (the test runs' window behind the
  user's, on GLFW: main's is SDL's). A sync merges main's changes into them, conflicts marked.
- **This branch's own:** the build files (`build.gradle`, `gradle.properties`), the tool and this file.

Bring the branch up to a newer main with `python tools\backport\port.py --sync <main's full commit hash>` (never
`HEAD`: in this worktree it is the branch's own commit), then `python tools\backport\port.py`; compile and sort the
errors with `gradlew compileJava` and `python tools\backport\errs.py <log>`.

## What differs from main for players

- **The frame scan** has no "Gathering what to draw" or "Sorting what to draw" parts: 26.1.2 hands the level's parts
  in inside its main pass and draws them without sorting them first, so neither is a step of its own. Their time is in
  the other parts.
- **Settings:** the world settings are `culprit-server.toml` (main's are synced settings).

## Checks

- `gradlew runGameTestServer`: main's GameTests (9 at `23f70eeb`, all pass).
- `gradlew runLab`, with `-PwithIris` (Iris 1.11.4, Sodium, Complementary) and `-PwithDH` (Distant Horizons): every
  frame part timed but the two above; the results on the world and the screens as main's. The dev runs' draw check is
  `neoforge.disableGlValidation` here (main's `neoforge.disableBlaze3DValidation`).
- The build deploys to the folder in `culprit_instance_dir_26_1_2` (in `%USERPROFILE%\.gradle\gradle.properties`).
