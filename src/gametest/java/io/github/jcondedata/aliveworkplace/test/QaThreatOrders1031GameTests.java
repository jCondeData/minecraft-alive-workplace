package io.github.jcondedata.aliveworkplace.test;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-10-31), from the player guide's {@code /workplace threat} pages (command-threat.md,
 * command-threat-disaster.md) and the wiki's peace.md, not from the code: "It's an order, so a raid starts whatever the
 * Threat Level, the village's At peace or the difficulty say"; the wiki: "it ignores the switches"; "The command
 * suggests the names as you type". Each claim through the operator's own entry point (the command dispatcher), and the
 * order is still under way after the hall's next raid round (an order isn't undone a round later).
 */
public class QaThreatOrders1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final long DAY = 24000;

	static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A hall with a village radius of 16 and four villagers; the config, the clock and the threats go back after. */
	static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
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
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	static CommandSourceStack op(ServerLevel level, BlockPos at, List<String> said) {
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
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, 2, "test", Component.literal("test"), level.getServer(), null);
	}

	static int run(ServerLevel level, CommandSourceStack source, String command) {
		try {
			return level.getServer().getCommands().getDispatcher().execute(command, source);
		} catch (CommandSyntaxException e) {
			throw new IllegalStateException(command + ": " + e.getMessage());
		}
	}

	/**
	 * Runs {@code command}, then the hall's raid round twice: the raid it ordered is on, with raiders in the world, and
	 * still on after the rounds (it's night, so nothing sends them home).
	 */
	private static void ordered(GameTestHelper helper, BlockPos hall, CommandSourceStack op, List<String> said, String command, ResourceLocation culture, String when) {
		ServerLevel level = helper.getLevel();
		said.clear();
		int sent = run(level, op, command);
		helper.assertTrue(sent > 0 && ThreatData.get(level).raid(hall).map(r -> r.culture().equals(culture)).orElse(false),
			when + ": '/" + command + "' started no raid by " + culture + " (returned " + sent + "): " + said);
		helper.assertTrue(!VillageRaids.raiders(level, hall).isEmpty(), when + ": the ordered raid has no raiders in the world: " + said);
		helper.assertTrue(said.stream().anyMatch(s -> s.contains(" are raiding ") && s.endsWith(sent + " of them.")), when + ": what the command said: " + said);
		for (int round = 0; round < 2; round++) {
			VillageRaids.tick(level, hall, 4, 0, -100, d -> { });
		}
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent() && !VillageRaids.raiders(level, hall).isEmpty(),
			when + ": the ordered raid was over after the hall's next round");
		said.clear();
		run(level, op, "workplace threat end");
		helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty(), when + ": end left the raid: " + said);
	}

	/**
	 * The wiki: "it ignores the switches". With {@code villageRaids} off the monsters still come on an order; with
	 * {@code banditCamps} off the bandits do; with the warband switched off in {@code raiderCultures}, the warband does.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaThreatOrderSwitches"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaThreatOrderSwitches")
	public void anOrderedRaidComesWithItsSwitchOff(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			at(level, 90, 15000);
			CommandSourceStack op = op(level, hall, said);
			WorkplaceConfig.parse("{\"villageRaids\": false, \"banditCamps\": false, \"raiderCultures\": {\"pillager_warband\": false}}").apply();
			helper.assertTrue(!VillageRaids.ENABLED && !BanditCamps.ENABLED && !Threats.enabled(AliveWorkplace.id("pillager_warband")),
				"setup: the switches didn't go off");
			ordered(helper, hall, op, said, "workplace threat start monsters", Threats.MONSTERS, "villageRaids off");
			ordered(helper, hall, op, said, "workplace threat start bandits", Threats.BANDITS, "banditCamps off");
			ordered(helper, hall, op, said, "workplace threat start pillager_warband", AliveWorkplace.id("pillager_warband"), "the warband off");
			helper.succeed();
		});
	}

	/**
	 * The guide: "a raid starts whatever the Threat Level, the village's At peace ... say", and the hall's next round
	 * doesn't send it home; "a disaster ends almost at once" in such a village: it starts, and the next round ends it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaThreatOrderQuiet"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaThreatOrderQuiet")
	public void anOrderedRaidComesAtThreatLevelZeroAndAtPeace(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(entity != null, "no hall");
			at(level, 91, 15000);
			CommandSourceStack op = op(level, hall, said);
			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			helper.assertTrue(Threats.quiet(level, hall), "setup: not quiet at threat level 0");
			ordered(helper, hall, op, said, "workplace threat start bandits", Threats.BANDITS, "threat level 0");
			ordered(helper, hall, op, said, "workplace threat start monsters", Threats.MONSTERS, "threat level 0");

			new WorkplaceConfig().apply();
			entity.setAtPeace(true, Chronicle.day(level));
			helper.assertTrue(Threats.quiet(level, hall), "setup: not quiet At peace");
			ordered(helper, hall, op, said, "workplace threat start bandits", Threats.BANDITS, "At peace");

			// "a disaster ends almost at once" in a quiet village: it starts (and says so), and the next round ends it
			said.clear();
			int sent = run(level, op, "workplace threat disaster drought");
			helper.assertTrue(sent == 1 && AliveWorkplace.id("drought").equals(entity.disaster()) && said.stream().anyMatch(s -> s.endsWith("has come to "
				+ VillageHalls.name(level, hall).getString() + ".")), "At peace: the ordered drought didn't start: " + said);
			Disasters.round(level, hall, entity, VillageHalls.census(level, hall));
			helper.assertTrue(entity.disaster() == null, "At peace: the ordered drought went on past the next round (the guide: it ends almost at once)");
			helper.succeed();
		});
	}

	/**
	 * An order that can't come is answered in words, never in silence: on Peaceful (where hostile mobs can't exist) the
	 * bandits' raid doesn't start, the command returns 0, and the operator is told so (QaThreatOrdersBugTests asks that
	 * those words name Peaceful).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaThreatOrderRefused"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaThreatOrderRefused")
	public void anOrderThatCantComeIsAnsweredInWords(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		net.minecraft.world.Difficulty before = level.getDifficulty();
		Leftovers.after(helper, () -> level.getServer().setDifficulty(before, true));
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			at(level, 93, 15000);
			CommandSourceStack op = op(level, hall, said);
			level.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
			int sent = run(level, op, "workplace threat start bandits");
			boolean raid = ThreatData.get(level).raid(hall).isPresent();
			level.getServer().setDifficulty(before, true);
			helper.assertTrue(sent == 0 && !raid, "on Peaceful the bandits' raid started: returned " + sent + ", " + said);
			helper.assertTrue(!said.isEmpty() && said.stream().noneMatch(s -> s.contains(" are raiding ")), "the refused order was answered with " + said);
			helper.succeed();
		});
	}

	/**
	 * The guide: "The command suggests the names as you type": {@code start} every culture (the monsters too), {@code lair}
	 * only those that keep one (never the monsters), {@code disaster} our three; a whole id typed in part narrows them.
	 */
	//$ gametest_batch EMPTY_STRUCTURE '"qaThreatOrderSuggest"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaThreatOrderSuggest")
	public void theCommandSuggestsTheNames(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CommandSourceStack op = op(level, helper.absolutePos(BlockPos.ZERO), new ArrayList<>());
		Set<String> start = suggestions(helper, op, "workplace threat start ");
		for (String path : List.of("monsters", "bandits", "pillager_warband", "desert_raiders", "drowned_pirates", "piglin_incursion", "witch_coven")) {
			helper.assertTrue(start.contains("aliveworkplace:" + path), "'start ' doesn't suggest " + path + ": " + start);
		}
		Set<String> lair = suggestions(helper, op, "workplace threat lair ");
		helper.assertTrue(lair.contains("aliveworkplace:bandits") && lair.contains("aliveworkplace:witch_coven") && !lair.contains("aliveworkplace:monsters"),
			"'lair ' suggestions (the bandits and the coven, never the monsters): " + lair);
		Set<String> disaster = suggestions(helper, op, "workplace threat disaster ");
		for (String path : List.of("village_fire", "drought", "plague")) {
			helper.assertTrue(disaster.contains("aliveworkplace:" + path), "'disaster ' doesn't suggest " + path + ": " + disaster);
		}
		Set<String> ours = suggestions(helper, op, "workplace threat start aliveworkplace:band");
		helper.assertTrue(ours.contains("aliveworkplace:bandits") && !ours.contains("aliveworkplace:monsters"), "'start aliveworkplace:band' suggestions: " + ours);
		helper.succeed();
	}

	static Set<String> suggestions(GameTestHelper helper, CommandSourceStack source, String typed) {
		var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
		ParseResults<CommandSourceStack> parse = dispatcher.parse(typed, source);
		Set<String> out = new TreeSet<>();
		dispatcher.getCompletionSuggestions(parse).join().getList().stream().map(Suggestion::getText).forEach(out::add);
		return out;
	}
}
