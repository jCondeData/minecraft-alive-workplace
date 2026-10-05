package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compost.CompostWork;
import io.github.jcondedata.aliveworkplace.explore.ExplorerWork;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.nether.Netherworkers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.sift.SifterWork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.PortalShape;

/**
 * ROADMAP 28.6, partners at work for everyone else: each show starts from the worker's own work (the miner's dig, the
 * fisherman's cast, the scholar's study and the teacher's lesson, the nurse's cure, the composter, florist, beekeeper,
 * sifter, netherworker, cartographer and rancher), played by a pastured partner of the show's type.
 */
public class PartnersAllCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static ResourceLocation show(String name) {
		return AliveWorkplace.id(name);
	}

	private static String why(Set<ResourceLocation> started) {
		return started + " (" + PartnerShows.lastRefusal() + ")";
	}

	private static Set<ResourceLocation> partnered(GameTestHelper helper, Villager worker, BlockPos pasture, String species, Set<Item> carried) {
		PartnersAtWorkCompatTests.partner(helper, pasture, species);
		return PartnersAtWorkCompatTests.watch(helper, worker, species, carried);
	}

	private static Villager employ(GameTestHelper helper, BlockPos station, net.minecraft.world.level.block.Block block,
								  net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
								  VillagerProfession job, BlockPos chestAt, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(station, block);
		if (chestAt != null) {
			helper.setBlock(chestAt, Blocks.CHEST);
			Container c = helper.getBlockEntity(chestAt);
			for (int i = 0; i < chest.length; i++) {
				c.setItem(i, chest[i]);
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, station.offset(1, 0, 1));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(station), poi, job);
		return villager;
	}

	/** A Geodude digs alongside the miner, in a shower of the stone's crumbs. */
	//$ gametest_ticks_batch AREA '2400' '"partners_dig"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "partners_dig")
	public void aGeodudeDigsAlongsideTheMiner(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		BlockPos min = new BlockPos(6, 2, 6);
		BlockPos max = new BlockPos(8, 2, 8);
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			helper.setBlock(p, Blocks.STONE);
		}
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.MINERS_BENCH);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		((Container) helper.getBlockEntity(new BlockPos(2, 2, 4))).setItem(0, new ItemStack(Items.STONE_PICKAXE));
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Miners.employ(level, miner, helper.absolutePos(new BlockPos(2, 2, 2)));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(min), helper.absolutePos(max));
		QuarrySite site = Miners.start(level, miner, null, box, box.getYSpan());
		site.setStairs(false);
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = partnered(helper, miner, new BlockPos(13, 2, 3), "geodude", carried);
		helper.succeedWhen(() -> helper.assertTrue(started.contains(show("miner_partner_digs_alongside")), "no dig show: " + why(started)));
	}

	/** A Psyduck swims out round the fisherman's bobber. */
	//$ gametest_ticks_batch AREA '1200' '"partners_cast"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "partners_cast")
	public void aPsyduckSwimsRoundTheBobber(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(11, 1, 11))) {
			helper.setBlock(p, Blocks.WATER);
		}
		Villager fisher = employ(helper, new BlockPos(2, 2, 2), Blocks.BARREL, PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN, null);
		Fishers.start(level, fisher, new ItemStack(Items.FISHING_ROD));
		Set<ResourceLocation> started = partnered(helper, fisher, new BlockPos(13, 2, 3), "psyduck", new HashSet<>());
		helper.succeedWhen(() -> helper.assertTrue(started.contains(show("fisher_partner_swims_round_the_bobber")), "no cast show: " + why(started)));
	}

	/** A Ralts tends a pink pulse over the hurt villager the nurse heals. */
	//$ gametest_ticks_batch AREA '600' '"partners_cure"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "partners_cure")
	public void aRaltsSendsAPinkPulseOverTheNursesPatient(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		Villager nurse = employ(helper, new BlockPos(2, 2, 2), ModBlocks.NURSE_STATION, ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE, null);
		Villager patient = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		patient.setHealth(4f);
		Set<ResourceLocation> started = partnered(helper, nurse, new BlockPos(13, 2, 3), "ralts", new HashSet<>());
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("nurse_partner_sends_a_pink_pulse")), "no pulse show: " + why(started));
			helper.assertTrue(patient.getHealth() >= 8f, "patient at " + patient.getHealth());
		});
	}

	/** A Grimer stirs the compost while the composter works. */
	//$ gametest_ticks_batch AREA '1200' '"partners_compost"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "partners_compost")
	public void aGrimerStirsTheCompost(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		Villager composter = employ(helper, new BlockPos(2, 2, 2), ModBlocks.COMPOST_BIN, ModVillagers.COMPOST_BIN_POI, ModVillagers.COMPOSTER,
			new BlockPos(2, 2, 4), new ItemStack(Items.KELP, 20));
		Set<ResourceLocation> started = partnered(helper, composter, new BlockPos(13, 2, 3), "grimer", new HashSet<>());
		helper.succeedWhen(() -> helper.assertTrue(started.contains(show("composter_partner_stirs_the_compost")), "no compost show: " + why(started)));
	}

	/** A Bellsprout sprinkles over the florist's garden as it's bone-mealed. */
	//$ gametest_ticks_batch AREA '2400' '"partners_grow"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "partners_grow")
	public void aBellsproutSprinklesTheGarden(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(7, 1, 7), new BlockPos(15, 1, 10))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		Villager florist = employ(helper, new BlockPos(11, 2, 11), ModBlocks.FLOWER_STAND, ModVillagers.FLOWER_STAND_POI, ModVillagers.FLORIST,
			new BlockPos(11, 2, 13), new ItemStack(Items.BONE_MEAL, 16));
		Set<ResourceLocation> started = partnered(helper, florist, new BlockPos(3, 2, 3), "bellsprout", new HashSet<>());
		helper.succeedWhen(() -> helper.assertTrue(started.contains(show("florist_partner_sprinkles_the_garden")), "no garden show: " + why(started)));
	}

	/** A Combee circles the hive the beekeeper harvests. */
	//$ gametest_ticks_batch AREA '1600' '"partners_harvest"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_harvest")
	public void aCombeeCirclesTheHive(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		Villager keeper = employ(helper, new BlockPos(2, 2, 2), ModBlocks.APIARY, ModVillagers.APIARY_POI, ModVillagers.BEEKEEPER,
			new BlockPos(2, 2, 4), new ItemStack(Items.GLASS_BOTTLE, 2));
		BlockPos hive = new BlockPos(9, 3, 9);
		helper.setBlock(hive.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.setBlock(hive, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
		Set<ResourceLocation> started = partnered(helper, keeper, new BlockPos(13, 2, 3), "combee", new HashSet<>());
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("beekeeper_partner_circles_the_hive")), "no hive show: " + why(started));
			helper.assertTrue(helper.getBlockState(hive).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "the hive is still full");
		});
	}

	/** A Sandshrew shakes the gravel's dust from the sifter's sieve. */
	//$ gametest_ticks_batch AREA '1200' '"partners_sift"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "partners_sift")
	public void aSandshrewShakesTheSieve(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		Villager sifter = employ(helper, new BlockPos(2, 2, 2), ModBlocks.SIEVE, ModVillagers.SIEVE_POI, ModVillagers.SIFTER,
			new BlockPos(2, 2, 4), new ItemStack(Items.GRAVEL, 16));
		Set<ResourceLocation> started = partnered(helper, sifter, new BlockPos(13, 2, 3), "sandshrew", new HashSet<>());
		helper.succeedWhen(() -> helper.assertTrue(started.contains(show("sifter_partner_shakes_the_dust")), "no sift show: " + why(started)));
	}

	/** A Houndour walks the netherworker to the portal as they set out, flames at its feet. */
	//$ gametest_ticks_batch AREA '1800' '"partners_depart"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "partners_depart")
	public void aHoundourWalksTheNetherworkerToThePortal(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		int trip = Netherworkers.TRIP_TICKS;
		Netherworkers.TRIP_TICKS = 120;
		PartnerShowsCompatTests.after(helper, () -> Netherworkers.TRIP_TICKS = trip);
		int x = 9;
		int z = 9;
		for (int dx = -1; dx <= 2; dx++) {
			helper.setBlock(new BlockPos(x + dx, 2, z), Blocks.OBSIDIAN);
			helper.setBlock(new BlockPos(x + dx, 6, z), Blocks.OBSIDIAN);
		}
		for (int y = 3; y <= 5; y++) {
			helper.setBlock(new BlockPos(x - 1, y, z), Blocks.OBSIDIAN);
			helper.setBlock(new BlockPos(x + 2, y, z), Blocks.OBSIDIAN);
		}
		PortalShape.findEmptyPortalShape(helper.getLevel(), helper.absolutePos(new BlockPos(x, 3, z)), Direction.Axis.X)
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no portal shape")).createPortalBlocks();
		Villager worker = employ(helper, new BlockPos(2, 2, 2), ModBlocks.NETHER_BRAZIER, ModVillagers.NETHER_BRAZIER_POI, ModVillagers.NETHERWORKER,
			new BlockPos(2, 2, 4), new ItemStack(Items.BREAD, 3), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SWORD));
		Set<ResourceLocation> started = partnered(helper, worker, new BlockPos(13, 2, 3), "houndour", new HashSet<>());
		helper.succeedWhen(() -> helper.assertTrue(started.contains(show("netherworker_partner_walks_them_to_the_portal")), "no depart show: " + why(started)));
	}

	/** A Pidgey scouts ahead as the cartographer sets out. */
	//$ gametest_ticks_batch AREA '2400' '"partners_set_out"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "partners_set_out")
	public void aPidgeyScoutsAheadOfTheCartographer(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		int range = ExplorerWork.RANGE;
		int min = ExplorerWork.MIN_HOP;
		int max = ExplorerWork.MAX_HOP;
		ExplorerWork.RANGE = 8;
		ExplorerWork.MIN_HOP = 3;
		ExplorerWork.MAX_HOP = 6;
		PartnerShowsCompatTests.after(helper, () -> {
			ExplorerWork.RANGE = range;
			ExplorerWork.MIN_HOP = min;
			ExplorerWork.MAX_HOP = max;
		});
		Villager explorer = employ(helper, new BlockPos(11, 2, 11), Blocks.CARTOGRAPHY_TABLE, PoiTypes.CARTOGRAPHER, VillagerProfession.CARTOGRAPHER,
			new BlockPos(11, 2, 13), new ItemStack(Items.BREAD, 4));
		Set<ResourceLocation> started = partnered(helper, explorer, new BlockPos(3, 2, 3), "pidgey", new HashSet<>());
		helper.succeedWhen(() -> helper.assertTrue(started.contains(show("cartographer_partner_scouts_ahead")), "no scouting show: " + why(started)));
	}

	/** A Tauros walks beside the wild horse the rancher is breaking in. */
	//$ gametest_ticks_batch AREA '2400' '"partners_tame"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "partners_tame")
	public void aTaurosWalksBesideTheWildHorse(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(14, 2, 11));
		Villager rancher = employ(helper, new BlockPos(11, 2, 11), ModBlocks.FEED_TROUGH, ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER,
			new BlockPos(11, 2, 13), new ItemStack(Items.SADDLE));
		Set<ResourceLocation> started = partnered(helper, rancher, new BlockPos(3, 2, 3), "tauros", new HashSet<>());
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("rancher_partner_walks_beside_the_horse")), "no taming show: " + why(started));
			horse.discard();
		});
	}

	/** An Abra floats a book beside the teacher's desk during the lesson. */
	//$ gametest_ticks_batch AREA '1600' '"partners_lesson"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_lesson")
	public void anAbraFloatsABookDuringTheLesson(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		helper.setDayTime(3000);
		Villager teacher = employ(helper, new BlockPos(11, 2, 11), ModBlocks.TEACHERS_DESK, ModVillagers.TEACHERS_DESK_POI, ModVillagers.TEACHER, null);
		helper.setDayTime(3000);
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		child.setAge(-24000);
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = partnered(helper, teacher, new BlockPos(3, 2, 3), "abra", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("teacher_partner_floats_a_book")), "no lesson show: " + why(started));
			helper.assertTrue(carried.contains(Items.BOOK), "the Abra held " + carried + ", not a book");
		});
	}

	/** An Abra floats a book beside the scholar's desk while the research goes on. */
	//$ gametest_ticks_batch AREA '1200' '"partners_study"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "partners_study")
	public void anAbraFloatsABookBesideTheScholar(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		int radius = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 16;
		PartnerShowsCompatTests.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.research.Research.forget();
		});
		BlockPos hallAt = new BlockPos(17, 2, 17);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		// outside the area, so never cleared: removed when the test ends
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().removeBlock(helper.absolutePos(hallAt), false));
		Villager scholar = employ(helper, new BlockPos(8, 2, 8), ModBlocks.SCHOLARS_DESK, ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR,
			new BlockPos(8, 2, 10), new ItemStack(Items.PAPER, 20), new ItemStack(Items.EMERALD, 5));
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = partnered(helper, scholar, new BlockPos(3, 2, 3), "abra", carried);
		helper.runAfterDelay(3, () -> {
			var player = helper.makeMockServerPlayerInLevel();
			PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
			BlockPos hall = helper.absolutePos(hallAt);
			io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu = io.github.jcondedata.aliveworkplace.research.ResearchScreen.forTest(player, hall);
			menu.press(io.github.jcondedata.aliveworkplace.research.ResearchScreen.TOPIC_SLOTS[
				io.github.jcondedata.aliveworkplace.research.Research.Topic.SWIFT_HANDS.ordinal()], player);
			helper.succeedWhen(() -> {
				helper.assertTrue(started.contains(show("scholar_psychic_floats_a_book")), "no study show: " + why(started));
				helper.assertTrue(carried.contains(Items.BOOK), "the Abra held " + carried + ", not a book");
			});
		});
	}
}
