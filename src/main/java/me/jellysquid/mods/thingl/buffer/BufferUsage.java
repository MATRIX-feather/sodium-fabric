package me.jellysquid.mods.thingl.buffer;

import org.lwjgl.opengl.GL20C;

public enum BufferUsage {
    STREAM_DRAW(GL20C.GL_STREAM_DRAW),
    STREAM_READ(GL20C.GL_STREAM_READ),
    STREAM_COPY(GL20C.GL_STREAM_COPY),
    STATIC_DRAW(GL20C.GL_STATIC_DRAW),
    STATIC_READ(GL20C.GL_STATIC_READ),
    STATIC_COPY(GL20C.GL_STATIC_COPY),
    DYNAMIC_DRAW(GL20C.GL_DYNAMIC_DRAW),
    DYNAMIC_READ(GL20C.GL_DYNAMIC_READ),
    DYNAMIC_COPY(GL20C.GL_DYNAMIC_COPY);

    private final int id;

    BufferUsage(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}
