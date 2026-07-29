# Template de Feature — DevTime

## Como usar este template

1. Copie a estrutura da **§ Template** abaixo para `specs/NNN-nome/spec.md`.
2. Preencha **todas** as seções. Nenhuma seção pode ser removida, renomeada ou reordenada.
3. Uma seção sem conteúdo aplicável recebe `Não se aplica — <motivo objetivo>`. Deixar em branco, escrever `TBD` ou `N/A` sem motivo é rejeitado em revisão (MN-03).
4. Crie também `tasks.md`, `acceptance.md` e `tests.md` conforme os modelos das §§ finais deste arquivo.

### Regras invioláveis do template

| # | Regra |
|---|---|
| TP-01 | **Nunca escreva código.** Nomes de classe, assinaturas e nomes de arquivo são permitidos; corpo de método, SQL executável e template HTML não |
| TP-02 | **Nunca invente regra de negócio.** Toda regra é uma referência a `RN-XXX` de `docs/02-domain/business-rules.md` |
| TP-03 | **Nunca deixe comportamento implícito.** Se não está escrito, não existe |
| TP-04 | **Nunca omita caso de erro nem caso extremo**, mesmo os improváveis |
| TP-05 | **Sempre justifique decisões**, citando a alternativa rejeitada |
| TP-06 | **Sempre responda às oito dimensões transversais** (multi-tenant, soft delete, UUID, auditoria, LGPD, escalabilidade, performance, segurança) |
| TP-07 | **Sempre inclua ao menos um diagrama Mermaid** |
| TP-08 | **Sempre liste nominalmente** os artefatos de código a criar |
| TP-09 | **Sempre use tabela** para qualquer enumeração de três itens ou mais |
| TP-10 | **Sempre considere a evolução para SaaS**: multi-tenant desde o início, nada que impeça F5–F8 |

---

# Template — `spec.md`

> Substitua `NNN` e `<Nome>`. Remova todas as instruções entre `<!-- -->` ao preencher.

```markdown
# NNN — <Nome da Funcionalidade>

| Campo | Valor |
|---|---|
| **Feature** | NNN |
| **Épico** | EP-XX |
| **Sprint** | SX |
| **Prioridade** | P0 · P1 · P2 |
| **Complexidade** | Baixa · Média · Alta · Crítica |
| **Estimativa** | XX pts · X dias-agente |
| **Stories** | US-XXX a US-YYY |
| **Status** | SPEC_DRAFT · SPEC_APPROVED · READY · IN_PROGRESS · IN_REVIEW · DONE |
```

## 1. Objetivo
<!-- Uma frase que define o que a funcionalidade entrega. Sem adjetivos. -->

## 2. Problema que resolve
<!-- Qual dor do usuário desaparece. Referenciar persona, JTBD ou princípio de produto (PV-XX, PR-XX). -->

## 3. Escopo
<!-- Tabela com o que está DENTRO. Cada item é verificável. -->

| # | Item | Referência |
|---|---|---|

## 4. Fora do escopo
<!-- O que explicitamente NÃO é entregue e ONDE está (outra feature ou fase). Evita escopo por omissão. -->

| Item | Onde está | Motivo |
|---|---|---|

## 5. Dependências

### 5.1 Features
| Feature | Tipo | O que consome |
|---|---|---|
<!-- Tipo: Bloqueante | Consumidora | Opcional -->

### 5.2 Documentos obrigatórios
| Documento | Seções relevantes |
|---|---|

### 5.3 Infraestrutura
| Componente | Uso |
|---|---|

## 6. Regras de negócio
<!-- SOMENTE referências. Nunca reescrever o enunciado normativo. -->

| ID | Tipo | Enunciado resumido | Erro | Onde é aplicada |
|---|---|---|---|---|
<!-- Tipo: Bloqueante | Aviso | Automática | Derivada -->

### 6.1 Ordem de aplicação
<!-- A ordem é NORMATIVA (SV-03). Numerar e justificar por que esta ordem. -->

### 6.2 Invariantes envolvidas
| ID | Invariante | Como é garantida |
|---|---|---|

