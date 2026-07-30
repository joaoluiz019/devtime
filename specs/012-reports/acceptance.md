# 012 — Reports & Export · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-012-XX`, estável e imutável |
| **Formato** | Gherkin: `Dado` / `Quando` / `Então` / `E` / `Mas` |
| **Categoria** | Feliz · Erro · Extremo · Segurança · Concorrência |
| **Regra** | `RN-XXX` ou invariante verificada |

**Regras de escrita:**
- Um cenário verifica **um** comportamento.
- `Então` descreve resultado **observável**, nunca implementação.
- Todo cenário de erro declara o código `DEVTIME-XXXX` e o status HTTP.
- Todo cenário é executável sem conhecimento adicional.

## 2. Índice

| ID | Categoria | Cenário | Regra |
|---|---|---|---|
| AC-012-01 | Feliz | Período fechado servido do snapshot | RN-701 |
| AC-012-02 | Feliz | PDF determinístico | RN-708 |
| AC-012-03 | Feliz | Período aberto marcado como parcial | RN-702 |
| AC-012-04 | Feliz | Cabeçalho com identificador de emissão | RN-703 |
| AC-012-05 | Feliz | Resumo consolidado por cliente | §7.1 reports.md |
| AC-012-06 | Feliz | Folha de horas por intervalo | §7.2 |
| AC-012-07 | Feliz | Detalhamento por ticket | §7.3 |
| AC-012-08 | Feliz | Relatório de produtividade | §7.4 |
| AC-012-09 | Feliz | Ordenação normativa | §6.3 |
| AC-012-10 | Feliz | Exportação síncrona | RN-706 |
| AC-012-11 | Feliz | Exportação assíncrona | RN-706 |
| AC-012-12 | Feliz | Duas colunas de duração no Excel | RN-710 |
| AC-012-13 | Feliz | Formatação monetária | RN-709 |
| AC-012-14 | Feliz | Download por URL assinada | RN-712 |
| AC-012-15 | Feliz | Registro de execução com filtros | RN-707 |
| AC-012-16 | Erro | Intervalo acima de 366 dias | RN-705 |
| AC-012-17 | Erro | Download de exportação não concluída | `DEVTIME-3002` |
| AC-012-18 | Erro | Download de exportação expirada | `DEVTIME-3003` |
| AC-012-19 | Erro | Cancelamento em processamento | `DEVTIME-3004` |
| AC-012-20 | Erro | Agrupamento incompatível com o tipo | §6.3 |
| AC-012-21 | Erro | `MEMBER` solicita produtividade | RN-711 |
| AC-012-22 | Erro | `MEMBER` exporta com filtro por cliente | CE-P-10 |
| AC-012-23 | Extremo | Cliente renomeado após o fechamento | CE-R-02 |
| AC-012-24 | Extremo | Período reaberto e refechado | CE-R-04 |
| AC-012-25 | Extremo | Contrato sem valor hora | CE-R-05 |
| AC-012-26 | Extremo | Relatório sem registros | CE-R-06 |
| AC-012-27 | Extremo | Exportação de exatamente 5.000 linhas | CX-10 |
| AC-012-28 | Extremo | Exportação de 5.001 linhas | CX-11 |
| AC-012-29 | Extremo | Exportação de 50.000 linhas | CE-R-07 |
| AC-012-30 | Extremo | Descrição com emoji nos três formatos | CE-R-08 |
| AC-012-31 | Extremo | Registro excluído não aparece | RN-704 |
| AC-012-32 | Extremo | Intervalo de exatamente 366 dias | CX-14 |
| AC-012-33 | Extremo | Folha de horas cruzando períodos aberto e fechado | CX-23 |
| AC-012-34 | Extremo | Falha na geração duas vezes | CX-20 |
| AC-012-35 | Extremo | Usuário removido com registros no relatório | CX-16 |
| AC-012-36 | Extremo | Duas exportações idênticas simultâneas | CX-19 |
| AC-012-37 | Segurança | Relatório de outro tenant retorna 404 | RN-002 |
| AC-012-38 | Segurança | Injeção de fórmula em CSV e XLSX | SG-05 |
| AC-012-39 | Segurança | XSS por descrição no PDF | SG-06 |
| AC-012-40 | Segurança | Valores monetários ausentes do arquivo | SG-07 |
| AC-012-41 | Segurança | Exportação de terceiro não é listada | SG-04 |
| AC-012-42 | Segurança | Binário removido na expiração | SG-09 |
| AC-012-43 | Segurança | Download é auditado | §18 |
| AC-012-44 | Concorrência | Exportação e fechamento simultâneos | RN-701 |
| AC-012-45 | Concorrência | Duas tentativas de download da mesma URL | RN-712 |
| AC-012-46 | Concorrência | Reprocessamento por duas instâncias | §4.10 |

