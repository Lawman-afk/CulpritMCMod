# Culprit — Progress

**Last updated: 2026-11-14** — the idea chosen (the performance inspector), researched again against the mods that
exist, named Culprit (no conflict anywhere), designed (DESIGN.md), and set up on NeoForge 26.3 from Showpiece's build.
The tick scan works from commands (`/culprit scan`, `stop`, `top things|types|mods|chunks`): block entities, entities,
scheduled block and fluid ticks, block events and chunk ticks, each charged its own time; a GameTest plants one lag
source of each kind and finds every one at its place.

## Plan

| Phase | What | Status |
|---|---|---|
| 0 | Idea, name, versions, design notes, project notes | done (2026-11-14) |
| 1 | The project on NeoForge 26.3: dev runs that stay out of the way, deploying to the test instance, GameTests | done (2026-11-14) |
| 2 | Tick scan (server): the hooks, the scan, results by thing, type, mod and chunk; `/culprit scan`, `top`; GameTests | built (2026-11-14); still to do: the idle cost measured (hooks on and off), mob spawning and the whole tick's phases, the server's settings (who may scan) |
| 3 | Results on the world (boxes, labels, chunk tiles, beams) and the Culprit screen; network; permissions | built (2026-11-14): the report sent to the player's client, the overlay after the level, the screen on F7 (things, types, mods, chunks; scan, stop, show, teleport), operators only; the Lab world photographed; still to do: settings (who may scan, ranges), see-through boxes |
| 4 | Frame scan (client): 26.3's attribution prototyped first (DESIGN §5.3) | to do |
| 5 | Chunk inspector: tickets and owners, the map, the world view | to do |
| 6 | The Lab world and its photographers; Sodium, Iris and shader packs, Distant Horizons checks; export | the Lab built (2026-11-14); the rest to do |
| 7 | The ports: NeoForge 1.21.1 from main, Forge 1.20.1 from 1.21.1 | to do |
| 8 | The first release: a jar per version with changelogs, the CurseForge page, real-world pictures | to do |
