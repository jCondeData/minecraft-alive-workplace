package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.story.Tastes;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gifts (ROADMAP 31.6): the two recipes (the Gift keeps the item), each taste band's points, the day and week limits
 * and their lines, the name day counting three times (and the hall's tooltip), where the item goes, the most-specific
 * taste rule, a data pack's taste file winning over ours, the first set of taste files as the roadmap lists it, the
 * Bottle o' Enchanting, unnamed villagers, {@code friendship} off, a save and reload, and every sentence a player reads.
 */
public class GiftGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	// --- Setting up -----------------------------------------------------------------------------------------------

	/** A hall, alone in the batch; {@code then} runs once the POIs placed so far are in. The clock is put back afterwards. */
	private static void village(GameTestHelper helper, Runnable then) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long dayTime = level.getDayTime();
		boolean friendship = Friendship.ENABLED;
		Map<ResourceLocation, JsonElement> files = Tastes.files(level.getServer().getResourceManager());
		Tastes.load(files); // the files as shipped, whatever an earlier test loaded
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Friendship.ENABLED = friendship;
			level.setDayTime(dayTime);
			Tastes.load(files);
			CivicEffects.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			CivicEffects.forget(); // the hall's POI goes in after the tick it was placed
			then.run();
		});
	}

	private static int uuids;

	/** A UUID nobody in {@code level} has, whose owner has exactly {@code traits} (traits come from the UUID; Nimble when none is given). */
	private static UUID uuidWith(ServerLevel level, Traits.Trait... traits) {
		List<Traits.Trait> want = traits.length == 0 ? List.of(Traits.Trait.NIMBLE) : List.of(traits);
		java.util.Random random = new java.util.Random(316L + 7919L * uuids++);
		for (int i = 0; i < 100_000; i++) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			if (Traits.of(id).equals(want) && level.getEntity(id) == null) {
				return id;
			}
		}
		throw new IllegalStateException("no UUID with traits " + want);
	}

	/**
	 * A villager standing at {@code at} with exactly {@code traits} (Nimble when none is given: its tastes, sugar and
	 * rabbit's feet, are in no test here), named {@code name} unless null, of {@code profession}.
	 */
	private static Villager villager(GameTestHelper helper, BlockPos at, String name, VillagerProfession profession, Traits.Trait... traits) {
		ServerLevel level = helper.getLevel();
		Villager v = loose(level, profession, traits);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		v.moveTo(pos.x, pos.y, pos.z, 0, 0);
		v.setNoAi(true);
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		level.addFreshEntity(v);
		return v;
	}

	/** A villager of {@code profession} with {@code traits} who isn't in the world: for asking {@link Tastes} only. */
	private static Villager loose(ServerLevel level, VillagerProfession profession, Traits.Trait... traits) {
		Villager v = EntityType.VILLAGER.create(level);
		v.setUUID(uuidWith(level, traits));
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		return v;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
	}

	/** The first day of a week from which, for ten days, it is none of the {@code villagers}' name day. */
	private static long quietWeek(Villager... villagers) {
		for (long day = 8; ; day += Gifts.WEEK) {
			boolean quiet = true;
			for (Villager v : villagers) {
				for (int i = 0; i < 10; i++) {
					quiet &= Gifts.daysToNameDay(v.getUUID(), day + i) != 0;
				}
			}
			if (quiet) {
				return day;
			}
		}
	}

	/** {@code player} holds a Gift of {@code item} and gives it to {@code villager}. */
	private static Gifts.Result give(ServerPlayer player, Villager villager, Item item) {
		player.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(item), player.getGameProfile().getName()));
		return Gifts.give(player, villager, player.getMainHandItem());
	}

	/** What a client sends when its player right-clicks {@code villager}: the click at a spot on them, where the use-entity callbacks run. */
	private static void rightClick(ServerPlayer player, Villager villager) {
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
	}

	private static String said(Villager villager, ServerLevel level) {
		WorkerStatus.Entry entry = WorkerStatus.get(villager, level.getGameTime());
		return entry == null || entry.line() == null ? "" : entry.line().getString();
	}

	private static ItemStack craft(ServerLevel level, ItemStack... grid) {
		CraftingInput input = CraftingInput.of(grid.length, 1, List.of(grid));
		return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	// --- The items and their recipes ------------------------------------------------------------------------------

	/** Paper, string and any dye make four Gift Wrap; Gift Wrap and any one item make a Gift that keeps the item, whole. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRecipesWrapAndKeepTheItem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (Item dye : List.of(Items.RED_DYE, Items.BLUE_DYE, Items.WHITE_DYE, Items.BLACK_DYE)) {
			ItemStack wrap = craft(level, new ItemStack(Items.PAPER), new ItemStack(Items.STRING), new ItemStack(dye));
			helper.assertTrue(wrap.is(ModItems.GIFT_WRAP) && wrap.getCount() == 4, "paper, string and " + dye + " make: " + wrap);
		}
		helper.assertTrue(craft(level, new ItemStack(Items.PAPER), new ItemStack(Items.STRING)).isEmpty(), "gift wrap without a dye");

		// The Gift keeps the item: one of it, with everything on it.
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Biter"));
		sword.setDamageValue(7);
		ItemStack gift = craft(level, new ItemStack(ModItems.GIFT_WRAP), sword);
		helper.assertTrue(gift.is(ModItems.GIFT) && gift.getCount() == 1, "gift wrap and a sword make: " + gift);
		helper.assertTrue(ItemStack.matches(Gifts.contents(gift), sword), "the gift holds " + Gifts.contents(gift) + ", not the sword as it was");
		ItemStack cookie = craft(level, new ItemStack(Items.COOKIE, 5), new ItemStack(ModItems.GIFT_WRAP, 3));
		helper.assertTrue(ItemStack.matches(Gifts.contents(cookie), new ItemStack(Items.COOKIE)), "a stack of five wraps one: " + Gifts.contents(cookie));

		// The grid: one wrap and one item are used up, and a wrapped bucket of milk leaves no bucket behind.
		CraftingInput milk = CraftingInput.of(2, 1, List.of(new ItemStack(ModItems.GIFT_WRAP), new ItemStack(Items.MILK_BUCKET)));
		RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, milk, level).orElse(null);
		helper.assertTrue(recipe != null && recipe.id().equals(AliveWorkplace.id("gift")), "the gift recipe: " + recipe);
		helper.assertTrue(recipe.value().getRemainingItems(milk).stream().allMatch(ItemStack::isEmpty), "wrapping milk left: " + recipe.value().getRemainingItems(milk));
		helper.assertTrue(Gifts.contents(recipe.value().assemble(milk, level.registryAccess())).is(Items.MILK_BUCKET), "the milk isn't in the gift");

		// What can't be wrapped: nothing, two things, more wrap, a Gift.
		helper.assertTrue(craft(level, new ItemStack(ModItems.GIFT_WRAP)).isEmpty(), "gift wrap alone made something");
		helper.assertTrue(craft(level, new ItemStack(ModItems.GIFT_WRAP), new ItemStack(ModItems.GIFT_WRAP)).isEmpty(), "gift wrap wrapped gift wrap");
		helper.assertTrue(craft(level, new ItemStack(ModItems.GIFT_WRAP), new ItemStack(Items.CAKE), new ItemStack(Items.BREAD)).isEmpty(), "two items in one gift");
		helper.assertTrue(craft(level, new ItemStack(ModItems.GIFT_WRAP), gift.copy()).isEmpty(), "a gift wrapped again");

		// The component survives a save, and two Gifts of the same thing are the same item.
		ItemStack again = ItemStack.parse(level.registryAccess(), gift.save(level.registryAccess())).orElseThrow();
		helper.assertTrue(ItemStack.matches(again, gift) && ItemStack.matches(Gifts.contents(again), sword), "after a save: " + again);
		helper.assertTrue(gift.get(ModComponents.GIFT).hashCode() == again.get(ModComponents.GIFT).hashCode(), "equal gifts hash differently");

		// Both recipes have their recipe-book unlock, which gives the recipe to whoever holds an ingredient.
		for (String id : List.of("gift_wrap", "gift")) {
			var unlock = level.getServer().getAdvancements().get(AliveWorkplace.id("recipes/misc/" + id));
			helper.assertTrue(unlock != null && unlock.value().rewards().recipes().contains(AliveWorkplace.id(id)), "no recipe-book unlock for " + id);
		}
		helper.succeed();
	}

	/** Taking a Gift off the grid signs it: the tooltip says "From <player>", and never what's inside. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTooltipSaysWhoItIsFromAndNotWhatIsInside(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		String name = player.getGameProfile().getName();
		ItemStack gift = craft(level, new ItemStack(ModItems.GIFT_WRAP), new ItemStack(Items.CAKE));
		helper.assertTrue(Gifts.from(gift).isEmpty(), "a gift on the grid is already from " + Gifts.from(gift));
		List<String> unsigned = gift.getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
		helper.assertTrue(unsigned.equals(List.of("Gift", "Right-click a named villager to give it")), "an unsigned gift's tooltip: " + unsigned);
		gift.onCraftedBy(level, player, 1); // what the result slot does when the player takes it
		helper.assertTrue(Gifts.from(gift).equals(name), "from: " + Gifts.from(gift));
		helper.assertTrue(Gifts.contents(gift).is(Items.CAKE), "signing it lost the cake");
		List<String> lines = gift.getTooltipLines(Item.TooltipContext.of(level), player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
		helper.assertTrue(lines.equals(List.of("Gift", "From " + name, "Right-click a named villager to give it")), "the tooltip: " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("Cake")), "the tooltip gives away what's inside: " + lines);
		helper.assertTrue(new ItemStack(ModItems.GIFT_WRAP).getHoverName().getString().equals("Gift Wrap"), "gift wrap's name");
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// --- Giving ---------------------------------------------------------------------------------------------------

	/** Right-clicking a named villager with a Gift: loved +80, liked +45, neutral +20, disliked −20, hated −40, each with its line. */
	//$ gametest_ticks_batch AREA '60' '"giftBands"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "giftBands")
	public void eachTasteBandGivesItsPoints(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			setDay(level, quietWeek(dara));
			record Case(Item item, Tastes.Band band, int start, int end, List<String> lines) {
			}
			String me = "test-mock-player";
			List<Case> cases = List.of(
				new Case(Items.CAKE, Tastes.Band.LOVED, 0, 80, List.of("Cake! You remembered.", "Cake? For me? It's my favourite, " + me + "!")),
				new Case(Items.COOKIE, Tastes.Band.LIKED, 0, 45, List.of("Cookie! How kind of you, " + me + ".", "Oh, Cookie. I do like that. Thank you!")),
				new Case(Items.STICK, Tastes.Band.NEUTRAL, 0, 20, List.of("Stick. Thank you, " + me + ".", "A present! Stick... that's thoughtful.")),
				new Case(Items.DIRT, Tastes.Band.DISLIKED, 100, 80, List.of("Dirt? Oh. You shouldn't have.", "Hm. Dirt. I'll find somewhere to put it.")),
				new Case(Items.ROTTEN_FLESH, Tastes.Band.HATED, 100, 60, List.of("Rotten Flesh?! What were you thinking, " + me + "?",
					"Ugh, Rotten Flesh. Take it as a hint, " + me + ": never again.")));
			for (Case c : cases) {
				ServerPlayer player = player(helper, new BlockPos(5, 2, 8));
				helper.assertTrue(player.getGameProfile().getName().equals(me), "the mock player is called " + player.getGameProfile().getName());
				if (c.start() > 0) {
					Friendship.add(dara, player, c.start());
				}
				// The player's own entry point: the click, with the Gift in hand.
				player.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(c.item()), me));
				rightClick(player, dara);
				int points = Friendship.points(dara, player.getUUID());
				helper.assertTrue(points == c.end(), c.band() + " (" + c.item() + "): " + c.start() + " became " + points + ", not " + c.end());
				helper.assertTrue(player.getMainHandItem().isEmpty(), "the gift of " + c.item() + " is still in hand");
				helper.assertTrue(c.lines().contains(said(dara, level)), c.band() + ": she said \"" + said(dara, level) + "\"");
				helper.assertTrue(dara.getUnhappyCounter() > 0 == c.band().points < 0, c.band() + ": head shake " + dara.getUnhappyCounter());
				dara.setUnhappyCounter(0);
				level.getServer().getPlayerList().remove(player);
			}
			// The numbers themselves, as the roadmap gives them.
			helper.assertTrue(Tastes.Band.LOVED.points == 80 && Tastes.Band.LIKED.points == 45 && Tastes.Band.NEUTRAL.points == 20
				&& Tastes.Band.DISLIKED.points == -20 && Tastes.Band.HATED.points == -40, "the bands' points changed");
			// Every line of every band is written (two each), and reads with the item and the giver.
			for (Tastes.Band band : Tastes.Band.values()) {
				for (int i = 0; i < Gifts.LINES; i++) {
					String line = Component.translatable("gift.aliveworkplace." + band.id() + "." + i, "Cake", "Jesse").getString();
					helper.assertTrue(line.contains("Cake") && !line.contains("gift.aliveworkplace") && !line.contains("%"), band + " line " + i + ": " + line);
				}
			}
			// A creative player keeps the gift; the villager still takes one.
			ServerPlayer creative = player(helper, new BlockPos(5, 2, 8));
			creative.setGameMode(GameType.CREATIVE);
			Gifts.Result kept = give(creative, dara, Items.CAKE);
			helper.assertTrue(kept.outcome() == Gifts.Outcome.GIVEN && creative.getMainHandItem().is(ModItems.GIFT), "creative: " + kept + ", holding " + creative.getMainHandItem());
			level.getServer().getPlayerList().remove(creative);
			helper.succeed();
		});
	}

	/** One gift a day and two a week from each player, refused with a line; the count is saved on the villager. */
	//$ gametest_ticks_batch AREA '60' '"giftLimits"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "giftLimits")
	public void oneGiftADayAndTwoAWeek(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			ServerPlayer alex = player(helper, new BlockPos(5, 2, 8));
			ServerPlayer sam = player(helper, new BlockPos(6, 2, 8));
			String name = alex.getGameProfile().getName();
			UUID me = alex.getUUID();
			long monday = quietWeek(dara);

			setDay(level, monday);
			helper.assertTrue(give(alex, dara, Items.STICK).outcome() == Gifts.Outcome.GIVEN, "the first gift");
			Gifts.Result second = give(alex, dara, Items.CAKE);
			helper.assertTrue(second.outcome() == Gifts.Outcome.DAY_LIMIT, "a second gift the same day: " + second);
			helper.assertTrue(second.line().getString().equals("You already gave me something today, " + name + ". Keep it for tomorrow."), "the day line: " + second.line().getString());
			helper.assertTrue(said(dara, level).equals(second.line().getString()), "over her head: " + said(dara, level));
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Gifts.contents(alex.getMainHandItem()).is(Items.CAKE), "the refused gift left the hand: " + alex.getMainHandItem());
			helper.assertTrue(Friendship.points(dara, me) == 20, "a refused gift counted: " + Friendship.points(dara, me));
			// The same, through the click.
			rightClick(alex, dara);
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Friendship.points(dara, me) == 20, "the click got past the day limit");
			// Someone else can still give her one today.
			helper.assertTrue(give(sam, dara, Items.STICK).outcome() == Gifts.Outcome.GIVEN && Friendship.points(dara, sam.getUUID()) == 20, "sam's gift the same day");

			setDay(level, monday + 1);
			helper.assertTrue(give(alex, dara, Items.STICK).outcome() == Gifts.Outcome.GIVEN && Friendship.points(dara, me) == 40, "the second day's gift");

			// Saved on the villager: a reloaded Dara still knows about both gifts.
			Friendship.Bond bond = Friendship.of(dara).bond(me);
			helper.assertTrue(bond.giftDay() == monday + 1 && bond.giftWeek() == Gifts.week(monday) && bond.giftsWeek() == 2, "the bond: " + bond);
			CompoundTag saved = dara.saveWithoutId(new CompoundTag());
			dara.discard();
			Villager back = EntityType.VILLAGER.create(level);
			back.load(saved);
			back.setUUID(dara.getUUID());
			level.addFreshEntity(back);
			CivicEffects.forget();
			helper.assertTrue(Friendship.of(back).bond(me).equals(bond), "after a reload: " + Friendship.of(back).bond(me));
			Gifts.Result sameDay = give(alex, back, Items.CAKE);
			helper.assertTrue(sameDay.outcome() == Gifts.Outcome.DAY_LIMIT, "a reload forgot today's gift: " + sameDay);

			setDay(level, monday + 2);
			Gifts.Result third = give(alex, back, Items.CAKE);
			helper.assertTrue(third.outcome() == Gifts.Outcome.WEEK_LIMIT, "a third gift in the week: " + third);
			helper.assertTrue(third.line().getString().equals("Two gifts in one week is plenty, " + name + ". Keep it for next week."), "the week line: " + third.line().getString());
			helper.assertTrue(said(back, level).equals(third.line().getString()), "over her head: " + said(back, level));
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Friendship.points(back, me) == 40, "the week's third gift counted");
			setDay(level, monday + 6);
			helper.assertTrue(give(alex, back, Items.CAKE).outcome() == Gifts.Outcome.WEEK_LIMIT, "the week's last day");

			// A new week.
			setDay(level, monday + 7);
			Gifts.Result next = give(alex, back, Items.CAKE);
			helper.assertTrue(next.outcome() == Gifts.Outcome.GIVEN && Friendship.points(back, me) == 120, "next week's gift: " + next + ", " + Friendship.points(back, me));
			helper.assertTrue(Friendship.of(back).bond(me).giftsWeek() == 1, "the new week's count: " + Friendship.of(back).bond(me));

			// A gift that can't move the points (a hated one at 0) still uses up the day.
			Villager bram = villager(helper, new BlockPos(3, 2, 5), "Bram", VillagerProfession.NONE);
			helper.assertTrue(give(alex, bram, Items.ROTTEN_FLESH).change() == 0, "below zero");
			helper.assertTrue(give(alex, bram, Items.CAKE).outcome() == Gifts.Outcome.DAY_LIMIT, "a hated gift at 0 hearts didn't use up the day");

			// A save from before gifts (31.5 wrote these fields with their defaults) takes a gift straight away.
			Friendship.Data old = Friendship.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString(
				"{\"players\": {\"" + me + "\": {\"points\": 300}}}")).getOrThrow();
			Villager cora = villager(helper, new BlockPos(7, 2, 5), "Cora", VillagerProfession.NONE);
			ModAttachments.FRIENDSHIP.set(cora, old);
			setDay(level, quietWeek(cora));
			helper.assertTrue(give(alex, cora, Items.CAKE).outcome() == Gifts.Outcome.GIVEN && Friendship.points(cora, me) == 380, "an old save's villager: " + Friendship.points(cora, me));
			helper.succeed();
		});
	}

	/** On a villager's name day (every 28 days, from their UUID) a gift counts three times; the hall's tooltip says when it is. */
	//$ gametest_ticks_batch AREA '60' '"giftNameDay"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "giftNameDay")
	public void aGiftOnTheirNameDayCountsThreeTimes(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			ServerPlayer alex = player(helper, new BlockPos(5, 2, 8));
			ServerPlayer sam = player(helper, new BlockPos(6, 2, 8));
			int offset = Gifts.nameDay(dara.getUUID());
			helper.assertTrue(offset >= 0 && offset < 28, "name day offset " + offset);
			long nameDay = 28 + offset + 1; // the second time round
			helper.assertTrue(Gifts.isNameDay(dara, nameDay) && Gifts.isNameDay(dara, nameDay + 28) && Gifts.isNameDay(dara, nameDay - 28), "every 28 days");
			for (long d = nameDay + 1; d < nameDay + 28; d++) {
				helper.assertTrue(!Gifts.isNameDay(dara, d), "day " + d + " is a name day too");
			}
			// Not everyone's is the same day.
			Set<Integer> days = new java.util.HashSet<>();
			for (int i = 0; i < 200; i++) {
				days.add(Gifts.nameDay(UUID.randomUUID()));
			}
			helper.assertTrue(days.size() >= 20, "name days fall on only " + days.size() + " of 28 days");

			java.util.function.Supplier<List<String>> lore = () -> VillageHallScreen.person(level, hall, dara, alex).getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
				.lines().stream().map(Component::getString).toList();
			setDay(level, nameDay - 5);
			helper.assertTrue(lore.get().contains("Name day: in 5 days"), "five days before: " + lore.get());
			helper.assertTrue(give(sam, dara, Items.CAKE).change() == 80, "an ordinary day's cake");
			setDay(level, nameDay - 1);
			helper.assertTrue(lore.get().contains("Name day: tomorrow"), "the day before: " + lore.get());
			setDay(level, nameDay + 1);
			helper.assertTrue(lore.get().contains("Name day: in 27 days"), "the day after: " + lore.get());

			setDay(level, nameDay);
			helper.assertTrue(Chronicle.day(level) == nameDay, "the clock: day " + Chronicle.day(level));
			helper.assertTrue(lore.get().contains("Name day: today (a gift counts three times)"), "on the day: " + lore.get());
			Gifts.Result cake = give(alex, dara, Items.CAKE);
			helper.assertTrue(cake.outcome() == Gifts.Outcome.GIVEN && cake.nameDay() && cake.change() == 240, "a loved gift on her name day: " + cake);
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 240, "points: " + Friendship.points(dara, alex.getUUID()));
			helper.assertTrue(cake.line().getString().endsWith(" And on my name day, too!") && cake.line().getString().startsWith("Cake"), "her line: " + cake.line().getString());
			helper.assertTrue(said(dara, level).equals(cake.line().getString()), "over her head: " + said(dara, level));
			// A hated gift costs three times as much, too (sam has 80).
			Gifts.Result flesh = give(sam, dara, Items.ROTTEN_FLESH);
			helper.assertTrue(flesh.change() == -80 && Friendship.points(dara, sam.getUUID()) == 0, "a hated gift on her name day: " + flesh);
			// The limits hold on a name day as on any other.
			helper.assertTrue(give(alex, dara, Items.CAKE).outcome() == Gifts.Outcome.DAY_LIMIT, "a second name-day gift");
			// With friendship off the hall doesn't show the name day.
			Friendship.ENABLED = false;
			helper.assertTrue(lore.get().stream().noneMatch(l -> l.startsWith("Name day")), "friendship off, the tooltip: " + lore.get());
			helper.succeed();
		});
	}

	/** The unwrapped item goes into a worker's supply chests, else the village's store, else their pockets, else it drops. */
	//$ gametest_ticks_batch AREA '60' '"giftChests"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "giftChests")
	public void theItemGoesIntoTheirChests(GameTestHelper helper) {
		BlockPos bench = new BlockPos(2, 2, 3);
		BlockPos chest = new BlockPos(2, 2, 4);
		BlockPos storehouse = new BlockPos(15, 2, 15);
		BlockPos storeChest = new BlockPos(15, 2, 14);
		helper.setBlock(bench, Blocks.STONECUTTER);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager mason = villager(helper, new BlockPos(4, 2, 4), "Marta", VillagerProfession.MASON);
			mason.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(bench)));
			Villager idle = villager(helper, new BlockPos(6, 2, 4), "Ines", VillagerProfession.NONE);
			setDay(level, quietWeek(mason, idle));
			List<BlockPos> hers = Gifts.chests(level, mason).get(0);
			helper.assertTrue(hers.equals(List.of(helper.absolutePos(chest))), "the mason's chests: " + hers);
			List<BlockPos> store = VillageNeeds.store(level, hall);
			helper.assertTrue(store.contains(helper.absolutePos(storeChest)) && !store.contains(helper.absolutePos(chest)), "the village's store: " + store);

			// A worker: her own supply chests.
			Gifts.Result toMason = give(player(helper, new BlockPos(4, 2, 6)), mason, Items.SPYGLASS);
			helper.assertTrue(toMason.stored() == Gifts.Stored.CHESTS && SupplyContainers.count(level, hers, Items.SPYGLASS) == 1, "the mason's spyglass: " + toMason);
			helper.assertTrue(SupplyContainers.count(level, store, Items.SPYGLASS) == 0, "it went to the store as well");
			helper.assertTrue(SupplyContainers.count(level, hers, ModItems.GIFT) == 0, "the wrapping went into the chest");

			// Someone without a workstation: the village's store.
			Gifts.Result toIdle = give(player(helper, new BlockPos(6, 2, 6)), idle, Items.BREAD);
			helper.assertTrue(toIdle.stored() == Gifts.Stored.STORE && SupplyContainers.count(level, store, Items.BREAD) == 1, "the jobless villager's bread: " + toIdle);

			// A worker with no chest by her workstation: the store.
			helper.setBlock(chest, Blocks.AIR);
			Gifts.Result noChest = give(player(helper, new BlockPos(4, 2, 6)), mason, Items.GLASS);
			helper.assertTrue(noChest.stored() == Gifts.Stored.STORE && SupplyContainers.count(level, store, Items.GLASS) == 1, "with her chest gone: " + noChest);

			// No chest anywhere: their own pockets; pockets full: at their feet. The gift still counts.
			helper.setBlock(storeChest, Blocks.AIR);
			helper.setBlock(storehouse, Blocks.AIR);
			ServerPlayer third = player(helper, new BlockPos(6, 2, 6));
			Gifts.Result pockets = give(third, idle, Items.EMERALD);
			helper.assertTrue(pockets.stored() == Gifts.Stored.POCKETS && idle.getInventory().countItem(Items.EMERALD) == 1, "no chest: " + pockets + ", pockets " + idle.getInventory());
			helper.assertTrue(pockets.change() == 80 && third.getMainHandItem().isEmpty(), "with no chest the gift didn't count: " + pockets);
			idle.getInventory().clearContent();
			for (int i = 0; i < idle.getInventory().getContainerSize(); i++) {
				idle.getInventory().setItem(i, new ItemStack(Items.WHEAT_SEEDS, 64));
			}
			Gifts.Result dropped = give(player(helper, new BlockPos(6, 2, 6)), idle, Items.DIAMOND);
			List<ItemEntity> lying = level.getEntitiesOfClass(ItemEntity.class, idle.getBoundingBox().inflate(2), e -> e.getItem().is(Items.DIAMOND));
			helper.assertTrue(dropped.stored() == Gifts.Stored.DROPPED && lying.size() == 1 && lying.get(0).getItem().getCount() == 1, "full pockets: " + dropped + ", lying " + lying);
			helper.succeed();
		});
	}

	/** A Bottle o' Enchanting is liked and gives a worker 15 XP (the bottle is used up); someone without a trade just keeps it. */
	//$ gametest_ticks_batch AREA '60' '"giftBottle"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "giftBottle")
	public void aBottleOEnchantingTeachesAWorker(GameTestHelper helper) {
		BlockPos bench = new BlockPos(2, 2, 3);
		BlockPos chest = new BlockPos(2, 2, 4);
		helper.setBlock(bench, Blocks.STONECUTTER);
		helper.setBlock(chest, Blocks.CHEST);
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager mason = villager(helper, new BlockPos(4, 2, 4), "Marta", VillagerProfession.MASON);
			mason.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(bench)));
			Villager idle = villager(helper, new BlockPos(6, 2, 4), "Ines", VillagerProfession.NONE);
			setDay(level, quietWeek(mason, idle));
			List<BlockPos> hers = List.of(helper.absolutePos(chest));
			helper.assertTrue(Tastes.named(mason, new ItemStack(Items.EXPERIENCE_BOTTLE)) == null, "a taste file names the bottle");
			int before = mason.getVillagerXp();
			ServerPlayer alex = player(helper, new BlockPos(4, 2, 6));
			Gifts.Result bottle = give(alex, mason, Items.EXPERIENCE_BOTTLE);
			helper.assertTrue(bottle.band() == Tastes.Band.LIKED && bottle.change() == 45, "the bottle: " + bottle);
			helper.assertTrue(mason.getVillagerXp() - before == Gifts.BOTTLE_XP && Gifts.BOTTLE_XP == 15, "her XP went from " + before + " to " + mason.getVillagerXp());
			helper.assertTrue(bottle.stored() == Gifts.Stored.USED && SupplyContainers.count(level, hers, Items.EXPERIENCE_BOTTLE) == 0, "the bottle was kept: " + bottle);
			List<String> lines = List.of("Bottle o' Enchanting! How kind of you, " + alex.getGameProfile().getName() + ".", "Oh, Bottle o' Enchanting. I do like that. Thank you!");
			helper.assertTrue(lines.contains(bottle.line().getString()), "her line: " + bottle.line().getString());

			Gifts.Result toIdle = give(player(helper, new BlockPos(6, 2, 6)), idle, Items.EXPERIENCE_BOTTLE);
			helper.assertTrue(toIdle.band() == Tastes.Band.LIKED && idle.getVillagerXp() == 0, "someone without a trade: " + toIdle + ", XP " + idle.getVillagerXp());
			helper.assertTrue(toIdle.stored() == Gifts.Stored.POCKETS && idle.getInventory().countItem(Items.EXPERIENCE_BOTTLE) == 1, "her bottle: " + toIdle);

			// A taste file that names the bottle has the say on how it's liked; a worker still learns from it.
			Map<ResourceLocation, JsonElement> files = new TreeMap<>(Tastes.files(level.getServer().getResourceManager()));
			files.put(ResourceLocation.fromNamespaceAndPath("mypack", "masons"), JsonParser.parseString(
				"{\"for\": {\"job\": \"minecraft:mason\"}, \"loved\": [\"minecraft:experience_bottle\"]}"));
			Tastes.load(files);
			setDay(level, Chronicle.day(level) + 1);
			int xp = mason.getVillagerXp();
			Gifts.Result loved = give(alex, mason, Items.EXPERIENCE_BOTTLE);
			helper.assertTrue(loved.band() == Tastes.Band.LOVED && mason.getVillagerXp() - xp == 15, "with a file naming it: " + loved);
			helper.succeed();
		});
	}

	/** Villagers without a name shake their head; so do named ones with no hall; an empty Gift isn't taken. The gift stays. */
	//$ gametest_ticks_batch AREA '60' '"giftUnnamed"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "giftUnnamed")
	public void villagersWithoutANameShakeTheirHead(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager nobody = villager(helper, new BlockPos(5, 2, 5), null, VillagerProfession.NONE);
			Villager dara = villager(helper, new BlockPos(7, 2, 5), "Dara", VillagerProfession.NONE);
			setDay(level, quietWeek(dara));
			ServerPlayer alex = player(helper, new BlockPos(5, 2, 8));
			UUID me = alex.getUUID();

			alex.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(Items.CAKE), "Alex"));
			rightClick(alex, nobody);
			helper.assertTrue(nobody.getUnhappyCounter() > 0, "the unnamed villager didn't shake their head");
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Gifts.contents(alex.getMainHandItem()).is(Items.CAKE), "the gift left the hand: " + alex.getMainHandItem());
			helper.assertTrue(!ModAttachments.FRIENDSHIP.has(nobody), "an unnamed villager keeps a friendship");
			helper.assertTrue(alex.containerMenu == alex.inventoryMenu, "the click with a gift opened a screen");
			Gifts.Result unnamed = Gifts.give(alex, nobody, alex.getMainHandItem());
			helper.assertTrue(unnamed.outcome() == Gifts.Outcome.NOT_NAMED && unnamed.line().getString().equals("They shake their head: only villagers with a name take gifts"),
				"unnamed: " + unnamed.outcome() + ", " + unnamed.line().getString());

			// An empty Gift (not made by wrapping): not taken, and the day isn't used up.
			alex.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GIFT));
			Gifts.Result empty = Gifts.give(alex, dara, alex.getMainHandItem());
			helper.assertTrue(empty.outcome() == Gifts.Outcome.EMPTY && empty.line().getString().equals("There's nothing inside this gift") && alex.getMainHandItem().is(ModItems.GIFT),
				"an empty gift: " + empty.outcome() + ", " + empty.line().getString());
			helper.assertTrue(Friendship.of(dara).bond(me).giftDay() == -1, "an empty gift used up the day");

			// Dara takes one while her village has a hall...
			helper.assertTrue(give(alex, dara, Items.CAKE).outcome() == Gifts.Outcome.GIVEN, "Dara by the hall");
			// ...and not once it's gone: nobody keeps friendships there.
			helper.setBlock(HALL, Blocks.AIR);
			CivicEffects.forget();
			setDay(level, Chronicle.day(level) + 1);
			dara.setUnhappyCounter(0);
			Gifts.Result noHall = give(alex, dara, Items.CAKE);
			helper.assertTrue(noHall.outcome() == Gifts.Outcome.NOT_NAMED && noHall.line().getString().equals("Dara shakes their head: there's no Village Hall here to keep friendships"),
				"no hall: " + noHall.outcome() + ", " + noHall.line().getString());
			helper.assertTrue(dara.getUnhappyCounter() > 0 && alex.getMainHandItem().is(ModItems.GIFT) && Friendship.points(dara, me) == 80, "no hall: the gift counted");
			helper.succeed();
		});
	}

	/** {@code friendship} off: a Gift is handed back unopened, nothing moves, and the item isn't put away. */
	//$ gametest_ticks_batch AREA '60' '"giftOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "giftOff")
	public void withFriendshipOffAGiftIsHandedBackUnopened(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(15, 2, 15);
		BlockPos storeChest = new BlockPos(15, 2, 14);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			setDay(level, quietWeek(dara));
			ServerPlayer alex = player(helper, new BlockPos(5, 2, 8));
			Friendship.ENABLED = false;
			alex.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(Items.CAKE), "Alex"));
			rightClick(alex, dara);
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Gifts.contents(alex.getMainHandItem()).is(Items.CAKE), "the gift wasn't handed back: " + alex.getMainHandItem());
			helper.assertTrue(!ModAttachments.FRIENDSHIP.has(dara), "friendship off, and she keeps one: " + Friendship.of(dara));
			helper.assertTrue(SupplyContainers.count(level, VillageNeeds.store(level, helper.absolutePos(HALL)), Items.CAKE) == 0, "the cake was put away");
			helper.assertTrue(alex.containerMenu == alex.inventoryMenu, "the click with a gift opened a screen");
			Gifts.Result off = Gifts.give(alex, dara, alex.getMainHandItem());
			helper.assertTrue(off.outcome() == Gifts.Outcome.OFF && off.line().getString().equals("Dara hands the gift back unopened: friendship is turned off in the config"),
				"off: " + off.outcome() + ", " + off.line().getString());
			// Back on: the same gift is taken.
			Friendship.ENABLED = true;
			rightClick(alex, dara);
			helper.assertTrue(alex.getMainHandItem().isEmpty() && Friendship.points(dara, alex.getUUID()) == 80, "back on: " + Friendship.points(dara, alex.getUUID()));
			helper.assertTrue(SupplyContainers.count(level, VillageNeeds.store(level, helper.absolutePos(HALL)), Items.CAKE) == 1, "the cake isn't in the store");
			helper.succeed();
		});
	}

	// --- Tastes ---------------------------------------------------------------------------------------------------

	private static JsonElement json(String text) {
		return JsonParser.parseString(text);
	}

	/** The most specific file that names an item wins: a job's, then a family's, then a trait's, then everyone's. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"giftSpecific"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "giftSpecific")
	public void theMostSpecificTasteWins(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<ResourceLocation, JsonElement> shipped = Tastes.files(level.getServer().getResourceManager());
		boolean traitsOn = Traits.ENABLED;
		Traits.ENABLED = true; // off in the test server, so other tests' villagers are all alike
		Leftovers.after(helper, () -> {
			Tastes.load(shipped);
			Traits.ENABLED = traitsOn;
		});
		Map<ResourceLocation, JsonElement> files = new TreeMap<>(shipped);
		files.put(ResourceLocation.fromNamespaceAndPath("test", "a_job"), json(
			"{\"for\": {\"job\": \"minecraft:mason\"}, \"hated\": [\"minecraft:cake\"], \"loved\": [\"minecraft:dirt\"]}"));
		files.put(ResourceLocation.fromNamespaceAndPath("test", "b_family"), json(
			"{\"for\": {\"family\": [\"minecraft:mason\", \"minecraft:farmer\"]}, \"liked\": [\"minecraft:cake\", \"minecraft:gravel\", \"minecraft:dirt\"]}"));
		files.put(ResourceLocation.fromNamespaceAndPath("test", "c_trait"), json(
			"{\"for\": {\"trait\": \"nimble\"}, \"loved\": [\"minecraft:gravel\", \"minecraft:bone\"], \"hated\": [\"minecraft:dirt\"]}"));
		// Two files as specific as each other: the kinder band.
		files.put(ResourceLocation.fromNamespaceAndPath("test", "d_family"), json(
			"{\"for\": {\"family\": [\"minecraft:mason\"]}, \"disliked\": [\"minecraft:gravel\"], \"loved\": [\"minecraft:flint\"]}"));
		files.put(ResourceLocation.fromNamespaceAndPath("test", "e_family"), json(
			"{\"for\": {\"family\": [\"minecraft:mason\"]}, \"hated\": [\"minecraft:flint\"]}"));
		Tastes.load(files);

		Villager mason = loose(level, VillagerProfession.MASON);
		Villager farmer = loose(level, VillagerProfession.FARMER);
		Villager cleric = loose(level, VillagerProfession.CLERIC);
		Villager plainCleric = loose(level, VillagerProfession.CLERIC, Traits.Trait.STRONG);
		record Case(String who, Villager villager, Item item, Tastes.Band band) {
		}
		List<Case> cases = List.of(
			new Case("the mason's job beats her family and everyone", mason, Items.CAKE, Tastes.Band.HATED),
			new Case("the mason's job beats her family and her trait", mason, Items.DIRT, Tastes.Band.LOVED),
			new Case("the farmer's family beats everyone", farmer, Items.CAKE, Tastes.Band.LIKED),
			new Case("the family beats the trait and everyone", farmer, Items.DIRT, Tastes.Band.LIKED),
			new Case("the family beats the trait", mason, Items.GRAVEL, Tastes.Band.LIKED),
			new Case("the trait beats everyone", mason, Items.BONE, Tastes.Band.LOVED),
			new Case("the trait beats everyone, for another job", cleric, Items.GRAVEL, Tastes.Band.LOVED),
			new Case("someone without the trait: everyone's", plainCleric, Items.GRAVEL, Tastes.Band.DISLIKED),
			new Case("nobody more specific names it: everyone's", mason, Items.ROTTEN_FLESH, Tastes.Band.HATED),
			new Case("another job: everyone's", cleric, Items.CAKE, Tastes.Band.LOVED),
			new Case("no file names it", mason, Items.STICK, Tastes.Band.NEUTRAL),
			new Case("two families as specific: the kinder", mason, Items.FLINT, Tastes.Band.LOVED),
			// The files as shipped: the building family's blueprint, a kitchen tag, a trait over everyone.
			new Case("a builder and a blueprint", loose(level, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER), ModItems.BLUEPRINT, Tastes.Band.LOVED),
			new Case("a chef and raw meat (a tag)", loose(level, io.github.jcondedata.aliveworkplace.registry.ModVillagers.CHEF), Items.BEEF, Tastes.Band.LIKED),
			new Case("a frugal villager and cake", loose(level, VillagerProfession.NONE, Traits.Trait.FRUGAL), Items.CAKE, Tastes.Band.DISLIKED),
			new Case("a glutton and rotten flesh (it's food)", loose(level, VillagerProfession.NONE, Traits.Trait.GLUTTON), Items.ROTTEN_FLESH, Tastes.Band.LOVED));
		List<String> wrong = new ArrayList<>();
		for (Case c : cases) {
			Tastes.Band got = Tastes.of(c.villager(), new ItemStack(c.item()));
			if (got != c.band()) {
				wrong.add(c.who() + " (" + c.item() + "): " + got + ", not " + c.band());
			}
		}
		helper.assertTrue(wrong.isEmpty(), "tastes: " + wrong);
		helper.succeed();
	}

	/** A data pack's file with the id of one of ours replaces it, a file of its own adds to them, and a broken one is skipped. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"giftDatapack"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "giftDatapack")
	public void aDataPacksTasteFileWinsOverOurs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<ResourceLocation, JsonElement> shipped = Tastes.files(level.getServer().getResourceManager());
		boolean traitsOn = Traits.ENABLED;
		Traits.ENABLED = true; // off in the test server, so other tests' villagers are all alike
		Leftovers.after(helper, () -> {
			Tastes.load(shipped);
			Traits.ENABLED = traitsOn;
		});
		Tastes.load(shipped);
		Villager idle = loose(level, VillagerProfession.NONE);
		Villager mason = loose(level, VillagerProfession.MASON);
		helper.assertTrue(shipped.containsKey(AliveWorkplace.id("everyone")), "our everyone file isn't in the server's data: " + shipped.keySet());
		helper.assertTrue(Tastes.of(mason, new ItemStack(Items.CAKE)) == Tastes.Band.LOVED && Tastes.of(mason, new ItemStack(Items.BREAD)) == Tastes.Band.LIKED, "setup: ours");

		// The same id (data/aliveworkplace/villager_tastes/everyone.json in a pack above ours): theirs is read, ours is gone.
		Map<ResourceLocation, JsonElement> files = new TreeMap<>(shipped);
		files.put(AliveWorkplace.id("everyone"), json("{\"for\": \"everyone\", \"hated\": [\"minecraft:cake\"], \"loved\": [\"#minecraft:planks\"]}"));
		// A file of the pack's own, for one job.
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "sweet_tooth"), json("{\"for\": {\"job\": \"minecraft:mason\"}, \"loved\": [\"minecraft:sugar\", \"minecraft:glass\"]}"));
		// Broken ones: skipped, the rest still load.
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "broken_for"), json("{\"for\": \"nobody\", \"loved\": [\"minecraft:stick\"]}"));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "broken_trait"), json("{\"for\": {\"trait\": \"sleepy\"}, \"loved\": [\"minecraft:stick\"]}"));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "broken_item"), json("{\"for\": \"everyone\", \"loved\": [\"Not An Id\"]}"));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "no_for"), json("{\"loved\": [\"minecraft:stick\"]}"));
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "off"), json("{\"enabled\": false, \"for\": \"everyone\", \"loved\": [\"minecraft:stick\"]}"));
		// A mod that isn't installed: its ids are kept and never match.
		files.put(ResourceLocation.fromNamespaceAndPath("mypack", "other_mod"), json("{\"for\": \"everyone\", \"loved\": [\"nosuchmod:thing\", \"#nosuchmod:things\"], \"liked\": [\"minecraft:clay_ball\"]}"));
		Tastes.load(files);

		helper.assertTrue(Tastes.of(idle, new ItemStack(Items.CAKE)) == Tastes.Band.HATED, "the pack's everyone file: cake is " + Tastes.of(idle, new ItemStack(Items.CAKE)));
		helper.assertTrue(Tastes.of(idle, new ItemStack(Items.OAK_PLANKS)) == Tastes.Band.LOVED, "the pack's tag: planks are " + Tastes.of(idle, new ItemStack(Items.OAK_PLANKS)));
		helper.assertTrue(Tastes.of(idle, new ItemStack(Items.ROTTEN_FLESH)) == Tastes.Band.NEUTRAL, "our everyone file is still read: rotten flesh is " + Tastes.of(idle, new ItemStack(Items.ROTTEN_FLESH)));
		helper.assertTrue(Tastes.of(mason, new ItemStack(Items.SUGAR)) == Tastes.Band.LOVED, "the pack's own file: " + Tastes.of(mason, new ItemStack(Items.SUGAR)));
		helper.assertTrue(Tastes.of(mason, new ItemStack(Items.GLASS)) == Tastes.Band.LOVED, "the pack's job file over our family's liked glass: " + Tastes.of(mason, new ItemStack(Items.GLASS)));
		helper.assertTrue(Tastes.of(mason, new ItemStack(Items.SPYGLASS)) == Tastes.Band.LOVED, "our other files are still read");
		helper.assertTrue(Tastes.of(idle, new ItemStack(Items.STICK)) == Tastes.Band.NEUTRAL, "a broken or switched-off file was read: stick is " + Tastes.of(idle, new ItemStack(Items.STICK)));
		helper.assertTrue(Tastes.of(idle, new ItemStack(Items.CLAY_BALL)) == Tastes.Band.LIKED, "a file naming a missing mod's items was dropped");
		for (String broken : List.of("broken_for", "broken_trait", "broken_item", "no_for", "off")) {
			helper.assertTrue(!Tastes.all().containsKey(ResourceLocation.fromNamespaceAndPath("mypack", broken)), broken + " was loaded");
		}

		helper.succeed();
	}

	/** The first set of taste files, as the roadmap lists them: every family's jobs, loves and likes, and every trait's. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"giftFirstSet"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "giftFirstSet")
	public void theFirstSetOfTastesIsComplete(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<ResourceLocation, JsonElement> shipped = Tastes.files(level.getServer().getResourceManager());
		boolean traitsOn = Traits.ENABLED;
		Traits.ENABLED = true; // off in the test server, so other tests' villagers are all alike
		Leftovers.after(helper, () -> {
			Tastes.load(shipped);
			Traits.ENABLED = traitsOn;
		});
		Tastes.load(shipped);
		String aw = "aliveworkplace:";
		String mc = "minecraft:";
		record Family(String file, List<String> jobs, List<String> loved, List<String> liked, List<String> disliked, List<String> hated) {
		}
		// Tags are checked with one of their items: "#tag=item".
		List<Family> families = List.of(
			new Family("everyone", List.of(mc + "cleric", aw + "vintner"), List.of("cake", "pumpkin_pie", "golden_apple", "diamond"),
				List.of("bread", "cookie", "honey_bottle", "emerald", "poppy", "dandelion"), List.of("dirt", "gravel", "cobblestone", "bone"),
				List.of("rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish")),
			new Family("building", List.of(aw + "builder", aw + "carpenter", mc + "mason", aw + "tinkerer", mc + "leatherworker"),
				List.of(aw + "blueprint", "spyglass"), List.of("bricks", "glass", "lantern", "oak_planks", "cherry_planks"), List.of(), List.of()),
			new Family("mining", List.of(aw + "miner", mc + "armorer", mc + "toolsmith", mc + "weaponsmith", aw + "sifter", aw + "netherworker"),
				List.of("amethyst_shard", "gold_ingot", "netherite_scrap"), List.of("iron_ingot", "coal", "raw_copper"), List.of(), List.of()),
			new Family("land", List.of(aw + "lumberjack", aw + "orchard_keeper", mc + "farmer", aw + "florist", aw + "beekeeper", aw + "composter"),
				List.of("golden_carrot", "honeycomb", "sunflower"), List.of("apple", "bone_meal", "wheat_seeds", "oak_sapling", "spruce_sapling"), List.of(), List.of()),
			new Family("animals", List.of(mc + "shepherd", mc + "butcher", aw + "rancher", mc + "fisherman"),
				List.of("saddle", "name_tag"), List.of("wheat", "hay_block", "cod", "salmon", "lead"), List.of(), List.of()),
			new Family("kitchen", List.of(aw + "chef"),
				List.of("glow_berries", "golden_carrot"), List.of("egg", "milk_bucket", "sugar", "cocoa_beans", "beef", "chicken"), List.of(), List.of()),
			new Family("learning", List.of(aw + "scholar", aw + "teacher", mc + "librarian", mc + "cartographer"),
				List.of("enchanted_book", "written_book", "filled_map"), List.of("book", "paper", "feather", "ink_sac", "compass"), List.of(), List.of()),
			new Family("healing", List.of(aw + "nurse", mc + "cleric", aw + "undertaker"),
				List.of("glistering_melon_slice", "ghast_tear", "totem_of_undying"), List.of("potion", "honey_bottle", "golden_carrot"), List.of(), List.of()),
			new Family("arms", List.of(aw + "guard", mc + "fletcher"),
				List.of("shield", "crossbow", "diamond_sword"), List.of("arrow", "flint", "iron_ingot"), List.of(), List.of()),
			new Family("trade", List.of(aw + "shopkeeper", aw + "innkeeper", aw + "ferryman", aw + "postman", aw + "porter"),
				List.of("emerald_block", "filled_map", "saddle"), List.of("paper", "oak_boat", "birch_chest_boat", "lantern"), List.of(), List.of()),
			new Family("music", List.of(aw + "bard"),
				List.of("music_disc_cat", "music_disc_13", "goat_horn"), List.of("note_block", "amethyst_shard"), List.of(), List.of()),
			new Family("no_trade", List.of(mc + "none", mc + "nitwit"),
				List.of("emerald"), List.of("bread", "red_bed", "white_bed"), List.of(), List.of()));
		List<String> wrong = new ArrayList<>();
		for (Family f : families) {
			Tastes.Taste file = Tastes.all().get(AliveWorkplace.id(f.file()));
			if (file == null) {
				wrong.add("no file " + f.file());
				continue;
			}
			if (!f.file().equals("everyone")) {
				Set<ResourceLocation> jobs = new java.util.TreeSet<>();
				f.jobs().forEach(j -> jobs.add(ResourceLocation.parse(j)));
				if (file.scope() != Tastes.Scope.FAMILY || !new java.util.TreeSet<>(file.jobs()).equals(jobs)) {
					wrong.add(f.file() + " is for " + file.scope() + " " + file.jobs());
				}
			} else if (file.scope() != Tastes.Scope.EVERYONE) {
				wrong.add("everyone is for " + file.scope());
			}
			for (String job : f.jobs()) {
				VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(ResourceLocation.parse(job)).orElse(null);
				if (profession == null) {
					wrong.add(f.file() + ": no job " + job);
					continue;
				}
				Villager v = loose(level, profession);
				check(wrong, f.file() + " " + job, v, f.loved(), Tastes.Band.LOVED);
				check(wrong, f.file() + " " + job, v, f.liked(), Tastes.Band.LIKED);
				check(wrong, f.file() + " " + job, v, f.disliked(), Tastes.Band.DISLIKED);
				check(wrong, f.file() + " " + job, v, f.hated(), Tastes.Band.HATED);
			}
		}
		// Pokémon: Cobblemon's items by id (they match only with Cobblemon installed), for the six Pokémon jobs.
		Tastes.Taste pokemon = Tastes.all().get(AliveWorkplace.id("pokemon"));
		helper.assertTrue(pokemon != null && pokemon.scope() == Tastes.Scope.FAMILY, "no pokemon file");
		Set<String> pokemonJobs = new java.util.TreeSet<>();
		pokemon.jobs().forEach(j -> pokemonJobs.add(j.toString()));
		helper.assertTrue(pokemonJobs.equals(new java.util.TreeSet<>(List.of(aw + "trainer", aw + "trainer_leader", aw + "tutor", aw + "ball_smith", aw + "pokemon_trader",
			aw + "fossil_scientist"))), "the pokemon file's jobs: " + pokemonJobs);
		for (String job : pokemonJobs) {
			helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(ResourceLocation.parse(job)), "no job " + job);
		}
		helper.assertTrue(entries(pokemon, Tastes.Band.LOVED).equals(List.of("cobblemon:rare_candy", "cobblemon:ultra_ball")), "pokemon loved: " + entries(pokemon, Tastes.Band.LOVED));
		helper.assertTrue(entries(pokemon, Tastes.Band.LIKED).equals(List.of("cobblemon:poke_ball", "cobblemon:exp_candy_s", "#cobblemon:berries")),
			"pokemon liked: " + entries(pokemon, Tastes.Band.LIKED));

		// Traits (a jobless villager with just that trait; a trait's word beats everyone's).
		record Trait(Traits.Trait trait, List<String> loved, List<String> disliked) {
		}
		List<Trait> traits = List.of(
			new Trait(Traits.Trait.GLUTTON, List.of("bread", "cooked_beef", "apple", "rotten_flesh"), List.of()),
			new Trait(Traits.Trait.FRUGAL, List.of("emerald", "gold_ingot"), List.of("cake")),
			new Trait(Traits.Trait.CHEERFUL, List.of("poppy", "sunflower", "music_disc_cat"), List.of()),
			new Trait(Traits.Trait.LAZY, List.of("red_bed"), List.of("iron_pickaxe", "wooden_hoe")),
			new Trait(Traits.Trait.DILIGENT, List.of("iron_pickaxe", "stone_axe"), List.of()),
			new Trait(Traits.Trait.CLEVER, List.of("book", "writable_book", "clock"), List.of()),
			new Trait(Traits.Trait.STRONG, List.of("cooked_beef", "iron_block"), List.of()),
			new Trait(Traits.Trait.NIMBLE, List.of("sugar", "rabbit_foot"), List.of()));
		helper.assertTrue(traits.size() == Traits.Trait.values().length, "a trait has no tastes");
		for (Trait t : traits) {
			Tastes.Taste file = Tastes.all().get(AliveWorkplace.id("trait/" + t.trait().key()));
			if (file == null || file.scope() != Tastes.Scope.TRAIT || file.trait() != t.trait()) {
				wrong.add("no trait file for " + t.trait());
				continue;
			}
			Villager v = loose(level, VillagerProfession.CLERIC, t.trait());
			check(wrong, "trait " + t.trait(), v, t.loved(), Tastes.Band.LOVED);
			check(wrong, "trait " + t.trait(), v, t.disliked(), Tastes.Band.DISLIKED);
		}
		helper.assertTrue(Tastes.all().size() == 21, "taste files loaded: " + Tastes.all().keySet());
		helper.assertTrue(wrong.isEmpty(), "the first set: " + wrong);
		helper.succeed();
	}

	private static List<String> entries(Tastes.Taste taste, Tastes.Band band) {
		return taste.bands().getOrDefault(band, List.of()).stream().map(e -> e.tag() != null ? "#" + e.tag().location() : e.item().toString()).toList();
	}

	private static void check(List<String> wrong, String who, Villager villager, List<String> items, Tastes.Band band) {
		for (String name : items) {
			ResourceLocation id = name.contains(":") ? ResourceLocation.parse(name) : ResourceLocation.withDefaultNamespace(name);
			Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (item == null) {
				wrong.add(who + ": no item " + id);
				continue;
			}
			Tastes.Band got = Tastes.of(villager, new ItemStack(item));
			if (got != band) {
				wrong.add(who + ": " + name + " is " + got + ", not " + band);
			}
		}
	}
}
