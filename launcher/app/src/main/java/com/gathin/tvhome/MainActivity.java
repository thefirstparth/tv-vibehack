package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvshared.Fonts;
import com.gathin.tvshared.HomeLines;
import com.gathin.tvshared.HorizonBackground;
import com.gathin.tvshared.SunLine;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private TextView greeting;
    private TextView btnWifi, btnToday;
    /** The Today app (its own APK): the moon pill opens it, and is hidden while it is not installed. */
    static final String TODAY_APP = "com.gathin.tvtoday";
    /** Back from the Today app opened by the moon: focus goes to the moon, not the last tile. */
    private boolean focusMoon;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int lastFocused = 0;
    /** Dock slot to land on when pressing Down from the top bar. */
    private int lastDockFocused = 0;
    /** Set in onResume: once the window has focus again, put focus back on the last tile. */
    private boolean refocusPending;
    private float density;
    /** Sports and weather above the dock: built after the first frame, and never when no feed address is set. */
    private HomeLines homeLines;
    /** Where the lines go (top bar to dock band), known from the first layout. */
    private int linesTop = -1, linesBottom = -1;
    /** Set once the first frame is on screen: the work kept off the startup path may begin. */
    private boolean firstFrameDone, resumed;
    /** The Horizon image is drawn here, never on the main thread. */
    private final java.util.concurrent.ExecutorService painter = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** The window's background, by time of day; drawn once per look. */
    private final HorizonBackground horizon = new HorizonBackground();
    private DockDecoration dock;
    private WifiBars wifiBars;
    private String wifiName = "";
    private boolean wifiIconOnly;
    /** Names for inputs (config input_names), by the port's label. */
    private final java.util.Map<String, String> inputNames = new java.util.HashMap<>();
    private static final java.util.regex.Pattern CONSOLE = java.util.regex.Pattern.compile("xbox|playstation|ps[45]|switch|nintendo", java.util.regex.Pattern.CASE_INSENSITIVE);
    /** Personal days (launcher/local.properties, tv.day.N): a greeting and their own light. */
    private java.util.List<HorizonBackground.Day> days;
    /** The same days' greetings, for the line above the clock. */
    private java.util.List<SunLine.Greeting> greetings;
    /**
     * For screenshots only, from the launch intent; any plain Home clears them: "at" (yyyy-MM-ddTHH:mm, the light's
     * time and date), "mood", "sky" (clear, rain, storm), "match" (a colour, #RRGGBB).
     */
    private Calendar fakeAt;
    private HorizonBackground.Mood fakeMood;
    private HorizonBackground.Sky fakeSky;
    private Integer fakeMatch;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            if (homeLines != null) homeLines.tick();
            updateHorizon();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Fonts.apply(getWindow().getDecorView());
        // startup ends at the first frame; the lines and the Horizon image are made just after it
        getWindow().getDecorView().getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
                if (firstFrameDone) return;
                firstFrameDone = true;
                handler.post(MainActivity.this::afterFirstFrame);
            }
        });
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();
        for (String pair : getResources().getStringArray(R.array.input_names)) {
            String[] kv = pair.split("\\|");
            if (kv.length == 2) inputNames.put(kv[0].trim(), kv[1].trim());
        }

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        dock = new DockDecoration(COLUMNS, density);
        grid.addItemDecoration(dock);
        HorizonBackground.locate(getResources());
        String dayText = getString(com.gathin.tvshared.R.string.tv_days);
        days = HorizonBackground.Day.parse(dayText);
        greetings = SunLine.greetings(dayText);
        readFakes(getIntent());
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row to the bottom of the screen; the grid hides below until you press Down.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRow = (int) ((145 + 6 + 12) * density);   // item height plus DockDecoration offsets
            int want = Math.max(0, h - dockRow - (int) (30 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
            // the sports and weather lines: centred between the top bar and the dock band's top edge
            Rect top = new Rect(0, 0, 0, findViewById(R.id.leftBlock).getHeight()), g = new Rect(0, 0, 0, 0);
            root.offsetDescendantRectToMyCoords(findViewById(R.id.leftBlock), top);
            root.offsetDescendantRectToMyCoords(grid, g);
            linesTop = top.bottom;
            linesBottom = g.top + want;
            if (homeLines != null) homeLines.frame(linesTop, linesBottom);
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        btnToday = findViewById(R.id.btnToday);
        btnInputs.setOnClickListener(v -> showInputs());
        // the moon: the Today app's normal launch; back on the moon when it closes
        btnToday.setOnClickListener(v -> {
            Intent i = todayIntent();
            if (i == null) return;
            focusMoon = true;
            open(i);
        });
        btnToday.setOnFocusChangeListener((v, focused) -> showTodayPill(focused));
        showTodayPill(false);
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        // the bars as the pill's icon; the name comes and goes with the pill's focus
        WifiBars bars = wifiBars = new WifiBars(density);
        bars.setBounds(0, 0, bars.getIntrinsicWidth(), bars.getIntrinsicHeight());
        btnWifi.setCompoundDrawablesRelative(bars, null, null, null);
        btnWifi.setOnFocusChangeListener((v, focused) -> showWifi(focused));
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction. Down always goes to the dock.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusTile(lastDockFocused);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnToday) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnInputs) {
                if (btnToday.getVisibility() == View.VISIBLE) btnToday.requestFocus();
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnToday.setOnKeyListener(pillKeys);
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        adapter.reload();
        grid.scheduleLayoutAnimation();
        updateGreeting();
        updateWifiLabel();
        btnToday.setVisibility(todayIntent() != null ? View.VISIBLE : View.GONE);
        if (btnToday.getVisibility() != View.VISIBLE) focusMoon = false;
        resumed = true;
        updateHorizon();
        if (homeLines != null) {
            homeLines.tick();      // the times worded for now, from the copy already here
            homeLines.refresh();   // and one request for a fresh copy
        }
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Return to the tile we left from, or the first one. The window restores its old focus (often a
        // top-bar pill) when it regains focus, so this runs again from onWindowFocusChanged.
        refocusPending = true;
        grid.post(this::refocus);
    }

    /** Back on the last tile, or on the moon pill when the Today app it opened just closed. */
    private void refocus() {
        if (focusMoon) btnToday.requestFocus();
        else focusTile(lastFocused);
    }

    private Intent todayIntent() {
        return getPackageManager().getLeanbackLaunchIntentForPackage(TODAY_APP);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readFakes(intent);
    }

    /** The Horizon background for the time of day: replaced only when the look changes, with no animation. */
    /** Just after the first frame: the sports and weather lines (when a feed is set) and the Horizon image. */
    private void afterFirstFrame() {
        if (!getString(com.gathin.tvshared.R.string.tv_feed_url).trim().isEmpty() && homeLines == null) {
            ((android.view.ViewStub) findViewById(R.id.linesStub)).inflate();
            Fonts.apply(findViewById(R.id.lines));
            homeLines = new HomeLines(this, density);
            homeLines.onChange(this::updateHorizon);   // the light follows Bengaluru's weather word and a live match
            if (linesTop >= 0) homeLines.frame(linesTop, linesBottom);
            if (resumed) {
                homeLines.tick();
                homeLines.refresh();
            }
        }
        updateHorizon();
    }

    private void readFakes(Intent i) {
        fakeAt = null;
        try {
            String at = i.getStringExtra("at");
            if (at != null) {
                fakeAt = HorizonBackground.now();
                fakeAt.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.ENGLISH).parse(at));
            }
        } catch (Exception ignored) {
            fakeAt = null;
        }
        fakeMood = HorizonBackground.Mood.named(i.getStringExtra("mood"));
        String sky = i.getStringExtra("sky");
        fakeSky = sky == null ? null : "rain".equalsIgnoreCase(sky) ? HorizonBackground.Sky.RAIN : "storm".equalsIgnoreCase(sky) ? HorizonBackground.Sky.STORM : HorizonBackground.Sky.CLEAR;
        String match = i.getStringExtra("match");
        try {
            fakeMatch = match == null ? null : android.graphics.Color.parseColor(match);
        } catch (IllegalArgumentException e) {
            fakeMatch = null;
        }
    }

    private Calendar lightTime() {
        return fakeAt != null ? fakeAt : HorizonBackground.now();
    }

    /**
     * The Horizon image for now: the sun's mood, the season, Bengaluru's sky and a live match from the lines, and any
     * personal day. A new image only when one of those changes; drawn off the main thread and swapped in whole.
     */
    void updateHorizon() {
        if (!firstFrameDone) return;
        HorizonBackground.Sky sky = fakeSky != null ? fakeSky : homeLines != null ? homeLines.sky() : HorizonBackground.Sky.CLEAR;
        Integer match = fakeMatch != null ? fakeMatch : homeLines != null ? homeLines.liveAccent() : null;
        HorizonBackground.State st = HorizonBackground.stateAt(lightTime(), sky, match, days);
        if (fakeMood != null) st = new HorizonBackground.State(fakeMood, st.season, st.sky, st.matchAccent, st.day);
        if (!horizon.claim(st)) return;
        final HorizonBackground.State state = st;
        android.content.res.Resources res = getResources();
        painter.execute(() -> {
            android.graphics.drawable.BitmapDrawable image = HorizonBackground.draw(res, state);
            handler.post(() -> {
                getWindow().setBackgroundDrawable(image);
                dock.setTopEdge(HorizonBackground.edge(state));
                grid.invalidate();
            });
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && refocusPending) {
            refocusPending = false;
            refocus();
            focusMoon = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(minuteTick);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        if (position < COLUMNS) lastDockFocused = position;
        if (homeLines != null) homeLines.setAway(position >= COLUMNS);
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(16f * density * AppAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
        } else {
            // Scroll just enough so the whole glass (not only the tile) is visible.
            Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false;          // dock row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    /**
     * Above the clock: Horizon's mood and the sun's next turn ("MIDDAY · SUNSET 5:59 PM"), or a personal day's greeting
     * with the sun time; hidden while the clock is not set. On the minute tick and when Home appears.
     */
    private void updateGreeting() {
        Calendar now = lightTime();
        String line = SunLine.text(now, HorizonBackground.LAT, HorizonBackground.LON, greetings);
        greeting.setText(line == null ? "" : line);
        greeting.setVisibility(line == null ? View.INVISIBLE : View.VISIBLE);
    }

    /**
     * The Wi-Fi pill: the signal's bars only, and the network's name only while the pill is focused. Offline and
     * Ethernet are said in words at all times (no bars to show).
     */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        boolean wifi = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                wifi = true;
                if (info != null) wifiBars.setBars(WifiManager.calculateSignalLevel(info.getRssi(), 4));
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        wifiName = text;
        wifiIconOnly = wifi;
        showWifi(btnWifi.isFocused());
    }

    /** The moon pill: icon only and square, with "Today" beside the moon while focused (as the Wi-Fi pill). */
    private void showTodayPill(boolean focused) {
        String want = focused ? getString(R.string.today) : "";
        if (!want.contentEquals(btnToday.getText())) btnToday.setText(want);
        int side = (int) ((focused ? 16 : 11) * density), end = (int) ((focused ? 20 : 11) * density);
        btnToday.setCompoundDrawablePadding(focused ? (int) (9 * density) : 0);
        btnToday.setPaddingRelative(side, btnToday.getPaddingTop(), end, btnToday.getPaddingBottom());
    }

    private void showWifi(boolean focused) {
        boolean iconOnly = wifiIconOnly && !focused;
        String want = iconOnly ? "" : wifiName;
        if (!want.contentEquals(btnWifi.getText())) btnWifi.setText(want);
        // an icon-only pill is as wide as it is tall: no gap for the missing text
        int side = (int) ((iconOnly ? 11 : 16) * density), end = (int) ((iconOnly ? 11 : 20) * density);
        btnWifi.setCompoundDrawablePadding(iconOnly ? 0 : (int) (9 * density));
        btnWifi.setPaddingRelative(side, btnWifi.getPaddingTop(), end, btnWifi.getPaddingBottom());
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact, hanging right below the pill; sized to its rows once they are added.
        final PopupWindow popup = new PopupWindow(panel, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> ports = new ArrayList<>();
            // Devices found over HDMI-CEC are separate inputs under their port; show them as the
            // port's subtitle instead of extra rows.
            java.util.Map<String, String> deviceOnPort = new java.util.HashMap<>();
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput()) continue;
                if (info.getParentId() == null) {
                    ports.add(info);
                } else if (!deviceOnPort.containsKey(info.getParentId())) {
                    String name = cleanLabel(info.loadLabel(this));
                    if (!name.isEmpty()) deviceOnPort.put(info.getParentId(), name);
                }
            }
            // HDMI ports first in port order (HW4 < HW5 < HW6), then AV and the rest.
            Collections.sort(ports, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                if (a.getId().length() != b.getId().length()) return a.getId().length() - b.getId().length();
                return a.getId().compareTo(b.getId());
            });
            int hdmiNumber = 0;
            for (TvInputInfo info : ports) {
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi) hdmiNumber++;
                String label = cleanLabel(info.loadLabel(this));
                if (label.isEmpty()) label = hdmi ? "HDMI " + hdmiNumber : getString(R.string.av);
                final String id = info.getId();
                String device = deviceOnPort.get(id);
                int state = tim.getInputState(id);
                if (device != null && state != TvInputManager.INPUT_STATE_DISCONNECTED) {
                    state = TvInputManager.INPUT_STATE_CONNECTED;
                }
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                // a port with its own name (config input_names: "HDMI 2|Xbox") reads as that, the port beside it
                String named = inputNames.get(label);
                int icon = named != null && CONSOLE.matcher(named).find() ? R.drawable.ic_game : hdmi ? R.drawable.ic_input : R.drawable.ic_av;
                addInputRow(rows, popup, icon, named != null ? named : label, named != null ? label : null, device, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), null, null, -1, () -> open(liveTv));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }
        // At least as wide as the pill, at most wide enough for a long device name.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(anchor.getWidth(), Math.min(panel.getMeasuredWidth(), (int) (320 * density))));

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    /** Input labels can be null or only whitespace/invisible characters; treat those as missing. */
    private static String cleanLabel(CharSequence l) {
        return l == null ? "" : l.toString().replaceAll("[\\p{Z}\\p{C}]+", " ").trim();
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, String port, String device,
                             int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        Fonts.apply(row);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown. A device name found over HDMI-CEC replaces "Connected".
        if (port != null) {
            // a named port: the port's own label on the right, with the dot when something is on
            status.setText(port);
            dot.setVisibility(state == TvInputManager.INPUT_STATE_CONNECTED ? View.VISIBLE : View.GONE);
        } else if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(device != null ? device : getString(R.string.connected));
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
