package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.explore.Explorers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.ExpeditionPower;
import io.github.jcondedata.aliveworkplace.legend.FarExpeditionsPower;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Pathfinder;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * 29.13, the Pathfinder: the real file (a Rare Cartographer who likes clothes, found at a ruined portal or born to a
 * Cartographer), found only for a village with an Expert explorer; an expedition to a staged place takes 8 food, hands
 * over a map, leads, waits for a player more than 24 blocks behind and catches up beyond 64; at the place a banner and
 * a chronicle line, and Home brings both back to the hall; one a day; a logout cancels it and the Pathfinder is home
 * the next morning; the new loot table rolls maps, trial keys and echo shards; their own expeditions range twice as far;
 * and every sentence a player reads.
 */
public class PathfinderGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation ID = AliveWorkplace.id("pathfinder");

	/** The hall (owned by {@code owner}, on the list of villages); villagers, record entries and players go at the end. */
	private static void setUp(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		LegendRecord stale = LegendRecord.get(level);
		stale.entries().stream().filter(e -> e.id().equals(ID)).map(LegendRecord.Entry::villager).toList().forEach(stale::forget);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		if (owner != null) {
			((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setOwner(owner.getUUID(), owner.getName().getString());
		}
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		Leftovers.after(helper, () -> {
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(96), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			Leftovers.players(helper);
			unstage(helper);
		});
	}

	/** Runs {@code body} with only the real Pathfinder loaded, moods and needs off; the roster and clock go back after. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend pathfinder = Legends.get(ID).orElseThrow(() -> new AssertionError("pathfinder.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(ID, pathfinder);
			Legends.setForTest(map);
			body.accept(pathfinder);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
		}
	}

	/** Puts the full roster back (at the end of a test that kept the staged one running). */
	private static void unstage(GameTestHelper helper) {
		Legends.reload(helper.getLevel().getServer().getResourceManager());
		LegendPowers.forget();
	}

	/** The Pathfinder, settled, at a cartography table {@code table} with a chest beside it holding {@code bread}. */
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

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos a = helper.absolutePos(at);
		p.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
		return p;
	}

	private static boolean holds(ServerPlayer player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	private static boolean chronicleHas(GameTestHelper helper, String key) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		for (Chronicle.Entry e : hall.chronicle()) {
			if (e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(key)) {
				return true;
			}
		}
		return false;
	}

	//$ gametest_ticks_batch AREA '100' '"pathfinderFound"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "pathfinderFound")
	public void foundOnlyForAVillageWithAnExpertExplorer(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(15, 2, 15));
		setUp(helper, owner);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		try {
			staged(helper, legend -> {
				helper.assertTrue(legend.job().equals(ResourceLocation.withDefaultNamespace("cartographer")), "a Cartographer");
				helper.assertTrue(legend.luxury().equals(java.util.Optional.of("clothes")), "likes clothes");
				helper.assertTrue(legend.ways("found").stream().anyMatch(w -> "ruined_portal".equals(w.get("site").getAsString())), "found at a ruined portal");
				helper.assertTrue(legend.ways("born").stream().anyMatch(w -> w.toString().contains("minecraft:cartographer")), "born to a Cartographer");
				helper.assertTrue(!legend.powers(ExpeditionPower.class).isEmpty() && !legend.powers(FarExpeditionsPower.class).isEmpty(), "both powers");
				Villager explorer = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
				explorer.setNoAi(true);
				explorer.setVillagerData(explorer.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER).setLevel(3));
				boolean met = legend.conditions().stream().allMatch(c -> c.met(level, hall));
				helper.assertFalse(met, "a Journeyman explorer isn't enough");
				helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", owner.blockPosition()).isEmpty(),
					"no Pathfinder at the portal for a village without an Expert explorer");
				explorer.setVillagerData(explorer.getVillagerData().setLevel(4));
				for (Condition c : legend.conditions()) {
					helper.assertTrue(c.met(level, hall), "met with an Expert explorer: " + c.type());
				}
				helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", owner.blockPosition())
						.map(m -> m.legend().id().equals(ID)).orElse(false),
					"the Pathfinder waits at the portal once one explorer is an Expert");
			});
		} finally {
			unstage(helper);
		}
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '1200' '"pathfinderLead"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "pathfinderLead")
	public void expeditionLeadsWaitsAndCatchesUp(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(4, 2, 3));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		BlockPos target = helper.absolutePos(new BlockPos(27, 2, 27));
		Villager[] v = new Villager[1];
		double[] start = new double[1];
		Vec3[] held = new Vec3[1];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(3, 2, 4), new BlockPos(1, 2, 8), 7);
			helper.assertFalse(Pathfinder.start(level, player, v[0], "stronghold", target, null), "7 food isn't enough");
			((Container) helper.getBlockEntity(new BlockPos(2, 2, 8))).setItem(1, new ItemStack(Items.BREAD, 3));
			helper.assertTrue(Pathfinder.start(level, player, v[0], "stronghold", target, null), "sets out with 8 food");
			helper.assertTrue(Pathfinder.food(level, v[0]) == 2, "8 rations taken from the chest, " + Pathfinder.food(level, v[0]) + " left");
			helper.assertTrue(holds(player, Items.FILLED_MAP), "the player holds a map to the place");
			helper.assertTrue(Pathfinder.isLeading(v[0]), "leading");
			helper.assertFalse(Pathfinder.start(level, player, v[0], "stronghold", target, null), "one at a time");
			start[0] = v[0].position().distanceTo(Vec3.atBottomCenterOf(target));
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(v[0].position().distanceTo(Vec3.atBottomCenterOf(target)) < start[0] - 6,
				"walks ahead toward the place (" + v[0].position() + ")"))
			.thenExecute(() -> {
				// the player falls more than 24 blocks behind: they stop and wait
				BlockPos behind = BlockPos.containing(v[0].position()).offset(-28, 0, 0);
				player.moveTo(behind.getX() + 0.5, v[0].getY() + 8, behind.getZ() + 0.5);
			})
			.thenIdle(20)
			.thenExecute(() -> held[0] = v[0].position())
			.thenIdle(60)
			.thenExecute(() -> helper.assertTrue(v[0].position().distanceTo(held[0]) < 1.5,
				"waits while the player is " + (int) v[0].distanceTo(player) + " blocks behind (moved " + v[0].position().distanceTo(held[0]) + ")"))
			.thenExecute(() -> player.moveTo(v[0].getX() - 70, v[0].getY() + 8, v[0].getZ()))
			.thenWaitUntil(() -> helper.assertTrue(Math.hypot(v[0].getX() - player.getX(), v[0].getZ() - player.getZ()) < 4,
				"catches up at once beyond 64 blocks"))
			.thenExecute(() -> {
				helper.assertTrue(Pathfinder.isLeading(v[0]), "still on the expedition");
				unstage(helper);
			})
			.thenSucceed();
	}

	//$ gametest_ticks_batch AREA '600' '"pathfinderHome"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "pathfinderHome")
	public void arrivingPlantsABannerAndHomeBringsBothBack(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(22, 2, 24));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		BlockPos target = helper.absolutePos(new BlockPos(26, 2, 26));
		BlockPos hall = helper.absolutePos(HALL);
		Villager[] v = new Villager[1];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 24), new BlockPos(20, 2, 20), 8);
			helper.assertTrue(Pathfinder.start(level, player, v[0], "ancient_city", target, null), "sets out");
			helper.assertTrue(Pathfinder.askHome(player, v[0]), "a right-click on the way is taken");
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(Pathfinder.state(v[0]).arrived(), "arrives at the place"))
			.thenExecute(() -> {
				int banner = -1;
				for (int y = 0; y < 40; y++) {
					if (helper.getBlockState(new BlockPos(26, y, 26)).is(Blocks.LIGHT_BLUE_BANNER)) {
						banner = y;
					}
				}
				helper.assertTrue(banner >= 2, "a banner marks the entrance (at y " + banner + ")");
				helper.assertTrue(chronicleHas(helper, "chronicle.aliveworkplace.legend.expedition"), "the chronicle records it");
				helper.assertTrue(Pathfinder.askHome(player, v[0]), "Home asked for");
			})
			.thenIdle(40)
			.thenExecute(() -> helper.assertTrue(Pathfinder.isLeading(v[0]), "not home before 5 seconds of standing still"))
			.thenWaitUntil(() -> helper.assertFalse(Pathfinder.isLeading(v[0]), "Home"))
			.thenExecute(() -> {
				helper.assertTrue(v[0].blockPosition().distManhattan(hall) <= 10, "the Pathfinder is beside the hall (" + v[0].blockPosition() + ")");
				helper.assertTrue(player.blockPosition().distManhattan(hall) <= 10, "and so is the player (" + player.blockPosition() + ")");
				helper.assertFalse(Pathfinder.start(level, player, v[0], "ancient_city", target, null), "one a day");
				unstage(helper);
			})
			.thenSucceed();
	}

	//$ gametest_ticks_batch AREA '400' '"pathfinderLogout"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "pathfinderLogout")
	public void aLogoutCancelsItAndTheyAreHomeNextMorning(GameTestHelper helper) {
		ServerPlayer player = player(helper, new BlockPos(22, 2, 22));
		setUp(helper, player);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		long time = level.getDayTime();
		Villager[] v = new Villager[1];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 22), new BlockPos(20, 2, 18), 8);
			helper.assertTrue(Pathfinder.start(level, player, v[0], "trial_chambers", helper.absolutePos(new BlockPos(5, 2, 26)), null), "sets out");
			level.getServer().getPlayerList().remove(player);
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertFalse(Pathfinder.isLeading(v[0]), "a logout cancels the expedition"))
			.thenExecute(() -> {
				helper.assertTrue(Pathfinder.state(v[0]).strandedDay() == Chronicle.day(level), "out until the next morning");
				helper.assertTrue(chronicleHas(helper, "chronicle.aliveworkplace.legend.expedition_cancelled"), "the chronicle says they turned back");
				helper.assertTrue(v[0].blockPosition().distManhattan(hall) > 12, "not home yet");
				level.setDayTime((level.getDayTime() / 24000L + 1) * 24000L + 1000);
			})
			.thenWaitUntil(() -> helper.assertTrue(v[0].blockPosition().distManhattan(hall) <= 10, "home the next morning"))
			.thenExecute(() -> {
				helper.assertTrue(Pathfinder.state(v[0]).strandedDay() < 0, "no longer out");
				level.setDayTime(time);
				unstage(helper);
			})
			.thenSucceed();
	}

	//$ gametest_ticks_batch AREA '100' '"pathfinderLoot"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "pathfinderLoot")
	public void theLootTableRollsAndTheirOwnTripsRangeTwiceAsFar(GameTestHelper helper) {
		setUp(helper, null);
		ServerLevel level = helper.getLevel();
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(HALL)))
			.create(LootContextParamSets.CHEST);
		Set<Item> seen = new HashSet<>();
		var table = level.getServer().reloadableRegistries().getLootTable(Pathfinder.LOOT);
		for (int i = 0; i < 400; i++) {
			for (ItemStack s : table.getRandomItems(params, 1300L + i)) {
				seen.add(s.getItem());
			}
		}
		helper.assertTrue(seen.contains(Items.MAP), "maps");
		helper.assertTrue(seen.contains(Items.TRIAL_KEY), "now and then a trial key");
		helper.assertTrue(seen.contains(Items.ECHO_SHARD), "now and then an echo shard");
		try {
			staged(helper, legend -> {
				Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
				plain.setNoAi(true);
				plain.setVillagerData(plain.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER).setLevel(5));
				Villager pf = pathfinder(helper, legend, new BlockPos(8, 2, 8), new BlockPos(10, 2, 10), 0);
				pf.setNoAi(true);
				helper.assertTrue(Pathfinder.rangeFactor(plain) == 1f && Pathfinder.rangeFactor(pf) == 2f, "the Pathfinder's own trips range twice as far");
				helper.assertTrue(Pathfinder.extraLoot(plain).isEmpty() && Pathfinder.extraLoot(pf).equals(java.util.Optional.of(Pathfinder.LOOT)),
					"and roll the pathfinder table");
				Set<Item> finds = new HashSet<>();
				for (int i = 0; i < 60; i++) {
					Explorers.finds(level, pf, pf.blockPosition(), false).forEach(s -> finds.add(s.getItem()));
				}
				helper.assertTrue(finds.contains(Items.MAP) || finds.contains(Items.PAPER), "the Pathfinder's finds include the table's maps and paper");
				// every sentence a player reads
				helper.assertTrue(Component.translatable("legend.aliveworkplace.pathfinder.title").getString().equals("Pathfinder"), "title");
				helper.assertTrue(Component.translatable("message.aliveworkplace.pathfinder.food", "Sorrel", 8, 3).getString()
					.equals("Sorrel needs 8 food in the chests by their table for the road (there are 3)"), "food line");
				helper.assertTrue(Component.translatable("chronicle.aliveworkplace.legend.expedition", "Sorrel", "Jesse",
						Component.translatable("expedition.aliveworkplace.stronghold"), 1240, Component.translatable("screen.aliveworkplace.hall.dir.ne"))
					.getString().equals("Sorrel led Jesse to a Stronghold, 1240 blocks north-east of the village"), "chronicle line");
				for (String key : List.of("legend.aliveworkplace.pathfinder.lore", "legend.aliveworkplace.pathfinder.name.1", "legend.aliveworkplace.power.far_expeditions",
					"legend.aliveworkplace.power.expedition", "expedition.aliveworkplace.ancient_city", "expedition.aliveworkplace.trial_chambers",
					"message.aliveworkplace.pathfinder.offer", "message.aliveworkplace.pathfinder.set_out", "message.aliveworkplace.pathfinder.arrived",
					"message.aliveworkplace.pathfinder.home_wait", "message.aliveworkplace.pathfinder.home", "message.aliveworkplace.pathfinder.tomorrow",
					"message.aliveworkplace.pathfinder.none", "message.aliveworkplace.pathfinder.no_offer", "message.aliveworkplace.pathfinder.busy",
					"message.aliveworkplace.pathfinder.on_strike", "message.aliveworkplace.pathfinder.not_yet", "message.aliveworkplace.pathfinder.pick",
					"chronicle.aliveworkplace.legend.expedition_cancelled")) {
					helper.assertTrue(!Component.translatable(key).getString().equals(key), "untranslated " + key);
				}
				for (var p : legend.powers()) {
					String line = p.describe().getString();
					helper.assertTrue(!line.startsWith("legend.") && !line.contains("%"), "power line " + line);
				}
				helper.assertTrue(ModAttachments.PATHFINDER.get(plain) == null, "nothing saved on an ordinary explorer");
			});
		} finally {
			unstage(helper);
		}
		helper.succeed();
	}
}
