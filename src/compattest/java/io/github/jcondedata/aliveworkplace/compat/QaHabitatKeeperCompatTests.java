package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (ROADMAP 28.10), written from the spec: the honey worn off and slathered again, the log scan's 32-block edge from
 * both sides, a Saccharine sapling planted by a lure spot, three snacks kept out and no more, who hears a sighting, the
 * hall's last five, the config keys, every lure's name, and her trades.
 */
public class QaHabitatKeeperCompatTests implements FabricGameTest {
	static final String AREA = CompatGameTests.AREA;
	static final BlockPos PASTURE = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	static final BlockPos LOG = new BlockPos(8, 2, 2);

	static ResourceLocation c(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}

	static Item item(String path) {
		return BuiltInRegistries.ITEM.get(c(path));
	}

	static Block block(String path) {
		return BuiltInRegistries.BLOCK.get(c(path));
	}

	static String said(GameTestHelper helper, Villager keeper) {
		WorkerStatus.Entry e = WorkerStatus.get(keeper, helper.getLevel().getGameTime());
		return (e == null ? "no status" : e.line().getString()) + " at " + helper.relativePos(keeper.blockPosition()) + ", bag "
			+ ModAttachments.BUILDER_BAG.getOrCreate(keeper).stacks();
	}

	static boolean snackAt(GameTestHelper helper, BlockPos rel) {
		return BuiltInRegistries.BLOCK.getKey(helper.getBlockState(rel).getBlock()).equals(c("poke_snack"));
	}

	static PokemonEntity wild(GameTestHelper helper, String props, Vec3 rel) {
		ServerLevel level = helper.getLevel();
		PokemonEntity wild = PokemonProperties.Companion.parse(props, " ", "=").createEntity(level);
		wild.setPos(helper.absoluteVec(rel));
		wild.setNoAi(true);
		helper.assertTrue(level.addFreshEntity(wild), "the wild " + props + " wasn't added");
		PartnerShowsCompatTests.after(helper, wild::discard);
		return wild;
	}

