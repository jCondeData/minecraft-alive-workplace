package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1123-1309), from the witch coven spec ({@code docs/wiki/covens.md}, 32.11), promises no earlier test held:
 * <ul>
 * <li>Every sentence the spec quotes reads word for word in {@code en_us.json}: the hut's chat line, the nightly hex's
 * line, "Hexed" on the hall's list, "Coven witch", "Coven Mother" with one of twenty names, the chronicle's "... broke
 * the witch coven and lifted its hexes"; in American English.</li>
 * <li>"Never cures it for the Hardy": a Hardy (Gifted) villager stays hexed through the hall's round while the coven
 * stands, though a Hardy villager's ordinary illness passes at once.</li>
 * <li>The hex lifts "once no coven stands by a village within the village's radius plus 16": a hexed villager just
 * inside that reach stays hexed, one just beyond it is cured on the round.</li>
 * <li>"Strength 8 when the hut is raised, 1 more a day, at most 14; at most 6 stand at the hut", and the Coven Mother's
 * name over her head is "Coven Mother" and one of the twenty.</li>
 * <li>"A hexed villager who was out of loaded ground is cured on the hall's next round": a hexed villager saved away
 * while the coven is broken comes back hexed and is cured on the round.</li>
 * <li>The nightly hex comes "at night (13000 to 23000)": not just after 23000, not just before 13000, and just after
 * 13000 it does.</li>
 * <li>"The coven's cauldron is the cauldron of the hut nearest its middle": a stranger mining another cauldron by the
 * hut leaves the coven standing and its hexes on.</li>
 * </ul>
 */
