package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.CrafterWork;
import io.github.jcondedata.aliveworkplace.hall.Banquets;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.BanquetPower;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PacePower;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.legend.StrangeMoods;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * 29.18, the Grand Chef: the real file (a Rare Chef who likes wine, inspired, at the inn or born to a Chef once the
 * store has 8 kinds of meal; a cake named for the village); a banquet every 5 days at supper, two meals each of as
 * many kinds as the store has, none without the Chef or on a festival's day, once through a reload; +20 mood for 3
 * days; two babies a day for 3 days with a free bed, one a day after; chefs 25% faster; every sentence a player reads.
 * Each test changes the clock and the roster and puts them back within one tick ({@link #staged}).
 */
public class GrandChefGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos SMOKER = new BlockPos(20, 2, 15);
	private static final BlockPos STORE = new BlockPos(21, 2, 15);
	private static final ResourceLocation ID = AliveWorkplace.id("grand_chef");
	private static final long DAY = VillageNeeds.DAY;
	/** Eight kinds of meal. */
	private static final List<Item> KINDS = List.of(Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.PUMPKIN_PIE, Items.COOKED_SALMON,
		Items.COOKED_CHICKEN, Items.COOKIE, Items.COOKED_MUTTON);

	private static VillageHallBlockEntity entity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** The hall and its kitchen (a smoker, the store's chest by it), a village of radius 14; afterwards nothing is left. */
	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(SMOKER, Blocks.SMOKER);
		helper.setBlock(STORE, Blocks.CHEST);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.RADIUS = radius;
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			Banquets.forget();
			VillageNeeds.forget();
			LegendPowers.forget();
			Moods.forget();
		});
	}

	/** Runs {@code body} with only the real Grand Chef loaded, moods and needs off, then puts the clock and roster back. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend chef = Legends.get(ID).orElseThrow(() -> new AssertionError("grand_chef.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(ID, chef);
			Legends.setForTest(map);
			LegendPowers.forget();
			body.accept(chef);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		return v;
	}

	private static Villager chefOf(GameTestHelper helper, BlockPos at) {
		Villager v = villager(helper, at);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.CHEF).setLevel(5));
		return v;
	}

	/** The Grand Chef, settled here. */
	private static Villager grandChef(GameTestHelper helper, Legend legend) {
		Villager v = chefOf(helper, new BlockPos(13, 2, 13));
		Legends.make(helper.getLevel(), v, legend, "test");
		LegendPowers.forget();
		return v;
	}

	private static void gone(Villager chef) {
		LegendRecord.get((ServerLevel) chef.level()).forget(chef.getUUID());
		chef.discard();
		LegendPowers.forget();
	}

	/** The store holds {@code each} of every kind in {@code kinds}, one stack a kind. */
	private static Container stock(GameTestHelper helper, List<Item> kinds, int each) {
		Container store = (Container) helper.getBlockEntity(STORE);
		store.clearContent();
		for (int i = 0; i < kinds.size(); i++) {
			store.setItem(i, new ItemStack(kinds.get(i), each));
		}
		return store;
	}

	private static int count(Container c, Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int meals(Container c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (VillageNeeds.isMeal(c.getItem(i))) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static List<String> strings(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	private static void bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	/** The file: Rare, a Chef, likes wine; inspired, at the inn or born to a Chef once the store has 8 kinds of meal; the powers; the text. */
	//$ gametest_ticks_batch AREA '100' '"grandChefFile"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "grandChefFile")
	public void theFileAndItsConditions(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			ResourceLocation chef = AliveWorkplace.id("chef");
			helper.assertTrue(legend.rarity() == Rarity.RARE, "not Rare");
			helper.assertTrue(legend.job().equals(chef), "trade " + legend.job());
			helper.assertTrue(legend.luxury().equals(Optional.of("wine")), "likes " + legend.luxury());
			helper.assertTrue(legend.ways("inspired").stream().anyMatch(w -> w.get("trades").toString().contains("aliveworkplace:chef")), "not inspired from a Chef");
			helper.assertTrue(legend.ways("visit").stream().anyMatch(w -> !w.has("place") || "inn".equals(w.get("place").getAsString())), "not a guest at the inn");
			helper.assertTrue(legend.ways("born").stream().anyMatch(w -> w.get("trades").toString().contains("aliveworkplace:chef")), "not born to a Chef");
			// The Masterwork: a cake named for the village, from the seven rare ingredients.
			helper.assertTrue(StrangeMoods.masterworkItem(legend) == Items.CAKE, "Masterwork " + StrangeMoods.masterworkItem(legend));
			helper.assertTrue(StrangeMoods.pool(legend).equals(List.of(id(Items.GOLDEN_APPLE), id(Items.GLISTERING_MELON_SLICE), id(Items.GOLDEN_CARROT),
				id(Items.HONEYCOMB), id(Items.GLOW_BERRIES), id(Items.CHORUS_FRUIT), id(Items.PUFFERFISH))), "materials " + StrangeMoods.pool(legend));
			String name = Component.translatable(legend.masterwork().get("name").getAsString(), Component.literal("Thornholm"), Component.literal("Ada"),
				Component.literal("Bright"), Items.CAKE.getDescription()).getString();
			helper.assertTrue(name.equals("The Thornholm Midsummer Cake"), "the cake's name: " + name);
			// The powers.
			helper.assertTrue(legend.powers(BanquetPower.class).equals(List.of(new BanquetPower(5, 2, 20, 3, 3))), "banquet " + legend.powers(BanquetPower.class));
			helper.assertTrue(legend.powers(PacePower.class).equals(List.of(new PacePower(Set.of(chef), 0, 1.25f))), "pace " + legend.powers(PacePower.class));
			// 8 kinds of meal in the store: not met with 7, met with 8.
			stock(helper, KINDS.subList(0, 7), 3);
			helper.assertTrue(VillageHalls.mealKinds(level, hall) == 7, "kinds " + VillageHalls.mealKinds(level, hall));
			helper.assertFalse(legend.conditions().stream().allMatch(c -> c.met(level, hall)), "met with 7 kinds of meal");
			stock(helper, KINDS, 3);
			for (Condition c : legend.conditions()) {
				helper.assertTrue(c.met(level, hall), "not met with 8 kinds of meal: " + c.type());
			}
			Language en = Language.getInstance();
			for (String k : List.of(key(legend.powers(BanquetPower.class).get(0).describe()), "legend.aliveworkplace.grand_chef.title",
				"legend.aliveworkplace.grand_chef.lore", "legend.aliveworkplace.grand_chef.name.1", "legend.aliveworkplace.grand_chef.name.2",
				"legend.aliveworkplace.grand_chef.name.3", "masterwork.aliveworkplace.grand_chef", "message.aliveworkplace.banquet.call",
				"message.aliveworkplace.banquet.feast", "message.aliveworkplace.banquet.feast.one", "message.aliveworkplace.banquet.bare",
				"chronicle.aliveworkplace.banquet", "chronicle.aliveworkplace.banquet.one", "chronicle.aliveworkplace.banquet_bare",
				"mood.aliveworkplace.reason.banquet")) {
				helper.assertTrue(en.has(k), "no text for " + k);
			}
			String banquet = legend.powers(BanquetPower.class).get(0).describe().getString();
			helper.assertTrue(banquet.equals("Every 5 days a banquet: 2 meals each from the store, everyone who comes 20 happier for 3 days, and babies twice as often for 3 days"),
				banquet);
			String pace = legend.powers(PacePower.class).get(0).describe().getString();
			helper.assertTrue(pace.equals("Every Chef in the village works 1.25× as fast"), pace);
		});
		helper.succeed();
	}

	private static ResourceLocation id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item);
	}

	/**
	 * A banquet: called after work, at supper each grown-up eats two meals of as many kinds as the store has (here all 8),
	 * the children none; once a day; the next only after 5 days; none on a festival's day; none without the Grand Chef.
	 */
	//$ gametest_ticks_batch AREA '100' '"grandChefBanquet"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "grandChefBanquet")
	public void aBanquetTakesTwoMealsEach(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			VillageHallBlockEntity entity = entity(helper);
			List<Villager> grown = List.of(villager(helper, new BlockPos(10, 2, 10)), villager(helper, new BlockPos(11, 2, 10)),
				villager(helper, new BlockPos(12, 2, 10)), villager(helper, new BlockPos(13, 2, 10)));
			Villager child = villager(helper, new BlockPos(10, 2, 12));
			child.setAge(-24000);
			Container store = stock(helper, KINDS, 4);
			// No Grand Chef: no banquet.
			at(level, 40, 9000);
			Banquets.round(level, hall, entity);
			helper.assertTrue(entity.banquetDay() < 0, "a banquet without the Grand Chef");
			Villager chef = grandChef(helper, legend);
			// Before work is done: not yet.
			at(level, 40, 8000);
			Banquets.round(level, hall, entity);
			helper.assertTrue(entity.banquetDay() < 0, "a banquet before work was done");
			// After work: called, the village gathers; nobody has eaten yet.
			at(level, 40, 9000);
			Banquets.round(level, hall, entity);
			helper.assertTrue(entity.banquetDay() == 40 && !entity.banquetEaten() && Banquets.isOn(level, hall), "no banquet called: day " + entity.banquetDay());
			helper.assertTrue(meals(store) == 32, "eaten before supper: " + meals(store));
			// Supper: two meals each for the 5 grown-ups (the Chef too), all 8 kinds served, the child none.
			at(level, 40, Banquets.SUPPER);
			Banquets.round(level, hall, entity);
			helper.assertTrue(entity.banquetEaten(), "no feast at supper");
			helper.assertTrue(meals(store) == 32 - 10, "meals eaten: " + (32 - meals(store)) + ", not 10 (two for each of 5 grown-ups)");
			for (Item kind : KINDS) {
				helper.assertTrue(count(store, kind) < 4, "nobody had " + kind);
			}
			for (Villager v : grown) {
				helper.assertTrue(ModAttachments.BANQUET.has(v), "a guest wasn't counted");
			}
			helper.assertTrue(ModAttachments.BANQUET.has(child) && ModAttachments.BANQUET.has(chef), "the child or the Chef wasn't counted");
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> key(e.text()).equals("chronicle.aliveworkplace.banquet")
				&& e.text().getString().equals("The Grand Chef's banquet: 6 came and feasted on 8 kinds of meal")), "no chronicle line: "
				+ entity.chronicle().stream().map(e -> e.text().getString()).toList());
			// Once: later that evening nothing more is eaten.
			at(level, 40, 11500);
			Banquets.round(level, hall, entity);
			helper.assertTrue(meals(store) == 22, "a second feast the same day");
			// The next: not on days 41 to 44, on day 45; not on a festival's day (the day after, then).
			for (long day = 41; day <= 44; day++) {
				at(level, day, 9000);
				Banquets.round(level, hall, entity);
				helper.assertTrue(entity.banquetDay() == 40, "a banquet on day " + day);
			}
			at(level, 45, 9000);
			entity.setFestivalDay(45);
			Banquets.round(level, hall, entity);
			helper.assertTrue(entity.banquetDay() == 40, "a banquet on the festival's day");
			at(level, 46, 9000);
			Banquets.round(level, hall, entity);
			helper.assertTrue(entity.banquetDay() == 46, "no banquet the day after the festival: " + entity.banquetDay());
			gone(chef);
		});
		helper.succeed();
	}

	/** A store with one kind still feeds two each; a bare store feeds nobody, but everyone came (and the chronicle says so). */
	//$ gametest_ticks_batch AREA '100' '"grandChefFewKinds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "grandChefFewKinds")
	public void aBanquetWithOneKindOrNone(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			VillageHallBlockEntity entity = entity(helper);
			villager(helper, new BlockPos(10, 2, 10));
			villager(helper, new BlockPos(11, 2, 10));
			grandChef(helper, legend);
			Container store = stock(helper, List.of(Items.BREAD), 64);
			at(level, 40, Banquets.SUPPER);
			Banquets.Feast feast = Banquets.feast(level, hall, entity, legend.powers(BanquetPower.class).get(0));
			helper.assertTrue(feast.came() == 3 && feast.fed() == 3 && feast.meals() == 6 && feast.kinds() == 1, "one kind: " + feast);
			helper.assertTrue(count(store, Items.BREAD) == 58, "bread left " + count(store, Items.BREAD));
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.text().getString().equals("The Grand Chef's banquet: 3 came and feasted on 1 kind of meal")),
				"one kind's chronicle line");
			// Not enough for two each: what there is.
			stock(helper, List.of(Items.BREAD, Items.COOKIE), 2);
			feast = Banquets.feast(level, hall, entity, legend.powers(BanquetPower.class).get(0));
			helper.assertTrue(feast.meals() == 4 && meals(store) == 0 && feast.kinds() == 2, "a short store: " + feast);
			// Bare: nobody eats, everyone still came.
			store.clearContent();
			at(level, 41, Banquets.SUPPER);
			feast = Banquets.feast(level, hall, entity, legend.powers(BanquetPower.class).get(0));
			helper.assertTrue(feast.came() == 3 && feast.fed() == 0 && feast.meals() == 0, "a bare store: " + feast);
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> key(e.text()).equals("chronicle.aliveworkplace.banquet_bare")
				&& e.text().getString().equals("The Grand Chef's banquet: 3 came, but the store was bare")), "no bare chronicle line");
		});
		helper.succeed();
	}

	/** Everyone who came is 20 happier, with its reason, for 3 days (the banquet's and the next two), and not after. */
	//$ gametest_ticks_batch AREA '100' '"grandChefMood"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "grandChefMood")
	public void theBanquetsMood(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			VillageHallBlockEntity entity = entity(helper);
			Villager guest = villager(helper, new BlockPos(10, 2, 10));
			grandChef(helper, legend);
			stock(helper, KINDS, 4);
			at(level, 40, 9000);
			Banquets.round(level, hall, entity);
			at(level, 40, Banquets.SUPPER);
			Banquets.round(level, hall, entity);
			Banquets.Feasted feasted = ModAttachments.BANQUET.get(guest);
			helper.assertTrue(feasted != null && feasted.day() == 40 && feasted.mood() == 20 && feasted.days() == 3, "came: " + feasted);
			Moods.forget();
			Moods.Mood happy = Moods.work(level, guest);
			ModAttachments.BANQUET.remove(guest);
			Moods.forget();
			Moods.Mood plain = Moods.work(level, guest);
			ModAttachments.BANQUET.set(guest, feasted);
			Moods.forget();
			helper.assertTrue(happy.score() - plain.score() == 20, "after the banquet " + happy.score() + ", before " + plain.score());
			helper.assertTrue(strings(happy.good()).contains("feasted at the Grand Chef's banquet"), "the reason: " + strings(happy.good()));
			at(level, 42, 6000);
			helper.assertTrue(Banquets.mood(level, guest) != null && Banquets.mood(level, guest).points() == 20, "the mood ended before 3 days");
			at(level, 43, 6000);
			helper.assertTrue(Banquets.mood(level, guest) == null, "the mood outlasted 3 days");
			// Saved on the villager.
			CompoundTag tag = new CompoundTag();
			guest.save(tag);
			Villager again = EntityType.VILLAGER.create(level);
			again.load(tag);
			helper.assertTrue(feasted.equals(ModAttachments.BANQUET.get(again)), "the banquet wasn't saved: " + ModAttachments.BANQUET.get(again));
		});
		helper.succeed();
	}

	/**
	 * Two births a day for the banquet's 3 days with free beds (the wait halved), one a day after; none with no free bed;
	 * not before the feast is eaten.
	 */
	//$ gametest_ticks_batch AREA '100' '"grandChefBirths"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "grandChefBirths")
	public void twoBirthsADayForThreeDays(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			VillageHallBlockEntity entity = entity(helper);
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of());
			villager(helper, new BlockPos(10, 2, 10));
			villager(helper, new BlockPos(11, 2, 10));
			grandChef(helper, legend);
			VillageNeeds.Needs happy = new VillageNeeds.Needs(3, 3, 3, 3, 3, 0, 0, 1f);
			Container store = stock(helper, KINDS, 16);
			at(level, 40, 9000);
			helper.assertTrue(VillageGrowth.every(level, hall) == DAY, "the usual wait: " + VillageGrowth.every(level, hall));
			Banquets.round(level, hall, entity);
			helper.assertTrue(VillageGrowth.every(level, hall) == DAY, "halved before the feast");
			at(level, 40, Banquets.SUPPER);
			Banquets.round(level, hall, entity);
			// No free bed: no baby, banquet or not.
			long now = level.getGameTime();
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - DAY / 2) == VillageGrowth.Blocker.NO_BED, "grew with no bed: "
				+ VillageGrowth.blocker(level, hall, happy, now - DAY / 2));
			bed(helper, new BlockPos(5, 2, 20));
			bed(helper, new BlockPos(7, 2, 20));
			bed(helper, new BlockPos(9, 2, 20));
			for (long day = 40; day <= 42; day++) {
				at(level, day, Banquets.SUPPER);
				helper.assertTrue(VillageGrowth.every(level, hall) == DAY / 2, "day " + day + ": wait " + VillageGrowth.every(level, hall));
				helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - DAY / 2 + 1) == VillageGrowth.Blocker.TOO_SOON, "day " + day + ": under half a day");
			}
			at(level, 41, 6000);
			for (int i = 0; i < 2; i++) { // two babies in a day: half a day apart
				store.setItem(0, new ItemStack(Items.BREAD, 64));
				Villager baby = VillageGrowth.grow(level, hall, happy, level.getGameTime() - DAY / 2);
				helper.assertTrue(baby != null && baby.isBaby(), "baby " + (i + 1) + " didn't come half a day after the last: "
					+ VillageGrowth.blocker(level, hall, happy, level.getGameTime() - DAY / 2));
				baby.setNoAi(true);
			}
			// Day 43: one a day again.
			at(level, 43, 6000);
			store.setItem(0, new ItemStack(Items.BREAD, 64));
			helper.assertTrue(VillageGrowth.every(level, hall) == DAY, "still halved on day 43: " + VillageGrowth.every(level, hall));
			helper.assertTrue(VillageGrowth.grow(level, hall, happy, level.getGameTime() - DAY / 2) == null, "two a day after the banquet's 3 days");
			Villager baby = VillageGrowth.grow(level, hall, happy, level.getGameTime() - DAY);
			helper.assertTrue(baby != null, "no baby a day after the last on day 43: " + VillageGrowth.blocker(level, hall, happy, level.getGameTime() - DAY));
			baby.setNoAi(true);
		});
		helper.succeed();
	}

	/**
	 * Saved and loaded between the call and supper, the hall still feasts that evening, and once: loaded again after the
	 * feast, nobody eats twice.
	 */
	//$ gametest_ticks_batch AREA '100' '"grandChefReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "grandChefReload")
	public void aBanquetThroughAReload(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			villager(helper, new BlockPos(10, 2, 10));
			grandChef(helper, legend);
			Container store = stock(helper, KINDS, 4);
			at(level, 40, 9000);
			Banquets.round(level, hall, entity(helper));
			CompoundTag saved = entity(helper).saveWithFullMetadata(level.registryAccess());
			helper.setBlock(HALL, Blocks.AIR);
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity reloaded = entity(helper);
			reloaded.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue(reloaded.banquetDay() == 40 && !reloaded.banquetEaten(), "the banquet was lost in a reload: " + reloaded.banquetDay());
			at(level, 40, Banquets.SUPPER + 600);
			Banquets.round(level, hall, reloaded);
			helper.assertTrue(reloaded.banquetEaten() && meals(store) == 32 - 4, "no feast after the reload: " + (32 - meals(store)) + " eaten");
			saved = reloaded.saveWithFullMetadata(level.registryAccess());
			helper.setBlock(HALL, Blocks.AIR);
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity again = entity(helper);
			again.loadWithComponents(saved, level.registryAccess());
			Banquets.round(level, hall, again);
			helper.assertTrue(again.banquetEaten() && meals(store) == 28, "a second feast after a reload: " + (32 - meals(store)) + " eaten");
			// An old hall (no banquet saved): no banquet day.
			CompoundTag old = again.saveWithFullMetadata(level.registryAccess());
			old.remove("banquetDay");
			old.remove("banquetEaten");
			helper.setBlock(HALL, Blocks.AIR);
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			entity(helper).loadWithComponents(old, level.registryAccess());
			helper.assertTrue(entity(helper).banquetDay() == -1 && !entity(helper).banquetEaten(), "an old hall has a banquet day");
		});
		helper.succeed();
	}

	/** Chefs in the Grand Chef's village cook 25% faster (other trades don't); not once the Grand Chef is gone. */
	//$ gametest_ticks_batch AREA '100' '"grandChefPace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "grandChefPace")
	public void chefsCookFaster(GameTestHelper helper) {
		setUp(helper);
		staged(helper, legend -> {
			Villager cook = chefOf(helper, new BlockPos(20, 2, 16));
			Villager farmer = villager(helper, new BlockPos(18, 2, 18));
			farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(5));
			int before = CrafterWork.craftTicks(cook, 100);
			helper.assertTrue(LegendPowers.pace(cook) == 1f, "faster before the Grand Chef came");
			Villager chef = grandChef(helper, legend);
			helper.assertTrue(Math.abs(LegendPowers.pace(cook) - 1.25f) < 1e-4, "a chef's pace " + LegendPowers.pace(cook));
			helper.assertTrue(LegendPowers.pace(farmer) == 1f, "a farmer's pace " + LegendPowers.pace(farmer));
			int with = CrafterWork.craftTicks(cook, 100);
			helper.assertTrue(Math.abs(with - before / 1.25f) <= 2, "craft ticks " + with + " with the Grand Chef, " + before + " without");
			gone(chef);
			helper.assertTrue(LegendPowers.pace(cook) == 1f && CrafterWork.craftTicks(cook, 100) == before, "still faster with the Grand Chef gone");
		});
		helper.succeed();
	}
}
