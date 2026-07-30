# 010 — Dashboard · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-010-XX`, estável e imutável |
| **Formato** | Gherkin: `Dado` / `Quando` / `Então` / `E` / `Mas` |
| **Categoria** | Feliz · Erro · Extremo · Segurança · Concorrência |
| **Regra** | `RN-XXX`, invariante ou regra de composição verificada |

**Regras de escrita:**
- Um cenário verifica **um** comportamento.
- `Então` descreve resultado **observável**, nunca implementação.
- Todo cenário de erro declara o código `DEVTIME-XXXX` e o status HTTP.
- Todo cenário é executável sem conhecimento adicional.

## 2. Índice

| ID | Categoria | Cenário | Regra |
|---|---|---|---|
| AC-010-01 | Feliz | Estatísticas rápidas | §10.1 reports.md |
| AC-010-02 | Feliz | Saldo idêntico ao de `011` | INV-DSH-01 |
| AC-010-03 | Feliz | Ordenação por criticidade | CP-02 |
| AC-010-04 | Feliz | Severidade pelos limiares default | §6.2 |
| AC-010-05 | Feliz | Projeção dentro do limite | §6.3 |
| AC-010-06 | Feliz | Alertas do estado atual | CP-03 |
| AC-010-07 | Feliz | Registros recentes limitados a cinco | CP-05 |
| AC-010-08 | Feliz | Tickets abertos do usuário | §10.1 |
| AC-010-09 | Feliz | Gráfico diário com 30 pontos | CP-04 |
| AC-010-10 | Feliz | Distribuição por cliente e categoria | §10.1 |
| AC-010-11 | Feliz | Recarga isolada de gráfico | §10.2 |
| AC-010-12 | Feliz | Timer ativo nas estatísticas | §10.1 |
| AC-010-13 | Feliz | Escopo `TENANT` para papéis gerenciais | CP-01 |
| AC-010-14 | Feliz | Período aberto marcado como parcial | RN-702 |
| AC-010-15 | Erro | Intervalo personalizado acima de 366 dias | RN-705 |
| AC-010-16 | Erro | `CUSTOM` sem `from` ou `to` | §10.1 |
| AC-010-17 | Erro | Tipo de gráfico inválido | §10.2 |
| AC-010-18 | Erro | Sem permissão de dashboard | §7 permissions |
| AC-010-19 | Erro | Tenant cancelado | RN-008 |
| AC-010-20 | Extremo | Tenant sem contratos | CX-01 |
| AC-010-21 | Extremo | Período sem nenhum registro | CX-02 |
| AC-010-22 | Extremo | Um único dia com registro | CX-03 |
| AC-010-23 | Extremo | Menos de três dias úteis decorridos | CX-04 |
| AC-010-24 | Extremo | Contrato `HOURLY_OPEN` | CX-05 |
| AC-010-25 | Extremo | Contrato com limiares personalizados | CX-08 |
| AC-010-26 | Extremo | Cinquenta contratos ativos | CX-07 |
| AC-010-27 | Extremo | Ajuste resolve o excedente | CX-09 |
| AC-010-28 | Extremo | Período corrente já fechado | CX-10 |
| AC-010-29 | Extremo | Percentuais com resto de arredondamento | CX-14 |
| AC-010-30 | Extremo | Fuso do tenant diferente do navegador | CX-19 |
| AC-010-31 | Extremo | Dia de virada de horário de verão | CX-20 |
| AC-010-32 | Extremo | Falha isolada em um bloco | CX-17 |
| AC-010-33 | Extremo | Categoria excluída com registros no período | CX-16 |
| AC-010-34 | Segurança | Contrato de outro tenant retorna 404 | RN-002 |
| AC-010-35 | Segurança | `MEMBER` recebe escopo `USER` | INV-DSH-02 |
| AC-010-36 | Segurança | `MEMBER` não recebe valores monetários | INV-DSH-04 |
| AC-010-37 | Segurança | `MEMBER` não vê carteira completa de clientes | SG-04 |
| AC-010-38 | Segurança | Cache não vaza entre tenants | SG-07 |
| AC-010-39 | Segurança | Nenhuma escrita ocorre | RS-01 |
| AC-010-40 | Concorrência | Evento invalida o cache | §15 |
| AC-010-41 | Concorrência | Registro criado durante a carga | §20 |
| AC-010-42 | Concorrência | Cargas simultâneas de dois usuários | CP-01 |

