package br.com.espectro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.hardware.usb.*;
import android.location.*;
import android.net.*;
import android.net.nsd.*;
import android.net.wifi.*;
import android.net.wifi.p2p.*;
import android.net.wifi.rtt.*;
import android.nfc.*;
import android.os.*;
import android.telephony.*;
import org.json.*;
import java.util.*;

/** Foreground continuous observation. All stored state lives on the main thread. */
@SuppressLint("MissingPermission")
final class ScanEngine {
    final LinkedHashMap<String, Finding> findings = new LinkedHashMap<>();
    final LinkedHashMap<String, String> modules = new LinkedHashMap<>();
    final LinkedHashMap<String, String> hardware = new LinkedHashMap<>();
    final ArrayList<String> log = new ArrayList<>();
    final Activity activity;
    final Handler main = new Handler(Looper.getMainLooper());
    private final long durationMs;
    private final Runnable changed;
    boolean running;
    long startedAt, deadline, endedAt;
    private WifiManager wifi;
    private BluetoothAdapter bluetooth;
    private BluetoothLeScanner ble;
    private ScanCallback bleCallback;
    private boolean bleStarted, classicRequested, receiverRegistered;
    private WifiP2pManager p2p;
    private WifiP2pManager.Channel p2pChannel;
    private NfcAdapter nfc;
    private SensorManager sensors;
    private final HashMap<Sensor, Long> sensorUpdatedAt = new HashMap<>();
    private LocationManager location;
    private GnssStatus.Callback gnss;
    private LocationListener gpsListener;
    private NsdManager nsd;
    private WifiManager.MulticastLock multicast;
    private final ArrayList<NsdManager.DiscoveryListener> discoveries = new ArrayList<>();
    private final ArrayDeque<NsdServiceInfo> resolveQueue = new ArrayDeque<>();
    private final HashSet<String> seenServices = new HashSet<>();
    private boolean resolving, rttPending;
    private long lastRttMs;
    private int generation;
    private final Runnable classicWatchdogTask = this::classicWatchdog;
    private long beganElapsed, lastNewElapsed, lastWifiSweep, lastCellSweep;
    private boolean monitoring, bleHealthy;
    private final HashSet<String> bleSeen = new HashSet<>(), classicSeen = new HashSet<>();

    ScanEngine(Activity activity, int seconds, Runnable changed) {
        this.activity = activity;
        this.durationMs = seconds * 1000L;
        this.changed = changed;
        for (String m : new String[]{"Wi-Fi", "Bluetooth LE", "Bluetooth clássico", "Wi-Fi Direct", "NFC", "Rede móvel", "Rede local", "Wi-Fi RTT", "Sensores", "GNSS", "USB"}) modules.put(m, "Aguardando");
        inventory();
    }

    boolean allowed(String permission) { return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    boolean feature(String name) { return activity.getPackageManager().hasSystemFeature(name); }
    void state(String module, String status) { modules.put(module, status); }
    void note(String text) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        log.add(time + "  " + text);
        if (log.size() > 150) log.remove(0);
    }

