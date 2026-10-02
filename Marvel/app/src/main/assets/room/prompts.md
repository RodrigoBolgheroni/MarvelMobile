# Mapa das duas salas originais

O jogo usa as duas salas do mapa detalhado original em `res/drawable-nodpi/room_map_288x144.png`: briefing à esquerda e laboratório à direita. A versão atual conserva a dimensão das salas e reduz aproximadamente 25–30% dos móveis por edição com `image_gen`. A imagem bruta e o prompt usado estão em `REFERENCIAS/room_two_rooms_small_furniture_raw.png` e `REFERENCIAS/room_two_rooms_small_furniture_raw.prompt.txt`.

`Marvel/tools/build_two_room_map.py` reduz a arte editada para `room_map_544x272.png` por nearest-neighbor, gera a prévia, `room_walkmap.json`, a máscara de colisão 68x34 e o manifesto com hashes. Cada sala continua medindo 272x272 pixels lógicos; a câmera e a moldura não mudaram. A malha de colisão acompanha o novo tamanho e a nova posição dos móveis.

A câmera conserva a janela de 288x144 pixels, segue o jogador nos dois eixos e mantém a proporção em paisagem e retrato. Paredes e móveis bloqueiam a caminhada em células de 8 pixels. A passagem entre as salas fica aberta nos dois sentidos. O script verifica que o ponto inicial alcança os cinco personagens e as três pistas.

Personagens e pistas são desenhados dinamicamente. Seus pontos de aproximação ficam em `room_walkmap.json`; as posições do caso ficam em `CaseSession.java`.
