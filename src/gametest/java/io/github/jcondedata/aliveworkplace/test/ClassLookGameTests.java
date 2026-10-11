package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.ClassLook;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Dressed by class, in game (ROADMAP 34.18): a player's client is told a villager's class and stage of life when it
 * starts tracking them, and again when either changes (a new class, becoming an elder); with {@code classOutfits} off
 * nothing is sent. A mock player has no client, so the test listens where the packets go out.
 */
public class ClassLookGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** Tracking, a change of class, becoming an elder, and the switch off. */
	//$ gametest_ticks_batch AREA '200' '"classLook"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "classLook")
	public void theLookGoesOutOnTrackingAndOnAChange(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<ClassLook.Look> sent = new ArrayList<>();
		boolean enabled = ClassLook.ENABLED;
		ClassLook.ENABLED = true;
		Villager ada = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ada.setNoAi(true);
		SocialClasses.seed(ada, cls("burgher"));
		long today = Chronicle.day(level);
		LifeStages.setAdultSince(ada, today - 10);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ClassLook.listener = (to, look) -> {
			if (to == player && look.entityId() == ada.getId()) {
				sent.add(look);
			}
		};
		Leftovers.after(helper, () -> {
			ClassLook.listener = null;
			ClassLook.ENABLED = enabled;
			level.getServer().getPlayerList().remove(player);
		});
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 2)));
		player.teleportTo(level, at.x, at.y, at.z, 0, 0);

		helper.startSequence().thenWaitUntil(() -> {
			// A mock player has no client and no connection ticking it: say for it that the chunks it was sent arrived,
			// and move it in the chunk map as its own tick would, so it starts tracking what is near (the real event).
			player.connection.chunkSender.onChunkBatchReceivedByClient(64f);
			level.getChunkSource().move(player);
			helper.assertTrue(!sent.isEmpty(), "no look sent when the player started tracking Ada");
			ClassLook.Look first = sent.get(0);
			helper.assertTrue(first.cls().equals(Optional.of(AliveWorkplace.id("burgher")))
				&& first.outfit().equals(Optional.of(AliveWorkplace.id("textures/entity/villager/class/burgher.png")))
				&& first.lifeStage() == LifeStages.Stage.GROWN, "the look on tracking: " + first);
			helper.assertTrue(Platform.get().tracking(ada).contains(player), "the player doesn't track Ada");
		}).thenExecute(() -> {

			// A change of class goes out to whoever sees her.
			int before = sent.size();
			SocialClasses.seed(ada, cls("noble"));
			helper.assertTrue(sent.size() == before + 1 && sent.get(before).cls().equals(Optional.of(AliveWorkplace.id("noble")))
				&& sent.get(before).outfit().equals(Optional.of(AliveWorkplace.id("textures/entity/villager/class/noble.png"))),
				"the look after Ada became a Noble: " + sent.subList(before, sent.size()));

			// So does becoming an elder (the hall's round notes it).
			before = sent.size();
			LifeStages.setAdultSince(ada, today - LifeStages.ELDER_DAYS - 1);
			LifeStages.round(level, helper.absolutePos(new BlockPos(0, 2, 0)), List.of(ada));
			helper.assertTrue(sent.size() == before + 1 && sent.get(before).lifeStage() == LifeStages.Stage.ELDER,
				"the look after Ada became an elder: " + sent.subList(before, sent.size()));

			// A Peasant wears no class layer: the packet says so.
			before = sent.size();
			SocialClasses.seed(ada, cls("peasant"));
			helper.assertTrue(sent.size() == before + 1 && sent.get(before).outfit().isEmpty(), "a Peasant's look: " + sent.subList(before, sent.size()));

			// classOutfits off: nothing goes out.
			before = sent.size();
			ClassLook.ENABLED = false;
			try {
				SocialClasses.seed(ada, cls("artisan"));
			} finally {
				ClassLook.ENABLED = true;
			}
			helper.assertTrue(sent.size() == before, "a look sent with classOutfits off: " + sent.subList(before, sent.size()));
		}).thenSucceed();
	}

	/** The config's {@code classOutfits} switch reaches the look, and is on by default. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSwitchTurnsTheLookOff(GameTestHelper helper) {
		boolean enabled = ClassLook.ENABLED;
		try {
			helper.assertTrue(new io.github.jcondedata.aliveworkplace.WorkplaceConfig().classOutfits, "classOutfits is off by default");
			io.github.jcondedata.aliveworkplace.WorkplaceConfig.parse("{\"classOutfits\": false}").apply();
			helper.assertTrue(!ClassLook.ENABLED, "classOutfits off in the file, the look still on");
		} finally {
			new io.github.jcondedata.aliveworkplace.WorkplaceConfig().apply();
			ClassLook.ENABLED = enabled;
		}
		helper.succeed();
	}

	private static SocialClasses.SocialClass cls(String name) {
		SocialClasses.SocialClass c = SocialClasses.get(AliveWorkplace.id(name));
		if (c == null) {
			throw new IllegalStateException("no class " + name + " loaded: " + SocialClasses.ladder());
		}
		return c;
	}
}
