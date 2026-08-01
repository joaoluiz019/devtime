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
  'DEVTIME-1101': $localize`:@@error.DEVTIME-1101:Você não tem permissão para esta ação.`,
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
