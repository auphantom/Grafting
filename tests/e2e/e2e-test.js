// End-to-end test: a real Minecraft client (mineflayer) uses the Thread of Grafting on a live Paper server.
// Every ability is selected the way a player would (left-click / the menu / the command) and then
// tied by actually right-clicking blocks and mobs. The server is driven and inspected over RCON.
const mineflayer = require('mineflayer')
const { Rcon } = require('rcon-client')
const { Vec3 } = require('vec3')

const BOT = 'Klein'
// Each ability's thread borrows a vanilla item model.
const MODEL_TO_MODE = { ender_pearl: 'distance', shield: 'fate', slime_ball: 'nature', nether_star: 'supernova' }
const sleep = ms => new Promise(r => setTimeout(r, ms))
const results = []
const check = (name, ok, extra = '') => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name} ${extra}`) }

;(async () => {
  const rcon = await Rcon.connect({ host: '127.0.0.1', port: 25575, password: 'test' })
  // RCON replies carry legacy colour codes; strip them so text and numbers can be matched.
  const cmd = async c => (await rcon.send(c)).replace(/§./g, '')
  const num = async c => parseFloat((await cmd(c)).split(': ').pop())
  const passes = async c => (await cmd(c)).includes('passed')
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: BOT, version: '1.21.11' })
  const chat = []
  bot.on('message', (m, position) => {
    const t = m.toString()
    if (position === 'game_info') { chat.push('[bar] ' + t); return }
    chat.push(t); if (!t.includes('Rcon')) console.log('  [chat]', t)
  })
  bot.on('actionBar', m => chat.push('[bar] ' + m.toString()))
  // This mineflayer version does not decode NBT action bars into an event, so read the raw packet.
  bot._client.on('action_bar', p => chat.push('[bar] ' + JSON.stringify(p.text)))
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
  // Sneak + right-click ties the thread to yourself. Look at the sky first: a use packet while looking
  // at a block is a block click for a real client, which this bot would not send.
  const tieSelf = async () => {
    await bot.look(bot.entity.yaw, Math.PI / 2, true); await sleep(100)
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
    return model ? MODEL_TO_MODE[String(model.data).replace('minecraft:', '')] ?? String(model.data) : null
  }
  const pickMode = async id => { bot.chat(`/graft mode ${id}`); await sleep(350) }
  const severAll = async () => { bot.chat('/graft sever all'); await sleep(350) }
  const refill = () => cmd(`graft spirit ${BOT} refill`)
  const spirit = async () => parseInt((await cmd(`graft spirit ${BOT}`)).match(/(\d+)\/\d+ spirit/)?.[1] ?? '-1')
  const nbt = path => cmd(`data get entity ${BOT} ${path}`)
  await cmd(`graft level ${BOT} 1`); await refill()

  // ---------- 0. switching abilities ----------
  check('starts on Distance, with its own model', modeOfHand() === 'distance', `model=${modeOfHand()}`)
  await look(bot.entity.position.offset(0, 30, 0))
  bot.swingArm('right'); await sleep(350) // left-click air
  check('left-click switches to next ability (Fate)', modeOfHand() === 'fate', `model=${modeOfHand()}`)
  await pickMode('distance')
  check('/graft mode works', modeOfHand() === 'distance')

  // ---------- 0b. the pathway sigil and menu ----------
  const sigilSlot = 9 // inventory slot 9 = top-left storage slot
  const sigil = bot.inventory.slots[sigilSlot]
  const sigilModel = sigil?.components?.find?.(c => c.type === 'item_model')
  check('every player gets the pathway sigil in its slot', String(sigilModel?.data) === 'grafting:fool_sigil',
    `slot ${sigilSlot}: ${sigil?.name} ${sigilModel?.data}`)
  check('the sigil tooltip shows the Mystery Arts readout',
    JSON.stringify(sigil?.components ?? []).includes('Attendant of Mysteries'))
  await cmd(`item replace entity ${BOT} container.${sigilSlot} with minecraft:dirt`); await sleep(1500)
  check('the sigil comes back if removed', JSON.stringify(bot.inventory.slots[sigilSlot]?.components ?? []).includes('fool_sigil'))
  const menuOpen = () => JSON.stringify(bot.currentWindow?.title ?? '').includes('Reassembly')
  // Click the sigil in the inventory (the player's own inventory window is id 0).
  await bot.clickWindow(sigilSlot, 0, 0); await sleep(800)
  check('clicking the sigil opens the pathway menu', menuOpen(), JSON.stringify(bot.currentWindow?.title ?? '').slice(0, 60))
  check('...without picking the sigil up', !bot.inventory.cursor ||
    !JSON.stringify(bot.inventory.cursor.components ?? []).includes('fool_sigil'))
  if (bot.currentWindow) {
    const items = bot.currentWindow.slots.slice(0, 45)
    check('the menu lists all thirteen abilities', [0, 1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 14, 15].every(s => items[s]?.name === 'string'))
    check('...the player portrait', items[18]?.name === 'player_head')
    check('...the Spirit Body toggle', items[20]?.name === 'soul_torch')
    check('...and the four Distance arts, locked by level', items[22]?.name === 'leather_boots'
      && [23, 24, 25].every(s => items[s]?.name === 'gray_dye'), [22, 23, 24, 25].map(s => items[s]?.name).join(','))
    await bot.clickWindow(8, 0, 0); await sleep(800) // Supernova: switches the thread already held
    // Read it from the server: the bot does not refresh hotbar slots while a container is open.
    check('clicking an ability in the menu sets the thread to it',
      (await cmd(`data get entity ${BOT} SelectedItem.components."minecraft:item_model"`)).includes('minecraft:nether_star'))
    bot.closeWindow(bot.currentWindow); await sleep(300)
  }
  // Drawing a thread when you have none.
  await cmd(`clear ${BOT} minecraft:string`); await sleep(300)
  bot.chat('/graft menu'); await sleep(800)
  if (bot.currentWindow) {
    await bot.clickWindow(1, 0, 0); await sleep(800) // Fate
    bot.closeWindow(bot.currentWindow); await sleep(300)
  }
  const drawn = bot.inventory.items().find(i => i.name === 'string')
  check('the menu hands out a thread of the chosen ability', !!drawn && String(drawn.components?.find?.(c => c.type === 'item_model')?.data) === 'minecraft:shield')
  if (drawn) await bot.equip(drawn, 'hand')
  bot.setControlState('sneak', true); await sleep(150)
  bot.swingArm('right'); await sleep(600)
  bot.setControlState('sneak', false)
  check('sneak + left-click with the thread opens the menu too', menuOpen())
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(300) }
  await pickMode('distance')

  // ---------- 0c. spirit, the HUD, and distance levels ----------
  await sleep(600)
  check('the spirit HUD shows above the hotbar while holding the thread', saw('"✦ "') && saw('/1000') && saw('Lv1 '))
  bot.chat('/graft art gateway'); await sleep(400)
  check('Gateway is locked at distance level 1', saw('Gateway needs distance level 2'))
  const lvl = await cmd(`graft level ${BOT} 2`)
  check('/graft level sets the distance level', lvl.includes('distance level 2'), lvl)
  bot.chat('/graft art gateway'); await sleep(400)
  check('...which unlocks Gateway', saw('Distance art: Gateway'))

  // ---------- 1. Distance: Gateway (level 2) ----------
  await refill()
  await cmd('setblock 4 -61 2 gold_block'); await cmd('setblock 30 -61 2 gold_block'); await sleep(300)
  await clickBlock(new Vec3(4, -61, 2))
  await cmd('setblock 30 -60 2 gold_block') // a raised pad, so the long sight line cannot graze the floor
  await aimBlock(new Vec3(30, -60, 2)) // 26 blocks away: tied at range, no walking needed
  await sleep(200)
  check('gateway graft created at range', saw('Gateway #'))
  check('...and to the exact block aimed at', saw('Gold block at 30, -60, 2'))
  const sp = await spirit()
  check('grafting costs spirit', sp > 0 && sp < 1000, `spirit=${sp}`)
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
  await refill(); await pickMode('return')
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
  await refill(); await pickMode('supernova')
  await cmd(`tp ${BOT} 4.5 -60 4.5`); await sleep(300)
  pig = await fresh('pig', 26.5, 6.5, ',Health:10,NoGravity:1')
  await cmd('kill @e[type=cow]'); await cmd('summon cow 28.5 -60 6.5 {NoAI:1,Silent:1,Health:10,Tags:["bystander"]}'); await sleep(300)
  await cmd('setblock 6 -60 4 glowstone')
  await clickBlock(new Vec3(6, -60, 4)); await aimEntity(pig())
  check('supernova ignited at range', saw('Supernova #'))
  const hpN = bot.health
  const supernovaId = (chat.find(c => c.includes('Supernova #')) ?? '').match(/Supernova #(\d+)/)?.[1]
  // Ignition (70t) + collapse (16t) is ~4.3s; the target is gone the moment it detonates.
  let pigAlive = true, cowHit = false
  for (let i = 0; i < 40 && pigAlive; i++) {
    await sleep(250)
    pigAlive = await passes('execute if entity @e[type=pig]')
    const cowHp = await num('data get entity @e[tag=bystander,limit=1] Health')
    cowHit = cowHit || !(await passes('execute if entity @e[tag=bystander]')) || cowHp < 10
  }
  await sleep(2000) // shockwave
  cowHit = cowHit || !(await passes('execute if entity @e[tag=bystander]'))
    || (await num('data get entity @e[tag=bystander,limit=1] Health')) < 10
  check('supernova: the target was destroyed', !pigAlive)
  check('supernova: bystanders in the blast were hit too', cowHit)
  check('supernova: the caster is never harmed', bot.health === hpN, `hp ${hpN} -> ${bot.health}`)
  check('supernova: ends after detonating', saw(`graft #${supernovaId} has fulfilled its purpose`), `#${supernovaId}`)

  // ---------- 11. Distance: Step (level 1) ----------
  await cmd(`graft level ${BOT} 1`); await refill()
  await cmd('kill @e[type=!player]')
  await cmd(`tp ${BOT} 4.5 -60 4.5 -90 0`); await sleep(400)
  await pickMode('distance')
  bot.chat('/graft art step'); await sleep(300)
  bot.chat('28 -60 10'); await sleep(500) // coordinates typed in chat
  check('Step is armed from coordinates in chat', saw('your next step lands at 28, -60, 10'))
  bot.setControlState('forward', true); await sleep(500); bot.setControlState('forward', false); await sleep(800)
  check('Step: the next step lands on that coordinate',
    Math.abs(bot.entity.position.x - 28.5) < 2 && Math.abs(bot.entity.position.z - 10.5) < 1, `pos=${bot.entity.position}`)
  const travel = await cmd(`graft level ${BOT}`)
  const walked = parseInt(travel.match(/\((\d+)\/500/)?.[1] ?? '0')
  check('Step: the distance travelled counts toward the next level', walked > 20, travel)

  // ---------- 12. Distance: Enemy Step (level 3) ----------
  await cmd(`graft level ${BOT} 3`); await refill()
  await cmd(`tp ${BOT} 4.5 -60 4.5`); await sleep(300)
  bot.chat('/graft art enemy'); await sleep(300)
  pig = await fresh('pig', 10.5, 4.5)
  await cmd('setblock 20 -60 10 lapis_block'); await sleep(200)
  await clickEntity(pig()); await aimBlock(new Vec3(20, -60, 10))
  check('enemy step graft created', saw('Enemy Step #'))
  await cmd('tp @e[type=pig] 11.5 -60 4.5'); await sleep(500)
  const pigAt = await num('data get entity @e[type=pig,limit=1] Pos[0]')
  check("Enemy Step: the pig's next step lands on the chosen block", Math.abs(pigAt - 20.5) < 0.6, `pig x=${pigAt}`)
  await cmd('setblock 20 -60 10 air'); await severAll()

  // ---------- 13. Distance: Infinity (level 4) ----------
  await cmd(`graft level ${BOT} 4`); await refill()
  bot.chat('/graft art infinity'); await sleep(300)
  await look(bot.entity.position.offset(0, 30, 0)); bot.activateItem(); await sleep(400)
  check('Infinity is raised', saw('surrounds you'))
  await cmd('summon zombie 6.5 -60 4.5 {NoAI:1,Silent:1}'); await sleep(200)
  const hpI = bot.health, spI = await spirit()
  await cmd(`damage ${BOT} 4 minecraft:mob_attack by @e[type=zombie,limit=1]`); await sleep(400)
  check('Infinity: the attack never arrives', bot.health === hpI, `hp ${hpI} -> ${bot.health}`)
  check('Infinity: holding it drains spirit, more for the blocked hit', (await spirit()) <= spI - 30, `spirit ${spI} -> ${await spirit()}`)
  bot.activateItem(); await sleep(300)
  await cmd('kill @e[type=zombie]')

  // ---------- 14. Spirit Body ----------
  await refill(); await cmd(`effect give ${BOT} instant_health 1 10`); await sleep(300)
  bot.chat('/graft body'); await sleep(500)
  check('Spirit Body: the player can fly', (await nbt('abilities.mayfly')).includes('1b'))
  const hpB = bot.health
  await cmd(`damage ${BOT} 4 minecraft:generic`); await sleep(400)
  check('Spirit Body: physical damage does nothing', bot.health === hpB, `hp ${hpB} -> ${bot.health}`)
  await cmd(`damage ${BOT} 3 minecraft:magic`); await sleep(400)
  check('Spirit Body: magic damage still hurts', bot.health < hpB, `hp ${hpB} -> ${bot.health}`)
  await pickMode('nature'); await cmd('setblock 6 -60 6 slime_block')
  await cmd(`tp ${BOT} 4.5 -60 6.5`); await sleep(300)
  await clickBlock(new Vec3(6, -60, 6)); await tieSelf()
  check('Spirit Body: using an ability returns you to your main body',
    (await nbt('abilities.mayfly')).includes('0b') && saw('return to your main body'))
  await severAll(); await cmd('setblock 6 -60 6 air')

  // ---------- 15. Life ----------
  await refill(); await pickMode('life')
  await cmd(`tp ${BOT} 10.5 -60 8.5`); await sleep(300)
  pig = await fresh('pig', 13.5, 8.5)
  await cmd('summon cow 13.5 -60 11.5 {NoAI:1,Silent:1}'); await sleep(300)
  await clickEntity(pig()); await clickEntity(bot.nearestEntity(e => e.name === 'cow'))
  check('life graft created', saw('Life #'))
  await cmd('damage @e[type=cow,limit=1] 100 minecraft:generic'); await sleep(600)
  check("Life: killing the vessel kills the one whose life it holds", !(await passes('execute if entity @e[type=pig]')))
  const seq = await cmd(`graft sequence ${BOT} 3`)
  check('/graft sequence sets a Beyonder sequence', seq.includes('Sequence 3'), seq)
  await cmd(`graft sequence ${BOT} 1`)

  // ---------- 16. Location ----------
  await refill(); await pickMode('location')
  await cmd('fill 72 -61 56 88 -61 64 stone'); await cmd(`tp ${BOT} 80.5 -60 60.5`); await sleep(800)
  await cmd('setblock 60 -60 60 diamond_block'); await cmd('setblock 60 -58 60 red_wool')
  await cmd('setblock 61 -60 61 chest'); await cmd('item replace block 61 -60 61 container.0 with minecraft:diamond 5')
  await cmd('setblock 100 -60 60 emerald_block'); await sleep(300)
  await aimBlock(new Vec3(60, -60, 60)); await aimBlock(new Vec3(100, -60, 60)); await sleep(400)
  check('location graft created', saw('Location #'))
  check('Location: the areas traded places', await passes('execute if block 100 -58 60 red_wool')
    && await passes('execute if block 60 -60 60 emerald_block'))
  check('Location: chests and their contents move too', (await cmd('data get block 101 -60 61 Items')).includes('diamond'))
  await severAll(); await sleep(300)
  check('Location: cutting the thread puts everything back', await passes('execute if block 60 -58 60 red_wool')
    && await passes('execute if block 100 -60 60 emerald_block') && (await cmd('data get block 61 -60 61 Items')).includes('diamond'))
  await cmd('fill 52 -65 52 108 -48 68 air')

  // ---------- 17. Storage ----------
  await refill(); await pickMode('storage')
  await cmd(`tp ${BOT} 4.5 -60 10.5`); await sleep(300)
  await cmd('setblock 6 -60 12 chest'); await cmd('item replace block 6 -60 12 container.0 with minecraft:emerald 3')
  await clickBlock(new Vec3(6, -60, 12)); await tieSelf()
  check('storage graft created', saw('Storage #'))
  await cmd(`tp ${BOT} 30.5 -60 2.5`); await sleep(300)
  bot.chat('/graft storage'); await sleep(700)
  check('Storage: open the grafted chest from anywhere', bot.currentWindow?.slots?.[0]?.name === 'emerald')
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(300) }
  for (let i = 0; i < 36; i++) if (i !== bot.quickBarSlot && i !== 9) await cmd(`item replace entity ${BOT} container.${i} with minecraft:dirt 64`)
  await cmd(`item replace entity ${BOT} weapon.offhand with minecraft:dirt 64`)
  await cmd(`summon item 30.5 -59.5 2.5 {Item:{id:"minecraft:cobblestone",count:7},PickupDelay:0}`); await sleep(1200)
  check('Storage: what does not fit flows into the grafted chest',
    await passes('execute if block 6 -60 12 chest{Items:[{id:"minecraft:cobblestone"}]}'))
  await cmd(`clear ${BOT} minecraft:dirt`); await severAll()

  // ---------- 18. Ability ----------
  await refill(); await pickMode('ability')
  await cmd(`tp ${BOT} 10.5 -60 8.5`); await sleep(300)
  pig = await fresh('pig', 13.5, 8.5)
  await clickEntity(pig()); await tieSelf()
  check("ability graft created (a creature's power onto you)", saw('Ability: Gentle Vitality #'))
  await sleep(1300)
  check("Ability: you wield the creature's trait", (await nbt('active_effects')).includes('regeneration'))
  const spA = await spirit(); await sleep(2100)
  check('Ability: it drains spirit every second', (await spirit()) < spA + 8, `spirit ${spA} -> ${await spirit()}`)
  await severAll()
  const audrey = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Audrey', version: '1.21.11' })
  await new Promise(r => audrey.once('spawn', r)); await sleep(1500)
  await cmd('tp Audrey 13.5 -60 8.5'); await cmd('clear Audrey'); await sleep(600)
  await refill(); await tieSelf()
  const aud = bot.nearestEntity(e => e.type === 'player' && e.username === 'Audrey')
  if (aud) await clickEntity(aud)
  await sleep(500)
  check('Ability: your own power is lent to another player as a borrowed thread',
    audrey.inventory.items().some(i => i.name === 'string'), audrey.inventory.items().map(i => i.name).join(','))
  await severAll(); await sleep(500)
  check('Ability: the borrowed thread vanishes when the graft ends', !audrey.inventory.items().some(i => i.name === 'string'))
  audrey.quit()
  await cmd(`graft level ${BOT} 1`)

  // ---------- misc ----------
  bot.chat('/graft list'); await sleep(300)
  check('list works', saw('You hold no grafts') || saw('Your grafts'))
  const help = await cmd('graft help')
  check('console help lists every ability', ['Distance', 'Exchange', 'Enmity', 'Gravity', 'Puppetry', 'Supernova',
    'Life', 'Location', 'Ability', 'Storage']
    .every(m => help.includes(m)))

  const failed = results.filter(r => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} passed`)
  bot.quit(); await rcon.end()
  process.exit(failed.length ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
