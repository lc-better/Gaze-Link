package com.demo.opencv.vision;

import android.graphics.Bitmap;
import android.media.Image;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import android.content.Context;


public class ImageFormatUtils {

    public static Mat yuv420(Image image) {
        android.graphics.Rect crop = image.getCropRect();
        int width = crop.width(), height = crop.height();
        byte[] bytes = new byte[width * height * 3 / 2];
        Image.Plane[] planes = image.getPlanes();
        YuvPlanes.copy(planes[0].getBuffer(), planes[0].getRowStride(), planes[0].getPixelStride(),
                crop.left, crop.top, width, height, bytes, 0);
        for (int i = 1; i <= 2; i++) {
            YuvPlanes.copy(planes[i].getBuffer(), planes[i].getRowStride(), planes[i].getPixelStride(),
                    crop.left / 2, crop.top / 2, width / 2, height / 2, bytes,
                    width * height + (i - 1) * width * height / 4);
        }
        Mat result = new Mat(height * 3 / 2, width, CvType.CV_8UC1);
        result.put(0, 0, bytes);
        return result;
    }

    public static Mat rgb(Image image) {
        Mat yuvMat = yuv420(image);
        int rows = image.getCropRect().height();
        int cols = image.getCropRect().width();
        // RGB三通道，保存采用CV_8UC3
        Mat rgbMat = new Mat(rows, cols, CvType.CV_8UC3);
        // 通过cv::cvtColor将yuv420转换为rgb格式
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_I420);
        // Mat是jni本地对象，释放对象是良好的习惯
        yuvMat.release();
        return rgbMat;
    }

    public static Mat gray(Image image) {
        Mat yuvMat = yuv420(image);
        int rows = image.getCropRect().height();
        int cols = image.getCropRect().width();
        // 灰色只有一个通道，保存采用CV_8UC1
        Mat grayMat = new Mat(rows, cols, CvType.CV_8UC1);
        // 通过cv::cvtColor将yuv420转换为灰色图片
        Imgproc.cvtColor(yuvMat, grayMat, Imgproc.COLOR_YUV2GRAY_I420);
        yuvMat.release();
        return grayMat;
    }

    public static Bitmap matToBitmap(Mat mat) {
            Bitmap bm = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat,bm);
            return bm;
    }

    public static File saveBitmapAsPng(Context context, Bitmap bitmap, String fileName) {
            // Use the app's internal storage
            File file = new File(context.getFilesDir(), fileName + ".png");
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // PNG is a lossless format, the compression factor (100) is ignored
                return file;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                try {
                    if (out != null) out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
    }
}