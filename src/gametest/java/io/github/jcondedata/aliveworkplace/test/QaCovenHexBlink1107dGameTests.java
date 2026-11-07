package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Culture;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1107-0108), from the witch coven spec (32.11, {@link Covens}), two lines a mutation run on Covens left
 * unchecked:
 * <ul>
 * <li>The nightly hex is told to the players by the village ("A swirl of witch-light over ...: the coven has hexed N of
 * its villagers"): to one near it, with the number hexed, never to one far off, and not at all on a night it hexes no
 * one (:206).</li>
 * <li>The Coven Mother blinks "to somewhere 8 to 12 blocks off": every blink, not only on average (:314).</li>
 * </ul>
 */
public class QaCovenHexBlink1107dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	/** A village round {@code at} of radius 20, lairs on; everything put back after (as QaCovens1102dGameTests). */
	private static BlockPos village(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		Set<String> off = Threats.DISABLED;
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4), v -> true).forEach(v -> Sickness.recover(level, v));
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			Lairs.forget(level);
			Covens.forget();
			Covens.Data.get(level).forget(hall);
			Lairs.ENABLED = lairs;
			Threats.DISABLED = off;
			VillageHalls.RADIUS = radius;
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

	/** The coven's lines among what {@code client} has been shown. */
	private static List<String> hexLines(QaStageFiveGameTests.Client client) {
		List<String> out = new ArrayList<>();
		for (Component c : client.seen()) {
			String s = c.getString();
			if (s.contains("witch-light")) {
				out.add(s);
			}
		}
		return out;
	}

	/**
	 * Four grown villagers; a player at the hall and one 60 blocks off (past the 52 a village's news reaches). The night's
	 * hex is told to the near player (one line), with the number it hexed and the village's name; the far player hears nothing.
	 * Then with every grown villager hexed the next night hexes no one, and nobody is told anything.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCovenHexTold1107d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCovenHexTold1107d")
	public void theNightsHexIsToldToThePlayerByTheVillageOnly(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = hut(helper, hall, new BlockPos(15, 1, 18));
			List<Villager> grown = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + 2 * i, 2, 3));
				v.setNoAi(true);
				grown.add(v);
			}
			QaStageFiveGameTests.Client near = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("QaNear"));
			near.player().moveTo(Vec3.atCenterOf(hall.offset(2, 0, 2)));
			QaStageFiveGameTests.Client far = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("QaFar"));
			far.player().moveTo(Vec3.atCenterOf(hall.offset(60, 0, 0)));
			near.seen().clear();
			far.seen().clear();
			List<Villager> hexed = Covens.hexNight(level, lair, RandomSource.create(3L));
			helper.assertTrue(!hexed.isEmpty(), "(setup) the night hexed no one");
			List<String> told = hexLines(near);
			String village = VillageHalls.name(level, hall).getString();
			// (the capture sees a chat line twice: as shown and as sent)
			helper.assertTrue(!told.isEmpty() && told.stream().distinct().count() == 1, "the player at the hall wasn't told of the hex once: " + told);
			helper.assertTrue(told.get(0).contains("the coven has hexed " + hexed.size() + " of its villagers") && told.get(0).contains(village),
				"the hex line doesn't say the " + hexed.size() + " hexed in " + village + ": " + told.get(0));
			helper.assertTrue(hexLines(far).isEmpty(), "the player 60 blocks off was told of the hex: " + hexLines(far));
			grown.forEach(v -> Sickness.hex(level, v));
			near.seen().clear();
			List<Villager> none = Covens.hexNight(level, lair, RandomSource.create(4L));
			helper.assertTrue(none.isEmpty(), "(setup) with every grown villager hexed the night still hexed " + none.size());
			helper.assertTrue(hexLines(near).isEmpty(), "a night that hexed no one was told to the player: " + hexLines(near));
			helper.succeed();
		});
	}

	/**
	 * A witch with the Coven Mother's blink (8 to 12 blocks) blinks thirty times from the middle of open ground: every
	 * time she lands 8 to 12 blocks (across the ground) from where she stood.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCovenBlinkReach1107d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCovenBlinkReach1107d")
	public void everyBlinkLandsEightToTwelveBlocksOff(GameTestHelper helper) {
		village(helper, HALL);
		ServerLevel level = helper.getLevel();
		Witch witch = helper.spawn(EntityType.WITCH, new BlockPos(15, 2, 15));
		witch.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			Culture.Blink blink = new Culture.Blink(3, 8, 12);
			Vec3 start = witch.position();
			int landed = 0;
			for (long seed = 0; seed < 30; seed++) {
				witch.teleportTo(start.x, start.y, start.z);
				if (!Covens.blink(level, witch, blink, RandomSource.create(seed))) {
					continue;
				}
				landed++;
				double moved = Math.hypot(witch.getX() - start.x, witch.getZ() - start.z);
				helper.assertTrue(moved >= 7.99 && moved <= 12.01, "blink " + seed + " took her " + String.format("%.2f", moved) + " blocks off, not 8 to 12");
			}
			helper.assertTrue(landed >= 25, "(setup) only " + landed + " of 30 blinks found somewhere to land");
			helper.succeed();
		});
	}
}
