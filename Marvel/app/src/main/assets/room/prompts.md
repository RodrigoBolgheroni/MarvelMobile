# Base Avengers Room — dois cômodos conectados

O ambiente runtime usa um tilemap top-down modular 18x9, grade lógica de 16 px e navegação em células de 8 px. Ele contém dois espaços fechados e conectados por uma passagem: sala de briefing à esquerda e laboratório/arsenal à direita. Paredes externas e divisória são bloqueadas. Móveis e pistas têm células sólidas próprias em `room_walkmap.json`.

## Referências consultadas

- `REFERENCIAS/07-referencia-assets-modulares-base-heroica.png`: painéis, paredes e consoles.
- `REFERENCIAS/08-referencia-composicao-sala-base-heroica.png`: proporção e leitura de cômodo fechado.
- `REFERENCIAS/09-referencia-mobiliario-base-heroica.png`: móveis e distribuição do interior.
- `assets/room/generated/command_room_two_rooms_source.png`: conceito original de dois cômodos conectado; guia visual, não é o mapa runtime achatado.

## Sprite standalone

A mesa tática holográfica é `res/drawable-nodpi/room_tactical_briefing_table.png`, 64x40 px lógicos, transparente. É renderizada pelo layer de props e bloqueia as células nav cobertas pelo footprint. Os demais móveis usam `room_props_16.png`.

## Contratos

- Base: `room_walkmap.json#tilemap`, atlas `room_tiles_16.png`, 18 colunas x 9 linhas.
- Navegação: `walkableRows` e `solidCells`, 36x18 células de 8 px. Paredes, móveis e pistas bloqueiam o caminho; todos os oito pontos de aproximação precisam continuar acessíveis desde o spawn.
- Props: `room_walkmap.json#props`; posições em tiles lógicos. O renderer aceita props de atlas 16x16 e a mesa standalone 64x40.
- `room_map_288x144.png` é o fallback composto da base e props; o app prefere os layers para manter movimento e colisões.
- Prévia de validação: `room_preview_stage_864x432.png`, ampliada 3x por nearest-neighbor.
