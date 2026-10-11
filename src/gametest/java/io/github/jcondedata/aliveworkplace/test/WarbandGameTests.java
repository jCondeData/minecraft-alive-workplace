package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Pillager warbands (ROADMAP 32.7): the warband's war camp by a Town in the plains, with its named Warlord and his band;
 * which comes, the warband or the bandits, by rank and land; a raid from the camp with its ravager rams (one at a Town,
 * two and an evoker at a City); at a City the Warlord at its head, his death there breaking the camp, and his way home
 * at dawn; the config switch and the expansion gate; a save and load mid-raid.
 */
public class WarbandGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** The ground the camp's middle goes on: the war camp (23 x 19) fits the huge area beside the hall. */
	private static final BlockPos CAMP = new BlockPos(16, 1, 16);
	private static final long DAY = 24000;
	private static final ResourceLocation WARBAND = AliveWorkplace.id("pillager_warband");

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A hall of {@code rank} in {@code biome} with a village radius of 16 and four villagers; what the test changes goes when it ends. */
	private static BlockPos village(GameTestHelper helper, VillageRanks.Rank rank, String biome) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		boolean open = Expansions.openForTests;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true; // (off in GameTests, as the config leaves it: here the other cultures' lairs come by chance)
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
		helper.assertTrue(level.getBiome(hall).is(ResourceLocation.parse(biome)), "the hall isn't in " + biome);
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	/** Sets the hall's rank (till its next round counts it again: so in the test's own tick). */
	private static void rank(ServerLevel level, BlockPos hall, VillageRanks.Rank rank) {
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(rank);
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	private static Lairs.Lair lair(GameTestHelper helper, BlockPos hall) {
		Optional<Lairs.Lair> lair = Lairs.near(helper.getLevel(), hall);
		helper.assertTrue(lair.isPresent(), "no lair by the village");
		return lair.get();
	}

	/** The band standing at the lair, its captain apart. */
	private static List<Mob> men(ServerLevel level, Lairs.Lair lair) {
		List<Mob> men = new ArrayList<>(Lairs.band(level, lair));
		men.removeIf(m -> m.getUUID().equals(lair.captain()));
		return men;
	}

	private static long count(List<Mob> mobs, EntityType<?> type) {
		return mobs.stream().filter(m -> m.getType() == type).count();
	}

	/** How often, in 400 rolls, the warband is the one that makes camp. */
	private static int warbands(ServerLevel level, BlockPos hall, boolean bandits) {
		int n = 0;
		for (int i = 0; i < 400; i++) {
			if (Lairs.comer(level, hall, bandits, RandomSource.create(1000L + i)).equals(Optional.of(WARBAND))) {
				n++;
			}
		}
		return n;
	}

	private static Culture warband(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(WARBAND);
		helper.assertTrue(culture.isPresent(), "pillager_warband.json isn't loaded");
		return culture.get();
	}

	/**
	 * A Town hall in the plains: from Town a warband is three times as likely to make camp as bandits; it makes its war
	 * camp with a named Warlord (the ominous banner on his head, iron, 40 more health), a band of 10 and the war camp's
	 * loot. A raid from it brings one ravager ram besides the band, and leaves the Warlord at home; a save and load mid-raid
	 * keeps who is out; at dawn the band's survivors rejoin and the ravager doesn't count among them.
	 */
	//$ gametest_ticks_batch AREA '100' '"warbandTown"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "warbandTown")
	public void aTownInThePlainsGetsAWarCamp(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.TOWN, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			Culture culture = warband(helper);
			helper.assertTrue(culture.tactics().equals(List.of("ram_gates", "ladders")) && Sieges.lays(culture), "its tactics: " + culture.tactics());
			helper.assertTrue(culture.lair().get().strength() == 10 && culture.lair().get().growth() == 2 && culture.lair().get().max() == 18,
				"its strength: " + culture.lair());
			// Who makes camp by a Town in the plains: the warband three times in four.
			int warbands = warbands(level, hall, true);
			helper.assertTrue(warbands > 260 && warbands < 340, "warbands in 400 camps by a Town: " + warbands);
			helper.assertTrue(warbands(level, hall, false) == 400, "with banditCamps off only the warband comes");

			at(level, 50, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null && lair.culture().equals(WARBAND) && lair.strength() == 10 && lair.home() == 10, "the war camp: " + lair);
			Mob warlord = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(warlord != null && warlord.getType() == EntityType.VINDICATOR && warlord.getTags().contains(Lairs.CAPTAIN_TAG), "the Warlord: " + warlord);
			String name = warlord.getCustomName().getString();
			helper.assertTrue(name.startsWith("Warlord ") && name.length() > "Warlord ".length() + 3 && warlord.isCustomNameVisible() && Lairs.named(lair),
				"the Warlord's name: " + name);
			helper.assertTrue(warlord.getItemBySlot(EquipmentSlot.HEAD).is(Items.WHITE_BANNER) && warlord.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
				&& warlord.getMaxHealth() >= 64, "the Warlord's banner, iron and health: " + warlord.getItemBySlot(EquipmentSlot.HEAD) + " " + warlord.getMaxHealth());
			List<Mob> men = men(level, lair);
			helper.assertTrue(men.size() == 10 && count(men, EntityType.PILLAGER) + count(men, EntityType.VINDICATOR) == 10 && count(men, EntityType.PILLAGER) > 0
				&& count(men, EntityType.VINDICATOR) > 0, "his band: " + men);
			// The war camp stands, with the war camp's loot in its chest.
			boolean loot = false;
			for (BlockPos p : BlockPos.betweenClosed(lair.pos().offset(-13, -2, -13), lair.pos().offset(13, 6, 13))) {
				if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null
					&& chest.getLootTable().location().equals(AliveWorkplace.id("chests/war_camp"))) {
					loot = true;
				}
			}
			helper.assertTrue(loot, "no chest of the war camp's loot");

			// A raid from the camp: the band, and one ravager ram at a Town; the Warlord stays at home.
			at(level, 50, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, culture, RandomSource.create(5L));
			helper.assertTrue(raid != null, "no raid");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(count(raiders, EntityType.RAVAGER) == 1 && count(raiders, EntityType.EVOKER) == 0 && raiders.size() == 11,
				"the raiders at a Town: " + raiders);
			Mob ravager = raiders.stream().filter(m -> m.getType() == EntityType.RAVAGER).findFirst().orElseThrow();
			helper.assertTrue(Threats.role(ravager) == Culture.Role.RAM && ravager.getTags().contains(Threats.EXTRA_TAG), "the ravager isn't a ram: " + ravager.getTags());
			helper.assertTrue(raiders.stream().noneMatch(m -> m.getUUID().equals(lair.captain())) && warlord.isAlive() && !Lairs.leading(warlord),
				"the Warlord left his camp at a Town");
			Lairs.Lair out = lair(helper, hall);
			helper.assertTrue(out.out() == 10 && out.strength() == 10, "the ravager was taken from the band: " + out);
			// Saved and loaded mid-raid.
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			helper.assertTrue(lair(helper, hall).equals(out), "after a save and load: " + lair(helper, hall));
			// Two of the band fall; at dawn the eight alive rejoin, the ravager is gone and isn't one of them.
			List<Mob> band = raiders.stream().filter(m -> m.getType() != EntityType.RAVAGER).toList();
			band.get(0).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			band.get(1).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			ThreatData.get(level).begin(new ThreatData.Under(hall, WARBAND, 11, level.getGameTime() - 2000));
			at(level, 50, 22500);
			VillageRaids.tick(level, hall, 52, 0, 50, d -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty() && !ravager.isAlive(), "the raid didn't end at dawn");
			Lairs.Lair home = lair(helper, hall);
			helper.assertTrue(home.strength() == 8 && home.out() == 0 && home.lost() == 2, "after the raid: " + home);
			helper.succeed();
		});
	}

	/**
	 * At a City the raid brings two ravagers and an evoker, and the Warlord leads it himself: he is among the raiders, and
	 * his lair's round doesn't take his absence for his death. Brought down at the walls, his war camp breaks: the band at
	 * the camp scatters, the raiders already at the village fight on.
	 */
	//$ gametest_ticks_batch AREA '100' '"warbandCity"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "warbandCity")
	public void atACityTheWarlordLeadsAndFalls(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.CITY, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.CITY);
			Culture culture = warband(helper);
			at(level, 60, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null && Lairs.leads(level, lair), "the Warlord doesn't lead at a City: " + lair);
			Mob warlord = (Mob) level.getEntity(lair.captain());
			String name = warlord.getCustomName().getString();
			at(level, 60, 15000);
			Lairs.setStrength(level, hall, 6);
			Lairs.round(level, hall, true);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, culture, RandomSource.create(6L));
			helper.assertTrue(raid != null, "no raid");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(count(raiders, EntityType.RAVAGER) == 2 && count(raiders, EntityType.EVOKER) == 1, "the extras at a City: " + raiders);
			helper.assertTrue(raiders.stream().anyMatch(m -> m.getUUID().equals(lair.captain())) && Lairs.leading(warlord) && raiders.size() == 6 + 3 + 1,
				"the Warlord isn't among the raiders: " + raiders);
			helper.assertTrue(warlord.distanceToSqr(lair.pos().getCenter()) > 20 * 20 && warlord.distanceToSqr(hall.getCenter()) < 40 * 40,
				"the Warlord didn't come to the village: " + warlord.blockPosition());
			Lairs.Lair out = lair(helper, hall);
			helper.assertTrue(out.out() == 6 && men(level, out).isEmpty(), "the band out: " + out);
			// The camp's round while he's away: it stands.
			Lairs.round(level, hall, true);
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp broke while its Warlord led the raid");
			// Brought down at the walls: the camp breaks, the raid goes on.
			warlord.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) == Lairs.REST_DAYS, "his death didn't break the war camp");
			helper.assertTrue(VillageRaids.raiders(level, hall).size() == 9 && VillageRaids.active(hall).isPresent(), "the raid ended with the Warlord");
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			String last = entity.chronicle().get(entity.chronicle().size() - 1).text().getString();
			helper.assertTrue(last.equals(name + " fell and the war camp was broken"), "the chronicle: " + last);
			helper.succeed();
		});
	}

	/**
	 * A City raid that the Warlord lives through: at dawn he goes home to his camp (not gone with the rest), and the camp
	 * stands. The lines a player reads. The switches: the config's {@code pillager_warband} off, a Village (bandits only),
	 * a desert Town (bandits only), and Milestone 32 closed (no warband).
	 */
	//$ gametest_ticks_batch AREA '100' '"warbandHome"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "warbandHome")
	public void theWarlordGoesHomeAndTheSwitches(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.CITY, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.CITY);
			Culture culture = warband(helper);
			at(level, 70, 1000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), WARBAND);
			helper.assertTrue(lair != null, "no war camp");
			Mob warlord = (Mob) level.getEntity(lair.captain());
			at(level, 70, 15000);
			helper.assertTrue(VillageRaids.start(level, hall, 52, 0, Double.NaN, culture, RandomSource.create(7L)) != null && Lairs.leading(warlord), "no raid led");
			ThreatData.get(level).begin(new ThreatData.Under(hall, WARBAND, 14, level.getGameTime() - 2000));
			at(level, 70, 22500);
			VillageRaids.tick(level, hall, 52, 0, 70, d -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid didn't end at dawn");
			helper.assertTrue(warlord.isAlive() && !warlord.isRemoved() && !Lairs.leading(warlord) && warlord.distanceToSqr(lair.pos().getCenter()) < 8 * 8,
				"the Warlord didn't go home: " + warlord.blockPosition() + ", the camp at " + lair.pos());
			Lairs.round(level, hall, true);
			Lairs.Lair home = lair(helper, hall);
			helper.assertTrue(home.strength() == 10 && home.out() == 0, "the camp after the raid: " + home);

			// The lines a player reads.
			for (String key : List.of("message.aliveworkplace.warband.camp_named", "message.aliveworkplace.warband.raid_led", "message.aliveworkplace.warband.broken_named",
				"chronicle.aliveworkplace.warband.raid_led", "chronicle.aliveworkplace.warband.broken_by_named", "threat.aliveworkplace.pillager_warband.name",
				"threat.aliveworkplace.pillager_warband.lair", "threat.aliveworkplace.pillager_warband.names.20", "message.aliveworkplace.lair.raid_led")) {
				helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
			}
			helper.assertTrue(Lairs.lairName(WARBAND).getString().equals("War camp") && Lairs.cultureName(WARBAND).getString().equals("Pillager warband"),
				"the names: " + Lairs.lairName(WARBAND).getString());

			// The switches.
			Lairs.forget(level);
			Threats.DISABLED = Set.of("pillager_warband");
			helper.assertTrue(warbands(level, hall, true) == 0, "a warband switched off in the config makes camp");
			Threats.DISABLED = Set.of();
			Expansions.openForTests = false;
			helper.assertTrue(warbands(level, hall, true) == 0, "a warband makes camp with Milestone 32 closed");
			Expansions.openForTests = true;
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(warbands(level, hall, true) == 0 && Lairs.comer(level, hall, true, RandomSource.create(3L)).equals(Optional.of(Threats.BANDITS)),
				"a Village gets a warband, or no bandits");
			entity.setRank(VillageRanks.Rank.TOWN);
			fill(level, hall, "minecraft:desert");
			helper.assertTrue(warbands(level, hall, true) == 0, "a warband makes camp in the desert");
			helper.succeed();
		});
	}
}
