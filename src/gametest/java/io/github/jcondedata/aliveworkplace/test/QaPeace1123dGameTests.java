package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.AtPeace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane D, 2026-11-23) for the peaceful settings and the threat level (ROADMAP 32.21, docs/wiki/peace.md), written
 * from the spec for what the feature's many tests leave out:
 * <ul>
 * <li>the fire follows the threat level like every other threat ("the fire's (lightning, chimney sparks, burning
 * arrows)" chance is 0 in a quiet village; half at 1, half again at 3): no test switched the threat level or At peace
 * under a burnable village;</li>
 * <li>on Peaceful the guards icon and the Defence page say "The world is on Peaceful" (only level 0 was rendered), and
 * the reasons come in the spec's order: threat level 0, then Peaceful, then At peace;</li>
 * <li>the saved {@code peaceSince} means nothing without {@code atPeace}, and is -1 when missing ("-1 when missing or not
 * at peace"), each key missing on its own;</li>
 * <li>every sentence the spec quotes, word for word, in American English.</li>
 * </ul>
 */
public class QaPeace1123dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos FIRE_HALL = new BlockPos(19, 2, 2);
	private static final BlockPos HUT = new BlockPos(12, 2, 12);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = 24000;

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
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

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	private static VillageHallBlockEntity entity(GameTestHelper helper, BlockPos hall) {
		VillageHallBlockEntity entity = helper.getLevel().getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		helper.assertTrue(entity != null, "no hall");
		return entity;
	}

	/**
	 * A village with a 5 x 5 hut of logs the builders finished (as FireGameTests builds it), in a thunderstorm; the
	 * config, weather, rules, difficulty and At peace go back when the test ends, even when it fails.
	 */
	private static BlockPos burnableVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		Difficulty difficulty = level.getDifficulty();
		long time = level.getDayTime();
		VillageHalls.RADIUS = 20;
		VillageFire.reset();
		helper.setBlock(FIRE_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(FIRE_HALL);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
			level.setWeatherParameters(6000, 0, false, false);
			level.getServer().setDifficulty(difficulty, true);
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setAtPeace(false, -1);
			}
			new WorkplaceConfig().apply();
			level.setDayTime(time);
			VillageFire.reset();
		});
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_peace_hut_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		return hall;
	}

	/** How many of {@code tries} lightning strikes on the hut's roof light it, from one fixed stream of seeds. */
	private static int lit(ServerLevel level, BlockPos strike, int tries, long seed) {
		RandomSource random = RandomSource.create(seed);
		int lit = 0;
		for (int i = 0; i < tries; i++) {
			var fire = VillageFire.lightning(level, strike, random);
			if (fire.isPresent()) {
				lit++;
				level.removeBlock(fire.get(), false);
			}
		}
		return lit;
	}

	/**
	 * The spec: in a quiet village "the raid chance, the lair chance and every disaster's chance are 0, and so is the
	 * fire's (lightning, chimney sparks, burning arrows)"; the threat level multiplies the chances 0, x0.5, x1, x1.5. A
	 * burnable village in a thunderstorm: its fire factor at level 2 is above 0; at 1 it is exactly half, at 3 half again;
	 * at 0, set At peace, and on Peaceful it is 0, and 60 lightning strikes on its roof light nothing (while at level 2
	 * the same strikes do).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPeace1123dFire"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPeace1123dFire")
	public void qaFireFollowsTheThreatLevelAndNeverComesToAQuietVillage(GameTestHelper helper) {
		BlockPos hall = burnableVillage(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = entity(helper, hall);
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			level.setWeatherParameters(0, 6000, true, true);
			BlockPos strike = helper.absolutePos(new BlockPos(13, 8, 13));
			new WorkplaceConfig().apply();
			double normal = VillageFire.factor(level, hall);
			helper.assertTrue(normal > 0, "the hut's village isn't burnable at level 2: " + normal);
			int litNormal = lit(level, strike, 60, 4242L);
			helper.assertTrue(litNormal > 0, "60 strikes lit nothing at level 2");

			WorkplaceConfig.parse("{\"threatLevel\": 1}").apply();
			double gentle = VillageFire.factor(level, hall);
			helper.assertTrue(Math.abs(gentle - normal * 0.5) < 1e-6, "level 1 isn't half as often: " + gentle + " of " + normal);
			WorkplaceConfig.parse("{\"threatLevel\": 3}").apply();
			double hard = VillageFire.factor(level, hall);
			helper.assertTrue(Math.abs(hard - normal * 1.5) < 1e-6, "level 3 isn't half again as often: " + hard + " of " + normal);

			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			helper.assertTrue(VillageFire.factor(level, hall) == 0 && lit(level, strike, 60, 4242L) == 0,
				"fire may still come at threat level 0: " + VillageFire.factor(level, hall));
			new WorkplaceConfig().apply();

			entity.setAtPeace(true, 1);
			helper.assertTrue(VillageFire.factor(level, hall) == 0 && lit(level, strike, 60, 4242L) == 0,
				"fire may still come to a village At peace: " + VillageFire.factor(level, hall));
			entity.setAtPeace(false, -1);
			helper.assertTrue(VillageFire.factor(level, hall) > 0, "the fire didn't come back once At peace was lifted");

			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			double peaceful = VillageFire.factor(level, hall);
			int litPeaceful = lit(level, strike, 60, 4242L);
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			helper.assertTrue(peaceful == 0 && litPeaceful == 0, "fire may still come on Peaceful: " + peaceful + ", lit " + litPeaceful);
			helper.succeed();
		});
	}

	/**
	 * The spec: the guards icon says "At peace: no raiders, camps or disasters come" and "The Bandit camp leaves at the
	 * next dawn"; "At threat level 0 or on Peaceful it says that instead ("Threats are off on this server", "The world is
	 * on Peaceful")". With a camp standing: on Peaceful the guards icon says the world is on Peaceful and that the camp
	 * leaves (not the At peace line, even with At peace on); with the threat level 0 as well it says threats are off; the
	 * Defence page's At peace slot, off on Peaceful, still names Peaceful as why nothing comes, and on, keeps its day and
	 * its camp line beside the Peaceful one.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaPeace1123dPeacefulLines"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaPeace1123dPeacefulLines")
	public void qaOnPeacefulTheHallSaysSoBeforeAtPeace(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		Difficulty difficulty = level.getDifficulty();
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getServer().setDifficulty(difficulty, true);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setAtPeace(false, -1);
			}
			BanditCamps.forget(level);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			level.setDayTime(time);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		ServerPlayer owner = player(helper);
		helper.runAfterDelay(2, () -> {
			at(level, 50, 13000);
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			new WorkplaceConfig().apply();
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			List<String> normal = lines(VillageHallScreen.forTest(owner, hall).icon(VillageHallScreen.GUARDS));
			helper.assertTrue(normal.stream().noneMatch(l -> l.contains("Peaceful") || l.contains("leaves at the next dawn")), "the guards icon on Normal: " + normal);

			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			List<String> guards = lines(VillageHallScreen.forTest(owner, hall).icon(VillageHallScreen.GUARDS));
			helper.assertTrue(guards.contains("The world is on Peaceful: no threats come") && guards.contains("The Bandit camp leaves at the next dawn"),
				"the guards icon on Peaceful: " + guards);
			ChoiceMenu menu = VillageHallScreen.forTest(owner, hall);
			menu.press(VillageHallScreen.GUARDS, owner);
			List<String> off = lines(menu.icon(DefencePage.AT_PEACE));
			helper.assertTrue(off.get(0).equals("At peace: off") && off.contains("The world is on Peaceful: no threats come"),
				"the At peace slot, off, on Peaceful: " + off);

			// At peace on as well: the reason is still Peaceful (the spec's "instead"), and the slot keeps its own lines.
			Component set = AtPeace.toggle(level, hall, owner);
			helper.assertTrue(entity(helper, hall).atPeace(), "the owner couldn't set At peace on Peaceful: " + set.getString());
			List<String> both = lines(VillageHallScreen.forTest(owner, hall).icon(VillageHallScreen.GUARDS));
			helper.assertTrue(both.contains("The world is on Peaceful: no threats come") && !both.contains("At peace: no raiders, camps or disasters come")
				&& both.contains("The Bandit camp leaves at the next dawn"), "the guards icon on Peaceful and At peace: " + both);
			ChoiceMenu again = VillageHallScreen.forTest(owner, hall);
			again.press(VillageHallScreen.GUARDS, owner);
			List<String> on = lines(again.icon(DefencePage.AT_PEACE));
			helper.assertTrue(on.get(0).equals("At peace") && on.contains("At peace since day 50") && on.contains("The Bandit camp leaves at the next dawn")
				&& on.contains("The world is on Peaceful: no threats come"), "the At peace slot, on, on Peaceful: " + on);

			// Threat level 0 comes first of all.
			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			List<String> zero = lines(VillageHallScreen.forTest(owner, hall).icon(VillageHallScreen.GUARDS));
			helper.assertTrue(zero.contains("Threats are off on this server: nothing comes") && zero.stream().noneMatch(l -> l.contains("Peaceful"))
				&& !zero.contains("At peace: no raiders, camps or disasters come"), "the guards icon at level 0 on Peaceful At peace: " + zero);
			helper.assertTrue(AtPeace.reason(level, hall).orElse("").equals("screen.aliveworkplace.hall.threats_off"), "the reason at level 0: " + AtPeace.reason(level, hall));
			new WorkplaceConfig().apply();
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			helper.assertTrue(AtPeace.reason(level, hall).orElse("").equals("screen.aliveworkplace.hall.at_peace"), "the reason back on Normal: " + AtPeace.reason(level, hall));
			helper.succeed();
		});
	}


	/**
	 * The spec: in a quiet village "the hall's round takes every attack off the threat clock ... and starts none, the
	 * Seer's foretold raid included". A Seer foretold a raid tonight at 13500 and the clock holds it; at 13600 the hall's
	 * round in a village At peace starts nothing, and nor does one at threat level 0; the same foretelling, with the
	 * village no longer quiet, brings the raid (so the foretelling would have come).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPeace1123dSeer"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPeace1123dSeer")
	public void qaASeersForetoldRaidNeverComesToAQuietVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean raids = VillageRaids.ENABLED;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setAtPeace(false, -1);
				e.setSeer(Seer.State.EMPTY);
			}
			VillageRaids.forget();
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageRaids.ENABLED = raids;
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			level.setDayTime(time);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		helper.runAfterDelay(2, () -> {
			new WorkplaceConfig().apply();
			VillageRaids.ENABLED = true; // (the config turns raids off in GameTests: on again after every apply)
			VillageHallBlockEntity entity = entity(helper, hall);
			at(level, 70, 13600);
			long day = Chronicle.day(level);
			Runnable foretell = () -> {
				entity.setSeer(new Seer.State(day, true, Math.PI, 13500, false, -1, -1, Map.of()));
				ThreatData.get(level).rolled(hall, day - 1, new ThreatData.Attack(day, Threats.MONSTERS, Math.PI, 13500));
			};
			List<Long> raided = new ArrayList<>();

			entity.setAtPeace(true, day);
			foretell.run();
			helper.assertTrue(Seer.tonight(level, hall).map(Seer.State::raid).orElse(false), "setup: the Seer's raid isn't tonight's");
			VillageRaids.tick(level, hall, 52, 0, 0, raided::add);
			helper.assertTrue(raided.isEmpty() && ThreatData.get(level).raid(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty(),
				"the Seer's foretold raid came to a village At peace: " + raided + ", " + VillageRaids.raiders(level, hall).size() + " raiders");
			entity.setAtPeace(false, -1);

			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			VillageRaids.ENABLED = true;
			foretell.run();
			VillageRaids.tick(level, hall, 52, 0, 0, raided::add);
			helper.assertTrue(raided.isEmpty() && ThreatData.get(level).raid(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty(),
				"the Seer's foretold raid came at threat level 0: " + raided + ", " + VillageRaids.raiders(level, hall).size() + " raiders");
			new WorkplaceConfig().apply();
			VillageRaids.ENABLED = true;

			foretell.run();
			VillageRaids.tick(level, hall, 52, 0, 0, raided::add);
			helper.assertTrue(raided.equals(List.of(day)) && ThreatData.get(level).raid(hall).isPresent(),
				"setup: the same foretelling brought no raid to a village that isn't quiet: " + raided);
			helper.succeed();
		});
	}

	/**
	 * The spec's saved data: {@code atPeace} "false when missing" and {@code peaceSince} "-1 when missing or not at
	 * peace", both "written only while the village is at peace". A hall lifted from peace saves neither; a tag with only
	 * {@code peaceSince} loads not at peace and -1; one with {@code atPeace} but no day loads at peace since -1; a tag
	 * with {@code atPeace} false and a day loads -1.
	 */
	//$ gametest_batch AREA '"qaPeace1123dSave"'
	@GameTest(template = AREA, batch = "qaPeace1123dSave")
	public void qaPeaceSinceMeansNothingWithoutAtPeace(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		entity.setAtPeace(true, 12);
		entity.setAtPeace(false, 40);
		CompoundTag lifted = entity.saveWithoutMetadata(level.registryAccess());
		helper.assertTrue(!lifted.contains("atPeace") && !lifted.contains("peaceSince") && entity.peaceSince() == -1,
			"a hall lifted from peace saves " + lifted.get("atPeace") + " / " + lifted.get("peaceSince") + ", since " + entity.peaceSince());

		CompoundTag onlyDay = lifted.copy();
		onlyDay.putLong("peaceSince", 9);
		entity.loadWithComponents(onlyDay, level.registryAccess());
		helper.assertTrue(!entity.atPeace() && entity.peaceSince() == -1, "a tag with only peaceSince loads at peace " + entity.atPeace() + " since " + entity.peaceSince());

		CompoundTag falseDay = lifted.copy();
		falseDay.putBoolean("atPeace", false);
		falseDay.putLong("peaceSince", 9);
		entity.loadWithComponents(falseDay, level.registryAccess());
		helper.assertTrue(!entity.atPeace() && entity.peaceSince() == -1, "atPeace false with a day loads since " + entity.peaceSince());

		CompoundTag noDay = lifted.copy();
		noDay.putBoolean("atPeace", true);
		entity.loadWithComponents(noDay, level.registryAccess());
		helper.assertTrue(entity.atPeace() && entity.peaceSince() == -1, "atPeace without a day loads at peace " + entity.atPeace() + " since " + entity.peaceSince());
		entity.setAtPeace(false, -1);
		helper.succeed();
	}

	/**
	 * Every sentence the spec quotes, with its real arguments, word for word, and the whole of the feature's text in
	 * American English (the owner's rule: defense, not defence).
	 */
	//$ gametest_batch EMPTY_STRUCTURE '"qaPeace1123dWords"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaPeace1123dWords")
	public void qaEverySentenceTheSpecQuotesWordForWord(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Component camp = Lairs.lairName(Threats.BANDITS);
		check(wrong, Component.translatable("message.aliveworkplace.lair.left", camp, "Oakvale"),
			"The raiders of the Bandit camp have packed up and gone: Oakvale is left in peace.");
		check(wrong, Component.translatable("screen.aliveworkplace.hall.at_peace"), "At peace: no raiders, camps or disasters come");
		check(wrong, Component.translatable("screen.aliveworkplace.hall.lair_leaves", camp), "The Bandit camp leaves at the next dawn");
		startsWith(wrong, Component.translatable("screen.aliveworkplace.hall.threats_off"), "Threats are off on this server");
		startsWith(wrong, Component.translatable("screen.aliveworkplace.hall.threats_peaceful"), "The world is on Peaceful");
		check(wrong, Component.translatable("screen.aliveworkplace.defence.peace_on"), "At peace");
		Language language = Language.getInstance();
		for (String key : List.of("screen.aliveworkplace.defence.peace_on", "screen.aliveworkplace.defence.peace_off", "screen.aliveworkplace.defence.peace_what",
			"screen.aliveworkplace.defence.peace_since", "screen.aliveworkplace.defence.peace_click_on", "screen.aliveworkplace.defence.peace_click_off",
			"screen.aliveworkplace.defence.peace_owner", "screen.aliveworkplace.hall.at_peace", "screen.aliveworkplace.hall.threats_off",
			"screen.aliveworkplace.hall.threats_peaceful", "screen.aliveworkplace.hall.lair_leaves", "message.aliveworkplace.peace.on",
			"message.aliveworkplace.peace.on_lair", "message.aliveworkplace.peace.off", "message.aliveworkplace.peace.not_owner",
			"chronicle.aliveworkplace.peace_on", "chronicle.aliveworkplace.peace_off", "message.aliveworkplace.lair.left",
			"chronicle.aliveworkplace.lair.left", "message.aliveworkplace.threat.peaceful", "aliveworkplace.config.threatLevel.tooltip")) {
			String text = language.getOrDefault(key, key);
			if (text.equals(key)) {
				wrong.add(key + " has no words");
			}
			String lower = text.toLowerCase(java.util.Locale.ROOT);
			for (String british : List.of("defence", "colour", "armour", "grey", "cancelled", "neighbour", "honour", "favour")) {
				if (lower.contains(british)) {
					wrong.add(key + " says \"" + british + "\": " + text);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private static void check(List<String> wrong, Component line, String expected) {
		if (!line.getString().equals(expected)) {
			wrong.add("expected \"" + expected + "\", got \"" + line.getString() + "\"");
		}
	}

	private static void startsWith(List<String> wrong, Component line, String expected) {
		if (!line.getString().startsWith(expected)) {
			wrong.add("expected \"" + expected + "...\", got \"" + line.getString() + "\"");
		}
	}
}
