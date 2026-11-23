package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bee.BeekeeperWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA lane C (lane-c-1123-1255), B1533, B1534 and B1541 from their specs, at the edges the earlier tests leave open.
 * README, Beekeepers: "Put a lit campfire under a hive and the bees stay calm; otherwise they fly out when it's
 * harvested" (B1533: as with a player's bottling, so vanilla's smoke: a soul campfire smokes too, and smoke rises through
 * air from a campfire a few blocks down); "feed pairs of bees flowers to breed until there are three bees a hive" (B1534:
 * a bee of the hive counts wherever it is). random-jobs.md: with randomStationJobs on, a shared block's Shift tooltip
 * names all the jobs a villager may draw there; off, the vanilla job alone (B1541), in American English.
 */
public class QaBeeTooltip1123c2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos APIARY = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Villager beekeeper(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(APIARY, ModBlocks.APIARY);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(APIARY), ModVillagers.APIARY_POI, ModVillagers.BEEKEEPER);
		helper.assertTrue(BeekeeperWork.isBeekeeper(villager), "setup: not a beekeeper");
		return villager;
	}

	private static void clearBees(GameTestHelper helper) {
		for (Bee b : helper.getLevel().getEntitiesOfClass(Bee.class, helper.getBounds().inflate(64))) {
			b.discard();
		}
	}

	/**
	 * A full hive at {@code hive} with two settled bees and {@code fire} placed {@code below} blocks under it (air between),
	 * harvested by the Beekeeper: on the harvest tick, {@code stay} of them must be inside.
	 */
	private static void harvest(GameTestHelper helper, BlockPos hive, BlockState fire, int below, int stay, String what) {
		Leftovers.clear(helper);
		helper.setBlock(hive.below(below), fire);
		helper.setBlock(hive, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
		BeehiveBlockEntity be = helper.getBlockEntity(hive);
		for (int i = 0; i < 2; i++) {
			Bee bee = helper.spawn(EntityType.BEE, hive.offset(i, 2, 1));
			bee.setHivePos(helper.absolutePos(hive));
			BeehiveBlockEntity.Occupant o = BeehiveBlockEntity.Occupant.of(bee);
			be.storeBee(new BeehiveBlockEntity.Occupant(o.entityData(), 0, 100000));
			bee.discard();
		}
		helper.assertTrue(be.getOccupantCount() == 2, "setup: " + be.getOccupantCount() + " bees in the hive");
		beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		AtomicLong harvested = new AtomicLong(-1);
		AtomicInteger inside = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			BlockState s = helper.getBlockState(hive);
			if (harvested.get() < 0 && s.is(Blocks.BEEHIVE) && s.getValue(BeehiveBlock.HONEY_LEVEL) == 0) {
				harvested.set(helper.getTick());
				inside.set(((BeehiveBlockEntity) helper.getBlockEntity(hive)).getOccupantCount());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(harvested.get() >= 0, what + ": the hive was never harvested");
			helper.assertTrue(inside.get() == stay, what + ": " + inside.get() + " of 2 bees in the hive on the harvest tick, expected " + stay);
			clearBees(helper);
		});
	}

	/** A lit soul campfire under the hive smokes it as a campfire does: both bees stay in. */
	//$ gametest_ticks_batch AREA '1800' '"qaBee1123c2Soul"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaBee1123c2Soul")
	public void aLitSoulCampfireKeepsTheBeesIn(GameTestHelper helper) {
		harvest(helper, new BlockPos(10, 3, 10), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 1, 2, "lit soul campfire under the hive");
	}

	/** A lit campfire two blocks under the hive, air between: the smoke rises to it (vanilla) and both bees stay in. */
	//$ gametest_ticks_batch AREA '1800' '"qaBee1123c2TwoDown"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaBee1123c2TwoDown")
	public void aLitCampfireTwoBlocksDownKeepsTheBeesIn(GameTestHelper helper) {
		harvest(helper, new BlockPos(10, 4, 10), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 2, 2, "lit campfire two blocks under the hive");
	}

	/** An unlit soul campfire doesn't smoke: both bees come out. */
	//$ gametest_ticks_batch AREA '1800' '"qaBee1123c2SoulUnlit"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaBee1123c2SoulUnlit")
	public void anUnlitSoulCampfireLetsTheBeesOut(GameTestHelper helper) {
		harvest(helper, new BlockPos(10, 3, 10), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 1, 0, "unlit soul campfire under the hive");
	}

	/**
	 * B1534 without chance: a flowered hive over a lit campfire, three adult bees whose home it is: two hover by the
	 * Apiary (AI off), the third held 40 blocks away (out foraging, AI off). That's three bees a hive: in 600 ticks he
	 * feeds none and no bee falls in love.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaBee1123c2Forager"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBee1123c2Forager")
	public void aBeeForaging40BlocksOutStillCountsAsTheThird(GameTestHelper helper) {
		threeBees(helper, new BlockPos(10, 6, 50), "the third bee 40 blocks out");
	}

	/** The same with the third bee 20 blocks out, just past the Apiary's 16. */
	//$ gametest_ticks_batch AREA '800' '"qaBee1123c2Forager20"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBee1123c2Forager20")
	public void aBeeForaging20BlocksOutStillCountsAsTheThird(GameTestHelper helper) {
		threeBees(helper, new BlockPos(10, 6, 30), "the third bee 20 blocks out");
	}

	private static void threeBees(GameTestHelper helper, BlockPos far, String what) {
		Leftovers.clear(helper);
		BlockPos hive = new BlockPos(10, 3, 10);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(4, 1, 4), new BlockPos(16, 1, 16))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		helper.setBlock(hive.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.setBlock(hive, Blocks.BEEHIVE);
		for (BlockPos p : List.of(hive.offset(-2, -1, 0), hive.offset(2, -1, 0), hive.offset(0, -1, 2), hive.offset(0, -1, -2))) {
			helper.setBlock(p, Blocks.POPPY);
		}
		List<Bee> mine = new ArrayList<>();
		for (BlockPos at : List.of(new BlockPos(6, 4, 6), new BlockPos(8, 4, 6), far)) {
			Bee bee = helper.spawn(EntityType.BEE, at);
			bee.setHivePos(helper.absolutePos(hive));
			bee.setPersistenceRequired();
			bee.setNoAi(true);
			bee.setNoGravity(true);
			mine.add(bee);
		}
		beekeeper(helper, new ItemStack(Items.DANDELION, 8));
		Container chest = helper.getBlockEntity(CHEST);
		AtomicInteger loved = new AtomicInteger();
		helper.onEachTick(() -> {
			for (Bee b : mine) {
				loved.addAndGet(b.isInLove() ? 1 : 0);
			}
		});
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(mine.get(2).isAlive() && mine.get(2).distanceToSqr(helper.absoluteVec(far.getCenter())) < 4, "setup: the far bee moved");
			helper.assertTrue(chest.countItem(Items.DANDELION) == 8 && loved.get() == 0, what + ": three bees a hive were fed to breed: "
				+ (8 - chest.countItem(Items.DANDELION)) + " dandelions taken, in love " + loved.get() + " bee-ticks");
			clearBees(helper);
			helper.succeed();
		});
	}

	private static List<String> lines(Block block) {
		return Stations.tooltip(new ItemStack(block.asItem()), true).stream().map(Component::getString).collect(Collectors.toList());
	}

	private static String articled(String name) {
		return ("AEIOU".indexOf(Character.toUpperCase(name.charAt(0))) >= 0 ? "an " : "a ") + name;
	}

	private static final String BY_ITSELF = "A villager without a job takes it as ";
	/** British spellings the owner asked never to show (CLAUDE.md, American English). */
	private static final List<String> BRITISH = List.of("armour", "jeweller", "colour", "grey", "defence", "cancelled", "traveller", "favour", "centre");

	/**
	 * Every shared station a jobless villager takes by himself: with randomStationJobs on, its one "takes it as" line
	 * names each of its jobs in this game with "a"/"an" right, ends "by chance"; off, it names the vanilla (first) job
	 * alone. Every line in American English, no raw translation key.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everySharedStationsTooltipNamesItsJobsOnlyWithTheSwitchOn(GameTestHelper helper) {
		boolean was = RandomJobs.ENABLED;
		List<String> problems = new ArrayList<>();
		int shared = 0;
		try {
			for (Stations.Station station : Stations.ALL) {
				Block block = station.block();
				if (block == Blocks.AIR || !station.byItself()) {
					continue;
				}
				List<Stations.Job> jobs = station.jobs().stream().filter(Stations::available).toList();
				if (jobs.isEmpty() || jobs.get(0) != station.jobs().get(0)) {
					continue;
				}
				List<String> names = jobs.stream().filter(j -> j == jobs.get(0) || j.profession().get() != ModVillagers.TRAINER_LEADER)
					.map(j -> Stations.name(j.profession().get()).getString()).toList();
				String name = block.getName().getString();
				RandomJobs.ENABLED = false;
				List<String> off = lines(block);
				RandomJobs.ENABLED = true;
				List<String> on = lines(block);
				List<String> offLine = off.stream().filter(l -> l.startsWith(BY_ITSELF)).toList();
				List<String> onLine = on.stream().filter(l -> l.startsWith(BY_ITSELF)).toList();
				if (!offLine.equals(List.of(BY_ITSELF + articled(names.get(0))))) {
					problems.add(name + " (off): " + off);
				}
				if (onLine.size() != 1) {
					problems.add(name + " (on): no single 'takes it as' line: " + on);
				} else if (names.size() > 1) {
					shared++;
					String l = onLine.get(0);
					if (!l.endsWith(", by chance")) {
						problems.add(name + " (on): doesn't end ', by chance': " + l);
					}
					for (String n : names) {
						if (!l.contains(articled(n))) {
							problems.add(name + " (on): doesn't name " + articled(n) + ": " + l);
						}
					}
					if (!l.contains(" or " + articled(names.get(names.size() - 1)))) {
						problems.add(name + " (on): the last job isn't joined with 'or': " + l);
					}
				} else if (!onLine.get(0).equals(BY_ITSELF + articled(names.get(0)))) {
					problems.add(name + " (on, one job): " + on);
				}
				for (String l : on) {
					String low = l.toLowerCase(Locale.ROOT);
					if (low.contains("tooltip.") || low.contains("aliveworkplace.") || low.contains("%s")) {
						problems.add(name + ": raw text: " + l);
					}
					for (String b : BRITISH) {
						if (low.contains(b)) {
							problems.add(name + ": British spelling '" + b + "': " + l);
						}
					}
				}
			}
		} finally {
			RandomJobs.ENABLED = was;
		}
		helper.assertTrue(shared >= 8, "only " + shared + " shared stations with two jobs or more found");
		helper.assertTrue(problems.isEmpty(), problems.size() + " tooltip problems: " + String.join(" | ", problems));
		helper.succeed();
	}
}