    private void inventory() {
        hardware.put("Celular", Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        hardware.put("Wi-Fi Aware", feature("android.hardware.wifi.aware") ? "Hardware presente; descoberta exige um serviço cooperante. Sem busca universal." : "Não anunciado pelo sistema");
        hardware.put("UWB", feature("android.hardware.uwb") ? "Hardware presente; exige sessão com dispositivo compatível. Sem varredura universal." : "Não anunciado pelo sistema");
        hardware.put("Infravermelho", feature(PackageManager.FEATURE_CONSUMER_IR) ? "Emissor presente. Não é receptor e não localiza aparelhos." : "Não anunciado pelo sistema");
        hardware.put("Câmera e microfone", "Não acionados nesta versão. Não identificam emissores de rádio.");
        hardware.put("Direção", "Indisponível para aparelhos. A bússola mede a orientação do celular, não a direção de um emissor.");
        hardware.put("Identificação", "Nomes são anunciados pelos próprios aparelhos. Endereços podem mudar; um aparelho pode gerar vários resultados.");
        hardware.put("Distância RSSI", "Modelo experimental com n=2,5. Referência assumida a 1 m: Wi-Fi 2,4 GHz −45 dBm; 5/6 GHz −50 dBm; Bluetooth −59 dBm. Calibre um emissor conhecido a 1 m para reduzir o erro. Paredes, antenas e potência podem causar erros grandes.");
    }

    void start() {
        if (running) return;
        running = true;
        startedAt = System.currentTimeMillis();
        beganElapsed = lastNewElapsed = SystemClock.elapsedRealtime();
        deadline = beganElapsed + durationMs;
        note("Busca completa iniciada; depois, acompanhamento até parar. Atualização a cada " + durationMs / 1000 + " s.");
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else activity.registerReceiver(receiver, filter);
        receiverRegistered = true;
        startWifi();
        startP2p();
        startBle();
        startNfc();
        readCells();
        startNsd();
        startSensors();
        startGnss();
        readUsb();
        // BLE and classic inquiry share a controller. Divide the session into two phases.
        main.postDelayed(this::classicPhase, 20000L);
        main.postDelayed(this::tick, 250);
        main.postDelayed(this::refreshWifi, durationMs);
        changed.run();
    }

    private void tick() {
        if (!running) return;
        long now = SystemClock.elapsedRealtime();
        boolean quiet = SignalPolicy.monitoring(now - beganElapsed, now - lastNewElapsed);
        if (quiet != monitoring) { monitoring = quiet; note(quiet ? "Sem novas identidades; acompanhamento periódico ativo." : "Novos sinais observados; ampliando a busca."); }
        for (Finding f : findings.values()) if (f.key.startsWith("nfc:") && f.ageMs() > Math.max(90000, durationMs * 3)) {
            f.active = false; f.presence = "Leitura NFC antiga; aproxime novamente";
        }
        changed.run();
        main.postDelayed(this::tick, 650);
    }

    int secondsLeft() { return running ? (int)Math.max(0, (deadline - SystemClock.elapsedRealtime() + 999) / 1000) : 0; }
    boolean monitoring() { return monitoring; }
    Collection<Finding> visible() { ArrayList<Finding> out = new ArrayList<>(); for (Finding f : findings.values()) if (f.active) out.add(f); return out; }

    private void sweep(String method, Set<String> observed, boolean immediate) {
        for (Finding f : findings.values()) if (f.methods.contains(method)) {
            if (observed.contains(f.key)) { f.misses.put(method, 0); continue; }
            int missed = f.misses.getOrDefault(method, 0) + 1; f.misses.put(method, missed);
            boolean allMissed = true;
            for (String source : f.methods) if (f.misses.getOrDefault(source, 0) < 3) allMissed = false;
            if (immediate || allMissed && SignalPolicy.absent(f.ageMs(), missed, durationMs)) {
                f.active = false; f.presence = "Sem reobservação em buscas concluídas";
            }
        }
    }

    private void timestamp(Finding f, long measuredAt) {
        long now = SystemClock.elapsedRealtime();
        measuredAt = Math.max(0, Math.min(now, measuredAt));
        f.sourceAgeMs = 0; f.lastSeenElapsed = measuredAt;
        f.lastSeen = System.currentTimeMillis() - (now - measuredAt);
        if (now - measuredAt > Math.max(90000, durationMs * 3)) { f.active = false; f.presence = "Cache antigo; aguardando nova observação"; }
    }

    private Finding find(String key, String name, String kind, String method) {
        Finding f = findings.get(key);
        if (f == null) {
            if (findings.size() >= 2000) return null;
            f = new Finding(key, name, kind, method);
            findings.put(key, f);
            lastNewElapsed = SystemClock.elapsedRealtime();
        } else {
            f.methods.add(method);
            if (name != null && !name.isEmpty() && !name.startsWith("Sem nome")) f.name = name;
        }
        f.lastSeen = System.currentTimeMillis();
        f.lastSeenElapsed = SystemClock.elapsedRealtime();
        f.observations++;
        if (!f.active) lastNewElapsed = SystemClock.elapsedRealtime();
        f.active = true; f.presence = "Observado"; f.sourceAgeMs = 0;
        for (String source : f.methods) f.misses.put(source, 0);
        return f;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!running) return;
            String action = intent.getAction();
            try {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                    readWifi(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false));
                } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    if (!classicRequested) return;
                    BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (d != null) bluetoothFinding(d, intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE), null, false);
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action) && classicRequested) {
                    state("Bluetooth clássico", "Busca concluída; somente aparelhos em modo visível respondem");
                    classicRequested = false;
                    sweep("Bluetooth clássico", classicSeen, false);
                    resumeBlePhase();
                } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) requestPeers(true);
            } catch (RuntimeException e) { note("Atualização bloqueada: " + e.getClass().getSimpleName()); }
        }
    };

    private void startWifi() {
        wifi = (WifiManager)activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi == null || !feature(PackageManager.FEATURE_WIFI)) { state("Wi-Fi", "Sem hardware"); return; }
        if (!allowed(Manifest.permission.ACCESS_FINE_LOCATION)) { state("Wi-Fi", "Permissão de localização precisa necessária"); return; }
        try {
            if (!wifi.isWifiEnabled()) { state("Wi-Fi", "Ative o Wi-Fi nas configurações"); return; }
            readWifi(false);
            state("Wi-Fi", wifi.startScan() ? "Buscando pontos de acesso" : "Solicitação limitada pelo Android; cache sinalizado nos resultados");
        } catch (RuntimeException e) { state("Wi-Fi", "Indisponível: verifique localização e permissões"); }
    }

    private void refreshWifi() {
        if (!running) return;
        startWifi();
        readCells(); readUsb();
        if (p2p == null || p2pChannel == null) startP2p();
        else try { p2p.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { if (running) requestPeers(); }
            @Override public void onFailure(int code) { if (running) state("Wi-Fi Direct", "Atualização indisponível (" + code + "); mantendo cache"); }
        }); } catch (RuntimeException ignored) { }
        if (discoveries.isEmpty()) startNsd();
        if (nfc == null || !nfc.isEnabled()) startNfc();
        deadline = SystemClock.elapsedRealtime() + durationMs;
        main.postDelayed(this::refreshWifi, durationMs);
    }

    private void readWifi(boolean fresh) {
        if (wifi == null || !running) return;
        List<android.net.wifi.ScanResult> results = wifi.getScanResults();
        HashSet<String> observed = new HashSet<>();
        long newest = 0;
        for (android.net.wifi.ScanResult r : results) {
            if (r.BSSID == null || r.BSSID.isEmpty()) continue;
            String name = r.SSID == null || r.SSID.isEmpty() ? "Rede sem nome anunciado" : r.SSID;
            String key = "wifi:" + r.BSSID;
            long measuredAt = r.timestamp / 1000;
            newest = Math.max(newest, measuredAt);
            if (SystemClock.elapsedRealtime() - measuredAt < Math.max(35000, durationMs)) observed.add(key);
            Finding previous = findings.get(key);
            if (previous != null && measuredAt <= previous.lastSeenElapsed) continue;
            Finding f = find(key, name, "Ponto de acesso / hotspot", "Wi-Fi");
            if (f == null) continue;
            f.sample(r.level, measuredAt);
            timestamp(f, measuredAt);
            f.details = r.frequency + " MHz · " + r.BSSID + "\n" + r.capabilities;
            f.identifiers.put("BSSID / MAC", r.BSSID);
            f.identifiers.put("SSID", name);
            f.identifiers.put("Frequência MHz", String.valueOf(r.frequency));
            f.identifiers.put("Largura de canal (código Android)", String.valueOf(r.channelWidth));
            f.identifiers.put("Segurança anunciada", r.capabilities);
            if (Build.VERSION.SDK_INT >= 30) f.identifiers.put("Padrão Wi-Fi (código Android)", String.valueOf(r.getWifiStandard()));
        }
        long now = SystemClock.elapsedRealtime();
        if (fresh && now - lastWifiSweep >= 30000 && (results.isEmpty() || newest > lastWifiSweep)) {
            sweep("Wi-Fi", observed, false); lastWifiSweep = now;
        }
        rangeWifi(results);
    }

    private void rangeWifi(List<android.net.wifi.ScanResult> results) {
        if (Build.VERSION.SDK_INT < 28 || !feature("android.hardware.wifi.rtt")) { state("Wi-Fi RTT", "Sem suporte anunciado; usando apenas estimativa experimental RSSI"); return; }
        WifiRttManager manager = (WifiRttManager)activity.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        if (manager == null || !manager.isAvailable()) { state("Wi-Fi RTT", "Indisponível no estado atual do rádio"); return; }
        if (rttPending || (lastRttMs != 0 && SystemClock.elapsedRealtime() - lastRttMs < 20000)) return;
        ArrayList<android.net.wifi.ScanResult> peers = new ArrayList<>();
        for (android.net.wifi.ScanResult r : results) if (r.is80211mcResponder() && SystemClock.elapsedRealtime() - r.timestamp / 1000 < 60000) peers.add(r);
        if (peers.isEmpty()) { state("Wi-Fi RTT", "Nenhum ponto de acesso compatível observado"); return; }
        try {
            List<android.net.wifi.ScanResult> batch = peers.subList(0, Math.min(peers.size(), RangingRequest.getMaxPeers()));
            RangingRequest request = new RangingRequest.Builder().addAccessPoints(batch).build();
            rttPending = true;
            lastRttMs = SystemClock.elapsedRealtime();
            state("Wi-Fi RTT", "Medindo distância de pontos compatíveis");
            manager.startRanging(request, activity.getMainExecutor(), new RangingResultCallback() {
                @Override public void onRangingFailure(int code) { if (running) { rttPending = false; state("Wi-Fi RTT", "Medição não disponível (" + code + ")"); } }
                @Override public void onRangingResults(List<RangingResult> values) {
                    if (!running) return;
                    rttPending = false;
                    int measured = 0;
                    for (RangingResult r : values) if (r.getStatus() == RangingResult.STATUS_SUCCESS && r.getMacAddress() != null && r.getDistanceMm() >= 0) {
                        Finding f = findings.get("wifi:" + r.getMacAddress().toString());
                        if (f == null) continue;
                        f.distanceMeters = r.getDistanceMm() / 1000.0;
                        f.distanceErrorMeters = r.getDistanceStdDevMm() / 1000.0;
                        f.distanceMethod = "Wi-Fi RTT IEEE 802.11mc";
                        f.rttElapsed = SystemClock.elapsedRealtime();
                        measured++;
                    }
                    state("Wi-Fi RTT", measured + " distância(s) medida(s); sem informação de direção");
                }
            });
        } catch (RuntimeException e) { rttPending = false; state("Wi-Fi RTT", "Medição bloqueada pelo sistema"); }
    }

    private void startP2p() {
        if (!feature(PackageManager.FEATURE_WIFI_DIRECT)) { state("Wi-Fi Direct", "Sem suporte"); return; }
        try {
            p2p = (WifiP2pManager)activity.getSystemService(Context.WIFI_P2P_SERVICE);
            if (p2p == null) { state("Wi-Fi Direct", "Serviço indisponível"); return; }
            p2pChannel = p2p.initialize(activity, Looper.getMainLooper(), () -> { if (running) state("Wi-Fi Direct", "Canal desconectado"); });
            p2p.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() { if (running) { state("Wi-Fi Direct", "Procurando participantes visíveis"); requestPeers(); } }
                @Override public void onFailure(int reason) { if (running) state("Wi-Fi Direct", "Não disponível (código " + reason + ")"); }
            });
        } catch (RuntimeException e) { state("Wi-Fi Direct", "Verifique Wi-Fi, localização e permissão de dispositivos próximos"); }
    }

    private void requestPeers() { requestPeers(false); }
    private void requestPeers(boolean changedList) {
        if (p2p == null || p2pChannel == null) return;
        p2p.requestPeers(p2pChannel, list -> {
            if (!running) return;
            HashSet<String> peers = new HashSet<>();
            for (WifiP2pDevice d : list.getDeviceList()) {
                if (d.deviceAddress == null) continue;
                peers.add("p2p:" + d.deviceAddress);
                Finding f = find("p2p:" + d.deviceAddress, empty(d.deviceName, "Sem nome anunciado"), "Participante Wi-Fi Direct", "Wi-Fi Direct");
                if (f != null) {
                    f.sourceAgeMs = -1;
                    f.presence = "Lista P2P do Android; idade da presença desconhecida";
                    f.identifiers.put("Endereço P2P", d.deviceAddress);
                    f.identifiers.put("Tipo WPS", empty(d.primaryDeviceType, "não informado"));
                    f.details = d.deviceAddress + "\nTipo WPS anunciado: " + empty(d.primaryDeviceType, "não informado") + " · cache do sistema; presença atual não garantida";
                }
            }
            if (changedList) sweep("Wi-Fi Direct", peers, true);
        });
    }

    private void startBle() {
        BluetoothManager manager = (BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetooth = manager == null ? null : manager.getAdapter();
        if (bluetooth == null) { state("Bluetooth LE", "Sem hardware"); state("Bluetooth clássico", "Sem hardware"); return; }
        try {
            if (!bluetooth.isEnabled()) { state("Bluetooth LE", "Ative o Bluetooth"); state("Bluetooth clássico", "Ative o Bluetooth"); return; }
            ble = bluetooth.getBluetoothLeScanner();
            if (ble == null) { state("Bluetooth LE", "Scanner indisponível"); return; }
            bleSeen.clear(); bleHealthy = true;
            bleCallback = new ScanCallback() {
                @Override public void onScanResult(int type, android.bluetooth.le.ScanResult r) { main.post(() -> { if (running) bluetoothFinding(r.getDevice(), r.getRssi(), r.getScanRecord(), true); }); }
                @Override public void onBatchScanResults(List<android.bluetooth.le.ScanResult> values) { for (android.bluetooth.le.ScanResult r : values) onScanResult(0, r); }
                @Override public void onScanFailed(int code) { main.post(() -> { bleHealthy = false; bleStarted = false; if (running) state("Bluetooth LE", "Falha de varredura (" + code + ")"); }); }
            };
            ble.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), bleCallback);
            bleStarted = true;
            state("Bluetooth LE", "Escutando anúncios BLE");
            state("Bluetooth clássico", "Aguardando segunda fase da varredura");
        } catch (RuntimeException e) { state("Bluetooth LE", "Permissão ou Bluetooth indisponível"); }
    }

    private void bluetoothFinding(BluetoothDevice device, int rssi, ScanRecord record, boolean isBle) {
        try {
            String address = device.getAddress();
            if (address == null) return;
            String name = record != null ? record.getDeviceName() : null;
            if (name == null || name.isEmpty()) name = device.getName();
            String kind = "Dispositivo Bluetooth · tipo não anunciado";
            BluetoothClass c = device.getBluetoothClass();
            if (c != null) switch (c.getMajorDeviceClass()) {
                case BluetoothClass.Device.Major.AUDIO_VIDEO: kind = "Áudio / vídeo (classe anunciada)"; break;
                case BluetoothClass.Device.Major.PHONE: kind = "Telefone (classe anunciada)"; break;
                case BluetoothClass.Device.Major.COMPUTER: kind = "Computador (classe anunciada)"; break;
                case BluetoothClass.Device.Major.PERIPHERAL: kind = "Periférico (classe anunciada)"; break;
                case BluetoothClass.Device.Major.WEARABLE: kind = "Vestível (classe anunciada)"; break;
                case BluetoothClass.Device.Major.HEALTH: kind = "Saúde (classe anunciada)"; break;
            }
            Finding f = find("bt:" + address, empty(name, "Sem nome anunciado"), kind, isBle ? "Bluetooth LE" : "Bluetooth clássico");
            if (f == null) return;
            if (!kind.contains("tipo não anunciado")) f.kind = kind;
            f.identifiers.put("MAC Bluetooth", address);
            f.identifiers.put("Tipo de transporte (código Android)", String.valueOf(device.getType()));
            if (c != null) f.identifiers.put("Classe Bluetooth", String.format(Locale.ROOT, "0x%04X", c.getDeviceClass()));
            (isBle ? bleSeen : classicSeen).add(f.key);
            if (rssi != Short.MIN_VALUE) f.sample(rssi, SystemClock.elapsedRealtime());
            String details = address;
            if (record != null) {
                if (record.getServiceUuids() != null) { details += "\nServiços: " + record.getServiceUuids(); f.identifiers.put("UUIDs de serviço BLE", record.getServiceUuids().toString()); }
                if (record.getBytes() != null) f.identifiers.put("Anúncio BLE hexadecimal", hex(record.getBytes()));
                if (record.getServiceData() != null) for (Map.Entry<ParcelUuid, byte[]> entry : record.getServiceData().entrySet()) f.identifiers.put("Dados do serviço " + entry.getKey(), hex(entry.getValue()));
                android.util.SparseArray<byte[]> manufacturers = record.getManufacturerSpecificData();
                if (manufacturers != null && manufacturers.size() > 0) {
                    StringJoiner ids = new StringJoiner(", ");
                    for (int i = 0; i < manufacturers.size(); i++) {
                        String manufacturer = String.format(Locale.ROOT, "0x%04X", manufacturers.keyAt(i));
                        ids.add(manufacturer);
                        f.identifiers.put("Dados fabricante " + manufacturer, hex(manufacturers.valueAt(i)));
                    }
                    details += "\nIDs de fabricante anunciados: " + ids + " (não confirmam o modelo)";
                }
            }
            f.details = details;
        } catch (SecurityException e) { state("Bluetooth LE", "Permissão revogada"); }
    }

    private void stopBle() {
        if (bleStarted && ble != null && bleCallback != null) try { ble.stopScan(bleCallback); } catch (RuntimeException ignored) { }
        bleStarted = false;
        if (modules.get("Bluetooth LE").startsWith("Escutando")) state("Bluetooth LE", "Fase BLE concluída");
    }

    private void startClassic() {
        if (bluetooth == null) return;
        try {
            if (!bluetooth.isEnabled()) { state("Bluetooth clássico", "Bluetooth desligado"); return; }
            if (bluetooth.isDiscovering()) { state("Bluetooth clássico", "Outra busca já está ativa; tente em uma nova sessão"); return; }
            classicRequested = bluetooth.startDiscovery();
            classicSeen.clear();
            state("Bluetooth clássico", classicRequested ? "Procurando aparelhos em modo visível" : "Busca não iniciada pelo sistema");
        } catch (RuntimeException e) { state("Bluetooth clássico", "Permissão ou rádio indisponível"); }
    }

    private void classicPhase() {
        if (!running) return;
        try { if (bleStarted && bleHealthy && bluetooth != null && bluetooth.isEnabled()) sweep("Bluetooth LE", bleSeen, false); } catch (RuntimeException ignored) { }
        stopBle();
        startClassic();
        if (classicRequested) main.postDelayed(classicWatchdogTask, 16000);
        else resumeBlePhase();
    }

    private void classicWatchdog() {
        if (!running || !classicRequested) return;
        classicRequested = false;
        if (bluetooth != null) try { bluetooth.cancelDiscovery(); } catch (RuntimeException ignored) { }
        resumeBlePhase();
    }

    private void resumeBlePhase() {
        if (!running) return;
        main.removeCallbacks(classicWatchdogTask);
        startBle();
        main.postDelayed(this::classicPhase, 20000);
    }

    private void startNfc() {
        try {
            nfc = NfcAdapter.getDefaultAdapter(activity);
            if (nfc == null) { state("NFC", "Ausente nesta variante do celular"); return; }
            if (!nfc.isEnabled()) { state("NFC", "Ative o NFC nas configurações"); return; }
            nfc.enableReaderMode(activity, tag -> main.post(() -> {
                if (!running) return;
                String id = hex(tag.getId());
                Finding f = find("nfc:" + id, "Tag NFC " + (id.length() > 8 ? id.substring(0, 8) : id), "Tag / cartão NFC", "NFC por aproximação");
                if (f != null) {
                    f.details = "Tecnologias: " + String.join(", ", tag.getTechList()) + "\nLido a poucos centímetros; UID não confirma identidade.";
                    f.identifiers.put("UID NFC", id);
                    f.identifiers.put("Tecnologias NFC", String.join(", ", tag.getTechList()));
                }
            }), NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_NFC_BARCODE | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null);
            state("NFC", "Aproxime uma tag ou cartão a poucos centímetros");
        } catch (RuntimeException e) { state("NFC", "Leitor não disponível"); }
    }

    private void refreshCells() { if (running) { readCells(); main.postDelayed(this::refreshCells, 30000); } }

    private void readCells() {
        if (!feature(PackageManager.FEATURE_TELEPHONY)) { state("Rede móvel", "Sem modem de telefonia"); return; }
        if (!allowed(Manifest.permission.ACCESS_FINE_LOCATION)) { state("Rede móvel", "Localização precisa necessária"); return; }
        try {
            TelephonyManager tm = (TelephonyManager)activity.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return;
            cellResults(tm.getAllCellInfo(), false);
            state("Rede móvel", "Células informadas pelo modem; não detecta outros celulares");
            if (Build.VERSION.SDK_INT >= 29) tm.requestCellInfoUpdate(activity.getMainExecutor(), new TelephonyManager.CellInfoCallback() {
                @Override public void onCellInfo(List<CellInfo> infos) { if (running) cellResults(infos, true); }
                @Override public void onError(int code, Throwable detail) { if (running) state("Rede móvel", "Atualização limitada; dados do modem podem estar em cache"); }
            });
        } catch (RuntimeException e) { state("Rede móvel", "Modem ou permissão indisponível"); }
    }

    private void cellResults(List<CellInfo> infos, boolean updated) {
        if (infos == null) return;
        HashSet<String> observed = new HashSet<>();
        long newest = 0;
        for (CellInfo c : infos) {
            String rat = "", identity = "", operator = "";
            int dbm = Integer.MAX_VALUE;
            if (c instanceof CellInfoLte) {
                CellInfoLte v = (CellInfoLte)c; CellIdentityLte id = v.getCellIdentity();
                if (id.getCi() == Integer.MAX_VALUE) continue;
                rat = "4G LTE"; identity = "CI " + id.getCi() + " · TAC " + id.getTac() + " · PCI " + id.getPci(); dbm = v.getCellSignalStrength().getDbm();
                if (Build.VERSION.SDK_INT >= 28) operator = String.valueOf(id.getOperatorAlphaLong()) + " · " + id.getMccString() + "/" + id.getMncString();
            } else if (Build.VERSION.SDK_INT >= 29 && c instanceof CellInfoNr) {
                CellInfoNr v = (CellInfoNr)c; CellIdentityNr id = (CellIdentityNr)v.getCellIdentity();
                if (id.getNci() == Long.MAX_VALUE) continue;
                rat = "5G NR"; identity = "NCI " + id.getNci() + " · TAC " + id.getTac() + " · PCI " + id.getPci(); dbm = v.getCellSignalStrength().getDbm();
                operator = String.valueOf(id.getOperatorAlphaLong()) + " · " + id.getMccString() + "/" + id.getMncString();
            } else if (c instanceof CellInfoGsm) {
                CellInfoGsm v = (CellInfoGsm)c; CellIdentityGsm id = v.getCellIdentity();
                if (id.getCid() == Integer.MAX_VALUE) continue;
                rat = "2G GSM"; identity = "CID " + id.getCid() + " · LAC " + id.getLac() + " · PLMN " + id.getMcc() + "/" + id.getMnc(); dbm = v.getCellSignalStrength().getDbm();
            } else if (c instanceof CellInfoWcdma) {
                CellInfoWcdma v = (CellInfoWcdma)c; CellIdentityWcdma id = v.getCellIdentity();
                if (id.getCid() == Integer.MAX_VALUE) continue;
                rat = "3G WCDMA"; identity = "CID " + id.getCid() + " · LAC " + id.getLac() + " · PLMN " + id.getMcc() + "/" + id.getMnc(); dbm = v.getCellSignalStrength().getDbm();
            } else continue;
            String key = "cell:" + rat + ":" + operator + ":" + identity;
            Finding previous = findings.get(key);
            long measuredAt = c.getTimeStamp() / 1000000;
            newest = Math.max(newest, measuredAt);
            if (SystemClock.elapsedRealtime() - measuredAt < Math.max(30000, durationMs)) observed.add(key);
            if (previous != null && measuredAt <= previous.lastSeenElapsed) continue;
            Finding f = find(key, "Célula " + rat + (c.isRegistered() ? " · em uso" : " · vizinha"), "Infraestrutura de rede móvel", "Modem celular");
            if (f == null) continue;
            if (dbm != Integer.MAX_VALUE) f.rssi = dbm;
            f.details = operator + "\n" + identity;
            f.identifiers.put("ID da célula", identity);
            f.identifiers.put("Operadora / PLMN", operator);
            f.identifiers.put("Tecnologia de rede", rat);
            timestamp(f, measuredAt);
        }
        long now = SystemClock.elapsedRealtime();
        if (updated && now - lastCellSweep >= 30000 && (infos.isEmpty() || newest > lastCellSweep)) {
            sweep("Modem celular", observed, false); lastCellSweep = now;
        }
    }

    private void startNsd() {
        try {
            ConnectivityManager cm = (ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { state("Rede local", "Conecte-se a uma rede Wi-Fi para descobrir serviços mDNS"); return; }
            LinkProperties links = cm.getLinkProperties(cm.getActiveNetwork());
            if (links != null) {
                hardware.put("IPs do próprio celular nesta rede", links.getLinkAddresses().toString());
                hardware.put("Rotas / gateway da rede", links.getRoutes().toString() + " · o gateway não confirma o IP do ponto de acesso");
                hardware.put("Servidores DNS da rede", links.getDnsServers().toString());
            }
            nsd = (NsdManager)activity.getSystemService(Context.NSD_SERVICE);
            if (nsd == null) { state("Rede local", "Serviço indisponível"); return; }
            if (wifi != null) { multicast = wifi.createMulticastLock("espectro-mdns"); multicast.setReferenceCounted(false); multicast.acquire(); }
            for (String type : new String[]{"_http._tcp.", "_https._tcp.", "_googlecast._tcp.", "_airplay._tcp.", "_ipp._tcp.", "_printer._tcp.", "_smb._tcp.", "_ssh._tcp.", "_esphomelib._tcp."}) discover(type);
            state("Rede local", "Buscando 9 tipos de serviço mDNS na rede conectada; não lista todos os clientes");
        } catch (RuntimeException e) { state("Rede local", "Descoberta indisponível"); }
    }

    private void discover(String type) {
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String s) { }
            @Override public void onDiscoveryStopped(String s) { }
            @Override public void onStartDiscoveryFailed(String s, int code) { main.post(() -> { if (running) note("mDNS " + s + ": indisponível (" + code + ")"); }); }
            @Override public void onStopDiscoveryFailed(String s, int code) { }
            @Override public void onServiceLost(NsdServiceInfo info) { main.post(() -> {
                if (!running) return;
                String key = info.getServiceName() + "|" + info.getServiceType(); seenServices.remove(key);
                Finding f = findings.get("lan:" + key);
                if (f != null) { f.active = false; f.presence = "Serviço retirado da descoberta mDNS"; }
            }); }
            @Override public void onServiceFound(NsdServiceInfo info) {
                main.post(() -> {
                    if (!running || seenServices.size() >= 2000) return;
                    String key = info.getServiceName() + "|" + info.getServiceType();
                    if (!seenServices.add(key)) return;
                    Finding f = find("lan:" + key, info.getServiceName(), serviceKind(info.getServiceType()), "Rede local · mDNS");
                    if (f != null) { f.details = "Serviço anunciado: " + info.getServiceType() + "\nEstar na mesma rede não confirma proximidade física."; f.identifiers.put("Tipo de serviço mDNS", info.getServiceType()); }
                    resolveQueue.add(info);
                    resolveNext();
                });
            }
        };
        discoveries.add(listener);
        try { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener); } catch (RuntimeException e) { note("mDNS " + type + ": limite do sistema."); }
    }

    private void resolveNext() {
        if (!running || resolving || resolveQueue.isEmpty()) return;
        resolving = true;
        NsdServiceInfo info = resolveQueue.remove();
        final int token = ++generation;
        Runnable timeout = () -> { if (running && token == generation) { resolving = false; resolveNext(); } };
        main.postDelayed(timeout, 4000);
        try {
            nsd.resolveService(info, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo service, int code) { main.post(() -> complete(null)); }
                @Override public void onServiceResolved(NsdServiceInfo service) { main.post(() -> complete(service)); }
                private void complete(NsdServiceInfo resolved) {
                    if (!running || token != generation) return;
                    main.removeCallbacks(timeout);
                    if (resolved != null) {
                        Finding f = findings.get("lan:" + info.getServiceName() + "|" + info.getServiceType());
                        if (f != null) {
                            if (resolved.getHost() != null) {
                                String address = resolved.getHost().getHostAddress();
                                f.details += "\n" + address + ":" + resolved.getPort();
                                f.identifiers.put(resolved.getHost() instanceof java.net.Inet6Address ? "IPv6" : "IP", address);
                            }
                            f.identifiers.put("Porta", String.valueOf(resolved.getPort()));
                            for (Map.Entry<String, byte[]> entry : resolved.getAttributes().entrySet()) f.identifiers.put("mDNS TXT " + entry.getKey(), hex(entry.getValue()));
                        }
                    }
                    resolving = false;
                    resolveNext();
                }
            });
        } catch (RuntimeException e) { main.removeCallbacks(timeout); resolving = false; main.post(this::resolveNext); }
    }

    private static String serviceKind(String type) {
        if (type.contains("googlecast")) return "Receptor Google Cast (serviço anunciado)";
        if (type.contains("airplay")) return "Receptor AirPlay (serviço anunciado)";
        if (type.contains("ipp") || type.contains("printer")) return "Impressora (serviço anunciado)";
        if (type.contains("smb")) return "Compartilhamento de arquivos (serviço anunciado)";
        if (type.contains("esphome")) return "Automação ESPHome (serviço anunciado)";
        return "Dispositivo com serviço " + type;
    }

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        @Override public void onSensorChanged(SensorEvent e) {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            Long previous = sensorUpdatedAt.get(e.sensor);
            if (previous != null && now - previous < 1000) return;
            sensorUpdatedAt.put(e.sensor, now);
            StringJoiner values = new StringJoiner(", ");
            for (int i = 0; i < Math.min(6, e.values.length); i++) values.add(String.format(Locale.ROOT, "%.2f", e.values[i]));
            hardware.put(sensorKey(e.sensor), values + " · precisão Android " + e.accuracy + " · " + e.sensor.getVendor());
        }
    };

    private static String sensorKey(Sensor s) { return s.getName() + " · tipo " + s.getType(); }
    private void startSensors() {
        sensors = (SensorManager)activity.getSystemService(Context.SENSOR_SERVICE);
        if (sensors == null) { state("Sensores", "Serviço indisponível"); return; }
        int active = 0;
        List<Sensor> all = sensors.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : all) {
            String key = sensorKey(s);
            if (s.getReportingMode() != Sensor.REPORTING_MODE_CONTINUOUS && s.getReportingMode() != Sensor.REPORTING_MODE_ON_CHANGE) {
                hardware.put(key, "Presente; sensor de gatilho não acionado"); continue;
            }
            try {
                boolean registered = sensors.registerListener(sensorListener, s, SensorManager.SENSOR_DELAY_NORMAL, main);
                hardware.put(key, registered ? "Aguardando leitura · " + s.getVendor() : "Presente; leitura não liberada");
                if (registered) active++;
            } catch (RuntimeException e) { hardware.put(key, "Presente; permissão específica ou acesso restrito"); }
        }
        state("Sensores", active + " de " + all.size() + " inscritos; leituras de contexto não localizam aparelhos");
    }

    private void startGnss() {
        location = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
        if (location == null || !feature(PackageManager.FEATURE_LOCATION_GPS)) { state("GNSS", "Sem suporte"); return; }
        if (!allowed(Manifest.permission.ACCESS_FINE_LOCATION)) { state("GNSS", "Localização precisa necessária"); return; }
        try {
            if (!location.isProviderEnabled(LocationManager.GPS_PROVIDER)) { state("GNSS", "Ative a localização do celular"); return; }
            gnss = new GnssStatus.Callback() {
                @Override public void onSatelliteStatusChanged(GnssStatus status) {
                    if (!running) return;
                    int used = 0;
                    for (int i = 0; i < status.getSatelliteCount(); i++) if (status.usedInFix(i)) used++;
                    state("GNSS", status.getSatelliteCount() + " satélites visíveis; " + used + " usados na posição. Não detecta celulares.");
                }
            };
            location.registerGnssStatusCallback(gnss, main);
            gpsListener = new LocationListener() {
                @Override public void onLocationChanged(Location value) { /* Coordinates deliberately not stored. */ }
                @Override public void onStatusChanged(String p, int s, Bundle extras) { }
                @Override public void onProviderEnabled(String p) { }
                @Override public void onProviderDisabled(String p) { if (running) state("GNSS", "Localização desativada"); }
            };
            location.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, gpsListener, Looper.getMainLooper());
            state("GNSS", "Aguardando satélites; coordenadas não são armazenadas");
        } catch (RuntimeException e) { state("GNSS", "Localização não disponível"); }
    }

    private void readUsb() {
        UsbManager manager = (UsbManager)activity.getSystemService(Context.USB_SERVICE);
        if (manager == null) { state("USB", "Indisponível"); return; }
        int total = 0;
        HashSet<String> connected = new HashSet<>();
        for (UsbDevice d : manager.getDeviceList().values()) {
            connected.add("usb:" + d.getDeviceName());
            Finding f = find("usb:" + d.getDeviceName(), empty(d.getProductName(), "Dispositivo USB"), "Periférico conectado por cabo", "USB / OTG");
            if (f != null) {
                f.details = "Fabricante: " + empty(d.getManufacturerName(), "não informado") + "\nVID " + d.getVendorId() + " · PID " + d.getProductId() + " · classe " + d.getDeviceClass();
                f.identifiers.put("VID USB", String.format(Locale.ROOT, "0x%04X", d.getVendorId()));
                f.identifiers.put("PID USB", String.format(Locale.ROOT, "0x%04X", d.getProductId()));
                f.identifiers.put("Caminho USB", d.getDeviceName());
                if (manager.hasPermission(d)) try { f.identifiers.put("Serial USB", empty(d.getSerialNumber(), "não informado")); } catch (SecurityException ignored) { }
            }
            total++;
        }
        sweep("USB / OTG", connected, true);
        state("USB", total + " periférico(s) conectado(s); não procura aparelhos sem fio");
    }

    void stop(String reason) {
        if (!running) return;
        running = false;
        endedAt = System.currentTimeMillis();
        main.removeCallbacksAndMessages(null);
        stopBle();
        if (classicRequested && bluetooth != null) try { bluetooth.cancelDiscovery(); } catch (RuntimeException ignored) { }
        if (p2p != null && p2pChannel != null) {
            try { p2p.stopPeerDiscovery(p2pChannel, null); } catch (RuntimeException ignored) { }
            if (Build.VERSION.SDK_INT >= 27) try { p2pChannel.close(); } catch (RuntimeException ignored) { }
        }
        if (nfc != null) try { nfc.disableReaderMode(activity); } catch (RuntimeException ignored) { }
        if (sensors != null) sensors.unregisterListener(sensorListener);
        if (location != null) {
            if (gnss != null) try { location.unregisterGnssStatusCallback(gnss); } catch (RuntimeException ignored) { }
            if (gpsListener != null) try { location.removeUpdates(gpsListener); } catch (RuntimeException ignored) { }
        }
        if (nsd != null) for (NsdManager.DiscoveryListener d : discoveries) try { nsd.stopServiceDiscovery(d); } catch (RuntimeException ignored) { }
        if (multicast != null && multicast.isHeld()) multicast.release();
        if (receiverRegistered) { activity.unregisterReceiver(receiver); receiverRegistered = false; }
        note(reason + ". " + findings.size() + " identificadores/serviços observados; não equivalem a aparelhos únicos.");
        changed.run();
    }

    JSONObject report() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "Espectro " + BuildConfig.VERSION_NAME).put("estado", running ? "em andamento" : "encerrada")
            .put("celular", Build.MANUFACTURER + " " + Build.MODEL).put("android", Build.VERSION.RELEASE)
            .put("inicio_ms", startedAt).put("fim_ms", endedAt == 0 ? JSONObject.NULL : endedAt)
            .put("intervalo_atualizacao_s", durationMs / 1000)
            .put("nota", "Identificadores/serviços não equivalem a aparelhos únicos. RSSI: distância experimental, n=2.5, potência de referência assumida ou calibrada. RTT tem prioridade por 60 s. Direção indisponível. no_radar=false preserva histórico sem reobservação; não prova distância física.")
            .put("modulos", new JSONObject(modules)).put("hardware_e_ultimas_leituras", new JSONObject(hardware))
            .put("registro", new JSONArray(log));
        JSONArray items = new JSONArray();
        for (Finding f : findings.values()) items.put(f.json());
        return root.put("resultados", items);
    }

    static String empty(String s, String fallback) { return s == null || s.isEmpty() ? fallback : s; }
    static String hex(byte[] bytes) { StringBuilder b = new StringBuilder(); if (bytes != null) for (byte value : bytes) b.append(String.format(Locale.ROOT, "%02X", value & 255)); return b.toString(); }
}
