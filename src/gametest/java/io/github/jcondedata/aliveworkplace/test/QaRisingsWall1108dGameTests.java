package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallSentries;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
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
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane D, 2026-11-08): a mutation check of {@code threat/Risings} and {@code wonder/GreatWallSentries}
 * (lane-d-1108-0407). These kill the survivors a player would notice, written from the spec (ROADMAP 35.16, the Great
 * Wall's sentries: "Damaged, or with Wonders off, the power is down ... nobody is replaced"; a lost sentry is replaced
 * the next morning, once a day, the hall's round before 2000).
 */
public class QaRisingsWall1108dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TWO_POSTS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1108d_two_post_wall");
	private static final long DAY = VillageNeeds.DAY;

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A wall of the test's own beside the loaded Wonders: the Great Wall's first part, two posts on {@link #walk}. */
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

	/** A finished Wonder of the village round {@code hall}, damaged (power down) or whole; forgotten after the test. */
	private static WonderSite wonder(GameTestHelper helper, BlockPos hall, BlockPos stone, boolean damaged) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().containsKey(TWO_POSTS), TWO_POSTS + " isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(TWO_POSTS, hall, null, "", 1);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(BlockPos.ZERO), Rotation.NONE, Mirror.NONE), stone);
		manager.finish(site.id(), Chronicle.day(level));
		manager.setKept(site.id(), new WonderSite.Kept(damaged ? 80 : 100, Chronicle.day(level) + 10_000, damaged, 0));
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

	private static Villager tinkerer(GameTestHelper helper, BlockPos rel) {
		Villager v = helper.spawn(EntityType.VILLAGER, rel);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setType(VillagerType.PLAINS).setProfession(ModVillagers.TINKERER).setLevel(3));
		v.setCustomName(Component.literal("Tibbs"));
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

	private static List<IronGolem> golems(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(4), IronGolem::isAlive);
	}

	/**
	 * A Wall finished while damaged (a part broken: its power is down) sends no sentries to its posts; nor does a whole
	 * Wall with Wonders switched off. Whole and with Wonders on, its two sentries come.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1108dWallDamagedNoSentries"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1108dWallDamagedNoSentries")
	public void aDamagedWallOrWondersOffSendsNoSentries(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(27, 2, 20));
		twoPostWall(helper);
		walk(helper);
		WonderSite damaged = wonder(helper, hall, hall.offset(-1, 0, 0), true);
		helper.assertTrue(!damaged.empowered(), "setup: expected the damaged Wall's power down");
		int came = GreatWallSentries.man(level, damaged);
		helper.assertTrue(came == 0 && golems(helper).isEmpty(), "a damaged Wall (power down) sent sentries: " + came + " came, golems " + golems(helper).size());
		WonderManager manager = WonderManager.get(level);
		manager.setKept(damaged.id(), new WonderSite.Kept(100, Chronicle.day(level) + 10_000, false, 0));
		Wonders.forgetPowers();
		WonderSite whole = manager.site(damaged.id()).orElseThrow();
		Wonders.ENABLED = false;
		try {
			came = GreatWallSentries.man(level, whole);
		} finally {
			Wonders.ENABLED = true;
		}
		helper.assertTrue(came == 0 && golems(helper).isEmpty(), "with Wonders off the Wall sent sentries: " + came + " came, golems " + golems(helper).size());
		came = GreatWallSentries.man(level, whole);
		helper.assertTrue(came == 2 && golems(helper).size() == 2, "the whole Wall's two sentries didn't come: " + came + ", golems " + golems(helper).size());
		AliveWorkplace.LOG.info("QA wall: damaged 0 sentries, Wonders off 0, whole {}", came);
		helper.succeed();
	}

	/**
	 * A lost sentry is replaced in the morning only (the hall's round before 2000: at 2000 it's too late), and once a day:
	 * lost again the same morning it waits for the next; each replacement costs 4 iron blocks and a pumpkin.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1108dWallMorningOnce"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1108dWallMorningOnce")
	public void aLostSentryIsReplacedBefore2000AndOnceADay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(5, 2, 24));
		twoPostWall(helper);
		walk(helper);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		BlockPos stoneRel = new BlockPos(24, 2, 24);
		helper.setBlock(stoneRel, Blocks.STONE);
		WonderSite site = wonder(helper, hall, helper.absolutePos(stoneRel), false);
		Container stock = chest(helper, stoneRel.offset(1, 0, 0));
		stock.setItem(0, new ItemStack(Items.IRON_BLOCK, 12));
		stock.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 3));
		tinkerer(helper, new BlockPos(3, 2, 20));
		helper.assertTrue(GreatWallSentries.man(level, site) == 2, "the sentries didn't take up their posts");
		GreatWallSentries.Post post = GreatWallSentries.posts(level, hall).get(0);
		GreatWallSentries.sentry(level, post).orElseThrow().discard();
		long day = Chronicle.day(level) + 1;
		at(level, day, GreatWallSentries.MORNING_TO);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isEmpty() && count(stock, Items.IRON_BLOCK) == 12,
			"replaced at 2000 (past the morning): iron left " + count(stock, Items.IRON_BLOCK));
		at(level, day + 1, GreatWallSentries.MORNING_TO - 1);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isPresent() && count(stock, Items.IRON_BLOCK) == 8 && count(stock, Items.CARVED_PUMPKIN) == 2,
			"not replaced (or not paid for) at 1999: iron " + count(stock, Items.IRON_BLOCK) + ", pumpkins " + count(stock, Items.CARVED_PUMPKIN));
		// lost again the same morning: the next round of that morning makes no second one
		GreatWallSentries.sentry(level, post).orElseThrow().discard();
		at(level, day + 1, GreatWallSentries.MORNING_TO - 1);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isEmpty() && count(stock, Items.IRON_BLOCK) == 8,
			"replaced twice in one morning: iron left " + count(stock, Items.IRON_BLOCK));
		at(level, day + 2, 0);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isPresent() && count(stock, Items.IRON_BLOCK) == 4 && count(stock, Items.CARVED_PUMPKIN) == 1,
			"not replaced the next morning: iron " + count(stock, Items.IRON_BLOCK) + ", pumpkins " + count(stock, Items.CARVED_PUMPKIN));
		AliveWorkplace.LOG.info("QA wall: not at 2000, at 1999 once, again the next day");
		helper.succeed();
	}
}
