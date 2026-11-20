package com.demo.opencv.communication;

import android.content.Context;
import com.demo.opencv.UserDataManager;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ordered local phrases. Editing never needs a network or a model key. */
public final class PhraseBook {
    public static final String[] CATEGORIES = {"常用", "饮食", "照护", "不适", "心情"};
    private static final String[][] DEFAULTS = {
        {"我要喝水", "我要上厕所", "我有点疼", "是", "不是", "请等一下"},
        {"我饿了", "我想吃饭", "请帮我擦嘴", "水太烫了", "我吃饱了", "我想喝温水"},
        {"请帮我翻身", "请调整枕头", "我想休息", "请帮我盖被子", "请开灯", "请叫家人过来"},
        {"我呼吸不舒服", "我觉得冷", "我觉得热", "这里疼", "请联系医生", "请扶我坐起来"},
        {"谢谢你", "我很高兴", "我有些担心", "我想听音乐", "我想安静一会儿", "我爱你"}
    };
    private final UserDataManager data;
    public PhraseBook(UserDataManager data) { this.data = data; }
    public List<String> get(int category) {
        String saved = data.experience().getString("phrases_" + category, "");
        if (saved.isEmpty()) return new ArrayList<>(Arrays.asList(DEFAULTS[category]));
        try {
            JSONArray array = new JSONArray(saved);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
            return result;
        } catch (JSONException error) { return new ArrayList<>(Arrays.asList(DEFAULTS[category])); }
    }
    public void save(int category, List<String> values) {
        data.experience().edit().putString("phrases_" + category, new JSONArray(values).toString()).apply();
    }
}
