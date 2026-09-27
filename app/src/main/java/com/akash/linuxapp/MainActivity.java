package com.akash.linuxapp;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
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
    private static final int MODE_BEGINNER=0, MODE_INTERMEDIATE=1, MODE_INTERVIEW=2;

    static class Q {
        String q, e, chapter; String[] o; int a, idx;
        String id(){ return chapter + ":" + idx; }
    }
    static class Chapter {
        String id, title, desc; List<Q> qs = new ArrayList<>();
    }
    static class Lesson {
        String title, body, diagram, example, scenario, checkQ, checkE; String[] checkOptions; int checkAnswer;
    }

    private FrameLayout container;
    private TextView streakChip;
    private SharedPreferences prefs;
    private final List<Chapter> chapters = new ArrayList<>();
    private final java.util.Map<String, List<Lesson>> lessons = new java.util.HashMap<>();
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
    private Button nextBtn;

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
        container.removeAllViews();
        container.addView(v);
        v.setAlpha(0f); v.setTranslationY(dp(26));
        v.animate().alpha(1f).translationY(0).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        prefs = getSharedPreferences("linuxapp", 0);
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
                    l.diagram = lj.optString("diagram", "");
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

    // ---------------- HOME ----------------
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
        hero.setBackground(bg(CARD, 20));
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView emoji = text("\uD83D\uDC27", 40, TEXT);
        hero.addView(emoji);
        hero.addView(bold("Master Linux, one quiz at a time", 20, TEXT));
        TextView sub = text("300+ chapter-wise questions, from history to systemd. Fully offline.", 13, DIM);
        sub.setPadding(0, dp(4), 0, 0);
        hero.addView(sub);
        int passed=totalPassedChecks();
        TextView guide=text(passed==0 ? "Start here: open a chapter, read a lesson, then answer its quick check. No Linux machine needed." :
            "Quick checks: " + passed + "/48 passed. Next, open any chapter to continue.", 13, GREEN);
        guide.setPadding(0, dp(10), 0, 0);
        hero.addView(guide);
        col.addView(hero, margins(0, 8));

        LinearLayout modesRow = new LinearLayout(this);
        modesRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Beginner", "Intermediate", "Interview"};
        for(int i=0;i<3;i++){
            final int m = i;
            Button b = new Button(this);
            b.setText(names[i]); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(TEXT);
            b.setBackground(bg(mode==m ? BLUE : SOFT, 22));
            b.setOnClickListener(v -> { mode = m; showHome(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
            p.setMargins(dp(3), 0, dp(3), 0);
            modesRow.addView(b, p);
            press(b);
        }
        TextView modeHelp=text("Beginner: no timer  •  Intermediate: 30s  •  Interview: 20s",12,DIM);
        col.addView(modeHelp, margins(0, 2));
        col.addView(modesRow, margins(0, 8));

        col.addView(actionCard("\u26A1 Daily Challenge", "10 questions - new set every day", BLUE,
                prefs.getString("lastDaily", "").equals(today()) ? "Done today \u2713" : "Start",
                v -> startDaily()), margins(0, 6));
        col.addView(actionCard("\uD83C\uDFAF Interview Mode", "15 mixed questions - 20s each, real pressure", PURPLE, "Start",
                v -> startInterview()), margins(0, 6));
        int wrong = wrongSet().size();
        if(wrong > 0)
            col.addView(actionCard("\uD83D\uDD01 Revise Mistakes", wrong + " question" + (wrong==1?"":"s") + " you got wrong", ORANGE, "Revise",
                    v -> startRevise()), margins(0, 6));

        TextView head = bold("Chapters", 16, TEXT);
        head.setPadding(0, dp(12), 0, dp(2));
        col.addView(head);
        int delay = 0;
        for(final Chapter ch : chapters){
            col.addView(chapterCard(ch, delay), margins(0, 6));
            delay += 60;
        }
        switchScreen(scroll);
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
        home.setText("Back to Home"); home.setAllCaps(false); home.setTextColor(TEXT); home.setTextSize(13);
        home.setBackground(bg(SOFT, 22));
        home.setOnClickListener(v -> showHome());
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
        TextView guide = text("Read → see an example → try the quick check below", 12, GREEN);
        col.addView(guide, margins(0, 2));

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
        if(!l.diagram.isEmpty()){
            LinearLayout box = new LinearLayout(this);
            box.setBackground(bg(0xff0b1220, 14));
            box.setPadding(dp(12), dp(12), dp(12), dp(12));
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
            TextView dt = mono(l.diagram, 12, GREEN);
            dt.setLineSpacing(0, 1.05f);
            hsv.addView(dt);
            box.addView(hsv);
            col.addView(box, margins(0, 6));
            popIn(box, 120);
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

    // ---------------- QUIZ STARTERS ----------------
    private void startChapterQuiz(Chapter ch){
        List<Q> pick = new ArrayList<>(ch.qs);
        Collections.shuffle(pick);
        if(pick.size() > 10) pick = pick.subList(0, 10);
        final Chapter c = ch;
        startQuiz(new ArrayList<>(pick), ch.title, mode, "best_" + ch.id, false, false, () -> startChapterQuiz(c));
    }
    private void startInterview(){
        List<Q> all = allQuestions();
        Collections.shuffle(all);
        List<Q> pick = new ArrayList<>(all.subList(0, Math.min(15, all.size())));
        startQuiz(pick, "Interview Mode", MODE_INTERVIEW, null, false, false, this::startInterview);
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
        timerSecs = m == MODE_INTERMEDIATE ? 30 : (m == MODE_INTERVIEW ? 20 : 0);

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
        final Q q = quiz.get(qi);
        counterText.setText("Question " + (qi+1) + " of " + quiz.size() + "  \u2022  " + quizTitle);
        quizBar.setProgress(qi);
        questionText.setText(q.q);
        optionsBox.removeAllViews();
        explainCard.setVisibility(View.GONE);
        nextBtn.setVisibility(View.GONE);
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
        super.onDestroy();
    }
}
