package br.pucrs.constrsw.oauth.port;

public interface AuthorizationGateway {
    boolean hasAccess(String authorization, String resourceUri);
}
