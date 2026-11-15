# Validação da versão 0.3.1

- Compilação assembleDebug concluída com JDK 17 / Android SDK 35.
- lintDebug: No issues found (zero erros e avisos).
- 30 verificações da política de atualização aprovadas.
- 130 verificações do modelo de sinais aprovadas: referência a 1 m, relação de perda/distância, monotonicidade, valores inválidos, suavização, convergência, período de busca inicial e ausência com carência de reobservação.
- Pacote br.com.espectro, versionCode 4 / versionName 0.3.1. APK assinado com o certificado original das versões 0.1/0.2; compatível com atualização por cima.
- Nenhum celular físico aparece em adb devices. Instalação, fluidez, setas sob toques rápidos, precisão do RSSI, rádios e compartilhamento Bluetooth no Mi 12 Lite não foram testados em execução. O emulador usado na versão anterior não inicializou corretamente.

## Comportamento revisado no código

- Releituras de cache Wi-Fi/modem com o mesmo timestamp não renovam a idade nem suavizam novamente o RSSI.
- Ausência só conta em atualizações bem-sucedidas de rádio; cache P2P desconhecido tem tratamento separado. Falha ou limitação não conta como sumiço.
- Parar/retomar preserva os objetos de resultado e calibrações. A interface remove cartões inativos; o relatório preserva o histórico.
- Animação da seta tem limites fixos, usa um único animador cancelável e não depende de glifo rotacionado.
- Exportação de recados usa provider privado, URI restrita, concessão de leitura e seletor nativo. Não envia a um destinatário automaticamente.
- Não há extração de senhas, quebra de autenticação, conexão forçada, envio de varreduras ou identificação fictícia.

## Verificação no celular ainda necessária

1. Instalar sobre 0.2.0, conceder permissões e conferir a atualização via GitHub.
2. Manter a busca por vários ciclos, ligar/desligar um emissor conhecido e conferir retenção, retirada gradual e reaparecimento.
3. Recolher/expandir abas rapidamente e com animações do Android desativadas; conferir legibilidade e acessibilidade.
4. Calibrar a 1 m e comparar o efeito de distância/obstáculos sem tratar o modelo como medição.
5. Compartilhar o .txt pelo Bluetooth do sistema e confirmar aceite/recebimento em um aparelho compatível sem Espectro.

## Fontes das limitações de plataforma

- [Varredura Wi-Fi e limites de frequência](https://developer.android.com/develop/connectivity/wifi/wifi-scan)
- [Conexões Bluetooth e pareamento](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)
- [Compartilhar arquivos com URI e permissão temporária](https://developer.android.com/training/secure-file-sharing/share-file)

É um protótipo para teste; compilação e testes de política não comprovam detecção universal nem funcionamento de todos os rádios em aparelhos reais.

## Verificações adicionais da publicação

- Quatro testes do preparador de release: bytes preservados, bloqueio de hash incorreto, versão divergente e caminho inválido.
- Recados agora também abrem a composição de SMS pelo Android, sem permissão SEND_SMS e sem envio automático. Não foi testado com operadora real.
- O APK distribuído foi assinado localmente. O workflow confere hash, certificado, pacote e versão antes de publicar; a compilação de CI valida os fontes, mas o APK gerado com chave de CI não é distribuído.
