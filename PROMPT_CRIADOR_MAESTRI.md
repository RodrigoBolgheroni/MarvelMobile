# Prompt para o criador de agentes no Maestri

Copie o bloco abaixo e envie ao agente que administra o workspace no Maestri.

```text
Monte a equipe de agentes do projeto Avengers Room no workspace cuja pasta é C:\Users\rodrigob-ieg\Documents\AvangersRoom. Esta tarefa é somente criar e conectar os agentes; ainda não inicie a implementação do app.

Leia PLANO.md, EQUIPE_AGENTES.md e PROMPTS_AGENTES.md nessa pasta. PROMPTS_AGENTES.md contém sete prompts completos, um para o orquestrador geral e seis para especialistas, além da tabela de skills escolhidas. Use o conteúdo de cada bloco de prompt correspondente como instrução da função; não use apenas o resumo da tabela.

Antes de criar qualquer terminal, execute maestri list, maestri preset list e maestri role list. Reutilize agentes e funções existentes que cubram exatamente uma das responsabilidades; evite duplicatas. Para cada função ausente, crie uma role do workspace com o texto integral do bloco correspondente em PROMPTS_AGENTES.md. Recrute um terminal com codinome próprio, diferente do nome da função, e atribua a role. Verifique que os sete papéis fiquem cobertos: orquestrador geral, backend e lógica do jogo, front-end Android, design e arte 2D, IA e integrações, narrativa, QA e apresentação.

Conecte os seis especialistas ao orquestrador geral para que possam trocar mensagens. Se houver portal Android disponível, conecte-o ao front-end e ao QA. Confirme a disponibilidade das skills indicadas em PROMPTS_AGENTES.md para cada terminal; se alguma não estiver disponível, registre a lacuna e mantenha o papel com as instruções do prompt.

Ao final, execute maestri list novamente. Responda com os nomes exatos dos terminais criados ou reutilizados, a role de cada um, as conexões, as skills indisponíveis e qualquer bloqueio. Não altere arquivos do app e não delegue tarefas de implementação neste passo.
```
