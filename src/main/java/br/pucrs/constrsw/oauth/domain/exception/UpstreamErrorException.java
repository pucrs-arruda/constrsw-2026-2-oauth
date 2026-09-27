package br.pucrs.constrsw.oauth.domain.exception;

/**
 * Erro devolvido por um sistema externo (ex.: o provedor de identidade). Nao e
 * lancado sozinho: os adapters o anexam como causa da excecao de dominio que
 * traduz o erro, para que o error_stack da resposta mostre a pilha completa
 * (erro de origem -> erro final da OAuthAPI).
 */
public class UpstreamErrorException extends DomainException {

    private final String source;
    private final int status;

    public UpstreamErrorException(String source, int status, String message) {
        super(message);
        this.source = source;
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public int getStatus() {
        return status;
    }
}
