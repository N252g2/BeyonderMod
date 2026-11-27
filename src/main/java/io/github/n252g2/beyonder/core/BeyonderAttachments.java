package io.github.n252g2.beyonder.core;

import io.github.n252g2.beyonder.Beyonder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BeyonderAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Beyonder.MODID);

    /** The player's pathway, Sequence, Spirituality, digestion and loss of control. Survives death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BeyonderData>> BEYONDER =
            TYPES.register("beyonder", () -> AttachmentType.builder(BeyonderData::new)
                    .serialize(BeyonderData.CODEC)
                    .copyOnDeath()
                    .build());

    /** The player's character: name, background and appearance. Survives death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CharacterData>> CHARACTER =
            TYPES.register("character", () -> AttachmentType.builder(CharacterData::new)
                    .serialize(CharacterData.CODEC)
                    .copyOnDeath()
                    .build());

    /**
     * Clothing, weapon, ammo, accessory, artifact and belt slots. Copied over on death, but death
     * empties it first unless keepInventory is on (see EquipmentRules).
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EquipmentData>> EQUIPMENT =
            TYPES.register("equipment", () -> AttachmentType.serializable(EquipmentData::new)
                    .copyOnDeath()
                    .build());

    private BeyonderAttachments() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
