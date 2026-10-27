package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA for 29.8, Legends who visit, written from the spec: the third evening from both sides (and a guest whose needs are
 * met that very evening settles instead), the 7 days counted from the day they came, a way's chance of 0 and 1, a
 * Legendary settled in another village or a Rare in the next village, and the terms screen and the leaving line in exact English.
 * Every roster and clock change is made and undone within one tick ({@link #staged}).
 */
public class QaLegendGuestsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos HALL = new BlockPos(3, 2, 3);
	static final BlockPos OTHER_HALL = new BlockPos(18, 2, 18);
	static final String NS = "aliveworkplace_test";
	static final long DAY = VillageNeeds.DAY;

	static ResourceLocation id(String name) {
		return ResourceLocation.fromNamespaceAndPath(NS, name);
	}

	/** A Mason Legend of {@code rarity} who visits the hall ({@code extra}: more fields of the way), with {@code more} fields of the file. */
	static Legend legend(String name, String rarity, String extra, String more, String powers) {
		return Legends.read(id(name), JsonParser.parseString("{\"rarity\": \"" + rarity + "\", \"job\": \"minecraft:mason\", \"title\": \"" + name + "\","
			+ " \"lore\": \"the lore\", \"names\": [\"Ada of " + name + "\"], \"arrive\": [{\"way\": \"visit\", \"place\": \"hall\"" + extra + "}],"
			+ " \"powers\": [" + powers + "]" + more + "}").getAsJsonObject());
	}

	static final String PACE = "{\"type\": \"pace\", \"trades\": [\"minecraft:farmer\"], \"factor\": 2.0}";

	static Legend rare(String name, String extra, String more) {
		return legend(name, "rare", extra, more, PACE);
	}

	static void setUp(GameTestHelper helper, List<BlueprintData.Placement> recorded) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
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

	static void staged(GameTestHelper helper, List<Legend> legends, Runnable body) {
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

	static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		return (VillageHallBlockEntity) helper.getBlockEntity(at);
	}

	static void wellbeing(GameTestHelper helper, BlockPos at, float wellbeing) {
		hall(helper, at).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, wellbeing));
	}

	static List<String> chronicle(GameTestHelper helper) {
		return hall(helper, HALL).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND).map(e -> e.text().getString()).toList();
	}

	/** A finished tier III home ({@code name} ends in _3) at {@code origin}, 6x5x6, with a bed at {@code bed}. */
	static void home(GameTestHelper helper, String name, BlockPos origin, BlockPos bed, List<BlueprintData.Placement> recorded) {
		ServerLevel level = helper.getLevel();
		Vec3i size = new Vec3i(6, 5, 6);
		level.getStructureManager().getOrCreate(id(name)).fillFromWorld(level, helper.absolutePos(origin).above(60), size, false, Blocks.AIR);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id(name), placement, UUID.randomUUID());
		recorded.add(placement);
		helper.setBlock(bed, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(bed.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	static List<String> lore(ChoiceMenu menu, int slot) {
		return menu.icon(slot).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	static String name(ChoiceMenu menu, int slot) {
		Component name = menu.icon(slot).get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	/**
	 * The third evening from both sides: still there at the last tick of day 2's night and at 11999 on day 3, gone at
	 * 12000 on day 3 (out of sight), and the chronicle's line says exactly what they missed, in English.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLegendGuestEvening"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendGuestEvening")
	public void qaGuestLeavesAtTheThirdEveningNotATickBefore(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend wine = rare("qa_evening_mason", "", ", \"needs\": {\"luxury\": \"wine\"}");
		helper.runAfterDelay(2, () -> staged(helper, List.of(wine), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			wellbeing(helper, HALL, 0.2f);
			at(level, 1, 1000);
			Villager guest = LegendGuests.come(level, hall, wine, "hall", hall, RandomSource.create(21L));
			helper.assertTrue(guest != null, "the guest didn't come");
			at(level, 2, DAY - 1);
			LegendGuests.tend(level, hall);
			helper.assertTrue(guest.isAlive(), "left on the night of day 2");
			at(level, 3, LegendGuests.EVENING - 1);
			LegendGuests.tend(level, hall);
			helper.assertTrue(guest.isAlive(), "left a tick before the third evening");
			at(level, 3, LegendGuests.EVENING);
			LegendGuests.tend(level, hall);
			helper.assertTrue(!guest.isAlive(), "still here on the third evening, out of sight");
			helper.assertTrue(LegendGuests.guest(level, hall) == null && hall(helper, HALL).legendGuests().guest().isEmpty(), "the hall still has a guest");
			String expected = "Ada of qa_evening_mason, the qa_evening_mason, left the village after three days, still wanting a home of my own, "
				+ "wine once a week and a happy village";
			helper.assertTrue(chronicle(helper).contains(expected), "the leaving line: " + chronicle(helper) + ", expected " + expected);
			helper.succeed();
		}));
	}

	/** Every need met on the evening they would leave: the round settles them (a Master Mason in the home) and they don't leave. */
	//$ gametest_ticks_batch AREA '100' '"qaLegendGuestLastRound"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendGuestLastRound")
	public void qaNeedsMetOnTheLastEveningSettleRatherThanLeave(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend plain = rare("qa_last_mason", "", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			wellbeing(helper, HALL, 0.8f);
			at(level, 1, 1000);
			Villager guest = LegendGuests.come(level, hall, plain, "hall", hall, RandomSource.create(22L));
			helper.assertTrue(guest != null, "the guest didn't come");
			at(level, 3, 11000);
			LegendGuests.tend(level, hall);
			helper.assertTrue(ModAttachments.LEGEND.get(guest).guest(), "settled with no home (not every need met)");
			// the home is finished on the third evening, as they would leave
			home(helper, "qa_last_manor_3", new BlockPos(8, 2, 8), new BlockPos(10, 2, 10), recorded);
			at(level, 3, 12500);
			LegendGuests.tend(level, hall);
			LegendData data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(guest.isAlive() && data != null && data.settled(), "left rather than settled on the last round: " + data);
			helper.assertTrue(guest.getVillagerData().getProfession() == VillagerProfession.MASON
				&& guest.getVillagerData().getLevel() == VillagerData.MAX_VILLAGER_LEVEL, "not a Master Mason: " + guest.getVillagerData());
			helper.assertTrue(chronicle(helper).stream().noneMatch(l -> l.contains("left the village")), "a leaving line for a settled guest: " + chronicle(helper));
			helper.succeed();
		}));
	}

	/** The 7 days count from the day they came: came on day 5, not on day 11 (6 days), back on day 12 (7 days). */
	//$ gametest_ticks_batch AREA '100' '"qaLegendGuestCooldown"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendGuestCooldown")
	public void qaSevenDaysFromTheVisitBothSides(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend sure = rare("qa_cool_mason", ", \"chance\": 1.0", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(sure), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 5, 1000);
			Villager guest = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(23L));
			helper.assertTrue(guest != null, "no guest on day 5 at chance 1");
			LegendGuests.leave(level, hall, guest);
			helper.assertTrue(LegendGuests.candidates(level, hall, "hall", 11).isEmpty(), "may come on day 11, 6 days after");
			helper.assertTrue(LegendGuests.candidates(level, hall, "hall", 12).contains(sure), "may not come on day 12, 7 days after");
			at(level, 11, 1000);
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(24L)) == null, "came back on day 11");
			at(level, 12, 1000);
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(25L)) != null, "didn't come back on day 12 at chance 1");
			helper.succeed();
		}));
	}

	/** A way's chance of 0 never brings them in 60 mornings; of 1, every morning; over 1, read as 1. */
	//$ gametest_ticks_batch AREA '100' '"qaLegendGuestChance"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendGuestChance")
	public void qaChanceZeroNeverAndOneAlways(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend never = rare("qa_never_mason", ", \"chance\": 0.0", "");
		Legend always = rare("qa_always_mason", ", \"chance\": 1.0", "");
		Legend over = rare("qa_over_mason", ", \"chance\": 3.0", "");
		helper.runAfterDelay(2, () -> {
			staged(helper, List.of(never), () -> {
				ServerLevel level = helper.getLevel();
				BlockPos hall = helper.absolutePos(HALL);
				RandomSource random = RandomSource.create(26L);
				for (long day = 1; day <= 60; day++) {
					at(level, day, 1000);
					helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, random) == null, "came at chance 0 on day " + day);
				}
				hall(helper, HALL).setLegendGuests(LegendGuests.State.EMPTY);
			});
			staged(helper, List.of(always, over), () -> {
				ServerLevel level = helper.getLevel();
				BlockPos hall = helper.absolutePos(HALL);
				helper.assertTrue(LegendGuests.chance(level, hall, over.ways("visit").get(0)) == 1f, "a chance of 3 isn't read as 1: "
					+ LegendGuests.chance(level, hall, over.ways("visit").get(0)));
				RandomSource random = RandomSource.create(27L);
				for (long day = 100; day < 130; day++) {
					at(level, day, 1000);
					Villager guest = LegendGuests.visit(level, hall, "hall", hall, random);
					helper.assertTrue(guest != null, "nobody came at chance 1 on day " + day);
					LegendGuests.leave(level, hall, guest);
					hall(helper, HALL).setLegendGuests(LegendGuests.State.EMPTY); // (no 7-day wait here)
				}
			});
			helper.succeed();
		});
	}

	/**
	 * The slot (29.3): a Legendary settled in another village keeps them from visiting here, and comes once that one is
	 * gone; a Rare settled in the next village doesn't keep them from visiting here (one of each per village).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLegendGuestSlot"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendGuestSlot")
	public void qaSlotTakenElsewhereLegendaryButNotRare(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.setBlock(OTHER_HALL, ModBlocks.VILLAGE_HALL);
		Legend legendary = legend("qa_slot_legendary", "legendary", ", \"chance\": 1.0", "", PACE);
		Legend rare = rare("qa_slot_rare", ", \"chance\": 1.0", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(legendary, rare), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos other = helper.absolutePos(OTHER_HALL);
			Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 15));
			a.setNoAi(true);
			Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 17));
			b.setNoAi(true);
			Legends.make(level, a, legendary, "test", Optional.of(other));
			Legends.make(level, b, rare, "test", Optional.of(other));
			at(level, 2, 1000);
			List<Legend> may = LegendGuests.candidates(level, hall, "hall", 2);
			helper.assertTrue(!may.contains(legendary), "a Legendary settled in another village may visit: " + may);
			helper.assertTrue(may.contains(rare), "a Rare settled in another village keeps them from this one: " + may);
			Villager guest = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(28L));
			helper.assertTrue(guest != null && ModAttachments.LEGEND.get(guest).id().equals(rare.id()), "not the Rare: " + guest);
			LegendGuests.leave(level, hall, guest);
			Legends.clear(a);
			at(level, 3, 1000);
			guest = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(29L));
			helper.assertTrue(guest != null && ModAttachments.LEGEND.get(guest).id().equals(legendary.id()), "the Legendary didn't come once the slot was free: " + guest);
			helper.succeed();
		}));
	}

	/**
	 * The terms screen in exact English on day 2 of 3: the needs with ticks and crosses, the hint, "2 days left", the day
	 * they leave; on day 3 "My last day here"; a Legend with no powers says so.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLegendGuestTerms"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendGuestTerms")
	public void qaTermsScreenInEnglish(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend wine = legend("qa_terms_mason", "rare", "", ", \"needs\": {\"luxury\": \"wine\"}", "");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> staged(helper, List.of(wine), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			wellbeing(helper, HALL, 0.8f);
			at(level, 1, 1000);
			Villager guest = LegendGuests.come(level, hall, wine, "hall", hall, RandomSource.create(32L));
			at(level, 2, 1000);
			ChoiceMenu menu = LegendGuests.termsMenuForTest(player, guest);
			List<String> wants = lore(menu, LegendGuests.WANTS_SLOT);
			helper.assertTrue(name(menu, LegendGuests.WANTS_SLOT).equals("What I would want, to stay"), "wants title: " + name(menu, LegendGuests.WANTS_SLOT));
			helper.assertTrue(wants.equals(List.of("✘ A home of their own (tier III or better)", "✘ Their luxury once a week: wine", "✔ A happy village",
				"Meet them all and I settle here, a Master of my trade")), "wants: " + wants);
			helper.assertTrue(name(menu, LegendGuests.BRINGS_SLOT).equals("What I would bring")
				&& lore(menu, LegendGuests.BRINGS_SLOT).equals(List.of("My name, and the stories that go with it")),
				"brings: " + name(menu, LegendGuests.BRINGS_SLOT) + " " + lore(menu, LegendGuests.BRINGS_SLOT));
			helper.assertTrue(name(menu, LegendGuests.DAYS_SLOT).equals("2 days left of my stay"), "day 2: " + name(menu, LegendGuests.DAYS_SLOT));
			helper.assertTrue(lore(menu, LegendGuests.DAYS_SLOT).equals(List.of("I leave on the evening of day 3")), "leaves: " + lore(menu, LegendGuests.DAYS_SLOT));
			at(level, 3, 13000);
			menu = LegendGuests.termsMenuForTest(player, guest);
			helper.assertTrue(name(menu, LegendGuests.DAYS_SLOT).equals("My last day here"), "day 3: " + name(menu, LegendGuests.DAYS_SLOT));
			helper.assertTrue(!name(menu, LegendGuests.DAYS_SLOT).contains("1 days"), "\"1 days\": " + name(menu, LegendGuests.DAYS_SLOT));
			helper.succeed();
		}));
	}
}
