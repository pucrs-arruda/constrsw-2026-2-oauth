<?php

declare(strict_types=1);

namespace App\Tests\Unit\Infrastructure\Keycloak\Adapter;

use App\Application\DTO\User\CreateUserDTO;
use App\Application\DTO\User\UpdatePasswordDTO;
use App\Application\DTO\User\UpdateUserDTO;
use App\Domain\Exception\UserNotFoundException;
use App\Infrastructure\Keycloak\Adapter\KeycloakUserAdapter;
use App\Infrastructure\Keycloak\Client\KeycloakHttpClient;
use PHPUnit\Framework\TestCase;
use RuntimeException;

final class KeycloakUserAdapterTest extends TestCase
{
    private KeycloakHttpClient $httpClient;
    private KeycloakUserAdapter $adapter;

    protected function setUp(): void
    {
        $this->httpClient = $this->createMock(KeycloakHttpClient::class);
        $this->httpClient->method('getRealm')->willReturn('constrsw');
        $this->adapter = new KeycloakUserAdapter($this->httpClient);
    }

    public function testCreateUserSuccessWithLocationHeader(): void
    {
        $dto = new CreateUserDTO(
            username: 'alice@example.com',
            email: 'alice@example.com',
            firstName: 'Alice',
            lastName: 'Smith',
            password: 'secretPassword123'
        );

        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->with(
                'POST',
                'admin/realms/constrsw/users',
                [],
                $this->callback(function (string $body) {
                    $decoded = json_decode($body, true);
                    return $decoded['username'] === 'alice@example.com'
                        && $decoded['credentials'][0]['value'] === 'secretPassword123';
                })
            )
            ->willReturn([
                'status' => 201,
                'headers' => [
                    'location' => ['http://keycloak:8080/auth/admin/realms/constrsw/users/generated-uuid-999'],
                ],
                'data' => [],
                'raw' => '',
            ]);

        $user = $this->adapter->createUser($dto);

        $this->assertSame('generated-uuid-999', $user->id);
        $this->assertSame('alice@example.com', $user->username);
        $this->assertSame('alice@example.com', $user->email);
        $this->assertSame('Alice', $user->firstName);
        $this->assertSame('Smith', $user->lastName);
        $this->assertTrue($user->enabled);
    }

