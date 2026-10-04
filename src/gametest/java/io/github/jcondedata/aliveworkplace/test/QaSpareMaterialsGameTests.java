package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-1004-0434) for B31 and B33, from their specs: another builder's chests keep what its builds (the one under way
 * and those queued after it) still need, and that hold is lifted once the build is cancelled. Covers the queue, the
 * one-spare boundary and the cancel, for builders (B31) and crafters (B33).
 */
public class QaSpareMaterialsGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos OTHER_BENCH = new BlockPos(26, 2, 2);
	private static final BlockPos OTHER_CHEST = new BlockPos(26, 2, 4);
	private static final BlockPos OTHER_HUT = new BlockPos(18, 2, 18);
	private static final BlockPos QUEUED_HUT = new BlockPos(18, 2, 8);
	private static final BlockPos CUTTER = new BlockPos(8, 2, 2);
	private static final BlockPos WALL = new BlockPos(6, 2, 12);
	private static final BlockPos OTHER_WALL = new BlockPos(18, 2, 20);
	private static final BlockPos TEMPLATE_SOURCE = new BlockPos(12, 2, 26);

	private record Pair(BuildSite site, BuildSite otherSite, Container otherChest) {
	}

	private record Masonry(BuildSite site, BuildSite otherSite, Container otherChest, Villager mason) {
	}

	/** B31: a queued build is reserved too. The other builder's chest holds exactly two huts' cobblestone (one queued). */
	//$ gametest_ticks_batch HUGE_AREA '3000' '"qa_b31_queued"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 3000, batch = "qa_b31_queued")
	public void qaB31ABuilderLeavesWhatAnotherBuildersQueuedHutNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Pair p = builders(helper, 50, true);
		helper.succeedWhen(() -> {
			helper.assertTrue(p.otherChest().countItem(Items.COBBLESTONE) == 50,
				"took the cobblestone the other builder's queued hut needs: " + p.otherChest().countItem(Items.COBBLESTONE) + " of 50 left");
			helper.assertTrue(p.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS,
				"not waiting yet: " + p.site().status() + " " + p.site().stage() + " placed " + p.site().placed());
			helper.assertTrue(Integer.valueOf(1).equals(p.site().missing().get(Items.COBBLESTONE)),
				"should be missing exactly 1 cobblestone, missing " + p.site().missing());
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qa] queued build reserved: other chest 50/50, missing " + p.site().missing()));
		});
	}

	/** B31: once the other builder's build is cancelled its cobblestone is free, and our builder takes its one and finishes. */
	//$ gametest_ticks_batch HUGE_AREA '4000' '"qa_b31_cancel"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 4000, batch = "qa_b31_cancel")
	public void qaB31TheHoldIsLiftedWhenTheOtherBuildIsCancelled(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Pair p = builders(helper, 25, false);
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(p.otherChest().countItem(Items.COBBLESTONE) == 25,
				"took reserved cobblestone before the cancel: " + p.otherChest().countItem(Items.COBBLESTONE));
			Builders.cancel(helper.getLevel(), p.otherSite());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 600, "not cancelled yet");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(p.site().id()) == null,
				"still building after the other build was cancelled: " + p.site().status() + " " + p.site().stage()
					+ " missing " + p.site().missing());
		});
	}

	/** B33 boundary: one andesite spare. The mason may cut that one, never the 3 the other wall needs, and our wall waits. */
	//$ gametest_ticks_batch HUGE_AREA '1600' '"qa_b33_one_spare"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1600, batch = "qa_b33_one_spare")
	public void qaB33AMasonTakesOnlyTheOneSpareAndesite(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Masonry s = masonry(helper, 4);
		helper.runAtTickTime(1400, () -> {
			int left = s.otherChest().countItem(Items.ANDESITE);
			helper.assertTrue(left >= 3, "the mason took andesite the other builder's wall needs: " + left + " of 4 left");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(s.site().id()) != null,
				"our wall of 3 finished from 1 spare andesite");
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qa] one spare: other chest " + left + "/4, crafted " + ModAttachments.ITEMS_CRAFTED.getOrElse(s.mason(), 0)));
			helper.succeed();
		});
	}

	/** B33: once the other builder's build is cancelled, the mason cuts its andesite and our wall is built. */
	//$ gametest_ticks_batch HUGE_AREA '4000' '"qa_b33_cancel"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 4000, batch = "qa_b33_cancel")
	public void qaB33TheHoldIsLiftedWhenTheOtherBuildIsCancelled(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Masonry s = masonry(helper, 3);
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(s.otherChest().countItem(Items.ANDESITE) == 3,
				"the mason took reserved andesite before the cancel: " + s.otherChest().countItem(Items.ANDESITE));
			Builders.cancel(helper.getLevel(), s.otherSite());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 600, "not cancelled yet");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(s.site().id()) == null,
				"still building after the other build was cancelled: " + s.site().status() + " " + s.site().stage()
					+ " missing " + s.site().missing() + ", other chest " + s.otherChest().countItem(Items.ANDESITE)
					+ " andesite, crafted " + ModAttachments.ITEMS_CRAFTED.getOrElse(s.mason(), 0));
		});
	}

	/**
	 * Our builder has a hut's materials but one cobblestone. Another builder, kept still (no AI), has a hut started
	 * (and, if {@code queued}, a second one queued) and {@code otherCobblestone} cobblestone with the rest of its materials.
	 */
	private static Pair builders(GameTestHelper helper, int otherCobblestone, boolean queued) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		int huts = queued ? 2 : 1;

		helper.setBlock(OTHER_BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(OTHER_CHEST, Blocks.CHEST);
		Container otherChest = helper.getBlockEntity(OTHER_CHEST);
		fill(otherChest, new ItemStack(Items.COBBLESTONE, otherCobblestone), new ItemStack(Items.OAK_PLANKS, 55 * huts),
			new ItemStack(Items.OAK_DOOR, huts), new ItemStack(Items.TORCH, huts));
		Villager other = helper.spawn(EntityType.VILLAGER, OTHER_BENCH.south().east());
		Builders.employ(level, other, helper.absolutePos(OTHER_BENCH));
		BuildSite otherSite = Builders.start(level, other, null, TEST_HUT, placement(helper, OTHER_HUT));
		if (queued) {
			BuildSite next = Builders.enqueue(level, other, null, TEST_HUT, placement(helper, QUEUED_HUT));
			helper.assertTrue(next != null && next.isQueued(), "the second hut wasn't queued");
		}
		other.setNoAi(true);

		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		fill(helper.getBlockEntity(CHEST), new ItemStack(Items.COBBLESTONE, 24), new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement(helper, HUT));
		helper.assertTrue(otherSite != null && site != null, "a build didn't start");
		return new Pair(site, otherSite, otherChest);
	}

	/**
	 * Our builder builds a wall of 3 polished andesite with none in its chest; a mason works a stonecutter by its bench.
	 * Another builder, kept still, builds a wall of 3 andesite and has {@code otherAndesite} andesite in its chest.
	 */
	private static Masonry masonry(GameTestHelper helper, int otherAndesite) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		String suffix = Long.toHexString(level.getGameTime()) + "_qa" + otherAndesite;
		ResourceLocation wall = wall(helper, Blocks.POLISHED_ANDESITE, "qa_b33_polished_" + suffix);
		ResourceLocation otherWall = wall(helper, Blocks.ANDESITE, "qa_b33_andesite_" + suffix);

		helper.setBlock(OTHER_BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(OTHER_CHEST, Blocks.CHEST);
		Container otherChest = helper.getBlockEntity(OTHER_CHEST);
		otherChest.setItem(0, new ItemStack(Items.ANDESITE, otherAndesite));
		Villager other = helper.spawn(EntityType.VILLAGER, OTHER_BENCH.south().east());
		Builders.employ(level, other, helper.absolutePos(OTHER_BENCH));
		BuildSite otherSite = Builders.start(level, other, null, otherWall, placement(helper, OTHER_WALL));
		other.setNoAi(true);

		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, wall, placement(helper, WALL));
		helper.assertTrue(otherSite != null && site != null, "a build didn't start");

		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, CUTTER.south());
		Jobs.employ(level, mason, helper.absolutePos(CUTTER), PoiTypes.MASON, VillagerProfession.MASON);
		return new Masonry(site, otherSite, otherChest, mason);
	}

	private static ResourceLocation wall(GameTestHelper helper, Block block, String name) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(TEMPLATE_SOURCE.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name);
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(TEMPLATE_SOURCE), new Vec3i(3, 1, 1), false,
			Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(TEMPLATE_SOURCE.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void fill(Container container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
	}
}
