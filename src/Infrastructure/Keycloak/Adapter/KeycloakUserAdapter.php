<?php

declare(strict_types=1);

namespace App\Infrastructure\Keycloak\Adapter;

use App\Application\DTO\User\CreateUserDTO;
use App\Application\DTO\User\UpdatePasswordDTO;
use App\Application\DTO\User\UpdateUserDTO;
use App\Application\DTO\User\UserDTO;
use App\Domain\Exception\UserNotFoundException;
use App\Domain\Port\Outbound\KeycloakUserPortInterface;
use App\Infrastructure\Keycloak\Client\KeycloakHttpClient;
use RuntimeException;

final class KeycloakUserAdapter implements KeycloakUserPortInterface
{
    public function __construct(
        private readonly KeycloakHttpClient $httpClient
    ) {
    }

    public function createUser(CreateUserDTO $dto): UserDTO
    {
        $payload = [
            'username' => $dto->username,
            'email' => $dto->email,
            'firstName' => $dto->firstName,
            'lastName' => $dto->lastName,
            'enabled' => $dto->enabled,
            'emailVerified' => true,
        ];

        if ($dto->password !== '') {
            $payload['credentials'] = [
                [
                    'type' => 'password',
                    'value' => $dto->password,
                    'temporary' => false,
                ],
            ];
        }

        $response = $this->httpClient->requestAdmin(
            'POST',
            $this->usersAdminPath(),
            [],
            json_encode($payload, JSON_THROW_ON_ERROR)
        );

        $userId = $this->extractUserIdFromLocation($response['headers'] ?? []);

        if ($userId === null) {
            $userId = $this->lookupUserIdByUsername($dto->username);
        }

        if ($userId === null) {
            throw new RuntimeException('Falha ao obter o ID do usuário recém-criado no Keycloak.');
        }

        return new UserDTO(
            id: $userId,
            username: $dto->username,
            email: $dto->email,
            firstName: $dto->firstName,
            lastName: $dto->lastName,
            enabled: $dto->enabled,
            roles: $dto->roles
        );
    }

    /**
     * @return UserDTO[]
     */
    public function listActiveUsers(): array
    {
        $response = $this->httpClient->requestAdmin('GET', $this->usersAdminPath());

        $activeUsers = [];
        $rawUsers = $response['data'];

        if (!is_array($rawUsers)) {
            return [];
        }

        foreach ($rawUsers as $userData) {
            if (!is_array($userData)) {
                continue;
            }

            $enabled = (bool) ($userData['enabled'] ?? true);
            if (!$enabled) {
                continue;
            }

            $userId = (string) ($userData['id'] ?? '');
            $roles = $userId !== '' ? $this->fetchUserRoles($userId) : [];

            $activeUsers[] = $this->mapUserRepresentationToDTO($userData, $roles);
        }

        return $activeUsers;
    }

    public function getUserById(string $id, bool $fetchRoles = true): ?UserDTO
    {
        try {
            $response = $this->httpClient->requestAdmin('GET', $this->userAdminPath($id));
        } catch (UserNotFoundException $e) {
            return null;
        }

        if (empty($response['data']) || !is_array($response['data'])) {
            return null;
        }

        $userId = (string) ($response['data']['id'] ?? $id);
        $roles = $fetchRoles ? $this->fetchUserRoles($userId) : [];

        return $this->mapUserRepresentationToDTO($response['data'], $roles);
    }

    public function updateUser(string $id, UpdateUserDTO $dto): void
    {
        $existing = $this->getUserById($id, false);
        if ($existing === null) {
            throw new UserNotFoundException();
        }

        $payload = [
            'firstName' => $dto->firstName ?? $existing->firstName,
            'lastName' => $dto->lastName ?? $existing->lastName,
            'email' => $dto->email ?? $existing->email,
        ];

        if ($dto->email !== null && $existing->username === $existing->email) {
            $payload['username'] = $dto->email;
        }

        $this->httpClient->requestAdmin(
            'PUT',
            $this->userAdminPath($id),
            [],
            json_encode($payload, JSON_THROW_ON_ERROR)
        );
    }

    public function updatePassword(string $id, UpdatePasswordDTO $dto): void
    {
        $existing = $this->getUserById($id, false);
        if ($existing === null) {
            throw new UserNotFoundException();
        }

        $payload = [
            'type' => 'password',
            'value' => $dto->password,
            'temporary' => false,
        ];

        $this->httpClient->requestAdmin(
            'PUT',
            $this->userAdminPath($id) . '/reset-password',
            [],
            json_encode($payload, JSON_THROW_ON_ERROR)
        );
    }

    public function disableUser(string $id): void
    {
        $existing = $this->getUserById($id, false);
        if ($existing === null) {
            throw new UserNotFoundException();
        }

        $payload = [
            'enabled' => false,
        ];

        $this->httpClient->requestAdmin(
            'PUT',
            $this->userAdminPath($id),
            [],
            json_encode($payload, JSON_THROW_ON_ERROR)
        );
    }

    private function extractUserIdFromLocation(array $headers): ?string
    {
        $location = $headers['location'][0] ?? $headers['Location'][0] ?? null;
        if ($location === null || !is_string($location)) {
            return null;
        }

        $parts = explode('/', rtrim($location, '/'));
        $lastPart = end($parts);

        return !empty($lastPart) ? $lastPart : null;
    }

    private function lookupUserIdByUsername(string $username): ?string
    {
        $encodedUsername = urlencode($username);
        $searchResponse = $this->httpClient->requestAdmin(
            'GET',
            $this->usersAdminPath() . "?exact=true&username={$encodedUsername}"
        );

        if (!empty($searchResponse['data']) && is_array($searchResponse['data'])) {
            $first = $searchResponse['data'][0] ?? null;
            if (is_array($first) && isset($first['id'])) {
                return (string) $first['id'];
            }
        }

        return null;
    }

    public function fetchUserRoles(string $userId): array
    {
        $path = sprintf('admin/realms/%s/users/%s/role-mappings/realm', $this->httpClient->getRealm(), rawurlencode($userId));
        $response = $this->httpClient->requestAdmin('GET', $path, [], null, false);

        if ($response['status'] !== 200 || !is_array($response['data'])) {
            return [];
        }

        $roles = [];
        foreach ($response['data'] as $role) {
            if (is_array($role) && !empty($role['name'])) {
                $roles[] = (string) $role['name'];
            }
        }

        return array_values(array_unique($roles));
    }

    private function mapUserRepresentationToDTO(array $data, array $roles = []): UserDTO
    {
        $assignedRoles = !empty($roles) ? $roles : (array) ($data['realmRoles'] ?? $data['roles'] ?? []);

        return new UserDTO(
            id: (string) ($data['id'] ?? ''),
            username: (string) ($data['username'] ?? ''),
            email: (string) ($data['email'] ?? ''),
            firstName: (string) ($data['firstName'] ?? ''),
            lastName: (string) ($data['lastName'] ?? ''),
            enabled: (bool) ($data['enabled'] ?? true),
            roles: array_values(array_unique($assignedRoles))
        );
    }

    private function usersAdminPath(): string
    {
        return "admin/realms/{$this->httpClient->getRealm()}/users";
    }

    private function userAdminPath(string $id): string
    {
        return "admin/realms/{$this->httpClient->getRealm()}/users/{$id}";
    }
}
