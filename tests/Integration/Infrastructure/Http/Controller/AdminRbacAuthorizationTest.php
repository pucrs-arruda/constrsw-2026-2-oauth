<?php

declare(strict_types=1);

namespace App\Tests\Integration\Infrastructure\Http\Controller;

use App\Tests\Support\KeycloakTestTrait;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

final class AdminRbacAuthorizationTest extends WebTestCase
{
    use KeycloakTestTrait;

    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    private function createMockJwt(array $payload): string
    {
        $header = rtrim(strtr(base64_encode(json_encode(['alg' => 'RS256', 'typ' => 'JWT'])), '+/', '-_'), '=');
        $body = rtrim(strtr(base64_encode(json_encode($payload)), '+/', '-_'), '=');
        $signature = 'mockSignature';

        return "{$header}.{$body}.{$signature}";
    }

    public function testNonAdminCannotAssignRoles(): void
    {
        $studentToken = $this->createMockJwt([
            'sub' => 'student-uuid-001',
            'preferred_username' => 'student@pucrs.br',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student', 'USER']],
        ]);

        $this->client->request(
            method: 'POST',
            uri: '/users/target-user-123/roles',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$studentToken}",
            ],
            content: json_encode(['roleName' => 'administrator'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    public function testNonAdminCannotAssignRolesByPath(): void
    {
        $studentToken = $this->createMockJwt([
            'sub' => 'student-uuid-001',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $this->client->request(
            method: 'POST',
            uri: '/users/target-user-123/roles/some-role-id',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$studentToken}",
            ]
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    public function testNonAdminCannotUnassignRoles(): void
    {
        $studentToken = $this->createMockJwt([
            'sub' => 'student-uuid-001',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $this->client->request(
            method: 'DELETE',
            uri: '/users/target-user-123/roles/some-role-id',
            server: [
                'HTTP_AUTHORIZATION' => "Bearer {$studentToken}",
            ]
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    public function testNonAdminCannotCreateRoles(): void
    {
        $studentToken = $this->createMockJwt([
            'sub' => 'student-uuid-001',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $this->client->request(
            method: 'POST',
            uri: '/roles',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$studentToken}",
            ],
            content: json_encode(['name' => 'new-role'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    public function testNonAdminCannotCreateUsers(): void
    {
        $studentToken = $this->createMockJwt([
            'sub' => 'student-uuid-001',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $this->client->request(
            method: 'POST',
            uri: '/users',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$studentToken}",
            ],
            content: json_encode([
                'email' => 'newuser@pucrs.br',
                'firstName' => 'Novo',
                'lastName' => 'User',
                'password' => 'secret123',
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    public function testNonAdminCannotDeleteUsers(): void
    {
        $studentToken = $this->createMockJwt([
            'sub' => 'student-uuid-001',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $this->client->request(
            method: 'DELETE',
            uri: '/users/target-user-123',
            server: [
                'HTTP_AUTHORIZATION' => "Bearer {$studentToken}",
            ]
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    public function testNonAdminCannotUpdateOtherUser(): void
    {
        $studentToken = $this->createMockJwt([
            'sub' => 'student-uuid-001',
            'preferred_username' => 'student@pucrs.br',
            'email' => 'student@pucrs.br',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $this->client->request(
            method: 'PUT',
            uri: '/users/other-user-999',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$studentToken}",
            ],
            content: json_encode(['firstName' => 'Hacked'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    public function testNonAdminCannotChangeOtherUserPassword(): void
    {
        $studentToken = $this->createMockJwt([
            'sub' => 'student-uuid-001',
            'preferred_username' => 'student@pucrs.br',
            'email' => 'student@pucrs.br',
            'exp' => time() + 3600,
            'realm_access' => ['roles' => ['student']],
        ]);

        $this->client->request(
            method: 'PATCH',
            uri: '/users/other-user-999',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$studentToken}",
            ],
            content: json_encode(['password' => 'newSecret999!'], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_FORBIDDEN, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $data['error']['code']);
    }

    public function testRealStudentCannotAssignRoleLiveKeycloak(): void
    {
        $this->requireKeycloak();

        // 1. Autentica com o usuário student do seed
        $this->client->request(
            method: 'POST',
            uri: '/login',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'username' => 'student@pucrs.br',
                'password' => 'a12345678',
            ], JSON_THROW_ON_ERROR)
        );

        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());
        $loginData = json_decode((string) $this->client->getResponse()->getContent(), true);
        $studentToken = $loginData['access_token'];

        // 2. Tenta atribuir role a outro usuário
        $this->client->request(
            method: 'POST',
            uri: '/users/target-user-uuid/roles',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$studentToken}",
            ],
            content: json_encode(['roleName' => 'administrator'], JSON_THROW_ON_ERROR)
        );

        $this->assertSame(Response::HTTP_FORBIDDEN, $this->client->getResponse()->getStatusCode());
        $body = json_decode((string) $this->client->getResponse()->getContent(), true);
        $this->assertSame('ACCESS_DENIED', $body['error']['code']);
    }
}
