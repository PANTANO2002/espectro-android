package br.com.espectro;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

// This first version intentionally has a Portuguese-only interface.
@android.annotation.SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int BG = 0xFF0C0C12, SURFACE = 0xFF16161F, BORDER = 0xFF292734;
    private static final int RED = 0xFFFF4357, WHITE = 0xFFF2EFF7, MUTED = 0xFFAAA4B6;
    private static final int PERMISSIONS = 40, EXPORT = 41;
    private final LinkedHashMap<String, CardViews> cards = new LinkedHashMap<>();
    private final LinkedHashMap<String, GroupViews> groups = new LinkedHashMap<>();
    private SignalRadarView radar;
    private TextView radarLegend, updateBanner;
    private boolean resumeInstall;
    private ScanEngine engine;
    private ScanHeroView hero;
    private ScrollView scroll;
    private LinearLayout content, results, resultList;
    private TextView status, count, empty, durationButton, summary, moduleSummary;
    private Button export;
    private ProgressBar progress;
    private int duration = 60;
    private boolean pendingStart;
    private boolean resumed, scanAfterResume;
    private ValueAnimator heroAnimator;
    private byte[] pendingReport;
    private int rendered;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        engine = new ScanEngine(this, duration, this::refresh);
        refresh();
        GithubUpdater.configure(this, GithubUpdater.automatic(this));
        if (GithubUpdater.automatic(this)) GithubUpdater.check(this, false, message -> runOnUiThread(() -> { if (!isDestroyed()) updateBanner.setText("↻  " + GithubUpdater.status(this)); }));
        if (GithubUpdater.ACTION_INSTALL.equals(getIntent().getAction())) scroll.post(this::showUpdates);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setLineSpacing(dp(3), 1);
        return v;
    }
    private void bold(TextView v) { v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
    private void updateText(TextView view, String value) {
        if (!android.text.TextUtils.equals(view.getText(), value)) view.setText(value);
    }
    private GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height)); }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), lp(1, height)); }

    private void buildUi() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);
        content = column(); content.setPadding(dp(24), dp(22), dp(24), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            content.setPadding(dp(24) + insets.getSystemWindowInsetLeft(), dp(18) + insets.getSystemWindowInsetTop(), dp(24) + insets.getSystemWindowInsetRight(), dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.requestApplyInsets();

        LinearLayout header = row();
        TextView mark = text("((•))", 22, RED); mark.setTypeface(Typeface.MONOSPACE);
        header.addView(mark, lp(57, 48)); mark.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout wordmark = column();
        TextView logo = text("ESPECTRO", 19, WHITE); bold(logo); logo.setLetterSpacing(.11f);
        wordmark.addView(logo); gap(wordmark, 5);
        TextView small = text("EXPLORADOR DE SINAIS", 8, MUTED); small.setLetterSpacing(.15f);
        wordmark.addView(small);
        header.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1));
        TextView menu = text("•••", 19, MUTED); menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Opções, hardware e registro"); menu.setBackground(shape(SURFACE, BORDER, 13));
        header.addView(menu, lp(48, 44)); menu.setOnClickListener(v -> showMenu());
        content.addView(header);
        gap(content, 15);
        updateBanner = text("↻  " + GithubUpdater.status(this), 11, MUTED);
        updateBanner.setPadding(dp(12), dp(10), dp(12), dp(10)); updateBanner.setBackground(shape(SURFACE, BORDER, 10));
        updateBanner.setOnClickListener(v -> showUpdates()); content.addView(updateBanner, lp(-1, -2));
        gap(content, 32);
        LinearLayout online = row();
        status = text("●  PRONTO PARA ESCANEAR", 10, RED); status.setLetterSpacing(.09f);
        online.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        TextView local = text("LOCAL", 9, MUTED); local.setPadding(dp(9), dp(5), dp(9), dp(5)); local.setBackground(shape(SURFACE, BORDER, 6));
        online.addView(local); content.addView(online);
        gap(content, 17);
        TextView headline = text("O invisível,\nem um toque.", 33, WHITE); bold(headline); headline.setLineSpacing(dp(1), 1);
        content.addView(headline);
        gap(content, 10);
        content.addView(text("Explore os sinais que o seu celular consegue observar.", 13, MUTED));
        hero = new ScanHeroView(this);
        content.addView(hero, lp(-1, 286));
        hero.setOnClickListener(v -> { if (engine != null && engine.running) engine.stop("Interrompida por você"); else requestScan(); });

        LinearLayout time = row(); time.setPadding(dp(15), dp(13), dp(15), dp(13)); time.setBackground(shape(SURFACE, BORDER, 14));
        TextView timeLabel = text("TEMPO DE VARREDURA", 9, MUTED); timeLabel.setLetterSpacing(.08f);
        time.addView(timeLabel, new LinearLayout.LayoutParams(0, -2, 1));
        durationButton = text("60 segundos  ⌄", 13, WHITE); bold(durationButton);
        time.addView(durationButton); time.setOnClickListener(v -> chooseDuration());
        content.addView(time);
        gap(content, 12);
        content.addView(text("Localização e dispositivos próximos permitem acessar os sinais. Mantenha o app aberto durante a busca.", 11, MUTED));
        gap(content, 24);

        results = column();
        LinearLayout resultHeader = row();
        TextView title = text("No seu radar", 22, WHITE); bold(title);
        resultHeader.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        count = text("0", 15, RED); count.setGravity(Gravity.CENTER); count.setBackground(shape(0xFF2B1821, 0, 10));
        resultHeader.addView(count, lp(42, 32));
        results.addView(resultHeader); gap(results, 7);
        summary = text("Toque em cada origem para expandir ou recolher.", 12, MUTED);
        results.addView(summary); gap(results, 14);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(RED));
        results.addView(progress, lp(-1, 3)); progress.setVisibility(View.GONE);
        gap(results, 14);
        moduleSummary = text("Wi-Fi · Bluetooth · Direct · NFC · rede móvel · rede local", 10, MUTED);
        moduleSummary.setOnClickListener(v -> showHardware()); results.addView(moduleSummary);
        gap(results, 16);
        radar = new SignalRadarView(this); results.addView(radar, lp(-1, 276));
        radarLegend = text("Cada anel representa uma origem. O arco mostra intensidade, sem indicar localização.", 10, MUTED); results.addView(radarLegend);
        gap(results, 16);
        empty = text("Seu ambiente, ainda por descobrir.\nToque em ESCANEAR para começar.", 13, MUTED);
        empty.setGravity(Gravity.CENTER); empty.setPadding(dp(18), dp(25), dp(18), dp(25));
        empty.setBackground(shape(SURFACE, BORDER, 17)); results.addView(empty, lp(-1, -2));
        resultList = column(); results.addView(resultList);
        gap(results, 17);
        export = new Button(this); export.setAllCaps(false); export.setText("Salvar relatório  ↓"); export.setTextColor(WHITE); export.setTextSize(13); export.setBackground(shape(SURFACE, BORDER, 13));
        export.setOnClickListener(v -> exportReport()); results.addView(export, lp(-1, 46));
        gap(results, 14);
        results.addView(text("Resultados são sinais, identidades e serviços observados. Um aparelho pode aparecer mais de uma vez. Intensidade de sinal não informa metros nem direção.", 10, MUTED));
        content.addView(results, lp(-1, -2));
        gap(content, 28);
        TextView footer = text("SINAIS LOCAIS  /  ATUALIZAÇÕES PELO GITHUB", 8, 0xFF716A7C);
        footer.setLetterSpacing(.07f); footer.setGravity(Gravity.CENTER); content.addView(footer);
    }

    private void chooseDuration() {
        if (engine != null && engine.running) { Toast.makeText(this, "Altere o tempo após encerrar a busca.", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Tempo de varredura")
            .setItems(new String[]{"30 segundos", "60 segundos", "2 minutos", "5 minutos", "Personalizar…"}, (dialog, which) -> {
                if (which < 4) setDuration(new int[]{30, 60, 120, 300}[which]);
                else {
                    EditText field = new EditText(this); field.setInputType(InputType.TYPE_CLASS_NUMBER); field.setText(String.valueOf(duration)); field.setSelectAllOnFocus(true);
                    AlertDialog custom = new AlertDialog.Builder(this).setTitle("De 15 a 900 segundos").setView(field).setPositiveButton("Aplicar", null).setNegativeButton("Cancelar", null).create();
                    custom.setOnShowListener(d -> custom.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        try { int value = Integer.parseInt(field.getText().toString()); if (value < 15 || value > 900) throw new NumberFormatException(); setDuration(value); custom.dismiss(); }
                        catch (NumberFormatException e) { field.setError("Digite um valor de 15 a 900"); }
                    })); custom.show();
                }
            }).show();
    }
    private void setDuration(int value) { duration = value; durationButton.setText(value + " segundos  ⌄"); }

    private void requestScan() {
        if (pendingStart) return;
        ArrayList<String> missing = new ArrayList<>();
        for (String p : new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        // Fine/coarse must be requested together on Android 12+ even if coarse was granted previously.
        if (missing.contains(Manifest.permission.ACCESS_FINE_LOCATION) && !missing.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            for (String p : new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if (!missing.isEmpty()) { pendingStart = true; requestPermissions(missing.toArray(new String[0]), PERMISSIONS); }
        else beginScan();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == PERMISSIONS && pendingStart) { pendingStart = false; scanAfterResume = true; startWhenReady(); }
    }

    private void startWhenReady() {
        if (scanAfterResume && resumed && getWindow().getDecorView().hasWindowFocus()) {
            scanAfterResume = false;
            beginScan();
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) startWhenReady();
    }

    private void beginScan() {
        if (isFinishing() || isDestroyed()) return;
        if (engine != null && engine.running) engine.stop("Nova sessão");
        cards.clear(); groups.clear(); resultList.removeAllViews(); rendered = 0;
        engine = new ScanEngine(this, duration, this::refresh);
        engine.start();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        animateReveal();
    }

    private void animateReveal() {
        if (heroAnimator != null) heroAnimator.cancel();
        int from = hero.getHeight();
        heroAnimator = ValueAnimator.ofInt(from, dp(206));
        heroAnimator.setDuration(550); heroAnimator.setInterpolator(new PathInterpolator(.2f, .8f, .2f, 1));
        heroAnimator.addUpdateListener(a -> { ViewGroup.LayoutParams p = hero.getLayoutParams(); p.height = (int)a.getAnimatedValue(); hero.setLayoutParams(p); });
        heroAnimator.start();
        results.setAlpha(.2f); results.setTranslationY(dp(24));
        results.animate().alpha(1).translationY(0).setDuration(650).start();
        scroll.postDelayed(() -> { if (!isDestroyed()) scroll.smoothScrollTo(0, Math.max(0, hero.getTop() - dp(45))); }, 350);
    }

    private void refresh() {
        if (engine == null) return;
        boolean active = engine.running;
        hero.update(active, engine.secondsLeft());
        status.setText(active ? "●  VARREDURA EM ANDAMENTO" : engine.startedAt == 0 ? "●  PRONTO PARA ESCANEAR" : "○  VARREDURA ENCERRADA");
        count.setText(String.valueOf(engine.findings.size()));
        summary.setText(active ? engine.secondsLeft() + " s restantes · resultados ao vivo" : engine.startedAt == 0 ? "Toque em cada origem para expandir ou recolher." : "Sessão concluída · " + engine.findings.size() + " identificadores e serviços");
        progress.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) progress.setProgress((int)(1000 * (1 - engine.secondsLeft() / (double)duration)), true);
        if (!active) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        empty.setVisibility(engine.findings.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(active ? "Escutando o ambiente…\nSó aparecem sinais que o Android consegue acessar." : engine.startedAt == 0 ? "Seu ambiente, ainda por descobrir.\nToque em ESCANEAR para começar." : "Nenhum resultado nesta sessão.\nConfira permissões, Wi-Fi, Bluetooth e localização em Opções.");
        export.setEnabled(engine.startedAt != 0); export.setAlpha(engine.startedAt != 0 ? 1 : .35f);
        moduleSummary.setText(active ? "Módulos em execução · toque para ver disponibilidade" : "Wi-Fi · Bluetooth · Direct · NFC · rede móvel · rede local");
        radar.update(engine.findings.values());
        for (String source : SignalRadarView.ORDER) {
            ArrayList<Finding> items = new ArrayList<>();
            for (Finding f : engine.findings.values()) if (f.methods.contains(source)) items.add(f);
            if (items.isEmpty()) continue;
            GroupViews group = groups.get(source);
            if (group == null) { group = new GroupViews(source); groups.put(source, group); resultList.addView(group.root); }
            group.counter.setText(String.valueOf(items.size()));
            group.header.setContentDescription(source + ", " + items.size() + " resultados. Toque para " + (group.expanded ? "recolher" : "expandir"));
            int index = 0;
            for (Finding f : items) {
                if (index++ >= 100) break;
                String cardKey = source + "|" + f.key;
                CardViews views = cards.get(cardKey);
                if (views == null) {
                    views = new CardViews(f); cards.put(cardKey, views); group.body.addView(views.root);
                    if (rendered++ < 40 && group.expanded) { views.root.setAlpha(0); views.root.setTranslationY(dp(18)); views.root.animate().alpha(1).translationY(0).setStartDelay(Math.min(index, 6) * 35L).setDuration(320).start(); }
                }
                views.update(f);
            }
            group.more.setVisibility(group.expanded && items.size() > 100 ? View.VISIBLE : View.GONE);
        }
        radarLegend.setText("Anéis, de fora para dentro: " + String.join(" → ", SignalRadarView.ORDER) + ". Marcador curto = presença sem RSSI.");
    }

    private final class GroupViews {
        final LinearLayout root = column(), body = column(), header = row();
        final TextView counter = text("0", 13, MUTED), arrow = text("⌄", 19, MUTED);
        final TextView more = text("Mostrando 100 nesta origem. Todos os resultados guardados estão no relatório.", 10, MUTED);
        boolean expanded = true;
        GroupViews(String source) {
            header.setPadding(dp(14), dp(16), dp(14), dp(16)); header.setBackground(shape(SURFACE, BORDER, 13));
            TextView title = text("●  " + source, 14, SignalRadarView.color(source)); bold(title);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            header.addView(counter); LinearLayout.LayoutParams arrowParams = lp(28, -2); arrow.setGravity(Gravity.END); header.addView(arrow, arrowParams);
            arrow.setRotation(180); root.addView(header, lp(-1, -2)); gap(root, 10); root.addView(body, lp(-1, -2)); root.addView(more); gap(root, 14);
            header.setFocusable(true); header.setOnClickListener(v -> {
                android.transition.AutoTransition transition = new android.transition.AutoTransition(); transition.setDuration(230);
                android.transition.TransitionManager.beginDelayedTransition(root, transition);
                expanded = !expanded; body.setVisibility(expanded ? View.VISIBLE : View.GONE);
                more.setVisibility(expanded && Integer.parseInt(counter.getText().toString()) > 100 ? View.VISIBLE : View.GONE);
                arrow.animate().rotation(expanded ? 180 : 0).setDuration(230).start();
                header.setContentDescription(source + ", " + counter.getText() + " resultados. Toque para " + (expanded ? "recolher" : "expandir"));
            });
        }
    }

    private final class CardViews {
        final LinearLayout root;
        final TextView title, kind, via, distance, signal, freshness, addresses;
        CardViews(Finding f) {
            root = column(); root.setPadding(dp(17), dp(17), dp(17), dp(17)); root.setBackground(shape(SURFACE, BORDER, 17));
            LinearLayout.LayoutParams margins = lp(-1, -2); margins.bottomMargin = dp(11); root.setLayoutParams(margins);
            LinearLayout top = row();
            TextView icon = text("◈", 20, RED); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(0xFF2B1821, 0, 12));
            top.addView(icon, lp(39, 39));
            LinearLayout labels = column(); LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1); labelParams.leftMargin = dp(12);
            title = text("", 16, WHITE); bold(title); title.setMaxLines(2); labels.addView(title); gap(labels, 5);
            kind = text("", 11, MUTED); labels.addView(kind); top.addView(labels, labelParams); root.addView(top);
            gap(root, 14); via = text("", 11, 0xFFE0B4BE); root.addView(via);
            gap(root, 8); addresses = text("", 11, MUTED); addresses.setMaxLines(3); root.addView(addresses);
            gap(root, 12); signal = text("", 11, MUTED); root.addView(signal);
            gap(root, 6); distance = text("", 12, WHITE); root.addView(distance);
            gap(root, 5); freshness = text("", 10, MUTED); root.addView(freshness);
            root.setOnClickListener(v -> findingDetails(f));
        }
        void update(Finding f) {
            updateText(title, f.name); updateText(kind, f.kind); updateText(via, "Encontrado via " + f.via());
            updateText(addresses, f.addresses());
            updateText(signal, f.signal()); updateText(distance, f.distance());
            long age = f.ageMs();
            String freshnessText = age < 0 ? " · idade do cache desconhecida" : age > 10000 ? " · dado de " + age / 1000 + " s atrás" : "";
            updateText(freshness, "Direção indisponível" + freshnessText);
            root.setContentDescription(f.name + ". " + f.kind + ". Encontrado via " + f.via() + ". " + f.signal() + ". " + f.distance());
        }
    }

    private void findingDetails(Finding f) {
        StringBuilder identifiers = new StringBuilder();
        for (Map.Entry<String, String> id : f.identifiers.entrySet()) identifiers.append(id.getKey()).append(": ").append(id.getValue()).append("\n");
        showText(f.name, f.kind + "\n\nEncontrado via " + f.via() + "\n" + f.signal() + "\n" + f.distance()
            + "\nDireção: indisponível\n\n" + identifiers + "\n" + f.details + "\n\nID observado: " + f.key
            + "\nPrimeira detecção no app: " + new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(f.firstSeen))
            + "\nAtualizações recebidas: " + f.observations
            + "\n\nO nome e a classe são anunciados e não confirmam o modelo ou dono. O sinal depende de paredes, potência e interferência. Um mesmo aparelho pode aparecer por vários sinais."
            + (f.distanceMeters == null ? "\n\nMetros exigem medição compatível, como Wi-Fi RTT. RSSI sozinho não fornece distância confiável." : "\n\nDistância via Wi-Fi RTT. O valor ± é o desvio padrão informado pelo Android; não inclui todo o erro por obstáculos ou reflexões."));
    }

    private void showMenu() {
        new AlertDialog.Builder(this).setTitle("Espectro").setItems(new String[]{"Hardware, sensores e disponibilidade", "Registro da sessão", "Permissões do app", "Configurações de conexões", "Atualizações pelo GitHub", "Sobre a identificação"}, (dialog, which) -> {
            if (which == 0) showHardware();
            else if (which == 1) showText("Registro da sessão", String.join("\n", engine.log));
            else if (which == 2) openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            else if (which == 3) openSettings(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            else if (which == 4) showUpdates();
            else showText("O que o radar observa", "O Espectro mostra os sinais que as APIs públicas do Android permitem acessar.\n\nWi-Fi: pontos de acesso e hotspots; não todos os clientes.\nBluetooth: anúncios BLE e aparelhos em modo visível.\nDirect: participantes visíveis da descoberta P2P.\nNFC: tags por aproximação, a centímetros.\nRede móvel: células da operadora; não celulares de terceiros.\nRede local: nove tipos de serviço mDNS anunciados na rede conectada.\nUSB: periféricos já conectados.\n\nSensores e GNSS fornecem contexto, sem identificar aparelhos ao redor. Infravermelho do celular é emissor. UWB e Wi-Fi Aware exigem participantes compatíveis e não têm varredura universal nesta versão.\n\nMetros são exibidos apenas se Wi-Fi RTT for suportado pelo celular e pelo ponto de acesso. Direção de aparelhos não está disponível.\n\nDados ficam em memória até fechar o app ou iniciar nova sessão. Exportar cria o arquivo no destino escolhido. Ao sair da tela, a varredura para. Sem conta, anúncios, analytics ou servidor.");
        }).show();
    }

    private void showHardware() {
        StringBuilder b = new StringBuilder();
        b.append(engine.running ? "SESSÃO EM ANDAMENTO\n\n" : "DISPONIBILIDADE / ÚLTIMA SESSÃO\n\n");
        for (Map.Entry<String,String> entry : engine.modules.entrySet()) b.append(entry.getKey()).append("\n").append(entry.getValue()).append("\n\n");
        b.append("HARDWARE E ÚLTIMAS LEITURAS\n\n");
        for (Map.Entry<String,String> entry : engine.hardware.entrySet()) b.append(entry.getKey()).append("\n").append(entry.getValue()).append("\n\n");
        showText("O que está disponível", b.toString());
    }

    private void showText(String title, String value) {
        ScrollView scroller = new ScrollView(this); TextView body = text(value.isEmpty() ? "Nenhum evento registrado." : value, 13, WHITE);
        body.setTextIsSelectable(true); body.setPadding(dp(24), dp(12), dp(24), dp(20)); scroller.addView(body);
        new AlertDialog.Builder(this).setTitle(title).setView(scroller).setPositiveButton("Fechar", null).show();
    }
    private void openSettings(Intent intent) { try { startActivity(intent); } catch (ActivityNotFoundException e) { Toast.makeText(this, "Abra as configurações do Android.", Toast.LENGTH_SHORT).show(); } }

    private void showUpdates() {
        LinearLayout panel = column(); panel.setPadding(dp(23), dp(12), dp(23), dp(18));
        TextView info = text(GithubUpdater.status(this), 13, WHITE); panel.addView(info); gap(panel, 13);
        panel.addView(text("Repositório: " + GithubUpdater.REPO + "\nO app consulta releases ao abrir e, periodicamente, em rede não tarifada. Baixa e verifica o APK; o Android pede sua confirmação para instalar. Nenhum resultado da varredura é enviado ao GitHub.", 12, MUTED));
        gap(panel, 14);
        Switch auto = new Switch(this); auto.setText("Consultar automaticamente"); auto.setChecked(GithubUpdater.automatic(this)); panel.addView(auto);
        auto.setOnCheckedChangeListener((button, enabled) -> GithubUpdater.configure(this, enabled));
        Button install = new Button(this); install.setAllCaps(false); install.setText("Instalar atualização pronta"); install.setEnabled(GithubUpdater.ready(this));
        Button check = new Button(this); check.setAllCaps(false); check.setText("Verificar agora"); panel.addView(check); panel.addView(install);
        check.setOnClickListener(v -> {
            check.setEnabled(false); info.setText("Consultando GitHub e verificando os arquivos…");
            GithubUpdater.check(this, true, message -> runOnUiThread(() -> {
                if (isDestroyed()) return;
                info.setText(message); check.setEnabled(true); install.setEnabled(GithubUpdater.ready(this)); updateBanner.setText("↻  " + GithubUpdater.status(this));
            }));
        });
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Button notifications = new Button(this); notifications.setAllCaps(false); notifications.setText("Ativar aviso de atualização"); panel.addView(notifications);
            notifications.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42));
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Espectro " + BuildConfig.VERSION_NAME).setView(panel).setPositiveButton("Fechar", null).create();
        install.setOnClickListener(v -> { dialog.dismiss(); installUpdate(); }); dialog.show();
    }

    private void installUpdate() {
        if (!GithubUpdater.ready(this)) { Toast.makeText(this, "Nenhuma atualização pronta. Toque em Verificar agora.", Toast.LENGTH_LONG).show(); return; }
        if (!getPackageManager().canRequestPackageInstalls()) {
            resumeInstall = true;
            openSettings(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        Toast.makeText(this, "Verificando assinatura e integridade…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                GithubUpdater.verifyApk(this, GithubUpdater.apk(this), GithubUpdater.prefs(this).getLong("pending_version", 0), GithubUpdater.prefs(this).getString("pending_sha256", ""));
                runOnUiThread(() -> {
                    if (isDestroyed() || !resumed) return;
                    try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(GithubUpdater.uri(this), "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
                    catch (ActivityNotFoundException | SecurityException e) { Toast.makeText(this, "O instalador do Android não está disponível.", Toast.LENGTH_LONG).show(); }
                });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) Toast.makeText(this, "Atualização recusada: " + e.getMessage(), Toast.LENGTH_LONG).show(); }); }
        }, "espectro-install-check").start();
    }

    private void exportReport() {
        try {
            // Snapshot before opening the picker; onPause then stops any active scan.
            if (engine.running) engine.stop("Encerrada para exportação");
            pendingReport = engine.report().toString(2).getBytes(StandardCharsets.UTF_8);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, "espectro-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".json");
            startActivityForResult(intent, EXPORT);
        } catch (Exception e) { Toast.makeText(this, "Não foi possível preparar o relatório.", Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT) return;
        byte[] report = pendingReport; pendingReport = null;
        if (result != RESULT_OK || data == null || data.getData() == null || report == null) return;
        try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
            if (stream == null) throw new IOException("Destino indisponível");
            stream.write(report); Toast.makeText(this, "Relatório salvo.", Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException e) { Toast.makeText(this, "Não foi possível salvar neste destino.", Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; if (hero != null) hero.animateWhileVisible(true); startWhenReady();
        if (updateBanner != null) updateBanner.setText("↻  " + GithubUpdater.status(this));
        if (resumeInstall) { resumeInstall = false; if (getPackageManager().canRequestPackageInstalls()) installUpdate(); }
    }
    @Override protected void onPause() {
        resumed = false;
        super.onPause();
        if (hero != null) hero.animateWhileVisible(false);
        if (engine != null && engine.running) engine.stop("Encerrada ao sair da tela");
        if (heroAnimator != null) heroAnimator.cancel();
    }
    @Override protected void onDestroy() { if (engine != null && engine.running) engine.stop("App fechado"); super.onDestroy(); }
}
