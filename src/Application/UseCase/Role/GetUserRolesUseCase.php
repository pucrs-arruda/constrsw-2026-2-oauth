<?php

declare(strict_types=1);

namespace App\Application\UseCase\Role;

use App\Domain\Exception\ValidationException;
use App\Domain\Port\Inbound\GetUserRolesUseCaseInterface;
use App\Domain\Port\Outbound\KeycloakRolePortInterface;

final class GetUserRolesUseCase implements GetUserRolesUseCaseInterface
{
    public function __construct(
        private readonly KeycloakRolePortInterface $rolePort
    ) {
    }

    /**
     * @return string[]
     */
    public function execute(string $userId): array
    {
        if (trim($userId) === '') {
            throw new ValidationException('O ID do usuário é obrigatório.', ['userId' => 'O ID do usuário é obrigatório.']);
        }

        return $this->rolePort->getUserRoles($userId);
    }
}
