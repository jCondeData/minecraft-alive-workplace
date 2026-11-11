package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B1171 (owner, 2026-11-11, live server on 0.154.0): the Carpenter and the Mason made a builder's things, carried them
 * to "the builder's house", the builder never got them, and they made and carried them again and again. Two causes in
 * CrafterWork: what was made went to the chests by the table of whichever builder was looked at (a helper on the build,
 * or the lead of a Wonder's part), not to the chests that feed the build, so it never came off the build's list; and a
 * request just filled stayed on the builder's list until his next count, a few seconds after the crafter's next look.
 * Expected: what a crafter makes reaches the chests the build takes from, and is made once. The village is
 * QaStalledCrafting1109bGameTests' (a real Storehouse and its chest, the builder's table by it, a blueprint site, the
 * crafters at their own workstations). Each test has its own batch: crafters' claims and the village are shared state.
 */
public class CraftersDeliverOnceB1171GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	/** A second builder's table and chest: out of reach of the first builder's table, the Storehouse and the Carpenter's bench. */
	static final BlockPos HELPER_BENCH = new BlockPos(12, 2, 19);
	static final BlockPos HELPER_CHEST = new BlockPos(12, 2, 20);
	/** How long after the last block is up a second batch is watched for (a crafter looks every 100 ticks). */
	static final int SETTLE = 500;

	/**
	 * A builder short of 4 oak stairs and 4 stone brick slabs, a Storehouse with planks and bricks for many more, a
	 * Carpenter, a Mason and a Porter: the stairs and slabs are made once (4 each, never more at any tick), reach the
	 * builder and are built, and nothing more is made in the 500 ticks after.
	 */
	//$ gametest_ticks_batch BIG '3600' '"b1171MadeOnce"'
	@GameTest(template = BIG, timeoutTicks = 3600, batch = "b1171MadeOnce")
	public void stairsAndSlabsAreMadeOnceAndReachTheBuilder(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(stairRow());
		QaStalledCrafting1109bGameTests.slabRow().forEach((p, s) -> blocks.put(p.offset(0, 0, 4), s));
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "b1171once", blocks, true, true,
			List.of(new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.STONE_BRICKS, 16)), false, false, false);
		Villager porter = helper.spawn(EntityType.VILLAGER, QaStalledCrafting1109bGameTests.STOREHOUSE.north());
		Porters.employ(run.level(), porter, helper.absolutePos(QaStalledCrafting1109bGameTests.STOREHOUSE));
		// the Mason was hired first, then the Carpenter
		madeOnce(helper, run, new int[] {4, 4}, 4, 4, null);
	}

	/**
	 * The loop itself: a second builder, another player's, helps on the build and has 32 oak planks in the chest by his
	 * own table, where the build never takes from; the build's own store has none. Before the fix the Carpenter served
	 * the helper as if the build were his: stairs from his planks into his chest, never off the build's list, made again
	 * every look. Expected: nothing is made into the helper's house, and his planks stay where they are.
	 */
	//$ gametest_ticks_batch BIG '1400' '"b1171HelpersHouse"'
	@GameTest(template = BIG, timeoutTicks = 1400, batch = "b1171HelpersHouse")
	public void nothingIsMadeIntoAHelpersHouseTheBuildNeverTakesFrom(GameTestHelper helper) {
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "b1171helper", stairRow(), false, true,
			List.of(), false, false, false);
		ServerLevel level = run.level();
		helper.setBlock(HELPER_CHEST, Blocks.CHEST);
		QaStalledCrafting1109bGameTests.fill(helper.getBlockEntity(HELPER_CHEST), List.of(new ItemStack(Items.OAK_PLANKS, 32)), null);
		helper.setBlock(HELPER_BENCH, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(HELPER_BENCH));
		Villager mate = helper.spawn(EntityType.VILLAGER, HELPER_BENCH.north());
		Builders.employ(level, mate, helper.absolutePos(HELPER_BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(mate, new Employer(UUID.randomUUID(), "Bea"));
		ModAttachments.BUILDER_JOB.set(mate, new BuilderJob(run.site().id(), true));
		Village.forget(mate);
		Village.forget(run.builder());
		helper.onEachTick(() -> {
			int crafted = ModAttachments.ITEMS_CRAFTED.getOrElse(run.crafters().get(0), 0);
			int inHisChest = inChest(helper, HELPER_CHEST, Items.OAK_STAIRS);
			if (crafted > 0 || inHisChest > 0) {
				throw new GameTestAssertException("at tick " + run.tick() + " the Carpenter made " + crafted + " for a helper's house ("
					+ inHisChest + " oak stairs in the helper's chest, " + inChest(helper, HELPER_CHEST, Items.OAK_PLANKS) + " of his 32 planks left)"
					+ state(run));
			}
		});
		helper.runAfterDelay(1200, () -> {
			int planks = inChest(helper, HELPER_CHEST, Items.OAK_PLANKS);
			if (planks != 32) {
				throw new GameTestAssertException("the helper's chest holds " + planks + " of his 32 oak planks" + state(run));
			}
			if (!Builders.isHelping(mate)) {
				throw new GameTestAssertException("the second builder stopped helping, so the test showed nothing" + state(run));
			}
			helper.succeed();
		});
	}

	/**
	 * Saved and loaded mid-delivery: the Carpenter is reloaded the moment his job turns to carrying the 4 oak stairs to
	 * the builder. They still arrive, are built, and no second batch is made.
	 */
	//$ gametest_ticks_batch BIG '3600' '"b1171Reload"'
	@GameTest(template = BIG, timeoutTicks = 3600, batch = "b1171Reload")
	public void reloadedOnTheWayTheStairsStillArriveOnce(GameTestHelper helper) {
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "b1171reload", stairRow(), false, true,
			List.of(new ItemStack(Items.OAK_PLANKS, 32)), false, false, false);
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			Villager carpenter = run.crafters().get(0);
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			if (!reloaded.get() && job != null && job.phase() == CrafterJob.Phase.DELIVERING) {
				reloaded.set(true);
				run.crafters().set(0, QaCarpenterStorehouse1101GameTests.reload(run.level(), carpenter));
			}
		});
		madeOnce(helper, run, new int[] {4}, 4, 0, reloaded);
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/** A row of 4 oak stairs. */
	static Map<BlockPos, BlockState> stairRow() {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (int x = 0; x < 4; x++) {
			blocks.put(new BlockPos(x, 0, 0), Blocks.OAK_STAIRS.defaultBlockState());
		}
		return blocks;
	}

	/** How many of {@code item} the chest at {@code pos} holds (0 without a chest). */
	static int inChest(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c ? c.countItem(item) : 0;
	}

	/** The site's list and each crafter's count, job and bag, for a failure's message. */
	static String state(QaStalledCrafting1109bGameTests.Run run) {
		BuildSite live = BuildSiteManager.get(run.level()).get(run.site().id());
		StringBuilder out = new StringBuilder("; site " + (live == null ? "gone" : live.status() + " missing " + live.missing()) + "; crafters ");
		for (Villager v : run.crafters()) {
			out.append(v.getVillagerData().getProfession()).append(" made ").append(ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0)).append(", job ")
				.append(ModAttachments.CRAFTER_JOB.get(v)).append(", bag ").append(ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()).append("; ");
		}
		return out.toString();
	}

	/**
	 * Fails the tick any of {@code run}'s crafters has made more than its share ({@code most}, in the order they were
	 * hired); succeeds once {@code stairs} oak stairs and {@code slabs} stone brick slabs stand on the site and, for
	 * {@link #SETTLE} ticks after, each crafter has made exactly its share and no oak stairs or slabs are left over
	 * anywhere (and {@code mustBe}, when given, is set).
	 */
	static void madeOnce(GameTestHelper helper, QaStalledCrafting1109bGameTests.Run run, int[] most, int stairs, int slabs, AtomicBoolean mustBe) {
		helper.onEachTick(() -> {
			for (int i = 0; i < most.length; i++) {
				int crafted = ModAttachments.ITEMS_CRAFTED.getOrElse(run.crafters().get(i), 0);
				if (crafted > most[i]) {
					throw new GameTestAssertException("at tick " + run.tick() + " a request already filled was made again: "
						+ run.crafters().get(i).getVillagerData().getProfession() + " has made " + crafted + ", " + most[i] + " were asked for"
						+ state(run));
				}
			}
		});
		AtomicLong builtAt = new AtomicLong(-1);
		helper.succeedWhen(() -> {
			int stairsUp = QaStalledCrafting1109bGameTests.placed(helper, Blocks.OAK_STAIRS);
			int slabsUp = QaStalledCrafting1109bGameTests.placed(helper, Blocks.STONE_BRICK_SLAB);
			if (stairsUp < stairs || slabsUp < slabs) {
				builtAt.set(-1);
				throw new GameTestAssertException("at tick " + run.tick() + " the builder has built " + stairsUp + "/" + stairs + " oak stairs and "
					+ slabsUp + "/" + slabs + " stone brick slabs" + state(run));
			}
			if (builtAt.get() < 0) {
				builtAt.set(run.tick());
			}
			if (run.tick() - builtAt.get() < SETTLE) {
				throw new GameTestAssertException("built at tick " + builtAt.get() + ", watching for a second batch until " + (builtAt.get() + SETTLE)
					+ state(run));
			}
			for (int i = 0; i < most.length; i++) {
				int crafted = ModAttachments.ITEMS_CRAFTED.getOrElse(run.crafters().get(i), 0);
				if (crafted != most[i]) {
					throw new GameTestAssertException(run.crafters().get(i).getVillagerData().getProfession() + " made " + crafted + ", not " + most[i]
						+ state(run));
				}
			}
			int spare = QaStalledCrafting1109bGameTests.made(helper, Items.OAK_STAIRS) + QaStalledCrafting1109bGameTests.made(helper, Items.STONE_BRICK_SLAB);
			if (spare != 0) {
				throw new GameTestAssertException(spare + " oak stairs and stone brick slabs are left over in the chests and bags" + state(run));
			}
			if (mustBe != null && !mustBe.get()) {
				throw new GameTestAssertException("the Carpenter was never caught on his way, so nothing was reloaded" + state(run));
			}
		});
	}
}
