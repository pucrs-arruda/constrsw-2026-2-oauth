<?php

declare(strict_types=1);

namespace App\Tests\Unit\Application\UseCase\Role;

use App\Application\UseCase\Role\GetUserRolesUseCase;
use App\Domain\Exception\UserNotFoundException;
use App\Domain\Exception\ValidationException;
use App\Domain\Port\Outbound\KeycloakRolePortInterface;
use PHPUnit\Framework\TestCase;

final class GetUserRolesUseCaseTest extends TestCase
{
    private KeycloakRolePortInterface $rolePort;
    private GetUserRolesUseCase $useCase;

    protected function setUp(): void
    {
        $this->rolePort = $this->createMock(KeycloakRolePortInterface::class);
        $this->useCase = new GetUserRolesUseCase($this->rolePort);
    }

    public function testExecuteSuccess(): void
    {
        $this->rolePort->expects($this->once())
            ->method('getUserRoles')
            ->with('user-123')
            ->willReturn(['administrator', 'professor']);

        $roles = $this->useCase->execute('user-123');

        $this->assertSame(['administrator', 'professor'], $roles);
    }

    public function testExecuteThrowsValidationExceptionWhenUserIdIsEmpty(): void
    {
        $this->expectException(ValidationException::class);
        $this->expectExceptionMessage('O ID do usuário é obrigatório.');

        $this->useCase->execute('   ');
    }

    public function testExecutePropagatesUserNotFoundException(): void
    {
        $this->rolePort->expects($this->once())
            ->method('getUserRoles')
            ->with('missing-user')
            ->willThrowException(new UserNotFoundException());

        $this->expectException(UserNotFoundException::class);

        $this->useCase->execute('missing-user');
    }
}
