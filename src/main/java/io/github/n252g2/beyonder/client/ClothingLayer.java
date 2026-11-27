package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.core.Equipment;
import io.github.n252g2.beyonder.item.ClothingItem;
import io.github.n252g2.beyonder.net.ClientInbox;
import io.github.n252g2.beyonder.net.EquipStatePayload;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;

/**
 * Clothing drawn on the character. Hats are small models on the head; everything else is a
 * skin-shaped texture on a slightly inflated player model, each slot a little further out so they
 * stack in order: shirt and trousers, shoes, coat, then gloves. Armour hides what's under it, unless
 * the player chose to show their clothes over it.
 */
public class ClothingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Drawing order and how far out each slot sits (in pixels). */
    private static final Equipment.Slot[] ORDER = {Equipment.Slot.SHIRT, Equipment.Slot.TROUSERS, Equipment.Slot.SHOES,
            Equipment.Slot.COAT, Equipment.Slot.GLOVES, Equipment.Slot.HAT};
    private static final Map<Equipment.Slot, Float> INFLATE = Map.of(Equipment.Slot.SHIRT, 0.3F, Equipment.Slot.TROUSERS, 0.3F,
            Equipment.Slot.SHOES, 0.38F, Equipment.Slot.COAT, 0.46F, Equipment.Slot.GLOVES, 0.52F);

    private final Map<String, PlayerModel<AbstractClientPlayer>> bodies;
    private final Map<ClothingItem.Hat, ModelPart> hats;

    public ClothingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            Map<String, PlayerModel<AbstractClientPlayer>> bodies, Map<ClothingItem.Hat, ModelPart> hats) {
        super(parent);
        this.bodies = bodies;
        this.hats = hats;
    }

    // ------------------------------------------------------------------ models

    private static ModelLayerLocation body(Equipment.Slot slot, boolean slim) {
        return new ModelLayerLocation(Beyonder.id("clothing_" + slot.name().toLowerCase() + (slim ? "_slim" : "")), "main");
    }

    private static ModelLayerLocation hat(ClothingItem.Hat hat) {
        return new ModelLayerLocation(Beyonder.id("hat_" + hat.name().toLowerCase()), "main");
    }

    static void registerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (Map.Entry<Equipment.Slot, Float> e : INFLATE.entrySet()) {
            for (boolean slim : new boolean[] {false, true}) {
                float d = e.getValue();
                event.registerLayerDefinition(body(e.getKey(), slim),
                        () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(d), slim), 64, 64));
            }
        }
        event.registerLayerDefinition(hat(ClothingItem.Hat.TOP_HAT), () -> hatMesh(b -> b
                .texOffs(0, 0).addBox(-4.0F, -16.0F, -4.0F, 8, 9, 8, new CubeDeformation(0.6F))
                .texOffs(0, 17).addBox(-6.0F, -8.0F, -6.0F, 12, 1, 12, new CubeDeformation(0.6F))));
        event.registerLayerDefinition(hat(ClothingItem.Hat.BOWLER), () -> hatMesh(b -> b
                .texOffs(0, 0).addBox(-4.0F, -11.5F, -4.0F, 8, 4, 8, new CubeDeformation(0.6F))
                .texOffs(0, 12).addBox(-5.0F, -8.0F, -5.0F, 10, 1, 10, new CubeDeformation(0.6F))));
        event.registerLayerDefinition(hat(ClothingItem.Hat.FLAT_CAP), () -> hatMesh(b -> b
                .texOffs(0, 0).addBox(-4.0F, -9.5F, -4.0F, 8, 2, 8, new CubeDeformation(0.65F))
                .texOffs(0, 10).addBox(-4.0F, -8.0F, -7.5F, 8, 1, 3, new CubeDeformation(0.3F))));
    }

    private static LayerDefinition hatMesh(java.util.function.UnaryOperator<CubeListBuilder> cubes) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hat", cubes.apply(CubeListBuilder.create()), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Bakes the models and adds the layer to both player renderers. */
    @SuppressWarnings("unchecked")
    static void addLayers(EntityRenderersEvent.AddLayers event) {
        Map<String, PlayerModel<AbstractClientPlayer>> bodies = new HashMap<>();
        for (Equipment.Slot slot : INFLATE.keySet()) {
            for (boolean slim : new boolean[] {false, true}) {
                bodies.put(key(slot, slim), new PlayerModel<>(event.getEntityModels().bakeLayer(body(slot, slim)), slim));
            }
        }
        Map<ClothingItem.Hat, ModelPart> hats = new EnumMap<>(ClothingItem.Hat.class);
        for (ClothingItem.Hat h : ClothingItem.Hat.values()) {
            if (h != ClothingItem.Hat.NONE) {
                hats.put(h, event.getEntityModels().bakeLayer(hat(h)).getChild("hat"));
            }
        }
        BODIES = bodies;
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
                renderer.addLayer(new ClothingLayer(renderer, bodies, hats));
            }
        }
    }

    private static Map<String, PlayerModel<AbstractClientPlayer>> BODIES = Map.of();

    private static String key(Equipment.Slot slot, boolean slim) {
        return slot.name() + (slim ? "_slim" : "");
    }

    // ------------------------------------------------------------------ what's worn

    /** The clothing in a slot, as far as this client knows. */
    static ItemStack worn(Player player, Equipment.Slot slot) {
        if (player.isLocalPlayer()) {
            return Equipment.item(player, slot);
        }
        EquipStatePayload s = ClientInbox.equipment(player.getId());
        if (s == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < Equipment.Slot.CLOTHING.length; i++) {
            if (Equipment.Slot.CLOTHING[i] == slot) {
                return s.item(EquipStatePayload.CLOTHING + i);
            }
        }
        return ItemStack.EMPTY;
    }

    private static EquipmentSlot armourOver(Equipment.Slot slot) {
        return switch (slot) {
            case HAT -> EquipmentSlot.HEAD;
            case TROUSERS -> EquipmentSlot.LEGS;
            case SHOES -> EquipmentSlot.FEET;
            default -> EquipmentSlot.CHEST;
        };
    }

    /** The piece to draw in a slot, or null when there's none or armour covers it. */
    static ClothingItem visible(Player player, Equipment.Slot slot) {
        ItemStack stack = worn(player, slot);
        if (!(stack.getItem() instanceof ClothingItem item)) {
            return null;
        }
        if (!EquipmentClient.clothesOverArmour(player) && !player.getItemBySlot(armourOver(slot)).isEmpty()) {
            return null;
        }
        return item;
    }

    private static boolean slim(AbstractClientPlayer player) {
        return player.getSkin().model() == PlayerSkin.Model.SLIM;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
            float limbAmount, float partialTick, float ageInTicks, float headYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        boolean slim = slim(player);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        for (Equipment.Slot slot : ORDER) {
            ClothingItem item = visible(player, slot);
            if (item == null) {
                continue;
            }
            if (slot == Equipment.Slot.HAT) {
                ModelPart hat = hats.get(item.hat());
                if (hat != null) {
                    hat.copyFrom(getParentModel().head);
                    hat.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(item.texture(false))), light, overlay);
                }
                continue;
            }
            PlayerModel<AbstractClientPlayer> model = bodies.get(key(slot, slim));
            getParentModel().copyPropertiesTo(model);
            model.setAllVisible(false);
            model.body.visible = true;
            model.rightArm.visible = true;
            model.leftArm.visible = true;
            model.rightLeg.visible = true;
            model.leftLeg.visible = true;
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(item.texture(slim))), light, overlay, -1);
        }
    }

    /** First person: sleeves and gloves on the arm you see. */
    static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        boolean slim = slim(player);
        for (Equipment.Slot slot : new Equipment.Slot[] {Equipment.Slot.COAT, Equipment.Slot.GLOVES}) {
            ClothingItem item = visible(player, slot);
            PlayerModel<AbstractClientPlayer> model = BODIES.get(key(slot, slim));
            if (item == null || model == null || !item.sleeves()) {
                continue;
            }
            ModelPart arm = event.getArm() == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            arm.resetPose();
            arm.zRot = event.getArm() == HumanoidArm.RIGHT ? 0.1F : -0.1F; // vanilla's arm at rest
            arm.visible = true;
            arm.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(item.texture(slim))),
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        }
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
