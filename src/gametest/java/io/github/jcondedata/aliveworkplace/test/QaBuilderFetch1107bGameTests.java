package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-11-07 (b): the Builder fetching its materials from its own chest by the bench, seen by a player. A walled-in
 * builder has to hop to a spot beside its chest (builder-s-bench.md: chests within 8 blocks of the bench are its
 * supplies); while it waits to hop, a player fills that spot, empties the chest, breaks it, or the server restarts once
 * it carries the stone. Every stone is counted before and after: none lost, none made, the one-block build finished.
 * The first test kills the mutant that let BuilderWork's B60 re-pick choose spots half a block beyond a chest's reach.
 */
public class QaBuilderFetch1107bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	/** A builder takes from a chest from up to this far, eye to the chest's centre (BuilderWork.CONTAINER_REACH). */
	private static final double CONTAINER_REACH = 3.0;
	private static final BlockPos SRC = new BlockPos(25, 2, 25);
	/** The builder, walled in a glass cell on a ledge, so it can only hop. */
	private static final BlockPos BUILDER = new BlockPos(3, 8, 3);
	private static final BlockPos BENCH = new BlockPos(2, 8, 3);
	/** Its supply chest, on a stone pillar 8 and 7 blocks from the bench (inside the 8-block supply radius). */
	private static final BlockPos CHEST = new BlockPos(10, 8, 10);
	/** The ledge nearest the builder from which the chest is in reach (eye about 2.29 from it): filled while it waits. */
	private static final BlockPos FIRST = new BlockPos(10, 8, 8);
	/** Nearer the builder than any ledge in reach, but its eye would be about 3.04 from the chest: out of reach. */
	private static final BlockPos TOO_FAR = new BlockPos(8, 8, 8);
	/** The far side of the chest: eye about 2.29 from it. */
	private static final BlockPos FAR_SIDE = new BlockPos(12, 8, 10);
	/** The one stone to build, on its own pillar (in reach of both ledges beside the chest). */
	private static final BlockPos TARGET = new BlockPos(12, 8, 12);
	private static final int STOCK = 4;

	private record Run(ServerLevel level, Villager[] builder, BuildSite site) {
		Villager v() {
			return builder[0];
		}
	}

	private static Run setUp(GameTestHelper helper, int buildDelay) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.STONE);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC);
		helper.setBlock(SRC, Blocks.AIR);

		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(buildDelay, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);

		for (int y = 2; y < CHEST.getY(); y++) {
			helper.setBlock(new BlockPos(CHEST.getX(), y, CHEST.getZ()), Blocks.STONE);
			helper.setBlock(new BlockPos(TARGET.getX(), y, TARGET.getZ()), Blocks.STONE);
		}
		for (BlockPos spot : new BlockPos[] {FIRST, TOO_FAR, FAR_SIDE}) {
			helper.setBlock(spot.below(), Blocks.STONE);
		}
		helper.setBlock(CHEST, Blocks.CHEST);
		chest(helper).setItem(0, new ItemStack(Items.STONE, STOCK));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(BUILDER.offset(dx, -1, dz), Blocks.STONE);
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos p = BUILDER.offset(dx, dy, dz);
					if (!p.equals(BUILDER) && !p.equals(BUILDER.above())) {
						helper.setBlock(p, Blocks.GLASS);
					}
				}
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(TARGET),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, id, placement);
		helper.assertTrue(site != null, "the one-block build didn't start");
		return new Run(level, new Villager[] {villager}, site);
	}

	private static Container chest(GameTestHelper helper) {
		return helper.getBlockEntity(CHEST);
	}

	private static int inChest(GameTestHelper helper) {
		return helper.getBlockState(CHEST).is(Blocks.CHEST) ? chest(helper).countItem(Items.STONE) : 0;
	}

	private static int inBag(Villager v) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).count(Items.STONE);
	}

	private static int onGround(GameTestHelper helper) {
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(30, 30, 30).inflate(2);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.STONE)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static int placed(GameTestHelper helper) {
		return helper.getBlockState(TARGET).is(Blocks.STONE) ? 1 : 0;
	}

	/** Every stone there is: in the chest, in the builder's bag and pockets, on the ground, and the one built. */
	private static int total(GameTestHelper helper, Villager v) {
		return inChest(helper) + inBag(v) + v.getInventory().countItem(Items.STONE) + onGround(helper) + placed(helper);
	}

	private static String rel(GameTestHelper helper, BlockPos abs) {
		return abs.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	private static boolean walledIn(GameTestHelper helper, Villager v) {
		return v.blockPosition().distManhattan(helper.absolutePos(BUILDER)) <= 1;
	}

	/** Records the first tick the builder is inside a block, if ever. */
	private static void watchInside(GameTestHelper helper, Run r, String[] inside) {
		if (inside[0] == null && r.v().isAlive() && !r.level().noCollision(r.v(), r.v().getBoundingBox().deflate(0.1))) {
			inside[0] = "the builder is inside a block at " + rel(helper, r.v().blockPosition()) + " ("
				+ r.level().getBlockState(r.v().blockPosition()).getBlock().getName().getString() + ") at tick " + helper.getTick();
		}
	}

	private static String where(GameTestHelper helper, Run r) {
		return "stage " + r.site().stage() + ", status " + r.site().status() + ", builder at " + rel(helper, r.v().blockPosition())
			+ ", chest " + inChest(helper) + ", bag " + inBag(r.v()) + ", ground " + onGround(helper) + ", built " + placed(helper);
	}

	/**
	 * The builder is on its way to its chest (walled in, waiting to hop to the ledge beside it) when a player fills that
	 * ledge with dirt. Nearest to it is a ledge just out of the chest's reach; in reach is the far side of the chest. It
	 * must take the stone from a spot within 3 blocks of the chest, never hop into a block or onto the ledge out of reach,
	 * and finish the build with exactly one stone out of the chest (4 before: chest + bag = 3 after, nothing on the ground).
	 */
	//$ gametest_ticks_batch HUGE '900' '"qa1107bFetchRepick"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qa1107bFetchRepick")
	public void aBuilderWhoseSpotByTheChestWasFilledTakesTheStoneFromASpotInReach(GameTestHelper helper) {
		Run r = setUp(helper, 2);
		BlockPos first = helper.absolutePos(FIRST);
		BlockPos chestAt = helper.absolutePos(CHEST);
		int[] fillAt = {-1};
		boolean[] filledWhileWalledIn = {false};
		double[] takenFrom = {-1};
		BlockPos[] stoodAt = {null};
		boolean[] onTooFar = {false};
		String[] inside = {null};
		helper.onEachTick(() -> {
			if (fillAt[0] < 0 && r.site().status() == BuildSite.Status.FETCHING) {
				fillAt[0] = (int) helper.getTick() + 5;
			}
			if (fillAt[0] >= 0 && helper.getTick() == fillAt[0]) {
				filledWhileWalledIn[0] = walledIn(helper, r.v()) && inChest(helper) == STOCK;
				r.level().setBlockAndUpdate(first, Blocks.DIRT.defaultBlockState());
			}
			if (takenFrom[0] < 0 && inChest(helper) < STOCK) {
				takenFrom[0] = r.v().getEyePosition().distanceTo(Vec3.atCenterOf(chestAt));
				stoodAt[0] = r.v().blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
			}
			onTooFar[0] |= r.v().blockPosition().equals(helper.absolutePos(TOO_FAR));
			watchInside(helper, r, inside);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inside[0] == null, inside[0] == null ? "" : inside[0]);
			helper.assertTrue(!onTooFar[0], "the builder hopped onto the ledge out of the chest's reach at " + TOO_FAR.toShortString()
				+ " (" + where(helper, r) + ")");
			helper.assertTrue(fillAt[0] >= 0 && helper.getTick() > fillAt[0], "the builder never set off for its chest: " + where(helper, r));
			helper.assertTrue(takenFrom[0] >= 0, "nothing taken from the chest yet: " + where(helper, r));
			helper.assertTrue(filledWhileWalledIn[0], "setup: the builder had left its cell (or emptied the chest) before its ledge was filled");
			helper.assertTrue(takenFrom[0] <= CONTAINER_REACH + 1e-6, "the stone was taken from " + String.format("%.2f", takenFrom[0])
				+ " blocks away (at " + stoodAt[0].toShortString() + "), beyond a chest's reach of " + CONTAINER_REACH);
			helper.assertTrue(placed(helper) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built yet: " + where(helper, r));
			helper.assertTrue(r.site().skipped() == 0, "the builder skipped " + r.site().skipped() + " block(s)");
			int chest = inChest(helper), bag = inBag(r.v()), ground = onGround(helper);
			helper.assertTrue(STOCK - chest - bag == 1 && ground == 0 && total(helper, r.v()) == STOCK,
				"wanted exactly one stone used: chest " + STOCK + " -> " + chest + ", bag " + bag + ", ground " + ground + ", built " + placed(helper));
			LOGGER.info("qa 1107b fetch re-pick: ledge {} filled while walled in; stone taken from {} ({} blocks, reach {}); chest {} -> {}, bag {}, built 1",
				FIRST.toShortString(), stoodAt[0].toShortString(), String.format("%.2f", takenFrom[0]), CONTAINER_REACH, STOCK, chest, bag);
		});
	}

	/**
	 * The builder is on its way to its chest when a player takes every stone out of it. It must not build from nothing:
	 * for as long as the chest is empty the stone isn't placed and the bag holds none, and it waits for materials. A player
	 * then puts 3 stone back: it finishes with exactly one of them used (chest + bag = 2), nothing on the ground.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qa1107bFetchEmptied"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qa1107bFetchEmptied")
	public void aChestEmptiedWhileTheBuilderWalksToItHoldsItUntilRefilledWithNothingMadeOrLost(GameTestHelper helper) {
		Run r = setUp(helper, 2);
		int[] emptyAt = {-1};
		int[] refillAt = {-1};
		boolean[] emptiedWhileWalledIn = {false};
		boolean[] waited = {false};
		String[] madeFromNothing = {null};
		String[] inside = {null};
		final int refill = 3;
		helper.onEachTick(() -> {
			int tick = (int) helper.getTick();
			if (emptyAt[0] < 0 && r.site().status() == BuildSite.Status.FETCHING) {
				emptyAt[0] = tick + 5;
			}
			if (emptyAt[0] >= 0 && tick == emptyAt[0]) {
				emptiedWhileWalledIn[0] = walledIn(helper, r.v()) && inChest(helper) == STOCK;
				chest(helper).clearContent(); // the player takes all four
			}
			if (emptyAt[0] >= 0 && tick > emptyAt[0] && refillAt[0] < 0) {
				waited[0] |= r.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS;
				if (madeFromNothing[0] == null && (placed(helper) > 0 || inBag(r.v()) > 0 || onGround(helper) > 0)) {
					madeFromNothing[0] = "with the chest empty at tick " + tick + ": " + where(helper, r);
				}
				if (tick >= emptyAt[0] + 300) {
					refillAt[0] = tick;
					chest(helper).setItem(4, new ItemStack(Items.STONE, refill));
				}
			}
			watchInside(helper, r, inside);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inside[0] == null, inside[0] == null ? "" : inside[0]);
			helper.assertTrue(madeFromNothing[0] == null, "stone appeared " + madeFromNothing[0]);
			helper.assertTrue(refillAt[0] >= 0, "not refilled yet: " + where(helper, r));
			helper.assertTrue(emptiedWhileWalledIn[0], "setup: the builder had left its cell or taken stone before the chest was emptied");
			helper.assertTrue(waited[0], "the builder never waited for materials while its chest was empty");
			helper.assertTrue(placed(helper) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built after the refill: " + where(helper, r));
			int chest = inChest(helper), bag = inBag(r.v()), ground = onGround(helper);
			helper.assertTrue(refill - chest - bag == 1 && ground == 0 && total(helper, r.v()) == refill,
				"wanted exactly one of the " + refill + " refilled stone used: chest " + chest + ", bag " + bag + ", ground " + ground);
			LOGGER.info("qa 1107b fetch emptied: chest emptied at tick {} while walled in, waited, nothing made; refilled {} at {}; built, chest {} bag {}",
				emptyAt[0], refill, refillAt[0], chest, bag);
		});
	}

	/**
	 * The builder is on its way to its chest when a player breaks the chest (its 4 stone spill) and puts a plain stone
	 * block on the pillar instead. Nothing may be taken from the stone block or out of thin air: no stone placed or carried
	 * while there's no chest. A player then sets a new chest with 2 stone there: the build finishes with one of them, and
	 * every stone adds up (4 spilled + 2 brought = ground + chest + bag + built).
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qa1107bFetchBroken"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qa1107bFetchBroken")
	public void aChestBrokenWhileTheBuilderWalksToItLosesNothingAndTakesNothingFromTheBlockInItsPlace(GameTestHelper helper) {
		Run r = setUp(helper, 2);
		BlockPos chestAt = helper.absolutePos(CHEST);
		int[] breakAt = {-1};
		int[] replacedAt = {-1};
		int[] spilled = {-1};
		boolean[] brokenWhileWalledIn = {false};
		String[] fromNothing = {null};
		String[] inside = {null};
		final int brought = 2;
		helper.onEachTick(() -> {
			int tick = (int) helper.getTick();
			if (breakAt[0] < 0 && r.site().status() == BuildSite.Status.FETCHING) {
				breakAt[0] = tick + 5;
			}
			if (breakAt[0] >= 0 && tick == breakAt[0]) {
				brokenWhileWalledIn[0] = walledIn(helper, r.v()) && inChest(helper) == STOCK;
				helper.assertTrue(r.level().destroyBlock(chestAt, true), "the chest wasn't broken");
				r.level().setBlockAndUpdate(chestAt, Blocks.STONE.defaultBlockState());
			}
			if (breakAt[0] >= 0 && tick > breakAt[0] && replacedAt[0] < 0) {
				if (fromNothing[0] == null && (placed(helper) > 0 || inBag(r.v()) > 0 || !helper.getBlockState(CHEST).is(Blocks.STONE))) {
					fromNothing[0] = "with no chest at tick " + tick + ": " + where(helper, r) + ", block at the chest's place "
						+ helper.getBlockState(CHEST).getBlock().getName().getString();
				}
				if (tick == breakAt[0] + 20) {
					spilled[0] = onGround(helper);
				}
				if (tick >= breakAt[0] + 300) {
					replacedAt[0] = tick;
					helper.setBlock(CHEST, Blocks.CHEST);
					chest(helper).setItem(0, new ItemStack(Items.STONE, brought));
				}
			}
			watchInside(helper, r, inside);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inside[0] == null, inside[0] == null ? "" : inside[0]);
			helper.assertTrue(fromNothing[0] == null, "stone used or taken " + fromNothing[0]);
			helper.assertTrue(replacedAt[0] >= 0, "no new chest yet: " + where(helper, r));
			helper.assertTrue(brokenWhileWalledIn[0], "setup: the builder had left its cell or taken stone before the chest was broken");
			helper.assertTrue(spilled[0] == STOCK, "breaking the chest spilled " + spilled[0] + " stone, wanted " + STOCK);
			helper.assertTrue(placed(helper) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built after the new chest: " + where(helper, r));
			int chest = inChest(helper), bag = inBag(r.v()), ground = onGround(helper), pockets = r.v().getInventory().countItem(Items.STONE);
			helper.assertTrue(total(helper, r.v()) == STOCK + brought && ground + pockets + chest + bag == STOCK + brought - 1,
				"stone doesn't add up: " + STOCK + " spilled + " + brought + " brought; now ground " + ground + ", pockets " + pockets
					+ ", chest " + chest + ", bag " + bag + ", built " + placed(helper));
			LOGGER.info("qa 1107b fetch broken: chest broken at tick {} while walled in, {} spilled, nothing taken from the stone block; new chest with {} at {}; built, ground {} chest {} bag {}",
				breakAt[0], spilled[0], brought, replacedAt[0], ground, chest, bag);
		});
	}

	/**
	 * The server restarts (the builder saved and loaded) the moment it carries the stone it took and hasn't placed it
	 * yet. After the reload the bag holds the same stone, the block gets placed, and exactly one stone is used in all:
	 * chest + bag = 3, nothing on the ground.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qa1107bFetchReload"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qa1107bFetchReload")
	public void aBuilderReloadedCarryingTheStoneItTookPlacesItAndUsesExactlyOne(GameTestHelper helper) {
		Run r = setUp(helper, 40);
		int[] reloadedAt = {-1};
		int[] carried = {-1};
		int[] carriedAfter = {-1};
		String[] inside = {null};
		helper.onEachTick(() -> {
			if (reloadedAt[0] < 0 && inBag(r.v()) > 0 && placed(helper) == 0) {
				carried[0] = inBag(r.v());
				reloadedAt[0] = (int) helper.getTick();
				r.builder()[0] = reload(r.level(), r.v());
				carriedAfter[0] = inBag(r.v());
			}
			watchInside(helper, r, inside);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inside[0] == null, inside[0] == null ? "" : inside[0]);
			helper.assertTrue(reloadedAt[0] >= 0, "the builder never carried the stone unplaced: " + where(helper, r));
			helper.assertTrue(carriedAfter[0] == carried[0], "the bag held " + carried[0] + " stone before the reload, " + carriedAfter[0] + " after");
			helper.assertTrue(placed(helper) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built after the reload: " + where(helper, r));
			int chest = inChest(helper), bag = inBag(r.v()), ground = onGround(helper);
			helper.assertTrue(STOCK - chest - bag == 1 && ground == 0 && total(helper, r.v()) == STOCK,
				"wanted exactly one stone used: chest " + STOCK + " -> " + chest + ", bag " + bag + ", ground " + ground + " (carried " + carried[0] + " when reloaded)");
			LOGGER.info("qa 1107b fetch reload: reloaded at tick {} carrying {} stone (after: {}); built; chest {} bag {}",
				reloadedAt[0], carried[0], carriedAfter[0], chest, bag);
		});
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
