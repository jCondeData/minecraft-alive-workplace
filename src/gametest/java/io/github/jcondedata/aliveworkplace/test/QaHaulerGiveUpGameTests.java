package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-e-1025-1314) on B450's fix for Hauler Golems (29.15), from its text and the fix's promise: "after a while it
 * tries the next reachable Storehouse ... and keeps hauling"; a given-up Storehouse "is left alone for a while ... (the
 * way may have been opened)"; "nothing of that is saved, so after a reload the golem simply tries the nearest again"
 * while the load itself is saved on the golem. GolemHaulerRouteGameTests covers the first trip; these cover what comes
 * after it: the wait, the way opened again (for a Storehouse and, B456, a Drop Box), and a save and reload in the middle.
 *
 * <p>Layout as GolemHaulerRouteGameTests: a Storehouse walled in and roofed over near the golem's start, an open one
 * farther off. Before each new load the golem is put back at its start, so the walled-in one is the nearest again.
 */
public class QaHaulerGiveUpGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos NEAR = new BlockPos(3, 2, 4);
	private static final BlockPos NEAR_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos FAR = new BlockPos(24, 2, 24);
	private static final BlockPos FAR_CHEST = new BlockPos(25, 2, 24);
	private static final BlockPos START = new BlockPos(12, 2, 12);

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Walls in and roofs over x 1..8, z 1..7 (inside: y 2..3), with the near Storehouse and its chest inside. */
	private static void layout(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		for (int x = 1; x <= 8; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = 1; z <= 7; z++) {
					boolean shell = x == 1 || x == 8 || z == 1 || z == 7 || y == 4;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE_BRICKS : Blocks.AIR);
				}
			}
		}
		helper.setBlock(NEAR, ModBlocks.STOREHOUSE);
		helper.setBlock(NEAR_CHEST, Blocks.CHEST);
		helper.setBlock(FAR, ModBlocks.STOREHOUSE);
		helper.setBlock(FAR_CHEST, Blocks.CHEST);
	}

	/** Takes the walls and roof away (the way to the near Storehouse is open again). */
	private static void openWalls(GameTestHelper helper) {
		for (int x = 1; x <= 8; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = 1; z <= 7; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!p.equals(NEAR) && !p.equals(NEAR_CHEST)) {
						helper.setBlock(p, Blocks.AIR);
					}
				}
			}
		}
	}

	private static IronGolem hauler(GameTestHelper helper) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(START.below()), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		return golem;
	}

	/** Puts {@code golem} back at its start, standing, with {@code count} of {@code item} to take home. */
	private static void loadAtStart(GameTestHelper helper, IronGolem golem, Item item, int count) {
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(START));
		golem.getNavigation().stop();
		golem.teleportTo(at.x, at.y, at.z);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(item, count));
	}

	private static int in(GameTestHelper helper, BlockPos chest, Item item) {
		Container c = helper.getBlockEntity(chest);
		return c.countItem(item);
	}

	/**
	 * After giving up the walled-in Storehouse and taking a load to the open one, the walls come down: the next load,
	 * at once, still goes to the open one (the given-up Storehouse is left alone for a while), and a load given once
	 * that while is over goes to the near one, now that it can be reached.
	 */
	//$ gametest_ticks_batch AREA '4400' '"qaHaulerWaySpent"'
	@GameTest(template = AREA, timeoutTicks = 4400, batch = "qaHaulerWaySpent")
	public void aGivenUpStorehouseIsLeftAloneAWhileThenUsedOnceTheWayIsOpen(GameTestHelper helper) {
		layout(helper);
		IronGolem golem = hauler(helper);
		Leftovers.after(helper, golem::discard);
		ServerLevel level = helper.getLevel();
		loadAtStart(helper, golem, Items.COBBLESTONE, 64);
		helper.runAfterDelay(1, () -> helper.assertTrue(helper.absolutePos(NEAR).equals(HaulerGolems.storehouse(level, golem).orElse(null)),
			"the walled-in Storehouse isn't the hauler's nearest: the test proves nothing"));
		AtomicInteger phase = new AtomicInteger();
		AtomicLong firstHome = new AtomicLong();
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			switch (phase.get()) {
				case 0 -> {
					if (in(helper, FAR_CHEST, Items.COBBLESTONE) == 64) {
						firstHome.set(now);
						openWalls(helper);
						loadAtStart(helper, golem, Items.DIRT, 64);
						phase.set(1);
					}
				}
				case 1 -> {
					helper.assertTrue(in(helper, NEAR_CHEST, Items.DIRT) == 0, "the load right after the give-up went to the Storehouse given up on "
						+ (now - firstHome.get()) + " ticks after the first load was home: it should be left alone for a while");
					if (in(helper, FAR_CHEST, Items.DIRT) == 64) {
						phase.set(2);
					}
				}
				case 2 -> {
					// The give-up came before the first load was home, so by then plus SKIP_FOR it's surely over.
					if (now >= firstHome.get() + HaulerGolems.SKIP_FOR + 20) {
						loadAtStart(helper, golem, Items.SAND, 64);
						phase.set(3);
					}
				}
				default -> { }
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase.get() == 3, "phase " + phase.get() + ": far chest cobblestone " + in(helper, FAR_CHEST, Items.COBBLESTONE)
				+ ", dirt " + in(helper, FAR_CHEST, Items.DIRT) + "; the golem carries " + carried(golem) + " at " + golem.position());
			helper.assertTrue(in(helper, FAR_CHEST, Items.SAND) == 0, "once the wait was over and the way open, the load still went to the far Storehouse");
			helper.assertTrue(in(helper, NEAR_CHEST, Items.SAND) == 64, "the near Storehouse, open again and its wait over, has "
				+ in(helper, NEAR_CHEST, Items.SAND) + " of 64 sand; the golem carries " + carried(golem) + " at " + golem.position()
				+ ", heading for " + HaulerGolems.storehouse(level, golem));
			helper.assertTrue(carried(golem) == 0, "the golem still carries " + carried(golem));
		});
	}

	/**
	 * B456 the same way for a pickup: an empty hauler gives up the Drop Box walled in nearest the Storehouse and fetches
	 * from the open one; with the walls down at once it still leaves that box alone (the next load in the open box comes
	 * first), and once the wait is over it fetches from it.
	 */
	//$ gametest_ticks_batch AREA '4400' '"qaHaulerPickupWaySpent"'
	@GameTest(template = AREA, timeoutTicks = 4400, batch = "qaHaulerPickupWaySpent")
	public void aGivenUpDropBoxIsLeftAloneAWhileThenFetchedFromOnceTheWayIsOpen(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		BlockPos storehouse = new BlockPos(24, 2, 24);
		BlockPos storeChest = new BlockPos(25, 2, 24);
		BlockPos walledBox = new BlockPos(17, 2, 17);
		BlockPos openBox = new BlockPos(4, 2, 4);
		for (int x = 14; x <= 20; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = 14; z <= 20; z++) {
					boolean shell = x == 14 || x == 20 || z == 14 || z == 20 || y == 4;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE_BRICKS : Blocks.AIR);
				}
			}
		}
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		helper.setBlock(walledBox, ModBlocks.DROP_BOX);
		helper.setBlock(openBox, ModBlocks.DROP_BOX);
		Container walled = helper.getBlockEntity(walledBox);
		Container open = helper.getBlockEntity(openBox);
		for (int i = 0; i < 3; i++) {
			walled.setItem(i, new net.minecraft.world.item.ItemStack(Items.OAK_LOG, 64));
			open.setItem(i, new net.minecraft.world.item.ItemStack(Items.COBBLESTONE, 64));
		}
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(10, 1, 10)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		ServerLevel level = helper.getLevel();
		AtomicInteger phase = new AtomicInteger();
		AtomicLong firstHome = new AtomicLong();
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			switch (phase.get()) {
				case 0 -> {
					helper.assertTrue(walled.countItem(Items.OAK_LOG) == 3 * 64, "the golem got into the walled-in Drop Box: the test proves nothing");
					if (in(helper, storeChest, Items.COBBLESTONE) == 3 * 64 && carried(golem) == 0) {
						firstHome.set(now);
						for (int x = 14; x <= 20; x++) {
							for (int y = 2; y <= 4; y++) {
								for (int z = 14; z <= 20; z++) {
									BlockPos p = new BlockPos(x, y, z);
									if (!p.equals(walledBox)) {
										helper.setBlock(p, Blocks.AIR);
									}
								}
							}
						}
						for (int i = 0; i < 3; i++) {
							open.setItem(i, new net.minecraft.world.item.ItemStack(Items.COBBLESTONE, 64));
						}
						phase.set(1);
					}
				}
				case 1 -> {
					helper.assertTrue(walled.countItem(Items.OAK_LOG) == 3 * 64, "the golem fetched from the Drop Box it gave up "
						+ (now - firstHome.get()) + " ticks after the first load was home: it should be left alone for a while");
					if (in(helper, storeChest, Items.COBBLESTONE) == 6 * 64 && carried(golem) == 0) {
						phase.set(2);
					}
				}
				default -> { }
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase.get() == 2, "phase " + phase.get() + ": the store has " + in(helper, storeChest, Items.COBBLESTONE)
				+ " cobblestone, the golem carries " + carried(golem) + " at " + golem.position());
			helper.assertTrue(level.getGameTime() >= firstHome.get() + HaulerGolems.SKIP_FOR, "the given-up Drop Box was fetched from "
				+ (level.getGameTime() - firstHome.get()) + " ticks after the first load was home, before its wait was over");
			helper.assertTrue(in(helper, storeChest, Items.OAK_LOG) == 3 * 64, "once the wait was over the store has " + in(helper, storeChest, Items.OAK_LOG)
				+ " of " + 3 * 64 + " logs from the Drop Box that was walled in; the box keeps " + walled.countItem(Items.OAK_LOG)
				+ ", the golem carries " + carried(golem) + " at " + golem.position());
		});
	}

	/**
	 * A hauler saved and loaded again right after giving up the walled-in Storehouse keeps its whole load (saved on the
	 * golem), tries the nearest again (the give-up isn't saved), gives it up again and gets every item to the open one.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaHaulerGiveUpReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaHaulerGiveUpReload")
	public void aHaulerReloadedAfterGivingUpKeepsItsLoadAndGetsItHome(GameTestHelper helper) {
		layout(helper);
		ServerLevel level = helper.getLevel();
		AtomicReference<IronGolem> golem = new AtomicReference<>(hauler(helper));
		Leftovers.after(helper, () -> golem.get().discard());
		loadAtStart(helper, golem.get(), Items.COBBLESTONE, 3 * 64);
		AtomicInteger phase = new AtomicInteger();
		helper.onEachTick(() -> {
			if (phase.get() != 0) {
				return;
			}
			IronGolem old = golem.get();
			if (!helper.absolutePos(FAR).equals(HaulerGolems.storehouse(level, old).orElse(null))) {
				return;
			}
			// It has just given the near one up: save it and load it back, as a world save and load would.
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(old.save(tag), "the golem wasn't saved");
			old.discard();
			Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(back instanceof IronGolem, "the golem didn't load as an iron golem: " + back);
			IronGolem loaded = (IronGolem) back;
			Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(START));
			loaded.moveTo(at.x, at.y, at.z);
			level.addFreshEntity(loaded);
			golem.set(loaded);
			helper.assertTrue(carried(loaded) == 3 * 64, "the reloaded golem carries " + carried(loaded) + " of " + 3 * 64);
			helper.assertTrue(helper.absolutePos(NEAR).equals(HaulerGolems.storehouse(level, loaded).orElse(null)),
				"after a reload the golem should try the nearest Storehouse again; it heads for " + HaulerGolems.storehouse(level, loaded));
			phase.set(1);
		});
		helper.succeedWhen(() -> {
			IronGolem g = golem.get();
			helper.assertTrue(phase.get() == 1, "the golem never gave the walled-in Storehouse up before the reload; it carries " + carried(g)
				+ " at " + g.position());
			helper.assertTrue(in(helper, NEAR_CHEST, Items.COBBLESTONE) == 0, "cobblestone got into the walled-in Storehouse's chest");
			helper.assertTrue(in(helper, FAR_CHEST, Items.COBBLESTONE) == 3 * 64, "the open Storehouse's chest has " + in(helper, FAR_CHEST, Items.COBBLESTONE)
				+ " of " + 3 * 64 + "; the reloaded golem carries " + carried(g) + " at " + g.position());
			helper.assertTrue(carried(g) == 0, "the reloaded golem still carries " + carried(g));
		});
	}
}
