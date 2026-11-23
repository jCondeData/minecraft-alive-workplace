package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.jeweller.MegaStones;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PokemonProfessor;
import io.github.jcondedata.aliveworkplace.legend.PokemonRanger;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1123-0408), from docs/wiki/legends.md and docs/wiki/raid-dens-megas-cuisine.md, on what the existing
 * classes leave open:
 * <ul>
 * <li>29.5 "On strike their powers stop" and 29.8 "a guest takes no job ... until they settle" for the two Pokémon
 * Legends: a Professor who is a guest, on strike, or with {@code legends} off sells no evolution stone and takes no money;
 * a Ranger on strike or with Legends off goes after no wild Pokémon, and both are back at it once settled at work.</li>
 * <li>38.6's table, on a real shift: an Apprentice Jeweller (level 2) cuts no Key Stone until made a Journeyman (3); a
 * Journeyman cuts no Mega Stone from meteorite ores until level 4; Key Stones are kept 2 (three ores make two).</li>
 * <li>38.6's Miner: Mega meteorite ores dug without silk touch give evolution stones that stay in the Miner's chest
 * (nothing to the store); silk-touched, the ores go to the store; and what the store has no room for stays in the
 * Miner's chest.</li>
 * </ul>
 */
public class QaLegendsDens1123dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos TRAINER = new BlockPos(9, 2, 9);

	// ---------------------------------------------------------------- the Pokémon Legends (29.21, 29.22)

	/** A hall in the middle (radius 14), Legend needs off, Pokémon and villagers round cleared; everything put back after. */
	private static void legendsSetUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(70)).forEach(e -> e.discard());
		level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
		int radius = VillageHalls.RADIUS;
		boolean needs = LegendNeeds.ENABLED;
		boolean legends = Legends.ENABLED;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 14;
		LegendNeeds.ENABLED = false;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LegendNeeds.ENABLED = needs;
			Legends.ENABLED = legends;
			level.setDayTime(time);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(70)).forEach(e -> e.discard());
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			CivicEffects.forget();
			LegendPowers.forget();
			VillageNeeds.forget();
			Moods.forget();
		});
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static Villager make(GameTestHelper helper, ResourceLocation id, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, Legends.get(id).orElseThrow(() -> new AssertionError(id + " didn't load with Cobblemon")), "test");
		LegendPowers.forget();
		return v;
	}

	/** The same Legend with {@code guest} and {@code strikeSince} changed. */
	private static void setLegend(Villager v, boolean guest, long strikeSince) {
		LegendData d = ModAttachments.LEGEND.get(v);
		ModAttachments.LEGEND.set(v, new LegendData(d.id(), d.name(), guest, d.hall(), d.since(), d.lastDay(), d.unmet(), strikeSince,
			d.lastLuxury(), d.way(), d.checked()));
		LegendPowers.forget();
		CivicEffects.forget();
	}

	/**
	 * Evolution Studies, "one evolution stone a day, to whoever buys it first, for 8 emeralds (800 CobbleDollars)", and
	 * 29.5/29.8: the same Professor as a guest (not settled yet), settled but on strike, and settled with {@code legends}
	 * off sells nothing: the player with 2000 CobbleDollars keeps every one, gets no stone, and the day's stone isn't used
	 * up. Settled and at work again, the same player buys it for exactly 800.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1123d_prof_strike"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1123d_prof_strike")
	public void aProfessorGuestOnStrikeOrSwitchedOffSellsNoStone(GameTestHelper helper) {
		legendsSetUp(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(5, () -> {
			Villager prof = make(helper, PokemonProfessor.ID, new BlockPos(13, 2, 13));
			String tree = ResearchTrees.get(PokemonProfessor.TREE).orElseThrow(() -> new AssertionError("no Pokédex tree")).key();
			hall(helper).setResearch(new Research.State(Map.of(tree + "/" + PokemonProfessor.EVOLUTION_STUDIES, 1), Optional.empty(), 0, false));
			CivicEffects.forget();
			Item stone = PokemonProfessor.stoneOfDay(level);
			helper.assertTrue(stone != null, "no stone of the day");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			CobbleDollarsBank.add(player, 2000 - CobbleDollarsBank.balance(player));
			long day = Chronicle.day(level);
			String[] cases = {"a guest", "on strike", "with Legends off"};
			for (String which : cases) {
				switch (which) {
					case "a guest" -> setLegend(prof, true, -1);
					case "on strike" -> setLegend(prof, false, day);
					default -> {
						setLegend(prof, false, -1);
						Legends.ENABLED = false;
						LegendPowers.forget();
					}
				}
				String said = PokemonProfessor.buyStone(player, prof).getString();
				helper.assertTrue(player.getInventory().countItem(stone) == 0, "a Professor " + which + " sold a stone (\"" + said + "\")");
				helper.assertTrue(CobbleDollarsBank.balance(player) == 2000, "a Professor " + which + " took money: balance "
					+ CobbleDollarsBank.balance(player) + " (\"" + said + "\")");
				helper.assertTrue(!PokemonProfessor.soldToday(prof), "a Professor " + which + " used up the day's stone");
			}
			Legends.ENABLED = true;
			LegendPowers.forget();
			CivicEffects.forget();
			String said = PokemonProfessor.buyStone(player, prof).getString();
			helper.assertTrue(player.getInventory().countItem(stone) == 1 && CobbleDollarsBank.balance(player) == 1200,
				"the control, settled and at work: stones " + player.getInventory().countItem(stone) + ", balance " + CobbleDollarsBank.balance(player)
					+ " (\"" + said + "\")");
			helper.succeed();
		});
	}

	/**
	 * 29.5 for the Ranger: on strike, with a wild Eevee beside them and a pasture with room, they go after no Pokémon (the
	 * Eevee stays wild); with Legends off the same; settled and at work, the next look goes after it.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qa1123d_ranger_strike"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qa1123d_ranger_strike")
	public void aRangerOnStrikeOrSwitchedOffBefriendsNothing(GameTestHelper helper) {
		legendsSetUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L); // past the morning: no calming first
		PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setGameMode(GameType.SURVIVAL);
		hall(helper).setOwner(owner.getUUID(), owner.getGameProfile().getName());
		helper.runAfterDelay(5, () -> {
			// all in one tick: the hall's round lifts a strike while legendNeeds is off, and the Legend's own tick would
			// choose the Eevee before the strike is set
			PokemonEntity eevee = PokemonProperties.Companion.parse("eevee level=8", " ", "=").createEntity(level);
			eevee.setPos(helper.absoluteVec(new Vec3(8.5, 2, 9.5)));
			eevee.setNoAi(true);
			level.addFreshEntity(eevee);
			Villager ranger = make(helper, PokemonRanger.ID, new BlockPos(7, 2, 9));
			setLegend(ranger, false, Chronicle.day(level));
			PokemonRanger.tick(ranger);
			PokemonRanger.tick(ranger);
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE, "a Ranger on strike goes after a Pokémon: " + PokemonRanger.state(ranger));
			helper.assertTrue(eevee.isAlive() && eevee.getPokemon().getOwnerUUID() == null, "a Ranger on strike befriended the Eevee");
			setLegend(ranger, false, -1);
			Legends.ENABLED = false;
			LegendPowers.forget();
			PokemonRanger.tick(ranger);
			PokemonRanger.tick(ranger);
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE, "a Ranger with Legends off goes after a Pokémon: " + PokemonRanger.state(ranger));
			helper.assertTrue(eevee.isAlive() && eevee.getPokemon().getOwnerUUID() == null, "a Ranger with Legends off befriended the Eevee");
			Legends.ENABLED = true;
			LegendPowers.forget();
			PokemonRanger.tick(ranger);
			helper.assertTrue(PokemonRanger.state(ranger).task() != PokemonRanger.NONE,
				"the control, at work again: the Ranger doesn't go after the wild Eevee beside them: " + PokemonRanger.state(ranger));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Jeweller (38.6)

	private static Item ms(String path) {
		return MegaStoneCompatTests.ms(path);
	}

	private static int keyStones(GameTestHelper helper, Container own, Villager v) {
		return own.countItem(ms("keystone")) + MegaStoneCompatTests.store(helper).countItem(ms("keystone")) + v.getInventory().countItem(ms("keystone"));
	}

	private static void level(Villager v, int level) {
		v.setVillagerData(v.getVillagerData().setLevel(level));
	}

	/**
	 * 38.6's table, "a Key Stone, level 3": an Apprentice (level 2) with Key Stone Ore, 2 gold and 4 emeralds in the store
	 * works 500 ticks and cuts nothing, takes nothing; made a Journeyman, the same Jeweller cuts exactly one Key Stone from
	 * those makings.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1123d_jeweller_apprentice"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1123d_jeweller_apprentice")
	public void anApprenticeCutsNoKeyStoneAJourneymanDoes(GameTestHelper helper) {
		Container own = MegaStoneCompatTests.shop(helper, new ItemStack(ms("keystone_ore")), new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 4));
		Container store = MegaStoneCompatTests.store(helper);
		Villager v = MegaStoneCompatTests.jeweller(helper, 2);
		MegaStoneCompatTests.work(helper, v);
		boolean[] promoted = {false};
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(keyStones(helper, own, v) == 0, "an Apprentice cut " + keyStones(helper, own, v) + " Key Stones; says " + MegaStoneCompatTests.line(helper, v));
			helper.assertTrue(store.countItem(ms("keystone_ore")) == 1 && store.countItem(Items.GOLD_INGOT) == 2 && store.countItem(Items.EMERALD) == 4,
				"an Apprentice took makings: " + store.countItem(ms("keystone_ore")) + " ore, " + store.countItem(Items.GOLD_INGOT) + " gold, "
					+ store.countItem(Items.EMERALD) + " emeralds left");
			level(v, 3);
			promoted[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(promoted[0], "not yet a Journeyman");
			helper.assertTrue(keyStones(helper, own, v) == 1, "the Journeyman cut " + keyStones(helper, own, v) + " Key Stones, expected 1; says "
				+ MegaStoneCompatTests.line(helper, v));
			helper.assertTrue(store.countItem(ms("keystone_ore")) == 0 && store.countItem(Items.GOLD_INGOT) == 0 && store.countItem(Items.EMERALD) == 0,
				"makings left in the store");
		});
	}

	/**
	 * 38.6's table, "4 Mega meteorite ores ... level 4": a Journeyman (level 3) with 4 meteorite ores, 2 gold and 4
	 * emeralds in the store and a Master trainer in the village cuts nothing in 500 ticks and the ores stay; at level 4 the
	 * same makings become exactly one stone of the Master's team.
	 */
	//$ gametest_ticks_batch AREA '2600' '"qa1123d_jeweller_meteorite_level"'
	@GameTest(template = AREA, timeoutTicks = 2600, batch = "qa1123d_jeweller_meteorite_level")
	public void aJourneymanCutsNoStoneFromMeteoriteOresLevelFourDoes(GameTestHelper helper) {
		Container own = MegaStoneCompatTests.shop(helper, new ItemStack(ms("mega_meteorid_fire_ore"), 2), new ItemStack(ms("mega_meteorid_water_ore")),
			new ItemStack(ms("mega_meteorid_dawn_ore")), new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 4));
		Container store = MegaStoneCompatTests.store(helper);
		Villager master = MegaStoneCompatTests.villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		List<Item> wanted = MegaStones.stonesOf(master);
		helper.assertTrue(!wanted.isEmpty(), "the Master's team has no Mega Stone");
		Villager v = MegaStoneCompatTests.jeweller(helper, 3);
		MegaStoneCompatTests.work(helper, v);
		boolean[] promoted = {false};
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(MegaStoneCompatTests.stonesIn(own).isEmpty() && MegaStoneCompatTests.stonesIn(store).isEmpty(),
				"a Journeyman cut " + MegaStoneCompatTests.stonesIn(own) + " from meteorite ores");
			int ores = ores(store);
			helper.assertTrue(ores == 4 && store.countItem(Items.GOLD_INGOT) == 2 && store.countItem(Items.EMERALD) == 4,
				"a Journeyman took makings: " + ores + " ores, " + store.countItem(Items.GOLD_INGOT) + " gold, " + store.countItem(Items.EMERALD) + " emeralds left");
			level(v, 4);
			promoted[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(promoted[0], "not yet level 4");
			List<Item> made = MegaStoneCompatTests.stonesIn(own);
			made.addAll(MegaStoneCompatTests.stonesIn(store));
			helper.assertTrue(made.size() == 1 && wanted.contains(made.get(0)), "at level 4: stones " + made + ", wanted one of " + wanted + "; says "
				+ MegaStoneCompatTests.line(helper, v));
			helper.assertTrue(ores(store) == 0, ores(store) + " ores left at level 4");
		});
	}

	private static int ores(Container c) {
		return c.countItem(ms("mega_meteorid_fire_ore")) + c.countItem(ms("mega_meteorid_water_ore")) + c.countItem(ms("mega_meteorid_dawn_ore"));
	}

	/**
	 * 38.6's table, "a Key Stone ... kept in the store: 2": three Key Stone Ores with makings for three (6 gold, 12
	 * emeralds) and no trainer in the village make exactly two Key Stones, and 600 ticks after the second, still two, with
	 * one ore, 2 gold and 4 emeralds left in the store.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1123d_jeweller_keep_two"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1123d_jeweller_keep_two")
	public void keyStonesAreKeptAtTwo(GameTestHelper helper) {
		Container own = MegaStoneCompatTests.shop(helper, new ItemStack(ms("keystone_ore"), 3), new ItemStack(Items.GOLD_INGOT, 6), new ItemStack(Items.EMERALD, 12));
		Container store = MegaStoneCompatTests.store(helper);
		Villager v = MegaStoneCompatTests.jeweller(helper, 3);
		MegaStoneCompatTests.work(helper, v);
		long[] two = {-1};
		helper.onEachTick(() -> {
			if (two[0] < 0 && keyStones(helper, own, v) >= 2) {
				two[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(two[0] >= 0, "fewer than two Key Stones yet: " + keyStones(helper, own, v) + "; says " + MegaStoneCompatTests.line(helper, v));
			helper.assertTrue(keyStones(helper, own, v) == 2, keyStones(helper, own, v) + " Key Stones, kept at 2");
			helper.assertTrue(helper.getTick() >= two[0] + 600, "waiting 600 ticks past the second Key Stone");
			helper.assertTrue(store.countItem(ms("keystone_ore")) == 1 && store.countItem(Items.GOLD_INGOT) == 2 && store.countItem(Items.EMERALD) == 4,
				"the store after two Key Stones: " + store.countItem(ms("keystone_ore")) + " ore, " + store.countItem(Items.GOLD_INGOT) + " gold, "
					+ store.countItem(Items.EMERALD) + " emeralds, expected 1, 2 and 4");
		});
	}

	// ---------------------------------------------------------------- the Miner's finds (38.6)

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BENCH_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STOREHOUSE = new BlockPos(2, 2, 13);
	private static final BlockPos STORE_CHEST = new BlockPos(2, 2, 15);

	record Dig(Container own, Container store, QuarrySite site) {
	}

	/**
	 * A Village Hall, a Storehouse with its chest (the village store), a Miner's Bench with its chest holding {@code tool},
	 * and a 2 by 2 patch of {@code ore} (with a stone beside it) the Miner is set to dig, as {@code MegaStoneCompatTests}
	 * sets it up for Key Stone ore.
	 */
	private static Dig dig(GameTestHelper helper, ItemStack tool, Block ore) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		PartnerShowsCompatTests.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer()));
		helper.setBlock(new BlockPos(15, 2, 15), ModBlocks.VILLAGE_HALL);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.setBlock(BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		Container own = helper.getBlockEntity(BENCH_CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		own.setItem(0, tool);
		BlockPos min = new BlockPos(7, 2, 7);
		BlockPos max = new BlockPos(8, 2, 8);
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			helper.setBlock(p, ore);
		}
		helper.setBlock(new BlockPos(9, 2, 7), Blocks.STONE);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Miners.employ(level, miner, helper.absolutePos(BENCH));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(min), helper.absolutePos(new BlockPos(9, 2, 8)));
		QuarrySite site = Miners.start(level, miner, null, box, box.getYSpan());
		site.setStairs(false);
		return new Dig(own, store, site);
	}

	private static boolean done(GameTestHelper helper, Dig d) {
		return QuarrySiteManager.get(helper.getLevel()).get(d.site().id()) == null;
	}

	/** Cobblemon's evolution stones (any {@code cobblemon:*_stone} item) in {@code c}. */
	private static int evolutionStones(Container c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
			if (!s.isEmpty() && id.getNamespace().equals("cobblemon") && id.getPath().endsWith("_stone")) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static String contents(Container c) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty()) {
				out.append(c.getItem(i)).append(' ');
			}
		}
		return out.length() == 0 ? "nothing" : out.toString().trim();
	}

	/**
	 * "Mega meteorite ores (silk-touched; without silk touch they drop Cobblemon's evolution stones, which stay in the
	 * Miner's chest)": four fire meteorite ores dug with a plain diamond pickaxe leave the village store empty (no ore, no
	 * stone, no cobblestone) and put the evolution stones in the Miner's own chest.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa1123d_miner_meteorite_plain"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa1123d_miner_meteorite_plain")
	public void meteoriteOresWithoutSilkTouchGiveStonesThatStayInTheMinersChest(GameTestHelper helper) {
		Dig d = dig(helper, new ItemStack(Items.DIAMOND_PICKAXE), MegaStoneCompatTests.msBlock("mega_meteorid_fire_ore"));
		helper.succeedWhen(() -> {
			helper.assertTrue(done(helper, d), "still digging: " + Math.round(d.site().progress() * 100) + "%");
			helper.assertTrue(d.store().isEmpty(), "the village store got " + contents(d.store()) + " from meteorite ores dug without silk touch");
			helper.assertTrue(d.own().countItem(ms("mega_meteorid_fire_ore")) == 0, "the ore itself came out without silk touch");
			helper.assertTrue(evolutionStones(d.own()) == 4, evolutionStones(d.own()) + " evolution stones in the Miner's chest, expected one a block (4): "
				+ contents(d.own()));
		});
	}

	/** Silk-touched: the same four fire meteorite ores go to the village store, none to the Miner's chest. */
	//$ gametest_ticks_batch AREA '3000' '"qa1123d_miner_meteorite_silk"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa1123d_miner_meteorite_silk")
	public void silkTouchedMeteoriteOresGoToTheStore(GameTestHelper helper) {
		ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
		pick.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
			.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
		Dig d = dig(helper, pick, MegaStoneCompatTests.msBlock("mega_meteorid_fire_ore"));
		helper.succeedWhen(() -> {
			helper.assertTrue(done(helper, d), "still digging: " + Math.round(d.site().progress() * 100) + "%");
			helper.assertTrue(d.store().countItem(ms("mega_meteorid_fire_ore")) == 4, d.store().countItem(ms("mega_meteorid_fire_ore"))
				+ " meteorite ores in the store, expected 4; the Miner's chest holds " + contents(d.own()));
			helper.assertTrue(d.own().countItem(ms("mega_meteorid_fire_ore")) == 0, "meteorite ores in the Miner's own chest: " + contents(d.own()));
			helper.assertTrue(evolutionStones(d.store()) == 0 && evolutionStones(d.own()) == 0, "evolution stones from silk-touched ores");
		});
	}

	/**
	 * "what the store has no room for goes in their chest as before": with the store's chest full (27 stacks of dirt), the
	 * four Key Stones from dug Key Stone ore go into the Miner's own chest, and the store is unchanged.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa1123d_miner_store_full"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa1123d_miner_store_full")
	public void keyStonesTheFullStoreCantTakeStayInTheMinersChest(GameTestHelper helper) {
		Dig d = dig(helper, new ItemStack(Items.DIAMOND_PICKAXE), MegaStoneCompatTests.msBlock("keystone_ore"));
		for (int i = 0; i < d.store().getContainerSize(); i++) {
			d.store().setItem(i, new ItemStack(Items.DIRT, 64));
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(done(helper, d), "still digging: " + Math.round(d.site().progress() * 100) + "%");
			helper.assertTrue(d.own().countItem(ms("keystone")) == 4, d.own().countItem(ms("keystone")) + " Key Stones in the Miner's chest, expected 4; "
				+ "the store holds " + d.store().countItem(ms("keystone")));
			helper.assertTrue(d.store().countItem(Items.DIRT) == 27 * 64 && d.store().countItem(ms("keystone")) == 0, "the full store changed: " + contents(d.store()));
		});
	}
}
