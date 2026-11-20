package com.demo.opencv.vision;

import java.nio.ByteBuffer;

/** Copy a plane using its actual strides, including a cropped origin. */
public final class YuvPlanes {
    public static void copy(ByteBuffer input, int rowStride, int pixelStride,
                            int x, int y, int width, int height, byte[] output, int offset) {
        int origin = input.position();
        for (int row = 0; row < height; row++) {
            int start = origin + (y + row) * rowStride + x * pixelStride;
            for (int col = 0; col < width; col++) output[offset++] = input.get(start + col * pixelStride);
        }
    }
}
