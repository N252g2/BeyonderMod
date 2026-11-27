package io.github.n252g2.beyonder.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** A player's character: whether it has been made yet, its name, background and appearance. */
public final class CharacterData {
    public static final Codec<CharacterData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("created").forGetter(d -> d.created),
            Codec.STRING.optionalFieldOf("name", "").forGetter(d -> d.name),
            Codec.STRING.optionalFieldOf("background", "").forGetter(d -> d.background),
            Codec.INT.listOf().optionalFieldOf("appearance", List.of()).forGetter(d -> toList(d.appearance))
    ).apply(i, (created, name, background, appearance) -> {
        CharacterData d = new CharacterData();
        d.created = created;
        d.name = name;
        d.background = background;
        d.appearance = Appearance.sanitize(appearance.stream().mapToInt(Integer::intValue).toArray());
        return d;
    }));

    private boolean created;
    private String name = "";
    private String background = "";
    private int[] appearance = Appearance.defaults();

    public boolean created() {
        return created;
    }

    public String name() {
        return name;
    }

    public String background() {
        return background;
    }

    public int[] appearance() {
        return appearance.clone();
    }

    public void create(String name, Background background, int[] appearance) {
        this.created = true;
        this.name = name;
        this.background = background.id();
        this.appearance = Appearance.sanitize(appearance);
    }

    public void reset() {
        this.created = false;
    }

    private static List<Integer> toList(int[] a) {
        return java.util.Arrays.stream(a).boxed().toList();
    }
}
