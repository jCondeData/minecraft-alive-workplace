package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendCommand;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.MoodPower;
import io.github.jcondedata.aliveworkplace.legend.PacePower;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 29.2, the Legend engine: the test Legend loads, every condition, {@code make}, the pace and mood powers, the switch. */
public class LegendEngineGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_legend");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Condition condition(String json) {
		return Conditions.parse(JsonParser.parseString(json).getAsJsonObject());
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	/** Legends made by a test leave with it, passed or failed: their auras would reach later tests on the same spot. */
	private static void sendLegendsAway(GameTestHelper helper) {
		Leftovers.after(helper, () -> {
			for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				v.discard();
			}
			LegendPowers.forget();
		});
	}

	private static void check(GameTestHelper helper, Condition c, BlockPos hall, boolean met, int have, int need) {
		Condition.Progress p = c.progress(helper.getLevel(), hall);
		helper.assertTrue(p.met() == met && p.have() == have && p.need() == need,
			c.type() + ": expected " + have + " of " + need + (met ? " (met)" : " (not met)") + ", got " + p.have() + " of " + p.need());
		helper.assertTrue(c.met(helper.getLevel(), hall) == met, c.type() + " met()");
		helper.assertTrue(p.line().getContents() instanceof TranslatableContents t && t.getKey().startsWith("rule.aliveworkplace."), c.type() + " line");
	}

	/** The test Legend's file loads with its rarity, trade, conditions and powers; a typo in a type fails a file. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void testLegendLoads(GameTestHelper helper) {
		Legend legend = Legends.get(TEST).orElse(null);
		helper.assertTrue(legend != null, "test Legend not loaded: " + Legends.all().stream().map(Legend::id).toList());
		helper.assertTrue(legend.rarity() == Rarity.RARE && legend.job().toString().equals("aliveworkplace:builder"), "rarity or job");
		helper.assertTrue(legend.conditions().size() == 2 && legend.powers(PacePower.class).size() == 1 && legend.powers(MoodPower.class).size() == 1,
			"conditions or powers");
		helper.assertTrue(legend.luxury().equals(Optional.of("books")) && legend.ways("visit").size() == 1 && legend.names().size() == 2, "needs, ways or names");
		for (String bad : List.of("{\"type\": \"rank_at_leest\", \"rank\": \"town\"}", "{\"type\": \"villagers\"}")) {
			JsonObject json = JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"title\": \"t\", \"lore\": \"l\", \"conditions\": [" + bad + "]}").getAsJsonObject();
			try {
				Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "bad"), json);
				helper.fail("a bad condition loaded: " + bad);
			} catch (IllegalArgumentException expected) {
				// a typo never makes a Legend free
			}
		}
		JsonObject badPower = JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"title\": \"t\", \"lore\": \"l\", \"powers\": [{\"type\": \"pase\"}]}").getAsJsonObject();
		try {
			Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "bad"), badPower);
			helper.fail("an unknown power loaded");
		} catch (IllegalArgumentException expected) {
			// as above
		}
		JsonObject needsMod = JsonParser.parseString("{\"requires\": [\"no_such_mod\"], \"rarity\": \"mythic\", \"job\": \"aliveworkplace:legend\", \"title\": \"t\", \"lore\": \"l\"}").getAsJsonObject();
		helper.assertTrue(Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "modded"), needsMod) == null, "a Legend needing a missing mod loaded");
		helper.succeed();
	}

	/** Every condition, false and then true on a staged village, with its progress numbers. */
	//$ gametest_ticks_batch AREA '200' '"legendConditions"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "legendConditions")
	public void legendConditions(GameTestHelper helper) {
		Leftovers.clear(helper);
		sendLegendsAway(helper);
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		ServerLevel level = helper.getLevel();
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(dayTime));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.SMOKER);
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.CHEST);
		helper.runAfterDelay(5, () -> {
			// rank_at_least and first_city
			Condition town = condition("{\"type\": \"rank_at_least\", \"rank\": \"town\"}");
			Condition firstCity = condition("{\"type\": \"first_city\"}");
			check(helper, town, hall, false, 0, 2);
			check(helper, firstCity, hall, false, 0, 1);
			entity.setRank(VillageRanks.Rank.CITY);
			check(helper, town, hall, true, 3, 2);
			check(helper, firstCity, hall, true, 1, 1);
			entity.setFounderMoodDay(4);
			check(helper, firstCity, hall, false, 0, 1);

			// villagers and job_level
			Condition two = condition("{\"type\": \"villagers\", \"count\": 2}");
			Condition masters = condition("{\"type\": \"job_level\", \"job\": \"aliveworkplace:builder\", \"level\": 4, \"count\": 1}");
			check(helper, two, hall, false, 0, 2);
			check(helper, masters, hall, false, 0, 1);
			villager(helper, new BlockPos(6, 2, 6), ModVillagers.BUILDER, 3);
			check(helper, two, hall, false, 1, 2);
			check(helper, masters, hall, false, 0, 1);
			Villager rancher = villager(helper, new BlockPos(8, 2, 6), ModVillagers.BUILDER, 5);
			check(helper, two, hall, true, 2, 2);
			check(helper, masters, hall, true, 1, 1);

			// treasury_total (in emeralds; the hall keeps cents) and festival_crowd
			Condition rich = condition("{\"type\": \"treasury_total\", \"emeralds\": 500}");
			check(helper, rich, hall, false, 0, 500);
			entity.addTreasuryTotal(499 * 100);
			check(helper, rich, hall, false, 499, 500);
			entity.addTreasuryTotal(100);
			check(helper, rich, hall, true, 500, 500);
			Condition crowd = condition("{\"type\": \"festival_crowd\", \"count\": 30}");
			entity.setFestivalCrowd(12);
			check(helper, crowd, hall, false, 12, 30);
			entity.setFestivalCrowd(31);
			check(helper, crowd, hall, true, 31, 30);

			// finished: a count, one blueprint, styles (counted from what other tests left finished nearby)
			int b0 = condition("{\"type\": \"finished\", \"count\": 0}").progress(level, hall).have();
			Condition built = condition("{\"type\": \"finished\", \"count\": " + (b0 + 2) + "}");
			Condition huts = condition("{\"type\": \"finished\", \"count\": 1, \"blueprint\": \"aliveworkplace_test:legend_hut\"}");
			check(helper, built, hall, false, b0, b0 + 2);
			check(helper, huts, hall, false, 0, 1);
			BuildSiteManager sites = BuildSiteManager.get(level);
			BlueprintData.Placement one = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 20)), Rotation.NONE, Mirror.NONE);
			BlueprintData.Placement two2 = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(10, 2, 20)), Rotation.NONE, Mirror.NONE);
			Leftovers.after(helper, () -> {
				sites.forgetFinished(one);
				sites.forgetFinished(two2);
			});
			sites.recordFinished(ResourceLocation.parse("aliveworkplace_test:legend_hut"), one, UUID.randomUUID());
			check(helper, built, hall, false, b0 + 1, b0 + 2);
			check(helper, huts, hall, true, 1, 1);
			int s1 = condition("{\"type\": \"finished\", \"styles\": 0}").progress(level, hall).have();
			Condition styles = condition("{\"type\": \"finished\", \"styles\": " + (s1 + 1) + "}");
			check(helper, styles, hall, false, s1, s1 + 1);
			sites.recordFinished(ResourceLocation.parse("aliveworkplace:styled/legendtest/aliveworkplace_test/legend_hut"), two2, UUID.randomUUID());
			check(helper, built, hall, true, b0 + 2, b0 + 2);
			check(helper, huts, hall, true, 2, 1);
			check(helper, styles, hall, true, s1 + 1, s1 + 1);

			// caravan_routes
			Condition routes = condition("{\"type\": \"caravan_routes\", \"count\": 1}");
			check(helper, routes, hall, false, 0, 1);
			Caravans.Data caravans = Caravans.Data.get(level);
			BlockPos far = hall.offset(500, 0, 0);
			caravans.toggleRoute(hall, far, 3);
			Leftovers.after(helper, () -> caravans.remove(hall));
			check(helper, routes, hall, true, 1, 1);

			// meal_kinds: the chest by the smoker is the store
			Condition meals = condition("{\"type\": \"meal_kinds\", \"count\": 2}");
			check(helper, meals, hall, false, 0, 2);
			Container chest = (Container) helper.getBlockEntity(new BlockPos(4, 2, 3));
			chest.setItem(0, new ItemStack(Items.BREAD, 3));
			chest.setItem(1, new ItemStack(Items.BREAD, 3));
			check(helper, meals, hall, false, 1, 2);
			chest.setItem(2, new ItemStack(Items.BAKED_POTATO, 1));
			check(helper, meals, hall, true, 2, 2);

			// animals_at_job: animals within 16 blocks of a rancher's workstation
			Condition animals = condition("{\"type\": \"animals_at_job\", \"jobs\": [\"aliveworkplace:builder\"], \"count\": 2}");
			check(helper, animals, hall, false, 0, 2);
			rancher.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(8, 2, 8))));
			helper.spawn(EntityType.COW, new BlockPos(10, 2, 10)).setNoAi(true);
			check(helper, animals, hall, false, 1, 2);
			helper.spawn(EntityType.COW, new BlockPos(12, 2, 10)).setNoAi(true);
			check(helper, animals, hall, true, 2, 2);

			// research_levels: in all, and in one tree
			Condition research = condition("{\"type\": \"research_levels\", \"count\": 5}");
			Condition lore = condition("{\"type\": \"research_levels\", \"count\": 2, \"tree\": \"ancient_lore\"}");
			check(helper, research, hall, false, 0, 5);
			entity.setResearch(new Research.State(Map.of("swift_hands", 2, "hearth", 1, "ancient_lore/old_tongues", 1), Optional.empty(), 0, false));
			check(helper, research, hall, false, 4, 5);
			check(helper, lore, hall, false, 1, 2);
			entity.setResearch(new Research.State(Map.of("swift_hands", 2, "hearth", 1, "ancient_lore/old_tongues", 2), Optional.empty(), 0, false));
			check(helper, research, hall, true, 5, 5);
			check(helper, lore, hall, true, 2, 2);

			// iron_golems
			Condition golems = condition("{\"type\": \"iron_golems\", \"count\": 1}");
			check(helper, golems, hall, false, 0, 1);
			helper.spawn(EntityType.IRON_GOLEM, new BlockPos(20, 2, 4)).setNoAi(true);
			check(helper, golems, hall, true, 1, 1);

			// full_moon
			Condition moon = condition("{\"type\": \"full_moon\"}");
			level.setDayTime(24000L * 8 + 18000);
			check(helper, moon, hall, true, 1, 1);
			level.setDayTime(24000L * 9 + 18000);
			check(helper, moon, hall, false, 0, 1);
			level.setDayTime(dayTime);

			// legend: a given Legend settled here
			Condition settled = condition("{\"type\": \"legend\", \"id\": \"aliveworkplace_test:test_legend\"}");
			check(helper, settled, hall, false, 0, 1);
			Villager sage = villager(helper, new BlockPos(20, 2, 20), VillagerProfession.NONE, 1);
			Legends.make(level, sage, Legends.get(TEST).orElseThrow(), "test");
			check(helper, settled, hall, true, 1, 1);
			helper.assertTrue(Conditions.types().size() == 14, "condition types: " + Conditions.types());
			helper.succeed();
		});
	}

	/** {@code make} turns the nearest villager into the Legend, a Master builder with every level's trades, and it survives a save and reload. */
	//$ gametest_ticks_batch AREA '100' '"legendMakeAndReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendMakeAndReload")
	public void legendMakeAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		sendLegendsAway(helper);
		hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			Villager v = villager(helper, new BlockPos(5, 2, 5), VillagerProfession.FARMER, 2);
			List<Component> said = new ArrayList<>();
			int made = LegendCommand.make(source(helper, new BlockPos(6, 2, 5), said), TEST);
			helper.assertTrue(made == 1, "make failed: " + said);
			helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.BUILDER && v.getVillagerData().getLevel() == 5, "not a Master builder: " + v.getVillagerData());
			helper.assertTrue(v.getOffers().size() >= 5, "trades: " + v.getOffers().size());
			LegendData data = ModAttachments.LEGEND.get(v);
			helper.assertTrue(data != null && data.id().equals(TEST) && data.settled() && data.hall().equals(Optional.of(helper.absolutePos(HALL)))
				&& !data.onStrike(), "attachment: " + data);
			helper.assertTrue(Legends.of(v).isPresent(), "Legends.of");
			CompoundTag tag = new CompoundTag();
			v.saveWithoutId(tag);
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(tag);
			LegendData loaded = ModAttachments.LEGEND.get(copy);
			helper.assertTrue(loaded != null && loaded.equals(data), "after reload: " + loaded);
			helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.BUILDER && copy.getVillagerData().getLevel() == 5
				&& copy.getOffers().size() == v.getOffers().size(), "trade after reload");
			// an old attachment with only the id still reads, with defaults
			CompoundTag old = new CompoundTag();
			old.putString("id", "aliveworkplace_test:test_legend");
			LegendData bare = LegendData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).result().orElse(null);
			helper.assertTrue(bare != null && bare.settled() && bare.strikeSince() == -1 && bare.hall().isEmpty(), "defaults: " + bare);
			int cleared = LegendCommand.clear(source(helper, new BlockPos(6, 2, 5), said));
			helper.assertTrue(cleared == 1 && !ModAttachments.LEGEND.has(v) && v.getVillagerData().getLevel() == 5, "clear");
			helper.assertTrue(LegendCommand.make(source(helper, new BlockPos(6, 2, 5), said), ResourceLocation.parse("aliveworkplace_test:nobody")) == 0, "an unknown Legend made");
			helper.succeed();
		});
	}

	/** A pace power speeds a builder within its radius, not one outside it, and never past the shared 2x cap. */
	//$ gametest_ticks_batch AREA '100' '"legendPace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendPace")
	public void legendPace(GameTestHelper helper) {
		Leftovers.clear(helper);
		sendLegendsAway(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Legend legend = Legends.get(TEST).orElseThrow();
			Villager near = villager(helper, new BlockPos(5, 2, 5), ModVillagers.BUILDER, 1);
			Villager far = villager(helper, new BlockPos(25, 2, 25), ModVillagers.BUILDER, 1);
			int nearBase = BuilderLevels.delay(1000, near);
			int farBase = BuilderLevels.delay(1000, far);
			Villager ada = villager(helper, new BlockPos(3, 2, 5), VillagerProfession.NONE, 1);
			Legends.make(level, ada, legend, "test");
			helper.assertTrue(LegendPowers.pace(near) == 1.5f && LegendPowers.pace(far) == 1f, "pace " + LegendPowers.pace(near) + " / " + LegendPowers.pace(far));
			helper.assertTrue(BuilderLevels.delay(1000, near) == Math.round(nearBase / 1.5f), "near: " + BuilderLevels.delay(1000, near) + " from " + nearBase);
			helper.assertTrue(BuilderLevels.delay(1000, far) == farBase, "far builder sped up");
			Villager second = villager(helper, new BlockPos(7, 2, 5), VillagerProfession.NONE, 1);
			Legends.make(level, second, legend, "test");
			helper.assertTrue(LegendPowers.pace(near) == LegendPowers.PACE_CAP, "two Legends: " + LegendPowers.pace(near) + ", over the cap");
			helper.assertTrue(BuilderLevels.delay(1000, near) == Math.round(nearBase / 2f), "capped delay: " + BuilderLevels.delay(1000, near));
			// on strike: no powers
			ModAttachments.LEGEND.set(ada, new LegendData(TEST, "", false, Optional.empty(), 0, -1, Map.of(), 3, -1, "test"));
			ModAttachments.LEGEND.set(second, new LegendData(TEST, "", false, Optional.empty(), 0, -1, Map.of(), 3, -1, "test"));
			LegendPowers.forget();
			helper.assertTrue(LegendPowers.pace(near) == 1f, "a Legend on strike still has powers");
			helper.succeed();
		});
	}

	/** A mood power shows in the mood of a villager near the Legend, as a reason, and not for one far away. */
	//$ gametest_ticks_batch AREA '100' '"legendMood"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendMood")
	public void legendMood(GameTestHelper helper) {
		Leftovers.clear(helper);
		sendLegendsAway(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Villager near = villager(helper, new BlockPos(5, 2, 5), VillagerProfession.FARMER, 1);
			Villager far = villager(helper, new BlockPos(25, 2, 25), VillagerProfession.FARMER, 1);
			int before = Moods.work(level, near).score();
			Villager ada = villager(helper, new BlockPos(3, 2, 5), VillagerProfession.NONE, 1);
			int farBefore = Moods.work(level, far).score();
			Legends.make(level, ada, Legends.get(TEST).orElseThrow(), "test");
			Moods.Mood mood = Moods.work(level, near);
			boolean reason = mood.good().stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("mood.aliveworkplace.reason.legend"));
			helper.assertTrue(reason, "no Legend reason in " + mood.good());
			helper.assertTrue(mood.score() == Math.min(100, before + 7 + 5), "score " + mood.score() + " from " + before);
			helper.assertTrue(Moods.work(level, far).good().stream().noneMatch(c -> c.getContents() instanceof TranslatableContents t
				&& t.getKey().equals("mood.aliveworkplace.reason.legend")) && Moods.work(level, far).score() == farBefore, "far villager");
			helper.succeed();
		});
	}

	/** With {@code legends} off no Legend loads, a Legend has no powers, nothing is listed, and the command says so. */
	//$ gametest_ticks_batch AREA '100' '"legendsOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendsOff")
	public void legendsOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		sendLegendsAway(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Villager near = villager(helper, new BlockPos(5, 2, 5), ModVillagers.BUILDER, 1);
			Villager ada = villager(helper, new BlockPos(3, 2, 5), VillagerProfession.NONE, 1);
			Legends.make(level, ada, Legends.get(TEST).orElseThrow(), "test");
			helper.assertTrue(LegendPowers.pace(near) == 1.5f, "on: pace");
			Legends.ENABLED = false;
			Leftovers.after(helper, () -> {
				Legends.ENABLED = true;
				Legends.reload(level.getServer().getResourceManager());
			});
			Legends.reload(level.getServer().getResourceManager());
			helper.assertTrue(Legends.all().isEmpty() && Legends.get(TEST).isEmpty(), "Legends loaded while off");
			helper.assertTrue(LegendPowers.settled(level).isEmpty() && LegendPowers.pace(near) == 1f, "powers while off");
			helper.assertTrue(ModAttachments.LEGEND.has(ada) && ada.getVillagerData().getLevel() == 5, "the settled Legend should stay a Master, attachment kept");
			List<Component> said = new ArrayList<>();
			helper.assertTrue(LegendCommand.list(source(helper, new BlockPos(4, 2, 5), said)) == 0
				&& LegendCommand.make(source(helper, new BlockPos(4, 2, 5), said), TEST) == 0
				&& LegendCommand.clear(source(helper, new BlockPos(4, 2, 5), said)) == 0, "command worked while off");
			helper.assertTrue(said.size() == 3 && said.stream().allMatch(c -> key(c).equals("message.aliveworkplace.legend.off")), "command said " + said);
			Legends.ENABLED = true;
			Legends.reload(level.getServer().getResourceManager());
			helper.assertTrue(Legends.get(TEST).isPresent() && LegendPowers.pace(near) == 1.5f, "back on: the Legend works again");
			helper.succeed();
		});
	}

	/** The lang key of a message (failures come wrapped in a red empty component). */
	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? "" : key(c.getSiblings().get(0));
	}

	/** An op's command source at {@code pos} that keeps what it is told. */
	private static CommandSourceStack source(GameTestHelper helper, BlockPos pos, List<Component> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message);
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(helper.absolutePos(pos)), Vec2.ZERO, helper.getLevel(), 4, "test", Component.literal("test"),
			helper.getLevel().getServer(), null);
	}
}
