package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.owpengram.OwpengramServer;
import org.telegram.owpengram.OwpengramServers;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerSelectFragment extends BaseFragment {

    private static final int TYPE_HEADER  = 0;
    private static final int TYPE_SERVER  = 1;
    private static final int TYPE_ADD     = 2;
    private static final int TYPE_SPACER  = 3;

    private static final int[] AVATAR_COLORS = {
        0xFFE53935, 0xFFE64A19, 0xFF43A047,
        0xFF00838D, 0xFF1976D2, 0xFF7B1FA2, 0xFFF57F17
    };

    private RecyclerListView listView;
    private ListAdapter adapter;

    private final List<OwpengramServer> servers = new ArrayList<>();
    private String selectedServerId;

    private final ExecutorService pingPool = Executors.newCachedThreadPool();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final HashMap<String, Integer> pingCache = new HashMap<>();

    // --- Lifecycle ---

    @Override
    public boolean onFragmentCreate() {
        reloadServers();
        selectedServerId = OwpengramServers.getServerIdForAccount(currentAccount);
        if (selectedServerId == null) {
            selectedServerId = OwpengramServers.ID_OWPENGRAM;
        }
        pingAll();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle("Server Selection");
        actionBar.setSubtitle("Choose a server to continue");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frame = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setOnItemClickListener((view, position) -> onRowClick(position));
        listView.setOnItemLongClickListener((view, position) -> {
            OwpengramServer s = serverAt(position);
            if (s != null) {
                presentFragment(new ServerInfoFragment(s, this::reloadServers));
                return true;
            }
            return false;
        });
        frame.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.TOP, 0, 0, 0, 64));

        // Continue button
        TextView continueBtn = new TextView(context);
        continueBtn.setText("Continue");
        continueBtn.setTextSize(16);
        continueBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        continueBtn.setGravity(Gravity.CENTER);
        continueBtn.setTypeface(AndroidUtilities.bold());
        continueBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        continueBtn.setOnClickListener(v -> onContinue());
        frame.addView(continueBtn, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 52,
                Gravity.BOTTOM | Gravity.LEFT, 16, 0, 16, 12));

        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        pingPool.shutdownNow();
        super.onFragmentDestroy();
    }

    // --- Data ---

    private void reloadServers() {
        servers.clear();
        servers.addAll(OwpengramServers.listServers());
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // --- Ping ---

    private void pingAll() {
        for (OwpengramServer s : servers) {
            final String id   = s.id;
            final String host = s.host;
            final int port    = s.port;
            pingPool.submit(() -> {
                int ms = pingTcp(host, port);
                uiHandler.post(() -> {
                    pingCache.put(id, ms);
                    updateRow(id);
                });
            });
        }
    }

    private int pingTcp(String host, int port) {
        long t = System.currentTimeMillis();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
            return (int) (System.currentTimeMillis() - t);
        } catch (Exception e) {
            return -1;
        }
    }

    private void updateRow(String serverId) {
        List<Object> rows = buildRows();
        for (int i = 0; i < rows.size(); i++) {
            Object o = rows.get(i);
            if (o instanceof OwpengramServer && serverId.equals(((OwpengramServer) o).id)) {
                adapter.notifyItemChanged(i);
                return;
            }
        }
    }

    // --- Row model ---

    private List<Object> buildRows() {
        List<Object> rows = new ArrayList<>();
        List<OwpengramServer> official = new ArrayList<>();
        List<OwpengramServer> custom   = new ArrayList<>();
        for (OwpengramServer s : servers) {
            if (s.isOfficial) official.add(s); else custom.add(s);
        }

        rows.add("official");
        rows.addAll(official);

        if (!custom.isEmpty()) {
            rows.add("custom");
            rows.addAll(custom);
        }

        rows.add("add");
        rows.add("spacer");
        return rows;
    }

    private OwpengramServer serverAt(int position) {
        List<Object> rows = buildRows();
        if (position < 0 || position >= rows.size()) return null;
        Object o = rows.get(position);
        return (o instanceof OwpengramServer) ? (OwpengramServer) o : null;
    }

    private void onRowClick(int position) {
        List<Object> rows = buildRows();
        if (position < 0 || position >= rows.size()) return;
        Object o = rows.get(position);
        if (o instanceof OwpengramServer) {
            selectedServerId = ((OwpengramServer) o).id;
            adapter.notifyDataSetChanged();
        } else if ("add".equals(o)) {
            presentFragment(new AddServerFragment(null, saved -> {
                reloadServers();
                selectedServerId = saved.id;
                pingPool.submit(() -> {
                    int ms = pingTcp(saved.host, saved.port);
                    uiHandler.post(() -> {
                        pingCache.put(saved.id, ms);
                        adapter.notifyDataSetChanged();
                    });
                });
            }));
        }
    }

    private void onContinue() {
        OwpengramServer server = OwpengramServers.getServerById(selectedServerId);
        if (server == null) {
            android.widget.Toast.makeText(getParentActivity(), "Please select a server", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        OwpengramServers.setServerForAccount(selectedServerId, currentAccount);
        OwpengramServers.applyServerToAccount(server, currentAccount);
        presentFragment(new LoginActivity(), true);
    }

    // --- Adapter ---

    private class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Context context;

        ListAdapter(Context context) { this.context = context; }

        @Override public int getItemCount() { return buildRows().size(); }

        @Override
        public int getItemViewType(int position) {
            Object o = buildRows().get(position);
            if (o instanceof OwpengramServer) return TYPE_SERVER;
            if ("add".equals(o)) return TYPE_ADD;
            if ("spacer".equals(o)) return TYPE_SPACER;
            return TYPE_HEADER;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            switch (type) {
                case TYPE_SERVER: return new ServerHolder(new ServerCell(context));
                case TYPE_ADD:    return new AddHolder(buildAddCell(context));
                case TYPE_HEADER: return new HeaderHolder(buildHeader(context));
                default:          return new SpacerHolder(buildSpacer(context));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = getItemViewType(position);
            Object o = buildRows().get(position);
            if (type == TYPE_SERVER) {
                OwpengramServer s = (OwpengramServer) o;
                ((ServerHolder) holder).cell.bind(s, selectedServerId, pingCache.get(s.id));
            } else if (type == TYPE_HEADER) {
                String key = (String) o;
                ((HeaderHolder) holder).tv.setText("official".equals(key) ? "Official Servers" : "Custom Servers");
            }
        }
    }

    // --- Cell builders ---

    private View buildHeader(Context context) {
        TextView tv = new TextView(context);
        tv.setTextSize(11);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.06f);
        tv.setPadding(dp(16), dp(16), dp(16), dp(6));
        tv.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private View buildAddCell(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setMinimumHeight(dp(52));
        row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        TextView tv = new TextView(context);
        tv.setText("+ Add Server");
        tv.setTextSize(15);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        tv.setTypeface(AndroidUtilities.bold());
        row.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View buildSpacer(Context context) {
        View v = new View(context);
        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
        return v;
    }

    // --- ViewHolders ---

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView tv;
        HeaderHolder(View v) { super(v); tv = (TextView) v; }
    }
    static class AddHolder extends RecyclerView.ViewHolder {
        AddHolder(View v) { super(v); }
    }
    static class SpacerHolder extends RecyclerView.ViewHolder {
        SpacerHolder(View v) { super(v); }
    }
    class ServerHolder extends RecyclerView.ViewHolder {
        final ServerCell cell;
        ServerHolder(ServerCell c) { super(c); cell = c; }
    }

    // --- ServerCell ---

    private class ServerCell extends FrameLayout {

        private final Paint avatarPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint letterPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint checkPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint checkStroke  = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final TextView nameTv;
        private final TextView metaTv;

        private String initial = "?";
        private int latencyColor = 0xFFAAAAAA;
        private boolean selected;

        ServerCell(Context context) {
            super(context);
            setMinimumHeight(dp(68));
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setWillNotDraw(false);

            letterPaint.setColor(Color.WHITE);
            letterPaint.setFakeBoldText(true);
            letterPaint.setTextAlign(Paint.Align.CENTER);

            checkStroke.setStyle(Paint.Style.STROKE);
            checkStroke.setStrokeWidth(dp(2));
            checkStroke.setAntiAlias(true);

            nameTv = new TextView(context);
            nameTv.setTextSize(15);
            nameTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTv.setTypeface(AndroidUtilities.bold());
            nameTv.setSingleLine();
            nameTv.setEllipsize(TextUtils.TruncateAt.END);

            metaTv = new TextView(context);
            metaTv.setTextSize(13);
            metaTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            metaTv.setSingleLine();

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setGravity(Gravity.CENTER_VERTICAL);
            textCol.addView(nameTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            textCol.addView(metaTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            // Avatar placeholder space = 46dp circle + 14dp right margin = left offset 74dp
            addView(textCol, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | Gravity.LEFT, 74, 0, 56, 0));
        }

        void bind(OwpengramServer server, String selectedId, Integer pingMs) {
            initial = server.name.isEmpty() ? "?" : String.valueOf(server.name.charAt(0)).toUpperCase();
            int hash = Math.abs(server.name.hashCode());
            avatarPaint.setColor(AVATAR_COLORS[hash % AVATAR_COLORS.length]);
            letterPaint.setTextSize(dp(18));

            selected = server.id.equals(selectedId);

            nameTv.setText(server.name);

            // Meta line: "host . port  - latency"
            StringBuilder meta = new StringBuilder(server.host).append(":").append(server.port);
            if (pingMs != null) {
                if (pingMs < 0) {
                    latencyColor = 0xFFE53935;
                    meta.append("   Offline");
                } else {
                    latencyColor = pingMs < 100 ? 0xFF4CAF82 : pingMs < 300 ? 0xFFE8A838 : 0xFFE53935;
                    meta.append("   ").append(pingMs).append(" ms");
                }
            } else {
                latencyColor = 0xFFAAAAAA;
            }
            metaTv.setText(meta.toString());

            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int h = getHeight();

            // Avatar circle
            float cx = dp(16 + 23); // 16 left padding + 23 radius = center x
            float cy = h / 2f;
            float r  = dp(23);
            canvas.drawCircle(cx, cy, r, avatarPaint);
            canvas.drawText(initial, cx, cy - (letterPaint.descent() + letterPaint.ascent()) / 2f, letterPaint);

            // Right-side radio / checkmark
            float rcx = getWidth() - dp(28);
            float rcy = h / 2f;
            float rr  = dp(11);

            if (selected) {
                checkPaint.setColor(Theme.getColor(Theme.key_radioBackgroundChecked));
                checkStroke.setColor(Theme.getColor(Theme.key_radioBackgroundChecked));
                canvas.drawCircle(rcx, rcy, rr, checkPaint);
                // White tick
                checkPaint.setColor(Color.WHITE);
                checkPaint.setStyle(Paint.Style.STROKE);
                checkPaint.setStrokeWidth(dp(2f));
                checkPaint.setStrokeCap(Paint.Cap.ROUND);
                checkPaint.setStrokeJoin(Paint.Join.ROUND);
                canvas.drawLine(rcx - dp(5), rcy, rcx - dp(1.5f), rcy + dp(4), checkPaint);
                canvas.drawLine(rcx - dp(1.5f), rcy + dp(4), rcx + dp(5), rcy - dp(4), checkPaint);
                checkPaint.setStyle(Paint.Style.FILL);
            } else {
                checkStroke.setColor(Theme.getColor(Theme.key_radioBackground));
                canvas.drawCircle(rcx, rcy, rr, checkStroke);
            }

            // Latency color is set in metaTv text above
        }
    }
}
