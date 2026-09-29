<?php

declare(strict_types=1);

namespace App\Tests\Unit\Infrastructure\Keycloak\Adapter;

use App\Application\DTO\Role\CreateRoleDTO;
use App\Domain\Exception\InvalidTokenException;
use App\Domain\Exception\RoleAlreadyExistsException;
use App\Infrastructure\Keycloak\Adapter\KeycloakRoleAdapter;
use App\Infrastructure\Keycloak\Client\KeycloakHttpClient;
use PHPUnit\Framework\TestCase;
use RuntimeException;

final class KeycloakRoleAdapterTest extends TestCase
{
    private KeycloakHttpClient $httpClient;
    private KeycloakRoleAdapter $adapter;

    protected function setUp(): void
    {
        $this->httpClient = $this->createMock(KeycloakHttpClient::class);
        $this->httpClient->method('getRealm')->willReturn('constrsw');
        $this->adapter = new KeycloakRoleAdapter($this->httpClient);
    }

    public function testCreateRoleSuccess(): void
    {
        $dto = new CreateRoleDTO(
            name: 'editor',
            description: 'Content Editor'
        );

        $this->httpClient
            ->expects($this->exactly(2))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path, array $headers, ?string $body, bool $mapUserExceptions) {
                if ($method === 'POST' && $path === 'admin/realms/constrsw/roles') {
                    $decoded = json_decode((string) $body, true);
                    $this->assertSame('editor', $decoded['name']);
                    $this->assertSame('Content Editor', $decoded['description']);

                    return [
                        'status' => 201,
                        'headers' => ['location' => ['http://keycloak:8080/auth/admin/realms/constrsw/roles/editor']],
                        'data' => [],
                        'raw' => '',
                    ];
                }

                if ($method === 'GET' && $path === 'admin/realms/constrsw/roles/editor') {
                    return [
                        'status' => 200,
                        'headers' => [],
                        'data' => [
                            'id' => 'generated-uuid-role-1',
                            'name' => 'editor',
                            'description' => 'Content Editor',
                            'attributes' => ['enabled' => ['true']],
                        ],
                        'raw' => '',
                    ];
                }

                $this->fail("Requisição inesperada: {$method} {$path}");
            });

        $role = $this->adapter->createRole($dto);

        $this->assertSame('generated-uuid-role-1', $role->id);
        $this->assertSame('editor', $role->name);
        $this->assertSame('Content Editor', $role->description);
        $this->assertTrue($role->enabled);
    }

    public function testCreateRoleThrowsRoleAlreadyExistsExceptionOn409(): void
    {
        $dto = new CreateRoleDTO(name: 'editor');

        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->with(
                'POST',
                'admin/realms/constrsw/roles',
                [],
                $this->anything(),
                false
            )
            ->willReturn([
                'status' => 409,
                'headers' => [],
                'data' => ['errorMessage' => 'Role with name editor already exists'],
                'raw' => 'Role with name editor already exists',
            ]);

        $this->expectException(RoleAlreadyExistsException::class);
        $this->expectExceptionMessage("Já existe um role com o nome 'editor'.");

        $this->adapter->createRole($dto);
    }

    public function testCreateRoleThrowsRuntimeExceptionOnServerError(): void
    {
        $dto = new CreateRoleDTO(name: 'editor');

        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->willReturn([
                'status' => 500,
                'headers' => [],
                'data' => [],
                'raw' => 'Server error',
            ]);

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('Falha ao criar role no Keycloak. Status: 500.');

        $this->adapter->createRole($dto);
    }

    public function testListActiveRolesFiltersOutDisabledRoles(): void
    {
        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->with('GET', 'admin/realms/constrsw/roles?briefRepresentation=false', [], null, false)
            ->willReturn([
                'status' => 200,
                'headers' => [],
                'data' => [
                    [
                        'id' => 'uuid-1',
                        'name' => 'administrator',
                        'description' => 'System Admin',
                        'attributes' => ['enabled' => ['true']],
                    ],
                    [
                        'id' => 'uuid-2',
                        'name' => 'archived-role',
                        'description' => 'Disabled Role',
                        'attributes' => ['enabled' => ['false']],
                    ],
                    [
                        'id' => 'uuid-3',
                        'name' => 'professor',
                        'description' => 'University Professor',
                    ],
                ],
                'raw' => '',
            ]);

        $roles = $this->adapter->listActiveRoles();

        $this->assertCount(2, $roles);
        $this->assertSame('administrator', $roles[0]->name);
        $this->assertSame('professor', $roles[1]->name);
    }

    public function testGetRoleByIdReturnsRoleWhenActive(): void
    {
        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->with('GET', 'admin/realms/constrsw/roles-by-id/uuid-123', [], null, false)
            ->willReturn([
                'status' => 200,
                'headers' => [],
                'data' => [
                    'id' => 'uuid-123',
                    'name' => 'coordinator',
                    'description' => 'Coordinator Role',
                    'attributes' => ['enabled' => ['true']],
                ],
                'raw' => '',
            ]);

        $role = $this->adapter->getRoleById('uuid-123');

        $this->assertNotNull($role);
        $this->assertSame('uuid-123', $role->id);
        $this->assertSame('coordinator', $role->name);
        $this->assertSame('Coordinator Role', $role->description);
        $this->assertTrue($role->enabled);
    }

    public function testGetRoleByIdReturnsNullWhenStatusIs404(): void
    {
        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->with('GET', 'admin/realms/constrsw/roles-by-id/non-existent', [], null, false)
            ->willReturn([
                'status' => 404,
                'headers' => [],
                'data' => [],
                'raw' => 'Not Found',
            ]);

        $role = $this->adapter->getRoleById('non-existent');

        $this->assertNull($role);
    }

    public function testGetRoleByIdReturnsNullWhenRoleIsLogicallyDisabled(): void
    {
        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->with('GET', 'admin/realms/constrsw/roles-by-id/disabled-uuid', [], null, false)
            ->willReturn([
                'status' => 200,
                'headers' => [],
                'data' => [
                    'id' => 'disabled-uuid',
                    'name' => 'old-role',
                    'attributes' => ['enabled' => ['false']],
                ],
                'raw' => '',
            ]);

        $role = $this->adapter->getRoleById('disabled-uuid');

        $this->assertNull($role);
    }

    public function testRoleOperationsThrowInvalidTokenExceptionWhenUnauthorized(): void
    {
        $dto = new CreateRoleDTO(name: 'editor');

        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->willThrowException(new InvalidTokenException('Token de serviço expirado ou sem permissão na Admin API.'));

        $this->expectException(InvalidTokenException::class);
        $this->expectExceptionMessage('Token de serviço expirado ou sem permissão na Admin API.');

        $this->adapter->createRole($dto);
    }

    public function testUpdateRoleSuccess(): void
    {
        $dto = new \App\Application\DTO\Role\UpdateRoleDTO(
            name: 'editor-chefe',
            description: 'Editor Geral'
        );

        $this->httpClient
            ->expects($this->exactly(2))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path, array $headers, ?string $body, bool $mapUserExceptions) {
                if ($method === 'GET' && $path === 'admin/realms/constrsw/roles-by-id/uuid-editor') {
                    return [
                        'status' => 200,
                        'headers' => [],
                        'data' => [
                            'id' => 'uuid-editor',
                            'name' => 'editor',
                            'description' => 'Editor',
                            'attributes' => ['enabled' => ['true']],
                        ],
                        'raw' => '',
                    ];
                }

                if ($method === 'PUT' && $path === 'admin/realms/constrsw/roles-by-id/uuid-editor') {
                    $decoded = json_decode((string) $body, true);
                    $this->assertSame('editor-chefe', $decoded['name']);
                    $this->assertSame('Editor Geral', $decoded['description']);
                    $this->assertSame(['true'], $decoded['attributes']['enabled']);

                    return [
                        'status' => 204,
                        'headers' => [],
                        'data' => [],
                        'raw' => '',
                    ];
                }

                $this->fail("Chamada inesperada: {$method} {$path}");
            });

        $role = $this->adapter->updateRole('uuid-editor', $dto);

        $this->assertSame('uuid-editor', $role->id);
        $this->assertSame('editor-chefe', $role->name);
        $this->assertSame('Editor Geral', $role->description);
        $this->assertTrue($role->enabled);
    }

    public function testUpdateRoleThrowsRoleNotFoundExceptionWhenNotFound(): void
    {
        $dto = new \App\Application\DTO\Role\UpdateRoleDTO(name: 'novo');

        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->willReturn([
                'status' => 404,
                'headers' => [],
                'data' => [],
                'raw' => '',
            ]);

        $this->expectException(\App\Domain\Exception\RoleNotFoundException::class);

        $this->adapter->updateRole('inexistente', $dto);
    }

    public function testDisableRoleSuccess(): void
    {
        $this->httpClient
            ->expects($this->exactly(2))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path, array $headers, ?string $body, bool $mapUserExceptions) {
                if ($method === 'GET' && $path === 'admin/realms/constrsw/roles-by-id/uuid-del') {
                    return [
                        'status' => 200,
                        'headers' => [],
                        'data' => [
                            'id' => 'uuid-del',
                            'name' => 'del-role',
                            'attributes' => ['enabled' => ['true']],
                        ],
                        'raw' => '',
                    ];
                }

                if ($method === 'PUT' && $path === 'admin/realms/constrsw/roles-by-id/uuid-del') {
                    $decoded = json_decode((string) $body, true);
                    $this->assertSame(['false'], $decoded['attributes']['enabled']);

                    return [
                        'status' => 204,
                        'headers' => [],
                        'data' => [],
                        'raw' => '',
                    ];
                }

                $this->fail("Chamada inesperada: {$method} {$path}");
            });

        $this->adapter->disableRole('uuid-del');
        $this->assertTrue(true);
    }

    public function testDisableRoleThrowsRoleNotFoundExceptionWhenNotFound(): void
    {
        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->willReturn([
                'status' => 404,
                'headers' => [],
                'data' => [],
                'raw' => '',
            ]);

        $this->expectException(\App\Domain\Exception\RoleNotFoundException::class);

        $this->adapter->disableRole('inexistente');
    }

    public function testAssignRoleToUserSuccess(): void
    {
        $this->httpClient
            ->expects($this->exactly(4))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path, array $headers, ?string $body, bool $mapUserExceptions) {
                if ($method === 'GET' && $path === 'admin/realms/constrsw/users/user-123') {
                    return ['status' => 200, 'headers' => [], 'data' => ['id' => 'user-123', 'username' => 'test@pucrs.br'], 'raw' => ''];
                }

                if ($method === 'GET' && $path === 'admin/realms/constrsw/roles-by-id/role-456') {
                    return ['status' => 200, 'headers' => [], 'data' => ['id' => 'role-456', 'name' => 'professor', 'attributes' => ['enabled' => ['true']]], 'raw' => ''];
                }

                if ($method === 'POST' && $path === 'admin/realms/constrsw/users/user-123/role-mappings/realm') {
                    $decoded = json_decode((string) $body, true);
                    $this->assertSame('role-456', $decoded[0]['id']);
                    $this->assertSame('professor', $decoded[0]['name']);
                    return ['status' => 204, 'headers' => [], 'data' => [], 'raw' => ''];
                }

                if ($method === 'GET' && $path === 'admin/realms/constrsw/users/user-123/role-mappings/realm') {
                    return ['status' => 200, 'headers' => [], 'data' => [['id' => 'role-456', 'name' => 'professor']], 'raw' => ''];
                }

                $this->fail("Chamada inesperada: {$method} {$path}");
            });

        $result = $this->adapter->assignRoleToUser('user-123', 'role-456');

        $this->assertTrue($result['success']);
        $this->assertSame('user-123', $result['userId']);
        $this->assertSame('role-456', $result['roleId']);
        $this->assertSame('professor', $result['roleName']);
        $this->assertSame(['professor'], $result['roles']);
    }

    public function testAssignRoleToUserThrowsUserNotFoundExceptionWhenUserNotFound(): void
    {
        $this->httpClient
            ->expects($this->exactly(3))
            ->method('requestAdmin')
            ->willReturn(['status' => 404, 'headers' => [], 'data' => [], 'raw' => '']);

        $this->expectException(\App\Domain\Exception\UserNotFoundException::class);

        $this->adapter->assignRoleToUser('inexistente', 'role-1');
    }

    public function testAssignRoleToUserThrowsRoleNotFoundExceptionWhenRoleNotFound(): void
    {
        $this->httpClient
            ->expects($this->exactly(3))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path, array $headers, ?string $body, bool $mapUserExceptions) {
                if ($method === 'GET' && $path === 'admin/realms/constrsw/users/user-1') {
                    return ['status' => 200, 'headers' => [], 'data' => ['id' => 'user-1'], 'raw' => ''];
                }

                if ($method === 'GET' && $path === 'admin/realms/constrsw/roles-by-id/role-inexistente') {
                    return ['status' => 404, 'headers' => [], 'data' => [], 'raw' => ''];
                }

                if ($method === 'GET' && $path === 'admin/realms/constrsw/roles/role-inexistente') {
                    return ['status' => 404, 'headers' => [], 'data' => [], 'raw' => ''];
                }

                $this->fail("Chamada inesperada: {$method} {$path}");
            });

        $this->expectException(\App\Domain\Exception\RoleNotFoundException::class);

        $this->adapter->assignRoleToUser('user-1', 'role-inexistente');
    }

    public function testRemoveRoleFromUserSuccess(): void
    {
        $this->httpClient
            ->expects($this->exactly(3))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path, array $headers, ?string $body, bool $mapUserExceptions) {
                if ($method === 'GET' && $path === 'admin/realms/constrsw/users/user-123') {
                    return ['status' => 200, 'headers' => [], 'data' => ['id' => 'user-123'], 'raw' => ''];
                }

                if ($method === 'GET' && $path === 'admin/realms/constrsw/roles-by-id/role-456') {
                    return ['status' => 200, 'headers' => [], 'data' => ['id' => 'role-456', 'name' => 'professor', 'attributes' => ['enabled' => ['true']]], 'raw' => ''];
                }

                if ($method === 'DELETE' && $path === 'admin/realms/constrsw/users/user-123/role-mappings/realm') {
                    $decoded = json_decode((string) $body, true);
                    $this->assertSame('role-456', $decoded[0]['id']);
                    return ['status' => 204, 'headers' => [], 'data' => [], 'raw' => ''];
                }

                $this->fail("Chamada inesperada: {$method} {$path}");
            });

        $this->adapter->removeRoleFromUser('user-123', 'role-456');
        $this->assertTrue(true);
    }
}

