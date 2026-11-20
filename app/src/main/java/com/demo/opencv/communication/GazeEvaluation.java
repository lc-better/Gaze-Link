package com.demo.opencv.communication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Fixed observation windows; errors stay in the report rather than being retried away. */
public final class GazeEvaluation {
    public enum Phase { READY, RUNNING, DONE }
    public Phase phase = Phase.READY;
    public int target, index;
    public final int[][] confusion = new int[3][4]; // observed truth x centre/left/right/unclassified
    public int successful, trials, wrongCommands, correctCommands;
    private final List<Integer> sequence = new ArrayList<>();
    private long targetAt, firstCorrect = -1, latencyTotal;
    private int latencyCount, targetCorrect, targetWrong, targetFrames, centreFrames;
    private static final String[] NAMES = {"中间", "左侧", "右侧"};

    public void start(long now) {
        List<Integer> sides = new ArrayList<>();
        for (int i = 0; i < 6; i++) { sides.add(1); sides.add(2); }
        Collections.shuffle(sides); sequence.clear(); sequence.add(0);
        for (int side : sides) { sequence.add(side); sequence.add(0); }
        for (int[] row : confusion) java.util.Arrays.fill(row, 0);
        successful = trials = wrongCommands = correctCommands = latencyCount = index = 0;
        latencyTotal = 0; phase = Phase.RUNNING; next(now);
    }
    private void next(long now) {
        target = sequence.get(index); targetAt = now; firstCorrect = -1;
        targetCorrect = targetWrong = targetFrames = centreFrames = 0;
    }
    public void tick(boolean valid, int direction, int command, long now) {
        if (phase != Phase.RUNNING) return;
        long elapsed = now - targetAt;
        if (elapsed >= duration()) {
            boolean pass = target == 0 ? targetWrong == 0 && targetFrames > 0 && centreFrames >= targetFrames * .6
                    : targetCorrect == 1 && targetWrong == 0;
            trials++; if (pass) successful++;
            if (firstCorrect >= 0) { latencyTotal += firstCorrect; latencyCount++; }
            index++;
            if (index == sequence.size()) { phase = Phase.DONE; return; }
            next(now); return; // The current frame still belongs to the previous displayed target.
        }
        // Allow 700 ms for moving the eyes; command errors are counted for the entire window.
        if (elapsed >= 700) {
            int predicted = valid && direction >= 0 && direction <= 2 ? direction : 3;
            confusion[target][predicted]++; targetFrames++;
            if (predicted == 0) centreFrames++;
        }
        if (command == 1 || command == 2) {
            if (target != 0 && command == target) {
                correctCommands++; targetCorrect++;
                if (firstCorrect < 0) firstCorrect = elapsed;
            } else { wrongCommands++; targetWrong++; }
        }
    }
    public void suspend() { if (phase == Phase.RUNNING) phase = Phase.READY; }
    public long duration() { return target == 0 ? 2500 : 4500; }
    public float progress(long now) { return Math.min(1f, Math.max(0, (now - targetAt) / (float) duration())); }
    public String targetName() { return NAMES[target]; }
    public String report() {
        int total = 0, matched = 0, missing = 0;
        for (int i = 0; i < 3; i++) {
            for (int n : confusion[i]) total += n;
            matched += confusion[i][i]; missing += confusion[i][3];
        }
        StringBuilder result = new StringBuilder(phase == Phase.DONE ? "测试完成" : "测试尚未完成");
        result.append("\n目标任务通过：").append(successful).append(" / ").append(trials)
                .append("（共25项，含13次回中）")
                .append("\n正确侧向确认：").append(correctCommands).append(" / 12")
                .append("；错误确认：").append(wrongCommands)
                .append("\n方向帧匹配：").append(total == 0 ? 0 : matched * 100 / total).append("%（未分清计入分母）")
                .append("；未分清：").append(total == 0 ? 0 : missing * 100 / total).append("%")
                .append("\n首次正确确认平均用时：").append(latencyCount == 0 ? "无" : String.format(Locale.CHINA, "%.2f 秒", latencyTotal / (latencyCount * 1000.0)))
                .append("\n混淆帧数（目标 → 中 / 左 / 右 / 未分清）：");
        for (int i = 0; i < 3; i++) result.append("\n").append(NAMES[i]).append(" → ")
                .append(confusion[i][0]).append(" / ").append(confusion[i][1]).append(" / ")
                .append(confusion[i][2]).append(" / ").append(confusion[i][3]);
        return result.append("\n按屏幕目标作为真值；须确实跟随目标。帧匹配不是长期准确率，确认用时包含反应与停留时间。").toString();
    }
}
