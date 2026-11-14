# Ideas for the next mod: a utility ("useful") mod

Research of 2026-11-14, after Showpiece was shelved. The user's brief: "one of those "useful" mods. By that I mean the
type of mod thats adds maybe a button on the recipe menu to switch between recipe conflicts, or the most famous one
being JEI/EMI, and also WAILA/Jade. I'd like you to research what has already been to as to not make a duplicate
(unless the prior one was of poor quality/lacked the full coverage that should be given by such a mod), and then
provide a LARGE list of possible ideas for what the mod can be. This is the kind of mod that doesnt add items or blocks
in most circumstances." Then: "Other examples include: Mouse tweaks, appleskin, clumps."

## How it was checked

- **Modrinth** (its public search API): the top 490 mods by downloads in Utility, Management, Social, Optimization and
  Game Mechanics, and about 180 searches for the ideas below, each hit with its downloads, loaders, Minecraft versions
  and last update.
- **CurseForge** (its site search): about 40 searches for the ideas most likely to be open, since many Forge and
  NeoForge mods are only there.
- **The web**: wish lists, the state of the recipe viewers on 26.x, recipe syncing since 1.21.2.
- The versions that matter are ours: **NeoForge 26.3, NeoForge 1.21.1 and Forge 1.20.1**.

Downloads are written "M" for Modrinth and "CF" for CurseForge. Status:

- **Open**: nothing usable on NeoForge or Forge for our versions, or only attempts with a few thousand downloads.
- **Thin**: something exists but is stale, Fabric-only, missing versions (often 26.x), or split across many small mods
  with no good one.
- **Taken**: a maintained, well-made mod already does the job on our loaders and versions. Making another would be a
  duplicate.

## What the field looks like in November 2026

- On 26.3 the recipe viewers are JEI (85.6M M) and REI (58.4M CF), plus the newer Reliable Recipe Viewer (946k M,
  26.x only). **EMI officially stops at 1.21.1** (last update 2026-05); 26.x has only community ports (SEMI, an
  "unofficial port"). EMI's add-ons (EMI Loot, EMI Ores, EMI Enchanting) stop there too.
- **Polymorph officially stops at 1.21.1** (2026-09); 26.x has only third-party ports (Polymorph+, 58k M).
- Jade, AppleSkin, Mouse Tweaks and Clumps are all on 26.3 and well kept.
- **Create's latest is 1.21.1** (2026-04): Create support can only be tested on 1.21.1 and 1.20.1 for now.
- Masa's mods (MiniHUD, Tweakeroo, Litematica) are **Fabric-only**; NeoForge has only unofficial ports for 1.21.1
  (BoccHUD, Tweakerge, Forgematica). Many technical-player tools follow them: Carpet, ChunkDebug, Redstone Multimeter.
- Since 1.21.2 recipes stay on the server: client-only recipe tools see little on a server without the mod.
- The biggest abandoned utility found: **Observable** (lag finder, 51.2M CF), last updated 2025-06, stops at 1.21.4.
  (Re-checked when the user chose it: young, small successors exist on one or a few versions each; see DESIGN.md §3.)

## Shortlist

