package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.JobLevel;
import io.github.jcondedata.aliveworkplace.rules.LegendSettled;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallSentries;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA from the player guide's Great Wall page (docs/guide/great-wall.md, ROADMAP 35.15 and 35.16), lane-d-1104-2209: what
 * the page promises that no test checked yet. The stages ask for the goods in the page's table; without Legends, 4 iron
 * golems and a Master Tinkerer stand in for the Golem Smith (3 golems or a Journeyman don't; with Legends they don't
 * count); a sentry fights a foe 16 blocks from the wall but not 17, watches the far one from the walk and goes back to its
 * alcove; a lost sentry needs its pumpkin as well as the iron, from the chests round the Foundation Stone, in the
 * morning only; with iron for one of two lost sentries, one comes; a tenth missing the Wall still stands, a block more
 * and both powers stop, and put back they return.
 */
public class QaGreatWall1104d4GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation GREAT_WALL = AliveWorkplace.id("great_wall");
	private static final ResourceLocation TWO_POSTS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1104d4_two_post_wall");
	private static final ResourceLocation FOLLY_WALL = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1104d4_folly_wall");
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	/** The two posts of the test's wall, on the walk {@link #walk} builds (top at y 8). */
	private static final BlockPos POST_A = new BlockPos(6, 9, 10);
	private static final BlockPos POST_B = new BlockPos(14, 9, 10);
	private static final long DAY = VillageNeeds.DAY;

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A wall of the test's own beside the loaded Wonders: the Great Wall's first part, two posts on {@link #walk}, its powers' numbers. */
	private static void twoPostWall(GameTestHelper helper) {
		Map<ResourceLocation, Wonder> before = Wonders.loaded();
		JsonObject json = JsonParser.parseString("""
			{"name": "wonder.aliveworkplace.great_wall.name", "description": "wonder.aliveworkplace.great_wall.description",
			 "icon": "minecraft:stone_brick_wall",
			 "stages": [{"name": "wonder.aliveworkplace.great_wall.stage.1", "parts": [{"part": "aliveworkplace:wonder/great_wall/stage_1_0"}]}],
			 "powers": [{"type": "raid_barrier", "margin_degrees": 10}, {"type": "sentries", "range": 16, "posts": [[6, 9, 10], [14, 9, 10]]}]}
			""").getAsJsonObject();
		Map<ResourceLocation, Wonder> with = new LinkedHashMap<>(before);
		with.put(TWO_POSTS, Wonders.read(TWO_POSTS, json));
		Wonders.setForTest(with);
		Leftovers.after(helper, () -> Wonders.setForTest(before));
	}

	/** The walk: solid stone bricks x 3-17, z 9-11, its top at y 8 (a sentry stands on it at 9). */
	private static void walk(GameTestHelper helper) {
		for (int x = 3; x <= 17; x++) {
			for (int z = 9; z <= 11; z++) {
				for (int y = 1; y <= 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS);
				}
			}
		}
	}

	private static BlockPos hall(GameTestHelper helper, BlockPos rel) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(rel);
	}

	/** A finished Wonder {@code id} of the village round {@code hall} at the test area's corner; forgotten after the test. */
	private static WonderSite wonder(GameTestHelper helper, ResourceLocation id, BlockPos hall, BlockPos origin, BlockPos stone, boolean whole) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().containsKey(id), id + " isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(id, hall, null, "", 1);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), stone);
		manager.finish(site.id(), Chronicle.day(level));
		if (whole) {
			manager.setKept(site.id(), new WonderSite.Kept(100, Chronicle.day(level) + 10_000, false, 0));
		}
		Wonders.forgetPowers();
		UUID uuid = site.id();
		Leftovers.after(helper, () -> {
			manager.remove(uuid);
			Wonders.forgetPowers();
			GreatWallPowers.forget();
			GreatWallSentries.forget();
		});
		return manager.site(uuid).orElseThrow();
	}

	private static Villager tinkerer(GameTestHelper helper, BlockPos rel, int level, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, rel);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setType(VillagerType.PLAINS).setProfession(ModVillagers.TINKERER).setLevel(level));
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static Container chest(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, Blocks.CHEST);
		return (Container) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	private static int count(Container chest, Item item) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	private static long madeLines(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().filter(e -> e.text().getString().contains("made a new sentry")).count();
	}

	private static String id(Item item) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
	}

	// ---------------------------------------------------------------- its stages and goods

	/** One row of the guide's table: what's found (item, who finds it faster, in how many days) and the workshop's goods. */
	private record Find(String item, String finder, int days) {
	}

	/**
	 * The guide's goods table: each of the four stages asks for exactly the rare finds it lists (each one, found by
	 * exploring, with the rare worker and the days the table names) and two of the workshop's best goods from the job the
	 * table names; nothing else.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStagesAskForTheGuidesGoods(GameTestHelper helper) {
		Wonder wall = Wonders.loaded().get(GREAT_WALL);
		helper.assertTrue(wall != null, "the Great Wall isn't loaded: " + Wonders.problems());
		List<List<Find>> finds = List.of(
			List.of(new Find("minecraft:sentry_armor_trim_smithing_template", "legend aliveworkplace:beastmaster", 9),
				new Find("minecraft:ghast_tear", "master aliveworkplace:netherworker", 7)),
			List.of(new Find("minecraft:totem_of_undying", "legend aliveworkplace:merchant_prince", 22),
				new Find("minecraft:ominous_trial_key", "legend aliveworkplace:pathfinder", 14),
				new Find("minecraft:rib_armor_trim_smithing_template", "legend aliveworkplace:seer", 14)),
			List.of(new Find("minecraft:wither_skeleton_skull", "legend aliveworkplace:founder", 14),
				new Find("minecraft:netherite_upgrade_smithing_template", "legend aliveworkplace:beastmaster", 14),
				new Find("minecraft:vex_armor_trim_smithing_template", "legend aliveworkplace:merchant_prince", 22)),
			List.of(new Find("minecraft:heavy_core", "legend aliveworkplace:pathfinder", 32),
				new Find("minecraft:nether_star", "legend aliveworkplace:founder", 36),
				new Find("minecraft:netherite_ingot", "legend aliveworkplace:old_sage", 14)));
		List<String> made = List.of("aliveworkplace:emerald_brooch x2 by aliveworkplace:jeweller", "aliveworkplace:vintage_wine x2 by aliveworkplace:vintner",
			"aliveworkplace:gold_circlet x2 by aliveworkplace:jeweller", "aliveworkplace:noble_robes x2 by aliveworkplace:tailor");
		List<String> titles = List.of("The Great Gate", "The West Run", "The East Run", "The Walk and the Towers' Crowns");
		helper.assertTrue(wall.stages().size() == 4, "stages: " + wall.stages().size());
		for (int s = 0; s < 4; s++) {
			Wonder.Stage stage = wall.stages().get(s);
			helper.assertTrue(stage.nameText().getString().equals(titles.get(s)), "stage " + (s + 1) + " is called " + stage.nameText().getString());
			List<Find> found = new ArrayList<>();
			List<String> workshop = new ArrayList<>();
			for (Wonder.Good g : stage.goods()) {
				String item = g.item().map(ResourceLocation::toString).orElse("tag " + g.tag().map(ResourceLocation::toString).orElse("?"));
				if (g.found()) {
					helper.assertTrue(g.count() == 1, titles.get(s) + ": " + g.count() + " of " + item + " to find, not one");
					String finder = g.helper().map(h -> h.condition() instanceof LegendSettled l ? "legend " + l.id()
						: h.condition().type().equals("job_level") ? "master " + describeJob(h.condition()) : h.condition().type()).orElse("nobody");
					found.add(new Find(item, finder, g.helper().map(Wonder.Helper::days).orElse(0)));
				} else {
					workshop.add(item + " x" + g.count() + " by " + g.job());
				}
			}
			helper.assertTrue(found.equals(finds.get(s)), titles.get(s) + "'s finds: " + found + ", the guide: " + finds.get(s));
			helper.assertTrue(workshop.equals(List.of(made.get(s))), titles.get(s) + "'s workshop goods: " + workshop + ", the guide: " + made.get(s));
		}
		helper.succeed();
	}

	/** "aliveworkplace:netherworker" when {@code c} asks for one Master (level 5) of that job; what it asks otherwise. */
	private static String describeJob(Condition c) {
		if (c instanceof JobLevel j) {
			return j.level() == 5 && j.count() == 1 ? j.job().toString() : j.count() + " x level " + j.level() + " " + j.job();
		}
		return c.type();
	}

	// ---------------------------------------------------------------- what it needs

	/**
	 * What it needs, the Golem Smith's part: on a server without Legends, 4 iron golems and a Master Tinkerer stand in.
	 * Three golems and a Master don't; four golems and a Journeyman (level 3) or Expert (level 4) don't; four and a Master
	 * do. With Legends on, the same golems and Master don't: the Golem Smith himself is asked for.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104d4WallStandIn"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104d4WallStandIn")
	public void withoutLegendsFourGolemsAndAMasterTinkererStandInButNotThreeOrAnExpert(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(15, 2, 15));
		boolean legends = Legends.ENABLED;
		Leftovers.after(helper, () -> Legends.ENABLED = legends);
		Wonder wall = Wonders.loaded().get(GREAT_WALL);
		helper.assertTrue(wall != null, "the Great Wall isn't loaded: " + Wonders.problems());
		LegendSettled smith = wall.requirements().stream().filter(c -> c instanceof LegendSettled).map(c -> (LegendSettled) c).findFirst().orElse(null);
		helper.assertTrue(smith != null && smith.id().equals(AliveWorkplace.id("golem_smith")), "the Great Wall's Legend: " + wall.requirements());
		Legends.ENABLED = false;
		helper.assertTrue(smith.standingIn(), "without Legends the Golem Smith has no stand-in");
		List<IronGolem> golems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(3 + 3 * i, 2, 5));
			g.setNoAi(true);
			golems.add(g);
		}
		Villager tinkerer = tinkerer(helper, new BlockPos(20, 2, 20), 5, "Tibbs");
		helper.assertFalse(smith.met(level, hall), "three iron golems and a Master Tinkerer stood in: " + smith.progress(level, hall).line().getString());
		IronGolem fourth = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(12, 2, 5));
		fourth.setNoAi(true);
		golems.add(fourth);
		helper.assertTrue(smith.met(level, hall), "four iron golems and a Master Tinkerer didn't stand in: " + smith.progress(level, hall).line().getString());
		for (int lower : new int[] {3, 4}) {
			tinkerer.setVillagerData(tinkerer.getVillagerData().setLevel(lower));
			helper.assertFalse(smith.met(level, hall), "four golems and a level-" + lower + " Tinkerer stood in: " + smith.progress(level, hall).line().getString());
		}
		tinkerer.setVillagerData(tinkerer.getVillagerData().setLevel(5));
		helper.assertTrue(smith.met(level, hall), "a Master again, still not met");
		// with Legends on (and the Golem Smith's file loaded), golems and a Tinkerer are no Golem Smith
		Legends.ENABLED = true;
		helper.assertTrue(Legends.get(AliveWorkplace.id("golem_smith")).isPresent() && Wonders.LEGENDS, "the Golem Smith's file isn't loaded, or wonderLegends is off");
		helper.assertFalse(smith.standingIn(), "with Legends on the stand-in is still asked for");
		helper.assertFalse(smith.met(level, hall), "with Legends on four golems and a Master Tinkerer stood in for the Golem Smith");
		golems.forEach(IronGolem::discard);
		tinkerer.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- the sentries

	private static boolean near(IronGolem golem, BlockPos spot, double within) {
		return golem.position().distanceTo(Vec3.atBottomCenterOf(spot)) < within;
	}

	/**
	 * "A sentry fights anything within 16 blocks of the wall, then goes back to its alcove. When raiders are about further
	 * off, it steps out onto the walk and watches them." A pillager 17 blocks from the wall isn't fought: both sentries step
	 * out of their alcoves onto the walk (two blocks toward the wall's middle) and stay up there; it's gone, and they go back
	 * to their alcoves; a pillager 16 blocks from the wall is fought.
	 */
	//$ gametest_ticks_batch HUGE '700' '"qa1104d4WallRange"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "qa1104d4WallRange")
	public void aSentryFightsAFoeSixteenBlocksFromTheWallButOnlyWatchesOneAtSeventeen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(27, 2, 20));
		twoPostWall(helper);
		walk(helper);
		WonderSite site = wonder(helper, TWO_POSTS, hall, helper.absolutePos(BlockPos.ZERO), hall.offset(-1, 0, 0), true);
		helper.assertTrue(GreatWallSentries.man(level, site) == 2, "the two sentries didn't take up their posts");
		List<GreatWallSentries.Post> posts = GreatWallSentries.posts(level, hall);
		helper.assertTrue(posts.size() == 2 && posts.get(0).at().equals(helper.absolutePos(POST_A)) && posts.get(1).at().equals(helper.absolutePos(POST_B)), "the posts: " + posts);
		IronGolem a = GreatWallSentries.sentry(level, posts.get(0)).orElseThrow();
		IronGolem b = GreatWallSentries.sentry(level, posts.get(1)).orElseThrow();
		BlockPos walkA = posts.get(0).walk();
		BlockPos walkB = posts.get(1).walk();
		helper.assertTrue(walkA.equals(helper.absolutePos(POST_A.offset(2, 0, 0))) && walkB.equals(helper.absolutePos(POST_B.offset(-2, 0, 0))),
			"the walk's spots: " + helper.relativePos(walkA) + ", " + helper.relativePos(walkB));
		// the wall runs along z 10.5 (the posts' line): a pillager standing at z 27.5 is 17 from it, at 26.5 it's 16
		BlockPos far = new BlockPos(10, 2, 27);
		BlockPos edge = new BlockPos(10, 2, 26);
		Pillager[] foe = {null};
		helper.startSequence()
			.thenExecuteAfter(30, () -> helper.assertTrue(near(a, posts.get(0).at(), 1.5) && near(b, posts.get(1).at(), 1.5),
				"the sentries aren't at their posts: " + a.position() + ", " + b.position()))
			.thenExecute(() -> {
				foe[0] = helper.spawn(EntityType.PILLAGER, far);
				foe[0].setNoAi(true);
				foe[0].moveTo(helper.absolutePos(far).getX() + 0.5, helper.absolutePos(far).getY(), helper.absolutePos(far).getZ() + 0.5);
				helper.assertTrue(Math.abs(posts.get(0).fromWall(foe[0].position()) - 17) < 0.01, "the far pillager is " + posts.get(0).fromWall(foe[0].position()) + " from the wall");
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(a.getTarget() != foe[0] && b.getTarget() != foe[0], "a sentry went for a pillager 17 blocks from the wall");
				helper.assertTrue(near(a, walkA, 0.9) && near(b, walkB, 0.9), "the sentries didn't step out onto the walk to watch: "
					+ helper.relativePos(a.blockPosition()) + ", " + helper.relativePos(b.blockPosition()));
			})
			.thenExecuteFor(60, () -> helper.assertTrue(a.getTarget() != foe[0] && b.getTarget() != foe[0] && a.getY() >= walkA.getY() - 0.1 && b.getY() >= walkB.getY() - 0.1,
				"watching a pillager 17 blocks off, a sentry left the walk or went for it: " + a.position() + " " + a.getTarget() + ", " + b.position() + " " + b.getTarget()))
			.thenExecute(() -> foe[0].discard())
			.thenWaitUntil(() -> helper.assertTrue(near(a, posts.get(0).at(), 0.9) && near(b, posts.get(1).at(), 0.9),
				"the pillager gone, the sentries didn't go back to their alcoves: " + helper.relativePos(a.blockPosition()) + ", " + helper.relativePos(b.blockPosition())))
			.thenExecute(() -> {
				foe[0] = helper.spawn(EntityType.PILLAGER, edge);
				foe[0].setNoAi(true);
				foe[0].moveTo(helper.absolutePos(edge).getX() + 0.5, helper.absolutePos(edge).getY(), helper.absolutePos(edge).getZ() + 0.5);
				helper.assertTrue(Math.abs(posts.get(0).fromWall(foe[0].position()) - 16) < 0.01, "the near pillager is " + posts.get(0).fromWall(foe[0].position()) + " from the wall");
			})
			.thenWaitUntil(() -> helper.assertTrue(a.getTarget() == foe[0] || b.getTarget() == foe[0] || !foe[0].isAlive(),
				"no sentry went for a pillager 16 blocks from the wall: " + a.getTarget() + ", " + b.getTarget()))
			.thenExecute(() -> foe[0].discard())
			.thenSucceed();
	}

	/**
	 * "A lost sentry is replaced the next morning ... if the Wonder's stockpile (the chests round the Foundation Stone)
	 * holds 4 iron blocks and a pumpkin." Four iron blocks by the stone and a pumpkin only in a chest 12 blocks off: no
	 * sentry, and nothing taken. A pumpkin put by the stone at noon: still nobody until the morning. The next morning: the
	 * sentry stands, the stockpile pays, the far chest keeps its pumpkin.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104d4WallPumpkin"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104d4WallPumpkin")
	public void aLostSentryNeedsAPumpkinByTheFoundationStoneAndTheMorning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(5, 2, 24));
		twoPostWall(helper);
		walk(helper);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		BlockPos stoneRel = new BlockPos(24, 2, 24);
		helper.setBlock(stoneRel, Blocks.STONE);
		WonderSite site = wonder(helper, TWO_POSTS, hall, helper.absolutePos(BlockPos.ZERO), helper.absolutePos(stoneRel), true);
		Container byStone = chest(helper, stoneRel.offset(1, 0, 0));
		Container farOff = chest(helper, stoneRel.offset(-12, 0, 0));
		byStone.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		farOff.setItem(0, new ItemStack(Items.CARVED_PUMPKIN, 1));
		farOff.setItem(1, new ItemStack(Items.IRON_BLOCK, 4));
		tinkerer(helper, new BlockPos(3, 2, 20), 3, "Tibbs");
		helper.assertTrue(GreatWallSentries.man(level, site) == 2, "the sentries didn't take up their posts");
		GreatWallSentries.Post post = GreatWallSentries.posts(level, hall).get(0);
		GreatWallSentries.sentry(level, post).orElseThrow().discard();
		long day = Chronicle.day(level) + 1;
		at(level, day, 500);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isEmpty(), "replaced without a pumpkin in the stockpile (one only in a chest 12 blocks from the stone)");
		helper.assertTrue(count(byStone, Items.IRON_BLOCK) == 4 && count(farOff, Items.CARVED_PUMPKIN) == 1 && count(farOff, Items.IRON_BLOCK) == 4,
			"no sentry, but the chests paid: " + count(byStone, Items.IRON_BLOCK) + " iron by the stone, far off " + count(farOff, Items.IRON_BLOCK) + " iron and " + count(farOff, Items.CARVED_PUMPKIN) + " pumpkin");
		// noon of the next day, a pumpkin by the stone: still not the morning
		byStone.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 1));
		at(level, day + 1, 6000);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isEmpty(), "replaced at noon");
		// the next morning
		at(level, day + 2, 300);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isPresent(), "not replaced the morning after the pumpkin came");
		helper.assertTrue(count(byStone, Items.IRON_BLOCK) == 0 && count(byStone, Items.CARVED_PUMPKIN) == 0, "the stockpile by the stone didn't pay: "
			+ count(byStone, Items.IRON_BLOCK) + " iron, " + count(byStone, Items.CARVED_PUMPKIN) + " pumpkin left");
		helper.assertTrue(count(farOff, Items.CARVED_PUMPKIN) == 1 && count(farOff, Items.IRON_BLOCK) == 4, "the far chest paid: "
			+ count(farOff, Items.IRON_BLOCK) + " iron, " + count(farOff, Items.CARVED_PUMPKIN) + " pumpkin left");
		helper.assertTrue(madeLines(level, hall) == 1, "chronicle lines: " + madeLines(level, hall));
		helper.succeed();
	}

	/**
	 * Both sentries lost, iron for one (4 iron blocks, 2 pumpkins): the morning brings one sentry, the stockpile keeps a
	 * pumpkin and the other post stays empty; four more iron blocks and the next morning bring the second.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104d4WallIronForOne"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104d4WallIronForOne")
	public void twoLostSentriesWithIronForOneGetOneThenTheOther(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(5, 2, 24));
		twoPostWall(helper);
		walk(helper);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		BlockPos stoneRel = new BlockPos(24, 2, 24);
		helper.setBlock(stoneRel, Blocks.STONE);
		WonderSite site = wonder(helper, TWO_POSTS, hall, helper.absolutePos(BlockPos.ZERO), helper.absolutePos(stoneRel), true);
		Container stock = chest(helper, stoneRel.offset(0, 0, 1));
		stock.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		stock.setItem(1, new ItemStack(Items.PUMPKIN, 2));
		tinkerer(helper, new BlockPos(3, 2, 20), 2, "Pell");
		helper.assertTrue(GreatWallSentries.man(level, site) == 2, "the sentries didn't take up their posts");
		List<GreatWallSentries.Post> posts = GreatWallSentries.posts(level, hall);
		posts.forEach(p -> GreatWallSentries.sentry(level, p).orElseThrow().discard());
		long day = Chronicle.day(level) + 1;
		at(level, day, 1000);
		GreatWallSentries.morning(level, hall);
		long manned = posts.stream().filter(p -> GreatWallSentries.sentry(level, p).isPresent()).count();
		helper.assertTrue(manned == 1, manned + " posts manned from iron for one");
		helper.assertTrue(count(stock, Items.IRON_BLOCK) == 0 && count(stock, Items.PUMPKIN) == 1, "one sentry's worth wasn't taken: "
			+ count(stock, Items.IRON_BLOCK) + " iron, " + count(stock, Items.PUMPKIN) + " pumpkins left");
		stock.setItem(2, new ItemStack(Items.IRON_BLOCK, 4));
		at(level, day + 1, 1000);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(posts.stream().allMatch(p -> GreatWallSentries.sentry(level, p).isPresent()), "the second post wasn't manned the next morning");
		helper.assertTrue(stock.isEmpty() && madeLines(level, hall) == 2, "the stockpile: " + count(stock, Items.IRON_BLOCK) + " iron, "
			+ count(stock, Items.PUMPKIN) + " pumpkins; chronicle lines " + madeLines(level, hall));
		helper.succeed();
	}

	// ---------------------------------------------------------------- damaged

	/** The test folly's stages with the Great Wall's two powers: a small Wonder to break block by block. */
	private static void follyWall(GameTestHelper helper) {
		Map<ResourceLocation, Wonder> before = Wonders.loaded();
		Wonder folly = before.get(FOLLY);
		Wonder wall = before.get(GREAT_WALL);
		helper.assertTrue(folly != null && wall != null, "the folly or the Great Wall isn't loaded: " + Wonders.problems());
		Map<ResourceLocation, Wonder> with = new LinkedHashMap<>(before);
		with.put(FOLLY_WALL, new Wonder(FOLLY_WALL, wall.name(), wall.description(), wall.icon(), true, wall.crew(), folly.requirements(), folly.stages(), wall.powers()));
		Wonders.setForTest(with);
		Leftovers.after(helper, () -> Wonders.setForTest(before));
	}

	/**
	 * "If the Wall is damaged (more than a tenth of it missing), both powers stop": the Wall's powers on a folly built
	 * block for block; with a tenth of its blocks gone the count keeps it standing and both powers on (its raid barrier's
	 * arc and its sentries' posts); one block more and both stop; that block put back, both are on again.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104d4WallTenth"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104d4WallTenth")
	public void aTenthMissingStillStandsOneBlockMoreStopsBothPowers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(2, 2, 27));
		follyWall(helper);
		Leftovers.finished(helper);
		int standingWas = WonderUpkeep.STANDING;
		Leftovers.after(helper, () -> WonderUpkeep.STANDING = standingWas);
		WonderUpkeep.STANDING = 90; // the default
		BlockPos origin = helper.absolutePos(new BlockPos(10, 2, 10));
		WonderSite site = wonder(helper, FOLLY_WALL, hall, origin, origin.offset(-1, 0, -1), false);
		List<WonderUpkeep.Block> blocks = WonderUpkeep.blocks(level, site);
		helper.assertTrue(blocks.size() >= 100, "the folly's blocks: " + blocks.size());
		for (WonderUpkeep.Block b : blocks) {
			level.setBlock(b.pos(), b.state(), 2 | 16);
		}
		helper.assertTrue(WonderUpkeep.countNow(level, site.id()) == 100, "the whole folly doesn't stand");
		helper.assertTrue(GreatWallPowers.arcs(level, hall).size() == 1 && GreatWallSentries.posts(level, hall).size() == 4,
			"the whole Wall's powers: arcs " + GreatWallPowers.arcs(level, hall) + ", posts " + GreatWallSentries.posts(level, hall).size());
		List<WonderUpkeep.Block> pick = new ArrayList<>(blocks);
		net.minecraft.Util.shuffle(pick, RandomSource.create(1104L));
		pick.removeIf(b -> b.state().isAir());
		int tenth = blocks.size() / 10;
		for (WonderUpkeep.Block b : pick.subList(0, tenth)) {
			level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		int left = WonderUpkeep.countNow(level, site.id());
		WonderSite.Kept kept = WonderManager.get(level).site(site.id()).orElseThrow().kept();
		helper.assertTrue(!kept.damaged() && GreatWallPowers.arcs(level, hall).size() == 1 && GreatWallSentries.posts(level, hall).size() == 4,
			tenth + " of " + blocks.size() + " blocks gone (" + left + "% standing): damaged " + kept.damaged() + ", arcs " + GreatWallPowers.arcs(level, hall).size()
				+ ", posts " + GreatWallSentries.posts(level, hall).size());
		WonderUpkeep.Block more = pick.get(tenth);
		level.setBlock(more.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		left = WonderUpkeep.countNow(level, site.id());
		kept = WonderManager.get(level).site(site.id()).orElseThrow().kept();
		helper.assertTrue(kept.damaged() && GreatWallPowers.arcs(level, hall).isEmpty() && GreatWallSentries.posts(level, hall).isEmpty(),
			(tenth + 1) + " of " + blocks.size() + " gone (" + left + "% standing): damaged " + kept.damaged() + ", arcs " + GreatWallPowers.arcs(level, hall).size()
				+ ", posts " + GreatWallSentries.posts(level, hall).size());
		level.setBlock(more.pos(), more.state(), 2 | 16);
		WonderUpkeep.countNow(level, site.id());
		kept = WonderManager.get(level).site(site.id()).orElseThrow().kept();
		helper.assertTrue(!kept.damaged() && GreatWallPowers.arcs(level, hall).size() == 1 && GreatWallSentries.posts(level, hall).size() == 4,
			"the block put back, the powers aren't: damaged " + kept.damaged());
		for (WonderUpkeep.Block b : blocks) {
			level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		helper.succeed();
	}
}
