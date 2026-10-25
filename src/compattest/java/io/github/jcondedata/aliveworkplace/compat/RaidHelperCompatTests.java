package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.RaidAssist;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 38.1a (with 38.3a and 38.4a): the village no longer raids the dens alone; a player hires its trainers from the
 * Defence page to help in a raid. Hiring charges the price (CobbleDollars here, as the pack has them) and the helper
 * follows; no money, no helper; at most three; a raid joined by the den gives each helper's cheer once a raid; their
 * Pokémon wear a boss down but never under a quarter; a won raid goes in the chronicle and the helpers walk home, as
 * they do when the day is up; switched off there's no button and nothing happens; the old wardens' and challenges'
 * saved data doesn't stop a world loading. Each test runs alone (its own batch) and leaves no hiring behind.
 */
public class RaidHelperCompatTests implements FabricGameTest {
	static final BlockPos HALL = new BlockPos(2, 2, 2);
	static final BlockPos SMOKER = new BlockPos(6, 2, 2);
	static final BlockPos CHEST = new BlockPos(7, 2, 2);
	static final BlockPos POST = new BlockPos(3, 2, 5);
	static final BlockPos DEN = new BlockPos(14, 2, 14);
	static final BlockPos[] POSTS = {new BlockPos(2, 2, 8), new BlockPos(4, 2, 8), new BlockPos(6, 2, 8), new BlockPos(8, 2, 8)};
	static final long PRICE = (long) RaidHelpers.PRICE * 100;

