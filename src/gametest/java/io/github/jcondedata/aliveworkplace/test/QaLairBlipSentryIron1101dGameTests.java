package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallSentries;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * QA (lane D, 2026-11-01), verifying two fixes from their specs.
 *
 * <p>B767: "a chief that Peaceful took away is never counted as fallen", for every kind of lair, not only the bandit
 * camp the bug was found with; and the memory of it mustn't spill onto the next camp, whose chief gone off Peaceful
 * still breaks it up.
 *
 * <p>B755: a Great Wall's lost sentry is paid for (four iron blocks and a pumpkin) only once the golem stands: kept
 * while another mod refuses it, paid exactly once when the refusal is lifted, and paid when forceVillageSpawns forces it.
 */
public class QaLairBlipSentryIron1101dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	private static final long DAY = 24000;
	private static final String LEFT = "packed up and left the village in peace";

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	private static RandomSource always() {
		return new LegacyRandomSource(7L) {
			@Override
			public float nextFloat() {
				return 0f;
			}

			@Override
			public double nextDouble() {
				return 0d;
			}
		};
	}

	/** A hall (village radius 16) with four villagers; config, clock, difficulty and threats put back at the end. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		Difficulty difficulty = level.getDifficulty();
		VillageHalls.RADIUS = 16;
		Lairs.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			level.getServer().setDifficulty(difficulty, true);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Lairs.forget(level);
			BanditCamps.forget(level);
			VillageRaids.forget();
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	private static String last(VillageHallBlockEntity entity) {
		List<Chronicle.Entry> chronicle = entity.chronicle();
		return chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
	}

	/**
	 * B767 for a raider lair (the desert raiders' camp, a husk at its head), not a bandit camp: Peaceful for a moment takes
	 * the captain away and the world is back on Normal before the hall's round. The lair leaves in peace, no rest days.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1101dDesertBlip"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1101dDesertBlip")
	public void aRaiderLairsCaptainPeacefulTookIsNoKillBackOnNormal(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
			helper.assertTrue(lair != null && Lairs.near(level, hall).isPresent(), "no desert lair");
			UUID captain = lair.captain();
			helper.assertTrue(level.getEntity(captain) != null, "the desert lair has no captain standing");
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(level.getEntity(captain) == null, "Peaceful didn't take the captain away; the test proves nothing");
				level.getServer().setDifficulty(Difficulty.NORMAL, true);
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair lingers without its captain");
				helper.assertTrue(last(entity).contains(LEFT) && Lairs.resting(level, hall) == 0,
					"a moment of Peaceful counted the desert captain as fallen: '" + last(entity) + "', resting " + Lairs.resting(level, hall));
				helper.succeed();
			});
		});
	}

	/**
	 * B767's other side, over two camps: the first's chief is taken by a moment of Peaceful (it leaves in peace); a new
	 * camp comes on Normal and its chief goes without dying off Peaceful. That one is broken up as before (rest days):
	 * the first chief's "taken by peace" doesn't carry over.
	 */
	//$ gametest_ticks_batch AREA '80' '"qa1101dBlipThenGone"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1101dBlipThenGone")
	public void afterABlipTheNextCampsChiefGoneOnNormalStillBreaksItUp(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			UUID first = Lairs.near(level, hall).orElseThrow().captain();
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(level.getEntity(first) == null, "Peaceful didn't take the chief away; the test proves nothing");
				level.getServer().setDifficulty(Difficulty.NORMAL, true);
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isEmpty() && last(entity).contains(LEFT) && Lairs.resting(level, hall) == 0,
					"the first camp didn't leave in peace: '" + last(entity) + "', resting " + Lairs.resting(level, hall));
				at(level, 100, 13000);
				helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no second camp on Normal");
				Lairs.Lair second = Lairs.near(level, hall).orElseThrow();
				helper.assertTrue(!second.captain().equals(first), "the second camp has the first one's chief");
				Entity chief = level.getEntity(second.captain());
				helper.assertTrue(chief != null, "the second camp has no chief standing");
				chief.discard();
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a camp without its chief lingers on Normal");
				helper.assertTrue(Lairs.resting(level, hall) > 0 && !last(entity).contains(LEFT),
					"a chief gone off Peaceful after an earlier blip counted as taken by peace: '" + last(entity) + "', resting " + Lairs.resting(level, hall));
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- B755

	/** A finished little Great Wall with one post, its stockpile chest holding {@code iron} iron blocks and {@code pumpkins} carved pumpkins, and a Tinkerer. */
	private static Container wallWithStock(GameTestHelper helper, BlockPos hall, int iron, int pumpkins, WonderSite[] site) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		BlockPos stoneRel = new BlockPos(12, 2, 24);
		helper.setBlock(stoneRel, Blocks.STONE);
		site[0] = QaThreatSpawnsPeace1101GameTests.wall(helper, hall, helper.absolutePos(stoneRel));
		helper.setBlock(new BlockPos(13, 2, 24), Blocks.CHEST);
		Container chest = (Container) level.getBlockEntity(helper.absolutePos(new BlockPos(13, 2, 24)));
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, iron));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, pumpkins));
		Villager tinkerer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 20));
		tinkerer.setNoAi(true);
		tinkerer.setVillagerData(tinkerer.getVillagerData().setType(VillagerType.PLAINS).setProfession(ModVillagers.TINKERER).setLevel(3));
		tinkerer.setCustomName(Component.literal("Tibbs"));
		return chest;
	}

	private static int count(Container chest, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int golems(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(8), Entity::isAlive).size();
	}

	private static long madeLines(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().filter(e -> e.text().getString().contains("made a new sentry")).count();
	}

	/**
	 * B755: refused with forceVillageSpawns off, the sentry is left out and the stockpile (two sentries' worth) keeps all
	 * of it, on two mornings in a row; the other mod stops refusing, and the next replacement makes one sentry and takes
	 * one sentry's worth (four iron blocks, one pumpkin), with one chronicle line; a second call with the post manned
	 * takes nothing more.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1101dWallLifted"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1101dWallLifted")
	public void aRefusedSentryIsPaidForOnceWhenTheRefusalIsLifted(GameTestHelper helper) {
		BlockPos hall = QaThreatSpawnsPeace1101GameTests.wallHall(helper);
		ServerLevel level = helper.getLevel();
		WonderSite[] site = {null};
		Container chest = wallWithStock(helper, hall, 8, 2, site);
		QaThreatSpawnsPeace1101GameTests.refusing(helper, e -> e instanceof IronGolem, true, false);
		for (int morning = 0; morning < 2; morning++) {
			GreatWallSentries.forget();
			helper.assertTrue(GreatWallSentries.replace(level, hall) == 0, "a refused sentry was replaced (morning " + morning + ")");
		}
		helper.assertTrue(count(chest, Items.IRON_BLOCK) == 8 && count(chest, Items.CARVED_PUMPKIN) == 2,
			"refused twice, the stockpile paid: " + count(chest, Items.IRON_BLOCK) + " iron, " + count(chest, Items.CARVED_PUMPKIN) + " pumpkins");
		helper.assertTrue(golems(helper) == 0 && madeLines(level, hall) == 0, "a refused sentry stands or is in the chronicle");
		HostileSpawns.REFUSED_FOR_TESTS = null; // the other mod no longer refuses golems
		GreatWallSentries.forget();
		helper.assertTrue(GreatWallSentries.replace(level, hall) == 1, "no sentry made once nothing refuses it");
		helper.assertTrue(count(chest, Items.IRON_BLOCK) == 4 && count(chest, Items.CARVED_PUMPKIN) == 1,
			"one sentry didn't cost one sentry's worth: " + count(chest, Items.IRON_BLOCK) + " iron, " + count(chest, Items.CARVED_PUMPKIN) + " pumpkins left of 8 and 2");
		helper.assertTrue(golems(helper) == 1 && madeLines(level, hall) == 1, "golems " + golems(helper) + ", chronicle lines " + madeLines(level, hall));
		GreatWallSentries.forget();
		helper.assertTrue(GreatWallSentries.replace(level, hall) == 0, "a manned post was given a second sentry");
		helper.assertTrue(count(chest, Items.IRON_BLOCK) == 4 && count(chest, Items.CARVED_PUMPKIN) == 1 && golems(helper) == 1,
			"a manned post took more iron: " + count(chest, Items.IRON_BLOCK) + " iron, " + count(chest, Items.CARVED_PUMPKIN) + " pumpkins, " + golems(helper) + " golems");
		helper.succeed();
	}

	/**
	 * B755's other side (B211): with forceVillageSpawns on, a sentry another mod refuses is forced in, so it does stand
	 * and the stockpile pays for it, once.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1101dWallForcedPays"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1101dWallForcedPays")
	public void aForcedSentryStandsAndIsPaidFor(GameTestHelper helper) {
		BlockPos hall = QaThreatSpawnsPeace1101GameTests.wallHall(helper);
		ServerLevel level = helper.getLevel();
		WonderSite[] site = {null};
		Container chest = wallWithStock(helper, hall, 4, 1, site);
		QaThreatSpawnsPeace1101GameTests.refusing(helper, e -> e instanceof IronGolem, true, true);
		GreatWallSentries.forget();
		helper.assertTrue(GreatWallSentries.replace(level, hall) == 1, "a forced sentry wasn't made");
		helper.assertTrue(golems(helper) == 1, "the forced sentry isn't standing: " + golems(helper));
		helper.assertTrue(count(chest, Items.IRON_BLOCK) == 0 && count(chest, Items.CARVED_PUMPKIN) == 0,
			"the forced sentry wasn't paid for: " + count(chest, Items.IRON_BLOCK) + " iron, " + count(chest, Items.CARVED_PUMPKIN) + " pumpkins left");
		helper.succeed();
	}
}
