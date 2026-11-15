Nova versão de teste para Android 8.0 ou superior.

- Distância experimental por RSSI em Wi-Fi e Bluetooth, com suavização e calibração a 1 metro. Wi-Fi RTT compatível tem prioridade; não há direção ou posição geográfica.
- Busca inicial seguida de acompanhamento contínuo em primeiro plano. Atualização sem apagar a lista; sinais sem reobservação saem gradualmente do radar e ficam no histórico.
- Correção da seta das abas, com desenho vetorial e animação cancelável para toques rápidos.
- Fichas com identificadores, informações e ações possíveis por tipo de conexão.
- Recado em arquivo .txt pelo compartilhamento Android/Bluetooth, com suporte e aceite do receptor; não precisa de Espectro no destino.
- Opção Preparar SMS abre o app de mensagens. Você escolhe o número e confirma o envio, que pode ter custo. Não força um alerta na tela do receptor.

Não descobre senhas nem força conexões. Os sinais disponíveis no Android padrão não fornecem senhas WPA/WPA2/WPA3 ou um canal universal para abrir alertas em outros celulares.

Compatível com atualização sobre 0.2.0 e 0.3.0: mesmo pacote e certificado, versionCode 4. No app: Opções → Atualizações pelo GitHub → Verificar agora. O Android pede confirmação para instalar.

Compilação e lint aprovados; 160 verificações de política passaram. Testes de rádios, aparência e instalação em um Mi 12 Lite físico continuam pendentes. Nenhum resultado de varredura é enviado ao GitHub.

O APK foi assinado localmente com a chave original. A publicação verifica SHA-256, pacote, versão, minSdk e certificado antes de liberar os arquivos. A chave privada não está no repositório.
