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
import net.minecraft.nbt.CompoundTag;
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
 * B711 (ROADMAP 31.4): an arc's {@code talk} and {@code kill role:} steps count only for the villager or mob that arc
 * cast in that village, never for someone another arc (or the same arc in another village) cast in a part of the same
 * name, and still after a save and reload.
 */
public class StoryArcCastGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos OTHER_HALL = new BlockPos(24, 2, 24);
	private static final ResourceLocation TALK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/talk");

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		VillageHalls.RADIUS = 8;
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 50;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
		});
	}

	private static BlockPos hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(abs)).setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return abs;
	}

	private static Arcs.Arc inline(String path, String json) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "cast/" + path);
		Arcs.Arc arc = Arcs.read(id, JsonParser.parseString(json).getAsJsonObject());
		Arcs.add(arc);
		return arc;
	}

	private static Arcs.Arc talkArc(GameTestHelper helper) {
		Arcs.Arc arc = Arcs.get(TALK).orElse(null);
		helper.assertTrue(arc != null, "the test pack's arc " + TALK + " didn't load");
		return arc;
	}

	private static Arcs.Arc watcher() {
		return inline("elder_watch", """
			{"name": "The Elder Watch", "trigger": {"chance": 0}, "roles": {"elder": {"villager": "any"}},
			 "chapters": [{"name": "Waiting", "needs_flags": ["never_set"]}]}""");
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static void tomorrow(ServerLevel level) {
		level.setDayTime(Chronicle.day(level) * VillageNeeds.DAY + 1000);
	}

	private static List<Quest> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static void elders(GameTestHelper helper, ArcState a, ArcState b, Villager... cast) {
		boolean ok = a != null && b != null;
		for (Villager v : cast) {
			ok &= v.getTags().contains(ArcEffects.roleTag("elder"));
		}
		helper.assertTrue(ok, "the elders weren't cast");
	}

	/** The same arc in two villages: each village's talk step counts only its own elder. */
	//$ gametest_ticks_batch AREA '40' '"arcCastSameArcTwice"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "arcCastSameArcTwice")
	public void talkInTheSameArcInTwoVillagesCountsOnlyThatVillagesElder(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = hall(helper, HALL);
		BlockPos b = hall(helper, OTHER_HALL);
		Villager ilse = villager(helper, HALL.east(2), "Ilse");
		Villager otto = villager(helper, OTHER_HALL.west(2), "Otto");
		ServerPlayer player = player(helper, OTHER_HALL.west(3));
		tomorrow(level);
		ArcState inA = Arcs.start(level, a, talkArc(helper));
		ArcState inB = Arcs.start(level, b, talkArc(helper));
		elders(helper, inA, inB, ilse, otto);
		helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, otto) == InteractionResult.SUCCESS,
			"Otto didn't count for his own village");
		helper.assertTrue(Arcs.running(level, b) == null, "village B's talk didn't finish its arc");
		helper.assertTrue(Arcs.running(level, a) == inA && arcQuests(level, a).size() == 1,
			"talking to Otto also counted for Ilse's village");
		helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, ilse) == InteractionResult.SUCCESS
			&& Arcs.running(level, a) == null, "Ilse didn't count for her own village");
		helper.succeed();
	}

	/** A {@code kill role:} step: another village's arc's villager of the same part isn't this arc's foe. */
	//$ gametest_ticks_batch AREA '40' '"arcCastKillRole"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "arcCastKillRole")
	public void killRoleCountsOnlyTheOneThisArcCast(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = hall(helper, HALL);
		BlockPos b = hall(helper, OTHER_HALL);
		Arcs.Arc hunt = inline("elder_hunt", """
			{"name": "The Elder Hunt", "trigger": {"chance": 0}, "roles": {"elder": {"villager": "any"}},
			 "chapters": [{"name": "Hunted", "quests": [{"objectives": [{"type": "kill", "entity": "role:elder", "count": 1}]}]}]}""");
		Villager ilse = villager(helper, HALL.east(2), "Ilse");
		Villager otto = villager(helper, OTHER_HALL.west(2), "Otto");
		ServerPlayer player = player(helper, OTHER_HALL.west(3));
		tomorrow(level);
		ArcState inA = Arcs.start(level, a, hunt);
		ArcState inB = Arcs.start(level, b, watcher());
		elders(helper, inA, inB, ilse, otto);
		helper.assertTrue(arcQuests(level, a).size() == 1, "the hunt posted no quest");
		Stories.onKill(level, otto, player);
		helper.assertTrue(Arcs.running(level, a) == inA && arcQuests(level, a).size() == 1,
			"defeating Otto (another arc's elder) counted for village A's hunt");
		Stories.onKill(level, ilse, player);
		helper.assertTrue(Arcs.running(level, a) == null, "defeating Ilse (the hunt's own elder) didn't count");
		helper.succeed();
	}

	/** Saved and loaded mid-arc, the talk step still knows which villager its arc cast. */
	//$ gametest_ticks_batch AREA '40' '"arcCastReload"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "arcCastReload")
	public void talkStillCountsOnlyTheCastVillagerAfterAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = hall(helper, HALL);
		BlockPos b = hall(helper, OTHER_HALL);
		Villager ilse = villager(helper, HALL.east(2), "Ilse");
		Villager otto = villager(helper, OTHER_HALL.west(2), "Otto");
		ServerPlayer player = player(helper, OTHER_HALL.west(3));
		tomorrow(level);
		ArcState inA = Arcs.start(level, a, talkArc(helper));
		ArcState inB = Arcs.start(level, b, watcher());
		elders(helper, inA, inB, ilse, otto);
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set("aliveworkplace_stories", Stories.Data.load(saved, level.registryAccess()));
		helper.assertTrue(Arcs.running(level, a) != null && Arcs.running(level, a) != inA, "the arc didn't reload");
		ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, otto);
		helper.assertTrue(Arcs.running(level, a) != null && arcQuests(level, a).size() == 1,
			"after a reload, talking to Otto counted for Ilse's arc");
		helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, ilse) == InteractionResult.SUCCESS
			&& Arcs.running(level, a) == null, "after a reload, Ilse didn't count");
		helper.succeed();
	}
}
