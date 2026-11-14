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

- Main: **NeoForge 26.3** (Java 25), test instance "Culprit26.3" (NeoForge 26.3.0.103). Ports: **NeoForge 1.21.1**
  (Java 21) from main, **Forge 1.20.1** (Java 17) from 1.21.1, with Ground Burst's port tooling. Every feature and fix
  goes to all three.
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
  results' Average / Lag spikes switch shows every list, the bar and the world over the ticks that took over twice
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

### 4.3 Chunk inspector

- **Every loaded chunk** of each dimension: why it is loaded (player, forced by `/forceload`, portal, ender pearl and
  the game's other tickets, and mod tickets with the mod and the block or entity that owns them on NeoForge and Forge),
  its level (entity ticking, block ticking, border), and what is in it (block entities, entities).
- **Shown** on a top-down map of the dimension's loaded chunks, coloured by why, and on the world (chunk outlines in
  the same colours); listed by mod and owner.

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
| Tickets | 26.3: `TicketStorage.tickets` (`Long2ObjectOpenHashMap<List<Ticket>>`, types in a registry) and NeoForge's `ForcedChunkManager.TicketTracker` (block and entity owners per controller); 1.21.1 and 1.20.1: `DistanceManager.tickets` (`SortedArraySet<Ticket<?>>`), NeoForge's `ForcedChunkManager` and Forge's `ForgeChunkManager` owners |
| Frames, 26.3 | `BlockEntityRenderDispatcher.tryExtractRenderState` and `submit`, `EntityRenderDispatcher.extractEntity` and `submit`; the batched drawing in `FeatureRenderDispatcher` (§5.3) |
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

### 5.4 Network

Results go to the client in pages under the payload limit. The payloads are optional, so the mod on one side alone is
fine (§2).

### 5.5 Permissions and settings

- Server: who may scan (operator level 2 by default), who may view results, who may teleport, the longest scan, the
  largest result.
- Client: the colour scale, labels, through walls, how long the overlay stays, the HUD line.

## 6. Compatibility

Sodium and Embeddium (their render loops call the dispatchers measured); Iris and Oculus (the overlay drawn after the
world, checked under shader packs; the shadow pass counted apart); Distant Horizons (its far terrain is not measured,
and the results say so); Create (contraptions are entities, kinetic machines are block entities; what Flywheel draws by
instancing is outside the renderers and is shown as such); Lithium (block entities it puts to sleep do not tick and so
cost nothing); ModernFix.

## 7. Testing

- **GameTests**, the developer's checks: left out of the released jar with their structures, and registered only in
  the dev runs; the scan itself only measures what is in a player's world and adds nothing to it. Built: hoppers,
  pigs, an observer clock and a piston each found at its place with its kind, type and mod, every total checked
  against the others and the tick's parts against the tick; nested time; passengers; random ticks (a field of grass
  with the random tick speed raised); the commands; the idle cost (`-Pbench`). To come: a forced chunk named by the
  ticket inspector.
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
