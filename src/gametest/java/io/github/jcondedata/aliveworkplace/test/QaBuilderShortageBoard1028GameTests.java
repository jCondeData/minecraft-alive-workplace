package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-28) for B636, written from the spec: the moment a builder's site is short of a material, the Storehouse's
 * board lists it (so the Porter and the crafters work while he builds), for exactly the real shortfall (what his bag and
 * his chests hold isn't asked again), still after a save and load, and nothing stale stays on the board once the build
 * is finished, cancelled or its bench broken. A material he has enough of is never listed.
 * <p>
 * The build: a 5×3 cobblestone floor with a row of 5 stone bricks on its front edge. The builder lays the floor first
 * (bottom-up), so he is busy on cobblestone while the bricks are short.
 */
public class QaBuilderShortageBoard1028GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos SITE = new BlockPos(6, 2, 7);
	private static final BlockPos SOURCE = new BlockPos(9, 2, 12);
	private static final BlockPos STOREHOUSE = new BlockPos(14, 2, 2);
	private static final int WIDTH = 5;
	private static final int DEPTH = 3;
	private static final int BRICKS = WIDTH;
	private static final int COBBLE = WIDTH * DEPTH;

	private record Setup(ServerLevel level, Villager[] builder, BuildSite[] site, ServerPlayer player) {
	}

	/**
	 * The build site with {@code cobble} cobblestone and {@code chestBricks} stone bricks in the builder's chest and
	 * {@code bagBricks} in his bag; a Storehouse in the village.
	 */
	private static Setup start(GameTestHelper helper, String name, int cobble, int chestBricks, int bagBricks, int delay, boolean free) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		var rules = level.getGameRules();
		int oldDelay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		boolean oldFree = rules.getRule(ModGameRules.FREE_MATERIALS).get();
		rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.BUILD_DELAY).set(oldDelay, level.getServer());
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(ModGameRules.FREE_MATERIALS).set(oldFree, level.getServer());
		});

		// The blueprint, captured from the world: the floor, and the bricks on its front edge (z = 0, one up).
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				helper.setBlock(SOURCE.offset(x, 0, z), Blocks.COBBLESTONE);
			}
			helper.setBlock(SOURCE.offset(x, 1, 0), Blocks.STONE_BRICKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa_short_" + name.toLowerCase() + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SOURCE), new Vec3i(WIDTH, 2, DEPTH), false,
			Blocks.STRUCTURE_VOID);
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				helper.setBlock(SOURCE.offset(x, 0, z), Blocks.AIR);
				helper.setBlock(SOURCE.offset(x, 1, z), Blocks.AIR);
			}
		}

		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		if (cobble > 0) {
			chest.setItem(0, new ItemStack(Items.COBBLESTONE, cobble));
		}
		if (chestBricks > 0) {
			chest.setItem(1, new ItemStack(Items.STONE_BRICKS, chestBricks));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		if (bagBricks > 0) {
			ModAttachments.BUILDER_BAG.getOrCreate(builder).add(new ItemStack(Items.STONE_BRICKS, bagBricks));
		}
		BuildSite site = Builders.start(level, builder, null, id,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SITE), Rotation.NONE, Mirror.NONE));
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("setup: the build didn't start");
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		return new Setup(level, new Villager[]{builder}, new BuildSite[]{site}, helper.makeMockServerPlayerInLevel());
	}

	/** How many of {@code item} the Storehouse's board asks for (0: none), as a player opening it sees it. */
	private static int asked(GameTestHelper helper, ServerPlayer player, Item item) {
		ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
		int n = 0;
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(item)) {
				n += board.icon(i).getCount();
			}
		}
		return n;
	}

	/** How many of {@code item} the builder himself asks for (what the Porter and crafters read). */
	private static int requested(ServerLevel level, Villager builder, Item item) {
		int n = 0;
		for (Requests.Request r : Requests.of(level, builder)) {
			if (r.item() == item) {
				n += r.count();
			}
		}
		return n;
	}

	private static int bricksPlaced(GameTestHelper helper) {
		int n = 0;
		for (int x = 0; x < WIDTH; x++) {
			if (helper.getBlockState(SITE.offset(x, 1, 0)).is(Blocks.STONE_BRICKS)) {
				n++;
			}
		}
		return n;
	}

	/** The real shortfall of stone bricks: what is still to be laid, less what his chest and his bag hold. */
	private static int realShortfall(GameTestHelper helper, Villager builder) {
		Container chest = helper.getBlockEntity(CHEST);
		int have = chest.countItem(Items.STONE_BRICKS) + ModAttachments.BUILDER_BAG.getOrCreate(builder).count(Items.STONE_BRICKS);
		return Math.max(0, BRICKS - bricksPlaced(helper) - have);
	}

	private static boolean running(ServerLevel level, BuildSite site) {
		return BuildSiteManager.get(level).get(site.id()) != null && !site.isDone();
	}

	private static void putInChest(GameTestHelper helper, Item item, int count) {
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).isEmpty()) {
				chest.setItem(i, new ItemStack(item, count));
				return;
			}
		}
		throw new GameTestAssertException("the chest is full");
	}

	/**
	 * Short 2 of 5 stone bricks (2 in his chest, 1 in his bag): while he still lays the floor (not waiting), the board asks
	 * for exactly 2 bricks within a second, never for more than the real shortfall, and never for cobblestone (he has
	 * all 15). One brick supplied: the board asks for 1. The second: off the board while the build goes on. Finished:
	 * nothing of his is left on the board.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaShort1028Exact"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaShort1028Exact")
	public void theBoardAsksForExactlyTheShortfallWhileHeBuilds(GameTestHelper helper) {
		Setup s = start(helper, "exact", COBBLE, 2, 1, 4, false);
		ServerLevel level = s.level();
		Villager builder = s.builder()[0];
		BuildSite site = s.site()[0];
		// listed (2), first brick given, board shows 1, second given, off the board, build gone, clean for 60 ticks after
		long[] at = {-1, -1, -1, -1, -1, -1};
		long[] graceUntil = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			long t = helper.getTick();
			int cobble = asked(helper, s.player(), Items.COBBLESTONE);
			helper.assertTrue(cobble == 0, "the board asks for " + cobble + " cobblestone, the builder has all " + COBBLE);
			int bricks = asked(helper, s.player(), Items.STONE_BRICKS);
			int real = realShortfall(helper, builder);
			if (t > graceUntil[0]) {
				helper.assertTrue(bricks <= real, "tick " + t + ": the board asks for " + bricks + " stone bricks, he is short of only " + real
					+ " (placed " + bricksPlaced(helper) + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(builder).count(Items.STONE_BRICKS)
					+ ", status " + site.status() + ")");
			}
			if (at[0] < 0) {
				if (bricks > 0) {
					helper.assertTrue(site.status() != BuildSite.Status.WAITING_FOR_MATERIALS && bricksPlaced(helper) == 0,
						"first listed only once he stood waiting (status " + site.status() + ", placed " + bricksPlaced(helper) + ")");
					helper.assertTrue(bricks == 2, "the board asks for " + bricks + " stone bricks, he is short of 2");
					helper.assertTrue(requested(level, builder, Items.STONE_BRICKS) == 2, "his own requests ask for "
						+ requested(level, builder, Items.STONE_BRICKS));
					at[0] = t;
					putInChest(helper, Items.STONE_BRICKS, 1);
					at[1] = t;
					graceUntil[0] = t + 110; // a chest filled by hand is seen at the next recheck (100 ticks)
				} else {
					helper.assertTrue(t < 40, "nothing on the board 2 s into the build (status " + site.status() + ", missing " + site.missing() + ")");
				}
			} else if (at[2] < 0) {
				if (bricks == 1 && running(level, site)) {
					at[2] = t;
					putInChest(helper, Items.STONE_BRICKS, 1);
					graceUntil[0] = t + 110;
				}
			} else if (at[3] < 0) {
				at[3] = t;
			} else if (at[4] < 0) {
				if (bricks == 0 && running(level, site)) {
					at[4] = t;
				}
			} else if (at[5] < 0) {
				if (!running(level, site)) {
					at[5] = t;
				}
			} else {
				helper.assertTrue(bricks == 0 && requested(level, builder, Items.STONE_BRICKS) == 0, "the finished build is still on the board: "
					+ bricks);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[0] >= 0, "never listed");
			helper.assertTrue(at[2] >= 0 && at[2] - at[1] <= 120, "after 1 of 2 bricks was supplied the board never showed 1 (given at " + at[1]
				+ ", missing " + site.missing() + ")");
			helper.assertTrue(at[4] >= 0 && at[4] - at[2] <= 120, "both bricks supplied at " + at[2] + " but the board asked for them until "
				+ (at[4] < 0 ? "the end of the build" : at[4]));
			helper.assertTrue(at[5] >= 0, "the build never finished: stage " + site.stage() + " status " + site.status() + " missing " + site.missing());
			helper.assertTrue(helper.getTick() - at[5] >= 60, "watching the board after the finish");
			List<BlockPos> wrong = site.plan(level).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the build wrong");
		});
	}

	/**
	 * Listed while he builds; the world is saved and loaded (the sites and the builder from their saves, his bag with its
	 * brick in it): within 2 s he asks again, for the same real shortfall, not counting his bag twice or forgetting it.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaShort1028Reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaShort1028Reload")
	public void afterASaveAndLoadTheSameShortfallIsListed(GameTestHelper helper) {
		Setup s = start(helper, "reload", COBBLE, 1, 2, 6, false);
		ServerLevel level = s.level();
		long[] at = {-1, -1, -1, -1}; // listed, reloaded, listed again, supplied and off
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			long t = helper.getTick();
			Villager builder = s.builder()[0];
			int bricks = asked(helper, s.player(), Items.STONE_BRICKS);
			if (at[0] < 0) {
				if (bricks > 0) {
					helper.assertTrue(bricks == 2, "the board asks for " + bricks + " stone bricks before the reload, he is short of 2");
					at[0] = t;
				}
			} else if (at[1] < 0) {
				helper.assertTrue(running(level, s.site()[0]) && s.site()[0].status() != BuildSite.Status.WAITING_FOR_MATERIALS,
					"setup: the build should still be on its floor at the reload (status " + s.site()[0].status() + ")");
				s.builder()[0] = reload(helper, level, builder);
				BuildSite loaded = Builders.activeSite(level, s.builder()[0]);
				helper.assertTrue(loaded != null, "no site for the builder after the reload");
				s.site()[0] = loaded;
				at[1] = t;
			} else if (at[2] < 0) {
				int real = realShortfall(helper, builder);
				helper.assertTrue(bricks <= real, "after the reload the board asks for " + bricks + " bricks, he is short of " + real);
				if (bricks == real && bricks > 0 && running(level, s.site()[0])) {
					at[2] = t;
					putInChest(helper, Items.STONE_BRICKS, 2);
				} else {
					helper.assertTrue(t - at[1] <= 40, "2 s after the reload the board asks for " + bricks + " bricks, he is short of " + real
						+ " (status " + s.site()[0].status() + ", missing " + s.site()[0].missing() + ")");
				}
			} else if (at[3] < 0 && bricks == 0 && running(level, s.site()[0])) {
				at[3] = t;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[2] >= 0, "never listed again after the reload");
			helper.assertTrue(at[3] >= 0 && at[3] - at[2] <= 120, "supplied at " + at[2] + " but still asked for (" + s.site()[0].missing() + ")");
			helper.assertTrue(!running(level, s.site()[0]), "still building: status " + s.site()[0].status() + " missing " + s.site()[0].missing());
			List<BlockPos> wrong = s.site()[0].plan(level).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the build wrong");
		});
	}

	/** Listed while he builds; the player cancels the build: off the board at once, and it doesn't come back. */
	//$ gametest_ticks_batch AREA '600' '"qaShort1028Cancel"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaShort1028Cancel")
	public void aCancelledBuildLeavesNothingOnTheBoard(GameTestHelper helper) {
		Setup s = start(helper, "cancel", COBBLE, 0, 0, 6, false);
		ServerLevel level = s.level();
		Villager builder = s.builder()[0];
		long[] at = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			int bricks = asked(helper, s.player(), Items.STONE_BRICKS);
			if (at[0] < 0) {
				if (bricks > 0) {
					helper.assertTrue(bricks == BRICKS, "the board asks for " + bricks + " bricks, he has none of " + BRICKS);
					Builders.cancel(level, s.site()[0]);
					at[0] = helper.getTick();
					helper.assertTrue(asked(helper, s.player(), Items.STONE_BRICKS) == 0, "the cancelled build is still on the board");
				}
			} else {
				helper.assertTrue(bricks == 0 && requested(level, builder, Items.STONE_BRICKS) == 0,
					"tick " + helper.getTick() + ": a cancelled build's bricks are back on the board (" + bricks + ")");
			}
		});
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(at[0] >= 0, "never listed");
			helper.assertTrue(Builders.activeSite(level, builder) == null, "the builder still has a site after the cancel");
			helper.succeed();
		});
	}

	/** Listed while he builds; his bench is broken: his bricks leave the board, and nobody is asked for them again. */
	//$ gametest_ticks_batch AREA '600' '"qaShort1028Bench"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaShort1028Bench")
	public void aBrokenBenchLeavesNothingOnTheBoard(GameTestHelper helper) {
		Setup s = start(helper, "bench", COBBLE, 0, 0, 6, false);
		long[] at = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			int bricks = asked(helper, s.player(), Items.STONE_BRICKS);
			if (at[0] < 0) {
				if (bricks > 0) {
					helper.destroyBlock(BENCH);
					at[0] = helper.getTick();
				}
			} else if (helper.getTick() - at[0] > 20) {
				helper.assertTrue(bricks == 0, "tick " + helper.getTick() + ": the board still asks for " + bricks
					+ " bricks for a builder whose bench was broken at tick " + at[0]);
			}
		});
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(at[0] >= 0, "never listed");
			helper.succeed();
		});
	}

	/** Everything in his chest: nothing of his on the board at any time, start to finish. */
	//$ gametest_ticks_batch AREA '2400' '"qaShort1028Enough"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaShort1028Enough")
	public void aBuilderWithEverythingAsksForNothing(GameTestHelper helper) {
		Setup s = start(helper, "enough", COBBLE, BRICKS, 0, 2, false);
		ServerLevel level = s.level();
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			Villager builder = s.builder()[0];
			List<Requests.Request> asked = Requests.of(level, builder);
			helper.assertTrue(asked.isEmpty(), "tick " + helper.getTick() + ": a builder with everything asks for " + asked.size() + " thing(s), e.g. "
				+ (asked.isEmpty() ? "" : asked.get(0).count() + " " + asked.get(0).icon().getHoverName().getString()));
			helper.assertTrue(asked(helper, s.player(), Items.STONE_BRICKS) == 0 && asked(helper, s.player(), Items.COBBLESTONE) == 0,
				"the board lists a material he has enough of");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!running(level, s.site()[0]), "still building: status " + s.site()[0].status() + " missing " + s.site()[0].missing());
			List<BlockPos> wrong = s.site()[0].plan(level).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the build wrong");
		});
	}

	/** Free materials on (the switch): a builder with nothing asks for nothing while he builds. */
	//$ gametest_ticks_batch AREA '2400' '"qaShort1028Free"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaShort1028Free")
	public void withFreeMaterialsNothingIsAsked(GameTestHelper helper) {
		Setup s = start(helper, "free", 0, 0, 0, 2, true);
		ServerLevel level = s.level();
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			int bricks = asked(helper, s.player(), Items.STONE_BRICKS);
			int cobble = asked(helper, s.player(), Items.COBBLESTONE);
			helper.assertTrue(bricks == 0 && cobble == 0, "with free materials the board asks for " + bricks + " bricks and " + cobble + " cobblestone");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!running(level, s.site()[0]), "still building: status " + s.site()[0].status());
			List<BlockPos> wrong = s.site()[0].plan(level).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the build wrong");
		});
	}

	/**
	 * Short all 5 stone bricks, 5 stone in his chest, a Mason with a stonecutter in the village: the Mason cuts the bricks
	 * while the builder still lays his slow floor, before he ever stands waiting for them, and the build finishes.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaShort1028Mason"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaShort1028Mason")
	public void theMasonCutsTheBricksWhileTheBuilderIsStillOnTheFloor(GameTestHelper helper) {
		Setup s = start(helper, "mason", COBBLE, 0, 0, 30, false);
		ServerLevel level = s.level();
		putInChest(helper, Items.STONE, BRICKS);
		BlockPos cutter = new BlockPos(14, 2, 6);
		helper.setBlock(cutter, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 5));
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, mason, helper.absolutePos(cutter),
			net.minecraft.world.entity.ai.village.poi.PoiTypes.MASON, VillagerProfession.MASON);
		long[] at = {-1, -1}; // the builder first stood waiting; the Mason's first cut
		int[] floorAtCut = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = s.site()[0];
			if (at[0] < 0 && site.status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
				at[0] = helper.getTick();
			}
			if (at[1] < 0 && ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) > 0) {
				at[1] = helper.getTick();
				int floor = 0;
				for (int x = 0; x < WIDTH; x++) {
					for (int z = 0; z < DEPTH; z++) {
						if (helper.getBlockState(SITE.offset(x, 0, z)).is(Blocks.COBBLESTONE)) {
							floor++;
						}
					}
				}
				floorAtCut[0] = floor;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[1] >= 0, "the Mason never cut a brick (crafted " + ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) + ")");
			helper.assertTrue(at[0] < 0 || at[1] < at[0], "the Mason first cut at tick " + at[1] + ", after the builder stood waiting at tick " + at[0]
				+ " (floor " + floorAtCut[0] + "/" + COBBLE + " laid)");
			helper.assertTrue(!running(level, s.site()[0]), "still building: status " + s.site()[0].status() + " missing " + s.site()[0].missing());
			List<BlockPos> wrong = s.site()[0].plan(level).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the build wrong");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == BRICKS, "the Mason cut " + ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0)
				+ " bricks for a build short of " + BRICKS);
		});
	}

	/** As a save and a load of the world would: every build site from its save, and the builder from his. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}
}
