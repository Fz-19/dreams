# DREAMS

App Android para guardar os PDFs dos seus projetos de sonho.

## Instalar no celular
1. No celular, abra a aba **Releases** deste repositório e baixe **DREAMS-debug.apk**
   (versão "APK do DREAMS").
2. Abra o arquivo e permita "instalar apps desconhecidos" quando pedir.
3. O app aparece com o ícone do D cromado.

O APK é gerado sozinho na aba **Actions** ("Gerar APK") a cada atualização, em uns 5 a 8 minutos.
Para gerar na hora: Actions > Gerar APK > Run workflow.

## Atualizar
Baixe o APK novo em Releases e instale por cima do app. Os PDFs e as capas continuam lá.
(Funciona porque o app mantém o mesmo nome de pacote e a mesma chave, e cada versão tem um número maior.)

## 3D em cada sonho
Ao adicionar ou editar um sonho, a seção "3D (opcional)" aceita:
- Modelo .glb: gira com o dedo, com zoom.
- Página .html: uma cena 3D em um único arquivo (com three.js dentro do arquivo, sem depender de internet).
- Imagens: de 4 a 90 fotos do objeto em ângulos diferentes, para girar arrastando.
No sonho aparecem as abas PDF e 3D.

## Copiar prompt
O botão "Copiar prompt" na tela inicial copia o texto para a IA gerar o PDF em formato de celular (e outro para o objeto 3D).

## Onde ficam os PDFs
Só dentro do próprio app, no aparelho. Nada é enviado para a internet.
Se desinstalar o app ou limpar os dados dele, os PDFs somem. Guarde os originais.

## Versão web (opcional)
https://fz-19.github.io/dreams/ abre o mesmo app no navegador.
