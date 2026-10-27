package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane d, 2026-10-27) of ROADMAP 28.13 with the real Cobblemon, from the spec: the five builds are "sold by their
 * job's Journeyman, each with its job block taking a villager with the item". Not covered before: each job's Journeyman
 * (Camp Cook, Berry Breeder, Habitat Keeper, Daycare Keeper, Gem Grower) has their build's blueprint among their own
 * offers, and a Novice or an Apprentice of that job doesn't; in each of the ten builds as drawn (both tiers), a jobless
 * villager standing by its job block, sneak-clicked with the job's item (Hearty Grains, an Oran Berry, a honey bottle,
 * an egg, an amethyst shard), takes that job at that very block.
 */
public class QaJobBuildsCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final String AREA = "aliveworkplace_compat:build_area";

	private record Job(VillagerProfession profession, StarterBlueprints.Entry build, StarterBlueprints.Entry upgrade, BlockPos jobBlock,
		String block, Item item) {
	}

	private static Item item(String id) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
		if (item == Items.AIR) {
			throw new GameTestAssertException("no item " + id);
		}
		return item;
	}

	private static List<Job> jobs() {
		return List.of(
			new Job(ModVillagers.CAMP_COOK, StarterBlueprints.CAMP_KITCHEN, StarterBlueprints.CAMP_KITCHEN_2, new BlockPos(6, 1, 5),
				"cobblemon:campfire", item("cobblemon:hearty_grains")),
			new Job(ModVillagers.BERRY_BREEDER, StarterBlueprints.BERRY_NURSERY, StarterBlueprints.BERRY_NURSERY_2, new BlockPos(5, 1, 8),
				"minecraft:composter", item("cobblemon:oran_berry")),
			new Job(ModVillagers.HABITAT_KEEPER, StarterBlueprints.HABITAT_GARDEN, StarterBlueprints.HABITAT_GARDEN_2, new BlockPos(14, 1, 7),
				"cobblemon:pasture", Items.HONEY_BOTTLE),
			new Job(ModVillagers.DAYCARE_KEEPER, StarterBlueprints.DAYCARE, StarterBlueprints.DAYCARE_2, new BlockPos(7, 1, 12),
				"cobblemon:pasture", Items.EGG),
			new Job(ModVillagers.GEM_GROWER, StarterBlueprints.GEM_GROTTO, StarterBlueprints.GEM_GROTTO_2, new BlockPos(4, 1, 2),
				"minecraft:stonecutter", Items.AMETHYST_SHARD));
	}

	/** The blueprints among {@code v}'s offers at its current level. */
	private static List<ResourceLocation> blueprintsSold(Villager v) {
		List<ResourceLocation> sold = new ArrayList<>();
		for (MerchantOffer offer : v.getOffers()) {
			BlueprintItem.data(offer.getResult()).ifPresent(d -> sold.add(d.structure()));
			if (BlueprintItem.data(offer.getResult()).isPresent() && !offer.getBaseCostA().is(Items.EMERALD)) {
				throw new GameTestAssertException(v.getVillagerData().getProfession() + " sells a blueprint for " + offer.getBaseCostA());
			}
		}
		return sold;
	}

	/** Each job's Journeyman sells their build's blueprint (for emeralds); a Novice and an Apprentice of the job don't. */
	//$ gametest_ticks_batch AREA '100' '"qaJobBuildsJourneymen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaJobBuildsJourneymen")
	public void qaEachJobsJourneymanSellsTheirBuild(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Job job : jobs()) {
			for (int level = 1; level <= 3; level++) {
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1 + level * 2, 2, 2));
				v.setNoAi(true);
				v.setVillagerData(v.getVillagerData().setProfession(job.profession()).setLevel(level));
				List<ResourceLocation> sold = blueprintsSold(v);
				List<ResourceLocation> want = level == 3 ? List.of(job.build().id()) : List.of();
				if (!sold.equals(want)) {
					wrong.add(job.profession().name() + " at level " + level + " sells " + sold + ", not " + want);
				}
				v.discard();
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * In each of the ten builds as drawn, a jobless villager by its job block, sneak-clicked with the job's item, takes the
	 * job at that block.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"qaJobBuildsJobBlocks"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "qaJobBuildsJobBlocks")
	public void qaEachBuildsJobBlockTakesAVillagerWithItsItem(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockState floor = helper.getBlockState(new BlockPos(2, 1, 2));
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			List<String> wrong = new ArrayList<>();
			try {
				for (Job job : jobs()) {
					for (StarterBlueprints.Entry entry : List.of(job.build(), job.upgrade())) {
						BlockPos at = place(helper, entry, new BlockPos(1, 1, 1));
						BlockPos block = at.offset(job.jobBlock());
						String there = BuiltInRegistries.BLOCK.getKey(level.getBlockState(block).getBlock()).toString();
						if (!there.equals(job.block()) || level.getPoiManager().getType(block).isEmpty()) {
							wrong.add(entry.id() + ": " + there + " at " + job.jobBlock().toShortString() + " (a job site: "
								+ level.getPoiManager().getType(block).isPresent() + ")");
						} else {
							BlockPos spot = standingSpot(level, block);
							Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 2, 0));
							v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
							v.setNoAi(true);
							v.setCustomName(Component.literal("Wren"));
							ItemStack held = new ItemStack(job.item(), 2);
							player.setItemInHand(InteractionHand.MAIN_HAND, held);
							player.setShiftKeyDown(true);
							InteractionResult r = Stations.choose(player, v, held);
							VillagerProfession got = v.getVillagerData().getProfession();
							boolean atBlock = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(block::equals).isPresent();
							if (got != job.profession() || !atBlock) {
								wrong.add(entry.id() + ": " + job.item() + " by the " + job.block() + " (villager at " + spot.subtract(at).toShortString()
									+ ") made a " + got.name() + (atBlock ? "" : ", not working at the build's job block") + " (" + r + ")");
							}
							v.discard();
						}
						clear(level, at, entry.size(), floor);
					}
				}
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		});
	}

	private static BlockPos place(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = StarterBlueprints.withFeatures(BlueprintLibrary.get(level, entry.id())
			.orElseThrow(() -> new GameTestAssertException("no blueprint " + entry.id())));
		BlockPos at = helper.absolutePos(origin);
		for (Blueprint.Entry e : blueprint.blocks()) {
			level.setBlock(at.offset(e.pos()), e.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		return at;
	}

	private static BlockPos standingSpot(ServerLevel level, BlockPos job) {
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

	private static void clear(ServerLevel level, BlockPos at, Vec3i size, BlockState floor) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(size.getX(), size.getY(), size.getZ()))) {
			level.setBlock(p, p.getY() == at.getY() ? floor : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
	}
}
