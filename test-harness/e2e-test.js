// End-to-end test: a real client (mineflayer) uses the Thread of Grafting on a live Paper server.
const mineflayer = require('mineflayer')
const { Rcon } = require('rcon-client')
const { Vec3 } = require('vec3')

const sleep = ms => new Promise(r => setTimeout(r, ms))
const results = []
const check = (name, ok, extra = '') => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name} ${extra}`) }

;(async () => {
  const rcon = await Rcon.connect({ host: '127.0.0.1', port: 25575, password: 'test' })
  const cmd = async c => { const r = await rcon.send(c); return r }
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Klein', version: '1.21.11' })
  const chat = []
  bot.on('message', m => { const t = m.toString(); chat.push(t); console.log('[chat]', t) })
  await new Promise(r => bot.once('spawn', r))
  await sleep(1500)

  await cmd('op Klein')
  await cmd('gamemode survival Klein')
  await cmd('time set day')
  await cmd('gamerule doMobSpawning false')
  await cmd('kill @e[type=!player]')
  // flat world surface is y=-61 (grass at -61, stand at -60)
  await cmd('fill 0 -61 0 40 -50 10 air')
  await cmd('fill 0 -61 0 40 -61 10 stone')
  await cmd('tp Klein 2.5 -60 2.5 0 0')
  await sleep(1000)
  bot.chat('/graft give')
  await sleep(800)
  const thread = bot.inventory.items().find(i => i.name === 'string')
  check('thread given', !!thread)
  await bot.equip(thread, 'hand')

  const look = async (pos) => { await bot.lookAt(pos, true); await sleep(200) }
  const clickBlock = async (pos) => {
    const block = bot.blockAt(pos)
    await look(pos.offset(0.5, 1, 0.5))
    await bot.activateBlock(block)
    await sleep(400)
  }
  const clickEntity = async (e) => { await look(e.position.offset(0, e.height / 2, 0)); await bot.activateEntity(e); await sleep(400) }

  // ---------- 1. Distance: two gold pads ----------
  await cmd('setblock 4 -61 2 gold_block')
  await cmd('setblock 30 -61 2 gold_block')
  await sleep(500)
  await clickBlock(new Vec3(4, -61, 2))
  await cmd('tp Klein 28.5 -60 4.5'); await sleep(600)
  await clickBlock(new Vec3(30, -61, 2))
  check('distance graft created', chat.some(c => c.includes('Distance #')))
  await cmd('tp Klein 4.5 -60 2.5')
  await sleep(1000)
  check('distance carries player', Math.abs(bot.entity.position.x - 30.5) < 1, `pos=${bot.entity.position}`)
  // walk off and back on to check it works the other way
  await cmd('tp Klein 30.5 -60 5.5'); await sleep(600)
  await cmd('tp Klein 30.5 -60 2.5'); await sleep(1000)
  check('distance works both ways', Math.abs(bot.entity.position.x - 4.5) < 1, `pos=${bot.entity.position}`)
  // items too
  await cmd('summon item 30.5 -59.5 2.5 {Item:{id:"minecraft:diamond",count:1},PickupDelay:200}')
  await sleep(1000)
  const itemNear = (await cmd('execute if entity @e[type=item,x=4,y=-61,z=2,dx=1,dy=2,dz=1]'))
  check('distance carries items', itemNear.includes('passed') || itemNear.includes('Test passed'), itemNear)
  await cmd('kill @e[type=item]')

  // ---------- 2. Fate: harm to Klein lands on a pig ----------
  await cmd('tp Klein 10.5 -60 6.5'); await sleep(500)
  await cmd('kill @e[type=pig]')
  await cmd('summon pig 13.5 -60 6.5 {NoAI:1,CustomName:"Scapegoat",Health:10}')
  await sleep(500)
  const pig = () => bot.nearestEntity(e => e.name === 'pig')
  await bot.lookAt(bot.entity.position.offset(0, 10, 0), true); await sleep(200)
  bot.activateItem(); await sleep(400) // tie to self (right-click air)
  check('tied to self', chat.some(c => c.includes('Thread tied to Klein')))
  await clickEntity(pig())
  check('fate graft created', chat.some(c => c.includes('Fate #')))
  const hpBefore = bot.health
  const pigHp = async () => parseFloat((await cmd('data get entity @e[type=pig,limit=1] Health')).split(': ').pop())
  const pigBefore = await pigHp()
  await cmd('damage Klein 4 minecraft:generic')
  await sleep(500)
  const pigAfter = await pigHp()
  check('fate: player unharmed', bot.health === hpBefore, `hp ${hpBefore} -> ${bot.health}`)
  check('fate: pig takes the hit', pigAfter < pigBefore, `pig ${pigBefore} -> ${pigAfter}`)
  // loop: pig -> Klein as well. Must not recurse forever or crash.
  await clickEntity(pig()); await sleep(100)
  await bot.lookAt(bot.entity.position.offset(0, 10, 0), true); await sleep(200)
  bot.activateItem(); await sleep(400)
  check('fate loop graft created', chat.filter(c => c.includes('Fate #')).length >= 2)
  await cmd('damage Klein 2 minecraft:generic'); await sleep(300)
  const pigLoop = await pigHp()
  check('fate loop: no infinite recursion, someone took it once', pigLoop === pigAfter - 2 || bot.health === hpBefore - 2, `pig ${pigAfter}->${pigLoop}, hp ${bot.health}`)
  bot.chat('/graft sever all'); await sleep(400)

  // ---------- 2b. Nature of Volatility: TNT onto a pig, hit it -> explosion ----------
  await cmd('setblock 12 -60 4 tnt')
  await clickBlock(new Vec3(12, -60, 4))
  await clickEntity(pig())
  check('volatile graft created', chat.some(c => c.includes('Nature of Volatility')))
  await cmd('setblock 12 -60 4 air')
  check('volatile: graft snaps when TNT block is removed', await (async () => { await sleep(300); return chat.some(c => c.includes('snapped')) })())
  await cmd('setblock 12 -60 4 tnt')
  await clickBlock(new Vec3(12, -60, 4))
  await clickEntity(pig())
  await bot.lookAt(pig().position.offset(0, 0.5, 0), true); await sleep(200)
  bot.attack(pig()); await sleep(500)
  check('volatile: struck being exploded (one-shot)', chat.filter(c => c.includes('fulfilled its purpose')).length >= 1)
  check('volatile: did not break blocks', (await cmd('execute if block 12 -61 4 stone')).includes('passed'))

  // ---------- 3. Nature: slime block onto a cow -> a 15-block fall (lethal) becomes a bounce ----------
  await cmd('tp Klein 18.5 -60 6.5'); await sleep(500)
  await cmd('setblock 17 -60 8 slime_block')
  await cmd('kill @e[type=cow]')
  await cmd('summon cow 20.5 -60 6.5 {NoAI:1,CustomName:"Bouncer"}'); await sleep(500)
  const cow = () => bot.nearestEntity(e => e.name === 'cow')
  await clickBlock(new Vec3(17, -60, 8))
  await clickEntity(cow())
  check('nature graft created', chat.some(c => c.includes('Nature of Bounce')))
  await cmd('data merge entity @e[type=cow,limit=1] {NoAI:0}')
  await cmd('tp @e[type=cow] 24.5 -45 4.5')
  const cowY = async () => parseFloat((await cmd('data get entity @e[type=cow,limit=1] Pos[1]')).split(': ').pop())
  let maxAfterLand = -999, landed = false
  for (let i = 0; i < 60; i++) {
    const y = await cowY()
    if (y < -59.5) landed = true
    else if (landed) maxAfterLand = Math.max(maxAfterLand, y)
    await sleep(50)
  }
  const cowHp = parseFloat((await cmd('data get entity @e[type=cow,limit=1] Health')).split(': ').pop())
  check('nature/bounce: cow bounced back up', landed && maxAfterLand > -57 && maxAfterLand < -45, `peak after landing y=${maxAfterLand}`)
  check('nature/bounce: no fall damage', cowHp === 10, `cow hp=${cowHp}`)

  // ---------- 4. Death & Return: Klein's death becomes a trip home ----------
  await cmd('graft sever all') // console has no grafts; sever ours via chat
  bot.chat('/graft sever all'); await sleep(500)
  await cmd('setblock 2 -61 8 emerald_block')
  await cmd('tp Klein 3.5 -60 9.5'); await sleep(500)
  await bot.lookAt(bot.entity.position.offset(0, 10, 0), true); await sleep(200)
  bot.activateItem(); await sleep(400)
  await clickBlock(new Vec3(2, -61, 8))
  check('return graft created', chat.some(c => c.includes('Death & Return #')))
  await cmd('tp Klein 25.5 -60 8.5'); await sleep(500)
  let died = false
  bot.once('death', () => { died = true })
  await cmd('damage Klein 100 minecraft:generic')
  await sleep(800)
  check('return: player did not die', !died && bot.health > 0, `hp=${bot.health}`)
  check('return: player sent home', Math.abs(bot.entity.position.x - 2.5) < 1 && Math.abs(bot.entity.position.z - 8.5) < 1, `pos=${bot.entity.position}`)
  check('return: one-shot, graft consumed', chat.filter(c => c.includes('fulfilled its purpose')).length >= 2)

  // ---------- 5. misc ----------
  bot.chat('/graft list'); await sleep(400)
  check('list works', chat.some(c => c.includes('Your grafts') || c.includes('You hold no grafts')))
  const errs = (await cmd('graft help'))
  check('console help works', errs.includes('Reassembly'))

  const failed = results.filter(r => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} passed`)
  bot.quit(); await rcon.end()
  process.exit(failed.length ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
