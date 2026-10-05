# Avengers Room — menu inicial (unidade de arte)

## Conceito

Tela de entrada com aparência de uma pausa de briefing dentro da base: skyline noturno atrás de vidro reforçado, mesa holográfica de planejamento e luzes práticas. O enquadramento reserva área calma para os controles XML, sem texto, logo, personagem ou botão incorporado na pintura.

## Composição

- **Landscape, 1024×576 px:** plate 16:9; skyline e mesa ficam à esquerda/centro. Os últimos 108/256 px lógicos (42,2%) são preenchidos por `#0D1720` para título e ações XML, com uma transição pixelada discreta de 16 px antes da zona limpa.
- **Portrait, 576×1024 px:** plate 9:16; janela, skyline e mesa ficam na área focal superior central. Os últimos 115/256 px lógicos (44,9%) são preenchidos por `#0D1720` para título e ações verticais, com transição de 13 px antes da zona limpa.
- Previews de `CENTER_CROP` em 20:9 e 9:20 confirmam que a mesa/janela permanecem visíveis e que a área dos controles segue calma; o corte atinge somente as periferias.
- Cada arte foi tratada como grade lógica (respectivamente 256×144 e 144×256) e ampliada 4× com nearest-neighbor. A arte é opaca e não contém conteúdo interativo.

## Paleta

| Papel | Cor |
|---|---|
| abismo / sombras | `#0D1720` |
| painel de aço | `#243543` |
| aço médio | `#384B57` |
| aço iluminado | `#52666A` |
| texto / luz neutra | `#C4D5C8` |
| holograma / tecnologia | `#44CFBD` |
| luz prática / foco | `#F2B84B` |
| alerta discreto | `#E4675D` |

## Contrato para integração

Assets sugeridos para `Marvel/app/src/main/res/drawable-nodpi/`:

- `menu_backdrop_landscape_1024x576.png`
- `menu_backdrop_portrait_576x1024.png`

O layout escolhe a variante por orientação. Use escala nearest-neighbor; preserve as zonas negativas para texto. Título em XML: `AVENGERS ROOM`. Ações XML: `Iniciar Investigação`, `Como Jogar`, `Créditos`. Não exibir Configurações enquanto não houver uma opção funcional.

Controles XML devem manter hit targets de pelo menos 48×48 dp e molduras quadradas coerentes com os selectors existentes. As placas são fundos opacos; não esticar sprites nem gravar textos nelas.

IDs sugeridos para coordenar com Front-end: `menu_root`, `menu_background`, `menu_title`, `menu_start`, `menu_how_to_play`, `menu_credits`. Só `menu_start` pode chamar `CaseEngine.newSession(seed)`; rotação não instancia caso, e voltar/reiniciar não mistura sessões. Estes nomes são proposta de integração, não IDs já existentes.

## Origem e licença

As plates foram geradas com OpenAI ImageGen para esta unidade; não foram fornecidas imagens locais como entrada, nem pixels ou arquivos de terceiros foram copiados. Não há personagens reconhecíveis, logotipos oficiais ou marcas na arte. Entre usuário e OpenAI, os Termos de Uso atribuem o output ao usuário na medida permitida por lei; também deixam o usuário responsável pelo uso e não garantem unicidade do output. Não se declara licença SPDX nem uma licença de personagem/franquia. O uso no protótipo permanece sujeito às leis e aos termos aplicáveis; a geração não concede direitos sobre marcas ou personagens Marvel. Termos consultados em 2026-10-05: https://openai.com/policies/terms-of-use/.
