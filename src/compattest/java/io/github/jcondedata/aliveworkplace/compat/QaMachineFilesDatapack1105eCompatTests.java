package io.github.jcondedata.aliveworkplace.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.MachineWorkCompatTests.Kind;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWork;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * QA (lane E, 2026-11-05), the machine-work data files from the datapack author's side (wiki create.md, "Machine work"):
 * "a datapack can add or replace them", read on load and on {@code /reload}; files "for a mod that isn't loaded, or
 * naming a block, job or recipe type that doesn't exist, are skipped quietly"; {@code batch} is the most carried per trip
 * and {@code keep} how many the store should keep; "a file gone still runs the trip, which brings the load back on foot",
 * and a trip whose worker is no longer in the file's job ends with its load put back. Tests that swap the loaded files
 * each have a batch of their own and put the original files back when they pass or fail.
 */
public class QaMachineFilesDatapack1105eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	// --- reading files --------------------------------------------------------------------------------------------

	/** The shipped millstone file, as a datapack author would copy it. */
	private static final String MILLSTONE = """
		{"requires": "create", "machine": "create:millstone", "job": "minecraft:farmer", "recipe_type": "create:milling",
		 "feed": {"at": [0, 0, 0]}, "collect": {"at": [0, 0, 0]},
		 "makes": [{"item": "create:wheat_flour", "keep": 64}], "batch": 16, "min_speed": 1}""";

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	private static JsonObject millstone(java.util.function.Consumer<JsonObject> change) {
		JsonObject o = json(MILLSTONE);
		change.accept(o);
		return o;
	}

	/** {@code MachineWorkFiles.read}; an exception it throws comes out as itself. */
	@Nullable
	private static MachineWorkFiles.Machine read(String id, JsonObject json) {
		Method m;
		try {
			m = MachineWorkFiles.class.getDeclaredMethod("read", ResourceLocation.class, JsonObject.class);
			m.setAccessible(true);
		} catch (NoSuchMethodException e) {
			throw new AssertionError("setup: MachineWorkFiles has no read(id, json)", e);
		}
		try {
			return (MachineWorkFiles.Machine) m.invoke(null, ResourceLocation.parse(id), json);
		} catch (InvocationTargetException e) {
			throw e.getCause() instanceof RuntimeException r ? r : new RuntimeException(e.getCause());
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Files naming a mod that isn't loaded, a block, job or recipe type that doesn't exist, or only items that don't
	 * exist, are skipped (no machine); an item that doesn't exist is dropped from {@code makes} while the real one stays;
	 * {@code batch} 0 or below still carries one; a datapack's own machine with vanilla ids only loads.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMachineFileNamingWhatIsntThereIsSkipped(GameTestHelper helper) {
		MachineWorkFiles.Machine base = read("aliveworkplace:qa_base", json(MILLSTONE));
		helper.assertTrue(base != null, "setup: a copy of the shipped millstone file didn't load");
		helper.assertTrue(base.batch() == 16 && base.makes().size() == 1 && base.makes().get(0).keep() == 64,
			"setup: the millstone copy read as batch " + base.batch() + ", makes " + base.makes());

		Map<String, JsonObject> skipped = new LinkedHashMap<>();
		skipped.put("requires a mod that isn't loaded", millstone(o -> o.addProperty("requires", "qa_no_such_mod")));
		skipped.put("a machine block that doesn't exist", millstone(o -> o.addProperty("machine", "create:qa_no_such_block")));
		skipped.put("a block of a mod that isn't loaded", millstone(o -> o.addProperty("machine", "qa_no_such_mod:millstone")));
		skipped.put("a job that doesn't exist", millstone(o -> o.addProperty("job", "minecraft:qa_nobody")));
		skipped.put("a recipe type that doesn't exist", millstone(o -> o.addProperty("recipe_type", "create:qa_no_such_type")));
		skipped.put("only items that don't exist", millstone(o -> o.add("makes",
			JsonParser.parseString("[{\"item\": \"create:qa_no_such_flour\", \"keep\": 8}, {\"item\": \"qa_no_such_mod:flour\"}]"))));
		skipped.put("nothing to make", millstone(o -> o.add("makes", JsonParser.parseString("[]"))));
		for (Map.Entry<String, JsonObject> e : skipped.entrySet()) {
			MachineWorkFiles.Machine got;
			try {
				got = read("aliveworkplace:qa_skipped", e.getValue());
			} catch (RuntimeException t) {
				throw new AssertionError("a file with " + e.getKey() + " threw instead of being skipped quietly: " + t, t);
			}
			helper.assertTrue(got == null, "a file with " + e.getKey() + " was loaded: " + got);
		}

		MachineWorkFiles.Machine mixed = read("aliveworkplace:qa_mixed", millstone(o -> o.add("makes",
			JsonParser.parseString("[{\"item\": \"create:qa_no_such_flour\", \"keep\": 3}, {\"item\": \"create:wheat_flour\", \"keep\": 5}]"))));
		helper.assertTrue(mixed != null, "a file making one unknown and one real item was skipped whole");
		helper.assertTrue(mixed.makes().size() == 1 && mixed.makes().get(0).item() == Kind.MILL.output() && mixed.makes().get(0).keep() == 5,
			"a file making one unknown and one real item kept " + mixed.makes() + ", not just the flour with keep 5");

		for (int batch : new int[] {0, -1, -64}) {
			MachineWorkFiles.Machine m = read("aliveworkplace:qa_batch", millstone(o -> o.addProperty("batch", batch)));
			helper.assertTrue(m != null && m.batch() == 1, "batch " + batch + " read as " + (m == null ? "no file" : m.batch()) + ", not 1");
		}
		MachineWorkFiles.Machine noKeep = read("aliveworkplace:qa_nokeep", millstone(o -> o.add("makes",
			JsonParser.parseString("[{\"item\": \"create:wheat_flour\"}]"))));
		helper.assertTrue(noKeep != null && noKeep.makes().get(0).keep() == 64, "a make without keep keeps " + (noKeep == null ? "no file" : noKeep.makes()));

		MachineWorkFiles.Machine kiln = read("qa_pack:kiln", json("""
			{"machine": "minecraft:furnace", "job": "minecraft:mason", "recipe_type": "minecraft:smelting",
			 "makes": [{"item": "minecraft:stone", "keep": 32}], "batch": 8}"""));
		helper.assertTrue(kiln != null, "a datapack's own machine of vanilla ids, with no requires, was skipped");
		helper.assertTrue(kiln.machine() == Blocks.FURNACE && kiln.job().equals(ResourceLocation.parse("minecraft:mason"))
			&& kiln.batch() == 8 && kiln.makes().get(0).item() == Items.STONE && kiln.makes().get(0).keep() == 32,
			"the datapack's kiln read as " + kiln);
		MachineWorkFiles.Machine needsMinecraft = read("qa_pack:kiln2", millstone(o -> o.addProperty("requires", "minecraft")));
		helper.assertTrue(needsMinecraft != null, "a file that requires a loaded mod (minecraft) was skipped");
		helper.succeed();
	}

	/** The loader's real reload, given the server's data plus a datapack's files: the four shipped ones and a list of extras. */
	private static List<MachineWorkFiles.Machine> reloadWith(GameTestHelper helper, Map<ResourceLocation, String> extra) {
		ResourceManager real = helper.getLevel().getServer().getResourceManager();
		Map<ResourceLocation, Resource> shipped = real.listResources("machine_work", p -> p.getPath().endsWith(".json"));
		PackResources pack = shipped.values().stream().findFirst().map(Resource::source)
			.orElseThrow(() -> new AssertionError("setup: the server has no machine_work files at all"));
		Map<ResourceLocation, Resource> datapack = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, String> e : extra.entrySet()) {
			byte[] bytes = e.getValue().getBytes(StandardCharsets.UTF_8);
			datapack.put(e.getKey(), new Resource(pack, () -> new ByteArrayInputStream(bytes)));
		}
		ResourceManager withPack = new ResourceManager() {
			@Override
			public Set<String> getNamespaces() {
				return real.getNamespaces();
			}

			@Override
			public List<Resource> getResourceStack(ResourceLocation id) {
				return datapack.containsKey(id) ? List.of(datapack.get(id)) : real.getResourceStack(id);
			}

			@Override
			public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
				Map<ResourceLocation, Resource> out = new LinkedHashMap<>(real.listResources(path, filter));
				datapack.forEach((id, r) -> {
					if (id.getPath().startsWith(path + "/") && filter.test(id)) {
						out.put(id, r); // a datapack's file of the same id replaces the mod's
					}
				});
				return out;
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
			public Optional<Resource> getResource(ResourceLocation id) {
				return datapack.containsKey(id) ? Optional.of(datapack.get(id)) : real.getResource(id);
			}
		};
		ResourceManagerReloadListener loader;
		try {
			Constructor<MachineWorkFiles> c = MachineWorkFiles.class.getDeclaredConstructor();
			c.setAccessible(true);
			loader = c.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("setup: can't make the machine-work loader", e);
		}
		loader.onResourceManagerReload(withPack);
		return MachineWorkFiles.all();
	}

	private static ResourceLocation file(String ns, String name) {
		return ResourceLocation.fromNamespaceAndPath(ns, "machine_work/" + name + ".json");
	}

	/**
	 * A reload through the loader itself with a datapack holding: a millstone file of the same id (batch 4, keep 9),
	 * which replaces the mod's; a kiln in the datapack's own namespace, which is added; and files that are broken (no
	 * machine, not JSON at all, an id that isn't one) or name what isn't there. The broken ones are skipped quietly,
	 * the rest all load; a reload without the datapack brings the shipped millstone back.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_machine_files_reload"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_machine_files_reload")
	public void aDatapackAddsAndReplacesMachineFilesOnReload(GameTestHelper helper) {
		List<MachineWorkFiles.Machine> original = MachineWorkFiles.all();
		try {
			List<String> ids = original.stream().map(m -> m.id().toString()).sorted().toList();
			helper.assertTrue(ids.containsAll(List.of("aliveworkplace:crushing", "aliveworkplace:millstone", "aliveworkplace:mixer", "aliveworkplace:press")),
				"the loaded files are " + ids + ", not the four shipped ones");
			Map<ResourceLocation, String> pack = new LinkedHashMap<>();
			pack.put(file(AliveWorkplace.MOD_ID, "millstone"), MILLSTONE.replace("\"batch\": 16", "\"batch\": 4").replace("\"keep\": 64", "\"keep\": 9"));
			pack.put(file("qa_pack", "kiln"), """
				{"machine": "minecraft:furnace", "job": "minecraft:mason", "recipe_type": "minecraft:smelting",
				 "makes": [{"item": "minecraft:stone", "keep": 32}]}""");
			pack.put(file("qa_pack", "no_machine"), "{\"job\": \"minecraft:farmer\", \"recipe_type\": \"create:milling\", \"makes\": [{\"item\": \"create:wheat_flour\"}]}");
			pack.put(file("qa_pack", "not_json"), "{{{ this is not a machine file");
			pack.put(file("qa_pack", "empty"), "");
			pack.put(file("qa_pack", "a_list"), "[1, 2, 3]");
			pack.put(file("qa_pack", "bad_id"), MILLSTONE.replace("create:millstone", "Create:Not An Id"));
			pack.put(file("qa_pack", "no_mod"), MILLSTONE.replace("\"requires\": \"create\"", "\"requires\": \"qa_no_such_mod\""));
			pack.put(file("qa_pack", "no_block"), MILLSTONE.replace("create:millstone", "create:qa_no_such_block"));
			pack.put(file("qa_pack", "no_job"), MILLSTONE.replace("minecraft:farmer", "minecraft:qa_nobody"));
			pack.put(file("qa_pack", "no_type"), MILLSTONE.replace("create:milling", "create:qa_no_such_type"));
			List<MachineWorkFiles.Machine> loaded;
			try {
				loaded = reloadWith(helper, pack);
			} catch (RuntimeException e) {
				throw new AssertionError("a reload with broken machine files threw instead of skipping them: " + e, e);
			}
			List<String> now = loaded.stream().map(m -> m.id().toString()).sorted().toList();
			List<String> expected = new ArrayList<>(ids);
			expected.add("qa_pack:kiln");
			expected.sort(null);
			helper.assertTrue(now.equals(expected), "after the datapack's reload the files are " + now + ", not " + expected);
			MachineWorkFiles.Machine mill = loaded.stream().filter(m -> m.id().equals(AliveWorkplace.id("millstone"))).findFirst().orElseThrow();
			helper.assertTrue(mill.batch() == 4 && mill.makes().get(0).keep() == 9,
				"the datapack's millstone (batch 4, keep 9) didn't replace the mod's: batch " + mill.batch() + ", makes " + mill.makes());
			helper.assertTrue(MachineWorkFiles.forMachine(Blocks.FURNACE.defaultBlockState()) != null, "the datapack's kiln isn't the furnace's file");
			helper.assertTrue(MachineWorkFiles.forJob(ResourceLocation.parse("minecraft:mason")).stream().anyMatch(m -> m.id().toString().equals("qa_pack:kiln")),
				"the mason doesn't work the datapack's kiln");

			List<MachineWorkFiles.Machine> back = reloadWith(helper, Map.of());
			MachineWorkFiles.Machine shipped = back.stream().filter(m -> m.id().equals(AliveWorkplace.id("millstone"))).findFirst().orElseThrow(
				() -> new AssertionError("a reload without the datapack has no millstone"));
			helper.assertTrue(shipped.batch() == 16 && shipped.makes().get(0).keep() == 64 && back.stream().noneMatch(m -> m.id().getNamespace().equals("qa_pack")),
				"a reload without the datapack kept its files: " + back);
		} finally {
			setFiles(original);
		}
		helper.succeed();
	}

	// --- trips with swapped files ---------------------------------------------------------------------------------

	private static Field filesField() {
		try {
			Field f = MachineWorkFiles.class.getDeclaredField("files");
			f.setAccessible(true);
			return f;
		} catch (NoSuchFieldException e) {
			throw new AssertionError("setup: MachineWorkFiles has no files list", e);
		}
	}

	private static void setFiles(List<MachineWorkFiles.Machine> list) {
		try {
			filesField().set(null, List.copyOf(list));
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Swaps the loaded files for what {@code change} makes of them, as a datapack's reload would, and puts the original
	 * list back when the test passes or fails.
	 */
	private static void swapFiles(GameTestHelper helper, UnaryOperator<List<MachineWorkFiles.Machine>> change) {
		List<MachineWorkFiles.Machine> original = MachineWorkFiles.all();
		PartnerShowsCompatTests.after(helper, () -> setFiles(original));
		setFiles(change.apply(new ArrayList<>(original)));
	}

	private static MachineWorkFiles.Machine shippedMill() {
		return MachineWorkFiles.all().stream().filter(m -> m.id().equals(AliveWorkplace.id(Kind.MILL.file))).findFirst()
			.orElseThrow(() -> new AssertionError("no machine_work file millstone: " + MachineWorkFiles.all()));
	}

	/** The millstone file with another batch, keep and job. */
	private static MachineWorkFiles.Machine mill(int batch, int keep, String job) {
		MachineWorkFiles.Machine m = shippedMill();
		return new MachineWorkFiles.Machine(m.id(), m.machine(), ResourceLocation.parse(job), m.recipeType(), m.feed(), m.collect(),
			List.of(new MachineWorkFiles.Make(Kind.MILL.output(), keep)), batch, m.minSpeed());
	}

	/** A second farmer's file, for a machine nobody has built here (a press): the farmer still has machine work. */
	private static MachineWorkFiles.Machine farmersPress() {
		return new MachineWorkFiles.Machine(AliveWorkplace.id("qa_farmers_press"), MechanicCompatTests.create("mechanical_press"),
			ResourceLocation.parse("minecraft:farmer"), ResourceLocation.parse("create:pressing"), shippedMill().feed(), shippedMill().collect(),
			List.of(new MachineWorkFiles.Make(Kind.PRESS.output(), 0)), 16, 1f);
	}

	private static List<MachineWorkFiles.Machine> replaceMill(List<MachineWorkFiles.Machine> files, @Nullable MachineWorkFiles.Machine mill) {
		files.removeIf(m -> m.id().equals(AliveWorkplace.id(Kind.MILL.file)));
		if (mill != null) {
			files.add(mill);
		}
		return files;
	}

	/** The shipped millstone file's feed spot, read before a test swaps the files. */
	private static volatile BlockPos millFeed = BlockPos.ZERO;

	private static void setUp(GameTestHelper helper) {
		millFeed = shippedMill().feed().at();
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
	}

	private static Villager farmer(GameTestHelper helper) {
		helper.setBlock(STATION, Kind.MILL.station());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(STATION), Kind.MILL.profession());
		helper.assertTrue(villager.getVillagerData().getProfession() == Kind.MILL.profession(), "setup: no farmer at the composter");
		helper.setDayTime(2000);
		return villager;
	}

	private static Container chest(GameTestHelper helper, ItemStack... stacks) {
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) helper.getBlockEntity(CHEST);
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	private static int count(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int ground(GameTestHelper helper, Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.isAlive() && e.getItem().is(item))
			.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static int carried(Villager villager, Item item) {
		MachineJob job = ModAttachments.MACHINE_JOB.get(villager);
		int inHand = 0;
		for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
			if (villager.getInventory().getItem(i).is(item)) {
				inHand += villager.getInventory().getItem(i).getCount();
			}
		}
		return inHand + (job == null ? 0 : job.carried().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum());
	}

	/** What the machine at {@code mill} (relative) holds of {@code item}. */
	private static long inMill(GameTestHelper helper, BlockPos mill, Item item) {
		BlockPos feed = helper.absolutePos(mill.offset(millFeed));
		return Platform.get().items().count(helper.getLevel(), List.of(feed), item);
	}

	private static int carriedNow(Villager villager) {
		MachineJob job = ModAttachments.MACHINE_JOB.get(villager);
		return job == null ? 0 : job.carried().stream().mapToInt(ItemStack::getCount).sum();
	}

	/** Every status line the worker shows from now on, each checked to have its text in en_us.json. */
	private static Set<String> listen(GameTestHelper helper, Villager villager) {
		Set<String> said = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(villager, helper.getLevel().getGameTime());
			if (e != null && said.add(e.title().getString() + ": " + e.line().getString())) {
				translated(helper, e.title());
				translated(helper, e.line());
			}
		});
		return said;
	}

	private static void translated(GameTestHelper helper, Component line) {
		if (line.getContents() instanceof TranslatableContents t) {
			helper.assertTrue(Language.getInstance().has(t.getKey()), "the status line " + t.getKey() + " has no text in en_us.json");
		}
		helper.assertTrue(!line.getString().contains("message.aliveworkplace") && !line.getString().contains("block.create"),
			"a status line shows a raw key: " + line.getString());
	}

	/**
	 * A datapack's millstone file with {@code batch} 3 and 20 wheat in the store: the farmer's trips carry at most 3 wheat
	 * each (the first exactly 3), and the store's wheat goes down by 3 at the first fetch.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_machine_files_batch_3"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_machine_files_batch_3")
	public void aFileWithBatchThreeCarriesThreeWheatATrip(GameTestHelper helper) {
		setUp(helper);
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 20));
		BlockPos mill = MachineWorkCompatTests.rig(helper, Kind.MILL, true);
		Villager farmer = farmer(helper);
		swapFiles(helper, files -> replaceMill(files, mill(3, 64, "minecraft:farmer")));
		Set<String> said = listen(helper, farmer);
		int[] most = {0};
		helper.onEachTick(() -> {
			int c = carriedNow(farmer);
			most[0] = Math.max(most[0], c);
			helper.assertTrue(c <= 3, "a trip with the file's batch 3 carries " + c + ": " + ModAttachments.MACHINE_JOB.get(farmer));
			helper.assertTrue(count(chest, Items.WHEAT) + carried(farmer, Items.WHEAT) + inMill(helper, mill, Items.WHEAT) + ground(helper, Items.WHEAT) <= 20,
				"the wheat came to more than the 20 there were");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inMill(helper, mill, Items.WHEAT) > 0 || count(chest, Kind.MILL.output()) > 0,
				"no wheat went into the millstone yet (store " + count(chest, Items.WHEAT) + ", trip " + ModAttachments.MACHINE_JOB.get(farmer) + ")");
			helper.assertTrue(most[0] == 3, "the trip carried at most " + most[0] + ", not the batch of 3 (20 in the store)");
			helper.assertTrue(said.stream().anyMatch(s -> s.contains("working the Millstone")), "the farmer never said he works the Millstone: " + said);
			MachineWorkCompatTests.clearMachines(helper);
		});
	}

	/**
	 * A datapack's millstone file with {@code batch} 1000 and 200 wheat in the store: no trip carries more than a stack,
	 * the millstone is fed, and when the farmer leaves it every one of the 200 wheat is in the store, the millstone or
	 * his load (none lost, none doubled); at no tick are there more than 200.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_machine_files_batch_1000"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_machine_files_batch_1000")
	public void aFileWithBatchAThousandCarriesAStackAndLosesNoWheat(GameTestHelper helper) {
		setUp(helper);
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 64), new ItemStack(Items.WHEAT, 64), new ItemStack(Items.WHEAT, 64),
			new ItemStack(Items.WHEAT, 8));
		BlockPos mill = MachineWorkCompatTests.rig(helper, Kind.MILL, true);
		Villager farmer = farmer(helper);
		swapFiles(helper, files -> replaceMill(files, mill(1000, 64, "minecraft:farmer")));
		MachineJob.Stage[] last = {null};
		int[] most = {0};
		boolean[] left = {false};
		helper.onEachTick(() -> {
			if (left[0]) {
				return;
			}
			MachineJob job = ModAttachments.MACHINE_JOB.get(farmer);
			int c = carriedNow(farmer);
			most[0] = Math.max(most[0], c);
			long all = count(chest, Items.WHEAT) + carried(farmer, Items.WHEAT) + inMill(helper, mill, Items.WHEAT) + ground(helper, Items.WHEAT);
			helper.assertTrue(c <= 64, "a trip with batch 1000 carries " + c + ", more than a stack: " + job);
			helper.assertTrue(all <= 200, "the wheat came to " + all + ", more than the 200 there were (store " + count(chest, Items.WHEAT)
				+ ", millstone " + inMill(helper, mill, Items.WHEAT) + ", carried " + carried(farmer, Items.WHEAT) + ")");
			if (last[0] == MachineJob.Stage.FEED && (job == null || job.stage() != MachineJob.Stage.FEED)) {
				// the tick the farmer fed the millstone: it has had one tick to work, too few to grind any
				helper.assertTrue(inMill(helper, mill, Items.WHEAT) > 0, "the farmer left the millstone without feeding it: " + job);
				helper.assertTrue(all == 200, "when the farmer left the millstone the wheat came to " + all + ", not 200 (store "
					+ count(chest, Items.WHEAT) + ", millstone " + inMill(helper, mill, Items.WHEAT) + ", carried " + carried(farmer, Items.WHEAT)
					+ ", ground " + ground(helper, Items.WHEAT) + ")");
				left[0] = true;
			}
			last[0] = job == null ? null : job.stage();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(left[0], "the farmer hasn't fed the millstone yet (store " + count(chest, Items.WHEAT) + ", trip "
				+ ModAttachments.MACHINE_JOB.get(farmer) + ")");
			helper.assertTrue(most[0] == 64, "the trip carried at most " + most[0] + ", not one full stack of the 200");
			MachineWorkCompatTests.clearMachines(helper);
		});
	}

	/** A datapack's millstone file with {@code keep} 0 (and one with -5): the store wants no flour, so no wheat goes to the turning millstone. */
	//$ gametest_ticks_batch AREA '600' '"qa_machine_files_keep_0"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_files_keep_0")
	public void aFileKeepingNoFlourSendsNoWheat(GameTestHelper helper) {
		keepNone(helper, 0);
	}

	//$ gametest_ticks_batch AREA '600' '"qa_machine_files_keep_negative"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_files_keep_negative")
	public void aFileKeepingNegativeFlourSendsNoWheat(GameTestHelper helper) {
		keepNone(helper, -5);
	}

	private static void keepNone(GameTestHelper helper, int keep) {
		setUp(helper);
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 20));
		BlockPos mill = MachineWorkCompatTests.rig(helper, Kind.MILL, true);
		Villager farmer = farmer(helper);
		swapFiles(helper, files -> replaceMill(files, mill(16, keep, "minecraft:farmer")));
		helper.onEachTick(() -> helper.assertTrue(!MachineWork.busy(farmer), "with the file's keep " + keep + " the farmer set off for the millstone: "
			+ ModAttachments.MACHINE_JOB.get(farmer)));
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(chest, Items.WHEAT) == 20, "with keep " + keep + ", " + (20 - count(chest, Items.WHEAT)) + " wheat left the store");
			helper.assertTrue(inMill(helper, mill, Items.WHEAT) == 0, "with keep " + keep + " wheat went into the millstone");
			MachineWorkCompatTests.clearMachines(helper);
			helper.succeed();
		});
	}

	/** A datapack's millstone file with {@code keep} 8: a store already holding 8 flour sends no wheat in 400 ticks. */
	//$ gametest_ticks_batch AREA '600' '"qa_machine_files_keep_8_reached"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_files_keep_8_reached")
	public void aFileKeepingEightFlourSendsNoWheatToAStoreWithEight(GameTestHelper helper) {
		setUp(helper);
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 20), new ItemStack(Kind.MILL.output(), 5), new ItemStack(Kind.MILL.output(), 3));
		BlockPos mill = MachineWorkCompatTests.rig(helper, Kind.MILL, true);
		Villager farmer = farmer(helper);
		swapFiles(helper, files -> replaceMill(files, mill(16, 8, "minecraft:farmer")));
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(chest, Items.WHEAT) == 20, "the store holds 8 flour (the file keeps 8) but " + (20 - count(chest, Items.WHEAT))
				+ " wheat went to the millstone");
			helper.assertTrue(!MachineWork.busy(farmer) && inMill(helper, mill, Items.WHEAT) == 0, "with 8 flour kept the farmer works the millstone: "
				+ ModAttachments.MACHINE_JOB.get(farmer));
			MachineWorkCompatTests.clearMachines(helper);
			helper.succeed();
		});
	}

	/** The same file, the store holding 7 flour, one short of the 8: the farmer takes wheat to the millstone. */
	//$ gametest_ticks_batch AREA '1200' '"qa_machine_files_keep_8_short"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_machine_files_keep_8_short")
	public void aFileKeepingEightFlourSendsWheatToAStoreWithSeven(GameTestHelper helper) {
		setUp(helper);
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 20), new ItemStack(Kind.MILL.output(), 7));
		BlockPos mill = MachineWorkCompatTests.rig(helper, Kind.MILL, true);
		farmer(helper);
		swapFiles(helper, files -> replaceMill(files, mill(16, 8, "minecraft:farmer")));
		helper.succeedWhen(() -> {
			helper.assertTrue(count(chest, Items.WHEAT) < 20 && inMill(helper, mill, Items.WHEAT) + count(chest, Kind.MILL.output()) > 7,
				"the store holds 7 flour, one short of the file's 8, but no wheat went to the millstone yet (store " + count(chest, Items.WHEAT) + ")");
			MachineWorkCompatTests.clearMachines(helper);
		});
	}

	/**
	 * A farmer carrying 12 wheat to the turning millstone when a reload takes the millstone file away (his job has no
	 * machine work left): the 12 come back to the store, none into the millstone, none on the ground, never more than 12.
	 */
	//$ gametest_ticks_batch AREA '600' '"qa_machine_files_gone_mid_trip"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_files_gone_mid_trip")
	public void aMillFileGoneMidTripBringsTheWheatBack(GameTestHelper helper) {
		loadBack(helper, files -> replaceMill(files, null), "the millstone file gone");
	}

	/**
	 * The same with the farmer still having machine work of another file (a press nobody built): the trip still runs and
	 * brings the 12 wheat back on foot, and every status line he shows has its text.
	 */
	//$ gametest_ticks_batch AREA '600' '"qa_machine_files_gone_other_file"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_files_gone_other_file")
	public void aMillFileGoneMidTripWithAnotherFileLeftBringsTheWheatBackOnFoot(GameTestHelper helper) {
		loadBack(helper, files -> {
			replaceMill(files, null);
			files.add(farmersPress());
			return files;
		}, "the millstone file gone, the farmer's press file left");
	}

	/**
	 * A reload replaces the millstone file by one for the mason while the farmer carries 12 wheat to it (and the farmer
	 * keeps another file of his own): he is no longer in the file's job, so the trip ends and the 12 go back to the store.
	 */
	//$ gametest_ticks_batch AREA '600' '"qa_machine_files_job_changed"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_files_job_changed")
	public void aMillFileGivenToTheMasonMidTripEndsTheFarmersTrip(GameTestHelper helper) {
		loadBack(helper, files -> {
			replaceMill(files, mill(16, 64, "minecraft:mason"));
			files.add(farmersPress());
			return files;
		}, "the millstone file given to the mason");
	}

	/** The same without the farmer having any file left. */
	//$ gametest_ticks_batch AREA '600' '"qa_machine_files_job_changed_only"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_files_job_changed_only")
	public void aMillFileGivenToTheMasonMidTripEndsTheFarmersOnlyTrip(GameTestHelper helper) {
		loadBack(helper, files -> replaceMill(files, mill(16, 64, "minecraft:mason")), "the millstone file given to the mason, no farmer file left");
	}

	private static void loadBack(GameTestHelper helper, UnaryOperator<List<MachineWorkFiles.Machine>> change, String what) {
		setUp(helper);
		Container chest = chest(helper);
		BlockPos mill = MachineWorkCompatTests.rig(helper, Kind.MILL, true);
		Villager farmer = farmer(helper);
		ModAttachments.MACHINE_JOB.set(farmer, new MachineJob(helper.absolutePos(mill), AliveWorkplace.id(Kind.MILL.file), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12))));
		swapFiles(helper, change);
		Set<String> said = listen(helper, farmer);
		helper.onEachTick(() -> {
			long all = count(chest, Items.WHEAT) + carried(farmer, Items.WHEAT) + inMill(helper, mill, Items.WHEAT) + ground(helper, Items.WHEAT);
			helper.assertTrue(all <= 12, what + ": the wheat came to " + all + ", more than the 12 carried");
			helper.assertTrue(inMill(helper, mill, Items.WHEAT) == 0, what + ": wheat went into the millstone");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!MachineWork.busy(farmer), what + ": the trip is still on: " + ModAttachments.MACHINE_JOB.get(farmer) + " (said " + said + ")");
			helper.assertTrue(count(chest, Items.WHEAT) == 12, what + ": the store got " + count(chest, Items.WHEAT) + " of the 12 wheat (ground "
				+ ground(helper, Items.WHEAT) + ")");
			helper.assertTrue(ground(helper, Items.WHEAT) == 0 && carried(farmer, Items.WHEAT) == 0, what + ": wheat left on the ground or in hand");
			MachineWorkCompatTests.clearMachines(helper);
		});
	}
}