---

## 3. Cenários felizes

### AC-012-01 — Período fechado servido do snapshot
```gherkin
Dado um período com status CLOSED e snapshot gerado
E que o nome de uma categoria foi alterado no banco após o fechamento
Quando eu envio GET /api/v1/reports/contract-period/{periodId}
Então recebo 200 OK
E o relatório exibe o nome da categoria vigente no momento do fechamento
E não o nome alterado depois
E o relatório é marcado como definitivo, não parcial
```

### AC-012-02 — PDF determinístico
```gherkin
Dado um período com status CLOSED
Quando eu exporto o relatório em PDF duas vezes, com intervalo entre as gerações
Então os dois arquivos possuem conteúdo idêntico
E a única diferença entre eles é o carimbo de data e hora de emissão
E a ordem das linhas é idêntica nos dois arquivos
```

### AC-012-03 — Período aberto marcado como parcial
```gherkin
Dado um período com status OPEN
Quando eu gero o relatório em tela, em PDF e em Excel
Então as três saídas indicam explicitamente que o relatório é parcial
E no PDF a indicação é visualmente proeminente
E no Excel a indicação aparece no cabeçalho da planilha
```

### AC-012-04 — Cabeçalho com identificador de emissão
```gherkin
Quando eu gero qualquer relatório
Então o cabeçalho traz o nome, o documento e o logo do tenant
E os dados do cliente, do contrato e do período quando aplicáveis
E a data e a hora de emissão
E um identificador único de emissão
Quando eu gero o mesmo relatório novamente
Então o identificador de emissão é diferente do anterior
```

### AC-012-05 — Resumo consolidado por cliente
```gherkin
Dado um cliente com três contratos ativos
E que estou autenticado com a permissão REPORT_VIEW_ANY
Quando eu envio GET /api/v1/reports/client-summary/{clientId}
Então recebo 200 OK
E o relatório agrega os três contratos
E traz os totais consolidados do intervalo solicitado
```

### AC-012-06 — Folha de horas por intervalo
```gherkin
Quando eu envio GET /api/v1/reports/timesheet com um intervalo de 60 dias
Então recebo 200 OK
E os registros do intervalo são retornados agrupados conforme o parâmetro escolhido
E os totais correspondem à soma dos registros exibidos
```

### AC-012-07 — Detalhamento por ticket
```gherkin
Dado um ticket com 12 registros de horas
Quando eu envio GET /api/v1/reports/ticket-detail/{ticketId}
Então recebo 200 OK com os 12 registros
E o relatório traz os totais de minutos gastos e faturáveis
E a chave legível do ticket aparece no cabeçalho
```

### AC-012-08 — Relatório de produtividade
```gherkin
Dado um tenant com quatro membros e registros ao longo de oito semanas
E que estou autenticado com a permissão REPORT_VIEW_ANY
Quando eu envio GET /api/v1/reports/productivity
Então recebo 200 OK
E o relatório traz agregação por usuário e por semana
```

### AC-012-09 — Ordenação normativa
```gherkin
Dado um relatório com registros de datas, tickets e horários variados
Quando eu gero o relatório em qualquer formato
Então as linhas são ordenadas por data crescente
E em seguida pela chave do ticket
E em seguida pelo horário de início
E nenhum parâmetro permite alterar essa ordenação
```

### AC-012-10 — Exportação síncrona
```gherkin
Dado um relatório com 300 linhas
Quando eu envio POST /api/v1/reports/exports com formato PDF
Então recebo 201 Created
E a resposta traz a URL de download assinada
E um ReportExecution com status COMPLETED foi registrado
```

### AC-012-11 — Exportação assíncrona
```gherkin
Dado um relatório com 20.000 linhas
Quando eu envio POST /api/v1/reports/exports com formato XLSX
Então recebo 202 Accepted
E a resposta traz uma pollUrl para acompanhamento
E um ReportExecution com status QUEUED foi registrado
Quando o processamento conclui
Então o status passa a COMPLETED
E eu recebo uma notificação informando que o arquivo está pronto
```

