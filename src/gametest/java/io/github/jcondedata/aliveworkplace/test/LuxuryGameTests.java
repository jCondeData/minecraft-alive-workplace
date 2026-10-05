package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Luxuries from the village store (ROADMAP 34.4, docs/design/M34.md): the GameTest data pack's two luxuries (Test Trinket,
 * a disc fragment every 2 days; Test Fine Trinket, the {@code #aliveworkplace_test:fine_trinkets} tag every 4) on a test
 * ladder where the Artisan needs the first and the Burgher the second. A due household takes exactly one, a couple one
 * between them, nothing before it's due, an Artisan also the Burgher's, an empty store is a miss, a porter carries a
 * maker's luxuries to the storehouse, and {@code luxuries_had} survives a reload. Each test has a batch of its own and
 * puts the real ladder back.
 */
public class LuxuryGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos STOREHOUSE = new BlockPos(19, 2, 19);
	private static final BlockPos STORE_CHEST = new BlockPos(19, 2, 17);
	private static final ResourceLocation TRINKET = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_trinket");
	private static final ResourceLocation FINE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_fine_trinket");

	/** A hall (reach 16), a Storehouse with its chest (the village store), and the test ladder; all put back after. */
	private static Container village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
		});
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(AliveWorkplace.id("peasant"), JsonParser.parseString("{\"tier\": 0}"));
		files.put(AliveWorkplace.id("artisan"), JsonParser.parseString("{\"tier\": 1, \"needs\": [{\"type\": \"luxury\", \"id\": \"" + TRINKET + "\"}]}"));
		files.put(AliveWorkplace.id("burgher"), JsonParser.parseString("{\"tier\": 2, \"needs\": [{\"type\": \"luxury\", \"id\": \"" + FINE + "\"}]}"));
		SocialClasses.load(files);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.assertTrue(VillageNeeds.store(level, helper.absolutePos(HALL)).contains(helper.absolutePos(STORE_CHEST)), "the storehouse chest isn't the store");
		return helper.getBlockEntity(STORE_CHEST);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, String cls) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
		return v;
	}

	private static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	/** The hall's dawn check on {@code day} for these villagers (the same slices the hall runs). */
	private static void dawn(GameTestHelper helper, long day, Villager... who) {
		SocialClasses.check(helper.getLevel(), helper.absolutePos(HALL), List.of(who), day, SocialClasses.SLICE);
	}

	private static Map<ResourceLocation, Long> had(Villager v) {
		return ModAttachments.LUXURIES_HAD.getOrElse(v, Map.of());
	}

	/** The data pack's files load (an item and a tag), and a due household takes exactly one, and the need holds. */
	//$ gametest_batch AREA '"luxuryDueOne"'
	@GameTest(template = AREA, batch = "luxuryDueOne")
	public void aDueHouseholdTakesExactlyOne(GameTestHelper helper) {
		Container store = village(helper);
		Luxuries.Luxury trinket = Luxuries.get(TRINKET);
		Luxuries.Luxury fine = Luxuries.get(FINE);
		helper.assertTrue(trinket != null && trinket.everyDays() == 2 && trinket.matches(new ItemStack(Items.DISC_FRAGMENT_5)),
			"the test trinket file: " + trinket);
		helper.assertTrue(fine != null && fine.everyDays() == 4 && fine.matches(new ItemStack(Items.PHANTOM_MEMBRANE)) && !fine.matches(new ItemStack(Items.DISC_FRAGMENT_5)),
			"the fine trinket file (a tag): " + fine);
		helper.assertTrue(ClassNeeds.luxuryEvery.everyDays(TRINKET) == 2 && ClassNeeds.luxuryEvery.everyDays(AliveWorkplace.id("no_such")) == 0,
			"the luxury need's hook doesn't read the files");
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 5));
		Villager ann = villager(helper, new BlockPos(4, 2, 4), "Ann", "artisan");
		dawn(helper, 10, ann);
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 4, "the store has " + store.countItem(Items.DISC_FRAGMENT_5) + " trinkets, expected 4");
		helper.assertTrue(had(ann).equals(Map.of(TRINKET, 10L)), "luxuries had: " + had(ann));
		helper.assertTrue(SocialClasses.progress(ann).missed() == 0 && SocialClasses.of(ann).id().getPath().equals("artisan"),
			"taken at dawn, the need should hold that day: " + SocialClasses.progress(ann));
		helper.succeed();
	}

	/** A married couple takes one between them, and both remember the day. */
	//$ gametest_batch AREA '"luxuryCouple"'
	@GameTest(template = AREA, batch = "luxuryCouple")
	public void aCoupleTakesOneBetweenThem(GameTestHelper helper) {
		Container store = village(helper);
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 5));
		Villager a = villager(helper, new BlockPos(4, 2, 4), "Ada", "artisan");
		Villager b = villager(helper, new BlockPos(6, 2, 4), "Ben", "artisan");
		marry(a, b);
		dawn(helper, 10, a, b);
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 4, "the couple took " + (5 - store.countItem(Items.DISC_FRAGMENT_5)) + ", expected 1");
		helper.assertTrue(had(a).equals(Map.of(TRINKET, 10L)) && had(b).equals(Map.of(TRINKET, 10L)), "had: " + had(a) + " and " + had(b));
		helper.assertTrue(SocialClasses.progress(a).missed() == 0 && SocialClasses.progress(b).missed() == 0, "the need should hold for both");
		helper.succeed();
	}

	/** Nothing is taken before it's due: had on day 9 (every 2), nothing on day 10, one on day 11. */
	//$ gametest_batch AREA '"luxuryNotDue"'
	@GameTest(template = AREA, batch = "luxuryNotDue")
	public void nothingIsTakenBeforeItsDue(GameTestHelper helper) {
		Container store = village(helper);
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 5));
		Villager ann = villager(helper, new BlockPos(4, 2, 4), "Ann", "artisan");
		ModAttachments.LUXURIES_HAD.set(ann, Map.of(TRINKET, 9L));
		dawn(helper, 10, ann);
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 5, "taken on day 10, had on day 9: " + store.countItem(Items.DISC_FRAGMENT_5) + " left");
		helper.assertTrue(had(ann).equals(Map.of(TRINKET, 9L)), "had: " + had(ann));
		helper.assertTrue(SocialClasses.progress(ann).missed() == 0, "had yesterday, the need still holds");
		dawn(helper, 11, ann);
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 4, "due on day 11: " + store.countItem(Items.DISC_FRAGMENT_5) + " left");
		helper.assertTrue(had(ann).equals(Map.of(TRINKET, 11L)), "had: " + had(ann));
		helper.succeed();
	}

	/** An Artisan household takes its own luxury and the Burgher's the store has; a Peasant takes the Artisan's. */
	//$ gametest_batch AREA '"luxuryClassAbove"'
	@GameTest(template = AREA, batch = "luxuryClassAbove")
	public void anArtisanAlsoTakesTheBurghersLuxuries(GameTestHelper helper) {
		Container store = village(helper);
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 3));
		store.setItem(1, new ItemStack(Items.PHANTOM_MEMBRANE, 3));
		Villager ann = villager(helper, new BlockPos(4, 2, 4), "Ann", "artisan");
		dawn(helper, 10, ann);
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 2 && store.countItem(Items.PHANTOM_MEMBRANE) == 2,
			"the store has " + store.countItem(Items.DISC_FRAGMENT_5) + " trinkets and " + store.countItem(Items.PHANTOM_MEMBRANE) + " fine ones, expected 2 and 2");
		helper.assertTrue(had(ann).equals(Map.of(TRINKET, 10L, FINE, 10L)), "had: " + had(ann));
		helper.assertTrue(SocialClasses.progress(ann).met() == 1, "with the Burgher's luxury the next class's needs hold: " + SocialClasses.progress(ann));
		Villager pat = villager(helper, new BlockPos(8, 2, 4), "Pat", "peasant");
		dawn(helper, 10, pat);
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 1 && store.countItem(Items.PHANTOM_MEMBRANE) == 2,
			"a Peasant takes the Artisan's luxury and not the Burgher's: " + store.countItem(Items.DISC_FRAGMENT_5) + " and " + store.countItem(Items.PHANTOM_MEMBRANE));
		helper.assertTrue(had(pat).equals(Map.of(TRINKET, 10L)), "the peasant had: " + had(pat));
		helper.succeed();
	}

	/** An empty store leaves the need unmet: a missed dawn, nothing noted. */
	//$ gametest_batch AREA '"luxuryEmpty"'
	@GameTest(template = AREA, batch = "luxuryEmpty")
	public void anEmptyStoreCountsAsMissed(GameTestHelper helper) {
		village(helper);
		Villager ann = villager(helper, new BlockPos(4, 2, 4), "Ann", "artisan");
		dawn(helper, 10, ann);
		helper.assertTrue(!ModAttachments.LUXURIES_HAD.has(ann), "had from an empty store: " + had(ann));
		helper.assertTrue(SocialClasses.progress(ann).missed() == 1, "the empty store should count as a missed dawn: " + SocialClasses.progress(ann));
		helper.succeed();
	}

	/** A save and reload keeps luxuries_had (a couple's two copies). */
	//$ gametest_batch AREA '"luxuryReload"'
	@GameTest(template = AREA, batch = "luxuryReload")
	public void aReloadKeepsLuxuriesHad(GameTestHelper helper) {
		Container store = village(helper);
		store.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 5));
		store.setItem(1, new ItemStack(Items.PHANTOM_MEMBRANE, 5));
		Villager a = villager(helper, new BlockPos(4, 2, 4), "Ada", "artisan");
		Villager b = villager(helper, new BlockPos(6, 2, 4), "Ben", "artisan");
		marry(a, b);
		dawn(helper, 12, a, b);
		for (Villager v : List.of(a, b)) {
			CompoundTag tag = v.saveWithoutId(new CompoundTag());
			Villager copy = EntityType.VILLAGER.create(helper.getLevel());
			copy.load(tag);
			helper.assertTrue(had(copy).equals(Map.of(TRINKET, 12L, FINE, 12L)), "luxuries had after a reload: " + had(copy));
			copy.discard();
		}
		// Reloaded on day 13, the trinket (every 2) isn't due yet: nothing more is taken.
		dawn(helper, 13, a, b);
		helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 4 && store.countItem(Items.PHANTOM_MEMBRANE) == 4,
			"the store after day 13: " + store.countItem(Items.DISC_FRAGMENT_5) + " and " + store.countItem(Items.PHANTOM_MEMBRANE));
		helper.succeed();
	}

	/**
	 * A luxury maker keeps the makings; the porter carries the luxuries to the storehouse. The makers' own jobs come with
	 * 34.9-34.12, so a Florist stands in: she keeps all her bone meal, but a luxury in her chest goes like anyone's.
	 */
	//$ gametest_ticks_batch AREA '1600' '"luxuryPorter"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "luxuryPorter")
	public void aPorterCarriesAMakersGoodsToTheStorehouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.assertTrue(Porters.keeps(ModVillagers.FLORIST, new ItemStack(Items.DISC_FRAGMENT_5), false) == 0,
			"a maker should keep none of a luxury");
		helper.assertTrue(Porters.keeps(ModVillagers.FLORIST, new ItemStack(Items.BONE_MEAL), false) == Porters.ALL,
			"a maker's makings stay");
		BlockPos bench = new BlockPos(3, 2, 3);
		BlockPos chestPos = new BlockPos(3, 2, 5);
		helper.setBlock(bench, ModBlocks.FLOWER_STAND);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container makers = helper.getBlockEntity(chestPos);
		makers.setItem(0, new ItemStack(Items.DISC_FRAGMENT_5, 20));
		makers.setItem(1, new ItemStack(Items.BONE_MEAL, 20));
		Villager maker = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		maker.setNoAi(true); // she stays at her stand (her own work would use the bone meal)
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(helper.getLevel(), maker, helper.absolutePos(bench), ModVillagers.FLOWER_STAND_POI,
			ModVillagers.FLORIST);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Porters.employ(helper.getLevel(), porter, helper.absolutePos(STOREHOUSE));
		helper.succeedWhen(() -> {
			Container store = helper.getBlockEntity(STORE_CHEST);
			helper.assertTrue(store.countItem(Items.DISC_FRAGMENT_5) == 20, "luxuries in the store: " + store.countItem(Items.DISC_FRAGMENT_5));
			helper.assertTrue(makers.countItem(Items.DISC_FRAGMENT_5) == 0, "the maker kept " + makers.countItem(Items.DISC_FRAGMENT_5) + " luxuries");
			helper.assertTrue(makers.countItem(Items.BONE_MEAL) == 20 && store.countItem(Items.BONE_MEAL) == 0, "the porter took the makings");
			helper.assertTrue(porter.isAlive() && maker.isAlive(), "the porter or the maker is gone");
		});
	}
}
