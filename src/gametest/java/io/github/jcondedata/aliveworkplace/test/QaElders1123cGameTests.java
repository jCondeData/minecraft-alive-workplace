package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;

/**
 * QA from docs/wiki/elders.md (lane C, 11-23): the card's countdown from ten days before ("Their time comes in 5 days
 * (an Evergreen Charm keeps them)", then "tonight"), its singular, the charm's hint gone when charms are off, the
 * Undertaker's 400 more days on the card, the "Elder · grown N days" line at the boundary, and the charm refused by a
 * child and by a villager whose age nobody knows, through the player's sneak-right-click.
 */
public class QaElders1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** Spec: "From ten days before, the card warns: ... then 'Their time comes tonight'"; never before ten days. */
	//$ gametest_ticks_batch AREA '100' '"qaElders1123cCountdown"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElders1123cCountdown")
	public void theCardCountsDownFromTenDaysBefore(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager dara = villager(helper, new BlockPos(6, 2, 6), "Dara", VillagerProfession.FARMER, 3);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(dara, today - 1200 - 389);
			helper.assertTrue(warnings(level, hallPos, dara).isEmpty(), "eleven days before, the card warns: " + lore(level, hallPos, dara));
			LifeStages.setAdultSince(dara, today - 1200 - 390);
			helper.assertTrue(lore(level, hallPos, dara).contains("Their time comes in 10 days (an Evergreen Charm keeps them)"), "ten days before: " + lore(level, hallPos, dara));
			LifeStages.setAdultSince(dara, today - 1200 - 399);
			helper.assertTrue(lore(level, hallPos, dara).contains("Their time comes in 1 day (an Evergreen Charm keeps them)"), "one day before: " + lore(level, hallPos, dara));
			LifeStages.setAdultSince(dara, today - 1200 - 400);
			helper.assertTrue(lore(level, hallPos, dara).contains("Their time comes tonight"), "the last day: " + lore(level, hallPos, dara));
			for (int d : new int[] {390, 395, 399, 400}) {
				LifeStages.setAdultSince(dara, today - 1200 - d);
				helper.assertTrue(warnings(level, hallPos, dara).size() == 1, "one warning at " + d + ": " + lore(level, hallPos, dara));
				noRawPlaceholders(helper, lore(level, hallPos, dara));
			}
			helper.succeed();
		});
	}

	/** With Evergreen Charms switched off, the card still counts down but doesn't point the player at a charm nobody can give. */
	//$ gametest_ticks_batch AREA '100' '"qaElders1123cCharmsOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElders1123cCharmsOff")
	public void withCharmsOffTheCountdownNamesNoCharm(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager dara = villager(helper, new BlockPos(6, 2, 6), "Dara", VillagerProfession.FARMER, 3);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.AGELESS = false;
			LifeStages.setAdultSince(dara, today - 1200 - 395);
			List<String> card = lore(level, hallPos, dara);
			helper.assertTrue(card.contains("Their time comes in 5 days"), "five days before, charms off: " + card);
			helper.assertTrue(card.stream().noneMatch(l -> l.contains("Evergreen")), "the card offers a switched-off charm: " + card);
			LifeStages.setAdultSince(dara, today - 1200 - 399);
			helper.assertTrue(lore(level, hallPos, dara).contains("Their time comes in 1 day"), "one day, charms off: " + lore(level, hallPos, dara));
			helper.succeed();
		});
	}

	/** Spec: "an Undertaker can bring them back for another 400 days": the brought-back elder's card stops warning, and warns again ten days before. */
	//$ gametest_ticks_batch AREA '100' '"qaElders1123cRevived"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElders1123cRevived")
	public void aRevivedEldersCardWarnsAgainOnlyTenDaysBefore(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(6, 2, 6), "Bram", VillagerProfession.FARMER, 3);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1200 - 400);
			helper.assertTrue(lore(level, hallPos, bram).contains("Their time comes tonight"), "before the revival: " + lore(level, hallPos, bram));
			LifeStages.onRevived(level, bram);
			helper.assertTrue(LifeStages.daysLeft(bram, today) == 400, "days left after a revival: " + LifeStages.daysLeft(bram, today));
			helper.assertTrue(warnings(level, hallPos, bram).isEmpty(), "the revived elder's card still warns: " + lore(level, hallPos, bram));
			helper.assertTrue(lore(level, hallPos, bram).stream().anyMatch(l -> l.startsWith("Elder · grown ")), "still an elder on the card: " + lore(level, hallPos, bram));
			ModAttachments.PASSED_DAY.set(bram, today - 390); // brought back 390 days ago
			helper.assertTrue(lore(level, hallPos, bram).contains("Their time comes in 10 days (an Evergreen Charm keeps them)"), "ten days before the second time: " + lore(level, hallPos, bram));
			helper.succeed();
		});
	}

	/** Spec: after 1200 grown days "their card on the hall's list says 'Elder · grown 1211 days'"; one day short, no such line. */
	//$ gametest_ticks_batch AREA '100' '"qaElders1123cCardLine"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElders1123cCardLine")
	public void theElderLineAppearsOnTheCardAtTwelveHundredDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(6, 2, 6), "Bram", VillagerProfession.FARMER, 3);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1199);
			helper.assertTrue(lore(level, hallPos, bram).stream().noneMatch(l -> l.startsWith("Elder")), "an elder line at 1199 days: " + lore(level, hallPos, bram));
			LifeStages.setAdultSince(bram, today - 1200);
			helper.assertTrue(lore(level, hallPos, bram).contains("Elder · grown 1200 days"), "at 1200 days: " + lore(level, hallPos, bram));
			LifeStages.setAdultSince(bram, today - 1211);
			helper.assertTrue(lore(level, hallPos, bram).contains("Elder · grown 1211 days"), "at 1211 days: " + lore(level, hallPos, bram));
			noRawPlaceholders(helper, lore(level, hallPos, bram));
			helper.succeed();
		});
	}

	/** Spec: a child, or someone whose age nobody knows, refuses with "X is not an elder: the Evergreen Charm is for elders.", and the player keeps the charm. */
	//$ gametest_ticks_batch AREA '100' '"qaElders1123cNotElder"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaElders1123cNotElder")
	public void aChildAndAnUnknownAgeRefuseTheCharmBySneakClick(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager pip = villager(helper, new BlockPos(6, 2, 6), "Pip", VillagerProfession.NONE, 1);
		pip.setAge(-24000);
		Villager wren = villager(helper, new BlockPos(9, 2, 6), "Wren", VillagerProfession.FARMER, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			player.setShiftKeyDown(true);
			ModAttachments.ADULT_SINCE.remove(wren); // nobody knows her age
			ModAttachments.ADULT_SINCE.remove(pip);
			for (Villager v : List.of(pip, wren)) {
				ItemStack charm = new ItemStack(ModItems.EVERGREEN_CHARM);
				player.setItemInHand(InteractionHand.MAIN_HAND, charm);
				UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, v, null);
				helper.assertTrue(player.getMainHandItem().is(ModItems.EVERGREEN_CHARM) && player.getMainHandItem().getCount() == 1,
					"the charm was used on " + v.getName().getString() + ": " + player.getMainHandItem());
				helper.assertTrue(!LifeStages.isAgeless(v), v.getName().getString() + " became ageless");
				LifeStages.Offer offer = LifeStages.offer(player, v, player.getMainHandItem());
				String name = v.getName().getString();
				helper.assertTrue(offer.outcome() == LifeStages.Outcome.NOT_AN_ELDER, name + ": " + offer.outcome());
				helper.assertTrue(offer.message().getString().equals(name + " is not an elder: the Evergreen Charm is for elders."), "said to " + name + ": " + offer.message().getString());
			}
			helper.succeed();
		});
	}

	private static List<String> warnings(ServerLevel level, BlockPos hall, Villager villager) {
		return lore(level, hall, villager).stream().filter(l -> l.startsWith("Their time")).toList();
	}

	private static void noRawPlaceholders(GameTestHelper helper, List<String> lines) {
		for (String l : lines) {
			helper.assertTrue(!l.contains("%s") && !l.contains("%d") && !l.contains("aliveworkplace.") && !l.contains(" 1 days") && !l.contains("{"), "a raw line on the card: " + l);
		}
	}

	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LifeStages.AGELESS = true;
			LifeStages.PASSING = true;
			LifeStages.AGES = true;
			LifeStages.ELDER_DAYS = 1200;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	private static List<String> lore(ServerLevel level, BlockPos hall, Villager villager) {
		ItemLore lore = VillageHallScreen.person(level, hall, villager).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}
}
