# Critérios de Aceite — DevTime

## 1. Objetivo

Consolidar os critérios de aceite verificáveis do DevTime, organizados por área funcional e por fase, incluindo os critérios automatizáveis (Gherkin) e os que exigem verificação manual. É a lista de verificação que determina se uma entrega está concluída.

## 2. Escopo

| Dentro | Fora |
|---|---|
| Critérios de aceite por área funcional | Estratégia e ferramentas (`strategy.md`) |
| Critérios de saída por fase | Casos de teste detalhados (`test-cases.md`) |
| Checklists de verificação manual | Requisitos (`01-product/requirements.md`) |
| Critérios de aceite não funcionais | Processo de release |

## 3. Definições

| Termo | Definição |
|---|---|
| **Critério de aceite** | Condição binária que determina se uma funcionalidade está pronta. |
| **Critério automatizável** | Verificável por teste automatizado. |
| **Critério manual** | Exige julgamento humano ou ferramenta externa. |
| **Gate de fase** | Conjunto de critérios que autoriza o avanço de fase. |
| **Aceite condicional** | Aprovado com pendência registrada e prazo. |

---

## 4. Como usar este documento

| Situação | Uso |
|---|---|
| Concluir uma user story | Verificar os critérios da área correspondente |
| Encerrar uma fase | Verificar o gate da fase (§13) |
| Revisar um PR | Confirmar que os critérios da funcionalidade estão verdes |
| Aceitar uma entrega | Executar a checklist manual aplicável |

**Regra:** um critério é **binário**. Não existe "parcialmente atendido". Se há dúvida, o critério não foi atendido.

---

## 5. Autenticação e conta

### 5.1 Automatizáveis

```gherkin
Funcionalidade: Cadastro e autenticação

  Cenário: Cadastro cria organização completa
    Quando me cadastro com dados válidos
    Então um usuário é criado com status PENDING_ACTIVATION
    E um tenant é criado com status ACTIVE
    E existe um membership OWNER ativo
    E existem exatamente 9 categorias padrão
    E recebo e-mail de verificação

  Cenário: E-mail inexistente e senha errada produzem a mesma resposta
    Quando autentico com e-mail inexistente
    E autentico com e-mail válido e senha errada
    Então ambas as respostas têm status 401 e código DEVTIME-1001
    E ambas as mensagens são idênticas
    E a diferença de tempo entre as respostas é inferior a 50ms

  Cenário: Bloqueio após 5 falhas
    Quando erro a senha 5 vezes em 15 minutos
    Então a próxima tentativa retorna 423
    E o bloqueio dura 30 minutos
    E recebo e-mail de alerta

  Cenário: Rotação de refresh token
    Dado um refresh token válido
    Quando o utilizo para renovar
    Então recebo um novo access token e um novo refresh token
    E o token anterior torna-se inválido

  Cenário: Reuso de refresh token revoga a cadeia
    Dado um refresh token já rotacionado
    Quando o utilizo novamente
    Então recebo 401 com código DEVTIME-1005
    E todas as sessões do usuário são revogadas
    E um evento de segurança crítico é registrado

  Cenário: Alteração de papel invalida o token
    Dado um usuário autenticado com papel ADMIN
    Quando seu papel é alterado para MEMBER
    Então o access token corrente é rejeitado
    E o refresh traz o novo papel
```

### 5.2 Manuais

| # | Verificação | Como verificar |
|---|---|---|
| MA-01 | E-mails renderizam corretamente | Abrir em Gmail, Outlook e Apple Mail, em desktop e mobile |
| MA-02 | Links de verificação e redefinição funcionam de qualquer cliente | Clicar a partir de cada cliente de e-mail |
| MA-03 | O fluxo de recuperação de senha é compreensível | Teste com usuário não familiarizado |

---

## 6. Registro de horas

### 6.1 Automatizáveis

