package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.move.MoveFit;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveResidents;
import io.github.jcondedata.aliveworkplace.move.MoveRules;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane b, 2026-11-11) for B1120, B1129 and B1154, written from the bug reports and 45.3/45.4, not the code. The
 * builder's tests cover his crate inside the house with the default quarter-turned move, one overlap not turned (a
 * plank on it), and a pre-fix save. These add: the new spot overlapping the crate by exactly one block when turned, and
 * one block off it (no overlap); a chest's spot landing on the crate (its items must not be lost); a save and reload in
 * every stage with the crate inside; the crate broken by a player while the house comes down and while it goes up; the
 * Move page's refusal of a spot over a Mover's Crate at the spot's edge from both sides (outside the house and in its
 * wall); and B1154's 128-block line from both sides (a resident just inside and just outside get the moved bed).
 */
public class QaMoverCrate1111bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String SMALL = "aliveworkplace_test:big_area";
	/** MoverGameTests' house (helper coordinates): 7 × 5 × 7 from MIN. */
	private static final BlockPos MIN = new BlockPos(2, 2, 2);
	private static final BlockPos MAX = new BlockPos(8, 6, 8);
	private static final BlockPos CHEST = new BlockPos(4, 3, 4);
	/** His only Mover's Crate, inside the house. */
	private static final BlockPos OWN_CRATE = new BlockPos(6, 3, 6);
	/** Where the Mover stands, out of every spot used here. */
	private static final BlockPos MOVER_AT = new BlockPos(24, 2, 24);

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** MoverGameTests' house and Mover, with his only crate inside the house and the move set to {@code to}/{@code rot}. */
	private static MoverGameTests.Setup crateInside(GameTestHelper helper, String name, BlockPos to, Rotation rot) {
		MoverGameTests.Setup s = MoverGameTests.setup(helper, name);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(12, 2, 11), Blocks.AIR); // no crate outside: his only one is in the house
		helper.setBlock(OWN_CRATE, ModBlocks.MOVERS_CRATE);
		s.mover().teleportTo(helper.absolutePos(MOVER_AT).getX() + 0.5, helper.absolutePos(MOVER_AT).getY(), helper.absolutePos(MOVER_AT).getZ() + 0.5);
		s.mover().getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(OWN_CRATE)));
		BlockPos absTo = to == null ? s.to() : helper.absolutePos(to);
		PendingMoves.Move pending = PendingMoves.get(level).forBuilding(s.from()).orElseThrow();
		PendingMoves.get(level).remove(s.from());
		PendingMoves.get(level).put(new PendingMoves.Move(pending.hall(), pending.from(), pending.structure(), absTo, rot, null));
		return new MoverGameTests.Setup(s.placement(), s.before(), s.hall(), s.from(), absTo, s.mover());
	}

	private static MoveSite work(ServerLevel level, MoverGameTests.Setup s, Predicate<MoveSite> stop) {
		return MoverGameTests.work(level, s, stop);
	}

	private static int crates(GameTestHelper helper) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 20, 29)))) {
			if (helper.getLevel().getBlockState(pos).is(ModBlocks.MOVERS_CRATE)) {
				n++;
			}
		}
		return n;
	}

	/** How many of {@code item} there are in the test area: in containers, lying loose, or carried by villagers. */
	private static int count(GameTestHelper helper, Item item) {
		ServerLevel level = helper.getLevel();
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 20, 29)))) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (c.getItem(i).is(item)) {
						n += c.getItem(i).getCount();
					}
				}
			}
		}
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(40);
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area)) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, area)) {
			for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
				if (v.getInventory().getItem(i).is(item)) {
					n += v.getInventory().getItem(i).getCount();
				}
			}
		}
		return n;
	}

	/** Blocks of the plan at the new spot that aren't what they should be, leaving out {@code except}; null when all are. */
	private static String wrongAtNewSpot(ServerLevel level, MoverGameTests.Setup s, Rotation rot, BlockPos except) {
		int wrong = 0;
		String first = null;
		for (MovePlan.Placed p : s.before().placed(s.to(), rot)) {
			if (p.pos().equals(except) || MoveSite.stays(p.state())) {
				continue;
			}
			if (!MaterialRules.matches(level.getBlockState(p.pos()), p.state())) {
				wrong++;
				if (first == null) {
					first = p.pos() + " is " + level.getBlockState(p.pos()) + ", not " + p.state();
				}
			}
		}
		return wrong == 0 ? null : wrong + " wrong, first " + first;
	}

	/** What of the plan would stand at {@code abs} for a move to {@code s.to()} turned by {@code rot} (null: nothing). */
	private static MovePlan.Placed landingOn(MoverGameTests.Setup s, Rotation rot, BlockPos abs) {
		for (MovePlan.Placed p : s.before().placed(s.to(), rot)) {
			if (p.pos().equals(abs)) {
				return p;
			}
		}
		return null;
	}

	/** The end of every move with his crate inside: done, his crate standing where it stood and his, nothing dropped. */
	private static void assertDoneCrateKept(GameTestHelper helper, ServerLevel level, MoverGameTests.Setup s, MoveSite site, String when) {
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.waiting() == null && !site.canceled(),
			when + ": the move ended " + site.stage() + ", waiting " + site.waiting() + ", canceled " + site.canceled());
		helper.assertTrue(level.getBlockState(helper.absolutePos(OWN_CRATE)).is(ModBlocks.MOVERS_CRATE),
			when + ": his crate isn't where it stood: " + level.getBlockState(helper.absolutePos(OWN_CRATE)));
		helper.assertTrue(MoverWork.hasCrate(level, s.mover()), when + ": the Mover lost his crate (workstation)");
		helper.assertTrue(crates(helper) == 1, when + ": " + crates(helper) + " Mover's Crates stand, not 1");
		helper.assertTrue(PendingMoves.get(level).forBuilding(s.from()).isEmpty() && MoveSites.get(level).forBuilding(s.from()).isEmpty(),
			when + ": the move wasn't put away");
	}

	// ---- B1129: the new spot over his crate --------------------------------------------------------------------------

	/**
	 * B1129, turned a quarter: the new spot's west wall column falls on his crate (an overlap of exactly one block). The
	 * one wall block that would stand there is left out, his crate is never broken or dropped, he keeps it as his
	 * workstation, and the rest of the house (with the chest's items) stands at the new spot.
	 */
	//$ gametest_batch AREA '"qaB1111CrateOverlapTurned"'
	@GameTest(template = AREA, batch = "qaB1111CrateOverlapTurned")
	public void aTurnedSpotOverlappingHisCrateByOneBlockLeavesItStanding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qacrateturn", new BlockPos(6, 2, 2), Rotation.CLOCKWISE_90);
		helper.runAfterDelay(2, () -> {
			BlockPos crate = helper.absolutePos(OWN_CRATE);
			MovePlan.Placed on = landingOn(s, Rotation.CLOCKWISE_90, crate);
			helper.assertTrue(on != null && !on.state().isAir() && !MoveSite.stays(on.state()), "setup: no house block lands on his crate: " + on);
			int bread = count(helper, Items.BREAD);
			int emeralds = count(helper, Items.EMERALD);
			MoveSite site = work(level, s, x -> false);
			assertDoneCrateKept(helper, level, s, site, "turned, one block over the crate");
			helper.assertTrue(site.skipped() == 1, "skipped " + site.skipped() + ", not just the block where his crate stands");
			String wrong = wrongAtNewSpot(level, s, Rotation.CLOCKWISE_90, crate);
			helper.assertTrue(wrong == null, "the house at its new spot: " + wrong);
			helper.assertTrue(MoverGameTests.loose(level, helper) == 0, "something was dropped: " + MoverGameTests.loose(level, helper) + " items");
			helper.assertTrue(count(helper, Items.BREAD) == bread && count(helper, Items.EMERALD) == emeralds,
				"the chest's items changed: bread " + bread + " -> " + count(helper, Items.BREAD) + ", emeralds " + emeralds + " -> " + count(helper, Items.EMERALD));
			helper.succeed();
		});
	}

	/**
	 * The other side of that line: the new spot starts one block east of his crate (no overlap). Nothing is skipped, the
	 * whole house stands at the new spot, and his crate is untouched.
	 */
	//$ gametest_batch AREA '"qaB1111CrateBeside"'
	@GameTest(template = AREA, batch = "qaB1111CrateBeside")
	public void aSpotOneBlockOffHisCrateMovesTheWholeHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qacratebeside", new BlockPos(7, 2, 2), Rotation.CLOCKWISE_90);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(landingOn(s, Rotation.CLOCKWISE_90, helper.absolutePos(OWN_CRATE)) == null, "setup: the spot overlaps his crate");
			MoveSite site = work(level, s, x -> false);
			assertDoneCrateKept(helper, level, s, site, "one block off the crate");
			helper.assertTrue(site.skipped() == 0, "skipped " + site.skipped() + " with nothing in the way");
			String wrong = wrongAtNewSpot(level, s, Rotation.CLOCKWISE_90, null);
			helper.assertTrue(wrong == null, "the house at its new spot: " + wrong);
			helper.assertTrue(MoverGameTests.loose(level, helper) == 0, "something was dropped");
			helper.succeed();
		});
	}

	/**
	 * B1129 where the chest goes: moved two blocks south-east, not turned, the chest (12 bread, 64 emeralds) would stand
	 * where his crate stands. His crate stays, and the chest's items are not lost (45.3: nothing dropped, nothing lost).
	 */
	//$ gametest_batch AREA '"qaB1111CrateUnderChest"'
	@GameTest(template = AREA, batch = "qaB1111CrateUnderChest")
	public void aChestWhoseSpotIsHisCrateKeepsItsItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qacratechest", new BlockPos(4, 2, 4), Rotation.NONE);
		helper.runAfterDelay(2, () -> {
			MovePlan.Placed on = landingOn(s, Rotation.NONE, helper.absolutePos(OWN_CRATE));
			helper.assertTrue(on != null && on.state().is(Blocks.CHEST), "setup: the chest doesn't land on his crate: " + on);
			helper.assertTrue(count(helper, Items.BREAD) == 12 && count(helper, Items.EMERALD) == 64, "setup: the chest's items");
			MoveSite site = work(level, s, x -> false);
			assertDoneCrateKept(helper, level, s, site, "the chest's spot over the crate");
			helper.assertTrue(count(helper, Items.BREAD) == 12 && count(helper, Items.EMERALD) == 64,
				"the chest's items were lost: " + count(helper, Items.BREAD) + " bread and " + count(helper, Items.EMERALD) + " emeralds left of 12 and 64");
			helper.succeed();
		});
	}

	/**
	 * B1184: the same spot, but a lectern holding a book stands where the chest stood (it holds its book without an Items
	 * list). His crate stays, and neither the lectern nor its book is deleted: both lie on top of his crate.
	 */
	//$ gametest_batch AREA '"qaB1184CrateUnderLectern"'
	@GameTest(template = AREA, batch = "qaB1184CrateUnderLectern")
	public void aLecternWhoseSpotIsHisCrateKeepsItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qacratelectern", new BlockPos(4, 2, 4), Rotation.NONE);
		BlockPos chest = helper.absolutePos(new BlockPos(4, 3, 4));
		Clearable.tryClear(level.getBlockEntity(chest));
		level.setBlock(chest, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true), Block.UPDATE_CLIENTS);
		helper.assertTrue(level.getBlockEntity(chest) instanceof LecternBlockEntity, "setup: no lectern");
		((LecternBlockEntity) level.getBlockEntity(chest)).setBook(new ItemStack(Items.WRITABLE_BOOK));
		helper.runAfterDelay(2, () -> {
			MovePlan.Placed on = landingOn(s, Rotation.NONE, helper.absolutePos(OWN_CRATE));
			helper.assertTrue(on != null, "setup: nothing lands on his crate");
			MoveSite site = work(level, s, x -> false);
			assertDoneCrateKept(helper, level, s, site, "the lectern's spot over the crate");
			helper.assertTrue(site.skipped() == 1, "skipped " + site.skipped() + ", not just the lectern where his crate stands");
			helper.assertTrue(count(helper, Items.WRITABLE_BOOK) == 1, "the lectern's book was lost: " + count(helper, Items.WRITABLE_BOOK) + " lie loose");
			helper.assertTrue(count(helper, Items.LECTERN) == 1, "the lectern was lost: " + count(helper, Items.LECTERN) + " lie loose");
			helper.succeed();
		});
	}

	// ---- B1120: save and reload, the crate broken ------------------------------------------------------------------

	/** His crate inside, saved and reloaded every 5 steps (in every stage): the move still ends, nothing waits for the crate. */
	//$ gametest_batch AREA '"qaB1111CrateReload"'
	@GameTest(template = AREA, batch = "qaB1111CrateReload")
	public void hisCrateInsideSurvivesAReloadInEveryStage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qacratereload", null, Rotation.CLOCKWISE_90);
		helper.runAfterDelay(2, () -> {
			MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
			java.util.Set<MoveSite.Stage> reloadedIn = java.util.EnumSet.noneOf(MoveSite.Stage.class);
			int steps = 0;
			while (site.stage() != MoveSite.Stage.DONE && steps++ < MoverGameTests.MAX_STEPS) {
				MoverWork.work(level, s.mover(), site);
				helper.assertTrue(!MoverWork.NO_CRATE.equals(site.waiting()), "step " + steps + " (" + site.stage() + "): he waits for his crate");
				if (steps % 5 == 0 && site.stage() != MoveSite.Stage.DONE) {
					reloadedIn.add(site.stage());
					site = MoverGameTests.reload(level, s);
				}
			}
			helper.assertTrue(reloadedIn.containsAll(List.of(MoveSite.Stage.DISMANTLE, MoveSite.Stage.FOUNDATION, MoveSite.Stage.STRUCTURE)),
				"reloaded only in " + reloadedIn);
			assertDoneCrateKept(helper, level, s, site, "reloaded every 5 steps");
			helper.assertTrue(site.skipped() == 0, "skipped " + site.skipped());
			String wrong = wrongAtNewSpot(level, s, Rotation.CLOCKWISE_90, null);
			helper.assertTrue(wrong == null, "the house at its new spot: " + wrong);
			helper.assertTrue(MoverGameTests.loose(level, helper) == 0, "something was dropped");
			helper.succeed();
		});
	}

	/**
	 * His crate (inside the house) broken by a player while the house comes down: he takes nothing more down and says
	 * why (no endless "packing"), the crate lies there once; put back where it stood, he finishes and no second crate
	 * appears at the new spot.
	 */
	//$ gametest_batch AREA '"qaB1111CrateBrokenDown"'
	@GameTest(template = AREA, batch = "qaB1111CrateBrokenDown")
	public void hisCrateBrokenWhileItComesDownStopsHimUntilItIsBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qacratebroken", null, Rotation.CLOCKWISE_90);
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> x.packed() >= 20);
			int packed = site.packed();
			helper.assertTrue(site.stage() == MoveSite.Stage.DISMANTLE && packed >= 20, "it didn't get part way down: " + site.stage() + ", " + packed);
			helper.assertTrue(level.getBlockState(helper.absolutePos(OWN_CRATE)).is(ModBlocks.MOVERS_CRATE), "his crate went before the player broke it");
			level.destroyBlock(helper.absolutePos(OWN_CRATE), true);
			for (int i = 0; i < 50; i++) {
				helper.assertFalse(MoverWork.work(level, s.mover(), site), "he took a block down with his crate broken");
			}
			helper.assertTrue(site.packed() == packed, "with no crate: " + site.packed() + " packed, was " + packed);
			helper.assertTrue(MoverWork.NO_CRATE.equals(site.waiting()), "he doesn't say why he waits: " + site.waiting());
			var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(s.mover(), level.getGameTime());
			String line = Component.translatable(MoverWork.NO_CRATE).getString();
			helper.assertTrue(line.equals("Waiting for his Mover's Crate: nowhere to pack the building"), "the line in en_us: " + line);
			helper.assertTrue(status != null && status.line().getString().equals(line), "the Mover's line: " + (status == null ? "none" : status.line().getString()));
			List<ItemEntity> loose = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(40));
			helper.assertTrue(loose.size() == 1 && loose.get(0).getItem().is(ModBlocks.MOVERS_CRATE.asItem()) && loose.get(0).getItem().getCount() == 1,
				"what lies there after the crate broke: " + loose.stream().map(e -> e.getItem().toString()).toList());
			loose.get(0).discard(); // the player picks it up and puts it back where it stood
			helper.setBlock(OWN_CRATE, ModBlocks.MOVERS_CRATE);
			site = work(level, s, x -> false);
			assertDoneCrateKept(helper, level, s, site, "crate back");
			String wrong = wrongAtNewSpot(level, s, Rotation.CLOCKWISE_90, null);
			helper.assertTrue(wrong == null, "the house at its new spot: " + wrong);
			helper.assertTrue(MoverGameTests.loose(level, helper) == 0, "something was dropped");
			helper.succeed();
		});
	}

	/**
	 * His crate broken while the house goes up at the new spot: the move never ends with the crate counted in as put up
	 * somewhere else, nothing is doubled, and with the crate back where it stood the move ends whole.
	 */
	//$ gametest_batch AREA '"qaB1111CrateBrokenUp"'
	@GameTest(template = AREA, batch = "qaB1111CrateBrokenUp")
	public void hisCrateBrokenWhileItGoesUpEndsWholeOnceItIsBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = crateInside(helper, "qacratebrokenup", null, Rotation.CLOCKWISE_90);
		helper.runAfterDelay(2, () -> {
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE);
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE, "it didn't get to putting it up: " + site.stage());
			level.destroyBlock(helper.absolutePos(OWN_CRATE), true);
			for (int i = 0; i < 300 && site.stage() != MoveSite.Stage.DONE; i++) {
				MoverWork.work(level, s.mover(), site);
			}
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE || MoverWork.NO_CRATE.equals(site.waiting()),
				"with his crate broken he neither carries on nor says why he waits: " + site.stage() + ", waiting " + site.waiting());
			helper.assertTrue(crates(helper) == 0, "a crate stands though the player broke his: " + crates(helper));
			List<ItemEntity> loose = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(40));
			helper.assertTrue(loose.size() == 1 && loose.get(0).getItem().is(ModBlocks.MOVERS_CRATE.asItem()),
				"what lies there: " + loose.stream().map(e -> e.getItem().toString()).toList());
			loose.get(0).discard();
			helper.setBlock(OWN_CRATE, ModBlocks.MOVERS_CRATE);
			site = site.stage() == MoveSite.Stage.DONE ? site : work(level, s, x -> false);
			assertDoneCrateKept(helper, level, s, site, "crate back while it went up");
			String wrong = wrongAtNewSpot(level, s, Rotation.CLOCKWISE_90, null);
			helper.assertTrue(wrong == null, "the house at its new spot: " + wrong);
			helper.assertTrue(MoverGameTests.loose(level, helper) == 0, "something was dropped");
			helper.assertTrue(count(helper, Items.BREAD) == 12 && count(helper, Items.EMERALD) == 64, "the chest's items doubled or lost");
			helper.succeed();
		});
	}

	// ---- the Move page refuses a spot over a Mover's Crate (B1120 "refused when planned", B1129) ----------------------

	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos HALL = new BlockPos(15, 2, 11);
	/** The finished Test Hut: x 3-7, y 2-5, z 3-7. One map cell east is x 7-11, two cells x 11-15. */
	private static final BlockPos HUT_ORIGIN = new BlockPos(3, 2, 3);

	private record Village(ServerLevel level, BlockPos hall, BlockPos hut, ServerPlayer player, ChoiceMenu menu) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Predicate<Villager> movers = Movers.isMover;
		boolean enabled = MoveRules.ENABLED;
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement hut = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Movers.isMover = movers;
			MoveRules.ENABLED = enabled;
			sites.forgetFinished(hut);
			PendingMoves.get(level).remove(hut.origin());
			MovePage.stop(player);
		});
		MoveRules.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		sites.recordFinished(HUT, hut, UUID.randomUUID());
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 15));
		v.setNoAi(true);
		Movers.isMover = villager -> villager.getUUID().equals(v.getUUID());
		return new Village(level, helper.absolutePos(HALL), origin, player, null);
	}

	/** The Hall's Move page opened and the hut picked; the map's cell {@code cell} pressed. */
	private static Village pickAt(GameTestHelper helper, Village v, int cell) {
		MovePage.stop(v.player()); // a fresh pick each time
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		menu.press(HallPages.slot(MovePage.PAGE), v.player());
		List<BuildSiteManager.Finished> listed = BuildSiteManager.get(v.level()).finishedInVillage(v.level(), v.hall());
		int slot = -1;
		for (int i = 0; i < listed.size(); i++) {
			if (listed.get(i).placement().origin().equals(v.hut())) {
				slot = VillageHallScreen.FIRST_ROW + i;
			}
		}
		helper.assertTrue(slot >= 0, "the hut isn't on the Move page");
		menu.press(slot, v.player());
		helper.assertTrue(MovePage.picking(v.player()).map(p -> p.from().equals(v.hut())).orElse(false), "the picker didn't open on the hut");
		menu.press(cell, v.player());
		return new Village(v.level(), v.hall(), v.hut(), v.player(), menu);
	}

	private static MoveFit.Fit fit(Village v) {
		return MovePage.fit(v.player()).orElseThrow();
	}

	private static void assertRefusedFor(GameTestHelper helper, Village v, BlockPos crateRel, String when) {
		MoveFit.Fit fit = fit(v);
		BlockPos abs = helper.absolutePos(crateRel);
		helper.assertTrue(fit.reason() == MoveFit.Reason.CRATE, when + ": not refused for the crate: " + fit.reason() + " " + fit.box());
		String line = fit.line().getString();
		String expected = "Refused: a Mover's Crate stands there (at " + abs.getX() + ", " + abs.getY() + ", " + abs.getZ() + "); it doesn't move with buildings";
		helper.assertTrue(line.equals(expected), when + ": the page says \"" + line + "\", not \"" + expected + "\"");
		v.menu().press(MovePage.CONFIRM, v.player());
		helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isEmpty(), when + ": Confirm saved a move over a Mover's Crate");
	}

	/**
	 * A Mover's Crate outside the hut, on the last column of the spot one map cell east: refused, with the crate's
	 * place in words, and Confirm saves nothing. One block further east (just off the spot) the same spot fits.
	 */
	//$ gametest_ticks_batch SMALL '100' '"qaB1111CrateFitEdge"'
	@GameTest(template = SMALL, timeoutTicks = 100, batch = "qaB1111CrateFitEdge")
	public void aSpotWithACrateOnItsEdgeIsRefusedAndOneBlockOffFits(GameTestHelper helper) {
		Village v0 = village(helper);
		BlockPos inside = new BlockPos(11, 2, 5);
		BlockPos outside = new BlockPos(12, 2, 5);
		helper.setBlock(inside, ModBlocks.MOVERS_CRATE);
		helper.runAfterDelay(2, () -> {
			Village v = pickAt(helper, v0, MovePage.CENTRE + 1);
			helper.assertTrue(fit(v).box().maxX() == helper.absolutePos(inside).getX(), "setup: the spot's east edge is x " + fit(v).box().maxX()
				+ ", not the crate's " + helper.absolutePos(inside).getX());
			assertRefusedFor(helper, v, inside, "crate on the spot's edge");
			helper.setBlock(inside, Blocks.AIR);
			helper.setBlock(outside, ModBlocks.MOVERS_CRATE);
			Village again = pickAt(helper, v0, MovePage.CENTRE + 1);
			helper.assertTrue(fit(again).fits(), "with the crate one block off the spot: " + fit(again).reason() + " at " + fit(again).at());
			helper.assertTrue(level(helper).getBlockState(helper.absolutePos(outside)).is(ModBlocks.MOVERS_CRATE), "the crate went");
			helper.succeed();
		});
	}

	private static ServerLevel level(GameTestHelper helper) {
		return helper.getLevel();
	}

	/**
	 * B1120 "the move refused when planned": the Mover's Crate is in the hut's own east wall. A spot overlapping that wall
	 * (one cell east) is refused for the crate; a spot clear of it (two cells east) fits, and the hut is untouched.
	 */
	//$ gametest_ticks_batch SMALL '100' '"qaB1111CrateInWallFit"'
	@GameTest(template = SMALL, timeoutTicks = 100, batch = "qaB1111CrateInWallFit")
	public void aCrateInTheHutsWallRefusesASpotOverItButNotOneClearOfIt(GameTestHelper helper) {
		Village v0 = village(helper);
		BlockPos wall = new BlockPos(7, 3, 5);
		helper.setBlock(wall, ModBlocks.MOVERS_CRATE);
		helper.runAfterDelay(2, () -> {
			Village v = pickAt(helper, v0, MovePage.CENTRE + 1);
			assertRefusedFor(helper, v, wall, "crate in the wall the spot overlaps");
			Village east = pickAt(helper, v0, MovePage.CENTRE + 2);
			helper.assertTrue(fit(east).fits(), "two cells east, clear of the crate: " + fit(east).reason() + " at " + fit(east).at());
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(wall)).is(ModBlocks.MOVERS_CRATE), "the crate went");
			helper.succeed();
		});
	}

	// ---- B1154: the 128-block line from both sides ----------------------------------------------------------------

	/**
	 * B1154's line: one resident 128 blocks east of the house and one 130 blocks east when the move starts (both still in
	 * the house's beds). Whichever side of the scan's edge each is on, both get their moved bed when they come back.
	 */
	//$ gametest_batch AREA '"qaB1111Away128"'
	@GameTest(template = AREA, batch = "qaB1111Away128")
	public void residentsJustInsideAndOutside128BlocksBothGetTheirMovedBeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = QaMoverResidents1110cGameTests.setup(helper, "qa_away128", true);
		helper.runAfterDelay(2, () -> {
			QaMoverResidents1110cGameTests.confirm(helper, s);
			int eastEdge = helper.absolutePos(QaMoverResidents1110cGameTests.MAX).getX();
			BlockPos home = helper.absolutePos(new BlockPos(10, 2, 3));
			List<Villager> away = new ArrayList<>();
			List<ChunkPos> forced = new ArrayList<>();
			try {
				int[] off = {128, 130};
				BlockPos[] beds = {QaMoverResidents1110cGameTests.BED_A, QaMoverResidents1110cGameTests.BED_B};
				for (int i = 0; i < 2; i++) {
					BlockPos at = new BlockPos(eastEdge + off[i], home.getY(), home.getZ());
					ChunkPos chunk = new ChunkPos(at);
					level.setChunkForced(chunk.x, chunk.z, true);
					forced.add(chunk);
					Villager v = EntityType.VILLAGER.spawn(level, at, MobSpawnType.COMMAND);
					helper.assertTrue(v != null, "setup: couldn't spawn the villager " + off[i] + " blocks off");
					away.add(v);
					v.setNoAi(true);
					v.moveTo(at.getX(), at.getY(), at.getZ() + 0.5); // block x itself: exactly off[i] blocks past the east wall
					helper.assertTrue(HallBeds.moveIn(level, v, helper.absolutePos(beds[i])), "setup: no bed for the villager " + off[i] + " blocks off");
				}
				MoveSite site = QaMoverResidents1110cGameTests.start(level, s);
				QaMoverResidents1110cGameTests.work(level, s, site, x -> false);
				helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
				for (int i = 0; i < 2; i++) {
					Villager v = away.get(i);
					v.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
					v.tickCount = 13;
					MoveResidents.tick(v);
					BlockPos bed = QaMoverResidents1110cGameTests.moved(s, beds[i]);
					helper.assertTrue(QaMoverResidents1110cGameTests.remembers(level, v, MemoryModuleType.HOME, bed),
						"the villager " + off[i] + " blocks off has her bed at " + QaMoverResidents1110cGameTests.memory(v, MemoryModuleType.HOME)
							+ ", not the moved bed " + bed.toShortString());
				}
			} finally {
				away.forEach(Villager::discard);
				forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
			}
			helper.succeed();
		});
	}
}
