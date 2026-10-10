package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Conditions;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The threat engine (ROADMAP 32.2): raider cultures read from datapacks, the roster in its shares with its gear, raids
 * that survive a restart, the threat clock, the conditions toolbox, the chance hook and the cultures' config switches.
 * The test datapack's cultures ask for 1000 villagers, so no other test's raid ever picks them.
 */
public class ThreatGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final long DAY = 24000;
	private static final ResourceLocation WARBAND = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_warband");
	private static final ResourceLocation DAWN_RAIDERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_dawn_raiders");
	private static final ResourceLocation HORDE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_horde");

	private static Culture culture(GameTestHelper helper, ResourceLocation id) {
		Optional<Culture> culture = Threats.get(id);
		helper.assertTrue(culture.isPresent(), id + " isn't loaded: " + Threats.all().stream().map(Culture::id).toList());
		return culture.get();
	}

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	private static String key(net.minecraft.network.chat.Component text) {
		return text.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** A hall with a village radius of 16; the raids, raiders, clock and settings the test changes go when it ends. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean raids = VillageRaids.ENABLED;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			VillageRaids.raiders(level, hall).forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			VillageRaids.ENABLED = raids;
			Threats.DISABLED = off;
			level.setDayTime(time);
		});
		return hall;
	}

	/**
	 * The cultures are read from the datapacks: today's two raids with today's numbers, and the test pack's own; a file
	 * that can't be read is skipped (the others still load), and a key the reader doesn't know is ignored.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void culturesAreReadFromDatapacks(GameTestHelper helper) {
		Culture monsters = culture(helper, Threats.MONSTERS);
		helper.assertTrue(monsters.roster().stream().map(m -> m.entity().getPath() + " " + m.share()).toList().equals(List.of("zombie 50", "skeleton 30", "spider 20")),
			"the monsters' roster: " + monsters.roster());
		helper.assertTrue(monsters.where().minVillagers() == 8 && monsters.where().minVillagers() == VillageRaids.MIN_VILLAGERS && monsters.where().minRank().isEmpty(),
			"the monsters' where: " + monsters.where());
		helper.assertTrue(monsters.arrival() == Culture.Arrival.EDGE && monsters.hours() == Culture.Hours.NIGHT && monsters.lair().isEmpty()
			&& monsters.captain().isEmpty() && !monsters.needsLair(), "the monsters: " + monsters);
		helper.assertTrue(monsters.messages().key("raid", "?").equals("message.aliveworkplace.raid.begins")
			&& monsters.chronicle().key("raid", "?").equals("chronicle.aliveworkplace.raid"), "the monsters' lines: " + monsters.messages());

		Culture bandits = culture(helper, Threats.BANDITS);
		helper.assertTrue(bandits.roster().stream().map(m -> m.entity().getPath() + " " + m.share()).toList().equals(List.of("pillager 50", "vindicator 50")),
			"the bandits' roster: " + bandits.roster());
		helper.assertTrue(bandits.where().minRank().equals(Optional.of(VillageRanks.Rank.VILLAGE)) && bandits.where().minVillagers() == 0,
			"the bandits' where: " + bandits.where());
		helper.assertTrue(bandits.arrival() == Culture.Arrival.LAIR && bandits.needsLair() && bandits.lair().get().structure().equals(BanditCamps.CAMP)
			&& bandits.lair().get().strength() == 6, "the bandits' lair: " + bandits.lair());
		Culture.Captain chief = bandits.captain().orElseThrow();
		helper.assertTrue(chief.entity().getPath().equals("vindicator") && chief.health() == 36
			&& chief.gear().equals(Map.of(EquipmentSlot.HEAD, "minecraft:iron_helmet", EquipmentSlot.CHEST, "minecraft:iron_chestplate")), "the chief: " + chief);
		helper.assertTrue(bandits.messages().key("raid", "?").equals("message.aliveworkplace.raid.bandits")
			&& bandits.chronicle().key("broken_by", "?").equals("chronicle.aliveworkplace.bandit_camp_broken_by")
			&& bandits.name().equals(Optional.of("entity.aliveworkplace.bandit"))
			&& bandits.loot().equals(Optional.of(AliveWorkplace.id("chests/bandit_camp"))), "the bandits' lines and loot: " + bandits);

		// The test datapack's culture, every part of the file.
		Culture warband = culture(helper, WARBAND);
		helper.assertTrue(warband.weight() == 30 && warband.where().minVillagers() == 1000 && warband.roster().size() == 3, "the warband: " + warband);
		Culture.Member husk = warband.roster().get(0);
		helper.assertTrue(husk.role() == Culture.Role.MELEE && husk.gear().equals(Map.of(EquipmentSlot.MAINHAND, "minecraft:iron_sword",
			EquipmentSlot.HEAD, "minecraft:leather_helmet")), "the husk: " + husk);
		helper.assertTrue(warband.roster().get(1).role() == Culture.Role.RANGED && warband.roster().get(2).role() == Culture.Role.RAM, "roles: " + warband.roster());
		helper.assertTrue(warband.captain().get().gear().get(EquipmentSlot.HEAD).equals(Culture.OMINOUS_BANNER) && warband.captain().get().health() == 20
			&& warband.captain().get().names().equals(Optional.of("threat.aliveworkplace_test.test_warband.names")), "the captain: " + warband.captain());
		helper.assertTrue(warband.tactics().equals(List.of("test_charge", "no_such_tactic")), "tactics: " + warband.tactics());
		helper.assertTrue(warband.lair().get().strength() == 7 && warband.lair().get().max() == 7 && warband.lair().get().growth() == 0, "the lair: " + warband.lair());
		helper.assertTrue(warband.messages().key("raid", "?").equals("message.aliveworkplace_test.test_warband.raid")
			&& warband.chronicle().key("raid", "the usual").equals("the usual"), "a key prefix, and none: " + warband.messages());
		helper.assertTrue(culture(helper, DAWN_RAIDERS).hours() == Culture.Hours.UNTIL_NOON, "until_noon");
		Culture horde = culture(helper, HORDE); // only a roster: everything else takes its default
		helper.assertTrue(horde.arrival() == Culture.Arrival.EDGE && horde.hours() == Culture.Hours.NIGHT && horde.roster().get(0).role() == Culture.Role.MELEE,
			"defaults: " + horde);

		// The pack's two broken files were skipped, and the rest loaded all the same.
		for (String bad : List.of("bad_entity", "bad_roster")) {
			helper.assertTrue(Threats.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", bad)).isEmpty(), bad + " was loaded");
		}
		// The same through the reader: text that isn't JSON, wrong values, and keys nobody knows.
		Map<ResourceLocation, String> files = new LinkedHashMap<>();
		String roster = "\"roster\": [{\"entity\": \"minecraft:zombie\", \"share\": 1}]";
		files.put(file("bad_syntax"), "{ \"roster\": [ ");
		files.put(file("bad_share"), "{\"roster\": [{\"entity\": \"minecraft:zombie\", \"share\": 0}]}");
		files.put(file("bad_role"), "{\"roster\": [{\"entity\": \"minecraft:zombie\", \"share\": 1, \"role\": \"wizard\"}]}");
		files.put(file("bad_gear"), "{\"roster\": [{\"entity\": \"minecraft:zombie\", \"share\": 1, \"gear\": {\"head\": \"minecraft:no_such_hat\"}}]}");
		files.put(file("bad_slot"), "{\"roster\": [{\"entity\": \"minecraft:zombie\", \"share\": 1, \"gear\": {\"tail\": \"minecraft:stick\"}}]}");
		files.put(file("bad_arrival"), "{\"arrival\": \"balloon\", " + roster + "}");
		files.put(file("bad_rank"), "{\"where\": {\"min_rank\": \"empire\"}, " + roster + "}");
		files.put(file("bad_missing_roster"), "{\"weight\": 3}");
		files.put(file("odd_keys"), "{\"mood\": \"grim\", \"where\": {\"moon\": \"full\", \"min_villagers\": 3}, " + roster + "}");
		files.put(file("fine"), "{" + roster + "}");
		Map<ResourceLocation, Culture> read = Threats.load(files);
		helper.assertTrue(read.keySet().stream().map(ResourceLocation::getPath).toList().equals(List.of("fine", "odd_keys")), "read: " + read.keySet());
		helper.assertTrue(read.get(ResourceLocation.fromNamespaceAndPath("testpack", "odd_keys")).where().minVillagers() == 3, "the known key beside the unknown one");
		helper.succeed();
	}

	private static ResourceLocation file(String name) {
		return ResourceLocation.fromNamespaceAndPath("testpack", "raider_cultures/" + name + ".json");
	}

	/** A thousand picks from a roster, with a fixed random source, come within 3% of each member's share. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRosterComesInItsShares(GameTestHelper helper) {
		for (ResourceLocation id : List.of(WARBAND, Threats.MONSTERS, Threats.BANDITS)) {
			Culture culture = culture(helper, id);
			RandomSource random = RandomSource.create(3202L);
			Map<Culture.Member, Integer> picked = new HashMap<>();
			for (int i = 0; i < 1000; i++) {
				picked.merge(culture.pick(random), 1, Integer::sum);
			}
			for (Culture.Member member : culture.roster()) {
				double share = 100.0 * member.share() / culture.shares();
				double came = picked.getOrDefault(member, 0) / 10.0;
				helper.assertTrue(Math.abs(came - share) <= 3.0, id + ": " + member.entity() + " came " + came + "% of the time, its share is " + share + "%");
			}
		}
		helper.succeed();
	}

	/**
	 * A raid by the test pack's culture: every raider is one of its roster, in its role, with that role's gear (which
	 * never drops), named as the culture names them and a foe to guards (the hoglins too); the tactic the engine knows
	 * is run and the one it doesn't is skipped.
	 */
	//$ gametest_ticks_batch AREA '100' '"threatRosterRaid"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatRosterRaid")
	public void aRaidSpawnsItsRosterWithItsGear(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(9 + i, 2, 9));
		}
		List<String> calls = new ArrayList<>();
		Threats.registerTactic("test_charge", new Threats.Tactic() {
			@Override
			public void begin(ServerLevel l, BlockPos h, Culture c, List<Mob> raiders) {
				calls.add("begin " + c.id().getPath() + " " + raiders.size());
			}

			@Override
			public void round(ServerLevel l, BlockPos h, Culture c, List<Mob> raiders) {
				calls.add("round " + raiders.size());
			}

			@Override
			public void end(ServerLevel l, BlockPos h, Culture c, boolean fled) {
				calls.add("end " + fled);
			}
		});
		Leftovers.after(helper, () -> Threats.unregisterTactic("test_charge"));
		helper.runAfterDelay(2, () -> {
			Culture warband = culture(helper, WARBAND);
			helper.assertTrue(Threats.tactics(warband).size() == 1, "the tactics the engine knows: " + Threats.tactics(warband).size());
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, warband, RandomSource.create(77L));
			helper.assertTrue(raid != null && raid.raiders() == 16, "the raid: " + raid);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == 16, "raiders about: " + raiders.size());
			Map<String, Integer> came = new HashMap<>();
			for (Mob mob : raiders) {
				ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
				Culture.Member member = warband.roster().stream().filter(m -> m.entity().equals(type)).findFirst().orElse(null);
				helper.assertTrue(member != null, "a raider who isn't of the roster: " + type);
				came.merge(type.getPath(), 1, Integer::sum);
				helper.assertTrue(mob.getTags().contains(member.role().tag()) && Threats.role(mob) == member.role(), type + " without its role: " + mob.getTags());
				member.gear().forEach((slot, item) -> helper.assertTrue(BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(slot).getItem()).toString().equals(item),
					type + " has " + mob.getItemBySlot(slot) + " in " + slot.getName() + ", not " + item));
				helper.assertTrue(mob.isPersistenceRequired() && mob.getCustomName() != null && mob.getCustomName().getString().equals("Bandit"),
					type + " would despawn, or isn't named: " + mob.getCustomName());
				helper.assertTrue(Guards.isFoe(mob), type + " isn't a foe to guards");
			}
			// (the same seed gives the same raid: more husks than strays, as their shares say)
			helper.assertTrue(came.getOrDefault("husk", 0) > came.getOrDefault("stray", 0), "who came: " + came);
			// Gear never drops.
			Mob husk = raiders.stream().filter(m -> m.getType() == EntityType.HUSK).findFirst().orElseThrow();
			CompoundTag saved = husk.saveWithoutId(new CompoundTag());
			ListTag hands = saved.getList("HandDropChances", Tag.TAG_FLOAT);
			ListTag armor = saved.getList("ArmorDropChances", Tag.TAG_FLOAT);
			helper.assertTrue(hands.getFloat(0) == 0f && armor.getFloat(3) == 0f, "the husk's gear may drop: " + hands + " " + armor);
			// A hoglin is no monster to the game: only the raider tag makes it a foe.
			Mob wild = helper.spawn(EntityType.HOGLIN, new BlockPos(3, 2, 3));
			helper.assertFalse(Guards.isFoe(wild), "a hoglin nobody sent is a foe");
			wild.addTag(VillageRaids.TAG);
			helper.assertTrue(Guards.isFoe(wild), "a raiding hoglin isn't a foe");
			wild.discard();
			// The raid is told as a raid, and the known tactic was run with the raiders.
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.RAID && key(e.text()).equals("chronicle.aliveworkplace.raid")),
				"not in the chronicle");
			helper.assertTrue(calls.equals(List.of("begin test_warband 16")), "the tactic: " + calls);
			at(level, 30, 15000);
			VillageRaids.tick(level, hall, 52, 0, -100, day -> { });
			raiders.forEach(Mob::discard);
			VillageRaids.tick(level, hall, 52, 0, -100, day -> { });
			helper.assertTrue(calls.equals(List.of("begin test_warband 16", "round 16", "end false")), "the tactic through the raid: " + calls);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid didn't end");
			helper.succeed();
		});
	}

	/**
	 * A raid saved half-way and loaded again is still on, with its raiders, and ends at dawn as before; a raid of a
	 * culture whose hours are until_noon lasts through the morning. An empty file loads as nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"threatRaidSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatRaidSaved")
	public void aRaidSavedHalfWayCarriesOn(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(9 + i, 2, 9));
		}
		helper.runAfterDelay(2, () -> {
			at(level, 30, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0);
			helper.assertTrue(raid != null && raid.raiders() == 5, "the raid: " + raid);
			ThreatData data = ThreatData.get(level);
			helper.assertTrue(data.isDirty(), "a raid began and nothing is to be saved");
			CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
			ListTag raids = tag.getList("raids", Tag.TAG_COMPOUND);
			helper.assertTrue(raids.size() == 1 && raids.getCompound(0).getString("culture").equals("aliveworkplace:monsters")
				&& raids.getCompound(0).getInt("raiders") == 5, "saved: " + tag);
			raids.getCompound(0).putLong("began", level.getGameTime() - 2000); // (the save is of a raid that began a while ago)
			// The server stops: nothing is left in memory. Then the world is loaded again.
			VillageRaids.forget();
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && !VillageRaids.raided(level, hall, hall), "the raid wasn't forgotten");
			data.read(tag);
			VillageRaids.Raid back = VillageRaids.active(hall).orElse(null);
			helper.assertTrue(back != null && back.raiders() == 5 && back.began() == level.getGameTime() - 2000 && back.hall().equals(hall), "after the load: " + back);
			helper.assertTrue(VillageRaids.raided(level, hall, hall) && VillageRaids.raidArea(level, hall).isPresent(), "the village isn't raided after the load");
			helper.assertTrue(data.raid(hall).get().culture().equals(Threats.MONSTERS), "whose raid: " + data.raid(hall));
			VillageRaids.tick(level, hall, 8, 0, 30, day -> { });
			helper.assertTrue(VillageRaids.active(hall).isPresent() && VillageRaids.raiders(level, hall).size() == 5, "the raid ended in the night");
			at(level, 30, 22500); // dawn
			VillageRaids.tick(level, hall, 8, 0, 30, day -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty(), "the raid didn't end at dawn");
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> key(e.text()).equals("chronicle.aliveworkplace.raid_fled")), "the raiders didn't flee");
			helper.assertTrue(data.save(new CompoundTag(), level.registryAccess()).getList("raids", Tag.TAG_COMPOUND).isEmpty(), "the ended raid is still saved");

			// until_noon: still there at dawn (after a load too), gone at noon.
			at(level, 31, 15000);
			helper.assertTrue(VillageRaids.start(level, hall, 8, 0, Double.NaN, culture(helper, DAWN_RAIDERS), RandomSource.create(5L)) != null, "no raid");
			CompoundTag morning = data.save(new CompoundTag(), level.registryAccess());
			morning.getList("raids", Tag.TAG_COMPOUND).getCompound(0).putLong("began", level.getGameTime() - 2000);
			data.read(morning);
			for (long time : new long[] {22500, 23999}) {
				at(level, 31, time);
				VillageRaids.tick(level, hall, 8, 0, 31, day -> { });
				helper.assertTrue(VillageRaids.active(hall).isPresent(), "until_noon raiders left at " + time);
			}
			at(level, 32, 3000);
			VillageRaids.tick(level, hall, 8, 0, 31, day -> { });
			helper.assertTrue(VillageRaids.active(hall).isPresent(), "until_noon raiders left in the morning");
			at(level, 32, 6100);
			VillageRaids.tick(level, hall, 8, 0, 31, day -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty(), "until_noon raiders stayed past noon");

			// A world from before the threats' file, and an entry without its newer fields.
			ThreatData empty = new ThreatData();
			empty.read(new CompoundTag());
			helper.assertTrue(empty.raids().isEmpty() && empty.attacks(hall).isEmpty() && empty.rolled(hall) == -1, "an empty file isn't empty");
			CompoundTag old = new CompoundTag();
			ListTag list = new ListTag();
			CompoundTag bare = new CompoundTag();
			bare.putLong("hall", hall.asLong());
			list.add(bare);
			old.put("raids", list);
			ListTag clocks = new ListTag();
			clocks.add(bare.copy());
			old.put("clock", clocks);
			empty.read(old);
			helper.assertTrue(empty.raid(hall).map(r -> r.culture().equals(Threats.MONSTERS) && r.raiders() == 0).orElse(false) && empty.rolled(hall) == -1,
				"defaults: " + empty.raid(hall));
			helper.succeed();
		});
	}

	/**
	 * The threat clock: at dusk the hall's round sets the attack for the next night; nothing comes the night it was
	 * rolled; the attack starts the night after, at its hour, a day after the roll; then the village has its rest days.
	 * With raids switched off the clock doesn't run. The clock is saved.
	 */
	//$ gametest_ticks_batch AREA '100' '"threatClock"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatClock")
	public void theClockSetsTheNextNightsAttack(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(9 + i, 2, 9));
		}
		ResourceLocation sure = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "sure_attack");
		Leftovers.after(helper, () -> Threats.removeFactor(sure));
		helper.runAfterDelay(2, () -> {
			ThreatData data = ThreatData.get(level);
			long[] last = {-100};
			Runnable round = () -> VillageRaids.tick(level, hall, 8, 0, last[0], day -> last[0] = day);
			Threats.addFactor(sure, (l, h) -> h.equals(hall) ? 100f : 1f); // every roll for this hall sets an attack
			helper.assertTrue(VillageRaids.chance(level, hall, 8) >= 1f && VillageRaids.chance(level, hall, 7) == 0f, "the chance: " + VillageRaids.chance(level, hall, 8));

			// Switched off (as in every other test): dusk passes and nothing is rolled.
			VillageRaids.ENABLED = false;
			at(level, 40, 12500);
			round.run();
			helper.assertTrue(data.rolled(hall) == -1 && Threats.next(level, hall).isEmpty(), "the clock ran with raids off");

			VillageRaids.ENABLED = true;
			at(level, 40, 11000);
			round.run();
			helper.assertTrue(data.rolled(hall) == -1 && Threats.next(level, hall).isEmpty(), "rolled before dusk");
			at(level, 40, 12000);
			long rolledAt = level.getDayTime();
			round.run();
			ThreatData.Attack attack = Threats.next(level, hall).orElse(null);
			helper.assertTrue(attack != null && attack.day() == 41 && attack.culture().equals(Threats.MONSTERS) && attack.at() >= 13500 && attack.at() < 18000,
				"dusk on day 40 set " + attack);
			helper.assertTrue(data.rolled(hall) == 40 && VillageRaids.active(hall).isEmpty(), "the roll itself started a raid");
			// The clock is saved with the rest.
			data.roundTrip(level.registryAccess());
			helper.assertTrue(Threats.clock(level, hall).equals(List.of(attack)) && data.rolled(hall) == 40, "the clock after a load: " + Threats.clock(level, hall));
			// The night of the roll, and all of the next day: nothing.
			for (long[] when : new long[][] {{40, 12600}, {40, 14000}, {40, 19000}, {40, 23000}, {41, 1000}, {41, 6000}, {41, 12000}, {41, attack.at() - 1}}) {
				at(level, when[0], when[1]);
				round.run();
				helper.assertTrue(VillageRaids.active(hall).isEmpty(), "raiders came on day " + when[0] + " at " + when[1] + ", before their hour (" + attack.at() + ")");
			}
			helper.assertTrue(Threats.clock(level, hall).equals(List.of(attack)), "dusk on day 41 set another attack: " + Threats.clock(level, hall));
			// Its hour, the night after the roll.
			at(level, 41, attack.at());
			round.run();
			helper.assertTrue(VillageRaids.active(hall).isPresent() && last[0] == 41, "the attack didn't come at its hour (last raid day " + last[0] + ")");
			helper.assertTrue(level.getDayTime() - rolledAt >= DAY, "the attack came " + (level.getDayTime() - rolledAt) + " ticks after the roll");
			helper.assertTrue(VillageRaids.raiders(level, hall).size() == 5 && Threats.next(level, hall).isEmpty(), "raiders: " + VillageRaids.raiders(level, hall).size());
			// Fought off; then three days' rest: dusk on day 42 sets nothing, dusk on day 43 sets day 44's.
			VillageRaids.raiders(level, hall).forEach(Mob::discard);
			round.run();
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid didn't end");
			at(level, 42, 12000);
			round.run();
			helper.assertTrue(data.rolled(hall) == 42 && Threats.next(level, hall).isEmpty(), "an attack within the rest days: " + Threats.next(level, hall));
			at(level, 43, 12000);
			round.run();
			helper.assertTrue(Threats.next(level, hall).map(a -> a.day() == 44).orElse(false), "after the rest days: " + Threats.next(level, hall));
			// A culture switched off in the meantime doesn't come.
			ThreatData.Attack second = Threats.next(level, hall).get();
			Threats.DISABLED = Set.of("monsters");
			at(level, 44, second.at());
			round.run();
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && last[0] == 41, "a culture switched off came all the same");
			helper.succeed();
		});
	}

	/**
	 * The conditions toolbox on a real hall: rank, head count, biomes (ids and tags), the coast and the Nether link (a
	 * lit portal, or a finished Nether Gate); bandits camp only where their culture's {@code where} holds; and the chance
	 * hook other systems add to.
	 */
	//$ gametest_ticks_batch AREA '100' '"threatConditions"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatConditions")
	public void conditionsSayWhereACultureComes(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation half = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "half");
		BlueprintData.Placement gate = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(4, 2, 4)), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		Leftovers.after(helper, () -> {
			Threats.removeFactor(half);
			sites.forgetFinished(gate);
		});
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			int[] asked = {0};
			java.util.function.IntSupplier four = () -> {
				asked[0]++;
				return 4;
			};
			helper.assertTrue(Conditions.ANY.test(level, hall, four) && asked[0] == 0, "no conditions: holds, without a head count");
			// Rank.
			Conditions village = conditions("{\"min_rank\": \"village\"}");
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertFalse(village.test(level, hall, four), "a Hamlet passes for a Village");
			helper.assertFalse(BanditCamps.near(level, hall).isPresent(), "a camp already");
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(village.test(level, hall, four) && asked[0] == 0, "a Town doesn't pass for a Village");
			// Bandits camp where their culture says, and nowhere once it's switched off.
			Culture bandits = culture(helper, Threats.BANDITS);
			helper.assertTrue(Threats.fits(level, hall, bandits, four), "bandits don't come to a Town");
			Threats.DISABLED = Set.of("bandits");
			helper.assertFalse(Threats.fits(level, hall, bandits, four) || Threats.enabled(Threats.BANDITS), "bandits come though switched off");
			Threats.DISABLED = Set.of();
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertFalse(Threats.fits(level, hall, bandits, four), "bandits come to a Hamlet");
			// Head count.
			helper.assertTrue(conditions("{\"min_villagers\": 4}").test(level, hall, four) && !conditions("{\"min_villagers\": 5}").test(level, hall, four) && asked[0] == 2,
				"min_villagers (asked " + asked[0] + ")");
			// Biomes: the hall's own, by id and by tag; another's.
			String here = level.getBiome(hall).unwrapKey().orElseThrow().location().toString();
			String other = here.equals("minecraft:desert") ? "minecraft:plains" : "minecraft:desert";
			helper.assertTrue(conditions("{\"biomes\": [\"" + other + "\", \"" + here + "\"]}").test(level, hall, four), "the hall's biome " + here);
			helper.assertFalse(conditions("{\"biomes\": [\"" + other + "\"]}").test(level, hall, four), "another biome than " + here);
			helper.assertTrue(conditions("{\"biomes\": [\"#minecraft:is_overworld\"]}").test(level, hall, four), "a biome tag");
			helper.assertFalse(conditions("{\"biomes\": [\"#minecraft:is_nether\"]}").test(level, hall, four), "another biome tag");
			// The coast: an ocean or a beach within 48 blocks, which this hall's land either has or hasn't.
			boolean sea = false;
			for (int dx = -48; dx <= 48; dx += 16) {
				for (int dz = -48; dz <= 48; dz += 16) {
					var biome = level.getBiome(hall.offset(dx, 0, dz));
					sea |= biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH);
				}
			}
			helper.assertTrue(conditions("{\"coast\": true}").test(level, hall, four) == sea && Conditions.coast(level, hall) == sea, "coast should be " + sea);
			helper.assertTrue(conditions("{\"coast\": false}").test(level, hall, four), "coast: false asks for nothing");
			// The Nether link: nothing; a finished Nether Gate; a lit portal.
			Conditions link = conditions("{\"nether_link\": true}");
			helper.assertFalse(link.test(level, hall, four), "a Nether link out of nowhere");
			sites.recordFinished(StarterBlueprints.NETHER_GATE_2.id(), gate, java.util.UUID.randomUUID());
			helper.assertTrue(link.test(level, hall, four) && Conditions.netherLink(level, hall).equals(Optional.of(gate.origin())), "a finished Nether Gate isn't a link");
			sites.forgetFinished(gate);
			helper.assertFalse(link.test(level, hall, four), "the link outlived its gate");
			for (int x = 14; x <= 17; x++) {
				for (int y = 2; y <= 6; y++) {
					boolean frame = x == 14 || x == 17 || y == 2 || y == 6;
					level.setBlock(helper.absolutePos(new BlockPos(x, y, 14)), (frame ? Blocks.OBSIDIAN : Blocks.NETHER_PORTAL).defaultBlockState(), 18);
				}
			}
			Optional<BlockPos> portal = Conditions.netherLink(level, hall);
			helper.assertTrue(link.test(level, hall, four) && portal.isPresent() && level.getBlockState(portal.get()).is(Blocks.NETHER_PORTAL), "a lit portal isn't a link: " + portal);
			// A culture that comes through the portal steps out of it.
			Culture horde = culture(helper, HORDE);
			Culture through = new Culture(horde.id(), horde.where(), horde.weight(), Culture.Arrival.PORTAL, horde.hours(), horde.roster(), horde.name(), horde.captain(),
				List.of(), horde.lair(), horde.loot(), horde.messages(), horde.chronicle());
			helper.assertTrue(VillageRaids.start(level, hall, 8, 0, Double.NaN, through, RandomSource.create(9L)) != null, "no raid through the portal");
			for (Mob mob : VillageRaids.raiders(level, hall)) {
				helper.assertTrue(mob.blockPosition().distManhattan(portal.get()) <= 6 && mob.isOnPortalCooldown(), "a raider far from the portal: " + mob.blockPosition()
					+ " (the portal: " + portal.get() + ")");
			}
			VillageRaids.raiders(level, hall).forEach(Mob::discard);
			VillageRaids.forget(hall);
			for (int x = 14; x <= 17; x++) {
				for (int y = 2; y <= 6; y++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, y, 14)), Blocks.AIR.defaultBlockState(), 18);
				}
			}
			helper.assertFalse(link.test(level, hall, four), "the link outlived its portal");

			// The chance hook: 1 by itself; what a system adds multiplies the village's chance, and only this village's.
			float usual = VillageRaids.chance(level, hall, 12);
			helper.assertTrue(Threats.chanceFactor(level, hall) == 1f && Math.abs(usual - VillageRaids.nightlyChance(12)) < 1e-6, "the usual: " + usual);
			Threats.addFactor(half, (l, h) -> h.equals(hall) ? 0.5f : 1f);
			helper.assertTrue(Math.abs(Threats.chanceFactor(level, hall) - 0.5f) < 1e-6 && Math.abs(VillageRaids.chance(level, hall, 12) - usual / 2) < 1e-6,
				"halved: " + VillageRaids.chance(level, hall, 12));
			helper.assertTrue(Threats.chanceFactor(level, hall.north(200)) == 1f, "another village's chance changed");
			Threats.removeFactor(half);
			helper.assertTrue(Math.abs(VillageRaids.chance(level, hall, 12) - usual) < 1e-6, "back: " + VillageRaids.chance(level, hall, 12));
			helper.succeed();
		});
	}

	private static Conditions conditions(String json) {
		return Conditions.read(JsonParser.parseString(json).getAsJsonObject(), "a test");
	}

	/**
	 * Config {@code raiderCultures}: a culture set to false is never picked, nor is {@code monsters} with
	 * {@code villageRaids} off or {@code bandits} with {@code banditCamps} off; with every culture off no raid starts.
	 * The file lists every culture, the datapacks' too.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"threatConfig"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "threatConfig")
	public void aCultureSwitchedOffIsNeverPicked(GameTestHelper helper) throws java.io.IOException {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(0, 2, 0));
		Set<String> before = Threats.DISABLED;
		Path dir = Files.createTempDirectory("aliveworkplace-cultures");
		try {
			// The defaults: both of ours listed and on, nothing off.
			WorkplaceConfig defaults = new WorkplaceConfig();
			helper.assertTrue(defaults.raiderCultures.equals(Map.of("monsters", true, "bandits", true)) && defaults.culturesOff().isEmpty(),
				"the defaults: " + defaults.raiderCultures);
			helper.assertTrue(WorkplaceConfig.parse("{}").raiderCultures.equals(defaults.raiderCultures), "a file without the map");
			helper.assertTrue(WorkplaceConfig.parse("{\"raiderCultures\": {\"pack:pirates\": false}}").raiderCultures
				.equals(Map.of("monsters", true, "bandits", true, "pack:pirates", false)), "a short map isn't completed");
			helper.assertTrue(WorkplaceConfig.parse("{\"villageRaids\": false}").culturesOff().equals(Set.of("monsters")), "villageRaids off");
			helper.assertTrue(WorkplaceConfig.parse("{\"banditCamps\": false}").culturesOff().equals(Set.of("bandits")), "banditCamps off");
			helper.assertTrue(WorkplaceConfig.parse("{\"raiderCultures\": {\"bandits\": false, \"monsters\": true}}").culturesOff().equals(Set.of("bandits")), "the map's own switch");

			// With everything on, a big enough village gets each of the cultures that fit, by weight: the horde (15), the
			// monsters (10) and the dawn raiders (5); never the warband, which raids only from its lair.
			Threats.DISABLED = Set.of();
			Map<ResourceLocation, Integer> picked = picks(level, hall, 1000, 3000);
			helper.assertTrue(picked.keySet().equals(Set.of(HORDE, Threats.MONSTERS, DAWN_RAIDERS)), "picked: " + picked);
			helper.assertTrue(Math.abs(picked.get(HORDE) / 3000.0 - 0.5) < 0.03 && Math.abs(picked.get(Threats.MONSTERS) / 3000.0 - 1 / 3.0) < 0.03
				&& Math.abs(picked.get(DAWN_RAIDERS) / 3000.0 - 1 / 6.0) < 0.03, "by weight: " + picked);
			// A village of 8: only the monsters ask for so few.
			helper.assertTrue(picks(level, hall, 8, 200).keySet().equals(Set.of(Threats.MONSTERS)), "a village of 8: " + picks(level, hall, 8, 200));
			helper.assertTrue(picks(level, hall, 7, 200).isEmpty(), "a village of 7 gets someone: " + picks(level, hall, 7, 200));

			// The horde switched off in the config: never picked.
			WorkplaceConfig config = WorkplaceConfig.parse("{\"raiderCultures\": {\"aliveworkplace_test:test_horde\": false}}");
			Threats.DISABLED = config.culturesOff();
			helper.assertFalse(Threats.enabled(HORDE), "the horde is still on");
			picked = picks(level, hall, 1000, 3000);
			helper.assertTrue(picked.keySet().equals(Set.of(Threats.MONSTERS, DAWN_RAIDERS)), "with the horde off: " + picked);
			// Monsters off through their own old switch: a village of 8 is left alone, and an ordered raid doesn't start.
			Threats.DISABLED = WorkplaceConfig.parse("{\"villageRaids\": false}").culturesOff();
			helper.assertTrue(picks(level, hall, 8, 200).isEmpty(), "monsters picked with villageRaids off");
			helper.assertTrue(VillageRaids.cultureFor(level, hall, 8, RandomSource.create(1L)).isEmpty(), "someone comes to a village of 8");
			helper.assertTrue(VillageRaids.start(level, hall, 8, 0) == null && VillageRaids.active(hall).isEmpty(), "a raid with every fitting culture off");

			// The file names every culture: a new one is added, on; what the owner set stays.
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), "{\"guardRadius\": 30, \"raiderCultures\": {\"bandits\": false}}");
			List<String> ids = Threats.all().stream().map(c -> Threats.key(c.id())).toList();
			helper.assertTrue(ids.contains("monsters") && ids.contains("aliveworkplace_test:test_horde"), "how the config names them: " + ids);
			WorkplaceConfig.listCultures(dir, ids);
			WorkplaceConfig written = WorkplaceConfig.load(dir);
			helper.assertTrue(written.raiderCultures.keySet().containsAll(ids) && !written.raiderCultures.get("bandits") && written.raiderCultures.get("monsters")
				&& written.raiderCultures.get("aliveworkplace_test:test_horde") && written.guardRadius == 30, "the file: " + written.raiderCultures);
			String text = Files.readString(dir.resolve(WorkplaceConfig.FILE));
			helper.assertTrue(text.contains("\"aliveworkplace_test:test_warband\": true") && text.contains("\"bandits\": false"), "the file's text: " + text);
		} finally {
			Threats.DISABLED = before;
			try (var files = Files.list(dir)) {
				for (Path p : files.toList()) {
					Files.deleteIfExists(p);
				}
			}
			Files.deleteIfExists(dir);
		}
		helper.succeed();
	}

	private static Map<ResourceLocation, Integer> picks(ServerLevel level, BlockPos hall, int villagers, int times) {
		RandomSource random = RandomSource.create(4242L);
		Map<ResourceLocation, Integer> picked = new HashMap<>();
		for (int i = 0; i < times; i++) {
			Threats.pick(level, hall, villagers, random).ifPresent(c -> picked.merge(c.id(), 1, Integer::sum));
		}
		return picked;
	}
}
