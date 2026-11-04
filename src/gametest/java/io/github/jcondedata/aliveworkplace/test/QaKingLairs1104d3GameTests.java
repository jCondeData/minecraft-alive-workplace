package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.io.Reader;
import java.util.List;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-d-1104-1907): the player guide's pages "The Bandit King" and "Lairs and sieges" tested from what they
 * promise a player. Adds what the earlier King, lair and siege tests leave out: the Captains of the Bandit King as the
 * arc really spawns them (iron helmet and chestplate, an iron axe, a chief's toughness, bandits with axes and crossbows
 * who stay by their camp, all through a save), the King's own gear and toughness through a save, the arc's numbers the
 * page quotes, and the sentences the pages quote word for word.
 */
public class QaKingLairs1104d3GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(0, 4, 0);
	private static final List<BlockPos> CAMPS = List.of(new BlockPos(6, 4, 6), new BlockPos(24, 4, 6), new BlockPos(6, 4, 24));
	/** A vindicator's own max health (vanilla). */
	private static final double VINDICATOR = 24;

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		VillageHalls.RADIUS = 8;
		Arcs.NEAR = 0;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
		});
	}

	private static void flatGround(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	/** The plain bandit chief's max health: a vindicator with his culture's extra health. */
	private static double chiefHealth(GameTestHelper helper) {
		Culture.Captain chief = Threats.get(Threats.BANDITS).flatMap(Culture::captain).orElse(null);
		helper.assertTrue(chief != null, "the bandits have no chief");
		return VINDICATOR + chief.health();
	}

	private static Mob reload(GameTestHelper helper, Mob mob) {
		ServerLevel level = helper.getLevel();
		CompoundTag saved = new CompoundTag();
		helper.assertTrue(mob.save(saved), "didn't save: " + mob);
		mob.discard();
		Entity loaded = EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(loaded instanceof Mob, "didn't load: " + loaded);
		helper.assertTrue(level.addFreshEntity(loaded), "the loaded mob wasn't added");
		return (Mob) loaded;
	}

	private static void says(GameTestHelper helper, Component text, String expected) {
		String got = text.getString();
		helper.assertTrue(got.equals(expected), "reads \"" + got + "\", the guide says \"" + expected + "\"");
	}

	private static void hasKey(GameTestHelper helper, String key) {
		helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
	}

	private static JsonObject arcJson(GameTestHelper helper) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "arcs/bandit_king.json");
		try (Reader reader = helper.getLevel().getServer().getResourceManager().getResourceOrThrow(id).openAsReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException("the Bandit King's arc file didn't read: " + e);
		}
	}

	private static JsonObject effect(JsonArray list, String type) {
		for (JsonElement e : list) {
			if (e.getAsJsonObject().get("type").getAsString().equals(type)) {
				return e.getAsJsonObject();
			}
		}
		throw new IllegalStateException("no " + type + " effect in " + list);
	}

	// ---- Smoke on Three Hills: the captains and their men ----------------------------------------------------------

	/**
	 * Guide: "The three chiefs call themselves Captains of the Bandit King: vindicators in an iron helmet and chestplate
	 * with an iron axe, tougher than a plain chief [fixed: as tough as a camp's chief], each with a few bandits (axes and
	 * crossbows) who stay close to their camp." The arc started for real and its mobs spawned at three camps in the test
	 * area by the player coming near: three captains, each a named vindicator in iron with an iron axe and a camp chief's
	 * health (more than a plain vindicator), and three or four bandits at each camp, every vindicator with an axe and
	 * every pillager with a crossbow, each kept within 16 blocks of its camp. A captain saved and loaded keeps it all.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaKingLairsCaptains"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaKingLairsCaptains")
	public void captainsOfTheKingWearIronCarryAxesAndKeepByTheirCamps(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		flatGround(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.Arc arc = Arcs.find("bandit_king").orElse(null);
		helper.assertTrue(arc != null, "no Bandit King arc");
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && s.chapter == 0, "the arc didn't start");
		List<ArcState.ArcPlace> camps = s.places.stream().filter(p -> p.stages.get(0).equals("aliveworkplace:camp/bandit_camp")).toList();
		helper.assertTrue(camps.size() == 3, "camps: " + camps.size());
		for (int i = 0; i < 3; i++) {
			camps.get(i).spot = helper.absolutePos(CAMPS.get(i));
			camps.get(i).placed = 1; // (the camp's template stands; only its men are this test's)
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos middle = helper.absolutePos(new BlockPos(15, 4, 15));
		player.teleportTo(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5);
		Arcs.NEAR = 96;
		ArcEffects.check(level);
		AABB area = helper.getBounds().inflate(4);
		String captainRole = ArcEffects.roleTag("captain");
		List<Mob> captains = level.getEntitiesOfClass(Mob.class, area, m -> m.getTags().contains(captainRole));
		List<Mob> bandits = level.getEntitiesOfClass(Mob.class, area, m -> m.getTags().contains(ArcEffects.roleTag("bandit"))
			|| m.getTags().contains(ArcEffects.roleTag("bandit_archer")));
		helper.assertTrue(captains.size() == 3, "captains spawned: " + captains.size());
		double chief = chiefHealth(helper);
		for (Mob c : captains) {
			helper.assertTrue(c.getType() == EntityType.VINDICATOR, "a captain is a " + c.getType());
			helper.assertTrue(c.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && c.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
				"a captain's armor: " + c.getItemBySlot(EquipmentSlot.HEAD) + ", " + c.getItemBySlot(EquipmentSlot.CHEST));
			helper.assertTrue(c.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_AXE), "a captain holds " + c.getItemBySlot(EquipmentSlot.MAINHAND));
			helper.assertTrue(c.getCustomName() != null && "Captain of the Bandit King".equals(c.getCustomName().getString()) && c.isCustomNameVisible(),
				"a captain's name: " + c.getCustomName());
			helper.assertTrue(Math.abs(c.getMaxHealth() - chief) < 0.01 && c.getMaxHealth() > VINDICATOR,
				"a captain has " + c.getMaxHealth() + " health, a camp's chief " + chief);
		}
		for (BlockPos camp : CAMPS) {
			BlockPos abs = helper.absolutePos(camp);
			long near = bandits.stream().filter(b -> b.getRestrictCenter().distManhattan(abs) <= 8).count();
			helper.assertTrue(near >= 3 && near <= 4, "bandits at the camp at " + camp.toShortString() + ": " + near);
		}
		helper.assertTrue(bandits.size() == 10, "bandits in all: " + bandits.size());
		for (Mob b : bandits) {
			if (b.getType() == EntityType.VINDICATOR) {
				helper.assertTrue(b.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_AXE), "a bandit vindicator holds " + b.getItemBySlot(EquipmentSlot.MAINHAND));
			} else {
				helper.assertTrue(b.getType() == EntityType.PILLAGER && b.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.CROSSBOW),
					"a bandit " + b.getType() + " holds " + b.getItemBySlot(EquipmentSlot.MAINHAND));
			}
			helper.assertTrue(b.hasRestriction() && b.getRestrictRadius() <= 16, "a bandit roams " + b.getRestrictRadius() + " from his camp");
		}
		// Through a save: the captain keeps his gear and toughness.
		Mob again = reload(helper, captains.get(0));
		helper.assertTrue(Math.abs(again.getMaxHealth() - chief) < 0.01 && Math.abs(again.getHealth() - chief) < 0.01,
			"after a reload the captain has " + again.getHealth() + "/" + again.getMaxHealth());
		helper.assertTrue(again.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_AXE) && again.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
			"after a reload the captain's gear: " + again.getItemBySlot(EquipmentSlot.MAINHAND));
		Arcs.stopAll(level, hall);
		level.getEntitiesOfClass(Mob.class, area, m -> m.getTags().contains(Arcs.MOB_TAG)).forEach(Entity::discard);
		helper.succeed();
	}

	// ---- The Storm: the King himself -------------------------------------------------------------------------------

	/**
	 * Guide: "The King himself is a vindicator with a golden crown, iron armor and a diamond axe, much tougher than a
	 * chief." Spawned: a vindicator in the golden crown, iron chestplate, leggings and boots, a diamond axe, with at
	 * least twice a camp chief's health, his name over his head; a save and load keeps all of it and his wounds.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaKingLairsKing"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaKingLairsKing")
	public void theKingWearsACrownIronAndADiamondAxeAndIsMuchTougherThroughASave(GameTestHelper helper) {
		Leftovers.clear(helper);
		flatGround(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(15, 4, 15));
		Mob king = BanditKing.spawn(level, at, at, at.north(2));
		helper.assertTrue(king != null, "no King");
		king.setNoAi(true);
		double chief = chiefHealth(helper);
		for (int round = 0; round < 2; round++) {
			String when = round == 0 ? "" : " after a reload";
			helper.assertTrue(king.getType() == EntityType.VINDICATOR, "the King is a " + king.getType());
			helper.assertTrue(king.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "his head" + when + ": " + king.getItemBySlot(EquipmentSlot.HEAD));
			helper.assertTrue(king.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && king.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
				&& king.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "his armor" + when);
			helper.assertTrue(king.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_AXE), "he holds" + when + " " + king.getItemBySlot(EquipmentSlot.MAINHAND));
			helper.assertTrue(king.getMaxHealth() >= 2 * chief, "the King has " + king.getMaxHealth() + " health" + when + ", a chief " + chief);
			helper.assertTrue(king.isCustomNameVisible() && king.getCustomName() != null && king.getCustomName().getString().endsWith(", the Bandit King"),
				"his name" + when + ": " + king.getCustomName());
			if (round == 0) {
				king.setHealth(100);
				king = reload(helper, king);
				king.setNoAi(true);
			} else {
				helper.assertTrue(Math.abs(king.getHealth() - 100) < 0.01, "his wounds after a reload: " + king.getHealth());
			}
		}
		king.discard();
		helper.succeed();
	}

	// ---- the arc's numbers the page quotes ---------------------------------------------------------------------------

	/**
	 * The infobox and "How it ends": starts by chance (8% a day) in a Town that has broken up two bandit camps; 10 days for
	 * the storm; 6 mercenaries for 24 emeralds, 3 guards from a neighbor, Pell's 16 bread; the ending's +150 standing,
	 * Kingslayer, a festival and 10 days without camps; the tribute's -10 mood for 3 days. Read from the arc's data file
	 * the game loads (not a copy), with the mod's own counts.
	 */
	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '20'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theArcsNumbersAreTheGuidesNumbers(GameTestHelper helper) {
		JsonObject arc = arcJson(helper);
		JsonObject trigger = arc.getAsJsonObject("trigger");
		helper.assertTrue(Math.abs(trigger.get("chance").getAsDouble() - 0.08) < 1e-9, "the chance a day: " + trigger.get("chance"));
		JsonArray conditions = trigger.getAsJsonArray("conditions");
		helper.assertTrue(effect(conditions, "rank_at_least").get("rank").getAsString().equals("town"), "the rank: " + conditions);
		helper.assertTrue(effect(conditions, "bandit_camps_broken").get("count").getAsInt() == 2, "camps broken: " + conditions);
		JsonArray chapters = arc.getAsJsonArray("chapters");
		helper.assertTrue(chapters.size() == 3, "chapters: " + chapters.size());
		JsonObject storm = chapters.get(2).getAsJsonObject();
		helper.assertTrue(storm.get("time_limit_days").getAsInt() == 10, "the storm's days: " + storm.get("time_limit_days"));
		JsonObject mood = effect(storm.getAsJsonArray("on_fail"), "village_mood");
		helper.assertTrue(mood.get("points").getAsInt() == -10 && mood.get("days").getAsInt() == 3, "the tribute's mood: " + mood);
		JsonObject fort = chapters.get(1).getAsJsonObject();
		JsonObject swords = fort.getAsJsonArray("quests").get(0).getAsJsonObject();
		JsonObject bring = swords.getAsJsonArray("objectives").get(0).getAsJsonObject();
		helper.assertTrue(bring.get("item").getAsString().equals("minecraft:emerald") && bring.get("count").getAsInt() == 24 && bring.get("to").getAsString().equals("hall"),
			"swords for hire: " + bring);
		helper.assertTrue(BanditKingStory.MERCENARIES == 6 && BanditKingStory.NEIGHBOUR_GUARDS == 3 && BanditKingStory.BREAD == 16,
			"allies: " + BanditKingStory.MERCENARIES + " mercenaries, " + BanditKingStory.NEIGHBOUR_GUARDS + " guards, " + BanditKingStory.BREAD + " bread");
		JsonArray ending = arc.getAsJsonArray("ending");
		JsonObject rep = effect(ending, "reputation");
		helper.assertTrue(rep.get("points").getAsInt() == 150 && rep.get("who").getAsString().equals("chapter_helpers"), "standing: " + rep);
		JsonObject honour = effect(ending, "honour");
		helper.assertTrue(honour.get("id").getAsString().equals("kingslayer") && honour.get("who").getAsString().equals("chapter_helpers"), "honor: " + honour);
		effect(ending, "festival");
		JsonObject flag = effect(ending, "flag");
		helper.assertTrue(flag.get("flag").getAsString().equals("no_bandit_camps") && flag.get("days").getAsInt() == 10, "no camps: " + flag);
		helper.succeed();
	}

	// ---- what the pages quote, word for word -----------------------------------------------------------------------

	/**
	 * Every sentence the two pages quote, as the mod writes it: the first chapter's smoke, the banner count, the siege's
	 * call with a portcullis (and without), a named bandit chief's camp ("Smoke rises beyond Oakvale: Chief Harl Ashgrave
	 * and a band of bandits have made camp ..."), another culture's ("... a band of raiders ..."), the honor
	 * Kingslayer and the tribute's mood.
	 */
	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '20'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theSentencesThePagesQuoteReadWordForWord(GameTestHelper helper) {
		says(helper, Component.translatable("arc.aliveworkplace.bandit_king.1.intro.1"),
			"Smoke rises from three hills round the village: three bandit camps went up in a night.");
		says(helper, Component.translatable("message.aliveworkplace.bandit_king.banner_taken", 2, 3), "A war banner of the Bandit King is taken (2 of 3).");
		says(helper, Component.translatable("message.aliveworkplace.siege.begins_portcullis", "Oakvale"),
			"Oakvale is under siege! The gates are shut and the portcullis is down.");
		says(helper, Component.translatable("message.aliveworkplace.siege.begins", "Oakvale"), "Oakvale is under siege! The gates are shut.");
		helper.assertTrue(Lairs.live(), "lairs aren't live in the test world");
		Lairs.Lair lair = new Lairs.Lair(BlockPos.ZERO, BlockPos.ZERO, UUID.randomUUID(), 0, Threats.BANDITS, 6, 0, 0, 0, 0);
		Component chief = Lairs.captainName(lair);
		says(helper, chief, "Chief Harl Ashgrave");
		String camp = Component.translatable("message.aliveworkplace.bandits.camp_named", "Oakvale", "to the south-east", chief).getString();
		helper.assertTrue(camp.startsWith("Smoke rises beyond Oakvale: Chief Harl Ashgrave and a band of bandits have made camp"), "the camp reads: " + camp);
		String other = Component.translatable("message.aliveworkplace.lair.camp_named", "Oakvale", "to the north", "Captain Vex").getString();
		helper.assertTrue(other.startsWith("Smoke rises beyond Oakvale: Captain Vex and a band of raiders have made camp"), "another camp reads: " + other);
		says(helper, Component.translatable("honour.aliveworkplace.kingslayer"), "Kingslayer");
		hasKey(helper, "mood.aliveworkplace.reason.tribute");
		says(helper, Component.translatable("item.aliveworkplace.kings_crown"), "The King's Crown");
		helper.succeed();
	}
}
