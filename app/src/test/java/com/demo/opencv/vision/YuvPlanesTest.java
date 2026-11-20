package com.demo.opencv.vision;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;
public class YuvPlanesTest {
    @Test public void paddedRowsAreNotPixels() {
        byte[] out=new byte[6];
        YuvPlanes.copy(ByteBuffer.wrap(new byte[]{1,2,3,99,4,5,6,99}),4,1,0,0,3,2,out,0);
        assertArrayEquals(new byte[]{1,2,3,4,5,6},out);
    }
    @Test public void interleavedChromaAndCropUseBothStrides() {
        byte[] out=new byte[2];
        YuvPlanes.copy(ByteBuffer.wrap(new byte[]{1,99,2,99,3,99,4,99,5,99,6}),6,2,1,0,1,2,out,0);
        assertArrayEquals(new byte[]{2,5},out);
    }
    @Test public void bufferPositionAndOutputOffsetArePreserved() {
        ByteBuffer b=ByteBuffer.wrap(new byte[]{99,1,2,3}); b.position(1); byte[] out=new byte[4];
        YuvPlanes.copy(b,3,1,0,0,3,1,out,1);
        assertArrayEquals(new byte[]{0,1,2,3},out); assertEquals(1,b.position());
    }
}