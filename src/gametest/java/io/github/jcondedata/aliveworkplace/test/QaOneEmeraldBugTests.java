package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane E, player sentences; kin of B377/B395/B402): one emerald is written "1 emeralds". Without CobbleDollars
 * every price, purse and treasury goes through {@link Money#describe}, and a Novice trainer's prize is 1 emerald.
 */
public class QaOneEmeraldBugTests implements FabricGameTest {

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oneEmeraldIsWrittenInTheSingular(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "CobbleDollars is loaded in this run");
		String one = Money.describe(100, 1).getString();
		helper.assertTrue(one.equals("1 emerald"), "one emerald reads: " + one);
		String two = Money.describe(200, 2).getString();
		helper.assertTrue(two.equals("2 emeralds"), "two emeralds read: " + two);
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void beatingANoviceTrainerPaysOneEmeraldInTheSingular(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		trainer.setNoAi(true);
		trainer.setVillagerData(trainer.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(1));
		helper.assertTrue(Trainers.tier(trainer) == 1, "not a Novice: " + Trainers.tier(trainer));
		Trainers.battleOver(level.getServer(), trainer.getUUID(), player.getUUID(), true);
		String won = seen.stream().filter(s -> s.startsWith("You beat ")).findFirst().orElse("");
		helper.assertTrue(won.endsWith("+1 emerald"), "the prize line: '" + won + "' (all: " + seen + ")");
		helper.succeed();
	}

	/** A test player who keeps every system message they're sent. */
	private static ServerPlayer listener(GameTestHelper helper, List<String> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-winner"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message.getString());
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}
}
