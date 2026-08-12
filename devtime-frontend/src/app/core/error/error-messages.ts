/**
 * Mapa de códigos `DEVTIME-XXXX` para mensagens localizadas (FR-071, I18-05, DS-07).
 *
 * CA-07 do design system: todo código de erro possui mensagem em linguagem natural. O `detail` do
 * backend é apenas fallback — a mensagem canônica ao usuário vive aqui, no idioma da interface, e o
 * código é exibido em texto discreto para o suporte.
 *
 * Contém apenas os códigos que a fundação técnica pode produzir. Cada feature acrescenta os seus no
 * mesmo PR que os introduz (EX-13 de ADR-017).
 */
export const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  'DEVTIME-1001': $localize`:@@error.DEVTIME-1001:Sua sessão expirou. Entre novamente para continuar.`,
  'DEVTIME-1002': $localize`:@@error.DEVTIME-1002:Selecione uma organização para continuar.`,
  // 001-authentication (§12 de `specs/001-authentication/spec.md`).
  'DEVTIME-1003': $localize`:@@error.DEVTIME-1003:Você não possui acesso ativo a nenhuma organização.`,
  'DEVTIME-1004': $localize`:@@error.DEVTIME-1004:Sua sessão expirou. Entre novamente para continuar.`,
  'DEVTIME-1005': $localize`:@@error.DEVTIME-1005:Sua sessão foi encerrada por segurança. Entre novamente.`,
  'DEVTIME-1006': $localize`:@@error.DEVTIME-1006:Conta bloqueada temporariamente após várias tentativas. Tente novamente mais tarde.`,
  'DEVTIME-1007': $localize`:@@error.DEVTIME-1007:Link expirado ou já utilizado. Solicite um novo.`,
  'DEVTIME-1008': $localize`:@@error.DEVTIME-1008:Verifique seu e-mail para continuar.`,
  'DEVTIME-1009': $localize`:@@error.DEVTIME-1009:Link expirado. Solicite um novo e-mail de verificação.`,
  'DEVTIME-1010': $localize`:@@error.DEVTIME-1010:Link de verificação inválido.`,
  'DEVTIME-1011': $localize`:@@error.DEVTIME-1011:A senha atual informada está incorreta.`,
  'DEVTIME-1012': $localize`:@@error.DEVTIME-1012:A nova senha deve ser diferente da atual.`,
  'DEVTIME-2451': $localize`:@@error.DEVTIME-2451:A senha não atende aos requisitos.`,
  'DEVTIME-2452': $localize`:@@error.DEVTIME-2452:Este e-mail já está em uso.`,
  'DEVTIME-2457': $localize`:@@error.DEVTIME-2457:Convite expirado. Solicite um novo.`,
  'DEVTIME-2458': $localize`:@@error.DEVTIME-2458:Convite inválido ou já revogado.`,
  'DEVTIME-2459': $localize`:@@error.DEVTIME-2459:Este e-mail já participa da organização ou tem convite pendente.`,
  'DEVTIME-2460': $localize`:@@error.DEVTIME-2460:O limite de membros do plano foi atingido.`,
  // 002-users — equipe (§7 de `docs/04-api/users.md`).
  'DEVTIME-2455': $localize`:@@error.DEVTIME-2455:A organização precisa de ao menos um proprietário ativo.`,
  'DEVTIME-2456': $localize`:@@error.DEVTIME-2456:Você não pode alterar o próprio papel. Peça a outro proprietário.`,
  'DEVTIME-1101': $localize`:@@error.DEVTIME-1101:Você não tem permissão para esta ação.`,
  // Separado de 1101 de propósito: falha de token CSRF não se resolve pedindo permissão a ninguém.
  'DEVTIME-1105': $localize`:@@error.DEVTIME-1105:Sua sessão expirou ou a página está aberta há muito tempo. Recarregue e tente novamente.`,
  'DEVTIME-1102': $localize`:@@error.DEVTIME-1102:Seu acesso a esta organização foi revogado.`,
  'DEVTIME-1103': $localize`:@@error.DEVTIME-1103:Você só pode alterar seus próprios registros.`,
  'DEVTIME-1104': $localize`:@@error.DEVTIME-1104:Ação não permitida sobre um proprietário.`,
  'DEVTIME-1200': $localize`:@@error.DEVTIME-1200:Esta operação não pertence à sua organização.`,
  'DEVTIME-1201': $localize`:@@error.DEVTIME-1201:Organização suspensa: apenas leitura disponível.`,
  'DEVTIME-1202': $localize`:@@error.DEVTIME-1202:Esta organização foi cancelada.`,
  'DEVTIME-2000': $localize`:@@error.DEVTIME-2000:Verifique os campos destacados e tente novamente.`,
  'DEVTIME-2001': $localize`:@@error.DEVTIME-2001:Já existe um registro com estes dados.`,
  'DEVTIME-2002': $localize`:@@error.DEVTIME-2002:Não encontramos o que você procura.`,
  'DEVTIME-2003': $localize`:@@error.DEVTIME-2003:Este campo não pode ser alterado após a criação.`,
  'DEVTIME-2004': $localize`:@@error.DEVTIME-2004:Este registro foi alterado por outra pessoa. Recarregue e tente novamente.`,
  'DEVTIME-2005': $localize`:@@error.DEVTIME-2005:Existem registros vinculados que impedem a exclusão.`,
  'DEVTIME-2006': $localize`:@@error.DEVTIME-2006:A consulta pede itens demais de uma vez.`,
  'DEVTIME-2007': $localize`:@@error.DEVTIME-2007:Esta requisição já foi processada com dados diferentes.`,
  'DEVTIME-2010': $localize`:@@error.DEVTIME-2010:Esta operação não é possível no estado atual do registro.`,
  'DEVTIME-2011': $localize`:@@error.DEVTIME-2011:Este registro está finalizado e não aceita alterações.`,
  // 003-clients (§12 de `specs/003-clients/spec.md`).
  'DEVTIME-2401': $localize`:@@error.DEVTIME-2401:Cliente com contrato ativo não pode ser excluído.`,
  'DEVTIME-2402': $localize`:@@error.DEVTIME-2402:CPF ou CNPJ inválido.`,
  'DEVTIME-2403': $localize`:@@error.DEVTIME-2403:Já existe um cliente com este documento.`,
  'DEVTIME-2404': $localize`:@@error.DEVTIME-2404:Já existe um cliente com este nome.`,
  'DEVTIME-2405': $localize`:@@error.DEVTIME-2405:Cliente inativo não aceita novos contratos.`,
  'DEVTIME-2406': $localize`:@@error.DEVTIME-2406:Apenas um contato pode ser o principal.`,
  'DEVTIME-2407': $localize`:@@error.DEVTIME-2407:Confirme o impacto sobre os contratos ativos para inativar o cliente.`,
  'DEVTIME-2408': $localize`:@@error.DEVTIME-2408:Este cliente atingiu o limite de 20 contatos.`,
  // 004-contracts (§12 de `specs/004-contracts/spec.md`).
  'DEVTIME-2201': $localize`:@@error.DEVTIME-2201:Cliente inválido ou inativo.`,
  'DEVTIME-2202': $localize`:@@error.DEVTIME-2202:Quantidade de horas mensais inválida.`,
  'DEVTIME-2203': $localize`:@@error.DEVTIME-2203:O dia de faturamento deve estar entre 1 e 28.`,
  'DEVTIME-2204': $localize`:@@error.DEVTIME-2204:A data final deve ser igual ou posterior à inicial.`,
  'DEVTIME-2205': $localize`:@@error.DEVTIME-2205:Contrato com registros de horas não pode ser excluído. Encerre ou cancele.`,
  'DEVTIME-2206': $localize`:@@error.DEVTIME-2206:Já existe um contrato com este código.`,
  'DEVTIME-2207': $localize`:@@error.DEVTIME-2207:A alteração afetaria um período já fechado.`,
  'DEVTIME-2208': $localize`:@@error.DEVTIME-2208:Não é possível alterar o ciclo com horas já lançadas.`,
  'DEVTIME-2211': $localize`:@@error.DEVTIME-2211:Não foi possível ativar o contrato com esta configuração.`,
  'DEVTIME-2213': $localize`:@@error.DEVTIME-2213:Data de encerramento inválida para este contrato.`,
  // 007-tickets (§12 de `specs/007-tickets/spec.md`).
  'DEVTIME-2301': $localize`:@@error.DEVTIME-2301:O ticket precisa pertencer a um contrato do tenant.`,
  'DEVTIME-2303': $localize`:@@error.DEVTIME-2303:O título precisa ter entre 3 e 200 caracteres.`,
  'DEVTIME-2304': $localize`:@@error.DEVTIME-2304:O responsável precisa ser um membro ativo da organização.`,
  'DEVTIME-2305': $localize`:@@error.DEVTIME-2305:Só é possível mover para outro contrato do mesmo cliente e sem horas registradas.`,
  'DEVTIME-2306': $localize`:@@error.DEVTIME-2306:Este contrato não aceita registros de horas.`,
  'DEVTIME-2307': $localize`:@@error.DEVTIME-2307:Ticket com horas registradas não pode ser excluído. Cancele-o.`,
  'DEVTIME-2308': $localize`:@@error.DEVTIME-2308:Descreva o motivo do bloqueio.`,
  'DEVTIME-2311': $localize`:@@error.DEVTIME-2311:Existe um cronômetro ativo neste ticket.`,
  'DEVTIME-2313': $localize`:@@error.DEVTIME-2313:No máximo 10 etiquetas por ticket.`,
  // 008-worklogs (§12 de `specs/008-worklogs/spec.md`).
  'DEVTIME-2100': $localize`:@@error.DEVTIME-2100:Todo registro de horas pertence a um ticket.`,
  'DEVTIME-2102': $localize`:@@error.DEVTIME-2102:Este intervalo se sobrepõe a outro registro seu.`,
  'DEVTIME-2103': $localize`:@@error.DEVTIME-2103:Um registro não pode passar de 24 horas.`,
  'DEVTIME-2104': $localize`:@@error.DEVTIME-2104:Categoria inválida ou inativa.`,
  'DEVTIME-2105': $localize`:@@error.DEVTIME-2105:A descrição precisa ter de 3 a 2.000 caracteres.`,
  'DEVTIME-2107': $localize`:@@error.DEVTIME-2107:Não há período de contrato que contenha esta data.`,
  'DEVTIME-2114': $localize`:@@error.DEVTIME-2114:O fim precisa ser depois do início.`,
  'DEVTIME-2115': $localize`:@@error.DEVTIME-2115:A duração líquida precisa ser maior que zero.`,
  'DEVTIME-2116': $localize`:@@error.DEVTIME-2116:A pausa precisa ser menor que a duração bruta.`,
  'DEVTIME-2117': $localize`:@@error.DEVTIME-2117:A data está fora da vigência do contrato.`,
  'DEVTIME-2118': $localize`:@@error.DEVTIME-2118:O fim não pode estar no futuro.`,
  'DEVTIME-2119': $localize`:@@error.DEVTIME-2119:Esta organização não permite lançar horas em data futura.`,
  'DEVTIME-2120': $localize`:@@error.DEVTIME-2120:Esta data é retroativa demais. Peça a um administrador.`,
  'DEVTIME-2121': $localize`:@@error.DEVTIME-2121:Registro de período fechado não pode ser alterado.`,
  'DEVTIME-2124': $localize`:@@error.DEVTIME-2124:Mover o registro exige que os dois períodos estejam abertos.`,
  'DEVTIME-2220': $localize`:@@error.DEVTIME-2220:Este registro ultrapassaria o saldo do contrato, que bloqueia excedente.`,
  // 009-timer (§12 de `specs/009-timer/spec.md`).
  'DEVTIME-2150': $localize`:@@error.DEVTIME-2150:Você já tem um cronômetro em andamento.`,
  'DEVTIME-2153': $localize`:@@error.DEVTIME-2153:O cronômetro já está pausado.`,
  'DEVTIME-2155': $localize`:@@error.DEVTIME-2155:O cronômetro não está pausado.`,
  'DEVTIME-2165': $localize`:@@error.DEVTIME-2165:O prazo de 7 dias para recuperar este cronômetro terminou.`,
  // 014-comments e 015-attachments (§12 dos respectivos specs).
  'DEVTIME-2701': $localize`:@@error.DEVTIME-2701:Arquivo grande demais, ou o armazenamento da organização está cheio.`,
  'DEVTIME-2702': $localize`:@@error.DEVTIME-2702:Este tipo de arquivo não é aceito.`,
  'DEVTIME-2703': $localize`:@@error.DEVTIME-2703:O arquivo ainda não passou pela verificação de segurança.`,
  'DEVTIME-2704': $localize`:@@error.DEVTIME-2704:Limite de anexos atingido para este item.`,
  'DEVTIME-2705': $localize`:@@error.DEVTIME-2705:O comentário precisa ter de 1 a 10.000 caracteres.`,
  'DEVTIME-2706': $localize`:@@error.DEVTIME-2706:A janela de 24 horas para alterar este comentário terminou.`,
  'DEVTIME-2707': $localize`:@@error.DEVTIME-2707:Registros automáticos do ticket não podem ser alterados.`,
  // 005-categories e 006-tags (§12 dos respectivos specs).
  'DEVTIME-2601': $localize`:@@error.DEVTIME-2601:Já existe uma categoria com este nome.`,
  'DEVTIME-2602': $localize`:@@error.DEVTIME-2602:Categoria padrão não pode ser excluída. Inative-a se não desejar utilizá-la.`,
  'DEVTIME-2603': $localize`:@@error.DEVTIME-2603:Existem registros nesta categoria. Escolha uma categoria substituta.`,
  'DEVTIME-2604': $localize`:@@error.DEVTIME-2604:Já existe uma etiqueta com este nome.`,
  'DEVTIME-2605': $localize`:@@error.DEVTIME-2605:Categoria substituta inválida.`,
  'DEVTIME-3001': $localize`:@@error.DEVTIME-3001:O intervalo consultado é grande demais. Reduza o período.`,
  // 012-reports (§12 de `specs/012-reports/spec.md`).
  //
  // Os códigos seguem `ReportController` e `ReportExportController`, e não a tabela de §17.2 do
  // spec: ela atribui `DEVTIME-3002` ao download de exportação não concluída, enquanto o controller
  // implementado usa `3002` para período não iniciado e `3004` para exportação em andamento. A
  // divergência foi resolvida em favor do comportamento implementado, que é o que o usuário recebe.
  'DEVTIME-3002': $localize`:@@error.DEVTIME-3002:Este período ainda não começou: não há o que relatar.`,
  'DEVTIME-3003': $localize`:@@error.DEVTIME-3003:Os parâmetros não correspondem a este tipo de relatório.`,
  'DEVTIME-3004': $localize`:@@error.DEVTIME-3004:A exportação ainda está sendo gerada. Aguarde a conclusão.`,
  'DEVTIME-3005': $localize`:@@error.DEVTIME-3005:O arquivo expirou e foi removido. Gere o relatório novamente.`,
  'DEVTIME-3006': $localize`:@@error.DEVTIME-3006:A geração do arquivo falhou. Tente gerar novamente.`,
  'DEVTIME-3007': $localize`:@@error.DEVTIME-3007:Este agrupamento não se aplica a este tipo de relatório.`,
  // 011-bank-hours (§12 de `specs/011-bank-hours/spec.md`).
  'DEVTIME-2215': $localize`:@@error.DEVTIME-2215:A justificativa precisa ter ao menos 10 caracteres.`,
  'DEVTIME-2235': $localize`:@@error.DEVTIME-2235:Só é possível ajustar o saldo de um período aberto.`,
  'DEVTIME-2236': $localize`:@@error.DEVTIME-2236:Ajustes não podem ser alterados. Registre um estorno.`,
  'DEVTIME-2237': $localize`:@@error.DEVTIME-2237:Este ajuste deixaria o saldo disponível negativo.`,
  'DEVTIME-2239': $localize`:@@error.DEVTIME-2239:O período ainda não terminou. Confirme o fechamento antecipado para continuar.`,
  'DEVTIME-2240': $localize`:@@error.DEVTIME-2240:Existe um cronômetro ativo neste período.`,
  'DEVTIME-2244': $localize`:@@error.DEVTIME-2244:Existe um período posterior já fechado. Reabra-o primeiro.`,
  'DEVTIME-9001': $localize`:@@error.DEVTIME-9001:Não foi possível concluir a operação. Informe o código ao suporte.`,
  'DEVTIME-9002': $localize`:@@error.DEVTIME-9002:Muitas requisições. Aguarde um instante e tente novamente.`,
};

/**
 * Mensagem localizada do código, com o `detail` do servidor como fallback.
 *
 * O fallback existe porque uma feature pode introduzir um código antes de sua tradução; exibir o
 * texto do servidor é preferível a exibir o código bruto ao usuário (DS-07).
 */
export function messageForCode(code: string, fallback: string): string {
  return ERROR_MESSAGES[code] ?? fallback;
}
