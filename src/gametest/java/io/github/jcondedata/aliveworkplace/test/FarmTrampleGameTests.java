package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A farm's field stays the farmer's (playtest, rc.11): villagers don't trample farmland, the farmer works a trampled gap
 * back into farmland and sows it without a hoe, and the builder's upkeep doesn't count cut or trampled crops as damage.
 */
public class FarmTrampleGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String BUILD_AREA = "aliveworkplace_test:build_area";
	private static final BlockPos COMPOSTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** Landing on farmland: a villager leaves it (and its wheat) be; a pig, the control, still tramples it. */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void villagersDontTrampleFarmland(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos byVillager = new BlockPos(5, 1, 5);
		BlockPos byPig = new BlockPos(9, 1, 5);
		for (BlockPos p : List.of(byVillager, byPig)) {
			helper.setBlock(p, Blocks.FARMLAND);
			helper.setBlock(p.above(), Blocks.WHEAT);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, byVillager.above());
		Entity pig = helper.spawn(EntityType.PIG, byPig.above());
		// A fall of 3 blocks: vanilla's roll (random < fall - 0.5) always tramples.
		for (Entity e : List.of(villager, pig)) {
			BlockPos at = e == villager ? byVillager : byPig;
			Blocks.FARMLAND.fallOn(level, level.getBlockState(helper.absolutePos(at)), helper.absolutePos(at), e, 3f);
		}
		helper.assertBlockPresent(Blocks.FARMLAND, byVillager);
		helper.assertBlockPresent(Blocks.WHEAT, byVillager.above());
		helper.assertBlockPresent(Blocks.DIRT, byPig); // the control: mobGriefing is on, a pig tramples
		villager.discard();
		pig.discard();
		helper.succeed();
	}

	/** A gap trampled in the middle of the field: worked back into farmland and sown, though the chest has no hoe. */
	//$ gametest_ticks AREA '2400'
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void farmerRetillsATrampledGapWithoutAHoe(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos gap = new BlockPos(9, 1, 8);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 8))) {
			helper.setBlock(p, Blocks.FARMLAND);
			helper.setBlock(p.above(), Blocks.WHEAT);
		}
		helper.setBlock(new BlockPos(9, 1, 9), Blocks.WATER);
		helper.setBlock(gap.above(), Blocks.AIR);
		helper.setBlock(gap, Blocks.DIRT); // trampled: the wheat on it popped off
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 8));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(COMPOSTER), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, villager, BoundingBox.fromCorners(helper.absolutePos(new BlockPos(8, 1, 8)), helper.absolutePos(new BlockPos(10, 1, 8))));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.FARMLAND, gap);
			helper.assertBlockPresent(Blocks.WHEAT, gap.above());
		});
	}

	/**
	 * A finished farm whose crops were cut, trampled and half-grown: nothing for the builder's upkeep to repair (no wheat
	 * seeds sown by the builder). The first build of it still plants the wheat, and a real hole in it is still repaired.
	 */
	//$ gametest_ticks_batch BUILD_AREA '200' '"farm_upkeep"'
	@GameTest(template = BUILD_AREA, timeoutTicks = 200, batch = "farm_upkeep")
	public void upkeepLeavesAFarmsCropsToTheFarmer(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean was = Upkeep.ENABLED;
		Upkeep.ENABLED = true;
		Leftovers.after(helper, () -> Upkeep.ENABLED = was);
		ServerLevel level = helper.getLevel();
		// The farm: a plank, then three farmland with ripe wheat on them.
		BlockPos src = new BlockPos(6, 2, 6);
		helper.setBlock(src, Blocks.OAK_PLANKS);
		for (int x = 1; x <= 3; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.FARMLAND);
			helper.setBlock(src.offset(x, 1, 0), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
		}
		ResourceLocation farm = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "farm_upkeep_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(farm).fillFromWorld(level, helper.absolutePos(src), new Vec3i(4, 2, 1), false, Blocks.STRUCTURE_VOID);
		Blueprint blueprint = BlueprintLibrary.get(level, farm).orElseThrow();
		var placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(src), Rotation.NONE, Mirror.NONE);
		long sown = BuildPlan.create(blueprint, placement).steps(BuildPlan.Stage.DECORATION).stream().filter(s -> s.state().is(Blocks.WHEAT)).count()
			+ BuildPlan.create(blueprint, placement).steps(BuildPlan.Stage.STRUCTURE).stream().filter(s -> s.state().is(Blocks.WHEAT)).count();
		helper.assertTrue(sown == 3, "the first build plants " + sown + " wheat, not 3");
		// Cut (air on farmland), trampled (dirt, the wheat popped off) and replanted (young wheat).
		helper.setBlock(src.offset(1, 1, 0), Blocks.AIR);
		helper.setBlock(src.offset(2, 1, 0), Blocks.AIR);
		helper.setBlock(src.offset(2, 0, 0), Blocks.DIRT);
		helper.setBlock(src.offset(3, 1, 0), Blocks.WHEAT);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 8));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 4));
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSiteManager.get(level).recordFinished(farm, placement, builder.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		helper.assertTrue(BuildPlan.repair(blueprint, placement, level).size() == 0,
			"a repair would put back " + BuildPlan.repair(blueprint, placement, level).size() + " blocks of a farm with only its crops gone");
		helper.assertTrue(Upkeep.look(level, builder) == null, "the builder set about repairing the crops");
		helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "the builder set about repairing the crops");
		// A real hole still counts: the plank, and only the plank.
		helper.setBlock(src, Blocks.AIR);
		BuildPlan repair = BuildPlan.repair(blueprint, placement, level);
		helper.assertTrue(repair.size() == 1 && repair.steps(BuildPlan.Stage.STRUCTURE).stream().allMatch(s -> s.state().is(Blocks.OAK_PLANKS)),
			"the repair of a knocked-out plank has " + repair.size() + " blocks");
		builder.discard();
		helper.succeed();
	}
}
