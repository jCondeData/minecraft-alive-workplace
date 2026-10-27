package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.wonder.WonderCelebration;
import io.github.jcondedata.aliveworkplace.wonder.WonderGoods;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.BeautyPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.MoodPower;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (qa-lane-b-1027-1241) for a finished Wonder (ROADMAP 35.8) and the folly's cornice (35.8a), filling what the Wonder
 * tests before it leave open: the {@code wonderStanding} line on both sides (90% stands, 89% is damaged, back at 90% it's
 * repaired), told once and only to its owner, in plain English; the daily count waiting while Wonders are off and running
 * when they're back on; a finished Wonder's stone breaking as a plain stone, with no plan back and its powers still on;
 * a Wonder finished after the evening's festival celebrating the next evening, its announcement said once and kept once
 * through a reload; a stranger handing goods in; and the folly turned and built against a hillside keeping its stone
 * cornice under the dome, no dirt on its roof.
 */
public class QaWonders1027GameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final BlockPos HALL = new BlockPos(2, 2, 27);
	private static final BlockPos MIDDLE = new BlockPos(14, 2, 14);
	private static final Set<Block> EARTH = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT,
		Blocks.DIRT_PATH, Blocks.MUD, Blocks.MYCELIUM, Blocks.FARMLAND);
	private static final Set<Block> CORNICE = Set.of(Blocks.STONE_BRICK_STAIRS, Blocks.SMOOTH_STONE, Blocks.CHISELED_STONE_BRICKS);

	record Finished(ServerLevel level, BlockPos hall, UUID id, List<WonderUpkeep.Block> blocks) {
		WonderSite site() {
			return WonderManager.get(level).site(id).orElseThrow();
		}
	}

	/** The folly finished at {@link #MIDDLE} for the hall at {@link #HALL}, every block of it standing in the world. */
	static Finished finishedFolly(GameTestHelper helper, UUID owner) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, hall, owner, owner == null ? "" : "Owner", 1);
		BlockPos origin = helper.absolutePos(MIDDLE).offset(-4, 0, -4);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		manager.finish(site.id(), 1);
		UUID id = site.id();
		int standing = WonderUpkeep.STANDING;
		boolean on = Wonders.ENABLED;
		Leftovers.after(helper, () -> {
			WonderUpkeep.STANDING = standing;
			Wonders.ENABLED = on;
			WonderManager.get(level).remove(id);
			Wonders.forgetPowers();
		});
		List<WonderUpkeep.Block> blocks = WonderUpkeep.blocks(level, manager.site(id).orElseThrow());
		helper.assertTrue(blocks.size() > 100, "the folly's blocks: " + blocks.size());
		for (WonderUpkeep.Block b : blocks) {
			level.setBlock(b.pos(), b.state(), 2 | 16);
		}
		return new Finished(level, hall, id, blocks);
	}

	static List<String> chronicle(ServerLevel level, BlockPos hall) {
		return ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.WONDER)
			.map(e -> e.text().getString()).toList();
	}

	private static void plain(GameTestHelper helper, List<String> lines) {
		for (String text : lines) {
			helper.assertTrue(!text.contains("aliveworkplace.") && !text.contains("%s") && !text.contains("%%") && !text.contains("$s"), "not plain English: " + text);
		}
	}

	/**
	 * wonderStanding 90 on both sides: exactly 90% standing keeps the powers; one block more broken (89%) is damaged,
	 * the owner told once (a second count while still damaged tells nobody again), a stranger online never; one block
	 * put back (90%) is repaired and the powers return. Every line read, rendered with its real numbers.
	 */
	//$ gametest_batch TALL '"qaWonderStanding1027"'
	@GameTest(template = TALL, batch = "qaWonderStanding1027")
	public void ninetyStandsEightyNineIsDamagedAndTheOwnerIsToldOnce(GameTestHelper helper) {
		List<Component> ownerHeard = new ArrayList<>();
		List<Component> strangerHeard = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, ownerHeard, "qa-wonder-owner");
		WonderFinishedGameTests.listener(helper, strangerHeard, "qa-wonder-stranger");
		Finished f = finishedFolly(helper, owner.getUUID());
		ServerLevel level = f.level();
		WonderUpkeep.STANDING = 90;
		int n = f.blocks().size();
		// the most blocks that can go with 90% still standing (the share is rounded down)
		int k = 0;
		while ((n - (k + 1)) * 100 / n >= 90) {
			k++;
		}
		helper.assertTrue((n - k) * 100 / n == 90 && (n - k - 1) * 100 / n == 89, "no exact 90/89 split for " + n + " blocks");
		List<WonderUpkeep.Block> pick = new ArrayList<>(f.blocks());
		net.minecraft.Util.shuffle(pick, RandomSource.create(10278L));
		helper.assertTrue(WonderUpkeep.countNow(level, f.id()) == 100, "the whole folly");
		for (WonderUpkeep.Block b : pick.subList(0, k)) {
			level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		int at90 = WonderUpkeep.countNow(level, f.id());
		helper.assertTrue(at90 == 90 && !f.site().kept().damaged() && Wonders.power(level, f.hall(), MoodPower.TYPE).isPresent()
			&& Wonders.power(level, f.hall(), BeautyPower.TYPE).isPresent(), "at exactly 90% the powers stopped: " + at90 + "% " + f.site().kept());
		helper.assertTrue(chronicle(level, f.hall()).isEmpty() && ownerHeard.stream().noneMatch(c -> c.getString().contains("Test Folly")),
			"told about 90%: " + chronicle(level, f.hall()));

		BlockPos last = pick.get(k).pos();
		level.setBlock(last, Blocks.AIR.defaultBlockState(), 2 | 16);
		int at89 = WonderUpkeep.countNow(level, f.id());
		helper.assertTrue(at89 == 89 && f.site().kept().damaged() && f.site().kept().standing() == 89, "at 89%: " + at89 + " " + f.site().kept());
		helper.assertTrue(Wonders.power(level, f.hall(), MoodPower.TYPE).isEmpty() && Wonders.power(level, f.hall(), BeautyPower.TYPE).isEmpty(),
			"a damaged folly's powers are still on");
		WonderUpkeep.countNow(level, f.id()); // counted again, still damaged
		String village = VillageHalls.name(level, f.hall()).getString();
		String told = "The Test Folly of " + village + " is damaged: 89% of it stands, 90% is needed. Its powers have stopped until the builders repair it.";
		List<String> ownerLines = ownerHeard.stream().map(Component::getString).toList();
		helper.assertTrue(ownerLines.stream().filter(told::equals).count() == 1, "the owner should hear \"" + told + "\" once: " + ownerLines);
		helper.assertTrue(strangerHeard.stream().noneMatch(c -> c.getString().contains("Test Folly")), "a stranger was told: "
			+ strangerHeard.stream().map(Component::getString).toList());
		List<String> lines = chronicle(level, f.hall());
		helper.assertTrue(lines.equals(List.of("The Test Folly was damaged: only 89% of it stands, and its powers stopped")), "the chronicle: " + lines);
		String stone = WonderStages.statusText(level, f.site().stone().orElseThrow()).getString();
		helper.assertTrue(stone.contains("Damaged: 89% of it stands (90% needed)"), "the stone says: " + stone);

		level.setBlock(last, pick.get(k).state(), 2 | 16);
		int back = WonderUpkeep.countNow(level, f.id());
		helper.assertTrue(back == 90 && !f.site().kept().damaged() && Wonders.power(level, f.hall(), MoodPower.TYPE).isPresent(), "back at 90%: " + back + " " + f.site().kept());
		String repaired = "The Test Folly of " + village + " stands again (90%): its powers have returned.";
		helper.assertTrue(ownerHeard.stream().map(Component::getString).filter(repaired::equals).count() == 1, "the owner should hear \"" + repaired + "\": "
			+ ownerHeard.stream().map(Component::getString).toList());
		helper.assertTrue(chronicle(level, f.hall()).equals(List.of("The Test Folly was damaged: only 89% of it stands, and its powers stopped",
			"The Test Folly was repaired, and its powers returned")), "the chronicle: " + chronicle(level, f.hall()));
		String after = WonderStages.statusText(level, f.site().stone().orElseThrow()).getString();
		helper.assertTrue(!after.contains("Damaged"), "the stone still says damaged: " + after);
		plain(helper, ownerHeard.stream().map(Component::getString).toList());
		plain(helper, chronicle(level, f.hall()));
		plain(helper, List.of(stone, after));
		helper.succeed();
	}

	/**
	 * The daily count, by the server's own tick: a folly 15% broken and due its count isn't counted while Wonders are off
	 * (its record unchanged for 100 ticks), and is counted as damaged once they're back on, with no owner online.
	 */
	//$ gametest_ticks_batch TALL '600' '"qaWonderOffCount1027"'
	@GameTest(template = TALL, timeoutTicks = 600, batch = "qaWonderOffCount1027")
	public void theDailyCountWaitsWhileWondersAreOff(GameTestHelper helper) {
		Finished f = finishedFolly(helper, UUID.randomUUID()); // its owner is offline
		ServerLevel level = f.level();
		WonderUpkeep.forget();
		WonderManager manager = WonderManager.get(level);
		long today = Chronicle.day(level);
		WonderSite.Kept due = new WonderSite.Kept(100, today - 1, false, 0);
		manager.setKept(f.id(), due);
		List<WonderUpkeep.Block> pick = new ArrayList<>(f.blocks());
		net.minecraft.Util.shuffle(pick, RandomSource.create(1027L));
		for (WonderUpkeep.Block b : pick.subList(0, (int) Math.ceil(f.blocks().size() * 0.15))) {
			level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		Wonders.ENABLED = false;
		AtomicInteger ticks = new AtomicInteger();
		helper.onEachTick(() -> {
			int t = ticks.incrementAndGet();
			if (t <= 100) {
				helper.assertTrue(f.site().kept().equals(due), "counted with Wonders off (tick " + t + "): " + f.site().kept());
			}
			if (t == 100) {
				Wonders.ENABLED = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ticks.get() > 100, "still off");
			WonderSite.Kept kept = f.site().kept();
			helper.assertTrue(kept.damaged() && kept.counted() == Chronicle.day(level) && kept.standing() <= 85, "not counted once back on: " + kept);
			helper.assertTrue(Wonders.power(level, f.hall(), MoodPower.TYPE).isEmpty(), "the damaged folly's mood power");
			List<String> lines = chronicle(level, f.hall());
			helper.assertTrue(lines.size() == 1 && lines.get(0).startsWith("The Test Folly was damaged: only "), "the chronicle: " + lines);
			plain(helper, lines);
		});
	}

	/**
	 * The owner, sneaking, breaks a finished Wonder's Foundation Stone the way a player breaks a block: it goes as a
	 * plain stone; no Wonder Plan comes back (that would raise a second Wonder for free), and the Wonder stays finished
	 * with its powers on, also after a save and reload.
	 */
	//$ gametest_batch TALL '"qaWonderFinishedStone1027"'
	@GameTest(template = TALL, batch = "qaWonderFinishedStone1027")
	public void aFinishedWondersStoneBreaksAsAStoneWithNoPlanBack(GameTestHelper helper) {
		List<Component> heard = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, heard, "qa-stone-owner");
		Finished f = finishedFolly(helper, owner.getUUID());
		ServerLevel level = f.level();
		BlockPos stone = f.site().stone().orElseThrow();
		level.setBlock(stone, ModBlocks.FOUNDATION_STONE.defaultBlockState(), 3);
		owner.teleportTo(stone.getX() + 0.5, stone.getY(), stone.getZ() - 1.5);
		owner.setShiftKeyDown(true);
		boolean broke = owner.gameMode.destroyBlock(stone);
		helper.assertTrue(broke && level.getBlockState(stone).isAir(), "the finished Wonder's stone didn't break: " + level.getBlockState(stone));
		for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
			ItemStack s = owner.getInventory().getItem(i);
			helper.assertTrue(WonderPlanItem.plan(s).isEmpty(), "a Wonder Plan came back for a finished Wonder: " + s);
		}
		helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(stone).inflate(4))
			.stream().noneMatch(e -> WonderPlanItem.plan(e.getItem()).isPresent()), "a Wonder Plan dropped by the stone");
		WonderSite site = WonderManager.get(level).site(f.id()).orElse(null);
		helper.assertTrue(site != null && site.done() && site.empowered(), "the finished Wonder went with its stone: " + site);
		helper.assertTrue(Wonders.power(level, f.hall(), MoodPower.TYPE).isPresent(), "the powers went with the stone");
		helper.assertTrue(heard.stream().noneMatch(c -> c.getString().toLowerCase().contains("cancel")), "told it was cancelled: "
			+ heard.stream().map(Component::getString).toList());
		var registries = level.registryAccess();
		WonderSite read = WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries).site(f.id()).orElse(null);
		helper.assertTrue(read != null && read.done() && read.empowered(), "read back: " + read);
		helper.succeed();
	}

	/**
	 * Finished just as the evening's festival ends (Festivals.END), the festival is the next evening's, not today's; the
	 * announcement is heard once, in plain English, and stays once though the stages go on ticking and the manager is
	 * saved and read back.
	 */
	//$ gametest_batch TALL '"qaWonderLateFestival1027"'
	@GameTest(template = TALL, batch = "qaWonderLateFestival1027")
	public void aWonderFinishedAtNightCelebratesTheNextEvening(GameTestHelper helper) {
		List<Component> heard = new ArrayList<>();
		WonderFinishedGameTests.listener(helper, heard, "qa-festival-listener");
		Finished f = finishedFolly(helper, null);
		ServerLevel level = f.level();
		long dayStart = (level.getDayTime() / 24000 + 1) * 24000;
		level.setDayTime(dayStart + io.github.jcondedata.aliveworkplace.hall.Festivals.END); // the festival's last tick is over
		long day = Chronicle.day(level);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(f.hall());
		helper.assertTrue(hall.festivalDay() < day, "a festival already set: " + hall.festivalDay());
		WonderCelebration.finished(level, f.site(), day);
		helper.assertTrue(f.site().kept().festival() == day + 1 && hall.festivalDay() == day + 1, "festival day " + f.site().kept().festival() + " / "
			+ hall.festivalDay() + ", finished on " + day);
		helper.assertTrue(WonderCelebration.celebrating(level, f.hall()).isEmpty(), "celebrating tonight, after the festival's end");
		for (int i = 0; i < 5; i++) {
			WonderStages.tick(level);
		}
		var registries = level.registryAccess();
		WonderManager back = WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries);
		helper.assertTrue(back.site(f.id()).orElseThrow().kept().festival() == day + 1, "the festival day read back: " + back.site(f.id()));
		String told = VillageHalls.name(level, f.hall()).getString() + " has finished the Test Folly!";
		List<String> lines = heard.stream().map(Component::getString).toList();
		helper.assertTrue(lines.stream().filter(told::equals).count() == 1, "\"" + told + "\" heard " + lines.stream().filter(told::equals).count() + " times: " + lines);
		List<String> chron = chronicle(level, f.hall());
		helper.assertTrue(chron.stream().filter(l -> l.startsWith("The Test Folly was finished")).count() == 1, "the chronicle: " + chron);
		plain(helper, lines);
		plain(helper, chron);
		// the next evening, it is the Wonder's festival
		level.setDayTime(dayStart + 24000 + 10000);
		helper.assertTrue(WonderCelebration.celebrating(level, f.hall()).map(WonderSite::id).filter(f.id()::equals).isPresent(),
			"no Wonder festival the next evening (day " + Chronicle.day(level) + ")");
		helper.succeed();
	}

	/** Anyone can hand goods in: a stranger's books count on the stage's line at the stone, and stay counted after a reload. */
	//$ gametest_batch TALL '"qaWonderStrangerGoods1027"'
	@GameTest(template = TALL, batch = "qaWonderStrangerGoods1027")
	public void aStrangerHandsGoodsInAtTheStone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		List<Component> heard = new ArrayList<>();
		ServerPlayer stranger = WonderFinishedGameTests.listener(helper, heard, "qa-goods-stranger");
		BlockPos origin = helper.absolutePos(new BlockPos(10, 2, 10));
		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, origin, UUID.randomUUID(), "Owner", 1);
		UUID id = site.id();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(sites.all())) {
				if (id.equals(s.wonder())) {
					sites.remove(s.id());
				}
			}
			manager.remove(id);
		});
		BlockPos stone = origin.offset(-2, 0, -2);
		for (BuildSite part : WonderStages.start(level, id, at, stone, null)) {
			for (int i = 0; i < 20 && !part.isDone(); i++) {
				part.finishList();
			}
		}
		WonderStages.tick(level);
		WonderSite two = manager.site(id).orElseThrow();
		helper.assertTrue(two.stage() == 2 && !two.started(), "stage II isn't waiting for its goods: " + two);
		int book = -1;
		List<WonderGoods.Line> lines = WonderGoods.lines(level, two);
		for (WonderGoods.Line l : lines) {
			if (WonderGoods.matches(l.good(), new ItemStack(Items.BOOK))) {
				book = l.index();
			}
		}
		helper.assertTrue(book >= 0, "no book line: " + lines);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK, 2));
		helper.assertTrue(WonderGoods.handInAtStone(stranger, stone, stranger.getMainHandItem()), "a stranger's books weren't taken");
		WonderGoods.Line now = WonderGoods.lines(level, manager.site(id).orElseThrow()).get(book);
		helper.assertTrue(now.in() == 2 && now.full() && stranger.getMainHandItem().isEmpty(), "the book line after the stranger: " + now + ", hand " + stranger.getMainHandItem());
		helper.assertTrue(!manager.site(id).orElseThrow().started(), "stage II began with one line of three");
		var registries = level.registryAccess();
		WonderManager back = WonderManager.load(manager.save(new CompoundTag(), registries), registries);
		helper.assertTrue(back.site(id).orElseThrow().delivered().equals(manager.site(id).orElseThrow().delivered()), "the books read back: " + back.site(id));
		plain(helper, heard.stream().map(Component::getString).toList());
		helper.succeed();
	}

	/**
	 * 35.8a, beyond the flat, unturned folly: the drawings' band under the dome is stone (stage II's top layer and the
	 * whole folly's at y 12 and 13 hold no earth); and the folly turned 180° and built against a dirt hillside higher
	 * than its dome, every step of every stage done as its plan has them (the landscaping too), has its stone cornice
	 * all round and no earth anywhere on its roof or dome.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaFollyCornice1027"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaFollyCornice1027")
	public void theFollysCorniceIsStoneTurnedAgainstAHill(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		// the drawings
		Blueprint two = BlueprintLibrary.get(level, ResourceLocation.parse("aliveworkplace_test:wonder/test_folly/stage_2_0")).orElseThrow();
		int top = two.size().getY() - 1;
		int topCount = 0;
		for (Blueprint.Entry e : two.blocks()) {
			if (e.pos().getY() == top && !e.state().isAir()) {
				topCount++;
				helper.assertTrue(CORNICE.contains(e.state().getBlock()), "stage II's top layer has " + e.state() + " at " + e.pos().toShortString());
			}
		}
		helper.assertTrue(topCount == 81, "stage II's top layer: " + topCount + " blocks");
		Blueprint drawn = BlueprintLibrary.get(level, ResourceLocation.parse("aliveworkplace_test:wonder_drawn/test_folly")).orElseThrow();
		for (Blueprint.Entry e : drawn.blocks()) {
			helper.assertTrue(e.pos().getY() < 1 || !EARTH.contains(e.state().getBlock()), "the drawn folly has " + e.state() + " at " + e.pos().toShortString());
		}

		// the folly turned, against a hill
		BlockPos origin = helper.absolutePos(new BlockPos(18, 2, 18));
		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.CLOCKWISE_180, Mirror.NONE);
		BoundingBox box = BlueprintOutline.bounds(at, drawn.size());
		for (int x = box.minX() - 3; x < box.minX(); x++) {
			for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
				for (int y = origin.getY(); y <= origin.getY() + 16; y++) {
					level.setBlock(new BlockPos(x, y, z), y == origin.getY() + 16 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2 | 16);
				}
			}
		}
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, origin, null, "Test", 1);
		UUID id = site.id();
		BlockPos stoneAt = WonderStages.stoneSpot(level, at).orElse(origin.offset(2, 0, 2));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(sites.all())) {
				if (id.equals(s.wonder())) {
					sites.remove(s.id());
				}
			}
			manager.remove(id);
			for (int x = box.minX() - 3; x < box.minX(); x++) {
				for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
					for (int y = origin.getY(); y <= origin.getY() + 16; y++) {
						level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				}
			}
		});
		WonderStages.start(level, id, at, stoneAt, null);
		for (int stage = 1; stage <= 3; stage++) {
			WonderSite now = manager.site(id).orElseThrow();
			helper.assertTrue(now.stage() == stage && now.started(), "at stage " + stage + ": " + now);
			for (UUID partId : now.parts()) {
				BuildSite part = BuildSiteManager.get(level).get(partId);
				BuildPlan plan = part.plan(level);
				for (BuildPlan.Stage kind : new BuildPlan.Stage[] {BuildPlan.Stage.CLEAR, BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE,
					BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE}) {
					for (BuildPlan.Step step : plan.steps(kind)) {
						level.setBlock(step.pos(), step.state(), 2 | 16);
					}
				}
				for (int i = 0; i < 20 && !part.isDone(); i++) {
					part.finishList();
				}
			}
			WonderStages.tick(level);
			if (stage < 3) {
				WonderGoodsKit.handInStage(level, id);
				WonderStages.tick(level);
			}
		}
		helper.assertTrue(manager.site(id).orElseThrow().done(), "the folly wasn't finished: " + manager.site(id));
		int corniceY = origin.getY() + 12;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				boolean edge = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
				BlockState cornice = level.getBlockState(new BlockPos(x, corniceY, z));
				helper.assertTrue(!edge || cornice.is(Blocks.STONE_BRICK_STAIRS), "the cornice at " + new BlockPos(x - origin.getX(), 12, z - origin.getZ()).toShortString()
					+ ": " + cornice);
				for (int y = corniceY; y <= origin.getY() + 18; y++) {
					BlockState s = level.getBlockState(new BlockPos(x, y, z));
					helper.assertTrue(!EARTH.contains(s.getBlock()), "earth on the folly at " + new BlockPos(x - origin.getX(), y - origin.getY(), z - origin.getZ()).toShortString()
						+ ": " + s);
				}
			}
		}
		helper.succeed();
	}

	/**
	 * A damaged folly and two builders of its village looking for work: the first takes every part with holes (one its
	 * job, the rest queued), the second finds nothing left, so no part is repaired twice; with Wonders off, nobody
	 * repairs it; a builder of a village far away never does.
	 */
	//$ gametest_batch TALL '"qaWonderTwoRepairers1027"'
	@GameTest(template = TALL, batch = "qaWonderTwoRepairers1027")
	public void twoBuildersNeverRepairTheSamePart(GameTestHelper helper) {
		Finished f = finishedFolly(helper, UUID.randomUUID());
		ServerLevel level = f.level();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(sites.all())) {
				if (f.id().equals(s.wonder())) {
					sites.remove(s.id());
				}
			}
		});
		List<WonderUpkeep.Block> pick = new ArrayList<>(f.blocks());
		net.minecraft.Util.shuffle(pick, RandomSource.create(27L));
		for (WonderUpkeep.Block b : pick.subList(0, (int) Math.ceil(f.blocks().size() * 0.15))) {
			level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		helper.assertTrue(WonderUpkeep.countNow(level, f.id()) <= 85 && f.site().kept().damaged(), "not damaged: " + f.site().kept());
		BlockPos benchA = new BlockPos(5, 2, 28);
		BlockPos benchB = new BlockPos(8, 2, 28);
		helper.setBlock(benchA, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(benchB, ModBlocks.BUILDERS_BENCH);
		net.minecraft.world.entity.npc.Villager a = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(5, 2, 26));
		net.minecraft.world.entity.npc.Villager b = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(8, 2, 26));
		io.github.jcondedata.aliveworkplace.build.Builders.employ(level, a, helper.absolutePos(benchA));
		io.github.jcondedata.aliveworkplace.build.Builders.employ(level, b, helper.absolutePos(benchB));

		Wonders.ENABLED = false;
		helper.assertTrue(WonderUpkeep.repair(level, a, helper.absolutePos(benchA)).isEmpty() && !WonderUpkeep.anyDamaged(level), "repaired with Wonders off");
		Wonders.ENABLED = true;
		BlockPos far = helper.absolutePos(benchA).offset(VillageHalls.RADIUS * 3, 0, 0);
		helper.assertTrue(WonderUpkeep.repair(level, b, far).isEmpty(), "a builder of a village " + (VillageHalls.RADIUS * 3) + " blocks away repairs it");

		List<BuildSite> first = WonderUpkeep.repair(level, a, helper.absolutePos(benchA));
		helper.assertTrue(!first.isEmpty() && first.stream().allMatch(s -> s.isRepair() && f.id().equals(s.wonder()) && a.getUUID().equals(s.builder())),
			"the first builder's repairs: " + first.size());
		helper.assertTrue(first.stream().filter(s -> !s.isQueued()).count() == 1, "the first builder's job: " + first.stream().map(BuildSite::isQueued).toList());
		List<BuildSite> second = WonderUpkeep.repair(level, b, helper.absolutePos(benchB));
		helper.assertTrue(second.isEmpty(), "the second builder repairs a part already taken: " + second.size());
		List<BuildSite> all = BuildSiteManager.get(level).all().stream().filter(s -> f.id().equals(s.wonder()) && s.isRepair()).toList();
		helper.assertTrue(all.stream().map(BuildSite::placement).distinct().count() == all.size(), "a part repaired twice: " + all.size() + " sites");
		helper.succeed();
	}
}
