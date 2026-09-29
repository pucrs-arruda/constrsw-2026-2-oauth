<?php

declare(strict_types=1);

namespace App\Infrastructure\Keycloak\Adapter;

use App\Application\DTO\Role\CreateRoleDTO;
use App\Application\DTO\Role\RoleDTO;
use App\Application\DTO\Role\UpdateRoleDTO;
use App\Domain\Exception\RoleAlreadyExistsException;
use App\Domain\Exception\RoleNotFoundException;
use App\Domain\Exception\UserNotFoundException;
use App\Domain\Port\Outbound\KeycloakRolePortInterface;
use App\Infrastructure\Keycloak\Client\KeycloakHttpClient;
use RuntimeException;

final class KeycloakRoleAdapter implements KeycloakRolePortInterface
{
    public function __construct(
        private readonly KeycloakHttpClient $httpClient
    ) {
    }

    public function createRole(CreateRoleDTO $dto): RoleDTO
    {
        $payload = [
            'name' => $dto->name,
            'description' => $dto->description ?? '',
            'attributes' => [
                'enabled' => ['true'],
            ],
        ];

        $response = $this->httpClient->requestAdmin(
            'POST',
            $this->rolesAdminPath(),
            [],
            json_encode($payload, JSON_THROW_ON_ERROR),
            false
        );

        if ($response['status'] === 409) {
            throw new RoleAlreadyExistsException("Já existe um role com o nome '{$dto->name}'.");
        }

        if ($response['status'] !== 201 && $response['status'] !== 200) {
            throw new RuntimeException("Falha ao criar role no Keycloak. Status: {$response['status']}.");
        }

        $roleData = $this->lookupRoleByName($dto->name);

        if ($roleData === null) {
            throw new RuntimeException("Falha ao recuperar dados da role recém-criada '{$dto->name}'.");
        }

        return $this->mapToDTO($roleData);
    }

    /**
     * @return RoleDTO[]
     */
    public function listActiveRoles(): array
    {
        $response = $this->httpClient->requestAdmin(
            'GET',
            $this->rolesAdminPath() . '?briefRepresentation=false',
            [],
            null,
            false
        );

        $roles = [];
        $data = is_array($response['data']) ? $response['data'] : [];

        foreach ($data as $roleData) {
            if (!is_array($roleData)) {
                continue;
            }

            if ($this->isRoleActive($roleData)) {
                $roles[] = $this->mapToDTO($roleData);
            }
        }

        return $roles;
    }

    public function getRoleById(string $id): ?RoleDTO
    {
        $roleData = $this->fetchRoleRaw($id);
        if ($roleData === null || !$this->isRoleActive($roleData)) {
            return null;
        }

        return $this->mapToDTO($roleData);
    }

    public function updateRole(string $id, UpdateRoleDTO $dto): RoleDTO
    {
        $roleData = $this->fetchRoleRaw($id);
        if ($roleData === null || !$this->isRoleActive($roleData)) {
            throw new RoleNotFoundException("Role '{$id}' não encontrado.");
        }

        $roleId = (string) ($roleData['id'] ?? $id);
        $name = $dto->name !== null && trim($dto->name) !== '' ? trim($dto->name) : (string) ($roleData['name'] ?? '');
        $description = $dto->description !== null ? $dto->description : ($roleData['description'] ?? null);

        $attributes = is_array($roleData['attributes'] ?? null) ? $roleData['attributes'] : [];
        $attributes['enabled'] = ['true'];

        $payload = array_merge($roleData, [
            'name' => $name,
            'description' => $description ?? '',
            'attributes' => $attributes,
        ]);

        $response = $this->httpClient->requestAdmin(
            'PUT',
            $this->rolesByIdAdminPath($roleId),
            [],
            json_encode($payload, JSON_THROW_ON_ERROR),
            false
        );

        if ($response['status'] === 404) {
            throw new RoleNotFoundException("Role '{$id}' não encontrado.");
        }

        if ($response['status'] === 409) {
            throw new RoleAlreadyExistsException("Já existe um role com o nome '{$name}'.");
        }

        if ($response['status'] !== 200 && $response['status'] !== 204) {
            throw new RuntimeException("Falha ao atualizar role no Keycloak. Status: {$response['status']}.");
        }

        return new RoleDTO(
            id: $roleId,
            name: $name,
            description: $description !== null && $description !== '' ? $description : null,
            enabled: true
        );
    }

