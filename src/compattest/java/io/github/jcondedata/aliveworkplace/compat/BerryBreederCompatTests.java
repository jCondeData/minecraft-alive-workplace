package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.berry.BerryBreeders;
import io.github.jcondedata.aliveworkplace.berry.BerryChains;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** ROADMAP 28.9 with the real Cobblemon: the Berry Breeder reads Cobblemon's berry data, plans from it and breeds. */
public class BerryBreederCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos COMPOSTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HALL = new BlockPos(1, 2, 7);
	/** Two beds side by side, east and west: one row of each parent. */
	private static final BlockPos BED_A = new BlockPos(5, 1, 3);
	private static final BlockPos BED_B = new BlockPos(6, 1, 3);

	private static ResourceLocation b(String name) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", name + "_berry");
	}

	private static ItemStack berry(String name, int count) {
		return new ItemStack(BuiltInRegistries.ITEM.get(b(name)), count);
	}

	/** Every berry and its mutations come from Cobblemon's data; Sitrus from Oran, Cheri and Figy is Lum, then Sitrus. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBerryBookComesFromCobblemonsData(GameTestHelper helper) {
		BerryChains.BerryData data = BerryChains.BerryData.EXTENSION.call(d -> d, null);
		helper.assertTrue(data != null, "no berry data");
		helper.assertTrue(data.berries().size() >= 60 && data.berries().contains(b("lum")), "berries: " + data.berries().size());
		List<BerryChains.Mutation> mutations = data.mutations();
		helper.assertTrue(mutations.contains(new BerryChains.Mutation(b("cheri"), b("oran"), b("lum"))), "Oran + Cheri = Lum is missing");
		List<BerryChains.Mutation> plan = BerryChains.plan(Set.of(b("oran"), b("cheri"), b("figy")), mutations, b("sitrus")).orElseThrow();
		helper.assertTrue(plan.equals(List.of(new BerryChains.Mutation(b("cheri"), b("oran"), b("lum")),
			new BerryChains.Mutation(b("figy"), b("lum"), b("sitrus")))), "the chain to Sitrus: " + plan);
		// The berries she sells from the start grow wild (no pair makes them).
		for (String wild : List.of("oran", "cheri", "chesto", "pecha", "rawst", "aspear", "persim")) {
			helper.assertTrue(BerryChains.madeFrom(mutations, b(wild)).isEmpty(), wild + " is made by a pair");
		}
		helper.assertTrue(data.berryOf(berry("oran", 1)).equals(b("oran")) && data.item(b("lum")).is(BuiltInRegistries.ITEM.get(b("lum"))),
			"berry items and ids");
		helper.succeed();
	}

	private static Villager breeder(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		helper.setBlock(BED_A, Blocks.FARMLAND);
		helper.setBlock(BED_B, Blocks.FARMLAND);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(COMPOSTER), PoiTypes.FARMER, ModVillagers.BERRY_BREEDER);
		helper.assertTrue(BerryBreeders.isBreeder(villager), "not a berry breeder");
		return villager;
	}

	/** The slot of {@code berry} on its page of the book; turns pages until it's there. */
	private static int find(GameTestHelper helper, ChoiceMenu menu, Villager breeder, int[] page, ResourceLocation berry) {
		ItemStack want = new ItemStack(BuiltInRegistries.ITEM.get(berry));
		for (int turn = 0; turn < 4; turn++) {
			for (int slot = 0; slot < 45; slot++) {
				if (ItemStack.isSameItem(menu.icon(slot), want)) {
					return slot;
				}
			}
			page[0]++;
			BerryBreeders.fill(menu, helper.getLevel(), breeder, page);
		}
		throw new AssertionError(berry + " isn't in the book");
	}

	/**
	 * The Done when: Oran and Cheri in the chest, Lum picked in the book; she plants them side by side, a forced
	 * harvest with a mutation gives Lum, and the book (the hall) marks it found; the Lum goes to the chest.
	 */
	//$ gametest_ticks_batch AREA '2400' '"berry_breeder"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "berry_breeder")
	public void sheBreedsLumFromOranAndCheri(GameTestHelper helper) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager breeder = breeder(helper, berry("oran", 4), berry("cheri", 4));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int[] page = {0};
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> BerryBreeders.fill(m, helper.getLevel(), breeder, page));
		int oran = find(helper, menu, breeder, page, b("oran"));
		helper.assertTrue(Boolean.TRUE.equals(menu.icon(oran).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "Oran (in the chest) isn't lit");
		page[0] = 0;
		BerryBreeders.fill(menu, helper.getLevel(), breeder, page);
		int lum = find(helper, menu, breeder, page, b("lum"));
		ItemStack lumIcon = menu.icon(lum);
		helper.assertTrue(lumIcon.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null, "Lum is lit before it's found");
		helper.assertTrue(lumIcon.get(DataComponents.LORE).lines().stream().anyMatch(l -> l.getString().contains("Oran") && l.getString().contains("Cheri")),
			"Lum's page doesn't say Oran + Cheri: " + lumIcon.get(DataComponents.LORE).lines());
		menu.press(lum, player);
		helper.assertTrue(b("lum").equals(BerryBreeders.goal(breeder)), "Lum isn't her goal: " + BerryBreeders.goal(breeder));
		BerryChains.BerryData data = BerryBreeders.data();
		boolean[] ripened = {false};
		helper.succeedWhen(() -> {
			ResourceLocation a = data.plantOf(helper.getBlockState(BED_A.above()));
			ResourceLocation c = data.plantOf(helper.getBlockState(BED_B.above()));
			helper.assertTrue(a != null && c != null && Set.of(a, c).equals(Set.of(b("oran"), b("cheri"))),
				"not planted side by side yet: " + a + ", " + c);
			if (!ripened[0]) {
				ripened[0] = true;
				// Forced: Cobblemon's own growth with a random that always mutates.
				BlockPos oranBed = b("oran").equals(a) ? BED_A.above() : BED_B.above();
				data.ripen(helper.getLevel(), helper.absolutePos(oranBed), new SingleThreadedRandomSource(1) {
					@Override
					public int nextInt(int bound) {
						return 0;
					}
				});
			}
			VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
			helper.assertTrue(hall.berriesFound().contains(b("lum")), "the hall hasn't noted Lum: " + hall.berriesFound());
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(BuiltInRegistries.ITEM.get(b("lum"))) > 0, "no Lum in the chest");
			helper.assertTrue(ModAttachments.BERRIES_PICKED.getOrElse(breeder, 0) > 0, "nothing counted as picked");
			BerryBreeders.fill(menu, helper.getLevel(), breeder, new int[] {0});
			int[] p = {0};
			int slot = find(helper, menu, breeder, p, b("lum"));
			helper.assertTrue(Boolean.TRUE.equals(menu.icon(slot).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "Lum isn't lit in the book");
		});
	}

	/** The chain to Sitrus (Lum + Figy) is planned from Oran, Cheri and Figy in her chest: Lum first. */
	//$ gametest_ticks_batch AREA '200' '"berry_breeder_chain"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "berry_breeder_chain")
	public void theChainToSitrusIsPlanned(GameTestHelper helper) {
		Villager breeder = breeder(helper, berry("oran", 2), berry("cheri", 2), berry("figy", 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BerryBreeders.choose(player, breeder, b("sitrus"));
		helper.assertTrue(b("sitrus").equals(BerryBreeders.goal(breeder)), "Sitrus isn't her goal");
		BerryChains.Mutation step = BerryBreeders.step(helper.getLevel(), breeder).orElseThrow();
		helper.assertTrue(step.equals(new BerryChains.Mutation(b("cheri"), b("oran"), b("lum"))), "first step: " + step);
		List<BerryChains.Mutation> plan = BerryChains.plan(BerryBreeders.have(helper.getLevel(), breeder), BerryBreeders.data().mutations(),
			b("sitrus")).orElseThrow();
		helper.assertTrue(plan.size() == 2 && plan.get(1).equals(new BerryChains.Mutation(b("figy"), b("lum"), b("sitrus"))), "plan: " + plan);
		helper.assertTrue(BerryBreeders.goalLine(helper.getLevel(), breeder).getString().contains("Lum"), "the book's goal line");
		helper.succeed();
	}

	/** Missing materials: with only Oran, Lum can't be her goal, and nothing is planted. */
	//$ gametest_ticks_batch AREA '400' '"berry_breeder_missing"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "berry_breeder_missing")
	public void withoutCheriLumIsOutOfReach(GameTestHelper helper) {
		Villager breeder = breeder(helper, berry("oran", 4));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BerryBreeders.choose(player, breeder, b("lum"));
		helper.assertTrue(BerryBreeders.goal(breeder) == null, "Lum became her goal without Cheri");
		// Forced as a goal anyway (an old save): she plants nothing and says why.
		BerryBreeders.setGoal(breeder, b("lum"));
		helper.runAfterDelay(300, () -> {
			helper.assertBlockPresent(Blocks.AIR, BED_A.above());
			helper.assertBlockPresent(Blocks.AIR, BED_B.above());
			helper.succeed();
		});
	}

	/** The composter broken mid-job: she plants nothing more. */
	//$ gametest_ticks_batch AREA '400' '"berry_breeder_broken"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "berry_breeder_broken")
	public void noComposterNoBreeding(GameTestHelper helper) {
		Villager breeder = breeder(helper, berry("oran", 4), berry("cheri", 4));
		BerryBreeders.setGoal(breeder, b("lum"));
		helper.destroyBlock(COMPOSTER);
		helper.runAfterDelay(300, () -> {
			helper.assertBlockPresent(Blocks.AIR, BED_A.above());
			helper.assertBlockPresent(Blocks.AIR, BED_B.above());
			helper.succeed();
		});
	}

	/** Save and reload: her goal and her plot stay. */
	//$ gametest_ticks_batch AREA '100' '"berry_breeder_save"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "berry_breeder_save")
	public void herGoalAndPlotSurviveAReload(GameTestHelper helper) {
		Villager breeder = breeder(helper);
		BerryBreeders.setGoal(breeder, b("sitrus"));
		BoundingBox box = new BoundingBox(10, 60, 10, 14, 61, 14);
		BerryBreeders.start(breeder, box);
		CompoundTag tag = new CompoundTag();
		breeder.saveWithoutId(tag);
		breeder.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.assertTrue(b("sitrus").equals(BerryBreeders.goal(copy)), "goal after reload: " + BerryBreeders.goal(copy));
		helper.assertTrue(box.equals(BerryBreeders.plot(copy)), "plot after reload: " + BerryBreeders.plot(copy));
		helper.succeed();
	}

	/** A berry picks the job at a composter (and is kept 16 a kind by porters); config berryBreeders off, it doesn't. */
	//$ gametest_ticks_batch AREA '100' '"berry_breeder_station"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "berry_breeder_station")
	public void aBerryPicksTheJobUnlessSwitchedOff(GameTestHelper helper) {
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5)));
		try {
			BerryBreeders.ENABLED = false;
			helper.assertTrue(!BerryBreeders.isBerry(berry("oran", 1)), "a berry is still a job item with the switch off");
			helper.assertTrue(Stations.choose(player, villager, berry("oran", 1)) == InteractionResult.PASS
				&& villager.getVillagerData().getProfession() == VillagerProfession.NONE, "switched off, the berry still gave a job");
		} finally {
			BerryBreeders.ENABLED = true;
		}
		helper.assertTrue(BerryBreeders.isBerry(berry("cheri", 1)) && BerryBreeders.isMulch(new ItemStack(BuiltInRegistries.ITEM.get(
			ResourceLocation.fromNamespaceAndPath("cobblemon", "surprise_mulch")))), "berries and mulch");
		Stations.choose(player, villager, berry("oran", 1));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.BERRY_BREEDER,
			"the berry made a " + villager.getVillagerData().getProfession());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.work.Partners.types(ModVillagers.BERRY_BREEDER).equals(Set.of("grass", "bug")),
			"partner types");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.store.Porters.keeps(ModVillagers.BERRY_BREEDER, berry("lum", 1), false)
			== BerryBreeders.KEEP_EACH, "porters leave 16 of each berry");
		helper.succeed();
	}
}
