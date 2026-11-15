package br.com.espectro;

import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;

/** One observed radio identity or service, never a guaranteed unique physical device. */
final class Finding {
    final String key;
    String name;
    String kind;
    String details = "";
    final LinkedHashSet<String> methods = new LinkedHashSet<>();
    final LinkedHashMap<String, String> identifiers = new LinkedHashMap<>();
    final long firstSeen = System.currentTimeMillis();
    long lastSeen = firstSeen;
    long lastSeenElapsed = SystemClock.elapsedRealtime();
    long sourceAgeMs;
    int observations;
    Integer rssi;
    Double distanceMeters;
    Double distanceErrorMeters;
    String distanceMethod;
    boolean inferred;

    Finding(String key, String name, String kind, String method) {
        this.key = key;
        this.name = name;
        this.kind = kind;
        methods.add(method);
    }

    String via() { return String.join(" + ", methods); }
    String addresses() {
        StringBuilder b = new StringBuilder();
        for (String field : new String[]{"IP", "IPv6", "Porta", "BSSID / MAC", "MAC Bluetooth", "Endereço P2P", "UID NFC", "ID da célula"}) {
            String value = identifiers.get(field);
            if (value != null) { if (b.length() > 0) b.append(" · "); b.append(field).append(": ").append(value); }
        }
        return b.length() == 0 ? "Endereço não informado pela fonte" : b.toString();
    }
    long ageMs() { return sourceAgeMs < 0 ? -1 : sourceAgeMs + Math.max(0, SystemClock.elapsedRealtime() - lastSeenElapsed); }

    String signal() {
        if (rssi == null) return "Sinal não informado";
        boolean cellular = key.startsWith("cell:");
        boolean rsrp = key.startsWith("cell:4G") || key.startsWith("cell:5G");
        int strong = rsrp ? -90 : cellular ? -75 : -55;
        int medium = rsrp ? -110 : cellular ? -95 : -75;
        String level = rssi >= strong ? "forte" : rssi >= medium ? "médio" : "fraco";
        return "Sinal " + level + " · " + rssi + " dBm";
    }

    String distance() {
        if (distanceMeters == null) return "Distância indisponível";
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f m ± %.1f m · RTT", distanceMeters, distanceErrorMeters);
    }

    JSONObject json() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id_observado", key).put("nome_anunciado", name).put("tipo", kind)
            .put("tipo_inferido", inferred).put("encontrado_via", new JSONArray(methods))
            .put("identificadores", new JSONObject(identifiers))
            .put("detalhes", details).put("primeira_deteccao_ms", firstSeen)
            .put("ultima_deteccao_ms", lastSeen).put("observacoes", observations)
            .put("idade_da_fonte_ms", ageMs() < 0 ? JSONObject.NULL : ageMs())
            .put("rssi_dbm", rssi == null ? JSONObject.NULL : rssi)
            .put("distancia_m", distanceMeters == null ? JSONObject.NULL : distanceMeters)
            .put("desvio_padrao_distancia_m", distanceErrorMeters == null ? JSONObject.NULL : distanceErrorMeters)
            .put("metodo_distancia", distanceMethod == null ? JSONObject.NULL : distanceMethod)
            .put("direcao", JSONObject.NULL);
        return j;
    }
}
