package com.example.freemusicplayer;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText searchBox;
    private Button searchBtn, playPauseBtn, prevBtn, nextBtn;
    private TextView titleView, statusView, licenseView;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private final List<Track> tracks = new ArrayList<>();
    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean prepared = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    static class Track {
        String title;
        String creator;
        String url;
        String source;
        String license;
        Track(String title, String creator, String url, String source, String license) {
            this.title = title; this.creator = creator; this.url = url; this.source = source; this.license = license;
        }
        String display() {
            String by = TextUtils.isEmpty(creator) ? "未知作者" : creator;
            return title + "\n" + by + " · 合法免费资源";
        }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        searchBox.setText("piano");
        searchMusic("piano");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(12));
        root.setBackgroundColor(Color.rgb(248, 247, 255));

        TextView appTitle = new TextView(this);
        appTitle.setText("免费音乐播放器");
        appTitle.setTextSize(26);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        appTitle.setTextColor(Color.rgb(45, 35, 80));
        root.addView(appTitle);

        TextView tip = new TextView(this);
        tip.setText("自动搜索 Internet Archive 中的公版 / Creative Commons 免费音频资源，搜索后自动播放第一首。");
        tip.setTextSize(13);
        tip.setTextColor(Color.rgb(95, 89, 120));
        tip.setPadding(0, dp(6), 0, dp(10));
        root.addView(tip);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("输入关键词，如 piano / sleep / guitar");
        searchBox.setTextSize(15);
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        searchBtn = new Button(this);
        searchBtn.setText("搜索");
        searchRow.addView(searchBtn, new LinearLayout.LayoutParams(dp(88), dp(48)));
        root.addView(searchRow);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(8), 0, dp(8));
        String[] words = {"piano", "sleep", "guitar", "classical"};
        for (String w: words) {
            Button chip = new Button(this);
            chip.setText(w);
            chip.setTextSize(12);
            chip.setOnClickListener(v -> { searchBox.setText(w); searchMusic(w); });
            chips.addView(chip, new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        root.addView(chips);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE); bg.setCornerRadius(dp(18));
        card.setBackground(bg);

        titleView = new TextView(this);
        titleView.setText("正在准备音乐……");
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(Color.rgb(40, 35, 60));
        card.addView(titleView);

        licenseView = new TextView(this);
        licenseView.setText("授权信息会显示在这里");
        licenseView.setTextSize(12);
        licenseView.setTextColor(Color.rgb(110, 105, 130));
        licenseView.setPadding(0, dp(4), 0, dp(8));
        card.addView(licenseView);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        prevBtn = new Button(this); prevBtn.setText("上一首");
        playPauseBtn = new Button(this); playPauseBtn.setText("播放/暂停");
        nextBtn = new Button(this); nextBtn.setText("下一首");
        controls.addView(prevBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(playPauseBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(nextBtn, new LinearLayout.LayoutParams(0, dp(44), 1));
        card.addView(controls);
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        statusView = new TextView(this);
        statusView.setText("状态：初始化");
        statusView.setTextSize(13);
        statusView.setTextColor(Color.rgb(95, 89, 120));
        statusView.setPadding(0, dp(10), 0, dp(6));
        root.addView(statusView);

        listView = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        searchBtn.setOnClickListener(v -> searchMusic(searchBox.getText().toString().trim()));
        nextBtn.setOnClickListener(v -> playAt(currentIndex + 1));
        prevBtn.setOnClickListener(v -> playAt(currentIndex - 1));
        playPauseBtn.setOnClickListener(v -> togglePlay());
        listView.setOnItemClickListener((parent, view, position, id) -> playAt(position));
    }

    private void searchMusic(String keyword) {
        if (TextUtils.isEmpty(keyword)) keyword = "piano";
        final String q = keyword;
        status("正在搜索合法免费音乐：" + q);
        executor.execute(() -> {
            try {
                List<Track> result = searchInternetArchive(q);
                ui.post(() -> {
                    tracks.clear(); tracks.addAll(result);
                    adapter.clear();
                    for (Track t: tracks) adapter.add(t.display());
                    adapter.notifyDataSetChanged();
                    if (tracks.isEmpty()) status("没有搜到可播放的免费音频，请换关键词。推荐：piano、sleep、classical、guitar");
                    else { status("找到 " + tracks.size() + " 首，自动播放第一首"); playAt(0); }
                });
            } catch (Exception e) {
                ui.post(() -> status("搜索失败：" + e.getMessage()));
            }
        });
    }

    private List<Track> searchInternetArchive(String keyword) throws Exception {
        List<Track> out = new ArrayList<>();
        String query = "mediatype:audio AND (title:" + keyword + " OR description:" + keyword + ")";
        String url = "https://archive.org/advancedsearch.php?q=" + enc(query) + "&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=licenseurl&rows=16&page=1&output=json";
        JSONObject root = new JSONObject(httpGet(url));
        JSONArray docs = root.getJSONObject("response").getJSONArray("docs");
        for (int i=0; i<docs.length() && out.size()<12; i++) {
            JSONObject d = docs.getJSONObject(i);
            String id = d.optString("identifier");
            if (TextUtils.isEmpty(id)) continue;
            try {
                Track t = findPlayable(id, d.optString("title", id), d.optString("creator", ""), d.optString("licenseurl", "Public Domain / Creative Commons / Free Archive Resource"));
                if (t != null) out.add(t);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private Track findPlayable(String identifier, String title, String creator, String license) throws Exception {
        JSONObject meta = new JSONObject(httpGet("https://archive.org/metadata/" + encPath(identifier)));
        JSONArray files = meta.getJSONArray("files");
        for (int i=0; i<files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            String name = f.optString("name");
            String fmt = f.optString("format").toLowerCase();
            String lower = name.toLowerCase();
            if ((lower.endsWith(".mp3") || fmt.contains("mp3")) && !lower.contains("_files.xml")) {
                String playUrl = "https://archive.org/download/" + encPath(identifier) + "/" + encPath(name);
                return new Track(title, creator, playUrl, "Internet Archive", license);
            }
        }
        return null;
    }

    private void playAt(int index) {
        if (tracks.isEmpty()) return;
        if (index < 0) index = tracks.size() - 1;
        if (index >= tracks.size()) index = 0;
        currentIndex = index;
        Track t = tracks.get(index);
        titleView.setText(t.title);
        licenseView.setText("来源：" + t.source + "\n授权：" + (TextUtils.isEmpty(t.license) ? "免费/公版/CC资源，请以来源页为准" : t.license));
        status("正在缓冲：" + t.title);
        prepared = false;
        try {
            if (player != null) { player.reset(); player.release(); }
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());
            player.setDataSource(t.url);
            player.setOnPreparedListener(mp -> { prepared = true; mp.start(); status("正在播放：" + t.title); });
            player.setOnCompletionListener(mp -> playAt(currentIndex + 1));
            player.setOnErrorListener((mp, what, extra) -> { status("播放失败，自动切下一首"); ui.postDelayed(() -> playAt(currentIndex + 1), 800); return true; });
            player.prepareAsync();
        } catch (Exception e) { status("播放错误：" + e.getMessage()); }
    }

    private void togglePlay() {
        if (player == null) return;
        if (!prepared) { status("还在缓冲，请稍等"); return; }
        if (player.isPlaying()) { player.pause(); status("已暂停"); }
        else { player.start(); status("继续播放"); }
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(urlStr).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(16000);
        c.setRequestProperty("User-Agent", "FreeMusicPlayerApp/1.0");
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private String encPath(String s) throws Exception { return URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%2F", "/"); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private void status(String s) { statusView.setText("状态：" + s); }
    @Override protected void onDestroy() { super.onDestroy(); if (player != null) player.release(); executor.shutdownNow(); }
}