```gherkin
Funcionalidade: Registro de horas

  Esquema do Cenário: Cálculo de duração
    Quando registro de <inicio> até <fim> com <pausa> minutos de pausa
    Então grossMinutes é <bruto>
    E netMinutes é <liquido>

    Exemplos:
      | inicio   | fim      | pausa | bruto | liquido |
      | 09:00:00 | 11:30:00 | 0     | 150   | 150     |
      | 09:00:00 | 11:30:59 | 0     | 150   | 150     |
      | 09:00:00 | 12:00:00 | 25    | 180   | 155     |
      | 22:00:00 | 01:30:00 | 0     | 210   | 210     |

  Cenário: Sobreposição é rejeitada com detalhes
    Dado um registro meu das 09:00 às 11:00
    Quando registro das 10:00 às 12:00
    Então recebo 422 com código DEVTIME-2102
    E a resposta identifica o registro conflitante
    E a resposta sugere o próximo horário disponível

  Cenário: Sessões que se tocam são permitidas
    Dado um registro meu das 09:00 às 11:00
    Quando registro das 11:00 às 12:00
    Então o registro é criado com sucesso

  Cenário: Limite de 24 horas
    Quando registro das 08:00 do dia 10 às 09:00 do dia 11
    Então recebo 422 com código DEVTIME-2103

  Cenário: Sessão atravessando a meia-noite
    Quando registro das 22:00 do dia 10 às 01:30 do dia 11
    Então o registro é criado
    E workDate é o dia 10
    E netMinutes é 210

  Cenário: Arredondamento sempre para baixo
    Dado que a organização usa arredondamento de 15 minutos
    Quando registro 112 minutos
    Então netMinutes é 105

  Cenário: Horas não faturáveis não consomem saldo
    Dado um período com 2400 minutos disponíveis
    Quando registro 300 minutos não faturáveis
    Então consumedMinutes permanece inalterado
    E nonBillableMinutes é 300

  Cenário: Registro travado não pode ser alterado
    Dado um período fechado
    Quando tento editar um registro do período
    Então recebo 409 com código DEVTIME-2121
    E o registro permanece inalterado

  Cenário: Saldo é retornado na criação
    Quando crio um registro válido
    Então a resposta contém o saldo atualizado do período
```

### 6.2 Manuais

| # | Verificação | Critério |
|---|---|---|
| MR-01 | Registro manual leva menos de 45 segundos | Cronometrar 5 execuções com usuário real |
| MR-02 | O campo de duração aceita todos os formatos documentados | Testar cada formato da tabela §6.2 de `components.md` |
| MR-03 | A mensagem de sobreposição é compreensível sem conhecimento técnico | Teste com usuário não técnico |
| MR-04 | O calendário destaca lacunas de forma perceptível | Inspeção visual |

---

## 7. Cronômetro

### 7.1 Automatizáveis

```gherkin
Funcionalidade: Cronômetro

  Cenário: Apenas um cronômetro ativo por usuário
    Dado um cronômetro em execução
    Quando tento iniciar outro
    Então recebo 409 com código DEVTIME-2150

  Cenário: Concorrência não cria dois cronômetros
    Quando duas requisições de início são enviadas simultaneamente
    Então apenas uma é bem-sucedida
    E existe exatamente um cronômetro ativo no banco

  Cenário: Troca atômica de tarefa
    Dado um cronômetro no ticket A
    Quando inicio um cronômetro no ticket B com stopCurrent
    Então um registro é criado para o ticket A
    E um cronômetro ativo existe no ticket B
    E a operação ocorreu em uma única transação

  Cenário: Falha de validação preserva o cronômetro
    Dado um cronômetro que geraria sobreposição
    Quando tento encerrá-lo
    Então recebo erro de validação
    E a resposta contém timerPreserved igual a true
    E o cronômetro permanece no estado anterior

  Cenário: Persistência após reinício do backend
    Dado um cronômetro em execução
    Quando a aplicação é reiniciada
    Então o cronômetro continua ativo
    E o tempo decorrido está correto

  Cenário: Cálculo com pausas
    Dado um cronômetro iniciado às 09:00
    E pausado às 10:30 e retomado às 11:00
    Quando o encerro às 12:15:40
    Então grossMinutes é 195
    E pausedMinutes é 30
    E netMinutes é 165

  Cenário: Abandono automático
    Dado um cronômetro em execução há 16 horas
    Quando o job de verificação executa
    Então o status passa a ABANDONED
    E nenhum registro é gerado automaticamente
    E o usuário é notificado
```

