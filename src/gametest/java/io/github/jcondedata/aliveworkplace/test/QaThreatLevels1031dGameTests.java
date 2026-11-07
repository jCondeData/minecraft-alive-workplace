package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.AtPeace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * QA (2026-10-31, lane d) of ROADMAP 32.21, "Peaceful settings and the threat level", written from the spec: the "next
 * dawn" a camp leaves at, from both sides of midnight and across a save; At peace lifted before dawn and set again; who
 * may set it (an unowned hall becomes the clicker's, an operator may lift it, a stranger is refused in words); the
 * gentle boundary from above (levels 2 and 3 keep the plague under way and the coven's hex; only 1 drops them); the
 * multipliers at every level edge; and the guards icon and Defence page saying why nothing comes at level 0.
 */
public class QaThreatLevels1031dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = 24000;
	private static final ResourceLocation PLAGUE = AliveWorkplace.id("plague");
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");

	static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A hall with a village radius of 16 and four villagers; the config, the clock and the threats go back when it ends. */
	static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		Expansions.openForTests = true;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		BanditCamps.forget(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setDisaster(null, -1, -1, -100);
				e.setAtPeace(false, -1);
			}
			VillageRaids.forget();
			BanditCamps.forget(level);
			Disasters.forget();
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	static VillageHallBlockEntity entity(GameTestHelper helper, BlockPos hall) {
		VillageHallBlockEntity entity = helper.getLevel().getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		helper.assertTrue(entity != null, "no hall");
		return entity;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
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

	private static String last(VillageHallBlockEntity entity) {
		List<Chronicle.Entry> chronicle = entity.chronicle();
		return chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
	}

	/** A random whose every float is 0: any chance above 0 comes. */
	static RandomSource always() {
		return new LegacyRandomSource(7L) {
			@Override
			public float nextFloat() {
				return 0f;
			}

			@Override
			public double nextDouble() {
				return 0d;
			}
		};
	}

	static Lairs.Lair camp(GameTestHelper helper, BlockPos hall) {
		helper.assertTrue(BanditCamps.found(helper.getLevel(), hall, helper.absolutePos(CAMP)) != null, "no camp");
		return Lairs.near(helper.getLevel(), hall).orElseThrow();
	}

	/**
	 * "On the Defence page the hall's owner can set the village At peace ... a lair already standing leaves at the next
	 * dawn": set at the very end of day 70 (tick 23990), the camp stands through day 70 and is gone at day 71's first
	 * round. Lifted again before that dawn, the camp stays at dawn; set At peace again on day 71, it stands all day 71
	 * and leaves at day 72's dawn, not at once (the day it was set again counts, not the first one). Every chat line
	 * the owner reads says so.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1031dPeaceToggle"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1031dPeaceToggle")
	public void peaceLiftedBeforeDawnKeepsTheCampAndSetAgainSendsItTheDawnAfter(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		ServerPlayer owner = player(helper);
		helper.runAfterDelay(2, () -> {
			at(level, 70, 23990);
			camp(helper, hall);
			String village = VillageHalls.name(level, hall).getString();
			String on = AtPeace.toggle(level, hall, owner).getString();
			helper.assertTrue(on.equals(village + " is at peace: no raids, raiders' camps or disasters will come to it, and the Bandit camp leaves at the next dawn"),
				"what the owner read setting it at peace: " + on);
			helper.assertTrue(entity.atPeace() && entity.peaceSince() == 70, "not at peace since day 70: " + entity.peaceSince());
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left on day 70, the day peace was set");

			// Lifted before dawn: the camp stays at dawn.
			at(level, 70, 23995);
			String off = AtPeace.toggle(level, hall, owner).getString();
			helper.assertTrue(off.equals(village + " is no longer at peace: raiders and disasters may come again"), "what the owner read lifting it: " + off);
			helper.assertTrue(last(entity).endsWith("let threats come to the village again"), "the chronicle: " + last(entity));
			at(level, 71, 100);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left at dawn though peace was lifted before it");

			// Set again on day 71: it stands that day, and goes at the dawn after.
			AtPeace.toggle(level, hall, owner);
			helper.assertTrue(entity.atPeace() && entity.peaceSince() == 71, "set again, peace since " + entity.peaceSince());
			at(level, 71, 23999);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left on day 71, the day peace was set again (peace counted from day 70?)");
			at(level, 72, 0);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands at the dawn after peace was set again");
			helper.succeed();
		});
	}

	/**
	 * At peace is on the hall, so a restart doesn't move the dawn: set on day 70 before a save, the hall loaded back
	 * from its saved data still sends the camp away at day 71's dawn, and the Defence page still says since day 70.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1031dPeaceSaved"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1031dPeaceSaved")
	public void peaceSetBeforeASaveStillSendsTheCampAwayAtTheFirstDawnAfter(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		ServerPlayer owner = player(helper);
		helper.runAfterDelay(2, () -> {
			at(level, 70, 18000);
			camp(helper, hall);
			AtPeace.toggle(level, hall, owner);
			CompoundTag saved = entity.saveWithoutMetadata(level.registryAccess());
			entity.setAtPeace(false, -1);
			entity.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue(entity.atPeace() && entity.peaceSince() == 70 && owner.getUUID().equals(entity.owner()),
				"after the load: at peace " + entity.atPeace() + " since " + entity.peaceSince() + ", owner " + entity.owner());
			ChoiceMenu menu = VillageHallScreen.forTest(owner, hall);
			menu.press(VillageHallScreen.GUARDS, owner);
			helper.assertTrue(lines(menu.icon(DefencePage.AT_PEACE)).contains("At peace since day 70"), "the page after the load: " + lines(menu.icon(DefencePage.AT_PEACE)));
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left the day peace was set");
			at(level, 71, 50);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands at the first dawn after a load");
			helper.succeed();
		});
	}

	/**
	 * "Like protection": a hall nobody owns becomes the clicker's; a stranger is refused in words naming the owner; an
	 * operator who doesn't own it may lift it, the chronicle names who did, and the hall stays its owner's.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1031dPeaceWho"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031dPeaceWho")
	public void anUnownedHallBecomesTheClickersAndAnOperatorMayLiftIt(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		ServerPlayer first = player(helper);
		ServerPlayer stranger = player(helper);
		ServerPlayer op = player(helper);
		helper.runAfterDelay(2, () -> {
			at(level, 40, 6000);
			helper.assertTrue(entity.owner() == null, "the hall already has an owner");
			AtPeace.toggle(level, hall, first);
			helper.assertTrue(entity.atPeace() && first.getUUID().equals(entity.owner()), "the first clicker didn't set it at peace and own it");

			String refused = AtPeace.toggle(level, hall, stranger).getString();
			helper.assertTrue(entity.atPeace() && refused.equals("Only " + first.getGameProfile().getName() + ", who owns this hall, can set the village at peace"),
				"a stranger: at peace " + entity.atPeace() + ", told '" + refused + "'");

			var ops = level.getServer().getPlayerList().getOps();
			ops.add(new net.minecraft.server.players.ServerOpListEntry(op.getGameProfile(), 4, false));
			try {
				helper.assertTrue(op.hasPermissions(2), "the operator isn't one");
				String lifted = AtPeace.toggle(level, hall, op).getString();
				helper.assertTrue(!entity.atPeace() && entity.peaceSince() == -1 && lifted.contains("is no longer at peace"),
					"the operator didn't lift it: " + lifted);
				helper.assertTrue(first.getUUID().equals(entity.owner()), "the operator took the hall: " + entity.ownerName());
				helper.assertTrue(last(entity).equals(op.getDisplayName().getString() + " let threats come to the village again"), "the chronicle: " + last(entity));
			} finally {
				ops.remove(op.getGameProfile());
			}
			helper.succeed();
		});
	}

	/**
	 * The gentle boundary from above: only threat level 1 drops the plague and the hex. At 2 and at 3 a plague under way
	 * goes on and a coven standing by the village hexes it; at 1 the plague under way ends.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1031dGentleEdge"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031dGentleEdge")
	public void onlyLevelOneDropsThePlagueAndTheHex(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		helper.runAfterDelay(2, () -> {
			at(level, 61, 15000);
			helper.assertTrue(Disasters.get(PLAGUE).isPresent(), "the plague isn't loaded: " + Disasters.all());
			Villager villager = level.getEntitiesOfClass(Villager.class, helper.getBounds(), v -> true).stream().findFirst().orElse(null);
			helper.assertTrue(villager != null, "no villager");
			Lairs.putForTest(level, new Lairs.Lair(helper.absolutePos(CAMP), hall, new java.util.UUID(1, 1), Chronicle.day(level), COVEN, 6, Chronicle.day(level), 0, 0, 0));
			for (int threat : new int[] {2, 3}) {
				WorkplaceConfig.parse("{\"threatLevel\": " + threat + "}").apply();
				helper.assertTrue(Threats.LEVEL == threat && !Threats.gentle(), "level " + threat + " is gentle");
				helper.assertTrue(Covens.cursed(level, villager), "the coven doesn't hex at level " + threat);
				long day = Chronicle.day(level);
				entity.setDisaster(PLAGUE, day, day + 1, -100);
				Disasters.round(level, hall, entity, VillageHalls.census(level, hall));
				helper.assertTrue(PLAGUE.equals(entity.disaster()), "the plague under way ended at level " + threat + ": " + entity.disaster());
			}
			WorkplaceConfig.parse("{\"threatLevel\": 1}").apply();
			helper.assertTrue(Threats.gentle() && !Covens.cursed(level, villager), "the coven still hexes at level 1");
			Disasters.round(level, hall, entity, VillageHalls.census(level, hall));
			helper.assertTrue(entity.disaster() == null, "the plague under way went on at level 1");
			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			helper.assertFalse(Threats.gentle(), "level 0 counts as gentle (it should be quieter still, by quiet)");
			helper.succeed();
		});
	}

	/**
	 * "1, gentle: half as often ... 3, hard: half again as often, a quarter more raiders": the multipliers at every
	 * level and past both ends, and a quarter more (rounded up) only at 3, never at 1 or 2: gentle is less often, not
	 * fewer raiders.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1031dLevelEdges"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031dLevelEdges")
	public void theMultipliersAtEveryLevelEdge(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			float[] want = {0f, 0f, 0.5f, 1f, 1.5f, 1.5f};
			for (int threat = -1; threat <= 4; threat++) {
				helper.assertTrue(Threats.often(threat) == want[threat + 1], "often(" + threat + ") = " + Threats.often(threat));
			}
			int[] sizes = {0, 1, 3, 4, 8, 12};
			for (int threat = 1; threat <= 3; threat++) {
				WorkplaceConfig.parse("{\"threatLevel\": " + threat + "}").apply();
				for (int n : sizes) {
					int more = Threats.more(n);
					int expected = threat == 3 ? (int) Math.ceil(n * 1.25) : n;
					helper.assertTrue(more == expected, "at level " + threat + " a band of " + n + " is " + more + ", not " + expected);
				}
				int raiders = VillageRaids.expected(level, hall, 20, 0, Threats.MONSTERS);
				helper.assertTrue(raiders == (threat == 3 ? 10 : 8), "a monsters' raid on 20 villagers at level " + threat + ": " + raiders);
			}
			WorkplaceConfig.parse("{\"threatLevel\": 3}").apply();
			helper.assertTrue(Threats.more(1) == 2 && Threats.more(3) == 4 && Threats.more(4) == 5, "a quarter more isn't rounded up at level 3");
			helper.succeed();
		});
	}

	/**
	 * At threat level 0 the guards icon says why nothing comes ("Threats are off on this server") and that the camp
	 * leaves at the next dawn, and the Defence page's At peace slot says the same beside its own setting; once the level
	 * is back at 2 with the village At peace, the reason is At peace.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1031dLevelZeroLines"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031dLevelZeroLines")
	public void theHallSaysWhyNothingComesAtLevelZero(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = player(helper);
		helper.runAfterDelay(2, () -> {
			at(level, 50, 13000);
			camp(helper, hall);
			List<String> before = lines(VillageHallScreen.forTest(owner, hall).icon(VillageHallScreen.GUARDS));
			helper.assertTrue(before.stream().noneMatch(l -> l.contains("Threats are off") || l.contains("leaves at the next dawn")), "the guards icon at level 2: " + before);
			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			List<String> guards = lines(VillageHallScreen.forTest(owner, hall).icon(VillageHallScreen.GUARDS));
			helper.assertTrue(guards.contains("Threats are off on this server: nothing comes") && guards.contains("The Bandit camp leaves at the next dawn"),
				"the guards icon at level 0: " + guards);
			ChoiceMenu menu = VillageHallScreen.forTest(owner, hall);
			menu.press(VillageHallScreen.GUARDS, owner);
			List<String> slot = lines(menu.icon(DefencePage.AT_PEACE));
			helper.assertTrue(slot.get(0).equals("At peace: off") && slot.contains("Threats are off on this server: nothing comes"), "the At peace slot at level 0: " + slot);
			AtPeace.toggle(level, hall, owner);
			WorkplaceConfig.parse("{\"threatLevel\": 2}").apply();
			List<String> peace = lines(VillageHallScreen.forTest(owner, hall).icon(VillageHallScreen.GUARDS));
			helper.assertTrue(peace.contains("At peace: no raiders, camps or disasters come") && peace.stream().noneMatch(l -> l.contains("Threats are off")),
				"the guards icon At peace at level 2: " + peace);
			helper.succeed();
		});
	}
}
