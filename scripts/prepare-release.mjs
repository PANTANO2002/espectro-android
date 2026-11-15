import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

// The APK is already signed locally. The signing key never enters the repository.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const manifest = JSON.parse(readFileSync(resolve(root, '.release/manifest.json'), 'utf8'));
if (manifest.packageName !== 'br.com.espectro' || manifest.minSdk !== 26
    || !Number.isSafeInteger(manifest.versionCode) || manifest.versionCode < 4
    || !/^\d+\.\d+\.\d+$/.test(manifest.versionName)
    || manifest.apkName !== `Espectro-${manifest.versionName}.apk`
    || !/^[a-f0-9]{64}$/.test(manifest.sha256)) throw new Error('Invalid release manifest');
const gradle = readFileSync(resolve(root, 'app/build.gradle'), 'utf8');
if (!new RegExp(`versionCode\\s+${manifest.versionCode}\\b`).test(gradle)
    || !gradle.includes(`versionName '${manifest.versionName}'`)) throw new Error('Source version mismatch');
const encoded = readFileSync(resolve(root, '.release/app.apk.b64'), 'utf8').replace(/\s/g, '');
if (!encoded.length || encoded.length > 112 * 1024 * 1024 || encoded.length % 4
    || !/^[A-Za-z0-9+/]*={0,2}$/.test(encoded)) throw new Error('Invalid base64 APK');
const apk = Buffer.from(encoded, 'base64');
if (apk.length < 1024 || apk.length > 80 * 1024 * 1024
    || createHash('sha256').update(apk).digest('hex') !== manifest.sha256) throw new Error('APK hash mismatch');
const output = resolve(root, 'build/release');
mkdirSync(output, { recursive: true });
writeFileSync(resolve(output, manifest.apkName), apk);
writeFileSync(resolve(output, 'update.json'), JSON.stringify(manifest, null, 2) + '\n');
writeFileSync(resolve(output, `SHA256SUMS-${manifest.versionName}.txt`), `${manifest.sha256}  ${manifest.apkName}\n`);
console.log(`Prepared ${manifest.apkName}; SHA-256 verified. Certificate/package must also be verified before publishing.`);