### 7.2 Manuais

| # | Verificação | Critério |
|---|---|---|
| MT-01 | Iniciar o cronômetro em um clique de qualquer tela | Testar em todas as telas |
| MT-02 | Tempo correto após hibernação da máquina | Hibernar 2h e verificar |
| MT-03 | Duas abas exibem o mesmo estado | Abrir duas abas e operar em uma |
| MT-04 | Tempo correto após perder e recuperar a conexão | Desligar e religar a rede |
| MT-05 | O estado (rodando/pausado) é óbvio à primeira vista | Inspeção visual |

---

## 8. Contratos e banco de horas

### 8.1 Automatizáveis

```gherkin
Funcionalidade: Banco de horas

  Esquema do Cenário: Políticas de carry-over
    Dado um contrato com política <politica> e teto <teto>
    E um período com <disponivel> disponíveis e <consumido> consumidos
    Quando fecho o período
    Então carriedOutMinutes é <transportado>

    Exemplos:
      | politica | teto | disponivel | consumido | transportado |
      | NONE     | 0    | 2400       | 1800      | 0            |
      | FULL     | 0    | 2400       | 1800      | 600          |
      | CAPPED   | 300  | 2400       | 1800      | 300          |
      | CAPPED   | 300  | 2400       | 2250      | 150          |
      | FULL     | 0    | 2400       | 2900      | 0            |

  Cenário: Rateio de período parcial
    Dado um contrato de 2400 minutos, início em 10/01 e ciclo no dia 1
    Quando o contrato é ativado
    Então o primeiro período vai de 10/01 a 31/01
    E contractedMinutes é 1703

  Cenário: Determinismo do cálculo
    Dado um período com 500 registros
    Quando recalculo o saldo 10 vezes
    Então todos os resultados são idênticos

  Cenário: Fechamento é atômico
    Dado um período pronto para fechar
    E uma falha simulada na geração do snapshot
    Quando tento fechar
    Então o período permanece OPEN
    E nenhum registro recebe lockedAt
    E nenhum snapshot é criado

  Cenário: Cronômetro ativo bloqueia o fechamento
    Dado um cronômetro ativo dentro do período
    Quando tento fechar
    Então recebo 409 com código DEVTIME-2240
    E a resposta identifica o cronômetro e seu dono

  Cenário: Reabertura respeita a ordem
    Dado os períodos de julho e agosto fechados
    Quando tento reabrir julho
    Então recebo 409 com código DEVTIME-2244
    E a resposta indica que agosto deve ser reaberto primeiro

  Cenário: Períodos são sempre contíguos
    Dado um contrato com 12 períodos gerados
    Então cada período inicia no dia seguinte ao fim do anterior
    E nenhum par de períodos se sobrepõe

  Cenário: Contrato de horas abertas não gera alerta
    Dado um contrato do tipo HOURLY_OPEN
    Quando registro 10000 minutos
    Então nenhuma notificação de consumo é gerada
```

### 8.2 Manuais

| # | Verificação | Critério |
|---|---|---|
| MC-01 | O extrato explica cada componente de forma compreensível | Usuário não técnico consegue explicar de onde vem o saldo |
| MC-02 | A prévia de períodos reflete exatamente o que será gerado | Comparar prévia com períodos após ativação |
| MC-03 | O diálogo de pré-fechamento informa todo o impacto | Inspeção do conteúdo |
| MC-04 | Cada `drillDown` do extrato reproduz exatamente o número | Clicar em cada linha e conferir |

