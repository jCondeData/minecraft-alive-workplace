package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * B943: a Legend struck by lightning becomes a witch without dying, so no death hook saw it: they kept their Legend
 * slot for ever (its clock never started), the Book of Families kept them living and their partner stayed married to nobody. Now the lightning
 * hook (B928's) handles it as a death.
 */
public class B943GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation TEST = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_legend");

	/** A married Legend in a village struck into a witch: their slot's clock starts as after a death, the book dates their death, their partner is widowed. */
	//$ gametest_ticks_batch AREA '100' '"b943Legend"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b943Legend")
	public void aLegendStruckIntoAWitchIsHandledAsADeath(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				LegendRecord.get(level).forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
		QaBuilderEdges1104bGameTests.difficulty(helper, Difficulty.NORMAL);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager ada = villager(helper, new BlockPos(4, 2, 4), "Ada");
		Villager tomas = villager(helper, new BlockPos(16, 2, 16), "Tomas");
		helper.onEachTick(() -> QaBuilderEdges1104bGameTests.calmWitches(helper));
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			long today = Chronicle.day(level);
			Legends.make(level, ada, Legends.get(TEST).orElseThrow(), "test", Optional.of(hall));
			ModAttachments.PARTNER.set(ada, new Couples.Partner(tomas.getUUID(), tomas.getDisplayName(), today, true));
			ModAttachments.PARTNER.set(tomas, new Couples.Partner(ada.getUUID(), ada.getDisplayName(), today, true));
			FamilyBook.round(level, hall, entity);
			helper.assertTrue(entity.families().get(ada.getUUID()) != null && !entity.families().get(ada.getUUID()).dead(), "setup: Ada not in the book alive");
			helper.assertTrue(LegendRecord.get(level).entry(ada.getUUID()).map(LegendRecord.Entry::holds).orElse(false), "setup: Ada holds no Legend slot");

			QaBuilderEdges1104bGameTests.strike(level, ada);
			helper.assertTrue(ada.isRemoved(), "setup: the lightning didn't turn Ada");

			Optional<LegendRecord.Entry> slot = LegendRecord.get(level).entry(ada.getUUID());
			// as a death with no grave: the slot's clock starts today (it falls free after LegendRecord.FALL_DAYS)
			helper.assertTrue(slot.isPresent() && slot.get().gone() == today && slot.get().grave().isEmpty(),
				"the struck Legend's slot clock didn't start: " + slot + " (expected gone on day " + today + ")");
			FamilyBook.Person page = entity.families().get(ada.getUUID());
			helper.assertTrue(page != null && page.dead() && page.died() == today, "the Book of Families still has the witch living: " + page);
			helper.assertTrue(!ModAttachments.PARTNER.has(tomas), "Tomas is still married to the witch: " + ModAttachments.PARTNER.get(tomas));
			Couples.LatePartner late = ModAttachments.LATE_PARTNER.get(tomas);
			helper.assertTrue(late != null && late.id().equals(ada.getUUID()) && ModAttachments.WIDOWED_DAY.get(tomas) == today,
				"Tomas isn't widowed of Ada: " + late);
			helper.succeed();
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
		return v;
	}
}
