# Espectro — protótipo Android 0.2.0

Esta versão acrescenta grupos expansíveis por origem, radar de intensidade, identificadores detalhados e atualizações pelo GitHub. Veja o [README](README.md) para o funcionamento do atualizador e a publicação de novas versões. O radar não informa posição geográfica nem direção; os anéis representam as origens dos sinais e os arcos, sua intensidade.

App nativo em português para observar sinais e serviços acessíveis ao celular durante uma sessão de 15 a 900 segundos. Desenvolvido para a ideia proposta no Xiaomi 12 Lite; exige Android 8.0 ou superior. O suporte real depende da versão do Android, da ROM e dos componentes instalados.

## Instalar e usar

1. Transfira `Espectro-0.2.0.apk` para o celular e abra o arquivo para instalar. Se solicitado pelo Android, permita a instalação pela aplicação que você usou para abrir o APK. É possível instalar sobre a 0.1.0, pois o pacote e a assinatura foram mantidos. Esta primeira instalação é manual; depois o atualizador passa a consultar as releases.
2. Ative Wi-Fi, Bluetooth e Localização. Para os serviços da rede local, conecte o celular à rede Wi-Fi. NFC depende da variante do aparelho e precisa estar ativado.
3. Abra **Espectro**, escolha o tempo e toque em **ESCANEAR**. Conceda Localização precisa e Dispositivos próximos para os módulos correspondentes. Permissões recusadas deixam os módulos afetados indisponíveis; os demais continuam.
4. A antena anima, o painel se recolhe e a tela rola para revelar radar e grupos por origem. Toque no cabeçalho de um grupo para expandir ou recolher. Cada cartão informa nome anunciado, tipo conhecido, por onde foi encontrado, endereços disponíveis, sinal e distância quando medida. Toque no cartão para ver todos os identificadores e dados anunciados.
5. Ao fim do tempo a sessão encerra automaticamente. Toque novamente no botão para interromper antes. Sair da tela ou bloquear o celular também encerra a coleta.
6. **Salvar relatório** encerra a sessão e permite escolher um arquivo JSON. As opções no canto superior mostram hardware, últimas leituras dos sensores, estado dos módulos e registro.

O APK é uma compilação de desenvolvimento, assinada com chave de debug, para testes. Não é uma publicação na Play Store. A compilação não significa que todos os módulos foram validados em um Mi 12 Lite físico.

## O que é encontrado

| Fonte | O que aparece na lista única | Limite |
|---|---|---|
| Wi-Fi | Pontos de acesso e hotspots, SSID, BSSID, frequência, segurança e RSSI | Não identifica todos os clientes da rede. Cache tem idade indicada. O Android limita a frequência de novas buscas. |
| Bluetooth LE | Aparelhos emitindo anúncios, nome quando disponível, UUIDs, IDs de fabricante e RSSI | Não há garantia de modelo, presença permanente ou MAC estável. |
| Bluetooth clássico | Aparelhos respondendo à descoberta, nome, classe anunciada e RSSI quando fornecido | Depende do modo visível. Aparelhos apenas pareados não são contados como presentes. |
| Wi-Fi Direct | Participantes que o Android fornece na descoberta P2P | Nem todo celular fica visível; o cache do sistema pode conter entradas anteriores. |
| NFC | UID e tecnologias de tags/cartões aproximados | Alcance de centímetros. Não é um radar de telefones. |
| Rede móvel | Células 2G/3G/4G/5G retornadas pelo modem | São antenas/células da operadora, não celulares de terceiros. A lista pode estar em cache ou incompleta. |
| Rede local | Serviços mDNS anunciados na rede conectada | Busca nove tipos: HTTP, HTTPS, Google Cast, AirPlay, IPP, printer, SMB, SSH e ESPHome. Não enumera todos os hosts. Estar na rede não confirma proximidade física. |
| USB/OTG | Periféricos conectados por cabo no início da sessão | Não representa aparelhos sem fio ao redor. |

Os resultados ficam em grupos expansíveis por origem. Um endereço Bluetooth observado por LE e clássico mantém uma identidade interna e pode aparecer nos dois grupos. Endereços ou tecnologias diferentes não são unidos por suposição. O contador representa **identificadores e serviços**, não aparelhos físicos únicos. O protótipo guarda até 2.000 resultados por sessão e exibe os primeiros 100 de cada origem; o relatório inclui todos os resultados guardados.

Bluetooth LE e clássico alternam janelas durante o tempo da sessão, porque compartilham o rádio. Uma busca Wi-Fi é solicitada no início e a cada 35 segundos; atualizações disponibilizadas pelo sistema também são lidas. A telemetria celular é solicitada no início e a cada 30 segundos. O Android pode limitar ou recusar solicitações. Não há tentativa de contornar esses limites.

