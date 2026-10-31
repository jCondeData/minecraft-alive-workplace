package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Pathfinder;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * B673 and B674: the Pathfinder Legend's sentences with a name or a number in them. The hall's chronicle says "led ...
 * to an Ancient City" and "an expedition to an Ancient City" (a Stronghold keeps "a"), and the Pathfinder asked with one
 * ration says "(there is 1)", with two "(there are 2)". The first three tests are QA's (lane-c-1028-0353,
 * QaWordsVillage1028BugTests on tests/words-village-1028), brought in with the fix.
 */
public class PathfinderWords1028GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation PATHFINDER = AliveWorkplace.id("pathfinder");

	private static void setUp(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		LegendRecord stale = LegendRecord.get(level);
		stale.entries().stream().filter(e -> e.id().equals(PATHFINDER)).map(LegendRecord.Entry::villager).toList().forEach(stale::forget);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setOwner(owner.getUUID(), owner.getName().getString());
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		Leftovers.after(helper, () -> {
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(96), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	/** Only the real Pathfinder on the roster, moods and Legend needs off while {@code body} runs. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend pathfinder = Legends.get(PATHFINDER).orElseThrow(() -> new AssertionError("pathfinder.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(PATHFINDER, pathfinder);
			Legends.setForTest(map);
			body.accept(pathfinder);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
		}
	}

	private static Villager pathfinder(GameTestHelper helper, Legend legend, BlockPos at, BlockPos table, int bread) {
		helper.setBlock(table, Blocks.CARTOGRAPHY_TABLE);
		helper.setBlock(table.east(), Blocks.CHEST);
		if (bread > 0) {
			((Container) helper.getBlockEntity(table.east())).setItem(0, new ItemStack(Items.BREAD, bread));
		}
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		Legends.make(helper.getLevel(), v, legend, "test");
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(table)));
		return v;
	}

	/** A player at {@code at} whose chat and system messages are kept in {@code seen}. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name, BlockPos at) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos a = helper.absolutePos(at);
		player.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static List<String> legendChronicle(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		return hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND).map(e -> e.text().getString()).toList();
	}

	/** B673: the Pathfinder leads a player to an Ancient City, and the chronicle reads "to an Ancient City,". */
	//$ gametest_ticks_batch HUGE '600' '"pathfinderWordsArrive"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "pathfinderWordsArrive")
	public void anExpeditionToAnAncientCityReadsAnInTheChronicle(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, "words-explorer", new BlockPos(22, 2, 24));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		BlockPos target = helper.absolutePos(new BlockPos(26, 2, 26));
		Villager[] v = new Villager[1];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 24), new BlockPos(20, 2, 20), 8);
			helper.assertTrue(Pathfinder.start(level, player, v[0], "ancient_city", target, null), "sets out");
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(Pathfinder.state(v[0]).arrived(), "arrives at the place"))
			.thenExecute(() -> {
				List<String> lines = legendChronicle(helper);
				String led = lines.stream().filter(l -> l.contains(" led ")).findFirst().orElse("(no expedition line: " + lines + ")");
				helper.assertTrue(led.contains(" to an Ancient City,"), "the chronicle reads \"" + led + "\"");
			})
			.thenSucceed();
	}

	/** B673: called off (the player logs out), "turned back from an expedition to an Ancient City". */
	//$ gametest_ticks_batch HUGE '400' '"pathfinderWordsTurnBack"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "pathfinderWordsTurnBack")
	public void aCanceledExpeditionToAnAncientCityReadsAnInTheChronicle(GameTestHelper helper) {
		turnBack(helper, "ancient_city", "words-quitter", "an expedition to an Ancient City");
	}

	/** B673's other side: a Stronghold keeps "a" ("an expedition to a Stronghold"). */
	//$ gametest_ticks_batch HUGE '400' '"pathfinderWordsTurnBackStronghold"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "pathfinderWordsTurnBackStronghold")
	public void aCanceledExpeditionToAStrongholdKeepsA(GameTestHelper helper) {
		turnBack(helper, "stronghold", "words-quitter-2", "an expedition to a Stronghold");
	}

	private static void turnBack(GameTestHelper helper, String kind, String name, String ending) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, name, new BlockPos(22, 2, 22));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		Villager[] v = new Villager[1];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 22), new BlockPos(20, 2, 18), 8);
			helper.assertTrue(Pathfinder.start(level, player, v[0], kind, helper.absolutePos(new BlockPos(5, 2, 26)), null), "sets out");
			level.getServer().getPlayerList().remove(player);
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertFalse(Pathfinder.isLeading(v[0]), "a logout cancels the expedition"))
			.thenExecute(() -> {
				List<String> lines = legendChronicle(helper);
				String back = lines.stream().filter(l -> l.contains("turned back")).findFirst().orElse("(no line: " + lines + ")");
				helper.assertTrue(back.endsWith(ending), "the chronicle reads \"" + back + "\"");
			})
			.thenSucceed();
	}

	/**
	 * B674 (24.5): asked through the sneak-right-click offer with one ration in the chest, the Pathfinder says "(there is
	 * 1)"; with two, "(there are 2)".
	 */
	//$ gametest_ticks_batch HUGE '100' '"pathfinderWordsFood"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "pathfinderWordsFood")
	public void aPathfinderWithOneRationDoesntSayThereAreOne(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, "words-asker", new BlockPos(22, 2, 22));
		setUp(helper, player);
		Villager[] v = new Villager[1];
		String[] said = new String[2];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 22), new BlockPos(20, 2, 18), 1);
			seen.clear();
			Pathfinder.offer(player, v[0]);
			said[0] = food(seen);
			((Container) helper.getBlockEntity(new BlockPos(21, 2, 18))).setItem(0, new ItemStack(Items.BREAD, 2));
			seen.clear();
			Pathfinder.offer(player, v[0]);
			said[1] = food(seen);
		});
		helper.assertTrue(said[0].endsWith("for the road (there is 1)"), "with one ration: \"" + said[0] + "\"");
		helper.assertTrue(said[1].endsWith("for the road (there are 2)"), "with two rations: \"" + said[1] + "\"");
		helper.succeed();
	}

	private static String food(List<Component> seen) {
		return seen.stream().map(Component::getString).filter(s -> s.contains("food")).findFirst().orElse("(nothing about food: " + seen + ")");
	}
}
