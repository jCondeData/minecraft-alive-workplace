package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Anthems;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.BardLaureate;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSlots;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;

/**
 * 29.19, the Bard Laureate: the real file (a Rare Bard who likes books, a guest at a festival of 30 or born to a Bard);
 * the anthem (the same name always the same 16 notes on one instrument, another name another; composed when they
 * settle, kept on the hall through a reload, never composed twice; played at a festival, a rank-up, a wedding and a
 * Legend's arrival and from the button on the hall's Legends page, the notes counted; the owner's book); work songs (twice a day at the
 * busiest spot; workers within 16 blocks 25% faster only while they sing; +5 mood for the day); every sentence.
 */
public class BardLaureateGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = VillageNeeds.DAY;

	private static VillageHallBlockEntity entity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Anthems.forget();
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.RADIUS = radius;
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			Anthems.forget();
			VillageNeeds.forget();
			LegendPowers.forget();
			Moods.forget();
		});
	}

	/** Runs {@code body} with only the real Bard Laureate loaded, moods and needs off, then puts the clock and roster back. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend bard = Legends.get(BardLaureate.ID).orElseThrow(() -> new AssertionError("bard_laureate.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(BardLaureate.ID, bard);
			Legends.setForTest(map);
			LegendPowers.forget();
			body.accept(bard);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession trade) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(trade).setLevel(2));
		return v;
	}

	/** The Bard Laureate, settled here. */
	private static Villager laureate(GameTestHelper helper, Legend legend, BlockPos at) {
		Villager v = villager(helper, at, ModVillagers.BARD);
		v.setVillagerData(v.getVillagerData().setLevel(5));
		Legends.make(helper.getLevel(), v, legend, "test");
		LegendPowers.forget();
		return v;
	}

	private static void gone(Villager v) {
		LegendRecord.get((ServerLevel) v.level()).forget(v.getUUID());
		v.discard();
		LegendPowers.forget();
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** Plays out whatever anthem is playing, note by note, and returns how many notes it took. */
	private static int playOut(ServerLevel level, BlockPos hall) {
		int before = Anthems.notesPlayed(hall);
		for (int i = 0; i < 40 && Anthems.isPlaying(level, hall); i++) {
			Anthems.tick(level);
		}
		return Anthems.notesPlayed(hall) - before;
	}

	/** The file: Rare, a Bard, likes books; a guest at a festival of 30, born to a Bard; its powers; every sentence. */
	//$ gametest_ticks_batch AREA '100' '"bardFile"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "bardFile")
	public void theFileAndItsText(GameTestHelper helper) {
		setUp(helper);
		staged(helper, legend -> {
			helper.assertTrue(legend.rarity() == Rarity.RARE, "not Rare");
			helper.assertTrue(legend.job().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("bard")), "trade " + legend.job());
			helper.assertTrue(legend.luxury().equals(Optional.of("books")), "likes " + legend.luxury());
			helper.assertTrue(legend.ways("visit").stream().anyMatch(w -> "festival".equals(w.get("place").getAsString()) && w.get("crowd").getAsInt() == 30),
				"not a guest at a festival of 30");
			helper.assertTrue(legend.ways("born").stream().anyMatch(w -> w.get("trades").toString().contains("aliveworkplace:bard")), "not born to a Bard");
			helper.assertTrue(legend.powers(BardLaureate.AnthemPower.class).size() == 1, "no anthem power");
			helper.assertTrue(legend.powers(BardLaureate.WorkSongsPower.class).equals(List.of(BardLaureate.WorkSongsPower.DEFAULT)),
				"work songs " + legend.powers(BardLaureate.WorkSongsPower.class));
			String songs = legend.powers(BardLaureate.WorkSongsPower.class).get(0).describe().getString();
			helper.assertTrue(songs.equals("Twice a day sings for 2 minutes where work is busiest: workers within 16 blocks work 1.25× as fast, everyone who hears is 5 happier for the day"), songs);
			Language en = Language.getInstance();
			List<String> keys = new java.util.ArrayList<>(List.of("legend.aliveworkplace.bard_laureate.title", "legend.aliveworkplace.bard_laureate.lore",
				"legend.aliveworkplace.bard_laureate.name.1", "legend.aliveworkplace.bard_laureate.name.2", "legend.aliveworkplace.bard_laureate.name.3",
				"legend.aliveworkplace.power.anthem", "mood.aliveworkplace.reason.work_song", "chronicle.aliveworkplace.anthem",
				"message.aliveworkplace.anthem.composed", "message.aliveworkplace.anthem.book", "message.aliveworkplace.anthem.playing",
				"message.aliveworkplace.anthem.already", "item.aliveworkplace.anthem.title", "item.aliveworkplace.anthem.author",
				"item.aliveworkplace.anthem.page", "screen.aliveworkplace.anthem.has", "screen.aliveworkplace.anthem.none",
				"screen.aliveworkplace.anthem.none_hint", "screen.aliveworkplace.anthem.about", "screen.aliveworkplace.anthem.play",
				"screen.aliveworkplace.anthem.play_hint"));
			for (String instrument : Anthems.INSTRUMENTS) {
				keys.add("anthem.aliveworkplace.instrument." + instrument);
			}
			for (String k : keys) {
				helper.assertTrue(en.has(k), "no text for " + k);
			}
		});
		helper.succeed();
	}

	/** The same name always gives the same anthem (case and spaces aside); other names others; 16 notes on one instrument. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"bardCompose"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "bardCompose")
	public void theSameNameGivesTheSameAnthem(GameTestHelper helper) {
		Anthems.Anthem thornholm = Anthems.compose("Thornholm");
		helper.assertTrue(thornholm.sameAs(Anthems.compose("Thornholm")) && thornholm.sameAs(Anthems.compose("  thornholm ")),
			"Thornholm's anthem changed between two compositions");
		helper.assertTrue(thornholm.notes().length == 16 && Anthems.INSTRUMENTS.contains(thornholm.instrument()), "not 16 notes on one instrument: " + thornholm.written());
		java.util.Set<String> tunes = new java.util.HashSet<>();
		java.util.Set<String> instruments = new java.util.HashSet<>();
		for (String name : List.of("Thornholm", "Oakvale", "Brightwater", "Ashford", "Millbrook", "Stonehaven", "Willowmere", "Redcliff", "Elmstead", "Foxhollow")) {
			Anthems.Anthem a = Anthems.compose(name);
			for (int n : a.notes()) {
				helper.assertTrue(n >= 0 && n <= 24, name + "'s anthem has a note off the note block: " + n);
			}
			tunes.add(a.instrument() + ":" + a.written());
			instruments.add(a.instrument());
		}
		helper.assertTrue(tunes.size() == 10, "two names gave the same anthem: " + tunes.size() + " tunes for 10 names");
		helper.assertFalse(Anthems.compose("Oakvale").sameAs(thornholm), "Oakvale's anthem is Thornholm's");
		helper.assertTrue(instruments.size() > 1, "every anthem on the same instrument: " + instruments);
		helper.succeed();
	}

	/**
	 * Composed when a Laureate settles (from the hall's name), saved through a reload, never composed again (a new name
	 * keeps the old tune); none without a Laureate; the owner gets the book once, with the notes written out.
	 */
	//$ gametest_ticks_batch AREA '100' '"bardAnthemKept"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "bardAnthemKept")
	public void theAnthemIsComposedSavedAndBooked(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		staged(helper, legend -> {
			entity(helper).setCustomName(Component.literal("Thornholm"));
			entity(helper).setOwner(player.getUUID(), player.getGameProfile().getName());
			Anthems.round(level, hall, entity(helper));
			helper.assertTrue(entity(helper).anthem().isEmpty() && !Anthems.play(level, hall, "hall"), "an anthem without a Bard Laureate");
			helper.assertTrue(playOut(level, hall) == 0, "notes played without an anthem");
			Villager bard = laureate(helper, legend, new BlockPos(12, 2, 12));
			Anthems.round(level, hall, entity(helper));
			Anthems.Anthem anthem = entity(helper).anthem().orElseThrow(() -> new AssertionError("no anthem after the Laureate settled"));
			helper.assertTrue(anthem.sameAs(Anthems.compose("Thornholm")), "not Thornholm's anthem: " + anthem.written());
			helper.assertTrue(Anthems.occasions(hall).equals(List.of("legend")) && playOut(level, hall) == 16, "not played when composed: " + Anthems.occasions(hall));
			helper.assertTrue(entity(helper).chronicle().stream().anyMatch(e -> key(e.text()).equals("chronicle.aliveworkplace.anthem")), "no chronicle line");
			// The owner's book.
			ItemStack book = ItemStack.EMPTY;
			int books = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(Items.WRITTEN_BOOK)) {
					book = player.getInventory().getItem(i);
					books++;
				}
			}
			helper.assertTrue(books == 1 && entity(helper).anthemBookGiven(), "the owner didn't get one book: " + books);
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content != null && content.title().raw().equals("The Anthem of Thornholm"), "the book's title: " + (content == null ? null : content.title().raw()));
			String page = content.pages().get(0).raw().getString();
			String instrument = Component.translatable("anthem.aliveworkplace.instrument." + anthem.instrument()).getString();
			helper.assertTrue(page.startsWith("The Anthem of Thornholm") && page.contains("For the " + instrument + ", sixteen notes:")
				&& page.contains(Anthems.noteName(anthem.notes()[0])) && page.split("\\s+").length >= 16, "the book's page: " + page);
			// Saved through a reload; a new name doesn't recompose it; the book isn't given again.
			CompoundTag saved = entity(helper).saveWithFullMetadata(level.registryAccess());
			helper.setBlock(HALL, Blocks.AIR);
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity reloaded = entity(helper);
			reloaded.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue(reloaded.anthem().isPresent() && reloaded.anthem().get().sameAs(anthem) && reloaded.anthemBookGiven(), "the anthem was lost in a reload");
			reloaded.setCustomName(Component.literal("Oakvale"));
			Anthems.round(level, hall, reloaded);
			helper.assertTrue(reloaded.anthem().get().sameAs(anthem), "the anthem was composed again");
			helper.assertTrue(player.getInventory().countItem(Items.WRITTEN_BOOK) == 1, "a second book");
			// An old hall (no anthem fields) loads with none.
			CompoundTag old = reloaded.saveWithFullMetadata(level.registryAccess());
			old.remove("anthemInstrument");
			old.remove("anthemNotes");
			old.remove("anthemBook");
			reloaded.loadWithComponents(old, level.registryAccess());
			helper.assertTrue(reloaded.anthem().isEmpty() && !reloaded.anthemBookGiven(), "an old hall loaded with an anthem");
			gone(bard);
		});
		helper.succeed();
	}

	/** Played (16 notes each, counted) at a festival, a rank-up, a wedding, a Legend's arrival and from the hall's page. */
	//$ gametest_ticks_batch AREA '100' '"bardAnthemPlayed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "bardAnthemPlayed")
	public void theAnthemPlaysAtEveryEvent(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		staged(helper, legend -> {
			// No anthem yet: nothing plays, and the page's button says so.
			Festivals.feast(level, hall);
			helper.assertTrue(playOut(level, hall) == 0 && Anthems.occasions(hall).isEmpty(), "played without an anthem");
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			int tab = HallPages.slot(io.github.jcondedata.aliveworkplace.legend.LegendsPage.PAGE);
			helper.assertTrue(tab >= 0 && menu.icon(tab).is(Items.NETHER_STAR), "no Legends tab: " + menu.icon(tab));
			menu.press(tab, player);
			helper.assertTrue(menu.icon(Anthems.PLAY).is(Items.BARRIER), "a play button without an anthem: " + menu.icon(Anthems.PLAY));
			menu.press(Anthems.PLAY, player);
			helper.assertTrue(playOut(level, hall) == 0, "the page played without an anthem");
			Villager bard = laureate(helper, legend, new BlockPos(12, 2, 12));
			Anthems.round(level, hall, entity(helper));
			playOut(level, hall);
			Anthems.forget();
			Festivals.feast(level, hall);
			helper.assertTrue(playOut(level, hall) == 16, "the festival: " + Anthems.notesPlayed(hall) + " notes");
			VillageRanks.celebrate(level, hall, VillageRanks.Rank.VILLAGE);
			helper.assertTrue(playOut(level, hall) == 16, "the rank-up");
			Villager a = villager(helper, new BlockPos(10, 2, 10), VillagerProfession.FARMER);
			Villager b = villager(helper, new BlockPos(11, 2, 10), VillagerProfession.MASON);
			Couples.wed(level, hall, a, b);
			helper.assertTrue(playOut(level, hall) == 16, "the wedding");
			LegendSlots.announce(level, hall, bard, legend, true);
			helper.assertTrue(playOut(level, hall) == 16, "a Legend's arrival");
			ChoiceMenu again = VillageHallScreen.forTest(player, hall);
			again.press(tab, player);
			helper.assertTrue(again.icon(Anthems.PLAY).is(Items.JUKEBOX), "no play button: " + again.icon(Anthems.PLAY));
			again.press(Anthems.PLAY, player);
			again.press(Anthems.PLAY, player); // pressed twice: played once
			helper.assertTrue(playOut(level, hall) == 16, "the hall's button");
			helper.assertTrue(Anthems.occasions(hall).equals(List.of("festival", "rank", "wedding", "legend", "hall")), "occasions " + Anthems.occasions(hall));
			helper.assertTrue(Anthems.notesPlayed(hall) == 80, "notes " + Anthems.notesPlayed(hall));
			gone(bard);
		});
		helper.succeed();
	}

	/**
	 * Work songs: mid-morning and mid-afternoon once each, at the busiest spot; workers within 16 blocks 1.25× as fast only
	 * while the song lasts, one 20 blocks off not (nor hears it); hearers +5 for the day; the song is kept on the Laureate.
	 */
	//$ gametest_ticks_batch AREA '100' '"bardWorkSongs"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "bardWorkSongs")
	public void workSongsSpeedWorkersOnlyWhileSung(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		staged(helper, legend -> {
			Villager bard = laureate(helper, legend, new BlockPos(14, 2, 12));
			// A knot of three workers to the east, one alone to the west.
			Villager near = villager(helper, new BlockPos(24, 2, 20), VillagerProfession.FARMER);
			villager(helper, new BlockPos(25, 2, 21), VillagerProfession.MASON);
			villager(helper, new BlockPos(26, 2, 20), VillagerProfession.FLETCHER);
			Villager far = villager(helper, new BlockPos(1, 2, 28), VillagerProfession.LIBRARIAN);
			level.setDayTime(40 * DAY + 2000);
			BardLaureate.tick(bard);
			helper.assertTrue(!BardLaureate.singing(bard) && LegendPowers.pace(near) == 1f, "a song before mid-morning");
			level.setDayTime(40 * DAY + 3100);
			BardLaureate.tick(bard);
			BardLaureate.Song song = ModAttachments.WORK_SONG.get(bard);
			helper.assertTrue(BardLaureate.singing(bard) && song != null && song.slot() == 0, "no song mid-morning");
			helper.assertTrue(helper.absolutePos(new BlockPos(24, 2, 20)).distSqr(song.spot()) <= 8, "not at the busiest spot: " + song.spot());
			helper.assertTrue(song.until() - level.getGameTime() == 2400, "not 2 minutes: " + (song.until() - level.getGameTime()));
			bard.moveTo(song.spot().getX() + 0.5, song.spot().getY(), song.spot().getZ() + 0.5);
			helper.assertTrue(Math.abs(LegendPowers.pace(near) - 1.25f) < 0.001f, "the knot works " + LegendPowers.pace(near) + "× during the song");
			helper.assertTrue(LegendPowers.pace(far) == 1f, "a worker 20 blocks off works " + LegendPowers.pace(far) + "×");
			BardLaureate.tick(bard);
			helper.assertTrue(ModAttachments.SONG_HEARD.has(near) && !ModAttachments.SONG_HEARD.has(far), "who heard it");
			LegendPowers.MoodReason mood = BardLaureate.heardMood(level, near);
			helper.assertTrue(mood != null && mood.points() == 5 && key(mood.reason()).equals("mood.aliveworkplace.reason.work_song"), "no +5 mood");
			// Over: no faster, and not sung again this morning.
			ModAttachments.WORK_SONG.set(bard, new BardLaureate.Song(song.day(), song.slot(), level.getGameTime() - 1, song.spot()));
			helper.assertTrue(!BardLaureate.singing(bard) && LegendPowers.pace(near) == 1f, "faster after the song: " + LegendPowers.pace(near));
			level.setDayTime(40 * DAY + 3400);
			BardLaureate.tick(bard);
			helper.assertFalse(BardLaureate.singing(bard), "a second song in the morning");
			level.setDayTime(40 * DAY + 8200);
			BardLaureate.tick(bard);
			helper.assertTrue(BardLaureate.singing(bard) && ModAttachments.WORK_SONG.get(bard).slot() == 1, "no song mid-afternoon");
			// The mood lasts the day only.
			level.setDayTime(41 * DAY + 1000);
			helper.assertTrue(BardLaureate.heardMood(level, near) == null, "the song's mood outlasted the day");
			gone(bard);
			// A Laureate gone mid-song gives nothing.
			helper.assertTrue(LegendPowers.pace(near) == 1f, "faster with the Laureate gone");
		});
		helper.succeed();
	}
}
