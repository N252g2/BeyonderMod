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

    private BeyonderAttachments() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
