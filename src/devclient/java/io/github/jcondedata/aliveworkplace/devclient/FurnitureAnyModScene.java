package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.FurnitureStock;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureEquivalents;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=furniture_any_mod (ROADMAP 37.13), filmed with -Pfurniture=true -Pcobblemon=true: two Starter Cottage IIs planned
 * the same way (Handcrafted first in {@code furnitureMods}), each with a Builder's Bench and a chest of chairs outside. The
 * left chest holds Handcrafted's chairs, the right one only CobbleFurnies' of the same wood: the Builder's furniture
 * follows its chest ({@link FurnitureStock#restock}), so the right cottage is built with CobbleFurnies' chairs in the same
 * places, facing the same way, and every other piece as planned. Checks exactly that.
 */
final class FurnitureAnyModScene {
	private static final ResourceLocation COTTAGE = StarterBlueprints.STARTER_COTTAGE_2.id();
	private static final BlockPos LEFT = new BlockPos(0, -60, 0);
	private static final BlockPos RIGHT = new BlockPos(24, -60, 0);
	/** The bench and chest, relative to a cottage's origin: outside its front corner. */
	private static final BlockPos BENCH = new BlockPos(-3, 0, -3);
	private static final BlockPos CHEST = new BlockPos(-2, 0, -3);

	private record View(String name, Vec3 eye, Vec3 at, BlockPos cottage) {
	}

	private static final List<View> VIEWS = List.of(
		new View("01_chests", new Vec3(-6.5, 3.5, -7.5), new Vec3(-2.5, 0.5, -2.5), RIGHT),
		new View("02_handcrafted_dining", new Vec3(7.5, 3.1, 4.2), new Vec3(2.5, 1.6, 5), LEFT),
		new View("03_cobblefurnies_dining", new Vec3(7.5, 3.1, 4.2), new Vec3(2.5, 1.6, 5), RIGHT),
		new View("04_both", new Vec3(18, 12, -14), new Vec3(18, 2, 5), LEFT));
	private static final int FIRST = 80;
	private static final int EACH = 50;

	private int tick;
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());
	private volatile String summary = "";

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(5000);
				place(level);
			});
		}
		int t = tick - FIRST;
		if (t >= 0 && t % EACH == 0 && t / EACH < VIEWS.size()) {
			View view = VIEWS.get(t / EACH);
			Vec3 base = Vec3.atLowerCornerOf(view.cottage());
			server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), base.add(view.eye()), base.add(view.at())));
		}
		if (t >= 40 && (t - 40) % EACH == 0 && (t - 40) / EACH < VIEWS.size()) {
			ScreenshotHarness.shot(mc, VIEWS.get((t - 40) / EACH).name());
		}
		if (tick == FIRST + VIEWS.size() * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty() ? summary : String.join("; ", wrong));
			mc.stop();
		}
	}

	private void place(ServerLevel level) {
		Blueprint base = BlueprintLibrary.get(level, COTTAGE).orElse(null);
		if (base == null) {
			wrong.add("no Starter Cottage II blueprint");
			return;
		}
		if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("cobblefurnies:oak_chair"))
			|| !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("handcrafted:oak_chair"))) {
			wrong.add("the furniture mods aren't loaded: film this scene with -Pfurniture=true -Pcobblemon=true");
			return;
		}
		FurnitureChoice left = build(level, base, LEFT, "handcrafted");
		FurnitureChoice right = build(level, base, RIGHT, "cobblefurnies");
		if (left == null || right == null) {
			return;
		}
		int chairs = 0;
		for (Map.Entry<Integer, FurnitureChoice.Placed> e : left.spots().entrySet()) {
			FurnitureChoice.Placed was = e.getValue();
			FurnitureChoice.Placed now = right.spots().get(e.getKey());
			if (was.kind() != FurnitureSpot.Kind.CHAIR) {
				if (!was.equals(now)) {
					wrong.add(was.kind() + " at " + was.pos().toShortString() + " changed from " + was.mod() + " to " + (now == null ? "vanilla" : now.mod()));
				}
				continue;
			}
			if (!was.mod().equals("handcrafted") || now == null || !now.mod().equals("cobblefurnies")) {
				wrong.add("chair at " + was.pos().toShortString() + ": " + was.mod() + " on the left, " + (now == null ? "vanilla" : now.mod()) + " on the right");
				continue;
			}
			chairs++;
			for (Map.Entry<BlockPos, BlockState> cell : now.cells().entrySet()) {
				BlockState there = level.getBlockState(RIGHT.offset(now.pos()).offset(cell.getKey()));
				BlockState planned = was.cells().get(cell.getKey());
				if (!there.is(cell.getValue().getBlock())) {
					wrong.add("the right chair at " + now.pos().toShortString() + " is " + BuiltInRegistries.BLOCK.getKey(there.getBlock()));
				} else if (!cell.getValue().isAir() && planned != null && planned.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
					&& there.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
					&& planned.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) != there.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
					wrong.add("the right chair at " + now.pos().toShortString() + " faces another way than Handcrafted's");
				}
			}
		}
		if (chairs == 0) {
			wrong.add("Starter Cottage II has no chair the two mods share");
		}
		summary = chairs + " chairs: Handcrafted's where the chest held Handcrafted's, CobbleFurnies' in the same places and facing where the chest held only "
			+ "CobbleFurnies', every other piece as planned";
	}

	/**
	 * A cottage at {@code at}, planned with this server's order, its chest outside holding each planned chair as
	 * {@code mod}'s piece of the same wood, its furniture restocked from that chest as a Builder's is, then built.
	 */
	private FurnitureChoice build(ServerLevel level, Blueprint base, BlockPos at, String mod) {
		BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "showcase", COTTAGE,
			new BlueprintData.Placement(level.dimension().location(), at, Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			wrong.add("no plan for the cottage");
			return null;
		}
		level.setBlock(at.offset(BENCH), ModBlocks.BUILDERS_BENCH.defaultBlockState(), 3);
		level.setBlock(at.offset(CHEST), Blocks.CHEST.defaultBlockState(), 3);
		Container chest = (Container) level.getBlockEntity(at.offset(CHEST));
		int slot = 0;
		Item wanted = null;
		for (FurnitureChoice.Placed placed : site.furniture().spots().values()) {
			if (placed.kind() != FurnitureSpot.Kind.CHAIR) {
				continue;
			}
			BlockState chair = placed.cells().values().stream().filter(s -> !s.isAir()).findFirst().orElse(null);
			FurnitureEquivalents.Group group = chair == null ? null : FurnitureEquivalents.of(chair.getBlock()).orElse(null);
			if (group == null || group.block(mod) == null || chest == null) {
				continue;
			}
			wanted = chair.getBlock().asItem();
			chest.setItem(slot++, new ItemStack(group.block(mod).asItem()));
		}
		if (wanted != null) {
			FurnitureStock.restock(level, site, BuilderBag.of(List.of()), SupplyContainers.find(level, at.offset(BENCH), plan.bounds()), wanted);
		}
		for (Blueprint.Entry e : site.furniture().apply(base, COTTAGE).blocks()) {
			level.setBlock(at.offset(e.pos()), e.state(), 2 | 16);
		}
		return site.furniture();
	}
}
