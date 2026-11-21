package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-e-1121-0713, independent of the fix) for B1411's fix, from the bug's Expected and the Create guide ("the pump
 * draws from the pool and fills the tank"): the Pump House's tank (13, 8..9, 3) fills when a player has the build
 * turned or flipped (the blueprint table's rotate and mirror), and keeps filling past what the cistern held when the
 * build finished (the cistern refills itself, as plan.mjs and the guide promise). The as-drawn case is
 * QaRebuiltCreate1121eCompatTests.aBuiltPumpHouseFillsItsTank.
 */
public class QaPumpHouseTank1121eCompatTests implements FabricGameTest {
	static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos TANK = new BlockPos(13, 8, 3);
	static final BlockPos TANK_TOP = new BlockPos(13, 9, 3);
	static final BlockPos PUMP = new BlockPos(13, 2, 3);
	/** Create on Fabric counts fluid in droplets, 81,000 a bucket (the full two-block tank reads 1,296,000). */
	static final long BUCKET = 81000L;

	/** Turned a quarter: the tank holds water within a minute of the build's end. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa1121e7_pump_turned"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa1121e7_pump_turned")
	public void aTurnedPumpHouseFillsItsTank(GameTestHelper helper) {
		fillsAfterBuild(helper, Rotation.CLOCKWISE_90, Mirror.NONE, 1);
	}

	/** Flipped left to right and turned half way: the same. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa1121e7_pump_flipped"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa1121e7_pump_flipped")
	public void aFlippedPumpHouseFillsItsTank(GameTestHelper helper) {
		fillsAfterBuild(helper, Rotation.CLOCKWISE_180, Mirror.LEFT_RIGHT, 1);
	}

	/**
	 * As drawn: the tank ends up holding more water than every source block standing in the build when it finished,
	 * two buckets more (so the cistern refilled itself instead of being pumped dry), within five minutes.
	 */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa1121e7_pump_refills"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa1121e7_pump_refills")
	public void aPumpHouseTankKeepsFillingPastItsCistern(GameTestHelper helper) {
		fillsAfterBuild(helper, Rotation.NONE, Mirror.NONE, -1);
	}

	/**
	 * Builds the Pump House turned and flipped as given, then waits for its tank: {@code buckets} > 0 wants that many
	 * buckets within 1,200 ticks; -1 wants the sources counted at the end plus two within 6,000.
	 */
	private static void fillsAfterBuild(GameTestHelper helper, Rotation rotation, Mirror mirror, int buckets) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		StarterBlueprints.Entry entry = StarterBlueprints.PUMP_HOUSE;
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlockPos far = StructureTemplate.transform(new BlockPos(entry.size().getX() - 1, 0, entry.size().getZ() - 1), mirror, rotation, BlockPos.ZERO);
		int width = Math.abs(far.getX()) + 1;
		int depth = Math.abs(far.getZ()) + 1;
		BlockPos corner = new BlockPos(Math.min(9, 29 - width), 2, Math.min(3, 29 - depth));
		BlockPos origin = corner.offset(-Math.min(0, far.getX()), 0, -Math.min(0, far.getZ()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), rotation, mirror);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		BuildPlan plan = site.plan(level);
		QaRebuiltCreate1121eCompatTests.barrels(helper, plan, 0);
		long[] doneAt = {-1};
		int[] sources = {0};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building");
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " blocks not as drawn, e.g. " + unfinished.stream().limit(8)
				.map(p -> p.toShortString() + "=" + level.getBlockState(p)).toList());
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
				sources[0] = sourcesIn(level, placement, entry);
			}
			// the controller keeps the whole tank's content; whichever block that is, take the larger reading
			long held = Math.max(amount(level, at(placement, TANK)), amount(level, at(placement, TANK_TOP)));
			long wanted = buckets > 0 ? buckets : sources[0] + 2L;
			int window = buckets > 0 ? 1200 : 6000;
			if (held < wanted * BUCKET && helper.getTick() < doneAt[0] + window) {
				throw new GameTestAssertException("waiting for the tank: " + held);
			}
			helper.assertTrue(held >= wanted * BUCKET, (window / 20) + " s after the " + rotation + "/" + mirror + " build, its tank holds " + held
				+ " (wanted " + wanted + " buckets; " + sources[0] + " sources stood in the build at its end); pump Speed "
				+ QaRebuiltCreate1121eCompatTests.speed(level, at(placement, PUMP)) + "; tank "
				+ QaRebuiltCreate1121eCompatTests.tag(level, at(placement, TANK)));
		});
	}

	static BlockPos at(BlueprintData.Placement placement, BlockPos local) {
		return placement.origin().offset(StructureTemplate.transform(local, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	/** Water source blocks in the build's box (the cistern, any other pool, waterlogged cells), on and under its floor. */
	static int sourcesIn(ServerLevel level, BlueprintData.Placement placement, StarterBlueprints.Entry entry) {
		int n = 0;
		for (int x = 0; x < entry.size().getX(); x++) {
			for (int y = -1; y < entry.size().getY(); y++) {
				for (int z = 0; z < entry.size().getZ(); z++) {
					if (level.getFluidState(at(placement, new BlockPos(x, y, z))).isSourceOfType(Fluids.WATER)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	/** The water a tank block's own content says (Create keeps the whole tank's content in its controller). */
	static long amount(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return 0;
		}
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		if (!tag.contains("TankContent")) {
			return 0;
		}
		CompoundTag content = tag.getCompound("TankContent");
		if (!content.toString().contains("minecraft:water")) {
			return 0;
		}
		// Create 6 on Fabric: TankContent {Fluid: {amount: <droplets>L, fluid: {fluid: "minecraft:water"}}}
		CompoundTag fluid = content.contains("Fluid") ? content.getCompound("Fluid") : content;
		return fluid.contains("amount") ? Nbt.getLong(fluid, "amount") : Nbt.getLong(fluid, "Amount");
	}

}
