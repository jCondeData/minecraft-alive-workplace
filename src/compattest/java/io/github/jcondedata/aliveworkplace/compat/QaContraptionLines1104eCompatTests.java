package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.Contraptions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA lane E, 2026-11-04 (Create, the sentences players read): ROADMAP 39.5's "the builder checks it turns when done and
 * lists what doesn't". {@code ContraptionCompatTests} reads the all-turn line (8 parts) and the one-part stuck line;
 * {@code CreateBuildsGameTests} only checks that the keys exist. Here every other shape the owner can read:
 * one part turning, two stuck, exactly {@code NAMED} (4) stuck with no "more", one over it ("and 1 more"), many over it,
 * a part gone from its spot ("a missing part"), and the village's Mechanic named as the one who checked when his
 * toolbox is in reach (the builder otherwise).
 */
public class QaContraptionLines1104eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	//$ gametest_ticks_batch AREA '100' '"qa_e1104_contraption_lines"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1104_contraption_lines")
	public void oneMovingPartTurningReadsInTheSingular(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<BlockPos> parts = shafts(helper, 1);
		String line = line(helper, parts, List.of());
		helper.assertTrue(line.equals("Bob checked the Watermill: its 1 moving part turns."), "the owner reads: " + line);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"qa_e1104_contraption_lines"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1104_contraption_lines")
	public void twoStuckPartsAreBothNamedInThePlural(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<BlockPos> parts = shafts(helper, 6);
		List<BlockPos> still = parts.subList(0, 2);
		String line = line(helper, parts, still);
		helper.assertTrue(line.equals("Bob checked the Watermill: 2 parts don't turn: " + at("Shaft", still.get(0)) + ", " + at("Shaft", still.get(1)) + "."),
			"the owner reads: " + line);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"qa_e1104_contraption_lines"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1104_contraption_lines")
	public void fourStuckPartsAreAllNamedWithNoMore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<BlockPos> parts = shafts(helper, 6);
		List<BlockPos> still = parts.subList(0, 4);
		String line = line(helper, parts, still);
		helper.assertTrue(line.startsWith("Bob checked the Watermill: 4 parts don't turn: ") && line.endsWith(at("Shaft", still.get(3)) + "."),
			"the owner reads: " + line);
		helper.assertTrue(!line.contains("more"), "four parts read with a 'more': " + line);
		for (BlockPos p : still) {
			helper.assertTrue(line.contains(at("Shaft", p)), "the stuck shaft at " + p.toShortString() + " isn't named: " + line);
		}
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"qa_e1104_contraption_lines"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1104_contraption_lines")
	public void fiveStuckPartsNameFourAndOneMore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<BlockPos> parts = shafts(helper, 6);
		List<BlockPos> still = parts.subList(0, 5);
		String line = line(helper, parts, still);
		helper.assertTrue(line.startsWith("Bob checked the Watermill: 5 parts don't turn: ") && line.endsWith(at("Shaft", still.get(3)) + ", and 1 more."),
			"the owner reads: " + line);
		helper.assertTrue(!line.contains(at("Shaft", still.get(4))), "the fifth part is named as well as counted: " + line);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"qa_e1104_contraption_lines"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1104_contraption_lines")
	public void manyStuckPartsCountTheRest(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<BlockPos> parts = shafts(helper, 11);
		String line = line(helper, parts, parts);
		helper.assertTrue(line.startsWith("Bob checked the Watermill: 11 parts don't turn: ") && line.endsWith(", and 7 more."), "the owner reads: " + line);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"qa_e1104_contraption_lines"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1104_contraption_lines")
	public void aPartGoneFromItsSpotIsCalledAMissingPart(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<BlockPos> parts = shafts(helper, 3);
		helper.getLevel().setBlockAndUpdate(parts.get(1), Blocks.AIR.defaultBlockState());
		String line = line(helper, parts, List.of(parts.get(1)));
		helper.assertTrue(line.equals("Bob checked the Watermill: 1 part doesn't turn: " + at("a missing part", parts.get(1)) + "."), "the owner reads: " + line);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"qa_e1104_contraption_mechanic"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_e1104_contraption_mechanic")
	public void theVillagesMechanicInReachIsTheOneWhoChecked(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<BlockPos> parts = shafts(helper, 2);
		Villager mechanic = MechanicCompatTests.mechanic(helper);
		String name = mechanic.getDisplayName().getString();
		String line = line(helper, parts, List.of());
		helper.assertTrue(line.equals(name + " checked the Watermill: all 2 moving parts turn."), "the owner reads: " + line + " (the Mechanic is " + name + ")");
		helper.succeed();
	}

	/** {@code n} Create shafts in a row in the test area (world positions). */
	private static List<BlockPos> shafts(GameTestHelper helper, int n) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			BlockPos rel = new BlockPos(1 + i % 6, 2 + i / 6, 6);
			helper.setBlock(rel, MechanicCompatTests.with(MechanicCompatTests.create("shaft").defaultBlockState(), "axis", "z"));
			out.add(helper.absolutePos(rel));
		}
		return out;
	}

	private static String line(GameTestHelper helper, List<BlockPos> parts, List<BlockPos> still) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 6)),
			Rotation.NONE, Mirror.NONE);
		BuildSiteManager.TurnCheck check = new BuildSiteManager.TurnCheck(AliveWorkplace.id("watermill"), placement, UUID.randomUUID(), "Bob", parts, 0, 0);
		return Contraptions.line(helper.getLevel(), check, still).getString();
	}

	private static String at(String part, BlockPos p) {
		return part + " at " + p.getX() + " " + p.getY() + " " + p.getZ();
	}
}
