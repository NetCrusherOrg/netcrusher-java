package org.netcrusher.filter;

import java.nio.ByteBuffer;

/**
 * <p>Filter for tranferred data. Filtering is made in reactor's thread so all blocking I/O should be made in other
 * thread with the copy of the input buffer.</p>
 *
 *
 * <p>Filter that filters nothing:</p>
 * <pre>
 * ByteBuffer filter(ByteBuffer bb) {
 *     return bb;
 * }
 * </pre>
 *
 * <p>Filter that inverses all bytes:</p>
 * <pre>
 * ByteBuffer filter(ByteBuffer bb) {
 *     final int size = bb.limit();
 *
 *     if (bb.hasArray()) {
 *         final byte[] data = bb.array();
 *         for (int i = 0; i < size; i++) {
 *             data[i] = (byte) ~data[i];
 *         }
 *     } else {
 *         for (int i = 0; i < size; i++) {
 *             bb.put(i, (byte) ~bb.get(i));
 *         }
 *     }
 *
 *     bb.position(0);
 *     bb.limit(size);
 *
 *     return bb;
 * }
 *</pre>
 */
public interface ByteBufferFilter {

    /**
     * Callback that filters input byte buffer and return output byte buffer
     * @param bb Input byte buffer with position set to 0 and limit set to buffer size
     * @return Output byte buffer with position set to 0 and limit set buffer size. If filtering was made in-place
     * then the input byte buffer allowed to be returned
     */
    ByteBuffer filter(ByteBuffer bb);

}
