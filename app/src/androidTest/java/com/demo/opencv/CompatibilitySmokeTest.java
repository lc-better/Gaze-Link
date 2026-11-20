package com.demo.opencv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/** Run only on the dedicated compatibility AVD; no accuracy claim is made for synthetic images. */
@RunWith(AndroidJUnit4.class)
public class CompatibilitySmokeTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test public void nativeVisionLibrariesLoadAndInfer() throws Exception {
        assertTrue(OpenCVLoader.initDebug());
        Mat matrix = Mat.eye(8, 8, CvType.CV_8UC1);
        try { assertEquals(1, matrix.get(0, 0)[0], 0); } finally { matrix.release(); }
        BaseOptions options = BaseOptions.builder().setModelAssetPath("face_landmarker.task").build();
        try (FaceLandmarker landmarker = FaceLandmarker.createFromOptions(context(),
                FaceLandmarker.FaceLandmarkerOptions.builder().setBaseOptions(options).build());
             MPImage blank = new BitmapImageBuilder(Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)).build()) {
            assertNotNull(landmarker.detect(blank));
        }
    }

    @Test public void cameraFramesReachAnalysisPipeline() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            java.util.concurrent.atomic.AtomicLong frames = new java.util.concurrent.atomic.AtomicLong();
            long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
            do {
                scenario.onActivity(activity -> {
                    try { frames.set((Long) field(activity, "frames")); }
                    catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                });
                if (frames.get() > 0) break;
                Thread.sleep(200);
            } while (android.os.SystemClock.elapsedRealtime() < deadline);
            assertTrue("No front-camera frame reached RGB conversion and face analysis", frames.get() > 0);
        }
    }

    @Test public void mainTargetsAndScrollableGuideStayInsideWindow() throws Exception {
        android.content.SharedPreferences prefs = context().getSharedPreferences("communication", Context.MODE_PRIVATE);
        boolean hadGuide = prefs.contains("guide_read"), oldGuide = prefs.getBoolean("guide_read", false);
        prefs.edit().putBoolean("guide_read", false).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // WindowManager applies fixed/ignored orientation asynchronously.
            Thread.sleep(1000);
            onView(withText("欢迎使用 Gaze Link")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                try {
                    FrameLayout shell = (FrameLayout) field(activity, "shell");
                    View left = (View) field(activity, "leftTarget"), right = (View) field(activity, "rightTarget");
                    FrameLayout guide = (FrameLayout) field(activity, "guideOverlay");
                    ScrollView viewport = (ScrollView) guide.getChildAt(0);
                    Rect bounds = new Rect(), visible = new Rect(); shell.getGlobalVisibleRect(bounds);
                    for (View target : new View[]{left, right, viewport}) {
                        assertTrue(target.getGlobalVisibleRect(visible));
                        assertTrue("Target clipped horizontally", visible.width() == target.getWidth());
                        assertTrue("Target clipped vertically", visible.height() == target.getHeight());
                        assertTrue("Target outside safe content area", bounds.contains(visible));
                    }
                    float density = activity.getResources().getDisplayMetrics().density;
                    assertTrue("Eye targets too short", left.getHeight() >= 48 * density);
                    if (viewport.getChildAt(0).getHeight() > viewport.getHeight()) {
                        assertTrue("Overflowing guide must scroll", viewport.canScrollVertically(1));
                    }
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            if (screenshot != null) {
                File output = new File(context().getExternalFilesDir(null), "compatibility-guide.png");
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream);
                } finally { screenshot.recycle(); }
            }
        } finally {
            android.content.SharedPreferences.Editor editor = prefs.edit();
            if (hadGuide) editor.putBoolean("guide_read", oldGuide); else editor.remove("guide_read");
            editor.commit();
        }
    }

    private Object field(MainActivity activity, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true); return field.get(activity);
    }

    @Test public void communicationLabelsFitTheirTargets() throws Exception {
        android.content.SharedPreferences prefs = context().getSharedPreferences("communication", Context.MODE_PRIVATE);
        boolean hadGuide = prefs.contains("guide_read"), oldGuide = prefs.getBoolean("guide_read", false);
        prefs.edit().putBoolean("guide_read", true).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Thread.sleep(1000);
            androidx.test.espresso.Espresso.onIdle();
            scenario.onActivity(activity -> {
                try {
                    for (String name : new String[]{"leftTarget", "rightTarget", "status", "speechState", "pauseButton"}) {
                        android.widget.TextView target = (android.widget.TextView) field(activity, name);
                        android.text.Layout layout = target.getLayout();
                        assertNotNull(layout);
                        int available = target.getHeight() - target.getCompoundPaddingTop() - target.getCompoundPaddingBottom();
                        assertTrue(name + " text vertically clipped: " + layout.getHeight() + " > " + available,
                                layout.getHeight() <= available);
                    }
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            if (screenshot != null) {
                try (FileOutputStream stream = new FileOutputStream(new File(context().getExternalFilesDir(null), "compatibility-main.png"))) {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream);
                } finally { screenshot.recycle(); }
            }
        } finally {
            android.content.SharedPreferences.Editor editor = prefs.edit();
            if (hadGuide) editor.putBoolean("guide_read", oldGuide); else editor.remove("guide_read");
            editor.commit();
        }
    }
}
