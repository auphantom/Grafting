// End-to-end test: a real Minecraft client (mineflayer) uses the Thread of Grafting on a live Paper server.
// Every ability is selected the way a player would (left-click / the menu / the command) and then
// tied by actually right-clicking blocks and mobs. The server is driven and inspected over RCON.
const mineflayer = require('mineflayer')
const { Rcon } = require('rcon-client')
const { Vec3 } = require('vec3')

const BOT = 'Klein'
const sleep = ms => new Promise(r => setTimeout(r, ms))
const results = []
const check = (name, ok, extra = '') => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name} ${extra}`) }

;(async () => {
  const rcon = await Rcon.connect({ host: '127.0.0.1', port: 25575, password: 'test' })
  const cmd = c => rcon.send(c)
  const num = async c => parseFloat((await cmd(c)).split(': ').pop())
  const passes = async c => (await cmd(c)).includes('passed')
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: BOT, version: '1.21.11' })
  const chat = []
  bot.on('message', m => { const t = m.toString(); chat.push(t); if (!t.includes('Rcon')) console.log('  [chat]', t) })
  bot.on('actionBar', m => chat.push('[bar] ' + m.toString()))
  await new Promise(r => bot.once('spawn', r))
  await sleep(1500)
  const saw = s => chat.some(c => c.includes(s))
  const count = s => chat.filter(c => c.includes(s)).length

  // ---------- arena: a stone floor in a void world ----------
  // Easy, not peaceful: peaceful deletes the zombies used below. Natural spawning is off instead.
  for (const c of [`op ${BOT}`, `gamemode survival ${BOT}`, `clear ${BOT}`, `effect clear ${BOT}`, 'time set day',
    'gamerule spawn_monsters false', 'gamerule spawn_mobs false', 'difficulty easy', 'kill @e[type=!player]',
    'fill -2 -61 -2 44 -45 14 air', 'fill -2 -61 -2 44 -61 14 stone', `tp ${BOT} 2.5 -60 2.5 0 0`]) await cmd(c)
  bot.chat('/graft sever all')
  await sleep(1000)
  await cmd(`effect give ${BOT} instant_health 1 10`)
  bot.chat('/graft give'); await sleep(800)
  const thread = bot.inventory.items().find(i => i.name === 'string')
  check('thread given', !!thread)
  await bot.equip(thread, 'hand')

  const look = async pos => { await bot.lookAt(pos, true); await sleep(150) }
  const clickBlock = async pos => { await look(pos.offset(0.5, 1, 0.5)); await bot.activateBlock(bot.blockAt(pos)); await sleep(350) }
  const clickEntity = async e => { await look(e.position.offset(0, e.height / 2, 0)); await bot.activateEntity(e); await sleep(350) }
  // Far away: aim and right-click the air; the thread finds what you are looking at.
  const aimBlock = async pos => { await look(pos.offset(0.5, 0.99, 0.5)); bot.activateItem(); await sleep(350) }
  const aimEntity = async e => { await look(e.position.offset(0, e.height / 2, 0)); bot.activateItem(); await sleep(350) }
  // Sneak + right-click ties the thread to yourself.
  const tieSelf = async () => {
    bot.setControlState('sneak', true); await sleep(150)
    bot.activateItem(); await sleep(250)
    bot.setControlState('sneak', false); await sleep(150)
  }
  const fresh = async (type, x, z, extra = '') => {
    await cmd(`kill @e[type=${type}]`)
    await cmd(`summon ${type} ${x} -60 ${z} {NoAI:1,Silent:1${extra}}`); await sleep(400)
    return () => bot.nearestEntity(e => e.name === type)
  }
  const modeOfHand = () => {
    const item = bot.heldItem
    const model = item?.components?.find?.(c => c.type === 'item_model')
    return model ? String(model.data).replace('grafting:', '') : null
  }
  const pickMode = async id => { bot.chat(`/graft mode ${id}`); await sleep(350) }
  const severAll = async () => { bot.chat('/graft sever all'); await sleep(350) }

  // ---------- 0. switching abilities ----------
  check('starts on Distance, with custom model', modeOfHand() === 'distance', `model=${modeOfHand()}`)
  await look(bot.entity.position.offset(0, 30, 0))
  bot.swingArm('right'); await sleep(350) // left-click air
  check('left-click switches to next ability (Fate)', modeOfHand() === 'fate', `model=${modeOfHand()}`)
  bot.setControlState('sneak', true); await sleep(150)
  bot.swingArm('right'); await sleep(500)
  const title = JSON.stringify(bot.currentWindow?.title ?? '')
  check('sneak + left-click opens the ability menu', title.includes('choose a graft'), title.slice(0, 60))
  bot.setControlState('sneak', false)
  if (bot.currentWindow) {
    await bot.clickWindow(23, 0, 0); await sleep(500) // slot 23 = Supernova
    check('clicking an icon in the menu selects it', modeOfHand() === 'supernova', `model=${modeOfHand()}`)
  }
  await pickMode('distance')
  check('/graft mode works', modeOfHand() === 'distance')

  // ---------- 1. Distance ----------
  await cmd('setblock 4 -61 2 gold_block'); await cmd('setblock 30 -61 2 gold_block'); await sleep(300)
  await clickBlock(new Vec3(4, -61, 2))
  await cmd('setblock 30 -60 2 gold_block') // a raised pad, so the long sight line cannot graze the floor
  await aimBlock(new Vec3(30, -60, 2)) // 26 blocks away: tied at range, no walking needed
  await sleep(200)
  check('distance graft created at range', saw('Distance #'))
  check('...and to the exact block aimed at', saw('Gold block at 30, -60, 2'))
  await cmd(`tp ${BOT} 4.5 -60 2.5`); await sleep(900)
  check('distance carries player', Math.abs(bot.entity.position.x - 30.5) < 1, `pos=${bot.entity.position}`)
  await cmd(`tp ${BOT} 30.5 -60 5.5`); await sleep(500)
  await cmd(`tp ${BOT} 30.5 -59 2.5`); await sleep(900)
  check('distance works both ways', Math.abs(bot.entity.position.x - 4.5) < 1, `pos=${bot.entity.position}`)
  await cmd('summon item 30.5 -58.5 2.5 {Item:{id:"minecraft:diamond",count:1},PickupDelay:200}'); await sleep(900)
  check('distance carries items', await passes('execute if entity @e[type=item,x=4,y=-61,z=2,dx=1,dy=2,dz=1]'))
  await cmd('kill @e[type=item]'); await cmd('setblock 30 -60 2 air'); await severAll()

  // ---------- 2. wrong-kind ends are refused ----------
  await pickMode('fate')
  await sleep(300)
  await clickBlock(new Vec3(4, -61, 2))
  check('mode rejects the wrong kind of end', saw('starts from a being'))

  // ---------- 3. Fate (+ loop safety) ----------
  await cmd(`tp ${BOT} 10.5 -60 6.5`); await sleep(400)
  let pig = await fresh('pig', 13.5, 6.5, ',Health:10')
  await tieSelf()
  await clickEntity(pig())
  check('fate graft created', saw('Fate #'))
  const pigHp = () => num('data get entity @e[type=pig,limit=1] Health')
  const hp0 = bot.health, pig0 = await pigHp()
  await cmd(`damage ${BOT} 4 minecraft:generic`); await sleep(400)
  const pig1 = await pigHp()
  check('fate: player unharmed', bot.health === hp0, `hp ${hp0} -> ${bot.health}`)
  check('fate: pig takes the hit', pig1 < pig0, `pig ${pig0} -> ${pig1}`)
  await clickEntity(pig()); await tieSelf()
  await cmd(`damage ${BOT} 2 minecraft:generic`); await sleep(300)
  const pig2 = await pigHp()
  check('fate loop: no infinite recursion', pig2 === pig1 - 2 || bot.health === hp0 - 2, `pig ${pig1}->${pig2}, hp ${bot.health}`)
  await severAll()

  // ---------- 4. Nature: volatility + bounce ----------
  await pickMode('nature')
  await cmd('setblock 12 -60 4 tnt')
  await clickBlock(new Vec3(12, -60, 4)); await clickEntity(pig())
  check('nature/volatility graft created', saw('Nature of Volatility'))
  await cmd('setblock 12 -60 4 air'); await sleep(300)
  check('graft snaps when its block is removed', saw('snapped'))
  await cmd('setblock 12 -60 4 tnt')
  await clickBlock(new Vec3(12, -60, 4)); await clickEntity(pig())
  await look(pig().position.offset(0, 0.5, 0))
  // Switch off the thread so the punch below is a real hit, not an ability switch.
  const slot = bot.quickBarSlot; bot.setQuickBarSlot((slot + 1) % 9); await sleep(200)
  bot.attack(pig()); await sleep(500)
  bot.setQuickBarSlot(slot); await sleep(200)
  check('volatility: struck being exploded (one-shot)', count('fulfilled its purpose') >= 1)
  check('volatility: did not break blocks', await passes('execute if block 12 -61 4 stone'))

  await cmd(`tp ${BOT} 18.5 -60 6.5`); await sleep(400)
  await cmd('setblock 17 -60 8 slime_block')
  const cow = await fresh('cow', 20.5, 6.5)
  await clickBlock(new Vec3(17, -60, 8)); await clickEntity(cow())
  check('nature/bounce graft created', saw('Nature of Bounce'))
  await cmd('data merge entity @e[type=cow,limit=1] {NoAI:0}')
  await cmd('tp @e[type=cow] 24.5 -45 4.5')
  let peak = -999, landed = false
  for (let i = 0; i < 60; i++) {
    const y = await num('data get entity @e[type=cow,limit=1] Pos[1]')
    if (y < -59.5) landed = true; else if (landed) peak = Math.max(peak, y)
    await sleep(50)
  }
  check('bounce: cow bounced back up (damped)', landed && peak > -57 && peak < -45, `peak=${peak}`)
  check('bounce: no fall damage', (await num('data get entity @e[type=cow,limit=1] Health')) === 10)
  await severAll()

  // ---------- 5. Death & Return ----------
  await pickMode('return')
  await cmd('setblock 2 -61 8 emerald_block')
  await cmd(`tp ${BOT} 3.5 -60 9.5`); await sleep(400)
  await tieSelf(); await clickBlock(new Vec3(2, -61, 8))
  check('return graft created', saw('Death & Return #'))
  await cmd(`tp ${BOT} 25.5 -60 8.5`); await sleep(400)
  let died = false; bot.once('death', () => { died = true })
  const consumed0 = count('fulfilled its purpose')
  await cmd(`damage ${BOT} 100 minecraft:generic`); await sleep(700)
  check('return: player did not die', !died && bot.health > 0, `hp=${bot.health}`)
  check('return: sent home', Math.abs(bot.entity.position.x - 2.5) < 1 && Math.abs(bot.entity.position.z - 8.5) < 1, `pos=${bot.entity.position}`)
  check('return: one-shot', count('fulfilled its purpose') > consumed0)

  // ---------- 6. Exchange ----------
  await pickMode('exchange')
  await cmd(`tp ${BOT} 10.5 -60 10.5`); await sleep(400)
  pig = await fresh('pig', 16.5, 10.5)
  await tieSelf(); await clickEntity(pig())
  await sleep(300)
  check('exchange: swapped places on tie', Math.abs(bot.entity.position.x - 16.5) < 0.6, `pos=${bot.entity.position}`)
  const pigX = await num('data get entity @e[type=pig,limit=1] Pos[0]')
  check('exchange: the pig took our place', Math.abs(pigX - 10.5) < 0.6, `pig x=${pigX}`)
  await cmd('summon zombie 16.5 -60 12.5 {NoAI:1,Silent:1}'); await sleep(200)
  const hpX = bot.health
  await sleep(600) // the swap has a short cooldown so a single blow cannot ping-pong
  const dmg = await cmd(`damage ${BOT} 3 minecraft:mob_attack_no_aggro by @e[type=zombie,limit=1]`); await sleep(400)
  check('exchange: being struck swaps again instead of taking damage',
    Math.abs(bot.entity.position.x - 10.5) < 0.6 && bot.health >= hpX, `pos=${bot.entity.position} hp ${hpX} -> ${bot.health} (${dmg})`)
  await cmd('kill @e[type=zombie]'); await severAll()

  // ---------- 7. Enmity ----------
  await pickMode('enmity')
  await cmd(`tp ${BOT} 6.5 -60 4.5`); await sleep(300)
  pig = await fresh('pig', 9.5, 4.5)
  // A helmet, or the daylight burns it before it can choose anyone.
  await cmd('summon zombie 16.5 -60 4.5 {Silent:1,PersistenceRequired:1,equipment:{head:{id:"minecraft:leather_helmet",count:1}}}')
  await sleep(300)
  await tieSelf(); await clickEntity(pig())
  check('enmity graft created', saw('Enmity #'))
  let huntsPig = false
  for (let i = 0; i < 30 && !huntsPig; i++) {
    await sleep(200)
    huntsPig = await passes('execute as @e[type=zombie,limit=1] on target if entity @s[type=pig]')
  }
  check('enmity: the zombie hunts the pig, not the player', huntsPig)
  await cmd('kill @e[type=zombie]'); await severAll()

  // ---------- 8. Gravity ----------
  // Tested on a mob: the server simulates mob physics, while a player's own movement is
  // simulated by their client (and this test client does not apply server-sent velocity).
  await pickMode('gravity')
  await cmd(`tp ${BOT} 10.5 -60 2.5`); await sleep(300)
  await cmd('kill @e[type=pig]'); await cmd('summon pig 12.5 -60 2.5 {Silent:1}'); await sleep(400)
  await cmd('setblock 12 -52 5 obsidian')
  await clickEntity(bot.nearestEntity(e => e.name === 'pig')); await aimBlock(new Vec3(12, -52, 5))
  check('gravity graft created', saw('Gravity #'))
  let pigPeak = -99
  for (let i = 0; i < 25; i++) { pigPeak = Math.max(pigPeak, await num('data get entity @e[type=pig,limit=1] Pos[1]')); await sleep(80) }
  check('gravity: the pig falls UP toward the block', pigPeak > -56, `peak y=${pigPeak}`)
  await severAll(); await cmd('setblock 12 -52 5 air'); await sleep(1200)

  // ---------- 9. Puppetry ----------
  await pickMode('puppet')
  pig = await fresh('pig', 12.5, 2.5)
  await tieSelf(); await clickEntity(pig())
  check('puppet graft created', saw('Puppetry #'))
  const before = await num('data get entity @e[type=pig,limit=1] Pos[2]')
  await cmd(`tp ${BOT} 10.5 -60 6.5`); await sleep(500)
  const after = await num('data get entity @e[type=pig,limit=1] Pos[2]')
  check('puppet: the pig mirrored our movement', Math.abs((after - before) - 4) < 0.6, `pig z ${before} -> ${after}`)
  await severAll()

  // ---------- 10. Supernova ----------
  await pickMode('supernova')
  await cmd(`tp ${BOT} 4.5 -60 4.5`); await sleep(300)
  pig = await fresh('pig', 26.5, 6.5, ',Health:10,NoGravity:1')
  await cmd('summon cow 28.5 -60 6.5 {NoAI:1,Silent:1,Health:10}'); await sleep(300)
  await cmd('setblock 6 -60 4 glowstone')
  await clickBlock(new Vec3(6, -60, 4)); await aimEntity(pig())
  check('supernova ignited at range', saw('Supernova #'))
  const hpN = bot.health
  await sleep(6500) // ignition (70t) + collapse (16t) + shockwave (34t)
  const pigAlive = await passes('execute if entity @e[type=pig]')
  const cowAlive = await passes('execute if entity @e[type=cow]')
  check('supernova: the target was destroyed', !pigAlive)
  check('supernova: bystanders in the blast were hit too', !cowAlive || (await num('data get entity @e[type=cow,limit=1] Health')) < 10)
  check('supernova: the caster is never harmed', bot.health === hpN, `hp ${hpN} -> ${bot.health}`)
  check('supernova: ends after detonating', count('fulfilled its purpose') >= 3)

  // ---------- misc ----------
  bot.chat('/graft list'); await sleep(300)
  check('list works', saw('You hold no grafts') || saw('Your grafts'))
  const help = await cmd('graft help')
  check('console help lists every ability', ['Distance', 'Exchange', 'Enmity', 'Gravity', 'Puppetry', 'Supernova']
    .every(m => help.includes(m)))

  const failed = results.filter(r => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} passed`)
  bot.quit(); await rcon.end()
  process.exit(failed.length ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
