package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;

import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Disaster;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (2026-11-01) for A day's warning (ROADMAP 32.14), written from the spec and the wiki's raids page. What
 * WarningGameTests, QaWarningGameTests, QaSiegeWarnings1031dGameTests and SeerWarningGameTests leave out: a tower that
 * is still a build site (not finished) gives no warning; a villager who isn't a guard, or a guard who died, keeps no
 * watch; a scout out on a mission doesn't count (the plain guard there still warns, without a number); a scout's chat
 * line as a player really reads it, with the number and the side; the guards icon and the Defence page naming who and
 * the side; What next? asking for a lookout until a guard keeps watch; the real dusk roll (VillageRaids.tick, as the
 * hall's round runs it) warning a watched village more than 20000 ticks ahead while an unwatched one hears only the
 * raid; and a disaster foretold "by the Seer" (32.15: 32.14's sources) foretold by the guard on the tower.
 */
public class QaDayWarning1101GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_day_warning_1101");
	private static final ResourceLocation FACTOR = AliveWorkplace.id("qa_day_warning_1101");
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	/** The Wall Tower: x 21 to 25, z 5 to 9. */
	private static final BlockPos TOWER = new BlockPos(21, 2, 5);
	/** Day 31 begins here: a day no other test of the warning uses. */
	private static final long BASE = 30 * VillageNeeds.DAY;
	private static final long ATTACK_DAY = 32;
	private static final double NORTH = -Math.PI / 2;
	private static final double EAST = 0;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
	}

	/** A hall (radius 24) with a clear clock and the test culture loaded; a finished Wall Tower when {@code tower}. */
	private static Village village(GameTestHelper helper, boolean tower) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true; // (the after() puts the config back)
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
			Threats.removeFactor(FACTOR);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			Sieges.raise(level, hall, false);
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
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		if (tower) {
			finished(helper, StarterBlueprints.WALL_TOWER, TOWER);
		}
		ThreatData.get(level).forgetClock(hall);
		return new Village(level, hall, entity);
	}

	private static BlueprintData.Placement place(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		return new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
	}

	private static BlueprintData.Placement finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		BlueprintData.Placement placement = place(helper, build, at);
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		return placement;
	}

	/** A guard with a Guard Post of their own two blocks south of them, {@code off} in the off hand (a bow: an archer; a spyglass: a scout). */
	private static Villager guard(GameTestHelper helper, ItemStack off, int x, int z) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, off);
		return v;
	}

	/** Dusk of day 31: the clock sets day 32's attack (tomorrow night) from {@code angle}, at 13600. */
	private static ThreatData.Attack setAttack(Village village, double angle) {
		village.level().setDayTime(BASE + 12100);
		ThreatData.Attack attack = new ThreatData.Attack(ATTACK_DAY, BESIEGERS, angle, 13600);
		ThreatData.get(village.level()).rolled(village.hall(), ATTACK_DAY - 1, attack);
		return attack;
	}

	/** A player standing by the hall whose chat lines land in {@code heard}. */
	private static ServerPlayer near(GameTestHelper helper, Village village, List<Component> heard, String name) {
		ServerPlayer p = RealmGameTests.listener(helper, heard, name);
		p.setPos(village.hall().getX() + 3.5, village.hall().getY(), village.hall().getZ() + 1.5);
		return p;
	}

	private static List<String> lines(ItemStack icon) {
		List<String> lines = new ArrayList<>();
		lines.add(icon.getHoverName().getString());
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(line -> lines.add(line.getString()));
		}
		return lines;
	}

	private static boolean sawThem(Village village) {
		return village.entity().chronicle().stream().anyMatch(e -> e.text().getString().contains("saw them coming"));
	}

	/**
	 * "Within 24 blocks of a finished Lookout Tower or Wall Tower": a Wall Tower whose blocks stand but is still a
	 * build site (not finished) is no tower to watch from. A guard posted by it gives no warning, the round tells nobody,
	 * nothing goes in the chronicle and the Defence page says "No lookout". Once the same tower is finished, it warns.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDayWarnBuildSite"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDayWarnBuildSite")
	public void aTowerStillBeingBuiltWarnsNobody(GameTestHelper helper) {
		Village village = village(helper, false);
		ServerLevel level = village.level();
		BlueprintData.Placement placement = place(helper, StarterBlueprints.WALL_TOWER, TOWER);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BuildSite site = sites.create(UUID.randomUUID(), "qa", StarterBlueprints.WALL_TOWER.id(), placement);
		Leftovers.after(helper, () -> sites.remove(site.id()));
		guard(helper, new ItemStack(Items.BOW), 16, 14);
		List<Component> heard = new ArrayList<>();
		near(helper, village, heard, "qa-dw-site");
		ThreatData.Attack attack = setAttack(village, NORTH);
		helper.assertTrue(Warnings.towers(level, village.hall()).isEmpty(), "a tower still being built counts as a tower: " + Warnings.towers(level, village.hall()));
		helper.assertTrue(Warnings.source(level, village.hall()).isEmpty(), "a guard by a tower still being built keeps watch");
		heard.clear();
		Warnings.round(level, village.hall(), 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(village.hall()) == -1, "warned from a tower still being built: " + ThreatData.get(level).warned(village.hall()));
		String line = Warnings.message(level, village.hall(), attack, 0).getString();
		helper.assertTrue(heard.stream().noneMatch(c -> c.getString().equals(line)), "the player in the village was told: " + heard.stream().map(Component::getString).toList());
		helper.assertFalse(sawThem(village), "a warning in the chronicle from a tower still being built");
		helper.assertTrue(DefencePage.next(level, village.hall()).getHoverName().getString().equals("No lookout"),
			"the Defence page: " + DefencePage.next(level, village.hall()).getHoverName().getString());
		// The builder finishes it: now it warns.
		sites.remove(site.id());
		sites.recordFinished(StarterBlueprints.WALL_TOWER.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		Warnings.round(level, village.hall(), 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(village.hall()) == ATTACK_DAY, "the finished tower didn't warn: " + ThreatData.get(level).warned(village.hall()));
		helper.assertTrue(heard.stream().filter(c -> c.getString().equals(line)).count() == 1, "the player wasn't told once: " + heard.stream().map(Component::getString).toList());
		helper.succeed();
	}

	/**
	 * "A guard ... whose post is within 24 blocks": a jobless villager standing on the tower's doorstep keeps no watch; a
	 * guard posted there does; once that guard is dead, nobody does, and the round warns no one.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDayWarnOnlyGuards"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDayWarnOnlyGuards")
	public void onlyALivingGuardKeepsWatch(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		Villager idle = helper.spawn(EntityType.VILLAGER, new BlockPos(22, 2, 12));
		idle.setNoAi(true);
		setAttack(village, NORTH);
		helper.assertTrue(Warnings.source(level, village.hall()).isEmpty(), "a jobless villager by the tower keeps watch");
		Villager archer = guard(helper, new ItemStack(Items.BOW), 16, 14);
		helper.assertTrue(Warnings.source(level, village.hall()).map(s -> s.who().getString().equals(archer.getName().getString())).orElse(false),
			"the archer posted by the tower keeps no watch");
		archer.kill();
		helper.assertTrue(Warnings.source(level, village.hall()).isEmpty(), "a dead guard keeps watch");
		Warnings.round(level, village.hall(), 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(village.hall()) == -1, "warned with only a dead guard: " + ThreatData.get(level).warned(village.hall()));
		helper.assertFalse(sawThem(village), "a warning in the chronicle with only a dead guard");
		helper.succeed();
	}

	/**
	 * "A Scout there (the same, with the raiders' number and side)", as the player reads it: with a scout posted by the
	 * Wall Tower and an attack from the east, the player in the village gets exactly one gold line, "Smoke on the east
	 * road: <who>, about N strong, will strike <village> tomorrow night.", N the raid's size; the chronicle names the scout
	 * and the east.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDayWarnScoutChat"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDayWarnScoutChat")
	public void theScoutsWarningGivesTheNumberAndTheSideInChat(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		Villager scout = guard(helper, new ItemStack(Items.SPYGLASS), 16, 14);
		List<Component> heard = new ArrayList<>();
		near(helper, village, heard, "qa-dw-scout");
		ThreatData.Attack attack = setAttack(village, EAST);
		heard.clear();
		Warnings.round(level, village.hall(), 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(village.hall()) == ATTACK_DAY, "not warned: " + ThreatData.get(level).warned(village.hall()));
		int count = VillageRaids.expected(level, village.hall(), 10, 1, BESIEGERS);
		String who = Warnings.who(level, village.hall(), attack).getString();
		String name = VillageHalls.name(level, village.hall()).getString();
		String expected = "Smoke on the east road: " + who + ", about " + count + " strong, will strike " + name + " tomorrow night.";
		List<Component> told = heard.stream().filter(c -> c.getString().startsWith("Smoke on")).toList();
		helper.assertTrue(count > 0 && told.size() == 1 && told.get(0).getString().equals(expected),
			"the scout's warning in chat, expected '" + expected + "': " + heard.stream().map(Component::getString).toList());
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(told.get(0).getStyle().getColor()), "not gold: " + told.get(0).getStyle());
		String chronicle = scout.getName().getString() + " saw them coming: " + who + " from the east";
		helper.assertTrue(village.entity().chronicle().stream().anyMatch(e -> e.text().getString().equals(chronicle)),
			"the chronicle, expected '" + chronicle + "': " + village.entity().chronicle().stream().map(e -> e.text().getString()).toList());
		helper.succeed();
	}

	/**
	 * "A Scout posted there (not out on a mission)": with a scout and an archer by the tower, the scout counts them; sent
	 * out to the lair, the scout no longer keeps watch, the archer does, and the warning the player reads has no number.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDayWarnScoutOut"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDayWarnScoutOut")
	public void aScoutOutOnAMissionLeavesTheArchersWarningWithoutANumber(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		Leftovers.after(helper, () -> {
			Scouts.forget(level);
			Lairs.forget(level);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(96), m -> m.getTags().contains(Lairs.TAG)).forEach(Mob::discard);
		});
		Villager scout = guard(helper, new ItemStack(Items.SPYGLASS), 16, 14);
		scout.setNoAi(true);
		Villager archer = guard(helper, new ItemStack(Items.BOW), 20, 14);
		archer.setNoAi(true);
		// A bandit camp 60 blocks east, as ScoutGameTests stands one.
		BlockPos hall = village.hall();
		int x = hall.getX() + 60;
		int z = hall.getZ();
		level.getChunk(x >> 4, z >> 4);
		BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		Vindicator captain = EntityType.VINDICATOR.create(level);
		captain.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		captain.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
		captain.setNoAi(true);
		captain.setPersistenceRequired();
		captain.setInvulnerable(true);
		captain.addTag(Lairs.TAG);
		captain.addTag(Lairs.CAPTAIN_TAG);
		level.addFreshEntity(captain);
		Lairs.putForTest(level, new Lairs.Lair(pos, hall, captain.getUUID(), Chronicle.day(level), Threats.BANDITS, 7, Chronicle.day(level), 0, 0, 3));
		helper.assertTrue(Warnings.source(level, hall).map(s -> s.counts() && s.who().getString().equals(scout.getName().getString())).orElse(false),
			"the scout at home isn't the one who counts them");
		level.setDayTime(BASE + 1000);
		Scouts.Sent sent = Scouts.send(level, hall);
		helper.assertTrue(sent == Scouts.Sent.SENT && Scouts.out(scout), "the scout wasn't sent: " + sent);
		List<Component> heard = new ArrayList<>();
		near(helper, village, heard, "qa-dw-out");
		ThreatData.Attack attack = setAttack(village, NORTH);
		helper.assertTrue(Scouts.out(scout), "the scout came home by dusk");
		helper.assertTrue(Warnings.source(level, hall).map(s -> !s.counts() && s.who().getString().equals(archer.getName().getString())).orElse(false),
			"with the scout out, the archer isn't the source: " + Warnings.source(level, hall).map(s -> s.who().getString() + " counts " + s.counts()));
		heard.clear();
		Warnings.round(level, hall, 10, 1);
		String expected = Warnings.message(level, hall, attack, 0).getString();
		List<String> told = heard.stream().map(Component::getString).filter(s -> s.startsWith("Smoke on")).toList();
		helper.assertTrue(told.equals(List.of(expected)) && !expected.contains("strong") && expected.startsWith("Smoke on the north road: "),
			"the archer's warning, expected '" + expected + "': " + told);
		helper.assertTrue(village.entity().chronicle().stream().anyMatch(e -> e.text().getString().startsWith(archer.getName().getString() + " saw them coming")),
			"the chronicle doesn't name the archer: " + village.entity().chronicle().stream().map(e -> e.text().getString()).toList());
		helper.succeed();
	}

	/**
	 * "The guards icon and the Defence page show the next attack and its side": before a warning the guards icon has no
	 * raiders line; warned of an attack from the east, it reads "Raiders tomorrow night, from the east" and the Defence
	 * page's slot "Attack tomorrow night" with "<who>, from the east"; on the day both say tonight.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDayWarnIcons"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDayWarnIcons")
	public void theGuardsIconAndDefencePageShowWhoAndTheSide(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		guard(helper, new ItemStack(Items.BOW), 16, 14);
		ServerPlayer viewer = near(helper, village, new ArrayList<>(), "qa-dw-icons");
		helper.assertTrue(DefencePage.open(), "the Defence page is closed in tests");
		ThreatData.Attack attack = setAttack(village, EAST);
		List<String> before = lines(VillageHallScreen.forTest(viewer, hall).icon(VillageHallScreen.GUARDS));
		helper.assertTrue(before.stream().noneMatch(l -> l.startsWith("Raiders ")), "the guards icon before any warning: " + before);
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(hall) == ATTACK_DAY, "not warned");
		String who = Warnings.who(level, hall, attack).getString();
		List<String> icon = lines(VillageHallScreen.forTest(viewer, hall).icon(VillageHallScreen.GUARDS));
		helper.assertTrue(icon.contains("Raiders tomorrow night, from the east"), "the guards icon: " + icon);
		List<String> page = lines(DefencePage.next(level, hall));
		helper.assertTrue(page.get(0).equals("Attack tomorrow night") && page.contains(who + ", from the east"), "the Defence page: " + page);
		// The day itself.
		level.setDayTime(BASE + VillageNeeds.DAY + 1000);
		icon = lines(VillageHallScreen.forTest(viewer, hall).icon(VillageHallScreen.GUARDS));
		helper.assertTrue(icon.contains("Raiders tonight, from the east"), "the guards icon on the day: " + icon);
		page = lines(DefencePage.next(level, hall));
		helper.assertTrue(page.get(0).equals("Attack tonight") && page.contains(who + ", from the east"), "the Defence page on the day: " + page);
		helper.succeed();
	}

	/**
	 * "Without a source ... What next? says to build a Lookout Tower and post a guard by it": a village of 8 with a
	 * finished Wall Tower but nobody posted by it is asked; once an archer is posted there, the tip is gone and the
	 * Defence page says who keeps watch.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDayWarnAdvice"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDayWarnAdvice")
	public void whatNextAsksForALookoutUntilAGuardKeepsWatch(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		for (int i = 0; i < 8; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + i * 2, 2, 22));
			v.setNoAi(true);
		}
		level.setDayTime(BASE + 1000);
		helper.assertTrue(VillageAdvice.tips(level, hall).stream().anyMatch(t -> t.key().equals("lookout")),
			"What next? doesn't ask for a lookout with nobody by the tower: " + VillageAdvice.tips(level, hall).stream().map(VillageAdvice.Tip::key).toList());
		Villager archer = guard(helper, new ItemStack(Items.BOW), 16, 14);
		archer.setNoAi(true);
		helper.assertTrue(VillageAdvice.tips(level, hall).stream().noneMatch(t -> t.key().equals("lookout")),
			"What next? still asks for a lookout with an archer posted by the tower");
		List<String> page = lines(DefencePage.next(level, hall));
		String watch = archer.getName().getString() + " keeps watch from a tower: a raid is seen a day ahead";
		helper.assertTrue(page.get(0).equals("No attack seen coming") && page.contains(watch), "the Defence page: " + page);
		helper.succeed();
	}

	/**
	 * The real entry point: the clock's own dusk roll (VillageRaids.tick, then Warnings.round, in the hall round's order)
	 * with the chance made certain. A watched village is told of the attack it sets the moment it is set, at least 20000
	 * ticks before it comes, once, with the side the clock set.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDayWarnRealRoll"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDayWarnRealRoll")
	public void theClocksDuskRollIsToldAtOnceToAWatchedVillage(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		guard(helper, new ItemStack(Items.BOW), 16, 14);
		List<Component> heard = new ArrayList<>();
		near(helper, village, heard, "qa-dw-roll");
		Threats.addFactor(FACTOR, (l, h) -> h.equals(hall) ? 100f : 1f);
		level.setDayTime(BASE + VillageRaids.DUSK + 100);
		heard.clear();
		VillageRaids.tick(level, hall, 10, 1, -100, d -> { });
		List<ThreatData.Attack> attacks = ThreatData.get(level).attacks(hall);
		helper.assertTrue(attacks.size() == 1 && attacks.get(0).day() == ATTACK_DAY, "the dusk roll set no attack for tomorrow: " + attacks);
		Warnings.round(level, hall, 10, 1);
		ThreatData.Attack attack = attacks.get(0);
		helper.assertTrue(ThreatData.get(level).warned(hall) == ATTACK_DAY, "not warned of the attack the clock set");
		long ahead = (attack.day() - 1) * VillageNeeds.DAY + attack.at() - level.getDayTime();
		helper.assertTrue(ahead >= 20000, "warned only " + ahead + " ticks ahead");
		String side = Warnings.side(hall, attack).getString();
		String expected = Warnings.message(level, hall, attack, 0).getString();
		List<String> told = heard.stream().map(Component::getString).filter(s -> s.startsWith("Smoke on")).toList();
		helper.assertTrue(told.equals(List.of(expected)) && expected.startsWith("Smoke on the " + side + " road: ") && expected.endsWith(" tomorrow night."),
			"the player's line, expected '" + expected + "': " + told);
		// The hall's next rounds that evening tell nobody again.
		VillageRaids.tick(level, hall, 10, 1, -100, d -> { });
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(heard.stream().filter(c -> c.getString().startsWith("Smoke on")).count() == 1, "told twice");
		helper.succeed();
	}

	/**
	 * "One without isn't told until it starts": the same real dusk roll in a village with a tower but no guard sets the
	 * attack and tells nobody, in chat or the chronicle; at the attack's hour the raid starts, still with no warning.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDayWarnRealRollBlind"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDayWarnRealRollBlind")
	public void anUnwatchedVillageHearsNothingUntilTheRaid(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		List<Component> heard = new ArrayList<>();
		near(helper, village, heard, "qa-dw-blind");
		Threats.addFactor(FACTOR, (l, h) -> h.equals(hall) ? 100f : 1f);
		level.setDayTime(BASE + VillageRaids.DUSK + 100);
		heard.clear();
		VillageRaids.tick(level, hall, 10, 1, -100, d -> { });
		Warnings.round(level, hall, 10, 1);
		List<ThreatData.Attack> attacks = ThreatData.get(level).attacks(hall);
		helper.assertTrue(attacks.size() == 1 && attacks.get(0).day() == ATTACK_DAY, "the dusk roll set no attack for tomorrow: " + attacks);
		helper.assertTrue(ThreatData.get(level).warned(hall) == -1 && Warnings.warned(level, hall).isEmpty(), "warned without a lookout");
		helper.assertTrue(heard.stream().noneMatch(c -> c.getString().startsWith("Smoke on")), "told without a lookout: " + heard.stream().map(Component::getString).toList());
		helper.assertFalse(sawThem(village), "a warning in the chronicle without a lookout");
		// Noon of the day: still nothing; the attack's hour: the raid.
		level.setDayTime(BASE + VillageNeeds.DAY + 7000);
		Warnings.round(level, hall, 10, 1);
		helper.assertFalse(Warnings.ready(level, hall), "ready on the day without a warning");
		level.setDayTime(BASE + VillageNeeds.DAY + attacks.get(0).at() + 5);
		VillageRaids.tick(level, hall, 10, 1, -100, d -> { });
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "the attack the clock set didn't start at its hour");
		helper.assertTrue(heard.stream().noneMatch(c -> c.getString().startsWith("Smoke on")) && ThreatData.get(level).warned(hall) == -1,
			"a warning came with the raid");
		helper.succeed();
	}

	/**
	 * 32.15: "foretold_by (jobs, or the Seer: 32.14's sources)". A disaster foretold by "seer" starts at once in a village
	 * with nobody by its tower; with an archer posted by the finished Wall Tower it is foretold a day ahead, and the
	 * chronicle says the archer foretold it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaDayWarnDisaster"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaDayWarnDisaster")
	public void aDisasterForetoldByTheSeerIsForetoldByTheGuardOnTheTower(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		VillageHallBlockEntity entity = village.entity();
		Disaster omen = Disaster.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_omen_1101"), com.google.gson.JsonParser.parseString(
			"{\"chance\": 0, \"duration\": {\"days\": 2}, \"effects\": [{\"type\": \"mood\", \"amount\": -1}], \"foretold_by\": [\"seer\"], \"foretell_days\": 1,"
				+ " \"kind\": \"drought\"}").getAsJsonObject());
		Leftovers.after(helper, () -> {
			Disasters.end(level, hall, entity, null, Chronicle.day(level));
			entity.setDisaster(null, -1, -1, -1);
		});
		level.setDayTime(BASE + 1000);
		long day = Chronicle.day(level);
		helper.assertTrue(Disasters.foreteller(level, hall, omen, VillageHalls.census(level, hall)).isEmpty(), "foretold with nobody by the tower");
		Disasters.start(level, hall, entity, omen, VillageHalls.census(level, hall), RandomSource.create(1L));
		helper.assertTrue(entity.disasterSince() == day, "without a source it doesn't start at once: since " + entity.disasterSince() + ", today " + day);
		Disasters.end(level, hall, entity, omen, day);
		entity.setDisaster(null, -1, -1, -1);
		Villager archer = guard(helper, new ItemStack(Items.BOW), 16, 14);
		helper.assertTrue(Disasters.foreteller(level, hall, omen, VillageHalls.census(level, hall)).map(c -> c.getString().equals(archer.getName().getString())).orElse(false),
			"the archer by the tower doesn't foretell a disaster foretold by the Seer's sources");
		Disasters.start(level, hall, entity, omen, VillageHalls.census(level, hall), RandomSource.create(1L));
		helper.assertTrue(entity.disasterSince() == day + 1, "not foretold a day ahead: since " + entity.disasterSince() + ", today " + day);
		String line = archer.getName().getString() + " foretold " + Disasters.name(omen).getString() + ".";
		helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.text().getString().equals(line)),
			"the chronicle, expected '" + line + "': " + entity.chronicle().stream().map(e -> e.text().getString()).toList());
		helper.succeed();
	}
}