---

## 9. Relatórios e exportação

### 9.1 Automatizáveis

```gherkin
Funcionalidade: Relatórios

  Cenário: Imutabilidade de período fechado
    Dado um período fechado com relatório gerado
    Quando altero o nome do cliente
    E gero o relatório novamente
    Então o conteúdo é idêntico ao primeiro
    E exibe o nome do cliente vigente no fechamento

  Cenário: Determinismo do PDF
    Dado um período fechado
    Quando gero o PDF duas vezes
    Então o conteúdo é idêntico exceto pelo carimbo de emissão

  Cenário: Período aberto é marcado como parcial
    Dado um período aberto
    Quando gero o relatório
    Então a resposta indica isPartial verdadeiro
    E o PDF contém a marcação PARCIAL em todas as páginas

  Cenário: Coluna decimal é numérica no Excel
    Quando exporto em XLSX
    Então a coluna de horas decimais é do tipo numérico
    E a soma da coluna confere com o total do relatório

  Cenário: MEMBER exporta apenas os próprios registros
    Dado que sou MEMBER
    Quando exporto sem filtro de usuário
    Então o relatório contém apenas os meus registros
    Quando exporto filtrando por outro usuário
    Então recebo 403

  Cenário: URL de download expira
    Dado um relatório exportado
    Quando acesso a URL após 16 minutos
    Então o acesso é negado
```

### 9.2 Manuais

| # | Verificação | Critério |
|---|---|---|
| MP-01 | O PDF é apresentável ao cliente final sem edição | Avaliação por 3 pessoas externas |
| MP-02 | O XLSX abre sem aviso | Abrir em Excel, LibreOffice e Google Sheets |
| MP-03 | O PDF é legível impresso em preto e branco | Imprimir e conferir |
| MP-04 | Nenhum identificador técnico aparece | Inspeção de todas as páginas |
| MP-05 | Descrições longas não são truncadas | Testar com descrição de 2000 caracteres |
| MP-06 | O relatório com marca do tenant fica coerente | Testar com logos claros e escuros |

---

## 10. Segurança e isolamento

### 10.1 Automatizáveis

```gherkin
Funcionalidade: Isolamento entre organizações

  Esquema do Cenário: Recurso de outra organização retorna 404
    Dado um <recurso> pertencente à organização A
    E que estou autenticado na organização B
    Quando faço <metodo> no recurso
    Então recebo 404
    E o corpo não revela a existência do recurso

    Exemplos:
      | recurso   | metodo |
      | cliente   | GET    |
      | cliente   | PUT    |
      | cliente   | DELETE |
      | contrato  | GET    |
      | ticket    | GET    |
      | registro  | GET    |
      | registro  | PATCH  |
      | período   | GET    |

  Cenário: Referência cruzada é rejeitada
    Dado um ticket da organização A
    E que estou autenticado na organização B
    Quando crio um registro referenciando aquele ticket
    Então recebo 404

  Cenário: Listagem não vaza dados
    Dado registros em duas organizações
    Quando listo registros autenticado em uma delas
    Então nenhum registro da outra aparece
    E o total da paginação não inclui os da outra

  Cenário: tenantId da requisição é ignorado
    Quando envio tenantId de outra organização no corpo
    Então o recurso é criado na minha organização
    E o valor enviado é ignorado
```

### 10.2 Manuais

| # | Verificação | Critério |
|---|---|---|
| MS-01 | Nenhum dado sensível em logs | Inspecionar logs após fluxo completo |
| MS-02 | Cabeçalhos de segurança presentes | Inspecionar respostas em produção |
| MS-03 | Anexo malicioso é bloqueado | Enviar arquivo EICAR |
| MS-04 | Mensagens de erro não revelam implementação | Revisar todas as respostas de erro |

---

## 11. Interface e acessibilidade

### 11.1 Automatizáveis

