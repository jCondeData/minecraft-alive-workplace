package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.GardensPowers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.DeclaredPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPower;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Hanging Gardens' powers (ROADMAP 35.20). A farmer in a village with a caravan route to the Gardens' village brings
 * in a quarter more over 40 harvests (a fixed random for the seeds), as do an Orchard Keeper and the Gardens' own
 * farmers, each counted on their own, and nobody without a route; in the Gardens' village two babies are born in a day
 * with 12 meals in the store and two free beds (11 aren't enough; without the Gardens 16 and one a day); a farmer whose
 * composter is a terrace's takes on its fields by themselves and brings in the extra; a damaged Garden turns both off.
 * The file's numbers and the lines a player reads.
 */
public class WonderGardensGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:colony_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation GARDENS = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "hanging_gardens");
	/** The Gardens' village and a village with a caravan route to it, 35 blocks apart (the halls reach 16 in these tests). */
	private static final BlockPos HOME = new BlockPos(4, 2, 4);
	private static final BlockPos PARTNER = new BlockPos(39, 2, 39);

	/** A finished, standing Hanging Gardens of the village round {@code hall}, placed at {@code origin} (or nowhere); never counted in the test. */
	private static UUID gardens(GameTestHelper helper, BlockPos hall, @org.jetbrains.annotations.Nullable BlockPos origin) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().containsKey(GARDENS), "the Hanging Gardens aren't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(GARDENS, hall, null, "", 1);
		if (origin != null) {
			manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		}
		manager.finish(site.id(), Chronicle.day(level));
		standing(level, site.id(), true);
		UUID id = site.id();
		Leftovers.after(helper, () -> {
			manager.remove(id);
			Wonders.forgetPowers();
		});
		return id;
	}

	/** Sets the Gardens standing or damaged, counted far ahead so that no count runs in the test. */
	private static void standing(ServerLevel level, UUID id, boolean whole) {
		WonderManager.get(level).setKept(id, new WonderSite.Kept(whole ? 100 : 80, Chronicle.day(level) + 10_000, !whole, 0));
	}

	/** The halls reach 16 blocks, so that the two villages in one area are two. */
	private static void villages(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
	}

	private static Villager worker(GameTestHelper helper, BlockPos station, net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
		VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, station.east());
		v.setNoAi(true);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(station), poi, job);
		Leftovers.after(helper, () -> GardensPowers.forget(v));
		return v;
	}

	/** Counts, by kind, what {@code worker} brings in from {@code picks}: the picked and the Gardens' extra. */
	private static int[] bring(GameTestHelper helper, Villager worker, BlockPos at, List<List<ItemStack>> picks, Item kind) {
		int picked = 0;
		int extra = 0;
		for (List<ItemStack> pick : picks) {
			for (ItemStack s : pick) {
				picked += s.is(kind) ? s.getCount() : 0;
			}
			for (ItemStack s : GardensPowers.harvest(helper.getLevel(), worker, helper.absolutePos(at), pick)) {
				extra += s.is(kind) ? s.getCount() : 0;
			}
		}
		return new int[] {picked, extra};
	}

	/** 40 harvests of ripe wheat: a wheat each and 0 to 3 seeds, from a fixed random. */
	private static List<List<ItemStack>> wheat(long seed) {
		RandomSource random = RandomSource.create(seed);
		List<List<ItemStack>> out = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			out.add(List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS, random.nextInt(4))));
		}
		return out;
	}

	/**
	 * A farmer in a village with a caravan route to the Gardens' village: 40 harvests bring in 50 wheat, not 40, and a
	 * quarter more seeds (rounded down), 25% more in all; an Orchard Keeper there a quarter more berries; a farmer of the
	 * Gardens' own village the same. Each worker counts on their own (two farmers' two wheat each are no extra), and
	 * without the route nothing more.
	 */
	//$ gametest_ticks_batch AREA '100' '"gardensHarvest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "gardensHarvest")
	public void aFarmerInALinkedVillageBringsInAQuarterMore(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		villages(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HOME, ModBlocks.VILLAGE_HALL);
		helper.setBlock(PARTNER, ModBlocks.VILLAGE_HALL);
		BlockPos composter = PARTNER.offset(-3, 0, -3);
		BlockPos basket = PARTNER.offset(-6, 0, -1);
		BlockPos homeComposter = HOME.offset(3, 0, 3);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(basket, ModBlocks.FRUIT_BASKET);
		helper.setBlock(homeComposter, Blocks.COMPOSTER);
		helper.runAfterDelay(2, () -> {
			BlockPos home = helper.absolutePos(HOME);
			BlockPos partner = helper.absolutePos(PARTNER);
			Caravans.Data caravans = Caravans.Data.get(level);
			Leftovers.after(helper, () -> {
				if (caravans.routesFrom(partner).contains(home)) {
					caravans.toggleRoute(partner, home);
				}
			});
			Villager farmer = worker(helper, composter, PoiTypes.FARMER, VillagerProfession.FARMER);
			Villager keeper = worker(helper, basket, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
			Villager ownFarmer = worker(helper, homeComposter, PoiTypes.FARMER, VillagerProfession.FARMER);
			BlockPos field = composter.offset(-2, 0, 0);

			// Without the Gardens, and with them but no route: nothing more.
			helper.assertTrue(bring(helper, farmer, field, wheat(3520), Items.WHEAT)[1] == 0, "extra wheat without the Gardens");
			gardens(helper, home, null);
			helper.assertTrue(GardensPowers.extraEvery(level, home) == 4, "the Gardens' own village: one in " + GardensPowers.extraEvery(level, home));
			helper.assertTrue(bring(helper, farmer, field, wheat(3520), Items.WHEAT)[1] == 0 && GardensPowers.extraEvery(level, partner) == 0,
				"extra wheat with no caravan route to the Gardens");
			GardensPowers.forget(farmer);

			// The route: a quarter more over 40 harvests.
			helper.assertTrue(caravans.toggleRoute(partner, home), "the route didn't open");
			helper.assertTrue(Wonders.linked(level, partner).stream().anyMatch(g -> g.pos().equals(home)), "not linked: " + Wonders.linked(level, partner));
			List<List<ItemStack>> harvests = wheat(3520);
			int seeds = harvests.stream().mapToInt(h -> h.get(1).getCount()).sum();
			int[] wheat = bring(helper, farmer, field, harvests.subList(0, 20), Items.WHEAT);
			int[] wheatToo = bring(helper, farmer, field, harvests.subList(20, 40), Items.WHEAT);
			GardensPowers.forget(farmer);
			int[] seedsBrought = bring(helper, farmer, field, harvests, Items.WHEAT_SEEDS);
			int picked = wheat[0] + wheatToo[0] + seedsBrought[0];
			int brought = picked + wheat[1] + wheatToo[1] + seedsBrought[1];
			helper.assertTrue(wheat[0] + wheatToo[0] == 40 && wheat[1] + wheatToo[1] == 10, "40 harvests: " + (wheat[1] + wheatToo[1]) + " more wheat, not 10");
			helper.assertTrue(seedsBrought[0] == seeds && seedsBrought[1] == seeds / 4, seeds + " seeds: " + seedsBrought[1] + " more");
			helper.assertTrue(brought * 100 >= picked * 124 && brought * 100 <= picked * 125, "brought " + brought + " of " + picked + " picked: not a quarter more");

			// The Orchard Keeper there: a quarter more berries.
			RandomSource random = RandomSource.create(3521);
			List<List<ItemStack>> berries = new ArrayList<>();
			for (int i = 0; i < 40; i++) {
				berries.add(List.of(new ItemStack(Items.SWEET_BERRIES, 2 + random.nextInt(2))));
			}
			int[] picks = bring(helper, keeper, basket, berries, Items.SWEET_BERRIES);
			helper.assertTrue(picks[1] == picks[0] / 4 && picks[0] >= 80, "the keeper: " + picks[1] + " more of " + picks[0]);
			helper.assertTrue(GardensPowers.extras(keeper) == picks[1], "the keeper's count: " + GardensPowers.extras(keeper));

			// The Gardens' own village: the same.
			helper.assertTrue(bring(helper, ownFarmer, homeComposter.offset(0, 0, 2), wheat(1), Items.WHEAT)[1] == 10, "the Gardens' own farmer");

			// Counted per worker: two farmers' two wheat each are no extra one.
			GardensPowers.forget(farmer);
			GardensPowers.forget(ownFarmer);
			List<List<ItemStack>> two = List.of(List.of(new ItemStack(Items.WHEAT, 2)));
			helper.assertTrue(bring(helper, farmer, field, two, Items.WHEAT)[1] + bring(helper, ownFarmer, homeComposter, two, Items.WHEAT)[1] == 0,
				"two farmers' wheat counted together");
			helper.assertTrue(bring(helper, farmer, field, two, Items.WHEAT)[1] == 1, "the farmer's fourth wheat brought no extra one");

			// The route closed: nothing more.
			caravans.toggleRoute(partner, home);
			GardensPowers.forget(farmer);
			helper.assertTrue(bring(helper, farmer, field, wheat(3520), Items.WHEAT)[1] == 0, "extra wheat after the route closed");
			helper.succeed();
		});
	}

	private static void bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	/** The hall after its first round: a Hamlet with nothing in force, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(hall));
		entity.setRank(VillageRanks.Rank.HAMLET);
		entity.setEdicts(List.of());
		entity.setReforms(List.of());
		entity.setExtraMeals(0);
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return entity;
	}

	/** Puts {@code meals} bread in the store and has a baby (the last one {@code lastBirth}); returns it. */
	private static Villager born(GameTestHelper helper, Container chest, VillageNeeds.Needs needs, int meals, long lastBirth) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HOME);
		chest.setItem(0, new ItemStack(Items.BREAD, meals));
		Villager baby = VillageGrowth.grow(level, hall, needs, lastBirth);
		helper.assertTrue(baby != null && baby.isBaby(), "no baby with " + meals + " meals: " + VillageGrowth.blocker(level, hall, needs, lastBirth));
		baby.setNoAi(true);
		return baby;
	}

	/**
	 * Births in the Gardens' village: without them a baby needs 16 meals and one comes a day; with them 12 (11 aren't
	 * enough), and two are born in one day with the meals and two free beds, not a third sooner.
	 */
	//$ gametest_ticks_batch BIG '100' '"gardensBirths"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "gardensBirths")
	public void twoBabiesADayOnTwelveMeals(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		villages(helper);
		BlockPos chestAt = new BlockPos(10, 2, 6);
		helper.setBlock(new BlockPos(10, 2, 4), ModBlocks.STOREHOUSE); // the store, within the hall's 16 blocks
		helper.setBlock(chestAt, Blocks.CHEST);
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8)).setNoAi(true);
		helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8)).setNoAi(true);
		bed(helper, new BlockPos(3, 2, 15));
		bed(helper, new BlockPos(5, 2, 15));
		bed(helper, new BlockPos(7, 2, 15)); // the third baby's bed: each takes one of their own (B206)
		helper.setBlock(HOME, ModBlocks.VILLAGE_HALL);
		VillageNeeds.Needs happy = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HOME);
			ready(helper, HOME);
			Container chest = helper.getBlockEntity(chestAt);
			long now = level.getGameTime();

			// Without the Gardens: 16 meals, a day apart.
			chest.setItem(0, new ItemStack(Items.BREAD, 12));
			helper.assertTrue(VillageGrowth.foodNeeded(level, hall) == 16 && VillageGrowth.every(level, hall) == VillageNeeds.DAY
				&& VillageGrowth.blocker(level, hall, happy, 0) == VillageGrowth.Blocker.FOOD, "without the Gardens: " + VillageGrowth.foodNeeded(level, hall)
				+ " meals, " + VillageGrowth.every(level, hall) + " ticks");

			// With them: 12 meals, half a day apart.
			gardens(helper, hall, null);
			helper.assertTrue(VillageGrowth.foodNeeded(level, hall) == 12 && VillageGrowth.every(level, hall) == VillageNeeds.DAY / 2,
				"with the Gardens: " + VillageGrowth.foodNeeded(level, hall) + " meals, " + VillageGrowth.every(level, hall) + " ticks");
			chest.setItem(0, new ItemStack(Items.BREAD, 11));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, 0) == VillageGrowth.Blocker.FOOD, "11 meals were enough");
			Villager first = born(helper, chest, happy, 12, 0);
			long firstAt = now - VillageNeeds.DAY / 2; // the hall's round notes the birth; half a day on, the second
			chest.setItem(0, new ItemStack(Items.BREAD, 12));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now) == VillageGrowth.Blocker.TOO_SOON, "a second baby at once: "
				+ VillageGrowth.blocker(level, hall, happy, now));
			Villager second = born(helper, chest, happy, 12, firstAt);
			helper.assertTrue(first != second && second.isBaby(), "two babies");
			chest.setItem(0, new ItemStack(Items.BREAD, 12));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - VillageNeeds.DAY / 2 + 1) == VillageGrowth.Blocker.TOO_SOON,
				"a third sooner than half a day after the second");
			first.discard();
			second.discard();
			helper.succeed();
		});
	}

	/**
	 * The Gardens damaged (under wonderStanding): the linked farmer brings in no more, a baby needs 16 meals again and comes
	 * once a day; repaired, both back.
	 */
	//$ gametest_ticks_batch BIG '100' '"gardensDamaged"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "gardensDamaged")
	public void aDamagedGardenTurnsBothOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		villages(helper);
		BlockPos composter = HOME.offset(4, 0, 0);
		helper.setBlock(HOME, ModBlocks.VILLAGE_HALL);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HOME);
			ready(helper, HOME);
			Villager farmer = worker(helper, composter, PoiTypes.FARMER, VillagerProfession.FARMER);
			UUID id = gardens(helper, hall, null);
			helper.assertTrue(bring(helper, farmer, composter, wheat(7), Items.WHEAT)[1] == 10 && VillageGrowth.foodNeeded(level, hall) == 12
				&& VillageGrowth.every(level, hall) == VillageNeeds.DAY / 2, "standing: both on");
			standing(level, id, false);
			GardensPowers.forget(farmer);
			helper.assertTrue(Wonders.power(level, hall, GardensPowers.HARVEST).isEmpty() && Wonders.power(level, hall, GardensPowers.BIRTHS).isEmpty(), "damaged, still granted");
			helper.assertTrue(bring(helper, farmer, composter, wheat(7), Items.WHEAT)[1] == 0, "damaged: extra wheat");
			helper.assertTrue(VillageGrowth.foodNeeded(level, hall) == 16 && VillageGrowth.every(level, hall) == VillageNeeds.DAY,
				"damaged: " + VillageGrowth.foodNeeded(level, hall) + " meals, " + VillageGrowth.every(level, hall) + " ticks");
			standing(level, id, true);
			helper.assertTrue(bring(helper, farmer, composter, wheat(7), Items.WHEAT)[1] == 10 && VillageGrowth.foodNeeded(level, hall) == 12,
				"repaired: not back");
			helper.succeed();
		});
	}

	/**
	 * The whole Gardens built in the area (the drawing's blocks set, the falls running): each of its four farm corners
	 * (a composter and a chest) has its terrace's field within a farmer's reach, and a farmer whose composter is the
	 * first terrace's takes on its field by themselves, harvests it and brings in the Gardens' extra.
	 */
	//$ gametest_ticks_batch AREA '2400' '"gardensTerrace"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "gardensTerrace")
	public void aVillageFarmerTakesUpATerracesFarm(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos origin = helper.absolutePos(new BlockPos(0, 2, 0));
		BlockPos hall = origin.offset(-3, 0, 2);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		UUID id = gardens(helper, hall, origin);
		WonderSite site = WonderManager.get(level).site(id).orElseThrow();
		List<WonderUpkeep.Block> blocks = new ArrayList<>(WonderUpkeep.blocks(level, site));
		helper.assertTrue(blocks.size() > 15_000, "the drawing: " + blocks.size() + " blocks");
		blocks.sort(Comparator.comparingInt(b -> b.pos().getY()));
		BoundingBox built = BoundingBox.encapsulatingPositions(blocks.stream().map(WonderUpkeep.Block::pos).toList()).orElseThrow().inflatedBy(3);
		Leftovers.after(helper, () -> {
			for (BlockPos p : BlockPos.betweenClosed(built.minX(), origin.getY(), built.minZ(), built.maxX(), built.maxY(), built.maxZ())) {
				if (!level.getBlockState(p).isAir()) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
			level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
		});
		for (WonderUpkeep.Block b : blocks) {
			level.setBlock(b.pos(), b.state(), 2);
		}
		// The four farm corners: a composter, a chest by it, and the terrace's field within a farmer's reach.
		List<BlockPos> composters = blocks.stream().filter(b -> b.state().is(Blocks.COMPOSTER)).map(WonderUpkeep.Block::pos).toList();
		helper.assertTrue(composters.size() == 4, "the Gardens' farm corners: " + composters.size());
		for (BlockPos c : composters) {
			helper.assertTrue(blocks.stream().anyMatch(b -> b.state().is(Blocks.CHEST) && b.pos().distManhattan(c) <= 2), "no chest by the composter at " + c);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.farm.Fields.farmNear(level, c).isPresent(), "no field within reach of the composter at " + c);
		}
		BlockPos composter = origin.offset(3, 7, 5); // the first terrace's
		BlockPos firstField = origin.offset(5, 6, 5);
		helper.assertTrue(composters.contains(composter) && level.getBlockState(firstField).is(Blocks.FARMLAND), "the first terrace's farm corner moved");
		net.minecraft.world.level.block.state.BlockState composterState = level.getBlockState(composter);
		level.setBlockAndUpdate(composter, Blocks.AIR.defaultBlockState()); // set again, so its point of interest is there
		level.setBlockAndUpdate(composter, composterState);
		Villager farmer = EntityType.VILLAGER.create(level);
		farmer.moveTo(composter.getX() + 0.5, composter.getY() + 1, composter.getZ() + 0.5, 0, 0);
		level.addFreshEntity(farmer);
		Jobs.employ(level, farmer, composter, PoiTypes.FARMER, VillagerProfession.FARMER);
		farmer.getInventory().addItem(new ItemStack(Items.BREAD, 9)); // food to share already: the harvest goes to the chest
		Leftovers.after(helper, () -> {
			GardensPowers.forget(farmer);
			farmer.discard();
		});
		helper.succeedWhen(() -> {
			FieldJob job = ModAttachments.FARM_FIELD.get(farmer);
			helper.assertTrue(job != null && job.adopted(), "the farmer didn't take on the terrace's farm");
			helper.assertTrue(job.box().isInside(firstField), "took on " + job.box() + ", not the first terrace's field at " + firstField);
			int harvested = ModAttachments.FARM_HARVESTED.getOrElse(farmer, 0);
			helper.assertTrue(harvested >= 4 && GardensPowers.extras(farmer) > 0, harvested + " harvested, " + GardensPowers.extras(farmer) + " more brought in");
		});
	}

	/** The file's numbers (a quarter more across the realm; 12 meals, two a day at home) and the lines the hall's Wonders page shows. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGardensFileAndItsLines(GameTestHelper helper) {
		Wonder wonder = Wonders.loaded().get(GARDENS);
		helper.assertTrue(wonder != null, "the Hanging Gardens aren't loaded: " + Wonders.problems());
		WonderPower harvest = wonder.powers().stream().map(Wonder.Power::power).filter(p -> p.type().equals(GardensPowers.HARVEST)).findFirst().orElse(null);
		WonderPower births = wonder.powers().stream().map(Wonder.Power::power).filter(p -> p.type().equals(GardensPowers.BIRTHS)).findFirst().orElse(null);
		helper.assertTrue(harvest instanceof DeclaredPower h && h.realm() && h.number("extra_every", 0) == 4, "harvest: " + harvest);
		helper.assertTrue(births instanceof DeclaredPower b && !b.realm() && b.number("meals", 0) == 12 && b.number("per_day", 0) == 2, "births: " + births);
		String harvestLine = harvest.describe().getString();
		String birthsLine = births.describe().getString();
		helper.assertTrue(harvestLine.equals("Farmers and orchard keepers across the realm bring in a quarter more: one more of each crop for every four they pick"),
			"the harvest line: " + harvestLine);
		helper.assertTrue(birthsLine.equals("The village grows faster: a baby needs 12 meals in the store, not 16, and up to two are born a day"),
			"the births line: " + birthsLine);
		helper.succeed();
	}
}
