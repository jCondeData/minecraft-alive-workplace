package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.story.HeartEvents;
import io.github.jcondedata.aliveworkplace.story.Keepsakes;
import io.github.jcondedata.aliveworkplace.story.LifeStory;
import io.github.jcondedata.aliveworkplace.story.Tastes;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1031): gifts (31.6), heart events (31.7) and the full set with keepsakes (31.8), from the spec, for the
 * gaps the other suites leave: the shipped taste files where a family, a trait and everyone disagree; the advanced
 * tooltip; a gift arriving whole and a full supply chest; the top of the friendship; an unnamed villager's refusal not
 * using up the day; the 2-heart edge reached through gifts; the event's +20 reaching the next level; told once across
 * a dip in friendship (and the keepsake); the life story's one line per event for any viewer; and American English in
 * every sentence of these features.
 */
public class QaHeartStories1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);
	private static final String NS = "aliveworkplace:";

	// --- Setting up -----------------------------------------------------------------------------------------------

	/** {@link HeartEventGameTests#village}, plus the taste files as shipped and the clock put back afterwards. */
	private static void village(GameTestHelper helper, Runnable then) {
		ServerLevel level = helper.getLevel();
		long dayTime = level.getDayTime();
		Map<ResourceLocation, JsonElement> tastes = Tastes.files(level.getServer().getResourceManager());
		Tastes.load(tastes);
		Leftovers.after(helper, () -> {
			level.setDayTime(dayTime);
			Tastes.load(tastes);
		});
		HeartEventGameTests.village(helper, then);
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
	}

	private static int uuids;

	/** A villager not in the world, of {@code profession}, whose traits are exactly {@code traits} (fixed seed). */
	private static Villager loose(ServerLevel level, VillagerProfession profession, Traits.Trait... traits) {
		List<Traits.Trait> want = List.of(traits);
		java.util.Random random = new java.util.Random(1031L + 104729L * uuids++);
		UUID id = null;
		for (int i = 0; i < 200_000 && id == null; i++) {
			UUID candidate = new UUID(random.nextLong(), random.nextLong());
			if (Traits.of(candidate).equals(want)) {
				id = candidate;
			}
		}
		if (id == null) {
			throw new IllegalStateException("no UUID with traits " + want);
		}
		Villager v = EntityType.VILLAGER.create(level);
		v.setUUID(id);
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		return v;
	}

	/** A named, still villager standing at {@code at} with a UUID whose name day is clear of days 8 to 17. */
	private static Villager villager(GameTestHelper helper, BlockPos at, String name, VillagerProfession profession) {
		java.util.Random random = new java.util.Random(31L + 7L * uuids++);
		UUID id;
		do {
			id = new UUID(random.nextLong(), random.nextLong());
		} while (!GiftGameTests.quietFromDay8(id));
		Villager v = HeartEventGameTests.villager(helper, at, name, id);
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		return v;
	}

	private static Gifts.Result give(ServerPlayer player, Villager villager, ItemStack item) {
		player.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(item, player.getGameProfile().getName()));
		return Gifts.give(player, villager, player.getMainHandItem());
	}

	private static void rightClick(ServerPlayer player, Villager villager) {
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
	}

	private static String pending(GameTestHelper helper, Villager villager, ServerPlayer me) {
		HeartEvents.Event event = HeartEvents.pending(helper.getLevel(), villager, me.getUUID());
		return event == null ? "nothing" : event.id() + "@" + event.hearts();
	}

	private static int count(ServerPlayer player, Item item) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			n += stack.is(item) ? stack.getCount() : 0;
		}
		return n;
	}

	private static List<String> lore(ItemStack icon) {
		var lore = icon.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	// --- Gifts (31.6) ---------------------------------------------------------------------------------------------

	/**
	 * The shipped taste files, where they disagree: "the most specific file that names an item wins (job, then family,
	 * then trait, then everyone)". A family's word beats the villager's trait (a Glutton farmer only likes apples, a
	 * Lazy jobless villager only likes beds, a Clever librarian only likes books, a Lazy guard still loves a diamond
	 * sword); a trait's word beats everyone's (a Cheerful villager loves a poppy, a Glutton loves the pufferfish everyone
	 * hates, a Frugal chef dislikes cake, a Lazy miner dislikes a pickaxe, a Diligent one loves it).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaHS1031Tastes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaHS1031Tastes")
	public void qaShippedTastesThatDisagreeFollowTheMostSpecificRule(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<ResourceLocation, JsonElement> shipped = Tastes.files(level.getServer().getResourceManager());
		boolean traitsOn = Traits.ENABLED;
		Traits.ENABLED = true;
		Leftovers.after(helper, () -> {
			Tastes.load(shipped);
			Traits.ENABLED = traitsOn;
		});
		Tastes.load(shipped);
		record Case(String who, Villager villager, Item item, Tastes.Band band) {
		}
		List<Case> cases = new ArrayList<>(List.of(
			new Case("a Glutton farmer and an apple (land likes it)", loose(level, VillagerProfession.FARMER, Traits.Trait.GLUTTON), Items.APPLE, Tastes.Band.LIKED),
			new Case("a Glutton chef and raw beef (kitchen likes raw meat)", loose(level, ModVillagers.CHEF, Traits.Trait.GLUTTON), Items.BEEF, Tastes.Band.LIKED),
			new Case("a Glutton cleric and a golden carrot (healing likes it)", loose(level, VillagerProfession.CLERIC, Traits.Trait.GLUTTON), Items.GOLDEN_CARROT, Tastes.Band.LIKED),
			new Case("a Lazy jobless villager and a bed (no trade likes beds)", loose(level, VillagerProfession.NONE, Traits.Trait.LAZY), Items.RED_BED, Tastes.Band.LIKED),
			new Case("a Clever librarian and a book (learning likes books)", loose(level, VillagerProfession.LIBRARIAN, Traits.Trait.CLEVER), Items.BOOK, Tastes.Band.LIKED),
			new Case("a Lazy fletcher and a diamond sword (arms loves it)", loose(level, VillagerProfession.FLETCHER, Traits.Trait.LAZY), Items.DIAMOND_SWORD, Tastes.Band.LOVED),
			new Case("a Cheerful cleric and a poppy (everyone only likes flowers)", loose(level, VillagerProfession.CLERIC, Traits.Trait.CHEERFUL), Items.POPPY, Tastes.Band.LOVED),
			new Case("a Frugal chef and cake (everyone loves it)", loose(level, ModVillagers.CHEF, Traits.Trait.FRUGAL), Items.CAKE, Tastes.Band.DISLIKED),
			new Case("a Lazy miner and an iron pickaxe", loose(level, ModVillagers.MINER, Traits.Trait.LAZY), Items.IRON_PICKAXE, Tastes.Band.DISLIKED),
			new Case("a Diligent miner and an iron pickaxe", loose(level, ModVillagers.MINER, Traits.Trait.DILIGENT), Items.IRON_PICKAXE, Tastes.Band.LOVED),
			new Case("a Strong butcher and cooked beef (animals don't name it)", loose(level, VillagerProfession.BUTCHER, Traits.Trait.STRONG), Items.COOKED_BEEF, Tastes.Band.LOVED),
			new Case("a Frugal miner and a gold ingot (both love it)", loose(level, ModVillagers.MINER, Traits.Trait.FRUGAL), Items.GOLD_INGOT, Tastes.Band.LOVED),
			new Case("a Strong cleric (no flower trait) and a poppy", loose(level, VillagerProfession.CLERIC, Traits.Trait.STRONG), Items.POPPY, Tastes.Band.LIKED)));
		// Everyone hates these; a Glutton loves the ones that are food (#c:foods).
		TagKey<Item> foods = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "foods"));
		for (Item hated : List.of(Items.PUFFERFISH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.ROTTEN_FLESH)) {
			boolean food = new ItemStack(hated).is(foods);
			cases.add(new Case("a Glutton cleric and " + hated + (food ? " (food)" : " (not food)"), loose(level, VillagerProfession.CLERIC, Traits.Trait.GLUTTON), hated,
				food ? Tastes.Band.LOVED : Tastes.Band.HATED));
		}
		List<String> wrong = new ArrayList<>();
		for (Case c : cases) {
			Tastes.Band got = Tastes.of(c.villager(), new ItemStack(c.item()));
			if (got != c.band()) {
				wrong.add(c.who() + ": " + got + ", not " + c.band());
			}
		}
		helper.assertTrue(new ItemStack(Items.PUFFERFISH).is(foods), "setup: pufferfish isn't in #c:foods");
		helper.assertTrue(wrong.isEmpty(), "tastes: " + wrong);
		helper.succeed();
	}

	/** "the tooltip says 'From Jesse' and not what's inside": not even with advanced tooltips on (F3+H), nor a custom name on the item. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheAdvancedTooltipStillHidesTheContents(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Biter"));
		ItemStack gift = Gifts.wrap(sword, "Jesse");
		List<String> lines = new ArrayList<>();
		for (TooltipFlag flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
			gift.getTooltipLines(Item.TooltipContext.of(level), null, flag).forEach(c -> lines.add(c.getString()));
		}
		helper.assertTrue(lines.contains("From Jesse"), "no 'From Jesse': " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("Biter") || l.contains("Diamond Sword") || l.contains("diamond_sword")),
			"the tooltip gives away what's inside: " + lines);
		helper.assertTrue(gift.getHoverName().getString().equals("Gift"), "the gift is called " + gift.getHoverName().getString());
		helper.succeed();
	}

	/** A worker with a supply chest by her workstation: a named, enchanted, worn sword arrives in that chest exactly as it was wrapped. */
	//$ gametest_ticks_batch AREA '60' '"qaHS1031Whole"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHS1031Whole")
	public void qaAGiftArrivesWholeInHerSupplyChest(GameTestHelper helper) {
		BlockPos bench = new BlockPos(2, 2, 3);
		BlockPos chest = new BlockPos(2, 2, 4);
		helper.setBlock(bench, Blocks.STONECUTTER);
		helper.setBlock(chest, Blocks.CHEST);
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager marta = villager(helper, new BlockPos(4, 2, 4), "Marta", VillagerProfession.MASON);
			marta.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(bench)));
			setDay(level, GiftGameTests.quietWeek(marta));
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			sword.set(DataComponents.CUSTOM_NAME, Component.literal("Biter"));
			sword.setDamageValue(11);
			sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(4, 2, 6));
			alex.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(sword.copy(), alex.getGameProfile().getName()));
			rightClick(alex, marta);
			helper.assertTrue(alex.getMainHandItem().isEmpty(), "the gift is still in hand: " + alex.getMainHandItem());
			helper.assertTrue(Friendship.points(marta, alex.getUUID()) == 20, "a neutral sword gave " + Friendship.points(marta, alex.getUUID()));
			Container box = (Container) level.getBlockEntity(helper.absolutePos(chest));
			ItemStack found = ItemStack.EMPTY;
			for (int i = 0; i < box.getContainerSize(); i++) {
				if (box.getItem(i).is(Items.DIAMOND_SWORD)) {
					found = box.getItem(i);
				}
			}
			helper.assertTrue(ItemStack.matches(found, sword), "in her chest: " + found + " " + found.getComponents() + ", not " + sword.getComponents());
			helper.succeed();
		});
	}

	/** "The item goes into their chests (a worker's supply chests, else the village's store)": her chest full, the store gets it. */
	//$ gametest_ticks_batch AREA '60' '"qaHS1031FullChest"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHS1031FullChest")
	public void qaAFullSupplyChestSendsTheGiftToTheStore(GameTestHelper helper) {
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
			Container box = (Container) level.getBlockEntity(helper.absolutePos(chest));
			for (int i = 0; i < box.getContainerSize(); i++) {
				box.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			Villager marta = villager(helper, new BlockPos(4, 2, 4), "Marta", VillagerProfession.MASON);
			marta.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(bench)));
			setDay(level, GiftGameTests.quietWeek(marta));
			List<BlockPos> store = VillageNeeds.store(level, helper.absolutePos(HALL));
			helper.assertTrue(store.contains(helper.absolutePos(storeChest)), "setup: the store is " + store);
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(4, 2, 6));
			Gifts.Result r = give(alex, marta, new ItemStack(Items.SPYGLASS));
			helper.assertTrue(r.outcome() == Gifts.Outcome.GIVEN && r.change() == 80, "the loved spyglass: " + r);
			helper.assertTrue(r.stored() == Gifts.Stored.STORE && SupplyContainers.count(level, store, Items.SPYGLASS) == 1,
				"with her chest full the spyglass went to " + r.stored() + "; in the store: " + SupplyContainers.count(level, store, Items.SPYGLASS));
			helper.assertTrue(marta.getInventory().countItem(Items.SPYGLASS) == 0, "it went into her pockets as well");
			helper.succeed();
		});
	}

	/** A loved gift at ten hearts is taken, thanked for, and the friendship stays at the top (1000), not past it. */
	//$ gametest_ticks_batch AREA '60' '"qaHS1031Top"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHS1031Top")
	public void qaALovedGiftAtTenHeartsStaysAtTheTop(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			setDay(level, GiftGameTests.quietWeek(dara));
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 8));
			Friendship.add(dara, alex, Friendship.MAX);
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 1000, "setup: " + Friendship.points(dara, alex.getUUID()));
			Gifts.Result cake = give(alex, dara, new ItemStack(Items.CAKE));
			helper.assertTrue(cake.outcome() == Gifts.Outcome.GIVEN && alex.getMainHandItem().isEmpty(), "at the top: " + cake);
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 1000, "past the top: " + Friendship.points(dara, alex.getUUID()));
			helper.assertTrue(Friendship.hearts(Friendship.points(dara, alex.getUUID())) == 10, "hearts: " + Friendship.hearts(Friendship.points(dara, alex.getUUID())));
			// And a hated one from the top costs its 40.
			setDay(level, GiftGameTests.quietWeek(dara) + 1);
			Gifts.Result flesh = give(alex, dara, new ItemStack(Items.ROTTEN_FLESH));
			helper.assertTrue(flesh.change() == -40 && Friendship.points(dara, alex.getUUID()) == 960, "a hated gift from the top: " + flesh + ", " + Friendship.points(dara, alex.getUUID()));
			helper.succeed();
		});
	}

	/** "Villagers who aren't named shake their head": the gift stays, and the day isn't used up; named, the same gift is taken that day. */
	//$ gametest_ticks_batch AREA '60' '"qaHS1031Unnamed"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHS1031Unnamed")
	public void qaAnUnnamedVillagersRefusalUsesUpNoDay(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager nob = villager(helper, new BlockPos(5, 2, 5), null, VillagerProfession.NONE);
			setDay(level, GiftGameTests.quietWeek(nob));
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 8));
			alex.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(Items.CAKE), "Alex"));
			rightClick(alex, nob);
			helper.assertTrue(nob.getUnhappyCounter() > 0, "no head shake");
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Gifts.contents(alex.getMainHandItem()).is(Items.CAKE), "the gift wasn't kept: " + alex.getMainHandItem());
			rightClick(alex, nob);
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT), "a second click took it");
			nob.setCustomName(Component.literal("Nob"));
			rightClick(alex, nob);
			helper.assertTrue(alex.getMainHandItem().isEmpty() && Friendship.points(nob, alex.getUUID()) == 80,
				"named, the same day: " + alex.getMainHandItem() + ", points " + Friendship.points(nob, alex.getUUID()));
			helper.succeed();
		});
	}

	// --- Heart events (31.7, 31.8) --------------------------------------------------------------------------------

	/** "At 2 ... hearts a villager has something to tell you", reached through gifts: 199 points, nothing; 200, the 2-heart event. */
	//$ gametest_ticks_batch AREA '60' '"qaHS1031TwoHearts"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHS1031TwoHearts")
	public void qaGiftsToOneNinetyNineTellNothingAndToTwoHundredTell(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(4, 2, 5), "Dara", VillagerProfession.NONE);
			Villager bram = villager(helper, new BlockPos(7, 2, 5), "Bram", VillagerProfession.NONE);
			for (Villager v : List.of(dara, bram)) {
				ModAttachments.PARENTS.set(v, new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
			}
			setDay(level, GiftGameTests.quietWeek(dara, bram));
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 8));
			Friendship.add(dara, alex, 154);
			Friendship.add(bram, alex, 155);
			helper.assertTrue(give(alex, dara, new ItemStack(Items.COOKIE)).change() == 45 && give(alex, bram, new ItemStack(Items.COOKIE)).change() == 45, "the cookies");
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 199 && Friendship.points(bram, alex.getUUID()) == 200, "setup points");
			helper.assertTrue(pending(helper, dara, alex).equals("nothing"), "at 199 Dara would tell " + pending(helper, dara, alex));
			helper.assertTrue(pending(helper, bram, alex).equals(NS + "born_here@2"), "at 200 Bram would tell " + pending(helper, bram, alex));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(dara) == null, "Dara started telling at 199");
			helper.assertTrue(HeartEvents.telling(bram) != null, "Bram didn't start at 200");
			helper.succeed();
		});
	}

	/** "Each event ... adds +20 friendship": at 380 points the 2-heart event takes them to exactly 400, and the 4-heart one is next. */
	//$ gametest_ticks_batch AREA '400' '"qaHS1031Carry"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaHS1031Carry")
	public void qaTheEventsTwentyPointsReachTheNextLevel(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			ModAttachments.PARENTS.set(dara, new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			Friendship.add(dara, me, 380);
			HeartEvents.Event first = HeartEvents.pending(level, dara, me.getUUID());
			helper.assertTrue(first != null && first.hearts() == 2, "at 380: " + pending(helper, dara, me));
			HeartEventGameTests.listen(helper, dara, me, HeartEventGameTests.later(level), first.lines().size());
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 400, "after the 2-heart event: " + Friendship.points(dara, me.getUUID()));
			HeartEvents.Event next = HeartEvents.pending(level, dara, me.getUUID());
			helper.assertTrue(next != null && next.hearts() == 4, "at 400 the next is " + pending(helper, dara, me));
			HeartEventGameTests.listen(helper, dara, me, HeartEventGameTests.later(level), next.lines().size());
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 420 && HeartEventGameTests.told(dara, me).size() == 2,
				"after the 4-heart event: " + Friendship.points(dara, me.getUUID()) + ", told " + HeartEventGameTests.told(dara, me));
			helper.assertTrue(pending(helper, dara, me).equals("nothing"), "at 420 something more: " + pending(helper, dara, me));
			helper.succeed();
		});
	}

	/** "Each event is told once per player": told at 2 hearts, a hated gift drops them under 2, gifts bring them back; nothing is told again. */
	//$ gametest_ticks_batch AREA '400' '"qaHS1031Dip"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaHS1031Dip")
	public void qaAToldEventIsNotToldAgainAfterADip(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			ModAttachments.PARENTS.set(dara, new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			long monday = GiftGameTests.quietWeek(dara);
			setDay(level, monday);
			Friendship.add(dara, me, 200);
			HeartEvents.Event first = HeartEvents.pending(level, dara, me.getUUID());
			HeartEventGameTests.listen(helper, dara, me, HeartEventGameTests.later(level), first.lines().size());
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 220, "told: " + Friendship.points(dara, me.getUUID()));
			// Rotten flesh: 180, one heart.
			give(me, dara, new ItemStack(Items.ROTTEN_FLESH));
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 180, "after the hated gift: " + Friendship.points(dara, me.getUUID()));
			// A cookie the next day: 225, two hearts again.
			setDay(level, monday + 1);
			give(me, dara, new ItemStack(Items.COOKIE));
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 225, "after the cookie: " + Friendship.points(dara, me.getUUID()));
			helper.assertTrue(pending(helper, dara, me).equals("nothing"), "back at 2 hearts she'd tell " + pending(helper, dara, me));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(dara) == null && HeartEventGameTests.told(dara, me).size() == 1, "told again: " + HeartEventGameTests.told(dara, me));
			helper.succeed();
		});
	}

	/** "At 10 hearts they also give you a keepsake, once": after it, a dip to 9 hearts and back up gives nothing more. */
	//$ gametest_ticks_batch AREA '400' '"qaHS1031KeepsakeDip"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaHS1031KeepsakeDip")
	public void qaAKeepsakeIsNotGivenAgainAfterADip(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			for (String below : List.of("before_hall", "work_none", "night_you", "love_village")) {
				Friendship.told(dara, me, NS + below);
			}
			Friendship.add(dara, me, 1000);
			HeartEvents.Event dream = HeartEvents.pending(level, dara, me.getUUID());
			helper.assertTrue(dream != null && dream.hearts() == 10, "at 10 hearts: " + pending(helper, dara, me));
			me.getInventory().clearContent();
			HeartEventGameTests.listen(helper, dara, me, HeartEventGameTests.later(level), dream.lines().size());
			helper.assertTrue(count(me, Items.CORNFLOWER) == 1 && Keepsakes.given(dara, me), "the pressed flower: " + count(me, Items.CORNFLOWER));
			Friendship.add(dara, me, -150);
			helper.assertTrue(Friendship.hearts(Friendship.points(dara, me.getUUID())) == 8, "the dip: " + Friendship.points(dara, me.getUUID()));
			Friendship.add(dara, me, 150);
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(dara) == null && pending(helper, dara, me).equals("nothing"), "back at 10 she'd tell " + pending(helper, dara, me));
			helper.assertTrue(Keepsakes.give(level, dara, me) == null && count(me, Items.CORNFLOWER) == 1, "a second keepsake: " + count(me, Items.CORNFLOWER));
			helper.succeed();
		});
	}

	/**
	 * The life story has "one line per event anyone was told": the 2-heart event told to two players is one line, the
	 * 4-heart event told to one of them a second, and a third player who was told nothing reads both on the page.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaHS1031Story"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaHS1031Story")
	public void qaTheLifeStoryHasOneLinePerEventForAnyViewer(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			ModAttachments.PARENTS.set(dara, new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			ServerPlayer sam = HeartEventGameTests.player(helper, new BlockPos(4, 2, 7));
			ServerPlayer cora = HeartEventGameTests.player(helper, new BlockPos(14, 2, 14));
			Friendship.add(dara, alex, 200);
			HeartEvents.Event born = HeartEvents.pending(level, dara, alex.getUUID());
			HeartEventGameTests.listen(helper, dara, alex, HeartEventGameTests.later(level), born.lines().size());
			Friendship.add(dara, sam, 380);
			HeartEvents.Event two = HeartEvents.pending(level, dara, sam.getUUID());
			HeartEventGameTests.listen(helper, dara, sam, HeartEventGameTests.later(level), two.lines().size());
			HeartEvents.Event four = HeartEvents.pending(level, dara, sam.getUUID());
			helper.assertTrue(four != null && four.hearts() == 4, "sam's next: " + pending(helper, dara, sam));
			HeartEventGameTests.listen(helper, dara, sam, HeartEventGameTests.later(level), four.lines().size());
			helper.assertTrue(HeartEventGameTests.told(dara, alex).size() == 1 && HeartEventGameTests.told(dara, sam).size() == 2, "setup: told");
			ChoiceMenu menu = VillageHallScreen.forTest(cora, hall);
			LifeStory.render(menu, level, hall, dara, 0, cora);
			int story = LifeStory.storySlot(0);
			List<String> titles = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				ItemStack icon = menu.icon(story + i);
				if (!icon.isEmpty()) {
					titles.add(icon.getHoverName().getString());
				}
			}
			String bornLine = Component.translatable(born.story(),
				HeartEvents.args(level, dara, Component.literal(HeartEventGameTests.ME))).getString();
			helper.assertTrue(titles.size() == 2, "the page Cora reads has " + titles.size() + " story lines: " + titles);
			helper.assertTrue(titles.get(0).equals(bornLine), "the first line: " + titles.get(0) + ", not " + bornLine);
			helper.assertTrue(!lore(menu.icon(story + 1)).isEmpty() && lore(menu.icon(story + 1)).get(0).equals("A story for 4 hearts"),
				"the second line's lore: " + lore(menu.icon(story + 1)));
			helper.succeed();
		});
	}

	/**
	 * 31.8, 6 hearts: "raids and bandits (a raid in the last 5 days, or a camp nearby)". A bandit camp preying on the
	 * village makes {@code raided} hold with no raid on record; once it's gone, it doesn't.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHS1031Camp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHS1031Camp")
	public void qaABanditCampNearbyIsAWorryAboutRaids(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		BlockPos corner = new BlockPos(1, 2, 1); // in a corner: the camp goes in the far one
		helper.setBlock(corner, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			CivicEffects.forget();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(corner);
			Villager dara = villager(helper, new BlockPos(3, 2, 3), "Dara", VillagerProfession.NONE);
			Leftovers.after(helper, () -> {
				level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(hall).inflate(40),
					m -> m.getTags().contains(io.github.jcondedata.aliveworkplace.guard.BanditCamps.TAG)).forEach(m -> m.discard());
				io.github.jcondedata.aliveworkplace.guard.BanditCamps.forget(level);
				CivicEffects.forget();
			});
			HeartEvents.When raided = HeartEvents.read(ResourceLocation.fromNamespaceAndPath("mypack", "qa_raided"), JsonParser.parseString(
				"{\"hearts\": 6, \"when\": {\"raided\": true}, \"lines\": [\"a\", \"b\", \"c\"], \"chronicle\": \"c\", \"story\": \"s\"}")).when();
			io.github.jcondedata.aliveworkplace.guard.BanditCamps.forget(level);
			helper.assertTrue(CivicEffects.hallOf(dara) != null && CivicEffects.hallOf(dara).getBlockPos().equals(hall), "setup: Dara's hall is " + CivicEffects.hallOf(dara));
			helper.assertTrue(!raided.holds(level, dara), "no raid and no camp, and she's raided");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.BanditCamps.found(level, hall, helper.absolutePos(new BlockPos(13, 1, 13))) != null, "setup: no camp");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.BanditCamps.near(level, hall).isPresent(), "setup: the camp doesn't prey on the village");
			CivicEffects.forget();
			helper.assertTrue(CivicEffects.hallOf(dara) != null, "setup: the camp took Dara's hall");
			helper.assertTrue(raided.holds(level, dara), "a bandit camp nearby isn't a worry about raids");
			io.github.jcondedata.aliveworkplace.guard.BanditCamps.forget(level);
			helper.assertTrue(!raided.holds(level, dara), "the camp is gone and she still worries");
			// "a raid in the last 5 days": one today is the freshest of all.
			var hallEntity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			hallEntity.setLastRaidDay(today);
			helper.assertTrue(raided.holds(level, dara), "a raid today isn't a worry about raids");
			hallEntity.setLastRaidDay(today + 1);
			helper.assertTrue(!raided.holds(level, dara), "a raid tomorrow (a clock set back) is a worry");
			hallEntity.setLastRaidDay(-100);
			helper.succeed();
		});
	}

	/** "Gift Wrap (paper, string and any dye make 4)": every one of the sixteen dyes. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEverySixteenDyesMakeGiftWrap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
			for (List<ItemStack> grid : List.of(
				List.of(new ItemStack(Items.PAPER), new ItemStack(Items.STRING), new ItemStack(net.minecraft.world.item.DyeItem.byColor(color))),
				List.of(new ItemStack(net.minecraft.world.item.DyeItem.byColor(color)), new ItemStack(Items.PAPER), new ItemStack(Items.STRING)))) {
				net.minecraft.world.item.crafting.CraftingInput input = net.minecraft.world.item.crafting.CraftingInput.of(3, 1, grid);
				ItemStack out = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
					.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
				if (!out.is(ModItems.GIFT_WRAP) || out.getCount() != 4) {
					wrong.add(color + " " + grid.get(0).getItem() + " first: " + out);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "gift wrap: " + wrong);
		helper.succeed();
	}

	/** Friendship never goes below nothing: a hated gift at 30 points leaves 0, a disliked one at 10 leaves 0, and both still use up the day. */
	//$ gametest_ticks_batch AREA '60' '"qaHS1031Floor"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHS1031Floor")
	public void qaABadGiftNearNothingStopsAtNothing(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(4, 2, 5), "Dara", VillagerProfession.NONE);
			Villager bram = villager(helper, new BlockPos(7, 2, 5), "Bram", VillagerProfession.NONE);
			setDay(level, GiftGameTests.quietWeek(dara, bram));
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 8));
			Friendship.add(dara, alex, 30);
			Friendship.add(bram, alex, 10);
			Gifts.Result flesh = give(alex, dara, new ItemStack(Items.ROTTEN_FLESH));
			Gifts.Result dirt = give(alex, bram, new ItemStack(Items.DIRT));
			helper.assertTrue(flesh.outcome() == Gifts.Outcome.GIVEN && Friendship.points(dara, alex.getUUID()) == 0, "hated at 30: " + flesh + ", " + Friendship.points(dara, alex.getUUID()));
			helper.assertTrue(dirt.outcome() == Gifts.Outcome.GIVEN && Friendship.points(bram, alex.getUUID()) == 0, "disliked at 10: " + dirt + ", " + Friendship.points(bram, alex.getUUID()));
			helper.assertTrue(give(alex, dara, new ItemStack(Items.CAKE)).outcome() == Gifts.Outcome.DAY_LIMIT, "the day wasn't used up");
			helper.succeed();
		});
	}

	/**
	 * The name day as the hall's tooltip gives it and as the life-story page gives it agree, the day before, on the day
	 * and the day after; and on the day a gift does count three times.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaHS1031NameDay"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHS1031NameDay")
	public void qaTheHallAndTheLifeStoryAgreeOnTheNameDay(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 8));
			long nameDay = 28 + Gifts.nameDay(dara.getUUID()) + 1;
			helper.assertTrue(Gifts.isNameDay(dara, nameDay), "setup: day " + nameDay + " isn't her name day");
			ChoiceMenu menu = VillageHallScreen.forTest(alex, hall);
			List<String> wrong = new ArrayList<>();
			for (long day : List.of(nameDay - 1, nameDay, nameDay + 1)) {
				setDay(level, day);
				List<String> hallLines = lore(VillageHallScreen.person(level, hall, dara, alex)).stream().filter(l -> l.startsWith("Name day")).toList();
				LifeStory.render(menu, level, hall, dara, 0, alex);
				String page = menu.icon(LifeStory.NAME_DAY).getHoverName().getString();
				if (hallLines.size() != 1 || !page.startsWith("Name day")) {
					wrong.add("day " + day + ": hall " + hallLines + ", page " + page);
					continue;
				}
				if (!page.equals(hallLines.get(0))) {
					wrong.add("day " + day + ": hall says \"" + hallLines.get(0) + "\", the page \"" + page + "\"");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "the name day: " + wrong);
			setDay(level, nameDay);
			Gifts.Result r = give(alex, dara, new ItemStack(Items.COOKIE));
			helper.assertTrue(r.nameDay() && r.change() == 135, "a liked gift on her name day: " + r);
			helper.succeed();
		});
	}

	/** "Each player can give each villager one gift a day": a creative player keeps the gift in hand but is held to the same day. */
	//$ gametest_ticks_batch AREA '60' '"qaHS1031Creative"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHS1031Creative")
	public void qaACreativePlayerIsHeldToOneGiftADay(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			setDay(level, GiftGameTests.quietWeek(dara));
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 8));
			alex.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			alex.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(Items.CAKE), alex.getGameProfile().getName()));
			rightClick(alex, dara);
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 80 && alex.getMainHandItem().is(ModItems.GIFT), "the first: " + Friendship.points(dara, alex.getUUID()));
			rightClick(alex, dara);
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 80, "a creative player's second gift the same day counted: " + Friendship.points(dara, alex.getUUID()));
			helper.succeed();
		});
	}

	/** On a worker's name day a Bottle o' Enchanting counts three times as a gift (135), and still teaches her the 15 XP the spec gives it. */
	//$ gametest_ticks_batch AREA '60' '"qaHS1031BottleNameDay"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHS1031BottleNameDay")
	public void qaABottleOnHerNameDayTeachesFifteen(GameTestHelper helper) {
		BlockPos bench = new BlockPos(2, 2, 3);
		helper.setBlock(bench, Blocks.STONECUTTER);
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager marta = villager(helper, new BlockPos(4, 2, 4), "Marta", VillagerProfession.MASON);
			marta.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(bench)));
			setDay(level, 28 + Gifts.nameDay(marta.getUUID()) + 1);
			int before = marta.getVillagerXp();
			Gifts.Result r = give(HeartEventGameTests.player(helper, new BlockPos(4, 2, 6)), marta, new ItemStack(Items.EXPERIENCE_BOTTLE));
			helper.assertTrue(r.nameDay() && r.band() == Tastes.Band.LIKED && r.change() == 135, "the bottle on her name day: " + r);
			helper.assertTrue(marta.getVillagerXp() - before == 15, "her XP went up by " + (marta.getVillagerXp() - before));
			helper.succeed();
		});
	}

	// --- Text ------------------------------------------------------------------------------------------------------

	private static final Pattern BRITISH = Pattern.compile("(?i)\\b(colour\\w*|favour\\w*|honour\\w*|neighbour\\w*|grey|greys|armour\\w*|defence|offence|"
		+ "travell(er|ers|ed|ing)|cancell(ed|ing)|jewell(er|ery)|centre\\w*|theatre|metre\\w*|realis\\w*|organis\\w*|recognis\\w*|apologis\\w*|"
		+ "whilst|amongst|learnt|spelt|dreamt|mum|plough\\w*|mould\\w*|storey|licence|practise|ageing|catalogue|programme)\\b");

	/**
	 * Every sentence a player reads in gifts, heart events, life stories and keepsakes is American English (owner,
	 * 2026-10-26: favorite, honor, gray, traveler), and none is left with a stray placeholder or a doubled space.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaHeartAndGiftTextIsAmericanEnglish(GameTestHelper helper) {
		JsonObject lang;
		try (InputStream in = AliveWorkplace.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "no en_us.json");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		List<String> prefixes = List.of("heart_event.", "life_story.", "gift.", "item.aliveworkplace.gift", "item.aliveworkplace.keepsake", "chronicle.aliveworkplace.heart_event",
			"screen.aliveworkplace.life_story", "message.aliveworkplace.keepsake", "message.aliveworkplace.gift", "tooltip.aliveworkplace.gift", "aliveworkplace.config.heartEvents",
			"aliveworkplace.config.friendship");
		List<String> wrong = new ArrayList<>();
		int read = 0;
		for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
			String key = e.getKey();
			if (prefixes.stream().noneMatch(key::startsWith)) {
				continue;
			}
			read++;
			String text = e.getValue().getAsString();
			Matcher m = BRITISH.matcher(text);
			while (m.find()) {
				wrong.add(key + ": \"" + m.group() + "\"");
			}
			if (text.contains("  ") || text.matches(".*%(?![0-9]+\\$s|s|d|%).*")) {
				wrong.add(key + ": spacing or placeholder: " + text);
			}
			if (text.matches("(?s).*\\b[Aa] [AEIOUaeiou]\\w*.*") && !text.matches("(?s).*\\b[Aa] (one|use|uni\\w*|Uni\\w*|eu\\w*)\\b.*")) {
				wrong.add(key + ": \"a\" before a vowel: " + text);
			}
		}
		helper.assertTrue(read > 100, "only " + read + " sentences read");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " sentences: " + wrong);
		helper.succeed();
	}
}
