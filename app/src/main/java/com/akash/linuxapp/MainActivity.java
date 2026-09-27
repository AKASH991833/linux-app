package com.akash.linuxapp;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG=0xff101827, CARD=0xff1c2940, SOFT=0xff293954, TEXT=0xffedf3fa;
    private static final int DIM=0xffa7b8cc, BLUE=0xff5199ff, GREEN=0xff48df99, ORANGE=0xffffbc60, RED=0xffe75b67, PURPLE=0xff9d7bff;
    private static final int MODE_BEGINNER=0, MODE_INTERMEDIATE=1;

    static class Q {
        String q, e, chapter; String[] o; int a, idx;
        String id(){ return chapter + ":" + idx; }
    }
    static class Chapter {
        String id, title, desc; List<Q> qs = new ArrayList<>();
    }
    static class Lesson {
        String title, body, example, scenario, checkQ, checkE; String[] checkOptions; int checkAnswer;
    }
    static class InterviewQ {
        String q, a;
    }
    static class CommandItem {
        String command, example, use;
    }
    static class CommandGroup {
        String title; List<CommandItem> items = new ArrayList<>();
    }
    static class InterviewChapter {
        String id, title; List<InterviewQ> qs = new ArrayList<>(); List<CommandGroup> commands = new ArrayList<>();
    }
    static class SourceQ {
        int n; String topic, q, a, note;
    }
    static class SourceCommand {
        int n; String topic, command, example, use;
    }
    static class ReaderPage {
        String label, title, body, note;
    }

    private FrameLayout container;
    private TextView streakChip;
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean ttsReady;
    private Button speakingButton;
    private String speakingLabel = "\uD83D\uDD0A Speak";
    private final List<String> speechQueue = new ArrayList<>();
    private int speechIndex = -1;
    private int utteranceSeq;
    private boolean speechActive;
    private final List<Chapter> chapters = new ArrayList<>();
    private final java.util.Map<String, List<Lesson>> lessons = new java.util.HashMap<>();
    private final List<InterviewChapter> interviewChapters = new ArrayList<>();
    private final List<SourceQ> myQuestions = new ArrayList<>();
    private final List<SourceCommand> commands200 = new ArrayList<>();
    private final List<SourceQ> networkSource = new ArrayList<>();
    private boolean interviewOpenedFromHandbook;
    private int mode = MODE_BEGINNER;

    // quiz state
    private List<Q> quiz;
    private int qi, correctCount, timerSecs;
    private String quizTitle, bestKey;
    private boolean daily, revise;
    private Runnable restart;
    private CountDownTimer timer;
    private TextView counterText, timerText, questionText, explainText, explainTitle;
    private ProgressBar quizBar;
    private LinearLayout optionsBox;
    private View explainCard;
    private Button nextBtn, quizSpeakBtn;

    private int dp(int n){ return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable bg(int color){ GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(14)); return d; }
    private GradientDrawable bg(int color, int radiusDp){ GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp)); return d; }
    private TextView text(String s, int size, int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private TextView bold(String s, int size, int color){ TextView t=text(s,size,color); t.setTypeface(null,Typeface.BOLD); return t; }
    private void press(final View v){
        v.setOnTouchListener((view, ev) -> {
            if(ev.getAction()==MotionEvent.ACTION_DOWN) view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
            else if(ev.getAction()==MotionEvent.ACTION_UP || ev.getAction()==MotionEvent.ACTION_CANCEL) view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            return false;
        });
    }
    private void popIn(View v, int delayMs){
        v.setAlpha(0f); v.setTranslationY(dp(18));
        v.animate().alpha(1f).translationY(0).setStartDelay(delayMs).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
    }
    private void switchScreen(View v){
        stopSpeech();
        container.removeAllViews();
        container.addView(v);
        v.setAlpha(0f); v.setTranslationY(dp(26));
        v.animate().alpha(1f).translationY(0).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("linuxapp", 0);
        initSpeech();
        loadData();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(6));
        TextView brand = bold("Linux App", 18, TEXT);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        streakChip = text("", 13, ORANGE);
        streakChip.setTypeface(null, Typeface.BOLD);
        streakChip.setBackground(bg(SOFT, 20));
        streakChip.setPadding(dp(12), dp(6), dp(12), dp(6));
        header.addView(streakChip);
        root.addView(header);
        container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        showHome();
    }

    private void loadData(){
        try {
            InputStream in = getAssets().open("questions.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while((n=in.read(buf))>0) bos.write(buf,0,n);
            in.close();
            JSONObject root = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
            JSONArray arr = root.getJSONArray("chapters");
            for(int i=0;i<arr.length();i++){
                JSONObject c = arr.getJSONObject(i);
                Chapter ch = new Chapter();
                ch.id = c.getString("id"); ch.title = c.getString("title"); ch.desc = c.getString("desc");
                JSONArray qa = c.getJSONArray("questions");
                for(int j=0;j<qa.length();j++){
                    JSONObject qj = qa.getJSONObject(j);
                    Q q = new Q();
                    q.q = qj.getString("q"); q.e = qj.getString("e"); q.a = qj.getInt("a");
                    q.chapter = ch.id; q.idx = j;
                    JSONArray oa = qj.getJSONArray("o");
                    q.o = new String[oa.length()];
                    for(int k=0;k<oa.length();k++) q.o[k] = oa.getString(k);
                    ch.qs.add(q);
                }
                chapters.add(ch);
            }
        } catch(Exception ex){
            Chapter ch = new Chapter(); ch.id="error"; ch.title="Load error"; ch.desc=String.valueOf(ex.getMessage());
            chapters.add(ch);
        }
        try {
            InputStream in = getAssets().open("lessons.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while((n=in.read(buf))>0) bos.write(buf,0,n);
            in.close();
            JSONObject root = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
            JSONArray arr = root.getJSONArray("chapters");
            for(int i=0;i<arr.length();i++){
                JSONObject c = arr.getJSONObject(i);
                List<Lesson> list = new ArrayList<>();
                JSONArray la = c.getJSONArray("lessons");
                for(int j=0;j<la.length();j++){
                    JSONObject lj = la.getJSONObject(j);
                    Lesson l = new Lesson();
                    l.title = lj.getString("title");
                    l.body = lj.getString("body");
                    l.example = lj.optString("example", "");
                    l.scenario = lj.optString("scenario", "");
                    JSONObject check = lj.optJSONObject("check");
                    if(check != null){
                        l.checkQ = check.getString("q");
                        l.checkE = check.getString("e");
                        l.checkAnswer = check.getInt("a");
                        JSONArray ops = check.getJSONArray("o");
                        l.checkOptions = new String[ops.length()];
                        for(int k=0;k<ops.length();k++) l.checkOptions[k] = ops.getString(k);
                    }
                    list.add(l);
                }
                lessons.put(c.getString("id"), list);
            }
        } catch(Exception ignored){ }
        try {
            InputStream in = getAssets().open("interview.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while((n=in.read(buf))>0) bos.write(buf,0,n);
            in.close();
            JSONObject root = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
            JSONArray arr = root.getJSONArray("chapters");
            for(int i=0;i<arr.length();i++){
                JSONObject c = arr.getJSONObject(i);
                InterviewChapter ic = new InterviewChapter();
                ic.id = c.getString("id");
                ic.title = c.optString("title", ic.id);
                JSONArray qa = c.getJSONArray("questions");
                for(int j=0;j<qa.length();j++){
                    JSONObject qj = qa.getJSONObject(j);
                    InterviewQ iq = new InterviewQ();
                    iq.q = qj.getString("q");
                    iq.a = qj.getString("a");
                    ic.qs.add(iq);
                }
                JSONArray ca = c.optJSONArray("commands");
                if(ca != null){
                    for(int j=0;j<ca.length();j++){
                        JSONObject gj = ca.getJSONObject(j);
                        CommandGroup group = new CommandGroup();
                        group.title = gj.getString("title");
                        JSONArray ia = gj.getJSONArray("items");
                        for(int k=0;k<ia.length();k++){
                            JSONObject ij = ia.getJSONObject(k);
                            CommandItem item = new CommandItem();
                            item.command = ij.getString("command");
                            item.example = ij.getString("example");
                            item.use = ij.getString("use");
                            group.items.add(item);
                        }
                        ic.commands.add(group);
                    }
                }
                interviewChapters.add(ic);
            }
        } catch(Exception ignored){ }
        try {
            InputStream in = getAssets().open("my_interview.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while((n=in.read(buf))>0) bos.write(buf,0,n);
            in.close();
            JSONArray arr = new JSONObject(new String(bos.toByteArray(), "UTF-8")).getJSONArray("questions");
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.getJSONObject(i);
                SourceQ q=new SourceQ();
                q.n=o.getInt("n"); q.q=o.getString("q"); q.a=o.getString("a"); q.note=o.optString("note", "");
                myQuestions.add(q);
            }
        } catch(Exception ignored){ }
        try {
            InputStream in = getAssets().open("commands200.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while((n=in.read(buf))>0) bos.write(buf,0,n);
            in.close();
            JSONArray arr = new JSONObject(new String(bos.toByteArray(), "UTF-8")).getJSONArray("items");
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.getJSONObject(i);
                SourceCommand c=new SourceCommand();
                c.n=o.getInt("n"); c.topic=o.getString("topic"); c.command=o.getString("command"); c.example=o.getString("example"); c.use=o.getString("use");
                commands200.add(c);
            }
        } catch(Exception ignored){ }
        try {
            InputStream in = getAssets().open("network_source.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while((n=in.read(buf))>0) bos.write(buf,0,n);
            in.close();
            JSONArray arr = new JSONObject(new String(bos.toByteArray(), "UTF-8")).getJSONArray("items");
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.getJSONObject(i);
                SourceQ q=new SourceQ();
                q.n=o.getInt("n"); q.topic=o.getString("topic"); q.q=o.getString("q"); q.a=o.getString("a"); q.note="";
                networkSource.add(q);
            }
        } catch(Exception ignored){ }
    }

    private Set<String> wrongSet(){ return new HashSet<>(prefs.getStringSet("wrong", new HashSet<String>())); }
    private void saveWrong(Set<String> w){ prefs.edit().putStringSet("wrong", w).apply(); }
    private String today(){ return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    private String yesterday(){ return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(System.currentTimeMillis()-86400000L)); }
    private int streak(){ return prefs.getInt("streak", 0); }
    private int totalPassedChecks(){
        int count=0;
        for(Chapter ch: chapters){
            List<Lesson> ls=lessons.get(ch.id);
            if(ls!=null) for(int i=0;i<ls.size();i++) if(prefs.getBoolean("check_"+ch.id+"_"+i,false)) count++;
        }
        return count;
    }

    private void updateStreakChip(){
        int s = streak();
        streakChip.setText(s > 0 ? "\uD83D\uDD25 " + s + " day streak" : "\uD83D\uDD25 Start a streak");
    }

    // ---------------- HOME DASHBOARD ----------------
    private void showHome(){
        cancelTimer();
        updateStreakChip();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff1d3260, 0xff3a2456});
        heroBg.setCornerRadius(dp(20));
        hero.setBackground(heroBg);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.addView(text("🐧", 38, TEXT));
        hero.addView(bold("Choose what to study", 21, TEXT));
        TextView sub = text("One simple dashboard. Open a tile, then choose a topic or chapter.", 13, DIM);
        sub.setPadding(0, dp(4), 0, 0);
        hero.addView(sub);
        TextView version = text("v1.3.1  •  328 interview Q&A  •  346 commands  •  Speak built in", 12, ORANGE);
        version.setTypeface(null, Typeface.BOLD);
        version.setPadding(0, dp(8), 0, 0);
        hero.addView(version);
        col.addView(hero, margins(0, 8));

        col.addView(actionCard("⚡ Daily Challenge", "10 questions - new set every day", BLUE,
                prefs.getString("lastDaily", "").equals(today()) ? "Done today ✓" : "Start",
                v -> startDaily()), margins(0, 6));
        int wrong = wrongSet().size();
        if(wrong > 0)
            col.addView(actionCard("🔁 Revise Mistakes", wrong + " question" + (wrong==1?"":"s") + " you got wrong", ORANGE, "Revise",
                    v -> startRevise()), margins(0, 6));

        col.addView(sectionHead("🎯", "Home Dashboard", "Big entry tiles only - topics open after you tap.", GREEN), margins(0, 8));
        col.addView(dashboardTile("🟢", "Beginner", "Topic-wise quizzes  •  no timer", BLUE,
                v -> showQuizTopics(MODE_BEGINNER)), margins(0, 6));
        col.addView(dashboardTile("🟠", "Intermediate", "Topic-wise quizzes  •  30 sec per question", ORANGE,
                v -> showQuizTopics(MODE_INTERMEDIATE)), margins(0, 6));
        col.addView(dashboardTile("💼", "Interview Questions", "Your questions, PDFs + topic-wise chapters", PURPLE,
                v -> showInterviewTopics()), margins(0, 6));
        col.addView(dashboardTile("📖", "Learn Chapters", "48 short lessons  •  Listen + quick checks", GREEN,
                v -> showLearnTopics()), margins(0, 6));
        switchScreen(scroll);
    }

    private View dashboardTile(String emoji, String title, String sub, int color, View.OnClickListener click){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(CARD, 22));
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView icon = text(emoji, 30, TEXT);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(bg(color, 18));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(58), dp(58));
        ip.bottomMargin = dp(12);
        card.addView(icon, ip);
        card.addView(bold(title, 20, TEXT));
        TextView s = text(sub, 13, DIM);
        s.setPadding(0, dp(4), 0, dp(8));
        card.addView(s);
        card.addView(bold("Open →", 13, color));
        card.setOnClickListener(click);
        press(card);
        return card;
    }

    private Button listBackButton(String label, View.OnClickListener click){
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(13);
        b.setBackground(bg(SOFT, 22));
        b.setOnClickListener(click);
        press(b);
        return b;
    }

    private void showQuizTopics(final int selectedMode){
        mode = selectedMode;
        cancelTimer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);
        boolean beginner = selectedMode == MODE_BEGINNER;
        col.addView(sectionHead(beginner ? "🟢" : "🟠",
                beginner ? "Beginner Quiz Topics" : "Intermediate Quiz Topics",
                beginner ? "Choose one topic - no timer." : "Choose one topic - 30 seconds per question.",
                beginner ? BLUE : ORANGE), margins(0, 7));
        for(final Chapter ch : chapters)
            col.addView(topicCard("📝", ch.title + " Quiz",
                    "10 of " + ch.qs.size() + " questions per round" + bestSuffix(ch), beginner ? BLUE : ORANGE, "Start",
                    v -> startChapterQuiz(ch)), margins(0, 5));
        col.addView(listBackButton("Back to Home", v -> showHome()), margins(0, 10));
        switchScreen(scroll);
    }

    private void showInterviewTopics(){
        cancelTimer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);

        col.addView(sectionHead("⭐", "Your Questions & PDFs", "The exact material you sent, each under its own name.", ORANGE), margins(0, 7));
        col.addView(topicCard("📖", "My Interview Questions",
                myQuestions.size() + " Q&A  •  in the order you sent", ORANGE, "Open",
                v -> showMyQuestions()), margins(0, 5));
        col.addView(topicCard("⌨️", "200 Important Commands",
                commands200.size() + " rows  •  from your command PDF", BLUE, "Open",
                v -> showCommands200()), margins(0, 5));
        col.addView(topicCard("🌐", "Networking Interview Q&A",
                networkSource.size() + " Q&A  •  simple English answers", PURPLE, "Open",
                v -> showNetworkSource()), margins(0, 5));
        col.addView(topicCard("🚀", "15 Advanced Topics Handbook",
                "15 topics  •  Part 2 practical handbook", GREEN, "Open",
                v -> showAdvancedHandbook()), margins(0, 5));

        col.addView(sectionHead("💼", "Topic-wise Interview Chapters", "The same content organised by subject for revision.", PURPLE), margins(0, 10));
        for(final InterviewChapter ic : interviewChapters)
            col.addView(topicCard("💼", ic.title + " Interview Questions",
                    interviewCardSub(ic), PURPLE, "Open",
                    v -> { interviewOpenedFromHandbook=false; showInterviewTopic(ic); }), margins(0, 5));
        col.addView(listBackButton("Back to Home", v -> showHome()), margins(0, 10));
        switchScreen(scroll);
    }

    private void showLearnTopics(){
        cancelTimer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);
        col.addView(sectionHead("📖", "Learn Chapters", "Choose one chapter, read short lessons, then take its quiz.", GREEN), margins(0, 7));
        int delay = 0;
        for(final Chapter ch : chapters){
            col.addView(chapterCard(ch, delay), margins(0, 6));
            delay += 60;
        }
        col.addView(listBackButton("Back to Home", v -> showHome()), margins(0, 10));
        switchScreen(scroll);
    }

    private void showMyQuestions(){
        cancelTimer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);
        col.addView(sectionHead("📖", "My Interview Questions", "Your questions and answers in your original order. Tap one to read it like a book page.", ORANGE), margins(0, 7));
        List<ReaderPage> pages = new ArrayList<>();
        for(SourceQ q : myQuestions){
            ReaderPage p = new ReaderPage();
            p.label = "Question " + q.n; p.title = q.q; p.body = q.a; p.note = q.note;
            pages.add(p);
        }
        for(int i=0;i<pages.size();i++){
            final int idx=i;
            col.addView(questionListRow(pages.get(i).label, pages.get(i).title, "Read answer →", ORANGE,
                    v -> showQaReader("My Interview Questions", pages, idx, ORANGE, () -> showMyQuestions())), margins(0, 5));
        }
        col.addView(listBackButton("Back to Interview Sections", v -> showInterviewTopics()), margins(0, 10));
        switchScreen(scroll);
    }

    private void showCommands200(){
        cancelTimer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);
        col.addView(sectionHead("⌨️", "200 Important Commands", "Your command PDF - all 200 rows in original order, grouped by topic.", BLUE), margins(0, 7));
        String lastTopic = "";
        for(final SourceCommand c : commands200){
            if(!c.topic.equals(lastTopic)){
                lastTopic = c.topic;
                TextView head = bold(lastTopic, 16, BLUE);
                head.setPadding(0, dp(10), 0, dp(2));
                col.addView(head);
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setBackground(bg(CARD, 16));
            row.setPadding(dp(14), dp(11), dp(10), dp(11));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView cmd = mono("#" + c.n + "  " + c.command, 13, BLUE);
            cmd.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            texts.addView(cmd);
            TextView ex = mono("Example: " + c.example, 11, TEXT);
            ex.setPadding(0, dp(4), 0, 0);
            texts.addView(ex);
            TextView use = text("Use: " + c.use, 11, DIM);
            use.setPadding(0, dp(3), 0, 0);
            texts.addView(use);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            Button speak = new Button(this);
            speak.setText("🔊"); speak.setAllCaps(false); speak.setTextColor(BLUE); speak.setTextSize(13);
            speak.setBackground(bg(SOFT, 14)); speak.setMinWidth(0); speak.setMinimumWidth(0); speak.setPadding(0,0,0,0);
            final List<String> speech = singleSpeech("Command " + c.command + ". Use. " + c.use);
            speak.setOnClickListener(v -> toggleSpeech(speak, "🔊", speech));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(40), dp(36));
            sp.leftMargin = dp(8);
            row.addView(speak, sp);
            col.addView(row, margins(0, 4));
        }
        col.addView(listBackButton("Back to Interview Sections", v -> showInterviewTopics()), margins(0, 10));
        switchScreen(scroll);
    }

    private void showNetworkSource(){
        cancelTimer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);
        col.addView(sectionHead("🌐", "Networking Interview Q&A", "Your networking PDF - all 40 Q&A in original order.", PURPLE), margins(0, 7));
        List<ReaderPage> pages = new ArrayList<>();
        for(SourceQ q : networkSource){
            ReaderPage p = new ReaderPage();
            p.label = q.n + ". " + q.topic; p.title = q.q; p.body = q.a; p.note = "";
            pages.add(p);
        }
        for(int i=0;i<pages.size();i++){
            final int idx=i;
            col.addView(questionListRow(pages.get(i).label, pages.get(i).title, "Read answer →", PURPLE,
                    v -> showQaReader("Networking Interview Q&A", pages, idx, PURPLE, () -> showNetworkSource())), margins(0, 5));
        }
        col.addView(listBackButton("Back to Interview Sections", v -> showInterviewTopics()), margins(0, 10));
        switchScreen(scroll);
    }

    private void showAdvancedHandbook(){
        cancelTimer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);
        col.addView(sectionHead("🚀", "15 Advanced Topics Handbook", "Your Part 2 PDF - 15 practical topics in original order.", GREEN), margins(0, 7));
        String[] ids = {"backup", "ubuntu-packages", "advanced-storage", "boot-recovery", "performance", "logging", "web-servers", "mysql", "advanced-security", "identity", "monitoring", "ansible", "virtualization", "time", "aws"};
        for(int i=0;i<ids.length;i++){
            final InterviewChapter ic = findInterviewChapter(ids[i]);
            if(ic == null) continue;
            col.addView(topicCard("🚀", (i+1) + ". " + ic.title,
                    interviewCardSub(ic), GREEN, "Open",
                    v -> { interviewOpenedFromHandbook=true; showInterviewTopic(ic); }), margins(0, 5));
        }
        col.addView(listBackButton("Back to Interview Sections", v -> showInterviewTopics()), margins(0, 10));
        switchScreen(scroll);
    }

    private InterviewChapter findInterviewChapter(String id){
        for(InterviewChapter ic : interviewChapters) if(ic.id.equals(id)) return ic;
        return null;
    }

    private View questionListRow(String label, String title, String cta, int color, View.OnClickListener click){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(CARD, 18));
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        card.addView(bold(label, 11, color));
        TextView q = bold(title, 16, TEXT);
        q.setLineSpacing(0, 1.12f);
        q.setPadding(0, dp(4), 0, dp(6));
        card.addView(q);
        card.addView(bold(cta, 12, color));
        card.setOnClickListener(click);
        press(card);
        return card;
    }

    private List<ReaderPage> interviewPages(InterviewChapter ch){
        List<ReaderPage> pages = new ArrayList<>();
        for(int i=0;i<ch.qs.size();i++){
            InterviewQ q = ch.qs.get(i);
            ReaderPage p = new ReaderPage();
            p.label = "Question " + (i+1); p.title = q.q; p.body = q.a; p.note = "";
            pages.add(p);
        }
        return pages;
    }

    private void showQaReader(String section, final List<ReaderPage> pages, final int index, int color, final Runnable back){
        cancelTimer();
        final ReaderPage page = pages.get(index);
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(8), dp(18), dp(26));
        scroll.addView(col);
        col.addView(bold(section + "  •  " + (index+1) + " of " + pages.size(), 12, DIM), margins(0, 3));
        TextView label = bold(page.label, 13, color);
        label.setPadding(0, dp(8), 0, dp(2));
        col.addView(label);
        TextView title = bold(page.title, 22, TEXT);
        title.setLineSpacing(0, 1.14f);
        col.addView(title, margins(0, 3));

        Button speak = new Button(this);
        speak.setText("🔊 Speak answer"); speak.setAllCaps(false); speak.setTextColor(color); speak.setTextSize(12);
        speak.setTypeface(null, Typeface.BOLD);
        speak.setBackground(bg(SOFT, 20));
        speak.setOnClickListener(v -> toggleSpeech(speak, "🔊 Speak answer", singleSpeech("Question. " + page.title + ". Answer. " + answerSpeechBody(page.body))));
        press(speak);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, dp(38));
        sp.setMargins(0, dp(8), 0, dp(4));
        col.addView(speak, sp);

        LinearLayout paper = new LinearLayout(this);
        paper.setOrientation(LinearLayout.VERTICAL);
        paper.setBackground(bg(CARD, 20));
        paper.setPadding(dp(18), dp(16), dp(18), dp(18));
        addBookAnswer(paper, page.body);
        col.addView(paper, margins(0, 8));

        if(page.note != null && !page.note.isEmpty()){
            LinearLayout note = new LinearLayout(this);
            note.setOrientation(LinearLayout.VERTICAL);
            note.setBackground(bg(0xff3a2f1e, 16));
            note.setPadding(dp(14), dp(12), dp(14), dp(12));
            note.addView(bold("Note", 12, ORANGE));
            TextView nt = text(page.note, 14, TEXT);
            nt.setLineSpacing(0, 1.15f);
            nt.setPadding(0, dp(4), 0, 0);
            note.addView(nt);
            col.addView(note, margins(0, 6));
        }

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(12), 0, 0);
        if(index > 0){
            Button prev = new Button(this);
            prev.setText("← Previous"); prev.setAllCaps(false); prev.setTextColor(TEXT); prev.setTextSize(13);
            prev.setBackground(bg(SOFT, 22));
            prev.setOnClickListener(v -> showQaReader(section, pages, index-1, color, back));
            press(prev);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1);
            p.rightMargin = dp(6);
            nav.addView(prev, p);
        }
        Button next = new Button(this);
        boolean last = index == pages.size()-1;
        next.setText(last ? "Back to list" : "Next →");
        next.setAllCaps(false); next.setTextColor(0xff101827); next.setTextSize(13); next.setTypeface(null, Typeface.BOLD);
        next.setBackground(bg(color, 22));
        next.setOnClickListener(v -> { if(last) back.run(); else showQaReader(section, pages, index+1, color, back); });
        press(next);
        nav.addView(next, new LinearLayout.LayoutParams(0, dp(46), 1));
        col.addView(nav);
        col.addView(listBackButton("Back to list", v -> back.run()), margins(0, 8));
        switchScreen(scroll);
    }

    private String answerSpeechBody(String body){
        if(body == null) return "";
        String answer = body.trim();
        int cut = answer.indexOf("\n\n");
        if(cut >= 0) answer = answer.substring(0, cut).trim();
        if(answer.startsWith("Answer:")) answer = answer.substring("Answer:".length()).trim();
        if(answer.startsWith("English:")) answer = answer.substring("English:".length()).trim();
        if(answer.startsWith("English/Hinglish:")) answer = answer.substring("English/Hinglish:".length()).trim();
        return answer;
    }

    private void addBookAnswer(LinearLayout box, String body){
        String[] paras = body.split("\\n\\n");
        for(String raw : paras){
            String para = raw.trim();
            if(para.isEmpty()) continue;
            boolean commandBlock = para.contains(" → ") || para.contains(";\n") || para.matches("(?s)(#?[a-zA-Z][a-zA-Z0-9_.-]*(\\s+[^\\n]+)?\\n?)+");
            if(commandBlock && para.length() < 500){
                LinearLayout code = new LinearLayout(this);
                code.setBackground(bg(0xff0b1220, 12));
                code.setPadding(dp(12), dp(10), dp(12), dp(10));
                TextView t = mono(para, 13, 0xffc9d7ea);
                t.setLineSpacing(0, 1.12f);
                code.addView(t);
                box.addView(code, margins(0, 5));
            } else {
                TextView t = text(para, 16, TEXT);
                t.setLineSpacing(0, 1.24f);
                t.setTextIsSelectable(true);
                box.addView(t, margins(0, 5));
            }
        }
    }

    private LinearLayout.LayoutParams margins(int h, int v){
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(h), dp(v), dp(h), dp(v));
        return p;
    }

    private View actionCard(String title, String sub, int color, String cta, View.OnClickListener click){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(bg(CARD, 18));
        card.setPadding(dp(16), dp(14), dp(10), dp(14));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(bold(title, 16, TEXT));
        texts.addView(text(sub, 12, DIM));
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Button b = new Button(this);
        b.setText(cta); b.setAllCaps(false); b.setTextColor(0xff101827); b.setTextSize(13); b.setTypeface(null, Typeface.BOLD);
        b.setBackground(bg(color, 22));
        card.addView(b, new LinearLayout.LayoutParams(-2, dp(44)));
        card.setOnClickListener(click);
        b.setOnClickListener(click);
        press(card);
        return card;
    }

    private String bestSuffix(Chapter ch){
        int best = prefs.getInt("best_" + ch.id, -1);
        return best >= 0 ? "  \u2022  Best: " + best + "%" : "";
    }

    private int commandCount(InterviewChapter ch){
        int count = 0;
        for(CommandGroup group : ch.commands) count += group.items.size();
        return count;
    }

    private String interviewCardSub(InterviewChapter ch){
        String noun = ch.qs.size() == 1 ? " question" : " questions";
        String base = ch.qs.size() + noun + ("hr".equals(ch.id) ? " with sample answers" : " with verified answers");
        int commands = commandCount(ch);
        return commands > 0 ? base + "  \u2022  " + commands + " command examples" : base;
    }

    private View sectionHead(String emoji, String title, String sub, int accent){
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        bar.setBackground(bg(accent, 3));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(4), dp(22));
        bp.rightMargin = dp(10);
        row.addView(bar, bp);
        row.addView(text(emoji, 17, TEXT));
        row.addView(bold("  " + title, 17, TEXT));
        box.addView(row);
        TextView s = text(sub, 12, DIM);
        s.setPadding(dp(14), dp(3), 0, 0);
        box.addView(s);
        return box;
    }

    private View topicCard(String emoji, String title, String sub, int color, String cta, View.OnClickListener click){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(bg(CARD, 18));
        card.setPadding(dp(12), dp(10), dp(10), dp(10));
        TextView ic = text(emoji, 16, TEXT);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(bg(SOFT, 14));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(44), dp(44));
        ip.rightMargin = dp(12);
        card.addView(ic, ip);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(bold(title, 14, TEXT));
        TextView s = text(sub, 11, DIM);
        s.setPadding(0, dp(2), 0, 0);
        texts.addView(s);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Button b = new Button(this);
        b.setText(cta); b.setAllCaps(false); b.setTextColor(0xff101827); b.setTextSize(12); b.setTypeface(null, Typeface.BOLD);
        b.setBackground(bg(color, 22));
        card.addView(b, new LinearLayout.LayoutParams(-2, dp(40)));
        card.setOnClickListener(click);
        b.setOnClickListener(click);
        press(card);
        return card;
    }

    private int passedChecks(Chapter ch){
        int count=0;
        List<Lesson> ls=lessons.get(ch.id);
        if(ls!=null) for(int i=0;i<ls.size();i++) if(prefs.getBoolean("check_"+ch.id+"_"+i,false)) count++;
        return count;
    }

    private View chapterCard(final Chapter ch, int delay){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(CARD, 18));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(bold(ch.title, 15, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        int best = prefs.getInt("best_" + ch.id, -1);
        int lc = lessons.containsKey(ch.id) ? lessons.get(ch.id).size() : 0;
        int readCount = 0;
        for(int i=0;i<lc;i++) if(prefs.getBoolean("read_" + ch.id + "_" + i, false)) readCount++;
        TextView pct = bold(best >= 0 ? best + "%" : ch.qs.size() + " Qs", 13, best >= 0 ? GREEN : DIM);
        top.addView(pct);
        card.addView(top);
        TextView d = text(ch.desc + (lc>0 ? "  \u2022  " + lc + " lessons" + (readCount>0 ? " (" + readCount + " read)" : "") : ""), 12, DIM);
        d.setPadding(0, dp(3), 0, dp(6));
        card.addView(d);
        TextView badge=text(passedChecks(ch)==lc && lc>0 ? "Chapter mastered ✓" : "Lessons " + readCount + "/" + lc + "  •  Checks " + passedChecks(ch) + "/" + lc,12,passedChecks(ch)==lc && lc>0 ? GREEN : ORANGE);
        card.addView(badge);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        card.addView(bar);
        if(best > 0){
            ValueAnimator anim = ValueAnimator.ofInt(0, best);
            anim.setDuration(600);
            anim.setStartDelay(200 + delay);
            anim.addUpdateListener(a -> bar.setProgress((Integer)a.getAnimatedValue()));
            anim.start();
        }
        card.setOnClickListener(v -> showChapter(ch));
        press(card);
        popIn(card, delay);
        return card;
    }

    // ---------------- CHAPTER + LESSONS ----------------
    private TextView mono(String s, int size, int color){
        TextView t = text(s, size, color);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        return t;
    }

    private void showChapter(final Chapter ch){
        cancelTimer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackground(bg(CARD, 20));
        head.setPadding(dp(18), dp(16), dp(18), dp(16));
        head.addView(bold(ch.title, 19, TEXT));
        TextView sub = text(ch.desc, 13, DIM);
        sub.setPadding(0, dp(4), 0, 0);
        head.addView(sub);
        col.addView(head, margins(0, 6));

        Button quiz = new Button(this);
        quiz.setText("\u25B6  Start Quiz (" + ch.qs.size() + " questions)");
        quiz.setAllCaps(false); quiz.setTextColor(0xff101827); quiz.setTextSize(14);
        quiz.setTypeface(null, Typeface.BOLD);
        quiz.setBackground(bg(BLUE, 22));
        quiz.setOnClickListener(v -> startChapterQuiz(ch));
        press(quiz);
        col.addView(quiz, margins(0, 6));

        final List<Lesson> ls = lessons.get(ch.id);
        if(ls != null && !ls.isEmpty()){
            TextView lh = bold("Learn first - " + ls.size() + " short lessons", 15, TEXT);
            lh.setPadding(0, dp(10), 0, dp(2));
            col.addView(lh);
            for(int i=0;i<ls.size();i++){
                final int idx = i;
                final Lesson l = ls.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(bg(CARD, 16));
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                TextView num = bold((i+1) + "", 14, BLUE);
                num.setMinWidth(dp(28));
                row.addView(num);
                row.addView(text(l.title, 14, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
                boolean read = prefs.getBoolean("read_" + ch.id + "_" + i, false);
                boolean checked = prefs.getBoolean("check_" + ch.id + "_" + i, false);
                TextView tick = text(checked ? "✓" : read ? "◐" : "○", 15, checked ? GREEN : read ? ORANGE : DIM);
                row.addView(tick);
                row.setOnClickListener(v -> showLesson(ch, idx));
                press(row);
                col.addView(row, margins(0, 4));
                popIn(row, 50 * i);
            }
        }
        Button home = new Button(this);
        home.setText("Back to Learn Chapters"); home.setAllCaps(false); home.setTextColor(TEXT); home.setTextSize(13);
        home.setBackground(bg(SOFT, 22));
        home.setOnClickListener(v -> showLearnTopics());
        press(home);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(46));
        hp.setMargins(0, dp(10), 0, 0);
        col.addView(home, hp);
        switchScreen(scroll);
    }

    private void showLesson(final Chapter ch, final int idx){
        cancelTimer();
        final List<Lesson> ls = lessons.get(ch.id);
        final Lesson l = ls.get(idx);
        prefs.edit().putBoolean("read_"+ch.id+"_"+idx,true).apply();

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);

        TextView crumb = bold(ch.title + "  \u2022  Lesson " + (idx+1) + " of " + ls.size(), 13, DIM);
        col.addView(crumb, margins(0, 4));
        col.addView(bold(l.title, 19, TEXT), margins(0, 2));
        TextView guide = text("Read or listen → see an example → try the quick check below", 12, GREEN);
        col.addView(guide, margins(0, 2));

        Button listen = new Button(this);
        listen.setText("\uD83D\uDD0A Listen to lesson"); listen.setAllCaps(false); listen.setTextColor(BLUE); listen.setTextSize(12);
        listen.setTypeface(null, Typeface.BOLD);
        listen.setBackground(bg(SOFT, 20));
        listen.setOnClickListener(v -> toggleSpeech(listen, "\uD83D\uDD0A Listen to lesson", lessonSpeechParts(l)));
        press(listen);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(0, dp(6), 0, 0);
        col.addView(listen, lp);

        for(String para : l.body.split("\n\n")){
            TextView t = text(para.trim(), 14, TEXT);
            t.setLineSpacing(0, 1.2f);
            col.addView(t, margins(0, 5));
        }
        if(!l.scenario.isEmpty()){
            LinearLayout scenario=new LinearLayout(this);
            scenario.setOrientation(LinearLayout.VERTICAL);
            scenario.setPadding(dp(14),dp(12),dp(14),dp(12));
            scenario.setBackground(bg(SOFT,14));
            scenario.addView(bold("Where you'd use it",13,ORANGE));
            scenario.addView(text(l.scenario,14,TEXT));
            col.addView(scenario,margins(0,6));
        }
        if(!l.example.isEmpty()){
            TextView cap = bold("Walkthrough: commands and sample output", 13, ORANGE);
            col.addView(cap, margins(0, 4));
            LinearLayout box = new LinearLayout(this);
            box.setBackground(bg(0xff0b1220, 14));
            box.setPadding(dp(12), dp(12), dp(12), dp(12));
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
            TextView et = mono(l.example, 12, 0xffc9d7ea);
            et.setLineSpacing(0, 1.1f);
            hsv.addView(et);
            box.addView(hsv);
            col.addView(box, margins(0, 4));
            TextView caution=text("Sample output differs by machine. Read before running; never try destructive disk, delete, firewall or account commands on real data.",12,DIM);
            col.addView(caution,margins(0,2));
            popIn(box, 160);
        }

        if(l.checkOptions!=null){
            LinearLayout checkCard=new LinearLayout(this);
            checkCard.setOrientation(LinearLayout.VERTICAL);
            checkCard.setPadding(dp(14),dp(14),dp(14),dp(14));
            checkCard.setBackground(bg(CARD,16));
            checkCard.addView(bold("Quick check  •  " + (idx+1) + "/" + ls.size(),14,GREEN));
            checkCard.addView(bold(l.checkQ,15,TEXT),margins(0,5));
            TextView feedback=text("Pick an answer to check your understanding.",13,DIM);
            Button[] answers=new Button[l.checkOptions.length];
            for(int i=0;i<answers.length;i++){
                final int choice=i;
                Button b=new Button(this);
                b.setAllCaps(false); b.setText(l.checkOptions[i]); b.setTextColor(TEXT); b.setTextSize(13);
                b.setBackground(bg(SOFT,13));
                LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);
                ap.setMargins(0,dp(5),0,0);
                checkCard.addView(b,ap); answers[i]=b;
                if(prefs.getBoolean("check_"+ch.id+"_"+idx,false)){
                    b.setEnabled(false);
                    if(choice==l.checkAnswer) b.setBackground(bg(GREEN,13));
                    feedback.setText("Already passed ✓ " + l.checkE);
                    feedback.setTextColor(GREEN);
                }
                b.setOnClickListener(v -> {
                    boolean ok=choice==l.checkAnswer;
                    feedback.setText((ok ? "You got it! ✓ " : "Try again. ") + l.checkE);
                    feedback.setTextColor(ok ? GREEN : ORANGE);
                    scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, checkCard.getBottom() - scroll.getHeight() + dp(42))));
                    if(ok){
                        prefs.edit().putBoolean("check_"+ch.id+"_"+idx,true)
                            .putBoolean("read_"+ch.id+"_"+idx,true).apply();
                        for(Button other:answers)other.setEnabled(false);
                        answers[choice].setBackground(bg(GREEN,13));
                    }
                });
            }
            checkCard.addView(feedback,margins(0,6));
            col.addView(checkCard,margins(0,8));
        }

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(14), 0, 0);
        if(idx > 0){
            Button prev = new Button(this);
            prev.setText("\u2190 Previous"); prev.setAllCaps(false); prev.setTextColor(TEXT); prev.setTextSize(13);
            prev.setBackground(bg(SOFT, 22));
            prev.setOnClickListener(v -> showLesson(ch, idx-1));
            press(prev);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1);
            p.rightMargin = dp(6);
            nav.addView(prev, p);
        }
        Button next = new Button(this);
        boolean last = idx == ls.size()-1;
        next.setText(last ? "Start Quiz \u25B6" : "Next lesson \u2192");
        next.setAllCaps(false); next.setTextColor(0xff101827); next.setTextSize(13);
        next.setTypeface(null, Typeface.BOLD);
        next.setBackground(bg(BLUE, 22));
        next.setOnClickListener(v -> { if(last) startChapterQuiz(ch); else showLesson(ch, idx+1); });
        press(next);
        nav.addView(next, new LinearLayout.LayoutParams(0, dp(46), 1));
        col.addView(nav);

        Button back = new Button(this);
        back.setText("Chapter overview"); back.setAllCaps(false); back.setTextColor(DIM); back.setTextSize(12);
        back.setBackground(bg(CARD, 22));
        back.setOnClickListener(v -> showChapter(ch));
        press(back);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(42));
        bp.setMargins(0, dp(8), 0, 0);
        col.addView(back, bp);
        switchScreen(scroll);
    }

    // ---------------- INTERVIEW QUESTIONS ----------------
    private void showInterviewTopic(final InterviewChapter ch){
        cancelTimer();
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(col);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable headBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff35254f, 0xff1c2940});
        headBg.setCornerRadius(dp(20));
        head.setBackground(headBg);
        head.setPadding(dp(18), dp(16), dp(18), dp(16));
        head.addView(text("💼", 28, TEXT));
        head.addView(bold(ch.title + " Interview Questions", 19, TEXT));
        int commands = commandCount(ch);
        TextView sub = text(ch.qs.size() + (ch.qs.size() == 1 ? " question" : " questions") + (commands > 0 ? "  •  " + commands + " command examples" : "") + "  •  tap a question to read its answer", 13, DIM);
        sub.setPadding(0, dp(4), 0, 0);
        head.addView(sub);
        Button playAll = new Button(this);
        playAll.setText("🔊 Play all topic"); playAll.setAllCaps(false); playAll.setTextColor(PURPLE); playAll.setTextSize(12);
        playAll.setTypeface(null, Typeface.BOLD);
        playAll.setBackground(bg(SOFT, 20));
        playAll.setOnClickListener(v -> toggleSpeech(playAll, "🔊 Play all topic", topicSpeechParts(ch)));
        press(playAll);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, dp(38));
        pp.setMargins(0, dp(10), 0, 0);
        head.addView(playAll, pp);
        col.addView(head, margins(0, 6));

        if(ch.qs.isEmpty()){
            col.addView(text("Interview questions for this topic are coming soon.", 13, DIM), margins(0, 6));
        }
        List<ReaderPage> pages = interviewPages(ch);
        for(int i=0;i<pages.size();i++){
            final int idx = i;
            col.addView(questionListRow(pages.get(i).label, pages.get(i).title, "Read answer →", PURPLE,
                    v -> showQaReader(ch.title + " Interview Questions", pages, idx, PURPLE, () -> showInterviewTopic(ch))), margins(0, 5));
        }
        if(!ch.commands.isEmpty()){
            col.addView(sectionHead("💻", "Command Reference", commands + " practical commands - each topic listed once, with one example per command.", PURPLE), margins(0, 8));
            for(final CommandGroup group : ch.commands){
                LinearLayout table = new LinearLayout(this);
                table.setOrientation(LinearLayout.VERTICAL);
                table.setBackground(bg(CARD, 16));
                table.setPadding(dp(14), dp(12), dp(14), dp(10));
                table.addView(bold(group.title, 15, TEXT));
                TextView count = text(group.items.size() + " commands", 11, DIM);
                count.setPadding(0, dp(2), 0, dp(8));
                table.addView(count);

                LinearLayout labels = new LinearLayout(this);
                labels.setOrientation(LinearLayout.HORIZONTAL);
                labels.setPadding(0, 0, 0, dp(4));
                labels.addView(bold("Command", 10, PURPLE), new LinearLayout.LayoutParams(0, -2, .85f));
                labels.addView(bold("Example", 10, PURPLE), new LinearLayout.LayoutParams(0, -2, 1.15f));
                labels.addView(bold("Use", 10, PURPLE), new LinearLayout.LayoutParams(0, -2, 1f));
                table.addView(labels);

                for(int r=0;r<group.items.size();r++){
                    CommandItem item = group.items.get(r);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.TOP);
                    row.setPadding(0, dp(5), 0, dp(5));
                    TextView c = mono(item.command, 10, PURPLE);
                    c.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    TextView e = mono(item.example, 10, TEXT);
                    TextView u = text(item.use, 10, DIM);
                    u.setLineSpacing(0, 1.08f);
                    row.addView(c, new LinearLayout.LayoutParams(0, -2, .85f));
                    LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, -2, 1.15f);
                    ep.leftMargin = dp(8); ep.rightMargin = dp(8);
                    row.addView(e, ep);
                    row.addView(u, new LinearLayout.LayoutParams(0, -2, 1f));
                    Button speak = new Button(this);
                    speak.setText("🔊"); speak.setAllCaps(false); speak.setTextColor(BLUE); speak.setTextSize(12);
                    speak.setBackground(bg(SOFT, 12));
                    speak.setMinWidth(0); speak.setMinimumWidth(0); speak.setPadding(0,0,0,0);
                    final List<String> commandSpeech = singleSpeech(commandSpeech(item));
                    speak.setOnClickListener(v -> toggleSpeech(speak, "🔊", commandSpeech));
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(36), dp(32));
                    sp.leftMargin = dp(6);
                    row.addView(speak, sp);
                    table.addView(row);
                    if(r < group.items.size()-1){
                        View divider = new View(this);
                        divider.setBackgroundColor(SOFT);
                        table.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                    }
                }
                col.addView(table, margins(0, 5));
            }
        }
        Button home = new Button(this);
        home.setText(interviewOpenedFromHandbook ? "Back to 15 Advanced Topics" : "Back to Interview Chapters");
        home.setAllCaps(false); home.setTextColor(TEXT); home.setTextSize(13);
        home.setBackground(bg(SOFT, 22));
        home.setOnClickListener(v -> { if(interviewOpenedFromHandbook) showAdvancedHandbook(); else showInterviewTopics(); });
        press(home);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(46));
        hp.setMargins(0, dp(10), 0, 0);
        col.addView(home, hp);
        switchScreen(scroll);
    }

    // ---------------- QUIZ STARTERS ----------------
    private void startChapterQuiz(Chapter ch){
        List<Q> pick = new ArrayList<>(ch.qs);
        Collections.shuffle(pick);
        if(pick.size() > 10) pick = pick.subList(0, 10);
        final Chapter c = ch;
        startQuiz(new ArrayList<>(pick), ch.title, mode, "best_" + ch.id, false, false, () -> startChapterQuiz(c));
    }
    private void startDaily(){
        List<Q> all = allQuestions();
        Collections.shuffle(all, new Random(today().hashCode()));
        List<Q> pick = new ArrayList<>(all.subList(0, Math.min(10, all.size())));
        startQuiz(pick, "Daily Challenge", mode, null, true, false, this::startDaily);
    }
    private void startRevise(){
        Set<String> wrong = wrongSet();
        List<Q> pick = new ArrayList<>();
        for(Chapter ch : chapters) for(Q q : ch.qs) if(wrong.contains(q.id())) pick.add(q);
        if(pick.isEmpty()){ showHome(); return; }
        Collections.shuffle(pick);
        if(pick.size() > 15) pick = pick.subList(0, 15);
        final List<Q> p = new ArrayList<>(pick);
        startQuiz(p, "Revise Mistakes", MODE_BEGINNER, null, false, true, () -> startQuiz(p, "Revise Mistakes", MODE_BEGINNER, null, false, true, null));
    }
    private List<Q> allQuestions(){
        List<Q> all = new ArrayList<>();
        for(Chapter ch : chapters) all.addAll(ch.qs);
        return all;
    }

    // ---------------- QUIZ ----------------
    private void startQuiz(List<Q> qs, String title, int m, String best, boolean isDaily, boolean isRevise, Runnable again){
        cancelTimer();
        quiz = qs; quizTitle = title; bestKey = best; daily = isDaily; revise = isRevise;
        qi = 0; correctCount = 0; restart = again;
        timerSecs = m == MODE_INTERMEDIATE ? 30 : 0;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(4), dp(16), dp(16));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        counterText = bold("", 14, DIM);
        topRow.addView(counterText, new LinearLayout.LayoutParams(0, -2, 1));
        timerText = bold("", 16, ORANGE);
        topRow.addView(timerText);
        col.addView(topRow, margins(0, 4));

        quizBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        quizBar.setMax(qs.size());
        col.addView(quizBar, margins(0, 4));

        LinearLayout qCard = new LinearLayout(this);
        qCard.setOrientation(LinearLayout.VERTICAL);
        qCard.setBackground(bg(CARD, 18));
        qCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        questionText = bold("", 17, TEXT);
        questionText.setLineSpacing(0, 1.15f);
        qCard.addView(questionText);
        quizSpeakBtn = new Button(this);
        quizSpeakBtn.setText("\uD83D\uDD0A Speak question"); quizSpeakBtn.setAllCaps(false); quizSpeakBtn.setTextColor(BLUE); quizSpeakBtn.setTextSize(12);
        quizSpeakBtn.setTypeface(null, Typeface.BOLD);
        quizSpeakBtn.setBackground(bg(SOFT, 20));
        quizSpeakBtn.setOnClickListener(v -> toggleSpeech(quizSpeakBtn, "\uD83D\uDD0A Speak question", quizSpeechParts()));
        press(quizSpeakBtn);
        LinearLayout.LayoutParams qsp = new LinearLayout.LayoutParams(-2, dp(38));
        qsp.setMargins(0, dp(10), 0, 0);
        qCard.addView(quizSpeakBtn, qsp);
        col.addView(qCard, margins(0, 8));

        optionsBox = new LinearLayout(this);
        optionsBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(optionsBox, margins(0, 2));

        explainCard = new LinearLayout(this);
        ((LinearLayout)explainCard).setOrientation(LinearLayout.VERTICAL);
        explainCard.setBackground(bg(SOFT, 18));
        explainCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        explainTitle = bold("", 14, GREEN);
        explainText = text("", 13, TEXT);
        explainText.setLineSpacing(0, 1.1f);
        ((LinearLayout)explainCard).addView(explainTitle);
        ((LinearLayout)explainCard).addView(explainText);
        explainCard.setVisibility(View.GONE);
        col.addView(explainCard, margins(0, 6));

        nextBtn = new Button(this);
        nextBtn.setAllCaps(false); nextBtn.setTextColor(0xff101827); nextBtn.setTextSize(15);
        nextBtn.setTypeface(null, Typeface.BOLD);
        nextBtn.setBackground(bg(BLUE, 24));
        nextBtn.setVisibility(View.GONE);
        nextBtn.setOnClickListener(v -> next());
        press(nextBtn);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(52));
        np.setMargins(0, dp(6), 0, 0);
        col.addView(nextBtn, np);

        ScrollView quizScroll=new ScrollView(this);
        quizScroll.setFillViewport(true);
        quizScroll.addView(col);
        switchScreen(quizScroll);
        renderQuestion();
    }

    private void renderQuestion(){
        stopSpeech();
        final Q q = quiz.get(qi);
        counterText.setText("Question " + (qi+1) + " of " + quiz.size() + "  \u2022  " + quizTitle);
        quizBar.setProgress(qi);
        questionText.setText(q.q);
        optionsBox.removeAllViews();
        explainCard.setVisibility(View.GONE);
        nextBtn.setVisibility(View.GONE);
        if(quizSpeakBtn != null) quizSpeakBtn.setText("\uD83D\uDD0A Speak question");
        for(int i=0;i<q.o.length;i++){
            final int idx = i;
            Button b = new Button(this);
            b.setText(q.o[i]);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setTextColor(TEXT);
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            b.setPadding(dp(14), 0, dp(14), 0);
            b.setBackground(bg(CARD, 16));
            b.setOnClickListener(v -> answer(idx, b));
            press(b);
            b.setMinHeight(dp(56));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.setMargins(0, dp(5), 0, dp(5));
            optionsBox.addView(b, p);
            popIn(b, 60 * i);
        }
        startTimerIfNeeded();
    }

    private List<String> quizSpeechParts(){
        List<String> parts = new ArrayList<>();
        if(quiz == null || qi < 0 || qi >= quiz.size()) return parts;
        Q q = quiz.get(qi);
        parts.add("Question. " + q.q);
        if(explainCard != null && explainCard.getVisibility() == View.VISIBLE){
            parts.add("Answer. " + q.o[q.a]);
        }
        return parts;
    }

    private void startTimerIfNeeded(){
        cancelTimer();
        if(timerSecs <= 0){ timerText.setText(""); return; }
        timerText.setText(timerSecs + "s");
        timer = new CountDownTimer(timerSecs * 1000L, 1000){
            public void onTick(long ms){ timerText.setText((ms/1000 + 1) + "s"); }
            public void onFinish(){ timerText.setText("0s"); answer(-1, null); }
        }.start();
    }
    private void cancelTimer(){ if(timer != null){ timer.cancel(); timer = null; } }

    // ---------------- OFFLINE SPEECH ----------------
    private void initSpeech(){
        try {
            tts = new TextToSpeech(this, status -> {
                ttsReady = status == TextToSpeech.SUCCESS;
                if(ttsReady){
                    int lang = tts.setLanguage(Locale.US);
                    ttsReady = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED;
                    if(ttsReady) tts.setSpeechRate(0.95f);
                }
            });
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String utteranceId){ }
                @Override public void onDone(String utteranceId){ advanceSpeech(utteranceId); }
                @Override public void onError(String utteranceId){ advanceSpeech(utteranceId); }
                @Override public void onError(String utteranceId, int errorCode){ advanceSpeech(utteranceId); }
            });
        } catch(Exception e){
            tts = null;
            ttsReady = false;
        }
    }

    private String cleanSpeech(String s){
        if(s == null) return "";
        return s.replace("\u2192", " then ")
                .replace("\u2022", " ")
                .replace("\u2713", " correct ")
                .replace("\u274C", " ")
                .replace("\uD83D\uDD0A", " ")
                .replace("\u23F8", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void toggleSpeech(final Button button, final String label, final List<String> parts){
        if(button == null) return;
        if(speechActive && speakingButton == button){
            stopSpeech();
            return;
        }
        speakParts(button, label, parts);
    }

    private void speakParts(final Button button, final String label, final List<String> parts){
        stopSpeech();
        if(button == null || parts == null || parts.isEmpty() || tts == null || !ttsReady){
            if(button != null) button.setText(label);
            return;
        }
        speakingButton = button;
        speakingLabel = label;
        speechQueue.clear();
        for(String part : parts){
            String clean = cleanSpeech(part);
            if(!clean.isEmpty()) speechQueue.add(clean);
        }
        if(speechQueue.isEmpty()){
            speakingButton = null;
            button.setText(label);
            return;
        }
        speechIndex = 0;
        speechActive = true;
        button.setText(label.startsWith("\uD83D\uDD0A Play all") ? "\u23F8 Stop topic" : (label.equals("\uD83D\uDD0A") ? "\u23F8" : "\u23F8 Stop"));
        speakNext();
    }

    private void speakNext(){
        if(tts == null || !speechActive || speechIndex < 0 || speechIndex >= speechQueue.size()){
            finishSpeech();
            return;
        }
        tts.speak(speechQueue.get(speechIndex), TextToSpeech.QUEUE_FLUSH, null, "linux-app-" + (++utteranceSeq));
    }

    private void advanceSpeech(String utteranceId){
        if(utteranceId == null || !utteranceId.startsWith("linux-app-")) return;
        runOnUiThread(() -> {
            if(!speechActive) return;
            speechIndex++;
            if(speechIndex < speechQueue.size()) speakNext();
            else finishSpeech();
        });
    }

    private void finishSpeech(){
        final Button old = speakingButton;
        final String label = speakingLabel;
        speakingButton = null;
        speakingLabel = "\uD83D\uDD0A Speak";
        speechQueue.clear();
        speechIndex = -1;
        speechActive = false;
        if(old != null) runOnUiThread(() -> old.setText(label));
    }

    private void stopSpeech(){
        if(tts != null){
            try { tts.stop(); } catch(Exception ignored){ }
        }
        finishSpeech();
    }

    private List<String> singleSpeech(String text){
        List<String> parts = new ArrayList<>();
        parts.add(text);
        return parts;
    }

    private List<String> lessonSpeechParts(Lesson l){
        List<String> parts = new ArrayList<>();
        parts.add(l.title);
        for(String para : l.body.split("\n\n")) parts.add(para.trim());
        if(!l.scenario.isEmpty()) parts.add("Where you would use it. " + l.scenario);
        if(!l.example.isEmpty()) parts.add("Walkthrough example. " + l.example);
        if(l.checkOptions != null){
            parts.add("Quick check. " + l.checkQ);
            for(int i=0;i<l.checkOptions.length;i++) parts.add("Option " + (i+1) + ". " + l.checkOptions[i]);
            parts.add("Correct answer. " + l.checkOptions[l.checkAnswer] + ". " + l.checkE);
        }
        return parts;
    }

    private String commandSpeech(CommandItem item){
        return "Command " + item.command + ". Use: " + item.use + ".";
    }

    private List<String> topicSpeechParts(InterviewChapter ch){
        List<String> parts = new ArrayList<>();
        for(int i=0;i<ch.qs.size();i++){
            InterviewQ item = ch.qs.get(i);
            parts.add("Question " + (i+1) + ". " + item.q + " Answer. " + answerSpeechBody(item.a));
        }
        return parts;
    }

    private void answer(int chosen, Button chosenBtn){
        cancelTimer();
        final Q q = quiz.get(qi);
        Set<String> wrong = wrongSet();
        for(int i=0;i<optionsBox.getChildCount();i++){
            View child = optionsBox.getChildAt(i);
            child.setEnabled(false);
            if(i == q.a){
                child.setBackground(bg(GREEN, 16));
                child.animate().scaleX(1.03f).scaleY(1.03f).setDuration(140).withEndAction(() ->
                        child.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
            } else if(i == chosen){
                child.setBackground(bg(RED, 16));
            } else {
                child.setAlpha(0.55f);
            }
        }
        if(chosen == q.a){
            correctCount++;
            wrong.remove(q.id());
            explainTitle.setText("Correct \u2713");
            explainTitle.setTextColor(GREEN);
        } else {
            wrong.add(q.id());
            explainTitle.setText(chosen < 0 ? "Time up!" : "Not quite");
            explainTitle.setTextColor(ORANGE);
        }
        saveWrong(wrong);
        explainText.setText(q.e);
        explainCard.setVisibility(View.VISIBLE);
        explainCard.setAlpha(0f);
        explainCard.setTranslationY(dp(20));
        explainCard.animate().alpha(1f).translationY(0).setDuration(260).start();
        nextBtn.setText(qi == quiz.size()-1 ? "See results" : "Next");
        nextBtn.setVisibility(View.VISIBLE);
        nextBtn.setAlpha(0f);
        nextBtn.animate().alpha(1f).setStartDelay(150).setDuration(200).start();
    }

    private void next(){
        qi++;
        if(qi >= quiz.size()) finishQuiz();
        else renderQuestion();
    }

    private void finishQuiz(){
        cancelTimer();
        int total = quiz.size();
        final int percent = total == 0 ? 0 : correctCount * 100 / total;
        if(daily){
            String last = prefs.getString("lastDaily", "");
            if(!today().equals(last)){
                int s = yesterday().equals(last) ? streak() + 1 : 1;
                prefs.edit().putInt("streak", s).putString("lastDaily", today()).apply();
            }
        }
        if(bestKey != null && percent > prefs.getInt(bestKey, -1))
            prefs.edit().putInt(bestKey, percent).apply();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(40), dp(24), dp(24));

        TextView done = text("\uD83C\uDFC6", 46, TEXT);
        col.addView(done);
        final TextView big = bold("0%", 52, percent >= 70 ? GREEN : (percent >= 50 ? ORANGE : RED));
        col.addView(big);
        TextView frac = bold(correctCount + " / " + total + " correct", 17, TEXT);
        col.addView(frac);
        TextView msg = text(percent >= 90 ? "Outstanding! Interview ready \uD83D\uDE80"
                : percent >= 70 ? "Great job! Almost there \uD83D\uDCAA"
                : percent >= 50 ? "Good progress - keep practicing \uD83D\uDCDA"
                : "Don't give up - revise and retry \uD83D\uDCA1", 14, DIM);
        if(percent >= 90) msg.setText("Strong result! Keep practicing unfamiliar topics before interviews.");
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, dp(6), 0, dp(6));
        col.addView(msg);
        if(daily){
            TextView st = bold("\uD83D\uDD25 Streak: " + streak() + " day" + (streak()==1?"":"s"), 15, ORANGE);
            st.setPadding(0, dp(4), 0, dp(4));
            col.addView(st);
        }
        if(revise && wrongSet().isEmpty()){
            TextView clean = bold("Mistake list cleared!", 15, GREEN);
            clean.setPadding(0, dp(4), 0, dp(4));
            col.addView(clean);
        }

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, dp(18), 0, 0);
        if(restart != null){
            Button retry = new Button(this);
            retry.setText("Retry"); retry.setAllCaps(false); retry.setTextColor(TEXT); retry.setTextSize(14);
            retry.setBackground(bg(SOFT, 22));
            retry.setOnClickListener(v -> restart.run());
            press(retry);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1);
            p.rightMargin = dp(6);
            btns.addView(retry, p);
        }
        Button home = new Button(this);
        home.setText("Back to Home"); home.setAllCaps(false); home.setTextColor(0xff101827); home.setTextSize(14);
        home.setTypeface(null, Typeface.BOLD);
        home.setBackground(bg(BLUE, 22));
        home.setOnClickListener(v -> showHome());
        press(home);
        btns.addView(home, new LinearLayout.LayoutParams(0, dp(50), 1));
        col.addView(btns, new LinearLayout.LayoutParams(-1, -2));

        switchScreen(col);
        big.setScaleX(0.6f); big.setScaleY(0.6f);
        big.animate().scaleX(1f).scaleY(1f).setDuration(500).setInterpolator(new OvershootInterpolator()).start();
        ValueAnimator anim = ValueAnimator.ofInt(0, percent);
        anim.setDuration(900);
        anim.addUpdateListener(a -> big.setText(a.getAnimatedValue() + "%"));
        anim.start();
    }

    @Override public void onBackPressed(){
        cancelTimer();
        showHome();
    }
    @Override protected void onDestroy(){
        cancelTimer();
        stopSpeech();
        if(tts != null){
            try { tts.shutdown(); } catch(Exception ignored){ }
            tts = null;
        }
        super.onDestroy();
    }
}
