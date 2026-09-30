# Avengers Room — equipe de agentes

Este arquivo define a divisão de trabalho para executar o [plano do protótipo](PLANO.md).

Os textos prontos para atribuir cada função estão em [PROMPTS_AGENTES.md](PROMPTS_AGENTES.md).

## Coordenação

Um **agente orquestrador geral** mantém o plano como referência, divide tarefas, define os contratos entre áreas, acompanha o prazo e integra as entregas. Ele valida o fluxo completo no celular e resolve dependências entre especialistas.

## Especialistas

| Especialista | Responsabilidade | Entrega verificável |
| --- | --- | --- |
| Backend e lógica do jogo | Motor do caso, sorteio, álibis, pistas reutilizáveis, caderno e avaliação da acusação | As 20 combinações de vítima e assassino têm solução única |
| Front-end Android | Interfaces XML, código Java para a sala 2D, movimento por toque, interação por proximidade, chat e demais telas | Uma partida pode ser percorrida e concluída no celular |
| Design e arte 2D | Identidade visual, cenário, personagens, objetos e legibilidade da interface | A sala comunica personagens e pistas com clareza em tela horizontal |
| IA e integrações | Comic Vine, Firebase, prompts, históricos separados e tratamento de falhas | Os quatro sobreviventes respondem pelo Gemini com fatos coerentes com o caso |
| Narrativa | Premissa, falas-base, personalidade e coerência dos motivos e depoimentos | Cada caso tem contexto compreensível e pistas interpretáveis |
| QA e apresentação | Testes de partidas, aparelho, rede e roteiro do vídeo | Fluxo estável e vídeo de uma partida completa |

O orquestrador define primeiro os dados que cada área recebe e entrega. Cada especialista trabalha em arquivos da própria área e comunica mudanças de contrato ao orquestrador antes da integração. QA revisa o resultado integrado, incluindo as combinações de caso e o uso real no celular.
