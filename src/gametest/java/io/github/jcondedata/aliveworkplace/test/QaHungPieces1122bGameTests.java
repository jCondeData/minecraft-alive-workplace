package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA lane B (2026-11-22), from the builder's spec ("DONE with every block as drawn"; steps that can't be done are
 * deferred once, then skipped): every piece a shipped blueprint hangs on a wall or a ceiling, or stands on a block
 * (ladders, wall and floor torches, tripwire hooks, wall and hanging signs, banners, buttons and levers, lanterns, the
 * lower half of doors), has something to hold it in the drawing itself. B1451 (Carpenter's Workshop), B1452 (Farmstead)
 * and B1453 (village Ball Workshops) were each one such piece facing a glass pane, found one build at a time; this looks
 * at every build at once. A piece that can't stand is never placed by a builder (deferred, then skipped), and a placed
 * copy (a village piece, a Settler's camp) drops it on the first block update.
 *
 * <p>The drawing is read on its own: the row under its lowest layer counts as ground (stone), everything else outside
 * it as air (the ground round a plot is whatever the player has there). Each piece is asked {@code canSurvive} against
 * that, the same check the builder makes before it places a block ({@code MaterialRules}).
 */
public class QaHungPieces1122bGameTests implements FabricGameTest {

	/** Whether {@code state} is one of the pieces this check asks about (it needs a block to hang on or stand on). */
	static boolean needsHold(BlockState state) {
		Block b = state.getBlock();
		if (b instanceof GrindstoneBlock) {
			return false; // stands anywhere
		}
		if (b instanceof DoorBlock) {
			return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
		}
		return b instanceof LadderBlock || b instanceof TorchBlock || b instanceof TripWireHookBlock || b instanceof WallSignBlock
			|| b instanceof StandingSignBlock || b instanceof WallHangingSignBlock || b instanceof CeilingHangingSignBlock
			|| b instanceof WallBannerBlock || b instanceof BannerBlock || b instanceof FaceAttachedHorizontalDirectionalBlock
			|| b instanceof LanternBlock;
	}

	/** A world that holds only {@code blocks}: stone under the drawing's lowest layer, air everywhere else. */
	static LevelReader drawingOnly(Map<BlockPos, BlockState> blocks, int minY, LevelReader real) {
		InvocationHandler handler = (proxy, method, args) -> {
			String name = method.getName();
			int n = args == null ? 0 : args.length;
			switch (name) {
				case "getBlockState":
					if (n == 1) {
						BlockPos p = (BlockPos) args[0];
						BlockState s = blocks.get(p);
						return s != null ? s : p.getY() < minY ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
					}
					break;
				case "getFluidState":
					if (n == 1) {
						return ((BlockState) ((LevelReader) proxy).getBlockState((BlockPos) args[0])).getFluidState();
					}
					break;
				case "getBlockEntity":
					if (n == 1) {
						return null;
					}
					break;
				case "getHeight":
					if (n == 0) {
						return real.getHeight();
					}
					break;
				case "getMinBuildHeight":
					return real.getMinBuildHeight();
				case "isClientSide":
					return false;
				case "enabledFeatures":
					return real.enabledFeatures();
				case "registryAccess":
					return real.registryAccess();
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "toString":
					return "drawing-only level";
				default:
					break;
			}
			if (method.isDefault()) {
				return InvocationHandler.invokeDefault(proxy, method, args);
			}
			throw new UnsupportedOperationException(name);
		};
		return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[] {LevelReader.class}, handler);
	}

	/** Every piece in {@code blueprint} that needs a hold and has none in the drawing; {@code unknown} counts pieces the check couldn't ask. */
	static List<String> unheld(Blueprint blueprint, LevelReader real, int[] unknown) {
		Map<BlockPos, BlockState> at = new HashMap<>();
		int minY = Integer.MAX_VALUE;
		for (Blueprint.Entry e : blueprint.blocks()) {
			if (!e.state().isAir()) {
				at.put(e.pos(), e.state());
				minY = Math.min(minY, e.pos().getY());
			}
		}
		LevelReader level = drawingOnly(at, minY, real);
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : at.entrySet()) {
			BlockState state = e.getValue();
			if (!needsHold(state)) {
				continue;
			}
			try {
				if (!state.canSurvive(level, e.getKey())) {
					BlockPos p = e.getKey();
					out.add(blueprint.id().getPath() + ": " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()
						+ " at " + p.getX() + ", " + p.getY() + ", " + p.getZ() + describeFacing(state));
				}
			} catch (UnsupportedOperationException ex) {
				unknown[0]++;
			}
		}
		return out;
	}

	private static String describeFacing(BlockState state) {
		for (var prop : state.getProperties()) {
			if (prop.getName().equals("facing") || prop.getName().equals("face") || prop.getName().equals("hanging")) {
				return " (" + prop.getName() + " " + state.getValue(prop) + ")";
			}
		}
		return "";
	}

	/**
	 * The mod's own blueprints a builder builds or the world sets down: everything in our namespace but the modded
	 * library's copies and the builds of Cobblemon's or Mega Showdown's blocks while those mods are absent (their blocks
	 * load as air here, so a lantern under a Cobblemon log would look unheld), and a Wonder's stage parts (each stands on
	 * the stages before it, so a part alone has pieces on blocks another part lays).
	 */
	static List<ResourceLocation> shipped(MinecraftServer server) {
		boolean cobblemon = io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon");
		boolean mega = io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("mega_showdown");
		return BlueprintLibrary.list(server, true).stream()
			.filter(id -> id.getNamespace().equals(AliveWorkplace.MOD_ID))
			.filter(id -> !id.getPath().startsWith("modded/") && !id.getPath().startsWith("compat_test/") && !id.getPath().startsWith("wonder/"))
			.filter(id -> cobblemon || !in(StarterBlueprints.COBBLEMON_ONLY, id) && !in(StarterBlueprints.COBBLEMON_WORKPLACES, id))
			.filter(id -> mega || !in(StarterBlueprints.MEGA_ONLY, id))
			.toList();
	}

	private static boolean in(List<StarterBlueprints.Entry> entries, ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.base(id);
		return entries.stream().anyMatch(e -> e.id().equals(id) || e.id().equals(base));
	}

	/** Self-check of the check: a ladder on a glass pane (B1451's kind) is refused, one on planks passes, a torch on the ground passes. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheHoldCheckTellsAPaneFromAWall(GameTestHelper helper) {
		LevelReader real = helper.getLevel();
		Map<BlockPos, BlockState> pane = new HashMap<>();
		pane.put(new BlockPos(0, 1, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, net.minecraft.core.Direction.NORTH));
		pane.put(new BlockPos(0, 1, 1), Blocks.GLASS_PANE.defaultBlockState());
		pane.put(new BlockPos(0, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
		LevelReader onPane = drawingOnly(pane, 0, real);
		helper.assertFalse(pane.get(new BlockPos(0, 1, 0)).canSurvive(onPane, new BlockPos(0, 1, 0)), "a ladder on a glass pane passed the check");
		pane.put(new BlockPos(0, 1, 1), Blocks.OAK_PLANKS.defaultBlockState());
		LevelReader onPlanks = drawingOnly(pane, 0, real);
		helper.assertTrue(pane.get(new BlockPos(0, 1, 0)).canSurvive(onPlanks, new BlockPos(0, 1, 0)), "a ladder on planks failed the check");
		Map<BlockPos, BlockState> torch = Map.of(new BlockPos(0, 0, 0), Blocks.TORCH.defaultBlockState());
		helper.assertTrue(Blocks.TORCH.defaultBlockState().canSurvive(drawingOnly(torch, 0, real), new BlockPos(0, 0, 0)), "a torch on the ground under the drawing failed the check");
		helper.succeed();
	}

	/** Every shipped blueprint: every hung or standing piece has a hold in the drawing. */
	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '400'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
	public void qaEveryHungPieceOfEveryShippedBlueprintHasAHold(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> problems = new ArrayList<>();
		int[] unknown = {0};
		int checked = 0;
		for (ResourceLocation id : shipped(server)) {
			Blueprint blueprint = BlueprintLibrary.get(server, id).orElse(null);
			if (blueprint == null) {
				continue;
			}
			checked++;
			problems.addAll(unheld(blueprint, helper.getLevel(), unknown));
		}
		helper.assertTrue(checked > 300, "only " + checked + " shipped blueprints were found");
		helper.assertTrue(unknown[0] == 0, unknown[0] + " piece(s) couldn't be checked against the drawing alone");
		helper.assertTrue(problems.isEmpty(), problems.size() + " piece(s) with nothing to hold them: "
			+ String.join("; ", problems.subList(0, Math.min(10, problems.size()))));
		helper.succeed();
	}
}