## 7. Fluxo principal
<!-- Passo a passo numerado do caminho feliz, do gatilho ao resultado observável. -->

## 8. Fluxos alternativos
| # | Fluxo | Gatilho | Comportamento |
|---|---|---|---|

## 9. Diagrama
<!-- Obrigatório. flowchart, sequenceDiagram ou stateDiagram-v2. Mais de um quando útil. -->

## 10. Estados
| Estado | Significado | Operações permitidas | Operações bloqueadas |
|---|---|---|---|

## 11. Transições
| Origem | Destino | Gatilho | Guarda | Efeito | Permissão |
|---|---|---|---|---|---|

### 11.1 Transições proibidas
| Transição | Motivo da proibição |
|---|---|

## 12. Casos de erro
| Código | HTTP | Situação | Mensagem ao usuário | Regra |
|---|:--:|---|---|---|

### 12.1 Casos extremos
| # | Caso | Comportamento esperado |
|---|---|---|

## 13. Modelo de dados

### 13.1 Entidades impactadas
| Entidade | Operação | Tabela | Referência |
|---|---|---|---|
<!-- Operação: Cria | Lê | Atualiza | Soft delete -->

### 13.2 Campos obrigatórios na criação
| Campo | Tipo | Origem | Imutável | Validação |
|---|---|---|:--:|---|

### 13.3 Migrations
| Migration | Conteúdo | Compatibilidade |
|---|---|---|

### 13.4 Índices
| Índice | Colunas | Sustenta |
|---|---|---|

## 14. Endpoints utilizados
| Método | Rota | Operação | Permissão | Status de sucesso | Doc |
|---|---|---|---|:--:|---|

## 15. Eventos
| Evento | Publicado por | Consumidores | Momento | Efeito |
|---|---|---|---|---|
<!-- Momento: dentro da transação | após o commit (justificar) -->

## 16. Permissões
| Operação | Permissão | Papéis | Ownership | Escopo de dados |
|---|---|---|---|---|

## 17. Validações

### 17.1 Camada 1 — Formato (Bean Validation, `400`)
| Campo | Restrição | Mensagem |
|---|---|---|

### 17.2 Camada 2 — Negócio (Service, `422`/`409`)
| Validação | Regra | Erro |
|---|---|---|

### 17.3 Camada 3 — Consistência (banco, `409`)
| Constraint | Garante | Mapeado para |
|---|---|---|

## 18. Auditoria
| Ação | `action` | `beforeState` | `afterState` | Metadata |
|---|---|---|---|---|

## 19. Segurança
| # | Vetor | Mitigação | Verificação |
|---|---|---|---|

### 19.1 LGPD
| Dado pessoal | Base legal | Retenção | Exportação | Anonimização | Proibido em log |
|---|---|---|---|---|---|

## 20. Performance
| Operação | Meta | Índice/estratégia | Risco |
|---|---|---|---|

### 20.1 Escalabilidade
<!-- Comportamento com 100k+ registros por tenant. Paginação, projeção, N+1. -->

## 21. Componentes Frontend

### 21.1 Rotas
| Rota | Componente | Guard | Lazy | Tela |
|---|---|---|:--:|---|

### 21.2 Componentes
| Componente | Tipo | Responsabilidade | Inputs | Outputs |
|---|---|---|---|---|
<!-- Tipo: Page (smart) | Presentational | Shared -->

### 21.3 Stores e serviços Angular
| Artefato | Tipo | Estado exposto | Escopo |
|---|---|---|---|

### 21.4 Guards, interceptors, pipes e directives
| Artefato | Tipo | Uso |
|---|---|---|

## 22. Serviços Backend

### 22.1 Controllers
| Classe | Rota base | Endpoints |
|---|---|---|

### 22.2 Services
| Interface | Implementação | Responsabilidade | Permissão declarada |
|---|---|---|---|

### 22.3 Componentes de domínio
| Classe | Tipo | Responsabilidade | Regras |
|---|---|---|---|
<!-- Tipo: Calculator | Policy | Validator | Generator | StateMachine -->

### 22.4 Jobs
| Classe | Cron | Lock | Responsabilidade | Idempotência |
|---|---|---|---|---|

