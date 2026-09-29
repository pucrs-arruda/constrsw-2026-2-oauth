<?php

declare(strict_types=1);

namespace App\Infrastructure\Http\Controller;

use App\Application\DTO\User\CreateUserDTO;
use App\Application\DTO\User\UpdatePasswordDTO;
use App\Application\DTO\User\UpdateUserDTO;
use App\Application\DTO\User\UserDTO;
use App\Domain\Port\Inbound\CreateUserUseCaseInterface;
use App\Domain\Port\Inbound\DisableUserUseCaseInterface;
use App\Domain\Port\Inbound\GetUserByIdUseCaseInterface;
use App\Domain\Port\Inbound\ListUsersUseCaseInterface;
use App\Domain\Port\Inbound\UpdatePasswordUseCaseInterface;
use App\Domain\Port\Inbound\UpdateUserUseCaseInterface;
use App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

final class UserController extends AbstractController
{
    public function __construct(
        private readonly CreateUserUseCaseInterface $createUserUseCase,
        private readonly ListUsersUseCaseInterface $listUsersUseCase,
        private readonly GetUserByIdUseCaseInterface $getUserByIdUseCase,
        private readonly UpdateUserUseCaseInterface $updateUserUseCase,
        private readonly UpdatePasswordUseCaseInterface $updatePasswordUseCase,
        private readonly DisableUserUseCaseInterface $disableUserUseCase,
        private readonly ?AdminAuthorizationGuardInterface $guard = null
    ) {
    }

    #[Route('/users', name: 'user_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $this->guard?->requireAdmin($request);

        $data = $this->extractRequestData($request);
        $dto = CreateUserDTO::fromArray($data);
        $createdUser = $this->createUserUseCase->execute($dto);

        return new JsonResponse($createdUser->toArray(), JsonResponse::HTTP_CREATED);
    }

    #[Route('/users', name: 'user_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        $users = $this->listUsersUseCase->execute();
        $payload = array_map(static fn (UserDTO $user) => $user->toArray(), $users);

        return new JsonResponse($payload, JsonResponse::HTTP_OK);
    }

    #[Route('/users/{id}', name: 'user_get', methods: ['GET'])]
    public function get(string $id): JsonResponse
    {
        $user = $this->getUserByIdUseCase->execute($id);

        return new JsonResponse($user->toArray(), JsonResponse::HTTP_OK);
    }

    #[Route('/users/{id}', name: 'user_update', methods: ['PUT'])]
    public function update(string $id, Request $request): Response
    {
        $this->guard?->requireAdminOrSelf($request, $id);

        $data = $this->extractRequestData($request);
        $dto = UpdateUserDTO::fromArray($data);
        $this->updateUserUseCase->execute($id, $dto);

        return new Response('', Response::HTTP_OK, ['Content-Type' => 'application/json']);
    }

    #[Route('/users/{id}', name: 'user_password', methods: ['PATCH'])]
    public function password(string $id, Request $request): Response
    {
        $this->guard?->requireAdminOrSelf($request, $id);

        $data = $this->extractRequestData($request);
        $dto = UpdatePasswordDTO::fromArray($data);
        $this->updatePasswordUseCase->execute($id, $dto);

        return new Response('', Response::HTTP_OK, ['Content-Type' => 'application/json']);
    }

    #[Route('/users/{id}', name: 'user_delete', methods: ['DELETE'])]
    public function delete(string $id, ?Request $request = null): Response
    {
        if ($request !== null) {
            $this->guard?->requireAdmin($request);
        }

        $this->disableUserUseCase->execute($id);

        return new Response('', Response::HTTP_NO_CONTENT);
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
