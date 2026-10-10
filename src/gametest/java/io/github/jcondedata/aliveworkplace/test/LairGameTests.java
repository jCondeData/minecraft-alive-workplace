package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/**
 * Lairs for every culture (ROADMAP 32.3): the lair's strength and what a raid does to it, the captain's name on him, on
 * the hall's Defence page and in the chronicle, a bandit camp saved by 0.138.0, the lair of a culture other than the
 * bandits, what the switches and the expansion gate leave of it, and a lair broken up while its raiders are out.
 */
public class LairGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** The ground the camp's middle goes on, in the far corner from the hall. */
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = 24000;
	private static final ResourceLocation WARBAND = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_warband");

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A hall with a village radius of 16 and four villagers; what the test changes goes when it ends. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		boolean open = Expansions.openForTests;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Threats.DISABLED = off;
			Expansions.openForTests = open;
			level.setDayTime(time);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** The band standing at the lair, its captain apart. */
	private static List<Mob> men(ServerLevel level, Lairs.Lair lair) {
		List<Mob> men = new ArrayList<>(Lairs.band(level, lair));
		men.removeIf(m -> m.getUUID().equals(lair.captain()));
		return men;
	}

	private static Lairs.Lair lair(GameTestHelper helper, BlockPos hall) {
		Optional<Lairs.Lair> lair = Lairs.near(helper.getLevel(), hall);
		helper.assertTrue(lair.isPresent(), "no lair by the village");
		return lair.get();
	}

	/** Every line of an icon: its name, then its tooltip. */
	private static List<String> lines(ItemStack icon) {
		List<String> lines = new ArrayList<>();
		lines.add(icon.getHoverName().getString());
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(line -> lines.add(line.getString()));
		}
		return lines;
	}

	private static String last(VillageHallBlockEntity entity) {
		List<Chronicle.Entry> chronicle = entity.chronicle();
		return chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
	}

	/**
	 * The strength. A bandit camp is founded with 6, all of them at the camp; one killed there is one less. A raid of 5
	 * from a lair of strength 8 leaves 3 at the camp (and a save and load keeps who is out); the two raiders alive at dawn
	 * bring it back to 5; each day adds one, up to 10. After a night that cost it everyone it has nobody to send.
	 */
	//$ gametest_ticks_batch AREA '100' '"lairStrength"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lairStrength")
	public void aRaidTakesItsRaidersFromTheLairsStrength(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 40, 1000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			Lairs.Lair founded = lair(helper, hall);
			helper.assertTrue(founded.culture().equals(Threats.BANDITS) && founded.strength() == 6 && founded.out() == 0 && founded.home() == 6
				&& founded.day() == 40 && founded.grown() == 40, "the lair as founded: " + founded);
			helper.assertTrue(men(level, founded).size() == 6, "the band at home: " + men(level, founded).size());
			// One of the band killed at the camp is gone for good.
			men(level, founded).get(0).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(lair(helper, hall).strength() == 5, "a bandit killed at the camp: strength " + lair(helper, hall).strength());
			Lairs.round(level, hall, false);
			helper.assertTrue(men(level, founded).size() == 5, "the dead bandit was put back: " + men(level, founded).size());

			// A raid of 5 from a lair of strength 8 leaves 3.
			Lairs.setStrength(level, hall, 8);
			Lairs.round(level, hall, false);
			helper.assertTrue(men(level, founded).size() == 8, "a band of 8 at home: " + men(level, founded).size());
			at(level, 40, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0);
			helper.assertTrue(raid != null && raid.raiders() == 5, "the raid: " + raid);
			Lairs.Lair raiding = lair(helper, hall);
			helper.assertTrue(raiding.strength() == 8 && raiding.out() == 5 && raiding.home() == 3, "with 5 out: " + raiding);
			helper.assertTrue(men(level, raiding).size() == 3, "left at the camp: " + men(level, raiding).size());
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == 5 && raiders.stream().allMatch(m -> m.getTags().contains(Lairs.TAG) && m.getTags().contains(BanditCamps.TAG)),
				"the raiders: " + raiders);
			// Saved and loaded in the middle of the raid: the same lair, the same five out.
			Lairs.Data data = Lairs.Data.get(level);
			data.roundTrip(level.registryAccess());
			helper.assertTrue(lair(helper, hall).equals(raiding), "after a save and load: " + lair(helper, hall) + ", was " + raiding);

			// Three fall; at dawn the two alive leave and rejoin: 5.
			for (int i = 0; i < 3; i++) {
				raiders.get(i).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			}
			helper.assertTrue(lair(helper, hall).strength() == 8, "a raider's death was counted before the raid ended");
			ThreatData.get(level).begin(new ThreatData.Under(hall, Threats.BANDITS, 5, level.getGameTime() - 2000)); // (the raid began a while ago)
			at(level, 40, 22500);
			VillageRaids.tick(level, hall, 8, 0, 40, day -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty(), "the raid didn't end at dawn");
			Lairs.Lair home = lair(helper, hall);
			helper.assertTrue(home.strength() == 5 && home.out() == 0 && home.home() == 5 && home.lost() == 3, "after the raid: " + home);
			helper.assertTrue(men(level, home).size() == 5, "the two didn't rejoin the band: " + men(level, home).size());
			List<ThreatData.Past> past = ThreatData.get(level).past(hall);
			helper.assertTrue(past.size() == 1 && past.get(0).equals(new ThreatData.Past(40, Threats.BANDITS, 5, 3, true)), "the attack remembered: " + past);

			// Each day adds one, in the hall's round, up to 10.
			at(level, 41, 1000);
			Lairs.round(level, hall, false);
			helper.assertTrue(lair(helper, hall).strength() == 6, "a day later: " + lair(helper, hall));
			Lairs.round(level, hall, false);
			helper.assertTrue(lair(helper, hall).strength() == 6, "it grew twice in a day: " + lair(helper, hall));
			at(level, 42, 1000);
			Lairs.round(level, hall, false);
			helper.assertTrue(lair(helper, hall).strength() == 7 && men(level, home).size() == 7, "two days later: " + lair(helper, hall));
			Lairs.dawn(level, hall, 60);
			helper.assertTrue(lair(helper, hall).strength() == 10, "past its most: " + lair(helper, hall));

			// A costly night: a lair of 2 sends 2, both fall, and it has nobody to send.
			Lairs.setStrength(level, hall, 2);
			at(level, 60, 15000);
			VillageRaids.Raid small = VillageRaids.start(level, hall, 8, 0);
			helper.assertTrue(small != null && small.raiders() == 2, "a raid from a lair of 2: " + small);
			VillageRaids.raiders(level, hall).forEach(m -> m.hurt(level.damageSources().fellOutOfWorld(), 1000f));
			VillageRaids.tick(level, hall, 8, 0, 60, day -> { });
			Lairs.Lair weak = lair(helper, hall);
			helper.assertTrue(weak.strength() == 0 && weak.out() == 0 && weak.lost() == 2, "after the costly night: " + weak);
			helper.assertTrue(VillageRaids.start(level, hall, 8, 0) == null && VillageRaids.raiders(level, hall).isEmpty(), "a lair with nobody left raided");
			helper.assertTrue(level.getEntity(weak.captain()) instanceof Mob captain && captain.isAlive(), "the captain left with his band");
			helper.succeed();
		});
	}

	/**
	 * The captain's name: one of his culture's twenty, with his title, over his head, in the chronicle's lines (the camp,
	 * the raid, the break-up) and on the hall's Defence page, which the guards icon opens. The camp's chest holds the
	 * culture's loot. The captain killed while his raiders are out: the lair is broken up, the raid still ends as raids
	 * do, the village rests 5 days and the page says so.
	 */
	//$ gametest_ticks_batch AREA '100' '"lairCaptain"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lairCaptain")
	public void theCaptainsNameIsOnHimOnTheDefencePageAndInTheChronicle(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			// The twenty names, each a real line.
			Set<String> names = new HashSet<>();
			for (int i = 1; i <= Lairs.NAMES; i++) {
				String name = Component.translatable("threat.aliveworkplace.bandits.names." + i).getString();
				helper.assertFalse(name.contains("threat.") || name.isBlank(), "name " + i + " has no line: " + name);
				names.add("Chief " + name);
			}
			helper.assertTrue(names.size() == 20 && names.contains("Chief Harl Ashgrave"), "the bandits' names: " + names);

			at(level, 50, 1000);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			Lairs.Lair lair = lair(helper, hall);
			String name = Lairs.captainName(lair).getString();
			helper.assertTrue(names.contains(name) && Lairs.named(lair), "the captain's name: " + name);
			// On him.
			Mob chief = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(chief != null && chief.getCustomName() != null && chief.getCustomName().getString().equals(name) && chief.isCustomNameVisible(),
				"over his head: " + (chief == null ? null : chief.getCustomName()));
			helper.assertTrue(chief.getTags().contains(Lairs.CAPTAIN_TAG) && chief.getTags().contains(BanditCamps.CHIEF_TAG), "his tags: " + chief.getTags());
			// In the chronicle.
			helper.assertTrue(last(entity).contains(name) && last(entity).contains("made camp"), "the chronicle's camp line: " + last(entity));
			// The chest holds the bandits' loot.
			ResourceKey<?> loot = null;
			for (BlockPos p : BlockPos.betweenClosed(lair.pos().offset(-8, 0, -8), lair.pos().offset(8, 3, 8))) {
				if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null) {
					loot = chest.getLootTable();
				}
			}
			helper.assertTrue(ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id("chests/bandit_camp")).equals(loot), "the chest's loot: " + loot);

			// On the hall: the guards icon names him and opens the Defence page.
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			helper.assertTrue(lines(menu.icon(VillageHallScreen.GUARDS)).stream().anyMatch(l -> l.contains(name)), "the guards icon: " + lines(menu.icon(VillageHallScreen.GUARDS)));
			menu.press(VillageHallScreen.GUARDS, player);
			helper.assertTrue(lines(menu.icon(DefencePage.HEADER)).get(0).contains("Defence"), "the page's header: " + lines(menu.icon(DefencePage.HEADER)));
			helper.assertTrue(menu.icon(DefencePage.LAIR).is(Items.CAMPFIRE) && lines(menu.icon(DefencePage.LAIR)).get(0).equals("Bandit camp")
				&& lines(menu.icon(DefencePage.LAIR)).contains("Raiders: Bandits"), "the lair: " + lines(menu.icon(DefencePage.LAIR)));
			helper.assertTrue(lines(menu.icon(DefencePage.CAPTAIN)).get(0).equals(name), "the captain on the page: " + lines(menu.icon(DefencePage.CAPTAIN)));
			helper.assertTrue(menu.icon(DefencePage.STRENGTH).getCount() == 6 && lines(menu.icon(DefencePage.STRENGTH)).get(0).equals("Strength: 6")
				&& lines(menu.icon(DefencePage.STRENGTH)).contains("At the camp: 6") && lines(menu.icon(DefencePage.STRENGTH)).contains("Out raiding: 0"),
				"the strength: " + lines(menu.icon(DefencePage.STRENGTH)));
			String where = lines(menu.icon(DefencePage.WHERE)).get(0);
			helper.assertTrue(where.matches("(north|south|east|west|north-east|north-west|south-east|south-west), about \\d+0 blocks"), "roughly where: " + where);
			helper.assertTrue(lines(menu.icon(DefencePage.STOOD)).get(0).equals("Made camp today"), "the days it has stood: " + lines(menu.icon(DefencePage.STOOD)));
			helper.assertTrue(lines(menu.icon(DefencePage.HISTORY[1])).get(0).equals("No attack yet"), "no attack yet: " + lines(menu.icon(DefencePage.HISTORY[1])));
			for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
				for (String line : lines(menu.icon(slot))) {
					helper.assertFalse(line.contains("aliveworkplace.") || line.contains("%"), "slot " + slot + " shows a raw line: " + line);
				}
			}
			at(level, 53, 1000);
			DefencePage.render(menu, level, hall);
			helper.assertTrue(lines(menu.icon(DefencePage.STOOD)).get(0).equals("Has stood 3 days") && menu.icon(DefencePage.STOOD).getCount() == 3,
				"three days on: " + lines(menu.icon(DefencePage.STOOD)));
			// Back to the hall's first page.
			menu.press(DefencePage.BACK, player);
			helper.assertTrue(menu.icon(VillageHallScreen.GUARDS).is(Items.IRON_SWORD) && menu.icon(VillageHallScreen.NAME).is(Items.NAME_TAG), "back didn't go back");

			// His raid carries his name.
			at(level, 53, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0);
			helper.assertTrue(raid != null && raid.raiders() == 5, "the raid: " + raid);
			helper.assertTrue(last(entity).contains(name + "'s bandits raided"), "the chronicle's raid line: " + last(entity));

			// He falls to a player while his five are out: the lair is broken up and the chronicle names both.
			chief.hurt(level.damageSources().playerAttack(player), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && BanditCamps.near(level, hall).isEmpty(), "the lair still stands");
			helper.assertTrue(last(entity).contains(name) && last(entity).contains(player.getGameProfile().getName()) && last(entity).contains("broke up"),
				"the chronicle's break-up line: " + last(entity));
			helper.assertTrue(level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(lair.pos()).inflate(12),
				m -> m.isAlive() && m.getTags().contains(Lairs.TAG) && !m.getTags().contains(VillageRaids.TAG)).isEmpty(), "the band didn't scatter");
			helper.assertTrue(Lairs.resting(level, hall) == 5, "the rest: " + Lairs.resting(level, hall));
			// The raid goes on without a lair, and ends as raids do.
			helper.assertTrue(VillageRaids.active(hall).isPresent() && VillageRaids.raiders(level, hall).size() == 5, "the raiders left with the lair");
			VillageRaids.raiders(level, hall).forEach(m -> m.hurt(level.damageSources().fellOutOfWorld(), 1000f));
			VillageRaids.tick(level, hall, 8, 0, 53, day -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && Lairs.near(level, hall).isEmpty(), "the raid didn't end");

			// The page now: no camp, the rest, and the attack that was fought off.
			DefencePage.render(menu, level, hall);
			for (int slot = DefencePage.LAIR; slot <= DefencePage.STOOD; slot++) {
				helper.assertTrue(lines(menu.icon(slot)).get(0).equals("No camp near"), "slot " + slot + " with no lair: " + lines(menu.icon(slot)));
			}
			helper.assertTrue(lines(menu.icon(DefencePage.LAIR)).get(1).contains("5 days"), "the rest on the page: " + lines(menu.icon(DefencePage.LAIR)));
			List<String> attack = lines(menu.icon(DefencePage.HISTORY[0]));
			helper.assertTrue(menu.icon(DefencePage.HISTORY[0]).getCount() == 5 && attack.get(0).equals("Day 53: Bandits") && attack.contains("5 came, 5 fell")
				&& attack.contains("Fought off: none got away"), "the last attack: " + attack);
			// While the village rests no lair comes, whatever the dice say; the rest ends after 5 days.
			helper.assertTrue(Lairs.resting(level, hall) > 0, "no rest");
			at(level, 57, 1000);
			helper.assertTrue(Lairs.resting(level, hall) == 1, "a day of rest left: " + Lairs.resting(level, hall));
			at(level, 58, 1000);
			helper.assertTrue(Lairs.resting(level, hall) == 0, "the rest didn't end: " + Lairs.resting(level, hall));
			helper.succeed();
		});
	}

	/**
	 * A bandit camp saved by 0.138.0 (the fixture is its {@code aliveworkplace_bandit_camps} file: {@code pos},
	 * {@code hall}, {@code chief}, {@code day} per camp; {@code hall}, {@code day} per camp broken up) loads with its
	 * camp, its chief and its rest days: a bandits' lair of the culture's strength, nobody out, the chief's name the same
	 * on every load. What is saved now loads back the same, and an empty file is no lair.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"lairOldSave"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "lairOldSave")
	public void aBanditCampSavedBy0138Loads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			BanditCamps.forget(level);
			level.setDayTime(time);
		});
		CompoundTag file;
		try (java.io.InputStream in = LairGameTests.class.getResourceAsStream("/fixtures/bandit_camps_0.138.0.snbt")) {
			helper.assertTrue(in != null, "missing fixture");
			file = TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (java.io.IOException | com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new GameTestAssertException("the fixture can't be read: " + e);
		}
		BlockPos hall = new BlockPos(100, 64, 200);
		BlockPos camp = new BlockPos(190, 64, 260);
		BlockPos rested = new BlockPos(-300, 70, 40);
		UUID chief = new UUID(0x1234567899BCDEF0L, 0x0123456787654321L);
		at(level, 14, 1000);
		Lairs.Data data = Lairs.Data.get(level);
		data.read(file.getCompound("data"));
		// Its camp and chief, through the bandit camps' own methods.
		BanditCamps.Camp old = BanditCamps.near(level, hall).orElse(null);
		helper.assertTrue(old != null && old.pos().equals(camp) && old.hall().equals(hall) && old.chief().equals(chief) && old.day() == 10, "the camp: " + old);
		helper.assertTrue(BanditCamps.all(level).size() == 1 && Lairs.all(level).size() == 1, "the camps: " + BanditCamps.all(level));
		// As a lair: the bandits', their strength, nobody out, last grown the day it was made.
		Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
		helper.assertTrue(lair.culture().equals(Threats.BANDITS) && lair.strength() == 6 && lair.out() == 0 && lair.lost() == 0 && lair.grown() == 10
			&& lair.name() >= 0 && lair.name() < Lairs.NAMES, "the lair: " + lair);
		String name = Lairs.captainName(lair).getString();
		helper.assertTrue(name.startsWith("Chief ") && !name.contains("threat."), "the chief's name: " + name);
		// Its rest days: broken up on day 12, so three of the five are left on day 14; the other village has none.
		helper.assertTrue(Lairs.resting(level, rested) == 3 && Lairs.resting(level, hall) == 0, "the rest: " + Lairs.resting(level, rested));
		// The same on every load, and what is saved now loads back the same.
		data.read(file.getCompound("data"));
		helper.assertTrue(Lairs.near(level, hall).orElseThrow().equals(lair), "a second load differs: " + Lairs.near(level, hall));
		CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.getList("camps", 10).getCompound(0).getUUID("chief").equals(chief)
			&& saved.getList("camps", 10).getCompound(0).getString("culture").equals("aliveworkplace:bandits")
			&& saved.getList("broken_up", 10).getCompound(0).getInt("rest") == 5, "saved: " + saved);
		data.roundTrip(level.registryAccess());
		helper.assertTrue(Lairs.near(level, hall).orElseThrow().equals(lair) && Lairs.resting(level, rested) == 3, "after a save and load: " + Lairs.near(level, hall));
		// It has stood four days since it last grew (the day it was made): four more, its most.
		Lairs.dawn(level, hall, 14);
		helper.assertTrue(Lairs.near(level, hall).orElseThrow().strength() == 10, "the days it stood: " + Lairs.near(level, hall));
		data.read(new CompoundTag());
		helper.assertTrue(Lairs.all(level).isEmpty() && Lairs.resting(level, rested) == 0, "an empty file isn't empty");
		helper.succeed();
	}

	/**
	 * Any culture with a lair: the test pack's warband makes camp with its own captain, strength and band, raids from it
	 * (no more than its strength), and isn't a bandit camp to anyone who asks for one.
	 */
	//$ gametest_ticks_batch AREA '100' '"lairOtherCulture"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lairOtherCulture")
	public void anotherCulturesLairStandsAndRaids(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			at(level, 70, 15000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null && lair.culture().equals(WARBAND) && lair.strength() == 7, "the warband's lair: " + lair);
			helper.assertTrue(Lairs.near(level, hall).isPresent() && BanditCamps.near(level, hall).isEmpty() && BanditCamps.all(level).isEmpty(),
				"a warband's lair passes for a bandit camp");
			Mob captain = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(captain != null && captain.getTags().contains(Lairs.CAPTAIN_TAG) && !captain.getTags().contains(BanditCamps.CHIEF_TAG)
				&& captain.getMaxHealth() > 40 && captain.isPersistenceRequired(), "its captain: " + captain);
			List<Mob> men = men(level, lair);
			helper.assertTrue(men.size() == 7 && men.stream().allMatch(m -> m.getTags().contains(Lairs.TAG) && !m.getTags().contains(BanditCamps.TAG)
				&& !m.getTags().contains(VillageRaids.TAG) && m.isPersistenceRequired()), "its band: " + men);
			helper.assertTrue(men.stream().anyMatch(m -> m.getType() == EntityType.HUSK) && men.stream().anyMatch(m -> m.getType() == EntityType.STRAY),
				"the band isn't its culture's roster: " + men);
			// The village's raids are the warband's now, and no bigger than its lair.
			helper.assertTrue(VillageRaids.cultureFor(level, hall, 8, RandomSource.create(4L)).map(c -> c.id().equals(WARBAND)).orElse(false), "who raids");
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0);
			helper.assertTrue(raid != null && raid.raiders() == 7, "a raid of the whole lair: " + raid);
			Lairs.Lair out = lair(helper, hall);
			helper.assertTrue(out.out() == 7 && out.home() == 0 && men(level, out).isEmpty(), "with everyone out: " + out);
			// The captain falls: this lair is broken up like any other.
			captain.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) == 5, "the warband's lair still stands");
			helper.succeed();
		});
	}

	/**
	 * The switches. Bandits come to a village of Village rank or more; not with their switch off, not when the config
	 * turns their culture off, not to a Hamlet; and the round founds nothing then. With Milestone 32 closed a camp is what
	 * it was: an unnamed chief, three or four men, a raid as big as ever, and a guards icon that opens nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"lairSwitches"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lairSwitches")
	public void theSwitchesAndTheExpansionGate(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(Lairs.comer(level, hall, true, RandomSource.create(9L)).equals(Optional.of(Threats.BANDITS)), "bandits don't come to a Town");
			helper.assertTrue(Lairs.comer(level, hall, false, RandomSource.create(9L)).isEmpty(), "bandits come with banditCamps off");
			Threats.DISABLED = Set.of("bandits");
			helper.assertTrue(Lairs.comer(level, hall, true, RandomSource.create(9L)).isEmpty(), "bandits come with their culture switched off");
			for (int i = 0; i < 200; i++) {
				Lairs.round(level, hall, true, RandomSource.create(i));
			}
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a lair of a culture that is switched off");
			Threats.DISABLED = Set.of();
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertTrue(Lairs.comer(level, hall, true, RandomSource.create(9L)).isEmpty(), "bandits come to a Hamlet");

			// Milestone 32 closed: the bandit camp as it was.
			Expansions.openForTests = false;
			try {
				helper.assertFalse(Lairs.live() || DefencePage.open(), "the gate is open");
				at(level, 80, 15000);
				helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
				Lairs.Lair lair = lair(helper, hall);
				Mob chief = (Mob) level.getEntity(lair.captain());
				helper.assertTrue(chief != null && chief.getCustomName().getString().equals("Bandit Chief") && !chief.isCustomNameVisible() && !Lairs.named(lair),
					"the chief with the gate closed: " + (chief == null ? null : chief.getCustomName()));
				int men = men(level, lair).size();
				helper.assertTrue(men == 3 || men == 4, "the band with the gate closed: " + men);
				VillageHallBlockEntity e = (VillageHallBlockEntity) level.getBlockEntity(hall);
				helper.assertTrue(last(e).startsWith("Bandits made camp"), "the chronicle with the gate closed: " + last(e));
				Lairs.setStrength(level, hall, 2);
				VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0);
				helper.assertTrue(raid != null && raid.raiders() == 5, "a raid with the gate closed: " + raid);
				helper.assertTrue(men(level, lair).size() == men && lair(helper, hall).out() == 0, "the band left with the raid");
				helper.assertTrue(last(e).equals("5 bandits raided the village"), "the raid's line with the gate closed: " + last(e));
				ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
				helper.assertTrue(lines(menu.icon(VillageHallScreen.GUARDS)).stream().anyMatch(l -> l.startsWith("Bandits camped")),
					"the guards icon with the gate closed: " + lines(menu.icon(VillageHallScreen.GUARDS)));
				menu.press(VillageHallScreen.GUARDS, player);
				helper.assertTrue(menu.icon(VillageHallScreen.NAME).is(Items.NAME_TAG), "the guards icon opened a page with the gate closed");
			} finally {
				Expansions.openForTests = true;
			}
			helper.succeed();
		});
	}
}