### AC-012-12 — Duas colunas de duração no Excel
```gherkin
Dado um relatório com registros de 149 horas e 30 minutos no total
Quando eu exporto em XLSX e abro o arquivo
Então cada linha possui uma coluna de duração no formato HH:MM como texto
E uma coluna de horas decimais como valor numérico com duas casas
E a soma da coluna decimal por fórmula produz o total correto
```

### AC-012-13 — Formatação monetária
```gherkin
Dado um contrato com valor hora definido e moeda BRL
Quando eu gero o relatório
Então os valores monetários são exibidos com duas casas decimais
E o arredondamento aplicado é HALF_UP
E o símbolo da moeda do contrato acompanha o valor no PDF
```

### AC-012-14 — Download por URL assinada
```gherkin
Dado uma exportação com status COMPLETED
Quando eu envio GET /api/v1/reports/exports/{id}/download
Então eu sou direcionado para uma URL assinada
E a URL expira em 15 minutos
E o download funciona dentro desse prazo
```

### AC-012-15 — Registro de execução com filtros
```gherkin
Quando eu solicito uma exportação com filtros de data, cliente e categoria
Então um ReportExecution é registrado
E ele armazena o tipo de relatório, o formato, os filtros aplicados,
      a contagem de linhas e o usuário solicitante
E um AuditLog com action "REPORT_EXPORT_REQUESTED" registra os filtros
```

---

## 4. Cenários de erro

### AC-012-16 — Intervalo acima de 366 dias
```gherkin
Quando eu envio GET /api/v1/reports/timesheet com um intervalo de 367 dias
Então recebo 400 Bad Request com o código DEVTIME-3001
E nenhuma consulta de agregação é executada
```

### AC-012-17 — Download de exportação não concluída
```gherkin
Dado uma exportação com status QUEUED ou PROCESSING
Quando eu envio GET /api/v1/reports/exports/{id}/download
Então recebo 409 Conflict com o código DEVTIME-3002
E a mensagem indica que o arquivo ainda está sendo gerado
```

### AC-012-18 — Download de exportação expirada
```gherkin
Dado uma exportação concluída há 8 dias, com status EXPIRED
Quando eu envio GET /api/v1/reports/exports/{id}/download
Então recebo 410 Gone com o código DEVTIME-3003
E a mensagem orienta a gerar o relatório novamente
```

### AC-012-19 — Cancelamento em processamento
```gherkin
Dado uma exportação com status PROCESSING
Quando eu envio DELETE /api/v1/reports/exports/{id}
Então recebo 409 Conflict com o código DEVTIME-3004
E o processamento continua
Mas uma exportação em QUEUED é cancelada com 204 No Content
```

### AC-012-20 — Agrupamento incompatível com o tipo
```gherkin
Quando eu solicito o relatório de detalhamento por ticket com agrupamento por semana
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
E a mensagem indica que o agrupamento é inválido para este relatório
```

### AC-012-21 — `MEMBER` solicita produtividade
```gherkin
Dado que estou autenticado com o papel MEMBER
Quando eu envio GET /api/v1/reports/productivity
Então recebo 403 Forbidden com o código DEVTIME-1101
E o campo requiredPermission indica REPORT_VIEW_ANY
```

### AC-012-22 — `MEMBER` exporta com filtro por cliente
```gherkin
Dado que estou autenticado com o papel MEMBER
E que possuo vínculo com um contrato desse cliente
Quando eu solicito uma exportação filtrando por cliente
Então recebo 403 Forbidden com o código DEVTIME-1101
E nenhum arquivo é gerado
Mas ao solicitar com escopo myWorkLogs eu recebo 201 Created
```

---

## 5. Cenários extremos

### AC-012-23 — Cliente renomeado após o fechamento
```gherkin
Dado um período CLOSED cujo relatório foi entregue ao cliente
Quando o nome do cliente é alterado no cadastro
E eu gero o relatório desse período novamente
Então o relatório exibe o nome vigente no momento do fechamento
E não o nome atual
```

### AC-012-24 — Período reaberto e refechado
```gherkin
Dado um período CLOSED que foi reaberto e fechado novamente
Quando eu gero o relatório desse período
Então o relatório reflete o novo snapshot
E o snapshot anterior permanece armazenado, mas não é servido
E o relatório é marcado como definitivo
```

### AC-012-25 — Contrato sem valor hora
```gherkin
Dado um contrato cujo hourlyRate é nulo
Quando eu gero o relatório em qualquer formato
Então nenhuma coluna monetária aparece
E nenhum erro é retornado
E as colunas de duração e descrição aparecem normalmente
```

