<?php

declare(strict_types=1);

namespace App\Tests\Unit\Application\DTO\Role;

use App\Application\DTO\Role\AssignRoleDTO;
use PHPUnit\Framework\TestCase;

final class AssignRoleDTOTest extends TestCase
{
    public function testFromArrayWithStandardRoleId(): void
    {
        $dto = AssignRoleDTO::fromArray(['roleId' => 'uuid-123']);
        $this->assertSame('uuid-123', $dto->roleId);
        $this->assertNull($dto->name);
        $this->assertSame('uuid-123', $dto->getRoleIdentifier());
    }

    public function testFromArrayWithSnakeCaseRoleId(): void
    {
        $dto = AssignRoleDTO::fromArray(['role_id' => 'uuid-456']);
        $this->assertSame('uuid-456', $dto->roleId);
        $this->assertSame('uuid-456', $dto->getRoleIdentifier());
    }

    public function testFromArrayWithGenericId(): void
    {
        $dto = AssignRoleDTO::fromArray(['id' => 'uuid-789']);
        $this->assertSame('uuid-789', $dto->roleId);
        $this->assertSame('uuid-789', $dto->getRoleIdentifier());
    }

    public function testFromArrayWithRoleNameVariants(): void
    {
        $dto1 = AssignRoleDTO::fromArray(['name' => 'professor']);
        $this->assertSame('professor', $dto1->name);

        $dto2 = AssignRoleDTO::fromArray(['roleName' => 'student']);
        $this->assertSame('student', $dto2->name);

        $dto3 = AssignRoleDTO::fromArray(['role_name' => 'coordinator']);
        $this->assertSame('coordinator', $dto3->name);

        $dto4 = AssignRoleDTO::fromArray(['role' => 'administrator']);
        $this->assertSame('administrator', $dto4->name);
    }

    public function testFromArrayWithRolesArray(): void
    {
        $dtoStrings = AssignRoleDTO::fromArray(['roles' => ['professor', 'student']]);
        $this->assertSame('professor', $dtoStrings->name);

        $dtoObjects = AssignRoleDTO::fromArray(['roles' => [['id' => 'uuid-role-obj', 'name' => 'guest']]]);
        $this->assertSame('uuid-role-obj', $dtoObjects->roleId);
        $this->assertSame('guest', $dtoObjects->name);
    }
}
