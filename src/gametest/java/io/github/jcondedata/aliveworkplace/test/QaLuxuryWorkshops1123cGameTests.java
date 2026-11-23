package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.jeweller.JewellerWork;
import io.github.jcondedata.aliveworkplace.printer.PrinterWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.tailor.TailorWork;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1123-0953): the luxury workshops' jobs (ROADMAP 34.13/34.14 with the jobs of 34.9-34.12 and the engine of
 * 34.5), written from the wiki's spec, filling what the earlier QA classes left: a second villager at a taken cauldron,
 * cartography table or stonecutter (the loom's is covered), a maker taking from another worker's chest but never a
 * builder's, a Novice leaving the makings of higher goods alone, the Tailor dyeing white wool, the cartography table
 * broken mid-job, and a maker whose own chest is full.
 */
public class QaLuxuryWorkshops1123cGameTests implements FabricGameTest {
	private static final String AREA = QaLuxuryJobsGameTests.AREA;
	private static final BlockPos STATION = QaLuxuryJobsGameTests.STATION;
	private static final BlockPos STANDING = new BlockPos(3, 2, 3);
	private static final BlockPos BESIDE = new BlockPos(1, 2, 3);
	/** Another worker's (a Vintner's) cauldron and chest, out of reach of the station's own chests. */
	private static final BlockPos MATE_STATION = new BlockPos(19, 2, 2);
	private static final BlockPos MATE_CHEST = new BlockPos(19, 2, 4);
	/** A builder's bench and chest, out of reach of both. */
	private static final BlockPos BENCH = new BlockPos(19, 2, 19);
	private static final BlockPos BENCH_CHEST = new BlockPos(19, 2, 17);

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	private static Villager jobless(GameTestHelper helper, BlockPos at, String called) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(called));
		return v;
	}

	private static Component said(List<Component> seen, String key) {
		return seen.stream().filter(c -> QaLuxuryJobsGameTests.key(c).equals(key)).findFirst().orElse(null);
	}

	private static boolean worksAt(GameTestHelper helper, Villager v, BlockPos rel) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(rel)::equals).isPresent();
	}

	private static int count(Container c, Item item) {
		return c.countItem(item);
	}

	record Taken(Block block, Item item, VillagerProfession job, String english) {
	}

	/**
	 * "Each building has exactly one job block" and "one worker per building": the Winery's cauldron, the Print Shop's
	 * cartography table and the Jeweler's stonecutter each take one worker. A second villager sneak-clicked with the
	 * job's item beside a taken block takes no job, the player is told in English that someone works at each one, and
	 * the first worker keeps the block.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLuxWs1123cTaken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLuxWs1123cTaken")
	public void aSecondVillagerAtATakenJobBlockIsTurnedAway(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaLuxuryJobsGameTests.listener(helper, seen);
			for (Taken t : List.of(
					new Taken(Blocks.CAULDRON, Items.APPLE, ModVillagers.VINTNER, "Vintner"),
					new Taken(Blocks.CARTOGRAPHY_TABLE, Items.INK_SAC, ModVillagers.PRINTER, "Printer"),
					new Taken(Blocks.STONECUTTER, Items.GOLD_NUGGET, ModVillagers.JEWELLER, "Jeweler"))) {
				helper.setBlock(STATION, t.block());
				Villager first = jobless(helper, STANDING, "Wren");
				QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(t.item(), 2));
				QaLuxuryJobsGameTests.click(player, first, true);
				helper.assertTrue(job(first) == t.job() && worksAt(helper, first, STATION),
					"the first villager isn't the " + t.block() + "'s " + t.english() + ": " + name(job(first)));
				Villager second = jobless(helper, BESIDE, "Ash");
				seen.clear();
				QaLuxuryJobsGameTests.hold(player, InteractionHand.OFF_HAND, new ItemStack(t.item(), 2));
				QaLuxuryJobsGameTests.click(player, second, true);
				helper.assertTrue(job(second) == VillagerProfession.NONE, "a second villager at a taken " + t.block() + " became a " + name(job(second)));
				Component taken = said(seen, "message.aliveworkplace.job.station_taken");
				helper.assertTrue(taken != null, "the player wasn't told the " + t.block() + " is taken: "
					+ seen.stream().map(QaLuxuryJobsGameTests::key).toList());
				String english = QaLuxuryJobsGameTests.english(taken);
				helper.assertTrue(english.startsWith("The " + t.english() + " job needs a free ") && english.endsWith("someone already works at each one here.")
					&& !english.contains("%"), t.block() + ": \"" + english + "\"");
				helper.assertTrue(said(seen, "message.aliveworkplace.job.chosen") == null, t.block() + ": told the second villager took the job");
				helper.assertTrue(job(first) == t.job() && worksAt(helper, first, STATION), "the first " + t.english() + " lost the " + t.block());
				first.discard();
				second.discard();
				helper.getLevel().getPoiManager().release(helper.absolutePos(STATION));
			}
			helper.succeed();
		});
	}

	/** A Vintner (NoAI) working at MATE_STATION with a chest beside it, and a NoAI builder at BENCH with its chest. */
	private static Container[] mateAndBuilder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// the village reach is 0 in GameTests (so neighbouring tests don't share); this test is alone in its batch
		int radius = io.github.jcondedata.aliveworkplace.work.Village.RADIUS;
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 48;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.work.Village.RADIUS = radius);
		helper.setBlock(MATE_STATION, Blocks.CAULDRON);
		helper.setBlock(MATE_CHEST, Blocks.CHEST);
		Villager vintner = helper.spawn(EntityType.VILLAGER, MATE_STATION.south());
		vintner.setNoAi(true);
		vintner.setVillagerData(vintner.getVillagerData().setProfession(ModVillagers.VINTNER).setLevel(1));
		vintner.refreshBrain(level);
		vintner.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(MATE_STATION)));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.north().east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		builder.setNoAi(true);
		return new Container[] {helper.getBlockEntity(MATE_CHEST), helper.getBlockEntity(BENCH_CHEST)};
	}

	/**
	 * 34.5: a maker takes "from their own chests, the store and the other workers' chests (never a builder's)". A Novice
	 * Jeweler with an empty chest; a Vintner's chest and a builder's chest each hold 2 amethyst shards and 2 copper
	 * ingots. The Jeweler makes one Amethyst Ring from the Vintner's, puts it in their own chest, and never touches the
	 * builder's.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaLuxWs1123cMates"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaLuxWs1123cMates")
	public void aMakerTakesFromAnotherWorkersChestButNeverABuilders(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.STONECUTTER);
		Container own = QaLuxuryJobsGameTests.chest(helper);
		Container[] others = mateAndBuilder(helper);
		Container mate = others[0];
		Container builders = others[1];
		mate.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 2));
		mate.setItem(1, new ItemStack(Items.COPPER_INGOT, 2));
		builders.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 2));
		builders.setItem(1, new ItemStack(Items.COPPER_INGOT, 2));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.JEWELLER, 1);
		QaLuxuryJobsGameTests.run(helper, v, new JewellerWork());
		helper.onEachTick(() -> helper.assertTrue(count(builders, Items.AMETHYST_SHARD) == 2 && count(builders, Items.COPPER_INGOT) == 2,
			"the Jeweler took from the builder's chest: " + count(builders, Items.AMETHYST_SHARD) + " shards, " + count(builders, Items.COPPER_INGOT) + " copper left"));
		long[] madeAt = {-1};
		helper.onEachTick(() -> {
			if (madeAt[0] < 0 && count(own, ModItems.AMETHYST_RING) >= 1) {
				madeAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(madeAt[0] >= 0, "no Amethyst Ring in the Jeweler's own chest yet (the Vintner's chest holds "
				+ count(mate, Items.AMETHYST_SHARD) + " shards, " + count(mate, Items.COPPER_INGOT) + " copper; the Jeweler sees stashes "
				+ io.github.jcondedata.aliveworkplace.work.Village.stashes(helper.getLevel(), v, helper.absolutePos(STATION), null)
				+ ", asks " + Requests.of(helper.getLevel(), v).stream().map(r -> r.what().getString()).toList() + ", bag "
				+ io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(v).stacks() + ", at " + v.blockPosition() + ", mates " + io.github.jcondedata.aliveworkplace.work.Village.mates(helper.getLevel(), v, helper.absolutePos(STATION))
				+ ", villagers " + helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4)).stream()
					.map(o -> name(job(o)) + "@" + o.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)).toList()
				+ ", mate chests " + io.github.jcondedata.aliveworkplace.build.SupplyContainers.find(helper.getLevel(), helper.absolutePos(MATE_STATION), null) + ")");
			helper.assertTrue(helper.getTick() > madeAt[0] + 400, "waiting a while after the ring to see the builder's chest stays whole");
			helper.assertTrue(count(own, ModItems.AMETHYST_RING) == 1, count(own, ModItems.AMETHYST_RING) + " rings, expected 1 (only the Vintner's makings)");
			helper.assertTrue(count(mate, Items.AMETHYST_SHARD) == 0 && count(mate, Items.COPPER_INGOT) == 0,
				"the Vintner's makings weren't the ring's: " + count(mate, Items.AMETHYST_SHARD) + " shards, " + count(mate, Items.COPPER_INGOT) + " copper left");
		});
	}

	/**
	 * 34.5/34.12: a maker makes "only from recipes up to their level". A Novice Jeweler whose chest holds a Brooch's and a
	 * Circlet's makings but only 1 amethyst shard and no copper takes nothing and asks for a ring's making (not a
	 * Brooch's or a Circlet's). Given the second shard and 2 copper, they make one Amethyst Ring and still leave the
	 * emeralds, gold nuggets and gold ingots alone.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaLuxWs1123cLevel"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaLuxWs1123cLevel")
	public void aNoviceLeavesTheMakingsOfHigherGoodsAlone(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.STONECUTTER);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(Items.EMERALD, 2));
		c.setItem(1, new ItemStack(Items.GOLD_NUGGET, 3));
		c.setItem(2, new ItemStack(Items.GOLD_INGOT, 2));
		c.setItem(3, new ItemStack(Items.AMETHYST_SHARD, 1));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.JEWELLER, 1);
		QaLuxuryJobsGameTests.run(helper, v, new JewellerWork());
		int[] step = {0};
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(count(c, Items.EMERALD) == 2 && count(c, Items.GOLD_NUGGET) == 3 && count(c, Items.GOLD_INGOT) == 2
				&& count(c, Items.AMETHYST_SHARD) == 1, "a Novice took makings: " + count(c, Items.EMERALD) + " emeralds, " + count(c, Items.GOLD_NUGGET)
				+ " nuggets, " + count(c, Items.GOLD_INGOT) + " ingots, " + count(c, Items.AMETHYST_SHARD) + " shards left");
			helper.assertTrue(count(c, ModItems.EMERALD_BROOCH) == 0 && count(c, ModItems.GOLD_CIRCLET) == 0, "a Novice made a Brooch or a Circlet");
			List<Requests.Request> asked = Requests.of(helper.getLevel(), v);
			String what = asked.stream().map(r -> r.what().getString() + " x" + r.count()).toList().toString();
			helper.assertTrue(!asked.isEmpty(), "the Novice asked for nothing");
			helper.assertTrue(asked.stream().allMatch(r -> r.accepts().test(new ItemStack(Items.AMETHYST_SHARD)) || r.accepts().test(new ItemStack(Items.COPPER_INGOT))),
				"the Novice asked for something not in a ring: " + what);
			helper.assertTrue(asked.stream().noneMatch(r -> r.accepts().test(new ItemStack(Items.EMERALD)) || r.accepts().test(new ItemStack(Items.GOLD_INGOT))),
				"the Novice asked for a higher good's making: " + what);
			c.setItem(3, new ItemStack(Items.AMETHYST_SHARD, 2));
			c.setItem(4, new ItemStack(Items.COPPER_INGOT, 2));
			step[0] = 1;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] == 1, "still checking the first part");
			helper.assertTrue(count(c, ModItems.AMETHYST_RING) == 1, count(c, ModItems.AMETHYST_RING) + " rings, expected 1");
			helper.assertTrue(count(c, Items.AMETHYST_SHARD) == 0 && count(c, Items.COPPER_INGOT) == 0, "the ring's makings left over");
			helper.assertTrue(count(c, Items.EMERALD) == 2 && count(c, Items.GOLD_NUGGET) == 3 && count(c, Items.GOLD_INGOT) == 2,
				"the higher goods' makings were touched");
		});
	}

	/**
	 * 34.10: "The Tailor ... dyes white wool themselves when dye is to hand." An Apprentice with 4 white wool, 4 red dye,
	 * a string and 2 gold nuggets (no leather, so no Work Clothes) sews one Fine Clothes from the wool dyed red.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaLuxWs1123cDye"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaLuxWs1123cDye")
	public void anApprenticeTailorDyesWhiteWoolForFineClothes(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.LOOM);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(Items.WHITE_WOOL, 4));
		c.setItem(1, new ItemStack(Items.RED_DYE, 4));
		c.setItem(2, new ItemStack(Items.STRING, 1));
		c.setItem(3, new ItemStack(Items.GOLD_NUGGET, 2));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.TAILOR, 2);
		QaLuxuryJobsGameTests.run(helper, v, new TailorWork());
		helper.succeedWhen(() -> {
			helper.assertTrue(count(c, ModItems.FINE_CLOTHES) == 1, count(c, ModItems.FINE_CLOTHES) + " Fine Clothes, expected 1 (chest: white wool "
				+ count(c, Items.WHITE_WOOL) + ", red dye " + count(c, Items.RED_DYE) + ", red wool " + count(c, Items.RED_WOOL) + ")");
			helper.assertTrue(count(c, Items.WHITE_WOOL) == 0 && count(c, Items.RED_WOOL) == 0, "wool left over: " + count(c, Items.WHITE_WOOL) + " white, "
				+ count(c, Items.RED_WOOL) + " red");
			helper.assertTrue(count(c, Items.STRING) == 0 && count(c, Items.GOLD_NUGGET) == 0, "string or nuggets left over");
			helper.assertTrue(count(c, Items.RED_DYE) < 4, "no dye was used");
		});
	}

	/**
	 * The job block broken mid-job: a Novice Printer has fetched for the Gazette (3 paper and an ink sac) when the
	 * cartography table is broken and not put back. Nothing is lost: the paper and ink are back in the chest or on the
	 * ground, or 2 Gazettes were printed from them.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaLuxWs1123cBroken"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaLuxWs1123cBroken")
	public void theCartographyTableBrokenMidJobLosesNothing(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.CARTOGRAPHY_TABLE);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(Items.PAPER, 3));
		c.setItem(1, new ItemStack(Items.INK_SAC, 1));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.PRINTER, 1);
		long[] brokenAt = {-1};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && QaLuxuryJobsGameTests.bag(v, Items.PAPER) > 0) {
				helper.setBlock(STATION, Blocks.AIR);
				brokenAt[0] = helper.getTick();
			}
		});
		QaLuxuryJobsGameTests.run(helper, v, new PrinterWork());
		helper.runAfterDelay(1800, () -> {
			helper.assertTrue(brokenAt[0] >= 0, "the Printer never fetched anything");
			int gazettes = c.countItem(ModItems.GAZETTE) + QaLuxuryJobsGameTests.ground(helper, ModItems.GAZETTE) + QaLuxuryJobsGameTests.bag(v, ModItems.GAZETTE);
			int paper = c.countItem(Items.PAPER) + QaLuxuryJobsGameTests.ground(helper, Items.PAPER);
			int ink = c.countItem(Items.INK_SAC) + QaLuxuryJobsGameTests.ground(helper, Items.INK_SAC);
			int printings = gazettes / 2;
			helper.assertTrue(gazettes % 2 == 0 && paper + 3 * printings == 3 && ink + printings == 1,
				"after the cartography table broke (tick " + brokenAt[0] + "): " + paper + " paper and " + ink + " ink in the chest or on the ground, "
					+ gazettes + " Gazettes; the bag holds " + io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(v).stacks());
			helper.succeed();
		});
	}

	/**
	 * Where it goes when there's no room: a Novice Jeweler's own chest is full (of cobblestone) and the ring's makings are
	 * in a Vintner's chest. Whatever the Jeweler does, nothing vanishes: every 2 shards and 2 copper gone became a ring
	 * somewhere (a chest, the bag or the ground), and the cobblestone is untouched.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaLuxWs1123cFull"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaLuxWs1123cFull")
	public void aJewelerWithAFullChestLosesNoRings(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.STONECUTTER);
		Container own = QaLuxuryJobsGameTests.chest(helper);
		for (int i = 0; i < own.getContainerSize(); i++) {
			own.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		Container mate = mateAndBuilder(helper)[0];
		mate.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 4));
		mate.setItem(1, new ItemStack(Items.COPPER_INGOT, 4));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.JEWELLER, 1);
		QaLuxuryJobsGameTests.run(helper, v, new JewellerWork());
		helper.runAfterDelay(1600, () -> {
			int rings = own.countItem(ModItems.AMETHYST_RING) + mate.countItem(ModItems.AMETHYST_RING) + QaLuxuryJobsGameTests.bag(v, ModItems.AMETHYST_RING)
				+ QaLuxuryJobsGameTests.ground(helper, ModItems.AMETHYST_RING);
			int shards = own.countItem(Items.AMETHYST_SHARD) + mate.countItem(Items.AMETHYST_SHARD) + QaLuxuryJobsGameTests.bag(v, Items.AMETHYST_SHARD)
				+ QaLuxuryJobsGameTests.ground(helper, Items.AMETHYST_SHARD);
			int copper = own.countItem(Items.COPPER_INGOT) + mate.countItem(Items.COPPER_INGOT) + QaLuxuryJobsGameTests.bag(v, Items.COPPER_INGOT)
				+ QaLuxuryJobsGameTests.ground(helper, Items.COPPER_INGOT);
			helper.assertTrue(shards + 2 * rings == 4 && copper + 2 * rings == 4,
				shards + " shards, " + copper + " copper and " + rings + " rings anywhere, from 4 shards and 4 copper; bag "
					+ io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(v).stacks());
			helper.assertTrue(own.countItem(Items.COBBLESTONE) == own.getContainerSize() * 64, "the full chest's cobblestone changed: " + own.countItem(Items.COBBLESTONE));
			helper.succeed();
		});
	}
}
