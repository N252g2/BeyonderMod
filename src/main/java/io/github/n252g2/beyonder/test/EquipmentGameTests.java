package io.github.n252g2.beyonder.test;

import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.content.BeyonderContent;
import io.github.n252g2.beyonder.core.BeyonderData;
import io.github.n252g2.beyonder.core.Beyonders;
import io.github.n252g2.beyonder.core.Equipment;
import io.github.n252g2.beyonder.core.EquipmentRules;
import io.github.n252g2.beyonder.core.Pathway;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the equipment rules on a mock player (run by the CI smoke test; never in normal play). */
@GameTestHolder(Beyonder.MODID)
@PrefixGameTestTemplate(false)
public final class EquipmentGameTests {
    private EquipmentGameTests() {}

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.getInventory().selected = 0;
        p.getInventory().items.set(0, new ItemStack(Items.BREAD, 3));
        Equipment.get(p).items().set(Equipment.Slot.WEAPON.ordinal(), new ItemStack(BeyonderContent.LONGSWORD.get()));
        return p;
    }

    @GameTest(template = "empty")
    public static void menuHasEquipmentSlots(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(p.inventoryMenu.slots.size() == 46 + Equipment.SIZE,
                "inventory menu has " + p.inventoryMenu.slots.size() + " slots");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drawingPutsTheWeaponInHand(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertTrue(p.getMainHandItem().is(Items.BREAD), "sheathed: the hotbar item is in hand");
        h.assertTrue(!Equipment.mayUse(p, new ItemStack(BeyonderContent.LONGSWORD.get())), "sheathed weapons can't be used");
        EquipmentRules.draw(p);
        h.assertTrue(p.getMainHandItem().is(BeyonderContent.LONGSWORD.get()), "drawn: the weapon is in hand");
        h.assertTrue(p.getInventory().removeFromSelected(true).isEmpty(), "a drawn weapon can't be dropped with Q");
        // whatever replaces the main hand while drawn goes to the weapon slot, not the hotbar
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        h.assertTrue(Equipment.item(p, Equipment.Slot.WEAPON).is(Items.STICK), "main-hand writes go to the weapon slot");
        h.assertTrue(p.getInventory().items.get(0).is(Items.BREAD), "the hotbar is untouched");
        EquipmentRules.sheathe(p);
        h.assertTrue(p.getMainHandItem().is(Items.BREAD), "sheathed again: the hotbar item is back in hand");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void swappingTradesWeaponAndSidearm(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(BeyonderContent.REVOLVER.get()));
        EquipmentRules.swap(p);
        h.assertTrue(Equipment.item(p, Equipment.Slot.WEAPON).is(BeyonderContent.REVOLVER.get()), "revolver now main");
        h.assertTrue(p.getOffhandItem().is(BeyonderContent.LONGSWORD.get()), "longsword now sidearm");
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        EquipmentRules.swap(p);
        h.assertTrue(p.getOffhandItem().is(Items.TORCH), "a torch can't be swapped into the weapon slot");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedActsDigestLess(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BeyonderData d = Beyonders.get(p);
        d.become(Pathway.SEER, 9);
        d.setDigestion(0.0F);
        Beyonders.act(p, 9, 6.0F, "test");
        float first = d.digestion();
        Beyonders.act(p, 9, 6.0F, "test");
        float second = d.digestion() - first;
        h.assertTrue(Math.abs(first - 1.5F) < 0.01F, "first act at Sequence 9 digests 1.5%, got " + first);
        h.assertTrue(second < first * 0.7F, "the same act again counts for less, got " + second);
        h.succeed();
    }
}
