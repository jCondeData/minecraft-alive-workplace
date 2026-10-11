package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
 * A day's warning (ROADMAP 32.14). Each test stands a Village Hall with a finished Gatehouse (the outside to the north)
 * and a Wall Tower beside it, and sets an attack from the north on the hall's clock at a day's dusk, as the threat
 * clock (32.2) does. With an archer posted by the tower the village is told at once, more than 20000 ticks ahead, the
 * chronicle has it and the Defence page and guards icon show it (a scout also counts them); without a guard it isn't
 * told. On the day the gates shut and the portcullis drops from noon, the archer is on the tower before any raider
 * comes, the villagers keep in, and the siege that begins takes the dropped bars over. And the ways it could break: the
 * warning and the bars saved and loaded (and a save from before), the bars going up when no attack came, the
 * {@code villageRaids} and {@code sieges} switches off, and every new sentence a player reads.
 */
public class WarningGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "warned_besiegers");
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	/** The Gatehouse: x 9 to 19, z 5 to 9, its front (the outside) to the north. */
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	/** The Wall Tower: x 21 to 25, z 5 to 9. */
	private static final BlockPos TOWER = new BlockPos(21, 2, 5);
	/** A day far from any other test's: day 11 begins here. */
	private static final long BASE = 10 * VillageNeeds.DAY;
	/** From the north (radians, as atan2(dz, dx)). */
	private static final double NORTH = -Math.PI / 2;

	private record Village(ServerLevel level, BlockPos hall, Culture culture, VillageHallBlockEntity entity) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true; // (off in GameTests by default; the after() puts the config back)
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
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
		finished(helper, StarterBlueprints.GATEHOUSE, GATEHOUSE);
		finished(helper, StarterBlueprints.WALL_TOWER, TOWER);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		ThreatData.get(level).forgetClock(hall);
		return new Village(level, hall, culture, entity);
	}

	private static void finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	/** A guard with a Guard Post of their own two blocks south of them, with {@code off} in the off hand (a bow: an archer; a spyglass: a scout). */
	private static Villager guard(GameTestHelper helper, ItemStack off, int x, int z) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, off);
		return v;
	}

	/** Dusk of day 11: the clock sets tomorrow night's attack from the north, at 13600. */
	private static ThreatData.Attack setAttack(Village village) {
		village.level().setDayTime(BASE + 12100);
		ThreatData.Attack attack = new ThreatData.Attack(12, BESIEGERS, NORTH, 13600);
		ThreatData.get(village.level()).rolled(village.hall(), 11, attack);
		return attack;
	}

	private static long when(ThreatData.Attack attack) {
		return (attack.day() - 1) * VillageNeeds.DAY + attack.at();
	}

	/**
	 * The item's first promise: an archer posted by the Wall Tower sees the attack the clock set at dusk, and the village
	 * is told then, more than 20000 ticks before it comes: who and from the north, in the chronicle, on the Defence page
	 * and the guards icon. A save and load keeps the warning; a save from before 32.14 has none. A scout there counts them.
	 */
	//$ gametest_ticks_batch AREA '200' '"warningTold"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "warningTold")
	public void aWatchedVillageIsWarnedADayAhead(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		Villager archer = guard(helper, new ItemStack(Items.BOW), 16, 14);
		ThreatData.Attack attack = setAttack(village);
		helper.assertTrue(Warnings.source(level, village.hall()).isPresent(), "an archer posted by the Wall Tower is no source");
		Warnings.round(level, village.hall(), 10, 1);
		ThreatData data = ThreatData.get(level);
		helper.assertTrue(data.warned(village.hall()) == 12, "not warned: " + data.warned(village.hall()));
		long ahead = when(attack) - level.getDayTime();
		helper.assertTrue(ahead >= 20000, "warned only " + ahead + " ticks ahead");
		String told = Warnings.message(level, village.hall(), attack, 0).getString();
		helper.assertTrue(told.startsWith("Smoke on the north road: ") && told.endsWith("tomorrow night.") && !told.contains("strong"), "the warning: " + told);
		helper.assertTrue(village.entity().chronicle().stream().anyMatch(e -> e.text().getString().startsWith(archer.getName().getString() + " saw them coming")
			&& e.text().getString().endsWith("from the north")), "the chronicle: " + village.entity().chronicle().stream().map(e -> e.text().getString()).toList());
		helper.assertTrue(Warnings.warned(level, village.hall()).isPresent(), "the warned attack isn't known");
		ItemStack next = DefencePage.next(level, village.hall());
		helper.assertTrue(next.getHoverName().getString().equals("Attack tomorrow night"), "the Defence page: " + next.getHoverName().getString());
		// Told once: the next round says nothing new.
		int entries = village.entity().chronicle().size();
		helper.assertFalse(Warnings.warn(level, village.hall(), 10, 1), "warned twice");
		helper.assertTrue(village.entity().chronicle().size() == entries, "a second chronicle entry");
		// Saved and loaded; a save from before 32.14 has no warning.
		CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
		data.roundTrip(level.registryAccess());
		helper.assertTrue(data.warned(village.hall()) == 12, "the warning after a load: " + data.warned(village.hall()));
		CompoundTag old = saved.copy();
		ListTag clocks = old.getList("clock", Tag.TAG_COMPOUND);
		for (int i = 0; i < clocks.size(); i++) {
			clocks.getCompound(i).remove("warned");
			clocks.getCompound(i).remove("braced");
		}
		data.read(old);
		helper.assertTrue(data.warned(village.hall()) == -1 && data.braced(village.hall()).isEmpty() && data.attacks(village.hall()).size() == 1,
			"an old save: warned " + data.warned(village.hall()));
		// A scout there counts them.
		archer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		helper.assertTrue(Warnings.source(level, village.hall()).map(Warnings.Source::counts).orElse(false), "a scout by the tower doesn't count them");
		helper.assertTrue(Warnings.warn(level, village.hall(), 10, 1), "the scout's warning");
		int count = VillageRaids.expected(level, village.hall(), 10, 1, BESIEGERS);
		String counted = Warnings.message(level, village.hall(), attack, count).getString();
		helper.assertTrue(count > 0 && counted.contains("about " + count + " strong"), "the scout's warning: " + counted);
		helper.succeed();
	}

	/**
	 * Without a source the horn is the only warning: no guard by a tower (a guard posted far off doesn't count), nothing
	 * told, the gates stay open at noon, the Defence page says "No lookout" and What next? says to build one.
	 */
	//$ gametest_ticks_batch AREA '200' '"warningNone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "warningNone")
	public void anUnwatchedVillageIsNotTold(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		for (int i = 0; i < 8; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + i * 2, 2, 22));
			v.setNoAi(true);
		}
		setAttack(village);
		Warnings.round(level, village.hall(), 8, 0);
		helper.assertTrue(ThreatData.get(level).warned(village.hall()) == -1 && Warnings.warned(level, village.hall()).isEmpty(), "warned without a lookout");
		helper.assertTrue(village.entity().chronicle().stream().noneMatch(e -> e.text().getString().contains("saw them coming")), "a warning in the chronicle");
		helper.assertTrue(DefencePage.next(level, village.hall()).getHoverName().getString().equals("No lookout"),
			"the Defence page: " + DefencePage.next(level, village.hall()).getHoverName().getString());
		helper.assertTrue(VillageAdvice.tips(level, village.hall()).stream().anyMatch(t -> t.key().equals("lookout")), "What next? doesn't ask for a lookout");
		helper.assertTrue(VillageAdvice.tips(level, village.hall()).stream().filter(t -> t.key().equals("lookout")).findFirst().orElseThrow().how().getString()
			.equals("Build a Lookout Tower and post a guard by it to see raids coming a day ahead"), "the tip");
		// The day: noon, and the gates are as any day's.
		level.setDayTime(BASE + VillageNeeds.DAY + 6500);
		Gates.round(level, village.hall(), 0);
		Warnings.round(level, village.hall(), 8, 0);
		helper.assertFalse(Warnings.ready(level, village.hall()), "ready without a warning");
		helper.assertFalse(Sieges.portcullisDown(level, village.hall()), "the portcullis dropped without a warning");
		helper.succeed();
	}

	/**
	 * In the warned village, on the day: at noon the gates are shut and the portcullis is down; at dusk the archer is on
	 * the tower within 400 ticks, before the raiders' hour, and the villagers keep in; the siege that begins takes the
	 * dropped bars over, and they go up when it is lifted.
	 */
	//$ gametest_ticks_batch AREA '900' '"warningReady"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "warningReady")
	public void theWarnedVillageIsReadyAtDusk(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		Villager archer = guard(helper, new ItemStack(Items.BOW), 16, 14);
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 22));
		ThreatData.Attack attack = setAttack(village);
		Warnings.round(level, village.hall(), 10, 1);
		helper.assertTrue(Warnings.warned(level, village.hall()).isPresent(), "not warned");
		// The morning: nothing yet.
		level.setDayTime(BASE + VillageNeeds.DAY + 2000);
		Gates.round(level, village.hall(), 1);
		Warnings.round(level, village.hall(), 10, 1);
		helper.assertFalse(Sieges.portcullisDown(level, village.hall()), "the portcullis dropped in the morning");
		// Noon.
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 100);
		Gates.round(level, village.hall(), 1);
		Warnings.round(level, village.hall(), 10, 1);
		Sieges.Status status = Sieges.status(level, village.hall());
		helper.assertTrue(status.gates() > 0 && status.open() == 0, "the gates at noon: " + status);
		helper.assertTrue(Sieges.portcullisDown(level, village.hall()) && !ThreatData.get(level).braced(village.hall()).isEmpty(), "the portcullis at noon");
		// Dusk.
		level.setDayTime(BASE + VillageNeeds.DAY + VillageRaids.DUSK + 10);
		long start = helper.getTick();
		long[] on = {-1};
		helper.onEachTick(() -> {
			if (on[0] >= 0) {
				return;
			}
			if (helper.getTick() - start == 2) {
				Warnings.round(level, village.hall(), 10, 1);
				helper.assertTrue(baker.getBrain().hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME), "the villagers weren't sent in at dusk");
			}
			helper.assertTrue(level.getDayTime() < when(attack), "the raiders' hour came first");
			if (BattleStations.onStation(archer)) {
				on[0] = helper.getTick() - start;
				helper.assertTrue(on[0] <= 400, "on the station after " + on[0] + " ticks");
				helper.assertTrue(BattleStations.post(archer).map(p -> p.getY() > helper.absolutePos(TOWER).getY() + 3).orElse(false), "not up on the tower");
				// The raiders come: the siege takes the dropped bars over, and draws them up when lifted.
				Mob husk = EntityType.HUSK.create(level);
				BlockPos out = helper.absolutePos(new BlockPos(14, 2, 1));
				husk.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 0f, 0f);
				husk.addTag(VillageRaids.TAG);
				husk.setNoAi(true);
				level.addFreshEntity(husk);
				VillageRaids.track(level, village.hall(), village.culture(), List.of(husk));
				ThreatData.Siege siege = Sieges.siege(level, village.hall()).orElseThrow();
				helper.assertTrue(!siege.portcullis.isEmpty() && ThreatData.get(level).braced(village.hall()).isEmpty() && Sieges.portcullisDown(level, village.hall()),
					"the siege didn't take the bars over: " + siege.portcullis.size());
				Sieges.lift(level, siege);
				helper.assertFalse(Sieges.portcullisDown(level, village.hall()), "the bars stayed down after the siege");
				helper.succeed();
			}
		});
	}

	/**
	 * The bars go up after the warned day if no attack came; with {@code villageRaids} off a warned day is not kept, and
	 * with {@code sieges} off no portcullis drops; and every new sentence a player reads is in the language file.
	 */
	//$ gametest_ticks_batch AREA '200' '"warningSwitches"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "warningSwitches")
	public void barsGoUpAndSwitchesHold(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		guard(helper, new ItemStack(Items.BOW), 16, 14);
		setAttack(village);
		Warnings.round(level, village.hall(), 10, 1);
		level.setDayTime(BASE + VillageNeeds.DAY + 7000);
		Warnings.round(level, village.hall(), 10, 1);
		helper.assertTrue(Sieges.portcullisDown(level, village.hall()), "the portcullis didn't drop");
		// The attack never came (its culture switched off, say): the next morning the bars go up and the gates open.
		ThreatData.get(level).drop(village.hall(), 12, true);
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000);
		Warnings.round(level, village.hall(), 10, 1);
		Gates.round(level, village.hall(), 1);
		helper.assertFalse(Sieges.portcullisDown(level, village.hall()), "the bars stayed down the day after");
		helper.assertTrue(ThreatData.get(level).braced(village.hall()).isEmpty() && Sieges.status(level, village.hall()).open() > 0, "the gates stayed shut");
		// villageRaids off: no warned day.
		ThreatData.get(level).warned(village.hall(), 13);
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 7000);
		VillageRaids.ENABLED = false;
		helper.assertFalse(Warnings.ready(level, village.hall()), "ready with raids off");
		VillageRaids.ENABLED = true;
		// sieges off: the gates may shut, but no portcullis drops.
		Sieges.ENABLED = false;
		helper.assertTrue(Warnings.ready(level, village.hall()), "not ready on the warned day");
		helper.assertTrue(Sieges.brace(level, village.hall()) == 0 && !Sieges.portcullisDown(level, village.hall()), "a portcullis dropped with sieges off");
		Sieges.ENABLED = true;
		// Every new sentence.
		Language lang = Language.getInstance();
		for (String key : List.of("message.aliveworkplace.warning.tomorrow", "message.aliveworkplace.warning.tomorrow_count", "message.aliveworkplace.warning.tonight",
			"message.aliveworkplace.warning.tonight_count", "message.aliveworkplace.warning.band", "chronicle.aliveworkplace.warning",
			"screen.aliveworkplace.defence.next_tomorrow", "screen.aliveworkplace.defence.next_tonight", "screen.aliveworkplace.defence.next_who",
			"screen.aliveworkplace.defence.next_ready", "screen.aliveworkplace.defence.next_none", "screen.aliveworkplace.defence.next_watch",
			"screen.aliveworkplace.defence.next_blind", "screen.aliveworkplace.defence.next_blind_hint", "screen.aliveworkplace.hall.attack_tomorrow",
			"screen.aliveworkplace.hall.attack_tonight", "advice.aliveworkplace.lookout", "advice.aliveworkplace.lookout.how")) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		helper.succeed();
	}
}
