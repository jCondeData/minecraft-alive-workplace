package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setup;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/**
 * QA (lane E, 2026-11-11), B1172's fix with what isn't a chest. B1172: when a Mover's Crate stands where a packed block
 * goes up, "nothing packed is ever destroyed: the block waits, goes back, or is dropped with its contents"; the fix drops
 * a block holding items with them. Two that hold their items another way than a chest: a Create Item Vault (48
 * emeralds) and a vanilla lectern with its book. The house is moved a quarter turned and a Mover's Crate is set down on
 * the spot where that block goes up, after the move was confirmed (as B1172 describes). Every emerald (and the book)
 * must still be somewhere: in the block, or dropped there.
 */
public class QaCreateMoverCrateSpot1111eBugCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/** A crate set down where the block at SPOT goes up, then the move run to its end; what lies loose after it. */
	static Map<String, Integer> moveOntoACrate(GameTestHelper helper, Setup s, String[] end) {
		ServerLevel level = helper.getLevel();
		BlockPos landing = moved(s, SPOT);
		set(level, landing, ModBlocks.MOVERS_CRATE.defaultBlockState());
		MoveSite site = work(level, s, x -> false);
		end[0] = site.stage() + ", skipped " + site.skipped() + ", the spot " + level.getBlockState(landing).getBlock();
		return loose(helper);
	}

	/** An Item Vault with 48 emeralds whose new spot holds a Mover's Crate: the 48 still exist after the move. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1111_mover_crate_vault"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1111_mover_crate_vault")
	public void aVaultWhoseSpotHoldsACrateKeepsItsItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "crate_vault", block("create:item_vault").defaultBlockState(), Items.EMERALD, 48);
		helper.runAfterDelay(2, () -> {
			String[] end = new String[1];
			Map<String, Integer> looseNow;
			long inOld;
			long inNew;
			try {
				looseNow = moveOntoACrate(helper, s, end);
				inOld = held(level, helper.absolutePos(SPOT), Items.EMERALD);
				inNew = held(level, moved(s, SPOT), Items.EMERALD);
			} finally {
				tidy(helper, s);
			}
			int lying = looseNow.getOrDefault("minecraft:emerald", 0);
			helper.assertTrue(inOld + inNew + lying == 48, "a vault holding 48 emeralds went up over a Mover's Crate (move " + end[0]
				+ "): " + inOld + " are in a vault at the old spot, " + inNew + " at the new and " + lying + " lie loose; loose " + looseNow
				+ " (nothing packed may be destroyed, B1172)");
			helper.succeed();
		});
	}

	/** A lectern holding a book whose new spot holds a Mover's Crate: the book still exists after the move. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1111_mover_crate_lectern"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1111_mover_crate_lectern")
	public void aLecternWhoseSpotHoldsACrateKeepsItsBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos at = helper.absolutePos(SPOT);
		set(level, at, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true));
		helper.assertTrue(level.getBlockEntity(at) instanceof LecternBlockEntity, "no lectern (setup)");
		((LecternBlockEntity) level.getBlockEntity(at)).setBook(new ItemStack(Items.WRITABLE_BOOK));
		Setup s = setupAround(helper, "crate_lectern");
		helper.runAfterDelay(2, () -> {
			String[] end = new String[1];
			Map<String, Integer> looseNow;
			boolean bookOld;
			boolean bookNew;
			try {
				looseNow = moveOntoACrate(helper, s, end);
				bookOld = level.getBlockEntity(at) instanceof LecternBlockEntity l && l.hasBook();
				bookNew = level.getBlockEntity(moved(s, SPOT)) instanceof LecternBlockEntity l && l.hasBook();
			} finally {
				tidy(helper, s);
			}
			int lying = looseNow.getOrDefault("minecraft:writable_book", 0);
			helper.assertTrue(bookOld || bookNew || lying == 1, "a lectern holding a book went up over a Mover's Crate (move " + end[0]
				+ "): the book is in no lectern and doesn't lie loose; loose " + looseNow + " (nothing packed may be destroyed, B1172)");
			helper.succeed();
		});
	}
}
