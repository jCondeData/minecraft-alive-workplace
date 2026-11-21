package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane C, 2026-11-21, lane-c-1121-0653): ROADMAP 50.12's rebuilt Pokémon Center (default look and Mountain Lodge,
 * commit 4730ef16e), written from its brief (tools/blueprints/briefs/pokemon_center.md), not from the code. With the real
 * Cobblemon: a builder builds the new default I and Lodge I to done and a jobless villager by the Healing Machine
 * (template (6, 1, 7), stood at (6, 1, 8)), sneak-clicked with a honey bottle, becomes the Nurse there and heals a team
 * in it; a restart mid-STRUCTURE and a Healing Machine and PC broken after they went in still end as drawn; Level I to II
 * on a standing new I keeps the machine, the PC and the chests (with what's in them) and adds the Pasture Block and the
 * Shop Counter at their new cells, which a villager takes as job sites; and a standing OLD Level I (blueprint_history)
 * upgraded to the new II keeps its machine, PC and barrels and gets the new Pasture Block and Shop Counter.
 */
public class QaRebuiltPokemonCenter1121cCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 3);
	private static final BlockPos MACHINE = new BlockPos(6, 1, 7);
	private static final BlockPos NURSE_SPOT = new BlockPos(6, 1, 8);
	private static final BlockPos PC = new BlockPos(10, 1, 7);
	/** Level II's new cells (50.12): default and Lodge both. */
	private static final BlockPos PASTURE_DEFAULT = new BlockPos(9, 1, 19);
	private static final BlockPos PASTURE_LODGE = new BlockPos(10, 1, 19);
	private static final BlockPos COUNTER = new BlockPos(15, 1, 7);

	// ---- builds of Level I ------------------------------------------------------------------------------------------

	/** The new default I, built from barrels holding exactly its materials; then a Nurse takes its machine and heals in it. */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"qa_pc1121c_default_one"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "qa_pc1121c_default_one")
	public void theNewDefaultIIsBuiltAndItsHealingMachineWorksForANurse(GameTestHelper helper) {
		buildThenNurse(helper, StarterBlueprints.POKEMON_CENTER);
	}

	/** The new Lodge I (only its II was built with Cobblemon before): built to done, then the Nurse heals in its machine. */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"qa_pc1121c_lodge_one"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "qa_pc1121c_lodge_one")
	public void theNewLodgeIIsBuiltAndItsHealingMachineWorksForANurse(GameTestHelper helper) {
		buildThenNurse(helper, StarterBlueprints.POKEMON_CENTER_LODGE);
	}

	/** A restart (sites and builder saved and loaded) mid-STRUCTURE of the new default I: it finishes as drawn. */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"qa_pc1121c_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "qa_pc1121c_reload")
	public void aRestartMidStructureOfTheNewDefaultIStillEndsAsDrawn(GameTestHelper helper) {
		Stage s = stage(helper, StarterBlueprints.POKEMON_CENTER.id(), true);
		Villager[] builder = {s.builder()};
		BuildSite started = Builders.start(s.level(), builder[0], null, s.id(), s.placement());
		BuildPlan plan = started.plan(s.level());
		UUID[] site = {started.id()};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(s.level()).get(site[0]);
			if (reloaded[0] || now == null || now.stage() != BuildPlan.Stage.STRUCTURE || now.cursor() < 60) {
				return;
			}
			reloaded[0] = true;
			builder[0] = reload(helper, s.level(), builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never restarted mid-STRUCTURE");
			assertDone(helper, s, site[0], plan);
			assertCobblemonAt(helper, s, MACHINE, "the Healing Machine");
			assertCobblemonAt(helper, s, PC, "the PC");
		});
	}

	/** The Lodge I's Healing Machine and PC broken (a player, a creeper) right after they went in: both are put back. */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"qa_pc1121c_broken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "qa_pc1121c_broken")
	public void aHealingMachineAndPcBrokenMidBuildArePutBack(GameTestHelper helper) {
		Stage s = stage(helper, StarterBlueprints.POKEMON_CENTER_LODGE.id(), true);
		BuildSite started = Builders.start(s.level(), s.builder(), null, s.id(), s.placement());
		BuildPlan plan = started.plan(s.level());
		UUID site = started.id();
		boolean[] machineBroken = {false};
		boolean[] pcBroken = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(s.level()).get(site);
			if (now == null) {
				return;
			}
			BlockPos machine = s.placement().origin().offset(MACHINE);
			BlockPos pc = s.placement().origin().offset(PC);
			if (!machineBroken[0] && isCobblemon(s.level().getBlockState(machine))) {
				machineBroken[0] = true;
				s.level().destroyBlock(machine, false);
			}
			if (!pcBroken[0] && isCobblemon(s.level().getBlockState(pc))) {
				pcBroken[0] = true;
				s.level().destroyBlock(pc, false);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(machineBroken[0] && pcBroken[0], "the machine (" + machineBroken[0] + ") or PC (" + pcBroken[0] + ") never went in");
			assertDone(helper, s, site, plan);
			assertCobblemonAt(helper, s, MACHINE, "the Healing Machine broken mid-build");
			assertCobblemonAt(helper, s, PC, "the PC broken mid-build");
			assertCobblemonAt(helper, s, PC.above(), "the PC's top half");
		});
	}

	// ---- Level I to II ----------------------------------------------------------------------------------------------

	/** Default I standing (new drawing) upgraded to II: machine, PC and chests kept, Pasture Block and Shop Counter new. */
	//$ gametest_ticks_batch HUGE_AREA '50000' '"qa_pc1121c_up_default"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 50000, batch = "qa_pc1121c_up_default")
	public void theNewDefaultIUpgradedToIIKeepsItsMachinePcAndChests(GameTestHelper helper) {
		upgrade(helper, StarterBlueprints.POKEMON_CENTER.id(), null, StarterBlueprints.POKEMON_CENTER_2.id(),
			List.of(new BlockPos(3, 1, 9), new BlockPos(9, 1, 9)), PASTURE_DEFAULT);
	}

	/** Lodge I standing (new drawing) upgraded to Lodge II. */
	//$ gametest_ticks_batch HUGE_AREA '50000' '"qa_pc1121c_up_lodge"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 50000, batch = "qa_pc1121c_up_lodge")
	public void theNewLodgeIUpgradedToIIKeepsItsMachinePcAndChests(GameTestHelper helper) {
		upgrade(helper, StarterBlueprints.POKEMON_CENTER_LODGE.id(), null, StarterBlueprints.POKEMON_CENTER_LODGE_2.id(),
			List.of(new BlockPos(2, 1, 9), new BlockPos(10, 1, 10)), PASTURE_LODGE);
	}

	/**
	 * A default I of the OLD drawing (v0.145.0, six barrels along z 9) standing on a live server, upgraded to the new II:
	 * its machine, PC and barrels (and what's in them) stay; the new Pasture Block and Shop Counter go in and work.
	 */
	//$ gametest_ticks_batch HUGE_AREA '50000' '"qa_pc1121c_up_old_default"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 50000, batch = "qa_pc1121c_up_old_default")
	public void anOldDrawingDefaultIUpgradedToTheNewIIKeepsItsMachinePcAndBarrels(GameTestHelper helper) {
		upgrade(helper, StarterBlueprints.POKEMON_CENTER.id(), "v0.145.0", StarterBlueprints.POKEMON_CENTER_2.id(),
			List.of(new BlockPos(2, 1, 9), new BlockPos(3, 1, 9), new BlockPos(4, 1, 9), new BlockPos(8, 1, 9), new BlockPos(9, 1, 9),
				new BlockPos(10, 1, 9)), PASTURE_DEFAULT);
	}

	/** And an OLD Lodge I (v0.146.0, barrels along z 9 and one at the front) upgraded to the new Lodge II. */
	//$ gametest_ticks_batch HUGE_AREA '50000' '"qa_pc1121c_up_old_lodge"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 50000, batch = "qa_pc1121c_up_old_lodge")
	public void anOldDrawingLodgeIUpgradedToTheNewIIKeepsItsMachinePcAndBarrels(GameTestHelper helper) {
		upgrade(helper, StarterBlueprints.POKEMON_CENTER_LODGE.id(), "v0.146.0", StarterBlueprints.POKEMON_CENTER_LODGE_2.id(),
			List.of(new BlockPos(2, 1, 9), new BlockPos(3, 1, 9), new BlockPos(4, 1, 9), new BlockPos(8, 1, 9), new BlockPos(9, 1, 9),
				new BlockPos(10, 1, 9), new BlockPos(10, 1, 1)), PASTURE_LODGE);
	}

	// ---- shared -----------------------------------------------------------------------------------------------------

	record Stage(ServerLevel level, ResourceLocation id, BlueprintData.Placement placement, Villager builder, ServerPlayer player) {
	}

	private static void buildThenNurse(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Stage s = stage(helper, entry.id(), false);
		BuildSite site = Builders.start(s.level(), s.builder(), null, s.id(), s.placement());
		BuildPlan plan = site.plan(s.level());
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(stock.size() <= 108, entry.id() + " needs " + stock.size() + " stacks, more than four barrels (108)");
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		boolean[] healing = {false};
		List<Pokemon> team = new ArrayList<>();
		helper.succeedWhen(() -> {
			if (!healing[0]) {
				assertDone(helper, s, site.id(), plan);
				assertCobblemonAt(helper, s, MACHINE, "the Healing Machine");
				assertCobblemonAt(helper, s, PC, "the PC");
				BlockPos machine = s.placement().origin().offset(MACHINE);
				Villager nurse = takes(helper, s.player(), machine, s.placement().origin().offset(NURSE_SPOT), Items.HONEY_BOTTLE, ModVillagers.NURSE,
					entry.id() + "'s Healing Machine");
				helper.assertTrue(machine.equals(Nurses.machine(s.level(), nurse)), entry.id() + ": the Nurse's machine is " + Nurses.machine(s.level(), nurse)
					+ ", not the built one at " + machine.toShortString());
				for (String name : List.of("pikachu", "bulbasaur")) {
					Pokemon pokemon = PokemonProperties.Companion.parse(name + " level=20", " ", "=").create();
					Cobblemon.INSTANCE.getStorage().getParty(s.player()).add(pokemon);
					pokemon.setCurrentHealth(1);
					team.add(pokemon);
				}
				helper.setDayTime(2000);
				Nurses.resetCooldowns();
				Nurses.treat(s.player(), nurse);
				helper.assertTrue(s.level().getBlockEntity(machine) instanceof HealingMachineBlockEntity be && be.isInUse(),
					entry.id() + ": the built Healing Machine isn't healing the team");
				healing[0] = true;
				throw new GameTestAssertException("healing");
			}
			for (Pokemon pokemon : team) {
				helper.assertTrue(pokemon.isFullHealth(), pokemon.getSpecies().getName() + " at " + pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth());
			}
		});
	}

	/**
	 * Stands {@code one} at ORIGIN: the live drawing built by the builder to done (as a player gets it: with this pack's
	 * blocks, Carved Wood's birch chests among them), or {@code oldVersion} from blueprint_history placed and recorded
	 * finished (a building of a drawing no longer shipped). Then a stack of glistering melon goes in each of
	 * {@code containers} and the builder builds {@code two} over it (free materials).
	 */
	private static void upgrade(GameTestHelper helper, ResourceLocation one, String oldVersion, ResourceLocation two, List<BlockPos> containers,
								BlockPos pasture) {
		Stage s = stage(helper, two, true);
		ServerLevel level = s.level();
		BlockPos origin = s.placement().origin();
		UUID[] first = {null};
		if (oldVersion == null) {
			first[0] = Builders.start(level, s.builder(), null, one, s.placement()).id();
		} else {
			oldTemplate(level, one, oldVersion).placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(50_12L), 2);
			BuildSiteManager.get(level).recordFinished(one, s.placement(), s.player().getUUID());
		}
		UUID[] site = {null};
		BuildPlan[] plan = {null};
		helper.onEachTick(() -> {
			if (site[0] != null || first[0] != null && BuildSiteManager.get(level).get(first[0]) != null) {
				return;
			}
			if (first[0] != null) {
				helper.assertTrue(BuildSiteManager.get(level).finishedAt(level, one, origin).isPresent(), "(setup) " + one + " isn't recorded finished");
			}
			for (BlockPos c : containers) {
				helper.assertTrue(level.getBlockEntity(origin.offset(c)) instanceof Container, "(setup) " + one + " " + oldVersion + ": no container at "
					+ c.toShortString() + ": " + level.getBlockState(origin.offset(c)));
				((Container) level.getBlockEntity(origin.offset(c))).setItem(0, new ItemStack(Items.GLISTERING_MELON_SLICE, 7));
			}
			assertCobblemonAt(helper, s, MACHINE, "(setup) the standing I's Healing Machine");
			assertCobblemonAt(helper, s, PC, "(setup) the standing I's PC");
			BuildSite started = Builders.start(level, s.builder(), null, two, s.placement());
			plan[0] = started.plan(level);
			site[0] = started.id();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(site[0] != null, "(setup) Level I not standing yet");
			// an old drawing's barrels and PC are never broken (block entities are kept), so where one stands and the new
			// drawing has something else (another block, the PC facing west, not north) is excused; the new drawing's own
			// chests, machine and PC are the same blocks in the same cells in I and II
			List<BlockPos> excused = new ArrayList<>();
			if (oldVersion != null) {
				excused.addAll(containers);
				excused.add(PC);
				excused.add(PC.above());
			}
			assertDone(helper, s, site[0], plan[0], excused);
			assertCobblemonAt(helper, s, MACHINE, "the Healing Machine after the upgrade");
			assertCobblemonAt(helper, s, PC, "the PC after the upgrade");
			Block pastureBlock = BuiltInRegistries.BLOCK.get(ModVillagers.PASTURE_BLOCK);
			helper.assertTrue(level.getBlockState(origin.offset(pasture)).is(pastureBlock), two + ": no Pasture Block at " + pasture.toShortString()
				+ ": " + level.getBlockState(origin.offset(pasture)));
			helper.assertTrue(level.getBlockState(origin.offset(COUNTER)).is(ModBlocks.SHOP_COUNTER), two + ": no Shop Counter at "
				+ COUNTER.toShortString() + ": " + level.getBlockState(origin.offset(COUNTER)));
			List<String> lost = new ArrayList<>();
			for (BlockPos c : containers) {
				if (!(level.getBlockEntity(origin.offset(c)) instanceof Container box) || box.countItem(Items.GLISTERING_MELON_SLICE) != 7) {
					lost.add(c.toShortString() + "=" + level.getBlockState(origin.offset(c)).getBlock());
				}
			}
			helper.assertTrue(lost.isEmpty(), "the I's containers (with 7 glistering melon each) lost or emptied by the upgrade at " + lost);
			helper.assertTrue(BuildSiteManager.get(level).finishedAt(level, two, origin).isPresent(), two + " isn't recorded finished after the upgrade");
			takes(helper, s.player(), origin.offset(COUNTER), null, cobblemon("poke_ball"), ModVillagers.POKEMON_TRADER, two + "'s new Shop Counter");
			takes(helper, s.player(), origin.offset(pasture), null, Items.HONEY_BOTTLE, ModVillagers.HABITAT_KEEPER, two + "'s new Pasture Block");
			takes(helper, s.player(), origin.offset(MACHINE), origin.offset(NURSE_SPOT), Items.HONEY_BOTTLE, ModVillagers.NURSE, two + "'s kept Healing Machine");
		});
	}

	/** The test area cleared of leftovers, a bench and a builder at x 2-3, rules set for the test and put back after it. */
	private static Stage stage(GameTestHelper helper, ResourceLocation id, boolean free) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		QaRebuildCreate1117e3CompatTests.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			BuildSiteManager.get(level).forgetFinished(placement);
			Cobblemon.INSTANCE.getStorage().getParty(player).clearParty();
			level.getServer().getPlayerList().remove(player);
		});
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		return new Stage(level, id, placement, builder, player);
	}

	/** {@code site} is finished and nothing of {@code plan} is left undone. */
	private static void assertDone(GameTestHelper helper, Stage s, UUID site, BuildPlan plan) {
		assertDone(helper, s, site, plan, List.of());
	}

	/** As above, but cells in {@code excused} (template) may differ. */
	private static void assertDone(GameTestHelper helper, Stage s, UUID site, BuildPlan plan, List<BlockPos> excused) {
		BuildSite now = BuildSiteManager.get(s.level()).get(site);
		helper.assertTrue(now == null, s.id() + " still building: " + (now == null ? "" : now.stage() + " " + now.status() + " missing " + now.missing()));
		List<BlockPos> unfinished = new ArrayList<>(plan.unfinished(s.level()));
		unfinished.removeIf(p -> excused.contains(p.subtract(s.placement().origin())));
		helper.assertTrue(unfinished.isEmpty(), s.id() + ": " + unfinished.size() + " blocks not as drawn, e.g. " + unfinished.stream().limit(4)
			.map(p -> p.subtract(s.placement().origin()).toShortString() + "=" + s.level().getBlockState(p) + " (drawn " + plan.targets().get(p) + ")").toList());
	}

	private static boolean isCobblemon(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("cobblemon");
	}

	private static void assertCobblemonAt(GameTestHelper helper, Stage s, BlockPos spot, String what) {
		BlockState there = s.level().getBlockState(s.placement().origin().offset(spot));
		helper.assertTrue(isCobblemon(there), s.id() + ": " + what + " isn't at " + spot.toShortString() + ": " + there);
	}

	/** A restart: the sites and the builder saved and loaded back. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	/** The drawing of {@code id} saved as {@code version} in blueprint_history, as a structure template. */
	private static StructureTemplate oldTemplate(ServerLevel level, ResourceLocation id, String version) {
		ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "blueprint_history/" + id.getPath() + "/" + version + ".nbt");
		try (InputStream in = level.getServer().getResourceManager().getResource(file)
				.orElseThrow(() -> new GameTestAssertException("no " + file + " shipped")).open()) {
			CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			StructureTemplate template = new StructureTemplate();
			template.load(Lookup.lookup(BuiltInRegistries.BLOCK), tag);
			return template;
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("can't read " + file + ": " + e);
		}
	}

	/**
	 * A jobless villager standing at {@code stand} (absolute; null: the first open cell beside {@code station}),
	 * sneak-clicked with {@code item}, takes {@code job} at that very block.
	 */
	private static Villager takes(GameTestHelper helper, ServerPlayer player, BlockPos station, BlockPos stand, Item item, VillagerProfession job, String what) {
		ServerLevel level = helper.getLevel();
		if (stand == null) {
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos p = station.relative(d);
				if (open(level, p) && open(level, p.above()) && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
					stand = p;
					break;
				}
			}
		}
		helper.assertTrue(stand != null, what + ": no open cell beside it at " + station.toShortString());
		helper.assertTrue(open(level, stand) && open(level, stand.above()), what + ": a villager can't stand at " + stand.toShortString() + ": "
			+ level.getBlockState(stand) + " / " + level.getBlockState(stand.above()));
		Villager villager = EntityType.VILLAGER.create(level);
		villager.moveTo(Vec3.atBottomCenterOf(stand));
		level.addFreshEntity(villager);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, true, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		player.setShiftKeyDown(false);
		VillagerProfession got = villager.getVillagerData().getProfession();
		helper.assertTrue(got == job, what + ": " + BuiltInRegistries.ITEM.getKey(item) + " gave " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(got)
			+ ", not " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(job));
		Optional<BlockPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
		helper.assertTrue(site.equals(Optional.of(station)), what + ": works at " + site + ", not " + station.toShortString());
		return villager;
	}

	private static boolean open(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	private static Item cobblemon(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
		if (item == Items.AIR) {
			throw new GameTestAssertException("no cobblemon:" + path);
		}
		return item;
	}
}
