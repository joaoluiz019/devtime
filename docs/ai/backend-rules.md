# Regras de Backend — DevTime

## 1. Objetivo

Estabelecer as regras obrigatórias de implementação do backend, em formato verificável e acionável por agentes de IA. Cada regra é uma instrução direta, com exemplo do que é proibido e do que é correto.

## 2. Escopo

| Dentro | Fora |
|---|---|
| Regras de implementação por camada | Arquitetura de solução (`03-architecture/backend.md`) |
| Padrões obrigatórios e proibições | Convenções transversais (`coding-guidelines.md`) |
| Templates de referência por tipo de classe | Modelo de dados (`03-architecture/database.md`) |
| Checklist de implementação de feature | Regras de negócio (`02-domain/business-rules.md`) |

## 3. Definições

| Termo | Definição |
|---|---|
| **Regra bloqueante** | Violação impede o merge. |
| **Verificação automática** | Checada por ArchUnit, lint ou teste. |
| **Verificação manual** | Checada em revisão de código. |
| **Template** | Estrutura de referência a ser seguida. |

---

## 4. Índice de regras

| Faixa | Área | Quantidade |
|---|---|:--:|
| `BR-001`–`BR-019` | Estrutura e camadas | 12 |
| `BR-020`–`BR-039` | Entidades e persistência | 15 |
| `BR-040`–`BR-059` | Multi-tenancy | 10 |
| `BR-060`–`BR-079` | Serviços e regras de negócio | 14 |
| `BR-080`–`BR-099` | Controllers e API | 13 |
| `BR-100`–`BR-119` | DTOs e mapeamento | 10 |
| `BR-120`–`BR-139` | Transações e concorrência | 9 |
| `BR-140`–`BR-159` | Tempo e cálculo | 11 |
| `BR-160`–`BR-179` | Segurança | 12 |
| `BR-180`–`BR-199` | Eventos e jobs | 9 |
| `BR-200`–`BR-219` | Testes | 12 |

---

## 5. Estrutura e camadas — `BR-001` a `BR-019`

| ID | Regra | Verificação |
|---|---|---|
| BR-001 | O código é organizado por feature (vertical slice), nunca por camada técnica | ArchUnit |
| BR-002 | Uma feature não acessa `Repository`, entidade ou implementação de outra feature | ArchUnit |
| BR-003 | O acesso entre features ocorre apenas pela interface `*Service` pública | ArchUnit |
| BR-004 | `shared` não depende de nenhuma feature | ArchUnit |
| BR-005 | Controller não acessa `Repository` | ArchUnit |
| BR-006 | Repository não acessa `Service` | ArchUnit |
| BR-007 | Entidade não depende de Spring, `Repository` ou apresentação | ArchUnit |
| BR-008 | Não existe ciclo de dependência entre pacotes de feature | ArchUnit |
| BR-009 | Toda feature expõe uma interface `*Service` e uma implementação `*ServiceImpl` | Revisão |
| BR-010 | Classe com mais de 300 linhas deve ser decomposta | Revisão |
| BR-011 | Método com mais de 40 linhas deve ser decomposto | Revisão |
| BR-012 | Método com mais de 5 parâmetros deve receber um objeto | Revisão |

**Exemplo de violação de BR-002:**

```java
// ❌ Proibido — worklog acessando repositório de ticket
@Service
class WorkLogServiceImpl {
    private final TicketRepository ticketRepository;   // BR-002
}

// ✅ Correto — acesso pela interface pública da feature
@Service
class WorkLogServiceImpl {
    private final TicketService ticketService;         // BR-003
}
```

---

## 6. Entidades e persistência — `BR-020` a `BR-039`