    public function testCreateUserSuccessWithLookupFallback(): void
    {
        $dto = new CreateUserDTO(
            username: 'bob@example.com',
            email: 'bob@example.com',
            firstName: 'Bob',
            lastName: 'Builder',
            password: 'secretPassword123'
        );

        $this->httpClient
            ->expects($this->exactly(2))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path) {
                if ($method === 'POST') {
                    return [
                        'status' => 201,
                        'headers' => [],
                        'data' => [],
                        'raw' => '',
                    ];
                }

                if ($method === 'GET') {
                    return [
                        'status' => 200,
                        'headers' => [],
                        'data' => [
                            [
                                'id' => 'fallback-uuid-888',
                                'username' => 'bob@example.com',
                            ],
                        ],
                        'raw' => '',
                    ];
                }

                throw new RuntimeException("Unexpected call {$method} {$path}");
            });

        $user = $this->adapter->createUser($dto);

        $this->assertSame('fallback-uuid-888', $user->id);
        $this->assertSame('bob@example.com', $user->username);
    }

    public function testCreateUserThrowsRuntimeExceptionWhenIdNotFound(): void
    {
        $dto = new CreateUserDTO(
            username: 'charlie@example.com',
            email: 'charlie@example.com',
            firstName: 'Charlie',
            lastName: 'Brown',
            password: 'secretPassword123'
        );

        $this->httpClient
            ->expects($this->exactly(2))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method) {
                if ($method === 'POST') {
                    return ['status' => 201, 'headers' => [], 'data' => [], 'raw' => ''];
                }
                return ['status' => 200, 'headers' => [], 'data' => [], 'raw' => ''];
            });

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('Falha ao obter o ID do usuário recém-criado no Keycloak.');

        $this->adapter->createUser($dto);
    }

    public function testListActiveUsersFiltersOutDisabledUsers(): void
    {
        $this->httpClient
            ->expects($this->exactly(3))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path) {
                if ($method === 'GET' && $path === 'admin/realms/constrsw/users') {
                    return [
                        'status' => 200,
                        'data' => [
                            [
                                'id' => 'active-1',
                                'username' => 'active1@example.com',
                                'email' => 'active1@example.com',
                                'firstName' => 'Active',
                                'lastName' => 'One',
                                'enabled' => true,
                            ],
                            [
                                'id' => 'disabled-2',
                                'username' => 'disabled@example.com',
                                'email' => 'disabled@example.com',
                                'firstName' => 'Disabled',
                                'lastName' => 'Two',
                                'enabled' => false,
                            ],
                            [
                                'id' => 'active-3',
                                'username' => 'active3@example.com',
                                'email' => 'active3@example.com',
                                'firstName' => 'Active',
                                'lastName' => 'Three',
                                'enabled' => true,
                            ],
                        ],
                        'raw' => '',
                    ];
                }

                if ($method === 'GET' && str_contains($path, 'role-mappings/realm')) {
                    return ['status' => 200, 'headers' => [], 'data' => [['name' => 'student']], 'raw' => ''];
                }

                $this->fail("Chamada inesperada: {$method} {$path}");
            });

        $users = $this->adapter->listActiveUsers();

        $this->assertCount(2, $users);
        $this->assertSame('active-1', $users[0]->id);
        $this->assertSame('active-3', $users[1]->id);
        $this->assertSame(['student'], $users[0]->roles);
    }

    public function testGetUserByIdSuccess(): void
    {
        $this->httpClient
            ->expects($this->exactly(2))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path) {
                if ($method === 'GET' && $path === 'admin/realms/constrsw/users/uuid-target') {
                    return [
                        'status' => 200,
                        'data' => [
                            'id' => 'uuid-target',
                            'username' => 'user@example.com',
                            'email' => 'user@example.com',
                            'firstName' => 'First',
                            'lastName' => 'Last',
                            'enabled' => true,
                        ],
                        'raw' => '',
                    ];
                }

                if ($method === 'GET' && $path === 'admin/realms/constrsw/users/uuid-target/role-mappings/realm') {
                    return [
                        'status' => 200,
                        'data' => [['name' => 'administrator']],
                        'raw' => '',
                    ];
                }

                $this->fail("Chamada inesperada: {$method} {$path}");
            });

        $user = $this->adapter->getUserById('uuid-target');

        $this->assertNotNull($user);
        $this->assertSame('uuid-target', $user->id);
        $this->assertSame('user@example.com', $user->email);
        $this->assertSame(['administrator'], $user->roles);
    }

    public function testGetUserByIdReturnsNullWhenNotFound(): void
    {
        $this->httpClient
            ->expects($this->once())
            ->method('requestAdmin')
            ->with('GET', 'admin/realms/constrsw/users/missing-uuid')
            ->willThrowException(new UserNotFoundException());

        $user = $this->adapter->getUserById('missing-uuid');

        $this->assertNull($user);
    }

    public function testUpdateUserSuccess(): void
    {
        $dto = new UpdateUserDTO(firstName: 'NewFirst', lastName: 'NewLast', email: 'newemail@example.com');

        $this->httpClient
            ->expects($this->exactly(2))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path, array $headers = [], ?string $body = null) {
                if ($method === 'GET') {
                    return [
                        'status' => 200,
                        'data' => [
                            'id' => 'uuid-update',
                            'username' => 'old@example.com',
                            'email' => 'old@example.com',
                            'firstName' => 'OldFirst',
                            'lastName' => 'OldLast',
                            'enabled' => true,
                        ],
                        'raw' => '',
                    ];
                }

                if ($method === 'PUT') {
                    $decoded = json_decode((string) $body, true);
                    $this->assertSame('NewFirst', $decoded['firstName']);
                    $this->assertSame('NewLast', $decoded['lastName']);
                    $this->assertSame('newemail@example.com', $decoded['email']);
                    return ['status' => 204, 'data' => [], 'raw' => ''];
                }

                throw new RuntimeException("Unexpected call {$method}");
            });

        $this->adapter->updateUser('uuid-update', $dto);
    }

    public function testUpdatePasswordSuccess(): void
    {
        $dto = new UpdatePasswordDTO('freshNewPassword123');

        $this->httpClient
            ->expects($this->exactly(2))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path, array $headers = [], ?string $body = null) {
                if ($method === 'GET') {
                    return [
                        'status' => 200,
                        'data' => [
                            'id' => 'uuid-pwd',
                            'username' => 'pwd@example.com',
                            'email' => 'pwd@example.com',
                            'firstName' => 'Pwd',
                            'lastName' => 'User',
                            'enabled' => true,
                        ],
                        'raw' => '',
                    ];
                }

                if ($method === 'PUT' && str_ends_with($path, '/reset-password')) {
                    $decoded = json_decode((string) $body, true);
                    $this->assertSame('password', $decoded['type']);
                    $this->assertSame('freshNewPassword123', $decoded['value']);
                    $this->assertFalse($decoded['temporary']);
                    return ['status' => 204, 'data' => [], 'raw' => ''];
                }

                throw new RuntimeException("Unexpected call {$method} {$path}");
            });

        $this->adapter->updatePassword('uuid-pwd', $dto);
    }

    public function testDisableUserSuccess(): void
    {
        $this->httpClient
            ->expects($this->exactly(2))
            ->method('requestAdmin')
            ->willReturnCallback(function (string $method, string $path, array $headers = [], ?string $body = null) {
                if ($method === 'GET') {
                    return [
                        'status' => 200,
                        'data' => [
                            'id' => 'uuid-disable',
                            'username' => 'disable@example.com',
                            'email' => 'disable@example.com',
                            'firstName' => 'Disable',
                            'lastName' => 'User',
                            'enabled' => true,
                        ],
                        'raw' => '',
                    ];
                }

                if ($method === 'PUT') {
                    $decoded = json_decode((string) $body, true);
                    $this->assertFalse($decoded['enabled']);
                    return ['status' => 204, 'data' => [], 'raw' => ''];
                }

                throw new RuntimeException("Unexpected call {$method}");
            });

        $this->adapter->disableUser('uuid-disable');
    }
}
