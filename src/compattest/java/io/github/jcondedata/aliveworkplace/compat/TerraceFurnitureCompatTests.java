package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Resting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** B281, B641: the Terrace's homes are furnished alike in kind: every one gets the installed furniture mods' pieces. */
public class TerraceFurnitureCompatTests implements FabricGameTest {
	private static final List<String> ORDER = List.of("handcrafted", "beautify");
	/** Each Terrace and how many homes it has (each between party walls 5 apart from x = 1). */
	private static final Map<String, Integer> HOMES = Map.of("terrace", 2, "terrace_2", 3);

	/**
	 * B281 (the owner, 2026-10-21: one Terrace home had a Handcrafted table and chair downstairs, the other spruce stand-ins):
	 * with Handcrafted and Beautify (and with CobbleFurnies first), every furniture spot of the Terrace and Terrace II is a
	 * mod's piece, none left vanilla, and each home's kitchen has a modded table and chair.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyTerraceHomeGetsTheModsFurniture(GameTestHelper helper) {
		for (List<String> order : List.of(ORDER, List.of("cobblefurnies", "handcrafted", "beautify"))) {
			HOMES.forEach((name, count) -> {
				List<FurnitureSpot> spots = BlueprintFurniture.of(AliveWorkplace.id(name));
				FurnitureChoice choice = FurnitureChoice.choose(spots, order, true);
				for (int i = 0; i < spots.size(); i++) {
					helper.assertTrue(choice.spots().containsKey(i), name + " with " + order + ": its " + spots.get(i).kind().id() + " at "
						+ spots.get(i).pos().toShortString() + " stays vanilla");
				}
				kitchensModded(helper, name + " with " + order, choice, count);
			});
		}
		helper.succeed();
	}

	/** Each home's kitchen (y = 1, between its party walls) has exactly one modded table and one modded chair in {@code choice}. */
	private static void kitchensModded(GameTestHelper helper, String what, FurnitureChoice choice, int count) {
		for (int home = 0; home < count; home++) {
			int x0 = 1 + 5 * home;
			for (FurnitureSpot.Kind kind : List.of(FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.CHAIR)) {
				long modded = choice.spots().values().stream().filter(p -> p.kind() == kind && p.pos().getY() == 1
					&& p.pos().getX() > x0 && p.pos().getX() < x0 + 5 && !p.mod().equals("minecraft")).count();
				helper.assertTrue(modded == 1, what + ": home " + (home + 1) + " has " + modded + " modded " + kind.id() + "s");
			}
		}
	}

	/**
	 * The Terrace's spots file as it was before B281: without the tables pushed against a wall (a fence post joined to
	 * it), which the generator learned to see then; the Terrace's east table is one.
	 */
	static List<FurnitureSpot> beforeB281(List<FurnitureSpot> spots) {
		return spots.stream().filter(s -> s.kind() != FurnitureSpot.Kind.TABLE || !joined(s.blocks().get(0).state())).toList();
	}

	private static boolean joined(BlockState state) {
		return state.getBlock() instanceof FenceBlock && (state.getValue(FenceBlock.NORTH) || state.getValue(FenceBlock.EAST)
			|| state.getValue(FenceBlock.SOUTH) || state.getValue(FenceBlock.WEST));
	}

	/** {@code choice} as a site saves and loads it. */
	private static FurnitureChoice saved(FurnitureChoice choice) {
		return FurnitureChoice.load(choice.save());
	}

