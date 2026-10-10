package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The Evergreen Charm and ageless elders (ROADMAP 34.19a), with the part of 34.19 it stands on: the charm's recipe,
 * tooltip and config; an elder taking it for each good trait (a Master, Gifted, a Legend, 20 happy days) through the
 * player's own sneak-right-click, the charm used up, the hall's gold badge and the chronicle; each refusal with its
 * reason and the charm kept (a plain elder, someone not yet an elder, a child, one already ageless); the happy days
 * counted from real moods; an ageless elder outliving 40 and more elder days and a reload while an ordinary one passes
 * in their sleep in the hall's own round and leaves a grave; an elder brought back from it; and each config switch off.
 */
public class AgelessElderGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation PRODIGY = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "prodigy");
	private static final ResourceLocation OLD_SAGE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "old_sage");
	private static final String BADGE = "❦ Ageless: will never leave us";

	/** A totem of undying, a golden apple, 2 emeralds and a heart of the sea make one charm; its tooltip; the config's four options. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"agelessConfigFile"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "agelessConfigFile")
	public void theCharmIsCraftedAndExplainsItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(3, 2, List.of(new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.GOLDEN_APPLE),
			new ItemStack(Items.EMERALD), new ItemStack(Items.EMERALD), new ItemStack(Items.HEART_OF_THE_SEA), ItemStack.EMPTY));
		ItemStack made = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
		helper.assertTrue(made.is(ModItems.EVERGREEN_CHARM) && made.getCount() == 1, "the recipe makes " + made);
		CraftingInput oneEmerald = CraftingInput.of(2, 2, List.of(new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.GOLDEN_APPLE),
			new ItemStack(Items.EMERALD), new ItemStack(Items.HEART_OF_THE_SEA)));
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, oneEmerald, level).isEmpty(), "one emerald made a charm");
		helper.assertTrue(made.getMaxStackSize() == 1, "charms stack: " + made.getMaxStackSize());
		helper.assertTrue(made.getHoverName().getString().equals("Evergreen Charm"), "name: " + made.getHoverName().getString());
		List<Component> tip = new ArrayList<>();
		made.getItem().appendHoverText(made, net.minecraft.world.item.Item.TooltipContext.EMPTY, tip, TooltipFlag.NORMAL);
		List<String> lines = tip.stream().map(Component::getString).toList();
		helper.assertTrue(lines.equals(List.of("Sneak-right-click an elder: they become ageless and never pass away. One charm, one villager.",
			"Only the best take it: a Master of their trade, a Gifted villager, a Legend, or an elder happy (mood 80 or more) for 20 days running.")),
			"tooltip: " + lines);

		WorkplaceConfig defaults = WorkplaceConfig.parse("{}");
		helper.assertTrue(defaults.agelessElders && defaults.elderPassing && defaults.villagerAges && defaults.villagerElderDays == 120, "defaults");
		helper.assertTrue(WorkplaceConfig.parse("{\"villagerElderDays\": 5}").villagerElderDays == 20, "clamped up to 20");
		helper.assertTrue(WorkplaceConfig.parse("{\"villagerElderDays\": 5000}").villagerElderDays == 1000, "clamped down to 1000");
		helper.assertTrue(LifeStages.AGELESS && LifeStages.PASSING && LifeStages.AGES && LifeStages.ELDER_DAYS == 120, "on in a GameTest");
		// The file's switches reach the game (at once, so no other test sees them; the defaults go back after).
		try {
			WorkplaceConfig.parse("{\"agelessElders\": false}").apply();
			helper.assertTrue(!LifeStages.AGELESS && LifeStages.PASSING && LifeStages.AGES, "agelessElders off in the file");
			WorkplaceConfig.parse("{\"elderPassing\": false}").apply();
			helper.assertTrue(LifeStages.AGELESS && !LifeStages.PASSING && LifeStages.AGES, "elderPassing off in the file");
			WorkplaceConfig.parse("{\"villagerAges\": false, \"villagerElderDays\": 400}").apply();
			helper.assertTrue(LifeStages.AGELESS && LifeStages.PASSING && !LifeStages.AGES && LifeStages.ELDER_DAYS == 400, "villagerAges off, 400 days, in the file");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(LifeStages.AGELESS && LifeStages.PASSING && LifeStages.AGES && LifeStages.ELDER_DAYS == 120, "the defaults again");
		helper.succeed();
	}

	/** A Master, a Gifted villager, a Legend and an elder happy for 20 days each take a charm; it is used up, badged and chronicled. */
	//$ gametest_ticks_batch AREA '100' '"agelessAccepted"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "agelessAccepted")
	public void anElderWithAGoodTraitTakesTheCharm(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 5);
		Villager ida = villager(helper, new BlockPos(7, 2, 6), "Ida", VillagerProfession.FLETCHER, 2);
		Villager odo = villager(helper, new BlockPos(9, 2, 6), "Odo", VillagerProfession.LIBRARIAN, 3);
		Villager wren = villager(helper, new BlockPos(13, 2, 6), "Wren", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			long today = Chronicle.day(level);
			for (Villager v : List.of(bram, ida, odo, wren)) {
				LifeStages.setAdultSince(v, today - 131);
			}
			Gifted.set(ida, PRODIGY);
			helper.assertTrue(Gifted.of(ida) != null, "the test's gift didn't load: " + Gifted.all().keySet());
			ModAttachments.LEGEND.set(odo, LegendData.settled(OLD_SAGE, "", Optional.of(hallPos), today - 1, "test"));
			helper.assertTrue(io.github.jcondedata.aliveworkplace.legend.Legends.of(odo).isPresent(), "the test's Legend didn't load");
			ModAttachments.HAPPY_STREAK.set(wren, new LifeStages.Streak(20, today));

			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL); // a charm is used up (not in creative)
			player.setShiftKeyDown(true);

			// Bram, through the player's own sneak-right-click.
			List<String> before = lore(level, hallPos, bram);
			helper.assertTrue(before.contains("Elder · grown 131 days") && !before.contains(BADGE), "an elder's card before: " + before);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EVERGREEN_CHARM));
			player.setShiftKeyDown(false);
			UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, bram, null);
			helper.assertTrue(!LifeStages.isAgeless(bram) && player.getMainHandItem().is(ModItems.EVERGREEN_CHARM), "a plain right-click gave the charm");
			player.setShiftKeyDown(true);
			InteractionResult result = UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, bram, null);
			helper.assertTrue(result == InteractionResult.SUCCESS, "the sneak-right-click: " + result);
			helper.assertTrue(LifeStages.isAgeless(bram), "Bram isn't ageless");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the charm wasn't used up: " + player.getMainHandItem());
			helper.assertTrue(Boolean.TRUE.equals(ModAttachments.AGELESS.get(bram)), "the attachment: " + ModAttachments.AGELESS.get(bram));
			String line = "Bram will never leave us: " + player.getDisplayName().getString() + " gave them an Evergreen Charm (a Master of their trade)";
			helper.assertTrue(chronicle(hall).contains(line), "chronicle: " + chronicle(hall));
			helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.LIFE && e.text().getString().equals(line)), "its kind");
			List<String> after = lore(level, hallPos, bram);
			helper.assertTrue(after.contains("Elder · grown 131 days") && after.contains(BADGE), "the gold leaf badge: " + after);
			ItemLore card = VillageHallScreen.person(level, hallPos, bram).get(DataComponents.LORE);
			helper.assertTrue(card.lines().stream().anyMatch(l -> l.getString().equals(BADGE)
				&& net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GOLD).equals(l.getStyle().getColor())), "the badge isn't gold");
			helper.assertTrue(LifeStages.daysLeft(bram, today + 500) == -1, "an ageless elder's days left: " + LifeStages.daysLeft(bram, today + 500));

			// The other three, each for their own trait.
			accepted(helper, player, ida, LifeStages.Trait.GIFTED, "Ida (Gifted) takes the Evergreen Charm and will never leave us.");
			accepted(helper, player, odo, LifeStages.Trait.LEGEND, "Odo (a Legend) takes the Evergreen Charm and will never leave us.");
			accepted(helper, player, wren, LifeStages.Trait.HAPPY, "Wren (happy for many days) takes the Evergreen Charm and will never leave us.");
			helper.assertTrue(chronicle(hall).stream().filter(l -> l.contains(" will never leave us")).count() == 4, "chronicle: " + chronicle(hall));

			// One charm, one villager: a second is refused and kept. A creative player keeps theirs.
			ItemStack second = new ItemStack(ModItems.EVERGREEN_CHARM);
			say(helper, LifeStages.offer(player, bram, second), LifeStages.Outcome.ALREADY, "Bram is already ageless.");
			helper.assertTrue(second.getCount() == 1, "a refused charm was used up");
			Villager fenn = villager(helper, new BlockPos(15, 2, 6), "Fenn", VillagerProfession.MASON, 5);
			LifeStages.setAdultSince(fenn, today - 200);
			player.setGameMode(GameType.CREATIVE);
			say(helper, LifeStages.offer(player, fenn, second), LifeStages.Outcome.ACCEPTED, "Fenn (a Master of their trade) takes the Evergreen Charm and will never leave us.");
			helper.assertTrue(second.getCount() == 1, "a creative player's charm was used up");
			helper.succeed();
		});
	}

	/** A plain elder, a Master not yet an elder, someone whose age nobody knows, a child: each refuses with the reason and the charm is kept. */
	//$ gametest_ticks_batch AREA '100' '"agelessRefused"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "agelessRefused")
	public void anyoneElseRefusesAndTheCharmIsKept(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager dara = villager(helper, new BlockPos(5, 2, 6), "Dara", VillagerProfession.FARMER, 4);
		Villager fenn = villager(helper, new BlockPos(7, 2, 6), "Fenn", VillagerProfession.MASON, 5);
		Villager gus = villager(helper, new BlockPos(9, 2, 6), "Gus", VillagerProfession.CLERIC, 5);
		Villager nit = villager(helper, new BlockPos(11, 2, 6), "Nit", VillagerProfession.NITWIT, 5);
		Villager pip = villager(helper, new BlockPos(13, 2, 6), "Pip", VillagerProfession.NONE, 1);
		pip.setBaby(true);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(dara, today - 150);
			ModAttachments.HAPPY_STREAK.set(dara, new LifeStages.Streak(19, today));
			LifeStages.setAdultSince(fenn, today - 30);
			LifeStages.setAdultSince(nit, today - 150);
			LifeStages.setAdultSince(pip, today - 150); // (a child is never an elder, whatever their record says)
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			player.setShiftKeyDown(true);
			ItemStack charm = new ItemStack(ModItems.EVERGREEN_CHARM);

			say(helper, LifeStages.offer(player, dara, charm), LifeStages.Outcome.NO_GOOD_TRAIT, "Dara won't take it. The charm is for an elder who is a Master of their "
				+ "trade, Gifted, a Legend, or happy (mood 80 or more) for 20 days running. Happy days so far: 19.");
			say(helper, LifeStages.offer(player, nit, charm), LifeStages.Outcome.NO_GOOD_TRAIT, "Nit won't take it. The charm is for an elder who is a Master of their "
				+ "trade, Gifted, a Legend, or happy (mood 80 or more) for 20 days running. Happy days so far: 0.");
			say(helper, LifeStages.offer(player, fenn, charm), LifeStages.Outcome.NOT_AN_ELDER, "Fenn is not an elder yet (grown 30 of 120 days): the Evergreen Charm is for elders.");
			say(helper, LifeStages.offer(player, gus, charm), LifeStages.Outcome.NOT_AN_ELDER, "Gus is not an elder: the Evergreen Charm is for elders.");
			say(helper, LifeStages.offer(player, pip, charm), LifeStages.Outcome.NOT_AN_ELDER, "Pip is not an elder: the Evergreen Charm is for elders.");
			helper.assertTrue(charm.getCount() == 1, "a refused charm was used up");
			for (Villager v : List.of(dara, fenn, gus, nit, pip)) {
				helper.assertTrue(!LifeStages.isAgeless(v) && !ModAttachments.AGELESS.has(v), v.getName().getString() + " was made ageless");
			}
			// Through the sneak-right-click too: refused, the charm still in hand.
			player.setItemInHand(InteractionHand.MAIN_HAND, charm);
			UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, dara, null);
			helper.assertTrue(player.getMainHandItem().is(ModItems.EVERGREEN_CHARM) && !LifeStages.isAgeless(dara), "the refused right-click");
			// The day they turn 120 they are an elder; the day before, not.
			LifeStages.setAdultSince(fenn, today - 119);
			helper.assertTrue(!LifeStages.isElder(fenn, today) && LifeStages.isElder(fenn, today + 1), "an elder from 120 grown days");
			// A happy day more and Dara takes it.
			ModAttachments.HAPPY_STREAK.set(dara, new LifeStages.Streak(20, today));
			say(helper, LifeStages.offer(player, dara, charm), LifeStages.Outcome.ACCEPTED, "Dara (happy for many days) takes the Evergreen Charm and will never leave us.");
			helper.assertTrue(charm.isEmpty(), "the charm wasn't used up");
			helper.succeed();
		});
	}

	/** Happy days are counted from the villager's real mood, once a day; a day under 80 starts the count again. */
	//$ gametest_ticks_batch AREA '100' '"agelessHappyDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "agelessHappyDays")
	public void happyDaysAreCountedFromTheMood(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(new BlockPos(5, 2, 4), Blocks.RED_BED);
		Villager wren = villager(helper, new BlockPos(5, 2, 6), "Wren", VillagerProfession.FARMER, 2);
		villager(helper, new BlockPos(6, 2, 6), "Tom", VillagerProfession.FARMER, 1); // company
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper);
			Moods.ENABLED = true; // (off in GameTests otherwise)
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(wren, today - 150);
			// Fed, a bed of her own, a job, company.
			ModAttachments.LAST_MEAL.set(wren, level.getGameTime());
			wren.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(5, 2, 4))));
			Moods.Mood mood = Moods.work(level, wren);
			helper.assertTrue(mood.score() >= LifeStages.HAPPY_MOOD, "the staged mood should be 80 or more: " + mood.score() + " "
				+ mood.good().stream().map(Component::getString).toList() + " " + mood.bad().stream().map(Component::getString).toList());
			for (int d = 1; d <= 19; d++) {
				LifeStages.countMood(wren, today + d);
				helper.assertTrue(LifeStages.happyDays(wren) == d, "after " + d + " happy days: " + LifeStages.happyDays(wren));
			}
			LifeStages.countMood(wren, today + 19);
			helper.assertTrue(LifeStages.happyDays(wren) == 19, "a day counted twice: " + LifeStages.happyDays(wren));
			helper.assertTrue(LifeStages.goodTrait(wren) == null, "19 days are enough: " + LifeStages.goodTrait(wren));
			LifeStages.countMood(wren, today + 20);
			helper.assertTrue(LifeStages.goodTrait(wren) == LifeStages.Trait.HAPPY, "20 happy days: " + LifeStages.goodTrait(wren));
			// The count is saved with her.
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(wren.saveWithoutId(new CompoundTag()));
			helper.assertTrue(LifeStages.happyDays(copy) == 20, "the count after a reload: " + LifeStages.happyDays(copy));
			// Hungry: a day under 80, and the count starts again.
			ModAttachments.LAST_MEAL.set(wren, level.getGameTime() - 5 * VillageNeeds.DAY);
			Moods.forget();
			helper.assertTrue(Moods.work(level, wren).score() < LifeStages.HAPPY_MOOD, "hungry and still happy: " + Moods.work(level, wren).score());
			LifeStages.countMood(wren, today + 21);
			helper.assertTrue(LifeStages.happyDays(wren) == 0 && LifeStages.goodTrait(wren) == null, "after an unhappy day: " + LifeStages.happyDays(wren));
			helper.succeed();
		});
	}

	/**
	 * Day by day past 40 elder days, asleep in the hall's round: the ageless elder stays, the ordinary one passes on the
	 * 40th and leaves a grave (so does one with no job and no name); the ageless one is still ageless after a reload.
	 */
	//$ gametest_ticks_batch AREA '100' '"agelessOutlives"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "agelessOutlives")
	public void anAgelessElderOutlivesFortyElderDaysAndAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		BlockPos[] beds = {new BlockPos(4, 2, 4), new BlockPos(8, 2, 4), new BlockPos(14, 2, 4)};
		for (BlockPos bed : beds) {
			helper.setBlock(bed, Blocks.RED_BED);
		}
		Villager bram = villager(helper, new BlockPos(4, 2, 6), "Bram", VillagerProfession.FARMER, 5);
		Villager dara = villager(helper, new BlockPos(8, 2, 6), "Dara", VillagerProfession.FARMER, 5);
		Villager nobody = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 6)); // no job, no name
		nobody.setNoAi(true);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			long today = Chronicle.day(level);
			List<Villager> all = List.of(bram, dara, nobody);
			for (Villager v : all) {
				LifeStages.setAdultSince(v, today - 120);
			}
			helper.assertTrue(LifeStages.makeAgeless(level, bram, Component.literal("Tester")).outcome() == LifeStages.Outcome.ACCEPTED, "Bram takes the charm");
			bram.startSleeping(helper.absolutePos(beds[0]));
			dara.startSleeping(helper.absolutePos(beds[1]));
			nobody.startSleeping(helper.absolutePos(beds[2]));
			helper.assertTrue(dara.isSleeping(), "Dara isn't asleep");
			for (int elderDays = 0; elderDays <= 45; elderDays++) {
				for (Villager v : all) {
					LifeStages.setAdultSince(v, today - 120 - elderDays);
				}
				LifeStages.round(level, hallPos, all);
				boolean gone = elderDays >= LifeStages.PASSING_DAYS;
				helper.assertTrue(bram.isAlive(), "the ageless elder passed after " + elderDays + " elder days");
				helper.assertTrue(dara.isAlive() != gone, "Dara after " + elderDays + " elder days: alive " + dara.isAlive());
				helper.assertTrue(nobody.isAlive() != gone, "the nameless elder after " + elderDays + " elder days: alive " + nobody.isAlive());
				if (elderDays == 35) {
					List<String> card = lore(level, hallPos, dara);
					helper.assertTrue(card.contains("Their time comes in 5 days (an Evergreen Charm keeps them)"), "five days before: " + card);
					helper.assertTrue(lore(level, hallPos, bram).stream().noneMatch(l -> l.startsWith("Their time")), "the ageless elder's card warns");
				}
			}
			helper.assertTrue(LifeStages.elderDays(bram, today) == 45 && LifeStages.isElder(bram, today), "Bram is an elder of 45 days");
			helper.assertTrue(graves(helper).size() == 2, "a grave each for Dara and the nameless elder: " + graves(helper));
			helper.assertTrue(graves(helper).stream().anyMatch(g -> g.name().getString().equals("Dara")), "Dara's grave");
			helper.assertTrue(chronicle(hall).contains("Dara passed away in their sleep, an elder"), "chronicle: " + chronicle(hall));

			// Saved and loaded: still ageless, the same age, still on the hall's list with the badge.
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(bram.saveWithoutId(new CompoundTag()));
			helper.assertTrue(LifeStages.isAgeless(copy), "ageless after a reload");
			helper.assertTrue(Long.valueOf(today - 165).equals(LifeStages.adultSince(copy)), "the day they grew up after a reload: " + LifeStages.adultSince(copy));
			helper.assertTrue(LifeStages.daysLeft(copy, today) == -1 && LifeStages.daysLeft(copy, today + 1000) == -1, "the reloaded elder's days left");
			// A villager from a save before all this: not ageless, no age, nothing written.
			Villager old = EntityType.VILLAGER.create(level);
			old.load(EntityType.VILLAGER.create(level).saveWithoutId(new CompoundTag()));
			helper.assertTrue(!LifeStages.isAgeless(old) && LifeStages.adultSince(old) == null && !LifeStages.isElder(old, today)
				&& LifeStages.happyDays(old) == 0 && LifeStages.daysLeft(old, today) == -1, "an older save's villager");
			helper.succeed();
		});
	}

	/** In the hall's own round an elder of 40 elder days, asleep, passes and leaves a grave; brought back, they have 40 more days and can take a charm. */
	//$ gametest_ticks_batch AREA '200' '"elderPasses"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "elderPasses")
	public void anElderPassesInTheirSleepAndLeavesAGrave(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long today = Chronicle.day(level);
		BlockPos bed = new BlockPos(5, 2, 4);
		helper.setBlock(bed, Blocks.RED_BED);
		Villager dara = villager(helper, new BlockPos(5, 2, 6), "Dara", VillagerProfession.FARMER, 5);
		LifeStages.setAdultSince(dara, today - 160);
		Villager young = villager(helper, new BlockPos(9, 2, 6), "Tom", VillagerProfession.FARMER, 5);
		LifeStages.setAdultSince(young, today - 159); // 39 elder days: not yet
		helper.setBlock(new BlockPos(9, 2, 4), Blocks.RED_BED);
		dara.startSleeping(helper.absolutePos(bed));
		young.startSleeping(helper.absolutePos(new BlockPos(9, 2, 4)));
		village(helper); // the hall's first tick is a round
		String[] later = new String[1];
		boolean[] passed = new boolean[1];
		helper.succeedWhen(() -> {
			if (!passed[0]) {
				helper.assertTrue(!dara.isAlive(), "Dara is still alive");
				List<GraveBlockEntity> graves = graves(helper);
				helper.assertTrue(graves.size() == 1 && graves.get(0).name().getString().equals("Dara"), "graves: " + graves.stream().map(g -> g.name().getString()).toList());
				passed[0] = true;
				// From here on once only (bringing her back takes the grave away): what fails is kept and reported.
				try {
					afterPassing(helper, graves.get(0), young, today);
				} catch (net.minecraft.gametest.framework.GameTestAssertException e) {
					later[0] = e.getMessage();
				}
			}
			helper.assertTrue(later[0] == null, "after Dara passed: " + later[0]);
		});
	}

	private static void afterPassing(GameTestHelper helper, GraveBlockEntity grave, Villager young, long today) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Graves.epitaph(grave).getString().contains("Dara"), "epitaph: " + Graves.epitaph(grave).getString());
		helper.assertTrue(young.isAlive(), "an elder of 39 days passed");
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.DEATH && e.text().getString().equals("Dara passed away in their sleep, an elder")),
			"chronicle: " + chronicle(hall));
		// An Undertaker's work: back as she was, with 40 more days, and now she can be given a charm.
		Villager back = Graves.revive(level, grave.getBlockPos());
		helper.assertTrue(back != null && back.isAlive() && back.getName().getString().equals("Dara")
			&& back.getVillagerData().getProfession() == VillagerProfession.FARMER && back.getVillagerData().getLevel() == 5, "Dara isn't back as she was: " + back);
		helper.assertTrue(graves(helper).isEmpty(), "the grave is still there");
		helper.assertTrue(LifeStages.isElder(back, today) && LifeStages.daysLeft(back, today) == LifeStages.PASSING_DAYS, "days left once back: " + LifeStages.daysLeft(back, today));
		LifeStages.round(level, helper.absolutePos(HALL), List.of(back));
		helper.assertTrue(back.isAlive(), "she passed again the same night");
		helper.assertTrue(LifeStages.daysLeft(back, today + 39) == 1 && LifeStages.daysLeft(back, today + 40) == 0, "her time comes again 40 days on: " + LifeStages.daysLeft(back, today + 40));
		helper.assertTrue(LifeStages.makeAgeless(level, back, Component.literal("Tester")).outcome() == LifeStages.Outcome.ACCEPTED, "the charm, once back");
		helper.assertTrue(LifeStages.daysLeft(back, today + 40) == -1, "ageless now");
	}

	/** Each switch off: charms refused (those already ageless stay so), nobody passes, nobody is an elder. A child grown up gets today. */
	//$ gametest_ticks_batch AREA '100' '"agelessConfig"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "agelessConfig")
	public void theConfigSwitches(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.RED_BED);
		helper.setBlock(new BlockPos(8, 2, 4), Blocks.RED_BED);
		Villager bram = villager(helper, new BlockPos(4, 2, 6), "Bram", VillagerProfession.FARMER, 5);
		Villager dara = villager(helper, new BlockPos(8, 2, 6), "Dara", VillagerProfession.FARMER, 5);
		Villager pip = villager(helper, new BlockPos(12, 2, 6), "Pip", VillagerProfession.NONE, 1);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 300);
			LifeStages.setAdultSince(dara, today - 300);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			player.setShiftKeyDown(true);
			ItemStack charm = new ItemStack(ModItems.EVERGREEN_CHARM);
			helper.assertTrue(LifeStages.makeAgeless(level, bram, Component.literal("Tester")).outcome() == LifeStages.Outcome.ACCEPTED, "Bram takes a charm first");
			bram.startSleeping(helper.absolutePos(new BlockPos(4, 2, 4)));
			dara.startSleeping(helper.absolutePos(new BlockPos(8, 2, 4)));

			// agelessElders off: refused and kept; Bram, ageless already, still outlives his days.
			LifeStages.AGELESS = false;
			say(helper, LifeStages.offer(player, dara, charm), LifeStages.Outcome.DISABLED, "Evergreen Charms are switched off on this server.");
			helper.assertTrue(charm.getCount() == 1 && !LifeStages.isAgeless(dara), "a charm refused by the config was used");
			LifeStages.round(level, hallPos, List.of(bram));
			helper.assertTrue(bram.isAlive() && lore(level, hallPos, bram).contains(BADGE), "an elder already ageless, with charms off");
			helper.assertTrue(lore(level, hallPos, dara).contains("Their time comes tonight"), "Dara's card: " + lore(level, hallPos, dara));

			// elderPassing off: nobody passes, and nobody needs a charm.
			LifeStages.AGELESS = true;
			LifeStages.PASSING = false;
			LifeStages.round(level, hallPos, List.of(dara));
			helper.assertTrue(dara.isAlive() && LifeStages.daysLeft(dara, today) == -1, "an elder passed with elderPassing off");
			say(helper, LifeStages.offer(player, dara, charm), LifeStages.Outcome.NOT_NEEDED, "Elders don't pass away on this server: nobody needs an Evergreen Charm.");
			helper.assertTrue(lore(level, hallPos, dara).contains("Elder · grown 300 days") && lore(level, hallPos, dara).stream().noneMatch(l -> l.startsWith("Their time")),
				"Dara's card with elderPassing off: " + lore(level, hallPos, dara));

			// villagerAges off: nobody is an elder, so nobody passes; the day they grew up is kept.
			LifeStages.PASSING = true;
			LifeStages.AGES = false;
			LifeStages.round(level, hallPos, List.of(dara));
			helper.assertTrue(dara.isAlive() && !LifeStages.isElder(dara, today), "an elder with villagerAges off");
			helper.assertTrue(Long.valueOf(today - 300).equals(LifeStages.adultSince(dara)), "the day she grew up was lost");
			say(helper, LifeStages.offer(player, dara, charm), LifeStages.Outcome.NOT_AN_ELDER, "Dara is not an elder: the Evergreen Charm is for elders.");
			helper.assertTrue(lore(level, hallPos, dara).stream().noneMatch(l -> l.startsWith("Elder")), "an elder line with villagerAges off");

			// villagerElderDays: an elder sooner or later.
			LifeStages.AGES = true;
			LifeStages.ELDER_DAYS = 400;
			helper.assertTrue(!LifeStages.isElder(dara, today) && LifeStages.isElder(dara, today + 100), "an elder at 400 grown days");

			// All on again: the same charm is taken now, and Dara would have passed tonight.
			LifeStages.ELDER_DAYS = 120;
			helper.assertTrue(LifeStages.daysLeft(dara, today) == 0, "Dara's time with everything on: " + LifeStages.daysLeft(dara, today));
			say(helper, LifeStages.offer(player, dara, charm), LifeStages.Outcome.ACCEPTED, "Dara (a Master of their trade) takes the Evergreen Charm and will never leave us.");
			LifeStages.round(level, hallPos, List.of(dara));
			helper.assertTrue(dara.isAlive() && charm.isEmpty(), "Dara, ageless");

			// A child who grows up in the hall's round is grown from today.
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Mara"), Component.literal("Tom"), "", "", false));
			helper.assertTrue(LifeStages.adultSince(pip) == null && LifeStages.grownDays(pip, today) == -1, "Pip's age before");
			Families.round(level, hallPos, pip, RandomSource.create(7));
			helper.assertTrue(Long.valueOf(today).equals(LifeStages.adultSince(pip)) && LifeStages.grownDays(pip, today + 3) == 3, "grown up today: " + LifeStages.adultSince(pip));
			helper.succeed();
		});
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	private static void accepted(GameTestHelper helper, ServerPlayer player, Villager villager, LifeStages.Trait trait, String message) {
		ItemStack charm = new ItemStack(ModItems.EVERGREEN_CHARM);
		LifeStages.Offer offer = LifeStages.offer(player, villager, charm);
		say(helper, offer, LifeStages.Outcome.ACCEPTED, message);
		helper.assertTrue(offer.trait() == trait, villager.getName().getString() + "'s trait: " + offer.trait());
		helper.assertTrue(charm.isEmpty(), "the charm wasn't used up");
		helper.assertTrue(LifeStages.isAgeless(villager), villager.getName().getString() + " isn't ageless");
		helper.assertTrue(lore(helper.getLevel(), helper.absolutePos(HALL), villager).contains(BADGE), villager.getName().getString() + "'s badge");
	}

	private static void say(GameTestHelper helper, LifeStages.Offer offer, LifeStages.Outcome outcome, String message) {
		helper.assertTrue(offer.outcome() == outcome, "expected " + outcome + ", got " + offer.outcome() + ": " + offer.message().getString());
		helper.assertTrue(offer.message().getString().equals(message), "said: " + offer.message().getString());
	}

	/** A Village Hall (the hall's radius 16 for the test); the life-stage switches are on again when the test ends. */
	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LifeStages.AGELESS = true;
			LifeStages.PASSING = true;
			LifeStages.AGES = true;
			LifeStages.ELDER_DAYS = 120;
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

	/** The hall after its first round, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	private static List<String> lore(ServerLevel level, BlockPos hall, Villager villager) {
		ItemLore lore = VillageHallScreen.person(level, hall, villager).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** Every grave in the test area. */
	private static List<GraveBlockEntity> graves(GameTestHelper helper) {
		List<GraveBlockEntity> found = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(22, 6, 22)))) {
			if (helper.getLevel().getBlockEntity(p) instanceof GraveBlockEntity grave) {
				found.add(grave);
			}
		}
		return found;
	}
}
