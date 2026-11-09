package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-03) for B851 (README "Nurses": once a minute per player): the nurse's wait line, read by a real
 * logged-in player who right-clicks her through the server's packet handler, at each edge of the minute. Right after a
 * treatment it reads 60 seconds (never 61); with 1199 ticks left still 60; with 1180 left 59; with 20 or 1 tick left
 * "1 second" in the singular (the lang's .one form); on the tick the minute ends she treats again. No refused click
 * heals, cures or restarts the minute.
 */
public class QaNurseWait1103dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	record Visitor(ServerPlayer player, List<String> seen) {
	}

	/** A logged-in survival player whose chat and action-bar lines land in {@code seen}; logged out when the test ends. */
	static Visitor visitor(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		UUID uuid = UUID.randomUUID();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(uuid, name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message.getString());
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> {
			if (level.getServer().getPlayerList().getPlayer(uuid) == player) {
				level.getServer().getPlayerList().remove(player);
			}
		});
		return new Visitor(player, seen);
	}

	/** A client's right-click with an empty hand on {@code villager}; returns the lines it got back. */
	static List<String> rightClick(Visitor v, Villager villager) {
		ServerPlayer player = v.player();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		int before = v.seen().size();
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (v.seen().size() == before && player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND));
		}
		// 43.3: an empty-hand click opens the villager's card; its first button does what the click did, pressed as the client sends it
		if (player.containerMenu instanceof io.github.jcondedata.aliveworkplace.card.StatusCardMenu card) {
			player.connection.handleContainerButtonClick(new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(
				card.containerId, card.card().buttons().get(0).id()));
		}
		return List.copyOf(v.seen().subList(before, v.seen().size()));
	}

	/** Hurt and poisoned, the player clicks; she refuses with exactly {@code expected} and treats nothing. */
	private static void refused(GameTestHelper helper, Visitor v, Villager nurse, long start, long elapsed, String expected) {
		helper.assertValueEqual(helper.getLevel().getGameTime() - start, elapsed, "setup: ticks since the treatment");
		v.player().setHealth(5f);
		v.player().addEffect(new MobEffectInstance(MobEffects.POISON, 1200));
		List<String> heard = rightClick(v, nurse);
		helper.assertTrue(heard.contains(expected), elapsed + " ticks after a treatment she should say \"" + expected + "\"; she said " + heard);
		helper.assertTrue(v.player().getHealth() == 5f, elapsed + " ticks after a treatment she healed again: " + v.player().getHealth());
		helper.assertTrue(v.player().hasEffect(MobEffects.POISON), elapsed + " ticks after a treatment she cured poison again");
	}

	//$ gametest_ticks_batch AREA '1700' '"qaNurseWaitEdges1103d"'
	@GameTest(template = AREA, timeoutTicks = 1700, batch = "qaNurseWaitEdges1103d")
	public void qaTheWaitLineAtEachEdgeOfTheMinute(GameTestHelper helper) {
		Leftovers.clear(helper);
		Nurses.resetCooldowns();
		// a villager's traits come from her random UUID and change her pace: a diligent nurse's minute is 55 seconds
		boolean traits = io.github.jcondedata.aliveworkplace.people.Traits.ENABLED;
		io.github.jcondedata.aliveworkplace.people.Traits.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.people.Traits.ENABLED = traits);
		Villager nurse = NurseGameTests.nurse(helper);
		nurse.setNoAi(true);
		// her "minute" is Nurses.COOLDOWN scaled by her pace (moods and other pace sources still apply in a full run),
		// so the edges are taken from her own minute; it is still roughly a minute
		int minute = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse);
		helper.assertTrue(minute >= 900 && minute <= 1500, "setup: her minute is not roughly a minute: " + minute + " ticks");
		Visitor v = visitor(helper, "qaNurseWait");
		v.player().setHealth(5f);
		v.player().addEffect(new MobEffectInstance(MobEffects.POISON, 1200));
		List<String> first = rightClick(v, nurse);
		helper.assertTrue(v.player().getHealth() == v.player().getMaxHealth(), "the first click didn't heal: " + v.player().getHealth() + "; " + first);
		helper.assertFalse(v.player().hasEffect(MobEffects.POISON), "the first click didn't cure poison");
		String name = nurse.getDisplayName().getString();
		long start = helper.getLevel().getGameTime();
		String seconds = name + " just treated you. Come back in %d seconds.";
		String second = name + " just treated you. Come back in 1 second.";
		// the seconds left round up: ticks left 1 to 20 read 1 second, 21 to 40 read 2, and a full minute never reads one more
		int full = (minute + 19) / 20;
		refused(helper, v, nurse, start, 0, seconds.formatted(full));
		helper.runAfterDelay(1, () -> refused(helper, v, nurse, start, 1, seconds.formatted((minute - 1 + 19) / 20)));
		helper.runAfterDelay(minute - 40, () -> refused(helper, v, nurse, start, minute - 40, seconds.formatted(2)));
		helper.runAfterDelay(minute - 21, () -> refused(helper, v, nurse, start, minute - 21, seconds.formatted(2)));
		helper.runAfterDelay(minute - 20, () -> refused(helper, v, nurse, start, minute - 20, second));
		helper.runAfterDelay(minute - 1, () -> refused(helper, v, nurse, start, minute - 1, second));
		helper.runAfterDelay(minute, () -> {
			v.player().setHealth(5f);
			v.player().addEffect(new MobEffectInstance(MobEffects.POISON, 1200));
			List<String> heard = rightClick(v, nurse);
			helper.assertTrue(v.player().getHealth() == v.player().getMaxHealth(), "the minute is up but she didn't heal: " + heard);
			helper.assertFalse(v.player().hasEffect(MobEffects.POISON), "the minute is up but she didn't cure poison");
			helper.assertTrue(heard.contains(name + " patched you up. Take care out there!"), "the treatment line: " + heard);
			// and the new minute starts there: straight away it's 60 seconds again
			v.player().setHealth(5f);
			int again = (io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(Nurses.COOLDOWN, nurse) + 19) / 20;
			helper.assertTrue(rightClick(v, nurse).contains(seconds.formatted(again)), "right after the second treatment: " + v.seen());
			helper.assertTrue(v.player().getHealth() == 5f, "treated twice on one tick");
			helper.succeed();
		});
	}

	/** The lang has the singular wait line, with the same arguments as the plural one. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheWaitLineHasASingular(GameTestHelper helper) {
		String one = io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.nurse.wait", 1, "Ada", 1).getString();
		String two = io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.nurse.wait", 2, "Ada", 2).getString();
		helper.assertValueEqual(one, "Ada just treated you. Come back in 1 second.", "the one-second line");
		helper.assertValueEqual(two, "Ada just treated you. Come back in 2 seconds.", "the two-second line");
		helper.succeed();
	}
}