| ID | Regra | Verificação |
|---|---|---|
| BR-020 | Toda entidade de domínio estende `BaseEntity` | ArchUnit |
| BR-021 | Toda PK é `UUID` gerada como UUIDv7 na aplicação | ArchUnit |
| BR-022 | Nenhuma entidade usa `@GeneratedValue` com estratégia de banco | ArchUnit |
| BR-023 | Nenhum campo de duração usa `float`, `double` ou `BigDecimal` | ArchUnit |
| BR-024 | Nenhum campo monetário usa `float` ou `double` | ArchUnit |
| BR-025 | Todo campo monetário é acompanhado de campo de moeda | Revisão |
| BR-026 | Todo instante é `Instant`; toda data de calendário é `LocalDate` | Revisão |
| BR-027 | Nenhum campo usa `Date`, `Calendar` ou `Timestamp` | ArchUnit |
| BR-028 | Toda entidade possui `@Version` | ArchUnit |
| BR-029 | Toda entidade possui `@SQLRestriction("deleted_at IS NULL")` | ArchUnit |
| BR-030 | Nenhum código executa `DELETE` físico em entidade de domínio | Revisão |
| BR-031 | Relacionamento `@ManyToOne` usa `FetchType.LAZY` sempre | ArchUnit |
| BR-032 | `@OneToMany` é evitado; prefira consulta explícita por ID | Revisão |
| BR-033 | Nenhuma entidade implementa `equals`/`hashCode` por campos mutáveis; usar o `id` | Revisão |
| BR-034 | `ddl-auto` é sempre `validate` | Configuração |
| BR-035 | Migration nunca é alterada após o merge | Revisão |

**Exemplo de BR-023 e BR-026:**

```java
// ❌ Proibido
@Column private Double hours;          // BR-023
@Column private Date startedAt;        // BR-027

// ✅ Correto
@Column(name = "net_minutes", nullable = false)
private int netMinutes;                // ART-034

@Column(name = "started_at", nullable = false)
private Instant startedAt;             // ART-030
```

---

## 7. Multi-tenancy — `BR-040` a `BR-059`

| ID | Regra | Verificação |
|---|---|---|
| BR-040 | Toda entidade tenant-scoped possui `tenantId` não nulo e imutável | ArchUnit |
| BR-041 | O `tenantId` **nunca** é lido de body, query, path ou header | Revisão + teste |
| BR-042 | O `tenantId` é sempre obtido de `TenantContext.requireTenantId()` | Revisão |
| BR-043 | `TenantContext` vazio lança exceção; **nunca** retorna nulo ou valor padrão | Teste |
| BR-044 | Nenhum repositório expõe método sem filtro de tenant fora de `@CrossTenant` | ArchUnit |
| BR-045 | Todo uso de `@CrossTenant` declara justificativa e é revisado explicitamente | Revisão |
| BR-046 | Nenhuma consulta nativa escreve `tenant_id = ?` manualmente — o filtro é automático | Revisão |
| BR-047 | Recurso de outro tenant sempre resulta em `404`, nunca `403` | Teste |
| BR-048 | Toda criação valida que as entidades referenciadas pertencem ao mesmo tenant | Teste |
| BR-049 | Job que percorre tenants define o contexto a cada iteração | Revisão |
| BR-050 | Todo endpoint novo possui classe de teste de isolamento | Pipeline |

**Exemplo de BR-041 e BR-048:**

```java
// ❌ Proibido — aceitar tenantId do cliente
public WorkLog create(UUID tenantId, WorkLogCreateRequest request) { }   // BR-041

// ✅ Correto — derivar do contexto e validar referências
public WorkLog create(WorkLogCreateRequest request) {
    var ticket = ticketService.getActiveForWorkLog(request.ticketId());
    // ticketService já filtra por tenant automaticamente;
    // ticket de outro tenant resulta em EntityNotFound → 404 (BR-047, BR-048)
}
```

---

## 8. Serviços e regras de negócio — `BR-060` a `BR-079`

