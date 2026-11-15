# Espectro 0.3.1 — guia de uso

## Instalar e começar

1. Instale Espectro-0.3.1.apk. Mantém pacote e assinatura das versões anteriores. Na 0.2.0, você também pode usar Opções → Atualizações pelo GitHub → Verificar agora.
2. Permita localização precisa e dispositivos próximos quando o Android pedir. Ative Wi-Fi, Bluetooth e localização para as buscas correspondentes.
3. Escolha o intervalo de atualização (35 a 900 segundos), toque em ESCANEAR e mantenha o app aberto. A busca continua até você parar ou sair da tela.
4. Toque nas abas para expandir/recolher e nos resultados para ver identificadores, limitações e ações.

A primeira fase usa todos os módulos públicos suportados e autorizados. Após pelo menos 45 segundos e 20 segundos sem sinais novos, entra no estado visual de acompanhamento. Continua procurando novos sinais e atualizando os existentes. Não há garantia de detectar todos os aparelhos; um celular pode nem anunciar algo acessível.

## Distância e radar

Wi-Fi e Bluetooth usam o RSSI suavizado (70% do valor anterior + 30% da nova amostra) para uma estimativa experimental:

`distância = 10 ^ ((RSSI a 1 m − RSSI observado) / 25)`

O expoente de perda n=2,5 é uma suposição. Referências padrão a 1 m: Wi-Fi 2,4 GHz −45 dBm; 5/6 GHz −50 dBm; Bluetooth −59 dBm. Não são potências medidas do aparelho. O modelo tem limites de 0,1 a 1.000 m, sem intervalo de confiança. Paredes, antenas, interferência e potência variável podem causar erros grandes.

Para calibrar, coloque o celular a 1 m de um emissor conhecido e toque em Calibrar RSSI a 1 metro. É necessária uma amostra com até 15 segundos. A calibração dura nesta abertura do app; Restaurar padrão desfaz.

Wi-Fi RTT/802.11mc compatível tem prioridade por 60 segundos; ± é o desvio padrão informado pelo Android, sem incluir todo o erro do ambiente. Outros tipos não recebem metros fictícios. Rede móvel não é convertida em distância pelo RSSI.

O radar usa anéis por origem e arcos de intensidade. Não representa posição, mapa geográfico ou direção. A bússola não informa de onde vem o sinal de outro aparelho.

## Preservação e retirada de resultados

Parar/retomar não apaga os resultados nesta abertura. A cada atualização são acrescentados novos identificadores e revistas as observações. Leituras de Wi-Fi e modem com o mesmo timestamp não rejuvenescem o sinal.

Wi-Fi, Bluetooth e modem são retirados da lista ativa depois de pelo menos três buscas concluídas sem reobservação e três intervalos sem leitura nova (mínimo 90 segundos). Falhas, falta de permissão e limitação do Android não contam como buscas bem-sucedidas. Bluetooth encontrado por dois transportes precisa ficar ausente em ambos. Isso indica falta de reobservação, não comprova que o aparelho saiu fisicamente do alcance.

Direct acompanha mudanças da lista P2P fornecida pelo Android, cuja idade pode ser desconhecida. Serviços mDNS saem quando o sistema informa perda do serviço. USB sai ao ser desconectado. NFC é leitura pontual: uma tag antiga é retirada da lista depois do período de retenção, sem afirmar que está fisicamente distante.

O relatório JSON preserva o histórico, com no_radar e presenca para distinguir entradas ativas. Limite de memória: 2.000 identidades; até 100 cartões por origem são mostrados, com todos os dados guardados no relatório. Ao encerrar o processo, os sinais e calibrações em memória são descartados.

## Fontes, informações e ações

