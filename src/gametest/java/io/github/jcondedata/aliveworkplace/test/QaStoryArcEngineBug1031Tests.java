package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * QA (2026-10-31), a bug in the story arc engine (ROADMAP 31.4): a {@code talk} step counts a right-click on any
 * villager carrying a part of the same name, from any arc in any village, not only the villager its own arc named.
 * Fails until fixed.
 */
public class QaStoryArcEngineBug1031Tests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos OTHER_HALL = new BlockPos(24, 2, 24);
	private static final ResourceLocation TALK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/talk");

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
		});
	}

	private static void hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(abs)).setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
	}

	private static Arcs.Arc inline(String path, String json) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/" + path);
		Arcs.Arc arc = Arcs.read(id, JsonParser.parseString(json).getAsJsonObject());
		Arcs.add(arc);
		return arc;
	}

	private static Arcs.Arc arc(GameTestHelper helper, ResourceLocation id) {
		Arcs.Arc arc = Arcs.get(id).orElse(null);
		helper.assertTrue(arc != null, "the test pack's arc " + id + " didn't load");
		return arc;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
	}

	private static List<Quest> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	/**
	 * {@code talk} counts only for the villager this arc names: another village's villager, cast in a part of the same
	 * name by a different arc, is no stand-in, and right-clicking them leaves this arc's quest open.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaTalkOnlyNamedBug1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaTalkOnlyNamedBug1031")
	public void qaTalkCountsOnlyForTheVillagerThisArcNames(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		hall(helper, OTHER_HALL);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = helper.absolutePos(OTHER_HALL);
		Arcs.Arc watcher = inline("elder_watch", """
			{"name": "The QA Elder Watch", "trigger": {"chance": 0}, "roles": {"elder": {"villager": "any"}},
			 "chapters": [{"name": "Waiting", "needs_flags": ["never_set"]}]}""");
		Villager ilse = helper.spawn(EntityType.VILLAGER, HALL.east(2));
		ilse.setCustomName(Component.literal("Ilse"));
		Villager other = helper.spawn(EntityType.VILLAGER, OTHER_HALL.west(2));
		other.setCustomName(Component.literal("Otto"));
		ServerPlayer player = player(helper, OTHER_HALL.west(3));
		setDay(level, Chronicle.day(level) + 1);
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 50;
		ArcState talk = Arcs.start(level, a, arc(helper, TALK));
		ArcState watch = Arcs.start(level, b, watcher);
		helper.assertTrue(talk != null && watch != null && ilse.getTags().contains(ArcEffects.roleTag("elder"))
			&& other.getTags().contains(ArcEffects.roleTag("elder")), "the elders weren't cast: Ilse " + ilse.getTags() + " Otto " + other.getTags());
		InteractionResult r = ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, other);
		helper.assertTrue(Arcs.running(level, a) == talk && arcQuests(level, a).size() == 1,
			"right-clicking Otto (the elder of another village's other arc) counted as talking to Ilse (" + r + ")");
		helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, ilse) == InteractionResult.SUCCESS && Arcs.running(level, a) == null,
			"Ilse herself didn't count");
		helper.succeed();
	}
}
