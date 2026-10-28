package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Pathfinder;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
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
import net.minecraft.server.MinecraftServer;
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
 * QA (lane-c-1028-0353) bug tests: the same mistakes B596 and B583 fixed, in sentences beside them that the fixes
 * missed. On the Pacts tab a feud in its last hour of fading, and a war in its last hour, read "0 days and 0 hours";
 * the Pathfinder's chronicle line reads "led ... to a Ancient City" and "an expedition to a Ancient City"; and a
 * Pathfinder with one ration says "(there are 1)".
 */
public class QaWordsVillage1028BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation PATHFINDER = AliveWorkplace.id("pathfinder");

	// ---- the feud lines on the Pacts tab -------------------------------------------------------------------------------

	private static void feudsOn(GameTestHelper helper, MinecraftServer server) {
		boolean feuds = Feuds.ENABLED;
		boolean wars = Feuds.WARS;
		boolean pvp = server.isPvpAllowed();
		Feuds.ENABLED = true;
		Feuds.WARS = true;
		server.setPvpAllowed(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Feuds.ENABLED = feuds;
			Feuds.WARS = wars;
			server.setPvpAllowed(pvp);
			Feuds.Data.get(server).clear();
		});
	}

	private static List<String> feudLore(Land land) {
		ChoiceMenu menu = QaPactsRealmEdictsGameTests.tab(land, 0, land.owner(), TradePage.Tab.PACTS);
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getHoverName().getString().startsWith("Feud with")) {
				return RealmGameTests.lore(menu.icon(slot));
			}
		}
		return List.of("(no feud line)");
	}

	/**
	 * Like B596's offers: a feud with under an hour before it fades (500 ticks, then 1) shouldn't read "Fades in 0 days
	 * and 0 hours without a war", which is false; something true, such as "Fades within the hour without a war".
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaWordsVillageBugFade"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWordsVillageBugFade")
	public void aFeudFadingWithinTheHourDoesntSayZeroDaysAndZeroHours(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		List<String> wrong = new ArrayList<>();
		for (long left : new long[]{500, 1}) {
			long now = server.overworld().getGameTime();
			Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - (Feuds.FADE - left)));
			List<String> lore = feudLore(land);
			boolean says = lore.stream().anyMatch(l -> l.startsWith("Fades"));
			if (!says || lore.stream().anyMatch(l -> l.contains("0 days and 0 hours"))) {
				wrong.add(left + " ticks before it fades: " + lore);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** Likewise a war in its last hour (500 ticks left, then 1): not "At war: 0 days and 0 hours left". */
	//$ gametest_ticks_batch HUGE '100' '"qaWordsVillageBugWar"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWordsVillageBugWar")
	public void aWarInItsLastHourDoesntSayZeroDaysAndZeroHours(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		feudsOn(helper, server);
		List<String> wrong = new ArrayList<>();
		for (long left : new long[]{500, 1}) {
			long now = server.overworld().getGameTime();
			Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now - 2 * 24000L, now - 100, now + left, false, 0, 0, 0));
			List<String> lore = feudLore(land);
			boolean says = lore.stream().anyMatch(l -> l.startsWith("At war"));
			if (!says || lore.stream().anyMatch(l -> l.contains("0 days and 0 hours"))) {
				wrong.add(left + " ticks of war left: " + lore);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- the Pathfinder ------------------------------------------------------------------------------------------------

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

	/**
	 * As B583 and B505: the Pathfinder leads a player to an Ancient City, and the hall's chronicle reads "Sorrel led
	 * Steve to a Ancient City, ..."; expected "to an Ancient City".
	 */
	//$ gametest_ticks_batch HUGE '600' '"qaWordsVillageBugArrive"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaWordsVillageBugArrive")
	public void anExpeditionToAnAncientCityReadsAnInTheChronicle(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, "qa-words-explorer", new BlockPos(22, 2, 24));
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

	/** And when the trip is called off (the player logs out): "turned back from an expedition to an Ancient City". */
	//$ gametest_ticks_batch HUGE '400' '"qaWordsVillageBugTurnBack"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaWordsVillageBugTurnBack")
	public void aCanceledExpeditionToAnAncientCityReadsAnInTheChronicle(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, "qa-words-quitter", new BlockPos(22, 2, 22));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		Villager[] v = new Villager[1];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 22), new BlockPos(20, 2, 18), 8);
			helper.assertTrue(Pathfinder.start(level, player, v[0], "ancient_city", helper.absolutePos(new BlockPos(5, 2, 26)), null), "sets out");
			level.getServer().getPlayerList().remove(player);
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertFalse(Pathfinder.isLeading(v[0]), "a logout cancels the expedition"))
			.thenExecute(() -> {
				List<String> lines = legendChronicle(helper);
				String back = lines.stream().filter(l -> l.contains("turned back")).findFirst().orElse("(no line: " + lines + ")");
				helper.assertTrue(back.endsWith("an expedition to an Ancient City"), "the chronicle reads \"" + back + "\"");
			})
			.thenSucceed();
	}

	/**
	 * 24.5's counted sentences: asked for an expedition with one ration in the chest, the Pathfinder says "... needs 8
	 * food in the chests by their table for the road (there are 1)"; expected "(there is 1)" (and "there are 2" for two).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaWordsVillageBugFood"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaWordsVillageBugFood")
	public void aPathfinderWithOneRationDoesntSayThereAreOne(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, "qa-words-asker", new BlockPos(22, 2, 22));
		setUp(helper, player);
		Villager[] v = new Villager[1];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 22), new BlockPos(20, 2, 18), 1);
			seen.clear();
			Pathfinder.offer(player, v[0]);
		});
		String said = seen.stream().map(Component::getString).filter(s -> s.contains("food")).findFirst().orElse("(nothing about food: " + seen + ")");
		helper.assertTrue(said.contains("(there is 1)"), "with one ration: \"" + said + "\"");
		helper.succeed();
	}
}