| ID | Regra | Verificação |
|---|---|---|
| BR-060 | Toda regra de negócio reside no serviço, nunca em controller, repositório ou mapper | Revisão |
| BR-061 | Toda validação de regra referencia seu identificador em comentário (`// RN-XXX`) | Revisão |
| BR-062 | A ordem de validação segue exatamente o documentado; a ordem é normativa | Teste |
| BR-063 | Toda exceção de negócio é criada por método fábrica nomeado pela regra | Revisão |
| BR-064 | Nenhum serviço retorna `null`; usar `Optional` ou lançar exceção | ArchUnit |
| BR-065 | Todo serviço de escrita declara `@PreAuthorize` | ArchUnit |
| BR-066 | Cálculo puro fica em `*Calculator`, sem efeito colateral | Revisão |
| BR-067 | Estratégia configurável fica em `*Policy`, com uma implementação por valor do enum | Revisão |
| BR-068 | Validação complexa fica em `*Validator` | Revisão |
| BR-069 | Serviço nunca conhece `HttpServletRequest`, `ResponseEntity` ou anotação HTTP | ArchUnit |
| BR-070 | Nenhuma chamada externa (e-mail, storage, HTTP) ocorre dentro de transação | Revisão |
| BR-071 | Toda alteração de estado passa por método de ação dedicado, nunca por `set` direto no status | Revisão |
| BR-072 | Toda guarda de transição é verificada antes de qualquer efeito | Revisão |
| BR-073 | Nenhum serviço decide regra não documentada | Revisão |

**Template de serviço:**

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractPeriodServiceImpl implements ContractPeriodService {

    private final ContractPeriodRepository repository;
    private final BalanceCalculator balanceCalculator;
    private final DomainEventPublisher events;
    private final TenantClock clock;

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'PERIOD_CLOSE')")
    public PeriodClosingResult close(UUID periodId, boolean confirmEarlyClose) {
        var period = repository.findByIdForUpdate(periodId)          // lock pessimista
            .orElseThrow(() -> EntityNotFoundException.of(ContractPeriod.class, periodId));

        assertCanClose(period, confirmEarlyClose);                    // RN-239
        assertNoActiveTimer(period);                                  // RN-240

        period.markClosing();
        var reconciled = reconcileConsumed(period);                   // RN-241 passo 1
        var carriedOut = calculateCarryOut(period, reconciled);       // RN-241 passo 2
        lockWorkLogs(period);                                         // RN-241 passo 3
        var snapshot = snapshotBuilder.build(period);                 // RN-241 passo 4
        period.markClosed(clock.now(), currentUserId(), carriedOut);  // RN-241 passo 5
        propagateCarryIn(period, carriedOut);                         // RN-241 passo 6

        events.publish(new PeriodClosedEvent(period.getId()));        // RN-241 passo 7
        return PeriodClosingResult.of(period, snapshot);
    }
}
```

---

## 9. Controllers e API — `BR-080` a `BR-099`

| ID | Regra | Verificação |
|---|---|---|
| BR-080 | Controller nunca contém regra de negócio | Revisão |
| BR-081 | Controller nunca declara `@Transactional` | ArchUnit |
| BR-082 | Controller nunca retorna entidade JPA | ArchUnit |
| BR-083 | Todo `@RequestBody` é anotado com `@Valid` | ArchUnit |
| BR-084 | Toda listagem recebe `Pageable` com `@PageableDefault` | Revisão |
| BR-085 | Nenhum controller contém `try/catch` de exceção de negócio | ArchUnit |
| BR-086 | Todo endpoint possui anotação OpenAPI descritiva | Revisão |
| BR-087 | Status HTTP é definido por `@ResponseStatus`, salvo quando headers dinâmicos forem necessários | Revisão |
| BR-088 | `201` sempre acompanha header `Location` | Teste |
| BR-089 | Ação de máquina de estado usa `POST /{recurso}/{id}/{ação}`, nunca `PATCH` no status | Revisão |
| BR-090 | Nenhum endpoint aceita `tenantId` como parâmetro | ArchUnit |
| BR-091 | Todo erro segue RFC 7807 com código `DEVTIME-XXXX` | Teste |
| BR-092 | Nenhuma resposta de erro contém stack trace, SQL ou nome de tabela | Teste |

---

## 10. DTOs e mapeamento — `BR-100` a `BR-119`

| ID | Regra | Verificação |
|---|---|---|
| BR-100 | Todo DTO é um `record` imutável | ArchUnit |
| BR-101 | Request e Response são tipos distintos; nunca reutilizados | Revisão |
| BR-102 | DTO nunca contém entidade JPA | ArchUnit |
| BR-103 | Validação cruzada usa método `@AssertTrue` no próprio record | Revisão |
| BR-104 | Mapper usa `unmappedTargetPolicy = ReportingPolicy.ERROR` | Revisão |
| BR-105 | Mapper nunca acessa banco nem contém regra de negócio | ArchUnit |
| BR-106 | Formatação de duração e data ocorre no mapper, nunca na entidade | Revisão |
| BR-107 | Listagens retornam projeção, nunca a entidade completa | Revisão |
| BR-108 | Campo sensível (`passwordHash`, `tokenHash`) nunca aparece em Response | Teste |
| BR-109 | Nome de campo JSON é `camelCase` e coincide com o glossário | Revisão |

---

## 11. Transações e concorrência — `BR-120` a `BR-139`

| ID | Regra | Verificação |
|---|---|---|
| BR-120 | `@Transactional` aparece apenas em classes com sufixo `Service`/`ServiceImpl` | ArchUnit |
| BR-121 | Classe de serviço declara `@Transactional(readOnly = true)`; escrita sobrescreve | Revisão |
| BR-122 | `REQUIRES_NEW` exige justificativa em comentário | Revisão |
| BR-123 | Isolamento padrão é `READ_COMMITTED`; alteração exige justificativa | Revisão |
| BR-124 | Operação crítica de concorrência usa lock pessimista explícito | Revisão |
| BR-125 | Toda entidade editável usa `@Version`; conflito retorna `409` | Teste |
| BR-126 | Transação com mais de 3 segundos gera alerta e deve ser investigada | Métrica |
| BR-127 | Nenhuma transação abrange chamada externa | Revisão |
| BR-128 | Efeito colateral não essencial usa `@TransactionalEventListener(AFTER_COMMIT)` | Revisão |

**Exemplo de BR-128:**

```java
// ❌ Proibido — falha no e-mail desfaz o registro de horas
@EventListener
void onWorkLogCreated(WorkLogCreatedEvent e) {
    mailService.send(...);                                   // BR-127, BR-128
}

