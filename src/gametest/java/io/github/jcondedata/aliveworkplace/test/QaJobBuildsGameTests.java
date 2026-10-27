package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane d, 2026-10-27) of ROADMAP 28.13, the builds for the new jobs, without Cobblemon (the Gem Grotto is the one in
 * the table without it), from the spec: "sold by their job's Journeyman, each with its job block taking a villager with
 * the item". Not covered before: a Journeyman Gem Grower's own offers hold the Gem Grotto's blueprint (and a Novice's
 * and an Apprentice's don't); in both Gem Grotto tiers as drawn, the stonecutter takes a jobless villager the player
 * sneak-clicks with an amethyst shard (the Gem Grower, at that stonecutter). With Cobblemon: {@code QaJobBuildsCompatTests}.
 */
public class QaJobBuildsGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";

	/** The blueprints among {@code v}'s offers at its current level. */
	private static List<String> blueprintsSold(Villager v) {
		List<String> sold = new ArrayList<>();
		for (MerchantOffer offer : v.getOffers()) {
			BlueprintItem.data(offer.getResult()).ifPresent(d -> sold.add(d.structure().toString()
				+ " for " + offer.getBaseCostA().getCount() + " " + offer.getBaseCostA().getItem()));
		}
		return sold;
	}

	/** A Gem Grower at {@code level} (1 Novice … 3 Journeyman), her offers made at that level. */
	private static Villager grower(GameTestHelper helper, int level, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.GEM_GROWER).setLevel(level));
		return v;
	}

	/** A Journeyman Gem Grower sells the Gem Grotto's blueprint (for emeralds); a Novice and an Apprentice don't yet. */
	//$ gametest_ticks_batch AREA '100' '"qaJobBuildsTrade"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaJobBuildsTrade")
	public void qaAJourneymanGemGrowerSellsTheGemGrotto(GameTestHelper helper) {
		Leftovers.clear(helper);
		String grotto = StarterBlueprints.GEM_GROTTO.id().toString();
		Villager novice = grower(helper, 1, new BlockPos(1, 2, 1));
		Villager apprentice = grower(helper, 2, new BlockPos(3, 2, 1));
		Villager journeyman = grower(helper, 3, new BlockPos(5, 2, 1));
		helper.assertTrue(blueprintsSold(novice).isEmpty(), "a Novice sells " + blueprintsSold(novice));
		helper.assertTrue(blueprintsSold(apprentice).isEmpty(), "an Apprentice sells " + blueprintsSold(apprentice));
		List<String> sold = blueprintsSold(journeyman);
		helper.assertTrue(sold.size() == 1 && sold.get(0).startsWith(grotto + " for ") && sold.get(0).endsWith("emerald"),
			"a Journeyman Gem Grower's blueprints: " + sold);
		helper.assertTrue(journeyman.getOffers().stream().noneMatch(o -> o.getResult().isEmpty()), "an empty offer");
		novice.discard();
		apprentice.discard();
		journeyman.discard();
		helper.succeed();
	}

	/** Places every block of {@code id} as drawn, its y = 0 on the area's floor layer at {@code origin}; returns the origin (absolute). */
	static BlockPos place(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id())
			.orElseThrow(() -> new GameTestAssertException("no blueprint " + entry.id()));
		BlockPos at = helper.absolutePos(origin);
		for (Blueprint.Entry e : blueprint.blocks()) {
			level.setBlock(at.offset(e.pos()), e.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		return at;
	}

	/** The nearest spot within 3 blocks of {@code job} a villager can stand on (two clear blocks over a solid one). */
	static BlockPos standingSpot(ServerLevel level, BlockPos job) {
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(job.offset(-3, -1, -3), job.offset(3, 1, 3))) {
			BlockState feet = level.getBlockState(p);
			BlockState head = level.getBlockState(p.above());
			BlockState below = level.getBlockState(p.below());
			if (feet.getCollisionShape(level, p).isEmpty() && head.getCollisionShape(level, p.above()).isEmpty()
				&& feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
				&& below.isFaceSturdy(level, p.below(), Direction.UP) && !p.equals(job)
				&& (best == null || p.distSqr(job) < best.distSqr(job))) {
				best = p.immutable();
			}
		}
		if (best == null) {
			throw new GameTestAssertException("nowhere to stand within 3 blocks of the job block at " + job.toShortString());
		}
		return best;
	}

	/** Clears the box {@code size} at {@code at} back to air (the floor layer back to the area's floor block). */
	static void clear(ServerLevel level, BlockPos at, net.minecraft.core.Vec3i size, BlockState floor) {
		for (BlockPos p : BlockPos.betweenClosed(at, at.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			level.setBlock(p, p.getY() == at.getY() ? floor : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
	}

	/**
	 * Each Gem Grotto tier as drawn: its stonecutter (by the door) is a job site, and a jobless villager standing by it,
	 * sneak-clicked by a player with an amethyst shard, becomes the Gem Grower working at that very stonecutter.
	 */
	//$ gametest_ticks_batch HUGE_AREA '300' '"qaJobBuildsGrotto"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 300, batch = "qaJobBuildsGrotto")
	public void qaEachGemGrottosStonecutterTakesAGemGrower(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockState floor = helper.getBlockState(new BlockPos(2, 1, 2));
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaLuxuryJobsGameTests.listener(helper, seen);
			for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.GEM_GROTTO, StarterBlueprints.GEM_GROTTO_2)) {
				BlockPos at = place(helper, entry, new BlockPos(2, 1, 1));
				BlockPos cutter = at.offset(4, 1, 2);
				helper.assertTrue(level.getBlockState(cutter).is(Blocks.STONECUTTER), entry.id() + ": no stonecutter by the door: " + level.getBlockState(cutter));
				helper.assertTrue(level.getPoiManager().getType(cutter).isPresent(), entry.id() + ": the stonecutter is no job site");
				BlockPos spot = standingSpot(level, cutter);
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 2, 0));
				v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
				v.setNoAi(true);
				v.setCustomName(Component.literal("Wren"));
				QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD, 2));
				seen.clear();
				QaLuxuryJobsGameTests.click(player, v, true);
				VillagerProfession job = v.getVillagerData().getProfession();
				helper.assertTrue(job == ModVillagers.GEM_GROWER, entry.id() + ": the shard made the villager at " + spot.subtract(at).toShortString()
					+ " a " + job + " (standing at " + v.blockPosition().subtract(at).toShortString() + ", POIs near: "
					+ level.getPoiManager().getInRange(t -> true, v.blockPosition(), 5, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
						.map(r -> r.getPos().subtract(at).toShortString() + "=" + r.getPoiType().unwrapKey().map(k -> k.location().getPath()).orElse("?") + "/" + r.getFreeTickets()).toList()
					+ ", cutter tickets " + level.getPoiManager().getFreeTickets(cutter) + "); the player read " + seen.stream().map(QaLuxuryJobsGameTests::english).toList());
				helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(cutter::equals).isPresent(),
					entry.id() + ": she doesn't work at the grotto's stonecutter: " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE));
				v.discard();
				clear(level, at, entry.size(), floor);
			}
			helper.succeed();
		});
	}
}
