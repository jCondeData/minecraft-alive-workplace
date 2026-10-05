package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.legend.BornGifts;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.legend.GiftedAuras;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.sift.SifterWork;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 29.7, Gifted villagers (2) and Legends born: Lucky's luck on the loot their work rolls (and the sifting tables'
 * quality weights), Hardy never ill with twice the health (kept over a save), a Beloved's neighbours by bed, a Born
 * Leader's own trade; the born rolls with fixed dice (a Legend, Gifted, neither, a taken slot falling back to Gifted);
 * an old {@code parents} record; and the inn's Gifted travellers at twice the price.
 */
public class GiftedBornGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation LUCKY = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "lucky");
	private static final ResourceLocation HARDY = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "hardy");
	private static final ResourceLocation BELOVED = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "beloved");
	private static final ResourceLocation BORN_LEADER = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "born_leader");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static boolean key(Component c, String key) {
		return c.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
	}

	private static Villager worker(GameTestHelper helper, BlockPos at, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}

	/** The gifts load: all eight, the four new ones with their effects. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eightGiftsLoad(GameTestHelper helper) {
		helper.assertTrue(Gifted.all().size() == 8, "gifts loaded: " + Gifted.all().keySet());
		helper.assertTrue(Gifted.get(LUCKY).has("loot_luck") && Gifted.get(HARDY).has("no_illness") && Gifted.get(HARDY).has("health")
			&& Gifted.get(BELOVED).has("mood") && Gifted.get(BORN_LEADER).has("pace"), "a new gift is missing an effect");
		helper.succeed();
	}

	/**
	 * Lucky: luck 3 on what their work rolls (the sift's own parameters say so), and with it the gravel table's rare finds
	 * come up more often and nothing less often, over the same 2,000 fixed rolls.
	 */
	//$ gametest_batch AREA '"giftedLucky"'
	@GameTest(template = AREA, batch = "giftedLucky")
	public void luckyAddsLuckToEveryLootRoll(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager lucky = worker(helper, new BlockPos(3, 2, 3), VillagerProfession.MASON);
		Villager plain = worker(helper, new BlockPos(6, 2, 3), VillagerProfession.MASON);
		Gifted.set(lucky, LUCKY);
		Gifted.set(plain, null);
		helper.assertTrue(Gifted.lootLuck(lucky) == 3 && Gifted.lootLuck(plain) == 0, "luck: " + Gifted.lootLuck(lucky) + " / " + Gifted.lootLuck(plain));
		BlockPos sieve = helper.absolutePos(new BlockPos(4, 2, 4));
		LootParams luckyParams = SifterWork.params(level, lucky, sieve);
		LootParams plainParams = SifterWork.params(level, plain, sieve);
		helper.assertTrue(luckyParams.getLuck() == 3f && plainParams.getLuck() == 0f, "the sift's luck: " + luckyParams.getLuck() + " / " + plainParams.getLuck());
		LootTable gravel = level.getServer().reloadableRegistries().getLootTable(SifterWork.SIFTABLE.get(Items.GRAVEL));
		int rareLucky = 0;
		int rarePlain = 0;
		int nothingLucky = 0;
		int nothingPlain = 0;
		List<net.minecraft.world.item.Item> rare = List.of(Items.EMERALD, Items.DIAMOND, Items.LAPIS_LAZULI, Items.AMETHYST_SHARD);
		for (long seed = 0; seed < 2000; seed++) {
			List<ItemStack> a = gravel.getRandomItems(luckyParams, seed);
			List<ItemStack> b = gravel.getRandomItems(plainParams, seed);
			rareLucky += (int) a.stream().filter(s -> rare.contains(s.getItem())).count();
			rarePlain += (int) b.stream().filter(s -> rare.contains(s.getItem())).count();
			nothingLucky += a.isEmpty() ? 1 : 0;
			nothingPlain += b.isEmpty() ? 1 : 0;
		}
		// Weights at luck 0: rare 7 of 100; at luck 3: 19 of 100. Expected 140 and 380 rare finds.
		helper.assertTrue(rareLucky > rarePlain * 2, "rare finds, Lucky " + rareLucky + " vs " + rarePlain);
		helper.assertTrue(nothingLucky < nothingPlain, "empty sifts, Lucky " + nothingLucky + " vs " + nothingPlain);
		helper.succeed();
	}

	/** Hardy: never falls ill (the daily chance is 0 and falling ill does nothing), and twice the health, kept over a save. */
	//$ gametest_batch AREA '"giftedHardy"'
	@GameTest(template = AREA, batch = "giftedHardy")
	public void hardyNeverFallsIllAndHasTwiceTheHealth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager hardy = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		float base = plain.getMaxHealth();
		Gifted.set(hardy, HARDY);
		Gifted.set(plain, null);
		helper.assertTrue(hardy.getMaxHealth() == base * 2 && hardy.getHealth() == base * 2, "a Hardy villager's health: "
			+ hardy.getHealth() + " of " + hardy.getMaxHealth() + ", a villager's " + base);
		helper.assertTrue(Sickness.dailyChance(level, hardy) == 0f && Sickness.dailyChance(level, plain) > 0f, "daily chance of illness");
		Sickness.fallIll(level, hardy);
		Sickness.fallIll(level, plain);
		helper.assertTrue(!Sickness.isIll(hardy), "a Hardy villager fell ill");
		helper.assertTrue(Sickness.isIll(plain), "an ordinary villager didn't fall ill");
		// Saved and loaded: the extra health is a saved modifier, so the health comes back whole.
		hardy.setHealth(30f);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(hardy.save(tag), "not saved");
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager v && v.getMaxHealth() == base * 2 && v.getHealth() == 30f && Gifted.noIllness(v),
			"after a reload: " + (loaded instanceof Villager v ? v.getHealth() + " of " + v.getMaxHealth() : loaded));
		// The gift taken away: back to a villager's health.
		Gifted.set(hardy, null);
		helper.assertTrue(hardy.getMaxHealth() == base && hardy.getHealth() <= base, "the gift gone, health " + hardy.getHealth() + " of " + hardy.getMaxHealth());
		helper.succeed();
	}

	/** Beloved: a neighbour whose bed is within 16 blocks of hers is 5 happier, "a beloved neighbour"; one 20 away isn't. */
	//$ gametest_batch HUGE '"giftedBeloved"'
	@GameTest(template = HUGE, batch = "giftedBeloved")
	public void belovedMakesNeighboursHappier(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.after(helper, () -> {
			GiftedAuras.forget();
			Moods.forget();
		});
		ServerLevel level = helper.getLevel();
		Villager beloved = worker(helper, new BlockPos(3, 2, 3), VillagerProfession.FARMER);
		Villager near = worker(helper, new BlockPos(5, 2, 3), VillagerProfession.FARMER);
		Villager far = worker(helper, new BlockPos(7, 2, 3), VillagerProfession.FARMER);
		// Beds (their HOME memories) apart from where they stand: hers at x 3, the neighbour's 12 away, the other's 20.
		beloved.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(3, 2, 26))));
		near.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(15, 2, 26))));
		far.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(23, 2, 26))));
		Gifted.set(near, null);
		Gifted.set(far, null);
		Moods.Mood before = Moods.work(level, near);
		Gifted.set(beloved, BELOVED);
		Moods.Mood after = Moods.work(level, near);
		helper.assertTrue(after.good().stream().anyMatch(c -> key(c, "mood.aliveworkplace.reason.beloved")), "no beloved neighbour: " + after.good());
		helper.assertTrue(after.score() - before.score() == 5, "the neighbour's mood went from " + before.score() + " to " + after.score());
		helper.assertTrue(Moods.work(level, far).good().stream().noneMatch(c -> key(c, "mood.aliveworkplace.reason.beloved")), "a bed 20 blocks off counted");
		helper.assertTrue(Moods.work(level, beloved).good().stream().noneMatch(c -> key(c, "mood.aliveworkplace.reason.beloved")), "she's her own beloved neighbour");
		Gifted.set(beloved, null);
		helper.assertTrue(Moods.work(level, near).good().stream().noneMatch(c -> key(c, "mood.aliveworkplace.reason.beloved")), "the gift gone, still beloved");
		helper.succeed();
	}

	/** Born Leader: farmers within 16 blocks of a farmer Born Leader work 10% faster, in the pace's sources; nobody else. */
	//$ gametest_batch HUGE '"giftedBornLeader"'
	@GameTest(template = HUGE, batch = "giftedBornLeader")
	public void bornLeaderSpeedsUpTheirOwnTrade(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.after(helper, GiftedAuras::forget);
		Villager leader = worker(helper, new BlockPos(3, 2, 3), VillagerProfession.FARMER);
		Villager near = worker(helper, new BlockPos(10, 2, 3), VillagerProfession.FARMER);
		Villager far = worker(helper, new BlockPos(24, 2, 3), VillagerProfession.FARMER);
		Villager mason = worker(helper, new BlockPos(6, 2, 3), VillagerProfession.MASON);
		for (Villager v : List.of(near, far, mason)) {
			Gifted.set(v, null);
		}
		helper.assertTrue(GiftedAuras.pace(near) == 1f, "faster before anyone leads");
		Gifted.set(leader, BORN_LEADER);
		helper.assertTrue(Math.abs(GiftedAuras.pace(near) - 1.1f) < 1e-5, "a farmer near a Born Leader: " + GiftedAuras.pace(near));
		helper.assertTrue(GiftedAuras.pace(far) == 1f, "a farmer 21 blocks off: " + GiftedAuras.pace(far));
		helper.assertTrue(GiftedAuras.pace(mason) == 1f, "a mason near a farmer Born Leader: " + GiftedAuras.pace(mason));
		helper.assertTrue(GiftedAuras.pace(leader) == 1f, "the Born Leader speeds herself up");
		Pace.Breakdown pace = Pace.of(near);
		helper.assertTrue(pace.faster().stream().anyMatch(p -> p.source() == Pace.BORN_LEADER), "not among the pace's sources: " + pace.faster());
		helper.assertTrue(Pace.of(far).faster().stream().noneMatch(p -> p.source() == Pace.BORN_LEADER), "the far farmer has it");
		helper.succeed();
	}

	/** A child grown up in the village round the hall, with parents on record as schooled Masters (or not). */
	private static Villager child(GameTestHelper helper, BlockPos at, String name, boolean masters) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		ModAttachments.PARENTS.set(v, new Families.Parents(Component.literal("Dara"), Component.literal("Tom"), "minecraft:farmer", "minecraft:fisherman",
			false, masters, masters));
		return v;
	}

	/**
	 * The born rolls with fixed dice (seeds whose first two throws are checked here): a Legend (seed 18, the Legend die
	 * hits), Gifted (seed 1, the Legend die misses and the gift die hits), neither (seed 2), a Legend whose slot is taken
	 * falling back to Gifted (seed 101, both hit), and parents who weren't both schooled Masters rolling nothing at all.
	 * The chronicle says what each became.
	 */
	//$ gametest_ticks_batch HUGE '100' '"bornRolls"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "bornRolls")
	public void bornRollsLegendGiftedNeitherAndTakenSlot(GameTestHelper helper) {
		Leftovers.clear(helper);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "born_farmer_legend");
		Legend legend = Legends.read(id, JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:farmer\", \"title\": \"legend.aliveworkplace_test.test_legend.title\","
			+ " \"lore\": \"l\", \"arrive\": [{\"way\": \"born\", \"trades\": [\"minecraft:farmer\"]}]}").getAsJsonObject());
		Map<ResourceLocation, Legend> before = new LinkedHashMap<>();
		Legends.all().forEach(l -> before.put(l.id(), l));
		Map<ResourceLocation, Legend> with = new LinkedHashMap<>(before);
		with.put(id, legend);
		Legends.setForTest(with);
		Leftovers.after(helper, () -> {
			LegendRecord record = LegendRecord.get(helper.getLevel());
			for (var e : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				record.forget(e.getUUID());
				e.discard();
			}
			Legends.setForTest(before);
			LegendPowers.forget();
		});
		// The dice the seeds throw (Legend die 1 in 20 first, then the gift die 1 in 4).
		int[][] dice = {{18, 0, -1}, {1, 1, 0}, {2, 1, 1}, {101, 0, 0}};
		for (int[] d : dice) {
			RandomSource r = RandomSource.create(d[0]);
			int legendDie = r.nextInt(BornGifts.LEGEND_ONE_IN);
			int giftDie = r.nextInt(BornGifts.GIFTED_ONE_IN);
			helper.assertTrue((legendDie == 0) == (d[1] == 0) && (d[2] < 0 || (giftDie == 0) == (d[2] == 0)), "seed " + d[0] + " throws " + legendDie + ", " + giftDie);
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);

			// Parents not both schooled Masters: nothing, whatever the dice.
			Villager ordinary = child(helper, new BlockPos(3, 2, 3), "Ada", false);
			Families.round(level, hall, ordinary, RandomSource.create(18));
			helper.assertTrue(!ModAttachments.LEGEND.has(ordinary) && !ModAttachments.GIFTED.has(ordinary), "a child of ordinary parents was rolled for");

			// A Legend: a Master of the Legend's trade, and the chronicle's line.
			Villager wren = child(helper, new BlockPos(6, 2, 3), "Wren", true);
			Families.round(level, hall, wren, RandomSource.create(18));
			helper.assertTrue(ModAttachments.LEGEND.has(wren) && ModAttachments.LEGEND.get(wren).id().equals(id) && "born".equals(ModAttachments.LEGEND.get(wren).way()),
				"Wren didn't grow up a born Legend: " + ModAttachments.LEGEND.get(wren));
			helper.assertTrue(wren.getVillagerData().getProfession() == VillagerProfession.FARMER, "the Legend's trade: " + wren.getVillagerData());
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.LEGEND && key(e.text(), "chronicle.aliveworkplace.grown_up_legend")
				&& e.text().getString().contains("Wren")), "the chronicle: " + entity.chronicle());
			helper.assertTrue(Families.parents(wren).grownUp(), "not marked grown up");

			// Gifted: the gift written down, and the chronicle's line.
			Villager ben = child(helper, new BlockPos(9, 2, 3), "Ben", true);
			Families.round(level, hall, ben, RandomSource.create(1));
			helper.assertTrue(!ModAttachments.LEGEND.has(ben) && Gifted.of(ben) != null, "Ben didn't grow up Gifted");
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> key(e.text(), "chronicle.aliveworkplace.grown_up_gifted") && e.text().getString().contains("Ben")),
				"no Gifted line in the chronicle: " + entity.chronicle());

			// Neither.
			Villager cal = child(helper, new BlockPos(12, 2, 3), "Cal", true);
			Families.round(level, hall, cal, RandomSource.create(2));
			helper.assertTrue(!ModAttachments.LEGEND.has(cal) && Gifted.of(cal) == null, "Cal turned out something");
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> key(e.text(), "chronicle.aliveworkplace.grown_up") && e.text().getString().contains("Cal")),
				"no grown-up line for Cal");

			// The slot taken (Wren holds it): the Legend die hits, but she falls back to Gifted.
			Villager dee = child(helper, new BlockPos(15, 2, 3), "Dee", true);
			helper.assertTrue(BornGifts.candidates(level, hall, dee, Families.parents(dee)).isEmpty(), "the Rare's slot is still open with Wren in it");
			Families.round(level, hall, dee, RandomSource.create(101));
			helper.assertTrue(!ModAttachments.LEGEND.has(dee) && Gifted.of(dee) != null, "Dee, with the slot taken, isn't Gifted");

			helper.succeed();
		});
	}

	/**
	 * A child grown up in one village's round settles there as a born Legend even when another village's hall is nearer
	 * where they stand: the Legend takes the slot the roll checked, so the next child of that village finds it taken.
	 */
	//$ gametest_ticks_batch HUGE '100' '"bornNearerHall"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "bornNearerHall")
	public void bornLegendSettlesInTheRoundsVillageNotTheNearestHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "born_nearer_hall_legend");
		Legend legend = Legends.read(id, JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:farmer\", \"title\": \"legend.aliveworkplace_test.test_legend.title\","
			+ " \"lore\": \"l\", \"arrive\": [{\"way\": \"born\", \"trades\": [\"minecraft:farmer\"]}]}").getAsJsonObject());
		Map<ResourceLocation, Legend> before = new LinkedHashMap<>();
		Legends.all().forEach(l -> before.put(l.id(), l));
		Map<ResourceLocation, Legend> with = new LinkedHashMap<>(before);
		with.put(id, legend);
		Legends.setForTest(with);
		Leftovers.after(helper, () -> {
			LegendRecord record = LegendRecord.get(helper.getLevel());
			for (var e : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				record.forget(e.getUUID());
				e.discard();
			}
			Legends.setForTest(before);
			LegendPowers.forget();
		});
		BlockPos nearer = new BlockPos(3, 2, 3);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(nearer, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos other = helper.absolutePos(nearer);
			Villager wren = child(helper, new BlockPos(5, 2, 4), "Wren", true);
			helper.assertTrue(VillageHalls.nearest(level, wren.blockPosition()).equals(Optional.of(other)), "setup: the other hall isn't the nearest");
			Families.round(level, hall, wren, RandomSource.create(18));
			helper.assertTrue(ModAttachments.LEGEND.has(wren) && ModAttachments.LEGEND.get(wren).hall().equals(Optional.of(hall)),
				"Wren didn't settle in the village whose round she grew up in: " + ModAttachments.LEGEND.get(wren));
			helper.assertTrue(LegendRecord.get(level).entry(wren.getUUID()).map(e -> e.in(level.dimension().location().toString(), hall)).orElse(false),
				"the record doesn't hold her in that village: " + LegendRecord.get(level).entry(wren.getUUID()));
			Villager dee = child(helper, new BlockPos(6, 2, 4), "Dee", true);
			helper.assertTrue(BornGifts.candidates(level, hall, dee, Families.parents(dee)).isEmpty(), "the Rare's slot is still open with Wren in it");
			helper.succeed();
		});
	}

	/**
	 * An old {@code parents} record (no schooled-Master fields) loads with both false; a new one keeps them; and a baby of
	 * two schooled Masters has them on record from the birth.
	 */
	//$ gametest_batch AREA '"bornParentsRecord"'
	@GameTest(template = AREA, batch = "bornParentsRecord")
	public void oldParentsRecordLoadsWithSchooledMastersFalse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Families.Parents now = new Families.Parents(Component.literal("Dara"), Component.literal("Tom"), "minecraft:farmer", "minecraft:mason", true, true, true);
		Tag saved = Families.Parents.CODEC.encodeStart(ops, now).getOrThrow();
		helper.assertTrue(saved instanceof CompoundTag c && c.getBoolean("mother_schooled_master") && c.getBoolean("father_schooled_master"), "saved: " + saved);
		CompoundTag old = ((CompoundTag) saved).copy();
		old.remove("mother_schooled_master");
		old.remove("father_schooled_master");
		Families.Parents loaded = Families.Parents.CODEC.parse(ops, old).getOrThrow();
		helper.assertTrue(!loaded.motherSchooledMaster() && !loaded.fatherSchooledMaster() && loaded.grownUp() && loaded.motherJob().equals("minecraft:farmer"),
			"an old record loaded as " + loaded);
		helper.assertTrue(Families.Parents.CODEC.parse(ops, saved).getOrThrow().equals(now), "a new record didn't load the same");

		Villager mother = worker(helper, new BlockPos(3, 2, 3), VillagerProfession.FARMER);
		Villager father = worker(helper, new BlockPos(5, 2, 3), VillagerProfession.FISHERMAN);
		mother.setVillagerData(mother.getVillagerData().setLevel(5));
		father.setVillagerData(father.getVillagerData().setLevel(5));
		ModAttachments.SCHOOLED.set(mother, true);
		Villager baby = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 5));
		baby.setAge(-24000);
		Families.born(baby, mother, father);
		Families.Parents p = Families.parents(baby);
		helper.assertTrue(p.motherSchooledMaster() && !p.fatherSchooledMaster(), "a schooled Master and an unschooled one: " + p);
		helper.succeed();
	}

	/**
	 * The inn: travellers are Gifted about 1 time in 10 (the dice fixed), none with {@code giftedChance} 0; a Gifted one
	 * costs twice as much, and the hire screen says so and shows the gift.
	 */
	//$ gametest_batch AREA '"giftedTraveller"'
	@GameTest(template = AREA, batch = "giftedTraveller")
	public void giftedTravellerCostsTwiceAsMuch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager guest = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		guest.setNoAi(true);
		guest.setVillagerData(guest.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Traveller t = new Traveller(level.getGameTime(), 2);
		ModAttachments.TRAVELLER.set(guest, t);
		Gifted.set(guest, null);
		int plain = Innkeepers.emeralds(guest, t);
		Gifted.set(guest, LUCKY);
		int gifted = Innkeepers.emeralds(guest, t);
		helper.assertTrue(plain > 0 && gifted == plain * 2, "a Gifted traveller costs " + gifted + ", an ordinary one " + plain);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu menu = Innkeepers.hireMenuForTest(player, guest);
		ItemStack hire = menu.icon(Innkeepers.HIRE_SLOT);
		helper.assertTrue(hire.getCount() == gifted, "the hire button shows " + hire.getCount() + " emeralds");
		var hireLore = hire.get(DataComponents.LORE);
		helper.assertTrue(hireLore != null && hireLore.lines().stream().anyMatch(l -> key(l, "screen.aliveworkplace.inn.gifted_price")), "no twice-the-price line");
		var infoLore = menu.icon(Innkeepers.INFO_SLOT).get(DataComponents.LORE);
		helper.assertTrue(infoLore != null && infoLore.lines().stream().anyMatch(l -> key(l, "screen.aliveworkplace.hall.gifted")), "the gift isn't on the hire screen");

		// The dice over 2,000 fixed throws: about 1 in 10 (expected 200, spread about 13).
		int count = 0;
		for (int seed = 0; seed < 2000; seed++) {
			Innkeepers.giftTraveller(guest, RandomSource.create(seed));
			count += Gifted.of(guest) != null ? 1 : 0;
		}
		helper.assertTrue(count >= 150 && count <= 250, "Gifted travellers: " + count + " of 2000");
		int chance = Gifted.CHANCE;
		try {
			Gifted.CHANCE = 0;
			Gifted.set(guest, LUCKY);
			helper.assertTrue(Innkeepers.emeralds(guest, t) == plain, "giftedChance 0, still twice the price");
		} finally {
			Gifted.CHANCE = chance;
		}
		helper.succeed();
	}
}
