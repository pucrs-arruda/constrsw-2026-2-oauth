package br.pucrs.constrsw.oauth.domain.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailValidatorTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "aluno@pucrs.br",
      "Aluno.Teste@edu.pucrs.br",
      "nome+tag@example.com",
      "a_b-c@sub-dominio.example.org",
      "!#$%&'*+/=?^_`{|}~-@example.com",
      "user@localhost",
      "\"joao silva\"@example.com",
      "\"aspas\\\"escapadas\"@example.com",
      "user@[192.168.0.1]",
      "e2e-1234abcd@e2e.constrsw.test"
  })
  void acceptsRfc5322Addresses(String email) {
    assertTrue(EmailValidator.isValid(email), email);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      "nao-eh-email",
      "@pucrs.br",
      "aluno@",
      "aluno@@pucrs.br",
      "aluno@pucrs@br",
      ".aluno@pucrs.br",
      "aluno.@pucrs.br",
      "alu..no@pucrs.br",
      "aluno@pucrs..br",
      "aluno@.pucrs.br",
      "aluno teste@pucrs.br",
      "aluno(comentario)@pucrs.br",
      "joão@pucrs.br",
      "\"sem-fechar@pucrs.br",
      "aluno@[192.168.0.1"
  })
  void rejectsInvalidAddresses(String email) {
    assertFalse(EmailValidator.isValid(email), email);
  }

  @Test
  void rejectsNull() {
    assertFalse(EmailValidator.isValid(null));
  }
}
