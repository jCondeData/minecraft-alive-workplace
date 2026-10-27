package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-10-27) of ROADMAP 28.11, the Gem Grower, hiring her through the player's own click (the use-entity
 * event, not {@code Stations.choose}), from the spec: "stand a villager by a stonecutter and sneak-right-click them with
 * an amethyst shard (masons keep the stonecutter's own job)". Not covered before: the real sneak-click hires her at that
 * stonecutter and tells the player so, a plain right-click with a shard hires nobody; a shard with no stonecutter near
 * says which block the job needs; a stonecutter a Mason works at isn't taken from him by a shard, and the player is told
 * it's taken. Each sentence is checked in English with its real arguments.
 */
public class QaGemGrowerHireGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos CUTTER = new BlockPos(2, 2, 2);
	private static final BlockPos STANDING = new BlockPos(3, 2, 2);
	private static final BlockPos BESIDE = new BlockPos(2, 2, 3);

	private static Villager jobless(GameTestHelper helper, BlockPos at, String called) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(called));
		return v;
	}

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	private static boolean worksAt(GameTestHelper helper, Villager v, BlockPos rel) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(rel)::equals).isPresent();
	}

	private static Component said(List<Component> seen, String key) {
		return seen.stream().filter(c -> QaLuxuryJobsGameTests.key(c).equals(key)).findFirst().orElse(null);
	}

	private static List<String> keys(List<Component> seen) {
		return seen.stream().map(QaLuxuryJobsGameTests::key).toList();
	}

	/**
	 * By a free stonecutter: a plain right-click with a shard hires nobody; the sneak-right-click makes her the Gem Grower
	 * at that stonecutter, takes no shard for it, and the player reads "Wren took the Gem Grower job at the Stonecutter."
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGemHireClick"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGemHireClick")
	public void qaASneakClickWithAShardHiresHerAPlainClickDoesnt(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaLuxuryJobsGameTests.listener(helper, seen);
			Villager wren = jobless(helper, STANDING, "Wren");
			QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD, 3));
			QaLuxuryJobsGameTests.click(player, wren, false);
			helper.assertTrue(job(wren) == VillagerProfession.NONE, "a plain right-click with a shard made her a " + job(wren));
			seen.clear();
			QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD, 3));
			QaLuxuryJobsGameTests.click(player, wren, true);
			helper.assertTrue(job(wren) == ModVillagers.GEM_GROWER && GemGrowers.isGrower(wren), "the sneak-click didn't hire her: " + job(wren));
			helper.assertTrue(worksAt(helper, wren, CUTTER), "she doesn't work at the stonecutter she stood by");
			Component chosen = said(seen, "message.aliveworkplace.job.chosen");
			helper.assertTrue(chosen != null, "the player wasn't told: " + keys(seen));
			Object[] args = ((TranslatableContents) chosen.getContents()).getArgs();
			helper.assertTrue(args.length == 3 && args[2] instanceof Component b && b.getContents() instanceof TranslatableContents t
				&& t.getKey().equals(Blocks.STONECUTTER.getDescriptionId()), "the block named isn't the stonecutter: " + chosen);
			String english = QaLuxuryJobsGameTests.english(chosen);
			helper.assertTrue(english.startsWith("Wren took the Gem Grower job at the ") && english.endsWith(".") && !english.contains("%"),
				"\"" + english + "\"");
			helper.succeed();
		});
	}

	/** No stonecutter within reach: the shard hires nobody, and the player is told the Gem Grower job needs a stonecutter. */
	//$ gametest_ticks_batch AREA '200' '"qaGemHireNone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGemHireNone")
	public void qaAShardWithNoStonecutterNearSaysWhichBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaLuxuryJobsGameTests.listener(helper, seen);
			Villager wren = jobless(helper, STANDING, "Wren");
			QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD, 3));
			QaLuxuryJobsGameTests.click(player, wren, true);
			helper.assertTrue(job(wren) == VillagerProfession.NONE, "a shard with no stonecutter near made her a " + job(wren));
			Component needs = said(seen, "message.aliveworkplace.job.needs_station");
			helper.assertTrue(needs != null, "the player wasn't told which block: " + keys(seen));
			Object[] args = ((TranslatableContents) needs.getContents()).getArgs();
			helper.assertTrue(args.length == 2 && args[1] instanceof Component b && b.getContents() instanceof TranslatableContents t
				&& t.getKey().equals(Blocks.STONECUTTER.getDescriptionId()), "the block named isn't the stonecutter: " + needs);
			String english = QaLuxuryJobsGameTests.english(needs);
			helper.assertTrue(english.startsWith("The Gem Grower job needs a ")
				&& english.endsWith(": stand them next to one, then hand them this again.") && !english.contains("%"), "\"" + english + "\"");
			helper.assertTrue(player.getMainHandItem().is(Items.AMETHYST_SHARD) && player.getMainHandItem().getCount() == 3, "the shard was used up");
			helper.succeed();
		});
	}

	/**
	 * "Masons keep the stonecutter's own job": a Mason works at the only stonecutter; a jobless villager beside it
	 * sneak-clicked with a shard takes no job, the player is told someone already works at each one, and the Mason keeps
	 * his job and his stonecutter.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGemHireMason"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGemHireMason")
	public void qaAMasonsStonecutterIsntTakenByAShard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		helper.runAfterDelay(2, () -> {
			Villager mason = jobless(helper, STANDING, "Bram");
			Jobs.employ(helper.getLevel(), mason, helper.absolutePos(CUTTER), PoiTypes.MASON, VillagerProfession.MASON);
			helper.assertTrue(job(mason) == VillagerProfession.MASON && worksAt(helper, mason, CUTTER), "setup: no Mason at the stonecutter");
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaLuxuryJobsGameTests.listener(helper, seen);
			Villager wren = jobless(helper, BESIDE, "Wren");
			QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD, 3));
			QaLuxuryJobsGameTests.click(player, wren, true);
			helper.assertTrue(job(wren) == VillagerProfession.NONE, "a villager by the Mason's stonecutter became a " + job(wren));
			Component taken = said(seen, "message.aliveworkplace.job.station_taken");
			helper.assertTrue(taken != null, "the player wasn't told the stonecutter is taken: " + keys(seen));
			String english = QaLuxuryJobsGameTests.english(taken);
			helper.assertTrue(english.startsWith("The Gem Grower job needs a free ")
				&& english.endsWith(": someone already works at each one here.") && !english.contains("%"), "\"" + english + "\"");
			helper.assertTrue(job(mason) == VillagerProfession.MASON && worksAt(helper, mason, CUTTER), "the Mason lost his job or his stonecutter: " + job(mason));
			helper.succeed();
		});
	}
}
