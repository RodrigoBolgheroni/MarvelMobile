# Avengers Room — três cômodos conectados

O ambiente usa pixel art top-down em escala nativa. O mapa contém três cômodos grandes de 288x144 px lógicos — briefing, laboratório e arsenal/diagnóstico — conectados por duas passagens. A câmera acompanha o jogador com movimento contínuo; no centro de cada cômodo ela mostra o ambiente inteiro, e durante a travessia deixa a porta vizinha visível. A proporção é preservada em paisagem e retrato.

## Referências consultadas

- `REFERENCIAS/07-referencia-assets-modulares-base-heroica.png`: painéis, paredes e consoles.
- `REFERENCIAS/08-referencia-composicao-sala-base-heroica.png`: proporção e leitura de cômodo fechado.
- `REFERENCIAS/09-referencia-mobiliario-base-heroica.png`: móveis e distribuição do interior.
- `assets/room/generated/command_room_two_rooms_source.png`: conceito original como guia visual; não é o mapa runtime.

## Contrato de renderização

- A base runtime é `res/drawable-nodpi/room_map_864x144.png`, composta com tiles e móveis na escala original de 16 px.
- O mapa global tem 864x144 px lógicos; cada sala mede 288x144 px.
- A navegação usa células de 8 px, em grade global de 108x18.
- A câmera mostra uma sala por vez. As duas portas internas precisam estar abertas tanto na imagem quanto em `wallCells`/`walkableRows`, permitindo caminhar nos dois sentidos.
- Personagens, pistas e móvel collision usam coordenadas globais normalizadas. Todos os pontos de aproximação precisam ser alcançáveis desde o spawn.
- Em orientação retrato, a sala é girada; o renderer usa escala uniforme e nearest-neighbor para preservar sprites e tiles sem deformação.
- `room_walkmap.json` é a fonte de verdade para tilemap, personagens, pistas, cômodos, móveis e colisões.
- Prévia: `room_preview_stage_2592x432.png`, ampliada 3x por nearest-neighbor do mapa runtime.
