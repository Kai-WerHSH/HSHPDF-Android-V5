# HSHPDF para Android (nativo: Kotlin + Jetpack Compose + MuPDF)

**Criador da fonte:** Henrique de Souza Henrique
## Versão 0.5 — o app agora faz só duas coisas, bem feitas
1. **Ler** PDF, CBZ, CBR e EPUB de forma fluida.
2. **Criar** CBZ, PDF, ZIP e EPUB a partir de imagens de qualquer tipo.

**Não há nenhuma ferramenta de edição** (lápis, caneta, texto, imagem, carimbo etc. foram removidas).

### Ler
- **Biblioteca integrada:** em *Biblioteca > Adicionar pasta* você escolhe a pasta onde ficam seus arquivos uma vez. O app navega pelas pastas,
  mostra **capas** e o **progresso** de cada arquivo (página atual, "Lido ✓"), e o topo mostra **Continuar lendo**.
- **Continua no próximo arquivo:** ao terminar um arquivo, aparece "Fim / Próximo: ..." com contagem de 3 s e abre o próximo da pasta.
  O próximo já é **pré-carregado** enquanto você lê (por isso a troca é rápida). Dá para desligar em Ajustes.
- **Abre na hora:** os arquivos **não são copiados** para o app (nada ocupa espaço). Os tamanhos das páginas são medidos em segundo plano,
  e o **CBR começa a ser lido assim que a 1ª página é extraída** (o resto continua sozinho).
- **Zoom em qualquer ponto:** pinça ou **toque duplo** ampliam exatamente onde você tocou; com zoom dá para arrastar para qualquer lado.
- **Leitor igual ao Mihon:** toque no centro mostra as barras (voltar, título, controle deslizante de páginas, início/fim — que viram anterior/próximo arquivo —,
  Modo, Orientação, Fundo e Ajustes); toque nas laterais vira a página; Voltar sai. Modos: tira longa (sem espaços), tira com lacunas, paginado
  (esquerda→direita, direita→esquerda, vertical). Fundo (preto, cinza, branco, auto), brilho, nível de cinza, invertido, margem lateral.
- Lembra a última página lida de cada arquivo.
- Em **Ler** não dá para alterar nada. Para mexer nas páginas use o menu ⋮ do arquivo > **Organizar páginas**.

### Criar (aba "Criar")
- **Escolher imagens**, **pasta de imagens** ou **juntar/converter arquivos** (PDF, CBZ, CBR, EPUB). Aceita JPG, PNG, WebP, GIF, BMP, HEIC/HEIF
  (HEIC no Android 9+, AVIF no Android 12+). Fotos de celular saem na orientação certa (EXIF).
- Também funciona pelo **Compartilhar** de outros apps (ex.: galeria > Compartilhar > HSHPDF).
- Na tela de organização: **segure uma página e arraste** para mudar a ordem; **Selecionar** para girar, duplicar, mover ou excluir várias;
  menu ⋮: desfazer/refazer, ordenar por nome, inverter, adicionar mais imagens/pastas/arquivos.
- **Criar arquivo:** um painel só — nome, **formatos (PDF, CBZ, ZIP e EPUB ao mesmo tempo)**, qualidade/tamanho (Original sem recompressar,
  Alta, Média, Pequena) e pasta de saída (escolhida uma vez). Com *Original*, JPG/PNG/WebP/GIF são **copiados sem recompressar** (rápido e sem perda);
  HEIC e outros viram JPEG. Páginas são preparadas em paralelo. Pode cancelar; no fim: abrir para ler ou compartilhar.

## Como gerar o APK sem instalar nada (GitHub Actions)
1. Crie um repositório no GitHub e envie **todo o conteúdo desta pasta** (inclusive a pasta oculta `.github`). Apague os arquivos da versão anterior.
2. Aba **Actions** > "Compilar APK" roda sozinho (ou *Run workflow*). Leva uns 5–10 minutos.
3. Baixe o artefato **HSHPDF-apk** > `app-debug.apk` e instale por cima da versão atual.

## Se a compilação falhar
Escrito sem poder compilar no ambiente de criação. Abra a execução ❌ > passo vermelho > copie as linhas que começam com `e:` e envie para análise e correção.

## Limitações atuais
- Sem busca de texto dentro dos documentos (foi retirada para focar em leitura e criação; pode voltar).
- CB7/7z ainda não; CBR depende da biblioteca junrar (RAR5 muito recente pode falhar).
- PDF criado de imagens guarda cada página como imagem (sem texto selecionável).
- Se você mover/apagar um arquivo, o progresso dele continua guardado, mas a capa e a lista se atualizam ao reabrir a pasta.
- MuPDF é **AGPL**: ok para uso pessoal; se um dia distribuir o app, a licença exige abrir o código.
