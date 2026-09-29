<?php

declare(strict_types=1);

namespace App\Tests\Unit\Infrastructure\Http\Controller;

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
use App\Infrastructure\Http\Controller\UserController;
use PHPUnit\Framework\TestCase;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;

final class UserControllerTest extends TestCase
{
    private CreateUserUseCaseInterface $createUserUseCase;
    private ListUsersUseCaseInterface $listUsersUseCase;
    private GetUserByIdUseCaseInterface $getUserByIdUseCase;
    private UpdateUserUseCaseInterface $updateUserUseCase;
    private UpdatePasswordUseCaseInterface $updatePasswordUseCase;
    private DisableUserUseCaseInterface $disableUserUseCase;
    private UserController $controller;

    protected function setUp(): void
    {
        $this->createUserUseCase = $this->createMock(CreateUserUseCaseInterface::class);
        $this->listUsersUseCase = $this->createMock(ListUsersUseCaseInterface::class);
        $this->getUserByIdUseCase = $this->createMock(GetUserByIdUseCaseInterface::class);
        $this->updateUserUseCase = $this->createMock(UpdateUserUseCaseInterface::class);
        $this->updatePasswordUseCase = $this->createMock(UpdatePasswordUseCaseInterface::class);
        $this->disableUserUseCase = $this->createMock(DisableUserUseCaseInterface::class);

        $this->controller = new UserController(
            $this->createUserUseCase,
            $this->listUsersUseCase,
            $this->getUserByIdUseCase,
            $this->updateUserUseCase,
            $this->updatePasswordUseCase,
            $this->disableUserUseCase
        );
    }