### AC-012-26 — Relatório sem registros
```gherkin
Dado um filtro que não retorna nenhum registro
Quando eu gero o relatório
Então recebo 200 OK
E os totais são zero
E uma mensagem explícita indica a ausência de registros
E a exportação em PDF gera um documento válido com essa mensagem
```

### AC-012-27 — Exportação de exatamente 5.000 linhas
```gherkin
Dado um relatório com exatamente 5.000 linhas
Quando eu solicito a exportação
Então recebo 201 Created de forma síncrona
E o arquivo já está disponível na resposta
```

### AC-012-28 — Exportação de 5.001 linhas
```gherkin
Dado um relatório com 5.001 linhas
Quando eu solicito a exportação
Então recebo 202 Accepted
E o processamento ocorre de forma assíncrona
```

### AC-012-29 — Exportação de 50.000 linhas
```gherkin
Dado um relatório com 50.000 linhas
Quando eu solicito a exportação em XLSX
Então recebo 202 Accepted
E o processamento conclui em menos de 5 minutos
E o consumo de memória permanece estável durante a geração
E eu recebo uma notificação ao concluir
```

### AC-012-30 — Descrição com emoji nos três formatos
```gherkin
Dado registros com descrições contendo emoji e caracteres acentuados
Quando eu exporto em PDF, XLSX e CSV
Então os caracteres são preservados corretamente nos três arquivos
E nenhum caractere aparece substituído ou corrompido
```

### AC-012-31 — Registro excluído não aparece
```gherkin
Dado um período OPEN com um registro excluído logicamente
Quando eu gero o relatório desse período
Então o registro excluído não aparece em nenhuma linha
E os totais não o consideram
```

### AC-012-32 — Intervalo de exatamente 366 dias
```gherkin
Quando eu solicito a folha de horas com intervalo de exatamente 366 dias
Então recebo 200 OK
Quando eu solicito com 367 dias
Então recebo 400 com o código DEVTIME-3001
```

### AC-012-33 — Folha de horas cruzando períodos aberto e fechado
```gherkin
Dado um intervalo que abrange um período CLOSED e um período OPEN
Quando eu gero a folha de horas desse intervalo
Então o relatório é marcado como PARCIAL
E a presença de qualquer período aberto torna o conjunto parcial
```

### AC-012-34 — Falha na geração duas vezes
```gherkin
Dado uma exportação cuja geração falha
Quando o job tenta reprocessá-la
E a segunda tentativa também falha
Então o status permanece FAILED com attemptCount igual a 2
E nenhuma terceira tentativa automática ocorre
E o motivo da falha é exibido ao usuário
E ele pode solicitar uma nova exportação
```

### AC-012-35 — Usuário removido com registros no relatório
```gherkin
Dado registros de um membro que foi removido do tenant
Quando eu gero o relatório de um período CLOSED que os contém
Então o nome exibido é o preservado no snapshot
Quando eu gero o relatório de um período OPEN que os contém
Então o nome exibido é substituído por "Usuário Removido"
```

### AC-012-36 — Duas exportações idênticas simultâneas
```gherkin
Quando eu solicito duas exportações com exatamente os mesmos filtros e formato, em paralelo
Então duas ReportExecution distintas são criadas
E dois arquivos são gerados
E nenhuma deduplicação ocorre
E ambos os downloads funcionam independentemente
```

---

## 6. Cenários de segurança

### AC-012-37 — Relatório de outro tenant retorna 404
```gherkin
Dado um período, cliente ou ticket pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu solicito qualquer relatório referenciando esse recurso
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
```

### AC-012-38 — Injeção de fórmula em CSV e XLSX
```gherkin
Dado um registro cuja descrição começa com "=SUM(A1:A99)"
E outro que começa com "+1+1", outro com "-1" e outro com "@import"
Quando eu exporto em CSV e em XLSX
E eu abro os arquivos em um editor de planilhas
Então nenhuma fórmula é executada
E o conteúdo é exibido como texto literal em todos os quatro casos
```

### AC-012-39 — XSS por descrição no PDF
```gherkin
Dado um registro cuja descrição contém "<script>alert(1)</script>"
Quando eu exporto em PDF
Então o conteúdo é renderizado como texto literal
E nenhuma marcação é interpretada pelo renderizador
```