---

## 3. Cenários felizes

### AC-010-01 — Estatísticas rápidas
```gherkin
Dado um tenant com registros hoje, na semana e no período corrente
E que estou autenticado com a permissão DASHBOARD_VIEW_ANY
Quando eu envio GET /api/v1/dashboard
Então recebo 200 OK
E quickStats traz todayMinutes, weekMinutes e periodMinutes
E cada valor possui um rótulo correspondente no formato HH:MM
E os totais correspondem à soma dos work logs nos respectivos intervalos
E os intervalos foram calculados no fuso do tenant
```

### AC-010-02 — Saldo idêntico ao de `011`
```gherkin
Dado um contrato com período aberto e work logs registrados
Quando eu consulto o dashboard
E eu consulto o saldo do mesmo período diretamente em GET /api/v1/contract-periods/{id}
Então availableMinutes, consumedMinutes, remainingMinutes e consumptionRate são idênticos nas duas respostas
E o dashboard não recalculou nenhum desses valores
```

### AC-010-03 — Ordenação por criticidade
```gherkin
Dado cinco contratos ativos com consumptionRate de 30%, 105%, 85%, 60% e 95%
E que dois deles possuem o mesmo consumptionRate de 85%, com 2 e 10 dias restantes
Quando eu consulto o dashboard
Então os contratos são retornados na ordem CRITICAL, WARNING, WARNING, INFO, OK
E entre os dois WARNING de mesma severidade, o de 2 dias restantes aparece primeiro
```

### AC-010-04 — Severidade pelos limiares default
```gherkin
Dado um contrato com notificationThresholds igual a 50, 80 e 100
Quando o consumptionRate é 30%, 60%, 85% e 105% em consultas sucessivas
Então a severity retornada é respectivamente OK, INFO, WARNING e CRITICAL
```

### AC-010-05 — Projeção dentro do limite
```gherkin
Dado um período de 30 dias com 10 dias úteis decorridos
E consumo compatível com o saldo disponível até o fim
Quando eu consulto o dashboard
Então projectedConsumedMinutes é calculado a partir do burnRate
E projectionStatus é WITHIN_LIMIT
```

### AC-010-06 — Alertas do estado atual
```gherkin
Dado um contrato cujo consumo atual é 83% do saldo
Quando eu consulto o dashboard
Então alerts contém uma entrada com severity WARNING
E a mensagem menciona o nome do contrato e o percentual atual
E entityType é CONTRACT_PERIOD com o entityId correspondente
E o alerta foi derivado do estado presente, não da tabela de notificações
```

### AC-010-07 — Registros recentes limitados a cinco
```gherkin
Dado vinte work logs registrados no tenant
Quando eu consulto o dashboard
Então recentWorkLogs contém exatamente 5 entradas
E são as cinco mais recentes por data de registro
```

### AC-010-08 — Tickets abertos do usuário
```gherkin
Dado tickets em BACKLOG, TODO, IN_PROGRESS, DONE e CANCELLED atribuídos a mim
Quando eu consulto o dashboard
Então openTickets contém apenas os que não estão em DONE nem CANCELLED
```

### AC-010-09 — Gráfico diário com 30 pontos
```gherkin
Dado um período em que apenas 12 dias possuem registros
Quando eu consulto o dashboard
Então charts.dailyMinutes contém exatamente 30 pontos
E os 18 dias sem registro aparecem com netMinutes igual a 0
E nenhum dia é omitido da série
```

### AC-010-10 — Distribuição por cliente e categoria
```gherkin
Dado registros distribuídos entre três clientes e quatro categorias
Quando eu consulto o dashboard
Então charts.byClient traz label, color, minutes e percentage por cliente
E charts.byCategory traz os mesmos campos por categoria
E as cores são as das entidades de origem, não geradas no dashboard
E a soma dos percentuais é 100
```

### AC-010-11 — Recarga isolada de gráfico
```gherkin
Dado o dashboard já carregado
Quando eu envio GET /api/v1/dashboard/chart/by-category com outro período
Então recebo 200 OK apenas com os dados daquele gráfico
E nenhuma outra agregação do dashboard é executada
```

