package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.CONTROL;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.CONTROL_TARGET;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.DEPLOYER;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.TARGET;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.deployer;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.leverControl;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.leverFlipped;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.nameTag;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.speed;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.start;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane E, 2026-11-22): who owns what a Create Deployer sets down or clicks (B843) and what a Deployer may do at a
 * protected hall (B844), from the promises. create.md: "A Deployer acts as a fake player: one a player placed acts for
 * him (his hall set down is his); one nobody placed acts for nobody, so a hall it sets down stays unowned until a player
 * claims it, and an unowned Mailbox, Shop Counter or Storehouse it places or clicks stays unowned (B843). A stranger's
 * deployer in a protected village can do only what the stranger may: it places no block and renames, opens or hands
 * nothing in at the hall (B844)." village-hall.md: "A hall placed by a machine nobody placed has no owner until a player
 * turns its protection on; one set down by a player's own Deployer is that player's." mailbox.md and shop-counter.md:
 * "Owner: whoever places it"; a structure's mailbox is the first opener's (MailboxBlock), a counter the first clicker's.
 *
 * <p>The player is a real (mock) server player, online, so the name the mod shows for him is his own; his Deployer
 * carries his UUID as Create's {@code Owner}. Set-up helpers: {@link QaCreateDeployerOnOurThings1102eCompatTests} (the
 * deployer facing west at {@code DEPLOYER} acts two blocks ahead, at {@code TARGET}). The bug this found (a player's
 * deployer's hall founded "by a rogue Deployer" in its chronicle) is in the unregistered
 * {@link QaCreateDeployerOwners1122eBugCompatTests}.
 */
public class QaCreateDeployerOwners1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final UUID STRANGER = UUID.fromString("5a0e1122-0000-4e00-9a00-00000000d00d");
	private static final UUID FRANK = UUID.fromString("f4a11122-0000-4e00-9a00-00000000f4a1");

	private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
	}

	static void logOut(GameTestHelper helper, ServerPlayer player) {
		if (helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}

	/** Saves the block entity at {@code pos} and loads it again in its place, as a chunk unloaded and loaded does. */
	private static void reload(GameTestHelper helper, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		BlockEntity be = level.getBlockEntity(abs);
		if (be == null) {
			throw new GameTestAssertException("reload: no block entity at " + pos.toShortString());
		}
		CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
		BlockState state = level.getBlockState(abs);
		level.removeBlockEntity(abs);
		BlockEntity again = BlockEntity.loadStatic(abs, state, tag, level.registryAccess());
		if (again == null) {
			throw new GameTestAssertException("reload: the block entity at " + pos.toShortString() + " didn't load from " + tag);
		}
		level.setBlockEntity(again);
	}

	/** The names the hall's chronicle gives for whoever founded it (the arguments of its first, founding line). */
	static List<String> founders(VillageHallBlockEntity hall) {
		List<String> names = new ArrayList<>();
		for (Chronicle.Entry e : hall.chronicle()) {
			if (e.kind() == Chronicle.Kind.FOUNDED && e.text().getContents() instanceof TranslatableContents tc) {
				for (Object arg : tc.getArgs()) {
					names.add(arg instanceof Component c ? c.getString() : String.valueOf(arg));
				}
				names.add(e.text().getString());
			}
		}
		return names;
	}

	static boolean namesADeployer(List<String> texts) {
		return texts.stream().anyMatch(s -> s.toLowerCase(java.util.Locale.ROOT).contains("deployer"));
	}

	// ---------------------------------------------------------------- 1. a Deployer nobody placed

	/**
	 * A deployer nobody placed sets a Village Hall down: the hall has no owner (no "Deployer" by name either) and its
	 * chronicle names no Deployer as founder. Then a player turns its protection on (village-hall.md: that claims an
	 * unowned hall): the hall is his, by UUID and by name.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_downers_nobody_hall"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_downers_nobody_hall")
	public void aHallANobodysDeployerSetsDownIsUnownedUntilAPlayerClaimsIt(GameTestHelper helper) {
		start(helper);
		deployer(helper, DEPLOYER, null, new ItemStack(ModBlocks.VILLAGE_HALL.asItem()));
		helper.runAfterDelay(240, () -> {
			helper.assertTrue(helper.getBlockState(TARGET).is(ModBlocks.VILLAGE_HALL), "the deployer hasn't placed the hall: " + helper.getBlockState(TARGET)
				+ " (speed " + speed(helper, DEPLOYER) + ", holding " + held(helper, DEPLOYER) + ")");
			VillageHallBlockEntity hall = helper.getBlockEntity(TARGET);
			String name = hall.ownerName();
			helper.assertTrue(hall.owner() == null, "a hall set down by a deployer nobody placed is owned by \"" + name + "\" (" + hall.owner() + ")");
			helper.assertTrue(name == null || !name.toLowerCase(java.util.Locale.ROOT).contains("deployer"),
				"an unowned hall set down by a deployer nobody placed shows its owner as \"" + name + "\"");
			List<String> founded = founders(hall);
			helper.assertTrue(!namesADeployer(founded), "the chronicle of a hall set down by a deployer nobody placed names a Deployer: " + founded);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				Component said = VillageProtection.toggle(helper.getLevel(), helper.absolutePos(TARGET), player);
				helper.assertTrue(player.getUUID().equals(hall.owner()), "a player turning protection on at the unowned hall didn't claim it: owner "
					+ hall.ownerName() + " (" + hall.owner() + "); the mod said \"" + said.getString() + "\"");
				helper.assertTrue(player.getGameProfile().getName().equals(hall.ownerName()), "the claimed hall shows its owner as \"" + hall.ownerName()
					+ "\", not the player's name " + player.getGameProfile().getName());
				helper.assertTrue(hall.isProtected(), "the claiming player's toggle didn't turn protection on: \"" + said.getString() + "\"");
			} finally {
				hall.setProtected(false);
				logOut(helper, player);
			}
			helper.succeed();
		});
	}

	/**
	 * A structure's unowned Mailbox clicked by a deployer nobody placed stays unowned (the lever control shows the deployers
	 * really click); then a player opening it gets it, by UUID and by name ("first to open it owns it").
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_downers_nobody_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_downers_nobody_mailbox")
	public void aMailboxANobodysDeployerClicksIsLeftForThePlayerWhoOpensIt(GameTestHelper helper) {
		start(helper);
		helper.setBlock(TARGET, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(TARGET);
		leverControl(helper);
		deployer(helper, DEPLOYER, null, new ItemStack(Items.STICK));
		helper.runAfterDelay(240, () -> {
			leverFlipped(helper);
			helper.assertTrue(box.owner() == null, "a deployer nobody placed clicked an unowned mailbox and it is now \"" + box.ownerName() + "\"'s ("
				+ box.owner() + ")");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				helper.getBlockState(TARGET).useWithoutItem(helper.getLevel(), player, hit(helper, TARGET));
				helper.assertTrue(player.getUUID().equals(box.owner()), "the first player to open the mailbox after the deployer didn't get it: it is \""
					+ box.ownerName() + "\"'s (" + box.owner() + ")");
				helper.assertTrue(player.getGameProfile().getName().equals(box.ownerName()), "the mailbox shows its owner as \"" + box.ownerName() + "\"");
			} finally {
				player.closeContainer();
				logOut(helper, player);
			}
			helper.succeed();
		});
	}

	/** The same for an unowned Shop Counter: the deployer nobody placed leaves it unowned, the first player clicking it owns it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_downers_nobody_counter"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_downers_nobody_counter")
	public void aShopCounterANobodysDeployerClicksIsLeftForThePlayerWhoClicksIt(GameTestHelper helper) {
		start(helper);
		helper.setBlock(TARGET, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(TARGET);
		leverControl(helper);
		deployer(helper, DEPLOYER, null, new ItemStack(Items.STICK));
		helper.runAfterDelay(240, () -> {
			leverFlipped(helper);
			helper.assertTrue(counter.owner() == null, "a deployer nobody placed clicked an unowned shop counter and it is now \"" + counter.ownerName()
				+ "\"'s (" + counter.owner() + ")");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				helper.getBlockState(TARGET).useWithoutItem(helper.getLevel(), player, hit(helper, TARGET));
				helper.assertTrue(player.getUUID().equals(counter.owner()), "the first player to click the counter after the deployer didn't get it: it is \""
					+ counter.ownerName() + "\"'s (" + counter.owner() + ")");
				helper.assertTrue(player.getGameProfile().getName().equals(counter.ownerName()), "the counter shows its owner as \"" + counter.ownerName() + "\"");
			} finally {
				player.closeContainer();
				logOut(helper, player);
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- 2. and 3. a Deployer a player placed

	/**
	 * A deployer a player placed sets a Village Hall down: the hall is his by UUID and shows his name (not "Deployer"), its
	 * chronicle names no Deployer as its founder, and after the hall is saved and loaded again it is still his.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_downers_player_hall"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_downers_player_hall")
	public void aHallAPlayersDeployerSetsDownIsHisByNameAndAfterAReload(GameTestHelper helper) {
		start(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID id = player.getUUID();
		String name = player.getGameProfile().getName();
		deployer(helper, DEPLOYER, id, new ItemStack(ModBlocks.VILLAGE_HALL.asItem()));
		helper.runAfterDelay(240, () -> {
			logOut(helper, player);
			helper.assertTrue(helper.getBlockState(TARGET).is(ModBlocks.VILLAGE_HALL), "the player's deployer hasn't placed the hall: " + helper.getBlockState(TARGET)
				+ " (speed " + speed(helper, DEPLOYER) + ", holding " + held(helper, DEPLOYER) + ")");
			VillageHallBlockEntity hall = helper.getBlockEntity(TARGET);
			helper.assertTrue(id.equals(hall.owner()), "the hall the player's deployer set down is owned by " + hall.ownerName() + " (" + hall.owner()
				+ "), not the player " + id);
			helper.assertTrue(name.equals(hall.ownerName()), "the hall the player's deployer set down shows its owner as \"" + hall.ownerName()
				+ "\", not \"" + name + "\"");
			reload(helper, TARGET);
			VillageHallBlockEntity loaded = helper.getBlockEntity(TARGET);
			helper.assertTrue(id.equals(loaded.owner()) && name.equals(loaded.ownerName()), "after a save and reload the hall is owned by \""
				+ loaded.ownerName() + "\" (" + loaded.owner() + "), not \"" + name + "\" (" + id + ")");
			helper.succeed();
		});
	}

	/** A player's deployer clicking a structure's unowned Mailbox: it becomes the player's, by UUID and name, not "Deployer"'s. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_downers_player_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_downers_player_mailbox")
	public void aPlayersDeployerClickingAnUnownedMailboxMakesItHis(GameTestHelper helper) {
		start(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.setBlock(TARGET, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(TARGET);
		deployer(helper, DEPLOYER, player.getUUID(), new ItemStack(Items.STICK));
		helper.runAfterDelay(240, () -> {
			String name = player.getGameProfile().getName();
			logOut(helper, player);
			helper.assertTrue(player.getUUID().equals(box.owner()), "the player's deployer clicked an unowned mailbox and it is \"" + box.ownerName()
				+ "\"'s (" + box.owner() + "), not the player's (speed " + speed(helper, DEPLOYER) + ")");
			helper.assertTrue(name.equals(box.ownerName()), "the mailbox the player's deployer claimed shows its owner as \"" + box.ownerName() + "\"");
			ItemStack kept = held(helper, DEPLOYER);
			helper.assertTrue(kept.is(Items.STICK) && kept.getCount() == 1, "the deployer's stick was used up or doubled: it holds " + kept);
			helper.succeed();
		});
	}

	/** A player's deployer clicking an unowned Shop Counter: it becomes the player's, by UUID and name. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_downers_player_counter"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_downers_player_counter")
	public void aPlayersDeployerClickingAnUnownedShopCounterMakesItHis(GameTestHelper helper) {
		start(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.setBlock(TARGET, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(TARGET);
		deployer(helper, DEPLOYER, player.getUUID(), new ItemStack(Items.STICK));
		helper.runAfterDelay(240, () -> {
			String name = player.getGameProfile().getName();
			logOut(helper, player);
			helper.assertTrue(player.getUUID().equals(counter.owner()), "the player's deployer clicked an unowned counter and it is \"" + counter.ownerName()
				+ "\"'s (" + counter.owner() + "), not the player's (speed " + speed(helper, DEPLOYER) + ")");
			helper.assertTrue(name.equals(counter.ownerName()), "the counter the player's deployer claimed shows its owner as \"" + counter.ownerName() + "\"");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- 4. B844: deployers at a protected hall

	/**
	 * Two of Frank's halls, both protected. A stranger's deployer (a player who isn't Frank's friend placed it) holding a
	 * named Name Tag at the first renames nothing and keeps its tag; Frank's own deployer holding a stick at the second
	 * changes nothing either (no rename, still Frank's, still protected) and keeps its stick. The control that the
	 * stranger's deployer really clicks: {@link #aStrangersDeployerRenamesAnOpenHall}.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_downers_protected_hall"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_downers_protected_hall")
	public void deployersAtAProtectedHallRenameNothing(GameTestHelper helper) {
		start(helper);
		helper.setBlock(TARGET, ModBlocks.VILLAGE_HALL);
		helper.setBlock(CONTROL_TARGET, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity theirs = helper.getBlockEntity(TARGET);
		VillageHallBlockEntity franks = helper.getBlockEntity(CONTROL_TARGET);
		for (VillageHallBlockEntity h : new VillageHallBlockEntity[] {theirs, franks}) {
			h.setOwner(FRANK, "Frank");
			h.setCustomName(Component.literal("Frankton"));
			h.setProtected(true);
		}
		helper.onEachTick(() -> {
			if (helper.getTick() >= 390) {
				theirs.setProtected(false);
				franks.setProtected(false);
			}
		});
		deployer(helper, DEPLOYER, STRANGER, nameTag("Strangerton"));
		deployer(helper, CONTROL, FRANK, new ItemStack(Items.STICK));
		helper.runAfterDelay(240, () -> {
			String a = theirs.getCustomName() == null ? "none" : theirs.getCustomName().getString();
			String b = franks.getCustomName() == null ? "none" : franks.getCustomName().getString();
			boolean aProt = theirs.isProtected();
			boolean bProt = franks.isProtected();
			ItemStack tag = held(helper, DEPLOYER);
			ItemStack stick = held(helper, CONTROL);
			float s1 = speed(helper, DEPLOYER);
			float s2 = speed(helper, CONTROL);
			if (!"Frankton".equals(a) || !"Frankton".equals(b) || !FRANK.equals(theirs.owner()) || !FRANK.equals(franks.owner()) || !aProt || !bProt
				|| !tag.is(Items.NAME_TAG) || !stick.is(Items.STICK)) {
				theirs.setProtected(false);
				franks.setProtected(false);
				helper.assertTrue(s1 != 0 && s2 != 0, "a deployer never turned (" + s1 + ", " + s2 + ")");
				helper.assertTrue("Frankton".equals(a), "a stranger's deployer renamed Frank's protected hall to \"" + a + "\"");
				helper.assertTrue(FRANK.equals(theirs.owner()) && aProt, "after the stranger's deployer Frank's hall is \"" + theirs.ownerName()
					+ "\"'s, protected " + aProt);
				helper.assertTrue(tag.is(Items.NAME_TAG) && tag.getCount() == 1, "the stranger's name tag was used up or doubled: the deployer holds " + tag);
				helper.assertTrue("Frankton".equals(b), "Frank's own deployer holding a stick renamed his hall to \"" + b + "\"");
				helper.assertTrue(FRANK.equals(franks.owner()) && "Frank".equals(franks.ownerName()) && bProt, "after Frank's own deployer clicked his hall it is \""
					+ franks.ownerName() + "\"'s (" + franks.owner() + "), protected " + bProt);
				helper.assertTrue(stick.is(Items.STICK) && stick.getCount() == 1, "Frank's deployer's stick was used up or doubled: it holds " + stick);
			}
			theirs.setProtected(false);
			franks.setProtected(false);
			helper.succeed();
		});
	}

	/** The control for the test above: at Frank's open hall the same stranger's deployer's Name Tag does rename it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_downers_open_hall"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_downers_open_hall")
	public void aStrangersDeployerRenamesAnOpenHall(GameTestHelper helper) {
		start(helper);
		helper.setBlock(TARGET, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(TARGET);
		hall.setOwner(FRANK, "Frank");
		hall.setCustomName(Component.literal("Frankton"));
		deployer(helper, DEPLOYER, STRANGER, nameTag("Strangerton"));
		helper.succeedWhen(() -> {
			String a = hall.getCustomName() == null ? "none" : hall.getCustomName().getString();
			helper.assertTrue("Strangerton".equals(a), "the control: at an open hall the stranger's deployer didn't rename it (\"" + a + "\", speed "
				+ speed(helper, DEPLOYER) + ")");
			helper.assertTrue(FRANK.equals(hall.owner()), "renaming made " + hall.ownerName() + " the hall's owner");
		});
	}
}
