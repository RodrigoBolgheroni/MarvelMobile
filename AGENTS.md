# Instruções para agentes — Avengers Room

Este workspace contém o protótipo Android em `Marvel/` e a documentação do projeto nesta raiz. O repositório GitHub informado para o projeto é [RodrigoBolgheroni/MarvelMobile](https://github.com/RodrigoBolgheroni/MarvelMobile.git).

## Fontes de referência

- **Requisitos, fluxo do jogo e critérios de conclusão:** [PLANO.md](PLANO.md). Leia antes de alterar regras, telas ou integrações.
- **Responsabilidades e coordenação da equipe:** [EQUIPE_AGENTES.md](EQUIPE_AGENTES.md). Consulte antes de assumir arquivos de outra área.
- **Instruções completas e skills de cada função:** [PROMPTS_AGENTES.md](PROMPTS_AGENTES.md). Use ao configurar ou assumir um papel.
- **Montagem da equipe no Maestri:** [PROMPT_CRIADOR_MAESTRI.md](PROMPT_CRIADOR_MAESTRI.md). Consulte apenas para criar ou reorganizar agentes.

## Ao trabalhar no projeto

1. Inspecione o estado atual de `Marvel/` e as instruções locais antes de editar. Coordene contratos compartilhados com o orquestrador geral.
2. Implemente o aplicativo Android em **Java**, com interfaces definidas em **XML**, conforme [PLANO.md](PLANO.md). Preserve a coerência entre motor do caso, pistas, chats e acusação.
3. Mantenha chaves e tokens fora do código versionado. Registre dependências de Comic Vine e Firebase/Gemini sem expor credenciais.
4. Ao concluir uma entrega, informe arquivos alterados, verificação executada, resultado observado e bloqueios reais. O fluxo completo no celular e as 20 combinações de vítima e assassino são os critérios centrais do MVP.

## Ciclo de trabalho e revisão

Trabalhe em **uma unidade revisável por commit**. Assim que a mudança tiver um objetivo completo e puder ser verificada, execute as verificações pertinentes, faça um único commit com apenas os arquivos dessa unidade, registre a tarefa em `docs/` e **pare**. Entregue ao usuário o commit, o resumo e os passos de revisão; aguarde a revisão antes de iniciar a próxima unidade. Não acumule várias tarefas em um commit nem avance automaticamente para a próxima.

O orquestrador geral organiza a fila de commits quando vários especialistas trabalham ao mesmo tempo. Cada especialista informa seus arquivos e verificações antes da integração; o responsável pelo commit confere o estado do Git e inclui apenas a própria unidade de trabalho.

O registro local de cada commit segue `docs/DATA-HORARIO-NUMERODOCOMMIT-TASK.md`:

- `DATA`: data local `AAAA-MM-DD`.
- `HORARIO`: hora local `HHmm`, no fuso `America/Sao_Paulo`.
- `NUMERODOCOMMIT`: total de commits alcançáveis em `HEAD` após o novo commit, com quatro dígitos (`git rev-list --count HEAD`).
- `TASK`: resumo curto em letras minúsculas, sem acentos, com palavras separadas por hífens.

Exemplo: `docs/2026-09-30-1430-0007-chat-gemini.md`. O registro descreve objetivo, mudanças feitas pela IA, arquivos alterados, testes executados, resultado, pendências e hash do commit. A pasta `docs/` é local e ignorada pelo Git; suas notas não entram no GitHub. Se este workspace ainda não tiver um repositório Git, prepare a mudança e pare para informar o bloqueio, sem inventar número ou hash de commit.

O projeto é um protótipo não comercial para competição. Confira a origem e os direitos de uso dos materiais Marvel antes de incluir imagens em um APK ou vídeo distribuído.
