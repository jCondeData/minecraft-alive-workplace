package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-10-25) of ROADMAP 28.11, the Gem Grower, with the real Cobblemon, from the spec. Not covered before:
 * tumblestones "planted against lava or magma" (a lava pool gets one; a stone block nothing); a bed's plantings stop at
 * the bed file's count (4 round one magma block, with 8 tumblestones in her chests and 5 free faces); Blank TMs "up to 8"
 * (made up to 8 and no more, on 1.8); the trades: tumblestones from the Apprentice, Type Gems from the Expert.
 * Random ticks are off so no bud grows on its own.
 */
public class QaGemGrowerCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Item item(String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
	}

	private static Block block(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
	}

	private static boolean tumblestoneGrowth(GameTestHelper helper, BlockPos pos) {
		var state = helper.getBlockState(pos);
		return state.is(block("small_budding_tumblestone")) || state.is(block("medium_budding_tumblestone"))
			|| state.is(block("large_budding_tumblestone")) || state.is(block("tumblestone_cluster"));
	}

	private static void noRandomTicks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules.IntegerValue ticking = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
		int was = ticking.get();
		ticking.set(0, level.getServer());
		PartnerShowsCompatTests.after(helper, () -> ticking.set(was, level.getServer()));
	}

	private static String said(GameTestHelper helper, Villager grower) {
		WorkerStatus.Entry e = WorkerStatus.get(grower, helper.getLevel().getGameTime());
		return (e == null ? "no status" : e.line().getString()) + " at " + helper.relativePos(grower.blockPosition()) + ", beds "
			+ GemGrowers.beds(grower) + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(grower).stacks();
	}

	/**
	 * A lava source walled in with glass (only its top open, a glass block beside that to sit on) and a plain stone block:
	 * she plants a tumblestone against the lava, and 400 ticks later still nothing against the stone, the other seven
	 * tumblestones still in her chest.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_gem_lava"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_gem_lava")
	public void qaATumblestoneIsPlantedAgainstLavaNeverAgainstStone(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		noRandomTicks(helper);
		BlockPos lava = new BlockPos(8, 2, 2);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.setBlock(lava.relative(d), Blocks.GLASS);
		}
		helper.setBlock(lava.above().east(), Blocks.GLASS);
		helper.setBlock(lava, Blocks.LAVA);
		BlockPos stone = new BlockPos(6, 2, 7);
		helper.setBlock(stone, Blocks.STONE);
		Villager grower = GemGrowerCompatTests.grower(helper, new ItemStack(item("tumblestone"), 8));
		long[] planted = {-1};
		helper.succeedWhen(() -> {
			helper.assertTrue(tumblestoneGrowth(helper, lava.above()), "no tumblestone planted against the lava: "
				+ helper.getBlockState(lava.above()) + "; she says " + said(helper, grower));
			long now = helper.getTick();
			if (planted[0] < 0) {
				planted[0] = now;
			}
			helper.assertTrue(now >= planted[0] + 400, "waiting to see the stone is left alone");
			for (Direction d : Direction.values()) {
				helper.assertFalse(tumblestoneGrowth(helper, stone.relative(d)), "a tumblestone planted against plain stone, " + d);
			}
			helper.assertTrue(helper.getBlockState(lava).is(Blocks.LAVA), "the lava is gone: " + helper.getBlockState(lava));
			Container chest = helper.getBlockEntity(CHEST);
			long left = chest.countItem(item("tumblestone")) + ModAttachments.BUILDER_BAG.getOrCreate(grower).count(item("tumblestone"));
			helper.assertTrue(left == 7, "tumblestones left: " + left + " (one planting fits against the walled-in lava)");
		});
	}

	/**
	 * A magma block on a glass post, five faces free, and 8 tumblestones in her chest: the tumblestone bed takes 4
	 * plantings ("plants": 4), so exactly 4 buds stand round it 400 ticks after the fourth, and 4 tumblestones are left.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_gem_four"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_gem_four")
	public void qaAMagmaBlockTakesFourPlantingsAndNoMore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		noRandomTicks(helper);
		BlockPos magma = new BlockPos(8, 3, 5);
		helper.setBlock(magma.below(), Blocks.GLASS);
		helper.setBlock(magma, Blocks.MAGMA_BLOCK);
		Villager grower = GemGrowerCompatTests.grower(helper, new ItemStack(item("tumblestone"), 8));
		long[] fourth = {-1};
		helper.succeedWhen(() -> {
			int buds = 0;
			for (Direction d : Direction.values()) {
				if (tumblestoneGrowth(helper, magma.relative(d))) {
					buds++;
				}
			}
			helper.assertTrue(buds <= 4, "more than 4 plantings round one magma block: " + buds);
			helper.assertTrue(buds == 4, "plantings so far: " + buds + "; she says " + said(helper, grower));
			long now = helper.getTick();
			if (fourth[0] < 0) {
				fourth[0] = now;
			}
			helper.assertTrue(now >= fourth[0] + 400, "waiting to see no fifth is planted");
			Container chest = helper.getBlockEntity(CHEST);
			long left = chest.countItem(item("tumblestone")) + ModAttachments.BUILDER_BAG.getOrCreate(grower).count(item("tumblestone"));
			helper.assertTrue(left == 4, "tumblestones left: " + left);
			helper.assertTrue(helper.getBlockState(magma).is(Blocks.MAGMA_BLOCK), "the magma was broken");
		});
	}

	/**
	 * Blank TMs "up to 8" (Cobblemon 1.8): none to start, 16 glass and 16 shards in her chest. She makes them (two a
	 * craft, Cobblemon's recipe) up to 8, and 600 ticks after there are 8 (three more of her 200-tick turns) there are
	 * still 8, with glass and shards to spare. On 1.7.3 (no TMs) she has no Blank TM recipe, and the test ends there.
	 * (Starting from 7 she ends on 9: QaGemGrowerBugTests.)
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_gem_tms"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_gem_tms")
	public void qaBlankTmsStopAtEight(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		if (!PokemonFeatures.TYPE_GEMS.available()) {
			helper.assertTrue(GemGrowers.blankTmRecipe(helper.getLevel()).isEmpty(), "a Blank TM recipe without Cobblemon 1.8");
			helper.succeed();
			return;
		}
		Item tm = item("blank_tm");
		Villager grower = GemGrowerCompatTests.grower(helper, new ItemStack(Items.GLASS, 16), new ItemStack(Items.AMETHYST_SHARD, 16));
		long[] eight = {-1};
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			int tms = chest.countItem(tm);
			helper.assertTrue(tms <= GemGrowers.BLANK_TMS_KEPT, "more than 8 Blank TMs: " + tms);
			helper.assertTrue(tms == 8, "Blank TMs: " + tms + "; she says " + said(helper, grower));
			long now = helper.getTick();
			if (eight[0] < 0) {
				eight[0] = now;
			}
			helper.assertTrue(now >= eight[0] + 600, "waiting to see no ninth is made");
			helper.assertTrue(chest.countItem(Items.GLASS) < 16 || chest.countItem(Items.AMETHYST_SHARD) < 16, "the eighth was made from nothing");
			helper.assertTrue(chest.countItem(Items.GLASS) > 0 && chest.countItem(Items.AMETHYST_SHARD) > 0, "she ran out, so the cap wasn't what stopped her");
		});
	}

	/**
	 * B490: 7 Blank TMs, glass and shards in her chest: she never holds more than 8. Cobblemon's recipe makes 2 a craft, so
	 * crafting whenever the chests held fewer than 8 turned 7 into 9.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_gem_tm_bug"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_gem_tm_bug")
	public void qaSevenBlankTmsNeverBecomeNine(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		if (!PokemonFeatures.TYPE_GEMS.available()) {
			helper.succeed();
			return;
		}
		Item tm = item("blank_tm");
		GemGrowerCompatTests.grower(helper, new ItemStack(tm, 7), new ItemStack(Items.GLASS, 16), new ItemStack(Items.AMETHYST_SHARD, 16));
		helper.onEachTick(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(tm) <= GemGrowers.BLANK_TMS_KEPT, "more than 8 Blank TMs: " + chest.countItem(tm));
		});
		helper.runAtTickTime(1200, helper::succeed);
	}

	/**
	 * Trades with Cobblemon: an Apprentice sells tumblestones (three of her four Apprentice trades are tumblestones and
	 * she gets two, so at least one); an Expert sells Type Gems (both Expert trades).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAnApprenticeSellsTumblestonesAndAnExpertTypeGems(GameTestHelper helper) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 1, 0));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.GEM_GROWER).setLevel(1));
		v.setVillagerXp(1);
		int novice = v.getOffers().size();
		((VillagerAccessor) v).aliveworkplace$increaseMerchantCareer();
		List<Item> tumblestones = List.of(item("tumblestone"), item("sky_tumblestone"), item("black_tumblestone"));
		List<String> apprentice = v.getOffers().subList(novice, v.getOffers().size()).stream().map(o -> o.getResult().toString()).toList();
		helper.assertTrue(v.getOffers().subList(novice, v.getOffers().size()).stream().anyMatch(o -> tumblestones.contains(o.getResult().getItem())),
			"an Apprentice sells no tumblestones: " + apprentice);
		((VillagerAccessor) v).aliveworkplace$increaseMerchantCareer();
		int journeyman = v.getOffers().size();
		((VillagerAccessor) v).aliveworkplace$increaseMerchantCareer();
		List<String> expert = v.getOffers().subList(journeyman, v.getOffers().size()).stream()
			.map(o -> BuiltInRegistries.ITEM.getKey(o.getResult().getItem()).toString()).toList();
		// Cobblemon has had the Type Gems as held items since before 1.7, so the Expert sells them on 1.7.3 too (only the
		// Gem Blocks and their beds came with 1.8).
		helper.assertTrue(expert.size() == 2 && expert.stream().allMatch(id -> id.startsWith("cobblemon:") && id.endsWith("_gem")),
			"an Expert's trades: " + expert);
		v.discard();
		helper.succeed();
	}
}
