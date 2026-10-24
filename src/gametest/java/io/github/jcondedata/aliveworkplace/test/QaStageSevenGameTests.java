package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.OldSage;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA stage 7 (2026-10-24): what stage 6 left unclicked. A found Legend's own clicks at their camp (the castaway fed a
 * cooked meal, the Old Sage handed his riddles' answers), the personal-request letter carried there and back, and a
 * friend added with {@code /workplace friend add} giving a builder orders and cancelling a build, against a stranger
 * who is refused. On stage 5's client (a real connection) and stage 6's clicks: the real interact packet, at a point
 * then plain, main hand then off hand, as the client sends them; survival players who aren't operators.
 *
 * <p>Expectations from docs/wiki and en_us.json, not the code: "the castaway [is free] for a cooked meal" and the line
 * "too weak to walk: Food... something cooked, please." (legends.md, LegendSites); the Sage's riddles "(hand them the
 * answer)"; "Right-click Mira with it in her village ... Right-click Dara with the reply and it's done. Lost the letter?
 * Right-click Dara empty-handed and she writes another" (personal-requests.md); "With workplaceBuilderOwnership on, a
 * Builder takes orders only from the player who hired it, their friends and operators" (builder.md) and "X can now give
 * orders to your builders and cancel your builds." (en_us.json).
 */
