package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA lane D, 2026-11-09, B1083 from docs/wiki/village-fire.md: "an item frame or painting that fell when its wall burned
 * is hung again with the wall, from one in the builder's chests; one you took down from a wall that still stands stays
 * down" (32.16a: a fire never destroys a build for good). The fire's work is done by hand here, as VillageFire records it:
 * the burned block is gone and on the threats' repair list, and what hung on it fell.
 */
public class QaFireFrames1109dGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	/** Two roof planks with a frame under each, and the painting's post. */
	static final BlockPos ROOF_A = HUT.offset(1, 2, 1);
	private static final BlockPos ROOF_B = HUT.offset(3, 2, 3);
	private static final BlockPos POST = HUT.offset(0, 1, 0);
	private static final BlockPos CHEST = new BlockPos(2, 2, 18);

	record Scene(ServerLevel level, Villager builder, Container chest) {
	}

	/**
	 * A 5x5 hut (log posts, plank roof, plank floor) with item frames under ROOF_A and ROOF_B and a painting on the POST,
	 * recorded as the builder's finished build with them; the builder's chest holds wood and {@code frames} item frames
	 * and {@code paintings} paintings.
	 */
	static Scene scene(GameTestHelper helper, int frames, int paintings) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(HUT.offset(x, -1, z), Blocks.OAK_PLANKS);
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_PLANKS);
			}
		}
		hangFrame(helper, ROOF_A.below());
		hangFrame(helper, ROOF_B.below());
		Painting painting = new Painting(level, helper.absolutePos(POST.east()), Direction.EAST,
			level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(PaintingVariants.KEBAB));
		helper.assertTrue(painting.survives(), "setup: the painting can't hang on the post");
		level.addFreshEntity(painting);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_fireframes_1109d_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT.below()), new Vec3i(5, 4, 5), true, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT.below()), Rotation.NONE, Mirror.NONE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 32));
		chest.setItem(1, new ItemStack(Items.OAK_LOG, 16));
		if (frames > 0) {
			chest.setItem(2, new ItemStack(Items.ITEM_FRAME, frames));
		}
		if (paintings > 0) {
			chest.setItem(3, new ItemStack(Items.PAINTING, paintings));
		}
		BlockPos bench = new BlockPos(1, 2, 17);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 17));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSiteManager.get(level).recordFinished(id, placement, builder.getUUID());
		VillageFire.forgetBuilds();
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
			BuildSiteManager.get(level).forgetFinished(placement);
			ThreatData data = ThreatData.get(level);
			for (BlockPos p : List.copyOf(data.damaged())) {
				if (helper.getBounds().inflate(1).contains(p.getCenter())) {
					data.repaired(p);
				}
			}
		});
		return new Scene(level, builder, chest);
	}

	private static void hangFrame(GameTestHelper helper, BlockPos at) {
		ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(at), Direction.DOWN);
		helper.assertTrue(frame.survives(), "setup: the frame at " + at + " can't hang under the roof");
		helper.getLevel().addFreshEntity(frame);
	}

	/** The fire's work: the block burned away and is on the threats' list; what hung on it fell. */
	static void burn(GameTestHelper helper, BlockPos block, BlockPos hungAt) {
		helper.setBlock(block, Blocks.AIR);
		ThreatData.get(helper.getLevel()).damaged(helper.absolutePos(block));
		for (HangingEntity h : hanging(helper, hungAt, HangingEntity.class)) {
			h.discard();
		}
	}

	static <T extends HangingEntity> List<T> hanging(GameTestHelper helper, BlockPos at, Class<T> kind) {
		BlockPos abs = helper.absolutePos(at);
		return helper.getLevel().getEntitiesOfClass(kind, new AABB(abs).inflate(0.3), h -> h.isAlive() && h.getPos().equals(abs));
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

	/** The builder looks at his builds now and every few seconds, so the repair needn't wait for his minute. */
	private static void looks(GameTestHelper helper, Scene scene) {
		helper.onEachTick(() -> {
			if (helper.getTick() % 60 == 5) {
				Upkeep.look(scene.level(), scene.builder());
			}
		});
	}

	/**
	 * The fire burns ROOF_A, frame A falls; the player had taken frame B down from its roof plank, which still stands.
	 * The repair puts ROOF_A back and hangs frame A from the chest; frame B stays down; one item frame is used, not two.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaFireFrames1109dTaken"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaFireFrames1109dTaken")
	public void aFrameAPlayerTookFromAStandingRoofStaysDownWhenTheBurnedOneGoesBackUp(GameTestHelper helper) {
		Scene scene = scene(helper, 4, 0);
		for (ItemFrame f : hanging(helper, ROOF_B.below(), ItemFrame.class)) {
			f.discard(); // the player takes it down
		}
		burn(helper, ROOF_A, ROOF_A.below());
		looks(helper, scene);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(ROOF_A).is(Blocks.OAK_PLANKS), "(the builder hasn't put the burned plank back yet)");
			helper.assertTrue(!hanging(helper, ROOF_A.below(), ItemFrame.class).isEmpty(), "(the frame under the burned plank isn't back yet)");
			helper.assertTrue(hanging(helper, ROOF_B.below(), ItemFrame.class).isEmpty(),
				"the frame a player took down from a roof plank that never burned was hung again with the burned one's repair");
			helper.assertTrue(hanging(helper, ROOF_A.below(), ItemFrame.class).size() == 1, "two frames hang under the repaired plank");
			helper.assertTrue(count(scene.chest(), Items.ITEM_FRAME) == 3,
				"the repair used " + (4 - count(scene.chest(), Items.ITEM_FRAME)) + " item frames from the chest, not 1");
		});
	}

	/** "An item frame or painting": the post a painting hung on burns; it goes back up with the post, from the chest. */
	//$ gametest_ticks_batch AREA '3000' '"qaFireFrames1109dPainting"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaFireFrames1109dPainting")
	public void aPaintingThatFellWithItsBurnedPostIsHungAgain(GameTestHelper helper) {
		Scene scene = scene(helper, 0, 2);
		burn(helper, POST, POST.east());
		looks(helper, scene);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(POST).is(Blocks.OAK_LOG), "(the builder hasn't put the burned post back yet)");
			helper.assertTrue(!hanging(helper, POST.east(), Painting.class).isEmpty(),
				"the builder put the burned post back but not the painting that hung on it (paintings in the chest: "
					+ count(scene.chest(), Items.PAINTING) + ")");
			helper.assertTrue(count(scene.chest(), Items.PAINTING) == 1, "the painting came from nowhere or took two");
			helper.assertTrue(hanging(helper, ROOF_A.below(), ItemFrame.class).size() == 1 && hanging(helper, ROOF_B.below(), ItemFrame.class).size() == 1,
				"the frames that never fell were doubled or lost: under A " + hanging(helper, ROOF_A.below(), ItemFrame.class).size() + ", under B "
					+ hanging(helper, ROOF_B.below(), ItemFrame.class).size() + "; all frames in the hut: "
					+ helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(HUT.below())).expandTowards(5, 4, 5), ItemFrame::isAlive)
						.stream().map(f -> f.getPos() + " " + f.getDirection()).toList());
		});
	}

	/**
	 * With no item frame in any chest, the burned plank still goes back (a fire never destroys a build for good) and no
	 * frame is conjured; the repair doesn't hang on the missing frame.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaFireFrames1109dNone"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaFireFrames1109dNone")
	public void withNoFrameInTheChestThePlankStillGoesBack(GameTestHelper helper) {
		Scene scene = scene(helper, 0, 0);
		burn(helper, ROOF_A, ROOF_A.below());
		looks(helper, scene);
		long[] back = {-1};
		helper.onEachTick(() -> {
			if (back[0] < 0 && helper.getBlockState(ROOF_A).is(Blocks.OAK_PLANKS)) {
				back[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0] >= 0, "(the builder hasn't put the burned plank back yet)");
			helper.assertTrue(helper.getTick() > back[0] + 200, "(watching a while after the plank came back)");
			helper.assertTrue(hanging(helper, ROOF_A.below(), ItemFrame.class).isEmpty(), "a frame was hung with no item frame in any chest");
			helper.assertTrue(ThreatData.get(scene.level()).damaged().stream().noneMatch(p -> p.equals(helper.absolutePos(ROOF_A))),
				"the plank is back but still on the threats' repair list");
		});
	}
}
