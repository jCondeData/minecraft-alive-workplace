package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The checks every Wonder's design meets (ROADMAP 35.9 on, docs/design/M35.md), read from its data file
 * ({@code data/aliveworkplace/wonders/<id>.json}) and its stage parts as they ship: every part loads and is at most 48
 * a side, the data file's names have their lines, the parts stay out of the Blueprint Table, no part sets air over an
 * earlier stage's block, the block count is within 10% of the roadmap's figure, and it uses no retired job block, no
 * calcite and nothing a player can't get. A new Wonder's design adds one line to {@link #WONDERS}.
 */
public class WonderBlueprintGameTests implements FabricGameTest {
	/** Each Wonder's design: its id and the block count its roadmap item names. */
	record Design(String id, int blocks) {
	}

	static final List<Design> WONDERS = List.of(
		new Design("great_cathedral", 14_000)); // 35.9

	/** The largest a part may be on any side (a structure file a Structure Block could load). */
	static final int PART_LIMIT = 48;

	/** Blocks no player can get in survival (beyond those with no item at all). */
	static final Set<Block> UNOBTAINABLE = Set.of(Blocks.BEDROCK, Blocks.BARRIER, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK,
		Blocks.REPEATING_COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID, Blocks.JIGSAW, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER,
		Blocks.VAULT, Blocks.END_PORTAL_FRAME, Blocks.REINFORCED_DEEPSLATE, Blocks.BUDDING_AMETHYST, Blocks.PETRIFIED_OAK_SLAB, Blocks.LIGHT,
		Blocks.INFESTED_STONE, Blocks.INFESTED_COBBLESTONE, Blocks.INFESTED_STONE_BRICKS, Blocks.INFESTED_MOSSY_STONE_BRICKS,
		Blocks.INFESTED_CRACKED_STONE_BRICKS, Blocks.INFESTED_CHISELED_STONE_BRICKS, Blocks.INFESTED_DEEPSLATE, Blocks.PLAYER_HEAD,
		Blocks.PLAYER_WALL_HEAD, Blocks.DRAGON_EGG);

	@GameTestGenerator
	public Collection<TestFunction> designs() {
		List<TestFunction> out = new ArrayList<>();
		for (Design d : WONDERS) {
			add(out, "wonderPartsLoad_" + d.id(), d, WonderBlueprintGameTests::partsLoad);
			add(out, "wonderStagesOnlyAdd_" + d.id(), d, WonderBlueprintGameTests::stagesOnlyAdd);
			add(out, "wonderBlockCount_" + d.id(), d, WonderBlueprintGameTests::blockCount);
			add(out, "wonderMaterials_" + d.id(), d, WonderBlueprintGameTests::materials);
		}
		return out;
	}

	private static void add(List<TestFunction> out, String name, Design d, BiConsumer<GameTestHelper, Design> check) {
		out.add(new TestFunction(name, name, FabricGameTest.EMPTY_STRUCTURE, 100, 0, true, helper -> {
			check.accept(helper, d);
			helper.succeed();
		}));
	}

	// --- reading a Wonder as it ships -----------------------------------------------------------------------------------

	/** One part of a stage: its blueprint and where it goes from the Wonder's corner. */
	record Part(ResourceLocation id, BlockPos offset, Blueprint blueprint) {
	}

	/** The Wonder's data file. */
	static JsonObject file(MinecraftServer server, String id) {
		ResourceLocation path = AliveWorkplace.id("wonders/" + id + ".json");
		var resource = server.getResourceManager().getResource(path)
			.orElseThrow(() -> new GameTestAssertException("no data file " + path));
		try (Reader reader = resource.openAsReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException(path + " doesn't read: " + e.getMessage());
		}
	}

	/** Its stages, in order, each a list of its parts (a part that doesn't load fails the test). */
	static List<List<Part>> stages(MinecraftServer server, String id) {
		List<List<Part>> out = new ArrayList<>();
		for (JsonElement stage : file(server, id).getAsJsonArray("stages")) {
			List<Part> parts = new ArrayList<>();
			for (JsonElement p : stage.getAsJsonObject().getAsJsonArray("parts")) {
				ResourceLocation part = ResourceLocation.parse(p.getAsJsonObject().get("part").getAsString());
				JsonArray o = p.getAsJsonObject().getAsJsonArray("offset");
				Blueprint blueprint = BlueprintLibrary.get(server, part)
					.orElseThrow(() -> new GameTestAssertException(id + ": part " + part + " doesn't load"));
				parts.add(new Part(part, new BlockPos(o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt()), blueprint));
			}
			out.add(parts);
		}
		return out;
	}

	// --- the checks -----------------------------------------------------------------------------------------------------

	/** Every part loads, holds blocks and is at most 48 a side; its names have their lines; none is in the Blueprint Table. */
	static void partsLoad(GameTestHelper helper, Design d) {
		MinecraftServer server = helper.getLevel().getServer();
		JsonObject file = file(server, d.id());
		List<List<Part>> stages = stages(server, d.id());
		helper.assertTrue(stages.size() >= 2, d.id() + " has " + stages.size() + " stages");
		List<String> wrong = new ArrayList<>();
		for (int n = 0; n < stages.size(); n++) {
			if (stages.get(n).isEmpty()) {
				wrong.add("stage " + (n + 1) + " has no parts");
			}
			for (Part p : stages.get(n)) {
				var size = p.blueprint().size();
				if (size.getX() > PART_LIMIT || size.getY() > PART_LIMIT || size.getZ() > PART_LIMIT) {
					wrong.add(p.id() + " is " + size.getX() + " x " + size.getY() + " x " + size.getZ());
				}
				if (p.blueprint().solidBlockCount() == 0) {
					wrong.add(p.id() + " is empty");
				}
				if (!p.id().getPath().startsWith("wonder/" + d.id() + "/")) {
					wrong.add(p.id() + " isn't in wonder/" + d.id());
				}
			}
		}
		List<String> keys = new ArrayList<>(List.of(file.get("name").getAsString(), file.get("description").getAsString()));
		file.getAsJsonArray("stages").forEach(s -> keys.add(s.getAsJsonObject().get("name").getAsString()));
		for (String key : keys) {
			if (!Language.getInstance().has(key)) {
				wrong.add("no text for " + key);
			}
		}
		List<ResourceLocation> table = BlueprintLibrary.list(server, false);
		table.stream().filter(id -> id.getPath().startsWith("wonder/")).forEach(id -> wrong.add(id + " is in the Blueprint Table"));
		helper.assertTrue(wrong.isEmpty(), d.id() + ": " + wrong);
	}

	/** No stage's part sets air (so a builder would clear) where an earlier stage left a block. */
	static void stagesOnlyAdd(GameTestHelper helper, Design d) {
		List<List<Part>> stages = stages(helper.getLevel().getServer(), d.id());
		Map<BlockPos, Integer> built = new HashMap<>();
		List<String> wrong = new ArrayList<>();
		for (int n = 0; n < stages.size(); n++) {
			Map<BlockPos, Integer> mine = new HashMap<>();
			for (Part p : stages.get(n)) {
				for (Blueprint.Entry e : p.blueprint().blocks()) {
					BlockPos at = p.offset().offset(e.pos());
					if (e.state().isAir()) {
						Integer earlier = built.get(at);
						if (earlier != null && wrong.size() < 10) {
							wrong.add(p.id() + " clears " + at.toShortString() + " (stage " + earlier + "'s)");
						}
					} else {
						mine.put(at, n + 1);
					}
				}
			}
			built.putAll(mine);
		}
		helper.assertTrue(wrong.isEmpty(), d.id() + ": " + wrong);
	}

	/** The whole Wonder's blocks are within 10% of the roadmap's figure. */
	static void blockCount(GameTestHelper helper, Design d) {
		int blocks = 0;
		for (List<Part> stage : stages(helper.getLevel().getServer(), d.id())) {
			for (Part p : stage) {
				blocks += p.blueprint().solidBlockCount();
			}
		}
		helper.assertTrue(Math.abs(blocks - d.blocks()) <= d.blocks() / 10,
			d.id() + " has " + blocks + " blocks, not within 10% of " + d.blocks());
	}

	/** No retired job block, no calcite, nothing a player can't get. */
	static void materials(GameTestHelper helper, Design d) {
		List<Block> retired = StationsSpecGameTests.gone();
		Set<String> wrong = new java.util.TreeSet<>();
		for (List<Part> stage : stages(helper.getLevel().getServer(), d.id())) {
			for (Part p : stage) {
				for (Blueprint.Entry e : p.blueprint().blocks()) {
					BlockState state = e.state();
					if (state.isAir()) {
						continue;
					}
					Block block = state.getBlock();
					String name = BuiltInRegistries.BLOCK.getKey(block).toString();
					if (retired.contains(block)) {
						wrong.add(name + " (a retired job block)");
					} else if (block == Blocks.CALCITE) {
						wrong.add(name);
					} else if (UNOBTAINABLE.contains(block) || block.asItem() == Items.AIR) {
						wrong.add(name + " (no player can get it)");
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), d.id() + " uses " + wrong);
	}
}
