# Village Life

> A Minecraft mod where you **build your village yourself** – and residents **live** in it.

**Version:** 1.0.0 · **Minecraft Java Edition 26.3** with **NeoForge** · **License:** LGPL-3.0

---

## The idea

Build houses, workshops, farms and storehouses exactly the way you like – with normal Minecraft blocks, no
blueprints. Hang a **building marker** on the wall like a sign and tell the game what the building is for. Residents move in,
find work, follow a daily routine, make friends, start families – and turn your buildings into a living village.

## Features

**Free building**
- Village center block founds a village; building markers register houses, storehouses, workplaces, fenced
  fields and pastures. Rooms, doors, beds, chests and workstations are detected automatically, rebuilds are
  noticed, multi-storey buildings work.
- Clear feedback when a building is not valid, and a marker that glows purple when it is.

**Residents**
- Women and men with their own looks and work clothes per profession, names, needs (food, sleep, mood) and a
  daily routine: work, lunch, free time, sleep.
- They walk, open doors and gates, climb ladders, eat from storage and sleep in their own beds. Newcomers
  arrive when there is room and bring provisions.

**17 professions** – each with a vanilla workstation
- Lumberjack, farmer, fisherman, shepherd, breeder, miner, cook, smith, baker, carpenter, storekeeper,
  merchant, innkeeper, teacher, guard, archer, captain.
- Production chains: craftsmen work until a stock level is reached, the storekeeper moves materials and
  products, the smith smelts, forges and repairs tools.

**Economy and trade**
- Market with emeralds and supply-and-demand prices, visiting trade caravans, a village treasury.
- **Trade contracts between villages** – also between different players – with deliveries on the road,
  escorts and bandit ambushes.

**Community**
- Friendships and quarrels, couples, children who grow up and go to school.
- A tavern that turns into a bar in the evening: guests sit on chairs and benches around tables.

**Growth, laws and defence**
- Settlement → village → town with more residents, a larger border and a supply parcel.
- Village laws (rest day, curfew, long evenings), job priorities, co-managers and plots for friends.
- Guards, archers and a captain, an alarm bell, bandit raids that really plunder.

**Village window** (key **B**)
- Overview with problems and events, residents, jobs, buildings, economy, a parchment-style map, defence,
  administration and your character – everything in one place.

**Multiplayer** and dedicated servers are supported from the start. 100+ residents per village run without
noticeable lag.

## Installation

1. Install Minecraft Java Edition **26.3** with **NeoForge 26.3.0.100** or newer (26.3.x).
2. Put `villagelife-26.3-1.0.0.jar` into the `mods` folder – on the server **and** on every player's game.
3. Start the game. The village center and building markers are in the creative tab "Village Life" and can be
   crafted in survival:
   - **Village center:** planks in the corners, cobblestone (or other stone) on the sides, a book in the middle.
   - **Building marker (2):** planks + stick + paper (shapeless).

## Quick start

1. Place the **village center** on open ground – your village is founded.
2. Build a house with a door, a roof and beds. Hang a **building marker** on its wall – outside next to or
   above the door works fine – and right-click it until it shows "House".
3. Build a storehouse with a chest, put some food (e.g. bread) inside, give it a marker set to "Storage".
4. The first resident soon moves in. For work: a room with a workstation, e.g. a composter for a farmer, with
   a marker set to "Workplace", and a fenced field with a marker on its fence set to "Field".
5. Press **B** to open the village window – you see and control everything there.

For a quick look in a flat creative world with cheats: `/villagelife debug testvillage` builds a complete
village, `/villagelife debug spawnresident 20` fills it.

## Customizing

Professions (with crafting recipes), market goods, daily schedules, building requirements and stage rewards
come from data packs – see [`docs/DATAPACKS.md`](docs/DATAPACKS.md). Balance values (village radius, hunger,
raids, …) are in the mod settings (`config/villagelife-*.toml`, also in the in-game mod list).

**For mod developers:** a small, stable API with events and read-only lookups – also described in
[`docs/DATAPACKS.md`](docs/DATAPACKS.md#for-mod-developers).

## Bugs and ideas

Please report them on the [issue tracker](https://github.com/ConfidesConch/village-life/issues). Tell us the
Minecraft, NeoForge and Village Life versions and attach `logs/latest.log` if possible.

## Inspiration

Village Life takes **ideas** – not code or graphics – from MineColonies and TekTopia. Everything is developed
independently.

## License

Village Life is free software under the [GNU Lesser General Public License v3.0](LICENSE). You may use it in
modpacks, and you may use, change and share its code as long as changes to Village Life itself stay under the
same license.
