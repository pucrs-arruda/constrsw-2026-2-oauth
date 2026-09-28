<?php

declare(strict_types=1);

namespace App\Tests\Integration\Infrastructure\Http\Controller;

use App\Tests\Support\KeycloakTestTrait;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

final class AuthControllerTest extends WebTestCase
{
    use KeycloakTestTrait;

    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    /**
     * POST /login: Valida autenticação com credenciais válidas via JSON (200 OK).
     */
    public function testLoginSuccessWithJson(): void
    {
        $this->requireKeycloak();

        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => 'professor@pucrs.br',
                'password' => 'a12345678',
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('Bearer', $data['token_type']);
        $this->assertNotEmpty($data['access_token']);
        $this->assertGreaterThan(0, $data['expires_in']);
        $this->assertNotEmpty($data['refresh_token']);
        $this->assertGreaterThan(0, $data['refresh_expires_in']);
    }

    /**
     * POST /login: Valida autenticação com credenciais válidas via form-data (200 OK).
     */
    public function testLoginSuccessWithFormData(): void
    {
        $this->requireKeycloak();

        $this->client->request(
            method: 'POST',
            uri: '/login',
            parameters: [
                'username' => 'professor@pucrs.br',
                'password' => 'a12345678',
            ]
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('Bearer', $data['token_type']);
        $this->assertNotEmpty($data['access_token']);
    }

    /**
     * POST /login: Valida rejeição com senha incorreta (401 INVALID_CREDENTIALS).
     */
    public function testLoginWithInvalidCredentialsReturns401(): void
    {
        $this->requireKeycloak();

        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => 'professor@pucrs.br',
                'password' => 'wrong-password',
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertArrayHasKey('error', $data);
        $this->assertSame('INVALID_CREDENTIALS', $data['error']['code']);
    }

    /**
     * POST /login: Valida erro quando campos obrigatórios são omitidos (400 VALIDATION_ERROR).
     */
    public function testLoginWithMissingParametersReturns400(): void
    {
        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
        $this->assertArrayHasKey('username', $data['error']['details']);
        $this->assertArrayHasKey('password', $data['error']['details']);
    }

    /**
     * POST /refresh: Valida renovação de sessão com refresh token ativo (200 OK).
     */
    public function testRefreshTokenSuccess(): void
    {
        $authData = $this->authenticateSeedUser();
        $refreshToken = $authData['refresh_token'];

        $this->client->request(
            method: 'POST',
            uri: '/refresh',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'refresh_token' => $refreshToken,
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('Bearer', $data['token_type']);
        $this->assertNotEmpty($data['access_token']);
        $this->assertNotEmpty($data['refresh_token']);
    }

    /**
     * POST /refresh: Valida rejeição de refresh token inválido ou expirado (401 INVALID_TOKEN).
     */
    public function testRefreshTokenInvalidReturns401(): void
    {
        $this->requireKeycloak();

        $this->client->request(
            method: 'POST',
            uri: '/refresh',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'refresh_token' => 'invalid-or-expired-token',
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('INVALID_TOKEN', $data['error']['code']);
    }

    /**
     * POST /refresh: Valida erro quando o campo refresh_token é omitido (400 VALIDATION_ERROR).
     */
    public function testRefreshTokenMissingReturns400(): void
    {
        $this->client->request(
            method: 'POST',
            uri: '/refresh',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
        $this->assertArrayHasKey('refresh_token', $data['error']['details']);
    }

    /**
     * GET /me: Valida retorno do perfil com Bearer token válido (200 OK).
     */
    public function testMeSuccessReturnsUserProfile(): void
    {
        $authData = $this->authenticateSeedUser();
        $accessToken = $authData['access_token'];

        $this->client->request(
            method: 'GET',
            uri: '/me',
            server: ['HTTP_AUTHORIZATION' => 'Bearer ' . $accessToken]
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('b2d1c002-0000-4000-b000-000000000003', $data['sub']);
        $this->assertSame('Professor PUCRS', $data['name']);
        $this->assertSame('professor@pucrs.br', $data['email']);
        $this->assertSame('professor@pucrs.br', $data['preferred_username']);
        $this->assertTrue($data['email_verified']);
    }

    /**
     * GET /me: Valida rejeição quando header Authorization está ausente (401 INVALID_TOKEN).
     */
    public function testMeMissingAuthorizationHeaderReturns401(): void
    {
        $this->client->request(
            method: 'GET',
            uri: '/me'
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('INVALID_TOKEN', $data['error']['code']);
    }

    /**
     * GET /me: Valida rejeição de Bearer token com assinatura ou formato inválido (401 INVALID_TOKEN).
     */
    public function testMeInvalidTokenReturns401(): void
    {
        $this->requireKeycloak();

        $this->client->request(
            method: 'GET',
            uri: '/me',
            server: ['HTTP_AUTHORIZATION' => 'Bearer invalid-access-token-123']
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('INVALID_TOKEN', $data['error']['code']);
    }

    private function authenticateSeedUser(): array
    {
        $this->requireKeycloak();

        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => 'professor@pucrs.br',
                'password' => 'a12345678',
            ], JSON_THROW_ON_ERROR)
        );

        return json_decode((string) $this->client->getResponse()->getContent(), true);
    }
}
