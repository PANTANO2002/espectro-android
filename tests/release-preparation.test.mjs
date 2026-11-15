import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, copyFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

function fixture(mutate) {
    const root = mkdtempSync(resolve(tmpdir(), 'espectro-release-'));
    try {
        for (const dir of ['app', '.release', 'scripts']) mkdirSync(resolve(root, dir));
        copyFileSync(new URL('../scripts/prepare-release.mjs', import.meta.url), resolve(root, 'scripts/prepare-release.mjs'));
        const bytes = Buffer.alloc(1500, 42);
        const manifest = { packageName: 'br.com.espectro', minSdk: 26, versionCode: 4, versionName: '0.3.1', apkName: 'Espectro-0.3.1.apk', sha256: createHash('sha256').update(bytes).digest('hex') };
        if (mutate) mutate(manifest);
        writeFileSync(resolve(root, 'app/build.gradle'), "versionCode 4\nversionName '0.3.1'\n");
        writeFileSync(resolve(root, '.release/manifest.json'), JSON.stringify(manifest));
        writeFileSync(resolve(root, '.release/app.apk.b64'), bytes.toString('base64'));
        const run = spawnSync(process.execPath, [resolve(root, 'scripts/prepare-release.mjs')], { encoding: 'utf8' });
        return { status: run.status, output: run.status === 0 ? readFileSync(resolve(root, 'build/release/Espectro-0.3.1.apk')) : null, bytes };
    } finally { rmSync(root, { recursive: true, force: true }); }
}
test('prepared payload preserves exact signed bytes (signature checked separately)', () => {
    const result = fixture(); assert.equal(result.status, 0); assert.deepEqual(result.output, result.bytes);
});
test('tampered APK hash blocks preparation', () => assert.notEqual(fixture(m => { m.sha256 = '0'.repeat(64); }).status, 0));
test('source and manifest versions must agree', () => assert.notEqual(fixture(m => { m.versionCode = 5; }).status, 0));
test('manifest cannot escape the release directory', () => assert.notEqual(fixture(m => { m.apkName = '../app.apk'; }).status, 0));