| Pick | Idea | Why it stands out |
|---|---|---|
| **1 (recommended; chosen, now Culprit)** | **Performance inspector** (ideas 112, 113, 114, 63): what is lagging the server, what is eating FPS, and what keeps chunks loaded, shown on the world itself with teleports and per-mod totals | The largest proven demand of any open or thin idea (Observable's 51.2M CF), its leader abandoned; nobody shows FPS cost by place or chunk loading on the world on NeoForge/Forge, and no tool covers 26.3, 1.21.1 and 1.20.1. It is the frame-rate and tick-time side the user cares about most. Server and client, no items, zero cost while idle. |
| 2 | **Death recap** (71, 72): the last seconds before death, every hit with its source, raw damage and what armour, enchantments and effects took off, what you lost and where | Open on every version and loader we target; every player understands it at once; small enough to polish to a very high bar. |
| 3 | **Crafting planner** (2, 3, 4): one button on the recipe screen: the whole tree, raw materials, what you already own, a shopping list that ticks off | A real need (a dozen small attempts, AE2's crafting tree at 26.9M CF), but no good one, and JEI users on 26.x have no tree at all while EMI is missing. Crowded and the biggest build. |
| 4 | **Technical overlays for NeoForge/Forge** (49-58, 65): light and spawnable spots, slime chunks, spawning sphere, ranges, structure boxes, villager links, mob caps, farm rates | Everything MiniHUD and Carpet show is missing natively on NeoForge and Forge. |
| 5 | **Storage memory** (26, 27, 42, 43): remember every container opened, search them, totals | Chest Tracker's NeoForge and Forge versions are small unofficial ports. |
| 6 | **Item facts tooltips** (46, 36) | Spread over a dozen small mods; Equipment Compare (51.1M CF) has no 26.x. |
| 7 | **Pack-maker toolkit** (131-133, 138) | Open, but for pack makers only. |
| 8 | **Colour-blind help** (74) | Open (only simulators exist); small audience. |

## The full list (138 ideas)

### A. Recipes and crafting

| # | Idea | Status | What exists now |
|---|---|---|---|
| 1 | Recipe-conflict picker (a button to choose between recipes that share a grid) | Taken (thin on 26.x) | Polymorph (26.6M M, 233.6M CF) to 1.21.1; on 26.x only third-party ports (Polymorph+) |
| 2 | Crafting planner: an item and an amount give the whole tree, raw materials, what you own and a shopping list | Thin | A dozen small attempts, none above about 50k: JEI Recipe Pins (51k CF), Just Enough Crafting Tree (33k CF, Fabric 1.21.1), JEI Crafting Tree (2.5k CF), CraftAnyway, Quick Craft, JEI Enhancements, Just Enough Calculation EX, How Much Do I Actually Need (Fabric); EMI's own tree (EMI not on 26.x). None covers JEI, REI and EMI on all three versions with storage and machine recipes |
| 3 | A pinned recipe on the HUD with have/need counts | Thin | JEI Recipe Pins (51k CF, NeoForge 26.1.2), Put A Pin In That, EMI Recipe Pin, PinIt, CraftPin; all small |
| 4 | "What can I craft now" filter for JEI | Open (JEI only) | REI and EMI have one built in; no JEI add-on found |
| 5 | Craft from nearby chests, without a station block | Thin | Craft From Chests (2k, Fabric), Quick Craft (1k), QuickStack & Craft (2.9k CF, NeoForge 1.21.4), CraftReach (Fabric); Proximity Crafting is a block |
| 6 | Recipes on vanilla servers (since 1.21.2 recipes stay on the server) | Taken | JEIRecipeFix, JEI Recipe Bridge, Just Enough Serverless Recipes, Client Recipe Fix |
| 7 | Loot tables in the recipe viewer (chests, mobs, blocks, fishing, archaeology) | Taken | Advanced Loot Info (8.1M M, 16.4M CF; JEI, REI and EMI; 26.3, 1.21.1, 1.20.1), EMI Loot, Just Enough Resources |
| 8 | Villager trades in the recipe viewer | Taken | Advanced Loot Info, Just Enough Professions (5.1M M), JEI Trade (81k CF) |
| 9 | Ore and worldgen distribution | Taken | Just Enough Resources (7.2M M, 246.6M CF, now on 26.3), JEI/REI/EMI WorldGen (79k M), EMI Ores |
| 10 | Breeding and status-effect pages | Taken | Just Enough Breeding (8.5M M), Just Enough Effect Descriptions (9.6M M) |
| 11 | Mob spawning rules (biomes, light, height, group sizes, structures) | Thin | Parts in Just Enough Resources and Araxer's Bestiary (84k M) |
| 12 | Enchantment pages (what takes it, levels, conflicts, where to get it) | Thin | Enchantment Descriptions (40.1M M) in tooltips; EMI Enchanting (9.2M M) is EMI-only, to 1.21.1 |
| 13 | Enchanting-table preview | Thin | Small Fabric mods (about 1k); needs a server part to be fair |
| 14 | Brewing guide at the stand | Thin | Brewing Guide Info (12k, Fabric), Potion Guide Vanilla (8k); the viewers already show brewing |
| 15 | Anvil costs, no "Too Expensive" | Taken | Easy Anvils (17.7M M), Anvil Never Too Expensive (1.9M M) |
| 16 | Armour-trim preview at the smithing table | Open | Trim Preview (151, Forge 1.20.6), Legion (2k, Fabric) |
| 17 | Stonecutter and loom search and memory | Thin | Finally Useful Stonecutter (828), Loom GUI Remastered (14k, Fabric) |
| 18 | Recipe history and back | Taken | Built into the viewers; JEI history add-ons stop at 1.18 |
| 19 | Undo or repeat the last craft | Thin | Undo Craft (2k), CraftRecall (173) |
| 20 | A better vanilla recipe book | Thin | Better Recipe Book (5.7M M, stops at 1.21.1, last 2024-07), Recipe Book is Pain (560k M) |
| 21 | Ore and ingot unification | Taken | Almost Unified (3.3M M, to 1.21.1) |
| 22 | A new recipe viewer | Taken | JEI and REI on 26.3, Reliable Recipe Viewer (26.x); EMI to 1.21.1 plus community ports |
| 23 | Export recipes and items to images, JSON or wiki pages | Thin | Workstation Recipe Exporter (1.4M M, Forge 1.20.1), RecipeExport (392) |

### B. Inventory and containers

| # | Idea | Status | What exists now |
|---|---|---|---|
| 24 | Sorting | Taken | Inventory Profiles Next (42.4M M), Inventory Sorting (3.7M M), Nemo's (1.5M M), Quark |
| 25 | Quick stack to nearby chests | Taken | Quickstack (6.4M CF, NeoForge 1.21.1), Sorted (28k M, 26.3), Quickstack Reborn (8.6k CF, NeoForge 26.1.2) |
| 26 | "Where did I put it": remember every container opened, search them, light up the chest | Thin | Chest Tracker (999k M, Fabric, official stops at 1.21.4) and unofficial ports (Fabric 26.3; NeoForge 9k; Forge 1.20.1 17k CF); Where Is It (382k CF, stops at 1.21.4); Sorted (new) |
| 27 | A search bar and highlight in any container | Thin | Sophisticated Inventory Interactions (639k CF, NeoForge 1.21.1), Inventory Search (16k, Fabric) |
| 28 | Shulker and bundle previews | Taken | Shulker Box Tooltip (41.4M M) |
| 29 | Locked or favourite slots | Taken | Inventory Profiles Next, Locked In Slots (8k) |
| 30 | Trash slot | Taken | TrashSlot (6.7M M) |
| 31 | Hotbar row swap | Thin | Hotbar Swapper (26k, stops at 1.21.5), Tweakeroo (Fabric) |
| 32 | Auto-refill and tool swap | Taken | Inventory Profiles Next, Inventive Inventory (150k), Tweakeroo |
| 33 | Gear loadouts (save a full set, swap with one key) | Thin | Part of Inventory Profiles Next; Client Kits+ (9k, Fabric) |
| 34 | Mouse drag tweaks | Taken | Mouse Tweaks (64.6M M), More Mouse Tweaks (532k M) |
| 35 | Crafting grid rotate, balance, clear | Taken | Crafting Tweaks (10.7M M) |
| 36 | Equipment comparison tooltips | Thin on 26.x | Equipment Compare (51.1M CF, last 2025-04, stops at 1.21.1) |
| 37 | Pick-up filter (never pick up X) | Thin | Auto Pickup Filter (1k), Pickup Filter (Fabric) |
| 38 | Drop protection | Taken | DropConfirm (1.5M M) |
| 39 | Pick-up notices, full-inventory warning | Taken | Pick Up Notifier (16.7M M), Item Highlighter (17.9M M) |
| 40 | Smart pick-block from shulkers and backpacks | Open | Nothing above a few hundred downloads |
| 41 | Labels over named containers | Thin | Storage Container Labels (1k, Fabric), ChestLabels (331) |
| 42 | Storage totals: how much of everything you own | Open | StorageEye (491, Fabric) |
| 43 | Total count of the held item across inventory and backpacks | Open | Tiny ones (ICNT, ItemCounter) |

### C. Information and HUD (the Jade and AppleSkin family)

| # | Idea | Status | What exists now |
|---|---|---|---|
| 44 | Look-at information | Taken | Jade (74M M) and Jade Addons (6M M), WTHIT, The One Probe |
| 45 | Food numbers | Taken | AppleSkin (99M M), Food Effect Tooltips (1.9M M), Effect Insights (1M M) |
| 46 | One complete item-facts tooltip: fuel time, compost chance, repair material, tool tier and speed, damage per second, armour reduction, enchantability, recipe uses | Thin | Spread over many: Durability Tooltip (15.2M M), Tag Tooltips (85k), Inline Tooltips (21k), Tooltips TXF (30k), Better Advanced Tooltips (141k, NeoForge) |
| 47 | Mob health bars | Taken | Neat (10.8M M), TslatEntityStatus (8.3M M) |
| 48 | Damage numbers | Taken | JeremySeq's Damage Indicator (629k M), ToroHealth |
| 49 | Light-level and spawnable-spot overlay | Thin on NeoForge | Light Overlay (616k M) is Fabric-only on 26.x, its all-loader build stops at 1.21.10; NeoForge 26.3 has LightLevelOverlay (4.8k CF), Light Level Simple (3.4k CF) |
| 50 | Slime-chunk finder | Thin | Slime Chunk Finder (17.6k CF, Fabric, stops at 1.21.1), a 26.3 Fabric one (4.4k CF) |
| 51 | Spawning (AFK) sphere for mob farms | Open on NeoForge/Forge | Only MiniHUD (Fabric) |
| 52 | Ranges: beacon, conduit, spawner, bell, raid | Thin | Range View (208 CF, NeoForge 26.2), Beacon Range Continued (270 CF, Fabric) |
| 53 | Structure boxes (witch huts, monuments, fortress zones) | Open on NeoForge/Forge | Only MiniHUD (Fabric) |
| 54 | Villager links: bed, workstation, who claimed what | Open on NeoForge/Forge | Villager Workstation Link (619, Fabric), Workstationfinder (221 CF, Fabric) |
| 55 | Mob caps and spawn attempts | Open on NeoForge/Forge | Only MiniHUD (Fabric) |
| 56 | Redstone multimeter | Thin on NeoForge/Forge | Redstone Multimeter Fabric (46k M); a 67-download NeoForge one |
| 57 | Farm rates (items per hour) | Open | Only Carpet's hopper counters (Fabric) |
| 58 | Info lines: coordinates, biome, light, day, chunk, TPS | Thin on NeoForge | BetterF3 (36.5M M) for F3; GUI Compass (654k M), Coordinates Display (601k M); MiniHUD's lines are Fabric-only |
| 59 | Status-effect timers | Taken | Effect Timer Plus (2.2M M), GUI Effect Timer (189k M) |
| 60 | Armour and durability HUD | Taken | uku's Armor HUD (10.2M M), Armor Hud (1.4M M) |
| 61 | Biome and dimension titles | Taken | Traveler's Titles (9.5M M) |
| 62 | Structure name and its mod when you enter | Thin | Structure Credits (1.3M M, stops at 1.21.1, last 2025-06) |
| 63 | Server TPS and MSPT on screen | Thin on NeoForge | TPS HUD (517k M, Fabric) |
| 64 | Horse stats | Taken | Horse Stats Vanilla (388k M), Jade |
| 65 | Raid information: wave, raiders left, where they are | Open | None found |
| 66 | Elytra flight HUD | Thin | Fabric-only, mostly stale |
| 67 | Despawn timers over dropped items | Thin | Item Despawn Timer (13k, stops at 1.21.1) |
| 68 | Directional sound indicators (for deaf and hard-of-hearing players) | Thin | Sound Visualizer (6k M, all loaders), Better Subtitles (Fabric) |
| 69 | Advancement tracker on the HUD | Thin | Advancements Tracker and Overview (1.1M CF, last 2024-04, 1.20.1), small Fabric ones |
| 70 | Character sheet: every attribute, vanilla and modded, and where each comes from | Thin | Apothic Attributes has a screen (in Apotheosis packs); standalone ones are tiny (Player Attribute Screen 1.5k CF) |
| 71 | Death recap: the last seconds before death, every hit and what armour took off, what you lost and where | Open | Death Recap (541, Fabric), DeathLesson (44 CF, Forge 1.20.1), Death Damage Recap (42 CF, 1.12.2) |
| 72 | Damage-per-second meter and combat log | Thin | Epic RPG: Average DPS (12k), small others |
| 73 | Session stats and loot journal | Thin | Loot Journal (2k), Session Stats (124) |
| 74 | Colour-blind help: a correction filter, marked colours on wool, dyes, glass, redstone and teams | Open | Only simulators (ColorBlindness, 1.5M CF, simulates); Runics Colorblind Aid (90 CF), ColorblindHelper (1.12.2) |

### D. Navigation and the world

| # | Idea | Status | What exists now |
|---|---|---|---|
| 75 | Maps and waypoints | Taken | Xaero's Minimap (120.5M M), JourneyMap, FTB Chunks |
| 76 | Death waypoint | Taken | Death Coordinates and others; the recovery compass |
| 77 | Pet and mount locator | Thin | Pets Locator (7.9k CF, NeoForge 26.1.2) |
| 78 | Nether-portal helper: where a portal will link, linked coordinates | Thin | Nether Portal Coordinate Scale (10k), NetherSearch (3k), Slash Portal (20k, Fabric) |
| 79 | Breadcrumb trail home | Thin | Breadcrumbs (2k, Fabric) |
| 80 | Structure and biome finder | Taken | Explorer's Compass (12.6M M), Nature's Compass (27.6M M) (both items) |
| 81 | Block and entity radar | Thin | Locate Plus (380) |
| 82 | Waypoints shared across map mods | Thin | Tiny Fabric ones |

### E. Building helpers

| # | Idea | Status | What exists now |
|---|---|---|---|
| 83 | Schematic projection (the Litematica kind) | Thin on NeoForge/Forge | Litematica (28M M) is Fabric; unofficial Forgematica (1.21.1); Create's schematics need Create. A very large project |
| 84 | Measuring tape | Thin | RulerMod (1k, Fabric), AxisRuler (1k) |
| 85 | Shape guides (circles, spheres) | Taken | Build Guide (1.7M M, all loaders, 26.3) |
| 86 | Symmetry and arrays | Taken | Effortless Building (1.6M M) |
| 87 | Accurate placement | Taken | Pro Placer (2.2M M), Accurate Block Placement Reborn (4.3M M, Fabric) |
| 88 | Block palette and gradient picker | Thin | Block Palettes (3k), BlockPalette Studio (2.4k CF, Fabric), Palettinator (4k) |
| 89 | WorldEdit interface | Taken | WorldEdit and its CUI; Axiom (Fabric) |

### F. Menus, controls and settings

| # | Idea | Status | What exists now |
|---|---|---|---|
| 90 | Keybind search and conflicts | Taken | Controlling (42.5M M) |
| 91 | Keybind profiles and key combinations | Thin | Keybind Profiles (1k), Keybindery (Fabric) |
| 92 | Keybind cheat-sheet overlay | Taken | Keybind Atlas (303k M, all loaders, 26.3) |
| 93 | Command palette: one key, then type anything (settings, keybinds, items, commands, worlds) | Thin | Butler (528 CF, NeoForge 1.21.1), Command Deck (395 CF), Limelight (Fabric) |
| 94 | Search every mod's config | Thin | Config Search (4k) |
| 95 | Option profiles | Thin on 26.x | Options Profiles (104k M, stops at 1.21.11) |
| 96 | World folders | Thin | World Folders (10k, stops at 1.21.8); Cherished Worlds (34.1M M) only pins |
| 97 | Resource-pack folders | Thin | Organize (new, a few hundred), Resource Tree (2k, Fabric) |
| 98 | Screenshot browser | Taken | Screenshot Viewer (4M M) |
| 99 | Toast control | Taken | Toast Control (457k M) |
| 100 | GUI scale per element | Thin | GUI Scale Splitter (108k, Fabric), Precise GUI Scale |
| 101 | In-game notes | Thin | Notely (5k), several tiny |
| 102 | To-do list with item goals that count themselves | Thin | Inventory ToDo (742 CF, Forge 26.2), TeamTasks (69k M), Todo (2k) |
| 103 | Game-rules screen for an open world | Thin | Cloth Gamerules (47k, Fabric) |
| 104 | Radial action menu | Taken | Mine Menu (51k), EZ Actions (32k) |
| 105 | Statistics and advancement screens | Taken | Better Statistics Screen (20.6M M), Better Advancements (27.8M M) |

### G. Multiplayer

| # | Idea | Status | What exists now |
|---|---|---|---|
| 106 | Ping wheel | Taken | Ping Wheel (14.7M M) |
| 107 | A safe player-to-player trading window | Thin | Player Trade - Secure Trading (2k, 26.1) |
| 108 | Party HUD: teammates' health, distance, direction | Thin | Epic RPG: Party Hud (16k), Better Party (11k) |
| 109 | Chat improvements | Taken | Chat Heads (53.1M M), Chat Tools, Chat Patches (Fabric) |

### H. Servers and performance (the Clumps family)

| # | Idea | Status | What exists now |
|---|---|---|---|
| 110 | XP orb merging | Taken | Clumps (42.9M M) |
| 111 | Dropped-item merging | Thin | Small Fabric ones; vanilla merges already |
| 112 | Lag finder: which entities, block entities and chunks cost the most tick time, on the world itself, with teleports | Thin | Observable (51.2M CF), last update 2025-06, stops at 1.21.4; young small ones found on re-checking: LagFinder (274, NeoForge/Fabric 1.21.8-26.3, server-only), LagSense (36, NeoForge 26.1.2), Observable - Remake (623, 26.1.2), LagLens (401, Forge 1.20.1); spark profiles code, not places |
| 113 | FPS finder: which block-entity renderers, entities and particles in view cost frame time | Open | None found (spark shows methods, not things in the world) |
| 114 | Chunk-loading inspector: who keeps which chunks loaded (tickets, force-loads, by mod) | Open on NeoForge/Forge | ChunkDebug (80k M) is Fabric-only; Why Loaded (6 CF, NeoForge 1.21.1, commands only) found on re-checking |
| 115 | Backups | Taken | Simple Backups (1.1M M), FTB Backups |
| 116 | Pre-generation | Taken | Chunky (20.3M M) |
| 117 | Scheduled restarts with warnings | Thin | Small Fabric ones |
| 118 | Item clearing with warnings | Thin | TCT ClearLag (17k), ItemClearLag (56k) |
| 119 | Crash screens | Taken | Crash Assistant (21.6M M), Not Enough Crashes (16.4M M) |
| 120 | Admin inventory viewer | Thin | Small ones |
| 121 | Sleep percentage | Taken | The vanilla game rule; Easier Sleeping (282k M) |
| 122 | Mob despawn tweaks | Taken | Let Me Despawn (25.6M M) |

### I. Gameplay helpers (no items)

| # | Idea | Status | What exists now |
|---|---|---|---|
| 123 | Trade cycling | Taken | Trade Cycling (7M M) |
| 124 | Vein mining | Taken | VeinMiner (103.3M M), FTB Ultimine |
| 125 | Right-click harvest | Taken | RightClickHarvest (13.3M M) |
| 126 | Double doors | Taken | Double Doors (9.5M M) |
| 127 | Elytra swap | Taken | Elytra Swapper (299k M) |
| 128 | Auto-replant | Thin | Tiny ones |
| 129 | Fishing helper | Thin | Fabric-only |
| 130 | Easier anvils and enchanting tables | Taken | Easy Anvils (17.7M M), Easy Magic (14.5M M) |

### J. Pack-maker tools

| # | Idea | Status | What exists now |
|---|---|---|---|
| 131 | Recipe-conflict and duplicate report: every clash in every grid, with fixes | Open | None found |
| 132 | Data browser: every recipe, loot table, tag, worldgen piece and advancement, which mod or datapack set it, what overrides it | Open | Datapack Viewer (21), Datapack Error Viewer (2k, Fabric) |
| 133 | Unobtainable-item report | Open | None (RecipeDumper is stale) |
| 134 | Modpack update checker | Taken | Modpack Update Checker (5.5M M) |
| 135 | Default options | Taken | Default Options (19.3M M) |
| 136 | Multiblock projection | Thin | Patchouli and GuideME in books; Building Blueprint (44k) |
| 137 | Guide books | Taken | Patchouli (37.1M M), GuideME |
| 138 | Startup-time profiler by mod | Open | Nothing dedicated found |