### AC-012-40 — Valores monetários ausentes do arquivo
```gherkin
Dado que estou autenticado com o papel MEMBER
Quando eu exporto um relatório com escopo myWorkLogs
E eu inspeciono o conteúdo do arquivo gerado
Então nenhuma coluna monetária está presente no arquivo
E nenhum valor de taxa ou total financeiro aparece
```

### AC-012-41 — Exportação de terceiro não é listada
```gherkin
Dado uma exportação solicitada por outro usuário do mesmo tenant
Quando eu envio GET /api/v1/reports/exports
Então essa exportação não aparece na minha listagem
Quando eu tento acessá-la diretamente por id
Então recebo 404 Not Found
```

### AC-012-42 — Binário removido na expiração
```gherkin
Dado uma exportação concluída há mais de 7 dias
Quando o job de expiração é executado
Então o status passa a EXPIRED
E o arquivo binário é removido do object storage
E uma tentativa de acesso direto à chave de armazenamento falha
```

### AC-012-43 — Download é auditado
```gherkin
Quando eu baixo um arquivo exportado
Então um AuditLog com action "REPORT_DOWNLOADED" é gravado
E ele registra quem baixou, quando e o endereço de origem
```

---

## 7. Cenários de concorrência

### AC-012-44 — Exportação e fechamento simultâneos
```gherkin
Dado um período OPEN sendo exportado
Quando o período é fechado durante a geração do arquivo
Então o arquivo gerado reflete uma das duas fontes de forma consistente
E nunca mistura dados calculados ao vivo com dados do snapshot
E, se refletir o estado aberto, permanece marcado como parcial
```

### AC-012-45 — Duas tentativas de download da mesma URL
```gherkin
Dado uma URL de download assinada e válida
Quando dois downloads simultâneos usam a mesma URL
Então ambos são atendidos com sucesso
E a URL continua válida até expirar
Quando a URL expira e eu solicito o download novamente
Então uma nova URL é gerada
E o arquivo não é regerado
```

### AC-012-46 — Reprocessamento por duas instâncias
```gherkin
Dado uma exportação com status QUEUED
Quando duas instâncias do job de processamento executam simultaneamente
Então apenas uma assume a exportação
E exatamente um arquivo é gerado
E attemptCount é incrementado uma única vez
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-701 | AC-012-01, AC-012-23, AC-012-24, AC-012-35, AC-012-44 | ✅ |
| RN-702 | AC-012-03, AC-012-33 | ✅ |
| RN-703 | AC-012-04 | ✅ |
| RN-704 | AC-012-31 | ✅ |
| RN-705 | AC-012-16, AC-012-32 | ✅ |
| RN-706 | AC-012-10, AC-012-11, AC-012-27, AC-012-28, AC-012-29 | ✅ |
| RN-707 | AC-012-15 | ✅ |
| RN-708 | AC-012-02, AC-012-09 | ✅ |
| RN-709 | AC-012-13, AC-012-25 | ✅ |
| RN-710 | AC-012-12 | ✅ |
| RN-711 | AC-012-21, AC-012-22, AC-012-40 | ✅ |
| RN-712 | AC-012-14, AC-012-18, AC-012-45 | ✅ |
| RN-002 | AC-012-37 | ✅ |
| RN-006 | AC-012-15, AC-012-43 | ✅ |
| INV-RPT-01 | AC-012-02, AC-012-23 | ✅ |
| INV-RPT-02 | AC-012-31 | ✅ |
| INV-RPT-03 | AC-012-03, AC-012-33 | ✅ |
| INV-RPT-04 | AC-012-22, AC-012-40 | ✅ |
| INV-RPT-05 | AC-012-15 | ✅ |
| INV-RPT-06 | AC-012-14, AC-012-45 | ✅ |
| §6.3 ordenação e agrupamento | AC-012-09, AC-012-20 | ✅ |
| §4.10 SM | AC-012-17, AC-012-18, AC-012-19, AC-012-34, AC-012-46 | ✅ |
| CE-P-10 | AC-012-22 | ✅ |
| CE-R-02 / R-04 / R-05 / R-06 / R-07 / R-08 | AC-012-23 a AC-012-26, AC-012-29, AC-012-30 | ✅ |
| SG-04 / SG-05 / SG-06 / SG-07 / SG-09 | AC-012-38 a AC-012-42 | ✅ |
| §7.1 a §7.4 reports.md | AC-012-05 a AC-012-08 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. `AC-012-01` e `AC-012-02` são os cenários centrais: juntos eles verificam que o relatório de período fechado é imutável e reproduzível, que é a razão de RN-701 e RN-708 existirem.
