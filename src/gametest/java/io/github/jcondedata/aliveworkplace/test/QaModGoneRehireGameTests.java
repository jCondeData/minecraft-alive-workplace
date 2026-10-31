package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * QA (lane E, saves, B542's fix): after Create or CobbleFurnies is taken off a server, an ex-Mechanic, ex-Conductor or
 * ex-Furnicrafter loads as a jobless Novice with no trades (QaModGoneVillagersGameTests). The rest of the promise: she
 * is the same villager, so what the mod saved on her stays (her family name, her tally of crafted items); saved and
 * loaded again she stays a jobless Novice (the fix runs on every load, so it must not misfire on her now-empty save);
 * and a player can hire her again: given a new job she trades that job's Novice goods, not nothing and not her old
 * job's.
 */
public class QaModGoneRehireGameTests implements FabricGameTest {

	/** Rosa, a level-3 desert toolsmith with 70 xp, her trades, a family name and 12 crafted items, saved as {@code job}. */
	private static CompoundTag savedAs(GameTestHelper helper, String job) {
		Villager rosa = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		rosa.setVillagerData(rosa.getVillagerData().setType(VillagerType.DESERT).setProfession(VillagerProfession.TOOLSMITH).setLevel(3));
		rosa.setVillagerXp(70);
		rosa.setCustomName(Component.literal("Rosa"));
		ModAttachments.FAMILY_NAME.set(rosa, 7);
		ModAttachments.ITEMS_CRAFTED.set(rosa, 12);
		helper.assertTrue(!rosa.getOffers().isEmpty(), "the setup: Rosa has no trades");
		CompoundTag saved = new CompoundTag();
		rosa.saveWithoutId(saved);
		rosa.discard();
		CompoundTag data = saved.getCompound("VillagerData");
		data.put("profession", StringTag.valueOf(job));
		saved.put("VillagerData", data);
		return saved;
	}

	private static Villager load(GameTestHelper helper, CompoundTag saved) {
		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(saved.copy());
		return loaded;
	}

	/** What the mod saved on her comes through the job's mod being removed. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anExMechanicKeepsWhatTheModSavedOnHer(GameTestHelper helper) {
		Villager rosa = load(helper, savedAs(helper, "aliveworkplace:mechanic"));
		helper.assertTrue(rosa.getVillagerData().getProfession() == VillagerProfession.NONE, "the setup: the ex-Mechanic isn't jobless");
		helper.assertTrue(ModAttachments.FAMILY_NAME.getOrElse(rosa, -1) == 7,
			"the ex-Mechanic lost her family name: " + ModAttachments.FAMILY_NAME.getOrElse(rosa, -1));
		helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(rosa, -1) == 12,
			"the ex-Mechanic's 12 crafted items became " + ModAttachments.ITEMS_CRAFTED.getOrElse(rosa, -1));
		helper.assertTrue(rosa.getCustomName() != null && rosa.getCustomName().getString().equals("Rosa"), "the ex-Mechanic lost her name");
		rosa.discard();
		helper.succeed();
	}

	/** Saved and loaded a second (and third) time, the ex-Conductor stays the same jobless Novice with no trades. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anExConductorStaysAJoblessNoviceOverMoreSaves(GameTestHelper helper) {
		Villager rosa = load(helper, savedAs(helper, "aliveworkplace:conductor"));
		for (int save = 2; save <= 3; save++) {
			CompoundTag again = new CompoundTag();
			rosa.saveWithoutId(again);
			rosa.discard();
			rosa = load(helper, again);
			helper.assertTrue(rosa.getVillagerData().getProfession() == VillagerProfession.NONE, "save " + save + ": the ex-Conductor has a job");
			helper.assertTrue(rosa.getOffers().isEmpty(), "save " + save + ": the ex-Conductor has " + rosa.getOffers().size() + " trades");
			helper.assertTrue(rosa.getVillagerData().getLevel() == 1 && rosa.getVillagerXp() == 0,
				"save " + save + ": the ex-Conductor is level " + rosa.getVillagerData().getLevel() + " with " + rosa.getVillagerXp() + " xp");
			helper.assertTrue(rosa.getVillagerData().getType() == VillagerType.DESERT, "save " + save + ": the ex-Conductor isn't a desert villager");
			helper.assertTrue(ModAttachments.FAMILY_NAME.getOrElse(rosa, -1) == 7, "save " + save + ": the ex-Conductor lost her family name");
		}
		rosa.discard();
		helper.succeed();
	}

	/**
	 * Hired again (she takes a lectern: her job becomes Librarian), the ex-Furnicrafter trades a Novice Librarian's
	 * goods: some trades, all of them a librarian's first-level ones (paper, books, a bookshelf), none of the old
	 * toolsmith's tools; and a save keeps them.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anExFurnicrafterHiredAgainTradesHerNewJobsGoods(GameTestHelper helper) {
		Villager rosa = load(helper, savedAs(helper, "aliveworkplace:furnicrafter"));
		helper.assertTrue(rosa.getOffers().isEmpty(), "the setup: the ex-Furnicrafter has trades");
		rosa.setVillagerData(rosa.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
		helper.assertTrue(!rosa.getOffers().isEmpty(), "the ex-Furnicrafter, hired as a Librarian, has no trades at all");
		for (MerchantOffer offer : rosa.getOffers()) {
			String goods = offer.getCostA().getItem() + " -> " + offer.getResult().getItem();
			helper.assertTrue(!goods.contains("_axe") && !goods.contains("_pickaxe") && !goods.contains("_shovel") && !goods.contains("_hoe") && !goods.contains("bell"),
				"the new Librarian still trades a toolsmith's goods: " + goods);
		}
		helper.assertTrue(rosa.getOffers().size() <= 2, "a Novice Librarian has " + rosa.getOffers().size() + " trades, more than a first level's two");
		int trades = rosa.getOffers().size();
		CompoundTag saved = new CompoundTag();
		rosa.saveWithoutId(saved);
		rosa.discard();
		Villager again = load(helper, saved);
		helper.assertTrue(again.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "the new Librarian lost her job in a save");
		helper.assertTrue(again.getOffers().size() == trades, "the new Librarian's " + trades + " trades became " + again.getOffers().size() + " in a save");
		again.discard();
		helper.succeed();
	}
}
