package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Plunder;
import io.github.jcondedata.aliveworkplace.threat.ThreatCommand;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, 2026-11-03, saves): yesterday's fixes meeting a save and a load of the world.
 * <ul>
 * <li>B840 (piglins' plunder, 32.10, with {@code /workplace threat end} and {@code clear}, 32.21): what a raider carries
 * is "kept on the raider (saved with it)"; called off, "what a raider took falls where it stood". So a plundering
 * piglin saved and loaded mid-raid, its raid saved and loaded too, still drops every ingot, once, when the operator
 * ends or clears the raid; a raider carrying nothing drops nothing.</li>
 * <li>B839 (builder.md: builders work from early morning to dusk, then go to bed; progress lives in the saved site):
 * a Builder saved and loaded at midnight mid-STRUCTURE builds nothing in the night and finishes in the morning.</li>
 * <li>B838 (builder.md: levelling never digs under a neighboring build): a save and load just before hut A levels its
 * LANDSCAPE ring still leaves the pad under hut B's floor alone.</li>
 * </ul>
 */
public class QaSavesAfterFixes1103eGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	// ------------------------------------------------------------------------------------------------ B840 + saves

	/** A piglin carrying 12 gold, it and the raid saved and loaded; {@code /workplace threat end}: exactly 12 lie in the village. */
	//$ gametest_ticks_batch HUGE '80' '"qaSavesPlunderEnd1103e"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaSavesPlunderEnd1103e")
	public void aPlunderingPiglinSavedAndLoadedStillDropsAllItsGoldWhenTheRaidIsEnded(GameTestHelper helper) {
		plunderReloaded(helper, false);
	}

	/** The same with {@code /workplace threat clear}: exactly 12 lie in the village, none twice. */
	//$ gametest_ticks_batch HUGE '80' '"qaSavesPlunderClear1103e"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaSavesPlunderClear1103e")
	public void aPlunderingPiglinSavedAndLoadedStillDropsAllItsGoldWhenTheThreatIsCleared(GameTestHelper helper) {
		plunderReloaded(helper, true);
	}

	/** Two piglins, one carrying 7 gold, the other nothing: ended, exactly 7 gold and nothing else of theirs lie on the ground. */
	//$ gametest_ticks_batch HUGE '80' '"qaSavesPlunderTwo1103e"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaSavesPlunderTwo1103e")
	public void ofTwoPiglinsOnlyTheOneCarryingGoldDropsAnythingWhenTheRaidIsEnded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = raidSetup(helper);
		Culture culture = Threats.get(PIGLINS).orElseThrow();
		Mob a = piglin(helper, culture, new BlockPos(12, 2, 12));
		Mob b = piglin(helper, culture, new BlockPos(16, 2, 12));
		VillageRaids.track(level, hall, culture, List.of(a, b));
		Plunder.carry(level, a, new ItemStack(Items.GOLD_INGOT, 7));
		helper.assertTrue(Plunder.carried(b).isEmpty(), "setup: the second piglin carries " + Plunder.carried(b));
		int ended = ThreatCommand.end(level, hall);
		helper.assertTrue(ended == 1, "setup: expected /workplace threat end to end the one raid, ended " + ended);
		helper.runAfterDelay(2, () -> {
			List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(48), e -> true);
			int gold = items.stream().filter(e -> e.getItem().is(Items.GOLD_INGOT)).mapToInt(e -> e.getItem().getCount()).sum();
			List<String> other = items.stream().filter(e -> !e.getItem().is(Items.GOLD_INGOT)).map(e -> e.getItem().toString()).toList();
			helper.assertTrue(gold == 7, "after /workplace threat end " + gold + " gold ingots lie in the village, wanted the 7 the one piglin carried");
			helper.assertTrue(other.isEmpty(), "the raiders dropped more than their plunder when the raid was ended: " + other);
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "the raid is still under way after /workplace threat end");
			helper.succeed();
		});
	}

	private static void plunderReloaded(GameTestHelper helper, boolean clear) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = raidSetup(helper);
		Culture culture = Threats.get(PIGLINS).orElseThrow();
		Mob piglin = piglin(helper, culture, new BlockPos(12, 2, 12));
		VillageRaids.track(level, hall, culture, List.of(piglin));
		Plunder.carry(level, piglin, new ItemStack(Items.GOLD_INGOT, 12));
		// the world saved and loaded: the raid from its save, the piglin from its own
		reloadThreats(helper, level);
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "the raid under way was lost in a save and load");
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(piglin.save(tag), "setup: the piglin wasn't saved");
		piglin.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Mob, "setup: the piglin didn't load as a mob: " + back);
		level.addFreshEntity(back);
		Mob loaded = (Mob) back;
		loaded.setNoAi(true);
		helper.assertTrue(Plunder.carried(loaded).getCount() == 12, "the loaded piglin carries " + Plunder.carried(loaded) + ", wanted the 12 gold it was saved with");
		helper.assertTrue(VillageRaids.raiders(level, hall).contains(loaded), "the loaded piglin isn't one of the raid's raiders any more");
		int ended = clear ? ThreatCommand.clear(level, hall) : ThreatCommand.end(level, hall);
		helper.assertTrue(ended >= 1, "setup: expected the command to end the raid, it ended " + ended);
		helper.assertTrue(!loaded.isAlive() || loaded.isRemoved(), "the loaded piglin stayed once the raid was " + (clear ? "cleared" : "ended"));
		helper.runAfterDelay(2, () -> {
			int counted = ThreatData.get(level).plundered(hall);
			int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(48), e -> e.getItem().is(Items.GOLD_INGOT))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(counted + dropped == 12, "after a save and load and /workplace threat " + (clear ? "clear" : "end") + " the hall counts "
				+ counted + " plundered and " + dropped + " gold lie in the village, wanted the 12 the piglin carried");
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "the raid is still under way");
			helper.succeed();
		});
	}

	/** A village round {@link #HALL} with a 20-block radius, put back afterwards with every raider, item and raid record gone. */
	private static BlockPos raidSetup(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(48), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		ThreatData.get(level).forgetPlunder(hall);
		helper.assertTrue(Threats.get(PIGLINS).isPresent(), "setup: expected piglin_incursion.json loaded");
		return hall;
	}

	/** A piglin of the incursion at {@code at}, tagged as a raider, bare-headed and standing still. */
	private static Mob piglin(GameTestHelper helper, Culture culture, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.PIGLIN))).findFirst().orElseThrow();
		Mob piglin = Threats.create(level, member);
		helper.assertTrue(piglin != null, "setup: no piglin made");
		BlockPos p = helper.absolutePos(at);
		piglin.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		piglin.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, piglin, culture, member);
		piglin.addTag(Lairs.TAG);
		piglin.addTag(VillageRaids.TAG);
		piglin.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		piglin.setDropChance(EquipmentSlot.MAINHAND, 0f);
		piglin.setDropChance(EquipmentSlot.OFFHAND, 0f);
		level.addFreshEntityWithPassengers(piglin);
		piglin.setNoAi(true);
		return piglin;
	}

	/** As a save and a load of the world would do to the threats' saved data. */
	private static void reloadThreats(GameTestHelper helper, ServerLevel level) {
		try {
			CompoundTag saved = ThreatData.get(level).save(new CompoundTag(), level.registryAccess());
			Method load = ThreatData.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
			load.setAccessible(true);
			ThreatData loaded = (ThreatData) load.invoke(null, saved, level.registryAccess());
			level.getDataStorage().set(ThreatData.NAME, loaded);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("setup: couldn't save and load the threats: " + e, e);
		}
	}

	// ------------------------------------------------------------------------------------------------ B839 + saves

	/**
	 * Mid-STRUCTURE (four blocks built since STRUCTURE began) midnight comes and the world is saved and loaded at once: the
	 * site from its save, the builder from his. Past a 200-tick grace the hut doesn't change all night (600 ticks
	 * watched); then morning comes and the hut is finished as drawn with every material accounted for. (A loaded brain
	 * starts no behavior, so this guards the loaded path: B839's canStillUse mutant survives it; the unloaded night is
	 * QaBuilderMidJob1102BugTests'.)
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaSavesBuilderNight1103e"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaSavesBuilderNight1103e")
	public void aBuilderSavedAndLoadedAtMidnightBuildsNothingAtNightAndFinishesInTheMorning(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(40, level.getServer());
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		// 0 day, 1 night (grace, then watched), 2 morning
		int[] phase = {0};
		int[] atStart = {-1};
		int[] unbuilt = {-1};
		long[] since = {-1};
		helper.onEachTick(() -> {
			if (phase[0] == 0) {
				helper.setDayTime(2000);
				BuildSite s = r.site();
				if (s == null || s.stage() != BuildPlan.Stage.STRUCTURE) {
					return;
				}
				int now = r.plan().unfinished(r.level()).size();
				if (atStart[0] < 0) {
					atStart[0] = now;
				}
				if (atStart[0] - now < 4) {
					return;
				}
				helper.setDayTime(18000);
				Villager back = QaBuilderTerrain1102GameTests.reload(helper, level, r.builder()[0]);
				r.builder()[0] = back;
				Beds.home(back, helper.absolutePos(new BlockPos(1, 2, 8)));
				phase[0] = 1;
				since[0] = helper.getTick();
				LOGGER.info("qa saves night: saved and loaded at midnight, {} unbuilt, {}", now, QaBuilderTerrain1102GameTests.state(helper, r));
				return;
			}
			if (phase[0] == 2) {
				if (helper.getTick() % 20 == 0) {
					helper.setDayTime(2000);
				}
				return;
			}
			helper.setDayTime(18000);
			long night = helper.getTick() - since[0];
			int now = r.plan().unfinished(r.level()).size();
			helper.assertTrue(r.site() != null, "the hut was finished in the night: " + QaBuilderTerrain1102GameTests.state(helper, r));
			if (night == 200) {
				unbuilt[0] = now;
			} else if (night > 200) {
				Villager b = r.builder()[0];
				helper.assertTrue(now == unbuilt[0], "the loaded builder built at night, " + night + " ticks after midnight: " + unbuilt[0] + " unbuilt, then "
					+ now + " (asleep=" + b.isSleeping() + ", activity " + b.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("none") + ", "
					+ QaBuilderTerrain1102GameTests.state(helper, r) + ")");
				if (night >= 800) {
					phase[0] = 2;
					helper.setDayTime(1000);
					LOGGER.info("qa saves night: morning, {}", QaBuilderTerrain1102GameTests.state(helper, r));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "the night isn't over yet (phase " + phase[0] + ")");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// ------------------------------------------------------------------------------------------------ B838 + saves

	/**
	 * QaBuilderMidJob1102BugTests' two touching huts, hut B on a one-high stone pad, with the world saved and loaded the
	 * tick hut A reaches DECORATION, just before its LANDSCAPE ring (sites and both builders from their saves). Both still end as drawn and the pad under
	 * hut B's floor is all there.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaSavesNeighbors1103e"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaSavesNeighbors1103e")
	public void twoTouchingHutsSavedAndLoadedAsTheFirstLevelsNeitherUndoesTheOther(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 11; x <= 15; x++) {
			for (int z = 6; z <= 10; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
			}
		}
		QaBuilderTerrain1102GameTests.Run a = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.Run b = QaBuilderMidJob1102GameTests.startAt(helper, new BlockPos(24, 2, 8), new BlockPos(11, 3, 6));
		helper.assertTrue(a.plan().bounds().maxX() + 1 == b.plan().bounds().minX(), "setup: the huts' boxes don't touch: " + a.plan().bounds() + " " + b.plan().bounds());
		QaBuilderTerrain1102GameTests.watchDeath(helper, a);
		QaBuilderTerrain1102GameTests.watchDeath(helper, b);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite s = a.site();
			// DECORATION: the last stage before the ring is levelled (LANDSCAPE can pass within a tick)
			if (reloaded[0] || s == null || s.stage().ordinal() < BuildPlan.Stage.DECORATION.ordinal()) {
				return;
			}
			reloaded[0] = true;
			a.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, level, a.builder()[0]);
			b.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, level, b.builder()[0]);
			LOGGER.info("qa saves neighbors: saved and loaded as hut A levels; A {}, B {}", QaBuilderTerrain1102GameTests.state(helper, a),
				QaBuilderTerrain1102GameTests.state(helper, b));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "hut A was never saved and loaded before levelling: " + QaBuilderTerrain1102GameTests.state(helper, a));
			QaBuilderTerrain1102GameTests.assertFinished(helper, a);
			QaBuilderTerrain1102GameTests.assertFinished(helper, b);
			List<String> dug = new ArrayList<>();
			for (int x = 11; x <= 15; x++) {
				for (int z = 6; z <= 10; z++) {
					BlockState s = helper.getBlockState(new BlockPos(x, 2, z));
					if (s.isAir() || s.canBeReplaced()) {
						dug.add("(" + x + ",2," + z + ")=" + s.getBlock().getDescriptionId());
					}
				}
			}
			helper.assertTrue(dug.isEmpty(), dug.size() + " block(s) of the pad under hut B's finished floor dug out after a save and load: " + dug);
		});
	}
}
