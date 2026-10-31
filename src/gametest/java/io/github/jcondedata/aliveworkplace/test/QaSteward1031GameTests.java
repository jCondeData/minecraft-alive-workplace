package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-10-31) for ROADMAP 27.1a and 27.19, written from the spec, for the boundaries the earlier Steward tests leave
 * open: a Builder levelling into Journeyman through his own XP, Expert and Master Builders, the refusal's exact sentence
 * and the plan kept, a Steward moving halls; the City Plan's shapeless recipe in any slot and with a lookalike; the other
 * hall's border to the block; a stranger's protected village by hand; the shopping list at exactly 8 and its "his
 * builds" only; the pause counting only his builds; no message to the owner with nothing to buy; the ledger's save at
 * negative coordinates. The village is QaStewardSafetyGameTests' (hall, table and builder, a Steward, Homes east).
 */
public class QaSteward1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final BlockPos HALL_A = new BlockPos(5, 2, 5);
	private static final BlockPos HALL_B = new BlockPos(24, 2, 24);

	record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.stewardSites(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		PlayerBuilt.get(level).forget(near);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		VillageHalls.forgetNearest();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(level, hall, owner, steward);
	}

	/** Waiting sites at x = 20, 25, ... (z = 24); {@code steward} false: a player's own build, not the Steward's. */
	private static List<BuildSite> waitingSites(GameTestHelper helper, Village v, List<Map<Item, Integer>> missings, boolean steward) {
		List<BuildSite> out = new ArrayList<>();
		int n = 0;
		for (Map<Item, Integer> missing : missings) {
			BlueprintData.Placement placement = new BlueprintData.Placement(v.level().dimension().location(),
				helper.absolutePos(new BlockPos(20 + 5 * n++, 2, steward ? 24 : 4)), Rotation.NONE, Mirror.NONE);
			BuildSite site = BuildSiteManager.get(v.level()).create(v.owner().getUUID(), "", AliveWorkplace.id("stone_house"), placement);
			if (steward) {
				site.setStewardHall(v.hall());
			}
			site.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
			site.setMissing(new HashMap<>(missing));
			out.add(site);
		}
		return out;
	}

	private static ItemStack planFor(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ItemStack stack = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(helper.getLevel(), player, stack, helper.absolutePos(hall));
		return stack;
	}

	private static BoundingBox box(GameTestHelper helper, int x0, int z0, int x1, int z1) {
		BlockPos a = helper.absolutePos(new BlockPos(x0, 2, z0));
		BlockPos b = helper.absolutePos(new BlockPos(x1, 5, z1));
		return BoundingBox.fromCorners(a, b);
	}

	// ---- 27.1a: who may be Steward ------------------------------------------------------------------------------------

	/**
	 * The boundary through play: an Apprentice Builder one XP short of Journeyman is refused with the exact sentence and
	 * keeps his City Plan; the XP that makes him a Journeyman (earned as a builder earns it) lets him in.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1031Threshold"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1031Threshold")
	public void aBuilderOneXpShortIsRefusedAndTheNextXpLetsHimIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, seen);
		ItemStack plan = planFor(helper, player, HALL_A);
		Villager builder = helper.spawn(EntityType.VILLAGER, HALL_A.east(2));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
		int journeymanXp = net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(2);
		builder.setVillagerXp(journeymanXp - 1);
		helper.assertTrue(!Stewards.qualifies(builder), "an Apprentice Builder one XP short of Journeyman qualifies");
		helper.assertTrue(Stewards.appoint(player, builder, plan) == InteractionResult.CONSUME
			&& builder.getVillagerData().getProfession() == ModVillagers.BUILDER, "an Apprentice Builder one XP short was appointed");
		String expected = builder.getDisplayName().getString()
			+ " isn't ready to be a Steward: only a Builder who has reached Journeyman or higher can run a village";
		helper.assertTrue(seen.contains(expected), "the refusal doesn't read \"" + expected + "\": " + seen);
		helper.assertTrue(plan.is(ModItems.CITY_PLAN) && plan.getCount() == 1, "a refusal used up the City Plan: " + plan);
		// one more XP, the way his building earns it (a trait may add to it; he only has to cross the line)
		for (int i = 0; i < 20 && builder.getVillagerData().getLevel() < Stewards.MIN_BUILDER_LEVEL; i++) {
			BuilderLevels.addXp(level, builder, 1, player.getUUID());
		}
		helper.assertTrue(builder.getVillagerData().getLevel() == Stewards.MIN_BUILDER_LEVEL,
			"setup: the XP didn't make him a Journeyman: " + builder.getVillagerData() + " xp " + builder.getVillagerXp());
		helper.assertTrue(Stewards.appoint(player, builder, plan) == InteractionResult.SUCCESS
			&& builder.getVillagerData().getProfession() == ModVillagers.STEWARD, "a fresh Journeyman Builder wasn't appointed: " + builder.getVillagerData());
		helper.assertTrue(builder.getVillagerData().getLevel() == 1, "not a Novice Steward: " + builder.getVillagerData());
		helper.succeed();
	}

	/** "Journeyman or higher": an Expert and a Master Builder both qualify, and a Master starts as a Novice Steward too. */
	//$ gametest_ticks_batch AREA '40' '"qa1031Master"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031Master")
	public void expertAndMasterBuildersQualify(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager expert = helper.spawn(EntityType.VILLAGER, HALL_A.south(2));
		expert.setVillagerData(expert.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(4));
		expert.setVillagerXp(150);
		helper.assertTrue(Stewards.qualifies(expert), "an Expert Builder doesn't qualify");
		Villager master = helper.spawn(EntityType.VILLAGER, HALL_A.east(2));
		master.setVillagerData(master.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(5));
		master.setVillagerXp(250);
		helper.assertTrue(Stewards.qualifies(master), "a Master Builder doesn't qualify");
		helper.assertTrue(Stewards.appoint(player, master, planFor(helper, player, HALL_A)) == InteractionResult.SUCCESS, "a Master Builder wasn't appointed");
		helper.assertTrue(master.getVillagerData().getProfession() == ModVillagers.STEWARD && master.getVillagerData().getLevel() == 1,
			"a Master Builder isn't a Novice Steward: " + master.getVillagerData());
		helper.succeed();
	}

	/** A Steward always qualifies: moved to a second hall (whatever his Builder past) he is its Steward, his Steward level kept. */
	//$ gametest_ticks_batch AREA '40' '"qa1031Move"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031Move")
	public void aStewardMovesToAnotherHallKeepingHisLevel(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
		helper.assertTrue(Stewards.appoint(player, steward, planFor(helper, player, HALL_A)) == InteractionResult.SUCCESS, "setup: not appointed at A");
		steward.setVillagerData(steward.getVillagerData().setLevel(3));
		steward.setVillagerXp(80);
		steward.teleportTo(helper.absolutePos(HALL_B).getX() + 2.5, helper.absolutePos(HALL_B).getY(), helper.absolutePos(HALL_B).getZ() + 0.5);
		helper.assertTrue(Stewards.qualifies(steward), "a Steward doesn't qualify to move");
		helper.assertTrue(Stewards.appoint(player, steward, planFor(helper, player, HALL_B)) == InteractionResult.SUCCESS, "the Steward couldn't move to hall B");
		helper.assertTrue(Stewards.hallOf(helper.getLevel(), steward).equals(Optional.of(helper.absolutePos(HALL_B))),
			"not the Steward of hall B: " + Stewards.hallOf(helper.getLevel(), steward));
		helper.assertTrue(steward.getVillagerData().getLevel() == 3, "moving halls reset his Steward level: " + steward.getVillagerData());
		helper.succeed();
	}

	/** The recipe is shapeless: the three in any slots of a 3×3 grid make a City Plan; a lookalike or a fourth item makes nothing. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theCityPlanRecipeIsShapelessAndExact(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack e = ItemStack.EMPTY;
		CraftingInput spread = CraftingInput.of(3, 3, List.of(new ItemStack(Items.HEART_OF_THE_SEA), e, e, e, e, new ItemStack(ModItems.BLANK_BLUEPRINT),
			e, new ItemStack(Items.MAP), e));
		helper.assertTrue(made(level, spread).is(ModItems.CITY_PLAN), "the three spread over a 3×3 grid make " + made(level, spread));
		CraftingInput lookalike = CraftingInput.of(3, 1, List.of(new ItemStack(Items.MAP), new ItemStack(ModItems.BLANK_BLUEPRINT),
			new ItemStack(Items.NAUTILUS_SHELL)));
		helper.assertTrue(!made(level, lookalike).is(ModItems.CITY_PLAN), "a nautilus shell stands in for the heart of the sea");
		CraftingInput prismarine = CraftingInput.of(3, 1, List.of(new ItemStack(Items.MAP), new ItemStack(ModItems.BLANK_BLUEPRINT),
			new ItemStack(Items.PRISMARINE_CRYSTALS)));
		helper.assertTrue(!made(level, prismarine).is(ModItems.CITY_PLAN), "prismarine crystals stand in for the heart of the sea");
		CraftingInput extra = CraftingInput.of(2, 2, List.of(new ItemStack(Items.MAP), new ItemStack(ModItems.BLANK_BLUEPRINT),
			new ItemStack(Items.HEART_OF_THE_SEA), new ItemStack(Items.PAPER)));
		helper.assertTrue(!made(level, extra).is(ModItems.CITY_PLAN), "a fourth ingredient still makes a City Plan");
		CraftingInput twoHearts = CraftingInput.of(2, 1, List.of(new ItemStack(Items.MAP), new ItemStack(Items.HEART_OF_THE_SEA)));
		helper.assertTrue(!made(level, twoHearts).is(ModItems.CITY_PLAN), "a City Plan without the Blank Blueprint");
		helper.succeed();
	}

	private static ItemStack made(ServerLevel level, CraftingInput input) {
		return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).map(r -> r.value().assemble(input, level.registryAccess()))
			.orElse(ItemStack.EMPTY);
	}

	// ---- 27.19: zones, halls, protection ----------------------------------------------------------------------------

	/**
	 * "Never nearer another hall", to the block: with a second hall 18 east of his, a plan whose east edge is exactly
	 * half-way is his; one block further east it is the next village's.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1031Halfway"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031Halfway")
	public void aPlanToTheHalfwayLineIsHisAndOneBlockMoreIsNot(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(HALL.east(18), ModBlocks.VILLAGE_HALL); // (20, 2, 14): half-way is x = 11
		VillageHalls.forgetNearest();
		BoundingBox upTo = box(helper, 8, 12, 11, 16);
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), upTo, false).isEmpty(),
			"a plan ending exactly half-way between the halls is refused: " + StewardSafety.check(v.level(), v.hall(), upTo, false));
		BoundingBox past = box(helper, 8, 12, 12, 16);
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), past, false).equals(Optional.of(StewardSafety.Refusal.OTHER_VILLAGE)),
			"a plan reaching one block nearer the other hall is allowed: " + StewardSafety.check(v.level(), v.hall(), past, false));
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), past, true).isPresent(), "the owner's approval by hand builds in the next village");
		helper.succeed();
	}

	/**
	 * A stranger's protected village: refused even by the owner's own hand. The same hall owned by his own hall's owner
	 * isn't a protected refusal (still another village where it's nearer); our own ledger mark, by hand, is allowed.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1031Protected"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031Protected")
	public void aStrangersProtectedVillageIsRefusedEvenByHand(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos other = helper.absolutePos(HALL.east(18));
		helper.setBlock(HALL.east(18), ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		VillageHallBlockEntity foreign = (VillageHallBlockEntity) v.level().getBlockEntity(other);
		foreign.setOwner(UUID.randomUUID(), "qaStranger");
		foreign.setProtected(true);
		Leftovers.after(helper, () -> foreign.setProtected(false));
		BoundingBox theirs = box(helper, 14, 12, 18, 16);
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), theirs, true).equals(Optional.of(StewardSafety.Refusal.PROTECTED)),
			"a stranger's protected village, by hand: " + StewardSafety.check(v.level(), v.hall(), theirs, true));
		foreign.setOwner(v.owner().getUUID(), v.owner().getGameProfile().getName());
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), theirs, true).equals(Optional.of(StewardSafety.Refusal.OTHER_VILLAGE)),
			"his own owner's second village: " + StewardSafety.check(v.level(), v.hall(), theirs, true));
		BoundingBox ours = box(helper, 6, 12, 8, 16);
		PlayerBuilt.get(v.level()).mark(helper.absolutePos(new BlockPos(7, 2, 14)));
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), ours, false).equals(Optional.of(StewardSafety.Refusal.PLAYER_BUILT)),
			"a marked section, not by hand: " + StewardSafety.check(v.level(), v.hall(), ours, false));
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), ours, true).isEmpty(),
			"the owner's approval by hand doesn't override the ledger: " + StewardSafety.check(v.level(), v.hall(), ours, true));
		helper.succeed();
	}

	// ---- 27.19: the ledger --------------------------------------------------------------------------------------------

	/** The ledger's save and reload at negative coordinates and below y = 0: each mark keeps its own section, no neighbours. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theLedgerSavesNegativeSectionsExactly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PlayerBuilt ledger = new PlayerBuilt();
		List<BlockPos> marks = List.of(new BlockPos(-1, -1, -1), new BlockPos(-16, -64, 15), new BlockPos(29_999_000, 300, -29_999_000), new BlockPos(0, 0, 0));
		marks.forEach(ledger::mark);
		PlayerBuilt loaded = PlayerBuilt.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(loaded.size() == marks.size(), "the reloaded ledger has " + loaded.size() + " sections, not " + marks.size());
		for (BlockPos p : marks) {
			helper.assertTrue(loaded.marked(p), "the mark at " + p + " was lost");
		}
		helper.assertTrue(loaded.marked(new BlockPos(-16, -1, -16)), "the rest of the section of (-1, -1, -1) isn't marked");
		helper.assertTrue(!loaded.marked(new BlockPos(-17, -1, -1)) && !loaded.marked(new BlockPos(-1, -17, -1)),
			"the section next to (-1, -1, -1) is marked");
		helper.assertTrue(!loaded.marked(new BlockPos(1, 16, 1)) && loaded.marked(new BlockPos(15, 15, 15)), "the origin's section is wrong");
		helper.succeed();
	}

	// ---- 27.19: the shopping list, the owner, the pause -----------------------------------------------------------

	/**
	 * Exactly 8 kinds over two of his waiting builds: all 8 listed. A player's own waiting build and one of his with
	 * nothing missing add nothing.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1031Eight"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031Eight")
	public void exactlyEightKindsAreAllListedAndOnlyHisBuildsCount(GameTestHelper helper) {
		Village v = village(helper);
		waitingSites(helper, v, List.of(Map.of(Items.GLASS, 30, Items.OAK_PLANKS, 20, Items.STONE_BRICKS, 15, Items.BRICKS, 9),
			Map.of(Items.COBBLESTONE, 7, Items.OAK_LOG, 12, Items.TORCH, 10, Items.WHITE_WOOL, 5), Map.of()), true);
		waitingSites(helper, v, List.of(Map.of(Items.DIAMOND_BLOCK, 99, Items.GLASS, 50)), false);
		List<Map.Entry<Item, Integer>> list = StewardSafety.shoppingList(v.level(), v.hall());
		List<Map.Entry<Item, Integer>> expected = List.of(Map.entry(Items.GLASS, 30), Map.entry(Items.OAK_PLANKS, 20), Map.entry(Items.STONE_BRICKS, 15),
			Map.entry(Items.OAK_LOG, 12), Map.entry(Items.TORCH, 10), Map.entry(Items.BRICKS, 9), Map.entry(Items.COBBLESTONE, 7), Map.entry(Items.WHITE_WOOL, 5));
		helper.assertTrue(list.equals(expected), "the shopping list is " + list + ", not " + expected);
		helper.succeed();
	}

	/** The pause counts only his builds: two of a player's own waiting a day don't stop him; two of his, with a third fresh, do. */
	//$ gametest_ticks_batch AREA '40' '"qa1031PauseHis"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1031PauseHis")
	public void thePauseCountsOnlyHisOwnBuilds(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		long now = level.getGameTime();
		List<BuildSite> players = waitingSites(helper, v, List.of(Map.of(Items.GLASS, 4), Map.of(Items.GLASS, 4)), false);
		players.forEach(s -> s.setWaitingSince(now - StewardSafety.PAUSE_TICKS * 2));
		helper.assertTrue(!StewardSafety.paused(level, v.hall()), "a player's own builds waiting two days pause the Steward");
		List<BuildSite> his = waitingSites(helper, v, List.of(Map.of(Items.GLASS, 4), Map.of(Items.GLASS, 4), Map.of(Items.GLASS, 4)), true);
		his.get(0).setWaitingSince(now - StewardSafety.PAUSE_TICKS);
		his.get(1).setWaitingSince(now - StewardSafety.PAUSE_TICKS - 5);
		his.get(2).setWaitingSince(now);
		helper.assertTrue(StewardSafety.paused(level, v.hall()), "two of his three builds waited a whole day, and he isn't paused");
		helper.succeed();
	}

	/** Nothing to buy, nothing said: with no waiting builds the owner hears no shopping list, and the desk shows none. */
	//$ gametest_ticks_batch AREA '60' '"qa1031Quiet"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1031Quiet")
	public void theOwnerHearsNothingWhenNothingWaits(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		MinecraftServer server = level.getServer();
		List<Component> seen = new ArrayList<>();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qaQuiet"), false);
		ServerPlayer owner = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, owner, cookie);
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> {
			server.getPlayerList().remove(owner);
			level.setDayTime(dayTime);
		});
		v.entity().setOwner(owner.getUUID(), owner.getGameProfile().getName());
		level.setDayTime(StewardWishes.day(level) * 24000L + 1000L);
		waitingSites(helper, v, List.of(Map.of()), true); // waiting, but missing nothing
		StewardDesk.plan(level, v.steward(), v.hall());
		helper.assertTrue(shoppingLines(seen).isEmpty(), "told a shopping list with nothing to buy: " + shoppingLines(seen));
		// a player's own waiting build is not his: still nothing
		waitingSites(helper, v, List.of(Map.of(Items.GLASS, 40)), false);
		level.setDayTime(level.getDayTime() + 24000L);
		StewardDesk.plan(level, v.steward(), v.hall());
		helper.assertTrue(shoppingLines(seen).isEmpty(), "told the shopping list of a player's own build: "
			+ shoppingLines(seen).stream().map(Component::getString).toList());
		helper.succeed();
	}

	private static List<Component> shoppingLines(List<Component> seen) {
		return seen.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.steward.shopping")).toList();
	}
}
