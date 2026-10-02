# Arte e navegação do mapa de três salas

As duas salas da esquerda preservam o mapa detalhado original em `res/drawable-nodpi/room_map_288x144.png`. A terceira, Arquivo de Evidências, foi gerada por `image_gen` com o mapa original como referência. A imagem bruta e o prompt exato estão em `REFERENCIAS/room_archives_generated.png` e `REFERENCIAS/room_archives_generated.prompt.txt`.

`Marvel/tools/build_three_room_map.py` compõe as três salas, abre as duas passagens, amplia a arte inteira em 2x por nearest-neighbor e gera `room_map_864x288.png`. Nenhuma sala é esticada em um único eixo. O script também gera `room_walkmap.json`, a máscara de colisão `room_walkmap_108x36.png`, a prévia e os hashes do manifesto.

Cada cômodo ocupa 288x288 pixels lógicos. A câmera mostra uma janela de 288x144 pixels e acompanha o jogador na horizontal e na vertical; no retrato, essa janela gira mantendo a proporção. Paredes e móveis são bloqueados por células de 8 pixels. As duas portas ficam abertas visualmente e na malha de caminhada, com percurso de ida e volta entre as três salas. O script valida que os cinco personagens e as três pistas podem ser alcançados a partir do ponto inicial.

Os personagens e as pistas são desenhados dinamicamente sobre a arte. Seus pontos de aproximação ficam em `room_walkmap.json`; as posições do caso ficam em `CaseSession.java`.
