package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;

/**
 * QA bug tests (2026-11-04, round 2) for ROADMAP 31.22: what the player reads when they talk to the villager who asks
 * for a reform.
 */
public class QaReformStories1104cBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final BlockPos ASKER = new BlockPos(8, 2, 6);

	/**
	 * Talking to the asker shows their line once, in one pair of quotes: "Toolsmith: "We hammer ... gladly."" is
	 * expected as Toolsmith: "We hammer from dawn to dark ... gladly." (message.aliveworkplace.arc.says is %s: "%s", so
	 * the says line itself must not carry quotes too). Checks all eight stories' lines through the Shift Bell's ask and
	 * the format for the rest.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1104cBugSays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1104cBugSays")
	public void qaTheAskersLineHasOnePairOfQuotes(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
			ServerPlayer player = client.player();
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			long before = level.getDayTime();
			Leftovers.after(helper, () -> level.setDayTime(before));
			long day = Chronicle.day(level) + 1;
			level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("long_shifts").toString(), day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			client.seen().clear();
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			String who = smith.getDisplayName().getString();
			String said = client.seen().stream().map(Component::getString).filter(s -> s.startsWith(who + ":")).findFirst().orElse("");
			String expected = who + ": \"We hammer from dawn to dark and nobody knows when the day ends. Give us a bell to ring the work in and out, and we'll keep the long shifts gladly.\"";
			helper.assertTrue(said.equals(expected), "the Toolsmith said '" + said + "', expected '" + expected + "'");
			Map<String, String> en = enUs();
			List<String> doubled = new ArrayList<>();
			for (String arc : List.of("shift_bell", "common_granary", "midwives", "watchful_gate", "festival_fund", "fair_ledger", "lamplighters", "militia_drill")) {
				String line = Component.translatable("message.aliveworkplace.arc.says", "Ada", Component.translatable("arc.aliveworkplace." + arc + ".says")).getString();
				if (line.contains("\"\"")) {
					doubled.add(arc + ": " + line);
				}
			}
			helper.assertTrue(en.containsKey("arc.aliveworkplace.shift_bell.says") && doubled.isEmpty(), "doubled quotes: " + doubled);
			helper.succeed();
		});
	}

	/** The mod's en_us.json as read from the jar. */
	private static Map<String, String> enUs() {
		try (var in = QaReformStories1104cBugTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, String> out = new java.util.LinkedHashMap<>();
			json.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
			return out;
		} catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
