# Espectro Android

Explorador de sinais com interface escura, antena animada, abas expansíveis, radar de intensidade e atualização pelo GitHub. Versão **0.3.1**, Android 8.0+ (API 26), alvo Android 15 (API 35).

[Baixar APK](https://github.com/PANTANO2002/espectro-android/releases/latest) · [Guia de uso](LEIA-ME.md) · [Validação](VALIDACAO.md)

## Nesta versão

- Busca inicial completa pelas APIs disponíveis, seguida de acompanhamento enquanto o app fica aberto. Depois de pelo menos 45 s de busca e 20 s sem identidades novas, a tela indica acompanhamento. A descoberta continua nos dois estados.
- Atualização Wi-Fi, modem, Direct e USB a cada 35–900 s, sem apagar a lista a cada ciclo. Bluetooth alterna BLE e descoberta clássica; mDNS acompanha anúncios.
- Wi-Fi, Bluetooth e modem só são retirados por ausência após três buscas concluídas e pelo menos três intervalos (mínimo 90 s) sem reobservação. Cache repetido não renova a medição. Falha ou limitação de busca não conta como ausência. O histórico fica no relatório, com no_radar=false.
- Distância experimental por RSSI em Wi-Fi e Bluetooth, suavização e calibração manual a 1 m. Modelo logarítmico com n=2,5 e potência de referência assumida. Não fornece localização confiável; RTT compatível tem prioridade por 60 s.
- Seta vetorial de tamanho fixo e animação cancelável nas abas.
- Fichas explicam os dados e ações de cada fonte. Conexões e pareamento abrem o Android; serviços web mDNS podem ser abertos pelo usuário.
- Recado como arquivo .txt pelo compartilhamento do Android. Bluetooth depende do suporte a arquivos e do aceite do receptor, sem Espectro instalado no destino. Não há conexão forçada nem envio automático para um MAC detectado.

## Limites

Não descobre todos os aparelhos. Wi-Fi mostra pontos de acesso; modem mostra células da operadora. Nomes são anunciados, MACs podem mudar e serviços não equivalem a aparelhos únicos. Direção não está disponível. A lista P2P pode ser cache do sistema. Não extrai ou descobre senhas: as APIs públicas do Android não oferecem captura de autenticação/modo monitor para auditoria de WPA/WPA2/WPA3. O painel explica a segurança anunciada e como recuperar credenciais da própria rede.

Sem conta, anúncios, analytics ou upload dos resultados. Dados de sinais ficam em memória até o processo encerrar; parar e retomar preserva resultados nesta abertura. O app pausa a busca ao sair da tela. Relatórios e recados só são exportados por ação do usuário.

## Atualizador

Consulta releases estáveis deste repositório ao abrir e periodicamente, valida manifesto, hash SHA-256, pacote, versão e certificado do APK. A instalação exige confirmação do Android. Não coloque tokens no app. A versão 0.2.0 já pode receber esta atualização; 0.1.0 exige instalação manual.

## Compilar e verificar

JDK 17, Android SDK 35 e Gradle Wrapper 8.11.1:

```powershell
.\scripts\test-update-policy.ps1
.\gradlew.bat assembleDebug lintDebug
```

No Linux/macOS: `bash scripts/test-update-policy.sh` e `./gradlew assembleDebug lintDebug`. Os testes cobrem atualização, modelo RSSI, suavização, período inicial e retirada conservadora por ausência. O GitHub Actions executa esses testes, compilação e lint.

O APK distribuído usa a chave de desenvolvimento original. Preserve essa chave fora do repositório para futuras atualizações. A validação em um Xiaomi 12 Lite físico continua necessária.

## Publicação sem seletor de arquivos

O workflow Publish signed Android release só publica em main, após mudança em .release/manifest.json ou execução manual. Primeiro roda os testes, compila e executa lint. Em seguida decodifica o APK já assinado localmente, confere SHA-256, certificado original, pacote, versão e minSdk, e publica a release estável com update.json. A chave privada de assinatura permanece fora do GitHub.

A pasta .release contém o APK público em base64, manifesto e notas. Ela é excluída do ZIP de fontes por export-ignore. Para a próxima versão, incremente versionCode/versionName, compile e assine com a chave original, atualize o payload e as notas, e envie o manifesto por último. O workflow não assina com uma chave nova. A permissão de escrita do token temporário do job serve à publicação no próprio repositório.

A preparação do pacote tem quatro testes adicionais: preservação dos bytes, hash incorreto, versão divergente e nome de arquivo inválido. Execute node --test tests/release-preparation.test.mjs para verificá-los.
