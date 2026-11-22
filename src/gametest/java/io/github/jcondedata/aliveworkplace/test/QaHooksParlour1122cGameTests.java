package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane C (2026-11-22), the fixes of B1451, B1452 and B1454 from their specs, what BlueprintPiecesGameTests doesn't
 * already cover: Farmstead II and III built whole by a Builder from exactly their materials (tripwire hooks included,
 * B1452); every tripwire hook of the Carpenter's Workshop and the Farmstead II and III, in each look, stands where it is
 * drawn (B1451, B1452: a hook on a glass pane can't); a real villager walks into the Flower Shop I and II parlours to the
 * chest at (10, 1, 2) (B1454: "every indoor chest reachable on foot").
 */
public class QaHooksParlour1122cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** farmstead.md in the world: Farmstead II built by a Builder from exactly its materials, every block right. */
	//$ gametest_ticks_batch HUGE '90000' '"qaHooks1122cFarm2"'
	@GameTest(template = HUGE, timeoutTicks = 90000, batch = "qaHooks1122cFarm2")
	public void qaABuilderBuildsFarmsteadIIWholeWithItsHooks(GameTestHelper helper) {
		farmstead(helper, StarterBlueprints.FARMSTEAD_2);
	}

	/** Farmstead III likewise (its hooks at (2, 6, 11) and (2, 6, 12)). */
	//$ gametest_ticks_batch HUGE '90000' '"qaHooks1122cFarm3"'
	@GameTest(template = HUGE, timeoutTicks = 90000, batch = "qaHooks1122cFarm3")
	public void qaABuilderBuildsFarmsteadIIIWholeWithItsHooks(GameTestHelper helper) {
		farmstead(helper, StarterBlueprints.FARMSTEAD_3);
	}

	private static void farmstead(GameTestHelper helper, StarterBlueprints.Entry entry) {
		ServerLevel level = helper.getLevel();
		// a basement (Farmstead III's, 4 deep) lies under the origin: raised by it, so the builder builds it whole above the
		// test's floor rather than finding the world's own ground there, with no foundation under the raised yard
		if (entry.basement() > 0) {
			int depth = level.getGameRules().getInt(io.github.jcondedata.aliveworkplace.registry.ModGameRules.FOUNDATION_DEPTH);
			Leftovers.after(helper, () -> level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.FOUNDATION_DEPTH)
				.set(depth, level.getServer()));
			level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.FOUNDATION_DEPTH).set(0, level.getServer());
		}
		QaRebuiltWorkshops1121bGameTests.buildAcrossAReload(helper, entry, new BlockPos(9, 2 + entry.basement(), 7), placement -> {
			Vec3i size = entry.size();
			List<BlockPos> hooks = QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.TRIPWIRE_HOOK);
			helper.assertTrue(hooks.size() == 2, entry.id().getPath() + ": the built Farmstead has " + hooks.size() + " tripwire hooks, drawn 2");
			for (BlockPos h : hooks) {
				helper.assertTrue(level.getBlockState(h).canSurvive(level, h), "a built hook can't hang where it is: " + h.subtract(placement.origin()).toShortString());
			}
		});
	}

	/**
	 * Each look of the Carpenter's Workshop and Farmstead II and III placed as drawn: every tripwire hook in it can hang
	 * where it is (it stands on the block it faces away from), so a neighbour update never pops it off and a builder can
	 * place it.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaHooks1122cLooks"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaHooks1122cLooks")
	public void qaEveryHookOfTheCarpentersWorkshopAndFarmsteadsHangsInEveryLook(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<String> names = List.of("carpenters_workshop", "modded/carpenters_workshop", "farmstead_2", "farmstead_3", "looks/desert/farmstead_2",
			"looks/desert/farmstead_3", "modded/farmstead_2", "modded/farmstead_3");
		List<String> bad = new ArrayList<>();
		int checked = 0;
		BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
		for (String name : names) {
			ResourceLocation id = AliveWorkplace.id(name);
			StructureTemplate template = level.getStructureManager().get(id).orElse(null);
			helper.assertTrue(template != null, "(setup) no drawing " + id);
			Vec3i size = template.getSize();
			// clear the last drawing's blocks first
			for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(26, 26, 26))) {
				if (!level.getBlockState(p).isAir()) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
			template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE),
				net.minecraft.util.RandomSource.create(173L), 2);
			int hooks = 0;
			for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
				if (level.getBlockState(p).is(Blocks.TRIPWIRE_HOOK)) {
					hooks++;
					if (!level.getBlockState(p).canSurvive(level, p)) {
						bad.add(name + " " + p.subtract(origin).toShortString() + " on " + level.getBlockState(p.relative(
							level.getBlockState(p).getValue(net.minecraft.world.level.block.TripWireHookBlock.FACING).getOpposite())).getBlock().getName().getString());
					}
				}
			}
			helper.assertTrue(hooks >= 1, "(setup) " + name + " has no tripwire hook any more");
			checked += hooks;
		}
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(26, 26, 26))) {
			if (!level.getBlockState(p).isAir()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
		helper.assertTrue(bad.isEmpty(), "tripwire hooks that can't hang where they are drawn: " + bad + " (of " + checked + ")");
		helper.succeed();
	}

	/**
	 * flower_shop.md, Storage, with a real walk: the drawing placed, a villager in the street in front told to walk to the
	 * parlour's chest at (10, 1, 2); he gets within a player's reach of it (2 blocks) through the parlour's doorway.
	 */
	private static void walksToTheParlourChest(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos origin = QaRebuiltWorkshops1121cLiveGameTests.place(helper, entry);
		BlockPos chest = origin.offset(10, 1, 2);
		helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "(setup) the parlour chest has moved: " + level.getBlockState(chest));
		Villager walker = helper.spawn(EntityType.VILLAGER, QaRebuiltWorkshops1121cLiveGameTests.AT.offset((entry.size().getX() - 1) / 2, 0, -3));
		// the villager's own brain walks him (and opens the doors on the way): his walk target held on the chest
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
			walker.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
				new net.minecraft.world.entity.ai.memory.WalkTarget(chest, 0.6f, 1));
		});
		helper.succeedWhen(() -> {
			double d = walker.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(chest));
			helper.assertTrue(walker.isAlive() && d <= 2.5, entry.id().getPath() + ": the villager didn't reach the parlour chest: "
				+ String.format("%.1f", d) + " blocks off, at " + walker.blockPosition().subtract(origin).toShortString());
			walker.discard();
		});
	}

	//$ gametest_ticks_batch HUGE '800' '"qaHooks1122cParlour1"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaHooks1122cParlour1")
	public void qaAVillagerWalksIntoTheFlowerShopParlourToItsChest(GameTestHelper helper) {
		walksToTheParlourChest(helper, StarterBlueprints.FLOWER_SHOP);
	}

	//$ gametest_ticks_batch HUGE '800' '"qaHooks1122cParlour2"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaHooks1122cParlour2")
	public void qaAVillagerWalksIntoTheFlowerShopIIParlourToItsChest(GameTestHelper helper) {
		walksToTheParlourChest(helper, StarterBlueprints.FLOWER_SHOP_2);
	}
}
