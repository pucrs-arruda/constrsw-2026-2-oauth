/**
 * O enunciado do T1 pede validacao de e-mail "RFC 5322 official standard
 * regular expression"; o regex colado no enunciado veio corrompido
 * (provavelmente por copia de PDF/Google Docs). Usamos aqui uma variante
 * amplamente adotada e equivalente na pratica (mesma intencao: recusar
 * e-mails malformados), documentada em README.md.
 */
export const EMAIL_REGEX =
  /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
