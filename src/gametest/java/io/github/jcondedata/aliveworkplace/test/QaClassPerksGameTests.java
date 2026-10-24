package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.NobleBalls;
import io.github.jcondedata.aliveworkplace.hall.Services;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-c-1024-1553), written from ROADMAP 34.3, 34.4 and 34.7, not from the code: the 48-block reach from both sides,
 * the market only from a Market Square in the village, styled upgrades counting as their base, a worker counted at their
 * job site until the next dawn, the hall's own round counting once a day from its census; luxuries from the kitchens'
 * chests before the Storehouses', one luxury between two due households, the every-4-days boundary, a Burgher not taking
 * the class below's, the hall's dawn round taking them; the takings through the census (a jobless Noble pays, a jobless
 * Burgher and a Noble child don't, {@code treasuryPerWorker} scales it), the tooltip's split read from en_us.json, all
 * ten crafter jobs 10% faster, Noble couples counting once for wellbeing and the bonus reaching the village's wellbeing,
 * and plain festivals again once the last Noble falls. Each test runs in a batch of its own and puts shared state back.
 */
public class QaClassPerksGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos STOREHOUSE = new BlockPos(19, 2, 19);
	private static final BlockPos STORE_CHEST = new BlockPos(19, 2, 17);
	private static final long DAY = 2000;
	private static final ResourceLocation TRINKET = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_trinket");
	private static final ResourceLocation FINE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_fine_trinket");

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	// --- set-ups ---

	/** A hall at {@link #HALL}, reach 16, nothing left from earlier batches; everything shared put back afterwards. */
	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		int pace = Pace.MAX_PERCENT;
		int cents = Treasury.CENTS_PER_WORKER;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Pace.MAX_PERCENT = pace;
			Treasury.CENTS_PER_WORKER = cents;
			new WorkplaceConfig().apply();
			ClassNeeds.services = Services.HOOK;
			Services.load(Services.files(level.getServer().getResourceManager()));
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
			ClassPerks.forget();
			Festivals.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		entity.setRank(VillageRanks.Rank.HAMLET);
		return entity;
	}

	/** {@link #hall} with our shipped class files and classes on. */
	private static VillageHallBlockEntity classes(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(helper.getLevel().getServer().getResourceManager()));
		ClassPerks.forget();
		return entity;
	}

	/** {@link #hall} with a Storehouse and its chest (the store) and the luxury test ladder (Artisan: trinket; Burgher: fine trinket). */
	private static Container luxuries(GameTestHelper helper) {
		hall(helper);
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("peasant"), JsonParser.parseString("{\"tier\": 0}"));
		files.put(ours("artisan"), JsonParser.parseString("{\"tier\": 1, \"needs\": [{\"type\": \"luxury\", \"id\": \"" + TRINKET + "\"}]}"));
		files.put(ours("burgher"), JsonParser.parseString("{\"tier\": 2, \"needs\": [{\"type\": \"luxury\", \"id\": \"" + FINE + "\"}]}"));
		SocialClasses.load(files);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.assertTrue(VillageNeeds.store(helper.getLevel(), helper.absolutePos(HALL)).contains(helper.absolutePos(STORE_CHEST)), "the storehouse chest is the store");
		return helper.getBlockEntity(STORE_CHEST);
	}

	private static Villager villager(GameTestHelper helper, int x, int z, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static Villager classed(GameTestHelper helper, int x, int z, String cls, VillagerProfession job) {
		Villager v = villager(helper, x, z, cls);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		SocialClasses.seed(v, SocialClasses.get(ours(cls)));
		return v;
	}

	/** A worker of {@code job} whose job site is {@code site}. */
	private static Villager worker(GameTestHelper helper, String job, BlockPos site) {
		Villager v = villager(helper, 6, 6, job);
		VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(job));
		helper.assertTrue(profession != VillagerProfession.NONE, "the job " + job + " exists");
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		v.refreshBrain(helper.getLevel());
		site(helper, v, site);
		return v;
	}

	private static void site(GameTestHelper helper, Villager v, BlockPos site) {
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), site));
	}

	private static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	private static void wants(Villager v, int wants) {
		ModAttachments.CLASS_STANDING.set(v, new SocialClasses.Standing(0, 0, 0, 0, wants));
	}

	private static void finished(GameTestHelper helper, ResourceLocation id, BlockPos origin) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		sites.recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	private static Set<ResourceLocation> reaching(GameTestHelper helper, BlockPos home, long day) {
		return Services.reaching(helper.getLevel(), helper.absolutePos(HALL), home, day);
	}

	private static void dawn(GameTestHelper helper, long day, Villager... who) {
		SocialClasses.check(helper.getLevel(), helper.absolutePos(HALL), List.of(who), day, SocialClasses.SLICE);
	}

	private static Map<ResourceLocation, Long> had(Villager v) {
		return ModAttachments.LUXURIES_HAD.getOrElse(v, Map.of());
	}

	private static void near(GameTestHelper helper, float a, float b, String what) {
		helper.assertTrue(Math.abs(a - b) < 1e-4f, what + ": " + a + " (expected " + b + ")");
	}

	// --- 34.3: services nearby ---

	/** "Within 48 blocks": a Teacher's desk exactly 48 blocks from home reaches it (on x and on z), 49 doesn't. */
	//$ gametest_batch HUGE '"qaServiceEdge"'
	@GameTest(template = HUGE, batch = "qaServiceEdge")
	public void aServiceReachesExactlyFortyEightBlocks(GameTestHelper helper) {
		hall(helper);
		BlockPos desk = helper.absolutePos(new BlockPos(4, 2, 4));
		worker(helper, "aliveworkplace:teacher", desk);
		ResourceLocation school = ours("school");
		helper.assertTrue(reaching(helper, desk.offset(48, 0, 0), DAY).contains(school), "48 blocks east is within 48");
		helper.assertTrue(!reaching(helper, desk.offset(49, 0, 0), DAY).contains(school), "49 blocks east is past 48");
		helper.assertTrue(reaching(helper, desk.offset(0, 0, -48), DAY).contains(school), "48 blocks north is within 48");
		helper.assertTrue(!reaching(helper, desk.offset(0, 0, -49), DAY).contains(school), "49 blocks north is past 48");
		helper.assertTrue(reaching(helper, desk, DAY).contains(school), "at the desk itself");
		helper.succeed();
	}

	/** The market is village-wide, but only from a Market Square in the village: one far outside the hall's reach gives none. */
	//$ gametest_batch HUGE '"qaServiceMarket"'
	@GameTest(template = HUGE, batch = "qaServiceMarket")
	public void theMarketReachesTheWholeVillageButOnlyFromInsideIt(GameTestHelper helper) {
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		finished(helper, ours("market_square"), hall.offset(120, 0, 0));
		helper.assertTrue(!reaching(helper, hall.offset(2, 0, 2), DAY).contains(ours("market")), "a Market Square 120 blocks from the hall is in no village");
		finished(helper, ours("market_square"), hall.offset(6, 0, -6));
		Set<ResourceLocation> far = reaching(helper, hall.offset(-300, 0, 300), DAY + 1);
		helper.assertTrue(far.equals(Set.of(ours("market"))), "the village's market reaches a home 400 blocks off: " + far);
		helper.succeed();
	}

	/** Styled builds and upgrades count as their base, also both at once: Library III in Stonework, Schoolhouse II in Spruce, Inn III in Sandstone. */
	//$ gametest_batch HUGE '"qaServiceStyledUpgrade"'
	@GameTest(template = HUGE, batch = "qaServiceStyledUpgrade")
	public void aStyledUpgradeCountsAsItsBase(GameTestHelper helper) {
		hall(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(20, 2, 4));
		BlockPos home = origin.offset(-10, 0, 0);
		Map<ResourceLocation, String> builds = Map.of(
			BlueprintStyles.styled(ours("library_3"), "stonework"), "library",
			BlueprintStyles.styled(ours("schoolhouse_2"), "spruce"), "school",
			BlueprintStyles.styled(ours("inn_3"), "sandstone"), "tavern",
			ours("healing_center_4"), "clinic");
		long day = DAY;
		for (Map.Entry<ResourceLocation, String> e : builds.entrySet()) {
			Leftovers.finished(helper);
			finished(helper, e.getKey(), origin);
			Set<ResourceLocation> found = reaching(helper, home, ++day);
			helper.assertTrue(found.equals(Set.of(ours(e.getValue()))), "a finished " + e.getKey() + " gives " + e.getValue() + ", found " + found);
		}
		helper.succeed();
	}

	/** A worker counts at their job site: moved 100 blocks away, they still count that day, not from the next dawn, and again once back. */
	//$ gametest_batch HUGE '"qaServiceMoved"'
	@GameTest(template = HUGE, batch = "qaServiceMoved")
	public void aWorkerWhoseJobSiteMovesCountsUntilTheNextDawn(GameTestHelper helper) {
		hall(helper);
		BlockPos desk = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos home = desk.offset(10, 0, 10);
		Villager nurse = worker(helper, "aliveworkplace:nurse", desk);
		helper.assertTrue(reaching(helper, home, DAY).contains(ours("clinic")), "the nurse's desk gives a clinic");
		site(helper, nurse, desk.offset(100, 0, 0));
		helper.assertTrue(reaching(helper, home, DAY).contains(ours("clinic")), "the day's list stands the day the desk moved");
		helper.assertTrue(!reaching(helper, home, DAY + 1).contains(ours("clinic")), "from the next dawn the clinic is 100 blocks away");
		site(helper, nurse, desk);
		helper.assertTrue(reaching(helper, home, DAY + 2).contains(ours("clinic")), "back at the near desk, the clinic counts again");
		helper.succeed();
	}

	/** The hall's own round from its census, ten times on one day, works the list out once and finds the Innkeeper at her job site. */
	//$ gametest_batch HUGE '"qaServiceRound"'
	@GameTest(template = HUGE, batch = "qaServiceRound")
	public void theHallsRoundWorksTheListOutOnceADayFromItsCensus(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		SocialClasses.ENABLED = true;
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		BlockPos counter = helper.absolutePos(new BlockPos(8, 2, 8));
		worker(helper, "aliveworkplace:innkeeper", counter);
		Villager idle = villager(helper, 10, 10, "idle teacher");
		idle.setVillagerData(idle.getVillagerData().setProfession(ModVillagers.TEACHER)); // no job site: nowhere to teach
		long today = Chronicle.day(level);
		entity.setServices(List.of(), today - 1);
		int before = Services.countings();
		for (int i = 0; i < 10; i++) {
			Services.round(level, pos, entity, VillageHalls.census(level, pos).workers());
		}
		helper.assertTrue(Services.countings() - before == 1, "ten rounds in a day worked it out " + (Services.countings() - before) + " times");
		helper.assertTrue(entity.servicesDay() == today, "the list is today's: " + entity.servicesDay());
		List<ResourceLocation> ids = entity.services().stream().map(Services.Found::id).toList();
		helper.assertTrue(ids.equals(List.of(ours("tavern"))), "the innkeeper's tavern and no school from a teacher with no desk: " + entity.services());
		helper.assertTrue(entity.services().get(0).pos().equals(counter), "found at her job site: " + entity.services().get(0).pos());
		helper.succeed();
	}

	// --- 34.4: luxuries from the village store ---

	/** The store is the kitchens' chests, then the Storehouses': a due household takes from the kitchen's chest first. */
	//$ gametest_batch BIG '"qaLuxuryKitchenFirst"'
	@GameTest(template = BIG, batch = "qaLuxuryKitchenFirst")
	public void theKitchensChestsAreTakenFromBeforeTheStorehouses(GameTestHelper helper) {
		Container storehouse = luxuries(helper);
		helper.setBlock(new BlockPos(4, 2, 10), Blocks.SMOKER);
		helper.setBlock(new BlockPos(5, 2, 10), Blocks.CHEST);
		Container kitchen = helper.getBlockEntity(new BlockPos(5, 2, 10));
		List<BlockPos> store = VillageNeeds.store(helper.getLevel(), helper.absolutePos(HALL));
		int k = store.indexOf(helper.absolutePos(new BlockPos(5, 2, 10)));
		int s = store.indexOf(helper.absolutePos(STORE_CHEST));
		helper.assertTrue(k >= 0 && s >= 0 && k < s, "the kitchen's chest comes before the storehouse's: " + store);
		kitchen.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 1));
		storehouse.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 1));
		Villager ann = villager(helper, 4, 4, "Ann");
		SocialClasses.seed(ann, SocialClasses.get(ours("artisan")));
		dawn(helper, 10, ann);
		helper.assertTrue(kitchen.countItem(Items.DISC_FRAGMENT_5) == 0 && storehouse.countItem(Items.DISC_FRAGMENT_5) == 1,
			"taken from the kitchen first: kitchen " + kitchen.countItem(Items.DISC_FRAGMENT_5) + ", storehouse " + storehouse.countItem(Items.DISC_FRAGMENT_5));
		helper.assertTrue(had(ann).equals(Map.of(TRINKET, 10L)), "had: " + had(ann));
		helper.succeed();
	}

	/** One trinket in the store and two single Artisans due: one has it, the other misses the day; never two taken or a copy made. */
	//$ gametest_batch BIG '"qaLuxuryOneForTwo"'
	@GameTest(template = BIG, batch = "qaLuxuryOneForTwo")
	public void oneLuxuryInStoreGoesToOneOfTwoHouseholds(GameTestHelper helper) {
		Container store = luxuries(helper);
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 1));
		Villager a = villager(helper, 4, 4, "Ada");
		Villager b = villager(helper, 8, 4, "Bo");
		SocialClasses.seed(a, SocialClasses.get(ours("artisan")));
		SocialClasses.seed(b, SocialClasses.get(ours("artisan")));
		dawn(helper, 10, a, b);
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 0, "the trinket is gone: " + store.countItem(Items.DISC_FRAGMENT_5));
		boolean aHad = had(a).containsKey(TRINKET);
		boolean bHad = had(b).containsKey(TRINKET);
		helper.assertTrue(aHad ^ bHad, "exactly one household had it: " + had(a) + " / " + had(b));
		Villager without = aHad ? b : a;
		helper.assertTrue(SocialClasses.progress(without).missed() == 1, "the other missed the day: " + SocialClasses.progress(without));
		helper.assertTrue(SocialClasses.progress(aHad ? a : b).missed() == 0, "the one who had it didn't miss: " + SocialClasses.progress(aHad ? a : b));
		helper.succeed();
	}

	/** Every 4 days (the fine trinket, a tag): had on day 20, nothing on days 21-23, one on day 24. */
	//$ gametest_batch BIG '"qaLuxuryEveryFour"'
	@GameTest(template = BIG, batch = "qaLuxuryEveryFour")
	public void aLuxuryEveryFourDaysIsDueOnTheFourthDay(GameTestHelper helper) {
		Container store = luxuries(helper);
		store.setItem(0, new ItemStack(Items.PHANTOM_MEMBRANE, 5));
		Villager bea = villager(helper, 4, 4, "Bea");
		SocialClasses.seed(bea, SocialClasses.get(ours("burgher")));
		ModAttachments.LUXURIES_HAD.set(bea, Map.of(FINE, 20L));
		for (long day = 21; day <= 23; day++) {
			dawn(helper, day, bea);
			helper.assertTrue(store.countItem(Items.PHANTOM_MEMBRANE) == 5, "taken on day " + day + " (had on day 20, every 4)");
			helper.assertTrue(SocialClasses.progress(bea).missed() == 0, "had " + (day - 20) + " days ago, the need holds: " + SocialClasses.progress(bea));
		}
		dawn(helper, 24, bea);
		helper.assertTrue(store.countItem(Items.PHANTOM_MEMBRANE) == 4, "due on day 24: " + store.countItem(Items.PHANTOM_MEMBRANE) + " left");
		helper.assertTrue(had(bea).get(FINE) == 24L, "had: " + had(bea));
		helper.succeed();
	}

	/** Its own class and the class above: a Burgher takes the fine trinket and leaves the Artisans' trinkets for them. */
	//$ gametest_batch BIG '"qaLuxuryNotBelow"'
	@GameTest(template = BIG, batch = "qaLuxuryNotBelow")
	public void aBurgherLeavesTheClassBelowsLuxuries(GameTestHelper helper) {
		Container store = luxuries(helper);
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 3));
		store.setItem(1, new ItemStack(Items.PHANTOM_MEMBRANE, 3));
		Villager bea = villager(helper, 4, 4, "Bea");
		SocialClasses.seed(bea, SocialClasses.get(ours("burgher")));
		dawn(helper, 10, bea);
		helper.assertTrue(store.countItem(Items.PHANTOM_MEMBRANE) == 2, "the Burgher's own: " + store.countItem(Items.PHANTOM_MEMBRANE) + " left");
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 3, "the Artisans' trinkets taken by a Burgher: " + store.countItem(Items.DISC_FRAGMENT_5) + " left");
		helper.assertTrue(had(bea).equals(Map.of(FINE, 10L)), "had: " + had(bea));
		helper.succeed();
	}

	/** Through the hall's own dawn round: a married Artisan couple and a single Artisan, two trinkets taken in all. */
	//$ gametest_batch BIG '"qaLuxuryHallRound"'
	@GameTest(template = BIG, batch = "qaLuxuryHallRound")
	public void theHallsDawnRoundHandsOutLuxuries(GameTestHelper helper) {
		Container store = luxuries(helper);
		SocialClasses.ENABLED = true;
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 5));
		Villager a = villager(helper, 4, 4, "Ada");
		Villager b = villager(helper, 6, 4, "Ben");
		Villager c = villager(helper, 8, 4, "Cy");
		marry(a, b);
		for (Villager v : List.of(a, b, c)) {
			SocialClasses.seed(v, SocialClasses.get(ours("artisan")));
		}
		long day = DAY + 7;
		for (int round = 0; round < 20; round++) {
			if (SocialClasses.round(level, helper.absolutePos(HALL), entity, day) == null) {
				break;
			}
		}
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 3, "a couple and a single take two: " + store.countItem(Items.DISC_FRAGMENT_5) + " left");
		for (Villager v : List.of(a, b, c)) {
			helper.assertTrue(had(v).equals(Map.of(TRINKET, day)), v.getName().getString() + " had: " + had(v));
		}
		helper.succeed();
	}

	// --- 34.7: what each class gives ---

	/**
	 * The hall's tax round from its census, at {@code treasuryPerWorker} 35: a Peasant worker (1), an Artisan carpenter with a
	 * want (1.65), a jobless Noble with two wants (4.8); a jobless Burgher and a Noble child pay nothing. Never below the
	 * old one-share-a-worker takings.
	 */
	//$ gametest_batch BIG '"qaPerkCensusTax"'
	@GameTest(template = BIG, batch = "qaPerkCensusTax")
	public void theTaxRoundChargesTheCensusByClass(GameTestHelper helper) {
		VillageHallBlockEntity entity = classes(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		Treasury.CENTS_PER_WORKER = 35;
		Villager peasant = classed(helper, 3, 3, "peasant", VillagerProfession.FARMER);
		site(helper, peasant, helper.absolutePos(new BlockPos(3, 2, 6)));
		Villager artisan = classed(helper, 6, 3, "artisan", ModVillagers.CARPENTER);
		site(helper, artisan, helper.absolutePos(new BlockPos(6, 2, 6)));
		wants(artisan, 1);
		Villager noble = classed(helper, 9, 3, "noble", VillagerProfession.NONE);
		wants(noble, 2);
		classed(helper, 12, 3, "burgher", VillagerProfession.NONE);
		Villager child = classed(helper, 15, 3, "noble", VillagerProfession.NONE);
		child.setAge(-24000);
		VillageHalls.Census census = VillageHalls.census(level, pos);
		helper.assertTrue(census.workers().size() == 2 && census.jobless().size() == 2, "census: " + census.workers().size() + " workers, " + census.jobless().size() + " jobless");
		ClassPerks.Tax tax = ClassPerks.tax(census.workers(), census.jobless());
		near(helper, tax.shares(), 1f + 1.5f * 1.1f + 4f * 1.2f, "shares");
		long today = Chronicle.day(level);
		entity.setLastTaxDay(today - 1);
		entity.setTreasury(0);
		Treasury.round(level, pos, entity, census.workers(), census.jobless());
		float w = entity.needs() == null ? 0.5f : Math.max(0, Math.min(1, entity.needs().wellbeing()));
		double rank = 1 + 0.25 * entity.rank().ordinal();
		long expected = Math.round((1 + 1.65 + 4.8) * 35 * (0.5 + w) * rank);
		helper.assertTrue(Math.abs(entity.treasury() - expected) <= 1, "takings " + entity.treasury() + " cents, expected " + expected + " (wellbeing " + w + ")");
		long old = Math.round(2 * 35 * (0.5 + w) * rank);
		helper.assertTrue(entity.treasury() >= old, "never below the old formula's " + old);
		helper.succeed();
	}

	/** The name tag's split by class, rendered from en_us.json: a heading, then one line a class with payers, its share's takings. */
	//$ gametest_batch BIG '"qaPerkTaxLines"'
	@GameTest(template = BIG, batch = "qaPerkTaxLines")
	public void theTaxSplitReadsEachClassesShare(GameTestHelper helper) {
		VillageHallBlockEntity entity = classes(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		List<Villager> workers = List.of(
			classed(helper, 3, 3, "peasant", VillagerProfession.FARMER),
			classed(helper, 6, 3, "peasant", VillagerProfession.FISHERMAN),
			classed(helper, 9, 3, "burgher", ModVillagers.SCHOLAR));
		List<Component> lines = Treasury.taxLines(level, pos, workers, List.of());
		List<String> text = lines.stream().map(Component::getString).toList();
		float w = entity.needs() == null ? 0.5f : entity.needs().wellbeing();
		String peasants = String.format(Locale.ROOT, "%.2f", Treasury.takings(2f, w, entity.rank()) / 100.0);
		String burghers = String.format(Locale.ROOT, "%.2f", Treasury.takings(2.5f, w, entity.rank()) / 100.0);
		helper.assertTrue(text.size() == 3, "a heading and two classes: " + text);
		helper.assertTrue(text.get(0).equals("Daily takings by class:"), "heading: " + text.get(0));
		helper.assertTrue(text.get(1).equals("Peasant: 2 pay " + peasants + " emeralds"), "peasants: " + text.get(1));
		helper.assertTrue(text.get(2).startsWith("Burgher: 1 ") && text.get(2).endsWith(" " + burghers + " emeralds"), "burghers: " + text.get(2));
		helper.assertTrue(text.stream().noneMatch(s -> s.contains("Artisan") || s.contains("Noble") || s.contains("%") || s.contains("aliveworkplace.")),
			"no class without payers, no raw keys: " + text);
		helper.succeed();
	}

	/** All ten crafter jobs the spec names work 10% faster as Artisans (and as Burghers), not as Peasants. */
	//$ gametest_batch BIG '"qaPerkCrafters"'
	@GameTest(template = BIG, batch = "qaPerkCrafters")
	public void everyCrafterJobWorksTenPercentFasterAsAnArtisan(GameTestHelper helper) {
		classes(helper);
		List<String> jobs = List.of("aliveworkplace:carpenter", "minecraft:mason", "aliveworkplace:tinkerer", "aliveworkplace:chef",
			"minecraft:leatherworker", "minecraft:toolsmith", "aliveworkplace:vintner", "aliveworkplace:tailor", "aliveworkplace:printer",
			"aliveworkplace:jeweller");
		int x = 2;
		for (String job : jobs) {
			VillagerProfession p = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(job));
			helper.assertTrue(p != VillagerProfession.NONE, job + " is a job");
			Villager artisan = classed(helper, x, 3, "artisan", p);
			Villager burgher = classed(helper, x, 6, "burgher", p);
			Villager peasant = classed(helper, x, 9, "peasant", p);
			x += 2;
			near(helper, ClassPerks.pace(artisan), 1f / 1.1f, "an Artisan " + job);
			near(helper, ClassPerks.pace(burgher), 1f / 1.1f, "a Burgher " + job);
			near(helper, ClassPerks.pace(peasant), 1f, "a Peasant " + job);
			helper.assertTrue(Pace.factor(artisan) <= Pace.factor(peasant) / 1.1f + 1e-4f, "the Artisan " + job + "'s pace " + Pace.factor(artisan)
				+ " against the Peasant's " + Pace.factor(peasant));
			helper.assertTrue(Pace.factor(artisan) >= Pace.cap() - 1e-4f, "within the cap");
		}
		helper.succeed();
	}

	/** A married Noble couple is one household: two couples and a single are 3 households (9%), one couple alone is 3%, not 6. */
	//$ gametest_batch BIG '"qaPerkNobleCouples"'
	@GameTest(template = BIG, batch = "qaPerkNobleCouples")
	public void aNobleCoupleLiftsWellbeingOnce(GameTestHelper helper) {
		classes(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		Villager a = classed(helper, 3, 3, "noble", VillagerProfession.NONE);
		Villager b = classed(helper, 5, 3, "noble", VillagerProfession.NONE);
		marry(a, b);
		near(helper, ClassPerks.wellbeing(ClassPerks.tally(level, pos, List.of(a, b))), 0.03f, "one Noble couple");
		Villager c = classed(helper, 7, 3, "noble", VillagerProfession.NONE);
		Villager d = classed(helper, 9, 3, "noble", VillagerProfession.NONE);
		marry(c, d);
		Villager e = classed(helper, 11, 3, "noble", VillagerProfession.NONE);
		ClassPerks.Sums sums = ClassPerks.tally(level, pos, List.of(a, b, c, d, e));
		helper.assertTrue(sums.atLeast(SocialClasses.get(ours("noble"))) == 3, "two couples and a single: " + sums);
		near(helper, ClassPerks.wellbeing(sums), 0.09f, "three Noble households");
		helper.succeed();
	}

	/** The Nobles' bonus reaches the village's own wellbeing count: the same villager as a Noble adds exactly 3%. */
	//$ gametest_batch BIG '"qaPerkWellbeingCount"'
	@GameTest(template = BIG, batch = "qaPerkWellbeingCount")
	public void aNobleHouseholdAddsThreePercentToTheVillagesWellbeing(GameTestHelper helper) {
		classes(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		Villager v = classed(helper, 4, 4, "peasant", VillagerProfession.NONE);
		float plain = VillageNeeds.count(level, pos).wellbeing();
		helper.assertTrue(plain <= 0.97f, "room for the bonus: " + plain);
		SocialClasses.seed(v, SocialClasses.get(ours("noble")));
		float noble = VillageNeeds.count(level, pos).wellbeing();
		near(helper, noble - plain, 0.03f, "a Noble household's lift (" + plain + " to " + noble + ")");
		helper.succeed();
	}

	/** The ball comes only with a Noble: once the village's only Noble falls to Burgher, every festival is plain again. */
	//$ gametest_batch BIG '"qaPerkBallEnds"'
	@GameTest(template = BIG, batch = "qaPerkBallEnds")
	public void theBallsStopWhenTheLastNobleFalls(GameTestHelper helper) {
		VillageHallBlockEntity entity = classes(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		Villager noble = classed(helper, 4, 4, "noble", VillagerProfession.NONE);
		List<Villager> people = new ArrayList<>(List.of(noble, classed(helper, 8, 4, "peasant", VillagerProfession.FARMER)));
		ClassPerks.tally(level, pos, people);
		int balls = 0;
		for (int i = 0; i < 4; i++) {
			Festivals.plan(level, pos, entity, today + 10 + i, 0);
			balls += NobleBalls.isBall(entity, today + 10 + i) ? 1 : 0;
		}
		helper.assertTrue(balls == 2, "with a Noble, 2 of 4 festivals are balls: " + balls);
		SocialClasses.seed(noble, SocialClasses.get(ours("burgher")));
		ClassPerks.tally(level, pos, people);
		for (int i = 0; i < 4; i++) {
			Festivals.plan(level, pos, entity, today + 20 + i, 0);
			helper.assertTrue(!NobleBalls.isBall(entity, today + 20 + i), "no Noble left, yet festival " + i + " is a ball");
		}
		helper.succeed();
	}
}
