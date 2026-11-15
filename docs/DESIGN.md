# Culprit — Design

## 1. Identity

The user's brief (2026-11-14): one of those "useful" mods, "the type of mod thats adds maybe a button on the recipe menu
to switch between recipe conflicts, or the most famous one being JEI/EMI, and also WAILA/Jade", "Other examples include:
Mouse tweaks, appleskin, clumps"; researched first "as to not make a duplicate (unless the prior one was of poor
quality/lacked the full coverage that should be given by such a mod)"; a mod that "doesnt add items or blocks in most
circumstances". From the list of 138 ideas (`docs/IDEAS.md`) the user chose the performance inspector: "I like the
performance inspector idea. Let's go with that."

- **Name: Culprit**, mod id `culprit`, Java package `dev.culprit`. On 2026-11-14 the name had no result at all in
  CurseForge's search of every category (modpacks included) nor on Modrinth, and Modrinth's slug was free (the user's
  rule: zero conflicts anywhere). TickScope, LagScope, LagLens, LagSense, LagFinder and Lagwatch were all taken.
- Tagline: *Find what is lagging your game.*
- Author Lawman-afk, MIT licence (as Ground Burst).
- **What it is**: one tool for the three questions a lagging world raises. What is costing the server its ticks? What
  is costing the client its frames? What keeps the world loaded? Each answer is shown on the world itself, where the
  culprit stands. No items, no blocks.

## 2. Versions and sides

- Main: **NeoForge 26.3** (Java 25), test instance "Culprit26.3" (NeoForge 26.3.0.103). Ports: **NeoForge 26.1.2**
  (Java 25) and **NeoForge 1.21.1** (Java 21) from main, **Forge 1.20.1** (Java 17) from 1.21.1, with Ground Burst's
  port tooling; each in its worktree beside main (`..\Culprit 26.1.2`, `..\Culprit 1.21.1`, `..\Culprit 1.20.1`), each
  deploying to its own test instance (Culprit26.1.2, Culprit1.21.1, Culprit1.20.1). Every feature and fix goes to all
  four (26.1.2 at the user's ask, 2026-11-15).
- MixinExtras (wrapped calls and methods): NeoForge carries it; for Forge 1.20.1 the jar carries it, as Ground Burst's
  does.
- **Either side alone works.** On a server without it, a player with the mod still gets the frame scan. On a server
  with it, players without the mod (vanilla clients) still get the commands and their text results. Both sides give
  everything.

## 3. What exists (researched 2026-11-14; IDEAS.md #112-114)

| Mod | Downloads | Versions | What it does | What it lacks |
|---|---|---|---|---|
| Observable | 51.2M CF, 1.9M M | 1.16 to 1.21.4, last update 2025-06 | Server ticks of entities and block entities; an overlay; results on a web page | 26.x; frames; chunk loading; abandoned |
| LagFinder | 274 CF | NeoForge and Fabric 1.21.8 to 26.3 (2026-09) | Server-only: chunks ranked by tick cost, markers, HTML export | 1.21.1 and 1.20.1; frames; chunk loading; no client view |
| LagSense | 36 CF | NeoForge 26.1.2 only (2026-11-05) | A full server profiler: world view, sampling, history, Discord, claims | Other versions; frames; chunk loading |
| Observable - Remake | 623 CF | NeoForge 26.1.2 | Server ticks, uploaded report | Other versions; frames; chunks |
| LagLens | 401 CF | Forge 1.20.1 only, 0.1.0 (2026-07) | A heatmap of blocks and entities | Other versions; little else |
| Why Loaded | 6 CF | NeoForge 1.21.1 only (2026-11) | Commands listing chunk tickets and forced chunks | Other versions; no view; no lag |
| ChunkDebug | 80k M | Fabric only | Chunk loading debug | NeoForge and Forge |
| spark | 24.3M M | all | Profiles code (methods, threads), not places | Where in the world |
| LagGoggles | 1.12.2 only | | Server ticks and, from 4.0, client render time by thing | Everything after 1.12.2 |

What no one does now: **frame cost by place** (not since LagGoggles on 1.12.2); **chunk loading shown on the world with
its owners** on NeoForge or Forge; **one tool for ticks, frames and chunks**; and **all three of our versions** (1.20.1
has only Observable and LagLens, 1.21.1 only Observable and Why Loaded).

## 4. What it does

### 4.1 Tick scan (server)

- **Measured**: every block entity tick; every entity tick (a vehicle's own time apart from its passengers'); scheduled
  block and fluid ticks; block events (pistons, note blocks, chests opening); each chunk's random ticks, charged to the
  kind of block ticked (crops, grass, leaves) in that chunk; each chunk's own upkeep (picking the blocks to tick,
  snow and ice); mob spawning per chunk.
