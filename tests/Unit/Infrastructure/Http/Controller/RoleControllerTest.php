<?php

declare(strict_types=1);

namespace App\Tests\Unit\Infrastructure\Http\Controller;

use App\Application\DTO\Role\AssignRoleDTO;
use App\Application\DTO\Role\CreateRoleDTO;
use App\Application\DTO\Role\RoleDTO;
use App\Application\DTO\Role\UpdateRoleDTO;
use App\Domain\Port\Inbound\AssignUserRoleUseCaseInterface;
use App\Domain\Port\Inbound\CreateRoleUseCaseInterface;
use App\Domain\Port\Inbound\DeleteRoleUseCaseInterface;
use App\Domain\Port\Inbound\GetRoleByIdUseCaseInterface;
use App\Domain\Port\Inbound\ListRolesUseCaseInterface;
use App\Domain\Port\Inbound\UnassignUserRoleUseCaseInterface;
use App\Domain\Port\Inbound\UpdateRoleUseCaseInterface;
use App\Infrastructure\Http\Controller\RoleController;
use PHPUnit\Framework\TestCase;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;

final class RoleControllerTest extends TestCase
{
    private CreateRoleUseCaseInterface $createRoleUseCase;
    private ListRolesUseCaseInterface $listRolesUseCase;
    private GetRoleByIdUseCaseInterface $getRoleByIdUseCase;
    private UpdateRoleUseCaseInterface $updateRoleUseCase;
    private DeleteRoleUseCaseInterface $deleteRoleUseCase;
    private AssignUserRoleUseCaseInterface $assignUserRoleUseCase;
    private UnassignUserRoleUseCaseInterface $unassignUserRoleUseCase;
    private \App\Domain\Port\Inbound\GetUserRolesUseCaseInterface $getUserRolesUseCase;
    private RoleController $controller;

    protected function setUp(): void
    {
        $this->createRoleUseCase = $this->createMock(CreateRoleUseCaseInterface::class);
        $this->listRolesUseCase = $this->createMock(ListRolesUseCaseInterface::class);
        $this->getRoleByIdUseCase = $this->createMock(GetRoleByIdUseCaseInterface::class);
        $this->updateRoleUseCase = $this->createMock(UpdateRoleUseCaseInterface::class);
        $this->deleteRoleUseCase = $this->createMock(DeleteRoleUseCaseInterface::class);
        $this->assignUserRoleUseCase = $this->createMock(AssignUserRoleUseCaseInterface::class);
        $this->unassignUserRoleUseCase = $this->createMock(UnassignUserRoleUseCaseInterface::class);
        $this->getUserRolesUseCase = $this->createMock(\App\Domain\Port\Inbound\GetUserRolesUseCaseInterface::class);

        $this->controller = new RoleController(
            $this->createRoleUseCase,
            $this->listRolesUseCase,
            $this->getRoleByIdUseCase,
            $this->updateRoleUseCase,
            $this->deleteRoleUseCase,
            $this->assignUserRoleUseCase,
            $this->unassignUserRoleUseCase,
            $this->getUserRolesUseCase
        );
    }

