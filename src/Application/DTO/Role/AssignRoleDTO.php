<?php

declare(strict_types=1);

namespace App\Application\DTO\Role;

final class AssignRoleDTO
{
    public function __construct(
        public readonly ?string $roleId = null,
        public readonly ?string $name = null
    ) {
    }

    public static function fromArray(array $data): self
    {
        $roleId = $data['roleId'] ?? $data['role_id'] ?? $data['id'] ?? null;
        if ($roleId !== null) {
            $roleId = trim((string) $roleId);
            if ($roleId === '') {
                $roleId = null;
            }
        }

        $name = $data['name'] ?? $data['roleName'] ?? $data['role_name'] ?? $data['role'] ?? null;
        if ($name !== null) {
            $name = trim((string) $name);
            if ($name === '') {
                $name = null;
            }
        }

        if ($roleId === null && $name === null && isset($data['roles']) && is_array($data['roles']) && !empty($data['roles'])) {
            $first = $data['roles'][0];
            if (is_string($first)) {
                $name = trim($first);
            } elseif (is_array($first)) {
                $roleId = isset($first['id']) ? trim((string) $first['id']) : (isset($first['roleId']) ? trim((string) $first['roleId']) : null);
                $name = isset($first['name']) ? trim((string) $first['name']) : (isset($first['roleName']) ? trim((string) $first['roleName']) : null);
            }
        }

        return new self(
            roleId: $roleId,
            name: $name
        );
    }

    public function getRoleIdentifier(): ?string
    {
        return $this->roleId ?? $this->name;
    }

    public function toArray(): array
    {
        $result = [];

        if ($this->roleId !== null) {
            $result['roleId'] = $this->roleId;
        }

        if ($this->name !== null) {
            $result['name'] = $this->name;
        }

        return $result;
    }
}