// ✅ Correto — separação por criticidade
@EventListener                                               // dentro da transação
void updateAggregates(WorkLogCreatedEvent e) {
    ticketService.addMinutes(e.ticketId(), e.netMinutes());
}

@TransactionalEventListener(phase = AFTER_COMMIT)            // após o commit
void evaluateThresholds(WorkLogCreatedEvent e) {
    thresholdEvaluator.evaluate(e.periodId());               // RN-602
}
```

---

## 12. Tempo e cálculo — `BR-140` a `BR-159`

| ID | Regra | Verificação |
|---|---|---|
| BR-140 | Nenhum código usa `Instant.now()`, `LocalDate.now()` ou `System.currentTimeMillis()` diretamente | ArchUnit |
| BR-141 | O instante atual vem sempre de um `Clock` injetado | ArchUnit |
| BR-142 | A data local vem de `TenantClock`, no fuso do tenant | Revisão |
| BR-143 | Toda duração é `int` em minutos | ArchUnit |
| BR-144 | Segundos são truncados com divisão inteira, nunca arredondados | Teste |
| BR-145 | Arredondamento configurado é sempre para baixo | Teste |
| BR-146 | Cálculo monetário usa `BigDecimal` com `RoundingMode` explícito | Revisão |
| BR-147 | Comparação de `BigDecimal` usa `compareTo`, nunca `equals` | Revisão |
| BR-148 | Intervalo de instantes é semi-aberto `[início, fim)` | Teste |
| BR-149 | Intervalo de datas é fechado `[início, fim]` | Teste |
| BR-150 | Cálculo é determinístico: mesma entrada produz sempre a mesma saída | Teste |

**Exemplo de BR-144:**

```java
// ❌ Proibido — arredonda e pode cobrar tempo não trabalhado
int gross = (int) Math.round(Duration.between(start, end).toSeconds() / 60.0);

