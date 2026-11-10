package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-10): Milestone 32's threat switches (32.21) regression-tested against what came after their
 * verification on 2026-10-25, from the spec:
 * <ul>
 * <li>32.21: "threatLevel 0: no raids, lairs, sieges or disasters at all; 1, gentle: ... no hexes". A coven hut standing
 * by a village hexes no one at night, through the covens' own clock, at level 1 (the lowest gentle) or 0, and hexes
 * again the same night once the level is back at 2; a villager already hexed is freed at level 1 on the hall's
 * round.</li>
 * <li>32.14 with 32.21: the clock's attack, at threat level 0, is called off ("a raid the clock set doesn't come"), so
 * the village's tower warns of nothing at level 0 and nobody is told; at level 1 (the lowest that lets raids come) the
 * same village is warned. A village warned of an attack that is then called off (level set to 0, or the world saved
 * and loaded at level 0) has nothing to shut its gates for on the day: no gates, no keeping in at dusk.</li>
 * </ul>
 * Each test runs the hall's own order: VillageRaids.tick, then Warnings.round (VillageHallBlockEntity's round).
 */
public class QaThreatSwitches1110dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_switch_besiegers");
	private static final BlockPos HALL = new BlockPos(15, 2, 12);
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final double NORTH = -Math.PI / 2;

	static void threatLevel(int level) {
		WorkplaceConfig.parse("{\"threatLevel\": " + level + "}").apply();
		VillageRaids.ENABLED = true; // (apply() turns raids off under GameTests; the after() puts the config back)
	}

	// The warning.

	/** A hall (radius 24) whose clock is clear, with a test culture loaded; all put back at the end (as QaWarningGameTests). */
	static BlockPos warnedVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true;
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity, "setup: no hall");
		ThreatData.get(level).forgetClock(hall);
		// A finished Lookout Tower with an archer posted 8 blocks from its middle: the village's source of warning.
		BlockPos origin = helper.absolutePos(new BlockPos(0, 2, 0));
		level.getStructureManager().get(StarterBlueprints.LOOKOUT_TOWER.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.LOOKOUT_TOWER.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		List<BlockPos> towers = Warnings.towers(level, hall);
		helper.assertTrue(towers.size() == 1, "setup: the village's towers: " + towers);
		BlockPos post = new BlockPos(towers.get(0).getX() + 8, origin.getY(), towers.get(0).getZ());
		level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
		Villager archer = EntityType.VILLAGER.create(level);
		archer.moveTo(post.getX() + 0.5, post.getY(), post.getZ() - 1.5, 0f, 0f);
		level.addFreshEntity(archer);
		Leftovers.after(helper, archer::discard);
		Jobs.employ(level, archer, post, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		archer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		helper.assertTrue(Warnings.source(level, hall).isPresent(), "setup: the archer by the Lookout Tower isn't a source");
		return hall;
	}

	/** A villager (no AI) by the hall: the one the warned evening keeps in. */
	static Villager townsman(GameTestHelper helper) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 14));
		v.setNoAi(true);
		v.setInvulnerable(true);
		return v;
	}

	/** Dusk of day 11: the clock sets tomorrow night's attack from the north. */
	static void setAttack(ServerLevel level, BlockPos hall) {
		level.setDayTime(BASE + 12100);
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, BESIEGERS, NORTH, 13600));
	}

	/** The hall's round, in the hall's own order: the raids' clock, then the day's warning. */
	static void hallRound(ServerLevel level, BlockPos hall) {
		VillageRaids.tick(level, hall, 10, 1, -1, d -> {
		});
		Warnings.round(level, hall, 10, 1);
	}

	/**
	 * Warned at level 2 of tomorrow night's attack; at noon of day 12 the village is ready (gates shut). The owner then
	 * sets the threat level to 0: the hall's round calls the attack off ("a raid the clock set doesn't come"), the
	 * Defense page shows none, and so nothing is shut for it: from noon the gates stay open, and at dusk the villagers
	 * aren't sent in for raiders who aren't coming.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaSwitchWarnCalledOff1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSwitchWarnCalledOff1110d")
	public void aWarnedAttackCalledOffAtThreatLevelZeroShutsNothingOnItsDay(GameTestHelper helper) {
		BlockPos hall = warnedVillage(helper);
		ServerLevel level = helper.getLevel();
		Villager townsman = townsman(helper);
		threatLevel(2);
		setAttack(level, hall);
		hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "setup: not warned at level 2: " + ThreatData.get(level).warned(hall));
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // day 12, just past noon
		hallRound(level, hall);
		helper.assertTrue(Warnings.ready(level, hall), "setup: at level 2 the warned village isn't ready at noon of the day: warned " + ThreatData.get(level).warned(hall)
			+ ", day " + Chronicle.day(level) + ", time " + level.getDayTime() + ", raids " + VillageRaids.ENABLED + ", attacks " + ThreatData.get(level).attacks(hall));
		threatLevel(0);
		level.setDayTime(BASE + VillageNeeds.DAY + 12600); // dusk of day 12, before the attack's hour
		townsman.getBrain().eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
		hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).attacks(hall).isEmpty(), "setup: the attack isn't called off at threat level 0: " + ThreatData.get(level).attacks(hall));
		helper.assertTrue(Warnings.warned(level, hall).isEmpty(), "the Defense page still shows the called-off attack");
		List<String> wrong = new ArrayList<>();
		if (Warnings.ready(level, hall)) {
			wrong.add("gates and portcullis kept shut from noon (Warnings.ready)");
		}
		if (Warnings.evening(level, hall)) {
			wrong.add("guards sent to battle stations at dusk (Warnings.evening)");
		}
		if (townsman.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).isPresent()) {
			wrong.add("a villager sent indoors at dusk");
		}
		helper.assertTrue(wrong.isEmpty(), "the attack was called off at threat level 0 (the Defense page shows none), yet the village is still braced for it: " + wrong);
		helper.succeed();
	}

	/**
	 * The same over a save and load: warned of tomorrow's attack, the world saved and loaded with the threat level at 0.
	 * On the day the clock's attack is gone and nothing is shut for it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaSwitchWarnSaved1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSwitchWarnSaved1110d")
	public void aWarnedVillageSavedAndLoadedAtThreatLevelZeroShutsNothing(GameTestHelper helper) {
		BlockPos hall = warnedVillage(helper);
		ServerLevel level = helper.getLevel();
		threatLevel(2);
		setAttack(level, hall);
		hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "setup: not warned at level 2: " + ThreatData.get(level).warned(hall));
		threatLevel(0);
		QaRaidCallOff1103cGameTests.reloadThreats(level);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "setup: the warning didn't survive the save: " + ThreatData.get(level).warned(hall));
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // day 12, just past noon
		hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).attacks(hall).isEmpty(), "the attack survived the save at threat level 0: " + ThreatData.get(level).attacks(hall));
		helper.assertTrue(!Warnings.ready(level, hall), "after a save at threat level 0 the village is still braced (gates shut from noon) for an attack that won't come");
		helper.succeed();
	}

	/**
	 * As {@link #aWarnedAttackCalledOffAtThreatLevelZeroShutsNothingOnItsDay}, but the hall's owner sets the village At
	 * peace the morning of the warned day ("no raids ... there"): from noon nothing is shut for the attack.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaSwitchWarnAtPeace1110d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSwitchWarnAtPeace1110d")
	public void aWarnedVillageSetAtPeaceShutsNothingOnItsDay(GameTestHelper helper) {
		BlockPos hall = warnedVillage(helper);
		ServerLevel level = helper.getLevel();
		threatLevel(2);
		setAttack(level, hall);
		hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "setup: not warned at level 2: " + ThreatData.get(level).warned(hall));
		level.setDayTime(BASE + VillageNeeds.DAY + 1000); // the morning of day 12
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setAtPeace(true, Chronicle.day(level));
		helper.assertTrue(Threats.quiet(level, hall), "setup: the village isn't quiet At peace");
		level.setDayTime(BASE + VillageNeeds.DAY + 6100); // noon
		hallRound(level, hall);
		helper.assertTrue(ThreatData.get(level).attacks(hall).isEmpty(), "setup: the attack isn't called off At peace: " + ThreatData.get(level).attacks(hall));
		helper.assertTrue(!Warnings.ready(level, hall), "a village set At peace is still braced from noon (gates shut) for an attack that won't come");
		helper.succeed();
	}

	// The covens' hex.

	/** A village round {@code at} (radius 20), a coven hut by it; everything put back after (as QaCovens1108dGameTests). */
	private static BlockPos covenVillage(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		boolean lairs = Lairs.ENABLED;
		Set<String> off = Threats.DISABLED;
		long time = level.getDayTime();
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4), v -> true).forEach(v -> Sickness.recover(level, v));
			VillageRaids.forget(hall);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Covens.Data.get(level).forget(hall);
			Lairs.forget(level);
			Covens.forget();
			Lairs.ENABLED = lairs;
			Threats.DISABLED = off;
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.assertTrue(Threats.get(COVEN).isPresent(), "setup: expected witch_coven.json loaded, found none");
		Lairs.ENABLED = true;
		return hall;
	}

	private static List<Villager> six(GameTestHelper helper) {
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + 2 * i, 2, 3));
			v.setNoAi(true);
			v.setInvulnerable(true);
			people.add(v);
		}
		return people;
	}

}
