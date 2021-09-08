package me.jellysquid.mods.thingl.shader.uniform;

public abstract class Uniform<T> {
    protected final int index;

    protected Uniform(int index) {
        this.index = index;
    }

    public abstract void set(T value);
}
