package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.nurse.NurseWork;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.RemedyItem;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Disaster;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Drought;
import io.github.jcondedata.aliveworkplace.threat.Plague;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * QA (2026-11-04), written from ROADMAP 32.17 (Drought) and 32.18 (Plague), covering what DroughtGameTests,
 * PlagueGameTests, QaDisastersGameTests and QaSicknessGameTests leave out: a drought without rain lasting its 3 to 5
 * days and ending the day after, not before; crops outside the hall's area at full pace in a drought; melon stems held
 * back; a bucket wetting exactly 9 farmland, not a 10th; the mood exactly 10 less and back when it ends; the plague's
 * 3-block reach from both sides; a nurse's remedy not curing (nor spent on) the plague; the Remedy's 10 days, counted
 * from the player's hand, from both sides; a new case resetting the plague's day without the ill; a plague surviving a
 * restart with its ill and kept-well; and config {@code disasters} switching a plague off mid-way.
 */
public class QaDroughtPlague1104dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(16, 2, 16);
	/** The hall's reach in these tests: 8, so a corner of the test area lies outside the village. */
	private static final int RADIUS = 8;
	/** Day 111 begins here: far from the other disaster tests' days. */
	private static final long BASE = 110 * VillageNeeds.DAY;
	/** The spec's ten days, in ticks, written out (not read from the mod). */
	private static final long TEN_DAYS = 10L * 24000L;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		VillageHalls.Census census() {
			return VillageHalls.census(level, hall);
		}

		long day() {
			return Chronicle.day(level);
		}

		void round() {
			Disasters.round(level, hall, entity, census());
		}

		void dayOf(long d) {
			level.setDayTime(BASE + d * VillageNeeds.DAY + 1000);
		}

		Disaster drought() {
			return Drought.get().orElseThrow(() -> new GameTestAssertException("drought.json isn't loaded: " + Disasters.all()));
		}

		Disaster plague() {
			return Disasters.get(Plague.ID).orElseThrow(() -> new GameTestAssertException("plague.json isn't loaded: " + Disasters.all()));
		}

		void start(Disaster d, long seed) {
			entity.setDisaster(null, -1, -1, -100);
			Disasters.start(level, hall, entity, d, census(), RandomSource.create(seed));
			if (!d.id().equals(entity.disaster())) {
				throw new GameTestAssertException("the " + d.id() + " didn't start");
			}
		}

		Disaster.Effect contagion() {
			return plague().effect(Disaster.Effect.Type.CONTAGION).orElseThrow();
		}
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int wasRadius = VillageHalls.RADIUS;
		long was = level.getDayTime();
		VillageHalls.RADIUS = RADIUS;
		level.setDayTime(BASE + 1000);
		level.setWeatherParameters(6000, 0, false, false);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setDisaster(null, -1, -1, -100);
			}
			Disasters.forget();
			Drought.forget();
			VillageHalls.RADIUS = wasRadius;
			level.setDayTime(was);
			level.setWeatherParameters(6000, 0, false, false);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		entity.setDisaster(null, -1, -1, -100);
		Disasters.forget();
		Drought.forget();
		return new Village(level, hall, entity);
	}

	private static Villager villager(GameTestHelper helper, double x, double z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new net.minecraft.world.phys.Vec3(x, 2, z));
		v.setNoAi(true);
		return v;
	}

	// --- 32.17: the drought.

	/**
	 * Without rain a drought lasts its 3 to 5 days: still on (and the village in it) on every one of them, over the day
	 * after the last; over 30 seeds every length is 3, 4 or 5 days, and both 3 and 5 come up.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104dDroughtDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dDroughtDays")
	public void aDryDroughtLastsItsDaysAndNoMore(GameTestHelper helper) {
		Village v = village(helper);
		Set<Long> lengths = new TreeSet<>();
		for (long seed = 0; seed < 30; seed++) {
			v.start(v.drought(), seed * 104729L + 7L); // (spread seeds: consecutive ones give near the same first number)
			lengths.add(v.entity().disasterEnds() - v.entity().disasterSince() + 1);
		}
		helper.assertTrue(lengths.stream().allMatch(n -> n >= 3 && n <= 5), "a drought's length outside 3 to 5 days: " + lengths);
		helper.assertTrue(lengths.contains(3L) && lengths.contains(5L), "over 30 seeds the drought never lasted 3 or never 5 days: " + lengths);
		v.start(v.drought(), 4L);
		long since = v.entity().disasterSince();
		long ends = v.entity().disasterEnds();
		long first = v.day();
		helper.assertTrue(since == first, "with no Well Keeper the drought didn't start today: " + since + ", today " + first);
		BlockPos soil = helper.absolutePos(new BlockPos(14, 1, 14));
		for (long d = since; d <= ends; d++) {
			v.dayOf(d - first);
			v.round();
			helper.assertTrue(Drought.ID.equals(v.entity().disaster()) && Drought.in(v.level(), soil),
				"no rain and the drought was over on day " + (d - since + 1) + " of its " + (ends - since + 1));
		}
		v.dayOf(ends + 1 - first);
		v.round();
		helper.assertTrue(v.entity().disaster() == null && !Drought.in(v.level(), soil),
			"the drought went on the day after its " + (ends - since + 1) + " days");
		helper.assertTrue(v.entity().lastDisasterDay() == ends + 1, "the drought's end wasn't recorded on the day after its last: " + v.entity().lastDisasterDay());
		helper.succeed();
	}

	/** In a drought, wheat outside the hall's area grows at the full pace: every random tick may grow it, and it grows as often as with no drought. */
	//$ gametest_ticks_batch AREA '100' '"qa1104dDroughtOutside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dDroughtOutside")
	public void cropsOutsideTheVillageGrowAtFullPace(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos inRel = new BlockPos(14, 1, 14);
		BlockPos outRel = new BlockPos(2, 1, 2);
		for (BlockPos p : List.of(inRel, outRel)) {
			helper.setBlock(p, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
			helper.setBlock(p.above(), Blocks.WHEAT);
		}
		BlockPos in = helper.absolutePos(inRel.above());
		BlockPos out = helper.absolutePos(outRel.above());
		helper.assertFalse(VillageHalls.area(v.hall()).contains(out.getX() + 0.5, out.getY() + 0.5, out.getZ() + 0.5),
			"test setup: the far wheat is inside the hall's area");
		int usual = wheatGrowths(v.level(), out);
		v.start(v.drought(), 1L);
		RandomSource gate = RandomSource.create(42L);
		int inside = 0;
		for (int i = 0; i < 400; i++) {
			helper.assertTrue(Disasters.cropGrows(v.level(), out, gate), "wheat outside the village was held back by its drought on random tick " + i);
			if (Disasters.cropGrows(v.level(), in, gate)) {
				inside++;
			}
		}
		helper.assertTrue(inside <= 115, "test setup: the wheat inside the village isn't held back (" + inside + " of 400)");
		int dry = wheatGrowths(v.level(), out);
		helper.assertTrue(usual >= 20 && dry == usual, "wheat outside the village grew " + dry + " times in the drought, " + usual + " without");
		helper.succeed();
	}

	private static int wheatGrowths(ServerLevel level, BlockPos crop) {
		RandomSource random = RandomSource.create(42L);
		int grew = 0;
		for (int i = 0; i < 400; i++) {
			level.setBlock(crop, Blocks.WHEAT.defaultBlockState(), 2);
			level.getBlockState(crop).randomTick(level, crop, random);
			if (((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getAge(level.getBlockState(crop)) > 0) {
				grew++;
			}
		}
		return grew;
	}

	/** Melon stems grow at a quarter pace in the drought too (400 random ticks, a fixed seed). */
	//$ gametest_ticks_batch AREA '100' '"qa1104dDroughtMelon"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dDroughtMelon")
	public void melonStemsAreHeldBack(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(14, 1, 12), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
		helper.setBlock(new BlockPos(14, 2, 12), Blocks.MELON_STEM);
		BlockPos stem = helper.absolutePos(new BlockPos(14, 2, 12));
		int usual = melonGrowths(v.level(), stem);
		v.start(v.drought(), 1L);
		int dry = melonGrowths(v.level(), stem);
		helper.assertTrue(usual >= 20, "the melon stem hardly grows without a drought: " + usual + " of 400 (test setup)");
		helper.assertTrue(dry * 4 <= usual * 1.5, "in the drought the melon stem grew " + dry + " times, against " + usual + " without: not a quarter pace");
		helper.succeed();
	}

	private static int melonGrowths(ServerLevel level, BlockPos stem) {
		RandomSource random = RandomSource.create(42L);
		int grew = 0;
		for (int i = 0; i < 400; i++) {
			level.setBlock(stem, Blocks.MELON_STEM.defaultBlockState(), 2);
			level.getBlockState(stem).randomTick(level, stem, random);
			if (level.getBlockState(stem).getValue(StemBlock.AGE) > 0) {
				grew++;
			}
		}
		return grew;
	}

	/** A bucket poured in the middle of a 5 x 5 field of dry farmland wets the 3 x 3 round it (moisture 7, watered today) and none of the 16 around. */
	//$ gametest_ticks_batch AREA '100' '"qa1104dDroughtNine"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dDroughtNine")
	public void aBucketWetsNineFarmlandNotATenth(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos middle = new BlockPos(14, 1, 12);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				helper.setBlock(middle.offset(dx, 0, dz), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
			}
		}
		v.start(v.drought(), 1L);
		int wet = Drought.wet(v.level(), helper.absolutePos(middle));
		helper.assertTrue(wet == 9, "a bucket wet " + wet + " farmland, not 9");
		int ring = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos p = helper.absolutePos(middle.offset(dx, 0, dz));
				int moisture = v.level().getBlockState(p).getValue(FarmBlock.MOISTURE);
				boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
				if (inner) {
					helper.assertTrue(moisture == 7 && Drought.watered(v.level(), p), "farmland " + dx + "," + dz + " from the bucket isn't wet through: " + moisture);
				} else if (moisture > 0 || Drought.watered(v.level(), p)) {
					ring++;
				}
			}
		}
		helper.assertTrue(ring == 0, ring + " farmland two blocks from the bucket was wet too");
		helper.succeed();
	}

	/** "The wells are low" makes a villager exactly 10 less happy while the drought lasts, and the mood is back as it was once rain ends it. */
	//$ gametest_ticks_batch AREA '100' '"qa1104dDroughtMood"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dDroughtMood")
	public void theMoodIsTenLessAndComesBack(GameTestHelper helper) {
		Village v = village(helper);
		Villager villager = villager(helper, 14.5, 14.5);
		int before = Moods.work(v.level(), villager).score();
		helper.assertTrue(before >= 10 && before <= 100, "test setup: a mood of " + before + " can't show 10 less");
		v.start(v.drought(), 1L);
		Moods.Mood dry = Moods.work(v.level(), villager);
		String low = Language.getInstance().getOrDefault("mood.aliveworkplace.reason.drought");
		helper.assertTrue(dry.score() == before - 10, "in the drought the mood is " + dry.score() + ", not " + before + " - 10");
		helper.assertTrue(dry.bad().stream().anyMatch(c -> c.getString().equals(low)), "the mood doesn't say the wells are low: " + dry.bad());
		v.level().setWeatherParameters(0, 6000, true, false);
		v.round();
		helper.assertTrue(v.entity().disaster() == null, "test setup: the rain didn't end the drought");
		Moods.Mood after = Moods.work(v.level(), villager);
		helper.assertTrue(after.score() == before && after.bad().stream().noneMatch(c -> c.getString().equals(low)),
			"after the drought the mood is " + after.score() + " (" + after.bad() + "), not back to " + before);
		helper.succeed();
	}

	// --- 32.18: the plague.

	/** The plague reaches 3 blocks: a well villager exactly 3 blocks from the ill catches it within 60 rounds; one exactly 4 blocks off never does in 200 (fixed seed). */
	//$ gametest_ticks_batch AREA '100' '"qa1104dPlagueReach"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dPlagueReach")
	public void thePlagueReachesThreeBlocksNotFour(GameTestHelper helper) {
		Village v = village(helper);
		Villager sick = villager(helper, 12.5, 12.5);
		Villager three = villager(helper, 15.5, 12.5);
		Villager four = villager(helper, 12.5, 16.5);
		Sickness.catchPlague(v.level(), sick);
		v.start(v.plague(), 1L);
		helper.assertTrue(Plague.underway(v.level(), v.hall()), "test setup: the plague isn't under way");
		helper.assertTrue(Math.abs(Math.sqrt(three.distanceToSqr(sick)) - 3) < 1e-6 && Math.abs(Math.sqrt(four.distanceToSqr(sick)) - 4) < 1e-6,
			"test setup: not 3 and 4 blocks off: " + Math.sqrt(three.distanceToSqr(sick)) + ", " + Math.sqrt(four.distanceToSqr(sick)));
		RandomSource random = RandomSource.create(5L);
		int round = 0;
		while (!Sickness.isPlagueIll(three) && round < 60) {
			Plague.spread(v.level(), v.contagion(), List.of(sick, three, four), random);
			round++;
		}
		helper.assertTrue(Sickness.isPlagueIll(three), "a villager 3 blocks from the ill didn't catch the plague in 60 rounds");
		Sickness.recover(v.level(), three);
		three.discard();
		for (int i = 0; i < 200; i++) {
			Plague.spread(v.level(), v.contagion(), List.of(sick, four), random);
		}
		helper.assertFalse(Sickness.isIll(four), "a villager 4 blocks from the ill caught the plague");
		helper.succeed();
	}

	/** A nurse with honey by her station cures an ordinary illness but not the plague, and spends no honey on the plague's ill. */
	//$ gametest_ticks_batch AREA '100' '"qa1104dPlagueNurse"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dPlagueNurse")
	public void aNursesRemedyDoesNotCureThePlague(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos desk = new BlockPos(12, 2, 10);
		helper.setBlock(desk, ModBlocks.NURSE_STATION);
		helper.setBlock(desk.east(), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) v.level().getBlockEntity(helper.absolutePos(desk.east()));
		chest.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 4));
		Villager nurse = villager(helper, 12.5, 11.5);
		Villager plagued = villager(helper, 14.5, 13.5);
		Villager ordinary = villager(helper, 10.5, 14.5);
		boolean sick = Sickness.ENABLED;
		Sickness.ENABLED = false;
		Leftovers.after(helper, () -> Sickness.ENABLED = sick);
		helper.runAfterDelay(2, () -> {
			Jobs.employ(v.level(), nurse, helper.absolutePos(desk), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
			Sickness.catchPlague(v.level(), plagued);
			v.start(v.plague(), 1L);
			Sickness.fallIll(v.level(), ordinary);
			helper.assertTrue(Sickness.isPlagueIll(plagued) && Sickness.isIll(ordinary) && !Sickness.isPlagueIll(ordinary), "test setup: the patients aren't ill");
			NurseWork.cure(v.level(), nurse);
			helper.assertFalse(Sickness.isIll(ordinary), "test setup: the nurse didn't cure the ordinary illness with her honey");
			int honey = chest.countItem(Items.HONEY_BOTTLE);
			for (int i = 0; i < 3; i++) {
				NurseWork.cure(v.level(), nurse);
			}
			helper.assertTrue(Sickness.isPlagueIll(plagued), "a nurse's honey cured the plague");
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == honey, "the nurse spent honey on the plague's ill: " + honey + " -> " + chest.countItem(Items.HONEY_BOTTLE));
			helper.succeed();
		});
	}

	/**
	 * A Remedy given from a player's hand keeps a villager well for exactly ten days: beside the ill they don't catch it
	 * with a day of the ten left (100 rounds), and do once the ten are up.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104dPlagueTenDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dPlagueTenDays")
	public void theRemedyKeepsThemWellTenDaysNotEleven(GameTestHelper helper) {
		Village v = village(helper);
		Villager sick = villager(helper, 12.5, 12.5);
		Villager patient = villager(helper, 14.5, 12.5);
		Sickness.catchPlague(v.level(), sick);
		Sickness.catchPlague(v.level(), patient);
		v.start(v.plague(), 1L);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEDY));
		long now = v.level().getGameTime();
		helper.assertTrue(RemedyItem.give(player, v.level(), InteractionHand.MAIN_HAND, patient) == InteractionResult.SUCCESS, "the Remedy wasn't given");
		helper.assertFalse(Sickness.isIll(patient), "the Remedy didn't cure the plague");
		Long until = ModAttachments.WELL_UNTIL.get(patient);
		helper.assertTrue(until != null && until - now == TEN_DAYS, "the Remedy keeps them well for " + (until == null ? "no" : (until - now) + " ticks, not " + TEN_DAYS));
		RandomSource random = RandomSource.create(9L);
		for (int i = 0; i < 100; i++) {
			Plague.spread(v.level(), v.contagion(), List.of(sick, patient), random);
		}
		helper.assertFalse(Sickness.isIll(patient), "a villager given a Remedy today caught the plague");
		// Nine days on: a day of the ten left.
		ModAttachments.WELL_UNTIL.set(patient, v.level().getGameTime() + VillageNeeds.DAY);
		for (int i = 0; i < 100; i++) {
			Plague.spread(v.level(), v.contagion(), List.of(sick, patient), random);
		}
		helper.assertFalse(Sickness.isIll(patient), "on the ninth day after a Remedy the villager caught the plague");
		// Ten days on: the Remedy's time is up.
		ModAttachments.WELL_UNTIL.set(patient, v.level().getGameTime());
		int round = 0;
		while (!Sickness.isPlagueIll(patient) && round < 100) {
			Plague.spread(v.level(), v.contagion(), List.of(sick, patient), random);
			round++;
		}
		helper.assertTrue(Sickness.isPlagueIll(patient), "ten days after a Remedy the villager beside the ill still can't catch it (100 rounds)");
		helper.succeed();
	}

	/**
	 * The plague's day without the ill starts again with a new case: the first ill cured on day 1, another ill on day 2
	 * keeps it on through day 2 (twice); cured that day, it's over on day 3, not before.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104dPlagueNewCase"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dPlagueNewCase")
	public void aNewCaseKeepsThePlagueOn(GameTestHelper helper) {
		Village v = village(helper);
		Villager a = villager(helper, 10.5, 10.5);
		Villager b = villager(helper, 20.5, 20.5);
		Sickness.catchPlague(v.level(), a);
		v.start(v.plague(), 1L);
		v.round();
		Plague.cure(v.level(), a);
		v.round();
		helper.assertTrue(Plague.ID.equals(v.entity().disaster()), "the plague ended the day the last of the ill was cured");
		v.dayOf(1);
		ModAttachments.WELL_UNTIL.remove(b);
		Sickness.catchPlague(v.level(), b);
		helper.assertTrue(Sickness.isPlagueIll(b), "test setup: the second villager isn't ill");
		v.round();
		helper.assertTrue(Plague.ID.equals(v.entity().disaster()), "the plague ended with a villager ill today");
		Plague.cure(v.level(), b);
		v.round();
		helper.assertTrue(Plague.ID.equals(v.entity().disaster()), "the plague ended the same day its new case was cured");
		v.dayOf(2);
		v.round();
		helper.assertTrue(v.entity().disaster() == null, "the plague went on a full day after the last of the ill was cured");
		helper.succeed();
	}

	/**
	 * A plague survives a restart: the hall read back, the memory forgotten, one round and it's under way again; the ill
	 * villager read back is still ill from the same tick (bedridden two days on) and passes it on; the one kept well by a
	 * Remedy is still kept well to the same tick and doesn't catch it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104dPlagueRestart"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dPlagueRestart")
	public void aPlagueSurvivesARestart(GameTestHelper helper) {
		Village v = village(helper);
		Villager sick = villager(helper, 12.5, 12.5);
		Villager kept = villager(helper, 14.5, 12.5);
		Sickness.catchPlague(v.level(), sick);
		v.start(v.plague(), 1L);
		Plague.cure(v.level(), kept);
		long illSince = ModAttachments.ILL_SINCE.get(sick);
		long keptUntil = ModAttachments.WELL_UNTIL.get(kept);
		CompoundTag hallTag = v.entity().saveWithoutMetadata(v.level().registryAccess());
		Villager sick2 = EntityType.VILLAGER.create(v.level());
		sick2.load(sick.saveWithoutId(new CompoundTag()));
		Villager kept2 = EntityType.VILLAGER.create(v.level());
		kept2.load(kept.saveWithoutId(new CompoundTag()));
		Disasters.forget();
		v.entity().setDisaster(null, -1, -1, -100);
		v.entity().loadWithComponents(hallTag, v.level().registryAccess());
		helper.assertTrue(Plague.ID.equals(v.entity().disaster()), "the plague wasn't read back with the hall: " + v.entity().disaster());
		v.round();
		helper.assertTrue(Plague.underway(v.level(), v.hall()), "after a restart and a round the plague isn't under way");
		helper.assertTrue(Plague.ID.equals(v.entity().disaster()), "after a restart the plague ended with a villager still ill");
		helper.assertTrue(Sickness.isPlagueIll(sick2) && ModAttachments.ILL_SINCE.get(sick2) == illSince, "the ill villager read back isn't ill since the same tick");
		ModAttachments.ILL_SINCE.set(sick2, v.level().getGameTime() - 2 * VillageNeeds.DAY);
		helper.assertTrue(Plague.bedridden(v.level(), sick2), "the ill villager read back isn't bedridden two days on");
		Long until = ModAttachments.WELL_UNTIL.get(kept2);
		helper.assertTrue(until != null && until == keptUntil, "the Remedy's ten days read back as " + until + ", not " + keptUntil);
		Villager well = villager(helper, 12.5, 14.5); // (after the round: only the villager read back can pass it to them)
		RandomSource random = RandomSource.create(17L);
		for (int i = 0; i < 60; i++) {
			Plague.spread(v.level(), v.contagion(), List.of(sick2, kept2, well), random);
		}
		helper.assertTrue(Sickness.isPlagueIll(well), "the ill villager read back passed nothing on in 60 rounds");
		helper.assertFalse(Sickness.isIll(kept2), "the villager kept well, read back, caught the plague");
		sick2.discard();
		kept2.discard();
		helper.succeed();
	}

	/**
	 * Config {@code disasters} with plague false, mid-plague (the real config, applied): the plague ends at the next round,
	 * thirty more rounds pass nothing on, the drought stays on, and the ill get well with time like any illness.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104dPlagueOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104dPlagueOff")
	public void switchedOffMidPlague(GameTestHelper helper) {
		Village v = village(helper);
		Villager sick = villager(helper, 12.5, 12.5);
		Villager near = villager(helper, 13.5, 12.5);
		Sickness.catchPlague(v.level(), sick);
		v.start(v.plague(), 1L);
		WorkplaceConfig config = new WorkplaceConfig();
		config.disasters.put("plague", false);
		config.apply();
		helper.assertFalse(Disasters.enabled(Plague.ID), "config plague false, the plague is still on");
		helper.assertTrue(Disasters.enabled(Drought.ID), "config plague false switched the drought off too");
		v.round();
		helper.assertTrue(v.entity().disaster() == null && !Plague.underway(v.level(), v.hall()), "switched off, the plague under way went on");
		for (int i = 0; i < 30; i++) {
			v.round();
		}
		helper.assertFalse(Sickness.isIll(near), "switched off, the plague still passed on");
		ModAttachments.ILL_SINCE.set(sick, v.level().getGameTime() - 4 * VillageNeeds.DAY);
		Sickness.round(v.level(), sick, 1);
		helper.assertFalse(Sickness.isIll(sick), "switched off, the plague's ill stay ill past an illness's three days");
		helper.succeed();
	}
}
