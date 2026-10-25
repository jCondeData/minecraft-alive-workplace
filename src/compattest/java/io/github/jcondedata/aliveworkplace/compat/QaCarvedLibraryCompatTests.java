package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * QA for ROADMAP 37.7 (Carved Wood and Rechiseled in the styles) and 37.12 (the vanilla and modded build libraries),
 * written from the specs: every style keeps each job block's villager job site; every modded copy keeps its vanilla
 * build's job sites and resolves to a real vanilla build; a copy with a block of a missing mod falls back to the vanilla
 * build, for new and saved sites; flipping the switches during a build doesn't change it; Carved Wood chests, trapped
 * chests and barrels of every wood are supply chests both ways (in and out), a double one listed once; the switch's text.
 */
public class QaCarvedLibraryCompatTests implements FabricGameTest {
	private static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	private static final String AREA = CompatGameTests.AREA;

	private static BuildSite site(GameTestHelper helper) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		return new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE, placement);
	}

	private static Set<String> blocks(BuildPlan plan) {
		Set<String> out = new HashSet<>();
		Stream.of(BuildPlan.Stage.values()).flatMap(s -> plan.steps(s).stream()).forEach(s -> out.add(id(s.state())));
		return out;
	}

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private static Optional<ResourceLocation> poi(BlockState state) {
		return PoiTypes.forState(state).flatMap(Holder::unwrapKey).map(k -> k.location());
	}

	/**
	 * 37.7: "job blocks are swapped only where Carved Wood's version is the same job site". For every style and every
	 * vanilla block that is some villager's job site (each of its states), the styled block is the same job site (or the
	 * style leaves it alone); and a block that is no job site doesn't become one (a supply chest turned barrel would be
	 * taken by a Fisherman).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyStyleKeepsEveryJobSite(GameTestHelper helper) {
		boolean was = Furnishings.ENABLED;
		List<String> wrong = new ArrayList<>();
		try {
			Furnishings.ENABLED = true;
			int checked = 0;
			for (BlueprintStyles.Style style : BlueprintStyles.all()) {
				if (style.name().equals("sophisticated")) {
					continue; // 37.9's Storehouse barrels, not 37.7: they are stores, not the Fisherman's (reported separately)
				}
				for (Block block : BuiltInRegistries.BLOCK) {
					if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")) {
						continue;
					}
					for (BlockState state : block.getStateDefinition().getPossibleStates()) {
						BlockState styled = BlueprintStyles.apply(style, state);
						if (styled.getBlock() == state.getBlock()) {
							continue;
						}
						checked++;
						Optional<ResourceLocation> before = poi(state);
						Optional<ResourceLocation> after = poi(styled);
						if (!before.equals(after)) {
							wrong.add(style.name() + ": " + state + " (" + before.map(Object::toString).orElse("no job site") + ") -> " + styled + " ("
								+ after.map(Object::toString).orElse("no job site") + ")");
							break;
						}
					}
				}
			}
			helper.assertTrue(checked > 0, "no style swaps any block: this test checks nothing");
		} finally {
			Furnishings.ENABLED = was;
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " job sites changed: " + wrong.subList(0, Math.min(6, wrong.size())));
		helper.succeed();
	}

	/**
	 * 37.12: "a copy of each home and workshop ... under its own ids, with the vanilla build as its fallback". Every copy is
	 * of a build the vanilla library has; every job site of the vanilla build (composters, smokers, looms, cauldrons ... not
	 * only lecterns and barrels) is the same job site in the copy, at the same place, and the copy adds no job site.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyModdedCopyKeepsItsJobSites(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int jobs = 0;
		for (ResourceLocation id : ModdedLibrary.builds()) {
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), id).orElse(null);
			Blueprint copy = ModdedLibrary.copy(id).orElse(null);
			if (base == null || copy == null) {
				wrong.add(id + ": vanilla " + (base != null) + ", copy " + (copy != null));
				continue;
			}
			if (!copy.id().equals(id)) {
				wrong.add(id + "'s copy is called " + copy.id());
			}
			Map<BlockPos, BlockState> at = new HashMap<>();
			copy.blocks().forEach(e -> at.put(e.pos(), e.state()));
			Map<BlockPos, BlockState> baseAt = new HashMap<>();
			base.blocks().forEach(e -> baseAt.put(e.pos(), e.state()));
			for (Map.Entry<BlockPos, BlockState> e : at.entrySet()) {
				BlockState was = baseAt.getOrDefault(e.getKey(), Blocks.AIR.defaultBlockState());
				Optional<ResourceLocation> before = poi(was);
				Optional<ResourceLocation> after = poi(e.getValue());
				jobs += before.isPresent() ? 1 : 0;
				if (!before.equals(after)) {
					wrong.add(id + " at " + e.getKey().toShortString() + ": " + id(was) + " " + before.orElse(null) + " -> " + id(e.getValue()) + " " + after.orElse(null));
					break;
				}
			}
		}
		helper.assertTrue(jobs > 50, "only " + jobs + " job sites in the copies: this test checks little");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.subList(0, Math.min(6, wrong.size())));
		helper.succeed();
	}

	/**
	 * 37.12: "every home and workshop" has a copy. The wiki names the builds without one (decorations, walls, arenas, the
	 * Create builds, the Habitat Garden); here every tier of the starter homes and the core workshops named in the ROADMAP
	 * must have a complete copy, and a build that doesn't exist must not be "used".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHomesAndWorkshopsHaveCopies(GameTestHelper helper) {
		List<String> lacking = new ArrayList<>();
		for (String name : List.of("starter_cottage", "library", "supply_shop", "print_shop")) {
			ResourceLocation id = AliveWorkplace.id(name);
			if (BlueprintLibrary.get(helper.getLevel(), id).isEmpty()) {
				lacking.add(name + " (no vanilla build)");
			} else if (!ModdedLibrary.complete(id)) {
				lacking.add(name + " (copy " + ModdedLibrary.hasCopy(id) + ", missing " + ModdedLibrary.missing(id) + ")");
			}
		}
		helper.assertTrue(lacking.isEmpty(), "homes and workshops without a modded copy: " + lacking);
		ResourceLocation none = AliveWorkplace.id("qa_no_such_build");
		helper.assertTrue(!ModdedLibrary.hasCopy(none) && !ModdedLibrary.use(none) && ModdedLibrary.copy(none).isEmpty() && ModdedLibrary.missing(none).isEmpty(),
			"a build that doesn't exist has a modded copy");
		helper.succeed();
	}

	/**
	 * 37.12: the switches flipped while a modded build is going up (the same site, no reload) change nothing: it is
	 * planned again from the modded copy; and the switch on again doesn't turn a vanilla site going up into a modded one.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaModdedLibrary"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaModdedLibrary")
	public void switchesFlippedMidBuildChangeNothing(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			BuildSite site = site(helper);
			helper.assertTrue(blocks(site.plan(helper.getLevel())).contains("supplementaries:daub"), "the new cottage isn't modded");
			site.advance();
			WorkplaceConfig.parse("{\"moddedLibrary\": false}").apply();
			Set<String> again = blocks(site.plan(helper.getLevel()));
			helper.assertTrue(site.furniture().modded() && again.contains("supplementaries:daub"), "the switch off mid-build changed the build: " + again);
			WorkplaceConfig.parse("{\"furnishedBuilds\": false}").apply();
			Set<String> third = blocks(site.plan(helper.getLevel()));
			helper.assertTrue(site.furniture().modded() && third.contains("supplementaries:daub"), "furnished builds off mid-build changed the build: " + third);

			WorkplaceConfig.parse("{\"moddedLibrary\": false}").apply();
			BuildSite vanilla = site(helper);
			vanilla.plan(helper.getLevel());
			new WorkplaceConfig().apply();
			Set<String> v = blocks(vanilla.plan(helper.getLevel()));
			helper.assertTrue(!vanilla.furniture().modded() && !v.contains("supplementaries:daub"), "the switch on mid-build turned a vanilla site modded: " + v);
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * 37.12: "the vanilla build as its fallback when a mod is missing", and the wiki: "a copy whose blocks are gone later
	 * is built as the vanilla build". The Starter Cottage's copy is reloaded with one block of a mod that isn't installed:
	 * it is incomplete (that block named as missing), a new cottage is the vanilla one, and a site planned and saved from
	 * the complete copy loads and plans as the vanilla build, without a crash. The real copies are reloaded afterwards.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaModdedLibraryMissing"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaModdedLibraryMissing")
	public void aCopyWithAMissingModsBlockFallsBackToVanilla(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ResourceManager real = level.getServer().getResourceManager();
		java.lang.reflect.Constructor<ModdedLibrary> make = ModdedLibrary.class.getDeclaredConstructor();
		make.setAccessible(true);
		ModdedLibrary listener = make.newInstance();
		ResourceLocation missingBlock = ResourceLocation.fromNamespaceAndPath("qa_absent_mod", "fancy_planks");
		try {
			new WorkplaceConfig().apply();
			BuildSite saved = site(helper);
			helper.assertTrue(blocks(saved.plan(level)).contains("supplementaries:daub"), "the cottage isn't modded before the test");
			CompoundTag savedTag = saved.save();

			ResourceLocation file = AliveWorkplace.id(ModdedLibrary.FOLDER + "/starter_cottage.nbt");
			Resource original = real.getResource(file).orElseThrow(() -> new GameTestAssertException("no " + file));
			CompoundTag tag;
			try (InputStream in = original.open()) {
				tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			}
			ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
			CompoundTag absent = new CompoundTag();
			absent.putString("Name", missingBlock.toString());
			palette.add(absent);
			// one block of the build uses it, so the copy really needs that mod
			ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
			blocks.getCompound(0).putInt("state", palette.size() - 1);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(tag, out);
			byte[] bytes = out.toByteArray();
			PackResources pack = original.source();
			Resource patched = new Resource(pack, () -> new ByteArrayInputStream(bytes));
			listener.onResourceManagerReload(withFile(real, file, patched));

			helper.assertTrue(ModdedLibrary.hasCopy(COTTAGE) && ModdedLibrary.missing(COTTAGE).contains(missingBlock),
				"the copy's missing block isn't named: " + ModdedLibrary.missing(COTTAGE));
			helper.assertTrue(!ModdedLibrary.complete(COTTAGE) && !ModdedLibrary.use(COTTAGE) && ModdedLibrary.copy(COTTAGE).isEmpty(),
				"a copy with a missing mod's block is still used");
			helper.assertTrue(ModdedLibrary.complete(AliveWorkplace.id("library")), "the other copies are no longer complete");
			BuildSite fresh = site(helper);
			Set<String> freshBlocks = blocks(fresh.plan(level));
			helper.assertTrue(!fresh.furniture().modded() && freshBlocks.contains("minecraft:birch_planks") && !freshBlocks.contains("supplementaries:daub"),
				"a new cottage with a mod missing isn't the vanilla build: " + freshBlocks);
			BuildSite loaded = BuildSite.load(savedTag);
			BuildPlan plan = loaded.plan(level);
			helper.assertTrue(plan != null, "the saved modded site has no plan with its copy incomplete");
			Set<String> loadedBlocks = blocks(plan);
			helper.assertTrue(loadedBlocks.contains("minecraft:birch_planks") && !loadedBlocks.contains("supplementaries:daub"),
				"a saved modded site whose copy lost a block isn't built as the vanilla build: " + loadedBlocks);
		} finally {
			listener.onResourceManagerReload(real);
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(ModdedLibrary.complete(COTTAGE), "the real copies didn't come back");
		helper.succeed();
	}

	/** {@code real} with {@code file} answered by {@code resource} (in listings and lookups). */
	private static ResourceManager withFile(ResourceManager real, ResourceLocation file, Resource resource) {
		return new ResourceManager() {
			@Override
			public Set<String> getNamespaces() {
				return real.getNamespaces();
			}

			@Override
			public List<Resource> getResourceStack(ResourceLocation location) {
				return location.equals(file) ? List.of(resource) : real.getResourceStack(location);
			}

			@Override
			public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
				Map<ResourceLocation, Resource> found = new HashMap<>(real.listResources(path, filter));
				if (found.containsKey(file)) {
					found.put(file, resource);
				}
				return found;
			}

			@Override
			public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
				return real.listResourceStacks(path, filter);
			}

			@Override
			public Stream<PackResources> listPacks() {
				return real.listPacks();
			}

			@Override
			public Optional<Resource> getResource(ResourceLocation location) {
				return location.equals(file) ? Optional.of(resource) : real.getResource(location);
			}
		};
	}

	/**
	 * 37.7 and the wiki: "Carved Wood's chests and barrels of every wood are supply chests". Each wood's Carved Wood chest,
	 * trapped chest and barrel by the bench is listed, takes items in and gives them back out; a double Carved Wood chest
	 * is one store, not two.
	 */
	//$ gametest_batch AREA '"qaCarvedSupply"'
	@GameTest(template = AREA, batch = "qaCarvedSupply")
	public void everyCarvedWoodChestAndBarrelIsASupplyChestBothWays(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		List<Block> kinds = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
			if (key.getNamespace().equals("carved_wood") && (key.getPath().endsWith("_chest") || key.getPath().endsWith("_barrel"))) {
				kinds.add(block);
			}
		}
		helper.assertTrue(kinds.size() >= 10, "only " + kinds.size() + " Carved Wood chests and barrels");
		BlockPos bench = helper.absolutePos(new BlockPos(1, 2, 1));
		helper.setBlock(new BlockPos(1, 2, 1), io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH);
		List<String> wrong = new ArrayList<>();
		for (Block kind : kinds) {
			BlockPos rel = new BlockPos(3, 2, 3);
			helper.setBlock(rel, Blocks.AIR);
			helper.setBlock(rel, kind);
			BlockPos abs = helper.absolutePos(rel);
			String name = BuiltInRegistries.BLOCK.getKey(kind).toString();
			if (!SupplyContainers.find(level, bench, null).contains(abs)) {
				wrong.add(name + " not listed");
				continue;
			}
			ItemStack left = SupplyContainers.insert(level, List.of(abs), new ItemStack(Items.COBBLESTONE, 10));
			if (!left.isEmpty() || SupplyContainers.count(level, List.of(abs), Items.COBBLESTONE) != 10) {
				wrong.add(name + " took " + (10 - left.getCount()) + " of 10 in");
				continue;
			}
			Container c = helper.getBlockEntity(rel);
			ItemStack taken = c.removeItemNoUpdate(firstFull(c));
			if (taken.getCount() != 10 || SupplyContainers.count(level, List.of(abs), Items.COBBLESTONE) != 0) {
				wrong.add(name + " gave back " + taken);
			}
			c.clearContent();
		}
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
		// a double Carved Wood spruce chest: one store
		Block chest = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("carved_wood:spruce_chest"));
		BlockState base = chest.defaultBlockState();
		if (base.hasProperty(ChestBlock.TYPE)) {
			helper.setBlock(new BlockPos(3, 2, 3), base.setValue(ChestBlock.TYPE, ChestType.LEFT));
			helper.setBlock(new BlockPos(4, 2, 3), base.setValue(ChestBlock.TYPE, ChestType.RIGHT));
			List<BlockPos> stores = SupplyContainers.inside(level, net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
				helper.absolutePos(new BlockPos(2, 1, 2)), helper.absolutePos(new BlockPos(5, 3, 4))));
			if (stores.size() != 1) {
				wrong.add("a double Carved Wood chest is " + stores.size() + " stores");
			}
		} else {
			wrong.add("carved_wood:spruce_chest has no chest type");
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		helper.succeed();
	}

	private static int firstFull(Container c) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty()) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * 37.12's switch is a sentence a player reads: its name and tooltip are in en_us.json, and the tooltip names every
	 * mod the library needs and the other switch it depends on.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSwitchHasItsText(GameTestHelper helper) throws Exception {
		com.google.gson.JsonObject lang;
		try (InputStream in = AliveWorkplace.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "no en_us.json");
			lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
		}
		helper.assertTrue(lang.has("aliveworkplace.config.moddedLibrary") && !lang.get("aliveworkplace.config.moddedLibrary").getAsString().isBlank(),
			"the switch has no name");
		String tip = lang.has("aliveworkplace.config.moddedLibrary.tooltip") ? lang.get("aliveworkplace.config.moddedLibrary.tooltip").getAsString() : "";
		for (String word : List.of("Rechiseled", "Supplementaries", "Carved Wood", "Chipped", "Handcrafted", "Furnished Builds", "vanilla")) {
			helper.assertTrue(tip.contains(word), "the tooltip doesn't say " + word + ": " + tip);
		}
		helper.assertTrue(WorkplaceConfig.parse("{}").moddedLibrary, "the switch isn't on by default with the mods installed");
		helper.succeed();
	}
}
