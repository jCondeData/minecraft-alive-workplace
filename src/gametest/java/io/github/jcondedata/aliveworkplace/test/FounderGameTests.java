package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSlots;
import io.github.jcondedata.aliveworkplace.legend.LegendText;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.legend.StrangeMood;
import io.github.jcondedata.aliveworkplace.legend.StrangeMoods;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.FirstCity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 29.23, the Founder: the real file (Mythic, keeps their own trade, likes books, the Founder's mood at the first rise to
 * City); the mood comes once, to the most experienced Master, not at a later rise, and passes after the sulk to the
 * next; the Mythic cap and the server-wide announcement; the Charter's pages from the chronicle; the statue queued,
 * built from the chests, its beauty and mood while it stands; a wagon a week, its camp's named hall and styled
 * blueprints; the hall's fields through a save; the switches; every sentence.
 */
public class FounderGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = VillageNeeds.DAY;

	private static VillageHallBlockEntity entity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static BlockPos hall(GameTestHelper helper) {
		return helper.absolutePos(HALL);
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.RADIUS = radius;
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
			}
			Leftovers.clear(helper);
			level.setBlockAndUpdate(hall(helper), Blocks.AIR.defaultBlockState());
			VillageNeeds.forget();
			LegendPowers.forget();
			Moods.forget();
			Founder.forgetStands();
		});
	}

	/** Runs {@code body} with only the real Founder loaded, moods and needs off, then puts the clock and roster back. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		boolean strange = StrangeMoods.ENABLED;
		boolean legends = Legends.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			StrangeMoods.ENABLED = true; // off by default in GameTests, so no mood starts by itself
			Legends.ENABLED = true;
			Legends.reload(level.getServer().getResourceManager());
			Legend founder = Legends.get(Founder.ID).orElseThrow(() -> new AssertionError("founder.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(Founder.ID, founder);
			Legends.setForTest(map);
			LegendPowers.forget();
			body.accept(founder);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			StrangeMoods.ENABLED = strange;
			Legends.ENABLED = legends;
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
			Founder.forgetStands();
		}
	}

	/** A Master of {@code trade} with {@code xp}, standing still at {@code at} with a chest beside them. */
	private static Villager master(GameTestHelper helper, BlockPos at, int xp) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(5));
		v.setVillagerXp(xp);
		helper.setBlock(at.east(), Blocks.CHEST);
		return v;
	}

	private static long today(GameTestHelper helper) {
		return Chronicle.day(helper.getLevel());
	}

	private static void give(GameTestHelper helper, BlockPos chest, Item... items) {
		Container c = (Container) helper.getBlockEntity(chest);
		for (int i = 0; i < items.length; i++) {
			c.setItem(i, new ItemStack(items[i]));
		}
	}

	/** The file: Mythic, keeps their own trade, likes books, the Founder's mood at the first City, gold, emeralds and a diamond, both powers, every sentence. */
	//$ gametest_ticks_batch AREA '100' '"founderFile"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "founderFile")
	public void theFileAndItsText(GameTestHelper helper) {
		setUp(helper);
		staged(helper, legend -> {
			helper.assertTrue(legend.rarity() == Rarity.MYTHIC, "not Mythic");
			helper.assertTrue(Founder.isFounder(legend) && Founder.ownTrade(legend), "not the Founder's mood keeping their trade");
			helper.assertTrue(legend.luxury().equals(Optional.of("books")), "likes " + legend.luxury());
			helper.assertTrue(legend.conditions().size() == 1 && legend.conditions().get(0) instanceof FirstCity, "conditions " + legend.conditions());
			helper.assertTrue(StrangeMoods.pool(legend).equals(List.of(ResourceLocation.parse("minecraft:gold_block"),
				ResourceLocation.parse("minecraft:emerald_block"), ResourceLocation.parse("minecraft:diamond"))), "materials " + StrangeMoods.pool(legend));
			helper.assertTrue(legend.powers(Founder.StatuePower.class).equals(List.of(Founder.StatuePower.DEFAULT)), "statue " + legend.powers(Founder.StatuePower.class));
			helper.assertTrue(legend.powers(Founder.FoundVillagesPower.class).equals(List.of(Founder.FoundVillagesPower.DEFAULT)), "wagons");
			String statue = Founder.StatuePower.DEFAULT.describe().getString();
			helper.assertTrue(statue.equals("The builders raise the Founder's statue by the hall: worth 5 beauty, and everyone in the village is 5 happier while it stands"), statue);
			String wagons = Founder.FoundVillagesPower.DEFAULT.describe().getString();
			helper.assertTrue(wagons.equals("Every 7 days, gives the hall's owner a Founder's Wagon to found a new village"), wagons);
			String line = LegendText.rarityLine(legend).getString();
			helper.assertTrue(line.contains("keeps their own trade"), line);
			Language en = Language.getInstance();
			for (String k : List.of("legend.aliveworkplace.founder.title", "legend.aliveworkplace.founder.lore", "legend.aliveworkplace.founder.name.1",
				"legend.aliveworkplace.founder.name.2", "legend.aliveworkplace.founder.name.3", "item.aliveworkplace.founders_wagon",
				"tooltip.aliveworkplace.founders_wagon", "mood.aliveworkplace.reason.founder_statue")) {
				helper.assertTrue(en.has(k), "no text for " + k);
			}
			check(helper, Component.translatable("message.aliveworkplace.founder.mood", "Aldric", "Oakbrook"),
				"Aldric of Oakbrook is seized by the Founder's mood: the City needs its Charter");
			check(helper, Component.translatable("message.aliveworkplace.founder.wagon", "Aldric", "Oakbrook"),
				"Aldric sends you a Founder's Wagon from Oakbrook: make camp where a new village should stand");
			check(helper, Component.translatable("chronicle.aliveworkplace.founder.statue", "Aldric", "Bob"), "Aldric asked for a statue by the hall, and Bob began it");
			check(helper, Component.translatable("chronicle.aliveworkplace.founder.wagon", "Aldric", "Jesse"), "Aldric sent a Founder's Wagon with Jesse");
			check(helper, Component.translatable("tooltip.aliveworkplace.founders_wagon.from", "Oakbrook"), "From Oakbrook");
			helper.assertTrue(Founder.newName("Oakbrook").getString().equals("New Oakbrook"), Founder.newName("Oakbrook").getString());
			check(helper, Component.translatable("book.aliveworkplace.charter.entry", 3, "The village was founded"), "Day 3: The village was founded");
			check(helper, Component.translatable("book.aliveworkplace.charter.signed", "Aldric", "Oakbrook", 40),
				"So it was, and so it shall be told.\n\nSigned, Aldric,\nFounder of Oakbrook,\non day 40.");
		});
		helper.succeed();
	}

	private static void check(GameTestHelper helper, Component c, String want) {
		helper.assertTrue(c.getString().equals(want), "'" + c.getString() + "' isn't '" + want + "'");
	}

	/**
	 * The mood comes only at the first rise to City, to the most experienced Master; a Town has none; after it came and
	 * the Founder was made, a fall to Town and a rise again bring no second mood; with strange moods switched off, none.
	 */
	//$ gametest_ticks_batch AREA '200' '"founderFirstCity"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "founderFirstCity")
	public void theMoodComesOnlyAtTheFirstRiseToCity(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		Villager low = master(helper, new BlockPos(8, 2, 8), 120);
		Villager high = master(helper, new BlockPos(8, 2, 11), 250);
		helper.runAfterDelay(5, () -> staged(helper, legend -> {
			VillageHallBlockEntity e = entity(helper);
			e.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(Founder.mood(level, hall(helper), today(helper)).isEmpty(), "a mood in a Town");
			e.setRank(VillageRanks.Rank.CITY);
			StrangeMoods.ENABLED = false;
			helper.assertTrue(Founder.mood(level, hall(helper), today(helper)).isEmpty(), "a mood with strange moods off");
			StrangeMoods.ENABLED = true;
			Optional<Villager> seized = Founder.mood(level, hall(helper), today(helper));
			helper.assertTrue(seized.equals(Optional.of(high)), "seized " + seized.map(Villager::getVillagerXp) + ", not the most experienced Master: " + Founder.blocked(level, hall(helper)));
			StrangeMood mood = ModAttachments.STRANGE_MOOD.get(high);
			helper.assertTrue(mood != null && mood.inMood() && mood.legend().equals(Founder.ID) && mood.materials().size() == 3, "mood " + mood);
			helper.assertTrue(e.founderMoodDay() > 0 && e.founderTried().equals(List.of(high.getUUID())), "the hall didn't keep the mood");
			helper.assertTrue(Founder.mood(level, hall(helper), today(helper)).isEmpty(), "a second mood while the first is on");
			// the materials come: the Charter, and the Founder, who keeps their trade
			give(helper, new BlockPos(9, 2, 11), Items.GOLD_BLOCK, Items.EMERALD_BLOCK, Items.DIAMOND);
			ItemStack charter = StrangeMoods.check(level, high, today(helper));
			helper.assertTrue(charter.is(Items.WRITTEN_BOOK), "the Masterwork " + charter);
			helper.assertTrue(Legends.of(high).map(l -> l.id().equals(Founder.ID)).orElse(false), "not made the Founder");
			helper.assertTrue(high.getVillagerData().getProfession() == ModVillagers.BUILDER && high.getVillagerData().getLevel() == 5,
				"lost their trade: " + high.getVillagerData());
			helper.assertTrue(e.founderMade(), "the hall doesn't know the Founder was made");
			// a fall to Town and a rise to City again: no second mood, even with a Master free
			e.setRank(VillageRanks.Rank.TOWN);
			e.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Founder.mood(level, hall(helper), today(helper)).isEmpty(), "a mood at a later rise to City");
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(low), "the other Master was seized");
			helper.succeed();
		}));
	}

	/** A failed mood: sulking for the week, then the mood passes to the next most experienced Master, never back to the first. */
	//$ gametest_ticks_batch AREA '200' '"founderPasses"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "founderPasses")
	public void aFailedMoodPassesToTheNextMasterAfterTheSulk(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		Villager first = master(helper, new BlockPos(8, 2, 8), 900);
		Villager second = master(helper, new BlockPos(8, 2, 11), 600);
		Villager third = master(helper, new BlockPos(8, 2, 14), 300);
		helper.runAfterDelay(5, () -> staged(helper, legend -> {
			VillageHallBlockEntity e = entity(helper);
			e.setRank(VillageRanks.Rank.CITY);
			long day = today(helper);
			helper.assertTrue(Founder.mood(level, hall(helper), day).equals(Optional.of(first)), "the first mood: " + Founder.blocked(level, hall(helper)));
			StrangeMood mood = ModAttachments.STRANGE_MOOD.get(first);
			StrangeMoods.check(level, first, mood.lastDay() + 1); // nothing came: it fails
			StrangeMood sulk = ModAttachments.STRANGE_MOOD.get(first);
			helper.assertTrue(sulk != null && !sulk.inMood() && sulk.sulkUntil() == mood.lastDay() + 1 + StrangeMoods.SULK_DAYS, "sulk " + sulk);
			e.setRank(VillageRanks.Rank.TOWN); // a rank fall in the week doesn't matter either way
			helper.assertTrue(Founder.mood(level, hall(helper), mood.lastDay() + 3).isEmpty(), "passed on during the sulk");
			StrangeMoods.check(level, first, sulk.sulkUntil()); // the sulk is over
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(first), "still sulking");
			helper.assertTrue(Founder.mood(level, hall(helper), sulk.sulkUntil()).equals(Optional.of(second)), "didn't pass to the next most experienced Master");
			// the second dies in their mood: it passes on to the third, never back to the first
			second.discard();
			helper.assertTrue(Founder.mood(level, hall(helper), sulk.sulkUntil() + 1).equals(Optional.of(third)), "didn't pass on past a lost Master");
			helper.assertTrue(e.founderTried().equals(List.of(first.getUUID(), second.getUUID(), third.getUUID())), "tried " + e.founderTried());
			helper.succeed();
		}));
	}

	/** The Mythic cap (29.3): with the village's Mythic slots full the mood waits; the announcement of the Founder reaches a player far away. */
	//$ gametest_ticks_batch AREA '200' '"founderCap"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "founderCap")
	public void theMythicCapAndTheAnnouncementToEveryPlayer(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		Villager master = master(helper, new BlockPos(8, 2, 8), 500);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(5, () -> staged(helper, legend -> {
			VillageHallBlockEntity e = entity(helper);
			e.setRank(VillageRanks.Rank.CITY);
			LegendRecord record = LegendRecord.get(level);
			UUID a = UUID.randomUUID();
			UUID b = UUID.randomUUID();
			record.settled(ResourceLocation.parse("aliveworkplace:other_mythic_a"), a, level.dimension(), Optional.of(hall(helper)), Rarity.MYTHIC, "A", 1);
			record.settled(ResourceLocation.parse("aliveworkplace:other_mythic_b"), b, level.dimension(), Optional.of(hall(helper)), Rarity.MYTHIC, "B", 1);
			try {
				helper.assertTrue(LegendSlots.whyNot(level, hall(helper), legend, null).isPresent(), "a City with two Mythics has room");
				helper.assertTrue(Founder.mood(level, hall(helper), today(helper)).isEmpty(), "a mood past the Mythic cap");
			} finally {
				record.forget(a);
				record.forget(b);
			}
			helper.assertTrue(Founder.mood(level, hall(helper), today(helper)).equals(Optional.of(master)), "no mood once a slot is free: " + Founder.blocked(level, hall(helper)));
			player.teleportTo(hall(helper).getX() + 5000, 100, hall(helper).getZ());
			helper.assertTrue(LegendSlots.audience(level, hall(helper), master.blockPosition(), legend.rarity()).contains(player),
				"the Founder's announcement doesn't reach a player 5,000 blocks away");
			give(helper, new BlockPos(9, 2, 8), Items.GOLD_BLOCK, Items.EMERALD_BLOCK, Items.DIAMOND);
			StrangeMoods.check(level, master, today(helper));
			helper.assertTrue(Legends.of(master).isPresent(), "not made the Founder");
			helper.succeed();
		}));
	}

	/** The Charter: its pages are the founding, each rank, the Legends settled, the first wedding and the raids won, oldest first; signed by the Founder. */
	//$ gametest_ticks_batch AREA '200' '"founderCharter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "founderCharter")
	public void theChartersPagesComeFromTheChronicle(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		Villager master = master(helper, new BlockPos(8, 2, 8), 500);
		master.setCustomName(Component.literal("Aldric"));
		helper.runAfterDelay(5, () -> staged(helper, legend -> {
			BlockPos hall = hall(helper);
			VillageHallBlockEntity e = entity(helper);
			e.setCustomName(Component.literal("Oakbrook"));
			Chronicle.record(level, hall, Chronicle.Kind.FOUNDED, Component.literal("The village was founded"));
			Chronicle.record(level, hall, Chronicle.Kind.BIRTH, Component.translatable("chronicle.aliveworkplace.raid_won", 3)); // not a raid: a birth
			Chronicle.record(level, hall, Chronicle.Kind.RANK, Component.translatable("chronicle.aliveworkplace.rank", VillageRanks.Rank.TOWN.title()));
			Chronicle.record(level, hall, Chronicle.Kind.WEDDING, Component.literal("Ann and Bo were wed"));
			Chronicle.record(level, hall, Chronicle.Kind.WEDDING, Component.literal("Cy and Di were wed"));
			Chronicle.record(level, hall, Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.raid_won", 12));
			Chronicle.record(level, hall, Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.raid_fled"));
			Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.legend.settled", "Ada", "Seer", "Rare"));
			Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.founder.wagon", "Ada", "Jesse"));
			List<String> story = Founder.story(level, hall).stream().map(x -> x.text().getString()).toList();
			helper.assertTrue(story.size() == 5 && story.get(0).contains("founded") && story.get(1).contains("Town") && story.get(2).equals("Ann and Bo were wed")
				&& story.get(3).equals("The village fought off the raid (12 monsters)") && story.get(4).startsWith("Ada, the Seer"), "story " + story);
			e.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Founder.mood(level, hall, today(helper)).equals(Optional.of(master)), "no mood: " + Founder.blocked(level, hall));
			give(helper, new BlockPos(9, 2, 8), Items.GOLD_BLOCK, Items.EMERALD_BLOCK, Items.DIAMOND);
			ItemStack book = StrangeMoods.check(level, master, today(helper));
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content != null && content.title().raw().equals("The Charter of Oakbrook") && content.author().equals("Aldric"), "book " + content);
			List<String> pages = new ArrayList<>();
			for (Filterable<Component> p : content.pages()) {
				pages.add(p.raw().getString());
			}
			helper.assertTrue(pages.get(0).startsWith("The Charter of Oakbrook\n\nThe story of our village, as I, Aldric, set it down on day "), "cover " + pages.get(0));
			String all = String.join("|", pages);
			for (String s : story) {
				helper.assertTrue(all.contains(s), "the Charter lacks '" + s + "': " + all);
			}
			helper.assertTrue(!all.contains("Cy and Di") && !all.contains("raiders fled") && !all.contains("Founder's Wagon"), "the Charter keeps lines it shouldn't: " + all);
			helper.assertTrue(pages.size() == 1 + 2 + 1 && pages.get(pages.size() - 1).contains("Signed, Aldric,\nFounder of Oakbrook"), "pages " + pages);
			helper.assertTrue(StrangeMoods.isMasterwork(book), "not marked a Masterwork");
			helper.assertTrue(e.chronicle().stream().anyMatch(x -> x.text().getString().contains("The Charter of Oakbrook")), "the chronicle doesn't name the Charter");
			helper.succeed();
		}));
	}

	/** The statue: queued for the village's builder on open ground by the hall, built from the barrels, then worth 5 beauty and +5 mood while it stands. */
	//$ gametest_ticks_batch AREA '24000' '"founderStatue"'
	@GameTest(template = AREA, timeoutTicks = 24000, batch = "founderStatue")
	public void theStatueIsQueuedBuiltAndWorthBeautyAndMood(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getRule(ModGameRules.BUILD_DELAY).get();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer()));
		BlockPos bench = new BlockPos(24, 2, 24);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(23, 2, 23));
		Builders.employ(level, builder, helper.absolutePos(bench));
		Villager founder = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 17));
		founder.setNoAi(true);
		Villager neighbour = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 17));
		neighbour.setNoAi(true);
		BuildSite[] site = {null};
		boolean[] staged = {false};
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> {
			Moods.ENABLED = moods;
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		helper.runAfterDelay(5, () -> {
			Legends.reload(level.getServer().getResourceManager());
			Legend legend = Legends.get(Founder.ID).orElseThrow();
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(Founder.ID, legend);
			Legends.setForTest(map);
			Legends.make(level, founder, legend, "test");
			LegendPowers.forget();
			Founder.round(level, hall(helper)); // the hall's round hands the statue to the builder
			site[0] = Founder.statueSite(level, hall(helper));
			helper.assertTrue(site[0] != null && site[0].builder().equals(builder.getUUID()), "the statue wasn't queued for the builder");
			helper.assertTrue(Founder.statue(level, hall(helper)) == null, "a second statue was queued");
			BlueprintData.Placement p = site[0].placement();
			BoundingBox box = BlueprintOutline.bounds(p, BlueprintLibrary.get(level, Founder.STATUE).orElseThrow().size());
			helper.assertTrue(!box.inflatedBy(1).isInside(hall(helper)) && box.minY() == helper.absolutePos(new BlockPos(0, 2, 0)).getY(), "the statue's spot " + box);
			helper.assertTrue(Founder.beauty(level, hall(helper)) == 0, "beauty before it's built");
			BuildPlan plan = site[0].plan(level);
			List<ItemStack> stock = new ArrayList<>();
			for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
				for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
					stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
				}
			}
			BlockPos[] barrels = {new BlockPos(26, 2, 22), new BlockPos(26, 2, 23), new BlockPos(26, 2, 25)};
			for (int i = 0; i < barrels.length; i++) {
				helper.setBlock(barrels[i], Blocks.BARREL);
				BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
				for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
					barrel.setItem(slot, stock.get(i * 27 + slot));
				}
			}
			staged[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(staged[0] && site[0] != null, "not staged");
			helper.assertTrue(BuildSiteManager.get(level).get(site[0].id()) == null || site[0].isDone(), "still building: " + site[0].stage() + " " + site[0].missing());
			helper.assertTrue(!Founder.statues(level, hall(helper)).isEmpty(), "not recorded as finished");
			Founder.forgetStands();
			helper.assertTrue(Founder.stands(level, hall(helper)), "doesn't stand");
			helper.assertTrue(Founder.beauty(level, hall(helper)) == 5, "beauty " + Founder.beauty(level, hall(helper)));
			int beauty = Decorations.beauty(level, hall(helper));
			helper.assertTrue(beauty >= 5, "the village's beauty " + beauty);
			LegendPowers.MoodReason mood = Founder.statueMood(level, neighbour);
			helper.assertTrue(mood != null && mood.points() == 5 && mood.reason().getString().equals("the Founder's statue"), "mood " + mood);
			helper.assertTrue(Founder.statue(level, hall(helper)) == null, "a second statue after the first stood");
			// the figure knocked down: no beauty and no mood any more
			BoundingBox box = BlueprintOutline.bounds(site[0].placement(), BlueprintLibrary.get(level, Founder.STATUE).orElseThrow().size());
			for (int y = box.maxY() - 2; y <= box.maxY(); y++) {
				level.setBlockAndUpdate(new BlockPos(box.getCenter().getX(), y, box.getCenter().getZ()), Blocks.AIR.defaultBlockState());
			}
			Founder.forgetStands();
			helper.assertTrue(Founder.beauty(level, hall(helper)) == 0 && Founder.statueMood(level, neighbour) == null, "a broken statue still counts");
		});
	}

	/** A wagon a week to the hall's owner (none while they're away, none the next day, another a week on); its camp's hall is "New <village>" and its blueprints in the village's style. */
	//$ gametest_ticks_batch AREA '300' '"founderWagon"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "founderWagon")
	public void aWagonAWeekWithANamedHallAndTheVillagesStyles(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		Villager founder = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 17));
		founder.setNoAi(true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(5, () -> staged(helper, legend -> {
			BlockPos hall = hall(helper);
			VillageHallBlockEntity e = entity(helper);
			e.setCustomName(Component.literal("Oakbrook"));
			Legends.make(level, founder, legend, "test");
			LegendPowers.forget();
			long day = today(helper);
			helper.assertTrue(Founder.wagon(level, hall, day).isEmpty(), "a wagon with no owner");
			e.setOwner(player.getUUID(), player.getGameProfile().getName());
			// the village builds in the Cherry style
			ResourceLocation cherry = BlueprintStyles.styled(ResourceLocation.parse("aliveworkplace:starter_cottage"), "cherry");
			helper.assertTrue(BlueprintLibrary.get(level, cherry).isPresent(), "setup: no cherry cottage");
			BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(cherry, at, player.getUUID());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(at));
			helper.assertTrue(Founder.styles(level, hall).equals(List.of("cherry")), "styles " + Founder.styles(level, hall));
			ItemStack wagon = Founder.wagon(level, hall, day);
			helper.assertTrue(wagon.is(ModItems.FOUNDERS_WAGON) && player.getInventory().countItem(ModItems.FOUNDERS_WAGON) == 1, "no wagon given");
			helper.assertTrue(Founder.wagon(level, hall, day + 1).isEmpty() && Founder.wagon(level, hall, day + 6).isEmpty(), "a wagon within the week");
			helper.assertTrue(!Founder.wagon(level, hall, day + 7).isEmpty() && player.getInventory().countItem(ModItems.FOUNDERS_WAGON) == 2, "no wagon a week on");
			// its camp: the hall named after the village, the blueprints in its style
			List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(6, 2, 6)),
				box -> Founder.stockCamp(level, player, box, wagon));
			helper.assertTrue(settlers.size() == 2, "no camp");
			ChestBlockEntity chest = null;
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 8, 29)))) {
				if (level.getBlockEntity(p) instanceof ChestBlockEntity c) {
					chest = c;
				}
			}
			helper.assertTrue(chest != null, "no camp chest");
			boolean named = false;
			List<ResourceLocation> blueprints = new ArrayList<>();
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack s = chest.getItem(i);
				named |= s.is(ModBlocks.VILLAGE_HALL.asItem()) && s.getHoverName().getString().equals("New Oakbrook");
				BlueprintItem.data(s).ifPresent(d -> blueprints.add(d.structure()));
			}
			helper.assertTrue(named, "the camp's hall isn't named New Oakbrook");
			helper.assertTrue(blueprints.contains(cherry), "the cottage isn't in the Cherry style: " + blueprints);
			helper.succeed();
		}));
	}

	/** The hall's Founder fields come back from a save; an older hall without them reads the defaults. */
	//$ gametest_ticks_batch AREA '100' '"founderSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "founderSave")
	public void theHallsFounderFieldsSurviveASave(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity e = entity(helper);
			UUID a = UUID.randomUUID();
			UUID b = UUID.randomUUID();
			e.setFounderMoodDay(12);
			e.addFounderTried(a);
			e.addFounderTried(b);
			e.addFounderTried(a);
			e.setFounderMade(true);
			e.setFounderWagonDay(30);
			CompoundTag tag = e.saveWithoutMetadata(level.registryAccess());
			e.setFounderMade(false);
			e.setFounderWagonDay(0);
			e.loadWithComponents(tag, level.registryAccess());
			helper.assertTrue(e.founderMoodDay() == 12 && e.founderTried().equals(List.of(a, b)) && e.founderMade() && e.founderWagonDay() == 30,
				"after a save: " + e.founderMoodDay() + " " + e.founderTried() + " " + e.founderMade() + " " + e.founderWagonDay());
			tag.remove("founderTried");
			tag.remove("founderMade");
			tag.remove("founderWagonDay");
			e.loadWithComponents(tag, level.registryAccess());
			helper.assertTrue(e.founderTried().isEmpty() && !e.founderMade() && e.founderWagonDay() == 0, "an older hall's defaults");
			helper.succeed();
		});
	}
}
