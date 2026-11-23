package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * Bug found by QA (lane-c-1123-0953), story arcs (ROADMAP 31.4): a spawned foe of a {@code kill role:} step killed by
 * something other than a player (an iron golem) is marked dead and never put back, so the tale hangs in that chapter for
 * good, and the village can never start another main arc.
 */
public class QaStoryArcs1123cBugGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos OTHER_HALL = new BlockPos(24, 2, 24);
	private static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/three_chapters");
	private static final ResourceLocation TALK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/talk");
	private static final long DAY = VillageNeeds.DAY;

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		int near = Arcs.NEAR;
		boolean enabled = Arcs.ENABLED;
		boolean auto = Arcs.AUTO;
		VillageHalls.RADIUS = 8;
		Arcs.AUTO = false; // (the rounds here move arcs on; no roll starts another tale in the test's villages)
		Arcs.ENABLED = true;
		Arcs.AT_ONCE = 50;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.NEAR = near;
			Arcs.ENABLED = enabled;
			Arcs.AUTO = auto;
		});
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
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

	/** A logged-in player at {@code at} (absolute) whose chat lines are kept in {@code heard}. */
	private static ServerPlayer listener(GameTestHelper helper, String name, BlockPos at, List<String> heard) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				if (!overlay) {
					heard.add(message.getString());
				}
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, p, cookie);
		Leftovers.after(helper, () -> server.getPlayerList().remove(p));
		p.setGameMode(GameType.SURVIVAL);
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		heard.clear();
		return p;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static List<Quest> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString()).toList();
	}

	private static List<Zombie> grubbs(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Zombie.class, new AABB(hall).inflate(24), z -> z.isAlive() && z.getTags().contains(ArcEffects.roleTag("grubb")));
	}

	/** Hands in the apples from village {@code hall}'s Story tab, as the player's click does. */
	private static void handInApples(GameTestHelper helper, ServerLevel level, BlockPos hall, ServerPlayer player) {
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		for (int slot : QuestJournal.STORY_QUEST_SLOTS) {
			if (menu.icon(slot).is(Items.APPLE)) {
				menu.press(slot, player);
				return;
			}
		}
		helper.fail("no apples quest on the Story tab");
	}

	private static String storyTabTitle(ServerLevel level, BlockPos hall, ServerPlayer player) {
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		return menu.icon(22).getHoverName().getString();
	}

	// ---- the tests -------------------------------------------------------------------------------------------------

	/**
	 * Grubb, the spawned foe of the kill step, is killed by an iron golem (the village's own guard), not by a player. The
	 * tale must not hang for ever on a foe that will never come again: within the next checks and days either the tale
	 * moves on (ends) or Grubb is put back at his spot to be fought.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcFoeGolem1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcFoeGolem1123c")
	public void qaAFoeKilledByAGolemDoesntLeaveTheTaleHanging(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		player(helper, HALL.south(2));
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		Arcs.start(level, hall, arc(helper, THREE));
		while (Arcs.running(level, hall) != null && Arcs.running(level, hall).chapter < 2) {
			Arcs.next(level, hall);
		}
		ArcEffects.check(level);
		List<Zombie> grubb = grubbs(level, hall);
		helper.assertTrue(grubb.size() == 1, "Grubb: " + grubb);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, HALL.west(3));
		grubb.get(0).hurt(level.damageSources().mobAttack(golem), 1000f);
		golem.discard();
		helper.assertTrue(grubbs(level, hall).isEmpty(), "Grubb lived");
		for (long d = day + 1; d <= day + 3; d++) {
			setDay(level, d);
			ArcEffects.check(level);
			ArcEffects.check(level);
			Arcs.round(level, hall, entity);
		}
		ArcState s = Arcs.running(level, hall);
		helper.assertTrue(s == null || !grubbs(level, hall).isEmpty(),
			"three days after a golem killed Grubb the tale still waits in chapter " + (s == null ? -1 : s.chapter + 1)
				+ " for a foe that never comes again (quests: " + arcQuests(level, hall).stream().map(q -> q.objectives.get(0).line().getString()).toList() + ")");
		helper.succeed();
	}
}
