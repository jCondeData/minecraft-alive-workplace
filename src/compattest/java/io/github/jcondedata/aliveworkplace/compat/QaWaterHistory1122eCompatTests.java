package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;

/**
 * QA lane E (2026-11-22), from the specs, with the pack's mods (Create, Cobblemon):
 * <ul>
 * <li>B1410: a player's own Create water wheel whose tank has a dry stair two cells from its water, built TURNED a
 * quarter: the water runs and the wheel turns (the 1121e tests build it unturned).</li>
 * <li>B1402: a Healing Center IV standing as the modded library's v0.157.0 drawing is told apart from today's: the
 * refresh finds the older drawing it was built from; one standing as today's modded copy finds none.</li>
 * </ul>
 */
public class QaWaterHistory1122eCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	/** ContraptionCompatTests' ORIGINAL (where the player's contraption is stood up) and the tank's corner two from the water. */
	private static final BlockPos ORIGINAL = new BlockPos(18, 2, 18);
	private static final BlockPos TWO_FROM_WATER = new BlockPos(2, 3, 5);
	private static final ResourceLocation HEALING_IV = AliveWorkplace.id("healing_center_4");
	/** git: the v0.157.0 tag's structure/modded/healing_center_4.nbt (blob 79c24dd6) ships as this key. */
	private static final String V0157 = "9c45f74f7";

	/** The contraption with a stone brick stair at the tank's corner, built turned a quarter: done, the water runs, everything turns. */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"qa1122e_wheel_turned"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "qa1122e_wheel_turned")
	public void aPlayersWaterWheelWithAStairTwoFromItsWaterTurnsWhenBuiltTurned(GameTestHelper helper) {
		Rotation rotation = Rotation.CLOCKWISE_90;
		ContraptionCompatTests.Run run = ContraptionCompatTests.start(helper, "qa1122e_turned", false, ContraptionCompatTests.origin(rotation), rotation,
			List.of(), () -> {
			}, () -> {
			});
		// the player's change, made the same tick (before the 80-tick wait in which it runs and is saved)
		helper.setBlock(ORIGINAL.offset(TWO_FROM_WATER), Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
		helper.runAfterDelay(79, () -> {
			helper.assertTrue(!helper.getBlockState(ORIGINAL.offset(ContraptionCompatTests.WATER).below()).getFluidState().isEmpty(),
				"setup: the player's own tank water doesn't run");
			helper.assertTrue(helper.getBlockState(ORIGINAL.offset(TWO_FROM_WATER)).is(Blocks.STONE_BRICK_STAIRS), "setup: no stair at the corner");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(run.started(), "the build hasn't started");
			helper.assertTrue(BuildSiteManager.get(run.level).get(run.site.id()) == null,
				"still building: " + run.site.stage() + " " + run.site.status() + " missing " + run.site.missing());
			BlockPos source = run.world(ContraptionCompatTests.WATER);
			helper.assertTrue(run.level.getFluidState(source).isSource(), "no water poured in the tank: " + run.level.getBlockState(source));
			helper.assertTrue(!run.level.getFluidState(source.below()).isEmpty(), "the tank's poured water never ran: under it is "
				+ run.level.getBlockState(source.below()));
			helper.assertTrue(run.level.getBlockState(run.world(TWO_FROM_WATER)).is(Blocks.STONE_BRICK_STAIRS), "the stair wasn't built: "
				+ run.level.getBlockState(run.world(TWO_FROM_WATER)));
			ContraptionCompatTests.assertBuiltAndTurning(helper, run);
		});
	}

	/**
	 * B1402: a modded Healing Center IV a builder built from the v0.157.0 modded drawing (every block that drawing puts,
	 * as built with the modded library) is found built from that drawing, so its refresh can tell the old drawing's
	 * blocks from a player's.
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qa1122e_healing_v0157"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qa1122e_healing_v0157")
	public void aModdedHealingCenterIvBuiltFromV0157IsToldApart(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			FurnitureChoice modded = FurnitureChoice.VANILLA.withLibrary(true);
			java.util.Optional<io.github.jcondedata.aliveworkplace.blueprint.Blueprint> old = Refreshes.olderDrawing(level.getServer(),
				AliveWorkplace.id("modded/healing_center_4"), V0157);
			helper.assertTrue(old.isPresent(), "the v0.157.0 modded drawing of the Healing Center IV isn't shipped");
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(4, 2, 4)),
				Rotation.NONE, Mirror.NONE);
			BuildPlan.create(modded.applyAsBuilt(old.get()), placement).targets().forEach((p, st) -> level.setBlock(p, st, 2 | 16));
			String from = Refreshes.builtFrom(level, HEALING_IV, placement, modded);
			helper.assertTrue(V0157.equals(from), "a Healing Center IV built from the v0.157.0 modded drawing was found built from '" + from + "'");
			helper.succeed();
		});
	}

	/** Stands what a builder puts for a new site of the Healing Center IV ({@code furniture}: modded library or vanilla) and asks. */
	private static String standTodaysAndAsk(GameTestHelper helper, FurnitureChoice furniture, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = new BuildSite(new UUID(1122, furniture.modded() ? 5 : 6), new UUID(1122, 7), "Jesse", HEALING_IV, placement);
		site.setFurniture(furniture);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "(setup) the Healing Center IV doesn't plan");
		plan.targets().forEach((p, st) -> level.setBlock(p, st, 2 | 16));
		return Refreshes.builtFrom(level, HEALING_IV, placement, furniture);
	}

	/**
	 * The control: a Healing Center IV a builder built today from the modded library (every block its plan puts, as a new
	 * site plans it) stands as today's drawing, so no older drawing is found.
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qa1122e_healing_today"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qa1122e_healing_today")
	public void aModdedHealingCenterIvBuiltTodayHasNoOlderDrawing(GameTestHelper helper) {
		helper.assertTrue(ModdedLibrary.use(HEALING_IV), "(setup) no modded copy of the Healing Center IV in the compat run");
		helper.runAfterDelay(1, () -> {
			String from = standTodaysAndAsk(helper, FurnitureChoice.VANILLA.withLibrary(true), new BlockPos(4, 2, 4));
			helper.assertTrue(from == null, "a modded Healing Center IV built today, as its plan puts it, was found built from the older drawing '" + from + "'");
			helper.succeed();
		});
	}

	/** The same for the vanilla library's Healing Center IV (the compat run's other library): no older drawing. */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qa1122e_healing_today_vanilla"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qa1122e_healing_today_vanilla")
	public void aVanillaHealingCenterIvBuiltTodayHasNoOlderDrawing(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			String from = standTodaysAndAsk(helper, FurnitureChoice.VANILLA, new BlockPos(4, 2, 4));
			helper.assertTrue(from == null, "a vanilla Healing Center IV built today, as its plan puts it, was found built from the older drawing '" + from + "'");
			helper.succeed();
		});
	}
}