    public function disableRole(string $id): void
    {
        $roleData = $this->fetchRoleRaw($id);
        if ($roleData === null || !$this->isRoleActive($roleData)) {
            throw new RoleNotFoundException("Role '{$id}' não encontrado.");
        }

        $roleId = (string) ($roleData['id'] ?? $id);
        $attributes = is_array($roleData['attributes'] ?? null) ? $roleData['attributes'] : [];
        $attributes['enabled'] = ['false'];

        $payload = array_merge($roleData, [
            'attributes' => $attributes,
        ]);

        $response = $this->httpClient->requestAdmin(
            'PUT',
            $this->rolesByIdAdminPath($roleId),
            [],
            json_encode($payload, JSON_THROW_ON_ERROR),
            false
        );

        if ($response['status'] === 404) {
            throw new RoleNotFoundException("Role '{$id}' não encontrado.");
        }

        if ($response['status'] !== 200 && $response['status'] !== 204) {
            throw new RuntimeException("Falha ao inativar role no Keycloak. Status: {$response['status']}.");
        }
    }

    public function assignRoleToUser(string $userId, string $roleIdentifier): array
    {
        $resolvedUserId = $this->resolveUserId($userId);
        $roleData = $this->resolveRole($roleIdentifier);

        $path = sprintf('admin/realms/%s/users/%s/role-mappings/realm', $this->httpClient->getRealm(), rawurlencode($resolvedUserId));
        $payload = json_encode([[
            'id' => (string) ($roleData['id'] ?? ''),
            'name' => (string) ($roleData['name'] ?? ''),
        ]], JSON_THROW_ON_ERROR);

        $response = $this->httpClient->requestAdmin('POST', $path, [], $payload, false);

        if ($response['status'] !== 200 && $response['status'] !== 204) {
            throw new RuntimeException("Falha ao atribuir role ao usuário no Keycloak. Status: {$response['status']}.");
        }

        $roles = $this->fetchUserRolesQuietly($resolvedUserId);
        if (empty($roles) && isset($roleData['name'])) {
            $roles = [(string) $roleData['name']];
        }

        return [
            'success' => true,
            'userId' => $resolvedUserId,
            'roleId' => (string) ($roleData['id'] ?? ''),
            'roleName' => (string) ($roleData['name'] ?? ''),
            'roles' => $roles,
        ];
    }

    public function removeRoleFromUser(string $userId, string $roleIdentifier): void
    {
        $resolvedUserId = $this->resolveUserId($userId);
        $roleData = $this->resolveRole($roleIdentifier);

        $path = sprintf('admin/realms/%s/users/%s/role-mappings/realm', $this->httpClient->getRealm(), rawurlencode($resolvedUserId));
        $payload = json_encode([[
            'id' => (string) ($roleData['id'] ?? ''),
            'name' => (string) ($roleData['name'] ?? ''),
        ]], JSON_THROW_ON_ERROR);

        $response = $this->httpClient->requestAdmin('DELETE', $path, [], $payload, false);

        if ($response['status'] !== 200 && $response['status'] !== 204) {
            throw new RuntimeException("Falha ao revogar role do usuário no Keycloak. Status: {$response['status']}.");
        }
    }

    /**
     * @return string[]
     */
    public function getUserRoles(string $userId): array
    {
        $resolvedUserId = $this->resolveUserId($userId);
        return $this->fetchUserRolesQuietly($resolvedUserId);
    }

