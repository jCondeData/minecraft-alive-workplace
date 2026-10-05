package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.StrangeMood;
import io.github.jcondedata.aliveworkplace.legend.StrangeMoods;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Pace;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * 29.10, strange moods and Masterworks, with fixed dice: a mood starts only in a happy village with a Master of a named
 * trade (not an Expert, not another trade, not with the Legend's conditions unmet or its slot taken, never during a raid
 * or a festival, never with the switch off, never twice at once); the three materials on the requests board; success
 * makes the Masterwork (name, lore, glint, the player who brought the most) and the Legend, and a framed Masterwork is
 * beauty; failure sulks a week (mood, pace, the line over their head) and blocks moods for 10 days; a mood survives a
 * save and reload halfway, and so do the hall's fields. Days are passed as {@link Chronicle#day}s.
 */
public class StrangeMoodGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos STAND = new BlockPos(8, 2, 8);
	private static final BlockPos CHEST = new BlockPos(10, 2, 8);
	private static final List<String> POOL = List.of("minecraft:diamond", "minecraft:blaze_rod", "minecraft:echo_shard", "minecraft:amethyst_shard",
		"minecraft:breeze_rod");
	private static final String NS = "aliveworkplace_test";

	/** A Rare cleric Legend inspired in Master clerics, whose Masterwork is an iron axe; {@code conditions} as JSON (may be empty). */
	static Legend legend(String name, String conditions) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath(NS, name), JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:cleric\","
			+ " \"title\": \"" + name + "\", \"lore\": \"l\", \"conditions\": [" + conditions + "],"
			+ " \"arrive\": [{\"way\": \"inspired\", \"trades\": [\"minecraft:cleric\"], \"chance\": 0.125}],"
			+ " \"masterwork\": {\"item\": \"minecraft:iron_axe\", \"materials\": [\"" + String.join("\", \"", POOL) + "\"]}}").getAsJsonObject());
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, helper.getBounds().inflate(4))) {
				f.discard();
			}
			VillageRaids.forget(helper.absolutePos(HALL));
			LegendPowers.forget();
		});
	}

	/** Runs {@code body} with strange moods on, moods off and only {@code legends} loaded, all put back within the tick. */
	private static void staged(GameTestHelper helper, List<Legend> legends, Runnable body) {
		boolean moods = Moods.ENABLED;
		boolean strange = StrangeMoods.ENABLED;
		try {
			StrangeMoods.ENABLED = true;
			Moods.ENABLED = false;
			Map<ResourceLocation, Legend> map = new java.util.LinkedHashMap<>();
			legends.forEach(l -> map.put(l.id(), l));
			Legends.setForTest(map);
			body.run();
		} finally {
			Moods.ENABLED = moods;
			StrangeMoods.ENABLED = strange;
			Moods.forget();
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	/** A still villager of {@code job} at {@code level}, working at a brewing stand at {@code station}. */
	private static Villager worker(GameTestHelper helper, BlockPos station, String name, VillagerProfession job, int lvl) {
		helper.setBlock(station, Blocks.BREWING_STAND);
		Villager v = helper.spawn(EntityType.VILLAGER, station.south());
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		v.setCustomName(Component.literal(name));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(station)));
		return v;
	}

	private static void wellbeing(GameTestHelper helper, float wellbeing) {
		hall(helper).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, wellbeing));
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static List<String> chronicle(GameTestHelper helper) {
		return hall(helper).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND).map(e -> e.text().getString()).toList();
	}

	/** A fixed seed whose first float is under (yes) or over (no) a 1-in-8 chance. */
	private static long seed(boolean yes) {
		for (long s = 1; ; s++) {
			if (RandomSource.create(s).nextFloat() < 0.125f == yes) {
				return s;
			}
		}
	}

	private static Item item(ResourceLocation id) {
		return BuiltInRegistries.ITEM.get(id);
	}

	private static Container chest(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.CHEST);
		return (Container) helper.getBlockEntity(CHEST);
	}

	/**
	 * The roll: an unhappy village, an Expert cleric, a Master mason, the Legend's conditions unmet, its slot taken, a
	 * raid, a festival and the switch off all give no mood; then a happy village with a Master cleric and a losing die
	 * gives none, and a winning die seizes her: the attachment, three different materials from the pool, her
	 * workstation claimed, the purple line and the chronicle. A second mood can't start while hers is on.
	 */
	//$ gametest_ticks_batch AREA '100' '"strangeMoodStart"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "strangeMoodStart")
	public void aMoodStartsOnlyInAHappyVillageWithAMasterOfANamedTrade(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("mood_cleric", "");
		Legend picky = legend("picky_cleric", "{\"type\": \"villagers\", \"count\": 60}");
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			long today = 5;
			long yes = seed(true);
			long no = seed(false);
			Villager ada = worker(helper, STAND, "Ada", VillagerProfession.CLERIC, 5);
			Villager mason = worker(helper, new BlockPos(13, 2, 13), "Tom", VillagerProfession.MASON, 5);

			wellbeing(helper, 0.3f);
			helper.assertTrue("not a happy village".equals(StrangeMoods.blocked(level, hall, today)), "unhappy: " + StrangeMoods.blocked(level, hall, today));
			helper.assertTrue(StrangeMoods.roll(level, hall, today, RandomSource.create(yes)).isEmpty(), "a mood in an unhappy village");
			wellbeing(helper, 0.9f);
			helper.assertTrue(StrangeMoods.blocked(level, hall, today) == null, "a happy village is blocked: " + StrangeMoods.blocked(level, hall, today));

			ada.setVillagerData(ada.getVillagerData().setLevel(4));
			helper.assertTrue(StrangeMoods.candidates(level, hall).isEmpty() && StrangeMoods.roll(level, hall, today, RandomSource.create(yes)).isEmpty(),
				"an Expert cleric (or the Master mason) was seized");
			ada.setVillagerData(ada.getVillagerData().setLevel(5));
			List<StrangeMoods.Candidate> open = StrangeMoods.candidates(level, hall);
			helper.assertTrue(open.size() == 1 && open.get(0).masters().equals(List.of(ada)) && open.get(0).chance() == 0.125f, "candidates: " + open);

			Legends.setForTest(Map.of(picky.id(), picky));
			helper.assertTrue(StrangeMoods.candidates(level, hall).isEmpty(), "a Legend whose conditions aren't met");
			Villager other = worker(helper, new BlockPos(13, 2, 4), "Rue", VillagerProfession.CLERIC, 5);
			Legends.setForTest(Map.of(smith.id(), smith));
			Legends.make(level, other, smith, "test");
			helper.assertTrue(StrangeMoods.candidates(level, hall).isEmpty(), "a Rare Legend whose slot is taken");
			Legends.clear(other);
			other.discard();
			helper.assertTrue(StrangeMoods.candidates(level, hall).size() == 1, "the slot is free again");

			VillageRaids.track(level, hall, 1);
			helper.assertTrue("a raid".equals(StrangeMoods.blocked(level, hall, today))
				&& StrangeMoods.roll(level, hall, today, RandomSource.create(yes)).isEmpty(), "a mood during a raid");
			VillageRaids.forget(hall);
			long time = level.getDayTime();
			long festival = hall(helper).festivalDay();
			try {
				level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 10000);
				hall(helper).setFestivalDay(Chronicle.day(level));
				helper.assertTrue("a festival".equals(StrangeMoods.blocked(level, hall, today)), "a festival: " + StrangeMoods.blocked(level, hall, today));
			} finally {
				level.setDayTime(time);
				hall(helper).setFestivalDay(festival);
			}
			StrangeMoods.ENABLED = false;
			helper.assertTrue(StrangeMoods.roll(level, hall, today, RandomSource.create(yes)).isEmpty() && "off".equals(StrangeMoods.blocked(level, hall, today)),
				"a mood with strangeMoods off");
			StrangeMoods.ENABLED = true;

			helper.assertTrue(StrangeMoods.roll(level, hall, today, RandomSource.create(no)).isEmpty(), "the 7-in-8 die seized her");
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada), "a lost roll left a mood");
			Optional<Villager> seized = StrangeMoods.roll(level, hall, today, RandomSource.create(yes));
			helper.assertTrue(seized.equals(Optional.of(ada)), "the winning die: " + seized);
			StrangeMood mood = ModAttachments.STRANGE_MOOD.get(ada);
			helper.assertTrue(mood != null && mood.inMood() && mood.legend().equals(smith.id()) && mood.station().equals(helper.absolutePos(STAND))
				&& mood.hall().equals(Optional.of(hall)) && mood.started() == today && mood.lastDay() == today + 2, "the mood: " + mood);
			Set<ResourceLocation> asked = new HashSet<>(mood.materials());
			helper.assertTrue(asked.size() == 3 && mood.materials().stream().allMatch(m -> POOL.contains(m.toString())), "the materials: " + mood.materials());
			helper.assertTrue(StrangeMoods.claiming(ada) && helper.absolutePos(STAND).equals(StrangeMoods.station(ada)), "her workstation isn't claimed");
			helper.assertTrue(!StrangeMoods.claiming(mason), "the mason claims his");
			Component over = StrangeMoods.overhead(mood);
			helper.assertTrue(over.getString().equals("Taken by a strange mood") && TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE).equals(over.getStyle().getColor()),
				"the line over her head: " + over.getString() + " " + over.getStyle().getColor());
			String wants = StrangeMoods.materials(mood).getString();
			helper.assertTrue(chronicle(helper).contains("Ada was taken by a strange mood and asked for " + wants), "chronicle: " + chronicle(helper));
			helper.assertTrue(StrangeMoods.line(level, mood, today).getString().equals("Wants " + wants + " in the chest, 3 days left"),
				"her second line: " + StrangeMoods.line(level, mood, today).getString());

			helper.assertTrue("a mood already on".equals(StrangeMoods.blocked(level, hall, today)), "a second mood: " + StrangeMoods.blocked(level, hall, today));
			helper.succeed();
		}));
	}

	/** The three materials go on the board (the hall's and the Storehouse's requests); one put in the chest comes off it. */
	//$ gametest_ticks_batch AREA '100' '"strangeMoodBoard"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "strangeMoodBoard")
	public void theThreeMaterialsAreOnTheBoard(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("board_cleric", "");
		Leftovers.village(helper, 32);
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = worker(helper, STAND, "Ada", VillagerProfession.CLERIC, 5);
			Container chest = chest(helper);
			StrangeMood mood = StrangeMoods.start(level, hall, ada, smith, 3, RandomSource.create(4L));
			List<Requests.Request> wanted = Requests.of(level, ada);
			Set<Item> asked = new HashSet<>();
			mood.materials().forEach(m -> asked.add(item(m)));
			helper.assertTrue(wanted.size() == 3 && wanted.stream().allMatch(r -> asked.contains(r.item()) && r.count() == 1
				&& r.station().equals(helper.absolutePos(STAND))), "her requests: " + wanted.stream().map(r -> r.what().getString()).toList());
			// a cleric isn't one of the board's usual trades: the mood puts her on it
			List<Requests.Request> board = Requests.near(level, helper.absolutePos(STAND), null);
			helper.assertTrue(board.stream().filter(r -> r.worker() == ada).count() == 3, "the Storehouse board: " + board.size());
			helper.assertTrue(VillageHalls.census(level, hall).requests().stream().filter(r -> r.worker() == ada).count() == 3, "the hall's list");
			chest.setItem(0, new ItemStack(item(mood.materials().get(0))));
			wanted = Requests.of(level, ada);
			helper.assertTrue(wanted.size() == 2 && wanted.stream().noneMatch(r -> r.item() == item(mood.materials().get(0))), "after one came: " + wanted.size());
			helper.succeed();
		}));
	}

	/**
	 * Success: two materials handed over by Jo at the board and one put in by Max beside the chest. Ada makes the
	 * Masterwork: a made-up name, lore naming her, the village, the day and the materials, a glint; it goes to Jo, who
	 * brought the most; the materials are used; Ada is the Legend (way "inspired", on the record, in the chronicle); a
	 * Masterwork in an item frame in the village is 3 beauty.
	 */
	//$ gametest_ticks_batch AREA '100' '"strangeMoodSuccess"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "strangeMoodSuccess")
	public void successMakesTheMasterworkAndTheLegend(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("success_cleric", "");
		ServerPlayer jo = helper.makeMockServerPlayerInLevel();
		ServerPlayer max = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			helper.getLevel().getServer().getPlayerList().remove(jo);
			helper.getLevel().getServer().getPlayerList().remove(max);
		});
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			long today = 4;
			jo.teleportTo(hall.getX() + 40.5, hall.getY(), hall.getZ() + 40.5); // far off: at the Storehouse
			BlockPos chestAt = helper.absolutePos(CHEST);
			max.teleportTo(chestAt.getX() + 1.5, chestAt.getY(), chestAt.getZ() + 0.5);
			Villager ada = worker(helper, STAND, "Ada", VillagerProfession.CLERIC, 5);
			Container chest = chest(helper);
			StrangeMood mood = StrangeMoods.start(level, hall, ada, smith, today, RandomSource.create(9L));
			int before = Decorations.beauty(level, hall);
			List<Item> mats = mood.materials().stream().map(StrangeMoodGameTests::item).toList();
			// Jo hands two over at the board (the board puts them in the chest and says who), Max puts the third in himself
			for (int i = 0; i < 2; i++) {
				chest.setItem(i, new ItemStack(mats.get(i)));
				Requests.Request r = Requests.forItem(ada, helper.absolutePos(STAND), mats.get(i), 1);
				Requests.given(jo, r, 1);
			}
			helper.assertTrue(StrangeMoods.check(level, ada, today).isEmpty(), "made with two materials");
			chest.setItem(2, new ItemStack(mats.get(2)));
			ItemStack work = StrangeMoods.check(level, ada, today + 1);
			helper.assertTrue(!work.isEmpty() && work.is(Items.IRON_AXE) && StrangeMoods.isMasterwork(work), "no Masterwork: " + work);
			String name = work.getHoverName().getString();
			String village = VillageHalls.name(level, hall).getString();
			List<String> words = new java.util.ArrayList<>();
			for (int i = 0; i < StrangeMoods.WORDS; i++) {
				words.add(Language.getInstance().getOrDefault("masterwork.aliveworkplace.word." + i));
			}
			helper.assertTrue(name.startsWith("The ") && name.endsWith(" Iron Axe") && words.contains(name.substring(4, name.length() - " Iron Axe".length())),
				"the name: " + name);
			List<String> lore = work.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
			String from = "From " + mats.get(0).getDescription().getString() + ", " + mats.get(1).getDescription().getString() + " and "
				+ mats.get(2).getDescription().getString();
			helper.assertTrue(lore.equals(List.of("Made by Ada of " + village, "On day " + (today + 1) + " of the chronicle", from)), "the lore: " + lore);
			helper.assertTrue(Boolean.TRUE.equals(work.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "no glint");
			helper.assertTrue(jo.getInventory().countItem(Items.IRON_AXE) == 1 && max.getInventory().countItem(Items.IRON_AXE) == 0,
				"the Masterwork didn't go to Jo, who brought two");
			helper.assertTrue(mats.stream().allMatch(m -> chest.countItem(m) == 0) && chest.countItem(Items.IRON_AXE) == 0, "the materials weren't used");
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada) && !StrangeMoods.claiming(ada), "the mood is still on");
			LegendData data = ModAttachments.LEGEND.get(ada);
			helper.assertTrue(data != null && data.id().equals(smith.id()) && data.way().equals("inspired") && data.settled(), "not the Legend: " + data);
			helper.assertTrue(LegendRecord.get(level).entry(ada.getUUID()).isPresent(), "not on the record");
			List<String> lines = chronicle(helper);
			helper.assertTrue(lines.contains("Ada made a Masterwork, " + name) && lines.stream().anyMatch(l -> l.startsWith("Ada") && l.contains("success_cleric")),
				"chronicle: " + lines);

			ItemFrame frame = new ItemFrame(level, helper.absolutePos(new BlockPos(5, 3, 1)), net.minecraft.core.Direction.SOUTH);
			frame.setItem(work.copy(), false);
			level.addFreshEntity(frame);
			ItemFrame plain = new ItemFrame(level, helper.absolutePos(new BlockPos(6, 3, 1)), net.minecraft.core.Direction.SOUTH);
			plain.setItem(new ItemStack(Items.IRON_AXE), false);
			level.addFreshEntity(plain);
			helper.assertTrue(Decorations.beauty(level, hall) == before + StrangeMoods.BEAUTY, "beauty " + before + " -> " + Decorations.beauty(level, hall));
			helper.succeed();
		}));
	}

	/**
	 * Failure: nothing came by the third day. Ada sulks a week (30 less mood, half pace, "Sulking" over her head, her
	 * workstation hers again), the village has no strange mood for 10 days, and the sulk ends on its day.
	 */
	//$ gametest_ticks_batch AREA '100' '"strangeMoodFailure"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "strangeMoodFailure")
	public void failureSulksAWeekAndBlocksMoodsForTenDays(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("failure_cleric", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			long today = Chronicle.day(level);
			wellbeing(helper, 0.9f);
			Villager ada = worker(helper, STAND, "Ada", VillagerProfession.CLERIC, 5);
			chest(helper);
			StrangeMoods.start(level, hall, ada, smith, today - 3, RandomSource.create(2L));
			helper.assertTrue(StrangeMoods.check(level, ada, today - 1).isEmpty() && ModAttachments.STRANGE_MOOD.get(ada).inMood(), "failed on its last day");
			StrangeMoods.check(level, ada, today);
			StrangeMood sulk = ModAttachments.STRANGE_MOOD.get(ada);
			helper.assertTrue(sulk != null && !sulk.inMood() && sulk.sulkUntil() == today + StrangeMoods.SULK_DAYS, "the sulk: " + sulk);
			helper.assertTrue(hall(helper).noMoodUntil() == today + StrangeMoods.NO_MOOD_DAYS, "no mood until " + hall(helper).noMoodUntil());
			helper.assertTrue(StrangeMoods.sulking(ada) && !StrangeMoods.claiming(ada), "sulking " + StrangeMoods.sulking(ada) + ", claiming " + StrangeMoods.claiming(ada));
			helper.assertTrue(StrangeMoods.overhead(sulk).getString().equals("Sulking")
				&& StrangeMoods.line(level, sulk, today).getString().equals("Back to work in 7 days"), "the line over her head: " + StrangeMoods.overhead(sulk).getString());
			Moods.ENABLED = true;
			Moods.forget();
			Moods.Mood mood = Moods.work(level, ada);
			helper.assertTrue(mood.bad().stream().anyMatch(c -> c.getString().equals("sulking after a strange mood")), "no sulking reason: " + mood.bad());
			helper.assertTrue(StrangeMoods.sulkMood(level, ada).points() == -StrangeMoods.SULK_MOOD, "the sulk's mood");
			Moods.ENABLED = false;
			helper.assertTrue(StrangeMoods.SULKING.factor().of(ada) == StrangeMoods.SULK_PACE
				&& Pace.of(ada).slower().stream().anyMatch(p -> p.source() == StrangeMoods.SULKING), "half pace: " + Pace.of(ada).slower());
			helper.assertTrue(chronicle(helper).contains("Ada's strange mood passed with nothing made"), "chronicle: " + chronicle(helper));

			Villager bo = worker(helper, new BlockPos(13, 2, 13), "Bo", VillagerProfession.CLERIC, 5);
			helper.assertTrue(StrangeMoods.blocked(level, hall, today + 9) != null && StrangeMoods.blocked(level, hall, today + 9).startsWith("no mood till")
				&& StrangeMoods.roll(level, hall, today + 9, RandomSource.create(seed(true))).isEmpty(), "a mood within 10 days");
			helper.assertTrue(StrangeMoods.blocked(level, hall, today + 10) == null, "still blocked after 10 days: " + StrangeMoods.blocked(level, hall, today + 10));
			List<StrangeMoods.Candidate> open = StrangeMoods.candidates(level, hall);
			helper.assertTrue(open.size() == 1 && open.get(0).masters().equals(List.of(bo)), "a sulking Master may be seized: " + open);

			StrangeMoods.check(level, ada, today + 6);
			helper.assertTrue(ModAttachments.STRANGE_MOOD.has(ada), "the sulk ended early");
			StrangeMoods.check(level, ada, today + 7);
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(ada) && !StrangeMoods.sulking(ada), "the sulk never ended");
			helper.succeed();
		}));
	}

	/**
	 * A mood saved and loaded halfway (one material in the chest) comes back the same and finishes: with nobody to give
	 * it to, the Masterwork goes into the chest. The hall's mood fields are saved, and an old hall loads them as 0. With
	 * the switch off, a mood still on is called off at its next check.
	 */
	//$ gametest_ticks_batch AREA '100' '"strangeMoodSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "strangeMoodSave")
	public void aMoodSurvivesSaveAndReloadHalfway(GameTestHelper helper) {
		setUp(helper);
		Legend smith = legend("saved_cleric", "");
		helper.setBlock(new BlockPos(16, 2, 16), ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> staged(helper, List.of(smith), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = worker(helper, STAND, "Ada", VillagerProfession.CLERIC, 5);
			Container chest = chest(helper);
			StrangeMood mood = StrangeMoods.start(level, hall, ada, smith, 6, RandomSource.create(7L));
			List<Item> mats = mood.materials().stream().map(StrangeMoodGameTests::item).toList();
			chest.setItem(0, new ItemStack(mats.get(0)));
			StrangeMoods.check(level, ada, 7);
			StrangeMood half = ModAttachments.STRANGE_MOOD.get(ada);
			helper.assertTrue(StrangeMoods.found(level, half) == 1, "one material counted: " + half);

			CompoundTag tag = ada.saveWithoutId(new CompoundTag());
			ada.discard();
			Villager loaded = EntityType.VILLAGER.create(level);
			loaded.load(tag);
			helper.assertTrue(half.equals(ModAttachments.STRANGE_MOOD.get(loaded)), "after reload: " + ModAttachments.STRANGE_MOOD.get(loaded) + " vs " + half);
			level.addFreshEntity(loaded);
			helper.assertTrue(StrangeMoods.claiming(loaded) && Requests.of(level, loaded).size() == 2, "the reloaded mood's wants");
			chest.setItem(1, new ItemStack(mats.get(1)));
			chest.setItem(2, new ItemStack(mats.get(2)));
			ItemStack work = StrangeMoods.check(level, loaded, 8);
			helper.assertTrue(StrangeMoods.isMasterwork(work) && chest.countItem(Items.IRON_AXE) == 1, "the Masterwork isn't in the chest");
			helper.assertTrue(Legends.of(loaded).map(l -> l.id().equals(smith.id())).orElse(false), "the reloaded villager isn't the Legend");

			// an old attachment with only the Legend and the workstation reads with defaults
			CompoundTag old = new CompoundTag();
			old.putString("legend", smith.id().toString());
			old.put("station", net.minecraft.nbt.NbtUtils.writeBlockPos(BlockPos.ZERO));
			StrangeMood bare = StrangeMood.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).result().orElse(null);
			helper.assertTrue(bare != null && bare.inMood() && bare.materials().isEmpty() && bare.givers().isEmpty(), "an old mood: " + bare);

			var registries = level.registryAccess();
			hall(helper).setNoMoodUntil(19);
			hall(helper).setMoodRolledDay(8);
			VillageHallBlockEntity other = (VillageHallBlockEntity) helper.getBlockEntity(new BlockPos(16, 2, 16));
			CompoundTag hallTag = hall(helper).saveWithoutMetadata(registries);
			other.loadWithComponents(hallTag, registries);
			helper.assertTrue(other.noMoodUntil() == 19 && other.moodRolledDay() == 8, "the hall's fields: " + other.noMoodUntil() + ", " + other.moodRolledDay());
			hallTag.remove("noMoodUntil");
			hallTag.remove("moodRolledDay");
			other.loadWithComponents(hallTag, registries);
			helper.assertTrue(other.noMoodUntil() == 0 && other.moodRolledDay() == 0, "an old hall");
			helper.setBlock(new BlockPos(16, 2, 16), Blocks.AIR);

			Villager bo = worker(helper, new BlockPos(13, 2, 13), "Bo", VillagerProfession.CLERIC, 5);
			Legends.clear(loaded);
			StrangeMoods.start(level, hall, bo, smith, 9, RandomSource.create(1L));
			StrangeMoods.ENABLED = false;
			helper.assertTrue(!StrangeMoods.claiming(bo) && Requests.of(level, bo).isEmpty(), "a mood claims with the switch off");
			StrangeMoods.check(level, bo, 9);
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(bo), "a mood not called off with the switch off");
			helper.succeed();
		}));
	}

	/** Every sentence a player reads from strange moods is in the lang file and reads as written. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyStrangeMoodSentenceReads(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> keys = new java.util.ArrayList<>(List.of("legend.aliveworkplace.overhead.strange_mood", "legend.aliveworkplace.overhead.mood_wants",
			"legend.aliveworkplace.overhead.sulking", "legend.aliveworkplace.overhead.sulk_days", "message.aliveworkplace.strange_mood.start",
			"message.aliveworkplace.strange_mood.masterwork.to", "message.aliveworkplace.strange_mood.masterwork.chest",
			"message.aliveworkplace.strange_mood.failed", "chronicle.aliveworkplace.strange_mood.start", "chronicle.aliveworkplace.strange_mood.masterwork",
			"chronicle.aliveworkplace.strange_mood.failed", "masterwork.aliveworkplace.name", "masterwork.aliveworkplace.lore.maker",
			"masterwork.aliveworkplace.lore.day", "masterwork.aliveworkplace.lore.materials", "mood.aliveworkplace.reason.sulking",
			"pace.aliveworkplace.source.sulking", "aliveworkplace.config.strangeMoods", "aliveworkplace.config.strangeMoods.tooltip"));
		for (int i = 0; i < StrangeMoods.WORDS; i++) {
			keys.add("masterwork.aliveworkplace.word." + i);
		}
		List<String> missing = keys.stream().filter(k -> !lang.has(k)).toList();
		helper.assertTrue(missing.isEmpty(), "missing: " + missing);
		String start = Component.translatable("message.aliveworkplace.strange_mood.start", "Ada", "Thornholm", "Diamond, Blaze Rod and Echo Shard", 3).getString();
		helper.assertTrue(start.equals("Ada of Thornholm has been taken by a strange mood! They want Diamond, Blaze Rod and Echo Shard, one of each, in a chest by their workstation within 3 days."),
			start);
		String to = Component.translatable("message.aliveworkplace.strange_mood.masterwork.to", "Ada", "The Ember Iron Axe", "Jo").getString();
		helper.assertTrue(to.equals("Ada has made a Masterwork, The Ember Iron Axe, and given it to Jo, who brought the most."), to);
		String chest = Component.translatable("message.aliveworkplace.strange_mood.masterwork.chest", "Ada", "The Ember Iron Axe").getString();
		helper.assertTrue(chest.equals("Ada has made a Masterwork, The Ember Iron Axe, and left it in the chest by their workstation."), chest);
		String failed = Component.translatable("message.aliveworkplace.strange_mood.failed", "Ada", 7).getString();
		helper.assertTrue(failed.equals("Ada's strange mood passed with nothing made. They will sulk for 7 days."), failed);
		String named = Component.translatable("masterwork.aliveworkplace.name", "Thornholm", "Ada", "Ember", "Ladle").getString();
		helper.assertTrue(named.equals("The Ember Ladle"), named);
		helper.succeed();
	}
}
