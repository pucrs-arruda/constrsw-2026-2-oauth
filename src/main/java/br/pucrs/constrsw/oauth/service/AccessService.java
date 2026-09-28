package br.pucrs.constrsw.oauth.service;

import br.pucrs.constrsw.oauth.port.AuthorizationGateway;

public class AccessService {

    private final AuthorizationGateway authorizationGateway;

    public AccessService(AuthorizationGateway authorizationGateway) {
        this.authorizationGateway = authorizationGateway;
    }

    public boolean hasAccess(String authorization, String resourceUri) {
        return authorizationGateway.hasAccess(authorization, resourceUri);
    }
}
