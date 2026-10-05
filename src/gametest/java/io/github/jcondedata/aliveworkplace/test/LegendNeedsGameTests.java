package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendText;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Picket;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * 29.5, a Legend's needs and strikes, through the hall's round and the daily check: a home of their own (a tier III home
 * shared with a stranger fails, with a spouse passes, a tier II one fails), the luxury taken once a week from a chest in
 * their home and else from the store (+10 mood while it lasts), a happy village with moods on and off, a strike after
 * two days unmet (powers gone, work stopped, the picket by the hall, the red line, the chronicle) and back at work the
 * day every need is met, the 3-day grace, 20 days on strike and still in the village, {@code legendNeeds} off, and a
 * Legend's needs saved and loaded. Days are passed to {@link LegendNeeds#check} as {@link Chronicle#day}s.
 */
public class LegendNeedsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final String NS = "aliveworkplace_test";
	/** How far above the test area the recorded homes stand (blocks there are never cleared: the test clears its own). */
	private static final int SKY = 100;

	private static ResourceLocation id(String name) {
		return ResourceLocation.fromNamespaceAndPath(NS, name);
	}

	/** A Mason Legend who makes every Farmer in the village twice as fast, liking {@code luxury} (none: no luxury). */
	private static Legend legend(String luxury) {
		return Legends.read(id("needs_mason"), JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"needs_mason\","
			+ " \"lore\": \"l\"" + (luxury == null ? "" : ", \"needs\": {\"luxury\": \"" + luxury + "\"}")
			+ ", \"powers\": [{\"type\": \"pace\", \"trades\": [\"minecraft:farmer\"], \"factor\": 2.0}]}").getAsJsonObject());
	}

	/**
	 * The test's set: a Village Hall, and when it ends the Legends it made (with their record entries) and the homes it
	 * recorded go. Tests in other batches run beside these, so every switch and the roster are changed only inside
	 * {@link #staged}, within one tick.
	 */
	private static void setUp(GameTestHelper helper, List<BuildSiteManager.Finished> homes, List<BlockPos> skyBlocks) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			BuildSiteManager sites = BuildSiteManager.get(level);
			homes.forEach(f -> sites.forgetFinished(f.placement()));
			skyBlocks.forEach(p -> level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState()));
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				record.forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
	}

	/**
	 * Runs {@code body} with needs on and only {@code legend} loaded, then puts the switches and the real roster back
	 * before the tick ends, so the tests running beside this one never see them.
	 */
	private static void staged(GameTestHelper helper, Legend legend, Runnable body) {
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			LegendNeeds.ENABLED = true;
			Legends.setForTest(Map.of(legend.id(), legend));
			body.run();
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Moods.forget();
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** {@code legend} made out of a new villager, settled in the test's village on day {@code settled}. */
	private static Villager legendary(GameTestHelper helper, BlockPos at, Legend legend, long settled) {
		Villager v = villager(helper, at, "Ada", VillagerProfession.MASON);
		Legends.make(helper.getLevel(), v, legend, "test");
		LegendData data = ModAttachments.LEGEND.get(v);
		ModAttachments.LEGEND.set(v, new LegendData(data.id(), data.name(), false, Optional.of(helper.absolutePos(HALL)), settled - 1, -1, Map.of(), -1, -1,
			"test"));
		return v;
	}

	/** A finished building of {@code size} named {@code path} (its tier goes by the name) high above the test area. */
	private static BlueprintData.Placement home(GameTestHelper helper, String path, BlockPos origin, Vec3i size, List<BuildSiteManager.Finished> homes) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = id(path);
		// a template of this size with nothing in it, taken from the empty air further up
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, origin.above(40), size, false, Blocks.AIR);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		homes.add(new BuildSiteManager.Finished(id, placement, UUID.randomUUID()));
		return placement;
	}

	private static BlockPos sky(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(2, 2, 2)).above(SKY);
	}

	private static void sleepsAt(ServerLevel level, Villager villager, BlockPos bed) {
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
	}

	private static void marry(Villager a, Villager b, boolean married) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getName(), 1, married));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getName(), 1, married));
	}

	/** Sets how the village is doing (the hall's wellbeing, with moods off). */
	private static void wellbeing(GameTestHelper helper, float wellbeing) {
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, wellbeing));
	}

	private static List<String> chronicle(GameTestHelper helper) {
		return ((VillageHallBlockEntity) helper.getBlockEntity(HALL)).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND)
			.map(e -> e.text().getString()).toList();
	}

	/** A home of their own: tier III and theirs passes; shared with a stranger or a sweetheart fails, with a spouse passes; tier II fails. */
	//$ gametest_ticks_batch AREA '100' '"legendNeedsHome"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendNeedsHome")
	public void aHomeOfTheirOwn(GameTestHelper helper) {
		List<BuildSiteManager.Finished> homes = new ArrayList<>();
		setUp(helper, homes, List.of());
		helper.runAfterDelay(2, () -> staged(helper, legend(null), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = legendary(helper, new BlockPos(10, 2, 10), Legends.get(id("needs_mason")).orElseThrow(), 1);
			Villager bo = villager(helper, new BlockPos(11, 2, 10), "Bo", VillagerProfession.FARMER);
			BlockPos grand = sky(helper);
			home(helper, "needs_manor_3", grand, new Vec3i(8, 5, 8), homes);
			BlockPos fine = sky(helper).offset(12, 0, 0);
			home(helper, "needs_cottage_2", fine, new Vec3i(6, 5, 6), homes);

			helper.assertTrue(!LegendNeeds.home(level, hall, ada), "a Legend with no bed has a home");
			sleepsAt(level, ada, grand.offset(1, 1, 1));
			helper.assertTrue(LegendNeeds.home(level, hall, ada), "a tier III home of her own isn't a home of her own");
			sleepsAt(level, bo, grand.offset(5, 1, 5));
			helper.assertTrue(!LegendNeeds.home(level, hall, ada), "a tier III home shared with a stranger counts");
			marry(ada, bo, false);
			helper.assertTrue(!LegendNeeds.home(level, hall, ada), "a tier III home shared with a sweetheart (not married) counts");
			marry(ada, bo, true);
			helper.assertTrue(LegendNeeds.home(level, hall, ada), "a tier III home shared with her spouse doesn't count");
			bo.getBrain().eraseMemory(MemoryModuleType.HOME);
			sleepsAt(level, ada, fine.offset(1, 1, 1));
			helper.assertTrue(!LegendNeeds.home(level, hall, ada), "a tier II home counts");
			// the round writes it: a day unmet is a cross on the hall
			LegendData data = LegendNeeds.check(level, hall, ada, 5);
			helper.assertTrue(data != null && data.unmet().getOrDefault(LegendText.HOME, 0) == 1 && !LegendText.met(data, LegendText.HOME),
				"the tier II home isn't a day unmet: " + data);
			helper.succeed();
		}));
	}

	/** The luxury: once a week, from a chest in their home, else the store; none anywhere is a need unmet; +10 mood while it lasts. */
	//$ gametest_ticks_batch AREA '100' '"legendNeedsLuxury"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendNeedsLuxury")
	public void theLuxuryOnceAWeek(GameTestHelper helper) {
		List<BuildSiteManager.Finished> homes = new ArrayList<>();
		List<BlockPos> skyBlocks = new ArrayList<>();
		setUp(helper, homes, skyBlocks);
		helper.runAfterDelay(2, () -> staged(helper, legend("wine"), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			// the store (a chest by a smoker) is there for this tick only: a store within 64 blocks of another test's hall is theirs too
			BlockPos smoker = helper.absolutePos(new BlockPos(16, 2, 16));
			BlockPos storePos = helper.absolutePos(new BlockPos(17, 2, 16));
			level.setBlockAndUpdate(smoker, Blocks.SMOKER.defaultBlockState());
			level.setBlockAndUpdate(storePos, Blocks.CHEST.defaultBlockState());
			try {
				luxury(helper, level, hall, homes, skyBlocks, storePos);
			} finally {
				((Container) level.getBlockEntity(storePos)).clearContent();
				level.setBlockAndUpdate(storePos, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(smoker, Blocks.AIR.defaultBlockState());
				for (BlockPos p : skyBlocks) {
					if (level.getBlockEntity(p) instanceof Container c) {
						c.clearContent();
					}
					level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				}
			}
			helper.succeed();
		}));
	}

	private static void luxury(GameTestHelper helper, ServerLevel level, BlockPos hall, List<BuildSiteManager.Finished> homes, List<BlockPos> skyBlocks,
							   BlockPos storePos) {
		{
			Villager ada = legendary(helper, new BlockPos(10, 2, 10), Legends.get(id("needs_mason")).orElseThrow(), 1);
			BlockPos origin = sky(helper);
			home(helper, "needs_manor_3", origin, new Vec3i(8, 5, 8), homes);
			sleepsAt(level, ada, origin.offset(1, 1, 1));
			BlockPos homeChestPos = origin.offset(3, 1, 3);
			level.setBlockAndUpdate(homeChestPos, Blocks.CHEST.defaultBlockState());
			skyBlocks.add(homeChestPos);
			Container homeChest = (Container) level.getBlockEntity(homeChestPos);
			homeChest.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 2));
			homeChest.setItem(1, new ItemStack(Items.EMERALD, 5)); // a jewel: not what she likes
			Container store = (Container) level.getBlockEntity(storePos);
			store.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 1));
			helper.assertTrue(VillageNeeds.store(level, hall).contains(storePos), "the chest by the smoker isn't the store");

			LegendData data = LegendNeeds.check(level, hall, ada, 10);
			helper.assertTrue(data.lastLuxury() == 10 && homeChest.countItem(Items.HONEY_BOTTLE) == 1 && store.countItem(Items.HONEY_BOTTLE) == 1,
				"day 10: not one bottle from her home chest: " + data + ", home " + homeChest.countItem(Items.HONEY_BOTTLE));
			helper.assertTrue(homeChest.countItem(Items.EMERALD) == 5, "she took emeralds, liking wine");
			for (long day = 11; day <= 16; day++) {
				data = LegendNeeds.check(level, hall, ada, day);
				helper.assertTrue(LegendText.met(data, LegendText.LUXURY), "day " + day + ": her luxury isn't met in the week it lasts");
			}
			helper.assertTrue(homeChest.countItem(Items.HONEY_BOTTLE) == 1, "she took another within the week");
			data = LegendNeeds.check(level, hall, ada, 17);
			helper.assertTrue(data.lastLuxury() == 17 && homeChest.countItem(Items.HONEY_BOTTLE) == 0 && store.countItem(Items.HONEY_BOTTLE) == 1,
				"day 17: not the second bottle from home: " + data);
			data = LegendNeeds.check(level, hall, ada, 24);
			helper.assertTrue(data.lastLuxury() == 24 && store.countItem(Items.HONEY_BOTTLE) == 0, "day 24: none at home, and not one from the store: " + data);
			data = LegendNeeds.check(level, hall, ada, 31);
			helper.assertTrue(data.lastLuxury() == 24 && data.unmet().getOrDefault(LegendText.LUXURY, 0) == 1, "day 31: no wine anywhere and the need met: " + data);

			// +10 mood while it lasts, and only then
			long today = Chronicle.day(level);
			ModAttachments.LEGEND.set(ada, ModAttachments.LEGEND.get(ada).checkedOn(today, Map.of(), -1, -1));
			int without = Moods.work(level, ada).score();
			ModAttachments.LEGEND.set(ada, ModAttachments.LEGEND.get(ada).checkedOn(today, Map.of(), -1, today));
			Moods.Mood with = Moods.work(level, ada);
			helper.assertTrue(with.score() == Math.min(100, without + LegendNeeds.LUXURY_MOOD), "luxury mood " + without + " -> " + with.score());
			helper.assertTrue(with.good().stream().anyMatch(c -> c.getString().equals("enjoying their wine")), "no \"enjoying their wine\": " + with.good());
			ModAttachments.LEGEND.set(ada, ModAttachments.LEGEND.get(ada).checkedOn(today, Map.of(), -1, today - LegendNeeds.LUXURY_DAYS));
			helper.assertTrue(Moods.work(level, ada).score() == without, "the luxury's mood lasts past its week");
		}
	}

	/** A happy village: the grown-ups' average mood 60 or more with moods on; wellbeing 60% with moods off. */
	//$ gametest_ticks_batch AREA '100' '"legendNeedsHappy"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendNeedsHappy")
	public void aHappyVillageWithMoodsOnAndOff(GameTestHelper helper) {
		setUp(helper, new ArrayList<>(), List.of());
		helper.runAfterDelay(2, () -> staged(helper, legend(null), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			List<Villager> folk = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				Villager v = villager(helper, new BlockPos(8 + i, 2, 8), "V" + i, VillagerProfession.FARMER);
				sleepsAt(level, v, helper.absolutePos(new BlockPos(8 + i, 2, 14)));
				folk.add(v);
			}
			Moods.ENABLED = true;
			Moods.forget();
			int content = folk.stream().mapToInt(v -> Moods.of(v).score()).sum() / folk.size();
			helper.assertTrue(content >= LegendNeeds.HAPPY_MOOD, "the staged village isn't happy: " + content);
			wellbeing(helper, 0.1f); // moods on: wellbeing doesn't count
			helper.assertTrue(LegendNeeds.happy(level, hall), "average mood " + content + " isn't a happy village");
			for (Villager v : folk) {
				v.getBrain().eraseMemory(MemoryModuleType.HOME);
				v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
			}
			Moods.forget();
			int sad = folk.stream().mapToInt(v -> Moods.of(v).score()).sum() / folk.size();
			wellbeing(helper, 1f);
			helper.assertTrue(sad < LegendNeeds.HAPPY_MOOD && !LegendNeeds.happy(level, hall), "average mood " + sad + " is a happy village");

			Moods.ENABLED = false;
			wellbeing(helper, 0.6f);
			helper.assertTrue(LegendNeeds.happy(level, hall), "wellbeing 60% with moods off isn't a happy village");
			wellbeing(helper, 0.55f);
			helper.assertTrue(!LegendNeeds.happy(level, hall), "wellbeing 55% with moods off is a happy village");
			helper.succeed();
		}));
	}

	/**
	 * The grace, then a strike after two days unmet: her pace power gone, her trade's work stopped (her WORK package's
	 * picket first, everything else held), the red line and the chronicle; the needs met again later the same day: back
	 * at work that day.
	 */
	//$ gametest_ticks_batch AREA '100' '"legendNeedsStrike"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendNeedsStrike")
	public void aStrikeAndBackToWork(GameTestHelper helper) {
		List<BuildSiteManager.Finished> homes = new ArrayList<>();
		setUp(helper, homes, List.of());
		helper.runAfterDelay(2, () -> staged(helper, legend(null), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = legendary(helper, new BlockPos(10, 2, 10), Legends.get(id("needs_mason")).orElseThrow(), 1);
			Villager farmer = villager(helper, new BlockPos(12, 2, 10), "Bo", VillagerProfession.FARMER);
			LegendPowers.forget();
			helper.assertTrue(LegendPowers.pace(farmer) == 2f, "her pace power doesn't work before the strike: " + LegendPowers.pace(farmer));
			wellbeing(helper, 0.9f); // a happy village; no home: one need unmet

			for (long day = 1; day <= 3; day++) {
				LegendData data = LegendNeeds.check(level, hall, ada, day);
				helper.assertTrue(!data.onStrike() && data.unmet().get(LegendText.HOME) == (int) day, "day " + day + " (grace): " + data);
			}
			helper.assertTrue(chronicle(helper).stream().noneMatch(l -> l.contains("strike")), "a strike in the grace: " + chronicle(helper));
			LegendData data = LegendNeeds.check(level, hall, ada, 4);
			helper.assertTrue(data.onStrike() && data.strikeSince() == 4, "day 4, home unmet four days, out of the grace: no strike: " + data);
			helper.assertTrue(LegendNeeds.striking(ada), "not striking");
			helper.assertTrue(LegendPowers.pace(farmer) == 1f, "her pace power works on strike: " + LegendPowers.pace(farmer));
			helper.assertTrue(chronicle(helper).contains("Ada, the needs_mason, went on strike: \"a home of my own\""), "chronicle: " + chronicle(helper));
			// her trade's work stops: the WORK package has the picket first and holds everything else back
			var pkg = Picket.work(net.minecraft.world.entity.ai.behavior.VillagerGoalPackages.getWorkPackage(VillagerProfession.MASON, 0.5f));
			helper.assertTrue(pkg.get(0).getSecond() instanceof Picket, "the WORK package doesn't start with the picket: " + pkg.get(0).getSecond());
			int held = 0;
			for (var entry : pkg) {
				if (!(entry.getSecond() instanceof Picket) && entry.getFirst() < 99) {
					helper.assertTrue(!entry.getSecond().tryStart(level, ada, level.getGameTime()), "work started on strike: " + entry.getSecond().debugString());
					held++;
				}
			}
			helper.assertTrue(held > 0, "nothing in the Mason's work package to hold back");
			// the red line over her head
			ada.tickCount = 20;
			io.github.jcondedata.aliveworkplace.legend.Legends.tick(ada);
			WorkerStatus.Entry line = WorkerStatus.get(ada, level.getGameTime());
			helper.assertTrue(line != null && line.title().getString().equals("On strike: a home of my own")
				&& TextColor.fromLegacyFormat(ChatFormatting.RED).equals(line.title().getStyle().getColor()), "no red line over her head: " + line);
			// the hall's list
			List<String> lines = LegendText.hallLines(ada).stream().map(Component::getString).toList();
			helper.assertTrue(lines.contains("On strike since day 4") && lines.contains("Wants: a home of my own"), "hall lines: " + lines);
			// the same day, a home: back to work that day
			BlockPos origin = sky(helper);
			home(helper, "needs_manor_3", origin, new Vec3i(8, 5, 8), homes);
			sleepsAt(level, ada, origin.offset(1, 1, 1));
			data = LegendNeeds.check(level, hall, ada, 4);
			helper.assertTrue(!data.onStrike() && data.unmet().isEmpty() && data.checked() == 4, "needs met on day 4, still on strike: " + data);
			helper.assertTrue(!LegendNeeds.striking(ada) && LegendPowers.pace(farmer) == 2f, "back at work but her power's still gone");
			helper.assertTrue(chronicle(helper).contains("Ada, the needs_mason, has all they asked for and went back to work"), "chronicle: " + chronicle(helper));
			// a need unmet one day doesn't start a strike again
			wellbeing(helper, 0.1f);
			data = LegendNeeds.check(level, hall, ada, 5);
			helper.assertTrue(!data.onStrike() && data.unmet().get(LegendText.HAPPY) == 1, "one unhappy day: " + data);
			data = LegendNeeds.check(level, hall, ada, 6);
			helper.assertTrue(data.onStrike() && data.strikeSince() == 6 && LegendNeeds.wants(Legends.of(ada).orElseThrow(), data).getString().equals("a happy village"),
				"two unhappy days: " + data);
			helper.succeed();
		}));
	}

	/** On strike for 20 days and still in the village: kept from despawning, no inn departure, the round still checks her. */
	//$ gametest_ticks_batch AREA '100' '"legendNeedsStay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendNeedsStay")
	public void aLegendOnStrikeTwentyDaysStays(GameTestHelper helper) {
		setUp(helper, new ArrayList<>(), List.of());
		helper.runAfterDelay(2, () -> staged(helper, legend("books"), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = legendary(helper, new BlockPos(10, 2, 10), Legends.get(id("needs_mason")).orElseThrow(), 1);
			ModAttachments.TRAVELLER.set(ada, new io.github.jcondedata.aliveworkplace.inn.Traveller(0, 5)); // came by the inn
			wellbeing(helper, 0.1f);
			for (long day = 1; day <= 24; day++) {
				LegendNeeds.check(level, hall, ada, day);
			}
			LegendData data = ModAttachments.LEGEND.get(ada);
			helper.assertTrue(data.onStrike() && data.strikeSince() == 4 && 24 - data.strikeSince() >= 20, "not on strike 20 days: " + data);
			helper.assertTrue(data.unmet().get(LegendText.HOME) == 24 && data.unmet().get(LegendText.LUXURY) == 24, "days unmet: " + data.unmet());
			String wants = LegendNeeds.wants(Legends.of(ada).orElseThrow(), data).getString();
			helper.assertTrue(wants.equals("a home of my own, books once a week and a happy village"), "wants: " + wants);
			helper.assertTrue(!Innkeepers.stayOver(level, ada), "the inn sends a settled Legend on");
			Innkeepers.leave(level, ada);
			helper.assertTrue(!ada.isRemoved(), "a settled Legend left the inn for good");
			LegendNeeds.round(level, hall);
			helper.assertTrue(ada.isPersistenceRequired(), "the round doesn't keep a Legend from despawning");
			helper.assertTrue(ada.isAlive() && !ada.isRemoved() && Legends.of(ada).isPresent()
				&& io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(level, ada.blockPosition()).equals(Optional.of(hall)), "she's gone");
			helper.succeed();
		}));
	}

	/** {@code legendNeeds} off: the round checks nothing and calls a strike off; the switch is read from the config. */
	//$ gametest_ticks_batch AREA '100' '"legendNeedsOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendNeedsOff")
	public void needsSwitchedOff(GameTestHelper helper) {
		setUp(helper, new ArrayList<>(), List.of());
		helper.runAfterDelay(2, () -> staged(helper, legend("jewels"), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = legendary(helper, new BlockPos(10, 2, 10), Legends.get(id("needs_mason")).orElseThrow(), 1);
			Villager farmer = villager(helper, new BlockPos(12, 2, 10), "Bo", VillagerProfession.FARMER);
			LegendData data = ModAttachments.LEGEND.get(ada);
			ModAttachments.LEGEND.set(ada, data.checkedOn(7, Map.of(LegendText.HOME, 5), 4, -1));
			LegendPowers.forget();
			helper.assertTrue(LegendNeeds.striking(ada) && LegendPowers.pace(farmer) == 1f, "staged strike not on");
			LegendNeeds.ENABLED = false;
			helper.assertTrue(!LegendNeeds.striking(ada), "a Legend pickets with needs off");
			LegendNeeds.round(level, hall);
			data = ModAttachments.LEGEND.get(ada);
			helper.assertTrue(!data.onStrike() && data.unmet().isEmpty() && data.checked() == 7, "needs off, the round: " + data);
			helper.assertTrue(LegendPowers.pace(farmer) == 2f, "her power is still off: " + LegendPowers.pace(farmer));
			helper.assertTrue(LegendText.hallLines(ada).stream().noneMatch(l -> l.getString().startsWith("On strike")), "the hall still shows a strike");
			helper.assertTrue(!WorkplaceConfig.parse("{\"legendNeeds\": false}").legendNeeds && new WorkplaceConfig().legendNeeds, "the config switch");
			helper.succeed();
		}));
	}

	/** A Legend's needs saved and loaded: every field back, and an older save (no "checked") loads unchecked. */
	//$ gametest_ticks_batch AREA '100' '"legendNeedsSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendNeedsSave")
	public void needsSaveAndLoad(GameTestHelper helper) {
		setUp(helper, new ArrayList<>(), List.of());
		helper.runAfterDelay(2, () -> staged(helper, legend("clothes"), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = legendary(helper, new BlockPos(10, 2, 10), Legends.get(id("needs_mason")).orElseThrow(), 1);
			wellbeing(helper, 0.1f);
			for (long day = 1; day <= 5; day++) {
				LegendNeeds.check(level, hall, ada, day);
			}
			LegendData before = ModAttachments.LEGEND.get(ada);
			helper.assertTrue(before.onStrike() && before.checked() == 5, "not staged: " + before);
			Tag tag = LegendData.CODEC.encodeStart(NbtOps.INSTANCE, before).getOrThrow();
			LegendData back = LegendData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
			helper.assertTrue(back.equals(before), "codec: " + before + " -> " + back);
			CompoundTag old = ((CompoundTag) tag).copy();
			old.remove("checked");
			LegendData older = LegendData.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
			helper.assertTrue(older.checked() == -1 && older.onStrike(), "an older save: " + older);
			// the villager saved and loaded keeps it all
			CompoundTag saved = ada.saveWithoutId(new CompoundTag());
			Villager loaded = EntityType.VILLAGER.create(level);
			loaded.load(saved);
			helper.assertTrue(before.equals(ModAttachments.LEGEND.get(loaded)), "the villager reloaded: " + ModAttachments.LEGEND.get(loaded));
			// checked today: the next round on the same day doesn't count the day twice
			LegendData again = LegendNeeds.check(level, hall, ada, 5);
			helper.assertTrue(again.unmet().equals(before.unmet()), "the same day counted twice: " + before.unmet() + " -> " + again.unmet());
			helper.succeed();
		}));
	}

	/** On strike by day, a Mason Legend leaves her stonecutter and pickets by the Village Hall. */
	//$ gametest_ticks_batch AREA '600' '"legendNeedsPicket"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "legendNeedsPicket")
	public void aLegendOnStrikePicketsTheHall(GameTestHelper helper) {
		setUp(helper, new ArrayList<>(), List.of());
		ServerLevel level = helper.getLevel();
		helper.setDayTime(3000); // mid-morning: work time
		// her strike has to hold over the ticks she walks: needs stay on till the test ends
		boolean needs = LegendNeeds.ENABLED;
		LegendNeeds.ENABLED = true;
		Leftovers.after(helper, () -> LegendNeeds.ENABLED = needs);
		BlockPos station = new BlockPos(18, 2, 18);
		helper.setBlock(station, Blocks.STONECUTTER);
		Villager[] ada = new Villager[1];
		helper.runAfterDelay(2, () -> staged(helper, legend(null), () -> {
			Villager v = helper.spawn(EntityType.VILLAGER, station.north());
			Jobs.employ(level, v, helper.absolutePos(station), PoiTypes.MASON, VillagerProfession.MASON);
			Legends.make(level, v, Legends.get(id("needs_mason")).orElseThrow(), "test");
			LegendData data = ModAttachments.LEGEND.get(v);
			ModAttachments.LEGEND.set(v, data.checkedOn(Chronicle.day(level), Map.of(LegendText.HOME, 3), Chronicle.day(level), -1));
			ada[0] = v;
		}));
		helper.succeedWhen(() -> {
			helper.assertTrue(ada[0] != null, "not staged");
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(ada[0].blockPosition().closerThan(hall, 7), "Ada is " + Math.round(Math.sqrt(ada[0].blockPosition().distSqr(hall)))
				+ " blocks from the hall, activity " + ada[0].getBrain().getActiveNonCoreActivity());
			helper.assertTrue(ada[0].getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(), "she gave up her stonecutter");
		});
	}

	/** Every new sentence a player reads (the stand-in luxuries are in their tags too). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void needsSentencesAndTags(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> missing = new ArrayList<>();
		for (String key : List.of("chronicle.aliveworkplace.legend.strike", "chronicle.aliveworkplace.legend.strike_over", "legend.aliveworkplace.strike_wants",
			"legend.aliveworkplace.want.home", "legend.aliveworkplace.want.luxury", "legend.aliveworkplace.want.happy", "legend.aliveworkplace.want.nothing",
			"legend.aliveworkplace.and", "legend.aliveworkplace.overhead.strike", "legend.aliveworkplace.overhead.picket", "mood.aliveworkplace.reason.luxury",
			"aliveworkplace.config.legendNeeds", "aliveworkplace.config.legendNeeds.tooltip")) {
			if (!lang.has(key)) {
				missing.add(key);
			}
		}
		helper.assertTrue(missing.isEmpty(), "missing sentences: " + missing);
		Map<String, List<net.minecraft.world.item.Item>> stand = Map.of("wine", List.of(Items.HONEY_BOTTLE), "jewels", List.of(Items.AMETHYST_SHARD, Items.EMERALD),
			"books", List.of(Items.BOOK, Items.ENCHANTED_BOOK),
			"clothes", List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS));
		for (var e : stand.entrySet()) {
			for (var item : e.getValue()) {
				helper.assertTrue(new ItemStack(item).is(LegendNeeds.luxuryTag(e.getKey())), item + " isn't in luxury/" + e.getKey());
			}
		}
		ItemStack dyed = new ItemStack(Items.LEATHER_CHESTPLATE);
		dyed.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x3366FF, true));
		helper.assertTrue(dyed.is(LegendNeeds.luxuryTag("clothes")), "dyed leather isn't fine clothes");
		helper.assertTrue(!new ItemStack(Items.IRON_CHESTPLATE).is(LegendNeeds.luxuryTag("clothes")), "iron armour is fine clothes");
		helper.succeed();
	}
}
