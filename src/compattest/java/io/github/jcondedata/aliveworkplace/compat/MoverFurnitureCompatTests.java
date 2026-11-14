package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 45.5 with the pack's furniture and storage mods: a room furnished with the pieces the mod's furnished homes use
 * (CobbleFurnies' chair, table and cabinet, Handcrafted's cupboard, shelf and side table) and the pack's modded storage
 * (a Sophisticated Storage chest and barrel), every store holding its own items put in through {@code ItemStores}, all
 * found by id, never by class. A Mover moves it a quarter turn away, through a save and reload half way down: every
 * piece arrives block for block, every store with exactly its items, nothing loose, nothing doubled. Lucky's Cozy Home
 * can't load here (stonecutter.properties.toml); its absence, and a saved move naming one of its pieces, are covered by
 * {@code MoverBelongingsGameTests}, and every piece here is looked up by id so the test also says which are missing.
 */
public class MoverFurnitureCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos MIN = new BlockPos(2, 2, 2);
	private static final BlockPos MAX = new BlockPos(8, 6, 8);
	private static final BlockPos TO = new BlockPos(16, 2, 16);
	private static final BlockPos HALL = new BlockPos(15, 2, 2);

	/** Each piece by id, where it stands (helper coordinates, inside the house) and, for a store, what it holds. */
	private record Piece(String id, BlockPos at, Item holds, int count) {
	}

	private static final List<Piece> PIECES = List.of(
		new Piece("cobblefurnies:oak_chair", new BlockPos(3, 3, 3), Items.AIR, 0),
		new Piece("cobblefurnies:oak_table", new BlockPos(4, 3, 3), Items.AIR, 0),
		new Piece("cobblefurnies:oak_cabinet", new BlockPos(6, 3, 3), Items.APPLE, 9),
		new Piece("handcrafted:oak_cupboard", new BlockPos(3, 3, 5), Items.BREAD, 14),
		new Piece("handcrafted:oak_shelf", new BlockPos(3, 3, 7), Items.BOOK, 3),
		new Piece("handcrafted:oak_side_table", new BlockPos(5, 3, 7), Items.CANDLE, 2),
		new Piece("sophisticatedstorage:chest", new BlockPos(7, 3, 5), Items.IRON_INGOT, 100),
		new Piece("sophisticatedstorage:barrel", new BlockPos(7, 3, 7), Items.EMERALD, 70));

	private static Block block(String id) {
		ResourceLocation rl = ResourceLocation.parse(id);
		return BuiltInRegistries.BLOCK.containsKey(rl) ? BuiltInRegistries.BLOCK.get(rl) : null;
	}

	private static long held(ServerLevel level, BlockPos abs, Item item) {
		return Platform.get().items().count(level, List.of(abs), item);
	}

	//$ gametest_ticks_batch AREA '400' '"moverFurniture"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "moverFurniture")
	public void furnitureAndModdedStorageArriveAsTheyWere(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<String> missing = new ArrayList<>();
		for (Piece p : PIECES) {
			if (block(p.id()) == null) {
				missing.add(p.id());
			}
		}
		helper.assertTrue(missing.isEmpty(), "pieces not installed: " + missing);
		for (int x = MIN.getX(); x <= MAX.getX(); x++) {
			for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
				for (int y = MIN.getY(); y <= MAX.getY(); y++) {
					boolean edge = x == MIN.getX() || x == MAX.getX() || z == MIN.getZ() || z == MAX.getZ();
					BlockState state = y == MIN.getY() ? Blocks.COBBLESTONE.defaultBlockState()
						: y == MAX.getY() ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: edge ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
		Map<Piece, Long> want = new LinkedHashMap<>();
		for (Piece p : PIECES) {
			BlockPos abs = helper.absolutePos(p.at());
			level.setBlock(abs, block(p.id()).defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (p.count() > 0) {
				ItemStack left = new ItemStack(p.holds(), p.count());
				while (!left.isEmpty()) {
					ItemStack part = left.split(Math.min(left.getCount(), p.holds().getDefaultMaxStackSize()));
					ItemStack back = Platform.get().items().insert(level, List.of(abs), part);
					helper.assertTrue(back.isEmpty(), p.id() + " is no store, or full: " + back);
				}
				want.put(p, held(level, abs, p.holds()));
				helper.assertTrue(want.get(p) == p.count(), p.id() + " holds " + want.get(p) + " " + p.holds());
			}
		}
		for (String id : List.of("cobblefurnies:oak_cabinet", "handcrafted:oak_cupboard")) {
			helper.assertTrue(block(id).defaultBlockState().is(Furnishings.FURNITURE_STORAGE), id + " isn't one of the furnished homes' storage pieces");
		}

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "mover_furniture_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(MIN), new Vec3i(7, 5, 7), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(MIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(id, placement, UUID.randomUUID());
		MovePlan before = MovePlan.capture(level, sites.finishedAt(level, id, helper.absolutePos(MIN)).orElseThrow()).orElseThrow();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos from = helper.absolutePos(MIN);
		BlockPos to = helper.absolutePos(TO);
		PendingMoves.get(level).put(new PendingMoves.Move(hall, from, id, to, Rotation.CLOCKWISE_90, null));
		BlockPos at = new BlockPos(12, 2, 12);
		helper.setBlock(at.north(), ModBlocks.MOVERS_CRATE);
		Villager mover = helper.spawn(EntityType.VILLAGER, at);
		mover.setNoAi(true);
		mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER));
		mover.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
			net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(at.north())));

		MoveSite site = MoveSites.get(level).work(level, hall).orElseThrow();
		BlockPos chest = helper.absolutePos(PIECES.get(6).at());
		for (int i = 0; i < 5000 && site.stage() != MoveSite.Stage.DONE && !level.getBlockState(chest).isAir(); i++) {
			MoverWork.work(level, mover, site);
		}
		helper.assertTrue(site.stage() == MoveSite.Stage.DISMANTLE, "the Sophisticated chest wasn't taken down: " + site.stage());
		CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
		site = MoveSites.get(level).forBuilding(from).orElseThrow();
		for (int i = 0; i < 5000 && site.stage() != MoveSite.Stage.DONE; i++) {
			MoverWork.work(level, mover, site);
		}
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move didn't end: " + site.stage());

		for (MovePlan.Placed p : before.placed(to, Rotation.CLOCKWISE_90)) {
			if (!MaterialRules.matches(level.getBlockState(p.pos()), p.state())) {
				helper.fail(p.pos() + " is " + level.getBlockState(p.pos()) + ", not " + p.state());
			}
		}
		for (Map.Entry<Piece, Long> e : want.entrySet()) {
			BlockPos now = before.at(e.getKey().at().subtract(MIN), to, Rotation.CLOCKWISE_90);
			helper.assertTrue(held(level, now, e.getKey().holds()) == e.getValue(),
				e.getKey().id() + " arrived with " + held(level, now, e.getKey().holds()) + " " + e.getKey().holds() + ", not " + e.getValue());
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(MIN), helper.absolutePos(MAX))) {
			if (!level.getBlockState(pos).isAir()) {
				helper.fail("the old spot still has " + level.getBlockState(pos) + " at " + pos);
			}
		}
		List<ItemEntity> loose = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 1, 0))).inflate(30));
		helper.assertTrue(loose.isEmpty(), "items dropped: " + loose);
		PendingMoves.get(level).remove(from);
		sites.finishedIn(level).stream().filter(f -> f.structure().equals(id)).forEach(f -> sites.forgetFinished(f.placement()));
		helper.succeed();
	}
}
