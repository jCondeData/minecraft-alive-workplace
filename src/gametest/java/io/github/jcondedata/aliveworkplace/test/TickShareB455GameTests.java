package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.farm.FieldWork;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.hall.Curfew;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B455 (our share of the tick on a big village): a farmer settles on one thing to do, and the edicts' per-villager
 * answers are kept on the villager.
 * <ul>
 *   <li>a farmer with harvest in the bag and nothing left to tend carries it to the chest once and then rests, looking
 *   for work every half second (not every tick), and sows soon after seeds reach his bag;</li>
 *   <li>a carrot farmer with ground to till and no hoe anywhere leaves the carrots in the chest (he took them out and
 *   put them back for as long as the ground stayed untilled), and tills and sows once a hoe is in the chest;</li>
 *   <li>the hall a villager lives by is looked up once for many asks, a hall broken shows in the very next ask and a
 *   hall placed within the time the answer is kept; Curfew's "kept in" follows the edict at once;</li>
 *   <li>how long the work gate's and Curfew's checks take per ask is written to the log.</li>
 * </ul>
 */
public class TickShareB455GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation CURFEW = AliveWorkplace.id("curfew");

	private static Villager farmer(GameTestHelper helper, BlockPos composter, BlockPos fieldFrom, BlockPos fieldTo) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, farmer, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, farmer, BoundingBox.fromCorners(helper.absolutePos(fieldFrom), helper.absolutePos(fieldTo)));
		return farmer;
	}

	/** Harvest in the bag and nothing to tend: one trip to the chest, then rest (never a trip every other tick). */
	//$ gametest_ticks_batch AREA '1200' '"b455_farmer_settles"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "b455_farmer_settles")
	public void anIdleFarmerWithHarvestDepositsOnceAndRests(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos chestAt = new BlockPos(2, 2, 4);
		helper.setDayTime(2000);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		Villager farmer = farmer(helper, new BlockPos(2, 2, 2), new BlockPos(8, 1, 8), new BlockPos(9, 1, 9));
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(farmer);
		bag.addAll(Items.WHEAT, 12);
		bag.addAll(Items.WHEAT_SEEDS, 5);
		long deposits = FieldWork.deposits, fetches = FieldWork.fetches;
		long[] looks = new long[1];
		helper.runAfterDelay(400, () -> {
			helper.setDayTime(2000);
			looks[0] = FieldWork.idleLooks;
			helper.assertTrue(chest.countItem(Items.WHEAT) == 12, "the wheat isn't in the chest after 400 ticks: " + chest.countItem(Items.WHEAT));
			helper.assertTrue(bag.count(Items.WHEAT_SEEDS) == 5, "the seeds he keeps: " + bag.count(Items.WHEAT_SEEDS));
			helper.assertTrue(FieldWork.deposits - deposits == 1, "trips to the chest for one bag of wheat: " + (FieldWork.deposits - deposits));
		});
		helper.runAfterDelay(800, () -> {
			helper.assertTrue(FieldWork.deposits - deposits == 1 && FieldWork.fetches == fetches, "an idle farmer went on to the chest: "
				+ (FieldWork.deposits - deposits) + " trips with the bag, " + (FieldWork.fetches - fetches) + " to take from it");
			// at rest he goes through the field's tasks every half second (and after each look over the field), not every tick
			long looked = FieldWork.idleLooks - looks[0];
			helper.assertTrue(looked >= 400 / FieldWork.IDLE_LOOK_TICKS / 2, "the farmer at rest looked for work " + looked + " times in 400 ticks: the test doesn't reach him");
			helper.assertTrue(looked <= 400 / FieldWork.IDLE_LOOK_TICKS + 8, "the farmer at rest looked for work " + looked + " times in 400 ticks (every "
				+ FieldWork.IDLE_LOOK_TICKS + " ticks is " + 400 / FieldWork.IDLE_LOOK_TICKS + ")");
			helper.succeed();
		});
	}

	/**
	 * Carrots in the chest, dirt to till and no hoe: the carrots stay in the chest (taken out, they are harvest to carry
	 * back, and the ground still wants seeds). With a hoe in the chest he takes both, tills and sows.
	 */
	//$ gametest_ticks_batch AREA '4000' '"b455_farmer_no_hoe"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "b455_farmer_no_hoe")
	public void aCarrotFarmerWithoutAHoeLeavesTheCarrotsInTheChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos chestAt = new BlockPos(2, 2, 4);
		helper.setDayTime(2000);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.CARROT, 16));
		BlockPos from = new BlockPos(8, 1, 8), to = new BlockPos(9, 1, 9);
		for (BlockPos p : BlockPos.betweenClosed(from, to)) {
			helper.setBlock(p, Blocks.DIRT);
		}
		Villager farmer = farmer(helper, new BlockPos(2, 2, 2), from, to);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(farmer);
		long deposits = FieldWork.deposits, fetches = FieldWork.fetches;
		helper.runAfterDelay(600, () -> {
			helper.setDayTime(2000);
			long trips = FieldWork.deposits - deposits + FieldWork.fetches - fetches;
			helper.assertTrue(trips <= 2, "a farmer with no hoe went to the chest " + trips + " times in 600 ticks ("
				+ (FieldWork.fetches - fetches) + " to take carrots, " + (FieldWork.deposits - deposits) + " to put them back)");
			helper.assertTrue(chest.countItem(Items.CARROT) + bag.count(Items.CARROT) == 16, "carrots lost: " + chest.countItem(Items.CARROT) + " in the chest, "
				+ bag.count(Items.CARROT) + " in the bag");
			chest.setItem(1, new ItemStack(Items.WOODEN_HOE));
			helper.succeedWhen(() -> {
				helper.setDayTime(2000);
				int sown = 0;
				for (BlockPos p : BlockPos.betweenClosed(from, to)) {
					if (helper.getBlockState(p).getBlock() instanceof FarmBlock && helper.getBlockState(p.above()).is(Blocks.CARROTS)) {
						sown++;
					}
				}
				helper.assertTrue(sown == 4, "with a hoe in the chest " + sown + " of 4 spots are tilled and sown with carrots");
			});
		});
	}

	/**
	 * A farmer at rest for want of seeds (bare farmland, none in the bag or the chest) still sows soon after seeds reach
	 * his bag: the wait between his looks is half a second, and the next look finds them.
	 */
	//$ gametest_ticks_batch AREA '1200' '"b455_farmer_wakes"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "b455_farmer_wakes")
	public void aFarmerAtRestSowsSoonAfterSeedsReachHisBag(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		BlockPos soil = new BlockPos(5, 1, 5);
		helper.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
		Villager farmer = farmer(helper, new BlockPos(2, 2, 2), soil, soil);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(farmer);
		helper.runAfterDelay(300, () -> {
			helper.setDayTime(2000);
			helper.assertTrue(helper.getBlockState(soil.above()).isAir(), "sown with no seeds: " + helper.getBlockState(soil.above()));
			long looks = FieldWork.idleLooks;
			helper.runAfterDelay(100, () -> {
				long looked = FieldWork.idleLooks - looks;
				helper.assertTrue(looked >= 5 && looked <= 100 / FieldWork.IDLE_LOOK_TICKS + 4, "a farmer waiting for seeds looked for work " + looked + " times in 100 ticks");
				bag.addAll(Items.WHEAT_SEEDS, 4);
				long given = helper.getLevel().getGameTime();
				helper.succeedWhen(() -> {
					helper.assertTrue(helper.getBlockState(soil.above()).is(Blocks.WHEAT), "the farmland isn't sown yet");
					helper.assertTrue(helper.getLevel().getGameTime() - given <= 200, "sown " + (helper.getLevel().getGameTime() - given) + " ticks after the seeds came");
				});
			});
		});
	}

	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			helper.getLevel().setDayTime(time);
			VillageNeeds.forget();
			CivicEffects.forget();
			Conscription.forget();
			Moods.forget();
		});
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.VILLAGE);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		hall.setFestivalDay(-1);
		VillageNeeds.forget();
		CivicEffects.forget();
		Conscription.forget();
		Moods.forget();
		return hall;
	}

	/**
	 * The hall a villager lives by is looked up once for a thousand asks; a hall broken shows in the next ask, a hall
	 * placed within the 200 ticks the answer is kept; "kept in" follows the Curfew edict the moment it is passed and
	 * repealed; each villager has an answer of their own.
	 */
	//$ gametest_ticks_batch AREA '600' '"b455_hall_kept"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "b455_hall_kept")
	public void aVillagersHallIsKeptAndStillFollowsTheWorld(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper);
			Villager one = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
			Villager two = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 5));
			Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 5));
			child.setBaby(true);
			long looks = CivicEffects.hallLooks;
			for (int i = 0; i < 1000; i++) {
				helper.assertTrue(CivicEffects.hallOf(one) == entity, "the villager's hall, ask " + i + ": " + CivicEffects.hallOf(one));
				helper.assertTrue(!Conscription.workStopped(one), "work stopped with no edict, ask " + i);
			}
			helper.assertTrue(CivicEffects.hallLooks - looks == 1, "the hall was looked up " + (CivicEffects.hallLooks - looks) + " times for 1,000 asks");
			helper.assertTrue(CivicEffects.hallOf(two) == entity && CivicEffects.hallLooks - looks == 2, "the second villager's own look: "
				+ (CivicEffects.hallLooks - looks));
			// Curfew, at night: passed and repealed, the answer follows at once (the hall is kept, not its edicts)
			level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + Curfew.DUSK + 10);
			helper.assertTrue(!Curfew.keepsIn(one), "kept in at night with no edict");
			entity.setEdicts(List.of(new Edicts.InForce(CURFEW.toString(), Chronicle.day(level))));
			helper.assertTrue(Curfew.keepsIn(one) && Curfew.keepsIn(two), "not kept in at night under Curfew");
			helper.assertTrue(!Curfew.keepsIn(child), "a child kept in by the edict (children keep their own hours)");
			entity.setEdicts(List.of());
			helper.assertTrue(!Curfew.keepsIn(one), "kept in after the repeal");
			entity.setEdicts(List.of(new Edicts.InForce(CURFEW.toString(), Chronicle.day(level))));
			// the hall broken: the very next ask
			helper.setBlock(HALL, Blocks.AIR);
			helper.assertTrue(CivicEffects.hallOf(one) == null && !Curfew.keepsIn(one), "a broken hall still counts: " + CivicEffects.hallOf(one));
			// a hall placed: within the time the answer is kept
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			long placed = level.getGameTime();
			helper.succeedWhen(() -> {
				VillageHallBlockEntity now = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
				helper.assertTrue(now != null && now != entity && CivicEffects.hallOf(one) == now, "the new hall isn't the villager's yet");
				helper.assertTrue(level.getGameTime() - placed <= 201, "the new hall took " + (level.getGameTime() - placed) + " ticks to count");
			});
		});
	}

	/**
	 * How long the two per-villager, per-tick edict checks take (the work gate, and Curfew's "kept in" at night under
	 * the edict), over 35 villagers: written to the log, and each must give the same answer every time.
	 */
	//$ gametest_ticks_batch AREA '200' '"b455_edict_checks"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b455_edict_checks")
	public void theEdictChecksPerVillagerAreTimed(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper);
			List<Villager> villagers = new ArrayList<>();
			for (int i = 0; i < 35; i++) {
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3 + i % 7 * 2, 2, 3 + i / 7 * 2));
				v.setNoAi(true);
				villagers.add(v);
			}
			level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + Curfew.DUSK + 10);
			entity.setEdicts(List.of(new Edicts.InForce(CURFEW.toString(), Chronicle.day(level))));
			int rounds = 20_000;
			long gate = 0, kept = 0;
			for (int pass = 0; pass < 3; pass++) { // the last pass counts: the first two warm the code up
				long t0 = System.nanoTime();
				int stopped = 0;
				for (int r = 0; r < rounds; r++) {
					for (Villager v : villagers) {
						if (Conscription.workStopped(v)) {
							stopped++;
						}
					}
				}
				long t1 = System.nanoTime();
				int in = 0;
				for (int r = 0; r < rounds; r++) {
					for (Villager v : villagers) {
						if (Curfew.keepsIn(v)) {
							in++;
						}
					}
				}
				long t2 = System.nanoTime();
				helper.assertTrue(stopped == 0, "work stopped with no raid: " + stopped);
				// every ask of a villager gives the same answer (one by a neighbouring test's leftover hall may live elsewhere)
				helper.assertTrue(in > 0 && in % rounds == 0, "kept in under Curfew at night: " + in + " of " + rounds * villagers.size()
					+ " asks, not the same answer for a villager every time");
				gate = (t1 - t0) / ((long) rounds * villagers.size());
				kept = (t2 - t1) / ((long) rounds * villagers.size());
			}
			AliveWorkplace.LOG.info("B455 edict checks, per ask over 35 villagers: work gate {} ns, Curfew kept-in {} ns", gate, kept);
			helper.succeed();
		});
	}
}
