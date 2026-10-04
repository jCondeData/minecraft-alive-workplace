package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Edicts and Long Shifts (ROADMAP 30.3): loaded from data (ours with lang texts, a test pack's plain ones, switched off
 * with {@code "enabled": false}), one slot per rank, three days before lifting, mutual exclusion, the newest lapsing on
 * a rank drop, kept over a save, only the owner's side may proclaim, and Long Shifts' 20% faster work and 10 less mood.
 * The test pack's edicts are in src/gametest/resources/data/aliveworkplace_test/edicts.
 */
public class EdictGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation LONG_SHIFTS = AliveWorkplace.id("long_shifts");
	private static final String DRILL = "aliveworkplace_test:test_drill";
	private static final String QUIET = "aliveworkplace_test:test_quiet";
	private static final String FAIR = "aliveworkplace_test:test_fair";

	/** Our Long Shifts loads with its lang texts, the test pack's edicts with plain ones; broken files are refused by field. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void edictsLoadFromDataWithTheirTexts(GameTestHelper helper) {
		Edicts.Edict shifts = Edicts.get(LONG_SHIFTS).orElse(null);
		helper.assertTrue(shifts != null, "Long Shifts didn't load: " + Edicts.all());
		helper.assertTrue(shifts.name().getString().equals("Long Shifts"), "name: " + shifts.name().getString());
		helper.assertTrue(shifts.description().getString().equals("Everyone works 20% faster; every grown villager is 10 less happy."),
			"description: " + shifts.description().getString());
		helper.assertTrue(shifts.icon().equals(ResourceLocation.withDefaultNamespace("clock")), "icon: " + shifts.icon());
		helper.assertTrue(shifts.boost().size() == 1 && shifts.boost().get(0) instanceof CivicEffects.WorkPace pace && pace.percent() == 20
			&& pace.jobs().isEmpty(), "boost: " + shifts.boost());
		helper.assertTrue(shifts.cost().size() == 1 && shifts.cost().get(0) instanceof CivicEffects.Mood mood && mood.points() == -10
			&& mood.reason().getString().equals("long shifts") && mood.when() == CivicEffects.When.ALWAYS, "cost: " + shifts.cost());
		Edicts.Edict drill = Edicts.find(DRILL).orElse(null);
		helper.assertTrue(drill != null && drill.name().getString().equals("Builders' Drill"), "the test pack's edict: " + drill);
		helper.assertTrue(Edicts.find("long_shifts").isPresent(), "ours by their short id");
		helper.assertTrue(Edicts.find(QUIET).orElseThrow().excludes(shifts) && shifts.excludes(Edicts.find(QUIET).orElseThrow()),
			"exclusion counts both ways");

		// A broken file names its field; an unknown effect type is refused.
		String missing = refusal(helper, "{\"cost\": []}");
		helper.assertTrue(missing.contains("name"), "missing name: " + missing);
		String type = refusal(helper, "{\"name\": \"X\", \"boost\": [{\"type\": \"aliveworkplace:nonsense\"}]}");
		helper.assertTrue(type.contains("nonsense"), "unknown type: " + type);
		helper.assertTrue(Edicts.read(AliveWorkplace.id("off"), JsonParser.parseString("{\"enabled\": false}")) == null, "switched off");

		// One slot a rank.
		helper.assertTrue(Edicts.slots(VillageRanks.Rank.HAMLET) == 1 && Edicts.slots(VillageRanks.Rank.VILLAGE) == 2
			&& Edicts.slots(VillageRanks.Rank.TOWN) == 3 && Edicts.slots(VillageRanks.Rank.CITY) == 4, "slots");

		// The config: villageEdicts on, edictMinDays 3 (0 to 30).
		helper.assertTrue(WorkplaceConfig.parse("{}").villageEdicts && WorkplaceConfig.parse("{}").edictMinDays == 3, "defaults");
		helper.assertTrue(WorkplaceConfig.parse("{\"edictMinDays\": 99}").edictMinDays == 30, "clamped to 30");
		helper.assertTrue(WorkplaceConfig.parse("{\"edictMinDays\": -4}").edictMinDays == 0, "clamped to 0");
		helper.succeed();
	}

	private static String refusal(GameTestHelper helper, String json) {
		try {
			Edicts.read(AliveWorkplace.id("broken"), JsonParser.parseString(json));
		} catch (RuntimeException e) {
			return String.valueOf(e.getMessage());
		}
		helper.fail("accepted " + json);
		return "";
	}

	/** A data pack's file at our path with {@code "enabled": false} hides Long Shifts; the rest still load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void enabledFalseHidesLongShifts(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(Edicts.FOLDER, p -> p.getPath().endsWith(".json"));
		ResourceLocation path = AliveWorkplace.id("edicts/long_shifts.json");
		Resource ours = real.get(path);
		helper.assertTrue(ours != null, "no " + path + " in " + real.keySet());
		Map<ResourceLocation, Resource> withPack = new HashMap<>(real);
		withPack.put(path, new Resource(ours.source(), () -> new ByteArrayInputStream("{\"enabled\": false}".getBytes(StandardCharsets.UTF_8))));
		try {
			Edicts.load(withPack);
			helper.assertTrue(Edicts.get(LONG_SHIFTS).isEmpty(), "Long Shifts still there: " + Edicts.all());
			helper.assertTrue(Edicts.find(DRILL).isPresent() && Edicts.find(FAIR).isPresent(), "the others went too: " + Edicts.all());
		} finally {
			Edicts.load(real);
		}
		helper.assertTrue(Edicts.get(LONG_SHIFTS).isPresent(), "Long Shifts back");
		helper.succeed();
	}

	/** Hamlet 1, Village 2, Town 3; a full village refuses another; an edict in force or excluded is refused. */
	//$ gametest_ticks_batch AREA '100' '"edictSlots"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictSlots")
	public void slotsGoByRank(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			String name = VillageHalls.name(level, hall).getString();

			Edicts.Result first = Edicts.proclaim(level, hall, null, Edicts.get(LONG_SHIFTS).orElseThrow());
			helper.assertTrue(first.done(), "a Hamlet's first: " + first.message().getString());
			helper.assertTrue(first.message().getString().equals(name + " proclaims the edict Long Shifts. Everyone works 20% faster; every grown villager is 10 less happy."),
				"told: " + first.message().getString());
			Edicts.Result full = Edicts.proclaim(level, hall, null, Edicts.find(FAIR).orElseThrow());
			helper.assertTrue(!full.done() && full.message().getString().equals(name + " has no free slot: a Hamlet may keep 1 edict in force. Lift one first."),
				"a Hamlet's second: " + full.message().getString());

			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find(FAIR).orElseThrow()).done(), "a Village's second");
			Edicts.Result third = Edicts.proclaim(level, hall, null, Edicts.find(DRILL).orElseThrow());
			helper.assertTrue(!third.done() && third.message().getString().equals(name + " has no free slot: a Village may keep 2 edicts in force. Lift one first."),
				"a Village's third: " + third.message().getString());

			entity.setRank(VillageRanks.Rank.TOWN);
			Edicts.Result again = Edicts.proclaim(level, hall, null, Edicts.get(LONG_SHIFTS).orElseThrow());
			helper.assertTrue(!again.done() && again.message().getString().equals("Long Shifts is already in force in " + name + "."),
				"twice: " + again.message().getString());
			Edicts.Result quiet = Edicts.proclaim(level, hall, null, Edicts.find(QUIET).orElseThrow());
			helper.assertTrue(!quiet.done() && quiet.message().getString().equals("Quiet Hours can't be in force alongside Long Shifts."),
				"excluded: " + quiet.message().getString());
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find(DRILL).orElseThrow()).done(), "a Town's third");
			helper.assertTrue(entity.edicts().size() == 3, "in force: " + entity.edicts());
			helper.assertTrue(chronicled(entity, "The edict Long Shifts was proclaimed") && chronicled(entity, "The edict Builders' Drill was proclaimed"),
				"chronicle: " + chronicle(entity));
			helper.assertTrue(entity.chronicle().get(entity.chronicle().size() - 1).kind() == Chronicle.Kind.EDICT, "kind EDICT");

			// The other way round: Quiet Hours in force refuses Long Shifts though only Quiet Hours names it.
			entity.setEdicts(List.of(new Edicts.InForce(QUIET, Chronicle.day(level))));
			Edicts.Result shifts = Edicts.proclaim(level, hall, null, Edicts.get(LONG_SHIFTS).orElseThrow());
			helper.assertTrue(!shifts.done() && shifts.message().getString().equals("Long Shifts can't be in force alongside Quiet Hours."),
				"excluded the other way: " + shifts.message().getString());
			helper.succeed();
		});
	}

	/** An edict stays {@code edictMinDays} (3) before it can be lifted; then it goes, told and chronicled. */
	//$ gametest_ticks_batch AREA '100' '"edictLift"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictLift")
	public void liftingIsRefusedBeforeThreeDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			String name = VillageHalls.name(level, hall).getString();
			long today = Chronicle.day(level);
			String id = LONG_SHIFTS.toString();
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LONG_SHIFTS).orElseThrow()).done(), "proclaimed");
			Edicts.Result soon = Edicts.lift(level, hall, null, id);
			helper.assertTrue(!soon.done() && soon.message().getString().equals("Long Shifts must stay in force at least 3 days: it can be lifted from day "
				+ (today + 3) + "."), "the same day: " + soon.message().getString());
			entity.setEdicts(List.of(new Edicts.InForce(id, today - 2)));
			helper.assertTrue(!Edicts.lift(level, hall, null, id).done(), "lifted after 2 days");
			int was = Edicts.MIN_DAYS;
			entity.setEdicts(List.of(new Edicts.InForce(id, today)));
			Edicts.MIN_DAYS = 1;
			try {
				String one = Edicts.lift(level, hall, null, id).message().getString();
				helper.assertTrue(one.equals("Long Shifts must stay in force at least a day: it can be lifted from day " + (today + 1) + "."), "one day: " + one);
			} finally {
				Edicts.MIN_DAYS = was;
			}
			entity.setEdicts(List.of(new Edicts.InForce(id, today - 3)));
			Edicts.Result lifted = Edicts.lift(level, hall, null, id);
			helper.assertTrue(lifted.done() && lifted.message().getString().equals(name + " lifts the edict Long Shifts."), "after 3 days: "
				+ lifted.message().getString());
			helper.assertTrue(entity.edicts().isEmpty(), "still in force: " + entity.edicts());
			helper.assertTrue(chronicled(entity, "The edict Long Shifts was lifted"), "chronicle: " + chronicle(entity));
			Edicts.Result gone = Edicts.lift(level, hall, null, id);
			helper.assertTrue(!gone.done() && gone.message().getString().equals("Long Shifts isn't in force in " + name + "."), "twice: " + gone.message().getString());
			helper.succeed();
		});
	}

	/** A village that drops a rank loses its newest edict (told, chronicled); the oldest stays. */
	//$ gametest_ticks_batch AREA '100' '"edictRankDrop"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictRankDrop")
	public void theNewestEdictLapsesOnARankDrop(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LONG_SHIFTS).orElseThrow()).done(), "first");
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find(FAIR).orElseThrow()).done(), "second");
			VillageRanks.Rank now = VillageRanks.round(level, hall, entity, 0);
			helper.assertTrue(now == VillageRanks.Rank.HAMLET, "rank: " + now);
			helper.assertTrue(entity.edicts().size() == 1 && entity.edicts().get(0).id().equals(LONG_SHIFTS.toString()), "kept: " + entity.edicts());
			helper.assertTrue(chronicled(entity, "The edict Fair Wages lapsed when the village fell to a Hamlet"), "chronicle: " + chronicle(entity));
			String told = Component.translatable("message.aliveworkplace.edict.lapsed", VillageHalls.name(level, hall), now.title(),
				io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.edict.count", 1, 1), Edicts.find(FAIR).orElseThrow().name()).getString();
			helper.assertTrue(told.equals(VillageHalls.name(level, hall).getString() + " is only a Hamlet now and may keep 1 edict: the edict Fair Wages lapses."),
				"told: " + told);
			helper.succeed();
		});
	}

	/** The edicts in force (id and day) are saved with the hall and come back with their effects; an old hall has none. */
	//$ gametest_ticks_batch AREA '100' '"edictSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictSave")
	public void edictsAreKeptOverASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LONG_SHIFTS).orElseThrow()).done(), "proclaimed");
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && copy.edicts().equals(entity.edicts()), "reloaded: " + (copy == null ? null : copy.edicts()));
			helper.assertTrue(copy.civicEffects().all().size() == 2, "its effects: " + copy.civicEffects().all());
			tag.remove("edicts");
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(old != null && old.edicts().isEmpty() && old.civicEffects().isEmpty(), "a hall saved before edicts");
			helper.succeed();
		});
	}

	/** In an owned village only the owner's side may proclaim or lift; a hall nobody owns lets anyone. */
	//$ gametest_ticks_batch AREA '100' '"edictStranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictStranger")
	public void aStrangerCantProclaimInAnOwnedVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			String name = VillageHalls.name(level, hall).getString();
			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			entity.setOwner(UUID.randomUUID(), "Jesse");
			Edicts.Result refused = Edicts.proclaim(level, hall, stranger, Edicts.get(LONG_SHIFTS).orElseThrow());
			helper.assertTrue(!refused.done() && refused.message().getString().equals("Only Jesse and their friends can proclaim edicts in " + name + "."),
				"a stranger: " + refused.message().getString());
			helper.assertTrue(entity.edicts().isEmpty(), "proclaimed by a stranger");

			entity.setOwner(null, "");
			helper.assertTrue(Edicts.proclaim(level, hall, stranger, Edicts.get(LONG_SHIFTS).orElseThrow()).done(), "a hall nobody owns");
			helper.assertTrue(entity.owner() == null, "proclaiming claimed the hall");

			entity.setOwner(UUID.randomUUID(), "Jesse");
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS.toString(), Chronicle.day(level) - 5)));
			Edicts.Result lift = Edicts.lift(level, hall, stranger, LONG_SHIFTS.toString());
			helper.assertTrue(!lift.done() && lift.message().getString().equals("Only Jesse and their friends can lift edicts in " + name + "."),
				"a stranger lifting: " + lift.message().getString());

			entity.setOwner(stranger.getUUID(), stranger.getGameProfile().getName());
			helper.assertTrue(Edicts.lift(level, hall, stranger, LONG_SHIFTS.toString()).done(), "the owner lifts it");
			helper.succeed();
		});
	}

	/**
	 * Long Shifts: a builder's delay is 1/1.2 of before, the status says why, their mood is 10 lower with "long shifts"
	 * in the hall's list. With {@code villageEdicts} off it does nothing but stays saved, and comes back when it's on.
	 */
	//$ gametest_ticks_batch AREA '100' '"edictLongShifts"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictLongShifts")
	public void longShiftsSpeedsUpBuildersAndCostsTenMood(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager builder = village(helper);
		moodsOn(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			int before = BuilderLevels.delay(1200, builder);
			int mood = Moods.work(level, builder).score();
			helper.assertTrue(before == 1200, "the usual pace first: " + before);

			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LONG_SHIFTS).orElseThrow()).done(), "proclaimed");
			helper.assertTrue(BuilderLevels.delay(1200, builder) == 1000, "1/1.2 of 1200: " + BuilderLevels.delay(1200, builder));
			String pace = Pace.describe(builder).getString();
			helper.assertTrue(pace.equals("20% faster (the Long Shifts edict)"), "status: " + pace);
			Moods.Mood after = Moods.work(level, builder);
			helper.assertTrue(after.score() == mood - 10, "mood " + mood + " -> " + after.score());
			helper.assertTrue(!after.bad().isEmpty() && after.bad().get(0).getString().equals("long shifts"), "reasons: " + after.bad());
			Moods.forget();
			ItemLore lore = VillageHallScreen.person(level, hall, builder).get(DataComponents.LORE);
			helper.assertTrue(lore != null && lore.lines().stream().map(Component::getString).anyMatch(l -> l.equals("Content (" + (mood - 10) + "): long shifts, no bed, fed, a job")),
"hall list: " + (lore == null ? "none" : lore.lines().stream().map(Component::getString).toList()));

			// A child has no mood to lose; everyone grown does, whatever their job.
			Villager farmer = EntityType.VILLAGER.create(level);
			farmer.moveTo(builder.position());
			farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
			helper.assertTrue(Pace.factor(farmer) == 1f / 1.2f, "a farmer: " + Pace.factor(farmer));
			helper.assertTrue(Moods.work(level, farmer).bad().stream().anyMatch(c -> c.getString().equals("long shifts")), "the farmer's mood");

			// Switched off: no effect, still saved; on again: back.
			try {
				Edicts.setEnabled(false);
				helper.assertTrue(BuilderLevels.delay(1200, builder) == 1200, "off: " + BuilderLevels.delay(1200, builder));
				helper.assertTrue(Moods.work(level, builder).score() == mood, "off, mood: " + Moods.work(level, builder).score());
				helper.assertTrue(entity.edicts().size() == 1, "forgotten while off");
				Edicts.Result refused = Edicts.proclaim(level, hall, null, Edicts.find(FAIR).orElseThrow());
				helper.assertTrue(!refused.done() && refused.message().getString().equals("Edicts are switched off on this server."), "off: " + refused.message().getString());
			} finally {
				Edicts.setEnabled(true);
			}
			helper.assertTrue(BuilderLevels.delay(1200, builder) == 1000, "on again: " + BuilderLevels.delay(1200, builder));
			helper.succeed();
		});
	}

	/** The test pack's edict works: builders only 50% faster and 5 happier ("drilled"); other jobs untouched. */
	//$ gametest_ticks_batch AREA '100' '"edictPack"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictPack")
	public void aDataPackEdictWorks(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager builder = village(helper);
		moodsOn(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			int mood = Moods.work(level, builder).score();
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find(DRILL).orElseThrow()).done(), "proclaimed");
			helper.assertTrue(BuilderLevels.delay(1200, builder) == 800, "1/1.5 of 1200: " + BuilderLevels.delay(1200, builder));
			helper.assertTrue(Pace.describe(builder).getString().equals("50% faster (the Builders' Drill edict)"), "status: " + Pace.describe(builder).getString());
			Moods.Mood after = Moods.work(level, builder);
			helper.assertTrue(after.score() == mood + 5 && after.good().stream().anyMatch(c -> c.getString().equals("drilled")), "mood: " + after);
			Villager farmer = EntityType.VILLAGER.create(level);
			farmer.moveTo(builder.position());
			farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
			helper.assertTrue(Pace.factor(farmer) == 1f, "a farmer: " + Pace.factor(farmer));
			helper.assertTrue(Moods.work(level, farmer).good().stream().noneMatch(c -> c.getString().equals("drilled")), "the farmer drilled");
			helper.succeed();
		});
	}

	/** An operator's {@code /workplace edict proclaim|lift <id>} in the village it runs in. */
	//$ gametest_ticks_batch AREA '100' '"edictCommand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictCommand")
	public void theOperatorsCommandProclaimsAndLifts(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			String name = VillageHalls.name(level, hall).getString();
			List<String> said = new ArrayList<>();
			CommandSource out = new CommandSource() {
				@Override
				public void sendSystemMessage(Component message) {
					said.add(message.getString());
				}

				@Override
				public boolean acceptsSuccess() {
					return true;
				}

				@Override
				public boolean acceptsFailure() {
					return true;
				}

				@Override
				public boolean shouldInformAdmins() {
					return false;
				}
			};
			CommandSourceStack op = new CommandSourceStack(out, Vec3.atCenterOf(hall.south(3)), Vec2.ZERO, level, 2, "test", Component.literal("test"),
				level.getServer(), null);
			var commands = level.getServer().getCommands();
			commands.performPrefixedCommand(op, "workplace edict proclaim long_shifts");
			helper.assertTrue(entity.edicts().size() == 1, "proclaimed by command: " + entity.edicts() + " " + said);
			helper.assertTrue(said.contains(name + " proclaims the edict Long Shifts. Everyone works 20% faster; every grown villager is 10 less happy."), "said: " + said);
			commands.performPrefixedCommand(op, "workplace edict proclaim nonsense");
			helper.assertTrue(said.contains("There is no edict called nonsense."), "unknown: " + said);
			commands.performPrefixedCommand(op, "workplace edict lift long_shifts");
			helper.assertTrue(entity.edicts().size() == 1, "lifted too soon");
			int was = Edicts.MIN_DAYS;
			try {
				Edicts.MIN_DAYS = 0;
				commands.performPrefixedCommand(op, "workplace edict lift long_shifts");
			} finally {
				Edicts.MIN_DAYS = was;
			}
			helper.assertTrue(entity.edicts().isEmpty(), "not lifted by command: " + said);
			commands.performPrefixedCommand(op.withPosition(Vec3.atCenterOf(hall.above(80))), "workplace edict proclaim long_shifts");
			helper.assertTrue(said.contains("There is no Village Hall here."), "no hall: " + said);
			// Not an operator: the command isn't there.
			said.clear();
			commands.performPrefixedCommand(op.withPermission(0), "workplace edict proclaim long_shifts");
			helper.assertTrue(entity.edicts().isEmpty(), "proclaimed without permission");
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** A Village Hall and a builder at a bench near it (no AI); the hall's first round runs in the next tick. */
	private static Villager village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos bench = new BlockPos(5, 2, 5);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		builder.setNoAi(true);
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(helper.getLevel(), builder, helper.absolutePos(bench),
			ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		return builder;
	}

	/** The hall after its first round: a Hamlet at the usual pace, nothing in force, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}

	private static void moodsOn(GameTestHelper helper) {
		boolean moods = Moods.ENABLED;
		Moods.ENABLED = true;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	private static boolean chronicled(VillageHallBlockEntity hall, String line) {
		return chronicle(hall).contains(line);
	}
}
