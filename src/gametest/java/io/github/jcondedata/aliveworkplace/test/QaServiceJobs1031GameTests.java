package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bard.BardWork;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.school.Schools;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA 2026-10-31: the service jobs, from their guide pages (docs/guide/bard.md, music-stand.md, innkeeper.md,
 * inn-counter.md, teacher.md, teacher-s-desk.md, daycare-keeper.md, move-tutor.md, tutor-s-desk.md). Each test quotes the
 * sentence it checks; the player's own entry points (a sneak-right-click with the job's item, a right-click on a traveler
 * and the hire button) drive them. The Daycare Keeper and the Move Tutor need Cobblemon: only their trades are here.
 */
public class QaServiceJobs1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STATION = new BlockPos(11, 2, 11);
	private static final BlockPos STANDING = new BlockPos(12, 2, 12);

	// ---------------------------------------------------------------- helpers

	/** A player who keeps every chat and action-bar line they're shown (as ClassJobGameTests' listener). */
	static ServerPlayer listener(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-service"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	private static String name(VillagerProfession p) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).toString();
	}

	private static Optional<BlockPos> site(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos());
	}

	/** A villager standing at {@code at}, given {@code profession} by the player's sneak-right-click with {@code item}. */
	private static Villager picked(GameTestHelper helper, ServerPlayer player, BlockPos at, Item item, VillagerProfession profession) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		StationsSpecGameTests.rightClick(player, v, new ItemStack(item), true);
		helper.assertTrue(job(v) == profession, "setup: " + BuiltInRegistries.ITEM.getKey(item) + " gave " + name(job(v)) + ", not " + name(profession));
		return v;
	}

	private static String line(Villager v, GameTestHelper helper) {
		WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
		return e == null ? "<no status>" : e.line().getString();
	}

	private static String title(Villager v, GameTestHelper helper) {
		WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
		return e == null ? "<no status>" : e.title().getString();
	}

	private static void bed(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, 2, z), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 2, z + 1), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static Villager traveller(GameTestHelper helper, BlockPos at, long arrived, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT));
		ModAttachments.TRAVELLER.set(v, new Traveller(arrived, level));
		return v;
	}

	/** A villager saved and loaded again, as a save and reload does (the old one is gone). */
	private static Villager reloaded(GameTestHelper helper, Villager v) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		return copy;
	}

	/** One trade a profession has at one level: what is paid and what is given. */
	private record Trade(int level, Item cost, int costCount, ItemStack result) {
		boolean sells(Item item) {
			return result.is(item);
		}

		boolean buys(Item item) {
			return cost == item && result.is(Items.EMERALD);
		}
	}

	/** Every trade {@code profession} can offer, level by level (fixed random). */
	private static List<Trade> trades(GameTestHelper helper, VillagerProfession profession) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		RandomSource random = RandomSource.create(1031);
		List<Trade> out = new ArrayList<>();
		var byLevel = VillagerTrades.TRADES.get(profession);
		helper.assertTrue(byLevel != null, name(profession) + " has no trades at all");
		for (int lvl = 1; lvl <= 5; lvl++) {
			VillagerTrades.ItemListing[] listings = byLevel.get(lvl);
			if (listings == null) {
				continue;
			}
			for (VillagerTrades.ItemListing listing : listings) {
				MerchantOffer offer = listing.getOffer(v, random);
				if (offer != null) {
					out.add(new Trade(lvl, offer.getBaseCostA().getItem(), offer.getBaseCostA().getCount(), offer.getResult()));
				}
			}
		}
		return out;
	}

	private static Optional<Integer> firstLevel(List<Trade> trades, Predicate<Trade> which) {
		return trades.stream().filter(which).map(Trade::level).min(Integer::compare);
	}

	private static void expect(GameTestHelper helper, List<Trade> trades, String what, Predicate<Trade> which, int minLevel, int maxLevel) {
		Optional<Integer> at = firstLevel(trades, which);
		helper.assertTrue(at.isPresent(), "no trade: " + what);
		helper.assertTrue(at.get() >= minLevel && at.get() <= maxLevel, what + " first offered at level " + at.get() + ", expected " + minLevel + ".." + maxLevel);
	}

	// ---------------------------------------------------------------- the Bard

	/**
	 * bard.md: "A jukebox never takes a villager by itself. Stand a villager by one and sneak-right-click them with any
	 * music disc." A plain right-click with a disc doesn't; a disc with no jukebox near says the job needs one.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaBardHire"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaBardHire")
	public void bardIsPickedWithADiscAtAJukeboxOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.JUKEBOX);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(job(v) == VillagerProfession.NONE, "the jukebox took a villager by itself: " + name(job(v)));
			v.moveTo(helper.absolutePos(STANDING).getCenter().subtract(0, 0.5, 0)); // back by the jukebox (they wander while we wait)
			v.setNoAi(true);
			StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.MUSIC_DISC_13), false);
			helper.assertTrue(job(v) == VillagerProfession.NONE, "a plain right-click with a disc gave " + name(job(v)));
			StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.MUSIC_DISC_PIGSTEP), true);
			helper.assertTrue(job(v) == ModVillagers.BARD, "a sneak-right-click with Pigstep gave " + name(job(v)));
			helper.assertTrue(site(v).equals(Optional.of(helper.absolutePos(STATION))), "the bard works at " + site(v) + ", not the jukebox");

			Villager far = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 20));
			seen.clear();
			StationsSpecGameTests.rightClick(player, far, new ItemStack(Items.MUSIC_DISC_CAT), true);
			helper.assertTrue(job(far) == VillagerProfession.NONE, "a disc with no jukebox near gave " + name(job(far)));
			helper.assertTrue(seen.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.job.needs_station")
				&& c.getString().contains("Bard") && c.getString().contains("Jukebox")), "not told the Bard needs a jukebox: "
				+ seen.stream().map(Component::getString).toList());
			helper.succeed();
		});
	}

	/** bard.md: "Every morning and evening the bard plays at the jukebox." An evening set plays the chest's disc. */
	//$ gametest_ticks_batch AREA '400' '"qaBardEvening"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBardEvening")
	public void bardPlaysTheEveningSet(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(9600);
		helper.setBlock(STATION, Blocks.JUKEBOX);
		helper.setBlock(new BlockPos(11, 2, 14), Blocks.CHEST);
		Container c = helper.getBlockEntity(new BlockPos(11, 2, 14));
		c.setItem(0, new ItemStack(Items.MUSIC_DISC_MALL));
		Villager bard = picked(helper, StationsSpecGameTests.player(helper), STANDING, Items.MUSIC_DISC_MALL, ModVillagers.BARD);
		helper.succeedWhen(() -> {
			helper.assertTrue(BardWork.playing(bard).map(s -> s.is(JukeboxSongs.MALL)).orElse(false), "not playing Mall in the evening");
			helper.assertTrue(c.countItem(Items.MUSIC_DISC_MALL) == 1, "the disc left the chest");
		});
	}

	/** bard.md: plays "every morning and evening" — so not at midday, discs or not. */
	//$ gametest_ticks_batch AREA '400' '"qaBardMidday"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBardMidday")
	public void bardDoesNotPlayAtMidday(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(6000);
		helper.setBlock(STATION, Blocks.JUKEBOX);
		helper.setBlock(new BlockPos(11, 2, 14), Blocks.CHEST);
		Container c = helper.getBlockEntity(new BlockPos(11, 2, 14));
		c.setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
		Villager bard = picked(helper, StationsSpecGameTests.player(helper), STANDING, Items.MUSIC_DISC_CAT, ModVillagers.BARD);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(BardWork.playing(bard).isEmpty(), "the bard plays at midday: " + BardWork.playing(bard));
			helper.assertTrue(c.countItem(Items.MUSIC_DISC_CAT) == 1, "the disc left the chest");
			helper.succeed();
		});
	}

	/**
	 * bard.md: "With music discs in a chest within 8 blocks, they play them ... with notes floating over their head."
	 * "Over their head: what they're playing." A chest exactly 8 blocks from the jukebox counts.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaBardReach8"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBardReach8")
	public void aChestEightBlocksAwayFeedsTheBard(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1500);
		BlockPos stand = new BlockPos(2, 2, 10);
		helper.setBlock(stand, Blocks.JUKEBOX);
		BlockPos chest = stand.east(8);
		helper.setBlock(chest, Blocks.CHEST);
		Container c = helper.getBlockEntity(chest);
		c.setItem(5, new ItemStack(Items.MUSIC_DISC_STRAD));
		Villager bard = picked(helper, StationsSpecGameTests.player(helper), stand.north(), Items.MUSIC_DISC_STRAD, ModVillagers.BARD);
		helper.succeedWhen(() -> {
			helper.assertTrue(BardWork.playing(bard).map(s -> s.is(JukeboxSongs.STRAD)).orElse(false), "the disc 8 blocks off isn't played");
			helper.assertTrue(line(bard, helper).contains("strad"), "the status doesn't say what's playing: " + line(bard, helper));
			helper.assertTrue(c.countItem(Items.MUSIC_DISC_STRAD) == 1, "the disc left the chest");
		});
	}

	/**
	 * bard.md: discs count "in a chest within 8 blocks"; "No discs? They make up a little tune on the harp." A chest 9
	 * blocks away doesn't count, so the bard plays the harp, and the disc stays put.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaBardReach9"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBardReach9")
	public void aChestNineBlocksAwayLeavesTheHarp(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1500);
		BlockPos stand = new BlockPos(2, 2, 10);
		helper.setBlock(stand, Blocks.JUKEBOX);
		BlockPos chest = stand.east(9);
		helper.setBlock(chest, Blocks.CHEST);
		Container c = helper.getBlockEntity(chest);
		c.setItem(0, new ItemStack(Items.MUSIC_DISC_STRAD));
		Villager bard = picked(helper, StationsSpecGameTests.player(helper), stand.north(), Items.MUSIC_DISC_STRAD, ModVillagers.BARD);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(BardWork.playing(bard).isEmpty(), "played a disc from 9 blocks off: " + BardWork.playing(bard));
			helper.assertTrue(line(bard, helper).equals("playing a tune"), "not on the harp: " + line(bard, helper));
			helper.assertTrue(c.countItem(Items.MUSIC_DISC_STRAD) == 1, "the disc left the chest");
			helper.succeed();
		});
	}

	/** bard.md: "they play them one after another, going round all the discs there". 11 (slot 0), then Cat. */
	//$ gametest_ticks_batch AREA '2100' '"qaBardRound"'
	@GameTest(template = AREA, timeoutTicks = 2100, batch = "qaBardRound")
	public void bardPlaysTheDiscsOneAfterAnother(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1000);
		helper.setBlock(STATION, Blocks.JUKEBOX);
		helper.setBlock(new BlockPos(11, 2, 14), Blocks.CHEST);
		Container c = helper.getBlockEntity(new BlockPos(11, 2, 14));
		c.setItem(0, new ItemStack(Items.MUSIC_DISC_11));
		c.setItem(1, new ItemStack(Items.MUSIC_DISC_CAT));
		Villager bard = picked(helper, StationsSpecGameTests.player(helper), STANDING, Items.MUSIC_DISC_11, ModVillagers.BARD);
		boolean[] heard11 = {false};
		helper.onEachTick(() -> {
			if (BardWork.playing(bard).map(s -> s.is(JukeboxSongs.ELEVEN)).orElse(false)) {
				heard11[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(heard11[0], "never played 11");
			helper.assertTrue(BardWork.playing(bard).map(s -> s.is(JukeboxSongs.CAT)).orElse(false), "Cat didn't follow 11: " + BardWork.playing(bard));
			helper.assertTrue(c.countItem(Items.MUSIC_DISC_11) == 1 && c.countItem(Items.MUSIC_DISC_CAT) == 1, "a disc left the chest");
		});
	}

	/**
	 * bard.md: "Bards buy string, and sell note blocks, a goat horn and, from Journeyman up, the discs "cat", "blocks"
	 * and, at Master, "otherside"."
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bardTradesAsTheGuideLists(GameTestHelper helper) {
		List<Trade> t = trades(helper, ModVillagers.BARD);
		expect(helper, t, "string bought", x -> x.buys(Items.STRING), 1, 5);
		expect(helper, t, "note blocks sold", x -> x.sells(Items.NOTE_BLOCK), 1, 5);
		expect(helper, t, "a goat horn sold", x -> x.sells(Items.GOAT_HORN), 1, 5);
		expect(helper, t, "cat sold from Journeyman", x -> x.sells(Items.MUSIC_DISC_CAT), 3, 5);
		expect(helper, t, "blocks sold from Journeyman", x -> x.sells(Items.MUSIC_DISC_BLOCKS), 3, 5);
		expect(helper, t, "otherside sold at Master", x -> x.sells(Items.MUSIC_DISC_OTHERSIDE), 5, 5);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Innkeeper

	/**
	 * innkeeper.md: "Stand a villager by a Shop Counter and sneak-right-click them with a bed." A plain right-click
	 * doesn't; a bed with no counter near says the job needs one.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaInnHire"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaInnHire")
	public void innkeeperIsPickedWithABedAtAShopCounter(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.SHOP_COUNTER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.RED_BED), false);
		helper.assertTrue(job(v) == VillagerProfession.NONE, "a plain right-click with a bed gave " + name(job(v)));
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.LIGHT_BLUE_BED), true);
		helper.assertTrue(job(v) == ModVillagers.INNKEEPER, "a sneak-right-click with a bed gave " + name(job(v)));
		helper.assertTrue(site(v).equals(Optional.of(helper.absolutePos(STATION))), "the innkeeper works at " + site(v));

		Villager far = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 20));
		seen.clear();
		StationsSpecGameTests.rightClick(player, far, new ItemStack(Items.RED_BED), true);
		helper.assertTrue(job(far) == VillagerProfession.NONE, "a bed with no counter near gave " + name(job(far)));
		helper.assertTrue(seen.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.job.needs_station") && c.getString().contains("Innkeeper")),
			"not told the Innkeeper needs a counter: " + seen.stream().map(Component::getString).toList());
		helper.succeed();
	}

	/** innkeeper.md: "Stops when: there's no free bed nearby ("needs a free bed for guests")." No traveler comes. */
	//$ gametest_ticks_batch AREA '500' '"qaInnNoBed"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaInnNoBed")
	public void noFreeBedNoTravelerAndItSaysSo(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1500);
		helper.setBlock(STATION, ModBlocks.SHOP_COUNTER);
		Villager inn = picked(helper, StationsSpecGameTests.player(helper), STANDING, Items.RED_BED, ModVillagers.INNKEEPER);
		helper.runAfterDelay(450, () -> {
			helper.assertTrue(Innkeepers.guests(helper.getLevel(), helper.absolutePos(STATION)).isEmpty(), "a traveler came with no bed");
			helper.assertTrue(line(inn, helper).equals("needs a free bed for guests"), "status: " + line(inn, helper));
			helper.assertTrue(title(inn, helper).equals("Innkeeper · 0 guests hosted"), "title: " + title(inn, helper));
			helper.succeed();
		});
	}

	/** innkeeper.md: "Guests: one traveler a morning, two at a time"; "the inn is full". Two staying: no third. */
	//$ gametest_ticks_batch AREA '500' '"qaInnFull"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaInnFull")
	public void twoGuestsFillTheInn(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1500);
		helper.setBlock(STATION, ModBlocks.SHOP_COUNTER);
		bed(helper, 4, 15);
		bed(helper, 6, 15);
		bed(helper, 8, 15);
		long now = helper.getLevel().getGameTime();
		traveller(helper, new BlockPos(3, 2, 3), now, 2);
		traveller(helper, new BlockPos(5, 2, 3), now, 3);
		Villager inn = picked(helper, StationsSpecGameTests.player(helper), STANDING, Items.RED_BED, ModVillagers.INNKEEPER);
		helper.runAfterDelay(450, () -> {
			int guests = Innkeepers.guests(helper.getLevel(), helper.absolutePos(STATION)).size();
			helper.assertTrue(guests == 2, guests + " guests: a third came to a full inn");
			helper.assertTrue(line(inn, helper).startsWith("the inn is full"), "status: " + line(inn, helper));
			helper.succeed();
		});
	}

	/** innkeeper.md: "Each morning ... a traveler comes to stay." In the afternoon, with free beds, nobody arrives. */
	//$ gametest_ticks_batch AREA '500' '"qaInnAfternoon"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaInnAfternoon")
	public void noTravelerArrivesInTheAfternoon(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(8000);
		helper.setBlock(STATION, ModBlocks.SHOP_COUNTER);
		bed(helper, 4, 15); // the innkeeper may sleep in one: "Put free beds nearby for the guests"
		bed(helper, 6, 15);
		bed(helper, 8, 15);
		Villager inn = picked(helper, StationsSpecGameTests.player(helper), STANDING, Items.RED_BED, ModVillagers.INNKEEPER);
		helper.runAfterDelay(450, () -> {
			helper.assertTrue(Innkeepers.guests(helper.getLevel(), helper.absolutePos(STATION)).isEmpty(), "a traveler arrived in the afternoon");
			helper.assertTrue(line(inn, helper).equals("waiting for travelers"), "status: " + line(inn, helper));
			helper.succeed();
		});
	}

	/**
	 * innkeeper.md: "Right-click a traveler to see what they'd be and what they cost: 8 emeralds for an Apprentice, 16 for
	 * a Journeyman, 32 for an Expert". One emerald short is refused (and nothing taken); the exact price hires them,
	 * and they're no longer a traveler.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaInnPrices"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaInnPrices")
	public void travelersCostEightSixteenThirtyTwo(GameTestHelper helper) {
		Leftovers.clear(helper);
		long now = helper.getLevel().getGameTime();
		int[][] cases = {{2, 8}, {3, 16}, {4, 32}};
		for (int i = 0; i < cases.length; i++) {
			int lvl = cases[i][0];
			int price = cases[i][1];
			Villager guest = traveller(helper, new BlockPos(4 + 5 * i, 2, 6), now, lvl);
			ServerPlayer player = StationsSpecGameTests.player(helper);
			StationsSpecGameTests.rightClick(player, guest, ItemStack.EMPTY, false);
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, price - 1));
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "level " + lvl + ": right-click opened " + player.containerMenu);
			ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
			ItemStack button = menu.icon(Innkeepers.HIRE_SLOT);
			helper.assertTrue(button.getHoverName().getString().contains(String.valueOf(price)), "level " + lvl + ": the button reads "
				+ button.getHoverName().getString() + ", not " + price);
			menu.press(Innkeepers.HIRE_SLOT, player);
			helper.assertTrue(Innkeepers.isTraveller(guest), "level " + lvl + ": hired for " + (price - 1) + " emeralds");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == price - 1, "level " + lvl + ": a refused hire took emeralds");
			player.getInventory().add(new ItemStack(Items.EMERALD, 1));
			menu.press(Innkeepers.HIRE_SLOT, player);
			helper.assertTrue(!Innkeepers.isTraveller(guest), "level " + lvl + ": not hired for " + price);
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "level " + lvl + ": "
				+ player.getInventory().countItem(Items.EMERALD) + " emeralds left after paying " + price);
		}
		helper.succeed();
	}

	/** innkeeper.md: "Nobody hires them? After two days they move on." One a little short of two days stays. */
	//$ gametest_ticks_batch AREA '500' '"qaInnStay"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaInnStay")
	public void travelersMoveOnAfterTwoDaysNotBefore(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(8000);
		helper.setBlock(STATION, ModBlocks.SHOP_COUNTER);
		bed(helper, 4, 15);
		long now = helper.getLevel().getGameTime();
		Villager staying = traveller(helper, new BlockPos(3, 2, 3), now - 48000 + 2000, 2);
		Villager leaving = traveller(helper, new BlockPos(5, 2, 3), now - 48000 - 1, 2);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		picked(helper, player, STANDING, Items.RED_BED, ModVillagers.INNKEEPER);
		player.moveTo(player.getX(), player.getY() + 200, player.getZ()); // travelers move on when nobody's watching
		helper.runAfterDelay(450, () -> {
			helper.assertTrue(!leaving.isAlive(), "a traveler two days in didn't move on");
			helper.assertTrue(staying.isAlive() && Innkeepers.isTraveller(staying), "a traveler left before their two days were up");
			helper.succeed();
		});
	}

	/**
	 * innkeeper.md: a morning traveler comes to a free bed, "Travelers already know a trade: most are Apprentices or
	 * Journeymen"; "Over their head: how many guests they've hosted". Both survive a save and reload.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaInnReload"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaInnReload")
	public void aTravelerAndTheHostedCountSurviveAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1500);
		helper.setBlock(STATION, ModBlocks.SHOP_COUNTER);
		bed(helper, 4, 15);
		Villager inn = picked(helper, StationsSpecGameTests.player(helper), STANDING, Items.RED_BED, ModVillagers.INNKEEPER);
		helper.runAfterDelay(450, () -> {
			List<Villager> guests = Innkeepers.guests(helper.getLevel(), helper.absolutePos(STATION));
			helper.assertTrue(guests.size() == 1, guests.size() + " travelers came on a morning with one free bed");
			Villager guest = guests.get(0);
			int lvl = Innkeepers.traveller(guest).level();
			helper.assertTrue(lvl >= 2 && lvl <= 4, "a traveler of level " + lvl);
			String shown = guest.getDisplayName().getString();
			helper.assertTrue(shown.startsWith("Traveler (") && !shown.contains("Traveller"), "the traveler is called " + shown);
			helper.assertTrue(title(inn, helper).equals("Innkeeper · 1 guest hosted"), "title: " + title(inn, helper));
			Villager innAgain = reloaded(helper, inn);
			Villager guestAgain = reloaded(helper, guest);
			helper.assertTrue(job(innAgain) == ModVillagers.INNKEEPER && ModAttachments.GUESTS_HOSTED.getOrElse(innAgain, 0) == 1,
				"after a reload: " + name(job(innAgain)) + ", hosted " + ModAttachments.GUESTS_HOSTED.getOrElse(innAgain, 0));
			helper.assertTrue(Innkeepers.isTraveller(guestAgain) && Innkeepers.traveller(guestAgain).level() == lvl
				&& job(guestAgain) == VillagerProfession.NITWIT, "the traveler changed in a reload");
			helper.succeed();
		});
	}

	/**
	 * innkeeper.md: "Innkeepers buy wheat and sugar, and sell bread, mushroom stew, a red bed, pumpkin pies, cake, lanterns
	 * and, at Master, a jukebox."
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void innkeeperTradesAsTheGuideLists(GameTestHelper helper) {
		List<Trade> t = trades(helper, ModVillagers.INNKEEPER);
		expect(helper, t, "wheat bought", x -> x.buys(Items.WHEAT), 1, 5);
		expect(helper, t, "sugar bought", x -> x.buys(Items.SUGAR), 1, 5);
		for (Item sold : List.of(Items.BREAD, Items.MUSHROOM_STEW, Items.RED_BED, Items.PUMPKIN_PIE, Items.CAKE, Items.LANTERN)) {
			expect(helper, t, BuiltInRegistries.ITEM.getKey(sold) + " sold", x -> x.sells(sold), 1, 5);
		}
		expect(helper, t, "a jukebox sold at Master", x -> x.sells(Items.JUKEBOX), 5, 5);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Teacher

	/**
	 * teacher.md: "Stand a villager by a lectern and sneak-right-click them with a book. (Lapis brings the Librarian back,
	 * paper picks the Scholar.)" A book with no lectern near says the Teacher needs one.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaTeacherHire"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTeacherHire")
	public void teacherIsPickedWithABookAtALectern(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, Blocks.LECTERN);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.BOOK), false);
		helper.assertTrue(job(v) == VillagerProfession.NONE, "a plain right-click with a book gave " + name(job(v)));
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.BOOK), true);
		helper.assertTrue(job(v) == ModVillagers.TEACHER, "a book gave " + name(job(v)));
		helper.assertTrue(site(v).equals(Optional.of(helper.absolutePos(STATION))), "the teacher works at " + site(v));
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.LAPIS_LAZULI), true);
		helper.assertTrue(job(v) == VillagerProfession.LIBRARIAN, "lapis gave " + name(job(v)));
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.PAPER), true);
		helper.assertTrue(job(v) == ModVillagers.SCHOLAR, "paper gave " + name(job(v)));
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.BOOK), true);
		helper.assertTrue(job(v) == ModVillagers.TEACHER, "a book didn't bring the teacher back: " + name(job(v)));

		Villager far = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 20));
		seen.clear();
		StationsSpecGameTests.rightClick(player, far, new ItemStack(Items.BOOK), true);
		helper.assertTrue(job(far) == VillagerProfession.NONE, "a book with no lectern near gave " + name(job(far)));
		helper.assertTrue(seen.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.job.needs_station") && c.getString().contains("Teacher")
				&& c.getString().contains("Lectern")), "not told the Teacher needs a lectern: " + seen.stream().map(Component::getString).toList());
		helper.succeed();
	}

	/**
	 * teacher.md: "every child within 5 blocks of the lectern has lessons", the others within 32 are called over; "Over
	 * their head: how many pupils they've taught, and how many children they're teaching or calling in; "no children to
	 * teach" when there are none."
	 */
	//$ gametest_ticks_batch AREA '400' '"qaTeacherClass"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTeacherClass")
	public void inClassWithinFiveTheRestCalledIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(3000);
		helper.setBlock(STATION, Blocks.LECTERN);
		Villager teacher = picked(helper, StationsSpecGameTests.player(helper), STANDING, Items.BOOK, ModVillagers.TEACHER);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(line(teacher, helper).equals("no children to teach"), "with no children: " + line(teacher, helper));
			Villager near = helper.spawn(EntityType.VILLAGER, STATION.west(4));
			Villager away = helper.spawn(EntityType.VILLAGER, STATION.west(9));
			for (Villager child : List.of(near, away)) {
				child.setAge(-24000);
				child.setNoAi(true);
			}
			helper.runAfterDelay(100, () -> {
				helper.assertTrue(Schools.lessons(near) > 0, "the child 4 blocks from the lectern had no lessons");
				helper.assertTrue(Schools.lessons(away) == 0, "the child 9 blocks away had lessons: " + Schools.lessons(away));
				helper.assertTrue(away.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent(), "the child 9 blocks away wasn't called in");
				helper.assertTrue(line(teacher, helper).equals("teaching 1 child"), "status: " + line(teacher, helper));
				helper.assertTrue(title(teacher, helper).equals("Teacher · 0 pupils taught"), "title: " + title(teacher, helper));
				helper.succeed();
			});
		});
	}

	/** teacher.md: "Works with: the children within 32 blocks". A child 32 blocks off is called in; one 33 off isn't. */
	//$ gametest_ticks_batch HUGE '300' '"qaTeacherReach"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaTeacherReach")
	public void childrenWithinThirtyTwoAreCalled(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(3000);
		BlockPos desk = new BlockPos(1, 2, 10);
		helper.setBlock(desk, Blocks.LECTERN);
		Villager teacher = picked(helper, StationsSpecGameTests.player(helper), desk.south(), Items.BOOK, ModVillagers.TEACHER);
		Villager in = helper.spawn(EntityType.VILLAGER, desk.east(32));
		Villager out = helper.spawn(EntityType.VILLAGER, desk.east(33).south(4));
		for (Villager child : List.of(in, out)) {
			child.setAge(-24000);
			child.setNoAi(true);
			child.setNoGravity(true);
		}
		Leftovers.after(helper, () -> {
			in.discard();
			out.discard();
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(in.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent(), "the child 32 blocks off wasn't called in");
			helper.assertTrue(out.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(), "the child 33 blocks off was called in");
			helper.assertTrue(line(teacher, helper).equals("calling 1 child in"), "status: " + line(teacher, helper));
			helper.succeed();
		});
	}

	/** teacher.md: "By day the teacher calls over the children". At midnight a child by the lectern has no lessons. */
	//$ gametest_ticks_batch AREA '300' '"qaTeacherNight"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaTeacherNight")
	public void noLessonsAtNight(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(18000);
		helper.setBlock(STATION, Blocks.LECTERN);
		picked(helper, StationsSpecGameTests.player(helper), STANDING, Items.BOOK, ModVillagers.TEACHER);
		Villager child = helper.spawn(EntityType.VILLAGER, STATION.west(2));
		child.setAge(-24000);
		child.setNoAi(true);
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(Schools.lessons(child) == 0, "lessons at midnight: " + Schools.lessons(child));
			helper.succeed();
		});
	}

	/**
	 * teacher.md: "A couple of minutes of lessons and a child has been to school. When they grow up and take a job, they
	 * start as an Apprentice instead of a Novice, with the Novice and Apprentice trades." Schooling survives a save and
	 * reload, and the job is taken the player's way (wheat by a composter).
	 */
	//$ gametest_ticks_batch AREA '600' '"qaTeacherSchooled"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaTeacherSchooled")
	public void aSchooledChildGrowsUpAnApprenticeAfterAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		int needed = Schools.LESSONS_NEEDED;
		Schools.LESSONS_NEEDED = 60; // a few rounds of class instead of a couple of minutes
		Leftovers.after(helper, () -> Schools.LESSONS_NEEDED = needed);
		helper.setDayTime(3000);
		helper.setBlock(STATION, Blocks.LECTERN);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager teacher = picked(helper, player, STANDING, Items.BOOK, ModVillagers.TEACHER);
		Villager child = helper.spawn(EntityType.VILLAGER, STATION.west(2));
		child.setAge(-24000);
		child.setNoAi(true);
		BlockPos composter = new BlockPos(3, 2, 3);
		helper.setBlock(composter, Blocks.COMPOSTER);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			if (!done[0]) {
				helper.assertTrue(Schools.isSchooled(child), "not schooled yet: " + Schools.lessons(child));
				helper.assertTrue(ModAttachments.PUPILS_TAUGHT.getOrElse(teacher, 0) == 1, "pupils: " + ModAttachments.PUPILS_TAUGHT.getOrElse(teacher, 0));
				done[0] = true;
				Villager again = reloaded(helper, child);
				helper.assertTrue(Schools.isSchooled(again), "schooling lost in a reload");
				again.setAge(0);
				again.moveTo(helper.absolutePos(composter.east()).getCenter().subtract(0, 0.5, 0));
				StationsSpecGameTests.rightClick(player, again, new ItemStack(Items.WHEAT), true);
				helper.assertTrue(job(again) == VillagerProfession.FARMER, "wheat gave the grown-up " + name(job(again)));
				helper.assertTrue(again.getVillagerData().getLevel() == 2, "the schooled grown-up started at level " + again.getVillagerData().getLevel());
				helper.assertTrue(again.getOffers().size() >= 4, "Novice and Apprentice trades: " + again.getOffers().size());
			}
		});
	}

	/**
	 * teacher.md: "Teachers buy paper, ink sacs and feathers, and sell books, a book and quill, a map, a bookshelf, a clock
	 * and, at Master, bottles o' enchanting."
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void teacherTradesAsTheGuideLists(GameTestHelper helper) {
		List<Trade> t = trades(helper, ModVillagers.TEACHER);
		for (Item bought : List.of(Items.PAPER, Items.INK_SAC, Items.FEATHER)) {
			expect(helper, t, BuiltInRegistries.ITEM.getKey(bought) + " bought", x -> x.buys(bought), 1, 5);
		}
		for (Item sold : List.of(Items.BOOK, Items.WRITABLE_BOOK, Items.MAP, Items.BOOKSHELF, Items.CLOCK)) {
			expect(helper, t, BuiltInRegistries.ITEM.getKey(sold) + " sold", x -> x.sells(sold), 1, 5);
		}
		expect(helper, t, "bottles o' enchanting sold at Master", x -> x.sells(Items.EXPERIENCE_BOTTLE), 5, 5);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Move Tutor and the Daycare Keeper

	/**
	 * move-tutor.md: "Tutors buy paper, books and books and quills, and sell books, bottles o' enchanting and, at Master,
	 * a name tag."
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tutorTradesAsTheGuideLists(GameTestHelper helper) {
		List<Trade> t = trades(helper, ModVillagers.TUTOR);
		for (Item bought : List.of(Items.PAPER, Items.BOOK, Items.WRITABLE_BOOK)) {
			expect(helper, t, BuiltInRegistries.ITEM.getKey(bought) + " bought", x -> x.buys(bought), 1, 5);
		}
		expect(helper, t, "books sold", x -> x.sells(Items.BOOK), 1, 5);
		expect(helper, t, "bottles o' enchanting sold", x -> x.sells(Items.EXPERIENCE_BOTTLE), 1, 5);
		expect(helper, t, "a name tag sold at Master", x -> x.sells(Items.NAME_TAG), 5, 5);
		helper.succeed();
	}

	/**
	 * daycare-keeper.md, its parts that don't need Cobblemon's items: "wheat bought (Novice), eggs bought and hay
	 * (Apprentice), ... the Daycare blueprint for 14 emeralds (Journeyman)".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void daycareKeeperTradesWithoutCobblemon(GameTestHelper helper) {
		List<Trade> t = trades(helper, ModVillagers.DAYCARE_KEEPER);
		expect(helper, t, "wheat bought at Novice", x -> x.buys(Items.WHEAT), 1, 1);
		expect(helper, t, "eggs bought at Apprentice", x -> x.buys(Items.EGG), 2, 2);
		expect(helper, t, "hay sold at Apprentice", x -> x.sells(Items.HAY_BLOCK), 2, 2);
		Predicate<Trade> daycare = x -> BlueprintItem.data(x.result()).map(d -> d.structure().equals(StarterBlueprints.DAYCARE.id())).orElse(false);
		expect(helper, t, "the Daycare blueprint at Journeyman", daycare, 3, 3);
		Trade bp = t.stream().filter(daycare).findFirst().orElseThrow();
		helper.assertTrue(bp.cost() == Items.EMERALD && bp.costCount() == 14, "the Daycare blueprint costs " + bp.costCount() + " " + BuiltInRegistries.ITEM.getKey(bp.cost()));
		helper.succeed();
	}
}
