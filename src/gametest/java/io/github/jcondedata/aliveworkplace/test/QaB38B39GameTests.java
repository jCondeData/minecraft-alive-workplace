package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/**
 * QA for B38 and B39, from the bugs' Expected lines rather than the fixes.
 * <ul>
 * <li>B39: "foundation work starts within 30 s of CLEAR ending": a hut on a one-block slope (so FOUNDATION has columns
 * to fill) at three distances from its chests, near, around the 16-block mark and far. Each measures the ticks from
 * CLEAR ending to the next block placed.</li>
 * <li>B38: "a builder with nothing missing never shows waiting for materials; it fetches or builds": the free variant
 * only in a chest, and the boundary from the other side, a bag of variants three short, which must name what is
 * missing (never an empty list) and finish once the three arrive.</li>
 * </ul>
 */
public class QaB38B39GameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos[] CHESTS = {new BlockPos(1, 2, 4), new BlockPos(2, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4)};
	/** The bugs' 30 seconds. */
	private static final int LIMIT = 600;

	// --- B39 -------------------------------------------------------------------------------------------------------

	//$ gametest_ticks_batch AREA '3000' '"qaB39NearSlope"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaB39NearSlope")
	public void qaB39ANearSiteOnASlopeStartsItsFoundationWithin30s(GameTestHelper helper) {
		Leftovers.clear(helper);
		slope(helper, new BlockPos(8, 3, 8));
	}

	//$ gametest_ticks_batch AREA '3000' '"qaB39BorderSlope"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaB39BorderSlope")
	public void qaB39ASiteAbout16BlocksAwayStartsItsFoundationWithin30s(GameTestHelper helper) {
		Leftovers.clear(helper);
		slope(helper, new BlockPos(13, 3, 13));
	}

	//$ gametest_ticks_batch AREA '3000' '"qaB39FarSlope"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaB39FarSlope")
	public void qaB39AFarSiteOnASlopeStartsItsFoundationWithin30s(GameTestHelper helper) {
		Leftovers.clear(helper);
		slope(helper, new BlockPos(22, 3, 22));
	}

	/**
	 * The hut's bottom layer sits at {@code origin}'s y, one above the floor; the ground under its first two columns is
	 * dirt, under the rest air, so its foundation fills those columns. A few dirt blocks inside give CLEAR some work.
	 */
	private void slope(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(origin.offset(x, -1, z), x < 2 ? Blocks.DIRT : Blocks.AIR);
			}
		}
		for (int i = 0; i < 4; i++) {
			helper.setBlock(origin.offset(i, 1, 0), Blocks.DIRT);
		}
		Run r = start(helper, origin, plan -> {
			Map<Item, Integer> stock = new LinkedHashMap<>(plan.materials());
			stock.merge(Items.COBBLESTONE, 32, Integer::sum); // the foundation's columns, whatever the floor is
			stock.merge(Items.OAK_PLANKS, 32, Integer::sum);
			return stock;
		});
		BuildPlan plan = r.site().plan(level);
		helper.assertTrue(plan != null && !plan.steps(BuildPlan.Stage.FOUNDATION).isEmpty(), "setup: the slope gives the hut no foundation to fill");

		long[] clearEnded = {-1};
		int[] placedThen = {0};
		String[] stall = {null};
		helper.onEachTick(() -> {
			BuildSite site = r.site();
			if (clearEnded[0] < 0 && site.stage() != BuildPlan.Stage.CLEAR) {
				clearEnded[0] = helper.getTick();
				placedThen[0] = site.placed();
			}
			if (clearEnded[0] >= 0 && stall[0] == null && site.placed() == placedThen[0] && !site.isDone()
				&& helper.getTick() - clearEnded[0] > LIMIT) {
				stall[0] = "nothing placed for " + LIMIT + " ticks after CLEAR ended at tick " + clearEnded[0] + ": stage " + site.stage()
					+ ", status " + site.status() + ", missing " + site.missing() + ", builder at "
					+ helper.relativePos(r.builder().blockPosition()).toShortString() + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(r.builder());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stall[0] == null, "B39: " + stall[0]);
			helper.assertTrue(clearEnded[0] >= 0, "still clearing: status " + r.site().status());
			helper.assertTrue(r.site().placed() > placedThen[0], "nothing placed yet after CLEAR");
			helper.assertTrue(r.site().stage().ordinal() > BuildPlan.Stage.FOUNDATION.ordinal(),
				"still on the foundation: placed " + r.site().placed() + ", status " + r.site().status());
		});
	}

	// --- B38 -------------------------------------------------------------------------------------------------------

	/** The chests hold only spruce planks where the hut needs oak (one family): the builder takes them and finishes. */
	//$ gametest_ticks_batch AREA '12000' '"qaB38VariantInChest"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qaB38VariantInChest")
	public void qaB38AVariantOnlyInTheChestIsUsedWithoutWaiting(GameTestHelper helper) {
		Leftovers.clear(helper);
		planksFamily(helper);
		Run r = start(helper, new BlockPos(14, 2, 14), plan -> {
			Map<Item, Integer> stock = new LinkedHashMap<>(plan.materials());
			Integer oak = stock.remove(Items.OAK_PLANKS);
			helper.assertTrue(oak != null && oak > 0, "setup: the hut uses no oak planks: " + plan.materials());
			stock.merge(Items.SPRUCE_PLANKS, oak, Integer::sum);
			return stock;
		});
		watchEmptyWaits(helper, r);
		helper.succeedWhen(() -> done(helper, r));
	}

	/**
	 * The other side of B38's boundary: the bag's spruce planks are three short of the oak planks the hut needs and the
	 * chests hold no planks. The builder must say what it is missing (planks), never wait with an empty list, and
	 * finish once three spruce planks are put in its chest.
	 */
	//$ gametest_ticks_batch AREA '12000' '"qaB38ShortVariants"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qaB38ShortVariants")
	public void qaB38ABagOfVariantsThreeShortNamesWhatIsMissing(GameTestHelper helper) {
		Leftovers.clear(helper);
		planksFamily(helper);
		int[] oak = {0};
		Run r = start(helper, new BlockPos(14, 2, 14), plan -> {
			Map<Item, Integer> stock = new LinkedHashMap<>(plan.materials());
			oak[0] = stock.remove(Items.OAK_PLANKS);
			stock.remove(Items.SPRUCE_PLANKS);
			return stock;
		});
		helper.assertTrue(oak[0] > 3, "setup: the hut uses " + oak[0] + " oak planks");
		ModAttachments.BUILDER_BAG.getOrCreate(r.builder()).addAll(Items.SPRUCE_PLANKS, oak[0] - 3);
		watchEmptyWaits(helper, r);
		boolean[] refilled = {false};
		helper.onEachTick(() -> {
			BuildSite site = r.site();
			if (!refilled[0] && site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && !site.missing().isEmpty()) {
				Integer planks = site.missing().getOrDefault(Items.OAK_PLANKS, site.missing().get(Items.SPRUCE_PLANKS));
				helper.assertTrue(planks != null && planks > 0, "waiting, but the missing list doesn't name the planks: " + site.missing());
				refilled[0] = true;
				BaseContainerBlockEntity chest = helper.getBlockEntity(CHESTS[CHESTS.length - 1]);
				chest.setItem(chest.getContainerSize() - 1, new ItemStack(Items.SPRUCE_PLANKS, 3));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(refilled[0], "never said it was short of planks: status " + r.site().status() + ", missing " + r.site().missing());
			done(helper, r);
		});
	}

	// --- shared ----------------------------------------------------------------------------------------------------

	private record Run(Villager builder, BuildSite site) {
	}

	private static void planksFamily(GameTestHelper helper) {
		List<List<Item>> before = MaterialFamilies.dataFamilies();
		Leftovers.after(helper, () -> MaterialFamilies.setDataFamilies(before));
		List<List<Item>> families = new ArrayList<>(before);
		families.add(List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS));
		MaterialFamilies.setDataFamilies(families);
	}

	private static Run start(GameTestHelper helper, BlockPos origin, java.util.function.Function<BuildPlan, Map<Item, Integer>> stockFor) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		for (BlockPos chest : CHESTS) {
			helper.setBlock(chest, Blocks.BARREL);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the test hut didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no plan for the test hut");
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : stockFor.apply(plan).entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		int slot = 0;
		for (ItemStack stack : stock) {
			BaseContainerBlockEntity chest = helper.getBlockEntity(CHESTS[slot / 27]);
			chest.setItem(slot % 27, stack);
			slot++;
		}
		return new Run(builder, site);
	}

	/** Fails the test once the builder waits on shift for materials with nothing missing for {@link #LIMIT} ticks. */
	private static void watchEmptyWaits(GameTestHelper helper, Run r) {
		int[] emptyFor = {0};
		helper.onEachTick(() -> {
			BuildSite site = r.site();
			boolean onShift = r.builder().getBrain().isActive(Activity.WORK);
			if (!site.isDone() && onShift && site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && site.missing().isEmpty()) {
				if (++emptyFor[0] >= LIMIT) {
					helper.fail("B38: waited " + LIMIT + " ticks on shift with nothing missing: stage " + site.stage() + ", bag "
						+ ModAttachments.BUILDER_BAG.getOrCreate(r.builder()));
				}
			} else {
				emptyFor[0] = 0;
			}
		});
	}

	private static void done(GameTestHelper helper, Run r) {
		helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(r.site().id()) == null || r.site().isDone(),
			"still building: stage " + r.site().stage() + ", status " + r.site().status() + ", missing " + r.site().missing());
	}
}
