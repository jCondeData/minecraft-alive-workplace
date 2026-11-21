package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.move.MoveFit;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA lane E, second pass (2026-11-17): the features that read a finished building's drawing by its id, once that
 * drawing was rebuilt from scratch (Milestones 49-52; tools/blueprints/REBUILD.md "standing buildings ... keep their old
 * drawing until a refresh"): the Mover (45.x) through the Hall's Move page, a sneak take-down, and the Move page's fit
 * check against such a house. Built on {@link RebuildDrawings1117e}'s drawings and stage.
 */
final class RebuildMoves1117e2 {
	/** The Mover and his crate (just north of him), inside the village round the hall. */
	static final BlockPos MOVER = new BlockPos(18, 2, 18);
	/** A villager who sleeps in the old house's bed (stands outside every box involved). */
	static final BlockPos SLEEPER = new BlockPos(12, 2, 3);
	/** Where the house's new spot's middle is clicked on the map: clear of the old plot, the hall and the bench. */
	static final BlockPos NEW_MIDDLE = new BlockPos(6, 2, 17);
	static final int MAX_STEPS = 5000;
	static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** The Test Hut (5 x 4 x 5) as a second building of the village: x 14-18, z 2-6. */
	static final BlockPos HUT_ORIGIN = new BlockPos(14, 2, 2);

	private RebuildMoves1117e2() {
	}

	static Villager mover(GameTestHelper helper) {
		helper.setBlock(MOVER.north(), ModBlocks.MOVERS_CRATE);
		Villager villager = helper.spawn(EntityType.VILLAGER, MOVER);
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.MOVER));
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(MOVER.north())));
		return villager;
	}

	/** A villager whose home is the old house's bed (its ticket taken), as a resident's. */
	static Villager sleeper(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityType.VILLAGER, SLEEPER);
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at(BED_HEAD))));
		claimBed(helper);
		return villager;
	}

	/** The move's own clean-up: whatever is left of it ended, the waiting move dropped, every record of {@code id} forgotten. */
	static void cleanUpMoves(GameTestHelper helper, Stage s) {
		ServerLevel level = s.level();
		BlockPos from = s.placement().origin();
		Leftovers.after(helper, () -> {
			MovePage.stop(s.owner());
			MoveSites.get(level).cancel(level, from);
			MoveSites.get(level).all().stream().filter(x -> x.from().equals(from)).forEach(x -> {
				for (int i = 0; i < MAX_STEPS && x.stage() != MoveSite.Stage.DONE; i++) {
					MoveSites.get(level).step(level, x);
				}
			});
			PendingMoves.get(level).remove(from);
			BuildSiteManager sites = BuildSiteManager.get(level);
			sites.finishedIn(level).stream().filter(f -> f.structure().equals(s.id()) || f.structure().equals(HUT)).toList()
				.forEach(f -> sites.forgetFinished(f.placement()));
		});
	}

	/** The Test Hut standing at {@link #HUT_ORIGIN}, recorded finished. */
	static BlueprintData.Placement standHut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(HUT, placement, UUID.randomUUID());
		return placement;
	}

	/** The hall opened, its Move tab clicked, and the row of the building at {@code from} (absolute) clicked: the picker. */
	static ChoiceMenu pick(GameTestHelper helper, Stage s, BlockPos from) {
		for (int row = 0; row < 6; row++) {
			ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), helper.absolutePos(HALL));
			menu.press(HallPages.slot(MovePage.PAGE), s.owner());
			menu.press(VillageHallScreen.FIRST_ROW + row, s.owner());
			Optional<MovePage.Picking> p = MovePage.picking(s.owner());
			if (p.isPresent() && p.get().from().equals(from)) {
				return menu;
			}
			menu.removed(s.owner());
			MovePage.stop(s.owner());
		}
		helper.fail("no row of the Move page opens the building at " + from);
		return null;
	}

	/** A click on the village map with the new spot's middle on block ({@code x}, {@code z}) relative to the test. */
	static MoveFit.Fit clickMap(GameTestHelper helper, Stage s, ChoiceMenu menu, int x, int z) {
		BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
		int id = MovePage.mapClick(s.owner(), at.getX(), at.getZ());
		helper.assertTrue(MovePage.handles(id), "(setup) the block " + at + " isn't on the map: " + id);
		helper.assertTrue(menu.clickMenuButton(s.owner(), id), "(setup) the hall didn't take the map click " + id);
		return MovePage.fit(s.owner()).orElseThrow(() -> new AssertionError("(setup) no fit after the map click"));
	}

	/** The house picked on the Move page, its new spot clicked, and Confirm pressed: the new lowest corner. */
	static BlockPos confirmMove(GameTestHelper helper, Stage s) {
		ChoiceMenu menu = pick(helper, s, s.placement().origin());
		MoveFit.Fit fit = clickMap(helper, s, menu, NEW_MIDDLE.getX(), NEW_MIDDLE.getZ());
		helper.assertTrue(fit.fits(), "(setup) the new spot doesn't fit: " + fit.reason() + " " + fit.what());
		helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "(setup) Confirm isn't ready: " + menu.icon(MovePage.CONFIRM));
		menu.press(MovePage.CONFIRM, s.owner());
		PendingMoves.Move move = PendingMoves.get(s.level()).forBuilding(s.placement().origin())
			.orElseThrow(() -> new AssertionError("(setup) Confirm saved no move"));
		menu.removed(s.owner());
		return move.to();
	}

	/** The Mover's work, the moves saved and loaded back every 7 steps, until it's over; the site worked on. */
	static MoveSite moveWithReloads(GameTestHelper helper, Stage s, Villager mover) {
		ServerLevel level = s.level();
		BlockPos from = s.placement().origin();
		MoveSite site = MoveSites.get(level).work(level, helper.absolutePos(HALL)).orElseThrow(() -> new AssertionError("the Mover started no site"));
		int steps = 0;
		while (site.stage() != MoveSite.Stage.DONE && steps++ < MAX_STEPS) {
			MoverWork.work(level, mover, site);
			if (steps % 7 == 0 && site.stage() != MoveSite.Stage.DONE) {
				CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
				level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
				site = MoveSites.get(level).forBuilding(from).orElseThrow();
			}
		}
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move never ended: " + site.stage());
		return site;
	}

	/** What of the old house is still at its old spot (relative spots), any block at all. */
	static List<BlockPos> leftAtOldSpot(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(4, 1, 4))) {
			if (!helper.getLevel().getBlockState(helper.absolutePos(at(p))).isAir()) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** Where in the area {@code block} stands, anywhere (y 2-4), as test-relative spots. */
	static List<BlockPos> anywhere(GameTestHelper helper, Block block) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(21, 4, 21))) {
			if (helper.getLevel().getBlockState(helper.absolutePos(p)).is(block)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/**
	 * The whole old house stands at {@code to} (absolute lowest corner, unturned), block for block, its chest with its 7
	 * emeralds; nothing of it left at the old spot; nothing of the new drawing anywhere; the sleeper's home is the moved bed.
	 */
	static void assertMovedWhole(GameTestHelper helper, Stage s, BlockPos to, Villager sleeper) {
		ServerLevel level = s.level();
		List<BlockPos> left = leftAtOldSpot(helper);
		helper.assertTrue(left.isEmpty(), "the old spot still has " + left.size() + " blocks of the house, first "
			+ left.stream().limit(6).map(p -> p + " " + level.getBlockState(helper.absolutePos(at(p))).getBlock().getName().getString()).toList());
		List<String> wrong = new ArrayList<>();
		for (Blueprint.Entry e : oldDrawing(true)) {
			BlockPos abs = to.offset(e.pos());
			if (!MaterialRules.matches(level.getBlockState(abs), e.state())) {
				wrong.add(e.pos() + " " + level.getBlockState(abs).getBlock().getName().getString());
			}
		}
		helper.assertTrue(wrong.isEmpty(), "at the new spot " + wrong.size() + " of the house's " + oldDrawing(true).size()
			+ " blocks are missing or wrong, first " + wrong.stream().limit(6).toList());
		if (!(level.getBlockEntity(to.offset(CHEST)) instanceof Container chest) || chest.countItem(Items.EMERALD) != 7) {
			helper.fail("the moved chest hasn't its 7 emeralds: " + level.getBlockEntity(to.offset(CHEST)));
		}
		helper.assertTrue(anywhere(helper, Blocks.SPRUCE_PLANKS).isEmpty() && anywhere(helper, Blocks.STONE_BRICKS).isEmpty(),
			"the new drawing's blocks were invented: spruce " + anywhere(helper, Blocks.SPRUCE_PLANKS) + ", stone bricks " + anywhere(helper, Blocks.STONE_BRICKS));
		helper.assertTrue(anywhere(helper, Blocks.COBBLESTONE).size() == 25 && anywhere(helper, Blocks.OAK_PLANKS).size() == 15,
			"the house's blocks are " + anywhere(helper, Blocks.COBBLESTONE).size() + " cobblestone (25) and " + anywhere(helper, Blocks.OAK_PLANKS).size() + " oak planks (15)");
		Optional<GlobalPos> home = sleeper.getBrain().getMemory(MemoryModuleType.HOME);
		helper.assertTrue(home.isPresent() && home.get().pos().equals(to.offset(BED_HEAD)), "the sleeper's home is " + home + ", not the moved bed at " + to.offset(BED_HEAD));
		helper.assertTrue(PendingMoves.get(level).forBuilding(s.placement().origin()).isEmpty(), "the move still waits");
		helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream().anyMatch(f -> f.structure().equals(s.id()) && f.placement().origin().equals(to)),
			"the hall's record didn't follow the house to " + to);
	}

	/** The house's placed blueprint, sneak-handed to its builder as the client sends it: a take-down. */
	static BuildSite takeDown(GameTestHelper helper, Stage s, Villager builder) {
		ItemStack blueprint = BlueprintItem.create(s.id(), null);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(s.placement())));
		Builders.assign(s.owner(), builder, blueprint, true);
		BuildSite site = Builders.activeSite(s.level(), builder);
		helper.assertTrue(site != null && site.isDeconstruction(), "(setup) the sneak hand-over started no take-down: " + site);
		return site;
	}

	/**
	 * After a take-down: the site over and the builder free; nothing of the house left but what a take-down keeps standing
	 * (the chest with its 7 emeralds and the lectern, blocks that hold things); the hall no longer lists it.
	 */
	static void assertTakenDown(GameTestHelper helper, Stage s, Villager builder) {
		helper.assertTrue(sites(s) == 0, "still taking it down: " + BuildSiteManager.get(s.level()).all().stream()
			.filter(x -> x.placement().equals(s.placement())).map(x -> x.stage() + " " + x.status()).toList());
		helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "the builder is still at it");
		List<BlockPos> left = new ArrayList<>(where(helper, Blocks.COBBLESTONE));
		left.addAll(where(helper, Blocks.OAK_PLANKS));
		left.addAll(where(helper, Blocks.RED_BED));
		boolean listed = BuildSiteManager.get(s.level()).finishedIn(s.level()).stream().anyMatch(f -> f.placement().equals(s.placement()));
		helper.assertTrue(left.isEmpty(), "the house still stands: " + left.size() + " of its 42 cobblestone, plank and bed blocks are left (the hall "
			+ (listed ? "still lists it" : "no longer lists it") + "), first " + left.stream().limit(6).toList());
		helper.assertBlockPresent(Blocks.CHEST, at(CHEST));
		Container chest = helper.getBlockEntity(at(CHEST));
		helper.assertTrue(chest.countItem(Items.EMERALD) == 7, "the house's chest lost its emeralds: " + chest.countItem(Items.EMERALD));
		helper.assertBlockPresent(Blocks.LECTERN, at(LECTERN));
		helper.assertTrue(BuildSiteManager.get(s.level()).finishedIn(s.level()).stream().noneMatch(f -> f.placement().equals(s.placement())),
			"the hall still lists the taken-down house");
	}
}
