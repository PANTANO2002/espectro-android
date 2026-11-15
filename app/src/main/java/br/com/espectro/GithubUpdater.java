package br.com.espectro;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class GithubUpdater {
    static final String REPO = BuildConfig.UPDATE_REPOSITORY;
    static final String ACTION_INSTALL = "br.com.espectro.INSTALL_UPDATE";
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final int JOB_ID = 701;
    private static final long APK_LIMIT = 80L * 1024 * 1024;
    private static final String CHANNEL = "espectro_updates";

    interface Result { void finished(String message); }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("updates", Context.MODE_PRIVATE); }
    static File apk(Context c) { return new File(new File(c.getFilesDir(), "updates"), "pending.apk"); }
    static boolean automatic(Context c) { return prefs(c).getBoolean("automatic", true); }
    static long installed(Context c) throws PackageManager.NameNotFoundException {
        PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }
    static boolean ready(Context c) {
        try { return apk(c).isFile() && UpdatePolicy.newer(prefs(c).getLong("pending_version", 0), installed(c)); }
        catch (Exception e) { return false; }
    }
    static String status(Context c) {
        if (ready(c)) return "Versão " + prefs(c).getString("pending_name", "nova") + " pronta para instalar";
        return prefs(c).getString("status", "Consulta automática ao GitHub ao abrir e periodicamente em Wi-Fi.");
    }
    static void configure(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("automatic", enabled).apply();
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        if (jobs == null) return;
        if (!enabled) { jobs.cancel(JOB_ID); return; }
        JobInfo task = new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateJobService.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setPeriodic(6 * 60 * 60 * 1000L)
            .setPersisted(true).build();
        jobs.schedule(task);
    }
    static void check(Context context, boolean manual, Result result) {
        Context c = context.getApplicationContext();
        if (!manual && System.currentTimeMillis() - prefs(c).getLong("last_check", 0) < 60 * 60 * 1000L && !ready(c)) {
            if (result != null) result.finished(status(c));
            return;
        }
        if (!BUSY.compareAndSet(false, true)) { if (result != null) result.finished("Uma consulta já está em andamento."); return; }
        new Thread(() -> {
            String message;
            try { message = run(c, manual); }
            catch (Exception e) { message = "Não foi possível atualizar: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            finally { BUSY.set(false); }
            prefs(c).edit().putString("status", message).putLong("last_check", System.currentTimeMillis()).apply();
            if (result != null) result.finished(message);
        }, "espectro-update").start();
    }
    private static String run(Context c, boolean manual) throws Exception {
        if (ready(c)) {
            try { verifyApk(c, apk(c), prefs(c).getLong("pending_version", 0), prefs(c).getString("pending_sha256", "")); return status(c); }
            catch (Exception e) { apk(c).delete(); prefs(c).edit().remove("pending_version").apply(); }
        }
        JSONObject release = new JSONObject(readText("https://api.github.com/repos/" + REPO + "/releases/latest", 1024 * 1024));
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) throw new IOException("release não estável");
        JSONArray assets = release.getJSONArray("assets");
        JSONObject manifestAsset = asset(assets, "update.json");
        String manifestUrl = manifestAsset.getString("browser_download_url");
        requireAsset(manifestUrl);
        JSONObject manifest = new JSONObject(readText(manifestUrl, 64 * 1024));
        long version = manifest.getLong("versionCode");
        if (!UpdatePolicy.newer(version, installed(c))) return "Você já está na versão mais recente (" + BuildConfig.VERSION_NAME + ").";
        if (manifest.getInt("minSdk") > Build.VERSION.SDK_INT) return "A nova versão exige um Android mais recente.";
        if (!c.getPackageName().equals(manifest.getString("packageName"))) throw new IOException("pacote incorreto no manifesto");
        String name = manifest.getString("apkName"), sha = manifest.getString("sha256");
        if (!UpdatePolicy.apkName(name) || !UpdatePolicy.sha256(sha)) throw new IOException("manifesto de atualização inválido");
        JSONObject apkAsset = asset(assets, name);
        long expectedSize = apkAsset.getLong("size");
        if (expectedSize <= 0 || expectedSize > APK_LIMIT) throw new IOException("APK excede o limite de tamanho");
        String url = apkAsset.getString("browser_download_url"); requireAsset(url);
        String digest = apkAsset.optString("digest", "");
        if (!digest.isEmpty() && !digest.equalsIgnoreCase("sha256:" + sha)) throw new IOException("hash do GitHub difere do manifesto");
        File directory = apk(c).getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("não foi possível criar a pasta de atualização");
        File temporary = new File(directory, "download.part");
        try {
            HttpsURLConnection connection = open(url);
            long copied = 0;
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32768]; int n;
                while ((n = in.read(buffer)) != -1) {
                    copied += n; if (copied > expectedSize || copied > APK_LIMIT) throw new IOException("download maior que o esperado");
                    out.write(buffer, 0, n);
                }
            } finally { connection.disconnect(); }
            if (copied != expectedSize) throw new IOException("download incompleto");
            verifyApk(c, temporary, version, sha);
            if (apk(c).exists() && !apk(c).delete()) throw new IOException("não foi possível substituir o download anterior");
            if (!temporary.renameTo(apk(c))) throw new IOException("não foi possível concluir o download");
            prefs(c).edit().putLong("pending_version", version).putString("pending_sha256", sha)
                .putString("pending_name", manifest.getString("versionName")).apply();
            notifyReady(c);
            return status(c);
        } finally { temporary.delete(); }
    }
    private static JSONObject asset(JSONArray assets, String name) throws Exception {
        JSONObject found = null;
        for (int i = 0; i < assets.length(); i++) if (name.equals(assets.getJSONObject(i).optString("name"))) {
            if (found != null) throw new IOException("asset duplicado");
            found = assets.getJSONObject(i);
        }
        if (found == null) throw new IOException("release sem " + name);
        return found;
    }
    private static void requireAsset(String url) throws IOException { if (!UpdatePolicy.releaseAssetUrl(url, REPO)) throw new IOException("origem da atualização não autorizada"); }
    private static HttpsURLConnection open(String address) throws IOException {
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (!UpdatePolicy.allowedUrl(address)) throw new IOException("URL de download não autorizada");
            HttpsURLConnection connection = (HttpsURLConnection)new URL(address).openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(25000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Espectro/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept", address.startsWith("https://api.github.com/") ? "application/vnd.github+json" : "application/octet-stream");
            int code = connection.getResponseCode();
            if (code == 200) return connection;
            String next = connection.getHeaderField("Location"); connection.disconnect();
            if ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && next != null) address = new URL(new URL(address), next).toString();
            else if (code == 404) throw new IOException("nenhuma release pública disponível");
            else throw new IOException("GitHub respondeu HTTP " + code);
        }
        throw new IOException("redirecionamentos em excesso");
    }
    private static String readText(String url, int limit) throws IOException {
        HttpsURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) { if (out.size() + n > limit) throw new IOException("resposta maior que o limite"); out.write(buffer, 0, n); }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { connection.disconnect(); }
    }
    static String sha256(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) { byte[] buffer = new byte[32768]; int n; while ((n = in.read(buffer)) != -1) hash.update(buffer, 0, n); }
        StringBuilder value = new StringBuilder(); for (byte b : hash.digest()) value.append(String.format(Locale.ROOT, "%02x", b & 255)); return value.toString();
    }
    @SuppressWarnings("deprecation")
    static void verifyApk(Context c, File file, long version, String sha) throws Exception {
        if (!UpdatePolicy.sha256(sha) || !sha.equalsIgnoreCase(sha256(file))) throw new IOException("SHA-256 do APK incorreto");
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageManager pm = c.getPackageManager();
        PackageInfo incoming = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo current = pm.getPackageInfo(c.getPackageName(), flags);
        if (incoming == null || !c.getPackageName().equals(incoming.packageName)) throw new IOException("APK não pertence ao Espectro");
        long incomingVersion = Build.VERSION.SDK_INT >= 28 ? incoming.getLongVersionCode() : incoming.versionCode;
        if (incomingVersion != version || !UpdatePolicy.newer(incomingVersion, installed(c))) throw new IOException("versão do APK não corresponde à atualização");
        if (incoming.applicationInfo == null || incoming.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) throw new IOException("APK incompatível com o Android");
        Signature[] a = Build.VERSION.SDK_INT >= 28 && incoming.signingInfo != null ? incoming.signingInfo.getApkContentsSigners() : incoming.signatures;
        Signature[] b = Build.VERSION.SDK_INT >= 28 && current.signingInfo != null ? current.signingInfo.getApkContentsSigners() : current.signatures;
        if (a == null || b == null || a.length == 0 || !new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b)))) throw new IOException("assinatura difere do app instalado");
    }
    static Uri uri(Context c) { return Uri.parse("content://" + c.getPackageName() + ".updates/update.apk"); }
    static void notifyReady(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Atualizações do Espectro", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, MainActivity.class).setAction(ACTION_INSTALL);
        PendingIntent action = PendingIntent.getActivity(c, 701, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(701, new Notification.Builder(c, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Espectro: atualização pronta").setContentText("Toque para confirmar a instalação no Android.")
            .setContentIntent(action).setAutoCancel(true).build());
    }
}
