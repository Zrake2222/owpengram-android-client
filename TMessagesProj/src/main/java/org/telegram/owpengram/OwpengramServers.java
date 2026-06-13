package org.telegram.owpengram;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OwpengramServers {

    private static final String PREFS_NAME = "owpengram_servers";
    private static final String KEY_CUSTOM_SERVERS = "custom_servers";
    private static final String KEY_ACCOUNT_SERVER = "account_server_";

    public static final String ID_OWPENGRAM = "owpengram";
    public static final String ID_TEAMGRAM  = "teamgram";
    public static final String ID_TELEGRAM  = "telegram";

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int    DEFAULT_PORT = 10443;

    private static final String TEAMGRAM_HOST = "43.155.11.190";
    private static final int    TEAMGRAM_PORT = 10443;

    // --- Built-in servers ---

    public static OwpengramServer owpengramServer() {
        OwpengramServer s = new OwpengramServer();
        s.id          = ID_OWPENGRAM;
        s.name        = "OwpenGram";
        s.description = "Default OwpenGram server configured for this client.";
        s.host        = DEFAULT_HOST;
        s.port        = DEFAULT_PORT;
        s.isOfficial  = true;
        s.isTelegram  = false;
        s.multiDc     = false;
        s.mainDcId    = 1;
        return s;
    }

    public static OwpengramServer teamgramServer() {
        OwpengramServer s = new OwpengramServer();
        s.id          = ID_TEAMGRAM;
        s.name        = "Teamgram";
        s.description = "Open-source MTProto server compatible with Telegram clients. Default test sign-in code: 12345.";
        s.host        = TEAMGRAM_HOST;
        s.port        = TEAMGRAM_PORT;
        s.isOfficial  = true;
        s.isTelegram  = false;
        s.multiDc     = false;
        s.mainDcId    = 1;
        return s;
    }

    public static OwpengramServer telegramServer() {
        OwpengramServer s = new OwpengramServer();
        s.id          = ID_TELEGRAM;
        s.name        = "Telegram";
        s.description = "Official Telegram cloud. Sign in with your Telegram account.";
        s.host        = "149.154.167.51";
        s.port        = 443;
        s.isOfficial  = true;
        s.isTelegram  = true;
        s.multiDc     = true;
        s.mainDcId    = 2;
        return s;
    }

    // --- Server list ---

    public static List<OwpengramServer> listServers() {
        List<OwpengramServer> result = new ArrayList<>();
        result.add(telegramServer());
        result.add(teamgramServer());
        result.add(owpengramServer());
        result.addAll(loadCustomServers());
        return result;
    }

    public static OwpengramServer getServerById(String id) {
        if (id == null) return null;
        for (OwpengramServer s : listServers()) {
            if (id.equals(s.id)) return s;
        }
        return null;
    }

    // --- Custom server persistence ---

    public static List<OwpengramServer> loadCustomServers() {
        List<OwpengramServer> result = new ArrayList<>();
        try {
            String json = getPrefs().getString(KEY_CUSTOM_SERVERS, "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                result.add(OwpengramServer.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    private static void saveCustomServers(List<OwpengramServer> customs) {
        try {
            JSONArray array = new JSONArray();
            for (OwpengramServer s : customs) {
                array.put(s.toJson());
            }
            getPrefs().edit().putString(KEY_CUSTOM_SERVERS, array.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void addCustomServer(OwpengramServer server) {
        if (server.id == null || server.id.isEmpty()) {
            server.id = UUID.randomUUID().toString();
        }
        server.isOfficial = false;
        List<OwpengramServer> customs = loadCustomServers();
        customs.add(server);
        saveCustomServers(customs);
    }

    public static void updateCustomServer(OwpengramServer updated) {
        List<OwpengramServer> customs = loadCustomServers();
        for (int i = 0; i < customs.size(); i++) {
            if (updated.id.equals(customs.get(i).id)) {
                customs.set(i, updated);
                saveCustomServers(customs);
                return;
            }
        }
    }

    public static void removeCustomServer(String id) {
        List<OwpengramServer> customs = loadCustomServers();
        for (int i = 0; i < customs.size(); i++) {
            if (id.equals(customs.get(i).id)) {
                customs.remove(i);
                saveCustomServers(customs);
                return;
            }
        }
    }

    // --- Account - server binding ---

    public static String getServerIdForAccount(int accountNum) {
        return getPrefs().getString(KEY_ACCOUNT_SERVER + accountNum, null);
    }

    public static void setServerForAccount(String serverId, int accountNum) {
        getPrefs().edit().putString(KEY_ACCOUNT_SERVER + accountNum, serverId).apply();
    }

    public static OwpengramServer getServerForAccount(int accountNum) {
        return getServerById(getServerIdForAccount(accountNum));
    }

    public static boolean hasServerForAccount(int accountNum) {
        return getServerIdForAccount(accountNum) != null;
    }

    // --- DC application ---

    /**
     * Applies server host:port to all DC 1-5 of the given account so that
     * FILE_MIGRATE / NETWORK_MIGRATE responses resolve correctly.
     */
    public static void applyServerToAccount(OwpengramServer server, int accountNum) {
        if (server == null) return;
        ConnectionsManager cm = ConnectionsManager.getInstance(accountNum);
        for (int dc = 1; dc <= 5; dc++) {
            cm.applyDatacenterAddress(dc, server.host, server.port);
        }
    }

    /**
     * Called from ApplicationLoader after each account's ConnectionsManager is
     * initialized, to restore the previously selected server config.
     */
    public static void applyStoredServerToAccount(int accountNum) {
        OwpengramServer server = getServerForAccount(accountNum);
        if (server != null) {
            applyServerToAccount(server, accountNum);
        }
    }

    // --- Helpers ---

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns true when at least one active account uses a Telegram-official server. */
    public static boolean anyAccountIsTelegram() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                OwpengramServer s = getServerForAccount(a);
                if (s != null && s.isTelegram) return true;
            }
        }
        return false;
    }

    /** Returns the first activated account whose server is isTelegram, or -1. */
    public static int firstTelegramAccount() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                OwpengramServer s = getServerForAccount(a);
                if (s != null && s.isTelegram) return a;
            }
        }
        return -1;
    }
}
