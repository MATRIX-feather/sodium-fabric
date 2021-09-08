package me.jellysquid.mods.sodium.model;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import me.jellysquid.mods.thingl.tessellation.IndexType;
import me.jellysquid.mods.sodium.model.quad.properties.ModelQuadWinding;

import java.nio.ByteBuffer;

public class IndexBufferBuilder {
    private final IntArrayList indices;

    public IndexBufferBuilder(int count) {
        this.indices = new IntArrayList(count);
    }

    public void add(int start, ModelQuadWinding winding) {
        for (int index : winding.getIndices()) {
            this.indices.add(start + index);
        }
    }

    public Result pop() {
        if (this.indices.isEmpty()) {
            return null;
        }

        return new Result(this.indices);
    }

    private static IndexType getOptimalIndexType(int count) {
        if (count < 65536) {
            return IndexType.UNSIGNED_SHORT;
        } else {
            return IndexType.UNSIGNED_INT;
        }
    }

    public int getCount() {
        return this.indices.size();
    }

    public void destroy() {

    }

    public static class Result {
        private final IntArrayList indices;

        private final int maxIndex, minIndex;
        private final IndexType format;

        private Result(IntArrayList indices) {
            this.indices = indices;

            int maxIndex = Integer.MIN_VALUE;
            int minIndex = Integer.MAX_VALUE;

            IntIterator it = this.indices.iterator();

            while (it.hasNext()) {
                int i = it.nextInt();

                minIndex = Math.min(minIndex, i);
                maxIndex = Math.max(maxIndex, i);
            }

            this.minIndex = minIndex;
            this.maxIndex = maxIndex;

            this.format = getOptimalIndexType(this.maxIndex - this.minIndex);
        }

        public int writeTo(int offset, ByteBuffer buffer) {
            IntIterator it = this.indices.iterator();
            int stride = this.format.getStride();

            int pointer = offset;

            while (it.hasNext()) {
                int value = it.nextInt() - this.minIndex;

                switch (this.format) {
                    case UNSIGNED_BYTE -> buffer.put(pointer, (byte) value);
                    case UNSIGNED_SHORT -> buffer.putShort(pointer, (short) value);
                    case UNSIGNED_INT -> buffer.putInt(pointer, value);
                }

                pointer += stride;
            }

            return pointer;
        }

        public int getByteSize() {
            return this.indices.size() * this.format.getStride();
        }

        public int getCount() {
            return this.indices.size();
        }

        public int getBaseVertex() {
            return this.minIndex;
        }

        public IndexType getFormat() {
            return this.format;
        }
    }
}
