package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B773/B778 (2026-11-01): a chestless builder's planks the Carpenter carries to a Storehouse that fills up on his way.
 * The scene is {@link QaCarpenterStorehouse1101GameTests}'s (a 3-plank row, the Storehouse chest at STORE_CHEST, slot 0
 * the logs), with chests by the Carpenter's own bench at CARPENTER_CHEST: none, full, or with room ({@link Home}).
 * Used by {@link QaCarpenterFullStore1101cBugTests}; its own test reloads the Carpenter while he carries the planks he
 * took home on to the Storehouse.
 */
public class QaCarpenterHomePlanks1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	/** The chests by the Carpenter's bench: none, full of cobblestone, or empty. */
	enum Home { NONE, FULL, ROOM }

	/** The scene, with the Carpenter and the builder's site (the Carpenter swapped in after a reload). */
	static final class Run {
		private final QaCarpenterStorehouse1101GameTests.Run base;
		private final AtomicReference<Villager> carpenter;

		Run(QaCarpenterStorehouse1101GameTests.Run base) {
			this.base = base;
			this.carpenter = new AtomicReference<>(base.carpenter());
		}

		long tick() {
			return base.tick();
		}

		Villager carpenter() {
			return carpenter.get();
		}

		void carpenter(Villager copy) {
			carpenter.set(copy);
		}

		BuildSite site() {
			return base.a();
		}

		QaCarpenterStorehouse1101GameTests.Run base() {
			return base;
		}
	}

	/**
	 * The scene for one chestless builder whose row ({@code length} spruce planks at {@code row}: only ROW_A's 3 are
	 * drawn) is fed from a Storehouse holding {@code logs} logs, with {@code homeChests} chests (0 or 1) by the bench.
	 */
	static Run setUp(GameTestHelper helper, String name, int logs, BlockPos row, int length, int homeChests, Home home) {
		if (!row.equals(QaCarpenterStorehouse1101GameTests.ROW_A) || length != 3 || homeChests > 1) {
			throw new GameTestAssertException("the scene has one home chest and ROW_A's 3 planks only");
		}
		Run run = new Run(QaCarpenterStorehouse1101GameTests.setUp(helper, "c_" + name, logs, false, false, false));
		if (home != Home.NONE && homeChests > 0) {
			helper.setBlock(QaCarpenterStorehouse1101GameTests.CARPENTER_CHEST, Blocks.CHEST);
			if (home == Home.FULL) {
				Container chest = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.CARPENTER_CHEST);
				for (int i = 0; i < chest.getContainerSize(); i++) {
					chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
				}
			}
		}
		return run;
	}

	/** Spruce planks in the Carpenter's own bag. */
	static int carrying(Villager carpenter) {
		return ModAttachments.BUILDER_BAG.getOrCreate(carpenter).count(Items.SPRUCE_PLANKS);
	}

	/** Every tick: no plank on the ground, and the wood (the home chest included) adds up to {@code wood} planks' worth. */
	static void everyTick(GameTestHelper helper, Run run, int wood) {
		int lying = QaCarpenterStorehouse1101GameTests.onGround(helper, Items.SPRUCE_PLANKS);
		if (lying > 0) {
			throw new GameTestAssertException("at tick " + run.tick() + " " + lying + " spruce planks lie on the ground while the chestless builder waits for "
				+ run.site().missing());
		}
		BlockPos home = QaCarpenterStorehouse1101GameTests.CARPENTER_CHEST;
		int total = 4 * QaCarpenterStorehouse1101GameTests.logsAnywhere(helper) + QaCarpenterStorehouse1101GameTests.planksAnywhere(helper)
			+ 4 * QaCarpenterStorehouse1101GameTests.inChest(helper, home, Items.SPRUCE_LOG) + QaCarpenterStorehouse1101GameTests.inChest(helper, home, Items.SPRUCE_PLANKS)
			+ QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A);
		if (total != wood) {
			throw new GameTestAssertException("at tick " + run.tick() + " the spruce wood adds up to " + total + " planks' worth, not " + wood + " (home chest "
				+ QaCarpenterStorehouse1101GameTests.inChest(helper, home, Items.SPRUCE_PLANKS) + " planks, bags "
				+ QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS) + ", store "
				+ QaCarpenterStorehouse1101GameTests.inChest(helper, QaCarpenterStorehouse1101GameTests.STORE_CHEST, Items.SPRUCE_PLANKS) + ", placed "
				+ QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) + ")");
		}
	}

	/**
	 * B778: the planks went home after the long wait at a full Storehouse; a slot frees and the Carpenter sets off with
	 * them again. Saved and reloaded on his way, he still carries them on (no job made twice, none on the ground) and the
	 * builder builds his row.
	 */
	//$ gametest_ticks_batch BIG '7000' '"qaFull1101cCarryReload"'
	@GameTest(template = BIG, timeoutTicks = 7000, batch = "qaFull1101cCarryReload")
	public void reloadedWhileCarryingHomePlanksOnStillFeedsTheBuilder(GameTestHelper helper) {
		Run run = setUp(helper, "carryreload", 1, QaCarpenterStorehouse1101GameTests.ROW_A, 3, 1, Home.ROOM);
		Container store = helper.getBlockEntity(QaCarpenterStorehouse1101GameTests.STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicInteger homeAt = new AtomicInteger(-1);
		AtomicInteger freedAt = new AtomicInteger(-1);
		AtomicInteger reloadedAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			everyTick(helper, run, 4);
			if (filledAt.get() < 0 && carrying(run.carpenter()) > 0) {
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set(tick);
			}
			if (homeAt.get() < 0 && QaCarpenterStorehouse1101GameTests.inChest(helper, QaCarpenterStorehouse1101GameTests.CARPENTER_CHEST, Items.SPRUCE_PLANKS) > 0) {
				homeAt.set(tick);
			}
			if (homeAt.get() >= 0 && freedAt.get() < 0 && tick == homeAt.get() + 200) {
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
				freedAt.set(tick);
			}
			if (freedAt.get() >= 0 && reloadedAt.get() < 0 && carrying(run.carpenter()) > 0) {
				run.carpenter(QaCarpenterStorehouse1101GameTests.reload(helper.getLevel(), run.carpenter()));
				reloadedAt.set(tick);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt.get() >= 0, "the Carpenter never set off again with the planks he took home");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "at tick " + run.tick() + " after a reload on his way the chestless builder still waits for "
				+ run.site().missing() + " (" + run.site().status() + ")");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) == 3, "the row has "
				+ QaCarpenterStorehouse1101GameTests.placed(helper, QaCarpenterStorehouse1101GameTests.ROW_A) + " of its 3 planks");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null, "a job stays saved on the Carpenter: " + ModAttachments.CRAFTER_JOB.get(run.carpenter()));
		});
	}
}
