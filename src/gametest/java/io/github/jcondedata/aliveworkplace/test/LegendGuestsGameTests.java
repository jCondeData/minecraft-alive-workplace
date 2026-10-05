package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendText;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * 29.8, Legends who visit: no guest before the village qualifies or while the slot is taken, never two at once, one at
 * each place (inn, market, festival, chapel, hall) through each place's own code, settling the round every need is met
 * and not before, leaving on the third evening out of sight and not coming back for 7 days, 1 in 4 by default, the
 * terms screen's lines against the needs, and a guest saved and loaded mid-stay. Every roster and clock change is made
 * and undone within one tick ({@link #staged}), as the tests in other batches run beside these.
 */
public class LegendGuestsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final String NS = "aliveworkplace_test";
	private static final long DAY = VillageNeeds.DAY;

	private static ResourceLocation id(String name) {
		return ResourceLocation.fromNamespaceAndPath(NS, name);
	}

	/** A Rare Mason Legend who visits {@code place} ({@code extra}: more fields of the way), with {@code more} fields of the file. */
	private static Legend legend(String name, String place, String extra, String more) {
		return Legends.read(id(name), JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"" + name + "\","
			+ " \"lore\": \"the lore\", \"names\": [\"Ada of " + name + "\"], \"arrive\": [{\"way\": \"visit\", \"place\": \"" + place + "\"" + extra + "}],"
			+ " \"powers\": [{\"type\": \"pace\", \"trades\": [\"minecraft:farmer\"], \"factor\": 2.0}]" + more + "}").getAsJsonObject());
	}

	private static Legend sure(String name, String place) {
		return legend(name, place, ", \"chance\": 1.0", "");
	}

	/** The hall, and when the test ends the Legends and guests it made (with their record entries) and the buildings it recorded go. */
	private static void setUp(GameTestHelper helper, List<BlueprintData.Placement> recorded) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			recorded.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
	}

	/**
	 * Runs {@code body} with only {@code legends} loaded (in order), moods off and needs on, then puts the switches, the
	 * clock and the real roster back before the tick ends.
	 */
	private static void staged(GameTestHelper helper, List<Legend> legends, Runnable body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		long time = level.getDayTime();
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = true;
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			legends.forEach(l -> map.put(l.id(), l));
			Legends.setForTest(map);
			body.run();
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	/** Sets how the village is doing (moods are off in {@link #staged}: a happy village is a wellbeing of 0.6 or more). */
	private static void wellbeing(GameTestHelper helper, float wellbeing) {
		hall(helper).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, wellbeing));
	}

	private static List<String> chronicle(GameTestHelper helper) {
		return hall(helper).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND).map(e -> e.text().getString()).toList();
	}

	/** Every Legend guest villager round the test. */
	private static List<Villager> guests(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> v.isAlive() && LegendGuests.isGuest(v));
	}

	/** A finished building of {@code size} named {@code path} (its tier goes by the name) at {@code origin} in the test area. */
	private static BlueprintData.Placement building(GameTestHelper helper, ResourceLocation id, BlockPos origin, Vec3i size,
													List<BlueprintData.Placement> recorded) {
		ServerLevel level = helper.getLevel();
		if (id.getNamespace().equals(NS)) {
			level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin).above(60), size, false, Blocks.AIR);
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		recorded.add(placement);
		return placement;
	}

	private static void bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	private static void assertGuest(GameTestHelper helper, Villager guest, Legend legend, String place, BlockPos near) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(guest != null, "no guest at the " + place);
		LegendData data = ModAttachments.LEGEND.get(guest);
		helper.assertTrue(data != null && data.guest() && data.id().equals(legend.id()) && data.way().equals("visit:" + place),
			"the " + place + "'s guest: " + data);
		helper.assertTrue(data.lastDay() == Chronicle.day(level) + LegendGuests.STAY_DAYS - 1, "stays 3 days: last day " + data.lastDay());
		helper.assertTrue(guest.getVillagerData().getProfession() == VillagerProfession.NITWIT, "a guest took a job: " + guest.getVillagerData());
		helper.assertTrue(guest.blockPosition().closerThan(near, 9), "the " + place + "'s guest stands at " + guest.blockPosition() + ", not by " + near);
		helper.assertTrue(LegendGuests.guest(level, helper.absolutePos(HALL)) == guest, "the hall doesn't know its guest");
		helper.assertTrue(chronicle(helper).stream().anyMatch(l -> l.contains("came to the village as a guest")), "not announced: " + chronicle(helper));
	}

	/** No guest before the conditions are met, nor while their slot is taken; the day the village qualifies, they come. */
	//$ gametest_ticks_batch AREA '100' '"legendGuestsWhen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendGuestsWhen")
	public void noGuestBeforeTheVillageQualifies(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend moon = legend("moon_mason", "hall", ", \"chance\": 1.0", ", \"conditions\": [{\"type\": \"full_moon\"}]");
		helper.runAfterDelay(2, () -> staged(helper, List.of(moon), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			RandomSource random = RandomSource.create(1L);
			at(level, 2, 1000); // day 2: not a full moon
			helper.assertTrue(LegendGuests.candidates(level, hall, "hall", 2).isEmpty(), "a candidate before the full moon");
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, random) == null, "a guest before the conditions are met");
			helper.assertTrue(!hall(helper).legendGuests().rolled().containsKey("hall"), "a day's roll spent with nobody who could come");
			at(level, 9, 1000); // day 9: a full moon
			Villager holder = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 15));
			holder.setNoAi(true);
			Legends.make(level, holder, moon, "test"); // the Rare slot is taken
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, random) == null, "a guest while the slot is taken");
			Legends.clear(holder);
			Villager guest = LegendGuests.visit(level, hall, "hall", hall, random);
			assertGuest(helper, guest, moon, "hall", hall);
			helper.succeed();
		}));
	}

	/** Never two guests at once: a second Legend who may come waits until the first has gone (and the first waits 7 days). */
	//$ gametest_ticks_batch AREA '100' '"legendGuestsOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendGuestsOne")
	public void neverTwoGuestsAtOnce(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend first = sure("first_mason", "hall");
		Legend second = sure("second_mason", "hall");
		helper.runAfterDelay(2, () -> staged(helper, List.of(first, second), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			RandomSource random = RandomSource.create(2L);
			at(level, 1, 1000);
			Villager a = LegendGuests.visit(level, hall, "hall", hall, random);
			assertGuest(helper, a, first, "hall", hall);
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, random) == null, "a second roll the same day");
			at(level, 2, 1000);
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, random) == null, "a second guest while the first stays");
			helper.assertTrue(guests(helper).size() == 1, "guests at once: " + guests(helper).size());
			LegendGuests.leave(level, hall, a);
			at(level, 3, 1000);
			Villager b = LegendGuests.visit(level, hall, "hall", hall, random);
			assertGuest(helper, b, second, "hall", hall);
			helper.assertTrue(guests(helper).size() == 1, "guests at once: " + guests(helper).size());
			helper.succeed();
		}));
	}

	/** One at each place, each through its own code: the inn's morning, market day, a festival's fireworks, the Chapel's full-moon midnight, the hall's morning. */
	//$ gametest_ticks_batch AREA '100' '"legendGuestsPlaces"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendGuestsPlaces")
	public void oneAtEachPlace(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend inn = sure("inn_mason", "inn");
		Legend market = sure("market_mason", "market");
		Legend festival = legend("festival_mason", "festival", ", \"chance\": 1.0, \"crowd\": 3", "");
		Legend crowded = legend("crowded_mason", "festival", ", \"chance\": 1.0, \"crowd\": 40", "");
		Legend chapel = sure("chapel_mason", "chapel");
		Legend morning = sure("hall_mason", "hall");
		helper.setBlock(new BlockPos(11, 2, 11), ModBlocks.INN_COUNTER);
		helper.setBlock(new BlockPos(3, 2, 9), Blocks.BELL); // the festival's square
		bed(helper, new BlockPos(17, 2, 17));
		helper.runAfterDelay(2, () -> staged(helper, List.of(inn, market, crowded, festival, chapel, morning), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hall(helper);
			// The inn, in the morning, instead of the day's traveller.
			at(level, 1, 1000);
			BlockPos counter = helper.absolutePos(new BlockPos(11, 2, 11));
			Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
			innkeeper.setNoAi(true);
			Jobs.employ(level, innkeeper, counter, ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
			helper.assertTrue("hosting".equals(Innkeepers.tend(level, innkeeper, counter)), "the inn isn't hosting");
			Villager guest = LegendGuests.guest(level, hall);
			assertGuest(helper, guest, inn, "inn", counter);
			helper.assertTrue(Innkeepers.guests(level, counter).isEmpty(), "a traveller came as well as the Legend");
			helper.assertTrue(ModAttachments.GUESTS_HOSTED.getOrElse(innkeeper, 0) == 1, "the innkeeper didn't host the Legend");
			Innkeepers.tend(level, innkeeper, counter);
			helper.assertTrue(Innkeepers.guests(level, counter).isEmpty(), "a traveller came later the same morning");
			LegendGuests.leave(level, hall, guest);

			// Market day: with the traders.
			BlockPos square = helper.absolutePos(new BlockPos(8, 2, 16));
			List<WanderingTrader> traders = MarketDays.hold(level, hall, square);
			helper.assertTrue(!traders.isEmpty(), "no traders came");
			traders.forEach(WanderingTrader::discard);
			guest = LegendGuests.guest(level, hall);
			assertGuest(helper, guest, market, "market", square);
			LegendGuests.leave(level, hall, guest);

			// A festival's fireworks: the crowd is counted and kept on the hall; one who wants 40 doesn't come for 4.
			at(level, 1, Festivals.FIREWORKS);
			BlockPos fest = Festivals.square(level, hall);
			for (int i = 0; i < 3; i++) {
				EntityType.VILLAGER.spawn(level, fest.offset(i - 1, 0, 1), net.minecraft.world.entity.MobSpawnType.COMMAND).setNoAi(true);
			}
			guest = Festivals.fireworks(level, hall, entity, fest, RandomSource.create(3L));
			helper.assertTrue(entity.festivalCrowd() >= 3, "the crowd at the fireworks: " + entity.festivalCrowd());
			assertGuest(helper, guest, festival, "festival", fest);
			LegendGuests.leave(level, hall, guest);

			// The Chapel, at midnight under a full moon (day 1 is one).
			building(helper, StarterBlueprints.CHAPEL.id(), new BlockPos(0, 1, 0), StarterBlueprints.CHAPEL.size(), recorded);
			BlockPos chapelAt = LegendGuests.chapel(level, hall).orElseThrow();
			at(level, 1, 14000);
			LegendGuests.round(level, hall);
			helper.assertTrue(LegendGuests.guest(level, hall) == null, "a Chapel guest before midnight");
			at(level, 1, 18000);
			helper.assertTrue(level.getMoonPhase() == 0, "not a full moon: " + level.getMoonPhase());
			LegendGuests.round(level, hall);
			guest = LegendGuests.guest(level, hall);
			assertGuest(helper, guest, chapel, "chapel", chapelAt);
			LegendGuests.leave(level, hall, guest);

			// The hall, in the morning (not in the afternoon).
			at(level, 2, 7000);
			LegendGuests.round(level, hall);
			helper.assertTrue(LegendGuests.guest(level, hall) == null, "a hall guest in the afternoon");
			at(level, 3, 1000);
			LegendGuests.round(level, hall);
			assertGuest(helper, LegendGuests.guest(level, hall), morning, "hall", hall);
			helper.assertTrue(entity.legendGuests().lastVisit(crowded.id()).isEmpty(), "the Legend who wants a crowd of 40 came to one of 3");
			helper.succeed();
		}));
	}

	/** They settle the round every need is met, and not before: the bed in the tier III home, a Master's trade and workstation, the record and the chronicle. */
	//$ gametest_ticks_batch AREA '100' '"legendGuestsSettle"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendGuestsSettle")
	public void theySettleWhenEveryNeedIsMet(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend wine = legend("wine_mason", "hall", "", ", \"needs\": {\"luxury\": \"wine\"}");
		helper.setBlock(new BlockPos(16, 2, 4), Blocks.STONECUTTER);
		helper.runAfterDelay(2, () -> staged(helper, List.of(wine), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 4, 2000);
			wellbeing(helper, 0.2f);
			Villager guest = LegendGuests.come(level, hall, wine, "hall", hall, RandomSource.create(4L));
			helper.assertTrue(guest != null, "the guest didn't come");
			LegendGuests.tend(level, hall);
			LegendData data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(data.guest() && data.unmet().keySet().equals(java.util.Set.of(LegendText.HOME, LegendText.LUXURY, LegendText.HAPPY)),
				"with nothing ready: " + data);
			// A tier II home is no home of their own.
			building(helper, id("guest_cottage_2"), new BlockPos(13, 2, 13), new Vec3i(6, 5, 6), recorded);
			bed(helper, new BlockPos(15, 2, 15));
			LegendGuests.tend(level, hall);
			helper.assertTrue(!LegendText.met(ModAttachments.LEGEND.get(guest), LegendText.HOME), "a tier II home counts");
			// A tier III home, with someone else's bed in it, isn't theirs either.
			building(helper, id("guest_manor_3"), new BlockPos(6, 2, 6), new Vec3i(6, 5, 6), recorded);
			bed(helper, new BlockPos(8, 2, 8));
			bed(helper, new BlockPos(10, 2, 8));
			Villager lodger = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 18));
			lodger.setNoAi(true);
			lodger.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(10, 2, 9))));
			LegendGuests.tend(level, hall);
			helper.assertTrue(!LegendText.met(ModAttachments.LEGEND.get(guest), LegendText.HOME), "a home shared with a stranger counts");
			lodger.discard();
			LegendGuests.tend(level, hall);
			data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(data.guest() && LegendText.met(data, LegendText.HOME) && data.unmet().size() == 2, "with the home ready: " + data);
			// Their wine, in a chest of the home.
			BlockPos chest = helper.absolutePos(new BlockPos(7, 2, 10));
			level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
			((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.HONEY_BOTTLE));
			LegendGuests.tend(level, hall);
			data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(data.guest() && data.unmet().keySet().equals(java.util.Set.of(LegendText.HAPPY)), "with the wine there: " + data);
			// A happy village: they settle at once.
			wellbeing(helper, 0.8f);
			LegendGuests.tend(level, hall);
			data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(data != null && data.settled() && data.hall().equals(Optional.of(hall)), "not settled with every need met: " + data);
			helper.assertTrue(guest.getVillagerData().getProfession() == VillagerProfession.MASON
				&& guest.getVillagerData().getLevel() == VillagerData.MAX_VILLAGER_LEVEL, "not a Master Mason: " + guest.getVillagerData());
			BlockPos bed = guest.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
			helper.assertTrue(helper.absolutePos(new BlockPos(8, 2, 9)).equals(bed), "didn't claim the tier III home's bed: " + bed);
			BlockPos station = guest.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
			helper.assertTrue(helper.absolutePos(new BlockPos(16, 2, 4)).equals(station), "didn't take the free stonecutter: " + station);
			helper.assertTrue(LegendRecord.get(level).entry(guest.getUUID()).isPresent(), "not on the record");
			helper.assertTrue(chronicle(helper).stream().anyMatch(l -> l.contains("settled in the village")), "not in the chronicle: " + chronicle(helper));
			helper.assertTrue(hall(helper).legendGuests().guest().isEmpty(), "the hall still has a guest");
			helper.assertTrue(LegendNeeds.home(level, hall, guest), "their bed isn't a home of their own to 29.5's needs");
			helper.succeed();
		}));
	}

	/** They leave on the third evening out of sight (not while a player is near), the chronicle says what they missed, and they don't come back for 7 days. */
	//$ gametest_ticks_batch AREA '100' '"legendGuestsLeave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendGuestsLeave")
	public void theyLeaveAfterThreeDays(GameTestHelper helper) {
		Leftovers.players(helper); // "out of sight" means no player at all: not even one an earlier batch left here
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend books = legend("books_mason", "hall", ", \"chance\": 1.0", ", \"needs\": {\"luxury\": \"books\"}");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		player.teleportTo(helper.absolutePos(HALL).getX(), helper.absolutePos(HALL).getY() + 300, helper.absolutePos(HALL).getZ());
		helper.runAfterDelay(2, () -> staged(helper, List.of(books), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			wellbeing(helper, 0.8f);
			at(level, 1, 1000);
			Villager guest = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(5L));
			assertGuest(helper, guest, books, "hall", hall);
			at(level, 2, 13000);
			LegendGuests.tend(level, hall);
			helper.assertTrue(guest.isAlive(), "left on the second evening");
			at(level, 3, 11000);
			LegendGuests.tend(level, hall);
			helper.assertTrue(guest.isAlive(), "left before the third evening");
			at(level, 3, 12500);
			player.teleportTo(guest.getX() + 3, guest.getY(), guest.getZ());
			LegendGuests.tend(level, hall);
			helper.assertTrue(guest.isAlive(), "left in sight of a player");
			player.teleportTo(guest.getX(), guest.getY() + 300, guest.getZ());
			LegendGuests.tend(level, hall);
			helper.assertTrue(!guest.isAlive(), "still here on the third evening, out of sight (nearest player: "
				+ level.getNearestPlayer(guest, LegendGuests.OUT_OF_SIGHT) + ")");
			helper.assertTrue(hall(helper).legendGuests().guest().isEmpty(), "the hall still has a guest");
			helper.assertTrue(chronicle(helper).stream().anyMatch(l -> l.contains("left the village") && l.contains("a home of my own")
				&& l.contains("books once a week") && !l.contains("a happy village")), "the chronicle doesn't say what they missed: " + chronicle(helper));
			for (long day = 4; day <= 7; day++) {
				helper.assertTrue(LegendGuests.candidates(level, hall, "hall", day).isEmpty(), "back on day " + day + ", within 7 days");
			}
			at(level, 6, 1000);
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(6L)) == null, "came back on day 6");
			at(level, 8, 1000);
			assertGuest(helper, LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(7L)), books, "hall", hall);
			helper.succeed();
		}));
	}

	/** 1 in 4 by default: 200 mornings with a fixed random give about 50 guests, and a day's roll is spent once. */
	//$ gametest_ticks_batch AREA '100' '"legendGuestsChance"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendGuestsChance")
	public void oneInFourByDefault(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend plain = legend("plain_mason", "hall", "", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(Legends.visitFactor(level, hall) == 1f, "a visit factor without edicts: " + Legends.visitFactor(level, hall));
			helper.assertTrue(LegendGuests.chance(level, hall, plain.ways("visit").get(0)) == 0.25f, "the default chance");
			RandomSource random = RandomSource.create(8L);
			int came = 0;
			for (long day = 1; day <= 200; day++) {
				at(level, day, 1000);
				Villager guest = LegendGuests.visit(level, hall, "hall", hall, random);
				if (guest != null) {
					came++;
					LegendGuests.leave(level, hall, guest);
					hall(helper).setLegendGuests(LegendGuests.State.EMPTY); // (no 7-day wait here)
				} else {
					for (int i = 0; i < 20; i++) {
						helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, random) == null, "a second roll on day " + day);
					}
				}
			}
			helper.assertTrue(came >= 30 && came <= 70, came + " guests in 200 mornings at 1 in 4");
			helper.succeed();
		}));
	}

	/** The terms screen: who they are, what they bring, their needs with the same ticks and crosses, the days left. */
	//$ gametest_ticks_batch AREA '100' '"legendGuestsTerms"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendGuestsTerms")
	public void theTermsMatchTheNeeds(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend books = legend("terms_mason", "hall", "", ", \"needs\": {\"luxury\": \"books\"}");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> staged(helper, List.of(books), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			wellbeing(helper, 0.8f);
			at(level, 1, 1000);
			Villager guest = LegendGuests.come(level, hall, books, "hall", hall, RandomSource.create(9L));
			ChoiceMenu menu = LegendGuests.termsMenuForTest(player, guest);
			List<Component> wants = lore(menu, LegendGuests.WANTS_SLOT);
			List<Component> expected = LegendText.needLines(books, ModAttachments.LEGEND.get(guest));
			helper.assertTrue(wants.size() == expected.size() + 1, "the wants: " + strings(wants));
			for (int i = 0; i < expected.size(); i++) {
				helper.assertTrue(wants.get(i).getString().equals(expected.get(i).getString()), "line " + i + ": " + strings(wants) + " vs " + strings(expected));
			}
			helper.assertTrue(wants.get(0).getString().startsWith("✘") && color(wants.get(0)) == ChatFormatting.RED, "no home, a tick: " + strings(wants));
			helper.assertTrue(wants.get(1).getString().contains("books") && wants.get(1).getString().startsWith("✘"), "no books, a tick: " + strings(wants));
			helper.assertTrue(wants.get(2).getString().startsWith("✔") && color(wants.get(2)) == ChatFormatting.GREEN, "a happy village, a cross: " + strings(wants));
			// a home is ready: the next look shows it
			building(helper, id("terms_manor_3"), new BlockPos(6, 2, 6), new Vec3i(6, 5, 6), recorded);
			bed(helper, new BlockPos(8, 2, 8));
			wants = lore(LegendGuests.termsMenuForTest(player, guest), LegendGuests.WANTS_SLOT);
			helper.assertTrue(wants.get(0).getString().startsWith("✔") && wants.get(1).getString().startsWith("✘"), "with a home ready: " + strings(wants));
			String who = name(menu, LegendGuests.WHO_SLOT);
			helper.assertTrue(who.contains("Ada of terms_mason") && who.contains("terms_mason"), "who: " + who);
			helper.assertTrue(strings(lore(menu, LegendGuests.WHO_SLOT)).contains("the lore"), "no lore: " + strings(lore(menu, LegendGuests.WHO_SLOT)));
			helper.assertTrue(lore(menu, LegendGuests.BRINGS_SLOT).size() == books.powers().size()
				&& lore(menu, LegendGuests.BRINGS_SLOT).get(0).getString().contains("Farmer"), "what they bring: " + strings(lore(menu, LegendGuests.BRINGS_SLOT)));
			helper.assertTrue(name(menu, LegendGuests.DAYS_SLOT).equals("3 days left of my stay"), "days: " + name(menu, LegendGuests.DAYS_SLOT));
			helper.assertTrue(strings(lore(menu, LegendGuests.DAYS_SLOT)).equals(List.of("I leave on the evening of day 3")), "leaves: " + strings(lore(menu, LegendGuests.DAYS_SLOT)));
			at(level, 3, 1000);
			helper.assertTrue(name(LegendGuests.termsMenuForTest(player, guest), LegendGuests.DAYS_SLOT).equals("My last day here"), "the last day");
			helper.succeed();
		}));
	}

	/** A guest survives a save and reload mid-stay: their data, the hall's fields, and the hall knowing them again; an old hall loads empty. */
	//$ gametest_ticks_batch AREA '100' '"legendGuestsSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendGuestsSave")
	public void aGuestSurvivesSaveAndReload(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend saved = sure("saved_mason", "hall");
		helper.setBlock(new BlockPos(18, 2, 18), ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> staged(helper, List.of(saved), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 2, 1000);
			Villager guest = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(10L));
			LegendGuests.tend(level, hall);
			LegendData before = ModAttachments.LEGEND.get(guest);
			CompoundTag tag = guest.saveWithoutId(new CompoundTag());
			guest.discard();
			Villager loaded = EntityType.VILLAGER.create(level);
			loaded.load(tag);
			helper.assertTrue(before.equals(ModAttachments.LEGEND.get(loaded)), "the guest's data: " + before + " vs " + ModAttachments.LEGEND.get(loaded));
			helper.assertTrue(loaded.getUUID().equals(guest.getUUID()) && loaded.getDisplayName().getString().equals(guest.getDisplayName().getString()), "who they are");
			level.addFreshEntity(loaded);
			helper.assertTrue(LegendGuests.guest(level, hall) == loaded, "the hall doesn't know the reloaded guest");

			var registries = level.registryAccess();
			VillageHallBlockEntity other = (VillageHallBlockEntity) helper.getBlockEntity(new BlockPos(18, 2, 18));
			CompoundTag hallTag = hall(helper).saveWithoutMetadata(registries);
			other.loadWithComponents(hallTag, registries);
			helper.assertTrue(other.legendGuests().equals(hall(helper).legendGuests()) && other.legendGuests().guest().isPresent()
				&& other.legendGuests().lastVisit(saved.id()).equals(Optional.of(2L)), "the hall's fields: " + other.legendGuests());
			hallTag.remove("legendVisits");
			hallTag.remove("legendGuest");
			hallTag.remove("legendRolled");
			other.loadWithComponents(hallTag, registries);
			helper.assertTrue(other.legendGuests().equals(LegendGuests.State.EMPTY), "an old hall: " + other.legendGuests());
			helper.setBlock(new BlockPos(18, 2, 18), Blocks.AIR);
			// still a guest after the reload: they leave on the third evening
			at(level, 4, 12500);
			LegendGuests.tend(level, hall);
			helper.assertTrue(!loaded.isAlive(), "the reloaded guest never left");
			helper.succeed();
		}));
	}

	private static List<Component> lore(ChoiceMenu menu, int slot) {
		return menu.icon(slot).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	private static String name(ChoiceMenu menu, int slot) {
		Component name = menu.icon(slot).get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	private static List<String> strings(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	private static ChatFormatting color(Component line) {
		TextColor c = line.getStyle().getColor();
		return c == null ? null : c.equals(TextColor.fromLegacyFormat(ChatFormatting.RED)) ? ChatFormatting.RED
			: c.equals(TextColor.fromLegacyFormat(ChatFormatting.GREEN)) ? ChatFormatting.GREEN : null;
	}
}
