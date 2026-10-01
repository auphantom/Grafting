# Grafting

A small, standalone Paper plugin that turns **Reassembly / Grafting**, the Sequence 1 *Attendant of Mysteries* power from the Fool pathway (*Lord of the Mysteries*), into something you can actually use in Minecraft.

> *"Everything can be connected, if you know where to make the cut."*

In the novel, Grafting takes two things (objects, concepts, rules, even directions) that should never touch and stitches them together, so one starts behaving according to the other. This plugin keeps that core idea and nothing else: no spirituality, no Beyonder sequences, no databases. There's one item and one rule.

## The idea: one thread, two ends

You hold the **Thread of Grafting**. Right-click two things, and the **first** is grafted **onto** the second. There are only two kinds of "things" in Minecraft worth tying a thread to, **places** (blocks) and **beings** (living entities), which gives four combinations. Each one tampers with a different concept:

| First ➜ Second | Concept tampered with | What happens |
|---|---|---|
| **Place ➜ Place** | **Distance** | The distance between the two blocks becomes zero. Step on one and you're already standing on the other. It works both ways, and for players, mobs, dropped items, anything. |
| **Being ➜ Being** | **Fate** | Harm meant for the first being finds the second one instead, wherever it is. Make a pig your scapegoat, or graft a boss's fate onto its own minion. |
| **Place ➜ Being** | **Nature** | The being takes on the *nature* of the block (see below). |
| **Being ➜ Place** | **Death & Return** | The being's next death is grafted onto a journey home. The killing blow never lands. Instead it's pulled back along the thread to that block, alive. One use. |

Order matters, just like in the books: grafting A onto B is not the same as grafting B onto A.

### Natures (Place ➜ Being)

The block's family decides what the being becomes:

| Block | Nature | Effect on the being |
|---|---|---|
| Slime block | **Bounce** | Falls turn into (damped) bounces, so no fall damage |
| TNT | **Volatility** | Explodes the next time something strikes it (no block damage, one use) |
| Ice / snow | **Frost** | Freezes water underfoot like Frost Walker, chills whatever it hits, immune to freezing |
| Magma, netherrack, campfire, furnace... | **Flame** | Fire immune, ignites whatever it strikes and whoever strikes it |
| Glowstone, lanterns, torches, froglights... | **Light** | Glows, sees in the dark, sets nearby undead on fire |
| Cobweb, honey, soul sand, mud | **Stickiness** | Can barely move |
| Wool, leaves, hay, scaffolding | **Feather** | Drifts instead of falling, runs light-footed |
| Prismarine, sponge, kelp... | **Water** | Breathes and swims like a fish |
| Flowers, saplings, crops, moss... | **Life** | Slowly regrows its wounds |
| Any other full solid block (stone, ores, planks...) | **Stone** | Takes far less damage, but moves like a statue |

Try grafting a cobweb onto the player chasing you, TNT onto a creeper's victim, or a slime block onto yourself before jumping off a cliff.

### Rules of the thread

* A graft is visible: a coloured particle thread runs between its two ends, with a bright mote travelling from the first to the second.
* A graft **snaps** as soon as either end stops existing. Break the block or kill the being and the connection is gone. So the counterplay is real: find the anchor block and break it.
* Grafts **unravel** after a configurable time. One-shot grafts (Death & Return, Volatility) end as soon as they're used.
* Each player keeps at most `max-active-grafts` grafts alive. Making a new one lets go of the oldest.

## Usage

| Action | Effect |
|---|---|
| `/graft give [player]` | Get the Thread of Grafting (op only by default) |
| Right-click a block / a mob / a player | Tie the thread to it |
| Right-click the air | Tie the thread to **yourself** |
| Sneak + right-click the air | Let go of a half-tied thread |
| `/graft list` | Your active grafts and their time left |
| `/graft sever [id\|all]` | Cut a graft |

Permissions: `grafting.use` (default: everyone) and `grafting.give` (default: op).

`config.yml`:

```yaml
max-active-grafts: 5
durations:          # seconds
  distance: 120
  fate: 60
  nature: 90
  return: 300
max-distance: 256   # Distance grafts refuse to span further than this, -1 = unlimited
```

## Building

Requires JDK 21+.

```bash
./gradlew build
# -> build/libs/Grafting-1.0.0.jar, drop it into a Paper 1.21.x server's plugins/ folder
```

Built against `paper-api 1.21.11`. The only dependency is the Paper API itself (`compileOnly`), with no shading and no external libraries.

## Code tour

```
anchor/
  Anchor            sealed interface: one end of a thread
  BlockAnchor       a place (remembers its material, so replacing the block snaps the thread)
  EntityAnchor      a being
graft/
  Graft             base class: id, owner, two anchors, expiry, tick/damage hooks
  GraftFactory      THE rule: (Place|Being) x (Place|Being) -> which graft
  GraftManager      ticks grafts, expires/snaps them, draws the threads, routes damage events
  DistanceGraft     Place + Place
  FateGraft         Being + Being
  NatureGraft       Place + Being  (+ Nature enum: block -> nature mapping)
  ReturnGraft       Being + Place
ThreadItem          the item (tagged via PersistentDataContainer, renaming plain string does nothing)
ThreadListener      clicks -> anchors -> grafts
GraftCommand        /graft
GraftingPlugin      wiring
```

Adding a new kind of graft means writing one class that overrides `tick` and/or `onDamage`, plus one line in `GraftFactory`.

A few details that took more care than they look:

* **Fate loops.** Graft A onto B and B onto A, and damage would ping-pong forever. A re-entrancy guard makes the redirected hit land exactly once.
* **Death & Return** listens at `HIGHEST` priority and checks `getFinalDamage()` against health plus absorption, so it only triggers on a hit that would *actually* kill after armour and other plugins have had their say. `/kill` is deliberately not intercepted.
* **Distance** remembers who just arrived on a pad, so you aren't bounced back and forth. You have to step off and back on. It also refuses to teleport into a wall if something was built on top of the other pad.
* **Nature** effects are applied as short ambient potion pulses that get refreshed every second, so if the plugin is unloaded mid-graft nothing sticks around for long. On end, it only strips effects that are still its own and never a real potion the being drank.

## Testing

`test-harness/` contains an end-to-end test where a real Minecraft client ([mineflayer](https://github.com/PrismarineJS/mineflayer)) joins a live Paper 1.21.11 server running the plugin, gets the thread with `/graft give`, and actually right-clicks blocks and mobs to make every kind of graft. The server is controlled over RCON. It checks, among other things:

* Distance carries the player both ways, and dropped items too
* Fate: the player takes 0 damage and the pig loses exactly that much, and an A⇄B loop doesn't recurse
* Volatility explodes once without breaking blocks, and snaps when its TNT is removed
* A slime-grafted cow falling 15 blocks bounces and takes no damage
* Death & Return: 100 damage, the player survives and is sent to the anchor block, and the graft is used up

```
24/24 passed
```

To run it, start a Paper server with `online-mode=false`, `enable-rcon=true`, `rcon.password=test`, then run `cd test-harness && npm install && npm test`.

## AI usage

As allowed by the brief, this plugin was built with the help of an AI coding agent (Claude, via the Jcode harness). The AI was used to write and iterate on the code, set up the local Paper test server, and write the automated in-game test. The design (the four-way Place/Being grafting matrix and the natures) and the review and testing of the result were done in collaboration with it.
