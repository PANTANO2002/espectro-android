param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][ValidateRange(3,2147483647)][int]$VersionCode,
    [Parameter(Mandatory=$true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$VersionName,
    [string]$ApkSigner = 'apksigner',
    [string]$Aapt = 'aapt'
)
$ErrorActionPreference = 'Stop'
$taskRepository = 'PANTANO2002/espectro-android'
$taskApk = (Resolve-Path -LiteralPath $Apk).Path
$taskCertificate = '875c853c593129d5054987438102cc25d38ce34853500697c18b32b831d64404'
$taskSignature = & $ApkSigner verify --print-certs $taskApk
if ($LASTEXITCODE -ne 0 -or -not ($taskSignature -match $taskCertificate)) { throw 'APK inválido ou assinado com outra chave. A chave original é necessária para atualizar o app instalado.' }
$taskBadging = (& $Aapt dump badging $taskApk) -join "`n"
if ($LASTEXITCODE -ne 0 -or $taskBadging -notmatch "package: name='br.com.espectro' versionCode='$VersionCode' versionName='$VersionName'") { throw 'Pacote ou versão do APK não correspondem aos parâmetros.' }
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOutput = Join-Path $taskRoot "build/release-$VersionName"
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
$taskName = "Espectro-$VersionName.apk"
$taskPublishedApk = Join-Path $taskOutput $taskName
Copy-Item -LiteralPath $taskApk -Destination $taskPublishedApk
$taskManifestPath = Join-Path $taskOutput 'update.json'
@{packageName='br.com.espectro';versionCode=$VersionCode;versionName=$VersionName;minSdk=26;apkName=$taskName;sha256=(Get-FileHash -LiteralPath $taskPublishedApk -Algorithm SHA256).Hash.ToLower()} | ConvertTo-Json | Set-Content -LiteralPath $taskManifestPath -Encoding utf8NoBOM
$taskNotes = Join-Path $taskOutput 'notes.md'
"Espectro $VersionName. APK compatível com o atualizador do app; a instalação é confirmada no Android." | Set-Content -LiteralPath $taskNotes -Encoding utf8NoBOM
& gh release create "v$VersionName" $taskPublishedApk $taskManifestPath --repo $taskRepository --target main --title "Espectro $VersionName" --notes-file $taskNotes
if ($LASTEXITCODE -ne 0) { throw 'Falha ao publicar a release. Verifique autenticação do gh, permissões e se a tag já existe.' }