public class QaCovens1123dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");
	private static final ResourceLocation HARDY = AliveWorkplace.id("hardy");
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	/** A village round {@code at} of radius {@code radius}, lairs on; every global put back after, pass or fail. */
	private static BlockPos village(GameTestHelper helper, BlockPos at, int radius) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		Set<String> off = Threats.DISABLED;
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true).forEach(v -> {
				Sickness.recover(level, v);
				Gifted.set(v, null);
			});
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			Lairs.forget(level);
			Covens.forget();
			Covens.Data.get(level).forget(hall);
			Lairs.ENABLED = lairs;
			Threats.DISABLED = off;
			VillageHalls.RADIUS = was;
			new WorkplaceConfig().apply();
		});
		return hall;
	}

	private static Lairs.Lair hut(GameTestHelper helper, BlockPos hall, BlockPos ground) {
		helper.assertTrue(Threats.get(COVEN).isPresent(), "setup: expected witch_coven.json loaded, found none");
		Lairs.Lair lair = Lairs.found(helper.getLevel(), hall, helper.absolutePos(ground), COVEN);
		helper.assertTrue(lair != null && lair.culture().equals(COVEN), "setup: expected a coven hut, got " + lair);
		return lair;
	}

	private static Villager still(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setInvulnerable(true);
		return v;
	}

	private static String text(GameTestHelper helper, String key, Object... args) {
		Language lang = Language.getInstance();
		helper.assertTrue(lang.has(key), "no text for " + key);
		return String.format(lang.getOrDefault(key), args);
	}

	private static void same(GameTestHelper helper, String key, String got, String want) {
		helper.assertTrue(got.equals(want), key + ": expected \"" + want + "\", got \"" + got + "\"");
	}

	/** The spec's quoted sentences, word for word, and the twenty names; no British spelling in any coven line. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaCovenWords1123d"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaCovenWords1123d")
	public void theCovensWordsReadAsTheSpecQuotesThem(GameTestHelper helper) {
		same(helper, "message.aliveworkplace.coven.hex", text(helper, "message.aliveworkplace.coven.hex", "Fenwick", 2),
			"A swirl of witch-light over Fenwick: the coven has hexed 2 of its villagers. No nurse's remedy lifts a hex; only breaking the coven does.");
		String camp = text(helper, "message.aliveworkplace.coven.camp_named", "Fenwick", "64 blocks north", "Coven Mother Morwen Hollowmere");
		String quoted = "Witch-light glimmers in the swamp beyond Fenwick: Coven Mother Morwen Hollowmere has raised her coven's hut 64 blocks north";
		helper.assertTrue(camp.startsWith(quoted), "message.aliveworkplace.coven.camp_named: expected to start \"" + quoted + "\", got \"" + camp + "\"");
		String hexed = text(helper, "screen.aliveworkplace.hall.hexed");
		helper.assertTrue(hexed.startsWith("Hexed"), "screen.aliveworkplace.hall.hexed: the hall's list should say \"Hexed\", got \"" + hexed + "\"");
		same(helper, "entity.aliveworkplace.coven_raider", text(helper, "entity.aliveworkplace.coven_raider"), "Coven witch");
		same(helper, "threat.aliveworkplace.witch_coven.title", text(helper, "threat.aliveworkplace.witch_coven.title"), "Coven Mother");
		same(helper, "threat.aliveworkplace.witch_coven.names.1", text(helper, "threat.aliveworkplace.witch_coven.names.1"), "Morwen Hollowmere");
		same(helper, "threat.aliveworkplace.witch_coven.names.2", text(helper, "threat.aliveworkplace.witch_coven.names.2"), "Agatha Blackthorn");
		for (int i = 1; i <= 20; i++) {
			helper.assertTrue(!text(helper, "threat.aliveworkplace.witch_coven.names." + i).isBlank(), "name " + i + " is blank");
		}
		helper.assertFalse(Language.getInstance().has("threat.aliveworkplace.witch_coven.names.21"), "the spec names one of twenty, found a 21st");
		String by = text(helper, "chronicle.aliveworkplace.coven.broken_by", "Alex");
		same(helper, "chronicle.aliveworkplace.coven.broken_by", by, "Alex broke the witch coven and lifted its hexes");
		Language lang = Language.getInstance();
		List<String> keys = new ArrayList<>(List.of("message.aliveworkplace.coven.camp", "message.aliveworkplace.coven.camp_named",
			"message.aliveworkplace.coven.broken", "message.aliveworkplace.coven.broken_named", "message.aliveworkplace.coven.raid",
			"message.aliveworkplace.coven.raid.one", "message.aliveworkplace.coven.raid_named", "message.aliveworkplace.coven.raid_named.one",
			"message.aliveworkplace.coven.hex", "chronicle.aliveworkplace.coven.camp", "chronicle.aliveworkplace.coven.camp_named",
			"chronicle.aliveworkplace.coven.broken", "chronicle.aliveworkplace.coven.broken_named", "chronicle.aliveworkplace.coven.broken_by",
			"chronicle.aliveworkplace.coven.broken_by_named", "chronicle.aliveworkplace.coven.raid", "chronicle.aliveworkplace.coven.hex",
			"threat.aliveworkplace.witch_coven.name", "threat.aliveworkplace.witch_coven.lair"));
		for (String key : keys) {
			helper.assertTrue(lang.has(key), "no text for " + key);
			String s = lang.getOrDefault(key).toLowerCase(java.util.Locale.ROOT);
			for (String british : List.of("colour", "defence", "armour", "grey", "cancelled", "jeweller", "neighbour", "favour", "honour")) {
				helper.assertFalse(s.contains(british), key + " isn't American English (\"" + british + "\"): " + lang.getOrDefault(key));
			}
		}
		helper.succeed();
	}

	/**
	 * A Hardy villager (whose ordinary illness passes on the round) is hexed: the hall's rounds, long past an illness's
	 * time, leave them hexed while the coven stands. The coven broken, they are cured.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCovenHardy1123d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCovenHardy1123d")
	public void aHardyVillagerStaysHexedWhileTheCovenStands(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL, 20);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = hut(helper, hall, new BlockPos(15, 1, 18));
			Villager hardy = still(helper, new BlockPos(4, 2, 3));
			Gifted.set(hardy, HARDY);
			helper.assertTrue(Gifted.noIllness(hardy), "setup: expected the Hardy gift to keep illness off, it doesn't");
			// Control: an ordinary illness forced on a Hardy villager passes on the round.
			ModAttachments.ILL_SINCE.set(hardy, level.getGameTime());
			ModAttachments.ILL_KIND.remove(hardy);
			Sickness.round(level, hardy, 40);
			helper.assertFalse(Sickness.isIll(hardy), "setup: expected a Hardy villager's ordinary illness to pass on the round");
			Sickness.hex(level, hardy);
			helper.assertTrue(Sickness.isHexed(hardy), "a Hardy villager can't be hexed");
			ModAttachments.ILL_SINCE.set(hardy, level.getGameTime() - Sickness.RECOVERY * 3);
			for (int i = 0; i < 5; i++) {
				Sickness.round(level, hardy, 40);
			}
			helper.assertTrue(Sickness.isHexed(hardy), "the hex lifted from a Hardy villager while the coven stands");
			Entity mother = level.getEntity(lair.captain());
			helper.assertTrue(mother instanceof Witch, "setup: expected the Coven Mother at her hut, found " + mother);
			mother.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "setup: expected her death to break the coven");
			helper.assertFalse(Sickness.isIll(hardy), "the coven broken, the Hardy villager is still hexed");
			helper.succeed();
		});
	}

	/**
	 * A village of radius 6 (its hex reaches 22 blocks from the hall): one hexed villager about 20 blocks from the hall and
	 * one about 24. On the hall's round the near one stays hexed and the far one is cured.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCovenReach1123d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCovenReach1123d")
	public void aHexHoldsWithinTheVillagesRadiusPlusSixteenAndLiftsBeyond(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL, 6);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			hut(helper, hall, new BlockPos(15, 1, 18));
			Villager near = still(helper, new BlockPos(21, 2, 3));
			Villager far = still(helper, new BlockPos(25, 2, 3));
			double reach = VillageHalls.RADIUS + 16.0;
			double dNear = Math.hypot(hall.getX() + 0.5 - near.getX(), hall.getZ() + 0.5 - near.getZ());
			double dFar = Math.hypot(hall.getX() + 0.5 - far.getX(), hall.getZ() + 0.5 - far.getZ());
			helper.assertTrue(dNear < reach - 1 && dFar > reach + 1, "setup: expected " + dNear + " inside and " + dFar + " beyond " + reach);
			Sickness.hex(level, near);
			Sickness.hex(level, far);
			helper.assertTrue(Sickness.isHexed(near) && Sickness.isHexed(far), "setup: expected both hexed");
			Sickness.round(level, near, 40);
			Sickness.round(level, far, 40);
			helper.assertTrue(Sickness.isHexed(near), "a villager " + (int) dNear + " blocks from the hall (within radius + 16 = " + (int) reach + ") was cured while the coven stands");
			helper.assertFalse(Sickness.isIll(far), "a villager " + (int) dFar + " blocks from the hall (beyond radius + 16 = " + (int) reach + ") stayed hexed");
			helper.succeed();
		});
	}

	/**
	 * The hut raised: strength 8, at most 6 of the band at the hut, the Coven Mother named "Coven Mother" and one of the
	 * twenty. Dawns: 8 grows one a day (three days: 11), 13 grows to 14, 14 stays 14 however many days pass.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCovenStrength1123d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCovenStrength1123d")
	public void aCovenHutStartsAtEightGrowsOneADayToFourteenAndKeepsSixAtHome(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL, 20);
		ServerLevel level = helper.getLevel();
		WorkplaceConfig.parse("{\"threatLevel\": 2}").apply();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = hut(helper, hall, new BlockPos(15, 1, 18));
			helper.assertTrue(lair.strength() == 8, "the hut raised at strength " + lair.strength() + ", not 8");
			Entity captain = level.getEntity(lair.captain());
			helper.assertTrue(captain instanceof Witch && captain.getCustomName() != null, "the Coven Mother has no name: " + captain);
			String name = captain.getCustomName().getString();
			Language lang = Language.getInstance();
			boolean known = false;
			for (int i = 1; i <= 20; i++) {
				known |= name.equals("Coven Mother " + lang.getOrDefault("threat.aliveworkplace.witch_coven.names." + i));
			}
			helper.assertTrue(known, "the Coven Mother's name isn't \"Coven Mother\" and one of the twenty: \"" + name + "\"");
			long home = level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.isAlive() && m.getTags().contains(Lairs.TAG) && !m.getTags().contains(Lairs.CAPTAIN_TAG)).size();
			helper.assertTrue(home >= 1 && home <= 6, "expected 1 to 6 of the band standing at the hut, found " + home);
			long today = Chronicle.day(level);
			int[][] cases = {{8, 3, 11}, {13, 1, 14}, {13, 5, 14}, {14, 4, 14}};
			for (int[] c : cases) {
				Lairs.putForTest(level, new Lairs.Lair(lair.pos(), hall, lair.captain(), today - c[1], COVEN, c[0], today - c[1], 0, 0, lair.name()));
				Lairs.dawn(level, hall, today);
				int grown = Lairs.near(level, hall).map(Lairs.Lair::strength).orElse(-1);
				helper.assertTrue(grown == c[2], "a coven at " + c[0] + " after " + c[1] + " dawns: " + grown + ", expected " + c[2]);
			}
			helper.succeed();
		});
	}

	/**
	 * A hexed villager is away (saved, out of the world) when the Coven Mother dies; back in the world they are still
	 * hexed, and the hall's next round cures them.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCovenAway1123d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCovenAway1123d")
	public void aHexedVillagerAwayWhenTheCovenBrokeIsCuredOnTheNextRound(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL, 20);
		ServerLevel level = helper.getLevel();
		CompoundTag[] saved = new CompoundTag[1];
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = hut(helper, hall, new BlockPos(15, 1, 18));
			Villager away = still(helper, new BlockPos(4, 2, 3));
			Sickness.hex(level, away);
			helper.assertTrue(Sickness.isHexed(away), "setup: expected the villager hexed");
			saved[0] = away.saveWithoutId(new CompoundTag());
			away.discard();
			Entity mother = level.getEntity(lair.captain());
			helper.assertTrue(mother instanceof Witch, "setup: expected the Coven Mother at her hut, found " + mother);
			mother.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "setup: expected her death to break the coven");
		});
		helper.runAfterDelay(6, () -> {
			Villager back = EntityType.VILLAGER.create(level);
			back.load(saved[0]);
			back.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 3.5)));
			helper.assertTrue(level.addFreshEntity(back), "setup: expected the villager back in the world");
			helper.assertTrue(Sickness.isHexed(back), "setup: expected the villager away when the coven broke to come back hexed");
			Sickness.round(level, back, 40);
			helper.assertFalse(Sickness.isIll(back), "a villager away when the coven broke is still hexed after the hall's round");
			helper.succeed();
		});
	}

	/**
	 * Six grown villagers by a coven hut. Just after 23000 (dawn) no one is hexed; just before 13000 no one; just after
	 * 13000 one to three are.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaCovenNightEdges1123d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCovenNightEdges1123d")
	public void theNightlyHexComesOnlyBetweenThirteenAndTwentyThreeThousand(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL, 20);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		long base = time / 24000 * 24000;
		List<Villager> people = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			hut(helper, hall, new BlockPos(15, 1, 20));
			for (int i = 0; i < 6; i++) {
				people.add(still(helper, new BlockPos(2 + 2 * i, 2, 3)));
			}
			Covens.Data.get(level).forget(hall);
			level.setDayTime(base + 23005);
		});
		helper.runAfterDelay(92, () -> {
			long hexed = people.stream().filter(Sickness::isHexed).count();
			helper.assertTrue(hexed == 0 && Covens.Data.get(level).lastHexed(hall) == -1,
				"hexed " + hexed + " after 23000 (dawn), time " + level.getDayTime() % 24000);
			level.setDayTime(base + 12900);
		});
		helper.runAfterDelay(142, () -> {
			long hexed = people.stream().filter(Sickness::isHexed).count();
			helper.assertTrue(hexed == 0 && Covens.Data.get(level).lastHexed(hall) == -1,
				"hexed " + hexed + " before 13000 (dusk), time " + level.getDayTime() % 24000);
			level.setDayTime(base + 13005);
		});
		helper.runAfterDelay(232, () -> {
			long hexed = people.stream().filter(Sickness::isHexed).count();
			helper.assertTrue(hexed >= 1 && hexed <= 3, "expected one to three hexed just after 13000, got " + hexed);
			helper.succeed();
		});
	}

	/**
	 * A stranger (a survival player) puts a cauldron of their own by the coven's, farther from the hut's middle, and mines
	 * it: the coven stands, its hex stays, and two checks later it still stands (her cauldron is still there).
	 */
	//$ gametest_ticks_batch AREA '120' '"qaCovenOtherCauldron1123d"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaCovenOtherCauldron1123d")
	public void aStrangerMiningAnotherCauldronByTheHutLeavesTheCovenStanding(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL, 20);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Villager[] hexed = new Villager[1];
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = hut(helper, hall, new BlockPos(15, 1, 18));
			hexed[0] = still(helper, new BlockPos(4, 2, 3));
			Sickness.hex(level, hexed[0]);
			BlockPos hers = Covens.cauldron(level, lair);
			helper.assertTrue(hers != null, "setup: expected her cauldron at the hut");
			BlockPos mid = lair.pos();
			BlockPos other = null;
			for (int r = 2; r <= 4 && other == null; r++) {
				for (int[] d : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
					BlockPos p = hers.offset(d[0], 0, d[1]);
					if (level.getBlockState(p).isAir() && p.distSqr(mid) > hers.distSqr(mid) + 2) {
						other = p;
						break;
					}
				}
			}
			helper.assertTrue(other != null, "setup: expected an open spot by her cauldron, farther from the middle");
			level.setBlockAndUpdate(other, Blocks.CAULDRON.defaultBlockState());
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(other.getX() + 0.5, other.getY() + 1, other.getZ() + 0.5);
			helper.assertTrue(player.gameMode.destroyBlock(other), "setup: the player couldn't mine their cauldron");
			helper.assertFalse(level.getBlockState(other).getBlock() instanceof AbstractCauldronBlock, "setup: their cauldron still stands");
			helper.assertTrue(level.getBlockState(hers).getBlock() instanceof AbstractCauldronBlock, "setup: her cauldron went too");
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "mining another cauldron " + Math.sqrt(other.distSqr(hers)) + " blocks from hers broke the coven");
			helper.assertTrue(Sickness.isHexed(hexed[0]), "mining another cauldron lifted the hex");
		});
		helper.runAfterDelay(90, () -> {
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the coven broke within two checks of another cauldron being mined");
			helper.assertTrue(Sickness.isHexed(hexed[0]), "the hex lifted within two checks of another cauldron being mined");
			helper.succeed();
		});
	}
}
