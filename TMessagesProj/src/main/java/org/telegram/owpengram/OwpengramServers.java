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

    /** Public repository of the OwpenGram server, opened from the settings entry. */
    public static final String SERVER_REPO_URL = "https://github.com/owpengram/owpengram-server";

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int    DEFAULT_PORT = 10443;

    private static final String TEAMGRAM_HOST = "43.155.11.190";
    private static final int    TEAMGRAM_PORT = 10443;

    // RSA key shared by OwpenGram and Teamgram (single-server MTProto forks)
    static final String OWPENGRAM_RSA_KEY =
        "-----BEGIN RSA PUBLIC KEY-----\n" +
        "MIIBCgKCAQEAvKLEOWTzt9Hn3/9Kdp/RdHcEhzmd8xXeLSpHIIzaXTLJDw8BhJy1\n" +
        "jR/iqeG8Je5yrtVabqMSkA6ltIpgylH///FojMsX1BHu4EPYOXQgB0qOi6kr08iX\n" +
        "ZIH9/iOPQOWDsL+Lt8gDG0xBy+sPe/2ZHdzKMjX6O9B4sOsxjFrk5qDoWDrioJor\n" +
        "AJ7eFAfPpOBf2w73ohXudSrJE0lbQ8pCWNpMY8cB9i8r+WBitcvouLDAvmtnTX7a\n" +
        "khoDzmKgpJBYliAY4qA73v7u5UIepE8QgV0jCOhxJCPubP8dg+/PlLLVKyxU5Cdi\n" +
        "QtZj2EMy4s9xlNKzX8XezE0MHEa6bQpnFwIDAQAB\n" +
        "-----END RSA PUBLIC KEY-----";
    static final long OWPENGRAM_RSA_FINGERPRINT = 0xa9e071c1771060cdL;

    // Official Telegram production RSA key (restored from original Android source)
    static final String TELEGRAM_RSA_KEY =
        "-----BEGIN RSA PUBLIC KEY-----\n" +
        "MIIBCgKCAQEA6LszBcC1LGzyr992NzE0ieY+BSaOW622Aa9Bd4ZHLl+TuFQ4lo4g\n" +
        "5nKaMBwK/BIb9xUfg0Q29/2mgIR6Zr9krM7HjuIcCzFvDtr+L0GQjae9H0pRB2OO\n" +
        "62cECs5HKhT5DZ98K33vmWiLowc621dQuwKWSQKjWf50XYFw42h21P2KXUGyp2y/\n" +
        "+aEyZ+uVgLLQbRA1dEjSDZ2iGRy12Mk5gpYc397aYp438fsJoHIgJ2lgMv5h7WY9\n" +
        "t6N/byY9Nw9p21Og3AoXSL2q/2IJ1WRUhebgAdGVMlV1fkuOQoEzR7EdpqtQD9Cs\n" +
        "5+bfo3Nhmcyvk5ftB0WkJ9z6bNZ7yxrP8wIDAQAB\n" +
        "-----END RSA PUBLIC KEY-----";
    static final long TELEGRAM_RSA_FINGERPRINT = 0xd09d1d85de64fd85L;

    // --- Built-in servers ---

    public static OwpengramServer owpengramServer() {
        OwpengramServer s = new OwpengramServer();
        s.id                 = ID_OWPENGRAM;
        s.name               = "OwpenGram";
        s.description        = "Default OwpenGram server configured for this client.";
        s.host               = DEFAULT_HOST;
        s.port               = DEFAULT_PORT;
        s.isOfficial         = true;
        s.isTelegram         = false;
        s.multiDc            = false;
        s.mainDcId           = 1;
        s.rsaPublicKey       = OWPENGRAM_RSA_KEY;
        s.rsaKeyFingerprint  = OWPENGRAM_RSA_FINGERPRINT;
        return s;
    }

    public static OwpengramServer teamgramServer() {
        OwpengramServer s = new OwpengramServer();
        s.id                 = ID_TEAMGRAM;
        s.name               = "Teamgram";
        s.description        = "Open-source MTProto server compatible with Telegram clients. Default test sign-in code: 12345.";
        s.host               = TEAMGRAM_HOST;
        s.port               = TEAMGRAM_PORT;
        s.isOfficial         = true;
        s.isTelegram         = false;
        s.multiDc            = false;
        s.mainDcId           = 2;  // Teamgram assigns users to DC2 (matches desktop)
        s.rsaPublicKey       = OWPENGRAM_RSA_KEY;
        s.rsaKeyFingerprint  = OWPENGRAM_RSA_FINGERPRINT;
        return s;
    }

    public static OwpengramServer telegramServer() {
        OwpengramServer s = new OwpengramServer();
        s.id                 = ID_TELEGRAM;
        s.name               = "Telegram";
        s.description        = "Official Telegram cloud. Sign in with your Telegram account.";
        s.host               = "149.154.167.51";
        s.port               = 443;
        s.isOfficial         = true;
        s.isTelegram         = true;
        s.multiDc            = true;
        s.mainDcId           = 2;
        s.rsaPublicKey       = TELEGRAM_RSA_KEY;
        s.rsaKeyFingerprint  = TELEGRAM_RSA_FINGERPRINT;
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

    /**
     * True when two account slots target the same server. Used so the duplicate
     * login check (same phone / same user id) only triggers within one server —
     * the same phone number on a different server is a different account.
     * Matches by server id, or by resolved endpoint (host:port + Telegram flag)
     * so two profiles pointing at the same machine are still treated as one.
     */
    public static boolean sameAccountServer(int accountA, int accountB) {
        String idA = getServerIdForAccount(accountA);
        String idB = getServerIdForAccount(accountB);
        if (java.util.Objects.equals(idA, idB)) {
            return true;
        }
        OwpengramServer sa = getServerById(idA);
        OwpengramServer sb = getServerById(idB);
        if (sa == null || sb == null) {
            return false;
        }
        return sa.isTelegram == sb.isTelegram
                && sa.port == sb.port
                && sa.host != null && sa.host.equals(sb.host);
    }

    // --- DC application ---

    /**
     * Applies the selected server to the given account, atomically on the network
     * thread. Mirrors desktop ApplyServerToDcOptions + RestoreServerToAccount:
     *   - Telegram:       restore real Telegram DC 1-5, unlock, Telegram RSA key
     *   - Single-server:  map DC 1-5 all to one host:port, lock, server RSA key
     *
     * resetKeys=true clears auth keys (user picked a NEW server -> force re-handshake);
     * resetKeys=false keeps them (startup restore -> reuse the existing session).
     */
    public static void applyServerToAccount(OwpengramServer server, int accountNum) {
        applyServerToAccount(server, accountNum, true);
    }

    public static void applyServerToAccount(OwpengramServer server, int accountNum, boolean resetKeys) {
        if (server == null) return;
        final String key;
        final long fingerprint;
        if (server.isTelegram) {
            key = TELEGRAM_RSA_KEY;
            fingerprint = TELEGRAM_RSA_FINGERPRINT;
        } else if (server.rsaPublicKey != null && !server.rsaPublicKey.isEmpty()) {
            // Custom server with its own RSA key. Pass fingerprint 0 so the native
            // layer derives it from the PEM (we can't compute it in Java).
            key = server.rsaPublicKey;
            fingerprint = (server.rsaKeyFingerprint != 0) ? server.rsaKeyFingerprint : 0;
        } else {
            // No key provided -> shared default owpengram key (fingerprint known).
            key = OWPENGRAM_RSA_KEY;
            fingerprint = OWPENGRAM_RSA_FINGERPRINT;
        }
        int mainDc = server.mainDcId > 0
                ? server.mainDcId
                : (server.isTelegram ? 2 : 1);
        ConnectionsManager.native_applyServerConfig(
                accountNum,
                server.host,
                server.port,
                server.isTelegram,
                mainDc,
                key,
                fingerprint,
                resetKeys);
    }

    /**
     * Called from ApplicationLoader after each account's ConnectionsManager is
     * initialized, to restore the previously selected server config. Does NOT
     * reset auth keys so an already-authorized session keeps working.
     */
    public static void applyStoredServerToAccount(int accountNum) {
        OwpengramServer server = getServerForAccount(accountNum);
        if (server != null) {
            applyServerToAccount(server, accountNum, false);
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

    /**
     * Display name of the server an account is on, or null if unknown. Used for
     * the main app header so it shows the current server instead of "Telegram".
     */
    public static String serverNameForAccount(int accountNum) {
        OwpengramServer s = getServerForAccount(accountNum);
        return (s != null) ? s.name : null;
    }

    // --- Account limits (mirrors desktop: premium cap is Telegram-only) ---

    /** First unused account slot (any server), or -1 if all slots are taken. */
    public static int firstFreeAccountSlot() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                return a;
            }
        }
        return -1;
    }

    /** Number of activated accounts on a Telegram-official server. */
    public static int countTelegramAccounts() {
        int n = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                OwpengramServer s = getServerForAccount(a);
                if (s != null && s.isTelegram) n++;
            }
        }
        return n;
    }

    /**
     * Telegram-account cap (premium raises it). Only Telegram accounts count
     * toward this; custom/self-hosted accounts are limited solely by the hard
     * total slot count (MAX_ACCOUNT_COUNT).
     */
    public static int telegramAccountLimit() {
        return UserConfig.hasPremiumOnAccounts()
                ? UserConfig.MAX_ACCOUNT_COUNT
                : UserConfig.MAX_ACCOUNT_DEFAULT_COUNT;
    }

    /** True if another Telegram account can still be added under the premium cap. */
    public static boolean canAddTelegramAccount() {
        return countTelegramAccounts() < telegramAccountLimit();
    }
}
