package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-04, third run) for the fixes B343, B354 and B390, from what each report expected a player to see,
 * on the far side of what the fixes' own tests cover:
 * <ul>
 *   <li>B343: mid-story, the healer lets the other two items the report names through: a Guild Charter (sneak-click) gets
 *   the guild's answer and a Remedy is used, neither one told the story;</li>
 *   <li>B354: the report's "next to one the villager could take the job": a lectern beside Mira and beside Dara, the
 *   Sealed Letter and then the Reply are both handed over with a sneak-click, and nobody takes a job; the lost compass
 *   sneak-clicked on its owner beside a cartography table is handed back too;</li>
 *   <li>B390: a fire burning on the outside of a finished hut's wall is written up under the hut's name; a fire one block
 *   further out, touching nothing of the hut, is "Fire in the village", and so is a fire two blocks over its roof.</li>
 * </ul>
 */
public class QaVillageFixes1104c3GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String ERRAND = PersonalErrandGameTests.AREA;
	private static final BlockPos SICK_HALL = new BlockPos(15, 2, 15);

	// ---- B343 ----

	/**
	 * B343 ("a gift, tonic, remedy or the guild charter can't be used on those villagers while their story runs"): the
	 * Gray Cough under way, the healer a Journeyman. Sneak-clicking her with a Guild Charter gets the guild's answer (she
	 * isn't a Master: refused, the charter kept), not the story's talk; a Remedy in the main hand is used on her (one of
	 * two gone). Neither click hands out the story's three maps or moves its chapter; an empty hand still does.
	 */
	//$ gametest_batch TALL '"qa1104c3HealerCharter"'
	@GameTest(template = TALL, batch = "qa1104c3HealerCharter")
	public void qaHealerMidStoryLetsTheCharterAndRemedyThrough(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean sick = Sickness.ENABLED;
		SicknessStory.Locator locator = SicknessStory.LOCATOR;
		BlockPos hall = helper.absolutePos(SICK_HALL);
		VillageHalls.RADIUS = 12;
		Arcs.NEAR = 0;
		Sickness.ENABLED = true;
		SicknessStory.LOCATOR = (l, from, herb) -> {
			String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(herb).getPath();
			return switch (path) {
				case "sea_lavender" -> from.offset(200, 0, 0);
				case "mirewort" -> from.offset(400, 0, 0);
				case "glowcap" -> from.offset(600, 0, 0);
				default -> null;
			};
		};
		Leftovers.after(helper, () -> {
			Arcs.stopAll(level, hall);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Sickness.ENABLED = sick;
			SicknessStory.LOCATOR = locator;
		});
		helper.setBlock(SICK_HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, hall);
		for (int i = 0; i < 8; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(11 + (i % 4) * 3, 2, 12 + (i / 4) * 3));
			v.setNoAi(true);
			v.setCustomName(Component.literal("Villager " + (i + 1)));
		}
		BlockPos station = new BlockPos(20, 2, 20);
		helper.setBlock(station, Blocks.BREWING_STAND);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(21, 2, 20));
		nurse.setNoAi(true);
		nurse.setCustomName(Component.literal("Nurse Ada"));
		Jobs.employ(level, nurse, helper.absolutePos(station), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
		nurse.setVillagerXp(70);
		level.setDayTime(Math.floorDiv(level.getDayTime(), VillageNeeds.DAY) * VillageNeeds.DAY + VillageNeeds.DAY + 1000);

		ArcState s = Arcs.start(level, hall, Arcs.find("the_sickness").orElseThrow());
		helper.assertTrue(s != null, "the arc didn't start");
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qa3c"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER));
		p.setShiftKeyDown(true);
		QaStageSixGameTests.clickVillager(p, nurse, c.seen());
		p.setShiftKeyDown(false);
		List<String> seen = QaStageFourGameTests.keys(c.seen());
		helper.assertTrue(seen.stream().anyMatch(k -> k.startsWith("message.aliveworkplace.guild.")), "the charter got no guild answer: saw " + seen);
		helper.assertTrue(p.getMainHandItem().is(ModItems.GUILD_CHARTER) && p.getMainHandItem().getCount() == 1, "the charter on a Journeyman went: "
			+ p.getMainHandItem());
		helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 0 && s.chapter == 0, "the charter's click told the story: chapter " + s.chapter);
		helper.runAfterDelay(3, () -> {
			c.seen().clear();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEDY, 2));
			QaStageSixGameTests.clickVillager(p, nurse, c.seen());
			helper.assertTrue(p.getMainHandItem().is(ModItems.REMEDY) && p.getMainHandItem().getCount() == 1, "the Remedy wasn't used on the healer: "
				+ p.getMainHandItem() + ", saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 0 && s.chapter == 0, "the Remedy's click told the story: chapter " + s.chapter);
		});
		helper.runAfterDelay(6, () -> {
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			QaStageSixGameTests.clickVillager(p, nurse, c.seen());
			helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 3 && s.chapter == 1, "an empty hand didn't talk to the healer afterwards: chapter "
				+ s.chapter);
			helper.succeed();
		});
	}

	// ---- B354 ----

	/**
	 * B354 ("next to one the villager could take the job"): a lectern beside Mira and one beside Dara. The Sealed Letter in
	 * the main hand, sneaking, on Mira: she reads it and writes back, and stays jobless. The Reply in the main hand,
	 * sneaking, on Dara: it finishes the request and pays 4 emeralds, and Dara is still a farmer.
	 */
	//$ gametest_ticks_batch HUGE '120' '"qa1104c3LetterLectern"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "qa1104c3LetterLectern")
	public void qaSneakClicksDeliverTheLetterAndReplyBesideALectern(GameTestHelper helper) {
		QaStageSevenGameTests.letterVillage(helper, (c, dara, mira) -> {
			ServerPlayer p = c.player();
			helper.setBlock(new BlockPos(24, 2, 23), Blocks.LECTERN);
			helper.setBlock(new BlockPos(8, 2, 7), Blocks.LECTERN);
			var miraJob = mira.getVillagerData().getProfession();
			var daraJob = dara.getVillagerData().getProfession();
			p.setItemInHand(InteractionHand.MAIN_HAND, QaStageSevenGameTests.takeMarked(p, false));
			p.setShiftKeyDown(true);
			QaStageSixGameTests.clickVillager(p, mira, c.seen());
			QaStageFiveGameTests.closeScreen(p);
			helper.assertTrue(PersonalErrandGameTests.marked(p, "aliveworkplace_letter", false).isEmpty()
				&& !PersonalErrandGameTests.marked(p, "aliveworkplace_letter", true).isEmpty(),
				"a sneak-click with the letter beside a lectern didn't deliver it: hand " + p.getMainHandItem() + ", saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(mira.getVillagerData().getProfession() == miraJob, "Mira took a job: " + mira.getVillagerData().getProfession());
			ItemStack reply = QaStageSevenGameTests.takeMarked(p, true);
			p.setItemInHand(InteractionHand.MAIN_HAND, reply);
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			QaStageFiveGameTests.closeScreen(p);
			p.setShiftKeyDown(false);
			helper.assertTrue(PersonalErrandGameTests.marked(p, "aliveworkplace_letter", true).isEmpty() && io.github.jcondedata.aliveworkplace.story.PersonalRequests.of(dara) == null,
				"a sneak-click with the reply on Dara didn't finish it: hand " + p.getMainHandItem() + ", saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 4, "not paid 4 emeralds: " + p.getInventory().countItem(Items.EMERALD));
			helper.assertTrue(dara.getVillagerData().getProfession() == daraJob, "Dara changed job: " + dara.getVillagerData().getProfession());
			helper.succeed();
		});
	}

	/**
	 * B354, the heirloom ("a heirloom such as Grandmother's Compass"): Dara's lost compass found in the chest it was put
	 * in, a cartography table beside her (a compass's job block). A sneak-right-click on Dara with the compass, through
	 * the real interact packet, hands it back: the request is done and pays 8 emeralds, and she is still a farmer.
	 */
	//$ gametest_ticks_batch ERRAND '80' '"qa1104c3HeirloomSneak"'
	@GameTest(template = ERRAND, timeoutTicks = 80, batch = "qa1104c3HeirloomSneak")
	public void qaASneakClickWithTheHeirloomHandsItBack(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 2);
			dara.setNoAi(true);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qa3h"));
			ServerPlayer p = c.player();
			io.github.jcondedata.aliveworkplace.story.Friendship.add(dara, p, 300);
			BlockPos chestRel = new BlockPos(14, 2, 14);
			helper.setBlock(chestRel, Blocks.CHEST);
			helper.setBlock(new BlockPos(5, 2, 3), Blocks.CARTOGRAPHY_TABLE);
			var quest = PersonalErrandGameTests.acceptedFile(helper, PersonalErrandGameTests.heirloomAt(helper, chestRel, "compass"), dara, p);
			PersonalErrandGameTests.moveTo(p, helper.absolutePos(chestRel).offset(30, 0, 0));
			helper.assertTrue(io.github.jcondedata.aliveworkplace.story.RequestErrands.placeHeirlooms(level) == 1, "the compass wasn't put in the chest");
			var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(helper.absolutePos(chestRel));
			ItemStack found = ItemStack.EMPTY;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (!chest.getItem(i).isEmpty()) {
					found = chest.getItem(i).copy();
					chest.setItem(i, ItemStack.EMPTY);
				}
			}
			helper.assertTrue(found.is(Items.COMPASS), "in the chest: " + found);
			PersonalErrandGameTests.moveTo(p, helper.absolutePos(new BlockPos(5, 2, 6)));
			var job = dara.getVillagerData().getProfession();
			int emeralds = p.getInventory().countItem(Items.EMERALD);
			p.setItemInHand(InteractionHand.MAIN_HAND, found);
			p.setShiftKeyDown(true);
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			QaStageFiveGameTests.closeScreen(p);
			p.setShiftKeyDown(false);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.story.PersonalRequests.of(dara) == null && p.getInventory().countItem(Items.COMPASS) == 0,
				"a sneak-click with the heirloom didn't hand it back: hand " + p.getMainHandItem() + ", saw " + QaStageFourGameTests.keys(c.seen()) + ", quest " + quest);
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) - emeralds == 8, "not paid 8 emeralds: " + (p.getInventory().countItem(Items.EMERALD) - emeralds));
			helper.assertTrue(dara.getVillagerData().getProfession() == job, "Dara changed job: " + dara.getVillagerData().getProfession());
			helper.succeed();
		});
	}

	// ---- B390 ----

	/**
	 * B390, the side of a hut rather than its roof: a fire set against the outside of the hut's corner post (the post is
	 * the block it burns on) is written up as "Fire in the &lt;hut&gt;: ...". The other side of the boundary, by the same
	 * lookup the chronicle uses: a spot one block further out, touching nothing of the hut, and a spot two blocks over the
	 * roof's middle belong to no build.
	 */
	//$ gametest_ticks_batch BIG '300' '"qa1104c3FireWall"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qa1104c3FireWall")
	public void qaAFireAgainstTheHutsWallIsNamedAfterItButNotOneFurtherOut(GameTestHelper helper) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, "wall1104c3", Blocks.OAK_PLANKS);
		ServerLevel level = hut.level();
		Villager warden = QaFireGameTests.firewarden(helper, new BlockPos(3, 2, 4));
		warden.setNoAi(true);
		String name = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(hut.id()).getString();
		// the hut spans (12..16, 2..4, 12..16); its corner post at (12, 2..3, 12)
		BlockPos beside = new BlockPos(11, 2, 12);
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			helper.assertTrue(VillageFire.buildBurning(level, helper.absolutePos(beside)).isPresent(), "a fire against the post belongs to no build");
			helper.assertTrue(VillageFire.buildBurning(level, helper.absolutePos(new BlockPos(14, 5, 14))).isPresent(), "a fire on the roof belongs to no build");
			helper.assertTrue(VillageFire.buildBurning(level, helper.absolutePos(new BlockPos(10, 2, 12))).isEmpty(), "a fire two out belongs to the hut");
			helper.assertTrue(VillageFire.buildBurning(level, helper.absolutePos(new BlockPos(14, 6, 14))).isEmpty(), "a fire two over the roof belongs to the hut");
			QaFireGameTests.fire(helper, beside);
			helper.assertTrue(level.getBlockState(helper.absolutePos(beside)).is(Blocks.FIRE), "no fire beside the post");
			VillageFire.douse(level, helper.absolutePos(beside), 0, warden);
		});
		helper.runAfterDelay(120, () -> {
			String text = QaFireGameTests.fireEntry(level, hut.hall()).map(e -> e.text().getString()).orElse("nothing");
			helper.assertTrue(text.startsWith("Fire in the " + name + ":"), "the hut is \"" + name + "\" and the chronicle says: " + text);
			helper.succeed();
		});
	}

	/**
	 * B390, the other side: a fire on the ground two blocks from the hut (nothing of the hut under or beside it) is
	 * written up as "Fire in the village: ...", not under the hut's name.
	 */
	//$ gametest_ticks_batch BIG '300' '"qa1104c3FireOut"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qa1104c3FireOut")
	public void qaAFireTwoBlocksFromTheHutIsFireInTheVillage(GameTestHelper helper) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, "out1104c3", Blocks.OAK_PLANKS);
		ServerLevel level = hut.level();
		Villager warden = QaFireGameTests.firewarden(helper, new BlockPos(3, 2, 4));
		warden.setNoAi(true);
		BlockPos out = new BlockPos(10, 2, 14);
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			QaFireGameTests.fire(helper, out);
			helper.assertTrue(level.getBlockState(helper.absolutePos(out)).is(Blocks.FIRE), "no fire on the ground");
			VillageFire.douse(level, helper.absolutePos(out), 0, warden);
		});
		helper.runAfterDelay(120, () -> {
			String text = QaFireGameTests.fireEntry(level, hut.hall()).map(e -> e.text().getString()).orElse("nothing");
			helper.assertTrue(text.startsWith("Fire in the village:"), "a fire two blocks from the hut reads: " + text);
			helper.succeed();
		});
	}
}
