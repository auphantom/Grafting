# Grafting

A standalone Paper plugin that turns **Reassembly / Grafting**, the Sequence 1 *Attendant of Mysteries* power from the Fool pathway (*Lord of the Mysteries*), into something you can actually use in Minecraft.

> *"Everything can be connected, if you know where to make the cut."*

In the novel, Grafting takes two things (objects, concepts, rules, even directions) that should never touch and stitches them together, so one starts behaving according to the other. This plugin builds on that: one item, thirteen abilities, a **spirit** pool that powers them, a **Spirit Body** the Attendant can shift into, and **Distance grafting** that grows through four levels the further you travel. It stays standalone, with no other plugins and no database.

![The ability icons](docs/images/icons.png)

## The idea: one thread, two ends

You hold the **Thread of Grafting**. Pick an ability, then right-click two things. The **first** is grafted **onto** the second. Order matters, just like in the books: grafting A onto B is not the same as grafting B onto A.

| Ability | First ➜ Second | Concept tampered with | What happens |
|---|---|---|---|
| **Distance** | depends on the art | distance | Four arts, unlocked by distance level (see [Distance grafting](#distance-grafting)). |
| **Fate** | Being ➜ Being | harm | Harm meant for the first being finds the second one instead, wherever it is. |
| **Nature** | Place ➜ Being | properties | The being takes on the *nature* of the block (see below). |
| **Death & Return** | Being ➜ Place | death | The being's next death is grafted onto a journey home. The killing blow never lands, and it's pulled back to the block alive. One use. |
| **Exchange** | Being ➜ Being | position | The two swap places, and swap again every time the first is struck, so the attacker suddenly faces the second. |
| **Enmity** | Being ➜ Being | hostility | Every creature hunting the first now hunts the second. Graft yourself onto a pig and the horde turns on the pig. |
| **Gravity** | Being ➜ Place | "down" | For the being, down now points at the block. It falls sideways, upward, across chasms, and never takes fall damage. |
| **Puppetry** | Being ➜ Being | movement | The second moves exactly as the first moves, on marionette strings. A nod to the Fool pathway. |
| **Supernova** | Place ➜ Anything | a star's death | The *death of a star* (any block) is grafted onto the target. A sun kindles around it and drags in everything nearby, collapses to a point, then detonates in a blinding flash and an expanding shockwave. The caster is never harmed. Once ignited it can't be stopped. |
| **Life** | Being ➜ Being | life | The first being's life now lives in the second. Kill the second and the first dies with it, wherever it is. Graft a Sequence 3 Fool's life onto a cow, kill the cow, and the Fool dies. Beyonders of the same or a higher sequence usually shake it off. |
| **Location** | Place ➜ Place | location | The areas around both blocks trade places, blocks, chests and their contents included. Graft your base onto a spot in the End and the End replaces your base until you cut the thread, then everything swaps back. Survives restarts: an open swap is undone the next time the server starts. |
| **Ability** | Being ➜ Being | power | Graft **your own** power onto another player: they get a borrowed thread locked to one of your abilities (`/graft lend <ability>`). Or graft **a creature's** power onto a player: blaze fire, ender blink, ghast fireball, creeper blast, evoker fangs, breeze gust (sneak + F), or a passive trait. Drains your spirit every second. |
| **Storage** | Place ➜ Being | storage | A chest, double chest, barrel or shulker box becomes part of the player's inventory: sneak + F (or `/graft storage`) opens it from anywhere, and anything picked up with a full inventory flows into it. |

### Natures (Place ➜ Being)

| Block | Nature | Effect on the being |
|---|---|---|
| Slime block | **Bounce** | Falls turn into (damped) bounces, so no fall damage |
| TNT | **Volatility** | Explodes the next time something strikes it (no block damage, one use) |
| Ice / snow | **Frost** | Freezes water underfoot, chills whatever it hits, immune to freezing |
| Magma, netherrack, campfire, furnace... | **Flame** | Fire immune, ignites whatever it strikes and whoever strikes it |
| Glowstone, lanterns, torches, froglights... | **Light** | Glows, sees in the dark, sets nearby undead on fire |
| Cobweb, honey, soul sand, mud | **Stickiness** | Can barely move |
| Wool, leaves, hay, scaffolding | **Feather** | Drifts instead of falling, runs light-footed |
| Prismarine, sponge, kelp... | **Water** | Breathes and swims like a fish |
| Flowers, saplings, crops, moss... | **Life** | Slowly regrows its wounds |
| Any other full solid block | **Stone** | Takes far less damage, but moves like a statue |

### Rules of the thread

* A graft is **visible**: a two-strand particle thread twists between its two ends in the ability's colour, with bright motes flowing from the first end to the second.
* A graft **snaps** as soon as either end stops existing. Break the block or kill the being and the connection is gone, so the counterplay is to find the anchor block and break it. (Supernova is the exception, because a dying star can't be saved.)
* Grafts **unravel** after a configurable time. One-shot grafts (Death & Return, Volatility, Supernova) end as soon as they're used.
* Each player keeps at most `max-active-grafts` alive. Making a new one lets go of the oldest.
* Every graft costs **spirit**: once when it is made, and some (Ability, Infinity) every second they hold.

## Spirit

Spirit is the fuel for every ability (1000 by default, regenerating 8 per second). It shows above the hotbar while you hold the thread, are in your Spirit Body, or are regenerating:

```
✦ ▮▮▮▮▮▮▮▮▮▮▮▮▮▮▯▯▯▯▯▯ 702/1000   ⟷ Lv2 ▮▮▮▯▯▯▯▯  SPIRIT BODY  -60 spirit
```

The first bar is spirit (cyan to violet, flashing red when low), the second is progress toward the next distance level. Costs show for a moment after each cast. Every cost and upkeep is in `config.yml`.

## Spirit Body

At Sequence 1 the Attendant's spirit body and main body are the same body, so they can shift between them (`/graft body`, or the soul torch in the menu, costs 30 spirit):

* creative-style **flight**,
* **no physical damage** (melee, arrows, falls, fire, explosions, drowning),
* **magic still hurts** (potions, wither, sonic booms, dragon's breath, and every graft),
* **using any ability returns you to your main body**, as acting requires it.

## Distance grafting

Distance has four arts, chosen in the menu or with `/graft art`. Each unlocks at a distance level, and your level rises with the blocks you travel through your own distance grafts (Step and Gateway). Every level also extends the arts before it.

| Level | Art | How | Reach |
|---|---|---|---|
| 1 | **Step** | Right-click a block, or type `x y z` in chat while holding the Distance thread (or `/graft step x y z [world]`). Your **next step** lands there. | 500 blocks, doubling each level |
| 2 | **Gateway** | Place ➜ Place. The distance between two blocks becomes zero: step on one, arrive on the other, both ways, for players, mobs and items. | 256 blocks, doubling each level |
| 3 | **Enemy Step** | Being ➜ Place. A being in front of you: its next step lands on the block you chose. Beyonders may resist. | 12 blocks from you (+4 at level 4) |
| 4 | **Infinity** | Right-click to raise or lower. Infinite distance around you: melee is pushed back and projectiles stop dead. Drains 25 spirit/s, plus 15 per point of damage it stops. | |

Levels are reached at 500, 2000 and 6000 blocks travelled (configurable). For testing or events, admins can set a level directly: `/graft level [player] <1-4>`.

## Controls

| Action | Effect |
|---|---|
| **Left-click** | Switch to the next ability |
| **Sneak + left-click** | Open the pathway menu |
| **Right-click** | Tie the thread to whatever you're looking at, up to 48 blocks away (block or being) |
| **Sneak + right-click** | Tie the thread to **yourself** |
| **Sneak + F** | Use a grafted creature power, or open your grafted storage |

| Command | |
|---|---|
| `/graft menu` | Open the pathway menu |
| `/graft give [player]` | Get the Thread of Grafting (op only by default) |
| `/graft mode <ability>` | Switch ability by name |
| `/graft list` | Your active grafts and their time left |
| `/graft sever [id\|all]` | Cut a graft |
| `/graft body` | Shift into or out of your Spirit Body |
| `/graft art <step\|gateway\|enemy\|infinity>` | Choose your Distance art |
| `/graft step <x> <y> <z> [world]` | Arm Step: your next step lands there |
| `/graft lend <ability>` | Which of your abilities the Ability graft lends |
| `/graft storage` | Open your grafted storage |
| `/graft level [player] [1-4]` | Show, or (admin) set, the distance level |
| `/graft spirit [player] [amount\|refill]` | Show, or (admin) set, spirit |
| `/graft sequence <player> [0-9\|none]` | Show, or (admin) set, a player's sequence for resistance rolls |
| `/graft pack` | Re-send the texture pack |

Permissions: `grafting.use` (default: everyone), `grafting.take` (draw a thread from the menu, default: everyone), `grafting.give` and `grafting.admin` (level, spirit, sequence; default: op).

Sequences only matter for resistance: players with `grafting.use` count as Sequence 1, everyone else as a non-Beyonder, unless set with `/graft sequence`.

## The pathway menu

![The pathway menu](docs/images/pathway-menu.png)

Every player carries the **Fool sigil**, the pathway's emblem, in a fixed inventory slot (top-left by default). It can't be dropped, moved or lost on death. Hovering it shows a live *Mystery Arts* readout: your sequence, spirit, distance level, threads in use, and the selected ability, with segmented bars.

Click the sigil (or right-click while holding it, sneak + left-click with the thread, or `/graft menu`) to open a plain chest menu:

* **Top rows:** the thirteen abilities, with their spirit cost. Click one to switch your thread to it (or draw a new thread if you have none).
* **Middle:** your portrait (the Mystery Arts readout), the **Spirit Body** toggle, and the four **Distance arts**. Locked arts show how far you still have to travel.
* **Bottom row:** your **active grafts**. Hover one to see both ends and its time left, click it to sever it.

The menu refreshes every second while open, so the timers stay live.

## Textures

Every ability has its own icon (shown at the top), and the thread in your hand changes to match the selected ability. The icons, the sigil, the tooltip frame and the bar font are all drawn by code (the `packgen` source set in `tools/packgen/`) at build time and packed into a resource pack inside the plugin jar. The generator itself is not shipped in the jar.

The plugin hosts that pack itself on a small built-in web server (JDK `HttpServer`, port 8164) and offers it to every player who joins, so there's nothing extra to set up. If your players reach the server through a public address, set `resource-pack.public-host`, or upload `grafting-pack.zip` anywhere and set `resource-pack.url`. Without the pack, the thread simply looks like string.

## Configuration

```yaml
max-active-grafts: 5
tie-range: 48            # how far right-click can reach
durations:               # seconds
  distance: 120
  fate: 60
  nature: 90
  return: 300
  exchange: 60
  enmity: 60
  gravity: 20
  puppet: 30
  enemy: 20
  life: 300
  location: 600
  ability: 120
  storage: 1800
max-distance: 256        # Gateway reach at level 2, doubling each level; -1 = unlimited
spirit:
  max: 1000
  regen-per-second: 8
  cost: { step: 40, gateway: 60, enemy: 120, infinity: 80, spirit-body: 30, life: 400, location: 250, ... }
  upkeep: { infinity: 25, infinity-per-damage: 15, ability: 6 }   # per second
distance:
  levels: { 2: 500, 3: 2000, 4: 6000 }   # blocks travelled to reach each level
  step-range: 500        # doubling each level
  enemy-range: 12
location: { radius: 8, depth: 4, height: 12 }   # the area each end moves
resist:                  # chance a Beyonder shakes a graft off
  life:  { same-or-higher: 0.85, lower: 0.30 }
  enemy: { same-or-higher: 0.60, lower: 0.20 }
hud: { enabled: true, always: false }
supernova:
  radius: 10
  max-damage: 30         # at the centre, falling off to 0 at the edge
  break-blocks: false
particles:
  density: 1.0           # multiplier for every effect, 0.1 - 4.0
pathway-menu:
  sigil: true            # give every player the sigil and keep it in their inventory
  sigil-slot: 9          # 0-8 hotbar, 9-35 storage
resource-pack:
  enabled: true
  custom-models: true
  required: false
  port: 8164
  public-host: ""
  url: ""
```

## Building

Requires JDK 21+.

```bash
./gradlew build
# -> build/libs/Grafting-2.2.0.jar, drop it into a Paper 1.21.x server's plugins/ folder
```

Built against `paper-api 1.21.11`. The only dependency is the Paper API itself (`compileOnly`), with no shading and no external libraries. `./gradlew generatePack` rebuilds just the resource pack, and `./gradlew updateDocs` refreshes the preview images in `docs/images`.

## Code tour

```
src/main/java/io/github/auphantom/grafting/
  GraftingPlugin    wires everything together
anchor/
  Anchor            sealed interface: one end of a thread
  BlockAnchor       a place (remembers its material, so replacing the block snaps the thread)
  EntityAnchor      a being
graft/
  Mode              the thirteen abilities: name, colour, which kind of thing each end must be
  Graft             base class: id, owner, two anchors, expiry, tick/damage/target hooks
  GraftFactory      turns (mode, first end, second end) into a live graft, or says why not, and charges spirit
  GraftManager      ticks grafts, expires/snaps them, drains upkeep, draws the threads, routes events
graft/types/
  DistanceGraft  FateGraft  NatureGraft (+ Nature)  ReturnGraft
  ExchangeGraft  EnmityGraft  GravityGraft  PuppetGraft  SupernovaGraft
  EnemyStepGraft  LifeGraft  LocationGraft  AbilityGraft (+ CreaturePower)  StorageGraft
beyonder/
  Beyonder          spirit, distance level, Spirit Body, Step, Infinity, sequences, the HUD; saved to players.yml
  DistanceArt       Step, Gateway, Enemy Step, Infinity
  Profile           one player's saved and live state
item/
  ThreadItem        the thread: mode stored in its data, model switched per mode
  Sigil             the Fool sigil and its live Mystery Arts tooltip
listener/
  ThreadListener    clicks -> ability switches, anchors, grafts
  SigilListener     keeps the sigil in its slot, opens the menu
ui/
  PathwayMenu       the chest menu: abilities, portrait, Spirit Body, Distance arts, active threads
  Bars              segmented progress bars
pack/PackServer     serves the bundled texture pack to players
command/GraftCommand  /graft
util/Fx             particle toolkit (helix, ring, sphere, burst), scaled by particles.density
util/Text           shared text formatting

tools/packgen/      build-time generator: AbilityIcons, PathwayArt, Pixels, PackGenerator
tests/e2e/          end-to-end test against a live server
docs/images/        README images
```

Adding an ability means adding a `Mode`, a `Graft` subclass that overrides `tick` and/or `onDamage`, one line in `GraftFactory`, and one icon in `AbilityIcons`.

Some details that took more care than they look:

* **Left-click vs right-click.** Minecraft clients swing their arm on every right-click, and the server reports that swing as a left-click in the air. A naive "left-click switches ability" would switch the ability every time you tied a thread. Air left-clicks are therefore held for two ticks and dropped if a right-click lands around them.
* **Fate loops.** Graft A onto B and B onto A, and damage would ping-pong forever. A re-entrancy guard makes the redirected hit land exactly once.
* **Damage the plugin deals on your behalf** (Supernova, redirected Fate, Volatility) is marked, so the "left-clicking a being with the thread switches ability" rule can't accidentally cancel your own Supernova.
* **Gravity** keeps its own velocity for the being instead of reading it back, because a player's physics run on their own client. Reading it back would stack the pull every tick into a violent fling.
* **Death & Return** listens at `HIGHEST` priority and checks `getFinalDamage()` against health plus absorption, so it only triggers on a hit that would *actually* kill.
* **Nature** effects are short ambient potion pulses refreshed every second, so nothing lingers if the plugin is unloaded mid-graft, and a real potion the being drank is never stripped.
* The resource pack zip is **reproducible** (fixed timestamps), so its SHA-1 only changes when the icons do and clients don't re-download it every build.
* **Editing an item while a menu is open.** Changing the held thread from inside the menu edits the stack in place, which the client never hears about, so the slot is set again explicitly to push the update.
* **Location** swaps are written to `locations.yml` the moment they happen and removed when they end, so a crash or restart mid-graft never leaves two bases swapped: they are swapped back on the next start. Both areas are captured before either is written, so the swap is exact.
* **Life** checks the vessel's death at `HIGHEST` priority against its final damage, and kills the other end one tick later, after the vessel has really died.
* **Spirit Body** only blocks physical harm: damage types in the magic set (and anything a graft deals) still land. Leaving it mid-air grants one fall-damage grace so dropping out of flight is not a death sentence.

## Testing

`tests/e2e/` contains an end-to-end test where a real Minecraft client ([mineflayer](https://github.com/PrismarineJS/mineflayer)) joins a live Paper 1.21.11 server running the plugin. It switches abilities the way a player would (left-click, the menu, the command) and actually right-clicks blocks and mobs, nearby and at range, to make every kind of graft. The server is driven and inspected over RCON. Among other things it checks:

* left-click cycles abilities, with the item's model following along
* every player gets the sigil in its slot, with the Mystery Arts tooltip, and it comes back if removed
* clicking the sigil opens the menu (without picking the sigil up), which lists all thirteen abilities, the portrait, the Spirit Body toggle and the Distance arts locked by level, and clicking an ability sets the thread
* the menu hands out a thread of the chosen ability, and sneak + left-click with the thread opens it too
* the spirit HUD is shown above the hotbar, grafting costs spirit, and `/graft level` unlocks the next art
* Gateway carries players and items both ways, tied to a block 26 blocks away
* Step lands you on coordinates typed in chat, and the distance counts toward the next level
* Enemy Step moves a pig's next step onto a chosen block
* Infinity stops an attack and drains spirit for it
* Spirit Body flies, ignores physical damage, still takes magic damage, and drops when you act
* Life kills a pig when the cow holding its life dies
* Location swaps two areas, chest contents included, and swaps them back when cut
* Storage opens a chest from 25 blocks away and catches overflow from a full inventory
* Ability grafts a pig's trait onto the player (draining spirit), and lends a borrowed thread to a second player that vanishes when cut
* Fate redirects exactly the damage taken, and an A⇄B loop doesn't recurse
* Volatility explodes once without breaking blocks, and snaps when its TNT is removed
* a slime-grafted cow falling 15 blocks bounces and takes no damage
* Death & Return survives 100 damage and sends you home
* Exchange swaps on tie and again on being struck
* Enmity makes a zombie hunt a pig instead of the player
* Gravity makes a pig fall upward toward a block
* Puppetry makes a pig mirror the player's movement
* Supernova destroys its target, hits bystanders and spares the caster

```
85/85 passed
```

To run it, start a Paper server with `online-mode=false`, `enable-rcon=true`, `rcon.password=test`, then run `cd tests/e2e && npm install && npm test`.