- **The whole tick**: the rest of it in parts, each its own time (what ran inside taken out): chunk loading and sending
  (the chunk source), the levels' upkeep (weather, time, raids, the tick lists), other mods' level tick events, the
  players' connections, the server's own work (functions, the player list), the autosave; Culprit's own timing,
  estimated from what measuring one call costs; and what is left, shown as not measured. The parts add up to the
  tick's total.
- **Results**: for each thing, its mean time per tick over the scan, its worst single tick and its share of the tick;
  folded by type, by mod and by chunk; with dimension and position.
- **Lag spikes** (2026-11-14): the same scan keeps its slowest ticks whole (the slowest 5 %, 5 to 60 of them); the
  results' "Lag spikes only" tick box (at the bar's end, apart from the tabs) shows every list, the bar and the world over the ticks that took over twice
  the scan's usual (median) tick and at least 5 ms, with the averages untouched. The chat gives their count.
- **Started** from the scan menu (F8: 10, 30 or 60 seconds, or stop) or by `/culprit scan [seconds]` (10 by default,
  5 to the server's longest, 120 by default); operators by default (the server's config). The chat says when a scan
  starts and when it ends, with the tick's mean and worst and the three costliest types; the results (F9) and the
  world show the rest. Results go to whoever started the scan, and to anyone permitted who asks.

### 4.2 Frame scan (client)

- **Measured**: every block-entity renderer and entity in view (per thing, type and mod), particles by type, and the
  frame's own totals (terrain, entities, block entities, particles, the HUD and screens).
- **Results**: as the tick scan's, per frame: mean time, worst frame, share of the frame.
- **Started** by a key or the screen, on any server, the mod's server side not needed.
- With Iris, the shadow pass is counted on its own.
- **Built on 26.3 (2026-11-14)**: the scan menu's Frames row (10, 30, 60 s, stop); each entity and block entity in view
  charged the time to make its render state and to hand in its parts; the frame's parts beside them (client ticks,
  packets and tasks, finding what is in view, particles, gathering and sorting what to draw, drawing, chunk meshes,
  the HUD and screens, mods' frame events, light updates, showing the frame, the frame-rate limit's wait), Culprit's
  own timing and what is left, adding up to the frame. The results' Server ticks / Frames switch shows it in the
  same lists (Frame, Things, Types, Mods, Chunks) and on the world; the stutters (frames over twice the usual one and at least 20 ms)
  beside the averages, as the lag spikes are for ticks. Particles by kind and chunk (each particle's move and making
  ready; its type kept from when it was made). The sorting and the drawing of the handed-in parts shared out to the
  entities and block entities by the parts each handed in (§5.3).
