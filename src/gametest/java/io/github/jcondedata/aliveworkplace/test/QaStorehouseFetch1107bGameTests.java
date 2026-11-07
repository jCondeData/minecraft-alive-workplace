package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-11-07 (b), the gaps QaBuilderFetch1107bGameTests left: a Builder fetching from the village's Storehouse
 * (builder.md, "Whichever table (B290)": beyond its bench's chests a Builder takes the village's stock, a Storehouse
 * shared by its owner) and from a supply barrel by its own bench (builder.md: "the chests and barrels within
 * supplyRadius of the bench"). As there, the builder is walled in a glass cell on a ledge and has to hop to a spot by
 * the store; a player fills that spot while it waits, or empties the Storehouse chest the moment the builder stands in
 * reach of it. Every stone is counted before and after: none lost, none made, the one-block build finished (stage DONE).
 */
public class QaStorehouseFetch1107bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	/** A builder takes from a chest from up to this far, eye to the chest's centre (BuilderWork.CONTAINER_REACH). */
	private static final double CONTAINER_REACH = 3.0;
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-0000001107b5");
	private static final BlockPos SRC = new BlockPos(25, 2, 25);
	/** The builder, walled in a glass cell on a ledge, so it can only hop. */
	private static final BlockPos BUILDER = new BlockPos(3, 8, 3);
	private static final BlockPos BENCH = new BlockPos(2, 8, 3);
	private static final int STOCK = 4;

	/**
	 * Where the store and the ledges by it stand: QaBuilderFetch1107b's layout moved {@code d} blocks along x and z. With
	 * d = 2 the store is 10 blocks from the bench, beyond its 8-block supply radius (only the Storehouse's then); with
	 * d = 0 it is 8 and 7 blocks off, the bench's own.
	 */
	private record Layout(int d) {
		BlockPos store() {
			return new BlockPos(10 + d, 8, 10 + d);
		}

		/** The ledge nearest the builder from which the store is in reach (eye about 2.29 from it): filled while it waits. */
		BlockPos first() {
			return new BlockPos(10 + d, 8, 8 + d);
		}

		/** Nearer the builder than any ledge in reach, but its eye would be about 3.04 from the store: out of reach. */
		BlockPos tooFar() {
			return new BlockPos(8 + d, 8, 8 + d);
		}

		/** The far side of the store: eye about 2.29 from it. */
		BlockPos farSide() {
			return new BlockPos(12 + d, 8, 10 + d);
		}

		/** The one stone to build, on its own pillar (in reach of both ledges beside the store). */
		BlockPos target() {
			return new BlockPos(12 + d, 8, 12 + d);
		}

		/** The Storehouse block, on its own pillar 4 blocks beyond the store, out of reach of every ledge. */
		BlockPos storehouse() {
			return new BlockPos(10 + d, 8, 14 + d);
		}
	}

	private static final Layout AT_STOREHOUSE = new Layout(2);
	private static final Layout BY_BENCH = new Layout(0);

	private record Run(ServerLevel level, Villager builder, BuildSite site, Layout at, Block kind) {
	}

	/**
	 * A builder hired by Jesse, walled in by its bench, with a one-stone build for Jesse; {@code STOCK} stone in the store
	 * ({@code kind}, a chest or a barrel). With {@code storehouse}, the store is one of Jesse's Storehouse's, 10 blocks
	 * from the bench; without, it's the bench's own supply.
	 */
	private static Run setUp(GameTestHelper helper, boolean storehouse, @org.jetbrains.annotations.Nullable Block kind) {
		Layout at = storehouse ? AT_STOREHOUSE : BY_BENCH;
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		Leftovers.village(helper, 48);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		Leftovers.stores(helper, helper.absolutePos(BENCH));
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
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);

		for (int y = 2; y < at.store().getY(); y++) {
			helper.setBlock(new BlockPos(at.store().getX(), y, at.store().getZ()), Blocks.STONE);
			helper.setBlock(new BlockPos(at.target().getX(), y, at.target().getZ()), Blocks.STONE);
			if (storehouse) {
				helper.setBlock(new BlockPos(at.storehouse().getX(), y, at.storehouse().getZ()), Blocks.STONE);
			}
		}
		for (BlockPos spot : new BlockPos[] {at.first(), at.tooFar(), at.farSide()}) {
			helper.setBlock(spot.below(), Blocks.STONE);
		}
		if (kind != null) {
			helper.setBlock(at.store(), kind);
			store(helper, at).setItem(0, new ItemStack(Items.STONE, STOCK));
		}
		if (storehouse) {
			helper.setBlock(at.storehouse(), ModBlocks.STOREHOUSE);
			BlockPos abs = helper.absolutePos(at.storehouse());
			if (level.getPoiManager().getType(abs).isEmpty()) {
				PoiTypes.forState(level.getBlockState(abs)).ifPresent(type -> level.getPoiManager().add(abs.immutable(), type));
			}
			((StorehouseBlockEntity) helper.getBlockEntity(at.storehouse())).setOwner(JESSE, "Jesse");
		}
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
		ModAttachments.BUILDER_EMPLOYER.set(villager, new Employer(JESSE, "Jesse"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at.target()),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, JESSE, "Jesse", id, placement);
		helper.assertTrue(site != null, "the one-block build didn't start");
		Village.forget(villager);
		// the layout really is what it claims: the Storehouse's chest isn't the bench's, the barrel is
		boolean own = io.github.jcondedata.aliveworkplace.build.SupplyContainers.find(level, helper.absolutePos(BENCH), null)
			.contains(helper.absolutePos(at.store()));
		helper.assertTrue(kind == null || own != storehouse, "setup: the store at " + at.store().toShortString() + (own ? " is" : " isn't") + " in the bench's supply radius");
		return new Run(level, villager, site, at, kind);
	}

	private static Container store(GameTestHelper helper, Layout at) {
		return helper.getBlockEntity(at.store());
	}

	private static int inStore(GameTestHelper helper, Run r) {
		return r.kind() != null && helper.getBlockState(r.at().store()).is(r.kind()) ? store(helper, r.at()).countItem(Items.STONE) : 0;
	}

	private static int inBag(Villager v) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).count(Items.STONE);
	}

	private static int onGround(GameTestHelper helper) {
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(30, 30, 30).inflate(2);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.STONE)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static int placed(GameTestHelper helper, Run r) {
		return helper.getBlockState(r.at().target()).is(Blocks.STONE) ? 1 : 0;
	}

	/** Every stone there is: in the store, in the builder's bag and pockets, on the ground, and the one built. */
	private static int total(GameTestHelper helper, Run r) {
		return inStore(helper, r) + inBag(r.builder()) + r.builder().getInventory().countItem(Items.STONE) + onGround(helper) + placed(helper, r);
	}

	private static String rel(GameTestHelper helper, BlockPos abs) {
		return abs.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	private static boolean walledIn(GameTestHelper helper, Villager v) {
		return v.blockPosition().distManhattan(helper.absolutePos(BUILDER)) <= 1;
	}

	private static double eyeToStore(GameTestHelper helper, Run r) {
		return r.builder().getEyePosition().distanceTo(Vec3.atCenterOf(helper.absolutePos(r.at().store())));
	}

	/** Records the first tick the builder is inside a block, if ever. */
	private static void watchInside(GameTestHelper helper, Run r, String[] inside) {
		Villager v = r.builder();
		if (inside[0] == null && v.isAlive() && !r.level().noCollision(v, v.getBoundingBox().deflate(0.1))) {
			inside[0] = "the builder is inside a block at " + rel(helper, v.blockPosition()) + " ("
				+ r.level().getBlockState(v.blockPosition()).getBlock().getName().getString() + ") at tick " + helper.getTick();
		}
	}

	private static String where(GameTestHelper helper, Run r) {
		return "stage " + r.site().stage() + ", status " + r.site().status() + ", builder at " + rel(helper, r.builder().blockPosition())
			+ ", store " + inStore(helper, r) + ", bag " + inBag(r.builder()) + ", ground " + onGround(helper) + ", built " + placed(helper, r);
	}

	/**
	 * The spot-filled case, shared by the Storehouse and the barrel: once the builder sets off for the store (walled in,
	 * waiting to hop to the ledge beside it), a player fills that ledge with dirt. It must take the stone from within reach
	 * of the store, never hop into a block or onto the ledge out of reach, and finish with exactly one stone out of the
	 * store (4 before: store + bag = 3 after, nothing on the ground).
	 */
	private static void spotFilled(GameTestHelper helper, Run r, String what) {
		BlockPos first = helper.absolutePos(r.at().first());
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
				filledWhileWalledIn[0] = walledIn(helper, r.builder()) && inStore(helper, r) == STOCK;
				r.level().setBlockAndUpdate(first, Blocks.DIRT.defaultBlockState());
			}
			if (takenFrom[0] < 0 && inStore(helper, r) < STOCK) {
				takenFrom[0] = eyeToStore(helper, r);
				stoodAt[0] = r.builder().blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
			}
			onTooFar[0] |= r.builder().blockPosition().equals(helper.absolutePos(r.at().tooFar()));
			watchInside(helper, r, inside);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inside[0] == null, inside[0] == null ? "" : inside[0]);
			helper.assertTrue(!onTooFar[0], "the builder hopped onto the ledge out of the " + what + "'s reach at " + r.at().tooFar().toShortString()
				+ " (" + where(helper, r) + ")");
			helper.assertTrue(fillAt[0] >= 0 && helper.getTick() > fillAt[0], "the builder never set off for the " + what + ": " + where(helper, r));
			helper.assertTrue(takenFrom[0] >= 0, "nothing taken from the " + what + " yet: " + where(helper, r));
			helper.assertTrue(filledWhileWalledIn[0], "setup: the builder had left its cell (or emptied the " + what + ") before its ledge was filled");
			helper.assertTrue(takenFrom[0] <= CONTAINER_REACH + 1e-6, "the stone was taken from " + String.format("%.2f", takenFrom[0])
				+ " blocks away (at " + stoodAt[0].toShortString() + "), beyond a store's reach of " + CONTAINER_REACH);
			helper.assertTrue(placed(helper, r) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built yet: " + where(helper, r));
			helper.assertTrue(r.site().skipped() == 0, "the builder skipped " + r.site().skipped() + " block(s)");
			int store = inStore(helper, r), bag = inBag(r.builder()), ground = onGround(helper);
			helper.assertTrue(STOCK - store - bag == 1 && ground == 0 && total(helper, r) == STOCK,
				"wanted exactly one stone used: " + what + " " + STOCK + " -> " + store + ", bag " + bag + ", ground " + ground + ", built " + placed(helper, r));
			LOGGER.info("qa 1107b {} spot filled: ledge {} filled while walled in; stone taken from {} ({} blocks, reach {}); {} {} -> {}, bag {}, built 1, stage {}",
				what, r.at().first().toShortString(), stoodAt[0].toShortString(), String.format("%.2f", takenFrom[0]), CONTAINER_REACH, what, STOCK, store, bag,
				r.site().stage());
		});
	}

	/**
	 * Nothing by the bench: the stone is in a chest of Jesse's Storehouse, 10 blocks off. The builder sets off for it and
	 * a player fills the ledge it was to hop to. It takes the stone from within 3 blocks of the Storehouse chest, never
	 * from inside a block or from the ledge out of reach, and the Storehouse holds exactly what wasn't used.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qa1107bStoreSpot"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qa1107bStoreSpot")
	public void aBuilderWhoseSpotByTheStorehouseChestWasFilledTakesFromASpotInReach(GameTestHelper helper) {
		spotFilled(helper, setUp(helper, true, Blocks.CHEST), "Storehouse chest");
	}

	/**
	 * The same with the bench's own supply barrel (barrels count as supply chests): the ledge by the barrel filled while the
	 * builder waits to hop there, the stone taken from within reach, exactly one used.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qa1107bBarrelSpot"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qa1107bBarrelSpot")
	public void aBuilderWhoseSpotByItsSupplyBarrelWasFilledTakesFromASpotInReach(GameTestHelper helper) {
		spotFilled(helper, setUp(helper, false, Blocks.BARREL), "barrel");
	}

	/**
	 * The builder has hopped to the ledge by the Storehouse chest and stands in reach, about to take, when a player takes
	 * all four stone out (at the end of the tick it arrived, before its next work tick). Nothing may be made from nothing:
	 * while the chest is empty no stone is placed, carried or dropped, and the builder waits for materials. A player then
	 * puts 3 back: it finishes with exactly one of them used (chest + bag = 2), nothing on the ground.
	 */
	//$ gametest_ticks_batch HUGE '1500' '"qa1107bStoreEmptied"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1107bStoreEmptied")
	public void aStorehouseChestEmptiedAsTheBuilderReachesItHoldsItUntilRefilledWithNothingMade(GameTestHelper helper) {
		Run r = setUp(helper, true, Blocks.CHEST);
		int[] emptyAt = {-1};
		int[] refillAt = {-1};
		double[] emptiedFrom = {-1};
		boolean[] waited = {false};
		String[] madeFromNothing = {null};
		String[] inside = {null};
		final int refill = 3;
		helper.onEachTick(() -> {
			int tick = (int) helper.getTick();
			if (emptyAt[0] < 0 && r.site().status() == BuildSite.Status.FETCHING && inStore(helper, r) == STOCK && inBag(r.builder()) == 0
				&& eyeToStore(helper, r) <= CONTAINER_REACH) {
				emptyAt[0] = tick;
				emptiedFrom[0] = eyeToStore(helper, r);
				store(helper, r.at()).clearContent(); // the player takes all four, right as the builder stands at the chest
			}
			if (emptyAt[0] >= 0 && tick > emptyAt[0] && refillAt[0] < 0) {
				waited[0] |= r.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS;
				if (madeFromNothing[0] == null && (placed(helper, r) > 0 || inBag(r.builder()) > 0 || onGround(helper) > 0
					|| r.builder().getInventory().countItem(Items.STONE) > 0)) {
					madeFromNothing[0] = "with the Storehouse chest empty at tick " + tick + ": " + where(helper, r);
				}
				if (tick >= emptyAt[0] + 300) {
					refillAt[0] = tick;
					store(helper, r.at()).setItem(4, new ItemStack(Items.STONE, refill));
				}
			}
			watchInside(helper, r, inside);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inside[0] == null, inside[0] == null ? "" : inside[0]);
			helper.assertTrue(madeFromNothing[0] == null, "stone appeared " + madeFromNothing[0]);
			helper.assertTrue(emptyAt[0] >= 0, "the builder never stood in reach of the full Storehouse chest: " + where(helper, r));
			helper.assertTrue(refillAt[0] >= 0, "not refilled yet: " + where(helper, r));
			helper.assertTrue(waited[0], "the builder never waited for materials while the Storehouse chest was empty");
			helper.assertTrue(placed(helper, r) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built after the refill: " + where(helper, r));
			int store = inStore(helper, r), bag = inBag(r.builder()), ground = onGround(helper);
			helper.assertTrue(refill - store - bag == 1 && ground == 0 && total(helper, r) == refill,
				"wanted exactly one of the " + refill + " refilled stone used: chest " + store + ", bag " + bag + ", ground " + ground);
			LOGGER.info("qa 1107b storehouse emptied: emptied at tick {} with the builder {} blocks from it, before it took any; waited, nothing made; refilled {} at {}; built (stage {}), chest {} bag {}",
				emptyAt[0], String.format("%.2f", emptiedFrom[0]), refill, refillAt[0], r.site().stage(), store, bag);
		});
	}

	/**
	 * No stone in any chest: a crewmate of the build (a helper seen on the site, standing on the pillar where the other
	 * tests have the store, more than 8 blocks off so the lead walks over rather than having it passed) carries 4 stone it
	 * doesn't need for its own block. The lead sets off for it and a player fills the ledge it was to hop to. It takes the
	 * stone from within 3 blocks of the crewmate, never from inside a block or the ledge out of reach, and no stone is lost
	 * or made: crewmate's bag + lead's bag = 3, one built, nothing on the ground. The crewmate is a villager without AI
	 * (it stays put; a real helper would wander) marked as a helper through BuildSite.claim, the site's own record of who
	 * worked on it lately.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qa1107bCrewSpot"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qa1107bCrewSpot")
	public void aBuilderWhoseSpotByACrewmateWasFilledTakesItsStoneFromASpotInReachWithNothingLost(GameTestHelper helper) {
		Run r = setUp(helper, false, null);
		// the bench's own supply chest, empty (the crew's stone came out of it earlier), in the cell's wall
		BlockPos benchChest = new BlockPos(2, 8, 2);
		helper.setBlock(benchChest, Blocks.CHEST);
		Villager mate = helper.spawn(EntityType.VILLAGER, r.at().store());
		mate.setNoAi(true);
		ModAttachments.BUILDER_BAG.getOrCreate(mate).addAll(Items.STONE, STOCK);
		BlockPos mateAt = helper.absolutePos(r.at().store());
		BlockPos first = helper.absolutePos(r.at().first());
		int[] fillAt = {-1};
		boolean[] filledWhileWalledIn = {false};
		double[] takenFrom = {-1};
		BlockPos[] stoodAt = {null};
		boolean[] onTooFar = {false};
		String[] inside = {null};
		helper.onEachTick(() -> {
			// the helper works on the site: seen there every tick, its claim its own spot (no block of the build)
			r.site().claim(mate.getUUID(), mateAt, r.level().getGameTime());
			if (fillAt[0] < 0 && r.site().status() == BuildSite.Status.FETCHING) {
				fillAt[0] = (int) helper.getTick() + 5;
			}
			if (fillAt[0] >= 0 && helper.getTick() == fillAt[0]) {
				filledWhileWalledIn[0] = walledIn(helper, r.builder()) && inBag(mate) == STOCK;
				r.level().setBlockAndUpdate(first, Blocks.DIRT.defaultBlockState());
			}
			if (takenFrom[0] < 0 && inBag(mate) < STOCK) {
				takenFrom[0] = r.builder().getEyePosition().distanceTo(Vec3.atCenterOf(mate.blockPosition()));
				stoodAt[0] = r.builder().blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
			}
			onTooFar[0] |= r.builder().blockPosition().equals(helper.absolutePos(r.at().tooFar()));
			watchInside(helper, r, inside);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inside[0] == null, inside[0] == null ? "" : inside[0]);
			helper.assertTrue(mate.isAlive() && mate.blockPosition().equals(mateAt), "setup: the crewmate left its pillar");
			helper.assertTrue(!onTooFar[0], "the builder hopped onto the ledge out of the crewmate's reach at " + r.at().tooFar().toShortString()
				+ " (" + where(helper, r) + ")");
			helper.assertTrue(fillAt[0] >= 0 && helper.getTick() > fillAt[0], "the builder never set off for its crewmate: " + where(helper, r));
			helper.assertTrue(takenFrom[0] >= 0, "nothing taken from the crewmate yet: " + where(helper, r) + ", crewmate's bag " + inBag(mate));
			helper.assertTrue(filledWhileWalledIn[0], "setup: the builder had left its cell (or taken the stone) before its ledge was filled");
			helper.assertTrue(takenFrom[0] <= CONTAINER_REACH + 1e-6, "the stone was taken from " + String.format("%.2f", takenFrom[0])
				+ " blocks away (at " + stoodAt[0].toShortString() + "), beyond the reach of " + CONTAINER_REACH);
			helper.assertTrue(placed(helper, r) == 1 && r.site().stage() == BuildPlan.Stage.DONE, "not built yet: " + where(helper, r));
			helper.assertTrue(r.site().skipped() == 0, "the builder skipped " + r.site().skipped() + " block(s)");
			int theirs = inBag(mate), bag = inBag(r.builder()), ground = onGround(helper);
			int put = ((Container) helper.getBlockEntity(benchChest)).countItem(Items.STONE);
			helper.assertTrue(theirs + bag + put == STOCK - 1 && ground == 0 && total(helper, r) + theirs + put == STOCK,
				"wanted exactly one stone used: crewmate " + STOCK + " -> " + theirs + ", lead's bag " + bag + ", bench chest " + put + ", ground " + ground
					+ ", built " + placed(helper, r));
			LOGGER.info("qa 1107b crewmate spot filled: ledge {} filled while walled in; stone taken from {} ({} blocks from the crewmate, reach {}); crewmate {} -> {}, lead's bag {}, bench chest {}, built 1, stage {}",
				r.at().first().toShortString(), stoodAt[0].toShortString(), String.format("%.2f", takenFrom[0]), CONTAINER_REACH, STOCK, theirs, bag, put,
				r.site().stage());
		});
	}
}
