package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-1004-0933) for 23.3 "What do you need? at a glance" and 23.5 "Self-healing supply", written from the items'
 * Done when: one look at a builder shows the fraction built, the 3 biggest shortages with counts (net of what its
 * chests hold and what it carries) and where it takes from; a full bag never stops work. The test hut needs 25
 * cobblestone, 55 oak planks, a door and a torch.
 */
public class QaGlanceSupplyGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos CHEST_2 = new BlockPos(0, 2, 2);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	private record Setup(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan) {
		BuilderStatusSync.Status status() {
			BuilderStatusSync.Status status = BuilderStatusSync.status(level, site, builder);
			if (status == null) {
				throw new GameTestAssertException("no overhead status for the builder");
			}
			return status;
		}

		String shortOf() {
			List<Component> more = status().more();
			if (more.isEmpty()) {
				throw new GameTestAssertException("no short-of line under the builder");
			}
			return more.get(0).getString();
		}
	}

	/** Exactly 3 kinds short (cobblestone all there): all 3 listed with counts, and no "and N more" (the boundary below 23.3's 4). */
	//$ gametest_ticks_batch AREA '40' '"qaGlanceExactlyThree"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaGlanceExactlyThree")
	public void qaGlanceExactlyThreeShortagesHaveNoAndMore(GameTestHelper helper) {
		Setup s = setup(helper, new ItemStack(Items.COBBLESTONE, 25));
		String line = s.shortOf();
		helper.assertTrue(line.equals("Short of: 55× Oak Planks, 1× Oak Door, 1× Torch")
				|| line.equals("Short of: 55× Oak Planks, 1× Torch, 1× Oak Door"),
			"expected the three shortages and nothing more: " + line);
		helper.succeed();
	}

	/** Part of a kind in the chest: the count shown is what is still short, not the whole need. */
	//$ gametest_ticks_batch AREA '40' '"qaGlanceNetOfChest"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaGlanceNetOfChest")
	public void qaGlanceCountsWhatTheChestHolds(GameTestHelper helper) {
		Setup s = setup(helper, new ItemStack(Items.OAK_PLANKS, 30));
		String line = s.shortOf();
		helper.assertTrue(line.startsWith("Short of: 25× Cobblestone, 25× Oak Planks") || line.startsWith("Short of: 25× Oak Planks, 25× Cobblestone"),
			"30 of 55 planks are in the chest, so 25 are short: " + line);
		helper.assertFalse(line.contains("55×"), "the whole need is shown, not the shortage: " + line);
		helper.succeed();
	}

	/** What the builder already carries isn't short either. */
	//$ gametest_ticks_batch AREA '40' '"qaGlanceNetOfBag"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaGlanceNetOfBag")
	public void qaGlanceCountsWhatTheBuilderCarries(GameTestHelper helper) {
		Setup s = setup(helper);
		ModAttachments.BUILDER_BAG.getOrCreate(s.builder()).add(new ItemStack(Items.OAK_PLANKS, 40));
		String line = s.shortOf();
		helper.assertTrue(line.startsWith("Short of: 25× Cobblestone, 15× Oak Planks"), "40 planks in the bag, so 15 short: " + line);
		helper.succeed();
	}

	/** Everything in the chest: the line says so in green words, with no counts. */
	//$ gametest_ticks_batch AREA '40' '"qaGlanceHasAll"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaGlanceHasAll")
	public void qaGlanceSaysWhenNothingIsShort(GameTestHelper helper) {
		Setup s = setup(helper, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH));
		helper.assertTrue(s.shortOf().equals("Has everything it needs"), "with every material in the chest: " + s.shortOf());
		helper.succeed();
	}

	/** Two chests by the bench: "2 chests", and the bench's position, read by a player. */
	//$ gametest_ticks_batch AREA '40' '"qaGlanceTwoChests"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaGlanceTwoChests")
	public void qaGlanceCountsEveryChestByTheBench(GameTestHelper helper) {
		helper.setBlock(CHEST_2, Blocks.CHEST);
		Setup s = setup(helper);
		List<Component> more = s.status().more();
		helper.assertTrue(more.size() == 2, "expected the short-of and takes-from lines: " + more);
		BlockPos bench = helper.absolutePos(BENCH);
		String expected = "Takes from: 2 chests by its bench at " + bench.getX() + " " + bench.getY() + " " + bench.getZ();
		helper.assertTrue(more.get(1).getString().equals(expected), "expected '" + expected + "', got '" + more.get(1).getString() + "'");
		helper.succeed();
	}

	/** The fraction built is in the title: 0% before the first block. */
	//$ gametest_ticks_batch AREA '40' '"qaGlanceFraction"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaGlanceFraction")
	public void qaGlanceShowsTheFractionBuilt(GameTestHelper helper) {
		Setup s = setup(helper);
		BuilderStatusSync.Status status = s.status();
		helper.assertTrue(status.title().getString().endsWith(" · 0%"), "a new build's title: " + status.title().getString());
		helper.assertTrue(status.progress() == 0f, "a new build's progress: " + status.progress());
		helper.succeed();
	}

	/**
	 * 23.5 "A full builder inventory never stops work", where nothing has room for the bag: its only chest is full
	 * apart from the hut's materials and there is no storehouse. The hut still gets built.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaSupplyFullBagNoRoom"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaSupplyFullBagNoRoom")
	public void qaAFullBagWithNowhereToEmptyItStillBuilds(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		ItemStack[] hut = {new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)};
		Setup s = setup(helper);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (ItemStack stack : hut) {
			chest.setItem(slot++, stack.copy());
		}
		while (slot < chest.getContainerSize()) {
			chest.setItem(slot++, new ItemStack(Items.STONE, 64));
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(s.builder());
		for (int i = 0; i < BuilderBag.SLOTS; i++) {
			bag.add(new ItemStack(Items.DIRT, 64));
		}
		helper.assertTrue(bag.freeSlots() == 0, "the bag isn't full: " + bag.freeSlots() + " free");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(s.site().id()) == null, "still building: stage=" + s.site().stage()
				+ " status=" + s.site().status() + " detail=" + (s.site().detail() == null ? "-" : s.site().detail().getString())
				+ " missing=" + s.site().missing() + " bag free=" + bag.freeSlots());
			List<BlockPos> unfinished = s.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
		});
	}

	private static Setup setup(GameTestHelper helper, ItemStack... chest) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container box = (Container) helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			box.setItem(i, chest[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		return new Setup(level, builder, site, plan);
	}
}
