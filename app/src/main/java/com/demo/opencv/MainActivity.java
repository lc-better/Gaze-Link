package com.demo.opencv;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import com.demo.opencv.communication.*;
import com.demo.opencv.textEntry.KeyboardManager;
import com.demo.opencv.textEntry.InitialsInput;
import com.demo.opencv.vision.*;
import com.google.common.util.concurrent.ListenableFuture;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Two-target communication: look left to choose, right to advance, centre to rest. */
public class MainActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(247, 250, 252), SURFACE = Color.WHITE;
    private static final int INK = Color.rgb(22, 50, 79), MUTED = Color.rgb(82, 101, 121);
    private static final int PRIMARY = Color.rgb(11, 114, 133), NEXT = Color.rgb(80, 72, 150);
    private static final int LOCKED = Color.rgb(47, 158, 68);
    private final Model model = new Model();
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean frameInFlight = new AtomicBoolean();
    private final Handler main = new Handler(Looper.getMainLooper());
    private UserDataManager data;
    private SpeechController speech;
    private SuggestionService suggestions;
    private PhraseBook phrases;
    private final Draft draft = new Draft();
    private final KeyboardManager keyboard = new KeyboardManager();
    private final InitialsInput initials = new InitialsInput();
    private LocalLexicon lexicon;
    private String code = "", lastPhrase = "", draftStatus = "选择一种输入方式";
    private String initialsStatus = "选择字母组，再选字母", adoptedInitialsText = "", beforeInitialsAdoption = "";
    private final InitialsSession initialsSession = new InitialsSession();
    private final List<String> spokenHistory = new ArrayList<>();
    private boolean paused, cameraStarted, draftGenerating, blinkControlsEnabled, mouthControlsEnabled;
    private int leftWinks, rightWinks, bothClosures, blinkPracticeMask;
    private TextView blinkSignal, blinkCounts, blinkFeedback;
    private volatile boolean destroyed, foreground = true;
    private int dialogCount;
    private volatile int generation;
    private LinearLayout content, centre, actions;
    private android.widget.FrameLayout shell;
    private View guideOverlay;
    private boolean guideActive;
    private ImageAnalysis cameraAnalysis;
    private int calibrationRotation = -1, calibrationWidth, calibrationHeight;
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) {}
        @Override public void onDisplayRemoved(int displayId) {}
        @Override public void onDisplayChanged(int displayId) {
            if (data == null || destroyed || !foreground) return;
            if (getWindowManager().getDefaultDisplay().getDisplayId() != displayId) return;
            if (updateCalibrationEnvironment()) {
                generation++; model.resetDwell();
                if (cameraAnalysis != null) cameraAnalysis.setTargetRotation(getWindowManager().getDefaultDisplay().getRotation());
                if (calibration != null || evaluation != null) {
                    speech.stop(); showCalibration();
                }
            }
        }
    };
    private int guideStep;
    private GazeFocusOverlay gazeOverlay;
    private ScrollView centreScroll;
    private TextView status, speechState, heading, detail, neutralHint;
    private TextView inputOverview;
    private String[] overviewLabels;
    private Button leftTarget, rightTarget, pauseButton;
    private ProgressBar dwellBar;
    private ImageView preview, leftEye, rightEye;
    private CalibrationSession calibration;
    private GazeEvaluation evaluation;
    private String screen = "";
    private List<Choice> menu = Collections.emptyList();
    private int selected;
    private Runnable parent;
    private Runnable pageRenderer;
    private long firstFrameAt, frames, trackedFrames, commands, processingTotal;
    private String cameraMessage = "正在准备相机；触摸表达随时可用";
    private static final class Choice {
        final String label; final Runnable action;
        Choice(String label, Runnable action) { this.label = label; this.action = action; }
    }
    private Choice choice(String label, Runnable action) { return new Choice(label, action); }
    private List<Choice> choices(Choice... values) { return new ArrayList<>(Arrays.asList(values)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(INK); view.setGravity(Gravity.CENTER); view.setPadding(dp(5), dp(4), dp(5), dp(4));
        return view;
    }
    private Button button(String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        button.setTextSize(15); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(8), dp(4), dp(8), dp(4));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(232, 241, 244)));
        button.setTextColor(INK); button.setOnClickListener(v -> action.run()); return button;
    }
    private void showDialog(AlertDialog dialog) {
        dialogCount++; model.resetDwell();
        dialog.setOnDismissListener(ignored -> { dialogCount--; model.resetDwell(); });
        dialog.show();
    }
    private void rowButton(LinearLayout row, String label, Runnable action) {
        row.addView(button(label, action), new LinearLayout.LayoutParams(0, dp(48), 1));
    }
    private void toolbarButton(LinearLayout row, String label, Runnable action) {
        Button control = button(label, action);
        control.setTextSize(14);
        row.addView(control, new LinearLayout.LayoutParams(-2, dp(48)));
        control.setMinWidth(dp(92));
    }
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        configureWindowForDeviceInsets();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        data = (UserDataManager) getApplication(); data.getSettings();
        updateCalibrationEnvironment();
        blinkControlsEnabled = data.experience().getBoolean("blink_controls", false);
        mouthControlsEnabled = data.experience().getBoolean("mouth_controls", false);
        blinkPracticeMask = data.experience().getInt("blink_practice_mask", 0);
        try { model.initialize(this, data); }
        catch (java.io.IOException error) { throw new IllegalStateException(error); }
        model.setDwellMillis(data.experience().getLong("dwell_millis", 800));
        model.setMouthTracking(mouthControlsEnabled);
        phrases = new PhraseBook(data); buildShell();
        speech = new SpeechController(this, state -> { if (!destroyed) speechState.setText(state); });
        speech.setSpeed(data.experience().getFloat("speech_speed", .9f));
        suggestions = new SuggestionService(); loadLexicon();
        if (saved != null) {
            draft.replace(saved.getString("draft", "")); draft.setContext(saved.getString("context", ""));
            lastPhrase = saved.getString("last_phrase", ""); paused = saved.getBoolean("paused");
        }
        pauseButton.setText(paused ? "继续" : "暂停");
        if (paused) showPauseScreen();
        else if (!data.experience().getBoolean("guide_read", false)) showGuide(); else showQuick(0);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1001);
    }
    private void buildShell() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG); root.setPadding(dp(8), 0, dp(8), dp(4));
        // Keep the navigation controls usable on narrow phones. The old
        // weighted row compressed seven labels into unreadable buttons on
        // 16:9 and compact landscape screens; it now scrolls horizontally
        // while retaining the same eye-control actions.
        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        toolbarScroll.setFillViewport(true);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setMinimumWidth(dp(560));
        toolbarButton(toolbar, "首页", this::showHome); toolbarButton(toolbar, "常用表达", () -> showQuick(0));
        toolbarButton(toolbar, "文字输入", this::showWriting); toolbarButton(toolbar, "眼动设置", this::showCalibration);
        toolbarButton(toolbar, "照护设置", this::showSettings);
        pauseButton = button("暂停", this::togglePause);
        pauseButton.setTextSize(14);
        toolbar.addView(pauseButton, new LinearLayout.LayoutParams(dp(92), dp(48)));
        toolbarButton(toolbar, "帮助", this::showGuide);
        toolbarScroll.addView(toolbar, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(toolbarScroll, new LinearLayout.LayoutParams(-1, dp(52)));
        status = text(cameraMessage, 14); status.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setTextColor(MUTED); root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        dwellBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        dwellBar.setMax(100); root.addView(dwellBar, new LinearLayout.LayoutParams(-1, dp(6)));
        content = new LinearLayout(this); content.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        leftTarget = button("", this::chooseCurrent); leftTarget.setTextSize(24); leftTarget.setTextColor(Color.WHITE);
        leftTarget.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
        content.addView(leftTarget, new LinearLayout.LayoutParams(0, -1, 1));
        ScrollView scroll = new ScrollView(this); centreScroll = scroll; scroll.setFillViewport(true);
        centre = new LinearLayout(this); centre.setOrientation(LinearLayout.VERTICAL); centre.setGravity(Gravity.CENTER);
        centre.setPadding(dp(8), 0, dp(8), 0); scroll.addView(centre);
        content.addView(scroll, new LinearLayout.LayoutParams(0, -1, 2));
        rightTarget = button("下一项 →", this::advance); rightTarget.setTextSize(24); rightTarget.setTextColor(Color.WHITE);
        rightTarget.setBackgroundTintList(android.content.res.ColorStateList.valueOf(NEXT));
        content.addView(rightTarget, new LinearLayout.LayoutParams(0, -1, 1));
        // A fixed-landscape activity can be letterboxed into a very short
        // window by a foldable or the system's forced-orientation policy.
        int minimumTargetSp = Math.max(6, Math.round(12 / Math.max(1f, getResources().getConfiguration().fontScale)));
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(leftTarget, minimumTargetSp, 24, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(rightTarget, minimumTargetSp, 24, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        neutralHint = text("看左侧选择 · 看右侧换下一项 · 每次确认后看回中间", 13); neutralHint.setTextColor(MUTED); root.addView(neutralHint);
        actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER); root.addView(actions);
        speechState = text("正在准备本机语音…", 13); speechState.setTextColor(MUTED);
        speechState.setSingleLine(true); speechState.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(speechState);
        shell = new android.widget.FrameLayout(this);
        shell.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));
        gazeOverlay = new GazeFocusOverlay(this); gazeOverlay.setVisibility(View.VISIBLE);
        gazeOverlay.setTargets(leftTarget, centreScroll, rightTarget);
        shell.addView(gazeOverlay, new android.widget.FrameLayout.LayoutParams(-1, -1)); setContentView(shell);
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            shell.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(shell);
    }

    /** Apply system-bar-safe content bounds on notched, gesture-navigation and
     * edge-to-edge devices without changing the app's landscape workflow. */
    private void configureWindowForDeviceInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private boolean updateCalibrationEnvironment() {
        android.view.Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point(); display.getRealSize(size);
        int rotation = display.getRotation();
        boolean changed = calibrationRotation != rotation || calibrationWidth != size.x || calibrationHeight != size.y;
        calibrationRotation = rotation; calibrationWidth = size.x; calibrationHeight = size.y;
        data.setCalibrationEnvironment(rotation, size.x, size.y);
        return changed;
    }
    private void leaveScreen(String value) {
        if (initialsSession.generating() && !value.equals("InitialsWaiting") && !value.equals("PageControls")) cancelInitialsGeneration();
        if (draftGenerating && !value.equals("WritingGenerating") && !value.equals("PageControls")) {
            draftGenerating = false; invalidateSuggestion();
        }
        if (calibration != null) { calibration.cancel(); calibration = null; }
        evaluation = null;
        if (!value.equals("Paused")) { paused = false; pauseButton.setText("暂停"); }
        inputOverview = null; overviewLabels = null;
        blinkSignal = blinkCounts = blinkFeedback = null;
        model.setBlinkTracking(blinkControlsEnabled || value.equals("BlinkPractice"));
        model.setMouthTracking(mouthControlsEnabled);
        generation++; screen = value; model.previewEnabled = value.equals("Calibration") || value.equals("Diagnostics");
        model.resetDwell(); dwellBar.setProgress(0); centre.removeAllViews(); centreScroll.scrollTo(0, 0); actions.removeAllViews();
        actions.setVisibility(android.view.View.VISIBLE);
        leftTarget.setEnabled(true); rightTarget.setEnabled(true); leftTarget.setAlpha(1); rightTarget.setAlpha(1);
        heading = text("", 22); heading.setTypeface(null, Typeface.BOLD); centre.addView(heading);
        detail = text("", 18); detail.setTextColor(MUTED); centre.addView(detail); preview = leftEye = rightEye = null;
        if (gazeOverlay != null) gazeOverlay.hide();
    }
    private void showMenu(String name, String title, List<Choice> values, String description, Runnable back) {
        leaveScreen(name); heading.setText(title); menu = new ArrayList<>(values); selected = 0; parent = back;
        pageRenderer = () -> showMenu(name, title, values, description, back);
        if (!name.equals("PageControls") && !name.equals("Paused"))
            menu.add(choice("导航 / 朗读控制", this::showPageControls));
        detail.setText(description); refreshTargets();
        if (back != null) rowButton(actions, "返回", back);
        rowButton(actions, "重播", () -> {
            if (screen.equals("Quick")) speech.speak(lastPhrase); else speech.replay();
        });
        rowButton(actions, "停止朗读", () -> speech.stop());
    }
    private void showPageControls() {
        Runnable resume = pageRenderer, goBack = parent; int resumeSelection = selected, resumeScroll = centreScroll.getScrollY();
        Runnable restore = () -> {
            resume.run(); selected = Math.min(resumeSelection, menu.size() - 1); refreshTargets();
            centreScroll.post(() -> centreScroll.scrollTo(0, resumeScroll));
        };
        List<Choice> values = choices(choice("返回刚才页面", restore),
            choice("停止朗读", () -> { speech.stop(); restore.run(); }),
            choice("重播朗读", () -> { speech.replay(); restore.run(); }),
            choice("文字输入", this::showWriting), choice("常用表达", () -> showQuick(0)),
            choice("首页", this::showHome), choice("使用帮助", this::showGuide),
            choice("眼动 / 语音设置", this::showSettings), choice("暂停眼动", this::togglePause));
        values.add(choice("眨眼练习 / 设置", this::showBlinkSettings));
        values.add(choice("向下查看页面", () -> { restore.run(); centreScroll.post(() -> centreScroll.smoothScrollBy(0, dp(140))); }));
        values.add(choice("向上查看页面", () -> { restore.run(); centreScroll.post(() -> centreScroll.smoothScrollBy(0, -dp(140))); }));
        if (goBack != null) values.add(1, choice("返回上一级", goBack));
        showMenu("PageControls", "导航 / 朗读控制", values, "选择要去的页面或朗读操作", restore);
    }
    private void refreshTargets() {
        if (menu.isEmpty()) return;
        boolean largeFont = getResources().getConfiguration().fontScale >= 1.5f;
        leftTarget.setText((largeFont ? "← 选择\n" : "← 选择\n\n") + menu.get(selected).label);
        rightTarget.setText("下一项 →\n" + menu.get((selected + 1) % menu.size()).label
                + (largeFont ? "" : "\n" + (selected + 1) + " / " + menu.size()));
        neutralHint.setText("看左侧选择 · 看右侧下一项 · 每次操作后看回中间");
        if (inputOverview != null) {
            android.text.SpannableStringBuilder overview = new android.text.SpannableStringBuilder();
            int columns = overviewLabels[0].contains(" ") ? 2 : 3;
            for (int i = 0; i < overviewLabels.length; i++) {
                if (i > 0) overview.append(i % columns == 0 ? "\n" : "    ");
                int start = overview.length();
                overview.append(i == selected ? "● " : "   ").append(overviewLabels[i]);
                if (i == selected) {
                    overview.setSpan(new android.text.style.ForegroundColorSpan(PRIMARY), start, overview.length(), 0);
                    overview.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, overview.length(), 0);
                }
            }
            inputOverview.setText(overview);
        }
    }
    private void showFocusedMenu(String name, String title, List<Choice> values, String primary,
            String secondary, String hint, Runnable back) {
        showMenu(name, title, values, "", back); detail.setVisibility(android.view.View.GONE);
        actions.setVisibility(android.view.View.GONE);
        TextView mainText = text(primary, 30); mainText.setTypeface(null, Typeface.BOLD);
        centre.addView(mainText);
        if (!secondary.isEmpty()) centre.addView(text(secondary, 16));
        if (!hint.isEmpty()) centre.addView(text(hint, 14));
        pageRenderer = () -> showFocusedMenu(name, title, values, primary, secondary, hint, back);
    }
    private void showInputOverview(String[] labels) {
        Runnable restore = pageRenderer;
        overviewLabels = labels; inputOverview = text("", 18);
        centre.addView(inputOverview, 3); refreshTargets();
        pageRenderer = () -> { restore.run(); showInputOverview(labels); };
    }
    private void chooseCurrent() {
        if (menu.isEmpty() || (screen.equals("Calibration") || screen.equals("Evaluation"))) return;
        model.resetDwell(); menu.get(selected).action.run();
    }
    private void advance() {
        if (menu.isEmpty() || (screen.equals("Calibration") || screen.equals("Evaluation"))) return;
        selected = (selected + 1) % menu.size(); model.resetDwell(); refreshTargets();
    }
    private void showHome() {
        showMenu("Home", "Gaze Link · 眼动沟通", choices(
            choice("常用表达", () -> showQuick(0)), choice("文字输入", this::showWriting),
            choice("使用帮助", this::showGuide), choice("照护设置", this::showSettings), choice("暂停眼动", this::togglePause)),
            "看左侧选择，看右侧切换。\n\n" + (data.isActiveCalibrationVerified()
                ? "已保存个人校准。先看中间，再看左或右。" : "先完成眼动设置。照护者可以直接触摸表达。"), null);
    }
    private void showQuick(int category) {
        List<Choice> options = new ArrayList<>();
        for (String phrase : phrases.get(category)) options.add(choice(phrase, () -> utterPhrase(phrase)));
        options.add(choice("更多分类", this::showCategories)); options.add(choice("撤销 / 重播 / 停止", this::showSpeechControls));
        options.add(choice("返回首页", this::showHome));
        showMenu("Quick", PhraseBook.CATEGORIES[category] + "表达 · 离线可用", options,
                lastPhrase.isEmpty() ? "先看回中间。然后看左侧选择短语，保持到进度填满，即可朗读。\n\n也可以直接点左侧按钮。"
                        : "刚才表达：\n" + lastPhrase, this::showHome);
    }
    private void showCategories() {
        List<Choice> values = new ArrayList<>();
        for (int i = 0; i < PhraseBook.CATEGORIES.length; i++) {
            final int category = i; values.add(choice(PhraseBook.CATEGORIES[i], () -> showQuick(category)));
        }
        values.add(choice("返回首页", this::showHome));
        showMenu("Quick", "选择表达分类", values, "选择一个分类，再选择要说的话。", () -> showQuick(0));
    }
    private void utterPhrase(String phrase) {
        spokenHistory.add(lastPhrase); lastPhrase = phrase;
        detail.setText("正在表达：\n" + phrase + "\n\n再次选择可重播；误选后到“撤销 / 重播 / 停止”。"); speech.speak(phrase);
    }
    private void showSpeechControls() {
        showMenu("Quick", "朗读与撤销", choices(
            choice("重播刚才内容", () -> speech.speak(lastPhrase)), choice("停止朗读", () -> speech.stop()),
            choice("撤销刚才表达", () -> {
                speech.stop(); if (!spokenHistory.isEmpty()) lastPhrase = spokenHistory.remove(spokenHistory.size() - 1);
                detail.setText(lastPhrase.isEmpty() ? "已撤销。已经播放的声音无法收回。" : "已撤销。上一句：\n" + lastPhrase);
            }), choice("返回常用表达", () -> showQuick(0)), choice("返回首页", this::showHome)),
            "当前表达：\n" + (lastPhrase.isEmpty() ? "尚未选择" : lastPhrase), () -> showQuick(0));
    }
    private void showGuide() {
        guideActive = true;
        guideStep = 0;
        showGuideStep();
    }
    private void showGuideStep() {
        if (guideOverlay != null) shell.removeView(guideOverlay);
        if (guideStep == 0) showHome();
        else if (guideStep == 1) showCalibration();
        else if (guideStep == 2) {
            showSettings();
        }
        else if (guideStep == 3) showQuick(0);
        else if (guideStep == 4) showWriting();

        android.widget.FrameLayout shade = new android.widget.FrameLayout(this);
        shade.setBackgroundColor(Color.argb(178, 10, 22, 36));
        shade.setClickable(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(16));
        card.setBackgroundColor(Color.WHITE);
        card.setElevation(dp(12));
        ScrollView guideScroll = new ScrollView(this);
        guideScroll.addView(card, new ScrollView.LayoutParams(-1, -2));
        android.widget.FrameLayout.LayoutParams cardParams = new android.widget.FrameLayout.LayoutParams(
            -1, -2, Gravity.CENTER);
        cardParams.setMargins(dp(16), dp(12), dp(16), dp(12));
        shade.addView(guideScroll, cardParams);
        shade.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int width = Math.max(1, Math.min(r - l - dp(32), dp(560)));
            if (cardParams.width != width) {
                cardParams.width = width; guideScroll.setLayoutParams(cardParams);
            }
        });

        String[] titles = {"欢迎使用 Gaze Link", "第一步 · 校准采集", "第二步 · 照护设置", "第三步 · 眼动操作", "第四步 · 连续输入", "引导完成"};
        String[] descriptions = {
            "我们会用几步带你熟悉设置和眼动沟通。请把手机固定在舒适位置，脸朝向屏幕，保持光线均匀。建议从约30–50厘米开始。",
            data.isActiveCalibrationVerified()
                ? "已检测到有效校准。需要重新采集时可选择“开始校准”；否则继续体验即可。"
                : "首次使用建议先完成校准：固定手机、头部保持自然，按提示只移动眼睛看中间、左侧和右侧圆点。验证通过后才会保存。",
            "照护者可在此调整停留速度、识别方案、眨眼和嘴型控制。建议先使用默认停留时间；眨眼与嘴型控制都是可选项，熟悉基础眼动后再尝试。",
            "看左侧并保持到顶部进度填满＝选择；看右侧＝切换到下一项；看回中间＝休息并准备下一次。每次操作后先回看中间。眨眼默认不执行操作。",
            "选择首字母组句、拼音选词、常用词句或逐字母输入。首字母可以连续追加，再查看生成候选并确认；原文可继续编辑，最后选择朗读。没有模型配置时仍可用本地输入方式。",
            "可以从常用表达或文字输入开始。任何时候都能点顶部“帮助”重新查看本引导。"
        };
        TextView progress = text("使用引导  ·  " + (guideStep == 5 ? "完成" : (guideStep + 1) + " / 5"), 14);
        progress.setTextColor(PRIMARY); progress.setGravity(Gravity.START); card.addView(progress);
        TextView title = text(titles[guideStep], 22); title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.START);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView body = text(descriptions[guideStep], 17); body.setGravity(Gravity.START); body.setLineSpacing(dp(3), 1f);
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = new LinearLayout(this); buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, -2);
        buttonsParams.topMargin = dp(10);
        HorizontalScrollView buttonScroll = new HorizontalScrollView(this);
        buttonScroll.addView(buttons); card.addView(buttonScroll, buttonsParams);
        if (guideStep > 0 && guideStep < 5) buttons.addView(button("上一步", () -> { guideStep--; showGuideStep(); }));
        if (guideStep == 1 && !data.isActiveCalibrationVerified()) {
            buttons.addView(button("现在开始采集", () -> { guideActive = true; shell.removeView(guideOverlay); guideOverlay = null; beginCalibration(); }));
            buttons.addView(button("稍后体验触摸", () -> { guideActive = false; finishGuide(); showQuick(0); }));
        } else if (guideStep == 1 && data.isActiveCalibrationVerified()) {
            buttons.addView(button("重新校准", () -> { guideActive = true; shell.removeView(guideOverlay); guideOverlay = null; beginCalibration(); }));
            buttons.addView(button("继续", () -> { guideStep++; showGuideStep(); }));
        } else if (guideStep == 2) {
            buttons.addView(button("进入照护设置", () -> { shell.removeView(guideOverlay); guideOverlay = null; }));
            buttons.addView(button("暂不调整", () -> { guideStep++; showGuideStep(); }));
        } else if (guideStep == 5) {
            buttons.addView(button("开始使用", () -> { guideActive = false; finishGuide(); showQuick(0); }));
        } else {
            buttons.addView(button(guideStep == 4 ? "下一步" : "继续",
                () -> { if (guideStep < 5) { guideStep++; showGuideStep(); } }));
        }
        if (guideStep < 5) buttons.addView(button("跳过", () -> { guideActive = false; finishGuide(); showQuick(0); }));
        guideOverlay = shade;
        shell.addView(shade, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }
    private void finishGuide() {
        if (guideOverlay != null) { shell.removeView(guideOverlay); guideOverlay = null; }
        data.experience().edit().putBoolean("guide_read", true).apply();
    }
    private void togglePause() {
        paused = !paused;
        speech.stop();
        if (paused) showPauseScreen(); else showQuick(0);
    }
    private void showPauseScreen() {
        showMenu("Paused", "眼动表达已暂停", choices(choice("继续眼动表达", this::togglePause),
            choice("停止朗读", () -> speech.stop())),
            "可以放松休息。\n恢复时先看回中间，再看左侧并保持到进度填满。\n也可点顶部“继续”或选择其他页面恢复使用。", this::showHome);
        pauseButton.setText("继续");
        neutralHint.setText("暂停期间只接受本页的继续 / 停止操作，不输入表达内容");
    }
    private void showCalibration() {
        showFocusedMenu("CalibrationReady", "准备眼动校准", choices(
            choice("开始采集", this::beginCalibration),
            choice("试听步骤", () -> speech.speak("固定手机，头保持不动，按提示看中间、左侧、右侧圆点。")),
            choice("返回设置", this::showSettings)),
            "固定手机，头保持不动", recognitionName(),
            data.isActiveCalibrationVerified() ? "选择开始后自动采集；通过验证才保存" : "首次校准请照护者触摸“开始采集”", this::showSettings);
    }
    private void beginCalibration() {
        leaveScreen("Calibration"); menu = Collections.emptyList(); parent = this::showHome;
        calibration = new CalibrationSession(model, data);
        heading.setText("眼动设置 · " + recognitionName());
        calibration.message = "当前：" + recognitionName() + "。固定手机，脸朝向屏幕，头保持不动，只移动眼睛看圆点。\n"
                + "点“开始 / 重新采集”后自动采集多个画面，再用新画面验证。全部通过后才保存。";
        detail.setText(calibration.message);
        leftTarget.setText("左侧\n●"); rightTarget.setText("右侧\n●");
        leftTarget.setEnabled(false); rightTarget.setEnabled(false);
        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setScaleX(-1); centre.addView(preview, new LinearLayout.LayoutParams(-1, dp(64)));
        LinearLayout eyes = new LinearLayout(this); eyes.setGravity(Gravity.CENTER);
        leftEye = new ImageView(this); rightEye = new ImageView(this);
        eyes.addView(leftEye, new LinearLayout.LayoutParams(dp(88), dp(24)));
        eyes.addView(rightEye, new LinearLayout.LayoutParams(dp(88), dp(24))); centre.addView(eyes);
        rowButton(actions, "试听", () -> speech.speak("看中间，再看左侧，再看右侧。保持头部不动。"));
        rowButton(actions, "开始 / 重新采集", () -> {
            generation++;
            if (calibration.phase == CalibrationSession.Phase.FAILED) calibration.retry(SystemClock.elapsedRealtime());
            else calibration.start(SystemClock.elapsedRealtime());
            speech.speak("看" + calibration.targetName()); model.resetDwell();
        });
        rowButton(actions, "退出，保留原校准", () -> showQuick(0));
        neutralHint.setText(data.usesIris() ? "绿色圆圈标记虹膜；按提示看圆点。单眼模式仅要求所选眼睛可见。" : "预览镜像便于摆位；下方显示实际裁剪的双眼。按提示看圆点，只移动眼睛。");
        if (!cameraStarted && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        calibration.start(SystemClock.elapsedRealtime()); speech.speak("看中间"); model.resetDwell();
    }
    private void updateCalibration(DetectionOutput frame) {
        CalibrationSession.Phase before = calibration.phase; int oldTarget = calibration.target;
        calibration.tick(frame, SystemClock.elapsedRealtime());
        if ((before != calibration.phase || oldTarget != calibration.target)
                && (calibration.phase == CalibrationSession.Phase.CAPTURE || calibration.phase == CalibrationSession.Phase.VERIFY))
            speech.speak("看" + calibration.targetName());
        long countdown = calibration.countdown(SystemClock.elapsedRealtime());
        String marker = calibration.target == 0 ? "● 中间" : "中间（休息区）";
        heading.setText(calibration.phase == CalibrationSession.Phase.READY ? "● 中间 · 准备摆位"
                : calibration.phase == CalibrationSession.Phase.DONE ? "验证通过 ✓" : marker + (countdown > 0 ? " · " + countdown : ""));
        detail.setText(calibration.message);
        leftTarget.setAlpha(calibration.target == 1 ? 1 : .35f); rightTarget.setAlpha(calibration.target == 2 ? 1 : .35f);
        dwellBar.setProgress((int) (calibration.progress * 100));
        if (frame.preview != null) preview.setImageBitmap(frame.preview);
        leftEye.setImageBitmap(frame.leftEye); rightEye.setImageBitmap(frame.rightEye);
        if (calibration.phase == CalibrationSession.Phase.DONE && before != CalibrationSession.Phase.DONE) {
            speech.speak("校准验证通过，可以开始表达。");
            if (guideActive) { guideStep = 2; showGuideStep(); }
            else { showQuick(0); detail.setText("校准验证通过，已保存。\n先看回中间，再看左侧选择短语，看右侧换下一项。"); }
        } else if (calibration.phase == CalibrationSession.Phase.FAILED && before != CalibrationSession.Phase.FAILED) {
            String reason = calibration.message;
            showFocusedMenu("CalibrationResult", "本次校准未通过", choices(
                choice("重新采集", this::beginCalibration), choice("返回设置", this::showSettings),
                choice("使用原有校准", () -> showQuick(0))),
                "原校准保留", reason, "调整位置后可重试", this::showSettings);
        }
    }
    private void showWriting() {
        List<Choice> values = choices(choice("首字母组句", this::openInitials),
            choice("拼音选词", this::showPinyin), choice("常用词句", this::showWordCategories),
            choice("逐字母输入", this::showLetters));
        if (!draft.text().isEmpty()) values.add(choice("朗读原文", () -> speech.speak(draft.text())));
        if (!draft.suggestion().isEmpty()) values.add(choice("查看待确认建议", this::showDraftSuggestion));
        values.add(choice("编辑 / 朗读操作", this::showWritingActions));
        values.add(choice("返回首页", this::showHome));
        showFocusedMenu("Writing", "文字输入", values,
            draft.text().isEmpty() ? "请选择输入方式" : draft.text(),
            draft.text().isEmpty() ? "首字母组句 · 拼音选词 · 常用词句 · 字母" : "当前原文",
            "", this::showHome);
        rowButton(actions, "照护者编辑", this::editDraft); rowButton(actions, "编辑对话背景", this::editContext);
        actions.setVisibility(android.view.View.VISIBLE);
    }
    private void showWritingActions() {
        List<Choice> values = choices(choice("返回输入", this::showWriting),
            choice("撤销上次编辑", () -> { draft.undo(); invalidateSuggestion(); showWriting(); }),
            choice("清空原文，开始新一句", () -> { draft.replace(""); invalidateSuggestion(); showWriting(); }),
            choice("请求表达建议", this::requestSuggestion),
            choice("重播朗读", () -> speech.replay()), choice("停止朗读", () -> speech.stop()),
            choice("返回首页", this::showHome));
        showFocusedMenu("WritingActions", "编辑 / 朗读操作", values,
            draft.text().isEmpty() ? "（尚未输入）" : draft.text(), "当前原文", draftStatus, this::showWriting);
    }
    private void showDraftSuggestion() {
        showFocusedMenu("DraftSuggestion", "确认表达建议", choices(
            choice("采用并朗读", () -> { draft.adopt(); speech.speak(draft.text()); showWriting(); }),
            choice("仅采用", () -> { draft.adopt(); showWriting(); }),
            choice("放弃建议 / 返回", () -> { invalidateSuggestion(); showWriting(); }),
            choice("重新生成", this::requestSuggestion)), draft.suggestion(),
            "原文：" + draft.text(), "确认意思后再采用", this::showWriting);
    }
    private void invalidateSuggestion() {
        suggestions.cancel(); draft.invalidate(); draftStatus = "输入已更新，可按需重新请求建议。";
    }
    private void addWord(String value) { draft.add(value); invalidateSuggestion(); draftStatus = "已添加，可继续输入或朗读原文。"; showWriting(); }
    private void showWordCategories() {
        List<Choice> values = new ArrayList<>();
        for (int i = 0; i < PhraseBook.CATEGORIES.length; i++) {
            final int index = i; values.add(choice(PhraseBook.CATEGORIES[i], () -> showWords(index)));
        }
        values.add(choice("返回原文", this::showWriting));
        showMenu("Writing", "添加本地词句", values, "不需要网络。选择后会加入原文，不会自动朗读。", this::showWriting);
    }
    private void showWords(int index) {
        List<Choice> values = new ArrayList<>();
        for (String phrase : phrases.get(index)) values.add(choice(phrase, () -> addWord(phrase)));
        values.add(choice("返回分类", this::showWordCategories));
        showMenu("Writing", "添加" + PhraseBook.CATEGORIES[index] + "词句", values, "当前原文：\n" + draft.text(), this::showWordCategories);
    }
    private void showPinyin() {
        List<Choice> values = new ArrayList<>(); String[] groups = {"ABCDEF", "GHIJKLM", "NOPQRST", "UVWXYZ"};
        for (int i = 0; i < groups.length; i++) {
            final int group = i; values.add(choice(groups[i], () -> { code += group; showPinyin(); }));
        }
        values.add(choice("完成选择 · 生成汉字候选", this::showCandidates));
        values.add(choice("删除一组", () -> { if (!code.isEmpty()) code = code.substring(0, code.length() - 1); showPinyin(); }));
        values.add(choice("清空编码", () -> { code = ""; showPinyin(); })); values.add(choice("返回原文", this::showWriting));
        showFocusedMenu("Pinyin", "拼音选词", values,
            code.isEmpty() ? "请选择拼音字母所属组" : "已选 " + code.length() + " 个拼音字母",
            code.isEmpty() ? "" : "分组编码：" + code,
            "例：shui → NOPQRST / GHIJKLM / UVWXYZ / GHIJKLM", this::showWriting);
        showInputOverview(groups);
    }
    private void showCandidates() {
        List<LocalLexicon.Entry> matches = lexicon.search(code); List<Choice> values = new ArrayList<>();
        for (LocalLexicon.Entry entry : matches) values.add(choice(entry.text + " · " + entry.pinyin, () -> { code = ""; addWord(entry.text); }));
        values.add(choice("继续输入拼音", this::showPinyin));
        values.add(choice("清空并重新选择", () -> { code = ""; showPinyin(); }));
        showFocusedMenu("PinyinCandidates", "本地候选 · " + matches.size() + " 项", values,
            matches.isEmpty() ? "暂无匹配" : "看左侧选择汉字词句",
            matches.isEmpty() ? "可返回修改拼音分组" : "看右侧浏览候选",
            matches.isEmpty() ? "本地词库有限，照护者可添加常用词" : "选中后加入原文", this::showPinyin);
    }
    private void showLetters() {
        List<Choice> values = new ArrayList<>(); String[] displays = keyboard.getDisplays().Options;
        List<String> visible = new ArrayList<>();
        for (int i = 0; i < displays.length; i++) {
            if (displays[i] == null) continue;
            final int slot = i; visible.add(displays[i].toUpperCase(Locale.ROOT));
            values.add(choice(displays[i].toUpperCase(Locale.ROOT), () -> { keyboard.selectSlot(slot); showLetters(); }));
        }
        if (!keyboard.choosingGroup()) values.add(choice("返回字母组", () -> { keyboard.backToGroups(); showLetters(); }));
        values.add(choice("加入原文", () -> { addWord(keyboard.getDisplays().TextInput); keyboard.clear(); }));
        values.add(choice("返回文字输入", this::showWriting));
        showFocusedMenu("Letters", "逐字母输入", values,
            keyboard.getDisplays().TextInput.isEmpty() ? "（尚未输入）" : keyboard.getDisplays().TextInput,
            "", "选择字母；适合英文或缩写", this::showWriting);
        showInputOverview(visible.toArray(new String[0]));
    }

    private void openInitials() {
        if (!adoptedInitialsText.isEmpty() && adoptedInitialsText.equals(draft.text()) && initialsSession.hasCandidates()) showInitialsAdopted();
        else showInitials();
    }
    private void showInitials() {
        List<Choice> values = new ArrayList<>();
        String[] labels = initials.choosingGroup() ? initials.groupOptions() : initials.letterOptions();
        for (int i = 0; i < labels.length; i++) {
            final int slot = i;
            values.add(choice(labels[i].toUpperCase(Locale.ROOT), () -> {
                if (initials.choosingGroup()) initials.chooseGroup(slot);
                else { initials.chooseLetter(slot); resetInitialsCandidates(); initialsStatus = "继续输入，完成后选择生成候选"; }
                showInitials();
            }));
        }
        if (!initials.choosingGroup()) values.add(choice("返回字母组", () -> { initials.chooseGroup(-1); showInitials(); }));
        if (!initials.text().isEmpty()) values.add(choice("完成输入 / 生成候选", this::confirmInitialsGeneration));
        values.add(choice("删除 / 清空 / 返回", this::showInitialsActions));
        if (initialsSession.hasCandidates()) values.add(choice("查看候选", this::showInitialsCandidates));
        showFocusedMenu("Initials", "首字母组句 · 输入", values,
            initials.text().isEmpty() ? "请输入首字母" : initials.text().toUpperCase(Locale.ROOT),
            "", initials.text().isEmpty() ? "例：我爱你 → W A N" : initialsStatus, this::showWriting);
        showInputOverview(labels);
    }
    private void showInitialsActions() {
        List<Choice> values = choices(
            choice("删除末个首字母", () -> { initials.deleteLast(); resetInitialsCandidates(); initialsStatus = "已删除末个字母"; showInitials(); }),
            choice("清空并重新输入", this::startNewInitialSentence),
            choice("返回字母组", () -> { initials.chooseGroup(-1); showInitials(); }),
            choice("返回文字输入", this::showWriting),
            choice("停止朗读", () -> speech.stop()));
        showFocusedMenu("InitialsActions", "首字母 · 编辑", values,
            initials.text().isEmpty() ? "（尚未输入）" : initials.text().toUpperCase(Locale.ROOT),
            "", "删除一个字母，或清空开始新一句", this::showInitials);
    }
    private void confirmInitialsGeneration() {
        showFocusedMenu("InitialsConfirm", "确认生成", choices(
            choice("确认生成候选", this::generateInitialSentence),
            choice("返回修改", this::showInitials),
            choice("清空并重新输入", this::startNewInitialSentence)),
            initials.text().toUpperCase(Locale.ROOT), initials.text().length() + " 个首字母 → " + initials.text().length() + " 个汉字",
            "生成后先选句子，再确认朗读", this::showInitials);
    }
    private void generateInitialSentence() {
        generateInitialSentence(false);
    }
    private List<InitialsCandidate> localInitialCandidates(String request) {
        List<InitialsCandidate> result = lexicon.searchInitials(request);
        for (int category = 0; category < PhraseBook.CATEGORIES.length; category++) {
            for (String phrase : phrases.get(category)) {
                InitialsCandidate candidate = new InitialsCandidate(phrase);
                if (!candidate.matches(request)) continue;
                boolean duplicate = false;
                for (InitialsCandidate existing : result) if (existing.text.equals(candidate.text)) duplicate = true;
                if (!duplicate && result.size() < 3) result.add(candidate);
            }
        }
        return result;
    }
    private void generateInitialSentence(boolean online) {
        String request = initials.text();
        if (request.isEmpty()) { showInitials(); return; }
        List<InitialsCandidate> local = localInitialCandidates(request);
        if (!online && !local.isEmpty()) {
            initialsSession.receive(initialsSession.begin(request), request, local);
            initialsStatus = suggestions.configured() ? "本地词库匹配 · 可选择联网补充" : "本地词库匹配 · 离线可用";
            showInitialsCandidates(); return;
        }
        if (!suggestions.configured()) {
            showFocusedMenu("InitialsUnavailable", "尚未配置模型", choices(
                choice("返回修改", this::showInitials), choice("改用本地拼音", this::showPinyin)),
                "暂时无法生成", "请照护者配置模型服务", "本地拼音和常用词句仍可使用", this::showInitials);
            return;
        }
        long version = initialsSession.begin(request);
        showFocusedMenu("InitialsWaiting", "正在生成候选", choices(
            choice("取消生成 / 返回修改", () -> { cancelInitialsGeneration(); showInitials(); }),
            choice("清空并重新输入", this::startNewInitialSentence)),
            request.toUpperCase(Locale.ROOT), "生成完成后需要确认", "", () -> { cancelInitialsGeneration(); showInitials(); });
        pageRenderer = this::showInitialsWaitingOrResult;
        suggestions.generateInitials(request, draft.context(), (callbackVersion, candidates, message, success) -> {
            List<InitialsCandidate> combined = new ArrayList<>(candidates);
            for (InitialsCandidate item : local) {
                boolean duplicate = false;
                for (InitialsCandidate existing : combined) if (existing.text.equals(item.text)) duplicate = true;
                if (!duplicate && combined.size() < 3) combined.add(item);
            }
            if (destroyed || !initialsSession.receive(version, request, combined)) return;
            initialsStatus = success ? message : message + (combined.isEmpty() ? "" : " · 可选择本地候选");
            if (screen.equals("InitialsWaiting")) showInitialsCandidates();
        });
    }
    private void showInitialsWaitingOrResult() {
        if (initialsSession.generating()) {
            showFocusedMenu("InitialsWaiting", "正在生成候选", choices(
                choice("取消生成 / 返回修改", () -> { cancelInitialsGeneration(); showInitials(); }),
                choice("清空并重新输入", this::startNewInitialSentence)),
                initials.text().toUpperCase(Locale.ROOT), "生成完成后需要确认", "", () -> { cancelInitialsGeneration(); showInitials(); });
            pageRenderer = this::showInitialsWaitingOrResult;
        } else showInitialsCandidates();
    }
    private void showInitialsCandidates() {
        List<Choice> values = new ArrayList<>();
        if (initialsSession.hasCandidates()) {
            values.add(choice("采用并朗读", () -> adoptInitialCandidate(true)));
            if (initialsSession.count() > 1) {
                values.add(choice("下一候选", () -> { initialsSession.next(); showInitialsCandidates(); }));
                values.add(choice("上一候选", () -> { initialsSession.previous(); showInitialsCandidates(); }));
            }
            values.add(choice("仅采用", () -> adoptInitialCandidate(false)));
        }
        if (suggestions.configured()) values.add(choice("联网补充 / 重新生成", () -> generateInitialSentence(true)));
        if (initialsSession.canRestore()) values.add(choice("回退上次生成结果", () -> { initialsSession.restore(); initialsStatus = "已恢复上次结果"; showInitialsCandidates(); }));
        values.add(choice("返回修改首字母", this::showInitials));
        values.add(choice("放弃候选", () -> { resetInitialsCandidates(); showInitials(); }));
        values.add(choice("清空并重新输入", this::startNewInitialSentence));
        String secondary = initials.text().toUpperCase(Locale.ROOT);
        if (initialsSession.hasCandidates()) secondary += " · 候选 " + (initialsSession.index() + 1) + " / " + initialsSession.count() + " · 首字母已核对";
        showFocusedMenu("InitialsCandidates", "选择要表达的句子", values,
            initialsSession.hasCandidates() ? initialsSession.text() : "暂无匹配候选",
            secondary, initialsStatus, this::showInitials);
    }
    private void adoptInitialCandidate(boolean speak) {
        beforeInitialsAdoption = draft.text();
        adoptedInitialsText = initialsSession.text();
        draft.replace(adoptedInitialsText); invalidateSuggestion();
        if (speak) speech.speak(adoptedInitialsText);
        showInitialsAdopted();
    }
    private void showInitialsAdopted() {
        showFocusedMenu("InitialsAdopted", "已采用", choices(
            choice("新一句 · 清空重新输入", this::startNewInitialSentence),
            choice("再次朗读", () -> speech.speak(draft.text())),
            choice("撤销采用 / 返回候选", () -> { draft.replace(beforeInitialsAdoption); invalidateSuggestion(); adoptedInitialsText = ""; speech.stop(); showInitialsCandidates(); }),
            choice("返回文字输入", this::showWriting), choice("停止朗读", () -> speech.stop())),
            draft.text(), "当前原文", "下一句可完全通过眼动开始", this::showWriting);
    }
    private void cancelInitialsGeneration() { initialsSession.cancel(); suggestions.cancel(); initialsStatus = "已取消生成"; }
    private void resetInitialsCandidates() { initialsSession.reset(); suggestions.cancel(); adoptedInitialsText = ""; }
    private void startNewInitialSentence() {
        initials.resetSentence(); resetInitialsCandidates(); draft.replace(""); invalidateSuggestion();
        initialsStatus = "选择字母组，开始新一句"; speech.stop(); showInitials();
    }
    private void requestSuggestion() {
        if (draft.text().trim().isEmpty()) { draftStatus = "先输入原文，再请求建议"; showWritingActions(); return; }
        if (!suggestions.configured()) { draftStatus = "请照护者先配置模型服务"; showWritingActions(); return; }
        draftGenerating = true;
        showFocusedMenu("WritingGenerating", "正在生成建议", choices(
            choice("取消 / 返回输入", () -> { invalidateSuggestion(); showWriting(); })),
            draft.text(), "当前原文", "生成后需确认采用", () -> { invalidateSuggestion(); showWriting(); });
        suggestions.generate(draft, (version, value, success) -> {
            if (destroyed || draft.version() != version) return;
            draftGenerating = false;
            if (success && !value.isEmpty()) { draft.receive(version, value); draftStatus = "建议已生成"; }
            else draftStatus = value.isEmpty() ? "模型没有返回建议，可重试" : value;
            if (screen.equals("WritingGenerating")) {
                if (success && !value.isEmpty()) showDraftSuggestion(); else showWritingActions();
            }
        });
        pageRenderer = () -> {
            if (!draft.suggestion().isEmpty()) showDraftSuggestion();
            else if (!draftGenerating) showWritingActions();
            else showWritingWaiting();
        };
    }
    private void showWritingWaiting() {
        showFocusedMenu("WritingGenerating", "正在生成建议", choices(
            choice("取消 / 返回输入", () -> { draftGenerating = false; invalidateSuggestion(); showWriting(); })),
            draft.text(), "当前原文", "生成后需确认采用", () -> { draftGenerating = false; invalidateSuggestion(); showWriting(); });
        pageRenderer = () -> {
            if (draftGenerating) showWritingWaiting();
            else if (!draft.suggestion().isEmpty()) showDraftSuggestion();
            else showWritingActions();
        };
    }
    private void editDraft() {
        EditText editor = new EditText(this); editor.setText(draft.text()); editor.setMinLines(2);
        showDialog(new AlertDialog.Builder(this).setTitle("编辑原文").setView(editor).setNegativeButton("取消", null)
            .setPositiveButton("保存", (dialog, which) -> { draft.replace(editor.getText().toString()); invalidateSuggestion(); showWriting(); }).create());
    }
    private void editContext() {
        EditText editor = new EditText(this); editor.setText(draft.context()); editor.setHint("例如：你想吃什么？");
        showDialog(new AlertDialog.Builder(this).setTitle("对话背景（可留空）").setView(editor).setNegativeButton("取消", null)
            .setPositiveButton("保存", (dialog, which) -> { draft.setContext(editor.getText().toString()); invalidateSuggestion(); showWriting(); }).create());
    }
    private void loadLexicon() {
        lexicon = new LocalLexicon();
        for (String line : data.experience().getString("personal_lexicon", "").split("\n")) addLexiconLine(line);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("ChinesePhrases.tsv"), java.nio.charset.StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) addLexiconLine(line.replace("\uFEFF", ""));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private void addLexiconLine(String line) {
        String[] pair = line.split("\t", 2); if (pair.length == 2) lexicon.add(pair[0], pair[1]);
    }
    private void showSettings() {
        showMenu("Settings", "照护者设置", choices(
            choice("识别方案 / 有效眼睛", this::showRecognition),
            choice("眼动设置", this::showCalibration), choice("停留速度", this::chooseDwell),
            choice("眨眼练习 / 设置", this::showBlinkSettings),
            choice("嘴型确认 / 设置", this::showMouthSettings),
            choice("中文语音试听", () -> speech.speak("你好，我想喝水，请帮我拿水。")),
            choice("朗读语速", this::chooseSpeechSpeed), choice("手机语音设置", this::openSpeechSettings),
            choice("编辑 / 排序常用短语", this::choosePhraseEditor), choice("个人拼音词库", this::editPersonalLexicon),
            choice("查看使用质量", this::showDiagnostics), choice("返回常用表达", () -> showQuick(0))),
            "当前识别：" + recognitionName() + "\n先固定手机与坐姿，再校准。推荐先用标准0.8秒停留；如果容易误选，可调慢。\n"
            + "常用短语与中文词库保存在本机。中文语音需手机已安装相应语音包。\n"
            + "联网建议服务：" + BuildConfig.LLM_PROVIDER + (suggestions.configured() ? "（已配置）" : "（未配置）"), this::showHome);
        if (guideActive && guideStep == 2)
            rowButton(actions, "设置完成，继续引导", () -> { guideStep = 3; showGuideStep(); });
    }
    private void openSpeechSettings() {
        try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
        catch (android.content.ActivityNotFoundException error) {
            detail.setText("请打开手机设置，搜索“文字转语音”，启用中文语音引擎并安装语音包。");
        }
    }
    private void chooseDwell() {
        List<Choice> values = new ArrayList<>();
        long[] times = {600, 800, 1200}; String[] labels = {"较快 · 0.6秒", "标准 · 0.8秒", "较慢 · 1.2秒"};
        for (int i = 0; i < times.length; i++) {
            final long value = times[i];
            values.add(choice(labels[i], () -> {
                model.setDwellMillis(value); data.experience().edit().putLong("dwell_millis", value).apply(); chooseDwell();
            }));
        }
        values.add(choice("返回设置", this::showSettings));
        showFocusedMenu("Settings", "停留确认时间", values,
            data.experience().getLong("dwell_millis", 800) / 1000f + " 秒", "当前确认时间", "容易误选时可调慢", this::showSettings);
    }
    private void showBlinkSettings() {
        showFocusedMenu("BlinkSettings", "眨眼控制 · 可选", choices(
            choice("先做眨眼练习", this::showBlinkPractice),
            choice("启用眨眼控制", () -> setBlinkControls(true)),
            choice("关闭眨眼控制", () -> setBlinkControls(false)),
            choice("返回设置", this::showSettings)),
            blinkControlStatus(),
            "左眼闭合确认 · 右眼闭合返回 · 双眼长闭取消",
            "练习记录：" + blinkPracticeStatus() + "\n左右指自己的眼睛；先回中间，再闭合并睁开。\n"
                + (data.usesIris() ? "启动后先自然睁眼约1秒，学习左右眼基准。\n" : "")
                + (data.isActiveCalibrationVerified() ? "自然短眨不作为命令；先在练习页观察是否误记。"
                    : "练习无需校准；正式控制前请完成眼动校准。"), this::showSettings);
    }
    private String blinkControlStatus() {
        if (!blinkControlsEnabled) return "眨眼控制未启用";
        if (!data.isActiveCalibrationVerified()) return "已启用 · 等待眼动校准";
        return blinkPracticeMask == 7 ? "已启用 · 三种手势已有记录" : "已启用 · 建议先完成练习";
    }
    private void showMouthSettings() {
        showFocusedMenu("MouthSettings", "嘴型确认 · 可选", choices(
            choice("启用张嘴确认", () -> setMouthControls(true)),
            choice("关闭张嘴确认", () -> setMouthControls(false)),
            choice("返回设置", this::showSettings)),
            mouthControlsEnabled ? "嘴型确认已启用" : "嘴型确认未启用",
            "动作：闭嘴准备 → 张嘴保持约0.3秒 → 闭嘴约0.12秒。只在视线稳定看向左右操作区时确认；说话、哈欠或短暂张嘴不会直接触发。",
            "建议先保持默认关闭，确认摄像头能稳定看到嘴部后再启用。",
            this::showSettings);
    }
    private void setMouthControls(boolean enabled) {
        mouthControlsEnabled = enabled;
        data.experience().edit().putBoolean("mouth_controls", enabled).apply();
        model.setMouthTracking(enabled); showMouthSettings();
    }
    private String blinkPracticeStatus() {
        if (blinkPracticeMask == 0) return "尚未记录手势";
        List<String> recorded = new ArrayList<>();
        if ((blinkPracticeMask & 1) != 0) recorded.add("左眼");
        if ((blinkPracticeMask & 2) != 0) recorded.add("右眼");
        if ((blinkPracticeMask & 4) != 0) recorded.add("双眼");
        return android.text.TextUtils.join("、", recorded);
    }
    private void setBlinkControls(boolean enabled) {
        blinkControlsEnabled = enabled;
        data.experience().edit().putBoolean("blink_controls", enabled).apply();
        showBlinkSettings();
    }
    private void showBlinkPractice() {
        showMenu("BlinkPractice", "眨眼练习 · 不执行命令", choices(
            choice("返回眨眼设置", this::showBlinkSettings),
            choice("重新学习睁眼基准", () -> { model.relearnBlinkBaseline(); showBlinkPractice(); }),
            choice("清零并重新练习", () -> {
                leftWinks = rightWinks = bothClosures = blinkPracticeMask = 0;
                data.experience().edit().putInt("blink_practice_mask", 0).apply(); showBlinkPractice();
            }), choice("返回常用表达", () -> showQuick(0))), "", this::showBlinkSettings);
        detail.setVisibility(android.view.View.GONE); actions.setVisibility(android.view.View.GONE);
        blinkFeedback = text("先自然睁开双眼", 26); centre.addView(blinkFeedback);
        blinkSignal = text("等待眼睛画面", 15); centre.addView(blinkSignal);
        blinkCounts = text("左眼 " + leftWinks + " · 右眼 " + rightWinks + " · 双眼 " + bothClosures, 17); centre.addView(blinkCounts);
        centre.addView(text((data.usesIris() ? "先自然睁开双眼约1秒，完成基准学习。\n" : "")
                + "单眼动作时让另一只眼自然睁开；闭合约0.4–1.5秒。\n双眼闭合约0.7–2秒后睁开；先观察自然眨眼是否误记。", 14));
        if (!data.isActiveCalibrationVerified())
            centre.addView(text("尚未校准也可练习；退出请触摸左侧“返回眨眼设置”。", 14));
        pageRenderer = this::showBlinkPractice;
    }
    private void updateBlinkPractice(DetectionOutput frame) {
        if (data.usesIris() && !frame.blinkBaselineReady) {
            blinkSignal.setText("请自然睁开双眼约1秒 · 学习睁眼基准 " + (int) (frame.blinkBaselineProgress * 100) + "%");
            blinkFeedback.setText("先自然睁眼，暂不做手势");
        } else {
            blinkSignal.setText(frame.blinkSignalsValid
                    ? String.format(Locale.CHINA, "左眼开合 %.2f · 右眼开合 %.2f", frame.leftEyeOpenness, frame.rightEyeOpenness)
                        + (data.usesIris() ? String.format(Locale.CHINA, "\n眼睑原始比例 %.3f / %.3f", frame.leftEyeAspectRatio, frame.rightEyeAspectRatio) : "")
                    : "双眼信号不完整 · 请正对相机，保持头部不动");
            if (data.usesIris() && blinkFeedback.getText().toString().equals("先自然睁眼，暂不做手势"))
                blinkFeedback.setText("基准已就绪，可以开始练习");
        }
        switch (frame.blinkEvent) {
            case LEFT_WINK: leftWinks++; blinkPracticeMask |= 1; blinkFeedback.setText("识别到：自己的左眼闭合"); break;
            case RIGHT_WINK: rightWinks++; blinkPracticeMask |= 2; blinkFeedback.setText("识别到：自己的右眼闭合"); break;
            case BOTH_CLOSED: bothClosures++; blinkPracticeMask |= 4; blinkFeedback.setText("识别到：双眼长闭"); break;
            default: break;
        }
        if (frame.blinkEvent != BlinkDetector.Event.NONE)
            data.experience().edit().putInt("blink_practice_mask", blinkPracticeMask).apply();
        blinkCounts.setText("左眼 " + leftWinks + " · 右眼 " + rightWinks + " · 双眼 " + bothClosures);
        dwellBar.setProgress((int) (Math.max(frame.dwellProgress, frame.blinkProgress) * 100));
    }
    private void handleBlinkCommand(BlinkDetector.Event event) {
        model.resetDwell();
        if (event == BlinkDetector.Event.LEFT_WINK) chooseCurrent();
        else if (event == BlinkDetector.Event.RIGHT_WINK) { speech.stop(); if (parent != null) parent.run(); }
        else if (event == BlinkDetector.Event.BOTH_CLOSED) {
            speech.stop();
            if (screen.equals("InitialsWaiting")) { cancelInitialsGeneration(); showInitials(); }
            else if (screen.equals("WritingGenerating") || screen.equals("DraftSuggestion")) { draftGenerating = false; invalidateSuggestion(); showWriting(); }
            else if (screen.equals("InitialsCandidates")) { resetInitialsCandidates(); showInitials(); }
            else if (parent != null) parent.run();
        }
    }
    private void chooseSpeechSpeed() {
        List<Choice> values = new ArrayList<>(); float[] speeds = {.75f, .9f, 1.1f}; String[] labels = {"慢", "标准", "快"};
        for (int i = 0; i < speeds.length; i++) {
            final float value = speeds[i];
            values.add(choice(labels[i], () -> {
                speech.setSpeed(value); data.experience().edit().putFloat("speech_speed", value).apply();
                speech.speak("这是当前朗读语速。"); chooseSpeechSpeed();
            }));
        }
        values.add(choice("返回设置", this::showSettings));
        showFocusedMenu("Settings", "朗读语速", values,
            "当前 " + data.experience().getFloat("speech_speed", .9f) + " 倍", "选择后会试听", "", this::showSettings);
    }
    private void choosePhraseEditor() {
        showDialog(new AlertDialog.Builder(this).setTitle("选择要编辑的分类").setItems(PhraseBook.CATEGORIES, (dialog, category) -> {
            EditText editor = new EditText(this); editor.setText(android.text.TextUtils.join("\n", phrases.get(category))); editor.setMinLines(5);
            showDialog(new AlertDialog.Builder(this).setTitle("每行一句，顺序即展示顺序").setView(editor).setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, which) -> {
                    List<String> values = new ArrayList<>();
                    for (String line : editor.getText().toString().split("\n")) if (!line.trim().isEmpty()) values.add(line.trim());
                    phrases.save(category, values); detail.setText("已保存“" + PhraseBook.CATEGORIES[category] + "”短语与顺序。");
                }).create());
        }).create());
    }
    private void editPersonalLexicon() {
        EditText editor = new EditText(this); editor.setMinLines(4); editor.setHint("每行：拼音 空格 汉字\n例如：xiaoming 小明");
        editor.setText(data.experience().getString("personal_lexicon", "").replace('\t', ' '));
        showDialog(new AlertDialog.Builder(this).setTitle("个人词库：姓名、地名和常用词句").setView(editor).setNegativeButton("取消", null)
            .setPositiveButton("保存", (dialog, which) -> {
                StringBuilder result = new StringBuilder();
                for (String line : editor.getText().toString().split("\n")) {
                    String[] pair = line.trim().split("\\s+", 2);
                    if (pair.length == 2) result.append(pair[0]).append('\t').append(pair[1]).append('\n');
                }
                data.experience().edit().putString("personal_lexicon", result.toString()).apply(); loadLexicon();
                detail.setText("个人词库已保存，可在“拼音分组选词”中选择。");
            }).create());
    }
    private String recognitionName() {
        return data.usesIris() ? "虹膜特征 · " + new String[]{"双眼", "左眼", "右眼"}[data.getEyeMode()] : "原版图像模板 · 双眼";
    }
    private void setRecognition(boolean iris, int mode) {
        data.experience().edit().putBoolean("iris_engine", iris).putInt("iris_eye_mode", mode).apply();
        model.updateCalibrationTemplates();
        firstFrameAt = frames = trackedFrames = processingTotal = commands = 0;
        showRecognition();
    }
    private void showRecognition() {
        showMenu("Settings", "选择识别方案", choices(
            choice("使用虹膜特征 · 双眼", () -> setRecognition(true, IrisFeatures.BOTH)),
            choice("使用虹膜特征 · 左眼", () -> setRecognition(true, IrisFeatures.LEFT)),
            choice("使用虹膜特征 · 右眼", () -> setRecognition(true, IrisFeatures.RIGHT)),
            choice("使用原版图像模板 · 双眼", () -> setRecognition(false, data.getEyeMode())),
            choice("校准当前方案", this::showCalibration), choice("独立方向测试", this::showEvaluation),
            choice("返回照护设置", this::showSettings)),
            "当前：" + recognitionName() + (data.isActiveCalibrationVerified() ? "（已校准）" : "（需要校准）")
            + "\n双眼、左眼、右眼各自保存虹膜校准，原版校准也会保留。左 / 右眼指使用者自己的眼睛。"
            + "\n先试双眼；若一只眼睛难以控制或睁开，可选择另一只。切换后先校准，再用相同摆位做独立测试。", this::showSettings);
    }
    private void showEvaluation() {
        if (!data.isActiveCalibrationVerified()) { detail.setText("当前方案尚未校准。请先选择“校准当前方案”或“重新校准”，再做独立测试。"); return; }
        showFocusedMenu("EvaluationReady", "准备方向测试", choices(
            choice("开始测试", this::beginEvaluation), choice("返回使用质量", this::showDiagnostics)),
            "25 个方向目标", "约90秒，不修改校准", "选中开始后，按圆点提示看中间、左侧和右侧", this::showDiagnostics);
    }
    private void beginEvaluation() {
        leaveScreen("Evaluation"); menu = Collections.emptyList(); parent = this::showDiagnostics;
        evaluation = new GazeEvaluation();
        heading.setText("独立方向测试 · " + recognitionName());
        detail.setText("约90秒，共25个目标。看圆点，只移动眼睛。中间目标用于休息与准备下一次。\n"
            + "侧向目标只需确认一次，然后保持目光；不要提前看下一目标。测试不修改校准，也不会朗读短语。\n"
            + "错误和未分清都会计入报告；请照护者确认使用者确实跟随目标。");
        leftTarget.setText("左侧\n●"); rightTarget.setText("右侧\n●");
        leftTarget.setEnabled(false); rightTarget.setEnabled(false);
        rowButton(actions, "开始 / 重测", () -> {
            generation++; model.resetDwell(); evaluation.start(SystemClock.elapsedRealtime()); speech.speak("看中间");
        });
        rowButton(actions, "返回使用质量", this::showDiagnostics);
        neutralHint.setText("屏幕目标作为真值 · 每次侧向确认后看回中间 · 请保持手机位置与校准时一致");
        evaluation.start(SystemClock.elapsedRealtime()); speech.speak("看中间"); model.resetDwell();
    }
    private void updateEvaluation(DetectionOutput output, long now) {
        GazeEvaluation.Phase before = evaluation.phase; int oldIndex = evaluation.index;
        evaluation.tick(output.AnalyzedData.Success, output.AnalyzedData.GazeType, output.gestureOutput, now);
        if (evaluation.phase == GazeEvaluation.Phase.RUNNING) {
            heading.setText(evaluation.target == 0 ? "● 看中间" : "中间（等待下一个圆点）");
            detail.setText("目标 " + (evaluation.index + 1) + " / 25：看" + evaluation.targetName()
                + "\n保持头部不动，只移动眼睛。侧向确认一次后保持目光。\n当前识别："
                + (output.AnalyzedData.Success ? new String[]{"中间", "左侧", "右侧"}[output.AnalyzedData.GazeType] : "未分清"));
            leftTarget.setAlpha(evaluation.target == 1 ? 1 : .3f); rightTarget.setAlpha(evaluation.target == 2 ? 1 : .3f);
            dwellBar.setProgress((int) (evaluation.progress(now) * 100));
            if (oldIndex != evaluation.index) speech.speak("看" + evaluation.targetName());
        } else if (evaluation.phase == GazeEvaluation.Phase.DONE && before != GazeEvaluation.Phase.DONE) {
            String report = "Gaze Link " + BuildConfig.VERSION_NAME + " · " + recognitionName()
                + "\n停留：" + data.experience().getLong("dwell_millis", 800) + " ms\n时间："
                + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date()) + "\n" + evaluation.report();
            data.experience().edit().putString("evaluation_" + data.activeCalibrationKey(), report).apply();
            showMenu("EvaluationResult", "独立测试结果", choices(choice("再次测试", this::showEvaluation),
                choice("切换方案对比", this::showRecognition), choice("导出测试结果", () -> shareReport(report)),
                choice("返回使用质量", this::showDiagnostics)), report, this::showDiagnostics);
            speech.speak("测试完成，请查看结果。");
        }
    }
    private void shareReport(String report) {
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "导出诊断"));
    }
    private String diagnosticText() {
        double elapsed = Math.max(1, SystemClock.elapsedRealtime() - firstFrameAt) / 1000.0;
        return "识别方案：" + recognitionName() + "\n个人校准：" + (data.isActiveCalibrationVerified() ? "已通过独立验证" : "尚未完成新校准")
            + "\n设备：" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
            + " · Android " + android.os.Build.VERSION.RELEASE + "（API " + android.os.Build.VERSION.SDK_INT + "）"
            + "\n架构：" + Arrays.toString(android.os.Build.SUPPORTED_ABIS)
            + "\n屏幕：" + calibrationWidth + " × " + calibrationHeight + " · 旋转 " + calibrationRotation
            + "\n内存页：" + memoryPageSize()
            + "\n上次验证：" + data.experience().getString("last_validation_" + data.activeCalibrationKey(), data.usesIris() ? "无" : data.experience().getString("last_validation", "无"))
            + "\n本次处理帧率：" + String.format(Locale.CHINA, "%.1f", frames / elapsed) + " fps"
            + "\n有效方向识别：" + (frames == 0 ? 0 : trackedFrames * 100 / frames) + "%"
            + "\n平均处理时间：" + (frames == 0 ? 0 : processingTotal / frames) + " ms"
            + "\n眼动确认次数：" + commands
            + "\n以上为本次运行数据，不等同于方向准确率。验证通过也不代表长期准确率。\n\n上次独立测试：\n"
            + data.experience().getString("evaluation_" + data.activeCalibrationKey(), "暂无。请选择“独立方向测试”。");
    }
    private void showDiagnostics() {
        showMenu("Diagnostics", "校准与使用质量", choices(choice("独立方向测试", this::showEvaluation),
            choice("识别方案 / 有效眼睛", this::showRecognition), choice("重新校准", this::showCalibration),
            choice("导出本次诊断", () -> shareReport("Gaze Link " + BuildConfig.VERSION_NAME + "\n" + diagnosticText())),
            choice("返回照护设置", this::showSettings)), diagnosticText(), this::showSettings);
        preview = new ImageView(this); preview.setScaleX(-1); centre.addView(preview, new LinearLayout.LayoutParams(-1, dp(100)));
        pageRenderer = this::showDiagnostics;
    }
    private String memoryPageSize() {
        try { return android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) + " 字节"; }
        catch (Exception error) { return "无法读取"; }
    }
    private void updateFrame(DetectionOutput output, long processing) {
        long now = SystemClock.elapsedRealtime(); if (firstFrameAt == 0) firstFrameAt = now;
        frames++; processingTotal += processing; if (output.AnalyzedData.Success) trackedFrames++;
        if (blinkControlsEnabled && dialogCount == 0 && output.blinkEvent == BlinkDetector.Event.BOTH_CLOSED
                && (screen.equals("Calibration") || screen.equals("Evaluation"))) {
            speech.stop();
            if (screen.equals("Calibration")) showSettings(); else showDiagnostics();
            return;
        }
        if (screen.equals("Calibration")) updateCalibration(output);
        else if (screen.equals("Evaluation")) updateEvaluation(output, now);
        else {
            boolean inputEnabled = (!paused || screen.equals("Paused")) && dialogCount == 0
                    && guideOverlay == null && data.isActiveCalibrationVerified();
            dwellBar.setProgress(inputEnabled ? (int) (Math.max(output.dwellProgress,
                    Math.max(output.blinkProgress, output.mouthProgress)) * 100) : 0);
            boolean practice = screen.equals("BlinkPractice");
            if (practice) updateBlinkPractice(output);
            boolean usable = inputEnabled && output.smoothedGazeSuccess;
            leftTarget.setAlpha(usable && output.smoothedGazeType == 1 ? .7f : 1f);
            rightTarget.setAlpha(usable && output.smoothedGazeType == 2 ? .7f : 1f);
            if (inputEnabled && !paused && !practice && blinkControlsEnabled && output.blinkCommandArmed
                    && output.blinkEvent != BlinkDetector.Event.NONE) {
                commands++; handleBlinkCommand(output.blinkEvent);
            } else if (inputEnabled && !paused && !practice && mouthControlsEnabled
                    && output.mouthCommandArmed && output.mouthEvent == MouthDetector.Event.OPEN_CONFIRM) {
                commands++; model.resetDwell(); chooseCurrent();
            } else if (usable && (output.gestureOutput == 1 || output.gestureOutput == 2)) {
                commands++; if (output.gestureOutput == 1) chooseCurrent(); else advance();
            }
        }
        if (gazeOverlay != null && !screen.equals("Calibration") && !screen.equals("Evaluation")
                && !screen.equals("BlinkPractice") && !paused && guideOverlay == null)
            gazeOverlay.setState(output.smoothedGazeType, output.smoothedGazeSuccess,
                    output.dwellProgress, output.armed);
        if (screen.equals("Diagnostics")) { detail.setText(diagnosticText()); if (output.preview != null) preview.setImageBitmap(output.preview); }
        if (dialogCount > 0) status.setText("照护者正在编辑 · 眼动选择暂时停止");
        else if (screen.equals("BlinkPractice")) status.setText("练习中只记录手势 · 不执行确认、返回或取消；左右为使用者自己的眼睛");
        else if (paused) status.setText("表达已暂停 · 回中间后看左侧继续，或触摸顶部“继续”");
        else if (screen.equals("Calibration")) status.setText("眼动设置中 · " + (output.eyesVisible ? "已看到所需眼睛" : output.qualityMessage.isEmpty() ? "请调整位置，让双眼清晰可见" : output.qualityMessage));
        else if (screen.equals("Evaluation")) status.setText("独立方向测试 · 不执行表达选择" + (output.qualityMessage.isEmpty() ? "" : " · " + output.qualityMessage));
        else if (!data.isActiveCalibrationVerified()) status.setText("尚未完成眼动设置 · 点击“眼动设置”开始；也可直接触摸表达");
        else if (mouthControlsEnabled && output.mouthProgress > 0) status.setText("正在识别张嘴确认 · 完整闭嘴→张嘴→闭嘴后生效");
        else if (blinkControlsEnabled && output.blinkProgress > 0) status.setText("正在识别闭眼动作 · 睁开双眼后生效，过短或过长不会执行");
        else if (!output.qualityMessage.isEmpty()) status.setText(output.qualityMessage);
        else if (!output.eyesVisible && !output.AnalyzedData.Success) status.setText("暂时没有看到眼睛 · 调整手机位置；本次停留已取消");
        else if (!output.smoothedGazeSuccess) status.setText("方向尚未分清 · 先看中间，保持头部不动；持续不准时重新校准");
        else if (!output.armed) status.setText("请看回中间，准备下一次操作");
        else if (output.dwellProgress > 0) status.setText("已锁定当前区域 · 识别为" + (output.smoothedGazeType == 1 ? "左侧 · 正在选择" : "右侧 · 正在切换") + "，保持到进度填满");
        else status.setText("已就绪 · 看左侧选择，看右侧换下一项，中间为休息区");
    }
    private void startCamera() {
        if (cameraStarted) return;
        cameraStarted = true;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (destroyed) return;
            try {
                ProcessCameraProvider provider = future.get();
                if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    throw new IllegalStateException("No front camera available");
                }
                ImageAnalysis analysis = new ImageAnalysis.Builder().setTargetResolution(new android.util.Size(640, 480))
                    .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(cameraExecutor, this::analyze);
                cameraAnalysis = analysis;
                provider.bindToLifecycle(this, new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build(), analysis);
                cameraStarted = true;
            } catch (Exception error) {
                cameraStarted = false;
                cameraMessage = "前置相机未启动 · 可继续触摸使用。请检查相机权限后重试。"; status.setText(cameraMessage);
                android.util.Log.e("GazeCamera", "Unable to bind front camera", error);
            }
        }, ContextCompat.getMainExecutor(this));
    }
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(@NonNull ImageProxy image) {
        if (destroyed || !foreground || !frameInFlight.compareAndSet(false, true)) { image.close(); return; }
        int frameGeneration = generation; long start = SystemClock.elapsedRealtime(); Mat rgb = null;
        try {
            rgb = ImageFormatUtils.rgb(image.getImage()); int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation == 90) Core.rotate(rgb, rgb, Core.ROTATE_90_CLOCKWISE);
            else if (rotation == 180) Core.rotate(rgb, rgb, Core.ROTATE_180);
            else if (rotation == 270) Core.rotate(rgb, rgb, Core.ROTATE_90_COUNTERCLOCKWISE);
            DetectionOutput output = model.classifyGaze(rgb); long processing = SystemClock.elapsedRealtime() - start;
            main.post(() -> {
                try { if (!destroyed && foreground && frameGeneration == generation) updateFrame(output, processing); }
                finally { frameInFlight.set(false); }
            });
        } catch (RuntimeException error) {
            frameInFlight.set(false); model.resetDwell(); android.util.Log.e("GazeCamera", "Frame processing failed", error);
            main.post(() -> { if (!destroyed) { dwellBar.setProgress(0); status.setText("图像处理未完成 · 请重新打开眼动设置，触摸仍可用"); } });
        } finally { if (rgb != null) rgb.release(); image.close(); }
    }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1001) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
            else { cameraMessage = "相机权限未开启 · 可触摸表达，在手机应用权限中开启相机后重试"; status.setText(cameraMessage); }
        }
    }
    @Override public void onBackPressed() { if (parent != null) parent.run(); else showQuick(0); }
    @Override protected void onResume() {
        super.onResume(); foreground = true; model.resetDwell();
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (manager != null) manager.registerDisplayListener(displayListener, main);
        if (data != null) {
            updateCalibrationEnvironment();
            if (cameraAnalysis != null) cameraAnalysis.setTargetRotation(getWindowManager().getDefaultDisplay().getRotation());
        }
        if (data != null && !cameraStarted && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
    }
    @Override protected void onPause() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (manager != null) manager.unregisterDisplayListener(displayListener);
        foreground = false; generation++;
        if (calibration != null) calibration.suspend();
        if (evaluation != null) { evaluation.suspend(); detail.setText("测试已暂停。回到原姿势后，点“开始 / 重测”；未完成测试不会保存。"); }
        model.resetDwell(); if (speech != null) speech.stop(); super.onPause();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putString("draft", draft.text()); out.putString("context", draft.context());
        out.putString("last_phrase", lastPhrase); out.putBoolean("paused", paused); super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null);
        if (speech != null) speech.close(); if (suggestions != null) suggestions.close();
        cameraExecutor.execute(model::close); cameraExecutor.shutdown(); super.onDestroy();
    }
}
