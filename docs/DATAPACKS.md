# Village Life – Data Pack Guide

Village Life reads much of its content from data packs, so you can add or change it without writing code:
professions, market goods, daily schedules, building requirements, stage rewards and a few block/item tags.

This guide is for Village Life **1.0** (Minecraft Java 26.3, NeoForge). Everything here is checked against the
mod's code. If a file contains an error, the game log names the file and that file is skipped – the rest keeps
working.

- [Basics](#basics)
- [Professions](#professions)
- [Market goods](#market-goods)
- [Daily schedules](#daily-schedules)
- [Building requirements](#building-requirements)
- [Stage rewards](#stage-rewards)
- [Tags](#tags)
- [Texts (resource pack)](#texts-resource-pack)
- [What needs code](#what-needs-code)
- [For mod developers](#for-mod-developers)

---

## Basics

A data pack is a folder (or zip) in `<world>/datapacks/` with a `pack.mcmeta` and a `data/` folder. Use your
own namespace for new things (`data/mypack/...`). To **change** a built-in file, put a file with the same path
into the `villagelife` namespace (`data/villagelife/profession/farmer.json`) – it replaces the built-in one.

```
my_village_pack/
├── pack.mcmeta
└── data/
    ├── mypack/
    │   ├── profession/potter.json
    │   └── market_good/flower_pot.json
    └── villagelife/
        └── market_good/bread.json        (replaces the built-in bread offer)
```

After changing files, run `/reload` in the world. All ids are normal Minecraft ids such as `minecraft:bread`.

---

## Professions

`data/<namespace>/profession/<name>.json` – the file name is the profession id (`mypack:potter`).

A **workplace** is a registered building (building marker set to "Workplace") with a workstation inside.
The workstation decides the profession: a workplace with a block listed in `workstations` employs this
profession. The workstation block must also be in the tag `villagelife:workstations` (see [Tags](#tags)),
otherwise the building scan does not count it as a workstation.

| Field | Type | Default | Meaning |
|---|---|---|---|
| `workstations` | list of block ids | **required** | Blocks that make a workplace employ this profession |
| `schedule` | schedule id | `villagelife:default` | Daily plan of workers (see [Daily schedules](#daily-schedules)) |
| `work_radius` | 4–64 | 24 | How far from the workplace the worker looks for work |
| `tool` | item id | – | Basic tool, shown in the village window |
| `materials` | list of item ids | `[]` | What the worker fetches from storage (shown in the window) |
| `products` | list of item ids | `[]` | What the worker delivers (shown in the window) |
| `nearby` | list of item ids | `[]` | What must be near the workplace (shown in the window) |
| `work_area` | true/false | false | Needs a work area drawn on the map (only used by built-in herders and miners) |
| `order` | number | 100 | Position in lists (lower = earlier) |
| `essential` | true/false | false | Basic profession a new village needs first: highlighted, top of the default job priorities |
| `defender` | true/false | false | Counts as a guard for village stages and raids |
| `crafts` | list of crafts | `[]` | Recipes – see below |

`tool`, `materials`, `products` and `nearby` only **describe** the profession in the village window. What a
worker really does comes from its crafts or from built-in behavior (see [What needs code](#what-needs-code)).

### Crafts – professions that work without code

Any profession with at least one craft becomes a working craftsman: the worker fetches the inputs from the
village storage, works at its workstation and brings the output back to storage. It only crafts while the
village holds fewer than `stock` of the output.

| Field | Type | Default | Meaning |
|---|---|---|---|
| `inputs` | list of stacks | **required** | What one craft uses up. `item` may be an item tag starting with `#` |
| `output` | stack | **required** | What one craft makes (no tags) |
| `stock` | 1–4096 | 64 | Stop crafting while the village has at least this many of the output |
| `ticks` | 1–1200 | 40 | Time one craft takes (20 ticks = 1 second) |

A stack is `{"item": "<id or #tag>", "count": 1–64}`; `count` defaults to 1.

**Example – a potter** working at a cauldron (already in the workstation tag and not used by a built-in profession):

```json
{
  "workstations": ["minecraft:cauldron"],
  "work_radius": 16,
  "materials": ["minecraft:brick"],
  "products": ["minecraft:flower_pot"],
  "order": 95,
  "crafts": [
    {
      "inputs": [{ "item": "minecraft:brick", "count": 3 }],
      "output": { "item": "minecraft:flower_pot", "count": 1 },
      "stock": 16,
      "ticks": 60
    }
  ]
}
```

> One workstation block should belong to one profession only. Taken by built-in professions: crafting table
> (lumberjack), fletching table (carpenter), furnace (baker), smoker (cook), smithing table (smith), composter
> (farmer), hay block (breeder), loom (shepherd), barrel (fisherman), stonecutter (miner), lectern
> (storekeeper), bookshelf (teacher), brewing stand (innkeeper), cartography table (merchant), grindstone
> (guard), target (archer), bell (captain). The smith also uses a blast furnace near its workplace. Free in the
> tag: anvils, cauldron.

---

## Market goods

`data/<namespace>/market_good/<name>.json` – one good that players can buy from and sell to the village
market (the merchant at the market).

| Field | Type | Default | Meaning |
|---|---|---|---|
| `item` | item id | **required** | The good |
| `count` | 1–64 | 1 | Items per bundle |
| `emeralds` | 1–64 | 1 | Price of one bundle at normal supply |
| `target` | 0–4096 | 32 | How many the village wants to keep in storage |

The village **sells** only what it has above `target` and **buys** only up to `target`. The price rises when
stock is low (below half of the target: ×1.5, below the target: ×1.2) and falls when stock is high (more than
twice the target: ×0.7). Prices are rounded and never below 1 emerald.

```json
{
  "item": "minecraft:flower_pot",
  "count": 4,
  "emeralds": 1,
  "target": 16
}
```

---

## Daily schedules

`data/<namespace>/schedule/<name>.json` – a daily plan, referenced by professions via `schedule`.

```json
{
  "entries": [
    { "from": 6, "activity": "work" },
    { "from": 12, "activity": "meal" },
    { "from": 13, "activity": "work" },
    { "from": 18, "activity": "leisure" },
    { "from": 21, "activity": "sleep" }
  ]
}
```

- `from`: full hour 0–23 (6 = 6:00, the Minecraft morning). No two entries may start at the same hour.
- `activity`: `sleep`, `home` (stay near the own bed), `work`, `meal` (meet at the village center), `leisure`
  (free time: friends, tavern, school for children).
- The day wraps around: before the first entry, the last entry of the previous day still applies.

The built-in plan is `villagelife:default` (work 6–18, free time until 21, then sleep). Note: when a village
owner changes the working hours in the village window, residents on the **default** plan follow those hours;
residents with their own plan keep it.

---

## Building requirements

`data/villagelife/building_type/<type>.json` – the minimum a building marker's room must have. Only the
five built-in types exist: `house`, `storage`, `workplace`, `field`, `pasture`. You can change their
requirements, but not add new types.

| Field | Default | Meaning |
|---|---|---|
| `min_volume` | 1 | Interior size in blocks; for `field` and `pasture` the area in blocks |
| `min_doors` | 0 | Doors |
| `min_beds` | 0 | Beds |
| `min_containers` | 0 | Storage containers (tag `villagelife:storage_containers`) |
| `min_workstations` | 0 | Workstations (tag `villagelife:workstations`) |

Built-in values: house 12 volume, 1 door, 1 bed · storage 8 volume, 1 door, 1 container · workplace 8 volume,
1 door, 1 workstation · field 9 · pasture 16. Example – houses need at least 20 blocks of room:

```json
{
  "min_volume": 20,
  "min_doors": 1,
  "min_beds": 1
}
```

---

## Stage rewards

`data/<namespace>/stage_reward/<name>.json` – the supply parcel a village receives once when it rises to a
stage (`1` = village, `2` = town). The items go into the village storage; what does not fit lands at the
village center.

```json
{
  "stage": 1,
  "items": [
    { "item": "minecraft:bread", "count": 32 },
    { "item": "minecraft:emerald", "count": 10 }
  ]
}
```

`count` may be 1–1024 (split into stacks automatically). Replace `villagelife:stage_reward/village.json` or
`town.json` to change the built-in parcels.

---

## Tags

| Tag | Meaning |
|---|---|
| `villagelife:workstations` (block) | Blocks that count as workstations in a workplace. Add your profession's workstation here |
| `villagelife:storage_containers` (block) | Blocks that count as storage (chests, barrels; also `#c:chests`, `#c:barrels`) |
| `villagelife:archer_weapons` (item) | Weapons archers take from storage (default: bow) |

Tags merge with the built-in ones unless you set `"replace": true`:

```json
{
  "values": ["minecraft:decorated_pot"]
}
```

(`data/villagelife/tags/block/workstations.json` in your pack.)

---

## Texts (resource pack)

Names come from language files in a **resource pack** (`assets/<namespace>/lang/en_us.json`, `de_de.json`,
…). For a profession `mypack:potter`:

```json
{
  "mypack.profession.potter": "Potter",
  "mypack.profession.potter.tip": "Makes flower pots from bricks.",
  "mypack.workplace.potter": "Pottery"
}
```

Without these the window shows the raw keys.

---

## What needs code

Data packs cannot (yet):

- give a profession special behavior other than crafting – farming, chopping trees, mining, fishing,
  herding, guarding, cooking in a smoker, serving in the tavern and so on are built into the mod and tied to
  the built-in profession ids;
- add new building types or new activities;
- change residents' names or looks;
- change balance values – those are in the config files (`config/villagelife-*.toml`, also in the in-game
  mod settings).

---

## For mod developers

Village Life 1.0 has a small, stable API in the package `io.github.confidesconch.villagelife.api`:

- **Events** on `NeoForge.EVENT_BUS` (server side, fired after the change, not cancellable):
  `VillageFoundedEvent`, `VillageDissolvedEvent`, `ResidentJoinedVillageEvent` (newcomer or born),
  `ResidentDiedEvent`, `BuildingRegisteredEvent`, `BuildingRemovedEvent`, `VillageStageChangedEvent`.
- **Lookups** via `VillageLifeApi`: `villageAt(level, pos)`, `village(level, id)`, `villages(level)`,
  `villageOfResident(level, residentId)` – read-only snapshots of villages, residents and buildings.

```java
@SubscribeEvent
public static void onJoin(ResidentJoinedVillageEvent event) {
    VillageLifeApi.village(event.getLevel(), event.getVillageId())
            .ifPresent(v -> LOGGER.info("{} now has {} residents", v.name().getString(), v.residents().size()));
}
```

Everything outside the `api` package is internal and may change in any version.