| # | Critério | Ferramenta |
|---|---|---|
| UI-01 | Zero violações do axe-core nas telas principais | axe-core |
| UI-02 | Contraste adequado nos dois temas | axe-core |
| UI-03 | Todo campo possui rótulo associado | axe-core |
| UI-04 | Hierarquia de cabeçalhos sem saltos | axe-core |
| UI-05 | Bundle inicial abaixo de 500 KB gzip | Análise de bundle |
| UI-06 | FCP abaixo de 1,5s | Lighthouse CI |
| UI-07 | Nenhum texto fixo em template | Verificação de i18n |

### 11.2 Manuais

| # | Verificação | Critério |
|---|---|---|
| MU-01 | Navegação completa por teclado | Percorrer todos os fluxos sem mouse |
| MU-02 | Funcional com leitor de tela | Testar com NVDA e VoiceOver nos fluxos principais |
| MU-03 | Funcional com zoom de 200% | Verificar todas as telas |
| MU-04 | Todos os atalhos funcionam | Testar cada atalho da §12 de `design-system.md` |
| MU-05 | Modo escuro sem perda de legibilidade | Inspecionar todas as telas |
| MU-06 | Responsivo de 360px a 2560px | Testar em 5 larguras |
| MU-07 | Estados vazios são instrutivos | Verificar cada estado vazio |
| MU-08 | Mensagens de erro têm ação sugerida | Verificar cada código de erro mapeado |

---

## 12. Desempenho

| # | Critério | Meta | Verificação |
|---|---|---|---|
| PF-01 | Listagem de registros | p95 < 300ms com 100k registros | Teste de carga |
| PF-02 | Dashboard | p95 < 800ms | Teste de carga |
| PF-03 | Cálculo de saldo | p95 < 100ms | Teste de carga |
| PF-04 | Validação de sobreposição | p95 < 50ms | Teste de carga |
| PF-05 | Criação de registro | p95 < 500ms | Teste de carga |
| PF-06 | PDF de 1.000 linhas | < 5s | Teste automatizado |
| PF-07 | XLSX de 5.000 linhas | < 15s | Teste automatizado |
| PF-08 | 1.000 usuários concorrentes | Sem degradação além das metas | Teste de carga |

---

## 13. Gates por fase

### F0 — Fundação

| # | Critério | Verificação |
|---|---|---|
| F0-01 | `docker compose up` sobe backend, frontend e banco funcionais | Manual + smoke test |
| F0-02 | Login retorna access e refresh tokens válidos | Automatizado |
| F0-03 | Teste prova que o tenant A não lê dado do tenant B | Automatizado |
| F0-04 | Migrations rodam do zero sem erro | CI |
| F0-05 | Pipeline falha em lint, cobertura ou CVE | Verificação intencional |
| F0-06 | OpenAPI publicado e acessível | Manual |

### F1 — Núcleo de Registro

| # | Critério |
|---|---|
| F1-01 | Todas as regras `RN-1xx` possuem teste referenciando o ID |
| F1-02 | É impossível criar registro sobreposto, com duração ≤ 0 ou > 24h |
| F1-03 | O cronômetro sobrevive a recarga, troca de aba e reinício do backend |
| F1-04 | Períodos são gerados automaticamente conforme o dia de faturamento |
| F1-05 | O time usa o sistema para as próprias horas por 2 semanas sem planilha |

### F2 — Inteligência Contratual

| # | Critério |
|---|---|
| F2-01 | O extrato explica linha a linha como o saldo foi obtido |
| F2-02 | Recalcular o saldo N vezes produz sempre o mesmo resultado |
| F2-03 | Todas as políticas de rollover e overage possuem teste |
| F2-04 | Dashboard responde em p95 < 800ms com 100k registros |
| F2-05 | Notificações disparam exatamente uma vez por limiar por período |

### F3 — Entrega ao Cliente