| Fonte | Dados que podem aparecer | Ações e limites |
|---|---|---|
| Wi-Fi | SSID, BSSID, frequência, largura de canal, segurança, padrão, RSSI e RTT compatível | Copiar IDs, abrir Wi-Fi do Android, consultar segurança anunciada, calibrar RSSI. Não lista todos os clientes, não anuncia IP ou senha. |
| Bluetooth LE | Nome, endereço possivelmente aleatório, RSSI, UUIDs, fabricante e dados anunciados | Copiar IDs, calibrar, abrir pareamento do Android. Anúncio BLE não garante capacidade de conexão ou envio de arquivos. |
| Bluetooth clássico | Nome, classe, endereço e RSSI quando anunciado | Depende do modo visível. Pareamento e conexões são controlados pelo sistema. |
| Wi-Fi Direct | Nome, endereço P2P e tipo WPS | Abrir configurações Wi-Fi; menus Direct variam por fabricante. Sem RSSI, distância ou conexão automática. |
| Rede móvel | Operadora/PLMN, tecnologia, identidade de célula e potência | Abrir configurações da operadora. São células, não outros celulares; não há mensagem, IP ou MAC de terceiros. |
| Rede local / mDNS | Nove tipos comuns de serviço; nome, IP/IPv6, porta e TXT anunciados | Copiar IDs; abrir serviço HTTP/HTTPS anunciado no navegador. Cada outro protocolo exige cliente compatível. Estar na mesma rede não informa distância. |
| NFC | UID e tecnologias de uma tag aproximada | Inspecionar/copiar identificação, sem alterar tag ou ler conteúdo protegido. |
| USB / OTG | Produto, fabricante, VID/PID, classe; serial se já autorizado | Inspecionar/copiar. Sem leitura/escrita genérica de periféricos. |

Sensores e GNSS fornecem contexto sem identificar aparelhos. Câmera e microfone não são usados. Infravermelho é emissor. UWB e Wi-Fi Aware exigem participantes cooperantes compatíveis e não têm busca universal nesta versão.

## Recado sem instalar o app no destino

Opções → Compartilhar recado, ou a ação na ficha Bluetooth, cria um arquivo recado-espectro.txt com até 2.000 caracteres. O Android abre o seletor de compartilhamento; escolha Bluetooth se ele estiver disponível e selecione o destinatário. O receptor deve aceitar e suportar arquivos. Ele não precisa instalar o Espectro. Essa ação não direciona automaticamente para o MAC da ficha.

O Espectro não consegue abrir um canal arbitrário em um celular que não o ofereça. Não existe envio universal/forçado por Wi-Fi, NFC ou BLE nas APIs usadas. Recados não garantem entrega e não substituem serviços de emergência. O anexo fica no cache privado; anexos com mais de 24 h são removidos na próxima criação, e o Android também pode limpar o cache. Apenas leitura é concedida ao aplicativo escolhido.

## Senhas da própria rede

Esta versão não descobre, testa ou exibe uma senha recuperada. SSID, MAC, canal e RSSI não contêm a senha WPA/WPA2/WPA3. As APIs públicas disponíveis ao app no Android comum não fornecem captura de autenticação/modo monitor para esse tipo de auditoria. Não há resultado simulado ou promessa de quebra de senha.

Para recuperar acesso à sua rede, consulte o compartilhamento de uma rede salva nas configurações do Android, a etiqueta ou a administração do roteador. Para conectar, use a tela Wi-Fi do Android. O painel de segurança explica PSK, SAE, EAP, OWE ou WEP conforme o anúncio, sem inferir credenciais.

## Atualizações e privacidade

Repositório público: https://github.com/PANTANO2002/espectro-android

O app consulta a última release estável ao abrir (com intervalo mínimo de uma hora) e agenda consulta aproximadamente a cada seis horas em rede não tarifada. O Android pode atrasar o agendamento. Baixa o APK e verifica SHA-256, pacote, versão e certificado antes de oferecer instalação. Você confirma a instalação no Android; não há instalação silenciosa. Desative a consulta automática no menu se desejar. Consultas ao abrir podem usar dados móveis.

Nenhuma leitura de sensores ou resultado é enviado ao GitHub. Não há conta, anúncios ou analytics. Relatórios contêm identificadores locais e devem ser compartilhados apenas com quem você escolher.

## Situação da validação

Protótipo de desenvolvimento, assinado com a mesma chave de debug das versões anteriores. Compilação, lint e testes no computador não substituem o teste dos rádios e do visual no Mi 12 Lite. Consulte VALIDACAO.md.

## SMS sem aplicativo extra no destino
No painel de recado, Preparar SMS abre o aplicativo de mensagens do Android com o texto. Você informa o número e confirma o envio nesse aplicativo. Não usa MAC/Bluetooth, não envia automaticamente e pode ter custo da operadora. O receptor vê uma mensagem comum conforme suas configurações, sem alerta de tela cheia imposto pelo Espectro. O app não pede permissão SEND_SMS.

A 0.3.1 usa versionCode 4, para atualizar também quem instalou manualmente o APK 0.3.0 (código 3).