// ✅ Correto — trunca (RN-010, PR-03)
int gross = (int) (Duration.between(start, end).toSeconds() / 60);
```

---

## 13. Segurança — `BR-160` a `BR-179`

| ID | Regra | Verificação |
|---|---|---|
| BR-160 | Todo endpoint é negado por padrão; o acesso público é declarado em allowlist explícita | Teste |
| BR-161 | A permissão é verificada na camada de serviço, não apenas no controller | ArchUnit |
| BR-162 | O papel vem do `TenantContext`, nunca da requisição | Revisão |
| BR-163 | Permissões são derivadas do papel a cada requisição, nunca lidas do token | Revisão |
| BR-164 | Senha usa BCrypt custo 12 | Revisão |
| BR-165 | Refresh token é persistido apenas como hash SHA-256 | Revisão |
| BR-166 | Nenhum segredo em código, configuração versionada ou log | Scanner |
| BR-167 | Nenhum dado sensível em log; máscara obrigatória | Teste |
| BR-168 | Nenhuma concatenação de string em consulta SQL | ArchUnit |
| BR-169 | Consulta dinâmica usa `Specification` ou parâmetros vinculados | Revisão |
| BR-170 | Toda entrada externa é validada antes de qualquer uso | Revisão |
| BR-171 | Upload valida tamanho, tipo declarado e assinatura binária | Teste |

---

## 14. Eventos e jobs — `BR-180` a `BR-199`

| ID | Regra | Verificação |
|---|---|---|
| BR-180 | Evento de domínio é `record` imutável, implementando a interface selada | Revisão |
| BR-181 | Evento carrega identificadores, nunca entidades | Revisão |
| BR-182 | Evento é publicado após a persistência, nunca antes | Revisão |
| BR-183 | Efeito que deve ser atômico usa `@EventListener`; efeito colateral usa `AFTER_COMMIT` | Revisão |
| BR-184 | Todo job usa `@SchedulerLock` com `lockAtMostFor` maior que a duração esperada | Revisão |
| BR-185 | Todo job é idempotente e seguro para reexecução | Teste |
| BR-186 | Todo job processa em lotes com limite por execução | Revisão |
| BR-187 | Falha em um tenant não interrompe o processamento dos demais | Teste |
| BR-188 | Todo job emite métrica de início, fim, itens processados e falhas | Revisão |

---

## 15. Testes — `BR-200` a `BR-219`

| ID | Regra | Verificação |
|---|---|---|
| BR-200 | Todo teste de regra referencia `RN-XXX` no `@DisplayName` | Pipeline |
| BR-201 | Todo teste de integração usa Testcontainers com PostgreSQL | ArchUnit |
| BR-202 | Nenhum teste usa H2 ou banco em memória | ArchUnit |
| BR-203 | Nenhum teste depende de rede externa | Revisão |
| BR-204 | Nenhum teste depende de ordem de execução | Configuração |
| BR-205 | Nenhum teste usa relógio real | ArchUnit |
| BR-206 | Nenhum teste usa dado aleatório | Revisão |
| BR-207 | Dados de teste são criados por builders, nunca por SQL bruto | Revisão |
| BR-208 | Todo endpoint possui classe de teste de isolamento | Pipeline |
| BR-209 | Toda correção de bug começa por um teste que o reproduz | Revisão |
| BR-210 | Nenhum teste é desabilitado sem issue vinculada | Pipeline |
| BR-211 | Todo teste possui ao menos uma asserção significativa | Revisão |

---

## 16. Checklist de implementação de feature

Ao implementar uma feature completa, execute nesta ordem:

| # | Passo | Verificação |
|---|---|---|
| 1 | Ler a documentação da feature em `02-domain/`, `04-api/` e `05-ui/` | Toda regra está documentada? |
| 2 | Identificar as `RN-XXX` aplicáveis | Lista completa? |
| 3 | Criar a migration Flyway | Segue as convenções de `database.md`? |
| 4 | Criar a entidade estendendo `BaseEntity` | BR-020 a BR-035 |
| 5 | Criar o repositório com projeções | BR-044, BR-107 |
| 6 | Criar DTOs de request e response | BR-100 a BR-109 |
| 7 | Criar o mapper com `ReportingPolicy.ERROR` | BR-104 |
| 8 | Escrever os testes das regras **antes** da implementação | BR-200 |
| 9 | Implementar a interface e a implementação do serviço | BR-060 a BR-073 |
| 10 | Implementar o controller | BR-080 a BR-092 |
| 11 | Criar a classe de teste de isolamento | BR-208 |
| 12 | Escrever testes de integração | BR-201 |
| 13 | Verificar cobertura (80% global, 90% em serviços) | Pipeline |
| 14 | Atualizar a documentação se algo mudou | ART-111 |
| 15 | Percorrer `review-checklist.md` | Todos os itens |
| 16 | Verificar `definition-of-done.md` | Todos os itens |

---

## 17. Erros comuns e correções

| Erro | Por que acontece | Correção |
|---|---|---|
| Duração em `double` | Hábito de representar horas | `int` em minutos (BR-143) |
| `Instant.now()` no serviço | Conveniência | `Clock` injetado (BR-141) |
| Entidade retornada pelo controller | Economia de código | DTO + mapper (BR-082) |
| `@Transactional` no controller | Confusão de responsabilidade | Mover para o serviço (BR-081) |
| Repositório de outra feature injetado | Caminho mais curto | Interface de serviço (BR-002) |
| Query nativa com `tenant_id` manual | Desconhecimento do filtro | Remover; o filtro é automático (BR-046) |
| `403` para recurso de outro tenant | Parece mais correto | `404` sempre (BR-047) |
| Arredondamento para cima | Parece justo | Sempre para baixo (BR-145) |
| E-mail dentro da transação | Simplicidade | `AFTER_COMMIT` (BR-128) |
| Teste com `Thread.sleep` | Sincronização preguiçosa | Espera por condição (BR-204) |
| Regra decidida no código | Documentação incompleta | Parar e reportar a lacuna (BR-073) |

---

## 18. Casos especiais

| # | Caso | Tratamento |
|---|---|---|
| CE-B-01 | Consulta precisa cruzar tenants | `@CrossTenant` com justificativa + teste de isolamento adicional |
| CE-B-02 | Regra exige `REQUIRES_NEW` | Permitido apenas em registro de falha, com comentário justificando |
| CE-B-03 | Consulta muito complexa para Specification | Query nativa com justificativa e revisão explícita |
| CE-B-04 | Biblioteca exige mutabilidade em DTO | Adaptador na fronteira; o domínio mantém imutabilidade |
| CE-B-05 | Cálculo pesado repetido | Cache por requisição; Redis apenas a partir de F6 |
| CE-B-06 | Entidade com mais de 30 campos | Extrair Value Object embutido |
| CE-B-07 | Duas features precisam da mesma regra | Extrair para `shared` só se não for regra de domínio específico |
| CE-B-08 | Migration precisa de correção após merge | Nova migration; nunca alterar a anterior (BR-035) |

## 19. Casos de erro

| Situação | Consequência |
|---|---|
| Violação de regra verificada por ArchUnit | Build falha |
| Query sem filtro de tenant | Build falha na suíte de isolamento |
| Cobertura abaixo da meta | Build falha |
| Regra sem teste referenciando o ID | Build falha |
| Entidade exposta na API | Build falha |
| `double` em duração ou dinheiro | Build falha |
| Migration alterada após merge | PR revertido |
| Regra de negócio inventada | PR revertido |

## 20. Critérios de aceite

| # | Critério |
|---|---|
| CA-01 | Todas as regras verificáveis automaticamente possuem teste de arquitetura |
| CA-02 | Nenhuma violação de regra bloqueante existe na branch principal |
| CA-03 | Todo serviço de escrita declara permissão |
| CA-04 | Toda validação de regra referencia seu identificador |
| CA-05 | Nenhuma consulta de domínio executa sem filtro de tenant |
| CA-06 | Nenhum cálculo usa ponto flutuante para duração ou dinheiro |
| CA-07 | Nenhum código usa relógio real |
| CA-08 | Todo endpoint possui teste de isolamento |

## 21. Dependências e impactos

| Documento | Relação |
|---|---|
| `project-constitution.md` | Fonte normativa das proibições |
| `03-architecture/backend.md` | Define a arquitetura implementada por estas regras |
| `03-architecture/database.md` | Define o schema mapeado |
| `03-architecture/security.md` | Define os controles de segurança |
| `coding-guidelines.md` | Convenções transversais |
| `review-checklist.md` | Verificação destas regras |

**Impacto:** adicionar uma regra bloqueante exige implementar sua verificação automática ou incluí-la explicitamente no checklist de revisão.
