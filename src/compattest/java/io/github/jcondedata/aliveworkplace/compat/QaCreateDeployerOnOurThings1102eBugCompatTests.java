package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.*;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1102-1915) BUG tests: Create's Deployer, a fake player, doing what only a real owner may. Each fails on the
 * current mod for the reason in its message, and each carries a control proving the deployer really clicked (Frank's own
 * deployer placing or renaming, or a deployer flipping a lever). Set-up helpers: {@link QaCreateDeployerOnOurThings1102eCompatTests}.
 *
 * <ul>
 * <li>A deployer nobody placed (its fake player is "Deployer", 9e2faded-cafe-4ec2-c314-dad129ae971d) becomes the owner of
 * a Village Hall it sets down, and of a structure's unowned Mailbox or Shop Counter it clicks: village-hall.md, mailbox.md
 * and shop-counter.md give them to "whoever places it", a person; the mod's own {@code VillageHallBlock.built} already
 * refuses "the builder standing in for nobody".</li>
 * <li>Inside Frank's protected village a stranger's deployer still places blocks (Create fires UseBlockCallback only when
 * the clicked block has a shape, so a block put into air is never asked about) and renames his hall with a Name Tag (the
 * mod's use-block hook answers a stranger at a hall with SUCCESS and the price board; Create stops only on FAIL, so the
 * hall's own useItemOn renames it).</li>
 * </ul>
 */
public class QaCreateDeployerOnOurThings1102eBugCompatTests implements FabricGameTest {

	/** In Frank's protected village: a stranger's deployer places nothing; Frank's own deployer there does (the control). */
	//$ gametest_ticks_batch AREA '400' '"qa_cdepbug_protect_place"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdepbug_protect_place")
	public void aStrangersDeployerPlacesNoBlockInAProtectedVillage(GameTestHelper helper) {
		start(helper);
		franksHall(helper, true);
		deployer(helper, DEPLOYER, null, new ItemStack(Items.COBBLESTONE, 1));
		deployer(helper, CONTROL, FRANK, new ItemStack(Items.COBBLESTONE, 1));
		thenOpenUpAndCheck(helper, 240, () -> {
			helper.assertTrue(helper.getBlockState(CONTROL_TARGET).is(Blocks.COBBLESTONE),
				"the control: Frank's own deployer in his village didn't place its cobblestone: " + helper.getBlockState(CONTROL_TARGET));
			helper.assertTrue(helper.getBlockState(TARGET).isAir(), "a stranger's deployer placed " + helper.getBlockState(TARGET) + " in Frank's protected village");
			ItemStack kept = held(helper, DEPLOYER);
			helper.assertTrue(kept.is(Items.COBBLESTONE) && kept.getCount() == 1, "the stranger's deployer lost its cobblestone: it holds " + kept);
		});
	}

	/** A protected hall: a stranger's deployer's Name Tag renames nothing and keeps its tag; Frank's own does rename it. */
	//$ gametest_ticks_batch AREA '400' '"qa_cdepbug_hall_nametag_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdepbug_hall_nametag_protected")
	public void aStrangersDeployerCannotRenameAProtectedHall(GameTestHelper helper) {
		start(helper);
		// two halls of Frank's, both protected: one before the stranger's deployer, one before Frank's own
		helper.setBlock(TARGET, ModBlocks.VILLAGE_HALL);
		helper.setBlock(CONTROL_TARGET, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity theirs = helper.getBlockEntity(TARGET);
		VillageHallBlockEntity franks = helper.getBlockEntity(CONTROL_TARGET);
		for (VillageHallBlockEntity h : new VillageHallBlockEntity[] {theirs, franks}) {
			h.setOwner(FRANK, "Frank");
			h.setCustomName(Component.literal("Frankton"));
			h.setProtected(true);
		}
		deployer(helper, DEPLOYER, null, nameTag("Strangerton"));
		deployer(helper, CONTROL, FRANK, nameTag("Oakvale"));
		thenOpenUpAndCheck(helper, 240, () -> {
			helper.assertTrue(franks.getCustomName() != null && franks.getCustomName().getString().equals("Oakvale"),
				"the control: Frank's own deployer didn't rename his protected hall: it is " + franks.getCustomName());
			helper.assertTrue(theirs.getCustomName() != null && theirs.getCustomName().getString().equals("Frankton"),
				"a stranger's deployer renamed Frank's protected hall to " + theirs.getCustomName());
			helper.assertTrue(FRANK.equals(theirs.owner()), "Frank's hall is now " + theirs.ownerName() + "'s");
			ItemStack kept = held(helper, DEPLOYER);
			helper.assertTrue(kept.is(Items.NAME_TAG) && kept.getCount() == 1, "the stranger's name tag was used up or doubled: the deployer holds " + kept);
		});
	}

	/** A deployer placed by nobody, setting a Village Hall down: no player placed it, so nobody owns it (as a Steward's build). */
	//$ gametest_ticks_batch AREA '400' '"qa_cdepbug_place_hall_nobody"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdepbug_place_hall_nobody")
	public void aDeployerNobodyPlacedSetsDownAHallNobodyOwns(GameTestHelper helper) {
		start(helper);
		deployer(helper, DEPLOYER, null, new ItemStack(ModBlocks.VILLAGE_HALL.asItem()));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(TARGET).is(ModBlocks.VILLAGE_HALL), "the deployer hasn't placed the hall: " + helper.getBlockState(TARGET));
			VillageHallBlockEntity hall = helper.getBlockEntity(TARGET);
			helper.assertTrue(hall.owner() == null, "a deployer nobody placed set down a hall now owned by \"" + hall.ownerName() + "\" (" + hall.owner()
				+ "), a fake player no one can log in as");
		});
	}

	/** mailbox.md, "Place it and it's yours": a structure's mailbox nobody owns isn't claimed by a deployer nobody placed. */
	//$ gametest_ticks_batch AREA '400' '"qa_cdepbug_claim_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdepbug_claim_mailbox")
	public void aDeployerNobodyPlacedDoesNotClaimAnUnownedMailbox(GameTestHelper helper) {
		start(helper);
		helper.setBlock(TARGET, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(TARGET);
		leverControl(helper);
		deployer(helper, DEPLOYER, null, new ItemStack(Items.STICK));
		helper.runAfterDelay(240, () -> {
			leverFlipped(helper);
			helper.assertTrue(box.owner() == null, "a deployer nobody placed clicked an unowned mailbox and it is now \"" + box.ownerName() + "\"'s ("
				+ box.owner() + "): no player can open it any more");
			helper.succeed();
		});
	}

	/** shop-counter.md, "Owner: whoever places it": a counter nobody owns isn't claimed by a deployer nobody placed. */
	//$ gametest_ticks_batch AREA '400' '"qa_cdepbug_claim_counter"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cdepbug_claim_counter")
	public void aDeployerNobodyPlacedDoesNotClaimAnUnownedShopCounter(GameTestHelper helper) {
		start(helper);
		helper.setBlock(TARGET, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(TARGET);
		leverControl(helper);
		deployer(helper, DEPLOYER, null, new ItemStack(Items.STICK));
		helper.runAfterDelay(240, () -> {
			leverFlipped(helper);
			helper.assertTrue(counter.owner() == null, "a deployer nobody placed clicked an unowned shop counter and it is now \"" + counter.ownerName() + "\"'s ("
				+ counter.owner() + "): no player can set its prices");
			helper.succeed();
		});
	}
}