    /**
     * @return string[]
     */
    private function fetchUserRolesQuietly(string $userId): array
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

    private function resolveRole(string $roleIdentifier): array
    {
        $roleData = $this->fetchRoleRaw($roleIdentifier);

        if ($roleData === null) {
            $roleData = $this->lookupRoleByName($roleIdentifier);
        }

        if ($roleData === null || !$this->isRoleActive($roleData)) {
            throw new RoleNotFoundException("Role '{$roleIdentifier}' não encontrado.");
        }

        return $roleData;
    }

    private function resolveUserId(string $userId): string
    {
        $userId = trim($userId);
        if ($userId === '') {
            throw new UserNotFoundException("ID de usuário inválido.");
        }

        $path = sprintf('admin/realms/%s/users/%s', $this->httpClient->getRealm(), rawurlencode($userId));
        $response = $this->httpClient->requestAdmin('GET', $path, [], null, false);

        if ($response['status'] === 200 && !empty($response['data']['id'])) {
            return (string) $response['data']['id'];
        }

        // Tenta buscar por username
        $searchResponse = $this->httpClient->requestAdmin(
            'GET',
            sprintf('admin/realms/%s/users?exact=true&username=%s', $this->httpClient->getRealm(), urlencode($userId)),
            [],
            null,
            false
        );

        if ($searchResponse['status'] === 200 && !empty($searchResponse['data'][0]['id'])) {
            return (string) $searchResponse['data'][0]['id'];
        }

        // Tenta buscar por email
        $searchResponse = $this->httpClient->requestAdmin(
            'GET',
            sprintf('admin/realms/%s/users?exact=true&email=%s', $this->httpClient->getRealm(), urlencode($userId)),
            [],
            null,
            false
        );

        if ($searchResponse['status'] === 200 && !empty($searchResponse['data'][0]['id'])) {
            return (string) $searchResponse['data'][0]['id'];
        }

        throw new UserNotFoundException("Usuário '{$userId}' não encontrado.");
    }

    private function fetchRoleRaw(string $id): ?array
    {
        $response = $this->httpClient->requestAdmin(
            'GET',
            $this->rolesByIdAdminPath($id),
            [],
            null,
            false
        );

        if ($response['status'] === 200 && is_array($response['data']) && !empty($response['data'])) {
            return $response['data'];
        }

        return null;
    }

    private function lookupRoleByName(string $name): ?array
    {
        $encodedName = rawurlencode($name);
        $response = $this->httpClient->requestAdmin(
            'GET',
            $this->rolesAdminPath() . '/' . $encodedName,
            [],
            null,
            false
        );

        if ($response['status'] === 200 && is_array($response['data']) && !empty($response['data'])) {
            return $response['data'];
        }

        return null;
    }

    private function isRoleActive(array $data): bool
    {
        $attributes = $data['attributes'] ?? [];
        if (!is_array($attributes)) {
            return true;
        }

        $enabledAttr = $attributes['enabled'] ?? null;
        if (is_array($enabledAttr) && isset($enabledAttr[0])) {
            return $enabledAttr[0] !== 'false';
        }

        if (is_string($enabledAttr)) {
            return $enabledAttr !== 'false';
        }

        return true;
    }

    private function mapToDTO(array $data): RoleDTO
    {
        return new RoleDTO(
            id: (string) ($data['id'] ?? ''),
            name: (string) ($data['name'] ?? ''),
            description: isset($data['description']) && $data['description'] !== '' ? (string) $data['description'] : null,
            enabled: $this->isRoleActive($data)
        );
    }

    private function rolesAdminPath(): string
    {
        return sprintf('admin/realms/%s/roles', $this->httpClient->getRealm());
    }

    private function rolesByIdAdminPath(string $id): string
    {
        return sprintf('admin/realms/%s/roles-by-id/%s', $this->httpClient->getRealm(), rawurlencode($id));
    }
}
