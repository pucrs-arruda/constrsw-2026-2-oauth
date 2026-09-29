<?php

declare(strict_types=1);

namespace App\Domain\Port\Outbound;

use App\Application\DTO\Role\CreateRoleDTO;
use App\Application\DTO\Role\RoleDTO;
use App\Application\DTO\Role\UpdateRoleDTO;

interface KeycloakRolePortInterface
{
    public function createRole(CreateRoleDTO $dto): RoleDTO;

    /**
     * @return RoleDTO[]
     */
    public function listActiveRoles(): array;

    public function getRoleById(string $id): ?RoleDTO;

    public function updateRole(string $id, UpdateRoleDTO $dto): RoleDTO;

    public function disableRole(string $id): void;

    /**
     * @return array<string, mixed>
     */
    public function assignRoleToUser(string $userId, string $roleIdentifier): array;

    public function removeRoleFromUser(string $userId, string $roleIdentifier): void;

    /**
     * @return string[]
     */
    public function getUserRoles(string $userId): array;
}