    public function testCreateReturns201WithRoleJson(): void
    {
        $roleDTO = new RoleDTO(
            id: 'uuid-role-1',
            name: 'editor',
            description: 'Content Editor',
            enabled: true
        );

        $this->createRoleUseCase
            ->expects($this->once())
            ->method('execute')
            ->with($this->callback(function (CreateRoleDTO $dto) {
                return $dto->name === 'editor' && $dto->description === 'Content Editor';
            }))
            ->willReturn($roleDTO);

        $request = new Request(
            content: json_encode(['name' => 'editor', 'description' => 'Content Editor'], JSON_THROW_ON_ERROR)
        );

        $response = $this->controller->create($request);

        $this->assertSame(Response::HTTP_CREATED, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('uuid-role-1', $data['id']);
        $this->assertSame('editor', $data['name']);
        $this->assertSame('Content Editor', $data['description']);
        $this->assertTrue($data['enabled']);
    }

    public function testListReturns200WithArrayOfRoles(): void
    {
        $roles = [
            new RoleDTO(id: 'uuid-1', name: 'admin', description: 'Admin role', enabled: true),
            new RoleDTO(id: 'uuid-2', name: 'student', description: 'Student role', enabled: true),
        ];

        $this->listRolesUseCase
            ->expects($this->once())
            ->method('execute')
            ->willReturn($roles);

        $response = $this->controller->list();

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertCount(2, $data);
        $this->assertSame('admin', $data[0]['name']);
        $this->assertSame('student', $data[1]['name']);
    }

    public function testGetReturns200WithRoleJson(): void
    {
        $roleDTO = new RoleDTO(
            id: 'uuid-123',
            name: 'coordinator',
            description: 'Coordinator role',
            enabled: true
        );

        $this->getRoleByIdUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('uuid-123')
            ->willReturn($roleDTO);

        $response = $this->controller->get('uuid-123');

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('uuid-123', $data['id']);
        $this->assertSame('coordinator', $data['name']);
    }

    public function testUpdateReturns200WithRoleJson(): void
    {
        $roleDTO = new RoleDTO(
            id: 'uuid-123',
            name: 'coordinator-updated',
            description: 'Updated desc',
            enabled: true
        );

        $this->updateRoleUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('uuid-123', $this->callback(function (UpdateRoleDTO $dto) {
                return $dto->name === 'coordinator-updated' && $dto->description === 'Updated desc';
            }), false)
            ->willReturn($roleDTO);

        $request = new Request(
            content: json_encode(['name' => 'coordinator-updated', 'description' => 'Updated desc'], JSON_THROW_ON_ERROR)
        );

        $response = $this->controller->update('uuid-123', $request);

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('uuid-123', $data['id']);
        $this->assertSame('coordinator-updated', $data['name']);
    }

    public function testPatchReturns200WithRoleJson(): void
    {
        $roleDTO = new RoleDTO(
            id: 'uuid-123',
            name: 'coordinator',
            description: 'Patched desc',
            enabled: true
        );

        $this->updateRoleUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('uuid-123', $this->callback(function (UpdateRoleDTO $dto) {
                return $dto->description === 'Patched desc';
            }), true)
            ->willReturn($roleDTO);

        $request = new Request(
            content: json_encode(['description' => 'Patched desc'], JSON_THROW_ON_ERROR)
        );

        $response = $this->controller->patch('uuid-123', $request);

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('uuid-123', $data['id']);
        $this->assertSame('Patched desc', $data['description']);
    }

    public function testDeleteReturns204WithEmptyBody(): void
    {
        $this->deleteRoleUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('uuid-123');

        $response = $this->controller->delete('uuid-123');

        $this->assertSame(Response::HTTP_NO_CONTENT, $response->getStatusCode());
    }

    public function testAssignRoleReturns200WithResultJson(): void
    {
        $expectedResult = [
            'message' => 'Papel atribuído com sucesso',
            'userId' => 'user-123',
            'roleId' => 'role-456',
            'roleName' => 'admin',
        ];

        $this->assignUserRoleUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('user-123', $this->callback(function (AssignRoleDTO $dto) {
                return $dto->roleId === 'role-456' && $dto->name === 'admin';
            }))
            ->willReturn($expectedResult);

        $request = new Request(
            content: json_encode(['roleId' => 'role-456', 'name' => 'admin'], JSON_THROW_ON_ERROR)
        );

        $response = $this->controller->assignRole('user-123', $request);

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('Papel atribuído com sucesso', $data['message']);
        $this->assertSame('user-123', $data['userId']);
        $this->assertSame('role-456', $data['roleId']);
        $this->assertSame('admin', $data['roleName']);
    }

    public function testUnassignRoleReturns204WithEmptyBody(): void
    {
        $this->unassignUserRoleUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('user-123', 'role-456');

        $response = $this->controller->unassignRole('user-123', 'role-456');

        $this->assertSame(Response::HTTP_NO_CONTENT, $response->getStatusCode());
    }

    public function testListUserRolesReturns200WithRoles(): void
    {
        $this->getUserRolesUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('user-123')
            ->willReturn(['administrator', 'professor']);

        $response = $this->controller->listUserRoles('user-123');

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('user-123', $data['userId']);
        $this->assertSame(['administrator', 'professor'], $data['roles']);
    }

    public function testAssignRoleByPathReturns200(): void
    {
        $this->assignUserRoleUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('user-123', $this->callback(function (AssignRoleDTO $dto) {
                return $dto->roleId === 'role-path-1';
            }))
            ->willReturn([
                'success' => true,
                'userId' => 'user-123',
                'roleId' => 'role-path-1',
                'roleName' => 'student',
                'roles' => ['student'],
            ]);

        $response = $this->controller->assignRoleByPath('user-123', 'role-path-1');

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertTrue($data['success']);
        $this->assertSame(['student'], $data['roles']);
    }

    public function testAssignRoleThrowsWhenGuardRejects(): void
    {
        $guard = $this->createMock(\App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface::class);
        $guard->expects($this->once())
            ->method('requireAdmin')
            ->willThrowException(new \App\Domain\Exception\AccessDeniedException('Apenas admin pode atribuir'));

        $controller = new RoleController(
            $this->createRoleUseCase,
            $this->listRolesUseCase,
            $this->getRoleByIdUseCase,
            $this->updateRoleUseCase,
            $this->deleteRoleUseCase,
            $this->assignUserRoleUseCase,
            $this->unassignUserRoleUseCase,
            $this->getUserRolesUseCase,
            $guard
        );

        $request = new Request(content: json_encode(['roleName' => 'admin'], JSON_THROW_ON_ERROR));

        $this->expectException(\App\Domain\Exception\AccessDeniedException::class);
        $controller->assignRole('user-target', $request);
    }

    public function testAssignRoleByPathThrowsWhenGuardRejects(): void
    {
        $guard = $this->createMock(\App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface::class);
        $guard->expects($this->once())
            ->method('requireAdmin')
            ->willThrowException(new \App\Domain\Exception\AccessDeniedException('Apenas admin pode atribuir'));

        $controller = new RoleController(
            $this->createRoleUseCase,
            $this->listRolesUseCase,
            $this->getRoleByIdUseCase,
            $this->updateRoleUseCase,
            $this->deleteRoleUseCase,
            $this->assignUserRoleUseCase,
            $this->unassignUserRoleUseCase,
            $this->getUserRolesUseCase,
            $guard
        );

        $request = new Request();

        $this->expectException(\App\Domain\Exception\AccessDeniedException::class);
        $controller->assignRoleByPath('user-target', 'role-123', $request);
    }

    public function testUnassignRoleThrowsWhenGuardRejects(): void
    {
        $guard = $this->createMock(\App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface::class);
        $guard->expects($this->once())
            ->method('requireAdmin')
            ->willThrowException(new \App\Domain\Exception\AccessDeniedException('Apenas admin pode remover'));

        $controller = new RoleController(
            $this->createRoleUseCase,
            $this->listRolesUseCase,
            $this->getRoleByIdUseCase,
            $this->updateRoleUseCase,
            $this->deleteRoleUseCase,
            $this->assignUserRoleUseCase,
            $this->unassignUserRoleUseCase,
            $this->getUserRolesUseCase,
            $guard
        );

        $request = new Request();

        $this->expectException(\App\Domain\Exception\AccessDeniedException::class);
        $controller->unassignRole('user-target', 'role-123', $request);
    }

    public function testCreateRoleThrowsWhenGuardRejects(): void
    {
        $guard = $this->createMock(\App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface::class);
        $guard->expects($this->once())
            ->method('requireAdmin')
            ->willThrowException(new \App\Domain\Exception\AccessDeniedException('Apenas admin pode criar'));

        $controller = new RoleController(
            $this->createRoleUseCase,
            $this->listRolesUseCase,
            $this->getRoleByIdUseCase,
            $this->updateRoleUseCase,
            $this->deleteRoleUseCase,
            $this->assignUserRoleUseCase,
            $this->unassignUserRoleUseCase,
            $this->getUserRolesUseCase,
            $guard
        );

        $request = new Request(content: json_encode(['name' => 'superadmin'], JSON_THROW_ON_ERROR));

        $this->expectException(\App\Domain\Exception\AccessDeniedException::class);
        $controller->create($request);
    }

    public function testListUserRolesThrowsWhenGuardRejects(): void
    {
        $guard = $this->createMock(\App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface::class);
        $guard->expects($this->once())
            ->method('requireAdminOrSelf')
            ->with($this->isInstanceOf(Request::class), 'other-target')
            ->willThrowException(new \App\Domain\Exception\AccessDeniedException('Apenas admin ou o próprio'));

        $controller = new RoleController(
            $this->createRoleUseCase,
            $this->listRolesUseCase,
            $this->getRoleByIdUseCase,
            $this->updateRoleUseCase,
            $this->deleteRoleUseCase,
            $this->assignUserRoleUseCase,
            $this->unassignUserRoleUseCase,
            $this->getUserRolesUseCase,
            $guard
        );

        $request = new Request();

        $this->expectException(\App\Domain\Exception\AccessDeniedException::class);
        $controller->listUserRoles('other-target', $request);
    }
}
