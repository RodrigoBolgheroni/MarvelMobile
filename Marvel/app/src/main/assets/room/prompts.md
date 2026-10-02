# Mapa das duas salas originais

O jogo usa as duas salas do mapa detalhado original em `res/drawable-nodpi/room_map_288x144.png`: briefing à esquerda e laboratório à direita. O corredor central é aberto com pixels do piso vizinho. A arte do terceiro cômodo foi retirada do aplicativo.

`Marvel/tools/build_two_room_map.py` gera o mapa final `room_map_544x272.png`, a prévia, `room_walkmap.json`, a máscara de colisão 68x34 e o manifesto com hashes. Cada sala mede 272x272 pixels lógicos, cerca de 6% menos que antes. A imagem é reduzida na mesma proporção horizontal e vertical por nearest-neighbor.

A câmera conserva a janela de 288x144 pixels, segue o jogador nos dois eixos e mantém a proporção em paisagem e retrato. Paredes e móveis bloqueiam a caminhada em células de 8 pixels. A passagem entre as salas fica aberta nos dois sentidos. O script verifica que o ponto inicial alcança os cinco personagens e as três pistas.

Personagens e pistas são desenhados dinamicamente. Seus pontos de aproximação ficam em `room_walkmap.json`; as posições do caso ficam em `CaseSession.java`.
