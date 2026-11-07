package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, saves and optional mods, 2026-11-02; verifies B791 for the Crafter): a Crafter halfway through a job whose
 * item came from a mod that has since been taken out of the pack can't finish it, but what he fetched for it lies in his
 * bag, a separate saved attachment. Reopened without the mod, the bag keeps every stack (the Crafter tidies it back into
 * his chest when he has no job), and the villager loads at all.
 */
public class QaCrafterJobRemovedMod1102GameTests implements FabricGameTest {

	static Tag attachment(CompoundTag tag, String name) {
		return tag.getCompound("fabric:attachments").get("aliveworkplace:" + name);
	}

	private static Villager crafterMidJob(GameTestHelper helper) {
		Villager crafter = EntityType.VILLAGER.create(helper.getLevel());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(crafter);
		bag.addAll(Items.OAK_PLANKS, 12);
		bag.addAll(Items.STICK, 8);
		bag.addAll(Items.IRON_INGOT, 3);
		Map<Item, Integer> in = new LinkedHashMap<>();
		in.put(Items.OAK_PLANKS, 6);
		in.put(Items.STICK, 4);
		Crafting.Step step = new Crafting.Step(in, new ItemStack(Items.LADDER, 3), 1);
		Map<Item, Integer> takes = new LinkedHashMap<>(in);
		takes.put(Items.IRON_INGOT, 3);
		ModAttachments.CRAFTER_JOB.set(crafter, new CrafterJob(new BlockPos(3, 2, 3), Optional.empty(), Items.LADDER, 3, List.of(step),
			takes, List.of(new BlockPos(1, 2, 1)), "", CrafterJob.Phase.CRAFTING, 0));
		return crafter;
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aCraftersBagKeepsEveryStackWhenTheJobsTargetCameFromARemovedMod(GameTestHelper helper) {
		CompoundTag saved = crafterMidJob(helper).saveWithoutId(new CompoundTag());
		Tag job = attachment(saved, "crafter_job");
		helper.assertTrue(job instanceof CompoundTag, "the Crafter's job didn't save: " + job);
		((CompoundTag) job).putString("target", "gonemod:brass_ladder");

		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		BuilderBag back = ModAttachments.BUILDER_BAG.get(reopened);
		helper.assertTrue(back != null && back.count(Items.OAK_PLANKS) == 12 && back.count(Items.STICK) == 8 && back.count(Items.IRON_INGOT) == 3,
			"the Crafter's bag after the load: " + (back == null ? "gone" : "planks " + back.count(Items.OAK_PLANKS) + " of 12, sticks "
				+ back.count(Items.STICK) + " of 8, iron " + back.count(Items.IRON_INGOT) + " of 3"));
		CrafterJob jobBack = ModAttachments.CRAFTER_JOB.get(reopened);
		helper.assertTrue(jobBack == null || jobBack.target() != Items.LADDER, "the renamed target came back as a ladder");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aCraftersBagKeepsEveryStackWhenAStepsMakingsCameFromARemovedMod(GameTestHelper helper) {
		CompoundTag saved = crafterMidJob(helper).saveWithoutId(new CompoundTag());
		CompoundTag job = (CompoundTag) attachment(saved, "crafter_job");
		CompoundTag in = job.getList("steps", Tag.TAG_COMPOUND).getCompound(0).getCompound("in");
		Tag sticks = in.get("minecraft:stick");
		helper.assertTrue(sticks != null, "no sticks in the saved step: " + job);
		in.remove("minecraft:stick");
		in.put("gonemod:brass_rod", sticks);
		CompoundTag takes = job.getCompound("takes");
		Tag iron = takes.get("minecraft:iron_ingot");
		helper.assertTrue(iron != null, "no iron in the saved takes: " + job);
		takes.remove("minecraft:iron_ingot");
		takes.put("gonemod:brass_ingot", iron);

		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		BuilderBag back = ModAttachments.BUILDER_BAG.get(reopened);
		helper.assertTrue(back != null && back.count(Items.OAK_PLANKS) == 12 && back.count(Items.STICK) == 8 && back.count(Items.IRON_INGOT) == 3,
			"the Crafter's bag after the load: " + (back == null ? "gone" : "planks " + back.count(Items.OAK_PLANKS) + " of 12, sticks "
				+ back.count(Items.STICK) + " of 8, iron " + back.count(Items.IRON_INGOT) + " of 3"));
		helper.assertTrue(reopened.getVillagerData() != null, "the villager didn't load");
		helper.succeed();
	}
}