## Distância, direção e identificação

- **Metros:** apenas uma medição bem-sucedida de Wi-Fi RTT/802.11mc gera distância. Exige suporte do celular e do ponto de acesso. O campo ± informa o desvio padrão reportado pelo Android, não todo o erro causado pelo ambiente.
- **Intensidade:** forte, médio ou fraco é uma descrição do RSSI, não uma medição de proximidade física. Potência, paredes e interferência alteram o valor.
- **Direção:** indisponível nesta versão. A bússola dá a orientação do celular; não aponta para o emissor. A antena animada é simbólica e não mostra posições espaciais de aparelhos.
- **Nome e tipo:** usam nomes, classes e serviços anunciados. Podem faltar ou estar incorretos. Não confirmam dono, marca ou modelo exato. Um ponto de acesso pode ser roteador, hotspot ou outra infraestrutura.

## Outros sensores e rádios

O menu e o relatório enumeram sensores contínuos e de mudança disponíveis pelo `SensorManager`, tentam registrar suas leituras na frequência normal e atualizam os últimos valores no máximo uma vez por segundo por sensor. Sensores com permissão específica ou acesso restrito são sinalizados; sensores de gatilho são apenas inventariados. Os valores são nativos da API do sensor e não são usados para classificar aparelhos.

O GNSS mostra a quantidade de satélites observados e usados na solução de posição. Coordenadas não são armazenadas. Sensores de movimento, luminosidade, proximidade e campo magnético fornecem contexto; não identificam aparelhos eletrônicos a distância.

Wi-Fi Aware e UWB são inventariados, sem sessão de descoberta universal: exigem participantes/serviços compatíveis e cooperação. O infravermelho presente em alguns celulares é **emissor**, sem capacidade de recepção de aparelhos próximos. Câmera e microfone não são acionados no protótipo.

A ficha global do Xiaomi 12 Lite anuncia Wi-Fi de 2,4/5 GHz, Bluetooth 5.2, GNSS, sensores e emissor infravermelho. NFC varia por mercado. O app consulta o hardware real, sem assumir que uma variante tenha todos os recursos.

## Dados

Não há conta própria, publicidade, analytics ou upload de varreduras. Os sinais ficam em memória e são descartados ao encerrar o processo ou iniciar uma nova sessão. A exportação grava identificadores somente no destino escolhido. A conexão de Internet serve à descoberta local e à consulta/download de atualizações no GitHub. Preferências do atualizador e APK baixado ficam na área privada do app. É possível desativar consultas automáticas no menu de atualizações.

## Compilar

Abra a pasta no Android Studio com **JDK 17**, Android SDK Platform **35** e Build Tools **35.0.0** instalados. O projeto usa Android Gradle Plugin **8.9.2** e Gradle **8.11.1**, com wrapper incluído.

```powershell
.\gradlew.bat assembleDebug lintDebug
```

APK gerado: `app/build/outputs/apk/debug/app-debug.apk`.

O projeto é Java nativo com APIs do Android, sem bibliotecas de runtime adicionais. `MainActivity` cuida da interface, animação de transição, permissões e exportação; `ScanHeroView` desenha a antena; `ScanEngine` coordena a coleta com prazo e limpeza dos recursos; `Finding` modela cada observação.

## Referências oficiais

- [Especificações do Xiaomi 12 Lite](https://www.mi.com/global/product/xiaomi-12-lite/specs/)
- [Descoberta de pontos de acesso e limites de Wi-Fi](https://developer.android.com/develop/connectivity/wifi/wifi-scan)
- [Descoberta Bluetooth clássico](https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices)
- [Descoberta Bluetooth LE](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices)
- [Wi-Fi Direct](https://developer.android.com/develop/connectivity/wifi/wifip2p)
- [NFC](https://developer.android.com/develop/connectivity/nfc/nfc)
- [Informações de células do modem](https://developer.android.com/reference/android/telephony/TelephonyManager)
- [Distância por Wi-Fi RTT](https://developer.android.com/develop/connectivity/wifi/wifi-rtt)
- [Wi-Fi Aware](https://developer.android.com/develop/connectivity/wifi/wifi-aware)
- [UWB](https://developer.android.com/develop/connectivity/uwb)
- [Referência visual solicitada: ExitLag](https://www.exitlag.com/)

O visual usa fundo escuro, vermelho e movimento como inspiração; nome, textos, desenho da antena e implementação do Espectro são próprios.