### AC-010-12 — Timer ativo nas estatísticas
```gherkin
Dado que eu possuo um cronômetro RUNNING há 45 minutos
Quando eu consulto o dashboard
Então quickStats.activeTimerMinutes é 45
E o bloco correspondente destaca o ticket do cronômetro
```

### AC-010-13 — Escopo `TENANT` para papéis gerenciais
```gherkin
Dado que estou autenticado com o papel MANAGER
Quando eu consulto o dashboard
Então scope é TENANT
E quickStats considera os registros de todos os membros
E os gráficos consideram todos os clientes e categorias do tenant
```

### AC-010-14 — Período aberto marcado como parcial
```gherkin
Dado um contrato cujo período corrente está OPEN
Quando eu consulto o dashboard
Então o cartão desse contrato indica que os valores são parciais
E a interface exibe o selo de parcial
```

---

## 4. Cenários de erro

### AC-010-15 — Intervalo personalizado acima de 366 dias
```gherkin
Quando eu envio GET /api/v1/dashboard com period CUSTOM e um intervalo de 367 dias
Então recebo 400 Bad Request com o código DEVTIME-3001
E nenhuma agregação é executada
Quando eu envio com exatamente 366 dias
Então recebo 200 OK
```

### AC-010-16 — `CUSTOM` sem `from` ou `to`
```gherkin
Quando eu envio GET /api/v1/dashboard com period CUSTOM sem informar from
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
E a mensagem orienta a informar o período personalizado
```

### AC-010-17 — Tipo de gráfico inválido
```gherkin
Quando eu envio GET /api/v1/dashboard/chart/inexistente
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
E a mensagem indica que o tipo de gráfico é inválido
```

### AC-010-18 — Sem permissão de dashboard
```gherkin
Dado um usuário sem DASHBOARD_VIEW_OWN nem DASHBOARD_VIEW_ANY
Quando ele envia GET /api/v1/dashboard
Então recebe 403 Forbidden com o código DEVTIME-1101
E o item de menu do dashboard não é exibido para ele
```

### AC-010-19 — Tenant cancelado
```gherkin
Dado um tenant com status CANCELLED após o período de retenção
Quando eu consulto o dashboard
Então recebo 403 Forbidden com o código DEVTIME-1202
Mas um tenant SUSPENDED permite a consulta normalmente, pois a leitura é liberada
```

---

## 5. Cenários extremos

### AC-010-20 — Tenant sem contratos
```gherkin
Dado um tenant recém-criado sem nenhum contrato
Quando eu consulto o dashboard
Então recebo 200 OK com a estrutura completa e vazia
E a interface exibe um estado de boas-vindas
E o estado aponta para o fluxo de onboarding
E nenhuma agregação de gráfico é executada
```

### AC-010-21 — Período sem nenhum registro
```gherkin
Dado um tenant com contratos ativos e nenhum work log no período
Quando eu consulto o dashboard
Então quickStats traz todos os valores em zero
E charts.dailyMinutes traz 30 pontos, todos com zero
E byClient e byCategory retornam listas vazias
E a interface exibe mensagem explicativa nos gráficos, não os oculta
```

### AC-010-22 — Um único dia com registro
```gherkin
Dado um período em que apenas um dia possui 480 minutos registrados
Quando eu consulto o gráfico diário
Então 29 pontos aparecem com zero e 1 ponto com 480
E o gráfico não comprime o eixo nem sugere tendência
```

### AC-010-23 — Menos de três dias úteis decorridos
```gherkin
Dado um período com apenas 2 dias úteis decorridos
Quando eu consulto o dashboard
Então projectionStatus é NOT_APPLICABLE para todos os contratos
E nenhum valor de projeção é exibido na interface
Quando o terceiro dia útil se completa
Então a projeção passa a ser calculada e exibida
```

### AC-010-24 — Contrato `HOURLY_OPEN`
```gherkin
Dado um contrato do tipo HOURLY_OPEN com 600 minutos consumidos
Quando eu consulto o dashboard
Então availableMinutes é 0
E consumptionRate é 0
E severity é OK
E projectionStatus é NOT_APPLICABLE
E nenhum alerta é gerado para esse contrato
```

### AC-010-25 — Contrato com limiares personalizados
```gherkin
Dado um contrato com notificationThresholds igual a 70 e 90
Quando o consumptionRate é 75%
Então severity é INFO, e não OK
Quando o consumptionRate é 92%
Então severity é WARNING
E a escala usada é a do contrato, não a padrão de 50 e 80
```