## 23. DTOs
| DTO | Direção | Campos principais | Observação |
|---|---|---|---|
<!-- Direção: Request | Response | Projection | Filter -->

## 24. Mappers
| Mapper | De → Para | Mapeamentos não triviais |
|---|---|---|

## 25. Repositories
| Repository | Entidade | Métodos específicos | Índice usado |
|---|---|---|---|

## 26. Entities utilizadas
| Entidade | Origem | Campos relevantes |
|---|---|---|

## 27. Validators e Exceptions
| Classe | Tipo | Regra | Código de erro |
|---|---|---|---|

## 28. Logs
| Evento | Nível | Campos | Proibido |
|---|---|---|---|

## 29. Métricas
| Métrica | Tipo | Tags | Alerta |
|---|---|---|---|

## 30. Comportamentos esperados
| # | Comportamento |
|---|---|

## 31. Comportamentos proibidos
| # | Proibição | Motivo |
|---|---|---|

## 32. Restrições
| # | Restrição | Origem |
|---|---|---|

## 33. Critérios de aceite
| # | Critério | Verificação |
|---|---|---|

## 34. Checklist de implementação
<!-- Marcável pelo agente durante a execução. -->

## 35. Checklist de revisão
<!-- Aplicado pelo revisor no PR. -->

## 36. Checklist de QA
<!-- Aplicado antes de declarar DONE. -->

## 37. Definition of Done
| # | Item | Referência |
|---|---|---|

## 38. Riscos
| # | Risco | Prob. | Impacto | Mitigação | Gatilho |
|---|---|:--:|:--:|---|---|

## 39. Observações
<!-- Decisões tomadas ao escrever a spec, alternativas rejeitadas, dívidas conhecidas, pontos de evolução para SaaS. -->
```

---

# Template — `tasks.md`

```markdown
# NNN — <Nome> · Tarefas

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `T-NNN-XX`, estável e imutável |
| **Descrição** | Verbo no infinitivo + objeto. Uma tarefa = uma unidade entregável |
| **Dependências** | IDs de tarefas ou features que precisam estar concluídas |
| **Estimativa** | Em horas-agente. Tarefa acima de 8h deve ser decomposta |
| **Prioridade** | P0 bloqueante · P1 necessária · P2 cortável |

## 2. Resumo
| Grupo | Tarefas | Estimativa |
|---|:--:|---|
| Banco | | |
| Backend | | |
| Frontend | | |
| Testes | | |
| Documentação | | |
| Infra | | |
| **Total** | | |

## 3. Banco
| ID | Descrição | Dependências | Estimativa | Prioridade |
|---|---|---|:--:|:--:|

## 4. Backend
| ID | Descrição | Dependências | Estimativa | Prioridade |
|---|---|---|:--:|:--:|

## 5. Frontend
| ID | Descrição | Dependências | Estimativa | Prioridade |
|---|---|---|:--:|:--:|

## 6. Testes
| ID | Descrição | Dependências | Estimativa | Prioridade |
|---|---|---|:--:|:--:|

## 7. Documentação
| ID | Descrição | Dependências | Estimativa | Prioridade |
|---|---|---|:--:|:--:|

## 8. Infra
| ID | Descrição | Dependências | Estimativa | Prioridade |
|---|---|---|:--:|:--:|

## 9. Ordem de execução
<!-- Diagrama Mermaid do encadeamento e identificação do caminho crítico. -->

## 10. Critérios de conclusão por grupo
| Grupo | Concluído quando |
|---|---|
```

---

# Template — `acceptance.md`

```markdown
# NNN — <Nome> · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-NNN-XX`, estável |
| **Formato** | Gherkin: `Dado` / `Quando` / `Então` / `E` / `Mas` |
| **Categoria** | Feliz · Erro · Extremo · Segurança · Concorrência |
| **Regra** | `RN-XXX` verificada |

**Regras de escrita:**
- Um cenário verifica **um** comportamento.
- `Então` descreve resultado **observável** (resposta, estado persistido, evento), nunca implementação.
- Todo cenário de erro declara o código `DEVTIME-XXXX` e o status HTTP.
- Todo cenário é executável sem conhecimento adicional.