public class QaStageSevenGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final BlockPos MIDDLE = new BlockPos(15, 1, 15);

	// --- Found Legends at their camp ------------------------------------------------------------------------------

	private static Legend found(String site) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa7_found_" + site), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:cartographer\", \"title\": \"Found " + site + "\", \"lore\": \"the lore\","
				+ " \"names\": [\"Pip of the " + site + "\"], \"arrive\": [{\"way\": \"found\", \"site\": \"" + site + "\"}]}").getAsJsonObject());
	}

	/** A hall owned by {@code owner}, on the villages' list; the camps, captives and villagers go when the test ends. */
	private static void legendHall(GameTestHelper helper, ServerPlayer owner) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setOwner(owner.getUUID(), owner.getName().getString());
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				record.forgetCaptive(v.getUUID());
				v.discard();
			}
			for (LegendRecord.Captive c : record.captives()) {
				if (c.hall().equals(Optional.of(hall))) {
					record.forgetCaptive(c.villager());
				}
			}
			OldSage.forget();
			CivicEffects.forget();
			LegendPowers.forget();
		});
	}

	/** Runs {@code body} with only {@code legends} loaded, putting the roster and the clock back before the tick ends. */
	private static void staged(GameTestHelper helper, List<Legend> legends, Runnable body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		try {
			Map<ResourceLocation, Legend> map = new java.util.LinkedHashMap<>();
			legends.forEach(l -> map.put(l.id(), l));
			Legends.setForTest(map);
			body.run();
		} finally {
			level.setDayTime(time);
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static Villager camp(GameTestHelper helper, String site, Legend legend) {
		Villager v = LegendSites.place(helper.getLevel(), LegendSites.site(site), new LegendSites.Match(helper.absolutePos(HALL), legend),
			helper.absolutePos(MIDDLE), RandomSource.create(29));
		helper.assertTrue(v != null, "the " + site + " camp is set down");
		return v;
	}

	private static boolean freed(GameTestHelper helper, Villager v) {
		return LegendRecord.get(helper.getLevel()).captive(v.getUUID()).map(LegendRecord.Captive::freed).orElse(false);
	}

	private static QaStageFiveGameTests.Client survivor(GameTestHelper helper, String name) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
		helper.assertFalse(c.player().hasPermissions(2), "the player is an operator; the check would prove nothing");
		return c;
	}

	private static List<String> keys(QaStageFiveGameTests.Client c) {
		return QaStageFourGameTests.keys(c.seen());
	}

	/**
	 * The castaway, clicked empty-handed, says "too weak to walk ... something cooked, please" and stays. With a cooked
	 * meal in the off hand (the main hand empty, as a player who keeps food there clicks) they are fed and freed, one meal
	 * eaten, as from the main hand.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa7CastawayOffHand"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa7CastawayOffHand")
	public void theCastawayTakesAMealFromTheOffHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qa7cast");
		ServerPlayer p = c.player();
		legendHall(helper, p);
		Legend legend = found("shipwreck");
		staged(helper, List.of(legend), () -> {
			Villager castaway = camp(helper, "shipwreck", legend);
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.legend_site.hungry"), "empty-handed: saw " + keys(c));
			helper.assertFalse(freed(helper, castaway), "a word fed the castaway");
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COOKED_BEEF, 3));
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertTrue(freed(helper, castaway), "a cooked meal in the off hand didn't free the castaway: saw " + keys(c));
			helper.assertTrue(p.getOffhandItem().getCount() == 2, "one meal eaten from the off hand: " + p.getOffhandItem());
		});
		helper.succeed();
	}

	/**
	 * Raw meat in the off hand (main hand empty) is no meal: "something cooked, please", kept. A cooked meal in the main
	 * hand, sneaking, frees them and one is eaten; a second click on the freed castaway eats nothing more.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa7CastawayMain"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa7CastawayMain")
	public void theCastawayIsFedOnceFromTheMainHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qa7castmain");
		ServerPlayer p = c.player();
		legendHall(helper, p);
		Legend legend = found("shipwreck");
		staged(helper, List.of(legend), () -> {
			Villager castaway = camp(helper, "shipwreck", legend);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BEEF, 2));
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertFalse(freed(helper, castaway), "raw meat freed the castaway");
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.legend_site.hungry"), "raw meat: saw " + keys(c));
			helper.assertTrue(p.getOffhandItem().getCount() == 2, "raw meat was taken: " + p.getOffhandItem());
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_COD, 2));
			p.setShiftKeyDown(true);
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertTrue(freed(helper, castaway), "a cooked meal in the main hand, sneaking, didn't free them: saw " + keys(c));
			helper.assertTrue(p.getMainHandItem().getCount() == 1 && p.getOffhandItem().getCount() == 2, "one cod eaten, the beef kept: "
				+ p.getMainHandItem() + " / " + p.getOffhandItem());
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertTrue(p.getMainHandItem().getCount() == 1, "the freed castaway ate a second meal: " + p.getMainHandItem());
		});
		helper.succeed();
	}

	/** A creative player's meal frees the castaway and isn't used up. */
	//$ gametest_ticks_batch HUGE '100' '"qa7CastawayCreative"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa7CastawayCreative")
	public void aCreativeMealFreesTheCastawayAndIsKept(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("qa7castcr"));
		ServerPlayer p = c.player();
		legendHall(helper, p);
		Legend legend = found("shipwreck");
		staged(helper, List.of(legend), () -> {
			Villager castaway = camp(helper, "shipwreck", legend);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_CHICKEN, 1));
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertTrue(freed(helper, castaway), "a cooked chicken didn't free the castaway in creative: saw " + keys(c));
			helper.assertTrue(p.getMainHandItem().is(Items.COOKED_CHICKEN) && p.getMainHandItem().getCount() == 1, "creative used up the chicken: " + p.getMainHandItem());
		});
		helper.succeed();
	}

	/** Twelve blocks away, the click packet (as a modified client could send it) reaches nobody: the castaway isn't fed. */
	//$ gametest_ticks_batch HUGE '100' '"qa7CastawayFar"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa7CastawayFar")
	public void aMealFromFarAwayFeedsNobody(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qa7castfar");
		ServerPlayer p = c.player();
		legendHall(helper, p);
		Legend legend = found("shipwreck");
		staged(helper, List.of(legend), () -> {
			Villager castaway = camp(helper, "shipwreck", legend);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF, 2));
			p.moveTo(castaway.getX() + 12, castaway.getY(), castaway.getZ(), 90f, 0f);
			for (InteractionHand hand : InteractionHand.values()) {
				p.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(castaway, false, hand, new Vec3(0, 1, 0)));
				p.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(castaway, false, hand));
			}
			helper.assertFalse(freed(helper, castaway), "fed from 12 blocks away");
			helper.assertTrue(p.getMainHandItem().getCount() == 2, "a meal went from 12 blocks away: " + p.getMainHandItem());
		});
		helper.succeed();
	}

	/** The Old Sage at his hut, his three riddles set (compass, clock, book), for {@code owner}'s village. */
	private static Villager sage(GameTestHelper helper) {
		Villager v = OldSage.placeHut(helper.getLevel(), helper.absolutePos(HALL), helper.absolutePos(MIDDLE), RandomSource.create(14));
		helper.assertTrue(v != null, "the hut is set down");
		ModAttachments.SAGE_RIDDLES.set(v, new OldSage.Riddles(List.of(2, 3, 4), 0, 0));
		return v;
	}

	private static void sageStaged(GameTestHelper helper, Runnable body) {
		Legend sage = Legends.get(OldSage.ID).orElse(null);
		helper.assertTrue(sage != null, "old_sage.json not loaded");
		staged(helper, List.of(sage), body);
	}

	/**
	 * The Sage, clicked empty-handed, asks his riddle "(hand them the answer)". The answer (a compass) in the off hand, the
	 * main hand empty, is taken: "Just so.", the next riddle asked. A torch in the off hand that answers nothing, the main
	 * hand empty, is an empty-handed click: the riddle again, no miss counted, the torch kept.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa7SageOffHand"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa7SageOffHand")
	public void theSageTakesTheAnswerFromTheOffHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qa7sage");
		ServerPlayer p = c.player();
		legendHall(helper, p);
		sageStaged(helper, () -> {
			Villager sage = sage(helper);
			QaStageSixGameTests.clickVillager(p, sage, c.seen());
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.sage.riddle"), "empty-handed: saw " + keys(c));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COMPASS));
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, sage, c.seen());
			OldSage.Riddles after = ModAttachments.SAGE_RIDDLES.get(sage);
			helper.assertTrue(after.solved() == 1, "the compass in the off hand wasn't taken: " + after + ", saw " + keys(c));
			helper.assertTrue(p.getOffhandItem().isEmpty(), "the compass is still in the off hand");
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.sage.right"), "no \"Just so.\": " + keys(c));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH, 5));
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, sage, c.seen());
			OldSage.Riddles then = ModAttachments.SAGE_RIDDLES.get(sage);
			helper.assertTrue(then.solved() == 1 && then.misses() == 0, "a torch in the off hand counted as an answer: " + then);
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.sage.riddle"), "with a torch in the off hand: saw " + keys(c));
			helper.assertTrue(p.getOffhandItem().getCount() == 5, "the torch was taken");
		});
		helper.succeed();
	}

	/**
	 * From the main hand: a wrong item (a stick) is refused with the shake of the head and kept; the right one (a compass,
	 * sneaking) is taken; the three answered (compass, clock, book) free the Sage. A click after that takes nothing.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa7SageMain"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa7SageMain")
	public void theSagesRiddlesAnsweredFromTheMainHandFreeHim(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qa7sagemain");
		ServerPlayer p = c.player();
		legendHall(helper, p);
		sageStaged(helper, () -> {
			Villager sage = sage(helper);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 3));
			QaStageSixGameTests.clickVillager(p, sage, c.seen());
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.sage.wrong"), "a stick: saw " + keys(c));
			helper.assertTrue(p.getMainHandItem().getCount() == 3 && ModAttachments.SAGE_RIDDLES.get(sage).solved() == 0, "the stick was taken");
			p.setShiftKeyDown(true);
			for (ItemStack answer : List.of(new ItemStack(Items.COMPASS), new ItemStack(Items.CLOCK), new ItemStack(Items.BOOK))) {
				p.setItemInHand(InteractionHand.MAIN_HAND, answer);
				c.seen().clear();
				QaStageSixGameTests.clickVillager(p, sage, c.seen());
				helper.assertTrue(p.getMainHandItem().isEmpty(), answer + " wasn't taken: saw " + keys(c));
			}
			helper.assertTrue(freed(helper, sage), "three answers didn't free the Sage: " + ModAttachments.SAGE_RIDDLES.get(sage));
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 4));
			QaStageSixGameTests.clickVillager(p, sage, c.seen());
			helper.assertTrue(p.getMainHandItem().getCount() == 4, "the freed Sage took an emerald");
		});
		helper.succeed();
	}

	// --- The personal-request letter ------------------------------------------------------------------------------

	private static ItemStack takeMarked(ServerPlayer p, boolean reply) {
		ItemStack s = PersonalErrandGameTests.marked(p, "aliveworkplace_letter", reply);
		ItemStack copy = s.copy();
		s.setCount(0);
		return copy;
	}

	/**
	 * The letter carried with real clicks, both hands. Accepted, the Sealed Letter is in the bag. In the off hand (main
	 * empty), clicked on Dara who wrote it: she doesn't take it back. On Mira: she reads it and the Reply is given. The
	 * reply in the main hand, sneaking, on Mira: kept. The reply from the off hand to Dara: done, paid 4 emeralds.
	 */
	//$ gametest_ticks_batch HUGE '120' '"qa7Letter"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "qa7Letter")
	public void aLetterCarriedWithRealClicksFromEitherHand(GameTestHelper helper) {
		letterVillage(helper, (c, dara, mira) -> {
			ServerPlayer p = c.player();
			ItemStack letter = takeMarked(p, false);
			helper.assertTrue(!letter.isEmpty(), "no Sealed Letter on accepting");
			p.setItemInHand(InteractionHand.OFF_HAND, letter);
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			QaStageFiveGameTests.closeScreen(p);
			helper.assertTrue(p.getOffhandItem().getCount() == 1, "Dara took back her own letter");
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, mira, c.seen());
			QaStageFiveGameTests.closeScreen(p);
			helper.assertTrue(p.getOffhandItem().isEmpty(), "Mira didn't take the letter from the off hand: saw " + keys(c));
			ItemStack reply = takeMarked(p, true);
			helper.assertTrue(!reply.isEmpty(), "no Reply from Mira");
			p.setItemInHand(InteractionHand.MAIN_HAND, reply);
			p.setShiftKeyDown(true);
			QaStageSixGameTests.clickVillager(p, mira, c.seen());
			QaStageFiveGameTests.closeScreen(p);
			helper.assertTrue(p.getMainHandItem().getCount() == 1, "Mira took her own reply back");
			p.setShiftKeyDown(false);
			p.setItemInHand(InteractionHand.OFF_HAND, p.getMainHandItem().copy());
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			QaStageFiveGameTests.closeScreen(p);
			helper.assertTrue(p.getOffhandItem().isEmpty() && PersonalRequests.of(dara) == null, "the reply home didn't finish it: " + PersonalRequests.of(dara));
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 4, "not paid 4 emeralds: " + p.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/**
	 * "Lost the letter? Right-click Dara empty-handed and she writes another": the bag emptied, a torch in the off hand,
	 * the main hand empty, a click on Dara gives a new Sealed Letter; a second click while it's carried gives no third.
	 * A stranger who never accepted gets none.
	 */
	//$ gametest_ticks_batch HUGE '120' '"qa7LetterLost"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "qa7LetterLost")
	public void aLostLetterIsWrittenAgainOnAnEmptyHandedClick(GameTestHelper helper) {
		letterVillage(helper, (c, dara, mira) -> {
			ServerPlayer p = c.player();
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH, 4));
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			QaStageFiveGameTests.closeScreen(p);
			helper.assertTrue(PersonalErrandGameTests.marked(p, "aliveworkplace_letter", false).getCount() == 1, "no new letter: saw " + keys(c));
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			QaStageFiveGameTests.closeScreen(p);
			int letters = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (p.getInventory().getItem(i).is(Items.PAPER)) {
					letters += p.getInventory().getItem(i).getCount();
				}
			}
			helper.assertTrue(letters == 1, "a second letter written while one is carried: " + letters);
			QaStageFiveGameTests.Client other = survivor(helper, "qa7nohelper");
			QaStageSixGameTests.clickVillager(other.player(), dara, other.seen());
			QaStageFiveGameTests.closeScreen(other.player());
			helper.assertTrue(PersonalErrandGameTests.marked(other.player(), "aliveworkplace_letter", false).isEmpty(), "a stranger was given the letter");
			helper.succeed();
		});
	}

	interface LetterBody {
		void run(QaStageFiveGameTests.Client c, Villager dara, Villager mira);
	}

	/** Dara's village and Mira's in Elmford (two halls in the area), Dara's letter accepted by a real client. */
	private static void letterVillage(GameTestHelper helper, LetterBody body) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.RADIUS = 6; // two villages in one test area (put back by the village's own cleanup)
			BlockPos farRel = new BlockPos(25, 2, 25);
			BlockPos far = helper.absolutePos(farRel);
			helper.setBlock(farRel, ModBlocks.VILLAGE_HALL);
			Leftovers.after(helper, () -> {
				Caravans.Data.get(level).remove(far);
				io.github.jcondedata.aliveworkplace.story.Stories.Data.get(level).remove(far);
			});
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(7, 2, 7), "Dara", "minecraft:farmer", 2);
			QaStageFiveGameTests.Client c = survivor(helper, "qa7letter");
			Friendship.add(dara, c.player(), 300);
			helper.runAfterDelay(2, () -> {
				CivicEffects.forget();
				Caravans.Data.get(level).setWants(far, Component.literal("Elmford"), List.of());
				Villager mira = HeartEventGameTests.villager(helper, new BlockPos(25, 2, 23), "Mira");
				dara.setNoAi(true);
				mira.setNoAi(true);
				CivicEffects.forget();
				Quest quest = PersonalRequestGameTests.accepted(helper, "letter_to_family", dara, c.player());
				helper.assertTrue(quest != null, "not accepted");
				c.seen().clear();
				body.run(c, dara, mira);
			});
		});
	}

	// --- A friend giving a builder orders -------------------------------------------------------------------------

	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	private static ItemStack placedHut(GameTestHelper helper, BlockPos rel) {
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
		BlueprintData data = BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(new BlueprintData.Placement(
			helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE)));
		blueprint.set(ModComponents.BLUEPRINT, data);
		return blueprint;
	}

	private static Villager hiredBuilder(GameTestHelper helper, ServerPlayer owner) {
		helper.setBlock(new BlockPos(1, 2, 5), ModBlocks.BUILDERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 4));
		v.setNoAi(true);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(new BlockPos(1, 2, 5)));
		Friends.hire(owner, v);
		return v;
	}

	private static List<BuildSite> sitesOf(GameTestHelper helper, Villager builder) {
		return BuildSiteManager.get(helper.getLevel()).all().stream().filter(s -> builder.getUUID().equals(s.builder())).toList();
	}

	/**
	 * A stranger right-clicks the owner's builder with a placed blueprint: refused ("works for X. Ask them to run
	 * /workplace friend add Y"), from the main hand and from the off hand, the blueprint kept and no build started. Added
	 * as a friend, the same player hands it over from the off hand (main hand empty) and the builder takes it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa7FriendOrders"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa7FriendOrders")
	public void aFriendGivesTheBuilderOrdersAStrangerIsRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client owner = survivor(helper, "qa7boss");
		QaStageFiveGameTests.Client other = survivor(helper, "qa7mate");
		ServerPlayer o = owner.player();
		ServerPlayer p = other.player();
		QaStageSixGameTests.profileCache(helper, o, p);
		Leftovers.after(helper, () -> Friends.get(helper.getLevel().getServer()).remove(o.getUUID(), p.getUUID()));
		Villager builder = hiredBuilder(helper, o);
		helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(builder).id().equals(o.getUUID()), "the owner didn't hire him");
		p.setItemInHand(InteractionHand.MAIN_HAND, placedHut(helper, new BlockPos(6, 2, 6)));
		QaStageSixGameTests.clickVillager(p, builder, other.seen());
		QaStageFiveGameTests.closeScreen(p);
		Component refused = QaStageFourGameTests.find(other.seen(), "message.aliveworkplace.not_your_builder");
		helper.assertTrue(refused != null, "the stranger wasn't refused (main hand): saw " + keys(other));
		QaStageFourGameTests.assertRendered(helper, refused, o.getGameProfile().getName(), p.getGameProfile().getName());
		helper.assertTrue(p.getMainHandItem().is(io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT) && sitesOf(helper, builder).isEmpty(),
			"a stranger's blueprint was taken: " + sitesOf(helper, builder));
		p.setItemInHand(InteractionHand.OFF_HAND, p.getMainHandItem().copy());
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		other.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, other.seen());
		QaStageFiveGameTests.closeScreen(p);
		helper.assertTrue(QaStageFourGameTests.saw(other.seen(), "message.aliveworkplace.not_your_builder"), "the stranger wasn't refused (off hand): saw " + keys(other));
		helper.assertTrue(sitesOf(helper, builder).isEmpty(), "a stranger's blueprint was taken from the off hand");

		QaStageSixGameTests.command(owner, "/workplace friend add " + p.getGameProfile().getName());
		helper.assertTrue(QaStageFourGameTests.saw(owner.seen(), "command.aliveworkplace.friend.added"), "friend add: " + keys(owner));
		other.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, other.seen());
		QaStageFiveGameTests.closeScreen(p);
		helper.assertFalse(QaStageFourGameTests.saw(other.seen(), "message.aliveworkplace.not_your_builder"), "the friend was refused: " + keys(other));
		helper.assertTrue(sitesOf(helper, builder).size() == 1, "the friend's blueprint didn't start a build: saw " + keys(other));
		helper.succeed();
	}

	/**
	 * "X can now give orders to your builders and cancel your builds." The owner's build: a stranger's
	 * /workplace cancel is refused and the build goes on; added as a friend, the same command cancels it.
	 * Removed again, they're refused once more (on a second build).
	 */
	//$ gametest_ticks_batch AREA '100' '"qa7FriendCancel"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa7FriendCancel")
	public void aFriendCancelsTheOwnersBuildAStrangerCannot(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client owner = survivor(helper, "qa7site");
		QaStageFiveGameTests.Client other = survivor(helper, "qa7helper");
		ServerPlayer o = owner.player();
		ServerPlayer p = other.player();
		QaStageSixGameTests.profileCache(helper, o, p);
		Leftovers.after(helper, () -> Friends.get(helper.getLevel().getServer()).remove(o.getUUID(), p.getUUID()));
		Villager builder = hiredBuilder(helper, o);
		o.setItemInHand(InteractionHand.MAIN_HAND, placedHut(helper, new BlockPos(6, 2, 6)));
		QaStageSixGameTests.clickVillager(o, builder, owner.seen());
		QaStageFiveGameTests.closeScreen(o);
		helper.assertTrue(sitesOf(helper, builder).size() == 1, "the owner's blueprint didn't start a build: " + keys(owner));
		BuildSite site = sitesOf(helper, builder).get(0);
		helper.assertTrue(o.getUUID().equals(site.owner()), "the build isn't the owner's");

		QaStageSixGameTests.command(other, "/workplace cancel " + site.id());
		helper.assertTrue(QaStageFourGameTests.saw(other.seen(), "command.aliveworkplace.not_owner"), "the stranger wasn't refused: " + keys(other));
		helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(site.id()) != null, "a stranger cancelled the build");

		QaStageSixGameTests.command(owner, "/workplace friend add " + p.getGameProfile().getName());
		QaStageSixGameTests.command(other, "/workplace cancel " + site.id());
		helper.assertTrue(QaStageFourGameTests.saw(other.seen(), "command.aliveworkplace.cancelled"), "the friend couldn't cancel: " + keys(other));
		helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(site.id()) == null, "the build is still on after the friend's cancel");

		QaStageSixGameTests.command(owner, "/workplace friend remove " + p.getGameProfile().getName());
		o.setItemInHand(InteractionHand.MAIN_HAND, placedHut(helper, new BlockPos(6, 2, 6)));
		QaStageSixGameTests.clickVillager(o, builder, owner.seen());
		QaStageFiveGameTests.closeScreen(o);
		List<BuildSite> again = sitesOf(helper, builder);
		helper.assertTrue(again.size() == 1, "the second build didn't start: " + keys(owner));
		QaStageSixGameTests.command(other, "/workplace cancel " + again.get(0).id());
		helper.assertTrue(QaStageFourGameTests.saw(other.seen(), "command.aliveworkplace.not_owner"), "a removed friend cancelled: " + keys(other));
		helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(again.get(0).id()) != null, "a removed friend cancelled the build");
		helper.succeed();
	}
}