### AC-010-26 — Cinquenta contratos ativos
```gherkin
Dado um tenant com 50 contratos ativos
Quando eu consulto o dashboard
Então os 10 contratos mais críticos são retornados na primeira carga
E os demais são carregados por rolagem
E o tempo de resposta permanece dentro da meta
```

### AC-010-27 — Ajuste resolve o excedente
```gherkin
Dado um contrato com excedente e um alerta CRITICAL no dashboard
E uma notificação de excedente já registrada no histórico
Quando um ajuste de saldo elimina o excedente
E eu consulto o dashboard novamente
Então o alerta CRITICAL não aparece mais
E a severity volta a WARNING ou inferior
Mas a notificação anterior permanece no histórico de notificações
```

### AC-010-28 — Período corrente já fechado
```gherkin
Dado um contrato encerrado cujo período corrente está CLOSED
Quando eu consulto o dashboard
Então os valores de saldo vêm do snapshot do período
E o cartão não exibe o selo de parcial
E os valores são marcados como definitivos
```

### AC-010-29 — Percentuais com resto de arredondamento
```gherkin
Dado três fatias cujos percentuais somariam 99,99 por arredondamento
Quando eu consulto o gráfico de distribuição
Então a soma dos percentuais retornados é exatamente 100
E o resto foi atribuído à maior fatia
E nenhuma fatia exibe percentual negativo
```

### AC-010-30 — Fuso do tenant diferente do navegador
```gherkin
Dado um tenant no fuso America/Sao_Paulo
E um usuário acessando de um navegador em UTC
Quando um registro é feito às 22:00 no horário local do tenant
E eu consulto o dashboard
Então esse registro é contabilizado no dia local do tenant
E não no dia seguinte
```

### AC-010-31 — Dia de virada de horário de verão
```gherkin
Dado um período que contém o dia de transição de horário de verão
Quando eu consulto o gráfico diário
Então o dia da transição aparece exatamente uma vez
E nenhum dia é duplicado nem omitido
E o total do dia corresponde aos registros com aquela data local
```

### AC-010-32 — Falha isolada em um bloco
```gherkin
Dado que a agregação de gráficos falhará por erro de banco
Quando eu consulto o dashboard
Então quickStats, contracts, alerts, recentWorkLogs e openTickets são exibidos normalmente
E o bloco de gráficos exibe um estado de erro com ação de tentar novamente
E a tela não fica em branco
E o restante da página permanece utilizável
```

### AC-010-33 — Categoria excluída com registros no período
```gherkin
Dado work logs vinculados a uma categoria posteriormente excluída
Quando eu consulto o gráfico por categoria
Então a categoria aparece com o nome vigente
E nenhuma fatia aparece sem rótulo
```

---

## 6. Cenários de segurança

### AC-010-34 — Contrato de outro tenant retorna 404
```gherkin
Dado um contrato pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu consulto o dashboard
Então nenhum dado do tenant B aparece em nenhum bloco
E nenhum total inclui registros do tenant B
Quando eu solicito um gráfico referenciando um contrato do tenant B
Então recebo 404 Not Found com o código DEVTIME-2002
```

### AC-010-35 — `MEMBER` recebe escopo `USER`
```gherkin
Dado um tenant com 100 work logs, dos quais 20 são meus
E que estou autenticado com o papel MEMBER
Quando eu consulto o dashboard
Então scope é USER
E quickStats considera apenas os meus 20 registros
E charts.dailyMinutes soma apenas os meus registros
E recentWorkLogs traz apenas registros meus
E o filtro por usuário está presente na consulta ao banco, não em memória
```

### AC-010-36 — `MEMBER` não recebe valores monetários
```gherkin
Dado que estou autenticado com o papel MEMBER e vinculado a um contrato
Quando eu consulto o dashboard
Então eu vejo o cartão desse contrato com minutos disponíveis, consumidos e restantes
E nenhum valor monetário está presente na resposta
E nenhum campo de taxa ou valor estimado é retornado
```

### AC-010-37 — `MEMBER` não vê carteira completa de clientes
```gherkin
Dado um tenant com dez clientes
E que estou autenticado com o papel MEMBER, vinculado a contratos de apenas dois deles
Quando eu consulto o gráfico por cliente
Então apenas os dois clientes vinculados aparecem
E os outros oito não aparecem em nenhuma fatia nem no total
```

