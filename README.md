# Espectro para Android

Scanner de sinais próximos com interface escura, antena animada, grupos expansíveis por origem, radar de intensidade e atualização por GitHub Releases. Versão **0.2.0**, Android 8.0+ (API 26), alvo Android 15 (API 35).

**[Baixar APK](https://github.com/PANTANO2002/espectro-android/releases/latest)** · [Guia de uso](LEIA-ME.md) · [Validação e limites](VALIDACAO.md)

## Recursos

- Sessões de 15 a 900 segundos, encerradas automaticamente e ao sair da tela.
- Grupos que aparecem quando há resultados e expandem/recolhem ao toque: Wi-Fi, Bluetooth LE, Bluetooth clássico, Wi-Fi Direct, NFC, rede móvel, rede local e USB.
- Cartões com nome e tipo anunciados, fonte da descoberta, intensidade, identificadores disponíveis e distância quando medida por Wi-Fi RTT compatível.
- MAC/BSSID e SSID de Wi-Fi; endereço Bluetooth; UUIDs, dados de serviço/fabricante e anúncio BLE hexadecimal; endereço P2P; UID e tecnologias NFC; identificação celular; IP/porta e atributos TXT de serviços mDNS; VID/PID de USB.
- Inventário e últimas leituras permitidas dos sensores; estado de GNSS e dos rádios.
- Exportação local em JSON. Dados de varredura não são enviados ao GitHub.
- Consulta automática de atualizações, download, verificação de SHA-256/pacote/versão/assinatura e instalação confirmada pelo usuário no Android.

O radar é **de intensidade**, sem posições ou rumos inventados. IP e MAC não indicam endereço físico. Uma identidade pode ser temporária e um aparelho pode emitir vários sinais. O app não consegue detectar todos os aparelhos ao redor.

## Atualizações

O app consulta a última release estável pública de `PANTANO2002/espectro-android`. A release deve conter `update.json` e o APK descrito por ele. Há consulta ao abrir (intervalo mínimo de uma hora) e uma tarefa periódica solicitada a cada seis horas em rede não tarifada. O Android/MIUI pode adiar tarefas. Ao abrir, a consulta e eventual download também podem usar dados móveis.

O Android normalmente exige que o usuário permita instalações deste app e confirme a instalação. O Espectro não solicita root nem tenta contornar essa etapa. O menu **Atualizações pelo GitHub** permite consultar, instalar e desativar a consulta automática. Avisos por notificação são opcionais.

Instale a versão 0.2.0 manualmente uma vez; a 0.1.0 ainda não tinha atualizador. Esta versão mantém o pacote e o certificado da 0.1.0 para permitir a atualização por cima.

## Compilação e testes

JDK 17, Android SDK 35, Build Tools 35.0.0, AGP 8.9.2 e Gradle Wrapper 8.11.1 com SHA-256 fixado. Sem bibliotecas adicionais de runtime.

```powershell
.\gradlew.bat assembleDebug lintDebug
.\scripts\test-update-policy.ps1
```

No Linux/macOS, use `bash scripts/test-update-policy.sh`. O GitHub Actions compila e roda lint e testes de política de URLs, nomes de arquivo, hashes e versões.

## Publicar uma próxima versão

1. Incremente `versionCode` e `versionName` em `app/build.gradle`.
2. Compile usando a **mesma chave de assinatura** da versão instalada. A chave não está neste repositório. Não publique arquivos de keystore ou credenciais.
3. Com GitHub CLI autenticado e `aapt`/`apksigner` no PATH, execute:

```powershell
.\scripts\publish-release.ps1 -Apk .\app\build\outputs\apk\debug\app-debug.apk -VersionCode 3 -VersionName 0.3.0
```

O script valida o pacote, a versão e o certificado original, gera o manifesto com o hash do APK e publica ambos em uma release. As builds do CI usam outra chave de debug e **não são publicadas para atualização**. Este protótipo usa assinatura de desenvolvimento; distribuição de produção precisa de um processo próprio para gestão da chave.

Veja as fontes oficiais e os limites de cada tecnologia no [guia](LEIA-ME.md).
