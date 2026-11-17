package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-11-17), Waystones (41.1) corners earlier runs left open, from the spec and the wiki:
 * <ul>
 * <li>41.1: "players who visit can discover it and travel there the usual way". The usual way costs a survival player
 * Waystones' own price (Waystones 21.1.37's default config: an experience POINT per 100 blocks, rounded down, from the
 * player to the stone; not refused in creative): the village's stone is an ordinary waystone, neither free (global) nor
 * refused. A player with exactly the price (705 blocks: 7 points, exactly level 1) travels and pays it all; one point short
 * stays put and keeps their experience; a creative player with none travels.</li>
 * <li>41.1 "the stone broken (the builder repairs it like any build)" with 45.1 "a moved building keeps what it had":
 * the stone of a plaza a Mover moved, broken, is put back by the village builder's upkeep at the NEW spot (never the old
 * one), registered and named after the village as it is then (renamed while the stone lay broken), and it is the
 * village's stone on the network again.</li>
 * </ul>
 * Helpers follow QaWaystoneMoved1116dCompatTests and QaWaystoneRenamedBroken1110dCompatTests.
 */
public class QaWaystones1117dCompatTests implements FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String AREA = WaystoneQaCompatTests.AREA;
	/** The stone (lower half) in the plaza, from its corner (as tools/blueprints/waystones.py draws it). */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final BlockPos HALL_A = WaystoneQaCompatTests.HALL_A;
	private static final BlockPos PLAZA_A = WaystoneQaCompatTests.PLAZA_A;
	/** Where the plaza is moved to: the new corner, still nearest the hall. */
	private static final BlockPos TO = new BlockPos(3, 2, 14);
	private static final BlockPos MOVER = new BlockPos(12, 2, 3);
	private static final BlockPos BENCH = new BlockPos(14, 2, 10);
	private static final BlockPos CHEST = new BlockPos(14, 2, 12);
	/** How far east of the village's stone the traveller stands: 705 blocks, so Waystones asks floor(7.05) = 7 points, exactly level 1. */
	private static final int EAST = 705;
	private static final int PRICE = 7;

	// ---- travel cost ------------------------------------------------------------------------------------------------------

	/** One village (its hall named {@code name}) with its plaza's stone raised and named; returns the stone's lower half. */
	private static BlockPos village(GameTestHelper helper, String name) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		VillageHalls.forgetNearest();
		WaystoneQaCompatTests.cleanUp(helper);
		ServerLevel level = helper.getLevel();
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		WaystoneQaCompatTests.hall(helper, HALL_A, name, new UUID(0x1117dL, 0x41L));
		WaystoneQaCompatTests.plaza(helper, PLAZA_A);
		VillageWaystones.round(level, helper.absolutePos(HALL_A));
		BlockPos stone = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		helper.assertTrue(WaystoneQaCompatTests.nameAt(helper, PLAZA_A).equals(Optional.of(name)), "setup: the village's stone is "
			+ WaystoneQaCompatTests.nameAt(helper, PLAZA_A));
		return stone;
	}

	private static void clearVillage(GameTestHelper helper) {
		WaystoneQaCompatTests.cleanUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos stone = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		level.setBlock(stone.above(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
		level.removeBlock(helper.absolutePos(HALL_A), false);
	}

	/** A player of {@code mode} with {@code points} experience points, standing {@link #EAST} blocks east of the stone. */
	private static ServerPlayer traveller(GameTestHelper helper, BlockPos stone, GameType mode, int points) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(mode);
		player.setExperienceLevels(0);
		player.experienceProgress = 0;
		player.totalExperience = 0;
		player.giveExperiencePoints(points);
		BlockPos at = stone.offset(EAST, 0, 0);
		level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
		level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 3);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Waystone target = WaystonesAPI.getWaystoneAt(level, stone).filter(Waystone::isValid)
			.orElseThrow(() -> new GameTestAssertException("setup: no working waystone at the village's stone " + stone.toShortString()));
		WaystonesAPI.activateWaystone(player, target); // visited once and discovered it, as 41.1 says
		return player;
	}

	private static void unforce(GameTestHelper helper, BlockPos stone) {
		BlockPos at = stone.offset(EAST, 0, 0);
		helper.getLevel().setBlock(at.below(), Blocks.AIR.defaultBlockState(), 3);
		helper.getLevel().setChunkForced(at.getX() >> 4, at.getZ() >> 4, false);
	}

	private static WaystoneTeleportContext context(GameTestHelper helper, ServerPlayer player, BlockPos stone) {
		Waystone target = WaystonesAPI.getWaystoneAt(helper.getLevel(), stone).filter(Waystone::isValid)
			.orElseThrow(() -> new GameTestAssertException("no working waystone at " + stone.toShortString()));
		var context = WaystonesAPI.createDefaultTeleportContext(player, target, c -> { });
		return context.left().orElseThrow(() -> new GameTestAssertException("the Waystones mod refused to plan the trip: "
			+ context.right().map(e -> e.getClass().getSimpleName()).orElse("?")));
	}

	/** What the Waystones mod asks and why, for a failure message: the price's own words, the stone's visibility, the distance. */
	private static String why(GameTestHelper helper, ServerPlayer player, BlockPos stone, WaystoneTeleportContext ctx) {
		List<Component> words = new java.util.ArrayList<>();
		ctx.getRequirements().appendHoverText(player, words);
		return "price " + words.stream().map(Component::getString).toList() + " (" + ctx.getRequirements().getClass().getSimpleName() + "), stone "
			+ ctx.getTargetWaystone().getVisibility() + " at " + ctx.getTargetWaystone().getPos().toShortString() + ", player at " + player.blockPosition().toShortString()
			+ " (" + (int) Math.sqrt(player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(stone))) + " blocks), " + player.gameMode.getGameModeForPlayer();
	}

	private static double flat(BlockPos x, BlockPos y) {
		return Math.hypot(x.getX() - y.getX(), x.getZ() - y.getZ());
	}

	/** Experience points the Waystones mod counts for {@code player} (its own sum: the levels' points and the bar's, rounded down). */
	private static int points(ServerPlayer player) {
		int level = player.experienceLevel;
		int cumulative = level <= 16 ? level * level + 6 * level : level <= 31 ? (int) (2.5 * level * level - 40.5 * level + 360) : (int) (4.5 * level * level - 162.5 * level + 2220);
		return cumulative + (int) Math.floor(player.experienceProgress * player.getXpNeededForNextLevel());
	}

	/**
	 * A survival player 705 blocks from the village's stone with exactly the price (7 points: level 1, an empty bar)
	 * travels with the Waystones mod's own teleport, lands by the stone, and pays it all (level 0, an empty bar). The price
	 * the mod asks for the village's stone is 7 points: 6 can't afford it, 7 can (the stone is not a free global waystone).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystones1117dExact"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystones1117dExact")
	public void aSurvivalPlayerWithExactlyThePriceTravelsAndPaysIt(GameTestHelper helper) {
		BlockPos stone = village(helper, "Hollinsby");
		try {
			ServerPlayer player = traveller(helper, stone, GameType.SURVIVAL, PRICE);
			helper.assertTrue(player.experienceLevel == 1 && points(player) == PRICE, "setup: 7 points should be level 1 with an empty bar: level "
				+ player.experienceLevel + ", bar " + player.experienceProgress);
			WaystoneTeleportContext ctx = context(helper, player, stone);
			WarpRequirement price = ctx.getRequirements();
			helper.assertFalse(price.isEmpty(), "the trip to the village's stone 705 blocks off costs nothing (a free waystone?) " + why(helper, player, stone, ctx));
			ServerPlayer poorer = traveller(helper, stone, GameType.SURVIVAL, PRICE - 1);
			helper.assertTrue(!price.canAfford(poorer) && price.canAfford(player), "the price of 705 blocks should be 7 points (6 short, 7 enough): affords with "
				+ points(poorer) + ": " + price.canAfford(poorer) + ", with " + points(player) + ": " + price.canAfford(player) + "; " + why(helper, player, stone, ctx));
			poorer.discard();
			var result = WaystonesAPI.tryTeleport(ctx);
			helper.assertTrue(result.left().isPresent(), "with exactly the price the trip failed: " + result.right().map(e -> e.getClass().getSimpleName()).orElse("?"));
			BlockPos landed = player.blockPosition();
			helper.assertTrue(flat(landed, stone) <= 3, "the player landed at " + landed.toShortString() + ", not by the village's stone " + stone.toShortString());
			helper.assertTrue(player.experienceLevel == 0 && points(player) == 0, "a player with exactly the price should have nothing left: level "
				+ player.experienceLevel + ", bar " + player.experienceProgress);
			LOGGER.info("QA 41.1 cost: survival with {} points travelled {} blocks to Hollinsby's stone, landed {}, points left {}", PRICE, EAST, landed.toShortString(), points(player));
		} finally {
			unforce(helper, stone);
			clearVillage(helper);
		}
		helper.succeed();
	}

	/**
	 * One point short (6 points: level 0, the bar 6/7 full): the Waystones mod refuses the trip as not enough experience;
	 * the player stays where they stood and keeps their experience.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystones1117dShort"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystones1117dShort")
	public void aSurvivalPlayerOnePointShortStaysAndKeepsTheirExperience(GameTestHelper helper) {
		BlockPos stone = village(helper, "Marrowdene");
		try {
			ServerPlayer player = traveller(helper, stone, GameType.SURVIVAL, PRICE - 1);
			int had = points(player);
			float bar = player.experienceProgress;
			helper.assertTrue(had == PRICE - 1, "setup: the player should have 6 points, has " + had);
			BlockPos before = player.blockPosition();
			WaystoneTeleportContext ctx = context(helper, player, stone);
			String why = why(helper, player, stone, ctx);
			var result = WaystonesAPI.tryTeleport(ctx);
			helper.assertTrue(result.right().filter(e -> e instanceof WaystoneTeleportError.NotEnoughXp).isPresent(),
				"one point short, the trip should be refused for want of experience: " + (result.left().isPresent() ? "it went"
					: result.right().map(e -> e.getClass().getSimpleName()).orElse("?")) + "; " + why);
			helper.assertTrue(player.blockPosition().equals(before), "refused, the player moved from " + before.toShortString() + " to " + player.blockPosition().toShortString());
			helper.assertTrue(player.experienceLevel == 0 && player.experienceProgress == bar, "refused, the player's experience changed: level "
				+ player.experienceLevel + ", bar " + player.experienceProgress + " (was " + bar + ")");
			LOGGER.info("QA 41.1 cost: survival with {} points refused for Marrowdene's stone ({}), stayed at {}", had, why, before.toShortString());
		} finally {
			unforce(helper, stone);
			clearVillage(helper);
		}
		helper.succeed();
	}

	/** A creative player with no experience at all travels to the village's stone (the price isn't asked in creative). */
	//$ gametest_ticks_batch AREA '200' '"qaWaystones1117dCreative"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystones1117dCreative")
	public void aCreativePlayerWithNoExperienceTravels(GameTestHelper helper) {
		BlockPos stone = village(helper, "Tallowfield");
		try {
			ServerPlayer player = traveller(helper, stone, GameType.CREATIVE, 0);
			var result = WaystonesAPI.tryTeleport(context(helper, player, stone));
			helper.assertTrue(result.left().isPresent(), "a creative player with no experience was refused: " + result.right().map(e -> e.getClass().getSimpleName()).orElse("?"));
			helper.assertTrue(flat(player.blockPosition(), stone) <= 3, "the creative player landed at " + player.blockPosition().toShortString()
				+ ", not by the stone " + stone.toShortString());
			helper.assertTrue(player.experienceLevel == 0 && player.experienceProgress >= 0, "the creative player's experience: level " + player.experienceLevel
				+ ", bar " + player.experienceProgress);
		} finally {
			unforce(helper, stone);
			clearVillage(helper);
		}
		helper.succeed();
	}

	// ---- the moved stone repaired -------------------------------------------------------------------------------------------

	/**
	 * The plaza (the builder's) moved a quarter turn by a Mover; the moved stone is broken and the village renamed while it
	 * lies broken. The builder's upkeep puts a waystone back at the NEW spot (nothing at the old one), named after the
	 * village's new name, a working waystone the network keeps as the village's stone.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaWaystones1117dMovedRepair"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaWaystones1117dMovedRepair")
	public void aBrokenMovedStoneIsRepairedAtItsNewSpotWithTheVillagesName(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		WaystoneQaCompatTests.cleanUp(helper);
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		boolean was = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		BlockPos hallAt = helper.absolutePos(HALL_A);
		BlockPos old = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		BlockPos[] moved = new BlockPos[1];
		PartnerShowsCompatTests.after(helper, () -> {
			Upkeep.ENABLED = was;
			Upkeep.ASK = asks;
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			PendingMoves.get(level).remove(helper.absolutePos(PLAZA_A));
			MoveSites.get(level).cancel(level, helper.absolutePos(PLAZA_A));
			BuildSiteManager sites = BuildSiteManager.get(level);
			net.minecraft.world.phys.AABB box = helper.getBounds();
			sites.finishedIn(level).stream().filter(f -> VillageWaystones.isPlaza(f.structure()) && box.contains(net.minecraft.world.phys.Vec3.atCenterOf(f.placement().origin())))
				.map(BuildSiteManager.Finished::placement).toList().forEach(sites::forgetFinished);
			sites.all().stream().filter(s -> box.contains(net.minecraft.world.phys.Vec3.atCenterOf(s.placement().origin()))).toList()
				.forEach(s -> sites.remove(s.id()));
			WaystoneQaCompatTests.cleanUp(helper);
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 8, 29)))) {
				if (VillageWaystones.isStone(level.getBlockState(p))) {
					level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
					level.setBlock(p.immutable(), Blocks.AIR.defaultBlockState(), 3);
				}
			}
			level.removeBlock(hallAt, false);
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());

		VillageHallBlockEntity hall = WaystoneQaCompatTests.hall(helper, HALL_A, "Ravensholt", new UUID(0x1117dL, 0x42L));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 11));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		WaystoneQaCompatTests.plaza(helper, PLAZA_A);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.forgetFinished(WaystoneQaCompatTests.placement(helper, PLAZA_A));
		sites.recordFinished(VillageWaystones.PLAZA, WaystoneQaCompatTests.placement(helper, PLAZA_A), builder.getUUID()); // the builder's own build
		VillageWaystones.round(level, hallAt);

		// The move, as QaWaystoneMoved1116dCompatTests does it.
		helper.setBlock(MOVER.north(), ModBlocks.MOVERS_CRATE);
		Villager mover = helper.spawn(EntityType.VILLAGER, MOVER);
		mover.setNoAi(true);
		mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER));
		mover.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(MOVER.north())));
		PendingMoves.get(level).put(new PendingMoves.Move(hallAt, helper.absolutePos(PLAZA_A), VillageWaystones.PLAZA, helper.absolutePos(TO), Rotation.CLOCKWISE_90, null));
		MoveSite site = MoveSites.get(level).work(level, hallAt).orElseThrow(() -> new GameTestAssertException("the plaza's move didn't begin"));
		MovePlan plan = site.plan();
		for (int i = 0; i < 8000 && site.stage() != MoveSite.Stage.DONE; i++) {
			MoverWork.work(level, mover, site);
		}
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "setup: the move didn't end: " + site.stage());
		mover.discard();
		VillageWaystones.round(level, hallAt);
		BlockPos now = plan.at(STONE, helper.absolutePos(TO), Rotation.CLOCKWISE_90);
		moved[0] = now;
		helper.assertTrue(VillageWaystones.isStone(level.getBlockState(now)) && level.getBlockState(old).isAir(), "setup: after the move the new spot holds "
			+ level.getBlockState(now) + " and the old " + level.getBlockState(old));
		helper.assertTrue(VillageWaystones.stoneOf(level, hallAt).equals(Optional.of(now)), "setup: the network's stone after the move: " + VillageWaystones.stoneOf(level, hallAt));
		helper.assertTrue(sites.finishedIn(level).stream().anyMatch(f -> VillageWaystones.isPlaza(f.structure()) && f.owner().equals(builder.getUUID())),
			"setup: the moved plaza is no longer the builder's finished build");

		// Broken at its new spot, and the village renamed while it lies broken.
		BlockState lower = level.getBlockState(now);
		level.setBlock(now.above(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(now, Blocks.AIR.defaultBlockState(), 3);
		helper.assertFalse(VillageWaystones.standing(level, hallAt), "setup: the broken moved stone still stands");
		hall.setCustomName(Component.literal("Ravensholt Green"));
		VillageWaystones.round(level, hallAt);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(lower.getBlock().asItem()));
		Upkeep.ENABLED = true;
		Upkeep.ASK = false; // 36.15: repairs without asking; the ask has its own tests
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				BuildSite repair = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
				helper.assertTrue(repair != null && repair.isRepair(), "no repair of the moved plaza started");
			})
			.thenWaitUntil(() -> {
				helper.assertFalse(VillageWaystones.isStone(level.getBlockState(old)) || VillageWaystones.isStone(level.getBlockState(old.above())),
					"the builder put a waystone back at the OLD spot " + old.toShortString() + " (the plaza now stands at " + now.toShortString() + ")");
				helper.assertTrue(VillageWaystones.isStone(level.getBlockState(now)), "no stone back at the moved spot yet");
				VillageWaystones.round(level, hallAt);
				Optional<String> name = VillageWaystones.EXTENSION.call(s -> s.nameAt(level, now), Optional.<String>empty());
				helper.assertTrue(name.equals(Optional.of("Ravensholt Green")), "the stone put back at the moved spot is named " + name + ", not Ravensholt Green");
			})
			.thenExecute(() -> {
				helper.assertTrue(VillageWaystones.stoneOf(level, hallAt).equals(Optional.of(now)) && VillageWaystones.standing(level, hallAt),
					"after the repair the network keeps " + VillageWaystones.stoneOf(level, hallAt) + " (standing " + VillageWaystones.standing(level, hallAt)
						+ "), not the moved spot " + now.toShortString());
				List<String> named = net.blay09.mods.waystones.core.WaystoneManagerImpl.get(level.getServer()).getWaystones()
					.filter(w -> w.getName().getString().startsWith("Ravensholt")).map(w -> w.getName().getString() + "@" + w.getPos().toShortString()).toList();
				helper.assertTrue(named.size() == 1, "the Waystones mod should list one stone of the village, lists " + named);
				LOGGER.info("QA 41.1 x 45.1: the broken moved stone was repaired at {} (old spot {} empty), named {}", now.toShortString(), old.toShortString(), named);
			})
			.thenSucceed();
	}
}
