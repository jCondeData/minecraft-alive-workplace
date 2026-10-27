package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.Contraptions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, Create, 2026-10-27), from the wiki's Create page (39.5, contraptions built from a blueprint): the line its
 * owner reads once the builder (or the Mechanic) has checked that a contraption turns, word for word in every count:
 * "its 1 moving part turns", "all 8 moving parts turn", "1 part doesn't turn", and the parts that don't turn named "up
 * to four", by block and place (a part that's gone as "a missing part"), the rest as "and N more". Without Create no
 * Mechanic leads, so the builder is who checked.
 */
public class QaContraptionLinesGameTests implements FabricGameTest {
	private static final ResourceLocation BUILD = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "qa/spinning_mill");

	/** Everything turns: one part reads in the singular, eight in the plural. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPartTurnsInTheSingularAndThePlural(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String build = Blueprints.displayName(BUILD).getString();
		List<BlockPos> one = parts(helper, 1);
		expect(helper, Contraptions.line(level, check(helper, one), List.of()).getString(),
			"Bob checked the " + build + ": its 1 moving part turns.");
		List<BlockPos> eight = parts(helper, 8);
		expect(helper, Contraptions.line(level, check(helper, eight), List.of()).getString(),
			"Bob checked the " + build + ": all 8 moving parts turn.");
		helper.succeed();
	}

	/** One part stuck: the singular, named by its block and place; a part that's gone is "a missing part". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void onePartStuckIsNamedInTheSingular(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String build = Blueprints.displayName(BUILD).getString();
		List<BlockPos> all = parts(helper, 3);
		level.setBlockAndUpdate(all.get(1), Blocks.STONE.defaultBlockState());
		expect(helper, Contraptions.line(level, check(helper, all), List.of(all.get(1))).getString(),
			"Bob checked the " + build + ": 1 part doesn't turn: Stone at " + at(all.get(1)) + ".");
		expect(helper, Contraptions.line(level, check(helper, all), List.of(all.get(0))).getString(),
			"Bob checked the " + build + ": 1 part doesn't turn: a missing part at " + at(all.get(0)) + ".");
		helper.succeed();
	}

	/** Four stuck: all four named and no "more"; five: four named and "and 1 more"; seven: "and 3 more". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void upToFourStuckPartsAreNamedAndTheRestCounted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String build = Blueprints.displayName(BUILD).getString();
		List<BlockPos> all = parts(helper, 8);
		for (int n : new int[] {4, 5, 7}) {
			List<BlockPos> stuck = all.subList(0, n);
			StringBuilder named = new StringBuilder();
			for (int i = 0; i < Math.min(4, n); i++) {
				named.append(i > 0 ? ", " : "").append("a missing part at ").append(at(stuck.get(i)));
			}
			if (n > 4) {
				named.append(", and ").append(n - 4).append(" more");
			}
			expect(helper, Contraptions.line(level, check(helper, all), stuck).getString(),
				"Bob checked the " + build + ": " + n + " parts don't turn: " + named + ".");
		}
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, String said, String expected) {
		helper.assertTrue(said.equals(expected), "the owner reads \"" + said + "\", expected \"" + expected + "\"");
	}

	/** {@code n} part places in a row (absolute), left as air. */
	private static List<BlockPos> parts(GameTestHelper helper, int n) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(helper.absolutePos(new BlockPos(i % 4, 2 + i / 4, 1)));
		}
		return out;
	}

	private static BuildSiteManager.TurnCheck check(GameTestHelper helper, List<BlockPos> parts) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(BlockPos.ZERO),
			Rotation.NONE, Mirror.NONE);
		return new BuildSiteManager.TurnCheck(BUILD, placement, UUID.randomUUID(), "Bob", parts, 0L, 0);
	}

	private static String at(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}
}