## 2. Índice
| ID | Categoria | Cenário | Regra |
|---|---|---|---|

## 3. Cenários felizes
## 4. Cenários de erro
## 5. Cenários extremos
## 6. Cenários de segurança
## 7. Cenários de concorrência
## 8. Matriz de cobertura de regras
| Regra | Cenários | Coberta |
|---|---|:--:|
```

---

# Template — `tests.md`

```markdown
# NNN — <Nome> · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-NNN-XX`, estável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` de todo teste de regra inicia com `RN-XXX`, permitindo extração automática da cobertura.

## 2. Estratégia
| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|

## 3. Testes unitários
## 4. Testes de integração
## 5. Testes de API
## 6. Testes de frontend
## 7. Testes E2E
## 8. Testes de performance
## 9. Testes de segurança
## 10. Testes de regressão

## 11. Matriz de rastreabilidade
| Regra | Testes | Cenários de aceite |
|---|---|---|

## 12. Dados de teste
| Fixture | Conteúdo | Uso |
|---|---|---|
```

---

# Checklists de referência

## Checklist de implementação (base)

- [ ] Li `project-constitution.md`, `coding-guidelines.md` e as regras da camada
- [ ] Todas as dependências da feature estão `DONE`
- [ ] Nenhuma lacuna de regra identificada (se houve, foi reportada e resolvida)
- [ ] Testes escritos **antes** do código, referenciando `RN-XXX` no `@DisplayName`
- [ ] Toda validação de negócio referencia sua regra em comentário (CM-02)
- [ ] Toda operação de escrita declara `@PreAuthorize` no Service (IMP-01)
- [ ] Nenhuma consulta sem filtro de tenant fora de `@CrossTenant` justificado (IA-10)
- [ ] Nenhuma entidade JPA em assinatura de Controller (IA-09)
- [ ] Todo DTO é `record` imutável; Request e Response são tipos distintos
- [ ] Mappers com `unmappedTargetPolicy = ERROR`
- [ ] Exclusão é lógica (`deletedAt`), nunca física
- [ ] Auditoria gerada na mesma transação (RN-006)
- [ ] Eventos publicados após a persistência (SV-06)
- [ ] Nenhuma chamada externa dentro de transação (TX-06)
- [ ] Componentes Angular `standalone` + `OnPush` + `input()`/`output()`
- [ ] Nenhum texto fixo em template (ART-095)
- [ ] Filtro, paginação e ordenação na URL
- [ ] Nenhum termo proibido do glossário
- [ ] Migration compatível com a versão anterior (DP-02)

## Checklist de revisão (base)

- [ ] PR possui rastreabilidade a story, requisito e regras (PR-02)
- [ ] Documentação atualizada no mesmo PR se o comportamento mudou (ART-111)
- [ ] Teste de isolamento entre tenants para todo endpoint novo
- [ ] Toda `RN-XXX` da spec possui teste que a referencia
- [ ] Toda transição proibida possui teste de rejeição
- [ ] Nenhuma resposta de erro vaza stack trace, SQL ou nome de tabela (EH-01)
- [ ] Nenhum segredo, dado sensível ou código morto
- [ ] Nenhum `TODO` sem issue vinculada (CM-05)
- [ ] ArchUnit, cobertura, lint e análise de dependências verdes
- [ ] Nenhuma consulta N+1 nos fluxos principais (DA-05)
- [ ] PR abaixo de 400 linhas alteradas (PR-01)

## Checklist de QA (base)

- [ ] Todos os cenários de `acceptance.md` executados e verdes
- [ ] Todos os testes de `tests.md` executados
- [ ] Cenários de segurança verificados (cross-tenant retorna `404`)
- [ ] Cenários de concorrência verificados
- [ ] Casos extremos verificados
- [ ] Mensagens de erro compreensíveis em pt-BR, sem jargão técnico
- [ ] Zero violações do axe-core nas telas da feature
- [ ] Navegação completa por teclado
- [ ] Metas de performance atingidas
- [ ] Comportamento correto após recarga, perda de conexão e troca de tenant