	/**
	 * B641 (the owner, 2026-10-27: the west kitchen still spruce stand-ins, the east one Handcrafted's): a Terrace planned
	 * with the spots file of before B281 (its pieces saved by spot index), built from today's drawing and spots file, still
	 * gets every piece it chose where it chose it, in both homes: the east home's chair and every bed upstairs too, which
	 * the shifted indices used to leave vanilla.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTerracePlannedBeforeItsSpotsGrewKeepsItsPiecesInBothHomes(GameTestHelper helper) {
		HOMES.keySet().forEach(name -> {
			ResourceLocation id = AliveWorkplace.id(name);
			List<FurnitureSpot> spots = BlueprintFurniture.of(id);
			List<FurnitureSpot> older = beforeB281(spots);
			helper.assertTrue(older.size() < spots.size(), name + " has no table against a wall: " + spots.size() + " spots");
			FurnitureChoice planned = saved(FurnitureChoice.choose(older, ORDER, true));
			helper.assertTrue(planned.spots().size() == older.size(), name + ": only " + planned.spots().size() + " of " + older.size() + " spots chosen");
			Map<BlockPos, BlockState> built = blocks(planned.apply(BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(), spots));
			List<String> wrong = new ArrayList<>();
			for (FurnitureChoice.Placed placed : planned.spots().values()) {
				placed.cells().forEach((at, state) -> {
					BlockState there = built.get(placed.pos().offset(at));
					if (there == null || there.getBlock() != state.getBlock()) {
						wrong.add(placed.kind().id() + " at " + placed.pos().offset(at).toShortString() + " is " + there);
					}
				});
			}
			helper.assertTrue(wrong.isEmpty(), name + ": " + wrong.size() + " piece(s) not as planned: " + wrong);
		});
		helper.succeed();
	}

	/**
	 * B641: refreshing such a Terrace (36.21) brings it up to the latest drawing, furniture too: every spot the drawing
	 * gained since it was planned gets the furniture mod's piece, so both kitchens have a modded table and chair; a
	 * Terrace planned with no furniture mod stays vanilla.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRefreshedTerraceGetsTheModsTableInBothKitchens(GameTestHelper helper) {
		HOMES.forEach((name, count) -> {
			List<FurnitureSpot> spots = BlueprintFurniture.of(AliveWorkplace.id(name));
			FurnitureChoice refreshed = saved(FurnitureChoice.choose(beforeB281(spots), ORDER, true)).withNewSpots(spots, ORDER, true);
			for (int i = 0; i < spots.size(); i++) {
				helper.assertTrue(refreshed.spots().containsKey(i), name + " refreshed: its " + spots.get(i).kind().id() + " at "
					+ spots.get(i).pos().toShortString() + " stays vanilla");
			}
			kitchensModded(helper, name + " refreshed", refreshed, count);
			helper.assertTrue(FurnitureChoice.VANILLA.withNewSpots(spots, ORDER, true).isVanilla(), name + ": a vanilla Terrace was furnished by a refresh");
		});
		helper.succeed();
	}

	/**
	 * B641: both kitchens keep their modded table and chair however the Terrace is placed: each of the four turns, plain
	 * and mirrored either way, for a new Terrace and for one planned before B281 and refreshed.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bothKitchensKeepTheModsFurnitureTurnedAndMirrored(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
		HOMES.forEach((name, count) -> {
			ResourceLocation id = AliveWorkplace.id(name);
			List<FurnitureSpot> spots = BlueprintFurniture.of(id);
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow();
			Map<String, FurnitureChoice> choices = Map.of("new", FurnitureChoice.choose(spots, ORDER, true),
				"refreshed", saved(FurnitureChoice.choose(beforeB281(spots), ORDER, true)).withNewSpots(spots, ORDER, true));
			choices.forEach((how, choice) -> {
				Blueprint built = choice.apply(base, spots);
				for (Rotation rotation : Rotation.values()) {
					for (Mirror mirror : Mirror.values()) {
						BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), origin, rotation, mirror);
						Map<BlockPos, BlockState> targets = BuildPlan.create(built, placement).targets();
						String what = how + " " + name + " " + rotation + " " + mirror;
						for (int home = 0; home < count; home++) {
							int x0 = 1 + 5 * home;
							for (FurnitureSpot.Kind kind : List.of(FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.CHAIR)) {
								long modded = spots.stream().filter(s -> s.kind() == kind && s.pos().getY() == 1 && s.pos().getX() > x0 && s.pos().getX() < x0 + 5)
									.filter(s -> {
										BlockState there = targets.get(origin.offset(StructureTemplate.transform(s.pos(), mirror, rotation, BlockPos.ZERO)));
										return there != null && !BuiltInRegistries.BLOCK.getKey(there.getBlock()).getNamespace().equals("minecraft");
									}).count();
								helper.assertTrue(modded == 1, what + ": home " + (home + 1) + "'s kitchen has " + modded + " modded " + kind.id() + "s");
							}
						}
					}
				}
			});
		});
		helper.succeed();
	}

	/**
	 * B641 (the owner's west kitchen: crockery in the air over a spruce table): no build of the modded library has a small
	 * thing (a pot, a candle, Handcrafted's crockery) over a block with no top to stand on, built with every spot
	 * furnished, with none (a fence-and-plate table left vanilla keeps no crockery a block over its plate), or as a
	 * Terrace planned before B281 (its east table left vanilla).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noModdedBuildHasCrockeryInTheAir(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (ResourceLocation id : ModdedLibrary.builds()) {
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), id).orElse(null);
			if (base == null || !ModdedLibrary.complete(id)) {
				continue;
			}
			checked++;
			List<FurnitureSpot> spots = BlueprintFurniture.of(id);
			problems.addAll(inTheAir("furnished " + id.getPath(), FurnitureChoice.choose(spots, ORDER, true).withLibrary(true).apply(base, id)));
			problems.addAll(inTheAir("unfurnished " + id.getPath(), new FurnitureChoice(Map.of(), true).apply(base, id)));
		}
		helper.assertTrue(checked > 100, "only " + checked + " modded builds were checked");
		for (String name : HOMES.keySet()) {
			ResourceLocation id = AliveWorkplace.id(name);
			FurnitureChoice planned = saved(FurnitureChoice.choose(beforeB281(BlueprintFurniture.of(id)), ORDER, true).withLibrary(true));
			problems.addAll(inTheAir("pre-B281 " + name, planned.apply(BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(), id)));
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " small thing(s) in the air: " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
		helper.succeed();
	}

	/** Where in {@code built} a small thing (or any crockery) stands over air or a block with no top (a pressure plate). */
	private static List<String> inTheAir(String what, Blueprint built) {
		Map<BlockPos, BlockState> at = blocks(built);
		int minY = at.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
		List<String> out = new ArrayList<>();
		at.forEach((pos, state) -> {
			boolean small = Resting.standsOnFloor(state) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("crockery");
			if (!small || state.getBlock() instanceof net.minecraft.world.level.block.BasePressurePlateBlock
				|| state.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock || pos.getY() == minY) {
				return; // a table's plate or a carpet lies on its block; the bottom row stands on the ground
			}
			BlockState above = at.get(pos.above());
			if (above != null && above.getBlock() instanceof net.minecraft.world.level.block.ChainBlock) {
				return; // hanging from a chain
			}
			BlockState below = at.get(pos.below());
			if (below == null || below.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
				out.add(what + ": " + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " at " + pos.toShortString() + " over " + below);
			}
		});
		return out;
	}

	private static Map<BlockPos, BlockState> blocks(Blueprint blueprint) {
		Map<BlockPos, BlockState> at = new HashMap<>();
		blueprint.blocks().forEach(e -> at.put(e.pos(), e.state()));
		return at;
	}
}
