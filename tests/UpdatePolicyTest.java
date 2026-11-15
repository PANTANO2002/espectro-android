package br.com.espectro;

public final class UpdatePolicyTest {
    private static int checks;
    private static void expect(boolean value, String description) { checks++; if (!value) throw new AssertionError(description); }
    public static void main(String[] args) {
        String repository = "PANTANO2002/espectro-android";
        String release = "https://github.com/" + repository + "/releases/download/v0.2.0/Espectro-0.2.0.apk";
        expect(UpdatePolicy.releaseAssetUrl(release, repository), "own release accepted");
        for (String unsafe : new String[]{"http://github.com/x", "https://github.com.evil.test/x", "https://evil.test/github.com/x", "https://github.com@evil.test/x", "https://user:password@github.com/x", "file:///tmp/update.apk", "https://github.com:444/x", "https://github.com/x#fragment", "https://api.github.com.evil.test/x", "https://github.com\\@evil.test/x"}) expect(!UpdatePolicy.allowedUrl(unsafe), "reject unsafe URL: " + unsafe);
        expect(UpdatePolicy.allowedUrl("https://release-assets.githubusercontent.com/signed-asset?token=example"), "signed GitHub redirect accepted");
        expect(UpdatePolicy.allowedUrl("https://objects.githubusercontent.com/asset"), "official object host accepted");
        for (String unsafe : new String[]{release.replace(repository, "other/app"), release.replace("v0.2.0", "../other"), release + "?redirect=evil", release.replace("v0.2.0", "%2e%2e"), release.replace("v0.2.0", "%2fother"), release.replace("v0.2.0", "%5cother")}) expect(!UpdatePolicy.releaseAssetUrl(unsafe, repository), "reject wrong namespace or encoded path");
        expect(UpdatePolicy.apkName("Espectro-0.2.0.apk"), "normal filename accepted");
        for (String unsafe : new String[]{"../../update.apk", "update.apk.exe", "/update.apk", "update.APK", "app\\update.apk", ""}) expect(!UpdatePolicy.apkName(unsafe), "reject misleading filename");
        expect(UpdatePolicy.sha256("a".repeat(64)), "valid sha256 accepted");
        expect(!UpdatePolicy.sha256("g".repeat(64)) && !UpdatePolicy.sha256("a".repeat(63)) && !UpdatePolicy.sha256(null), "invalid hashes rejected");
        expect(UpdatePolicy.newer(2, 1), "newer version accepted");
        expect(!UpdatePolicy.newer(2, 2) && !UpdatePolicy.newer(1, 2) && !UpdatePolicy.newer(-1, 2) && !UpdatePolicy.newer(Long.MAX_VALUE, 2), "reinstall downgrade and overflow rejected");
        System.out.println(checks + " update-policy checks passed");
    }
}
