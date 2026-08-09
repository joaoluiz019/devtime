import { Role } from '../../core/auth/auth.model';

/**
 * Nomes e descrições dos papéis (`permissions.md` §5).
 *
 * Vivem em `shared` porque duas features os consomem: a tela de equipe, que atribui papéis, e o
 * aceite de convite, que exibe o papel oferecido antes de a pessoa entrar. Uma cópia em cada uma
 * divergiria — e o texto que descreve o alcance de alguém sobre dados de clientes é exatamente o
 * que não pode dizer duas coisas diferentes em duas telas.
 */

/**
 * Papéis oferecidos na interface, em ordem decrescente de alcance.
 *
 * `CLIENT_PORTAL` fica de fora: `permissions.md` §5 o reserva para a v2.x, e atribuí-lo produziria
 * um vínculo que nenhuma tela do produto atende.
 */
export const ASSIGNABLE_ROLES: readonly Role[] = ['OWNER', 'ADMIN', 'MANAGER', 'MEMBER', 'VIEWER'];

export const ROLE_LABELS: Readonly<Record<Role, string>> = {
  OWNER: $localize`:@@role.owner:Proprietário`,
  ADMIN: $localize`:@@role.admin:Administrador`,
  MANAGER: $localize`:@@role.manager:Gestor`,
  MEMBER: $localize`:@@role.member:Membro`,
  VIEWER: $localize`:@@role.viewer:Observador`,
  CLIENT_PORTAL: $localize`:@@role.clientPortal:Portal do cliente`,
};

/**
 * O que cada papel alcança, em uma frase.
 *
 * A descrição acompanha a escolha porque "Gestor" e "Administrador" não se distinguem pelo nome, e
 * quem escolhe está decidindo o alcance de outra pessoa sobre dados de clientes e valores.
 */
export const ROLE_DESCRIPTIONS: Readonly<Record<Role, string>> = {
  OWNER: $localize`:@@role.owner.description:Controle total, incluindo faturamento e cancelamento da organização.`,
  ADMIN: $localize`:@@role.admin.description:Administra a organização e a equipe, exceto proprietários.`,
  MANAGER: $localize`:@@role.manager.description:Gerencia clientes, contratos e as horas de toda a equipe.`,
  MEMBER: $localize`:@@role.member.description:Registra e consulta as próprias horas.`,
  VIEWER: $localize`:@@role.viewer.description:Apenas leitura, com acesso a relatórios e exportação.`,
  CLIENT_PORTAL: $localize`:@@role.clientPortal.description:Acesso externo do cliente, em leitura. Reservado para versões futuras.`,
};
