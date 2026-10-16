package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The campfire on top of a chimney (rc.11 playtest): a chimney well above any floor, with nowhere to stand within reach of
 * its top but the roof's slope, had its campfire skipped by the builder every time, and upkeep sent him for it again and
 * again: out of the chest, over to the chimney, "repaired", and back in the chest, for good. Now the builder stands on the
 * roof for it, and a block that really can't be reached is tried once by a repair and then left be.
 */
public class ChimneyCampfireGameTests {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation COTTAGE_II = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "starter_cottage_2");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/**
	 * A Starter Cottage II, finished, its chimney's campfire gone (its top 13 blocks up, the roof beside it the only place
	 * within reach): upkeep sends the builder, he takes the campfire from his chest, stands on the roof, puts it on the
	 * chimney and is done, with nothing left to repair.
	 */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"chimney_campfire"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "chimney_campfire")
	public void builderPutsTheCampfireOnATallChimneyFromTheRoof(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		liveServer(helper);
		ServerLevel level = helper.getLevel();
		Blueprint cottage = BlueprintLibrary.get(level, COTTAGE_II).orElseThrow(() -> new GameTestAssertException("no " + COTTAGE_II));
		BlockPos origin = new BlockPos(8, 2, 8);
		BlockPos chimneyTop = null;
		for (Blueprint.Entry e : cottage.blocks()) {
			if (!e.state().is(Blocks.STRUCTURE_VOID)) {
				level.setBlock(helper.absolutePos(origin.offset(e.pos())), e.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			if (e.state().getBlock() instanceof CampfireBlock && e.pos().getY() > 10) {
				chimneyTop = origin.offset(e.pos());
			}
		}
		helper.assertTrue(chimneyTop != null, "the cottage II has no campfire on its chimney");
		BlockPos campfire = chimneyTop;
		helper.setBlock(campfire, Blocks.AIR); // gone: never built, or knocked off
		Setup s = setup(helper, COTTAGE_II, origin);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(campfire).getBlock() instanceof CampfireBlock,
				"no campfire on the chimney: " + helper.getBlockState(campfire) + ", builder at " + helper.relativePos(s.builder().blockPosition()));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "still repairing");
			helper.assertTrue(s.chest().countItem(Items.CAMPFIRE) == 0, "the campfire went back in the chest");
			helper.assertTrue(BuildSiteManager.get(level).gaveUp(s.placement()).isEmpty(), "a block was given up: " + BuildSiteManager.get(level).gaveUp(s.placement()));
			helper.assertTrue(Upkeep.look(level, s.builder()) == null, "another repair started");
		});
	}

	/**
	 * A bare stone column 12 blocks tall, nothing near it to stand on, its campfire gone: the repair tries it, skips it
	 * (deferred once, then skipped, as any block out of reach), puts the campfire back in the chest, and upkeep doesn't
	 * send the builder for it again.
	 */
	//$ gametest_ticks_batch BIG_AREA '3000' '"chimney_campfire_unreachable"'
	@GameTest(template = BIG_AREA, timeoutTicks = 3000, batch = "chimney_campfire_unreachable")
	public void aCampfireNobodyCanReachIsTriedOnceThenLeft(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		liveServer(helper);
		ServerLevel level = helper.getLevel();
		BlockPos base = new BlockPos(9, 2, 9);
		for (int y = 0; y < 12; y++) {
			helper.setBlock(base.above(y), Blocks.STONE_BRICKS);
		}
		BlockPos campfire = base.above(12);
		helper.setBlock(campfire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "chimney_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(base), new Vec3i(1, 13, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(campfire, Blocks.AIR);
		Setup s = setup(helper, id, base);
		helper.succeedWhen(() -> {
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "still repairing");
			helper.assertBlockPresent(Blocks.AIR, campfire);
			helper.assertTrue(s.chest().countItem(Items.CAMPFIRE) == 1, "campfires in the chest: " + s.chest().countItem(Items.CAMPFIRE));
			helper.assertTrue(BuildSiteManager.get(level).gaveUp(s.placement()).contains(helper.absolutePos(campfire)),
				"the campfire wasn't given up: " + BuildSiteManager.get(level).gaveUp(s.placement()));
			BuildSite again = Upkeep.look(level, s.builder());
			helper.assertTrue(again == null, "upkeep sent the builder for the same campfire again");
		});
	}

	/** What a repair gave up is saved with the finished building (an older save has none), and an upgrade on its spot forgets it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void givenUpBlocksSurviveASaveAndGoWithAnUpgrade(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), new BlockPos(100, 70, 100), Rotation.NONE, Mirror.NONE);
		BlueprintData.Placement other = new BlueprintData.Placement(level.dimension().location(), new BlockPos(200, 70, 100), Rotation.NONE, Mirror.NONE);
		BuildSiteManager manager = new BuildSiteManager();
		java.util.UUID owner = java.util.UUID.randomUUID();
		manager.recordFinished(COTTAGE_II, placement, owner);
		manager.recordFinished(COTTAGE_II, other, owner);
		manager.gaveUp(placement, List.of(new BlockPos(100, 85, 105)));
		CompoundTag tag = manager.save(new CompoundTag(), level.registryAccess());
		BuildSiteManager loaded = BuildSiteManager.load(tag, level.registryAccess());
		helper.assertTrue(loaded.gaveUp(placement).equals(java.util.Set.of(new BlockPos(100, 85, 105))), "after a reload: " + loaded.gaveUp(placement));
		helper.assertTrue(loaded.gaveUp(other).isEmpty(), "the other building gave nothing up: " + loaded.gaveUp(other));
		loaded.recordFinished(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "starter_cottage_3"), placement, owner);
		helper.assertTrue(loaded.gaveUp(placement).isEmpty(), "an upgrade on the spot kept what the old building gave up");
		helper.succeed();
	}

	private record Setup(Villager builder, Container chest, BlueprintData.Placement placement) {
	}

	/** As on the live server (1.0): repairs on, and no scaffolding (35.6 is Milestone 35's, still shut). */
	private static void liveServer(GameTestHelper helper) {
		boolean upkeep = Upkeep.ENABLED;
		boolean scaffolds = Scaffolds.ENABLED;
		Upkeep.ENABLED = true;
		Scaffolds.ENABLED = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = upkeep;
			Scaffolds.ENABLED = scaffolds;
		});
	}

	/** A builder at their bench with a campfire in the chest beside it, and {@code structure} at {@code origin} on their books as finished. */
	private static Setup setup(GameTestHelper helper, ResourceLocation structure, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.CAMPFIRE));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(structure, placement, builder.getUUID());
		helper.runAfterDelay(2, () -> {
			// (the builder may have looked already)
			BuildSite site = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
			helper.assertTrue(site != null && site.isRepair(), "no repair started");
		});
		return new Setup(builder, chest, placement);
	}
}
