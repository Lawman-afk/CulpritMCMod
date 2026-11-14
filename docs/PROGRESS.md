# Culprit — Progress

**Last updated: 2026-11-14** — the tick scan accounts for the whole tick: block entities, entities, scheduled block
and fluid ticks, block events, random ticks (by kind of block, per chunk), chunk upkeep and mob spawning, each charged
its own time, and the server's own parts of the tick, Culprit's own timing and what is left, so every list adds up to
the tick. A scan starts from the scan menu (F8) or `/culprit scan`; the chat says when it starts and ends with quick
results; the results screen (F9: Tick, Things, Types, Mods, Chunks, with a search box) shows the rest, and on the
world when the player asks (a white outline and a readout under the crosshair for the thing looked at). The GameTests
(the developer's checks, left out of the jar) plant lag sources of each kind and find every one at its place. spark
researched; its ideas are with the user to choose from.

## Plan

| Phase | What | Status |
|---|---|---|
| 0 | Idea, name, versions, design notes, project notes | done (2026-11-14) |
| 1 | The project on NeoForge 26.3: dev runs that stay out of the way, deploying to the test instance, GameTests | done (2026-11-14) |
| 2 | Tick scan (server): the hooks, the scan, results by thing, type, mod and chunk; `/culprit scan`, `top`; GameTests | done (2026-11-14): the whole tick accounted for (random ticks, chunk upkeep, mob spawning, the server's parts, Culprit's own timing); the idle cost measured (none seen); the server's settings (who may scan, the longest scan) |
| 3 | Results on the world (boxes, labels, chunk tiles, beams) and the Culprit screen; network; permissions | built (2026-11-14): the scan menu (F8) and the results (F9: Tick, Things, Types, Mods, Chunks; search, show on the world, teleport), chat at a scan's start and end, the overlay after the level (off until asked for; a readout for the thing looked at), operators only; the Lab world photographed; still to do: the client's settings (ranges, labels), see-through boxes |
| 4 | Frame scan (client): 26.3's attribution prototyped first (DESIGN §5.3) | to do |
| 5 | Chunk inspector: tickets and owners, the map, the world view | to do |
| 6 | The Lab world and its photographers; Sodium, Iris and shader packs, Distant Horizons checks; export | the Lab built (2026-11-14); the rest to do |
| 7 | The ports: NeoForge 1.21.1 from main, Forge 1.20.1 from 1.21.1 | to do |
| 8 | The first release: a jar per version with changelogs, the CurseForge page, real-world pictures | to do |
