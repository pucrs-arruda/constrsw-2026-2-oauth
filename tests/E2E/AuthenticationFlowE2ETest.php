<?php

declare(strict_types=1);

namespace App\Tests\E2E;

use App\Tests\Support\KeycloakTestTrait;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

/**
 * Teste End-to-End do fluxo completo de Autenticação e Gestão de Sessão.
 *
 * Cenário E2E:
 * 1. Login com credenciais válidas via password grant (POST /login) -> Recebe tokens Bearer.
 * 2. Consulta do perfil de identidade com o access token (GET /me) -> Valida claims do usuário.
 * 3. Renovação de sessão via refresh token grant (POST /refresh) -> Recebe novo par de tokens.
 * 4. Validação da sessão contínua usando o novo access token (GET /me) -> 200 OK.
 * 5. Rejeição de credenciais incorretas (POST /login) -> 401 INVALID_CREDENTIALS.
 * 6. Rejeição de token adulterado ou inválido (GET /me) -> 401 INVALID_TOKEN.
 */
final class AuthenticationFlowE2ETest extends WebTestCase
{
    use KeycloakTestTrait;

    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    public function testCompleteAuthenticationAndSessionLifecycleE2E(): void
    {
        $this->requireKeycloak();

        // 1. Login com credenciais válidas
        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => 'professor@pucrs.br',
                'password' => 'a12345678',
            ], JSON_THROW_ON_ERROR)
        );

        $loginResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $loginResponse->getStatusCode(), 'Login deve retornar 200 OK');

        $tokens = json_decode((string) $loginResponse->getContent(), true);
        $this->assertSame('Bearer', $tokens['token_type']);
        $this->assertNotEmpty($tokens['access_token']);
        $this->assertNotEmpty($tokens['refresh_token']);
        $this->assertGreaterThan(0, $tokens['expires_in']);

        $initialAccessToken = $tokens['access_token'];
        $refreshToken = $tokens['refresh_token'];

        // 2. Consulta de identidade (/me)
        $this->client->request(
            method: 'GET',
            uri: '/me',
            server: ['HTTP_AUTHORIZATION' => "Bearer {$initialAccessToken}"]
        );

        $meResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $meResponse->getStatusCode(), 'GET /me deve retornar 200 OK');

        $userProfile = json_decode((string) $meResponse->getContent(), true);
        $this->assertSame('professor@pucrs.br', $userProfile['email']);
        $this->assertSame('Professor PUCRS', $userProfile['name']);
        $this->assertNotEmpty($userProfile['sub']);

        // 3. Renovação de sessão via refresh_token
        $this->client->request(
            method: 'POST',
            uri: '/refresh',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'refresh_token' => $refreshToken,
            ], JSON_THROW_ON_ERROR)
        );

        $refreshResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $refreshResponse->getStatusCode(), 'POST /refresh deve retornar 200 OK');

        $refreshedTokens = json_decode((string) $refreshResponse->getContent(), true);
        $this->assertNotEmpty($refreshedTokens['access_token']);
        $newAccessToken = $refreshedTokens['access_token'];

        // 4. Acesso ao perfil com o novo token
        $this->client->request(
            method: 'GET',
            uri: '/me',
            server: ['HTTP_AUTHORIZATION' => "Bearer {$newAccessToken}"]
        );

        $newMeResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $newMeResponse->getStatusCode(), 'GET /me com novo token deve retornar 200 OK');

        // 5. Tentativa com credenciais inválidas (negativo)
        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => 'professor@pucrs.br',
                'password' => 'wrong-password-999',
            ], JSON_THROW_ON_ERROR)
        );

        $failedLoginResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $failedLoginResponse->getStatusCode());

        // 6. Tentativa com token adulterado (negativo)
        $this->client->request(
            method: 'GET',
            uri: '/me',
            server: ['HTTP_AUTHORIZATION' => 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.tampered.token']
        );

        $failedMeResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $failedMeResponse->getStatusCode());
    }
}
