<?php

declare(strict_types=1);

namespace App\Tests\Integration\Infrastructure\Http\Controller;

use App\Tests\Support\KeycloakTestTrait;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

final class AuthorizationControllerTest extends WebTestCase
{
    use KeycloakTestTrait;

    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    /**
     * POST /authorize: Rejeita quando header Authorization está ausente (401 INVALID_TOKEN).
     */
    public function testAuthorizeMissingAuthorizationHeaderReturns401(): void
    {
        $this->client->request(
            method: 'POST',
            uri: '/authorize',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode(['resource' => 'rooms'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('INVALID_TOKEN', $data['error']['code']);
    }

    /**
     * POST /authorize: Rejeita quando o Bearer token é inválido ou expirado (401 INVALID_TOKEN).
     */
    public function testAuthorizeInvalidTokenReturns401(): void
    {
        $this->requireKeycloak();

        $this->client->request(
            method: 'POST',
            uri: '/authorize',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => 'Bearer invalid-token-xyz',
            ],
            content: json_encode(['resource' => 'rooms'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_UNAUTHORIZED, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('INVALID_TOKEN', $data['error']['code']);
    }

    /**
     * POST /authorize: Rejeita quando o campo resource é omitido ou vazio (400 VALIDATION_ERROR).
     */
    public function testAuthorizeMissingResourceReturns400(): void
    {
        $this->client->request(
            method: 'POST',
            uri: '/authorize',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => 'Bearer some-token',
            ],
            content: json_encode([], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
        $this->assertArrayHasKey('resource', $data['error']['details']);
    }

    /**
     * POST /authorize: Professor acessando 'lessons' (recurso permitido) -> 200 OK.
     */
    public function testProfessorAuthorizedOnLessons(): void
    {
        $token = $this->login('professor@pucrs.br', 'a12345678');

        $this->client->request(
            method: 'POST',
            uri: '/authorize',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$token}",
            ],
            content: json_encode(['resource' => 'lessons'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertTrue($data['authorized']);
        $this->assertSame('lessons', $data['resource']);
        $this->assertContains('professor', $data['matching_roles']);
    }

    /**
     * POST /authorize: Professor acessando 'classes' (recurso de coordenador) -> 403 Forbidden.
     */
    public function testProfessorForbiddenOnClasses(): void
    {
        $token = $this->login('professor@pucrs.br', 'a12345678');

        $this->client->request(
            method: 'POST',
            uri: '/authorize',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$token}",
            ],
            content: json_encode(['resource' => 'classes'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    /**
     * POST /authorize: Aluno acessando 'rooms' (recurso restrito a admin) -> 403 Forbidden.
     */
    public function testStudentForbiddenOnRooms(): void
    {
        $token = $this->login('student@pucrs.br', 'a12345678');

        $this->client->request(
            method: 'POST',
            uri: '/authorize',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$token}",
            ],
            content: json_encode(['resource' => 'rooms'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    /**
     * POST /authorize: Coordenador acessando 'courses' (recurso permitido) -> 200 OK.
     */
    public function testCoordinatorAuthorizedOnCourses(): void
    {
        $token = $this->login('coordinator@pucrs.br', 'a12345678');

        $this->client->request(
            method: 'POST',
            uri: '/authorize',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$token}",
            ],
            content: json_encode(['resource' => 'courses'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertTrue($data['authorized']);
        $this->assertSame('courses', $data['resource']);
        $this->assertContains('coordinator', $data['matching_roles']);
    }

    private function login(string $username, string $password): string
    {
        $this->requireKeycloak();

        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => $username,
                'password' => $password,
            ], JSON_THROW_ON_ERROR)
        );

        $data = json_decode((string) $this->client->getResponse()->getContent(), true);
        return $data['access_token'] ?? '';
    }
}