- **With Sodium and Iris (2026-11-15, Sodium 0.9.3 and Iris 1.11.7 for 26.3)**: Sodium gathers the block entities in
  view itself (`SodiumWorldRenderer.extractBlockEntity`, in place of the game's loop) and builds chunk meshes its own
  way (`RenderSectionManager.updateChunks`, `processChunkBuilds`): both timed as the game's are, so block entities and
  "building chunk meshes" are measured with it too. Iris's shadow pass (`ShadowRenderer.renderShadows`) is its own part
  of the frame, "Shader shadows"; the entities and block entities it gathers and hands in for the shadows are charged
  to them, as in the main pass, and the parts it hands in are drawn and shared out with the rest. In the Lab under
  Complementary: shader shadows 1.12 ms a frame, every part timed, the results drawn over the pack untouched.

### 4.3 Chunk inspector

- **Every loaded chunk** of each dimension: why it is loaded (player, forced by `/forceload`, portal, ender pearl and
  the game's other tickets, and mod tickets with the mod and the block or entity that owns them on NeoForge and Forge),
  its level (entity ticking, block ticking, border), and what is in it (block entities, entities).
- **Shown** on a top-down map of the dimension's loaded chunks, coloured by why, and on the world (chunk outlines in
  the same colours); listed by mod and owner.
- **Built on 26.3 (2026-11-14)**: the results' Loaded chunks section (a first look as it opens, Look again): the
  headline (how many chunks, how busy, by reason), a map round the player (colour by reason: players, mods,
  /forceload, portals, ender pearls, the spawn, other; shade by ticking entities, ticking blocks, border), a hover
  for each chunk (how busy, its strongest loader and the other reasons, its block entities and entities), the
  loaders listed with the chunks each keeps loaded (mods and forced first; the search box narrows them), a click
  marking them on the map and the world, teleport to one; on the world, the loaded chunks near the camera outlined in
  their colours and the loaders other than players named over their chunks. `/culprit chunks` for consoles.
- **Every kind of chunk loader** (the user: "Does the force load account for modded chunk loader? Of any type too?
  Items, FTB maps, commands, blocks?"). A chunk stays loaded only while a loading ticket holds it, and the inspector
  reads every ticket of every dimension, so nothing that keeps a chunk loaded is missed; what differs is how well its
  owner is named:
  - NeoForge's chunk-loader API (blocks, entities, items carried by a player, claim mods): the mod by its name, then
    the block and its place, the entity, or the owner known by id: a player online or seen by the server, a team of
    FTB Teams (FTB Chunks keeps a team's force-loaded claims under the team's id: checked in its source on 1.21.1 and
    26.1), else the id's first eight digits. "FTB Chunks: team Steve's Party", "Mekanism: Digital Miner at 10 64 -5".
  - A mod's own kind of ticket: the mod and the ticket's name ("Create: contraption").
  - The game's forced ticket, which `/forceload` and a mod calling the game's `setChunkForced` share and which says
    nothing of its owner: Culprit records each change as it happens and keeps the record with the world (its own
    small saved data, each dimension's `data/culprit/forced_chunks.dat`): "/forceload by Steve" (or by a command block, the server),
    "Mekanism: forced chunk" (the mod whose code asked, the first class on the stack that is no part of the game,
    NeoForge or a library, found in the mod files), the game's test structures; a chunk forced before Culprit was
    installed reads "/forceload or a mod".
  - NeoForge's `/neoforge generate` making new chunks; portals, ender pearls (and who threw them), the dragon fight,
    the spawn, players.
  - Passing requests (2026-11-15), their own reason: code that reads a block in an unloaded chunk makes the game load it
    there and then (a stall on the server thread) and hold it a tick; asked again every tick, it stays loaded. When such
    a request has to load a chunk in full, the code that asked is noted, in memory while the chunk stays loaded:
    "Mekanism: asked for it", or "the game (Raid): asked for it". A request where anything else holds the chunk is not
    why it is loaded (every tick something reads the chunks of a player's area), so it is left out of the results; on a
    tie of strength it is taken last.

### 4.4 Results on the world and on screen

- **On the world**: a box round each measured thing, coloured from cool to hot by its cost in a colour scale that also
  reads for colour-blind players; its cost as a label when close or looked at; each chunk's total as a tile on the
  ground; a beam over the worst few so they can be found from afar; through walls when chosen; hidden by a key, or
  after some minutes.
- **Built (2026-11-14)**: off until the player shows it with the results screen's button; labels over the costliest
  close things on screen that the camera sees, all one size on screen and drawn over the blocks round them (so a pane of glass or a
  hopper above never cuts one short); the thing the crosshair rests on outlined in white and read out under the
  crosshair (its name, cost a tick, share of the tick, worst tick, mod), or, on open ground, the outlined chunk's total
  and what is mostly in it.
- **The Culprit screen**: Ticks, Frames and Chunks tabs; lists sorted and grouped by thing, type, mod or chunk; search;
  a click marks the thing on the world with an arrow towards it; teleport for operators; copy a summary.
- **Built for the tick scan (2026-11-14)**: the results screen (F9) opens on a Tick tab, a bar of the whole tick by part
  with every part listed, then Things, Types, Mods (the game's own work as "(the game)" lines, so the tab adds up to
  the tick) and Chunks. Both keys can be changed in Controls. A search box narrows the list shown (names as shown and
  ids, kinds, places, mods); typing anywhere starts it, Enter picks the first line found.
- **Commands** for vanilla clients and server consoles: `/culprit scan`, `stop`, `top`, `chunks`, `export`; text
  results with click-to-teleport for operators.
- **Export**: a report file (Markdown and JSON) in the game folder, for sharing with a pack's makers.

### 4.5 HUD line (optional)

- The server's TPS and MSPT (when permitted), the client's FPS and frame time.
- **Built (2026-11-15)**: off by default; on from the settings or a key (none set at first). "TPS 20.0  MSPT 3.8  FPS 60
  (16.7 ms)" in a corner (top left by default), the numbers green, yellow or red. A world of one's own is read directly;
  another server sends its average tick and ticks a second once a second to a player who asked and may scan there
  (nothing is sent while no one asks). Hidden with the HUD (F1) and under the debug screen (F3).

### 4.6 Later, not in the first release

- Event-handler time by mod (NeoForge's event bus), sampling of call paths (spark's ground), saved history and
  comparisons, scheduled and lag-spike scans, Discord reports, players scanning their own claims.

## 5. How it is built

### 5.1 Where it measures (the game's own calls, the same in all three versions; read in each version's decompiled code 2026-11-14)

| What | 26.3, 1.21.1 and 1.20.1 |
|---|---|
| Block entities | `Level.tickBlockEntities()` calls `TickingBlockEntity.tick()` for each ticker: wrapped |
| Entities | `ServerLevel.tickNonPassenger(Entity)` and `tickPassenger(Entity, Entity)`: wrapped |
| Scheduled ticks | `ServerLevel.tickBlock(BlockPos, Block)`, `tickFluid(BlockPos, Fluid)` (called by `LevelTicks.tick`) |
| Block events | `ServerLevel.doBlockEvent(BlockEventData)` |
| A chunk's upkeep | `ServerLevel.tickChunk(LevelChunk, int)`, its random ticks taken out |
| Random ticks | the `BlockState.randomTick` and `FluidState.randomTick` calls in `tickChunk` (26.3; to read in 1.21.1 and 1.20.1 when porting) |
| Mob spawning | `NaturalSpawner.spawnForChunk` in `ServerChunkCache.tickSpawningChunk` (26.3; to read in the ports) |
| Whole tick | the tick's total between NeoForge's server tick events; parts: `ServerChunkCache.tick`, `ServerLevel.tick`, `MinecraftServer.tickChildren`, `tickConnection`, `autoSave`, and NeoForge's level tick events in `tickChildren` (26.3; to read in the ports) |
| Tickets | 26.3: the loaded chunks from `ChunkMap.visibleChunkMap` (an accessor), each one's tickets from `TicketStorage.getTickets` (the world's `TicketStorage` from its saved data), NeoForge's owners (controller, owning block or entity) from `TicketStorage.CODEC`'s own `neo_ticket_data` (public, as the world saves them); before that: `TicketStorage.tickets` (`Long2ObjectOpenHashMap<List<Ticket>>`, types in a registry) and NeoForge's `ForcedChunkManager.TicketTracker` (block and entity owners per controller); 1.21.1 and 1.20.1: `DistanceManager.tickets` (`SortedArraySet<Ticket<?>>`), NeoForge's `ForcedChunkManager` and Forge's `ForgeChunkManager` owners |
| Frames, 26.3 | `LevelExtractor.extractEntity` and the `BlockEntityRenderDispatcher.tryExtractRenderState` calls in `extractVisibleBlockEntities` (states remembered to their thing); the `EntityRenderDispatcher.submit` and `BlockEntityRenderDispatcher.submit` calls in `LevelRenderer.submitEntities`/`submitBlockEntities`; parts: `Minecraft.runTick` (the frame: `tick`, the packets and tasks) and `renderFrame` (`CommandEncoder.submit`, `GpuSurface.present`, `FramerateLimiter.limitDisplayFPS`), NeoForge's `RenderFrameEvent`s (first and last listeners); `GameRenderer`'s `LevelExtractor.extract`, `Gui.extractRenderState`, `GuiRenderer.render`; `ParticleEngine.extract`, `ParticlesRenderState.submit`, `LevelRenderer.submitFeatures`, `FeatureRenderDispatcher.prepareFrame`, `FrameGraphBuilder.execute`, `compileSections`, `uploadTerrainBuffersToGpu` |
| Frames, 1.21.1 and 1.20.1 | `BlockEntityRenderDispatcher.render`, `EntityRenderDispatcher.render`, `ParticleEngine.render`: they build their vertices as they are called; Sodium's and Embeddium's loops call the same dispatchers |

### 5.2 Measuring without disturbing

- **Idle**: one boolean read per hook, nothing else.
- **Scanning**: two `System.nanoTime()` calls per measured call; costs kept in primitive arrays by a slot per thing
  (the thing's slot found once per thing through an identity map); nothing allocated per tick or per frame; the
  timer's own cost measured when a scan starts and taken off; nested calls (passengers in vehicles, a block entity that
  ticks another) counted once, each to its own.
- **Culprit's own cost while scanning**: what measuring one call costs in all (both timings, finding the slot, adding)
  is measured as a scan starts, and the report shows the calls times that cost as its own part of the tick, so the
  tick's parts add up without hiding what the scan itself adds.
- **Budgets**: idle cost too small to measure; under 5 % added to the tick while scanning 10,000 block entities; the
  overlay under 0.5 ms a frame for 2,000 boxes.
- **Measured (2026-11-14)**, the idle cost: a plot of 1,792 hoppers and 200 pigs (about 2,000 hooked calls a tick),
  1,200 ticks timed with no scan, twice with the hooks and twice without them (`-Pbench`, `-PnoHooks`): the tick's
  median was 1.64 and 1.55 ms with them, 1.67 and 1.67 ms without. The hooks cost nothing the measurement can see; the
  runs differ among themselves by more than any cost of theirs.

### 5.3 Frames on 26.3 (to prototype first)

26.3 draws in two steps: each block entity and entity is turned into a render state and submits what it draws, and the
submitted parts are drawn later, in batches by kind. Timing the first step per thing is direct; the drawing is shared.
The prototype measures both and tries tagging each submission with the thing that made it, so the drawing time can be
shared out by what each thing submitted. If tagging costs too much, the drawing is shown per kind of part, beside each
thing's own time and the count of parts it submitted.

Built (2026-11-14): every part handed in is counted (`SubmitNodeCollection`'s submit methods), each thing's own count
taken round its submission; the drawing of the handed-in parts is timed apart from the terrain's (`PreparedFrame`'s
execute methods); at each frame's end the sorting and that drawing are shared out to the things by their share of all
the parts handed in, and moved from the frame's parts to them. In the Lab, 160 dropped items went from 154 to 489 µs a
frame, and the sorting part from 0.70 to 0.06 ms.

### 5.4 Network

Results go to the client in pages under the payload limit. The payloads are optional, so the mod on one side alone is
fine (§2).

### 5.5 Permissions and settings

- Server: who may scan (operator level 2 by default), who may view results, who may teleport, the longest scan, the
  largest result.
- Client: the colour scale, labels, through walls, how long the overlay stays, the HUD line.
- **Built (2026-11-15)**: the player's own settings (config/culprit-client.toml, and Config in the mod list, NeoForge's
  own screen, one page, opened by the results screen's Settings button in its top right corner, and by Config in the
  mod list): how far things are boxed (16 to 128 blocks, 64), how many are labelled (0 to 30, 12), minutes after which
  the results hide themselves (0, never), the HUD line and its corner. The server's (who may scan, the longest scan) show
  on the same screen in one's own world. Left out: a second colour scale (the one there reads for colour-blind players),
  boxes through walls (the game's line and quad kinds all test depth; the picked thing's beam and label already show
  through), and separate permissions to view or teleport (results go only to who scanned; teleporting uses /tp's own).

## 6. Compatibility

Sodium and Embeddium (their render loops call the dispatchers measured; on 26.3 Sodium's own block-entity gathering
and chunk-mesh work are hooked, §4.2); Iris and Oculus (the overlay drawn after the world, checked under shader packs;
the shadow pass counted apart, §4.2); Distant Horizons (its far terrain, drawn and faded in, is its own part of the
frame, "Distant Horizons": DH 3.3.4's `ClientApi` render calls, 2026-11-15); Create (contraptions are entities, kinetic machines are block entities; what Flywheel draws by
instancing is outside the renderers and is shown as such); Lithium (block entities it puts to sleep do not tick and so
cost nothing); ModernFix.

## 7. Testing

- **GameTests**, the developer's checks: left out of the released jar with their structures, and registered only in
  the dev runs; the scan itself only measures what is in a player's world and adds nothing to it. Built: hoppers,
  pigs, an observer clock and a piston each found at its place with its kind, type and mod, every total checked
  against the others and the tick's parts against the tick; nested time; passengers; random ticks (a field of grass
  with the random tick speed raised); the commands and the report files; the idle cost (`-Pbench`); every kind of chunk loader named
  (a forced chunk by the command and by code, a block's and a team's loader, a passing request).
- **The prepared world "Culprit Lab"** (a dev-only run, never in the jar): a wall of hoppers, a mob crowd, item piles,
  a redstone clock, a forest of signs and banners (frames), item frames with maps, armour stands, a beacon, a
  force-loaded chunk far away, a portal; signs saying what to try. Photographers take the overlay's pictures.
- Client runs muted, behind the user's windows, without focus or the mouse, the early window off.

## 8. Decision log

| Date | Decision |
|---|---|
| 2026-11-14 | Showpiece shelved; a utility mod next, its ideas researched (`docs/IDEAS.md`, 138 ideas). |
| 2026-11-14 | The performance inspector, the user's choice: "I like the performance inspector idea. Let's go with that." Re-checked before starting: since the list, three young server profilers (LagFinder, LagSense, Observable - Remake) and a chunk-ticket command mod (Why Loaded) were found, each on one or a few versions and with a few hundred downloads at most; none measures frames by place or shows chunk loading on the world, and none covers 26.3, 1.21.1 and 1.20.1 together (§3). |
| 2026-11-14 | The name is Culprit (`culprit`): no result on CurseForge (every category) or Modrinth. |
| 2026-11-14 | Main NeoForge 26.3, ports NeoForge 1.21.1 and Forge 1.20.1, as Ground Burst; the project set up from Showpiece's build (dev runs, deploy task, GameTests); the folder, repository, instance ("Culprit26.3") and build key renamed for it. |
| 2026-11-14 | First the tick scan with its commands and GameTests, then the results on the world and the screen, then the frame scan (26.3's attribution prototyped first), then the chunk inspector. |
| 2026-11-14 | The tick scan built (as DESIGN §4.1 and §5.2, PLAYBOOK has how): `@WrapOperation` round block entity tickers, `@WrapMethod` on the server level's entity, passenger, scheduled tick, block event and chunk methods; per-thing slots in primitive arrays; nested calls taken out; the timer's cost taken off. Commands only for now; its GameTest finds hoppers, pigs, an observer clock, a piston and the plot's chunk at their places. |
| 2026-11-14 | The results reach a player's client and the world (DESIGN §4.4, §5.4): three optional payloads (report, status, scan request; operators only); the overlay drawn after the level, as Ground Burst's effects are, so shader packs and Distant Horizons leave it alone (boxes in the plasma colour scale, labels over the costliest close ones, chunk tiles at the feet, the screen's pick marked by a beam); the Culprit screen on F7 (summary, things, types, mods, chunks, scan, stop, show or hide, teleport); the Lab world built by a dev-only run and photographed. |
| 2026-11-14 | After the first pictures of the overlay: chunk outlines on the ground (not at the player's feet), labels kept apart as seen from the camera, the player's own box left out, cheap things fainter; the colour scale turned round so the costliest looks the most alarming (pale yellow, orange, red, magenta); the screen fitted to any window. Server settings: who may scan (operators by default) and the longest scan. The scanning tests each run alone (two scans raced in one batch). |
| 2026-11-14 | The user asked why the screen said "3.03 ms" of a 3.86 ms tick, and why the "By mod" list came to only 2.43 ms: the scan measured only the things, and chunk ticks belonged to no mod. Now the whole tick is accounted for (§4.1, §5.2): the server's parts, Culprit's own timing and what is left as not measured; a chunk's random ticks split from its upkeep and charged to the kind of block ticked (and so to its mod); mob spawning measured; the Mods tab lists the game's own work as "(the game)" lines, so every tab adds up to the tick. |
| 2026-11-14 | Keys, the user's idea ("f7 to open the start menu, f8 to read the last test result"; F7 is a popular lighting mod's, so keys must be changeable): F8 opens the scan menu, F9 the results, both changeable. F7 is Light Overlay's; F8 is free in Minecraft on every version (the smooth camera has no key); F9 and F10 are used only by small mods. The chat says when a scan starts and when it ends, with the quick results. |
| 2026-11-14 | After the pictures of the new screens: a share is of the server's tick (the Tick and Mods tabs add up to 100 %), not of the 50 ms a tick may take (the summary gives that); things are named as the game names them in the player's language (Villager, Grass Block, Water), from the registries by kind; a place's block entities are one thing (a piston's moving block is a new block entity at every push: 536 "pistons" for 8); a whole chunk's things are placed as "chunk x, z" and teleported to on the ground; the Mods tab's own lines are one "The game's own work", Culprit's own timing and Not measured. |
| 2026-11-14 | The GameTests (and their structures) are left out of the released jar and registered only in the dev runs: the user asked whether the test is part of the mod; it is the developer's check, and the scan only measures what is already in the world. A stop asked for inside a tick ends with that tick, so the parts always fit in the ticks. |
| 2026-11-14 | The user's calls after trying the screens: the chat says only "Scanning the server for 10 seconds." and "Done. The server tick took ... Costliest: ...", capitalised, with no key hints; the world view is off until the results screen's button; a search box for each list; and the thing looked at is the one read out ("maybe making it so that it will focus on the one your looking at"), so labels are no longer the only way to see a cost; labels all one size on screen. |
| 2026-11-14 | spark (spark.lucko.me, which the user's friend called a non-real-time version) researched: a sampling profiler with a web viewer (call tree, flame graph, by mod), a health report (TPS, MSPT, CPU, memory, disk, GC), a tick monitor that reports ticks over a threshold, `--only-ticks-over` to profile only lag spikes, heap summaries and dumps, and world statistics (entities by type, chunk and cluster). It finds which code is slow; Culprit finds which thing and where, live on the world: they work side by side. Ideas worth taking (lag spikes, a health line, mods' event time, entity counts in the chunk inspector, a local report to share) were put to the user before building any, as asked. |
| 2026-11-14 | The user: "I think the lag spikes are important to use, but how do we implement it without sacrificing the average results as well? Maybe a toggle in the menu to switch between the 2." One scan gives both: each tick's costs are folded into the averages as before, and the slowest ticks are also kept whole (a pool of the slowest 5 %); at the end, those over twice the median tick and at least 5 ms are the spikes, a second report sent beside the first. A switch beside the tabs picks which one the lists, bar and world show. Twice the median rather than a fixed 50 ms: a server rarely goes over 50 ms in a short scan, but the ticks well over its usual one are what players feel as stutter; the 5 ms floor keeps an idle server's noise out. Also, at the user's word: no chunk under the crosshair (things only), and a thing that is gone (an item picked up) is no longer boxed as a whole block at its old place. |
| 2026-11-14 | The frame scan built on 26.3 ("continue with your plans"): the same tables and reports as the tick scan (a frame for a tick), so frames have types, mods, chunks, the world view and stutters at no extra cost; a thing's cost is what it takes on the render thread to be made ready and handed in, the drawing itself a part of the frame, not yet shared out by thing. A frame scan needs nothing from the server and runs beside a tick scan (each its own nesting, the server thread's and the render thread's). |
| 2026-11-14 | The frame hooks are optional (`require = 0`): a hook that cannot find its place (another mod changed the frame loop, a later version) leaves that part unmeasured instead of stopping the game, which the tick hooks still do since the tick scan is useless without them. The Lab names any frame part never timed. |
| 2026-11-14 | Particles by kind (a new kind, chunk-wide like random ticks: per kind and chunk, not per particle) and the drawing shared out by parts handed in: the two gaps the first frame scan left, done before the chunk inspector. A particle's type is kept in a field set when the engine makes it (a mixin), its cost charged by kind; one added another way is named by its class. |
| 2026-11-14 | The chunk inspector built on 26.3 ("continue with your plans"): a chunk's reason is its strongest loading ticket's (level plus distance), a player's taken last on a tie (a player's surroundings are expected to be loaded; a mod's or a forced chunk is what stays when everyone leaves); a loader is listed with every chunk it keeps playable, not only those it is strongest for. NeoForge's chunk-loader owners are read through the world's save codec rather than its private trackers (public, and what the world itself writes). Sent on request (operators, as scans), at most 40,000 chunks. |
| 2026-11-14 | The user asked whether the inspector accounts for modded chunk loaders "of any type". Every loading ticket was already read, so every loader was counted; two kinds were named wrongly. A loader owned by an id that is no entity (FTB Chunks keeps a team's claims under the team's id, which we checked in its source) read "an entity not loaded"; it is now named as the player or team. A mod forcing a chunk the game's own way read "/forceload"; a record of each change, kept with the world, now says who ran /forceload or which mod's code asked. The mod is found from the stack, by the first class that is not the game's, found in the mod files: dependable, and paid only when a chunk is forced. Both hooks are optional (`require = 0`), like the frame hooks. Mods are named as they name themselves, blocks and entities as the game does. |
| 2026-11-15 | Passing requests named and put in their place (the user said yes to it, then "continue your development plan"). In the Lab, 287 of a player's chunks read "passing requests": something reads them every tick, and on a tie of strength a passing request won over the player. Now a passing request is taken last and dropped wherever anything else holds the chunk; where it is all a chunk has, the code that made the request load it is named. The request that has to load a chunk is the only one walked, so reading loaded chunks costs nothing more. The stack walk is shared with the forced-chunk record. |
| 2026-11-15 | The frame scan with Sodium and Iris (the plan's next step). Sodium on 26.3 replaces the game's block-entity gathering and chunk-mesh building, so the frame scan saw no block entities with it: its own methods are hooked the same way, by name, only when Sodium is there (`@Pseudo`, optional). Iris's shadow pass becomes its own part, "Shader shadows", and what each thing costs for its shadow is the thing's, since a mob's shadow is drawn because of the mob. Hooks into other mods are written against the game's types only (their own classes are named as strings), so Culprit needs neither mod to build or run. |
| 2026-11-15 | Settings and the HUD line (the plan's next step). The player's settings are the ones that change what they see: range, labels, hiding after a while, the HUD line. NeoForge's own config screen shows them, with no screen of our own to keep. The HUD line reads one's own world directly and asks any other server, which sends only to players who asked and may scan, once a second. Distant Horizons' far terrain is its own part of the frame (it had been counted as drawing the terrain: 1.42 ms of the Lab's frame with DH against 0.19 ms without). The hooks into other mods are now applied only when the mod is installed, so a game without them logs no warnings. |
| 2026-11-15 | Reports as files (§4.4): a Markdown page to read and paste where a pack's makers will see it, and JSON for tools, both made from the report as the client holds it, so the server's `/culprit export` and the results screen's Save report write the same thing (the page names things as the game does, in English on a server). The name-giving and the parts of the tick or frame moved from the client to a common package for it. |
| 2026-11-15 | After the user's look: "Change the average versus lag spikes menu button to not be in the same place, and look like a selectable catergoy, because it is confusing. Also I cant find this settings page. Please place it in the F9 menu instead." The switch sat at the end of the tab row and read as a sixth tab; it is now a tick box, "Lag spikes only (4)" ("Stutters only" for frames), at the bar's end under the headline, since it changes what the headline, the bar and every tab cover. Settings is a button in the results screen's top right corner, after the search box (in the bottom row six buttons no longer fit a 1280×720 window at the game's own scale, and beside the sections it would read as a fourth section); it opens the settings page and returns to the results. Config in the mod list opens the same page. |
| 2026-11-15 | Light updates are their own part of the frame, found while porting the frame scan to 1.21.1. The client works out the world's light again where blocks changed, once a frame; on 26.x it does so inside `renderFrame`, whose untimed time is "showing the frame", so it was counted as waiting for the GPU or the screen, and on 1.21.1 it is the start of the level's drawing. Now "Light updates" (`ClientLevel.update` on 26.x, the level renderer's two light calls on 1.21.1): about 45 µs of the Lab's frame, and more wherever blocks change in numbers. |
| 2026-11-15 | The ports (the user: "Yes please go ahead, and maybe do 26.1.2 as well please."). Each branch is made by its own tool from the version above it (26.1.2 and 1.21.1 from main, 1.20.1 from 1.21.1), never kept by hand, so a fix on main reaches every version by a sync. What each version draws differently gets that version's own frame hooks (1.21.1 and 1.20.1 draw each thing as it goes: each one's drawing charged to it whole; 26.x hands parts in), and the frame parts' words say what that version measures. Forge 1.20.1 draws no settings page for a mod: Culprit draws its own there, with the same five settings. Test runs prove a port, not its compiling: each runs its GameTests (reading the log's pass line: a server that fails to start still ends the task without an error) and its Lab plain, with shader packs and with Distant Horizons, guarded so a client with a loading error closes at once (the user saw one error window); the 1.20.1 release jar (Forge's names, through the refmap) runs in a real Forge client before it reaches the instance. Where a version's test server differs from main's (1.21.1's and 1.20.1's helper counting from the structure block, chunks ticking only near players, 1.20.1's tests at the origin among the spawn chunks), dev-only mixins make it main's, so the tests themselves stay main's. |
| 2026-11-15 | The first release, 0.1.0 (the user: "Please provide the 4 jar version (no changelogs as this isnt an update), as well as the summary one line sentence for the modpage, and a logo. For the logo, and the banner please you a similiar styling to groundburst ... Then please provide the desciption for the mod I can paste in amrkdown, use the layout of groundburst as an example."). Every release jar is checked as a player has it, not only by the dev runs: its contents (its version; nothing dev-only: the 1.21.1 and 1.20.1 jars had carried the test server's own mixins, which only dev runs apply, now left out with their mod-file entry; mixin configs, and the refmap on 1.20.1) and a real client of its loader opening a world with Mixin counting every hook's places (`tools/prod_client.py`): every hook found its place but the four into Sodium, Iris and Distant Horizons, which apply only with those mods. That check found what the Lab could not: 26.1.2 uploads the chunk meshes in another method than 26.3, so its optional hook there had no place and was silent (another hook times the same part, so the Lab saw every part timed); fixed in 26.1.2's port tool. The branding follows Ground Burst's (its type, a stripe under the title, a tagline, the logo's icon over a diagonal tape on the dark) in Culprit's own theme, the cost scale it colours the world with: the title CULPRIT with its last letters running up the scale, the stripe and the logo's tape the scale itself in steps, the logo a pixel-art magnifying glass holding the costliest kind of box. The banner and the page's pictures come from a normally generated world, as the rule for public pictures asks: a fresh default world's plains village (a desert one was tried first: the scale's warm colours vanish on sand), scanned and photographed by a dev-only run on main (`devworld/Showcase`), nothing built, only the time of day set and one chunk /forceloaded beside the village. The page names only what was checked on each version, in the code or by running it; that players without Culprit can join a server with it rests on the code (its network is optional on both loaders), and vanilla clients are not promised. |
| 2026-11-15 | The user: "The logo is good, but the centre piece covers the stripe." The cost-scale tape had run behind the glass, as Ground Burst's tape runs beside its missile; the glass's round lens is wide, so the tape now crosses the bottom-right corner clear of it, from the bottom edge to the right edge, the whole scale in sight. Then "the stripe doesnt finish all the way down": its end, cut on the slant, stopped inside the logo; it is now long enough to meet both edges whole. |