    public function testCreateReturns201WithUserJson(): void
    {
        $userDTO = new UserDTO(
            id: 'uuid-123',
            username: 'maria@example.com',
            email: 'maria@example.com',
            firstName: 'Maria',
            lastName: 'Silva',
            enabled: true,
            roles: ['student']
        );

        $this->createUserUseCase
            ->expects($this->once())
            ->method('execute')
            ->with($this->callback(function (CreateUserDTO $dto) {
                return $dto->email === 'maria@example.com' && $dto->firstName === 'Maria';
            }))
            ->willReturn($userDTO);

        $request = new Request(
            content: json_encode([
                'email' => 'maria@example.com',
                'firstName' => 'Maria',
                'lastName' => 'Silva',
                'password' => 'secret123',
            ], JSON_THROW_ON_ERROR)
        );

        $response = $this->controller->create($request);

        $this->assertSame(Response::HTTP_CREATED, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('uuid-123', $data['id']);
        $this->assertSame('maria@example.com', $data['email']);
    }

    public function testListReturns200WithUsersArray(): void
    {
        $users = [
            new UserDTO('1', 'u1@test.com', 'u1@test.com', 'U', 'One', true, []),
            new UserDTO('2', 'u2@test.com', 'u2@test.com', 'U', 'Two', true, []),
        ];

        $this->listUsersUseCase
            ->expects($this->once())
            ->method('execute')
            ->willReturn($users);

        $response = $this->controller->list();

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertCount(2, $data);
        $this->assertSame('1', $data[0]['id']);
        $this->assertSame('2', $data[1]['id']);
    }

    public function testGetReturns200WithUserJson(): void
    {
        $userDTO = new UserDTO('target-uuid', 'target@test.com', 'target@test.com', 'Target', 'User', true, []);

        $this->getUserByIdUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('target-uuid')
            ->willReturn($userDTO);

        $response = $this->controller->get('target-uuid');

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $data = json_decode((string) $response->getContent(), true);
        $this->assertSame('target-uuid', $data['id']);
    }

    public function testUpdateReturns200WithEmptyBody(): void
    {
        $this->updateUserUseCase
            ->expects($this->once())
            ->method('execute')
            ->with(
                'uuid-1',
                $this->callback(function (UpdateUserDTO $dto) {
                    return $dto->firstName === 'UpdatedName';
                })
            );

        $request = new Request(
            content: json_encode(['firstName' => 'UpdatedName'], JSON_THROW_ON_ERROR)
        );

        $response = $this->controller->update('uuid-1', $request);

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $this->assertSame('', (string) $response->getContent());
    }

    public function testPasswordReturns200WithEmptyBody(): void
    {
        $this->updatePasswordUseCase
            ->expects($this->once())
            ->method('execute')
            ->with(
                'uuid-1',
                $this->callback(function (UpdatePasswordDTO $dto) {
                    return $dto->password === 'newSecretPassword!';
                })
            );

        $request = new Request(
            content: json_encode(['password' => 'newSecretPassword!'], JSON_THROW_ON_ERROR)
        );

        $response = $this->controller->password('uuid-1', $request);

        $this->assertSame(Response::HTTP_OK, $response->getStatusCode());
        $this->assertSame('', (string) $response->getContent());
    }

    public function testDeleteReturns204WithEmptyBody(): void
    {
        $this->disableUserUseCase
            ->expects($this->once())
            ->method('execute')
            ->with('uuid-delete');

        $response = $this->controller->delete('uuid-delete');

        $this->assertSame(Response::HTTP_NO_CONTENT, $response->getStatusCode());
        $this->assertSame('', (string) $response->getContent());
    }

    public function testCreateThrowsWhenGuardRejects(): void
    {
        $guard = $this->createMock(\App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface::class);
        $guard->expects($this->once())
            ->method('requireAdmin')
            ->willThrowException(new \App\Domain\Exception\AccessDeniedException('Acesso negado'));

        $controller = new UserController(
            $this->createUserUseCase,
            $this->listUsersUseCase,
            $this->getUserByIdUseCase,
            $this->updateUserUseCase,
            $this->updatePasswordUseCase,
            $this->disableUserUseCase,
            $guard
        );

        $request = new Request(content: json_encode([
            'email' => 'test@pucrs.br',
            'firstName' => 'A',
            'lastName' => 'B',
            'password' => 'secret',
        ], JSON_THROW_ON_ERROR));

        $this->expectException(\App\Domain\Exception\AccessDeniedException::class);
        $controller->create($request);
    }

    public function testUpdateThrowsWhenGuardRejects(): void
    {
        $guard = $this->createMock(\App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface::class);
        $guard->expects($this->once())
            ->method('requireAdminOrSelf')
            ->with($this->isInstanceOf(Request::class), 'other-user')
            ->willThrowException(new \App\Domain\Exception\AccessDeniedException('Acesso negado'));

        $controller = new UserController(
            $this->createUserUseCase,
            $this->listUsersUseCase,
            $this->getUserByIdUseCase,
            $this->updateUserUseCase,
            $this->updatePasswordUseCase,
            $this->disableUserUseCase,
            $guard
        );

        $request = new Request(content: json_encode(['firstName' => 'X'], JSON_THROW_ON_ERROR));

        $this->expectException(\App\Domain\Exception\AccessDeniedException::class);
        $controller->update('other-user', $request);
    }

    public function testPasswordThrowsWhenGuardRejects(): void
    {
        $guard = $this->createMock(\App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface::class);
        $guard->expects($this->once())
            ->method('requireAdminOrSelf')
            ->with($this->isInstanceOf(Request::class), 'other-user')
            ->willThrowException(new \App\Domain\Exception\AccessDeniedException('Acesso negado'));

        $controller = new UserController(
            $this->createUserUseCase,
            $this->listUsersUseCase,
            $this->getUserByIdUseCase,
            $this->updateUserUseCase,
            $this->updatePasswordUseCase,
            $this->disableUserUseCase,
            $guard
        );

        $request = new Request(content: json_encode(['password' => 'new123'], JSON_THROW_ON_ERROR));

        $this->expectException(\App\Domain\Exception\AccessDeniedException::class);
        $controller->password('other-user', $request);
    }

    public function testDeleteThrowsWhenGuardRejects(): void
    {
        $guard = $this->createMock(\App\Infrastructure\Http\Security\AdminAuthorizationGuardInterface::class);
        $guard->expects($this->once())
            ->method('requireAdmin')
            ->willThrowException(new \App\Domain\Exception\AccessDeniedException('Acesso negado'));

        $controller = new UserController(
            $this->createUserUseCase,
            $this->listUsersUseCase,
            $this->getUserByIdUseCase,
            $this->updateUserUseCase,
            $this->updatePasswordUseCase,
            $this->disableUserUseCase,
            $guard
        );

        $request = new Request();

        $this->expectException(\App\Domain\Exception\AccessDeniedException::class);
        $controller->delete('other-user', $request);
    }
}
