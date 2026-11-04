package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot.Kind;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-11-04), verifying B903 from 37.5 (owner's answer 2026-10-18: "the Healing Center's and the
 * Schoolhouse's desks are CobbleFurnies' Poke Ball desks at every level"): a STYLED Healing Center or Schoolhouse
 * (aliveworkplace:styled/&lt;style&gt;/aliveworkplace/healing_center, from a city zone's style or the Table's style picker)
 * gets them too, through the path a builder takes: a builder is given the styled build ({@link Builders#start}) and the
 * site makes its plan (the furniture is chosen there, once, and saved on the site), with CobbleFurnies listed after
 * Handcrafted. Every desk the plan puts is {@code cobblefurnies:poke_ball_desk}. A styled Library (which has desks too)
 * is the control: its desks follow the order (Handcrafted's), never a Poke Ball desk.
 * <p>
 * Every style that is for the building and has a blueprint is checked, at levels 1 and 2.
 */
public class QaStyledPokeDesks1104dCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final String POKE_DESK = "cobblefurnies:poke_ball_desk";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	/** The styled ids of {@code bases} that exist on this server. */
	private static List<ResourceLocation> styled(ServerLevel level, List<String> bases) {
		List<ResourceLocation> out = new ArrayList<>();
		for (String name : bases) {
			ResourceLocation base = AliveWorkplace.id(name);
			for (BlueprintStyles.Style style : BlueprintStyles.all()) {
				ResourceLocation id = BlueprintStyles.styled(base, style.name());
				if (style.isFor(base) && BlueprintLibrary.get(level, id).isPresent()) {
					out.add(id);
				}
			}
		}
		return out;
	}

	/**
	 * Gives a fresh builder the build {@code id} at (9, 2, 3), makes its plan as the site does, and returns each desk's
	 * planned block id (as the plan puts it, at the desk's spot); the site and the builder are gone afterwards.
	 */
	private static List<String> plannedDesks(GameTestHelper helper, ResourceLocation id, List<String> problems) {
		ServerLevel level = helper.getLevel();
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		BuildSite site = null;
		List<String> desks = new ArrayList<>();
		try {
			Builders.employ(level, builder, helper.absolutePos(new BlockPos(2, 2, 2)));
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)),
				Rotation.NONE, Mirror.NONE);
			site = Builders.start(level, builder, null, id, placement);
			BuildPlan plan = site.plan(level);
			if (plan == null) {
				problems.add(id + ": no plan (" + site.status() + ")");
				return desks;
			}
			FurnitureChoice choice = site.furniture();
			Map<BlockPos, BlockState> targets = plan.targets();
			for (FurnitureChoice.Placed placed : choice.spots().values()) {
				if (placed.kind() != Kind.DESK) {
					continue;
				}
				BlockPos at = placement.origin().offset(placed.pos());
				BlockState there = targets.get(at);
				desks.add(there == null ? "nothing planned at " + placed.pos().toShortString() : BuiltInRegistries.BLOCK.getKey(there.getBlock()).toString());
			}
		} finally {
			if (site != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
			builder.discard();
		}
		return desks;
	}

	private static void setUp(GameTestHelper helper) {
		new WorkplaceConfig().apply();
		Furnishings.MODS = List.of("handcrafted", "cobblefurnies", "beautify"); // CobbleFurnies after Handcrafted, as the pack has it
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
	}

	private static void checkPokeDesks(GameTestHelper helper, List<String> bases) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int buildings = 0;
		int desks = 0;
		try {
			setUp(helper);
			List<ResourceLocation> ids = styled(level, bases);
			helper.assertTrue(!ids.isEmpty(), "setup: no styled blueprint of " + bases + " on this server");
			for (ResourceLocation id : ids) {
				List<String> planned = plannedDesks(helper, id, problems);
				if (planned.isEmpty()) {
					problems.add(id + ": the plan has no desk");
				}
				for (String block : planned) {
					desks++;
					if (!block.equals(POKE_DESK)) {
						problems.add(id + ": a desk is " + block);
					}
				}
				buildings++;
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), "styled " + bases + " built with CobbleFurnies don't get Poke Ball desks (" + problems.size() + " of "
			+ desks + " desks in " + buildings + " builds): " + problems.stream().limit(8).toList());
		helper.assertTrue(buildings > 0 && desks > 0, "checked no desk");
		LOGGER.info("QaStyledPokeDesks1104d: {} styled {} builds planned, all {} desks Poke Ball desks", buildings, bases, desks);
		helper.succeed();
	}

	/** Every styled Healing Center (levels 1 and 2): every desk its builder's plan puts is a Poke Ball desk. */
	//$ gametest_batch HUGE_AREA '"qaStyledPokeDesksHealing1104d"'
	@GameTest(template = HUGE_AREA, batch = "qaStyledPokeDesksHealing1104d")
	public void aStyledHealingCenterIsPlannedWithPokeBallDesks(GameTestHelper helper) {
		checkPokeDesks(helper, List.of("healing_center", "healing_center_2"));
	}

	/** Every styled Schoolhouse (levels 1 and 2): every desk its builder's plan puts is a Poke Ball desk. */
	//$ gametest_batch HUGE_AREA '"qaStyledPokeDesksSchool1104d"'
	@GameTest(template = HUGE_AREA, batch = "qaStyledPokeDesksSchool1104d")
	public void aStyledSchoolhouseIsPlannedWithPokeBallDesks(GameTestHelper helper) {
		checkPokeDesks(helper, List.of("schoolhouse", "schoolhouse_2"));
	}

	/** The control: a styled Library's desks follow the order (Handcrafted's), not Poke Ball desks. */
	//$ gametest_batch HUGE_AREA '"qaStyledPokeDesksLibrary1104d"'
	@GameTest(template = HUGE_AREA, batch = "qaStyledPokeDesksLibrary1104d")
	public void aStyledLibraryKeepsHandcraftedDesks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int desks = 0;
		int buildings = 0;
		try {
			setUp(helper);
			List<ResourceLocation> ids = styled(level, List.of("library"));
			helper.assertTrue(!ids.isEmpty(), "setup: no styled Library on this server");
			for (ResourceLocation id : ids) {
				List<String> planned = plannedDesks(helper, id, problems);
				helper.assertTrue(!planned.isEmpty(), "setup: " + id + " has no desk");
				for (String block : planned) {
					desks++;
					if (!block.startsWith("handcrafted:") || !block.endsWith("_desk")) {
						problems.add(id + ": a desk is " + block);
					}
				}
				buildings++;
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), "a styled Library's desks aren't Handcrafted's (the order's): " + problems.stream().limit(8).toList());
		LOGGER.info("QaStyledPokeDesks1104d: {} styled Library builds planned, all {} desks Handcrafted's", buildings, desks);
		helper.succeed();
	}
}
