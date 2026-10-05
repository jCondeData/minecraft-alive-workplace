package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * 29.6, Gifted villagers: the UUID roll (about 1 in 30, the same every time, none with {@code giftedChance} 0), and the
 * first four gifts through the code players reach them by: Prodigy's XP (three times, Clever or not), Iron Will in a
 * staged raid with the bell rung and a hurt, Silver Tongue's 20% off a player's offers, a Night Owl builder placing
 * blocks at midnight and asleep at noon, and the gold line on the hall's list.
 */
public class GiftedGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation PRODIGY = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "prodigy");
	private static final ResourceLocation IRON_WILL = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "iron_will");
	private static final ResourceLocation SILVER_TONGUE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "silver_tongue");
	private static final ResourceLocation NIGHT_OWL = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "night_owl");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** About one in 30 of 3,000 fixed UUIDs is Gifted, each gift turns up, and the answer is the same the second time. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void giftedRollIsAboutOneInThirtyAndStable(GameTestHelper helper) {
		helper.assertTrue(Gifted.all().keySet().containsAll(List.of(PRODIGY, IRON_WILL, SILVER_TONGUE, NIGHT_OWL)), "gifts loaded: " + Gifted.all().keySet());
		helper.assertTrue(Gifted.CHANCE == 30, "giftedChance should default to 30, is " + Gifted.CHANCE);
		RandomSource random = RandomSource.create(296L);
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 3000; i++) {
			ids.add(new UUID(random.nextLong(), random.nextLong()));
		}
		List<Gifted.Gift> first = new ArrayList<>();
		java.util.Map<ResourceLocation, Integer> byGift = new java.util.HashMap<>();
		int gifted = 0;
		for (UUID id : ids) {
			Gifted.Gift gift = Gifted.roll(id);
			first.add(gift);
			if (gift != null) {
				gifted++;
				byGift.merge(gift.id(), 1, Integer::sum);
			}
		}
		// Expected 100; a binomial's spread is about 10, so 70-130 is three of them either way.
		helper.assertTrue(gifted >= 70 && gifted <= 130, "expected about 100 of 3000 Gifted, got " + gifted);
		helper.assertTrue(byGift.keySet().containsAll(List.of(PRODIGY, IRON_WILL, SILVER_TONGUE, NIGHT_OWL)), "a gift never came up: " + byGift);
		for (int i = 0; i < ids.size(); i++) {
			helper.assertTrue(Gifted.roll(ids.get(i)) == first.get(i), "UUID " + ids.get(i) + " rolled differently the second time");
		}
		helper.succeed();
	}

	/** With {@code giftedChance} 0 nobody is Gifted: not by the roll and not by the attachment (which is kept). */
	//$ gametest_batch AREA '"giftedChanceZero"'
	@GameTest(template = AREA, batch = "giftedChanceZero")
	public void giftedChanceZeroMeansNobody(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Gifted.set(villager, PRODIGY);
		helper.assertTrue(Gifted.of(villager) != null, "the attachment didn't give the gift");
		int before = Gifted.CHANCE;
		boolean roll = Gifted.ROLL;
		try {
			Gifted.CHANCE = 0;
			Gifted.ROLL = true;
			RandomSource random = RandomSource.create(7L);
			for (int i = 0; i < 3000; i++) {
				helper.assertTrue(Gifted.roll(new UUID(random.nextLong(), random.nextLong())) == null, "someone is Gifted with giftedChance 0");
			}
			helper.assertTrue(Gifted.of(villager) == null, "the attachment still gives a gift with giftedChance 0");
			helper.assertTrue(Gifted.xpFactor(villager) == 1f, "a Prodigy still learns faster with giftedChance 0");
			helper.assertTrue(PRODIGY.toString().equals(ModAttachments.GIFTED.get(villager)), "the gift was erased");
		} finally {
			Gifted.CHANCE = before;
			Gifted.ROLL = roll;
		}
		helper.succeed();
	}

	/** A Prodigy's work earns three times the XP; Clever on top is still three times. */
	//$ gametest_batch AREA '"giftedProdigy"'
	@GameTest(template = AREA, batch = "giftedProdigy")
	public void prodigyLearnsThreeTimesAsFast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager prodigy = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		Gifted.set(prodigy, PRODIGY);
		Gifted.set(plain, null);
		BuilderLevels.addXp(level, prodigy, 2, null);
		BuilderLevels.addXp(level, plain, 2, null);
		helper.assertTrue(prodigy.getVillagerXp() == 6, "a Prodigy got " + prodigy.getVillagerXp() + " XP for 2");
		helper.assertTrue(plain.getVillagerXp() == 2, "an ordinary villager got " + plain.getVillagerXp() + " XP for 2");
		helper.assertTrue(Gifted.hallLine(prodigy) != null && Gifted.hallLine(plain) == null, "the hall's gold gift line");
		// Clever as well (traits are off in tests, so on for this check only): still three times, not 3.75.
		UUID clever = null;
		for (long i = 1; clever == null; i++) {
			UUID id = new UUID(i * 0x9E3779B97F4A7C15L, i);
			if (Traits.of(id).contains(Traits.Trait.CLEVER)) {
				clever = id;
			}
		}
		Villager both = EntityType.VILLAGER.create(level);
		both.setUUID(clever);
		ModAttachments.GIFTED.set(both, PRODIGY.toString());
		Villager cleverOnly = EntityType.VILLAGER.create(level);
		cleverOnly.setUUID(clever);
		boolean traits = Traits.ENABLED;
		try {
			Traits.ENABLED = true;
			for (int i = 0; i < 20; i++) {
				int got = Traits.xp(both, 4, RandomSource.create(i));
				helper.assertTrue(got == 12, "a clever Prodigy got " + got + " XP for 4");
				int c = Traits.xp(cleverOnly, 4, RandomSource.create(i));
				helper.assertTrue(c == 5, "a clever villager got " + c + " XP for 4");
			}
		} finally {
			Traits.ENABLED = traits;
		}
		both.discard();
		cleverOnly.discard();
		helper.succeed();
	}

	/** Every offer of a Silver Tongue is 20% cheaper for a player (10 emeralds: 8); an ordinary villager's isn't. */
	//$ gametest_batch AREA '"giftedSilverTongue"'
	@GameTest(template = AREA, batch = "giftedSilverTongue")
	public void silverTongueTradesTwentyPercentCheaper(GameTestHelper helper) {
		Villager silver = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Villager plain = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		Gifted.set(silver, SILVER_TONGUE);
		Gifted.set(plain, null);
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		for (Villager v : List.of(silver, plain)) {
			v.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.BREAD), 12, 1, 0.05f));
			v.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE), 12, 1, 0.05f));
			((VillagerAccessor) v).aliveworkplace$updateSpecialPrices(player);
		}
		int silverBread = silver.getOffers().get(silver.getOffers().size() - 2).getCostA().getCount();
		int silverApple = silver.getOffers().get(silver.getOffers().size() - 1).getCostA().getCount();
		int plainBread = plain.getOffers().get(plain.getOffers().size() - 2).getCostA().getCount();
		helper.assertTrue(silverBread == 8, "a Silver Tongue's 10-emerald offer costs " + silverBread);
		helper.assertTrue(silverApple == 1, "a 1-emerald offer can't go below 1, costs " + silverApple);
		helper.assertTrue(plainBread == 10, "an ordinary villager's 10-emerald offer costs " + plainBread);
		helper.succeed();
	}

	/**
	 * A staged raid on a village (a claimed bell), the bell rung and both villagers hurt: the ordinary one goes into the
	 * raid, hides or panics; the Iron-Willed one never leaves their routine.
	 */
	//$ gametest_ticks_batch AREA '400' '"giftedIronWill"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "giftedIronWill")
	public void ironWillNeverPanicsInARaid(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(6000);
		BlockPos bell = new BlockPos(8, 2, 8);
		helper.setBlock(bell, Blocks.BELL);
		Villager iron = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Gifted.set(iron, IRON_WILL);
		Gifted.set(plain, null);
		AtomicBoolean plainRaid = new AtomicBoolean();
		AtomicBoolean plainScared = new AtomicBoolean();
		List<String> ironDid = new ArrayList<>();
		Raid[] raid = new Raid[1];
		ServerPlayer[] player = new ServerPlayer[1];
		helper.runAfterDelay(5, () -> level.getPoiManager().take(h -> h.is(PoiTypes.MEETING), (h, p) -> true, helper.absolutePos(bell), 2)
			.orElseThrow(() -> new GameTestAssertException("the bell isn't a meeting point")));
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(level.isVillage(helper.absolutePos(bell)), "the test area isn't a village");
			player[0] = helper.makeMockServerPlayerInLevel();
			raid[0] = level.getRaids().createOrExtendRaid(player[0], helper.absolutePos(bell));
			helper.assertTrue(raid[0] != null, "no raid");
			raid[0].setRaidOmenLevel(1);
		});
		helper.runAfterDelay(120, () -> ((BellBlock) Blocks.BELL).attemptToRing(level, helper.absolutePos(bell), Direction.NORTH));
		helper.runAfterDelay(200, () -> {
			for (Villager v : List.of(iron, plain)) {
				v.getBrain().setMemory(MemoryModuleType.HURT_BY, level.damageSources().generic());
			}
		});
		helper.onEachTick(() -> {
			for (Activity a : List.of(Activity.PANIC, Activity.HIDE, Activity.RAID, Activity.PRE_RAID)) {
				if (iron.getBrain().isActive(a) && !ironDid.contains(a.getName())) {
					ironDid.add(a.getName());
				}
			}
			if (plain.getBrain().isActive(Activity.RAID) || plain.getBrain().isActive(Activity.PRE_RAID)) {
				plainRaid.set(true);
			}
			if (plain.getBrain().isActive(Activity.PANIC) || plain.getBrain().isActive(Activity.HIDE)) {
				plainScared.set(true);
			}
		});
		helper.runAfterDelay(300, () -> {
			try {
				helper.assertTrue(plainRaid.get(), "the ordinary villager never joined the raid: the raid wasn't staged");
				helper.assertTrue(plainScared.get(), "the ordinary villager never hid or panicked: the bell and the hurt weren't staged");
				helper.assertTrue(ironDid.isEmpty(), "the Iron-Willed villager did " + ironDid);
				helper.succeed();
			} finally {
				if (raid[0] != null) {
					raid[0].stop();
				}
				if (player[0] != null) {
					player[0].discard();
				}
			}
		});
	}

	/** A Night Owl builder places blocks at midnight, and at noon is asleep in bed. */
	//$ gametest_ticks_batch AREA '2400' '"giftedNightOwl"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "giftedNightOwl")
	public void nightOwlBuilderWorksAtMidnightAndSleepsAtNoon(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(18000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chest = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chest, Blocks.CHEST);
		Container container = helper.getBlockEntity(chest);
		container.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		container.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		container.setItem(2, new ItemStack(Items.OAK_DOOR));
		container.setItem(3, new ItemStack(Items.TORCH));
		BlockPos foot = new BlockPos(2, 2, 9);
		BlockPos head = new BlockPos(2, 2, 10);
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		Villager owl = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, owl, helper.absolutePos(bench));
		Gifted.set(owl, NIGHT_OWL);
		owl.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(head)));
		helper.assertTrue(owl.getBrain().getSchedule() == ModVillagers.NIGHT_OWL_SCHEDULE, "a Night Owl builder isn't on the night schedule");
		BuildSite site = Builders.start(level, owl, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		int toDo = plan.unfinished(level).size();
		AtomicInteger stage = new AtomicInteger();
		helper.onEachTick(() -> {
			if (stage.get() == 0) {
				long time = level.getDayTime() % 24000;
				helper.assertTrue(time >= 18000 && time < 19000 || plan.unfinished(level).size() < toDo,
					"not one block placed by " + time + " (activity " + owl.getBrain().getActiveNonCoreActivity() + ")");
				if (plan.unfinished(level).size() < toDo - 3) {
					helper.assertTrue(owl.getBrain().isActive(Activity.WORK), "placing blocks at midnight outside WORK");
					stage.set(1);
					helper.setDayTime(6000);
				}
			} else if (stage.get() == 1 && owl.isSleeping()) {
				helper.assertTrue(owl.getBrain().isActive(Activity.REST), "asleep outside REST");
				stage.set(2);
				helper.succeed();
			}
		});
	}
}
