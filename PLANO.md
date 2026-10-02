# Avengers Room — plano do protótipo

## Objetivo

Criar em cinco dias um protótipo Android em **Java**, executável no celular e apresentado em vídeo. O jogador entra em uma sala 2D temática da Marvel, investiga um assassinato, conversa com os sobreviventes e acusa um suspeito.

Este documento registra o plano. A implementação e as contas de API ainda não foram criadas.

## Decisões do produto

| Tema | Decisão |
| --- | --- |
| Entrega principal | Vídeo de uma partida completa e protótipo Android jogável |
| Prazo | Cinco dias de trabalho |
| Plataforma | Android, Java, interfaces em XML, orientação horizontal e vertical |
| Sala | Três cômodos 2D conectados; a câmera mostra um cômodo ampliado por vez; paredes e móveis bloqueiam a caminhada |
| Elenco inicial | Homem de Ferro, Capitão América, Thor, Viúva Negra e Gavião Arqueiro |
| Variação | Os cinco personagens permanecem; vítima e assassino mudam a cada partida. No MVP, o mesmo modelo de pistas pode ser reutilizado |
| Conversas | Gemini ao vivo é obrigatório na demonstração |
| Rede | Disponível durante a apresentação |
| Uso | Protótipo não comercial; o regulamento da competição permite personagens de franquias |

## Como uma partida funciona

1. O jogo sorteia uma vítima entre os cinco personagens e um assassino entre os quatro sobreviventes. A vítima aparece na sala, mas não participa dos chats.
2. O motor do caso monta uma linha do tempo, álibis e três pistas que identificam um único culpado. No MVP, os mesmos objetos e tipos de pista podem aparecer em todas as partidas; o texto e a associação com o culpado acompanham o sorteio. A sala de contenção mantém poderes e equipamentos inativos, para que os casos sejam coerentes com qualquer combinação sorteada.
3. O jogador toca no chão para caminhar. Ao se aproximar de um personagem ou objeto, pode abrir uma conversa ou examinar uma pista.
4. Cada sobrevivente tem um chat separado, com sua personalidade, seu histórico e apenas os fatos que conhece. O Gemini redige as falas; o motor do caso controla pistas, fatos decisivos e resultado.
5. Um caderno registra pistas e depoimentos. O jogador escolhe um dos quatro sobreviventes como acusado. A tela final informa o resultado e explica a sequência dos fatos.

### Componentes e contratos

- **Motor do caso:** recebe uma semente de sorteio e entrega vítima, assassino oculto, álibis, pistas e fatos permitidos por personagem. Também avalia a acusação.
- **Sala Android:** usa interfaces XML e código Java para mostrar personagens e objetos, mover o jogador ao ponto tocado e liberar interação por proximidade. Abre chat, caderno e acusação sem alterar a solução do caso.
- **Fonte de personagens:** busca os cinco perfis selecionados na Comic Vine, mantém cache para reduzir chamadas e exibe crédito e links para a origem dos dados.
- **Serviço de conversa:** mantém quatro históricos independentes e envia ao Gemini a personalidade e os fatos permitidos do personagem correspondente. Em falha de rede, preserva a conversa e oferece **Tentar novamente**.

Usar o SDK [Firebase AI Logic para Android em Java](https://firebase.google.com/docs/ai-logic) com [App Check](https://firebase.google.com/docs/ai-logic/app-check). As credenciais do Google ficam protegidas pelo serviço; segredos locais não entram no repositório. A [Comic Vine](https://comicvine.gamespot.com/api/) limita a API a uso não comercial, impõe limites de requisições e pede atribuição.

## Plano de cinco dias

| Dia | Trabalho | Critério de conclusão |
| --- | --- | --- |
| 1 | Organizar o projeto; criar as contas; configurar Android; testar uma consulta Comic Vine e uma resposta Gemini em Java | As duas APIs respondem no ambiente de desenvolvimento |
| 2 | Construir o motor do caso, a sala e o movimento por toque | Uma partida inicia, o jogador caminha e os objetos podem ser examinados |
| 3 | Integrar chats, pistas, caderno e acusação | Uma partida pode ser concluída no celular |
| 4 | Testar casos, rede, reinício e apresentação; corrigir inconsistências | Fluxo completo estável e roteiro do vídeo definido |
| 5 | Congelar o protótipo, ensaiar e gravar | Vídeo mostra uma partida completa e APK de demonstração abre no celular |

## Verificação e dependências

- Testar as **20 combinações** possíveis de vítima e assassino. Cada uma precisa ter três pistas acessíveis e exatamente uma solução, mesmo com o modelo de pistas reutilizado.
- Verificar movimento por toque, interação por proximidade, quatro chats independentes, caderno, acusação e tela final.
- Verificar falhas de rede nas duas APIs. O chat preserva o histórico para nova tentativa.
- Fazer um teste real no celular e conferir as APIs antes de gravar o vídeo.
- **Dependência do primeiro dia:** ainda não existem chave Comic Vine nem projeto Firebase/Gemini. O Gemini é obrigatório na demo; sua configuração precisa funcionar antes de avançar com o restante da integração.
- O regulamento da competição permite o uso de personagens conhecidos no protótipo. Antes de distribuir imagens ou o APK publicamente, conferir os direitos dos materiais usados: acesso à API da Comic Vine não equivale a uma licença das artes ou personagens Marvel.
