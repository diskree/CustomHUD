package com.minenash.customhud.HudElements.supplier;

import com.minenash.customhud.HudElements.interfaces.HudElement;
import com.minenash.customhud.complex.MusicAndRecordTracker;
import com.minenash.customhud.data.Flags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.function.Supplier;

import static com.minenash.customhud.CustomHud.CLIENT;

public class IdentifierSupplierElement implements HudElement {
    private static BlockPos blockPos() { return CLIENT.getCameraEntity().getBlockPos(); }

    public static final Supplier<Identifier> DIMENSION_ID = () -> CLIENT.world.getRegistryKey().getValue();
    public static final Supplier<Identifier> BIOME_ID = () -> CLIENT.world.getBiome(blockPos()).getKey().get().getValue();
    public static final Supplier<Identifier> MUSIC_ID = () -> MusicAndRecordTracker.isMusicPlaying ? MusicAndRecordTracker.musicId : null;
    public static final Supplier<Identifier> RECORD_ID = () -> MusicAndRecordTracker.isRecordPlaying ? MusicAndRecordTracker.getClosestRecord().id : null;



    private final Supplier<Identifier> supplier;

    public IdentifierSupplierElement(Supplier<Identifier> supplier, Flags flags) {
        this.supplier = supplier;
    }

    public Identifier getIdentifier() {
        return sanitize(supplier, null);
    }


    @Override
    public String getString() {
        Identifier id = getIdentifier();
        return id == null ? "-" : id.toString();
    }

    @Override
    public Number getNumber() {
        Identifier id = getIdentifier();
        return id == null ? 0 : id.toString().length();
    }

    @Override
    public boolean getBoolean() {
        Identifier id = getIdentifier();
        return id != null && !id.toString().isEmpty();
    }


}
