package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.*;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSlots;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.LegendsPage;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-d-1028-0408) of the Legend guest fixes B583-B588 and B609 (29.3, 29.4, 29.8), written from the spec and
 * covering what the tests that found them don't: the "Born to a/an" line for the mod's own trades and lists of trades; a
 * guest reloaded from disk still takes no job (and a jobless villager does, so the check can see it); a killed guest
 * still counts as that Legend's visit, while a guest only out of loaded land still holds the village; the host village
 * the other way round and after a reload; a Legendary guest is no candidate elsewhere until they are gone, while a Rare
 * is; a guest refused by the slot leaves on the third evening; a Mythic guest refused when the Town fills meanwhile and
 * settling once it is a City; a guest's terms from a later arrival day, through a reload, across the evening tick and
 * past their stay; and the Legends switch turned off mid-stay. Helpers are {@link QaLegendGuestsGameTests}'.
 */
public class QaLegendGuestFixes1028GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = QaLegendGuestsGameTests.AREA;

	/** The villager as it would come back from its chunk: saved, removed, loaded into a new entity with the same UUID. */
	static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		return copy;
	}

	private static boolean free(ServerLevel level, BlockPos station) {
		return level.getPoiManager().getInRange(h -> true, station, 0, PoiManager.Occupancy.HAS_SPACE).anyMatch(r -> r.getPos().equals(station));
	}

	private static List<String> cardLore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static String cardName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	private static Legend born(String name, String trades) {
		return Legends.read(id(name), JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"" + name + "\","
			+ " \"lore\": \"l\", \"arrive\": [{\"way\": \"born\", \"trades\": [" + trades + "]}]}").getAsJsonObject());
	}

	/**
	 * B583: the Legends page's "Born to" line takes "an" before the mod's own trades that start with a vowel (Innkeeper,
	 * Undertaker, Orchard Keeper), "a" before a sounded h (Habitat Keeper), and with a list of trades the article follows the
	 * first one ("an Undertaker or Mason", "a Mason or Armorer").
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuestFixBornArticle"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuestFixBornArticle")
	public void qaBornToTheModsOwnTradesAndLists(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		List<Legend> roster = List.of(
			born("qa_born_inn", "\"aliveworkplace:innkeeper\""),
			born("qa_born_grave", "\"aliveworkplace:undertaker\", \"minecraft:mason\""),
			born("qa_born_orchard", "\"aliveworkplace:orchard_keeper\""),
			born("qa_born_habitat", "\"aliveworkplace:habitat_keeper\""),
			born("qa_born_mason", "\"minecraft:mason\", \"minecraft:armorer\""));
		List<String> expected = List.of("  • Born to an Innkeeper", "  • Born to an Undertaker or Mason", "  • Born to an Orchard Keeper",
			"  • Born to a Habitat Keeper", "  • Born to a Mason or Armorer");
		helper.runAfterDelay(2, () -> staged(helper, roster, () -> {
			ServerLevel level = helper.getLevel();
			List<ItemStack> cards = LegendsPage.cards(level, helper.absolutePos(HALL));
			for (int i = 0; i < roster.size(); i++) {
				String title = roster.get(i).id().getPath();
				ItemStack card = cards.stream().filter(c -> cardName(c).equals(title)).findFirst().orElse(ItemStack.EMPTY);
				helper.assertTrue(cardLore(card).contains(expected.get(i)), title + ": expected \"" + expected.get(i) + "\" in " + cardLore(card));
			}
			helper.succeed();
		}));
	}

	/**
	 * B584 through a reload: a guest saved and loaded again (their chunk unloaded and back) still takes no job, so the
	 * stonecutter beside them stays free for 200 ticks of their AI; a jobless villager put beside it then takes it (the
	 * check can see a claim).
	 */
	//$ gametest_ticks_batch AREA '600' '"qaGuestFixReloadNoJob"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaGuestFixReloadNoJob")
	public void qaReloadedGuestTakesNoJobButAJoblessVillagerDoes(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend plain = rare("qa_fix_reload_mason", "", "");
		BlockPos stationRel = new BlockPos(6, 2, 3);
		helper.setBlock(stationRel, Blocks.STONECUTTER);
		Villager[] guest = new Villager[1];
		helper.runAfterDelay(2, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			at(level, 1, 1000);
			guest[0] = LegendGuests.come(level, helper.absolutePos(HALL), plain, "hall", helper.absolutePos(new BlockPos(5, 2, 3)), RandomSource.create(101L));
			helper.assertTrue(guest[0] != null, "the guest didn't come");
		}));
		helper.runAfterDelay(4, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			guest[0] = reload(level, guest[0]);
			helper.assertTrue(LegendGuests.isGuest(guest[0]) && guest[0].getUUID().equals(LegendGuests.guest(level, helper.absolutePos(HALL)).getUUID()),
				"the reloaded guest isn't the hall's guest any more");
		}));
		helper.runAfterDelay(220, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos station = helper.absolutePos(stationRel);
			Villager g = guest[0];
			helper.assertTrue(g.isAlive() && LegendGuests.isGuest(g), "no longer a guest 200 ticks after the reload");
			helper.assertTrue(g.getVillagerData().getProfession() == VillagerProfession.NITWIT, "the reloaded guest took a job: " + g.getVillagerData());
			helper.assertTrue(g.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty() && g.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(),
				"the reloaded guest claimed a workstation: " + g.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE));
			helper.assertTrue(free(level, station), "the stonecutter isn't free beside a reloaded guest");
			Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 4));
			jobless.setVillagerData(jobless.getVillagerData().setProfession(VillagerProfession.NONE));
		}));
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(!free(helper.getLevel(), helper.absolutePos(stationRel)), "a jobless villager beside the stonecutter didn't claim it in 280 ticks (the check can't see claims)");
			helper.succeed();
		});
	}

	/**
	 * B585, both sides: a killed guest frees the village, but still counts as that Legend's visit (not on day 2 nor day 7,
	 * back on day 8); a guest only out of loaded land still holds the village (no second guest on day 2), and is let go
	 * once their stay is long past (day 5).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuestFixKilledUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuestFixKilledUnloaded")
	public void qaKilledGuestIsStillAVisitAndAnUnloadedOneStillStays(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend killed = rare("qa_fix_killed_mason", ", \"chance\": 1.0", "");
		Legend away = rare("qa_fix_away_mason", ", \"chance\": 1.0", "");
		Legend next = rare("qa_fix_next_mason", ", \"chance\": 1.0", "");
		Villager[] guest = new Villager[1];
		helper.runAfterDelay(2, () -> staged(helper, List.of(killed), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 1, 1000);
			guest[0] = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(102L));
			helper.assertTrue(guest[0] != null, "no guest on day 1 at chance 1");
			guest[0].kill();
			helper.assertTrue(hall(helper, HALL).legendGuests().guest().isEmpty(), "the hall still holds the guest the moment they died");
		}));
		helper.runAfterDelay(60, () -> staged(helper, List.of(killed), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 2, 1000);
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(103L)) == null, "the killed Legend came back the next morning (7 days)");
			at(level, 7, 1000);
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(104L)) == null, "the killed Legend came back on day 7, 6 days after");
			at(level, 8, 1000);
			Villager back = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(105L));
			helper.assertTrue(back != null && ModAttachments.LEGEND.get(back).id().equals(killed.id()), "the killed Legend didn't come on day 8, 7 days after");
			LegendGuests.leave(level, hall, back);
			hall(helper, HALL).setLegendGuests(LegendGuests.State.EMPTY);
		}));
		helper.runAfterDelay(80, () -> staged(helper, List.of(away, next), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 1, 1000);
			Villager g = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(106L));
			helper.assertTrue(g != null && ModAttachments.LEGEND.get(g).id().equals(away.id()), "no first guest: " + g);
			g.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // their chunk unloads, with them saved in it
			at(level, 2, 1000);
			helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(107L)) == null,
				"a second guest came while the first is only out of loaded land (never two at once)");
			at(level, 5, 1000);
			Villager second = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(108L));
			helper.assertTrue(second != null && ModAttachments.LEGEND.get(second).id().equals(next.id()),
				"no guest on day 5, long after the unloaded guest's stay: " + hall(helper, HALL).legendGuests().guest());
			helper.succeed();
		}));
	}

	/**
	 * B586 the other way round and through a reload: a guest of the far hall, standing nearer this one and saved and loaded
	 * again, settles in the far village (their data and the record), whose slot is taken, and this village's stays free.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuestFixFarHost"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuestFixFarHost")
	public void qaGuestOfTheFarHallReloadedSettlesThere(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.setBlock(OTHER_HALL, ModBlocks.VILLAGE_HALL);
		Legend plain = rare("qa_fix_far_mason", "", "");
		Villager[] guest = new Villager[1];
		helper.runAfterDelay(2, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos other = helper.absolutePos(OTHER_HALL);
			at(level, 1, 1000);
			guest[0] = LegendGuests.come(level, other, plain, "market", helper.absolutePos(new BlockPos(5, 2, 6)), RandomSource.create(109L));
			helper.assertTrue(guest[0] != null && guest[0].blockPosition().distSqr(hall) < guest[0].blockPosition().distSqr(other), "the guest stands nearer this hall: " + guest[0]);
		}));
		helper.runAfterDelay(4, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos other = helper.absolutePos(OTHER_HALL);
			Villager g = reload(level, guest[0]);
			wellbeing(helper, OTHER_HALL, 0.8f);
			wellbeing(helper, HALL, 0.8f);
			home(helper, "qa_fix_far_manor_3", new BlockPos(11, 2, 11), new BlockPos(13, 2, 13), recorded);
			at(level, 1, 3000);
			LegendGuests.tend(level, hall);
			helper.assertTrue(ModAttachments.LEGEND.get(g).guest(), "this hall's round settled the far hall's guest");
			LegendGuests.tend(level, other);
			LegendData data = ModAttachments.LEGEND.get(g);
			helper.assertTrue(data != null && data.settled() && data.hall().equals(Optional.of(other)), "not settled in the far village: " + data);
			Optional<BlockPos> recordHall = LegendRecord.get(level).entry(g.getUUID()).flatMap(LegendRecord.Entry::hall);
			helper.assertTrue(recordHall.equals(Optional.of(other)), "the record puts them at " + recordHall + ", not " + other);
			helper.assertTrue(LegendSlots.whyNot(level, other, plain, null).isPresent(), "the far village's slot is still free");
			helper.assertTrue(LegendSlots.whyNot(level, hall, plain, null).isEmpty(), "this village's slot is taken by a Legend who settled in the far one");
			helper.succeed();
		}));
	}

	/**
	 * B587, both sides: a Legendary guest in one village isn't a candidate in another (so the second village gets the Rare
	 * next in line); once that guest is dead, they are again. A Rare guest in one village stays a candidate elsewhere.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuestFixCandidates"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuestFixCandidates")
	public void qaALegendaryGuestIsNoCandidateElsewhereUntilGone(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.setBlock(OTHER_HALL, ModBlocks.VILLAGE_HALL);
		Legend legendary = legend("qa_fix_cand_legendary", "legendary", ", \"chance\": 1.0", "", PACE);
		Legend rare = rare("qa_fix_cand_rare", ", \"chance\": 1.0", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(legendary, rare), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos other = helper.absolutePos(OTHER_HALL);
			at(level, 1, 1000);
			Villager first = LegendGuests.come(level, hall, legendary, "hall", hall, RandomSource.create(110L));
			helper.assertTrue(first != null, "the Legendary guest didn't come");
			List<Legend> may = LegendGuests.candidates(level, other, "hall", 1);
			helper.assertTrue(!may.contains(legendary) && may.contains(rare), "candidates in the other village while the Legendary is a guest here: "
				+ may.stream().map(l -> l.id().getPath()).toList());
			Villager second = LegendGuests.visit(level, other, "hall", other, RandomSource.create(111L));
			helper.assertTrue(second != null && ModAttachments.LEGEND.get(second).id().equals(rare.id()), "the other village didn't get the Rare: " + second);
			// a Rare guest there doesn't keep the Rare from this village's list
			helper.assertTrue(LegendGuests.candidates(level, hall, "hall", 9).contains(rare), "a Rare guest in one village kept them off another's list");
			first.kill();
			may = LegendGuests.candidates(level, other, "hall", 1);
			helper.assertTrue(may.contains(legendary), "the Legendary isn't a candidate elsewhere once their guest is dead: " + may.stream().map(l -> l.id().getPath()).toList());
			helper.succeed();
		}));
	}

	/**
	 * B587/B609: a Legendary guest whose slot is taken meanwhile (the same Legendary settled in another village) doesn't
	 * settle with every need met, stays on the record once, and leaves on the third evening, not a tick before.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuestFixRefused"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuestFixRefused")
	public void qaARefusedLegendaryGuestLeavesOnTheThirdEvening(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.setBlock(OTHER_HALL, ModBlocks.VILLAGE_HALL);
		Legend legendary = legend("qa_fix_refused_legendary", "legendary", "", "", PACE);
		helper.runAfterDelay(2, () -> staged(helper, List.of(legendary), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos other = helper.absolutePos(OTHER_HALL);
			wellbeing(helper, HALL, 0.8f);
			at(level, 1, 1000);
			Villager guest = LegendGuests.come(level, hall, legendary, "hall", hall, RandomSource.create(112L));
			helper.assertTrue(guest != null, "the guest didn't come");
			Villager elsewhere = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 15));
			elsewhere.setNoAi(true);
			Legends.make(level, elsewhere, legendary, "test", Optional.of(other));
			home(helper, "qa_fix_refused_manor_3", new BlockPos(6, 2, 8), new BlockPos(8, 2, 10), recorded);
			at(level, 2, 1000);
			LegendGuests.tend(level, hall);
			helper.assertTrue(guest.isAlive() && ModAttachments.LEGEND.get(guest).guest(), "the refused guest settled or left on day 2: " + ModAttachments.LEGEND.get(guest));
			long held = LegendRecord.get(level).holding(e -> e.id().equals(legendary.id()));
			helper.assertTrue(held == 1, "the Legendary on the record " + held + " times");
			at(level, 3, LegendGuests.EVENING - 1);
			LegendGuests.tend(level, hall);
			helper.assertTrue(guest.isAlive(), "the refused guest left before the third evening");
			at(level, 3, LegendGuests.EVENING);
			LegendGuests.tend(level, hall);
			helper.assertTrue(!guest.isAlive() && hall(helper, HALL).legendGuests().guest().isEmpty(), "the refused guest didn't leave on the third evening");
			helper.assertTrue(LegendRecord.get(level).holding(e -> e.id().equals(legendary.id())) == 1, "the record changed when the refused guest left");
			helper.succeed();
		}));
	}

	/**
	 * B609 for a Mythic: a Mythic guest in a Town (cap 1) doesn't settle once another Mythic has come to live there
	 * meanwhile; the Town grown into a City (cap 2), the same round settles them.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuestFixMythic"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuestFixMythic")
	public void qaAMythicGuestFollowsTheCapWhenSettling(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend guestMyth = legend("qa_fix_myth_guest", "mythic", "", "", PACE);
		Legend livingMyth = legend("qa_fix_myth_living", "mythic", "", "", PACE);
		helper.runAfterDelay(2, () -> {
			int[] caps = Legends.MYTHIC_CAP;
			try {
				Legends.MYTHIC_CAP = new int[] {0, 0, 1, 2};
				staged(helper, List.of(guestMyth, livingMyth), () -> {
					ServerLevel level = helper.getLevel();
					BlockPos hall = helper.absolutePos(HALL);
					String dim = level.dimension().location().toString();
					VillageHallBlockEntity entity = hall(helper, HALL);
					wellbeing(helper, HALL, 0.8f);
					entity.setRank(VillageRanks.Rank.TOWN);
					at(level, 1, 1000);
					Villager guest = LegendGuests.come(level, hall, guestMyth, "hall", hall, RandomSource.create(113L));
					helper.assertTrue(guest != null, "the Mythic guest didn't come");
					Villager living = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 4));
					living.setNoAi(true);
					helper.assertTrue(Legends.canCome(level, hall, livingMyth), "a first Mythic may not live in a Town");
					Legends.make(level, living, livingMyth, "test", Optional.of(hall));
					home(helper, "qa_fix_myth_manor_3", new BlockPos(6, 2, 8), new BlockPos(8, 2, 10), recorded);
					entity.setRank(VillageRanks.Rank.TOWN);
					LegendGuests.tend(level, hall);
					long mythics = LegendRecord.get(level).holding(e -> e.rarity() == livingMyth.rarity() && e.in(dim, hall));
					helper.assertTrue(ModAttachments.LEGEND.get(guest).guest() && mythics == 1, "a Mythic guest settled past a Town's cap of 1: " + mythics + " Mythics here");
					entity.setRank(VillageRanks.Rank.CITY);
					LegendGuests.tend(level, hall);
					LegendData data = ModAttachments.LEGEND.get(guest);
					helper.assertTrue(data != null && data.settled(), "the Mythic guest didn't settle in a City (cap 2, one living): " + data);
				});
			} finally {
				Legends.MYTHIC_CAP = caps;
			}
			helper.succeed();
		});
	}

	/**
	 * B588 from a later arrival and through a reload: come on day 5, "3 days left" and "the evening of day 7"; reloaded on
	 * day 6, "2 days left" and still day 7; on day 7 a tick before the evening the day is named, on the evening tick "once no
	 * one is near"; kept to day 8 by a player, "My stay is over"; the player gone, the next round they leave.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuestFixLaterTerms"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuestFixLaterTerms")
	public void qaTermsFromALaterArrivalThroughAReload(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend plain = rare("qa_fix_terms_mason", "", "");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 5, 1000);
			Villager guest = LegendGuests.come(level, hall, plain, "hall", hall, RandomSource.create(114L));
			helper.assertTrue(guest != null, "the guest didn't come");
			player.setPos(guest.getX() + 2, guest.getY(), guest.getZ());
			check(helper, LegendGuests.termsMenuForTest(player, guest), "day 5", "3 days left of my stay", "I leave on the evening of day 7");
			at(level, 6, 1000);
			guest = reload(level, guest);
			check(helper, LegendGuests.termsMenuForTest(player, guest), "day 6, reloaded", "2 days left of my stay", "I leave on the evening of day 7");
			at(level, 7, LegendGuests.EVENING - 1);
			check(helper, LegendGuests.termsMenuForTest(player, guest), "day 7, a tick before evening", "My last day here", "I leave on the evening of day 7");
			at(level, 7, LegendGuests.EVENING);
			LegendGuests.tend(level, hall);
			helper.assertTrue(guest.isAlive(), "left on the evening in sight of a player");
			check(helper, LegendGuests.termsMenuForTest(player, guest), "day 7 evening", "My last day here", "I leave once no one is near");
			at(level, 8, 1000);
			LegendGuests.tend(level, hall);
			helper.assertTrue(guest.isAlive(), "left on day 8 in sight of a player");
			check(helper, LegendGuests.termsMenuForTest(player, guest), "day 8", "My stay is over", "I leave once no one is near");
			player.setPos(guest.getX() + 200, guest.getY(), guest.getZ());
			LegendGuests.tend(level, hall);
			helper.assertTrue(!guest.isAlive() && LegendGuests.guest(level, hall) == null, "still here on day 8 with no one near");
			helper.succeed();
		}));
	}

	private static void check(GameTestHelper helper, ChoiceMenu menu, String when, String title, String line) {
		String name = name(menu, LegendGuests.DAYS_SLOT);
		List<String> lore = lore(menu, LegendGuests.DAYS_SLOT);
		helper.assertTrue(name.equals(title) && lore.equals(List.of(line)), when + ": \"" + name + "\" / " + lore + ", expected \"" + title + "\" / " + line);
	}

	/**
	 * The Legends switch off mid-stay (config): no round, roll or settling while off, and on again the same guest is the
	 * village's guest and no second one comes (never two at once).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuestFixSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuestFixSwitchOff")
	public void qaLegendsOffMidStayKeepsTheOneGuest(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend plain = rare("qa_fix_off_mason", ", \"chance\": 1.0", "");
		Legend next = rare("qa_fix_off_next", ", \"chance\": 1.0", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(plain, next), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			wellbeing(helper, HALL, 0.8f);
			at(level, 1, 1000);
			Villager guest = LegendGuests.come(level, hall, plain, "hall", hall, RandomSource.create(115L));
			helper.assertTrue(guest != null, "the guest didn't come");
			home(helper, "qa_fix_off_manor_3", new BlockPos(6, 2, 8), new BlockPos(8, 2, 10), recorded);
			boolean enabled = Legends.ENABLED;
			try {
				Legends.ENABLED = false;
				at(level, 2, 1000);
				LegendGuests.round(level, hall, RandomSource.create(116L));
				helper.assertTrue(LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(117L)) == null, "a guest came with Legends off");
				helper.assertTrue(guest.isAlive() && ModAttachments.LEGEND.get(guest).guest(), "the guest settled or left with Legends off: " + ModAttachments.LEGEND.get(guest));
			} finally {
				Legends.ENABLED = enabled;
			}
			at(level, 2, 1500);
			Villager second = LegendGuests.visit(level, hall, "hall", hall, RandomSource.create(118L));
			helper.assertTrue(second == null, "a second guest came while the first still stays: " + (second == null ? "" : ModAttachments.LEGEND.get(second).id()));
			Villager now = LegendGuests.guest(level, hall);
			helper.assertTrue(now != null && now.getUUID().equals(guest.getUUID()), "the hall lost its guest over the switch: " + now);
			helper.succeed();
		}));
	}
}
