package br.pucrs.constrsw.oauth.domain.util;

import java.util.regex.Pattern;

/**
 * Valida e-mail com a regex "RFC 5322 official standard" do enunciado:
 *
 * <pre>
 * ([-!#-'*+/-9=?A-Z^-~]+(\.[-!#-'*+/-9=?A-Z^-~]+)*|"([]!#-[^-~ \t]|(\\[\t -~]))+")
 *   @([-!#-'*+/-9=?A-Z^-~]+(\.[-!#-'*+/-9=?A-Z^-~]+)*|\[[\t -Z^-~]*])
 * </pre>
 *
 * O texto do enunciado tem dois erros de copia em relacao a regex original,
 * corrigidos aqui: {@code (\\(\t -~])} -> {@code (\\[\t -~])} (caractere
 * escapado na parte entre aspas) e {@code \([\t -Z^-~]*]} -> {@code \[[\t -Z^-~]*]}
 * (dominio literal, ex.: {@code [192.168.0.1]}).
 *
 * Adaptacoes de sintaxe para java.util.regex: dentro de classes de caracteres,
 * {@code [} e {@code ]} literais precisam de escape ({@code []!#-[...]} vira
 * {@code [\]!#-\[...]}).
 */
public final class EmailValidator {

    /** Atom: caracteres permitidos em cada parte separada por ponto. */
    private static final String ATOM = "[-!#-'*+/-9=?A-Z^-~]+";

    /** dot-atom: atoms separados por um unico ponto (sem ponto no inicio, no fim ou repetido). */
    private static final String DOT_ATOM = ATOM + "(\\." + ATOM + ")*";

    /** quoted-string: "..." com caracteres imprimiveis ou pares escapados com barra invertida. */
    private static final String QUOTED_STRING = "\"([\\]!#-\\[^-~ \\t]|(\\\\[\\t -~]))+\"";

    /** domain-literal: [ ... ], ex.: [192.168.0.1]. */
    private static final String DOMAIN_LITERAL = "\\[[\\t -Z^-~]*\\]";

    private static final Pattern RFC_5322 = Pattern.compile(
            "(" + DOT_ATOM + "|" + QUOTED_STRING + ")@(" + DOT_ATOM + "|" + DOMAIN_LITERAL + ")");

    private EmailValidator() {}

    public static boolean isValid(String email) {
        return email != null && RFC_5322.matcher(email).matches();
    }
}
