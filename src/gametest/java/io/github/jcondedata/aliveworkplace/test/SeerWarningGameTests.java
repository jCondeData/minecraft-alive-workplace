package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * B133: the Seer (29.16) is a source of 32.14's warning, two days ahead. A village with a settled Seer, a finished
 * Gatehouse and a player by the hall: at a dawn the Seer's foretelling rolls the threat clock two nights ahead, and when
 * it sets an attack the village is told at once, two days before (the chat, the chronicle, the Defence page and the
 * guards icon), as a guard by a tower tells a day before. On the day the Seer foretells it for tonight, the gates shut and
 * the portcullis drops from noon, the villagers go in at dusk, and the raiders come at the hour and from the side told.
 * The warning and the clock survive a save and load; with the Legends switch off the Seer is no source and nothing is
 * told; and every new sentence is in the language file. Each dawn's randomness is a fixed {@link RandomSource}.
 */
public class SeerWarningGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation RAIDERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "foretold_raiders");
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	/** The Gatehouse: x 9 to 19, z 5 to 9, its front (the outside) to the north. */
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final long DAY = VillageNeeds.DAY;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Legend seer, Villager seerVillager,
						   List<Villager> villagers, List<Component> heard) {
	}

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/**
	 * The village: the hall, a finished Gatehouse, a settled Seer and four villagers, raids on with one test culture that
	 * fits any village, a player by the hall who keeps what they're told. Everything is put back when the test ends.
	 */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		long time = level.getDayTime();
		int radius = VillageHalls.RADIUS;
		int min = VillageRaids.MIN_VILLAGERS;
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			VillageRaids.raiders(level, hall).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			Sieges.raise(level, hall, false);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Sieges.forget();
			recorded.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			VillageHalls.RADIUS = radius;
			VillageRaids.MIN_VILLAGERS = min;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true; // (off in GameTests by default; the after() puts the config back)
		VillageRaids.MIN_VILLAGERS = 2;
		Culture culture = Culture.read(RAIDERS, com.google.gson.JsonParser.parseString("{\"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": []}").getAsJsonObject());
		Threats.setForTest(Map.of(RAIDERS, culture));
		Legend seer = Legends.get(Seer.ID).orElse(null);
		helper.assertTrue(seer != null, "seer.json not loaded");
		Legends.setForTest(Map.of(seer.id(), seer));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(GATEHOUSE);
		level.getStructureManager().get(StarterBlueprints.GATEHOUSE.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GATEHOUSE.id(), placement, UUID.randomUUID());
		recorded.add(placement);
		ThreatData.get(level).forgetClock(hall);
		Villager s = helper.spawn(EntityType.VILLAGER, HALL.east(2));
		s.setNoAi(true);
		Legends.make(level, s, seer, "test");
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(6 + i * 2, 2, 22));
			v.setNoAi(true);
			villagers.add(v);
		}
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, heard, "SeerWatcher");
		player.moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		return new Village(level, hall, entity, seer, s, villagers, heard);
	}

	/** The hall's dawn round, as the hall runs it: the Seer foretells (LegendGuests' round), then the warning (32.14). */
	private static void dawn(Village v, long day, RandomSource random) {
		at(v.level(), day, 300);
		LegendGuests.round(v.level(), v.hall(), random);
		Warnings.round(v.level(), v.hall(), VillageHalls.census(v.level(), v.hall()).villagers(), 0);
	}

	private static List<String> strings(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	/**
	 * The fix's promise: a Seer's foretelling of an attack gives the village its warning two days ahead, with the chat,
	 * the chronicle, the Defence page and the guards icon; on the day the Seer foretells it, the gates shut and the
	 * portcullis drops at noon, the villagers go in at dusk and the raiders come at the hour and from the side told. The
	 * warning and the clock are saved and loaded.
	 */
	//$ gametest_ticks_batch AREA '200' '"seerWarning"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "seerWarning")
	public void aSeersFortellingWarnsTheVillageTwoDaysAhead(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		BlockPos hall = v.hall();
		ThreatData data = ThreatData.get(level);
		helper.assertTrue(Legends.foretold(level, hall, Seer.RAID) == 2, "the Seer's warning days: " + Legends.foretold(level, hall, Seer.RAID));
		Optional<Warnings.Source> source = Warnings.source(level, hall, 2);
		helper.assertTrue(source.map(s -> s.who().getString().equals(v.seerVillager().getName().getString()) && s.days() == 2).orElse(false),
			"the Seer is no source two days ahead: " + source);
		helper.assertTrue(DefencePage.next(level, hall).getHoverName().getString().equals("No attack seen coming"),
			"the Defence page with the Seer watching: " + DefencePage.next(level, hall).getHoverName().getString());
		// Each dawn the Seer foretells and the clock is rolled two nights ahead; wait for a dawn that sets an attack two days off.
		RandomSource random = RandomSource.create(13313L);
		ThreatData.Attack attack = null;
		long told = -1;
		for (long day = 20; day < 140 && attack == null; day++) {
			v.heard().clear();
			v.entity().setLastRaidDay(-100);
			dawn(v, day, random);
			helper.assertTrue(data.rolled(hall) == day + 1, "day " + day + ": the clock is rolled through " + (data.rolled(hall) + 1) + ", not two nights ahead");
			final long today = day;
			Optional<ThreatData.Attack> far = data.attacks(hall).stream().filter(a -> a.day() == today + 2).findFirst();
			if (far.isPresent() && data.warned(hall) == today + 2) {
				attack = far.get();
				told = day;
			}
		}
		helper.assertTrue(attack != null, "no attack set two days ahead in 120 dawns (chance " + VillageRaids.chance(level, hall, 5) + ")");
		final ThreatData.Attack set = attack;
		helper.assertTrue(set.culture().equals(RAIDERS), "who comes: " + set.culture());
		long ahead = (set.day() - 1) * DAY + set.at() - level.getDayTime();
		helper.assertTrue(ahead >= 2 * DAY, "warned only " + ahead + " ticks ahead");
		String side = Warnings.side(hall, set).getString();
		String village = VillageHalls.name(level, hall).getString();
		String line = "A vision of the " + side + " road: Foretold Raiders will strike " + village + " in 2 days.";
		String said = Warnings.message(level, hall, set, 0).getString();
		helper.assertTrue(said.startsWith("A vision of the " + side + " road: ") && said.endsWith(" will strike " + village + " in 2 days."), "the warning: " + said);
		helper.assertTrue(strings(v.heard()).contains(said), "the player wasn't told: " + strings(v.heard()) + " (expected " + said + ", like " + line + ")");
		String seerName = v.seerVillager().getName().getString();
		helper.assertTrue(v.entity().chronicle().stream().anyMatch(e -> e.text().getString().startsWith(seerName + " saw them coming")
			&& e.text().getString().endsWith("from the " + side)), "the chronicle: " + v.entity().chronicle().stream().map(e -> e.text().getString()).toList());
		helper.assertTrue(DefencePage.next(level, hall).getHoverName().getString().equals("Attack in 2 days"),
			"the Defence page: " + DefencePage.next(level, hall).getHoverName().getString());
		// Told once.
		int entries = v.entity().chronicle().size();
		helper.assertFalse(Warnings.warn(level, hall, 5, 0), "warned twice");
		helper.assertTrue(v.entity().chronicle().size() == entries, "a second chronicle entry");
		// Saved and loaded: the warning, the attack and how far the clock is rolled; and the Seer's foretelling.
		CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(!saved.isEmpty(), "nothing saved");
		long rolled = data.rolled(hall);
		data.roundTrip(level.registryAccess());
		helper.assertTrue(data.warned(hall) == set.day() && data.rolled(hall) == rolled && data.attacks(hall).contains(set),
			"after a load: warned " + data.warned(hall) + ", rolled " + data.rolled(hall) + ", attacks " + data.attacks(hall));
		CompoundTag hallTag = new CompoundTag();
		v.entity().seer().save(hallTag);
		helper.assertTrue(Seer.State.load(hallTag).equals(v.entity().seer()), "the foretelling didn't save");
		// The next dawn: nothing new is told, and the night after is not rolled into a second attack (raiders rest).
		final long next = told + 1;
		dawn(v, next, random);
		helper.assertTrue(data.warned(hall) == set.day() && data.attacks(hall).stream().filter(a -> a.day() >= next).count() == 1,
			"the next dawn: warned " + data.warned(hall) + ", attacks " + data.attacks(hall));
		helper.assertFalse(Warnings.ready(level, hall), "ready the day before");
		// The day: the Seer foretells raiders tonight, from the side the warning named.
		long day = set.day();
		dawn(v, day, random);
		helper.assertTrue(v.entity().seer().raid() && v.entity().seer().night().side(hall).getString().equals(side) && v.entity().seer().raidAt() == set.at(),
			"the Seer's dawn on the day: raid " + v.entity().seer().raid() + " from the " + v.entity().seer().night().side(hall).getString());
		helper.assertTrue(strings(Seer.lines(level, hall, v.entity().seer())).get(0).equals("  Raiders will come tonight, from the " + side + "."),
			"the Seer's line: " + strings(Seer.lines(level, hall, v.entity().seer())));
		// Noon: the gates shut and the portcullis drops.
		at(level, day, Warnings.NOON + 100);
		Gates.round(level, hall, 0);
		Warnings.round(level, hall, 5, 0);
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(status.gates() > 0 && status.open() == 0, "the gates at noon: " + status);
		helper.assertTrue(Sieges.portcullisDown(level, hall), "the portcullis at noon");
		helper.assertTrue(DefencePage.next(level, hall).getHoverName().getString().equals("Attack tonight"), "the Defence page on the day");
		// Dusk: the villagers go in.
		at(level, day, VillageRaids.DUSK + 10);
		Warnings.round(level, hall, 5, 0);
		helper.assertTrue(v.villagers().stream().allMatch(x -> x.getBrain().hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME)), "the villagers weren't sent in at dusk");
		// Not before the hour; at it, the raiders come, from the side told.
		VillageHallBlockEntity entity = v.entity();
		if (set.at() > 13600) {
			at(level, day, 13500);
			VillageRaids.tick(level, hall, 5, 0, entity.lastRaidDay(), entity::setLastRaidDay);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid came before its hour " + set.at());
		}
		at(level, day, set.at() + 1);
		VillageRaids.tick(level, hall, 5, 0, entity.lastRaidDay(), entity::setLastRaidDay);
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "the raid warned of didn't come at " + set.at());
		helper.assertTrue(entity.lastRaidDay() == day && entity.seer().raidStarted(), "the raid isn't counted");
		helper.assertTrue(data.raid(hall).map(r -> r.culture().equals(RAIDERS)).orElse(false), "not the raiders the clock set: " + data.raid(hall));
		helper.assertTrue(data.attacks(hall).stream().noneMatch(a -> a.day() == day), "the attack that came is still on the clock");
		helper.succeed();
	}

	/**
	 * With the Legends switch off the Seer is no source: dawns go by with an attack on the clock and nothing is told, the
	 * clock isn't rolled ahead for the Seer, and What next? asks for a lookout. Switched on again, the same village is
	 * told (so the test can tell the two apart). And every new sentence a player reads is in the language file.
	 */
	//$ gametest_ticks_batch AREA '200' '"seerWarningOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "seerWarningOff")
	public void withLegendsOffTheSeerGivesNoWarning(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		BlockPos hall = v.hall();
		ThreatData data = ThreatData.get(level);
		Legend seer = v.seer();
		Legends.ENABLED = false; // the config's `legends` (the after() puts the config back)
		helper.assertTrue(Legends.foretold(level, hall, Seer.RAID) == 0 && Warnings.source(level, hall).isEmpty(), "a source with Legends off");
		RandomSource random = RandomSource.create(13313L);
		for (long day = 20; day < 26; day++) {
			v.entity().setLastRaidDay(-100);
			data.rolled(hall, day - 1, new ThreatData.Attack(day + 1, RAIDERS, -Math.PI / 2, 15000)); // an attack on the clock for tomorrow night
			dawn(v, day, random);
			data.forgetClock(hall);
		}
		helper.assertTrue(data.warned(hall) == -1, "warned with Legends off: " + data.warned(hall));
		helper.assertTrue(v.heard().stream().noneMatch(c -> c.getString().contains(" will strike ")), "told with Legends off: " + strings(v.heard()));
		helper.assertTrue(v.entity().chronicle().stream().noneMatch(e -> e.text().getString().contains("saw them coming")), "a warning in the chronicle");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.VillageAdvice.tips(level, hall).stream().anyMatch(t -> t.key().equals("lookout")),
			"What next? doesn't ask for a lookout with the Seer's power off");
		// On again (the roster as the test set it): the same attack is told, from the Seer.
		Legends.ENABLED = true;
		Legends.setForTest(Map.of(seer.id(), seer));
		LegendPowers.forget();
		data.rolled(hall, 26, new ThreatData.Attack(28, RAIDERS, -Math.PI / 2, 15000));
		at(level, 27, 300);
		helper.assertTrue(Warnings.warn(level, hall, 5, 0) && data.warned(hall) == 28, "not told with Legends on");
		// Every new sentence.
		Language lang = Language.getInstance();
		for (String key : List.of("message.aliveworkplace.warning.later", "message.aliveworkplace.warning.later_count", "message.aliveworkplace.warning.sea_later",
			"message.aliveworkplace.warning.sea_later_count", "screen.aliveworkplace.defence.next_later", "screen.aliveworkplace.hall.attack_later")) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		helper.succeed();
	}
}
