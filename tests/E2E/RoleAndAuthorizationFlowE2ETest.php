<?php

declare(strict_types=1);

namespace App\Tests\E2E;

use App\Tests\Support\KeycloakTestTrait;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

/**
 * Teste End-to-End do fluxo completo de Gerenciamento de Papéis e Matriz de Autorização (RBAC).
 *
 * Cenário E2E:
 * 1. Criação de role customizada via POST /roles -> 201 Created.
 * 2. Consulta da role criada por ID via GET /roles/{id} -> 200 OK.
 * 3. Atualização da role via PUT /roles/{id} -> 200 OK.
 * 4. Validação da matriz RBAC com usuários pré-carregados (seed users):
 *    - Professor tentando acessar 'lessons' -> 200 OK (autorizado).
 *    - Professor tentando acessar 'classes' -> 403 Forbidden (negado).
 *    - Aluno tentando acessar 'rooms' -> 403 Forbidden (negado).
 *    - Coordenador tentando acessar 'courses' -> 200 OK (autorizado).
 *    - Administrador tentando acessar 'rooms' -> 200 OK (autorizado).
 * 5. Exclusão lógica da role via DELETE /roles/{id} -> 204 No Content.
 */
final class RoleAndAuthorizationFlowE2ETest extends WebTestCase
{
    use KeycloakTestTrait;

    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    public function testRoleManagementAndRbacAuthorizationE2E(): void
    {
        $this->requireKeycloak();

        $suffix = uniqid();
        $roleName = "e2e-role-{$suffix}";
        $roleDescription = "Papel E2E de auditoria {$suffix}";

        // 1. Criação de role
        $this->client->request(
            method: 'POST',
            uri: '/roles',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'name' => $roleName,
                'description' => $roleDescription,
            ], JSON_THROW_ON_ERROR)
        );

        $createResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_CREATED, $createResponse->getStatusCode());

        $roleData = json_decode((string) $createResponse->getContent(), true);
        $this->assertNotEmpty($roleData['id']);
        $this->assertSame($roleName, $roleData['name']);
        $roleId = $roleData['id'];

        // 2. Consulta por ID
        $this->client->request('GET', "/roles/{$roleId}");
        $getResponse = $this->client->getResponse();
        $this->assertSame(Response::HTTP_OK, $getResponse->getStatusCode());

        // 3. Atualização da role (PUT)
        $this->client->request(
            method: 'PUT',
            uri: "/roles/{$roleId}",
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'name' => $roleName,
                'description' => 'Descricao atualizada no E2E',
            ], JSON_THROW_ON_ERROR)
        );
        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());

        // 4. Testes de Autorização RBAC ponta a ponta
        // 4.1 Professor em 'lessons' (Permitido)
        $profToken = $this->login('professor@pucrs.br', 'a12345678');
        $this->assertAuthorizeAccess($profToken, 'lessons', Response::HTTP_OK, true);

        // 4.2 Professor em 'classes' (Negado)
        $this->assertAuthorizeAccess($profToken, 'classes', Response::HTTP_FORBIDDEN, false);

        // 4.3 Aluno em 'rooms' (Negado)
        $studentToken = $this->login('student@pucrs.br', 'a12345678');
        $this->assertAuthorizeAccess($studentToken, 'rooms', Response::HTTP_FORBIDDEN, false);

        // 4.4 Coordenador em 'courses' (Permitido)
        $coordToken = $this->login('coordinator@pucrs.br', 'a12345678');
        $this->assertAuthorizeAccess($coordToken, 'courses', Response::HTTP_OK, true);

        // 4.5 Administrador em 'rooms' (Permitido)
        $adminToken = $this->login('admin@pucrs.br', 'a12345678');
        $this->assertAuthorizeAccess($adminToken, 'rooms', Response::HTTP_OK, true);

        // 5. Exclusão lógica da role temporária
        $this->client->request('DELETE', "/roles/{$roleId}");
        $this->assertSame(Response::HTTP_NO_CONTENT, $this->client->getResponse()->getStatusCode());
    }

    private function login(string $username, string $password): string
    {
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

    private function assertAuthorizeAccess(string $token, string $resource, int $expectedStatus, bool $expectedAuthorized): void
    {
        $this->client->request(
            method: 'POST',
            uri: '/authorize',
            server: [
                'CONTENT_TYPE' => 'application/json',
                'HTTP_AUTHORIZATION' => "Bearer {$token}",
            ],
            content: json_encode(['resource' => $resource], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame($expectedStatus, $response->getStatusCode(), "Acesso ao recurso '{$resource}' deve retornar HTTP {$expectedStatus}");

        $body = json_decode((string) $response->getContent(), true);
        $this->assertSame($expectedAuthorized, $body['authorized'] ?? false);
    }
}
