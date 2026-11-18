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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;

/**
 * QA (qa-lane-c-1031-1553): a failing test for a real problem left by B711's fix. Fails until fixed.
 */
public class QaVillage1031VerifyBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos OTHER_HALL = new BlockPos(24, 2, 24);

	/**
	 * B711 (31.4) for a spawned foe: the same arc runs in two villages, each spawning its own boar to hunt
	 * ({@code kill role:boar}). A player near village B only: B's boar comes, A's waits for a player to come near.
	 * Killing B's boar must finish B's hunt and leave A's open; A's quest is for A's own boar.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaVerify1031ArcFoe"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaVerify1031ArcFoe")
	public void killingAnotherVillagesFoeOfTheSameArcDoesntCountBeforeOursComes(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		int near = Arcs.NEAR;
		VillageHalls.RADIUS = 8;
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 50;
		Arcs.NEAR = 8;
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = helper.absolutePos(OTHER_HALL);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.NEAR = near;
			Arcs.stopAll(level, a);
			Arcs.stopAll(level, b);
		});
		for (BlockPos at : List.of(HALL, OTHER_HALL)) {
			helper.setBlock(at, ModBlocks.VILLAGE_HALL);
			BlockPos abs = helper.absolutePos(at);
			((VillageHallBlockEntity) level.getBlockEntity(abs)).setLastQuestDay(Long.MAX_VALUE / 2);
			Arcs.stopAll(level, abs);
		}
		Arcs.Arc hunt = Arcs.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_verify/boar_hunt"), JsonParser.parseString("""
			{"name": "The Boar Hunt", "trigger": {"chance": 0},
			 "chapters": [{"name": "Hunted",
			   "on_start": [{"type": "spawn", "entity": "minecraft:pig", "key": "boar", "name": {"text": "The Boar"}, "offset": [2, 2], "restrict": 3}],
			   "quests": [{"objectives": [{"type": "kill", "entity": "role:boar", "count": 1}]}]}]}""").getAsJsonObject());
		Arcs.add(hunt);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() - 1.5);
		level.setDayTime(Chronicle.day(level) * VillageNeeds.DAY + 1000);
		ArcState inA = Arcs.start(level, a, hunt);
		ArcState inB = Arcs.start(level, b, hunt);
		helper.assertTrue(inA != null && inB != null, "the hunt didn't start in both villages");
		ArcEffects.check(level);
		List<Pig> boars = level.getEntitiesOfClass(Pig.class, helper.getBounds().inflate(32), p -> p.getTags().contains(ArcEffects.roleTag("boar")));
		String mobs = inA.mobs.stream().map(m -> "A " + m.key + " " + m.spot + " " + m.uuid).toList() + " "
			+ inB.mobs.stream().map(m -> "B " + m.key + " " + m.spot + " " + m.uuid + " near " + (m.spot != null && ArcEffects.near(level, m.spot))
			+ " ticking " + (m.spot != null && level.isPositionEntityTicking(m.spot))).toList();
		helper.assertTrue(boars.size() == 1 && inA.mobs.get(0).uuid == null && boars.get(0).getUUID().equals(inB.mobs.get(0).uuid),
			"setup: expected only village B's boar to come (the player is near B only), found " + boars.size() + ": " + mobs);
		Stories.onKill(level, boars.get(0), player);
		boars.get(0).discard();
		List<Quest> openA = Stories.open(level, a).stream().filter(q -> q.arc != null).toList();
		helper.assertTrue(Arcs.running(level, b) == null, "killing village B's boar didn't finish B's own hunt");
		helper.assertTrue(Arcs.running(level, a) == inA && openA.size() == 1,
			"killing village B's boar also finished village A's hunt, whose own boar hadn't even come yet");
		helper.succeed();
	}
}