	/**
	 * What earlier batches left round this spot, taken away: villagers that wandered out of their area (a trainer there
	 * would be hired instead of this test's), mock players, Village Halls, raid dens and the village stores of other
	 * halls. Only for tests alone in their batch.
	 */
	static void clearAround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.world.phys.AABB around = helper.getBounds().inflate(48);
		for (net.minecraft.world.entity.Entity e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, around,
				e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
			e.discard();
		}
		var list = level.getServer().getPlayerList();
		for (ServerPlayer p : List.copyOf(level.players())) {
			if (around.contains(p.position())) {
				list.remove(p);
			}
		}
		BlockPos center = BlockPos.containing(helper.getBounds().getCenter());
		for (RaidDens.Den den : RaidDens.near(level, center)) {
			level.setBlockAndUpdate(den.pos(), Blocks.AIR.defaultBlockState());
		}
		RaidDens.near(level, center);
		int reach = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS + 32;
		for (BlockPos hall : level.getPoiManager().findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), p -> true, center, reach,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(hall, false);
		}
		// The village store is the chests by the kitchens and Storehouses within a hall's reach: an earlier batch's camp
		// kitchen, built outside this smaller area, became this village's store (after the 37.5/37.6 merge reordered the batches).
		net.minecraft.world.phys.AABB own = helper.getBounds();
		for (BlockPos store : level.getPoiManager().findAll(h -> h.is(ModVillagers.KITCHEN_STOVE_POI) || h.is(ModVillagers.STOREHOUSE_POI)
				|| h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.BUTCHER), p -> !own.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), center, reach,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(store, false);
		}
		RaidHelpers.Data data = RaidHelpers.Data.get(level);
		data.hires().clear();
	}

	/** Sets the time of day and the sky's darkness with it (isNight reads the darkness, updated once a tick). */
	static void time(ServerLevel level, long time) {
		level.setDayTime(time);
		level.updateSkyBrightness();
	}

	/** A hall and a known active den; returns {hall, den}. */
	static BlockPos[] village(GameTestHelper helper) {
		clearAround(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(DEN, RaidDenCompatTests.den("tier_three", "fire", true));
		BlockPos den = helper.absolutePos(DEN);
		RaidDens.chunkLoaded(level, level.getChunkAt(den));
		RaidDens.tick(level);
		helper.assertTrue(RaidDenCompatTests.listed(helper, helper.absolutePos(HALL), den).isPresent(), "the den isn't known");
		return new BlockPos[] {helper.absolutePos(HALL), den};
	}

	/** A village trainer of {@code rank} (1 Novice to 5 Master) at its own Training Post. */
	static Villager trainer(GameTestHelper helper, int index, int rank) {
		helper.setBlock(POSTS[index], ModBlocks.TRAINING_POST);
		Villager villager = helper.spawn(EntityType.VILLAGER, POSTS[index].offset(0, 0, -2));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POSTS[index]), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		villager.setVillagerData(villager.getVillagerData().setLevel(rank));
		return villager;
	}

	/** A Master trainer (kept for the tests that need one). */
	static Villager master(GameTestHelper helper) {
		return trainer(helper, 0, 5);
	}

	/** A mock player at {@code pos} with {@code dollars} CobbleDollars and nothing else. */
	static ServerPlayer player(GameTestHelper helper, BlockPos pos, long dollars) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().instabuild = false; // a mock player is in creative, and creative players pay nothing
		player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		CobbleDollarsBank.take(player, CobbleDollarsBank.balance(player));
		CobbleDollarsBank.add(player, dollars);
		return player;
	}

	static void cleanUp(GameTestHelper helper, BlockPos den, ServerPlayer... players) {
		ServerLevel level = helper.getLevel();
		RaidHelpers.Data.get(level).hires().clear();
		RaidHelpers.Data.get(level).setDirty();
		for (ServerPlayer p : players) {
			level.getServer().getPlayerList().remove(p);
		}
		level.setBlockAndUpdate(den, Blocks.AIR.defaultBlockState());
		RaidDens.near(level, helper.absolutePos(HALL));
	}

	static RaidHelpers.Hire hire(GameTestHelper helper, ServerPlayer player) {
		return RaidHelpers.hireOf(helper.getLevel().getServer(), player.getUUID()).orElseThrow(() -> new AssertionError("no hiring"));
	}

	/** Raid Dens awards its statistic, as on a raid joined, hosted or won (the stat registry holds the mod's own id instance). */
	static void stat(ServerPlayer player, String path) {
		ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.CUSTOM_STAT.get(ResourceLocation.fromNamespaceAndPath("cobblemonraiddens", path));
		if (id == null) {
			throw new AssertionError("Raid Dens has no statistic " + path);
		}
		player.awardStat(id);
	}

	static int cheers(ServerPlayer player) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if (id.startsWith("cobblemonraiddens:cheer_")) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Hiring takes the price and the best trainer first; the helper then keeps up with the player. */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '800' '"raidHelpersHire"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 800, batch = "raidHelpersHire")
	public void hiringChargesAndTheHelperFollows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		time(level, 1000);
		BlockPos[] at = village(helper);
		Villager journeyman = trainer(helper, 1, 3);
		Villager master = trainer(helper, 0, 5);
		ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE * 2);
		String said = RaidHelpers.hire(level, at[0], at[1], player).getString();
		helper.assertTrue(said.matches(".+ will help you raid the tier 3 Fire raid den \\(.+\\): 1 of 3 hired\\. Lead the way!"), "hired: " + said);
		helper.assertTrue(CobbleDollarsBank.balance(player) == PRICE, "left after one helper: " + CobbleDollarsBank.balance(player));
		helper.assertTrue(hire(helper, player).helpers.equals(List.of(master.getUUID())), "the Master should be hired first");
		RaidHelpers.hire(level, at[0], at[1], player);
		helper.assertTrue(hire(helper, player).helpers.equals(List.of(master.getUUID(), journeyman.getUUID())) && CobbleDollarsBank.balance(player) == 0,
			"the second helper: " + hire(helper, player).helpers);
		helper.assertTrue(RaidHelpers.busy(master) && RaidHelpers.free(level, at[0]).isEmpty(), "the hired are still free");
		String none = RaidHelpers.hire(level, at[0], at[1], player).getString();
		helper.assertTrue(none.equals("No trainer of the village is free to help"), "with no trainer left: " + none);
		BlockPos away = helper.absolutePos(new BlockPos(12, 2, 4));
		player.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(master.distanceTo(player) <= 4.5 && journeyman.distanceTo(player) <= 4.5,
				"the helpers are " + Math.round(master.distanceTo(player)) + " and " + Math.round(journeyman.distanceTo(player)) + " blocks from the player"))
			.thenExecute(() -> {
				cleanUp(helper, at[1], player);
				time(level, time);
			})
			.thenSucceed();
	}

	/** No money: refused with the price, nothing taken, nobody hired. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersNoMoney"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersNoMoney")
	public void noMoneyIsRefused(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper);
		master(helper);
		ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE - 1);
		String said = RaidHelpers.hire(level, at[0], at[1], player).getString();
		helper.assertTrue(said.equals("A raid helper costs " + PRICE + " CobbleDollars"), "refused: " + said);
		helper.assertTrue(CobbleDollarsBank.balance(player) == PRICE - 1, "money taken: " + CobbleDollarsBank.balance(player));
		helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty(), "hired without paying");
		cleanUp(helper, at[1], player);
		helper.succeed();
	}

	/** At most three helpers, and only for an active den; the fourth is refused and costs nothing. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersCap"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersCap")
	public void theCapIsRespected(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper);
		for (int i = 0; i < 4; i++) {
			trainer(helper, i, 2 + i);
		}
		ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE * 10);
		for (int i = 0; i < RaidHelpers.CAP; i++) {
			RaidHelpers.hire(level, at[0], at[1], player);
		}
		String said = RaidHelpers.hire(level, at[0], at[1], player).getString();
		helper.assertTrue(said.equals("You already have 3 raid helpers"), "the fourth: " + said);
		helper.assertTrue(hire(helper, player).helpers.size() == 3 && CobbleDollarsBank.balance(player) == PRICE * 7,
			"hired " + hire(helper, player).helpers.size() + ", left " + CobbleDollarsBank.balance(player));
		helper.assertTrue(RaidHelpers.free(level, at[0]).size() == 1, "one trainer should be left free");
		// a resting den takes no helpers
		ServerPlayer other = player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE * 2);
		level.setBlockAndUpdate(at[1], RaidDenCompatTests.den("tier_three", "fire", false));
		RaidDens.near(level, at[0]);
		String resting = RaidHelpers.hire(level, at[0], at[1], other).getString();
		helper.assertTrue(resting.equals("That den is resting: hire helpers when it glows again") && CobbleDollarsBank.balance(other) == PRICE * 2,
			"a resting den: " + resting);
		cleanUp(helper, at[1], player, other);
		helper.succeed();
	}

	/** A raid joined by the den (Raid Dens' own statistic): each helper cheers once, never twice in that raid; far off, nothing. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersCheer"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersCheer")
	public void eachHelperCheersOnceARaid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper);
		trainer(helper, 0, 5);
		trainer(helper, 1, 4);
		ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE * 2);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.hire(level, at[0], at[1], player);
			player.teleportTo(at[1].getX() + 40.5, at[1].getY(), at[1].getZ() + 0.5);
			stat(player, RaidHelpers.JOINED);
			helper.assertTrue(cheers(player) == 0 && !hire(helper, player).joined, "a raid joined 40 blocks off the den: " + cheers(player) + " cheers");
			player.teleportTo(at[1].getX() + 3.5, at[1].getY(), at[1].getZ() + 0.5);
			stat(player, RaidHelpers.JOINED);
			helper.assertTrue(cheers(player) == 2 && hire(helper, player).joined, "cheers for a raid with two helpers: " + cheers(player));
			stat(player, RaidHelpers.JOINED);
			stat(player, RaidHelpers.HOSTED);
			helper.assertTrue(cheers(player) == 2, "more than one cheer a helper in a raid: " + cheers(player));
			String said = RaidHelpers.hire(level, at[0], at[1], player).getString();
			helper.assertTrue(said.equals("Your raid is under way: no more helpers can join it"), "hiring mid-raid: " + said);
		} finally {
			cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** A won raid: the chronicle tells it, the helpers get their XP and walk home to their posts. */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '1200' '"raidHelpersWon"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 1200, batch = "raidHelpersWon")
	public void aWonRaidIsWrittenAndTheHelpersGoHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		time(level, 1000);
		BlockPos[] at = village(helper);
		Villager master = trainer(helper, 0, 5);
		ServerPlayer player = player(helper, at[1].offset(3, 0, 0), PRICE);
		RaidHelpers.hire(level, at[0], at[1], player);
		stat(player, RaidHelpers.JOINED);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(at[0]);
		int before = hall.chronicle().size();
		int xp = master.getVillagerXp();
		stat(player, "tier_three_completed");
		helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && RaidHelpers.homeward(level, master.getUUID()),
			"the hiring should be over and the helper on the way home");
		helper.assertTrue(hall.chronicle().size() == before + 1, "chronicle lines: " + (hall.chronicle().size() - before));
		Chronicle.Entry line = hall.chronicle().get(hall.chronicle().size() - 1);
		helper.assertTrue(line.text().getString().matches(".+ and .+'s band beat the tier 3 Fire raid den"), "the line: " + line.text().getString());
		helper.assertTrue(master.getVillagerXp() > xp, "no battle XP for the helper");
		BlockPos post = helper.absolutePos(POSTS[0]);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!RaidHelpers.homeward(level, master.getUUID()),
				"the helper is " + Math.round(Math.sqrt(master.blockPosition().distSqr(post))) + " blocks from home"))
			.thenExecute(() -> {
				helper.assertTrue(master.blockPosition().distSqr(post) <= 25, "home is the post: " + master.blockPosition() + " vs " + post);
				cleanUp(helper, at[1], player);
				time(level, time);
			})
			.thenSucceed();
	}

	/** A day after the hiring with no raid won, the helpers head home. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersDay"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersDay")
	public void aDayLaterTheHelpersGoHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper);
		Villager master = master(helper);
		ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE);
		RaidHelpers.hire(level, at[0], at[1], player);
		RaidHelpers.Hire hire = hire(helper, player);
		helper.assertTrue(hire.until == level.getGameTime() + RaidHelpers.STAY, "hired until " + hire.until);
		RaidHelpers.tick(level, hire);
		helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isPresent(), "gone before the day was up");
		hire.until = level.getGameTime();
		RaidHelpers.tick(level, hire);
		helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && RaidHelpers.homeward(level, master.getUUID()),
			"a day later the helper should head home");
		cleanUp(helper, at[1], player);
		helper.succeed();
	}

	/** A raid the reflection reached: blows by rank and the den's tier, never under a quarter; a weak helper only cheers. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersStrike"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersStrike")
	public void theHelpersWearTheBossDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(RaidAssist.EXTENSION.present(), "Raid Dens 0.11.3's raids weren't reached by reflection");
		BlockPos[] at = village(helper);
		Villager master = trainer(helper, 0, 5);
		Villager novice = trainer(helper, 1, 1);
		ServerPlayer player = player(helper, at[1].offset(3, 0, 0), PRICE * 2);
		try {
			helper.assertTrue(RaidAssist.EXTENSION.call(a -> a.raidOf(player), java.util.Optional.<RaidAssist.Raid>empty()).isEmpty(),
				"a player in no raid was found in one");
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.hire(level, at[0], at[1], player);
			helper.assertTrue(RaidHelpers.strength(novice, 6) == 0f && RaidHelpers.strength(novice, 2) > 0f, "a Novice against tier 6 / 2");
			float blow = RaidHelpers.strength(master, 3) + RaidHelpers.strength(novice, 3);
			helper.assertTrue(blow > 0f && RaidHelpers.strength(master, 3) > RaidHelpers.strength(novice, 3), "the Master should hit harder");
			FakeRaid raid = new FakeRaid(400f);
			float dealt = RaidHelpers.strike(level, hire(helper, player), raid);
			helper.assertTrue(Math.abs(dealt - blow) < 1.0e-5f && Math.abs(raid.health - 400f * (1 - blow)) < 0.01f,
				"one strike took " + dealt + " (expected " + blow + "), health " + raid.health);
			for (int i = 0; i < 500; i++) {
				RaidHelpers.strike(level, hire(helper, player), raid);
			}
			helper.assertTrue(Math.abs(raid.health - 100f) < 0.01f, "the helpers took the boss to " + raid.health + " (never under 100 of 400)");
			raid.state = RaidAssist.State.WON;
			helper.assertTrue(RaidHelpers.strike(level, hire(helper, player), raid) == 0f, "a blow after the raid ended");
		} finally {
			cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	static final class FakeRaid implements RaidAssist.Raid {
		final UUID id = UUID.randomUUID();
		final float max;
		float health;
		RaidAssist.State state = RaidAssist.State.RUNNING;

		FakeRaid(float max) {
			this.max = max;
			this.health = max;
		}

		@Override
		public UUID id() {
			return id;
		}

		@Override
		public float health() {
			return health;
		}

		@Override
		public float maxHealth() {
			return max;
		}

		@Override
		public RaidAssist.State state() {
			return state;
		}

		@Override
		public void hurt(float fraction) {
			health = Math.min(max, health - fraction * max);
		}
	}

	/** The Defence page offers the hiring by an active den, in plain words; switched off, no button, no hiring, no cheers. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersSwitch"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersSwitch")
	public void theButtonAndTheSwitch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper);
		master(helper);
		ServerPlayer player = player(helper, at[1].offset(3, 0, 0), PRICE);
		boolean was = RaidHelpers.ENABLED;
		try {
			ChoiceMenu menu = VillageHallScreen.forTest(player, at[0]);
			DefencePage.render(menu, level, at[0]);
			List<String> lines = new ArrayList<>();
			for (int slot : DefencePage.HELPERS) {
				lines.addAll(RaidDenCompatTests.lines(menu.icon(slot)));
			}
			helper.assertTrue(lines.contains("Hire a raid helper") && lines.contains(PRICE + " CobbleDollars a helper")
				&& lines.contains("Hired: 0 of 3 (1 free in the village)"), "the Defence page: " + lines);
			for (String l : lines) {
				helper.assertFalse(l.contains("aliveworkplace.") || l.contains("%"), "a raw line: " + l);
			}
			RaidHelpers.ENABLED = false;
			ChoiceMenu off = VillageHallScreen.forTest(player, at[0]);
			DefencePage.render(off, level, at[0]);
			for (int slot : DefencePage.HELPERS) {
				helper.assertTrue(off.icon(slot).isEmpty() || !RaidDenCompatTests.lines(off.icon(slot)).contains("Hire a raid helper"), "a button while off");
			}
			String said = RaidHelpers.hire(level, at[0], at[1], player).getString();
			helper.assertTrue(said.equals("Raid helpers are switched off") && CobbleDollarsBank.balance(player) == PRICE, "hired while off: " + said);
			stat(player, RaidHelpers.JOINED);
			helper.assertTrue(cheers(player) == 0, "a cheer while off");
		} finally {
			RaidHelpers.ENABLED = was;
			cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** Hirings survive a save; an old world's den-warden and den-challenge data on disk doesn't get in the way. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersSave"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersSave")
	public void hiringsSaveAndOldDataLoads(GameTestHelper helper) throws java.io.IOException {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper);
		Villager master = master(helper);
		// the 38.3 and 38.4 files as an old world has them
		java.nio.file.Path data = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
			level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data");
		java.nio.file.Files.createDirectories(data);
		CompoundTag warden = new CompoundTag();
		warden.putUUID("guard", master.getUUID());
		warden.putLong("den", at[1].asLong());
		ListTag wardens = new ListTag();
		wardens.add(warden);
		CompoundTag oldWardens = new CompoundTag();
		oldWardens.put("wardens", wardens);
		CompoundTag oldChallenges = new CompoundTag();
		oldChallenges.put("halls", new ListTag());
		oldChallenges.put("tired", new ListTag());
		for (var e : java.util.Map.of("aliveworkplace_den_wardens", oldWardens, "aliveworkplace_den_challenges", oldChallenges).entrySet()) {
			CompoundTag file = new CompoundTag();
			file.put("data", e.getValue());
			file.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
			NbtIo.writeCompressed(file, data.resolve(e.getKey() + ".dat"));
		}
		ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), PRICE);
		try {
			String said = RaidHelpers.hire(level, at[0], at[1], player).getString();
			helper.assertTrue(said.contains("will help you raid"), "a trainer named in the old warden file: " + said);
			RaidHelpers.Hire hire = hire(helper, player);
			hire.joined = true;
			hire.raid = UUID.randomUUID();
			CompoundTag saved = RaidHelpers.Data.get(level).save(new CompoundTag(), level.registryAccess());
			CompoundTag again = RaidHelpers.Data.load(saved.copy(), level.registryAccess()).save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(again.equals(saved) && saved.toString().contains(master.getUUID().toString()), "read back: " + again + " saved: " + saved);
			helper.assertTrue(RaidHelpers.Data.load(new CompoundTag(), level.registryAccess()).hires().isEmpty(), "a save from before 38.1a");
			helper.assertTrue(RaidHelpers.Data.load(oldWardens, level.registryAccess()).hires().isEmpty(), "the old wardens' data read as hirings");
		} finally {
			cleanUp(helper, at[1], player);
			java.nio.file.Files.deleteIfExists(data.resolve("aliveworkplace_den_wardens.dat"));
			java.nio.file.Files.deleteIfExists(data.resolve("aliveworkplace_den_challenges.dat"));
		}
		helper.succeed();
	}

	/** A raid Raid Dens no longer runs (gone from its list): the helpers' next look ends the hiring and they walk home. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersGone"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersGone")
	public void aRaidNoLongerRunningSendsTheHelpersHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(RaidAssist.EXTENSION.present(), "Raid Dens 0.11.3's raids weren't reached by reflection");
		BlockPos[] at = village(helper);
		Villager master = master(helper);
		ServerPlayer player = player(helper, at[1].offset(3, 0, 0), PRICE);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			UUID gone = UUID.randomUUID();
			helper.assertTrue(RaidAssist.EXTENSION.call(a -> a.raid(level.getServer(), gone), java.util.Optional.<RaidAssist.Raid>empty()).isEmpty(),
				"a raid Raid Dens doesn't list was found");
			RaidHelpers.Hire hire = hire(helper, player);
			hire.joined = true;
			hire.raid = gone;
			hire.nextStrike = level.getGameTime();
			RaidHelpers.tick(level, hire);
			helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).isEmpty() && RaidHelpers.homeward(level, master.getUUID()),
				"the hiring for a raid that's over goes on");
		} finally {
			cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** In the raid, a helper at its place by the den stands there: a walk it was on is dropped. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersStand"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersStand")
	public void aHelperAtItsPlaceByTheDenStandsStill(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper);
		Villager master = master(helper);
		ServerPlayer player = player(helper, at[1].offset(3, 0, 0), PRICE);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			RaidHelpers.Hire hire = hire(helper, player);
			hire.joined = true;
			BlockPos spot = at[1].offset(3, 0, 0); // (the first helper's place: three blocks east of the crystal)
			master.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			master.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(helper.absolutePos(POSTS[0]), 0.6f, 1));
			RaidHelpers.tick(level, hire);
			helper.assertFalse(master.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "the helper at its place by the den walks off");
		} finally {
			cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}

	/** A raid joined right at the edge of the den's reach ({@link RaidHelpers#NEAR} blocks from it) is the den's raid. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidHelpersEdge"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidHelpersEdge")
	public void aRaidJoinedAtTheEdgeOfTheDensReachCounts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper);
		master(helper);
		ServerPlayer player = player(helper, at[1].offset(3, 0, 0), PRICE);
		try {
			RaidHelpers.hire(level, at[0], at[1], player);
			player.teleportTo(at[1].getX() + 0.5 + RaidHelpers.NEAR, at[1].getY() + 0.5, at[1].getZ() + 0.5);
			stat(player, RaidHelpers.JOINED);
			helper.assertTrue(hire(helper, player).joined && cheers(player) == 1,
				"a raid joined " + player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(at[1])) + " blocks from the den: " + cheers(player) + " cheers");
		} finally {
			cleanUp(helper, at[1], player);
		}
		helper.succeed();
	}
}
