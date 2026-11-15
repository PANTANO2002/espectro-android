# Validação da versão 0.2.0

- 30 verificações de política do atualizador: HTTPS, hosts exatos, origem da release, caminhos codificados, nomes de APK, formato SHA-256, versões repetidas, downgrade e overflow.
- Certificado SHA-256 comparado à 0.1.0: mesmo certificado, `875c853c593129d5054987438102cc25d38ce34853500697c18b32b831d64404`.
- O atualizador compara pacote, versão, mínimo de Android, hash do arquivo e certificado do APK com o app instalado antes de abrir o instalador.
- Teste real de download e instalação no Android continua pendente; a checagem de política no computador não substitui esse teste.

- `assembleDebug`: concluído com sucesso.
- `lintDebug`: concluído com sucesso, com zero erros e zero avisos na execução final completa (`--rerun-tasks`).
- Assinatura do APK: verificada com `apksigner`; APK Signature Scheme v2, um signatário de desenvolvimento.
- Metadados conferidos com `aapt`: pacote `br.com.espectro`, versão `0.1.0`, mínimo API 26, alvo API 35.
- Gradle Wrapper 8.11.1: distribuição fixada com SHA-256 no arquivo de propriedades.
- JDK 17, ferramentas de compilação Android 35.0.0 e SDK Platform 35.

O emulador Android não inicializou corretamente no ambiente de desenvolvimento. Não foi possível validar visualmente a tela em execução, medir a taxa de quadros ou executar testes dos rádios. Nenhum Xiaomi 12 Lite físico estava conectado. A versão entregue é um protótipo para instalação e teste no celular, não uma versão homologada.

Verificações a fazer no Mi 12 Lite: primeira abertura e permissões; antena e transição de rolagem; cartões na lista única; parar e encerrar pelo tempo; encerrar ao sair da tela; sensores e disponibilidade dos rádios; salvar e abrir o JSON; comparação com aparelhos conhecidos em modo visível. Distância RTT depende de hardware e ponto de acesso compatíveis.
