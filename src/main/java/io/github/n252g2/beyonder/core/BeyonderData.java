package io.github.n252g2.beyonder.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

/**
 * A player's Beyonder state. Lives on the player as a data attachment (saved with the player,
 * kept on death) and is synced to the client by {@link io.github.n252g2.beyonder.net.BeyonderNetwork}.
 */
public final class BeyonderData {
    public static final Codec<BeyonderData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("pathway", "none").forGetter(d -> d.pathway.id()),
            Codec.INT.optionalFieldOf("sequence", Pathway.MORTAL).forGetter(d -> d.sequence),
            Codec.FLOAT.optionalFieldOf("spirituality", 0.0F).forGetter(d -> d.spirituality),
            Codec.FLOAT.optionalFieldOf("digestion", 0.0F).forGetter(d -> d.digestion),
            Codec.FLOAT.optionalFieldOf("lossOfControl", 0.0F).forGetter(d -> d.lossOfControl)
    ).apply(i, BeyonderData::new));

    private Pathway pathway;
    private int sequence;
    private float spirituality;
    private float digestion;
    private float lossOfControl;

    public BeyonderData() {
        this("none", Pathway.MORTAL, 0.0F, 0.0F, 0.0F);
    }

    private BeyonderData(String pathway, int sequence, float spirituality, float digestion, float lossOfControl) {
        this.pathway = Pathway.byId(pathway);
        this.sequence = this.pathway == Pathway.NONE ? Pathway.MORTAL : Mth.clamp(sequence, 0, 9);
        this.spirituality = spirituality;
        this.digestion = digestion;
        this.lossOfControl = lossOfControl;
    }

    public boolean isBeyonder() {
        return pathway != Pathway.NONE;
    }

    public Pathway pathway() {
        return pathway;
    }

    public int sequence() {
        return sequence;
    }

    public float spirituality() {
        return spirituality;
    }

    public float maxSpirituality() {
        return Pathway.maxSpirituality(sequence);
    }

    /** 0..100: how far the current potion has been digested through acting. */
    public float digestion() {
        return digestion;
    }

    /** 0..100: at 100 the Beyonder loses control. */
    public float lossOfControl() {
        return lossOfControl;
    }

    public void become(Pathway pathway, int sequence) {
        this.pathway = pathway;
        this.sequence = pathway == Pathway.NONE ? Pathway.MORTAL : Mth.clamp(sequence, 0, 9);
        this.digestion = 0.0F;
        this.spirituality = Math.min(spirituality, maxSpirituality());
    }

    public void setSpirituality(float value) {
        this.spirituality = Mth.clamp(value, 0.0F, maxSpirituality());
    }

    public void setDigestion(float value) {
        this.digestion = Mth.clamp(value, 0.0F, 100.0F);
    }

    public void setLossOfControl(float value) {
        this.lossOfControl = Mth.clamp(value, 0.0F, 100.0F);
    }
}