| # | Critério |
|---|---|
| F3-01 | O PDF de período fechado é idêntico ao ser regerado |
| F3-02 | O Excel abre sem aviso em três ferramentas |
| F3-03 | Relatório de período fechado ignora alterações posteriores |
| F3-04 | Exportação de 5.000 linhas conclui em menos de 15s |

### F4 — Produtividade (fim do MVP)

| # | Critério |
|---|---|
| F4-01 | Todos os requisitos `Must` estão implementados e aceitos |
| F4-02 | Todos os critérios deste documento aplicáveis ao MVP estão verdes |
| F4-03 | A auditoria registra quem alterou cada registro, quando e o valor anterior |
| F4-04 | Anexo malicioso é rejeitado |
| F4-05 | WCAG 2.1 AA nas telas principais |

---

## 14. Checklist de aceite do MVP

| # | Critério | Tipo | Status |
|---|---|---|---|
| MVP-01 | Novo usuário registra a primeira hora em menos de 5 minutos | Manual | ☐ |
| MVP-02 | Todos os requisitos `Must` implementados | Automatizado | ☐ |
| MVP-03 | Todas as `RN-XXX` com teste | Automatizado | ☐ |
| MVP-04 | Zero vazamentos entre tenants | Automatizado | ☐ |
| MVP-05 | Saldo sempre reproduzível e explicável | Automatizado | ☐ |
| MVP-06 | PDF de período fechado imutável | Automatizado | ☐ |
| MVP-07 | Cronômetro resiliente a todos os cenários | Misto | ☐ |
| MVP-08 | Todas as metas de desempenho atingidas | Automatizado | ☐ |
| MVP-09 | Dogfooding de 30 dias sem recorrer a planilha | Manual | ☐ |
| MVP-10 | WCAG 2.1 AA nas telas principais | Misto | ☐ |
| MVP-11 | Nenhum dado sensível em logs | Manual | ☐ |
| MVP-12 | Documentação sincronizada com o código | Manual | ☐ |

---

## 15. Casos especiais

| # | Caso | Tratamento |
|---|---|---|
| CE-A-01 | Critério não pode ser verificado antes da fase seguinte | Registrado como pendência com prazo; não bloqueia se não for de segurança ou de cálculo |
| CE-A-02 | Critério manual sem avaliador disponível | A fase não é encerrada; critério manual não é dispensável |
| CE-A-03 | Critério atendido em ambiente local mas não em staging | Não atendido |
| CE-A-04 | Critério ambíguo | Reescrito antes de qualquer avaliação |
| CE-A-05 | Regressão em critério já aceito | Bug de prioridade máxima; a fase é reaberta |

## 16. Casos de erro do processo

| Situação | Consequência |
|---|---|
| Fase declarada concluída com critério pendente | Fase reaberta |
| Critério marcado como atendido sem evidência | Auditoria de aceite; critério revertido |
| Critério de segurança ou de cálculo pendente | Bloqueio absoluto — sem exceção |
| Critério removido sem justificativa | Rejeitado na revisão |

## 17. Critérios de aceite deste documento

| # | Critério |
|---|---|
| CA-01 | Todo critério é binário e objetivamente verificável |
| CA-02 | Todo critério automatizável possui teste correspondente |
| CA-03 | Todo critério manual possui procedimento descrito |
| CA-04 | Todo gate de fase corresponde ao definido em `roadmap.md` |
| CA-05 | Nenhum critério depende de julgamento subjetivo sem procedimento |

## 18. Dependências e impactos

| Documento | Relação |
|---|---|
| `00-overview/roadmap.md` | Define os gates de fase |
| `01-product/requirements.md` | Fonte dos critérios funcionais |
| `02-domain/business-rules.md` | Fonte das regras verificadas |
| `strategy.md` | Define como os critérios são verificados |
| `test-cases.md` | Detalha os casos que provam os critérios |
| `ai/definition-of-done.md` | Consome estes critérios |

**Impacto:** adicionar um critério a uma fase já encerrada exige reavaliação daquela fase.
