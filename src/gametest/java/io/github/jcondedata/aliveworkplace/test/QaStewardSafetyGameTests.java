package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for ROADMAP 27.19, safe by design, written from the spec: the ledger's edges (a block broken, a block outside every
 * hall's area, the 16×16×16 section and nothing more, per dimension), Keep Clear against the owner's own approval, the
 * shopping list's cap of 8 and its sum, the caravans' wants, the owner told once a day, the pause's boundaries and every
 * new sentence rendered. The village is StewardSafetyGameTests' (hall, table and builder, a Steward, Homes east).
 */
public class QaStewardSafetyGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final ResourceLocation RULE = AliveWorkplace.id("test/qa_safety_well");
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);
	private static final BlockPos STOREHOUSE = new BlockPos(4, 2, 18);

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	private static Village village(GameTestHelper helper) {
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

	private static StewardWishes.Wish wish() {
		return new StewardWishes.Wish(RULE, new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(WELL), Optional.of("homes"),
			Optional.empty(), Optional.empty()), 50, "steward.aliveworkplace.why.beds", List.of(3L));
	}

	private static List<BuildSite> waitingSites(GameTestHelper helper, Village v, List<Map<Item, Integer>> missings) {
		List<BuildSite> out = new ArrayList<>();
		int n = 0;
		for (Map<Item, Integer> missing : missings) {
			BlueprintData.Placement placement = new BlueprintData.Placement(v.level().dimension().location(),
				helper.absolutePos(new BlockPos(20 + 5 * n++, 2, 24)), Rotation.NONE, Mirror.NONE);
			BuildSite site = BuildSiteManager.get(v.level()).create(v.owner().getUUID(), "", AliveWorkplace.id("stone_house"), placement);
			site.setStewardHall(v.hall());
			site.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
			site.setMissing(new HashMap<>(missing));
			out.add(site);
		}
		return out;
	}

	private static List<String> loreLines(ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (Component c : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
			out.add(c.getString());
		}
		return out;
	}

	private static List<String> deskLines(Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
		menu.press(VillageHallScreen.ADVICE, v.owner());
		return loreLines(menu.icon(StewardDeskPage.STEWARD));
	}

	// ---- the ledger ----------------------------------------------------------------------------------------------

	/** A block a player breaks (not one they placed) within the hall's area marks its section too; a block set by the world doesn't. */
	//$ gametest_ticks_batch AREA '20' '"qaSafetyBreak"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaSafetyBreak")
	public void aBlockAPlayerBreaksMarksItsSection(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos rel = new BlockPos(8, 2, 20);
		BlockPos at = helper.absolutePos(rel);
		helper.setBlock(rel, Blocks.OAK_PLANKS); // the world's block (a village's own house), not a player's
		helper.assertTrue(!PlayerBuilt.get(v.level()).marked(at), "a block the world set marked the ledger");
		helper.assertTrue(v.owner().gameMode.destroyBlock(at), "setup: the player couldn't break the block");
		helper.assertTrue(v.level().getBlockState(at).isAir(), "setup: the block is still there: " + v.level().getBlockState(at));
		helper.assertTrue(PlayerBuilt.get(v.level()).marked(at), "a block the player broke in the hall's area left its section unmarked");
		BoundingBox box = new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + 1, at.getY() + 2, at.getZ() + 1);
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), box, false).equals(Optional.of(StewardSafety.Refusal.PLAYER_BUILT)),
			"the Steward may still plan through the spot a player cleared: " + StewardSafety.check(v.level(), v.hall(), box, false));
		helper.succeed();
	}

	/** A block a player places outside every hall's area (here a hall's area of 6) marks nothing; one inside does. */
	//$ gametest_ticks_batch AREA '20' '"qaSafetyOutside"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaSafetyOutside")
	public void aPlayersBlockOutsideEveryHallsAreaMarksNothing(GameTestHelper helper) {
		Village v = village(helper);
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = 6;
		VillageHalls.forgetNearest();
		PlayerBuilt ledger = PlayerBuilt.get(v.level());
		BoundingBox near = new BoundingBox(helper.absolutePos(BlockPos.ZERO)).inflatedBy(48);
		ledger.forget(near);
		BlockPos far = helper.absolutePos(HALL.east(10)); // 10 blocks from the hall
		helper.assertTrue(VillageHalls.nearest(v.level(), far).isEmpty(), "setup: a hall within 6 of " + far);
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), far, Blocks.COBBLESTONE), "setup: the far block wasn't placed");
		helper.assertTrue(!ledger.marked(far), "a block placed outside every hall's area marked its section");
		BlockPos inside = helper.absolutePos(HALL.east(3));
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), inside, Blocks.COBBLESTONE), "setup: the near block wasn't placed");
		helper.assertTrue(ledger.marked(inside), "a block placed 3 blocks from the hall didn't mark its section");
		helper.succeed();
	}

	/**
	 * A block at the very edge of a section marks that 16×16×16 section only: a plan wholly in the neighbouring section
	 * is still allowed, and one reaching one block back into the marked section is refused.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaSafetySection"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaSafetySection")
	public void aMarkCoversItsOwnSectionAndNoMore(GameTestHelper helper) {
		Village v = village(helper);
		int base = helper.absolutePos(BlockPos.ZERO).getX();
		// The first x from 8 on whose absolute coordinate is a section's east edge (x & 15 == 15). Any 16 columns hold
		// exactly one, so this lands in 8..23 wherever the framework places the area (B522: a 15-column search missed it
		// whenever the edge fell on x = 7 or 23), and the plans below reach x + 4 <= 27, inside the 30-wide area.
		int relX = 8 + Math.floorMod(15 - (base + 8), 16);
		helper.assertTrue(((base + relX) & 15) == 15 && relX <= 23, "setup: no section edge in the area");
		BlockPos edge = helper.absolutePos(new BlockPos(relX, 2, 20));
		PlayerBuilt ledger = PlayerBuilt.get(v.level());
		int before = ledger.size();
		ledger.setDirty(false);
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), edge, Blocks.OAK_PLANKS), "setup: not placed");
		helper.assertTrue(ledger.marked(edge), "the player's block didn't mark its section");
		helper.assertTrue(ledger.isDirty(), "the new mark isn't saved with the world (the ledger isn't dirty)");
		helper.assertTrue(ledger.size() == before + 1, "one block marked " + (ledger.size() - before) + " sections");
		helper.assertTrue(!ledger.marked(edge.east()), "the block at a section's east edge marked the next section");
		helper.assertTrue(!ledger.marked(edge.above(16)) && !ledger.marked(edge.below(16)), "the sections above or below were marked");
		BoundingBox next = new BoundingBox(edge.getX() + 1, edge.getY(), edge.getZ() - 1, edge.getX() + 4, edge.getY() + 3, edge.getZ() + 1);
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), next, false).isEmpty(),
			"a plan in the next section, untouched by players, is refused: " + StewardSafety.check(v.level(), v.hall(), next, false));
		BoundingBox across = new BoundingBox(edge.getX(), edge.getY(), edge.getZ() - 1, edge.getX() + 4, edge.getY() + 3, edge.getZ() + 1);
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), across, false).equals(Optional.of(StewardSafety.Refusal.PLAYER_BUILT)),
			"a plan reaching one block into the marked section is allowed: " + StewardSafety.check(v.level(), v.hall(), across, false));
		helper.succeed();
	}

	/** The ledger is kept per dimension: a mark in the Overworld is no mark at the same coordinates in the Nether. */
	//$ gametest_ticks_batch AREA '20' '"qaSafetyDimension"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaSafetyDimension")
	public void theLedgerIsKeptPerDimension(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel nether = v.level().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "setup: no Nether on the test server");
		BlockPos at = helper.absolutePos(new BlockPos(9, 2, 9));
		PlayerBuilt.get(nether).forget(new BoundingBox(at).inflatedBy(32));
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), at, Blocks.OAK_PLANKS), "setup: not placed");
		helper.assertTrue(PlayerBuilt.get(v.level()).marked(at), "the Overworld's ledger didn't mark the block");
		helper.assertTrue(!PlayerBuilt.get(nether).marked(at), "a block placed in the Overworld marked the Nether's ledger");
		helper.assertTrue(PlayerBuilt.get(nether) != PlayerBuilt.get(v.level()), "both dimensions share one ledger");
		helper.succeed();
	}

	// ---- zones -----------------------------------------------------------------------------------------------------

	/** Keep Clear painted over a proposal's spot: refused even when the owner approves it by hand (the ledger's override only). */
	//$ gametest_ticks_batch AREA '40' '"qaSafetyKeepClear"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSafetyKeepClear")
	public void keepClearRefusesEvenTheOwnersOwnApproval(GameTestHelper helper) {
		Village v = village(helper);
		Plots.Verdict verdict = Plots.check(v.level(), v.hall(), v.entity().plan(), "homes", WELL, helper.absolutePos(SPOT),
			Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		helper.assertTrue(verdict.plot().isPresent(), "setup: the spot doesn't fit: " + verdict);
		Plots.Plot plot = verdict.plot().get();
		Optional<StewardDesk.Proposal> proposal = StewardDesk.offer(v.level(), v.hall(), wish(), plot.blueprint(), plot.placement(), plot.zone(), false);
		helper.assertTrue(proposal.isPresent(), "setup: not proposed");
		BitSet cells = new BitSet();
		for (int x = 6; x <= 20; x++) {
			for (int z = 6; z <= 22; z++) {
				cells.set(CityPlan.cellAt(v.hall(), helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		CityPlan plan = v.entity().plan().addZone(StewardSafety.KEEP_CLEAR, "Keep Clear", "");
		helper.assertTrue(plan != null, "setup: no Keep Clear zone");
		v.entity().setPlan(plan.paint(plan.zones().size() - 1, cells));
		StewardDesk.Outcome byHand = StewardDesk.approve(v.level(), v.hall(), v.owner(), proposal.get().id());
		helper.assertTrue(byHand == StewardDesk.Outcome.UNSAFE, "the owner's approval built in Keep Clear: " + byHand);
		helper.assertTrue(StewardDesk.openSites(v.level(), v.hall()).isEmpty(), "a site opened in Keep Clear");
		helper.succeed();
	}

	// ---- materials ---------------------------------------------------------------------------------------------------

	/**
	 * Nine kinds missing over two waiting builds: the list shows the 8 most needed, added up before they're ranked
	 * (cobblestone 3 + 4 beats wool's 5), and the least needed (sand, 1) is left off; the desk, the Storehouse's board and
	 * the caravans' wants all show it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSafetyCap"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSafetyCap")
	public void theShoppingListShowsTheEightMostNeeded(GameTestHelper helper) {
		Village v = village(helper);
		Map<Item, Integer> first = new HashMap<>(Map.of(Items.GLASS, 30, Items.OAK_PLANKS, 20, Items.STONE_BRICKS, 15, Items.BRICKS, 9, Items.COBBLESTONE, 3));
		Map<Item, Integer> second = new HashMap<>(Map.of(Items.COBBLESTONE, 4, Items.OAK_LOG, 12, Items.TORCH, 10, Items.WHITE_WOOL, 5, Items.SAND, 1));
		waitingSites(helper, v, List.of(first, second));
		List<Map.Entry<Item, Integer>> list = StewardSafety.shoppingList(v.level(), v.hall());
		List<Map.Entry<Item, Integer>> expected = List.of(Map.entry(Items.GLASS, 30), Map.entry(Items.OAK_PLANKS, 20), Map.entry(Items.STONE_BRICKS, 15),
			Map.entry(Items.OAK_LOG, 12), Map.entry(Items.TORCH, 10), Map.entry(Items.BRICKS, 9), Map.entry(Items.COBBLESTONE, 7), Map.entry(Items.WHITE_WOOL, 5));
		helper.assertTrue(list.equals(expected), "the shopping list is " + list + ", not " + expected);
		String line = "Shopping list: 30 Glass, 20 Oak Planks, 15 Stone Bricks, 12 Oak Logs, 10 Torches, 9 Bricks, 7 Cobblestone, 5 White Wool";
		List<String> desk = deskLines(v);
		helper.assertTrue(desk.contains(line), "the desk doesn't read \"" + line + "\": " + desk);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		ChoiceMenu board = StorehouseBoard.boardForTest(v.owner(), helper.absolutePos(STOREHOUSE));
		ItemStack icon = board.icon(StorehouseBoard.SHOPPING_SLOT);
		List<String> lines = loreLines(icon);
		helper.assertTrue(icon.getHoverName().getString().equals("The Steward's shopping list"), "the board's title: " + icon.getHoverName().getString());
		helper.assertTrue(lines.equals(List.of("30 Glass", "20 Oak Planks", "15 Stone Bricks", "12 Oak Logs", "10 Torches", "9 Bricks", "7 Cobblestone", "5 White Wool")),
			"the Storehouse's board lists " + lines);
		helper.succeed();
	}

	/**
	 * B698: the list counts in the plural ("2 Torches") but names one in the singular ("1 Oak Log", not "1 Oak Logs"),
	 * and an item the lang has no plural for keeps its own name ("3 Glass"), on the desk and the Storehouse's board.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSafetyPlural"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSafetyPlural")
	public void theShoppingListCountsInThePlural(GameTestHelper helper) {
		Village v = village(helper);
		waitingSites(helper, v, List.of(Map.of(Items.GLASS, 3, Items.TORCH, 2, Items.OAK_LOG, 1)));
		String line = "Shopping list: 3 Glass, 2 Torches, 1 Oak Log";
		List<String> desk = deskLines(v);
		helper.assertTrue(desk.contains(line), "the desk doesn't read \"" + line + "\": " + desk);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		ChoiceMenu board = StorehouseBoard.boardForTest(v.owner(), helper.absolutePos(STOREHOUSE));
		List<String> lines = loreLines(board.icon(StorehouseBoard.SHOPPING_SLOT));
		helper.assertTrue(lines.equals(List.of("3 Glass", "2 Torches", "1 Oak Log")), "the Storehouse's board lists " + lines);
		helper.succeed();
	}

	/** The caravans' list of what the village wants carries what the waiting builds miss, and drops it once they're supplied. */
	//$ gametest_ticks_batch AREA '40' '"qaSafetyCaravans"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSafetyCaravans")
	public void caravansSeeTheShoppingList(GameTestHelper helper) {
		Village v = village(helper);
		List<BuildSite> sites = waitingSites(helper, v, List.of(Map.of(Items.GLASS, 40), Map.of(Items.GLASS, 6, Items.STONE_BRICKS, 9)));
		Caravans.round(v.level(), v.hall(), VillageHalls.census(v.level(), v.hall()));
		Caravans.Village entry = Caravans.Data.get(v.level()).village(v.hall());
		helper.assertTrue(entry != null, "the village isn't on the caravans' list");
		helper.assertTrue(entry.wants().contains(new Caravans.Want(Items.GLASS, 46)), "the caravans don't want the 46 glass: " + entry.wants());
		helper.assertTrue(entry.wants().contains(new Caravans.Want(Items.STONE_BRICKS, 9)), "the caravans don't want the 9 stone bricks: " + entry.wants());
		sites.forEach(s -> s.setStatus(BuildSite.Status.WORKING));
		Caravans.round(v.level(), v.hall(), VillageHalls.census(v.level(), v.hall()));
		entry = Caravans.Data.get(v.level()).village(v.hall());
		helper.assertTrue(entry.wants().stream().noneMatch(w -> w.item() == Items.GLASS || w.item() == Items.STONE_BRICKS),
			"supplied, the caravans still want: " + entry.wants());
		helper.succeed();
	}

	/** The owner hears the shopping list once a day: twice the same morning is one line, the next day a second. */
	//$ gametest_ticks_batch AREA '60' '"qaSafetyTold"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSafetyTold")
	public void theOwnerIsToldOnceADay(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		MinecraftServer server = level.getServer();
		List<Component> seen = new ArrayList<>();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qaShopper"), false);
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
		waitingSites(helper, v, List.of(Map.of(Items.GLASS, 40), Map.of(Items.OAK_PLANKS, 12, Items.TORCH, 10, Items.OAK_LOG, 1)));
		StewardDesk.plan(level, v.steward(), v.hall());
		StewardDesk.plan(level, v.steward(), v.hall());
		List<Component> told = shoppingLines(seen);
		helper.assertTrue(told.size() == 1, "told " + told.size() + " times the same day: " + told);
		String expected = v.steward().getDisplayName().getString() + " of " + VillageHalls.name(level, v.hall()).getString()
			+ " needs these for his builds: 40 Glass, 12 Oak Planks, 10 Torches, 1 Oak Log";
		helper.assertTrue(told.get(0).getString().equals(expected), "the owner read \"" + told.get(0).getString() + "\", not \"" + expected + "\"");
		level.setDayTime(level.getDayTime() + 24000L);
		StewardDesk.plan(level, v.steward(), v.hall());
		helper.assertTrue(shoppingLines(seen).size() == 2, "not told again the next day: " + shoppingLines(seen).size());
		helper.succeed();
	}

	private static List<Component> shoppingLines(List<Component> seen) {
		return seen.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.steward.shopping")).toList();
	}

	/**
	 * The pause's edges: builds just begun waiting are noted as waiting from now; 2 waiting a tick less than a day, or 1
	 * waiting a whole day, don't pause; 2 waiting a whole day do, and the desk says so; one back at work lifts it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSafetyPauseEdges"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSafetyPauseEdges")
	public void thePauseNeedsTwoBuildsWaitingAWholeDay(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		List<BuildSite> sites = waitingSites(helper, v, List.of(Map.of(Items.GLASS, 4), Map.of(Items.GLASS, 4)));
		StewardDesk.plan(level, v.steward(), v.hall());
		long now = level.getGameTime();
		for (BuildSite s : sites) {
			helper.assertTrue(s.waitingSince() == now, "a waiting build's wait began at " + s.waitingSince() + ", not now (" + now + ")");
		}
		helper.assertTrue(!StewardSafety.paused(level, v.hall()), "paused by builds that just began waiting");
		String paused = "No new builds until his waiting builds are supplied";
		sites.forEach(s -> s.setWaitingSince(now - StewardSafety.PAUSE_TICKS + 1));
		helper.assertTrue(!StewardSafety.paused(level, v.hall()), "paused by two builds waiting a tick less than a day");
		helper.assertTrue(!deskLines(v).contains(paused), "the desk says paused before a day: " + deskLines(v));
		sites.get(0).setWaitingSince(now - StewardSafety.PAUSE_TICKS);
		helper.assertTrue(!StewardSafety.paused(level, v.hall()), "paused by one build waiting a day");
		sites.get(1).setWaitingSince(now - StewardSafety.PAUSE_TICKS);
		helper.assertTrue(StewardSafety.paused(level, v.hall()), "not paused by two builds waiting a whole day");
		helper.assertTrue(deskLines(v).contains(paused), "the desk doesn't say \"" + paused + "\": " + deskLines(v));
		sites.get(0).setStatus(BuildSite.Status.WORKING); // supplied: back at work
		StewardDesk.plan(level, v.steward(), v.hall());
		helper.assertTrue(sites.get(0).waitingSince() == BuildSite.NOT_WAITING, "a build back at work still counts as waiting");
		helper.assertTrue(!StewardSafety.paused(level, v.hall()), "still paused with one build left waiting");
		helper.succeed();
	}

	// ---- text ----------------------------------------------------------------------------------------------------------

	/** Every 27.19 sentence a player reads, rendered with real arguments from en_us.json. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everySafetySentenceReadsRight(GameTestHelper helper) {
		List<Map.Entry<Item, Integer>> list = List.of(Map.entry(Items.GLASS, 40), Map.entry(Items.OAK_PLANKS, 12), Map.entry(Items.STONE_BRICKS, 5));
		Map<String, String> want = new java.util.LinkedHashMap<>();
		want.put(StewardSafety.describe(list.subList(0, 1)).getString(), "40 Glass");
		want.put(StewardSafety.describe(list).getString(), "40 Glass, 12 Oak Planks, 5 Stone Bricks");
		want.put(StewardSafety.describe(List.of()).getString(), "");
		want.put(StewardSafety.describe(List.of(Map.entry(Items.TORCH, 1), Map.entry(Items.TORCH, 2), Map.entry(Items.GLASS, 1))).getString(),
			"1 Torch, 2 Torches, 1 Glass");
		want.put(Component.translatable("screen.aliveworkplace.desk.shopping", StewardSafety.describe(list.subList(0, 2))).getString(),
			"Shopping list: 40 Glass, 12 Oak Planks");
		want.put(Component.translatable("message.aliveworkplace.steward.shopping", "Ada", "Oakham", StewardSafety.describe(list.subList(0, 2))).getString(),
			"Ada of Oakham needs these for his builds: 40 Glass, 12 Oak Planks");
		want.put(Component.translatable("screen.aliveworkplace.desk.paused").getString(), "No new builds until his waiting builds are supplied");
		want.put(Component.translatable("screen.aliveworkplace.desk.player_block").getString(), "A player's block is in the way");
		want.put(Component.translatable("screen.aliveworkplace.storehouse.shopping").getString(), "The Steward's shopping list");
		want.put(Component.translatable("message.aliveworkplace.steward.desk.outcome.unsafe", "Well").getString(),
			"The Well would go through a player's build, a Keep Clear zone or another village");
		for (Map.Entry<String, String> e : want.entrySet()) {
			helper.assertTrue(e.getKey().equals(e.getValue()), "reads \"" + e.getKey() + "\", not \"" + e.getValue() + "\"");
		}
		helper.succeed();
	}
}
