# Culprit — Progress

**Last updated: 2026-11-15** — the tick scan accounts for the whole tick: block entities, entities, scheduled block
and fluid ticks, block events, random ticks (by kind of block, per chunk), chunk upkeep and mob spawning, each charged
its own time, and the server's own parts of the tick, Culprit's own timing and what is left, so every list adds up to
the tick. A scan starts from the scan menu (F8) or `/culprit scan`; the chat says when it starts and ends with quick
results; the results screen (F9: Tick, Things, Types, Mods, Chunks, with a search box) shows the rest, and on the
world when the player asks (a white outline and a readout under the crosshair for the thing looked at). The GameTests
(the developer's checks, left out of the jar) plant lag sources of each kind and find every one at its place. spark
researched; of its ideas the user chose lag spikes: the same scan now keeps its slowest ticks, and the results switch
between the average and the lag spikes. The frame scan (the client's frame by thing, part and particle kind, with
stutters) and the chunk inspector (every loaded chunk, how busy, and what keeps it loaded) are built on 26.3; the
inspector names every kind of chunk loader: a mod's by the mod and the block, entity, player or team behind it (FTB
Chunks' team claims too), who forced a chunk (/forceload by whom, or which mod's code), from a record kept with
the world, and which code keeps loading a chunk by reading it (passing requests). The frame scan works with Sodium, Iris
(the shadow pass its own part) and Distant Horizons (its far terrain its own part); the player has settings (Config in
the mod list), a HUD line (TPS, MSPT, FPS) and reports saved as files to share. Light updates are their own part of the
frame (they had been counted as showing the frame). The ports are made: 26.1.2 (NeoForge) and 1.21.1 (NeoForge) from
main, 1.20.1 (Forge) from the 1.21.1 branch, each by its own tool, each passing its GameTests and its Lab runs (plain,
with Iris or Oculus and Sodium or Embeddium, with Distant Horizons), each deployed to its test instance. The first
release, 0.1.0, is ready for the user to upload: the four jars in `build/release`, each checked in a real client of its
loader, the logo, the banner and the page (`docs/branding`, `docs/CURSEFORGE.md`), its pictures from a real plains
village. The GitHub repository is up (Lawman-afk/CulpritMCMod, public, all four branches) and the page's pictures load
from it.

## Plan

| Phase | What | Status |
|---|---|---|
| 0 | Idea, name, versions, design notes, project notes | done (2026-11-14) |
| 1 | The project on NeoForge 26.3: dev runs that stay out of the way, deploying to the test instance, GameTests | done (2026-11-14) |
| 2 | Tick scan (server): the hooks, the scan, results by thing, type, mod and chunk; `/culprit scan`, `top`; GameTests | done (2026-11-14): the whole tick accounted for (random ticks, chunk upkeep, mob spawning, the server's parts, Culprit's own timing); the idle cost measured (none seen); the server's settings (who may scan, the longest scan) |
| 3 | Results on the world (boxes, labels, chunk tiles, beams) and the Culprit screen; network; permissions | built (2026-11-14): the scan menu (F8) and the results (F9: Tick, Things, Types, Mods, Chunks; search, show on the world, teleport), chat at a scan's start and end, the overlay after the level (off until asked for; a readout for the thing looked at), operators only; the Lab world photographed; still to do: the client's settings (ranges, labels), see-through boxes; settings (Config in the mod list: range, labels, hiding after a while, the HUD line) and the HUD line (TPS, MSPT, FPS; a server's numbers to who may scan) built (2026-11-15) |
| 4 | Frame scan (client): 26.3's attribution prototyped first (DESIGN §5.3) | built on 26.3 (2026-11-14): entities and block entities by the time to make and hand in each and their share of the drawing (by parts handed in), particles by kind and chunk, the frame's parts, stutters; the scan menu's Frames row and the results' Frames switch; still to do: Iris's shadow pass; with Sodium (its own block-entity gathering and chunk meshes hooked), Iris (the shadow pass its own part, things' shadows charged to them) and Distant Horizons (its far terrain its own part), checked in the Lab (2026-11-15) |
| 5 | Chunk inspector: tickets and owners, the map, the world view | built on 26.3 (2026-11-14): every loaded chunk, how busy, why (the strongest loader; players, mods with the block or entity behind them, /forceload, portals, pearls, the spawn), what is in it; the results' Loaded chunks section (map, loaders, search, marking, teleport), the world view, `/culprit chunks`; every kind of loader named: a mod's chunk loader by the mod and the block, entity, player or team that owns it (FTB Chunks' team claims), a mod's own tickets, and who forced a chunk the game's way (/forceload by whom, or the mod whose code asked; a record kept with the world); passing requests named by the code that made them load a chunk, and left out where anything else holds it; a test naming each kind |
| 6 | The Lab world and its photographers; Sodium, Iris and shader packs, Distant Horizons checks; export | the Lab built (2026-11-14); Complementary and Distant Horizons checked, the results drawn untouched (2026-11-15); reports as files (Markdown and JSON: `/culprit export`, Save report) built (2026-11-15); Unbound, BSL, Bliss, Photon, Solas and MakeUp checked too, every part timed and the results drawn clean under all (2026-11-15) |
| 7 | The ports: NeoForge 1.21.1 from main, Forge 1.20.1 from 1.21.1 | done (2026-11-15), and 26.1.2 too: branches `26.1.2`, `1.21.1`, `1.20.1` in worktrees beside main, each made by its own `tools/backport/port.py` (docs/BACKPORT.md on each); all GameTests pass on each (9, 8, 8); the Lab plain, with shaders (Iris and Sodium on NeoForge, both 1.21.1 pairs; Oculus and Embeddium on 1.20.1) and with Distant Horizons on each; 1.20.1's release jar checked in a real Forge client; each deployed with the Lab world |
| 8 | The first release: a jar per version with changelogs, the CurseForge page, real-world pictures | made (2026-11-15), for the user to upload: `build/release` holds `culprit-0.1.0.jar` (26.3), `culprit-mc26.1.2-0.1.0.jar`, `culprit-mc1.21.1-0.1.0.jar` and `culprit-mc1.20.1-0.1.0.jar` (no changelog files: a first release, the user's call); each checked (version, nothing dev-only, mixin configs, refmap) and run in a real client of its loader with every hook's places counted (`tools/prod_client.py`); the checks found and fixed the 1.21.1 and 1.20.1 jars carrying the test server's mixins and 26.1.2's chunk-mesh upload hook without a place; logo and banner (`tools/branding`, Ground Burst's style in the cost scale's colours), the page and README; pictures by `runShowcase` (a fresh default world's plains village); the GitHub repository made at the user's word and all four branches pushed (2026-11-15), every picture the page links checked to load; the user uploads |
| 9 | 0.1.1: older loader builds | done (2026-11-27): 0.1.0 asked for the build it was made with and later (NeoForge 21.1.266 on 1.21.1, refused by All the Mods 10 on 21.1.250); now 26.3 any build, 26.1.2 any, 1.21.1 21.1.22 and later (MixinExtras 0.4), Forge 1.20.1 any 47; GameTests and the Lab pass on 26.3.0.76, 26.1.2.71, 21.1.22, 21.1.250, Forge 47.1.0; jars in `build/release` with a changelog, for the user to upload |