	static void clearWilds(GameTestHelper helper) {
		helper.getLevel().getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(HabitatKeepers.SIGHT_RANGE + 4)).forEach(e -> e.discard());
	}

	// ------------------------------------------------------------------ honey

	/**
	 * "Slathered again when it wears off": she slathers a bare Saccharine log from her chest; when the honey wears off (the
	 * log is bare again) she slathers it again with her second bottle, and both bottles come back empty.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qa_hk_reslather"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_hk_reslather")
	public void aLogWhoseHoneyWoreOffIsSlatheredAgain(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(LOG, block("saccharine_log"));
		Villager keeper = HabitatKeeperCompatTests.keeper(helper, new ItemStack(Items.HONEY_BOTTLE, 2));
		int[] slathers = {0};
		boolean[] wasSlathered = {false};
		helper.onEachTick(() -> {
			boolean now = HabitatKeepers.isSlathered(helper.getBlockState(LOG));
			if (now && !wasSlathered[0]) {
				slathers[0]++;
				if (slathers[0] == 1) {
					// The honey wears off: Cobblemon turns it back into a bare log.
					helper.setBlock(LOG, block("saccharine_log"));
					now = false;
				}
			}
			wasSlathered[0] = now;
		});
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(slathers[0] >= 2, "slathered " + slathers[0] + " times; the log is " + helper.getBlockState(LOG) + "; she says " + said(helper, keeper));
			helper.assertTrue(HabitatKeepers.isSlathered(helper.getBlockState(LOG)), "the log isn't slathered at the end");
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 0 && chest.countItem(Items.GLASS_BOTTLE) == 2,
				"bottles: " + chest.countItem(Items.HONEY_BOTTLE) + " honey, " + chest.countItem(Items.GLASS_BOTTLE) + " glass");
		});
	}

	/**
	 * "Saccharine logs within 32 blocks": the scan finds a log 32 blocks east and 32 west of the pasture, and none 33
	 * blocks east or 33 north.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_hk_scan_edge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_hk_scan_edge")
	public void theLogScanReachesThirtyTwoBlocksNotThirtyThree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(PASTURE);
		List<BlockPos> in = List.of(center.offset(32, 0, 0), center.offset(-32, 0, 0));
		List<BlockPos> out = List.of(center.offset(33, 0, 0), center.offset(0, 0, -33));
		List<BlockPos> all = new ArrayList<>(in);
		all.addAll(out);
		List<BlockState> before = new ArrayList<>();
		for (BlockPos p : all) {
			helper.assertTrue(level.isLoaded(p), "the log spot " + p + " isn't loaded, so the scan proves nothing");
			before.add(level.getBlockState(p));
		}
		Runnable restore = () -> {
			for (int i = 0; i < all.size(); i++) {
				level.setBlock(all.get(i), before.get(i), Block.UPDATE_CLIENTS);
			}
		};
		PartnerShowsCompatTests.after(helper, restore);
		try {
			for (BlockPos p : all) {
				level.setBlock(p, block("saccharine_log").defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			List<BlockPos> found = new ArrayList<>();
			int cursor = 0;
			int ticks = 0;
			do {
				cursor = HabitatKeepers.scan(level, center, cursor, Integer.MAX_VALUE, found);
			} while (cursor != 0 && ++ticks < 10_000);
			helper.assertTrue(found.containsAll(in), "a log 32 blocks out wasn't found: found " + found + ", logs at " + in);
			for (BlockPos p : out) {
				helper.assertTrue(!found.contains(p), "a log 33 blocks out was found: " + p);
			}
		} finally {
			restore.run();
		}
		helper.succeed();
	}

	/** "Saccharine saplings from the chests are planted round the lure spots": one is planted on the grass by a marked spot. */
	//$ gametest_ticks_batch AREA '2400' '"qa_hk_sapling"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_hk_sapling")
	public void aSaplingFromTheChestIsPlantedByALureSpot(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos marked = new BlockPos(9, 1, 9);
		BlockPos spot = marked.above();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (Math.abs(dx) + Math.abs(dz) > 1) {
					helper.setBlock(marked.offset(dx, 0, dz), Blocks.GRASS_BLOCK);
				}
			}
		}
		Villager keeper = HabitatKeeperCompatTests.keeper(helper, new ItemStack(item("poke_snack"), 1), new ItemStack(item("saccharine_sapling"), 1));
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, new ArrayList<>(), "qa-sapling");
		HabitatKeepers.markSpot(player, keeper, HabitatKeeperCompatTests.marker(helper, marked));
		helper.assertTrue(HabitatKeepers.spots(keeper).size() == 1, "the spot wasn't marked: " + HabitatKeepers.spots(keeper));
		helper.succeedWhen(() -> {
			BlockPos planted = null;
			for (BlockPos p : BlockPos.betweenClosed(spot.offset(-HabitatKeepers.SAPLING_RADIUS, -1, -HabitatKeepers.SAPLING_RADIUS),
				spot.offset(HabitatKeepers.SAPLING_RADIUS, 1, HabitatKeepers.SAPLING_RADIUS))) {
				if (helper.getBlockState(p).is(block("saccharine_sapling"))) {
					planted = p.immutable();
				}
			}
			helper.assertTrue(planted != null, "no Saccharine sapling round the spot; she says " + said(helper, keeper));
			helper.assertTrue(((Container) helper.getBlockEntity(CHEST)).countItem(item("saccharine_sapling")) == 0
				&& ModAttachments.BUILDER_BAG.getOrCreate(keeper).stacks().stream().noneMatch(s -> s.is(item("saccharine_sapling"))),
				"the sapling was planted but one is still in the chest or her bag (copied?)");
		});
	}

	/**
	 * "Up to 3 Poké Snacks kept set out": with three marked spots and six snacks in the chest she sets one on each spot,
	 * and keeps the other three in the chest.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_hk_three"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_hk_three")
	public void threeSpotsGetThreeSnacksAndNoMore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper, new ItemStack(item("poke_snack"), 6));
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, new ArrayList<>(), "qa-three");
		List<BlockPos> spots = List.of(new BlockPos(8, 2, 10), new BlockPos(11, 2, 10), new BlockPos(13, 2, 13));
		for (BlockPos s : spots) {
			HabitatKeepers.markSpot(player, keeper, HabitatKeeperCompatTests.marker(helper, s.below()));
		}
		helper.assertTrue(HabitatKeepers.spots(keeper).size() == 3, "three spots marked: " + HabitatKeepers.spots(keeper));
		long[] allOut = {-1};
		helper.onEachTick(() -> {
			if (allOut[0] < 0 && spots.stream().allMatch(s -> snackAt(helper, s))) {
				allOut[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(allOut[0] >= 0, "not all three spots have a snack: " + spots.stream().map(s -> snackAt(helper, s)).toList()
				+ "; she says " + said(helper, keeper));
			helper.assertTrue(helper.getTick() - allOut[0] >= 400, "watching that she sets out no more");
			int out = 0;
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(15, 4, 15))) {
				if (snackAt(helper, p)) {
					out++;
				}
			}
			Container chest = helper.getBlockEntity(CHEST);
			int bag = ModAttachments.BUILDER_BAG.getOrCreate(keeper).stacks().stream().filter(s -> s.is(item("poke_snack"))).mapToInt(ItemStack::getCount).sum();
			helper.assertTrue(out == 3, out + " snacks set out, not 3");
			helper.assertTrue(chest.countItem(item("poke_snack")) + bag == 3, "chest " + chest.countItem(item("poke_snack")) + " + bag " + bag
				+ " snacks kept, not 3");
		});
	}

	// ------------------------------------------------------------------ sightings

	/**
	 * "Each new one is told to players in the village": a player by the pasture hears "<name> spotted a shiny Eevee east of
	 * her pasture" once; a player 300 blocks away, far from any village, hears nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_hk_audience"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_hk_audience")
	public void aSightingIsToldInTheVillageNotFarAway(GameTestHelper helper) {
		clearWilds(helper);
		ServerLevel level = helper.getLevel();
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		List<Component> near = new ArrayList<>();
		List<Component> far = new ArrayList<>();
		ServerPlayer nearby = QaCampHabitatCompatTests.listener(helper, near, "qa-near");
		nearby.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
		ServerPlayer away = QaCampHabitatCompatTests.listener(helper, far, "qa-far");
		away.moveTo(helper.absoluteVec(new Vec3(304.5, 2, 4.5)));
		wild(helper, "eevee shiny=yes level=5", new Vec3(PASTURE.getX() + 12.5, 2, PASTURE.getZ() + 0.5));
		List<HabitatKeepers.Sighting> found = HabitatKeepers.sight(level, keeper, helper.absolutePos(PASTURE));
		helper.assertTrue(found.size() == 1, "sighted " + found.size());
		String expected = keeper.getDisplayName().getString() + " spotted a shiny Eevee east of her pasture";
		helper.assertTrue(QaCampHabitatCompatTests.lines(near).stream().filter(expected::equals).count() == 1,
			"the player by the pasture heard " + QaCampHabitatCompatTests.lines(near) + ", expected once: " + expected);
		helper.assertTrue(far.stream().noneMatch(c -> c.getString().contains("spotted")), "a player 300 blocks away heard " + QaCampHabitatCompatTests.lines(far));
		helper.succeed();
	}

	/**
	 * "The hall's list shows her last five sightings": six shinies seen one after another leave the newest five, newest
	 * first, the first one gone; the list reads "Day N: spotted a shiny <species>"; the chronicle has all six, each once.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_hk_five"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_hk_five")
	public void theHallListKeepsHerLastFiveNewestFirst(GameTestHelper helper) {
		clearWilds(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hallRel = new BlockPos(1, 2, 8);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		BlockPos pasture = helper.absolutePos(PASTURE);
		List<String> species = List.of("Eevee", "Pikachu", "Squirtle", "Charmander", "Bulbasaur", "Pidgey");
		for (int i = 0; i < species.size(); i++) {
			wild(helper, species.get(i).toLowerCase() + " shiny=yes level=5", new Vec3(6.5 + i, 2, 12.5));
			List<HabitatKeepers.Sighting> found = HabitatKeepers.sight(level, keeper, pasture);
			helper.assertTrue(found.size() == 1, "look " + (i + 1) + " sighted " + found.size() + ", not just the shiny " + species.get(i));
		}
		List<HabitatKeepers.Sighting> kept = HabitatKeepers.sightings(keeper);
		helper.assertTrue(kept.size() == HabitatKeepers.SIGHTINGS_KEPT && HabitatKeepers.SIGHTINGS_KEPT == 5, "she keeps " + kept.size());
		List<String> what = kept.stream().map(s -> s.what().getString()).toList();
		List<String> newestFirst = new ArrayList<>();
		for (int i = species.size() - 1; i >= 1; i--) {
			newestFirst.add("a shiny " + species.get(i));
		}
		helper.assertTrue(what.equals(newestFirst), "her last five: " + what + ", expected " + newestFirst);
		long day = Chronicle.day(level);
		List<String> lines = HabitatKeepers.sightingLines(keeper).stream().map(Component::getString).toList();
		helper.assertTrue(lines.size() == 5 && lines.get(0).equals("Day " + day + ": spotted a shiny Pidgey"), "the hall's list reads " + lines);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(hallRel);
		List<String> chronicled = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.SIGHTING).map(e -> e.text().getString()).toList();
		helper.assertTrue(chronicled.size() == 6, "the chronicle has " + chronicled.size() + " sightings, not 6: " + chronicled);
		helper.assertTrue(new HashSet<>(chronicled).size() == 6, "a sighting chronicled twice: " + chronicled);
		helper.succeed();
	}

	/**
	 * The config keys, as a server owner writes them: habitatSightings false stops the sightings only (she still works);
	 * habitatKeepers false switches her off; both default to on.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_hk_config"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_hk_config")
	public void theConfigKeysSwitchSightingsAndKeepers(GameTestHelper helper) {
		clearWilds(helper);
		ServerLevel level = helper.getLevel();
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		BlockPos pasture = helper.absolutePos(PASTURE);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, seen, "qa-config");
		player.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
		wild(helper, "eevee shiny=yes level=5", new Vec3(PASTURE.getX() + 8.5, 2, PASTURE.getZ() + 0.5));
		PartnerShowsCompatTests.after(helper, () -> new WorkplaceConfig().apply());
		try {
			WorkplaceConfig defaults = new WorkplaceConfig();
			helper.assertTrue(defaults.habitatKeepers && defaults.habitatSightings, "the keys don't default to on");
			WorkplaceConfig.parse("{\"habitatSightings\": false}").apply();
			helper.assertTrue(HabitatKeepers.ENABLED && !HabitatKeepers.SIGHTINGS, "habitatSightings false: keepers " + HabitatKeepers.ENABLED
				+ ", sightings " + HabitatKeepers.SIGHTINGS);
			helper.assertTrue(HabitatKeepers.isHoney(new ItemStack(Items.HONEY_BOTTLE)), "habitatSightings false also stopped the job");
			helper.assertTrue(HabitatKeepers.sight(level, keeper, pasture).isEmpty(), "habitatSightings false: she still sighted the shiny");
			WorkplaceConfig.parse("{\"habitatKeepers\": false}").apply();
			helper.assertTrue(!HabitatKeepers.ENABLED && !HabitatKeepers.isHoney(new ItemStack(Items.HONEY_BOTTLE)), "habitatKeepers false didn't switch her off");
			helper.assertTrue(HabitatKeepers.sight(level, keeper, pasture).isEmpty(), "habitatKeepers false: she still sighted the shiny");
			helper.assertTrue(seen.stream().noneMatch(c -> c.getString().contains("spotted")), "told while switched off: " + QaCampHabitatCompatTests.lines(seen));
			new WorkplaceConfig().apply();
			helper.assertTrue(HabitatKeepers.sight(level, keeper, pasture).size() == 1, "switched back on, the shiny wasn't sighted (was it marked told while off?)");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the lure picker, trades

	/**
	 * Every lure in her picker has a readable name (no raw translation key), and picking an egg group (on whatever page it
	 * is) sets it and tells the player "<name> now sets out snacks for Field egg group".
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_hk_lures"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_hk_lures")
	public void everyLureHasANameAndAnEggGroupCanBePicked(GameTestHelper helper) {
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		List<String> lures = HabitatKeepers.lures();
		helper.assertTrue(lures.size() > 10, "only " + lures.size() + " lures: " + lures);
		for (String lure : lures) {
			String name = HabitatKeepers.lureName(lure).getString();
			helper.assertTrue(!name.contains("cobblemon.") && !name.contains("aliveworkplace.") && !name.contains("_"), lure + " reads \"" + name + "\"");
		}
		helper.assertTrue(HabitatKeepers.lureName("typing/fire").getString().equals("Fire type"), "fire reads " + HabitatKeepers.lureName("typing/fire").getString());
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaCampHabitatCompatTests.listener(helper, seen, "qa-lures");
		int[] page = {0};
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> HabitatKeepers.fillLures(m, keeper, page));
		int index = lures.indexOf("egg_group/field");
		helper.assertTrue(index >= 0, "no Field egg group lure: " + lures);
		for (int p = 0; p < index / 45; p++) {
			menu.press(53, player);
		}
		menu.press(index % 45, player);
		helper.assertTrue("egg_group/field".equals(HabitatKeepers.lure(keeper)), "her lure is " + HabitatKeepers.lure(keeper));
		String expected = keeper.getDisplayName().getString() + " now sets out snacks for Field egg group";
		helper.assertTrue(QaCampHabitatCompatTests.lines(seen).contains(expected), "the player saw " + QaCampHabitatCompatTests.lines(seen) + ", expected " + expected);
		helper.assertTrue(!HabitatKeepers.berries("egg_group/field").isEmpty(), "no berry seasons for the Field egg group");
		helper.succeed();
	}

	/** Her trades: Saccharine saplings and honey (Novice), Poké Snacks (Journeyman). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herTradesAreSaplingsAndHoneyThenSnacks(GameTestHelper helper) {
		var byLevel = VillagerTrades.TRADES.get(ModVillagers.HABITAT_KEEPER);
		helper.assertTrue(byLevel != null, "the Habitat Keeper has no trades");
		Villager v = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(1, 2, 1));
		Set<Item> novice = sold(v, byLevel.get(1));
		helper.assertTrue(novice.contains(item("saccharine_sapling")) && novice.contains(Items.HONEY_BOTTLE), "Novice sells " + novice);
		Set<Item> journeyman = sold(v, byLevel.get(3));
		helper.assertTrue(journeyman.contains(item("poke_snack")), "Journeyman sells " + journeyman);
		v.discard();
		helper.succeed();
	}

	/** What a level's listings sell for emeralds, over a few seeds. */
	static Set<Item> sold(Villager v, VillagerTrades.ItemListing[] listings) {
		Set<Item> out = new HashSet<>();
		if (listings == null) {
			return out;
		}
		for (VillagerTrades.ItemListing listing : listings) {
			// One random drawn from again and again (fresh randoms with nearby seeds give the same first boolean).
			RandomSource random = RandomSource.create(42);
			for (int draw = 0; draw < 32; draw++) {
				MerchantOffer offer = listing.getOffer(v, random);
				if (offer != null && offer.getBaseCostA().is(Items.EMERALD)) {
					out.add(offer.getResult().getItem());
				}
			}
		}
		return out;
	}
}
