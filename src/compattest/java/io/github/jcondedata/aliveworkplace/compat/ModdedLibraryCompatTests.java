package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 37.12 with the pack's building mods (Rechiseled, Supplementaries, Carved Wood, Chipped, Handcrafted): every
 * modded copy can be built here and has its vanilla build's shape; a new Starter Cottage is planned from its copy (daub,
 * herringbone floors, Carved Wood chests) and still gets its Handcrafted furniture; the switch off, or furnished builds
 * off, plans the vanilla build; a saved site keeps the library it was planned with.
 */
public class ModdedLibraryCompatTests implements FabricGameTest {
	private static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	/** What a copy may add in an empty cell: decoration only, nothing that holds items (no job may take it for a chest). */
	private static final Set<String> DECOR = Set.of("handcrafted:wood_crockery_combo", "handcrafted:stackable_book", "handcrafted:terracotta_wide_pot");

	private static BuildSite site(GameTestHelper helper) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		return new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE, placement);
	}

	/** The block ids a plan places. */
	private static Set<String> blocks(BuildPlan plan) {
		Set<String> out = new HashSet<>();
		Stream.of(BuildPlan.Stage.values()).flatMap(s -> plan.steps(s).stream())
			.forEach(s -> out.add(BuiltInRegistries.BLOCK.getKey(s.state().getBlock()).toString()));
		return out;
	}

	private static boolean dressed(Set<String> blocks) {
		return blocks.contains("supplementaries:daub") && blocks.contains("chipped:herringbone_spruce_planks") && blocks.contains("carved_wood:spruce_chest");
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyModdedCopyCanBeBuiltWithThePacksMods(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (ResourceLocation id : ModdedLibrary.builds()) {
			if (!ModdedLibrary.missing(id).isEmpty()) {
				wrong.add(id + " lacks " + ModdedLibrary.missing(id));
				continue;
			}
			Blueprint copy = ModdedLibrary.copy(id).orElse(null);
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), id).orElse(null);
			if (copy == null || base == null) {
				wrong.add(id + ": copy " + (copy != null) + ", vanilla " + (base != null));
				continue;
			}
			if (!copy.size().equals(base.size()) || copy.basement() != base.basement()) {
				wrong.add(id + " is " + copy.size() + " not " + base.size());
				continue;
			}
			// The same shape: every block of the vanilla build is in the copy, at its place; doors, beds and job blocks unchanged.
			Map<BlockPos, BlockState> at = new HashMap<>();
			copy.blocks().forEach(e -> at.put(e.pos(), e.state()));
			int differ = 0;
			for (Blueprint.Entry e : base.blocks()) {
				BlockState mine = at.get(e.pos());
				if (mine == null || !e.state().isAir() && mine.isAir()) {
					wrong.add(id + " lost the block at " + e.pos());
					break;
				}
				if (e.state().isAir() && !mine.isAir() && !DECOR.contains(BuiltInRegistries.BLOCK.getKey(mine.getBlock()).toString())) {
					wrong.add(id + " has " + mine + " in an empty cell at " + e.pos());
					break;
				}
				String name = BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).getPath();
				if (mine.getBlock() != e.state().getBlock()) {
					differ++;
					if (name.endsWith("_door") || name.endsWith("_bed") || name.equals("lectern") || name.equals("barrel") || name.equals("smithing_table")) {
						wrong.add(id + " changed its " + name + " at " + e.pos());
						break;
					}
				}
			}
			for (FurnitureSpot spot : BlueprintFurniture.of(id)) {
				for (FurnitureSpot.Cell cell : spot.blocks()) {
					BlockState mine = at.get(spot.pos().offset(cell.at()));
					if (mine == null || mine.getBlock() != cell.state().getBlock()) {
						wrong.add(id + "'s " + spot.kind() + " spot at " + spot.pos() + " changed");
					}
				}
			}
			if (differ == 0 && copy.blocks().size() == base.blocks().size()) {
				wrong.add(id + " is the vanilla build unchanged");
			}
		}
		helper.assertTrue(ModdedLibrary.builds().size() > 100, "only " + ModdedLibrary.builds().size() + " copies");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.subList(0, Math.min(8, wrong.size())));
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStarterCottageIsBuiltFromTheModdedLibrary(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			BuildSite site = site(helper);
			BuildPlan plan = site.plan(helper.getLevel());
			Set<String> blocks = blocks(plan);
			helper.assertTrue(site.furniture().modded(), "the new cottage wasn't planned from the modded library");
			helper.assertTrue(dressed(blocks), "the cottage isn't dressed: " + blocks);
			helper.assertTrue(blocks.contains("handcrafted:spruce_chair") && blocks.contains("handcrafted:spruce_fancy_bed"),
				"the modded cottage lost its Handcrafted furniture: " + blocks);
			helper.assertTrue(blocks.contains("handcrafted:wood_crockery_combo"), "no crockery on the table: " + blocks);
			helper.assertTrue(!blocks.contains("minecraft:white_concrete") && !blocks.contains("minecraft:red_bed"), "vanilla plaster or bed left: " + blocks);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"moddedLibrary"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "moddedLibrary")
	public void switchedOffItIsTheVanillaLibrary(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{\"moddedLibrary\": false}").apply();
			BuildSite site = site(helper);
			Set<String> blocks = blocks(site.plan(helper.getLevel()));
			helper.assertTrue(!site.furniture().modded(), "planned from the modded library with the switch off");
			helper.assertTrue(blocks.contains("minecraft:birch_planks") && !blocks.contains("supplementaries:daub"), "not the vanilla cottage: " + blocks);
			helper.assertTrue(blocks.contains("handcrafted:spruce_chair"), "the vanilla cottage is no longer furnished: " + blocks);
			WorkplaceConfig.parse("{\"furnishedBuilds\": false}").apply();
			BuildSite plain = site(helper);
			Set<String> plainBlocks = blocks(plain.plan(helper.getLevel()));
			helper.assertTrue(!plain.furniture().modded() && !plainBlocks.contains("supplementaries:daub") && plainBlocks.contains("minecraft:red_bed"),
				"furnished builds off didn't plan the plain vanilla cottage: " + plainBlocks);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"moddedLibrary"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "moddedLibrary")
	public void aSavedSiteKeepsItsLibrary(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			BuildSite site = site(helper);
			site.plan(helper.getLevel());
			site.advance();
			net.minecraft.nbt.CompoundTag saved = site.save();
			WorkplaceConfig.parse("{\"moddedLibrary\": false}").apply();
			BuildSite loaded = BuildSite.load(saved);
			helper.assertTrue(loaded.furniture().modded() && dressed(blocks(loaded.plan(helper.getLevel()))), "a reload with the switch off changed the build's library");
			WorkplaceConfig.parse("{}").apply();
			BuildSite vanilla = site(helper);
			WorkplaceConfig.parse("{\"moddedLibrary\": false}").apply();
			vanilla.plan(helper.getLevel());
			net.minecraft.nbt.CompoundTag vanillaSaved = vanilla.save();
			new WorkplaceConfig().apply();
			BuildSite back = BuildSite.load(vanillaSaved);
			helper.assertTrue(!back.furniture().modded() && !blocks(back.plan(helper.getLevel())).contains("supplementaries:daub"),
				"a vanilla site became modded after a reload with the switch on");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}
}
