<?php

declare(strict_types=1);

namespace App\Tests\Integration\Infrastructure\Http\Controller;

use App\Tests\Support\KeycloakTestTrait;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;
use Symfony\Component\HttpFoundation\Response;

final class RoleControllerTest extends WebTestCase
{
    use KeycloakTestTrait;

    private KernelBrowser $client;

    protected function setUp(): void
    {
        $this->client = static::createClient();
    }

    /**
     * POST /roles: Valida erro de validação quando o campo obrigatório name é omitido (400 VALIDATION_ERROR).
     */
    public function testCreateRoleWithMissingNameReturns400(): void
    {
        $this->client->request(
            method: 'POST',
            uri: '/roles',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertArrayHasKey('error', $data);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
        $this->assertArrayHasKey('name', $data['error']['details']);
    }

    /**
     * POST /roles: Valida erro de validação quando o nome está em branco (400 VALIDATION_ERROR).
     */
    public function testCreateRoleWithBlankNameReturns400(): void
    {
        $this->client->request(
            method: 'POST',
            uri: '/roles',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode(['name' => '   '], JSON_THROW_ON_ERROR)
        );

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_BAD_REQUEST, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('VALIDATION_ERROR', $data['error']['code']);
        $this->assertArrayHasKey('name', $data['error']['details']);
    }

    /**
     * GET /roles/{id}: Valida retorno 404 quando a role não existe.
     */
    public function testGetRoleByIdNotFoundReturns404(): void
    {
        $this->requireKeycloak();

        $this->client->request('GET', '/roles/00000000-0000-0000-0000-000000000000');

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_NOT_FOUND, $response->getStatusCode());

        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('ROLE_NOT_FOUND', $data['error']['code']);
    }

    /**
     * Ciclo de criação e consulta de role:
     * 1. Criação de role (POST /roles -> 201)
     * 2. Rejeição de role duplicada (POST /roles -> 409)
     * 3. Listagem de roles ativas (GET /roles -> 200)
     * 4. Consulta por ID da role criada (GET /roles/{id} -> 200)
     */
    public function testRoleCreationAndRetrievalLifecycle(): void
    {
        $this->requireKeycloak();

        $uniqueSuffix = uniqid();
        $roleName = "role-test-{$uniqueSuffix}";
        $roleDescription = "Papel de teste automatizado {$uniqueSuffix}";

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

        $response = $this->client->getResponse();
        $this->assertSame(Response::HTTP_CREATED, $response->getStatusCode());
        $createdRole = json_decode((string) $response->getContent(), true);
        $this->assertNotEmpty($createdRole['id']);
        $this->assertSame($roleName, $createdRole['name']);
        $this->assertSame($roleDescription, $createdRole['description']);
        $this->assertTrue($createdRole['enabled']);
        $roleId = $createdRole['id'];

        // 2. Conflito ao tentar criar role com nome duplicado (409)
        $this->client->request(
            method: 'POST',
            uri: '/roles',
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode([
                'name' => $roleName,
                'description' => 'Tentativa duplicada',
            ], JSON_THROW_ON_ERROR)
        );
        $this->assertSame(Response::HTTP_CONFLICT, $this->client->getResponse()->getStatusCode());
        $conflictData = json_decode((string) $this->client->getResponse()->getContent(), true);
        $this->assertSame('ROLE_ALREADY_EXISTS', $conflictData['error']['code']);

        // 3. Listagem de roles ativas
        $this->client->request('GET', '/roles');
        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());
        $rolesList = json_decode((string) $this->client->getResponse()->getContent(), true);
        $this->assertIsArray($rolesList);

        $found = false;
        foreach ($rolesList as $role) {
            if ($role['id'] === $roleId) {
                $found = true;
                $this->assertSame($roleName, $role['name']);
                $this->assertTrue($role['enabled']);
                break;
            }
        }
        $this->assertTrue($found, 'A role recém-criada deve constar na listagem de GET /roles.');

        // 4. Busca por ID
        $this->client->request('GET', "/roles/{$roleId}");
        $this->assertSame(Response::HTTP_OK, $this->client->getResponse()->getStatusCode());
        $fetchedRole = json_decode((string) $this->client->getResponse()->getContent(), true);
        $this->assertSame($roleId, $fetchedRole['id']);
        $this->assertSame($roleName, $fetchedRole['name']);
        $this->assertSame($roleDescription, $fetchedRole['description']);
    }
}
