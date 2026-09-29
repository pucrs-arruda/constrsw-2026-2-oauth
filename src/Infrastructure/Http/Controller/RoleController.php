<?php

declare(strict_types=1);

namespace App\Infrastructure\Http\Controller;

use App\Application\DTO\Role\AssignRoleDTO;
use App\Application\DTO\Role\CreateRoleDTO;
use App\Application\DTO\Role\RoleDTO;
use App\Application\DTO\Role\UpdateRoleDTO;
use App\Domain\Port\Inbound\AssignUserRoleUseCaseInterface;
use App\Domain\Port\Inbound\CreateRoleUseCaseInterface;
use App\Domain\Port\Inbound\DeleteRoleUseCaseInterface;
use App\Domain\Port\Inbound\GetRoleByIdUseCaseInterface;
use App\Domain\Port\Inbound\GetUserRolesUseCaseInterface;
use App\Domain\Port\Inbound\ListRolesUseCaseInterface;
use App\Domain\Port\Inbound\UnassignUserRoleUseCaseInterface;
use App\Domain\Port\Inbound\UpdateRoleUseCaseInterface;
use App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

final class RoleController extends AbstractController
{
    public function __construct(
        private readonly CreateRoleUseCaseInterface $createRoleUseCase,
        private readonly ListRolesUseCaseInterface $listRolesUseCase,
        private readonly GetRoleByIdUseCaseInterface $getRoleByIdUseCase,
        private readonly UpdateRoleUseCaseInterface $updateRoleUseCase,
        private readonly DeleteRoleUseCaseInterface $deleteRoleUseCase,
        private readonly AssignUserRoleUseCaseInterface $assignUserRoleUseCase,
        private readonly UnassignUserRoleUseCaseInterface $unassignUserRoleUseCase,
        private readonly GetUserRolesUseCaseInterface $getUserRolesUseCase,
        private readonly ?AdminAuthorizationGuardInterface $guard = null
    ) {
    }

    #[Route('/roles', name: 'role_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $this->guard?->requireAdmin($request);

        $data = $this->extractRequestData($request);
        $dto = CreateRoleDTO::fromArray($data);
        $createdRole = $this->createRoleUseCase->execute($dto);

        return new JsonResponse($createdRole->toArray(), JsonResponse::HTTP_CREATED);
    }

    #[Route('/roles', name: 'role_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        $roles = $this->listRolesUseCase->execute();
        $payload = array_map(static fn (RoleDTO $role) => $role->toArray(), $roles);

        return new JsonResponse($payload, JsonResponse::HTTP_OK);
    }

    #[Route('/roles/{id}', name: 'role_get', methods: ['GET'])]
    public function get(string $id): JsonResponse
    {
        $role = $this->getRoleByIdUseCase->execute($id);

        return new JsonResponse($role->toArray(), JsonResponse::HTTP_OK);
    }

    #[Route('/roles/{id}', name: 'role_update', methods: ['PUT'])]
    public function update(string $id, Request $request): JsonResponse
    {
        $this->guard?->requireAdmin($request);

        $data = $this->extractRequestData($request);
        $dto = UpdateRoleDTO::fromArray($data);
        $updatedRole = $this->updateRoleUseCase->execute($id, $dto, false);

        return new JsonResponse($updatedRole->toArray(), JsonResponse::HTTP_OK);
    }

    #[Route('/roles/{id}', name: 'role_patch', methods: ['PATCH'])]
    public function patch(string $id, Request $request): JsonResponse
    {
        $this->guard?->requireAdmin($request);

        $data = $this->extractRequestData($request);
        $dto = UpdateRoleDTO::fromArray($data);
        $updatedRole = $this->updateRoleUseCase->execute($id, $dto, true);

        return new JsonResponse($updatedRole->toArray(), JsonResponse::HTTP_OK);
    }

    #[Route('/roles/{id}', name: 'role_delete', methods: ['DELETE'])]
    public function delete(string $id, ?Request $request = null): JsonResponse
    {
        if ($request !== null) {
            $this->guard?->requireAdmin($request);
        }

        $this->deleteRoleUseCase->execute($id);

        return new JsonResponse(null, JsonResponse::HTTP_NO_CONTENT);
    }

    #[Route('/users/{userId}/roles', name: 'user_role_list', methods: ['GET'])]
    public function listUserRoles(string $userId, ?Request $request = null): JsonResponse
    {
        if ($request !== null) {
            $this->guard?->requireAdminOrSelf($request, $userId);
        }

        $roles = $this->getUserRolesUseCase->execute($userId);

        return new JsonResponse([
            'userId' => $userId,
            'roles' => $roles,
        ], JsonResponse::HTTP_OK);
    }

    #[Route('/users/{userId}/roles', name: 'user_role_assign', methods: ['POST'])]
    public function assignRole(string $userId, Request $request): JsonResponse
    {
        $this->guard?->requireAdmin($request);

        $data = $this->extractRequestData($request);
        $dto = AssignRoleDTO::fromArray($data);
        $result = $this->assignUserRoleUseCase->execute($userId, $dto);

        return new JsonResponse($result, JsonResponse::HTTP_OK);
    }

    #[Route('/users/{userId}/roles/{roleId}', name: 'user_role_assign_path', methods: ['POST'])]
    public function assignRoleByPath(string $userId, string $roleId, ?Request $request = null): JsonResponse
    {
        if ($request !== null) {
            $this->guard?->requireAdmin($request);
        }

        $dto = new AssignRoleDTO(roleId: $roleId);
        $result = $this->assignUserRoleUseCase->execute($userId, $dto);

        return new JsonResponse($result, JsonResponse::HTTP_OK);
    }

    #[Route('/users/{userId}/roles/{roleId}', name: 'user_role_unassign', methods: ['DELETE'])]
    public function unassignRole(string $userId, string $roleId, ?Request $request = null): JsonResponse
    {
        if ($request !== null) {
            $this->guard?->requireAdmin($request);
        }

        $this->unassignUserRoleUseCase->execute($userId, $roleId);

        return new JsonResponse(null, JsonResponse::HTTP_NO_CONTENT);
    }

    private function extractRequestData(Request $request): array
    {
        if ($request->request->count() > 0) {
            return $request->request->all();
        }

        $rawContent = $request->getContent();
        if (trim($rawContent) === '') {
            return [];
        }

        $decoded = json_decode($rawContent, true);
        return is_array($decoded) ? $decoded : [];
    }
}