### AC-010-38 — Cache não vaza entre tenants
```gherkin
Dado dois tenants com dados distintos
E que um gráfico do tenant A foi carregado e cacheado
Quando um usuário do tenant B solicita o mesmo tipo de gráfico no mesmo período
Então os dados retornados são exclusivamente do tenant B
E nenhum valor do tenant A é reaproveitado
E a chave de cache inclui o identificador do tenant e o escopo resolvido
```

### AC-010-39 — Nenhuma escrita ocorre
```gherkin
Quando eu consulto o dashboard e todos os seis tipos de gráfico
Então nenhuma instrução de escrita é emitida ao banco
E nenhum AuditLog é criado
E nenhum evento de domínio é publicado por esta feature
```

---

## 7. Cenários de concorrência

### AC-010-40 — Evento invalida o cache
```gherkin
Dado um gráfico já cacheado para o tenant
Quando um work log é criado nesse tenant
E eu consulto o mesmo gráfico novamente
Então os dados retornados incluem o novo registro
E o cache anterior foi invalidado pelo evento
```

### AC-010-41 — Registro criado durante a carga
```gherkin
Dado uma carga de dashboard em andamento
Quando um work log é criado no mesmo instante
Então a resposta reflete um estado consistente, com ou sem o novo registro
E nunca um estado misto em que quickStats inclui o registro e os gráficos não
Ou, havendo divergência entre blocos, ela desaparece na carga seguinte
```

### AC-010-42 — Cargas simultâneas de dois usuários
```gherkin
Dado um MANAGER e um MEMBER do mesmo tenant
Quando ambos consultam o dashboard simultaneamente
Então o MANAGER recebe scope TENANT com todos os dados
E o MEMBER recebe scope USER apenas com os próprios
E nenhuma resposta é contaminada pela outra, mesmo com cache ativo
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| INV-DSH-01 | AC-010-02 | ✅ |
| INV-DSH-02 | AC-010-35, AC-010-37 | ✅ |
| INV-DSH-03 | AC-010-09, AC-010-21, AC-010-22 | ✅ |
| INV-DSH-04 | AC-010-36 | ✅ |
| CP-01 | AC-010-13, AC-010-35, AC-010-42 | ✅ |
| CP-02 | AC-010-03 | ✅ |
| CP-03 | AC-010-06, AC-010-27 | ✅ |
| CP-04 | AC-010-09, AC-010-21, AC-010-22 | ✅ |
| CP-05 | AC-010-07 | ✅ |
| CP-06 | AC-010-29 | ✅ |
| §6.2 severidade | AC-010-04, AC-010-24, AC-010-25 | ✅ |
| §6.3 projeção | AC-010-05, AC-010-23, AC-010-24 | ✅ |
| RN-218 a RN-222 | AC-010-02, AC-010-24 | ✅ |
| RN-702 | AC-010-14, AC-010-28 | ✅ |
| RN-705 | AC-010-15 | ✅ |
| RN-009 | AC-010-01, AC-010-30, AC-010-31 | ✅ |
| RN-002 | AC-010-34 | ✅ |
| RN-008 | AC-010-19 | ✅ |
| §10.1 reports.md | AC-010-01, AC-010-07, AC-010-08, AC-010-10, AC-010-12 | ✅ |
| §10.2 reports.md | AC-010-11, AC-010-17 | ✅ |
| §7 permissions | AC-010-18 | ✅ |
| §9 permissions | AC-010-13, AC-010-35, AC-010-36, AC-010-37 | ✅ |
| SG-04 | AC-010-37 | ✅ |
| SG-07 | AC-010-38, AC-010-42 | ✅ |
| RS-01 | AC-010-39 | ✅ |
| CX-05 / CX-09 / CX-16 / CX-17 | AC-010-24, AC-010-27, AC-010-33, AC-010-32 | ✅ |

**Verificação de completude:** toda regra da §6 e toda regra de composição da §6.1 da spec possuem ao menos um cenário. `AC-010-02` (equivalência com `011`) é o cenário mais importante da feature: ele verifica INV-DSH-01, que impede o dashboard de se tornar uma segunda fonte de verdade para o saldo.
