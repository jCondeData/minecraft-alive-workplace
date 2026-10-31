package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Risings;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-10-24) for pillager warbands (ROADMAP 32.7) and desert raiders (32.9), written from the items' text
 * for what WarbandGameTests and DesertRaiderGameTests leave unchecked: the warband's biome list from both sides and its
 * Town rank boundary (bandits still at a Village); the camp's strength growing 2 a day up to 18 and no more; the roster
 * as the item writes it (pillagers with crossbows 55%, vindicators 45%; husks 55%, gold-armoured husks with iron swords
 * 25%, gold-helmeted skeleton archers 20%); desert raiders only in the desert and badlands from Town; the Pharaoh's
 * golden gear and +50 health and the Tomb Camp's loot; the rising's 24-block reach (23 rises, 25 doesn't); the desert
 * siege still on just before noon and over at noon; and every line the two cultures make a player read.
 */
public class QaRaiderCulturesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(16, 1, 16);
	private static final long DAY = 24000;
	private static final ResourceLocation WARBAND = AliveWorkplace.id("pillager_warband");
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_sand_pilers");

	// --- A village by a hall, as WarbandGameTests makes one.

	private static BlockPos village(GameTestHelper helper, String biome) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		boolean open = Expansions.openForTests;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, hall, biome);
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG)
				|| m instanceof Vex).forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			Lairs.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			ThreatData.get(level).forgetSiege(hall);
			VillageHalls.RADIUS = radius;
			Threats.DISABLED = off;
			Expansions.openForTests = open;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			fill(level, hall, old);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	private static void rank(ServerLevel level, BlockPos hall, VillageRanks.Rank rank) {
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(rank);
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	/** How often, in 200 rolls, {@code culture} is the one that makes camp. */
	private static int comers(ServerLevel level, BlockPos hall, boolean bandits, ResourceLocation culture) {
		int n = 0;
		for (int i = 0; i < 200; i++) {
			if (Lairs.comer(level, hall, bandits, RandomSource.create(7000L + i)).equals(Optional.of(culture))) {
				n++;
			}
		}
		return n;
	}

	private static Culture culture(GameTestHelper helper, ResourceLocation id) {
		Optional<Culture> culture = Threats.get(id);
		helper.assertTrue(culture.isPresent(), id + " isn't loaded");
		return culture.get();
	}

	private static boolean lootChest(ServerLevel level, BlockPos at, ResourceLocation table) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-16, -3, -16), at.offset(16, 8, 16))) {
			if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null
				&& chest.getLootTable().location().equals(table)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The warband's lands from both sides at a Town: every land the item lists (meadows, the forests, taiga, the snowy
	 * lands, savanna) gets warbands, the lands it doesn't list (jungle, swamp, mushroom fields, desert, badlands, the
	 * ocean) never do.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarbandLands"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarbandLands")
	public void theWarbandComesOnlyToTheLandsTheItemLists(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			culture(helper, WARBAND);
			for (String biome : List.of("minecraft:meadow", "minecraft:forest", "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:flower_forest",
				"minecraft:cherry_grove", "minecraft:taiga", "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:savanna")) {
				fill(level, hall, biome);
				int n = comers(level, hall, true, WARBAND);
				helper.assertTrue(n > 100, "warbands in 200 camps by a Town in " + biome + ": " + n);
			}
			for (String biome : List.of("minecraft:jungle", "minecraft:swamp", "minecraft:mushroom_fields", "minecraft:desert", "minecraft:badlands",
				"minecraft:ocean", "minecraft:mangrove_swamp")) {
				fill(level, hall, biome);
				int n = comers(level, hall, true, WARBAND);
				helper.assertTrue(n == 0, "warbands in 200 camps by a Town in " + biome + ": " + n);
			}
			helper.succeed();
		});
	}

	/**
	 * The rank boundary in the plains: at a Village every camp is the bandits' (they still come at Village rank) and no
	 * warband; from Town on the warband comes, at a City too.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarbandRank"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarbandRank")
	public void banditsStillComeToAVillageAndTheWarbandFromTown(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			culture(helper, WARBAND);
			rank(level, hall, VillageRanks.Rank.VILLAGE);
			int bandits = comers(level, hall, true, Threats.BANDITS);
			helper.assertTrue(comers(level, hall, true, WARBAND) == 0, "a warband at a Village");
			helper.assertTrue(bandits == 200, "bandit camps in 200 by a Village in the plains: " + bandits);
			rank(level, hall, VillageRanks.Rank.TOWN);
			int town = comers(level, hall, true, WARBAND);
			helper.assertTrue(town > 120 && town < 180, "warbands in 200 camps by a Town: " + town);
			rank(level, hall, VillageRanks.Rank.CITY);
			int city = comers(level, hall, true, WARBAND);
			helper.assertTrue(city > 100, "warbands in 200 camps by a City: " + city);
			helper.succeed();
		});
	}

	/**
	 * Strength 10, 2 more a day, at most 18: a camp founded today is 10, a day later 12, three days later 16, four days
	 * later 18, and many days later still 18; the same dawn twice doesn't grow it twice; a save and load keeps it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWarbandGrowth"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWarbandGrowth")
	public void theWarCampGrowsTwoADayUpToEighteen(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			culture(helper, WARBAND);
			level.setDayTime(49 * DAY + 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null && lair.strength() == 10, "the war camp: " + lair);
			long day = lair.grown();
			Lairs.dawn(level, hall, day + 1);
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 12, "a day later: " + Lairs.near(level, hall).get());
			Lairs.dawn(level, hall, day + 1);
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 12, "the same dawn twice: " + Lairs.near(level, hall).get());
			Lairs.dawn(level, hall, day + 3);
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 16, "three days later: " + Lairs.near(level, hall).get());
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 16, "after a save and load: " + Lairs.near(level, hall));
			Lairs.dawn(level, hall, day + 4);
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 18, "four days later: " + Lairs.near(level, hall).get());
			Lairs.dawn(level, hall, day + 5);
			Lairs.dawn(level, hall, day + 30);
			helper.assertTrue(Lairs.near(level, hall).get().strength() == 18, "past the cap: " + Lairs.near(level, hall).get());
			helper.succeed();
		});
	}

	/**
	 * The rosters as the items write them, each one outfitted the way a raid outfits it: pillagers with crossbows 55%
	 * (the climbers) and vindicators 45%; husks 55%, husks in gold armour with iron swords 25%, skeleton archers in gold
	 * helmets 20%.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaRosters"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRosters")
	public void theRostersAreAsTheItemsWriteThem(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Mob> made = new ArrayList<>();
		Leftovers.after(helper, () -> made.forEach(Mob::discard));
		Culture warband = culture(helper, WARBAND);
		List<Culture.Member> w = warband.roster();
		helper.assertTrue(w.size() == 2 && w.get(0).entity().equals(ResourceLocation.parse("minecraft:pillager")) && w.get(0).share() == 55
			&& w.get(0).role() == Culture.Role.CLIMBER && w.get(1).entity().equals(ResourceLocation.parse("minecraft:vindicator")) && w.get(1).share() == 45,
			"the warband's roster: " + w);
		Mob pillager = outfitted(helper, warband, w.get(0), made);
		helper.assertTrue(pillager.getType() == EntityType.PILLAGER && pillager.getMainHandItem().is(Items.CROSSBOW), "the pillager: " + pillager.getMainHandItem());
		Mob vindicator = outfitted(helper, warband, w.get(1), made);
		helper.assertTrue(vindicator.getType() == EntityType.VINDICATOR, "the vindicator: " + vindicator);

		Culture desert = culture(helper, DESERT);
		List<Culture.Member> d = desert.roster();
		helper.assertTrue(d.size() == 3, "the desert roster: " + d);
		Mob husk = outfitted(helper, desert, d.get(0), made);
		helper.assertTrue(husk.getType() == EntityType.HUSK && d.get(0).share() == 55, "the plain husk: " + husk);
		Mob gold = outfitted(helper, desert, d.get(1), made);
		helper.assertTrue(gold.getType() == EntityType.HUSK && d.get(1).share() == 25 && gold.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET)
			&& gold.getItemBySlot(EquipmentSlot.CHEST).is(Items.GOLDEN_CHESTPLATE) && gold.getItemBySlot(EquipmentSlot.LEGS).is(Items.GOLDEN_LEGGINGS)
			&& gold.getItemBySlot(EquipmentSlot.FEET).is(Items.GOLDEN_BOOTS) && gold.getMainHandItem().is(Items.IRON_SWORD),
			"the husk in gold: " + gold.getItemBySlot(EquipmentSlot.CHEST) + " " + gold.getMainHandItem());
		Mob archer = outfitted(helper, desert, d.get(2), made);
		helper.assertTrue(archer.getType() == EntityType.SKELETON && d.get(2).share() == 20 && archer.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET)
			&& archer.getMainHandItem().is(Items.BOW), "the skeleton archer: " + archer.getItemBySlot(EquipmentSlot.HEAD) + " " + archer.getMainHandItem());
		helper.succeed();
	}

	private static Mob outfitted(GameTestHelper helper, Culture culture, Culture.Member member, List<Mob> made) {
		ServerLevel level = helper.getLevel();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos at = helper.absolutePos(new BlockPos(3 + 3 * made.size(), 2, 3));
		mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		mob.setNoAi(true);
		level.addFreshEntity(mob);
		made.add(mob);
		return mob;
	}

	/**
	 * Desert raiders' lands and rank: a Town in the desert and in the badlands gets them, a Village in the desert and a
	 * Town in the plains don't; their Tomb Camp has a named Pharaoh, a husk in a golden helmet and chestplate with a
	 * golden sword and 50 more health, and the Pharaoh's tomb loot in its chest.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaTombCamp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTombCamp")
	public void desertRaidersComeToDesertTownsWithTheirPharaoh(GameTestHelper helper) {
		BlockPos hall = village(helper, "minecraft:desert");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			culture(helper, DESERT);
			rank(level, hall, VillageRanks.Rank.TOWN);
			helper.assertTrue(comers(level, hall, false, DESERT) > 0, "no desert raiders by a Town in the desert");
			fill(level, hall, "minecraft:badlands");
			helper.assertTrue(comers(level, hall, false, DESERT) > 0, "no desert raiders by a Town in the badlands");
			fill(level, hall, "minecraft:plains");
			helper.assertTrue(comers(level, hall, true, DESERT) == 0, "desert raiders by a Town in the plains");
			fill(level, hall, "minecraft:desert");
			rank(level, hall, VillageRanks.Rank.VILLAGE);
			helper.assertTrue(comers(level, hall, true, DESERT) == 0, "desert raiders by a Village");
			rank(level, hall, VillageRanks.Rank.TOWN);

			level.setDayTime(49 * DAY + 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
			helper.assertTrue(lair != null && lair.culture().equals(DESERT) && lair.strength() == 10, "the tomb camp: " + lair);
			Mob pharaoh = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(pharaoh != null && pharaoh.getType() == EntityType.HUSK && pharaoh.getTags().contains(Risings.RAISER_TAG), "the Pharaoh: " + pharaoh);
			helper.assertTrue(pharaoh.getCustomName() != null && pharaoh.getCustomName().getString().length() > 3 && Lairs.named(lair),
				"the Pharaoh's name: " + pharaoh.getCustomName());
			helper.assertTrue(pharaoh.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET) && pharaoh.getItemBySlot(EquipmentSlot.CHEST).is(Items.GOLDEN_CHESTPLATE)
				&& pharaoh.getMainHandItem().is(Items.GOLDEN_SWORD), "the Pharaoh's gear: " + pharaoh.getItemBySlot(EquipmentSlot.HEAD) + " "
				+ pharaoh.getItemBySlot(EquipmentSlot.CHEST) + " " + pharaoh.getMainHandItem());
			helper.assertTrue(pharaoh.getMaxHealth() >= 70 && pharaoh.getHealth() >= 70, "the Pharaoh's health: " + pharaoh.getHealth() + "/" + pharaoh.getMaxHealth());
			helper.assertTrue(lootChest(level, lair.pos(), AliveWorkplace.id("chests/pharaohs_tomb")), "no chest of the Pharaoh's tomb loot");
			helper.succeed();
		});
	}

	/** The Pharaoh's reach is 24 blocks: a husk falling 23 blocks from him rises, one falling 25 blocks off doesn't. */
	//$ gametest_ticks_batch AREA '120' '"qaPharaohReach"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaPharaohReach")
	public void aHuskRisesWithinTwentyFourBlocksOfThePharaohAndNotBeyond(GameTestHelper helper) {
		Leftovers.clear(helper);
		Risings.forget();
		Culture culture = culture(helper, DESERT);
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Husk.class, box.inflate(8), h -> true).forEach(Mob::discard);
			Risings.forget();
		});
		Husk pharaoh = helper.spawn(EntityType.HUSK, new BlockPos(2, 2, 4));
		pharaoh.setNoAi(true);
		Risings.mark(pharaoh, culture.captain().orElseThrow());
		Husk far = helper.spawn(EntityType.HUSK, new BlockPos(27, 2, 4));
		far.setNoAi(true);
		far.addTag(VillageRaids.TAG);
		helper.assertTrue(Math.abs(far.distanceTo(pharaoh) - 25) < 0.01, "the far husk is " + far.distanceTo(pharaoh) + " off");
		far.kill();
		helper.assertTrue(Risings.pending() == 0, "a husk 25 blocks off is waiting to rise");
		Husk near = helper.spawn(EntityType.HUSK, new BlockPos(25, 2, 4));
		near.setNoAi(true);
		near.addTag(VillageRaids.TAG);
		helper.assertTrue(Math.abs(near.distanceTo(pharaoh) - 23) < 0.01, "the near husk is " + near.distanceTo(pharaoh) + " off");
		near.kill();
		helper.assertTrue(Risings.pending() == 1, "a husk 23 blocks off isn't waiting to rise");
		helper.runAfterDelay(80, () -> {
			List<Husk> up = level.getEntitiesOfClass(Husk.class, box, h -> h.isAlive() && h.getTags().contains(Risings.RISEN_TAG));
			helper.assertTrue(up.size() == 1 && up.get(0).blockPosition().closerThan(helper.absolutePos(new BlockPos(25, 2, 4)), 1.5),
				"risen: " + up.stream().map(h -> h.blockPosition().toShortString()).toList());
			helper.succeed();
		});
	}

	// --- The ladder tests' yard, as DesertRaiderGameTests builds it.

	private static final BlockPos YARD_HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
	}

	private static Culture pilers(GameTestHelper helper) {
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:husk\", \"share\": 40, \"role\": \"melee\"}],"
			+ " \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	private static Yard yard(GameTestHelper helper, Culture culture) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(YARD_HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Ladders.forget();
			SandRamps.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				if (x < 11 || x > 17) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(11, 2, FRONT - 2));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
		return new Yard(level, hall, culture, villager);
	}

	private static Mob raider(GameTestHelper helper, Yard yard, Culture.Role role, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == role).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		// The same raider every run (as DesertRaiderGameTests, B372, B591): finalizeSpawn rolls a baby or a chicken jockey
		// on the level's random, and the mob's own random drives its wandering and pathing.
		if (mob instanceof net.minecraft.world.entity.monster.Zombie zombie) {
			zombie.setBaby(false);
		}
		if (mob.getVehicle() != null) {
			net.minecraft.world.entity.Entity vehicle = mob.getVehicle();
			mob.stopRiding();
			vehicle.discard();
		}
		mob.getRandom().setSeed(1L);
		Threats.outfit(level, mob, yard.culture(), member);
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	private static Map<BlockPos, BlockState> blocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			out.put(pos.immutable(), level.getBlockState(pos));
		}
		return out;
	}

	private static List<String> changes(GameTestHelper helper, Map<BlockPos, BlockState> before) {
		ServerLevel level = helper.getLevel();
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			BlockState now = level.getBlockState(e.getKey());
			if (now != e.getValue()) {
				out.add(e.getKey().toShortString() + ": " + e.getValue() + " -> " + now);
			}
		}
		return out;
	}

	/**
	 * Until noon, at its edge: a desert siege is still on a tick before noon and over at noon itself (6000), its raiders
	 * gone.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDesertNoon"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDesertNoon")
	public void theDesertSiegeIsOnJustBeforeNoonAndOverAtNoon(GameTestHelper helper) {
		Yard yard = yard(helper, culture(helper, DESERT));
		ServerLevel level = yard.level();
		long day = level.getDayTime() / DAY + 1;
		level.setDayTime(day * DAY + 15000);
		Mob husk = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 23.5);
		husk.setNoAi(true);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(husk));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), "the desert raid laid no siege");
		ThreatData data = ThreatData.get(level);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		tag.getList("raids", Tag.TAG_COMPOUND).getCompound(0).putLong("began", level.getGameTime() - 2000);
		data.read(tag);
		helper.runAfterDelay(2, () -> {
			for (long time : new long[] {DAY + 5000, DAY + 5999}) {
				level.setDayTime(day * DAY + time);
				VillageRaids.tick(level, yard.hall(), 8, 0, day, d -> { });
				helper.assertTrue(VillageRaids.active(yard.hall()).isPresent(), "the desert raiders left at " + time % DAY);
				helper.assertTrue(Sieges.siege(level, yard.hall()).map(s -> !s.over).orElse(false), "the siege is over at " + time % DAY);
			}
			level.setDayTime(day * DAY + DAY + 6000);
			VillageRaids.tick(level, yard.hall(), 8, 0, day, d -> { });
			helper.assertTrue(VillageRaids.active(yard.hall()).isEmpty() && VillageRaids.raiders(level, yard.hall()).isEmpty(), "the desert raiders stayed at noon");
			helper.assertTrue(Sieges.siege(level, yard.hall()).map(s -> s.over).orElse(true), "the siege isn't over at noon");
			helper.succeed();
		});
	}

	/**
	 * A player's own sand at the outside foot of the Stone Wall (blocks at {@code x}, y 2, one out from the wall), then a
	 * husk climber: it should ramp over, and when the siege is lifted every grain the raiders piled is gone, the
	 * player's sand still there and nothing else changed. Used by QaRaiderCulturesBugTests (the ramp stalls there).
	 */
	static void playersSand(GameTestHelper helper, List<Integer> xs) {
		atTheWallsFoot(helper, xs, Blocks.SAND, null);
	}

	/**
	 * {@code foot} standing at the Stone Wall's outside foot at each of {@code xs} (on {@code ground}, when given, laid
	 * under it first): the husk should still ramp over the wall. Sand must stay after the siege, untouched; anything else
	 * (a plant the ramp buries) may be gone, but no other block may change.
	 */
	static void atTheWallsFoot(GameTestHelper helper, List<Integer> xs, net.minecraft.world.level.block.Block foot,
		@org.jetbrains.annotations.Nullable net.minecraft.world.level.block.Block ground) {
		atTheWallsFoot(helper, xs, foot, ground, foot == Blocks.SAND, false);
	}

	/**
	 * As above, with {@code keep} saying whether {@code foot} must stay after the siege (a player's sand or flower) or may be
	 * buried and gone (a wild plant), and {@code reload} putting the threats through a save and load once the ramp is up,
	 * before the siege is lifted (lane D QA 2026-10-31). No item may drop in the yard.
	 */
	static void atTheWallsFoot(GameTestHelper helper, List<Integer> xs, net.minecraft.world.level.block.Block foot,
		@org.jetbrains.annotations.Nullable net.minecraft.world.level.block.Block ground, boolean keep, boolean reload) {
		Yard yard = yard(helper, pilers(helper));
		ServerLevel level = yard.level();
		List<BlockPos> mine = new ArrayList<>();
		if (ground != null) {
			// the ground in front of the wall, out to where the husk comes from
			for (int x = WEST; x <= EAST; x++) {
				for (int z = FRONT + 1; z <= FRONT + 10; z++) {
					helper.setBlock(new BlockPos(x, 0, z), Blocks.SANDSTONE); // under the floor: sand there would fall
					helper.setBlock(new BlockPos(x, 1, z), ground);
				}
			}
		}
		for (int x : xs) {
			helper.setBlock(new BlockPos(x, 2, FRONT + 1), foot);
			mine.add(helper.absolutePos(new BlockPos(x, 2, FRONT + 1)));
		}
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 23.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		boolean[] ramped = {false};
		helper.onEachTick(() -> {
			List<BlockPos> sand = SandRamps.sand(level, yard.hall());
			BlockPos base = helper.absolutePos(BlockPos.ZERO);
			if (!ramped[0] && helper.getTick() > 850) {
				helper.fail("no ramp in time: piled " + sand.stream().map(p -> p.subtract(base).toShortString()).toList() + ", the husk at "
					+ climber.position().subtract(base.getX(), base.getY(), base.getZ()) + (climber.isAlive() ? "" : " (dead)"));
			}
			for (BlockPos p : sand) {
				helper.assertTrue(!keep || !mine.contains(p), "the player's sand at " + p.subtract(base).toShortString() + " is recorded as the raiders'");
			}
			if (ramped[0]) {
				return;
			}
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent() && siege.get().ramped) {
				ramped[0] = true;
				climber.discard();
				VillageRaids.forget(yard.hall());
				ThreatData.Siege lifted = siege.get();
				if (reload) {
					int piled = lifted.sand.size();
					ThreatData.get(level).roundTrip(level.registryAccess());
					lifted = Sieges.siege(level, yard.hall()).orElse(null);
					helper.assertTrue(lifted != null, "the siege was lost in the save and load");
					helper.assertTrue(lifted.sand.size() == piled, "the ramp's sand after the load: " + lifted.sand.size() + " of " + piled);
				}
				Sieges.lift(level, lifted);
				helper.runAfterDelay(5, () -> {
					List<net.minecraft.world.entity.item.ItemEntity> drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						helper.getBounds().inflate(2));
					helper.assertTrue(drops.isEmpty(), "dropped: " + drops.stream().map(e -> e.getItem().toString()).toList());
					for (BlockPos p : mine) {
						helper.assertTrue(!keep || level.getBlockState(p).is(foot), "the player's " + foot + " at " + p.subtract(base).toShortString() + " was taken away");
						helper.assertTrue(!level.getBlockState(p).is(Blocks.SAND) || keep, "the raiders' sand left at " + p.subtract(base).toShortString());
					}
					helper.assertTrue(SandRamps.sand(level, yard.hall()).isEmpty(), "sand still recorded");
					List<String> changed = new ArrayList<>(changes(helper, before));
					if (!keep) {
						for (BlockPos p : mine) {
							changed.removeIf(c -> c.startsWith(p.toShortString() + ":") && c.endsWith("Block{minecraft:air}"));
						}
					}
					helper.assertTrue(changed.isEmpty(), "blocks changed after the siege: " + changed);
					helper.succeed();
				});
			}
		});
	}

	/**
	 * B591: the husk stopped within two blocks of its spot beside the ramp's outer end, but on the block the ramp's last
	 * stage begins with; no sand is piled onto a raider, so the ramp stood one short of the top for good. Here it is put
	 * there just as the stage before the last is done: it should step aside and the ramp reach the top.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaHuskOnTheNextBlock"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaHuskOnTheNextBlock")
	public void aHuskStandingWhereTheRampsNextBlockGoesStepsAside(GameTestHelper helper) {
		Yard yard = yard(helper, pilers(helper));
		ServerLevel level = yard.level();
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 23.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		BlockPos[] put = {null};
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> found = Sieges.siege(level, yard.hall());
			if (found.isEmpty()) {
				return;
			}
			ThreatData.Siege siege = found.get();
			if (siege.ramped) {
				helper.assertTrue(put[0] != null, "ramped before the husk was put in the way");
				helper.succeed();
				return;
			}
			if (helper.getTick() > 850) {
				helper.fail("no ramp in time: put at " + (put[0] == null ? "nowhere" : put[0].toShortString()) + ", sand " + siege.sand.size()
					+ ", the husk at " + climber.position() + (climber.isAlive() ? "" : " (dead)"));
			}
			if (put[0] != null || siege.rampFeet.isEmpty()) {
				return;
			}
			BlockPos foot = siege.rampFeet.get(0);
			net.minecraft.core.Direction into = net.minecraft.core.Direction.from3DDataValue(siege.rampDirs.get(0));
			BlockPos wall = foot.relative(into);
			int h = 0;
			while (h <= SandRamps.MAX_HEIGHT && (!level.getBlockState(wall.above(h)).getCollisionShape(level, wall.above(h)).isEmpty()
				|| !level.getBlockState(wall.above(h + 1)).getCollisionShape(level, wall.above(h + 1)).isEmpty())) {
				h++;
			}
			helper.assertTrue(h >= 3, "the wall is only " + h + " high");
			if (siege.sand.size() == (h - 1) * h / 2) {
				// The last stage's outer end: two from where the husk waits (foot - (h + 1)).
				BlockPos at = foot.relative(into, -(h - 1));
				helper.assertTrue(level.getBlockState(at).isAir(), "the last stage's first block is already " + level.getBlockState(at));
				climber.getNavigation().stop();
				climber.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				put[0] = at.subtract(helper.absolutePos(BlockPos.ZERO));
			}
		});
	}

	/** Every line the two cultures make a player read is in the lang file, the twenty names of each chief too, and none is blank. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void everyLineOfBothCulturesIsWritten(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> missing = new ArrayList<>();
		for (ResourceLocation id : List.of(WARBAND, DESERT)) {
			Culture culture = culture(helper, id);
			List<String> keys = new ArrayList<>(culture.messages().own().values());
			culture.captain().ifPresent(c -> {
				helper.assertTrue(c.names().isPresent() && c.title().isPresent(), "no names or title for the chief of " + id);
				for (int i = 1; i <= Lairs.NAMES; i++) {
					keys.add(c.names().get() + "." + i);
				}
				keys.add(c.title().get());
			});
			String group = id.equals(WARBAND) ? "warband" : "desert";
			for (String event : List.of("raid", "raid_named", "raid_led", "camp", "camp_named", "broken", "broken_named", "broken_by", "broken_by_named")) {
				keys.add("chronicle.aliveworkplace." + group + "." + event);
			}
			keys.add("threat.aliveworkplace." + id.getPath() + ".name");
			keys.add("threat.aliveworkplace." + id.getPath() + ".lair");
			for (String key : keys) {
				if (!lang.has(key) || lang.getOrDefault(key).isBlank()) {
					missing.add(key);
				}
			}
			helper.assertTrue(!Lairs.lairName(id).getString().startsWith("threat.") && !Lairs.cultureName(id).getString().startsWith("threat."),
				"an untranslated name for " + id);
		}
		helper.assertTrue(lang.has("message.aliveworkplace.siege.sand_ramps"), "no text for the sand ramp's warning");
		helper.assertTrue(missing.isEmpty(), "no text for " + missing);
		helper.succeed();
	}
}
