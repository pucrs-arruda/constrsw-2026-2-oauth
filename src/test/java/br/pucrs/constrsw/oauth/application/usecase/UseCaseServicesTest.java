package br.pucrs.constrsw.oauth.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.pucrs.constrsw.oauth.application.port.out.AuthGateway;
import br.pucrs.constrsw.oauth.application.port.out.RoleGateway;
import br.pucrs.constrsw.oauth.application.port.out.UserGateway;
import br.pucrs.constrsw.oauth.domain.exception.InvalidEmailException;
import br.pucrs.constrsw.oauth.domain.exception.InvalidInputException;
import br.pucrs.constrsw.oauth.domain.model.AuthTokens;
import br.pucrs.constrsw.oauth.domain.model.Credentials;
import br.pucrs.constrsw.oauth.domain.model.NewRole;
import br.pucrs.constrsw.oauth.domain.model.NewUser;
import br.pucrs.constrsw.oauth.domain.model.Role;
import br.pucrs.constrsw.oauth.domain.model.RoleUpdate;
import br.pucrs.constrsw.oauth.domain.model.User;
import br.pucrs.constrsw.oauth.domain.model.UserUpdate;

@ExtendWith(MockitoExtension.class)
class UseCaseServicesTest {

  @Mock
  private UserGateway userGateway;

  @Mock
  private RoleGateway roleGateway;

  @Mock
  private AuthGateway authGateway;

  @Test
  void createsUserAfterValidatingEmail() {
    NewUser input = new NewUser("ana@example.com", "secret", "Ana", "Silva");
    User created = new User("u1", input.getUsername(), input.getFirstName(), input.getLastName(), true);
    when(userGateway.create("Bearer token", input)).thenReturn(created);

    User result = new CreateUserService(userGateway).execute("Bearer token", input);

    assertEquals(created, result);
    verify(userGateway).create("Bearer token", input);
  }

  @Test
  void rejectsInvalidUserEmailBeforeGatewayCall() {
    NewUser input = new NewUser("invalid-email", "secret", "Ana", "Silva");

    assertThrows(InvalidEmailException.class, () -> new CreateUserService(userGateway).execute("token", input));
    verifyNoInteractions(userGateway);
  }

  @Test
  void delegatesUserQueriesAndMutations() {
    User user = new User("u1", "ana@example.com", "Ana", "Silva", true);
    UserUpdate update = new UserUpdate("ana@example.com", "Ana", "Santos", true);
    when(userGateway.list("token", true)).thenReturn(List.of(user));
    when(userGateway.findById("token", "u1")).thenReturn(user);

    assertEquals(List.of(user), new ListUsersService(userGateway).execute("token", true));
    assertEquals(user, new GetUserService(userGateway).execute("token", "u1"));
    new UpdateUserService(userGateway).execute("token", "u1", update);
    new UpdatePasswordService(userGateway).execute("token", "u1", "new-secret");
    new DisableUserService(userGateway).execute("token", "u1");

    verify(userGateway).update("token", "u1", update);
    verify(userGateway).updatePassword("token", "u1", "new-secret");
    verify(userGateway).disable("token", "u1");
  }

  @Test
  void rejectsEmptyOrInvalidUserUpdates() {
    UpdateUserService service = new UpdateUserService(userGateway);

    assertThrows(InvalidInputException.class,
        () -> service.execute("token", "u1", new UserUpdate(null, null, null, null)));
    assertThrows(InvalidEmailException.class,
        () -> service.execute("token", "u1", new UserUpdate("bad", null, null, null)));
    verifyNoInteractions(userGateway);
  }

  @Test
  void rejectsBlankPasswordBeforeUpdating() {
    assertThrows(InvalidInputException.class,
        () -> new UpdatePasswordService(userGateway).execute("token", "u1", " "));
    verifyNoInteractions(userGateway);
  }

  @Test
  void delegatesRoleCrudAndAssignments() {
    NewRole newRole = new NewRole("teacher", "Teaching role");
    Role role = new Role("r1", "teacher", "Teaching role", true);
    RoleUpdate update = new RoleUpdate("teacher", "Updated", true);
    when(roleGateway.create("token", newRole)).thenReturn(role);
    when(roleGateway.list("token", false)).thenReturn(List.of(role));
    when(roleGateway.findById("token", "r1")).thenReturn(role);

    assertEquals(role, new CreateRoleService(roleGateway).execute("token", newRole));
    assertEquals(List.of(role), new ListRolesService(roleGateway).execute("token", false));
    assertEquals(role, new GetRoleService(roleGateway).execute("token", "r1"));
    new UpdateRoleService(roleGateway).execute("token", "r1", update);
    new DeleteRoleService(roleGateway).execute("token", "r1");
    new AttachRoleToUserService(roleGateway).execute("token", "u1", "r1");
    new DetachRoleFromUserService(roleGateway).execute("token", "u1", "r1");

    verify(roleGateway).update("token", "r1", update);
    verify(roleGateway).delete("token", "r1");
    verify(roleGateway).assignToUser("token", "u1", "r1");
    verify(roleGateway).unassignFromUser("token", "u1", "r1");
  }

  @Test
  void replaceRoleSendsWholeRoleDefaultingEnabledToTrue() {
    new ReplaceRoleService(roleGateway).execute("token", "r1", new RoleUpdate("teacher", null, null));

    org.mockito.ArgumentCaptor<RoleUpdate> sent = org.mockito.ArgumentCaptor.forClass(RoleUpdate.class);
    verify(roleGateway).replace(org.mockito.ArgumentMatchers.eq("token"), org.mockito.ArgumentMatchers.eq("r1"),
        sent.capture());
    assertEquals("teacher", sent.getValue().getName());
    assertEquals(null, sent.getValue().getDescription());
    assertEquals(Boolean.TRUE, sent.getValue().getEnabled());
  }

  @Test
  void replaceRoleRequiresName() {
    assertThrows(InvalidInputException.class,
        () -> new ReplaceRoleService(roleGateway).execute("token", "r1", new RoleUpdate(null, "d", true)));
    assertThrows(InvalidInputException.class,
        () -> new ReplaceRoleService(roleGateway).execute("token", "r1", new RoleUpdate(" ", "d", true)));
    verifyNoInteractions(roleGateway);
  }

  @Test
  void rejectsInvalidRoleInputBeforeGatewayCall() {
    assertThrows(InvalidInputException.class,
        () -> new CreateRoleService(roleGateway).execute("token", new NewRole(" ", "description")));
    assertThrows(InvalidInputException.class,
        () -> new UpdateRoleService(roleGateway).execute("token", "r1", new RoleUpdate(null, null, null)));
    verifyNoInteractions(roleGateway);
  }

  @Test
  void authenticatesCredentialsAndValidatesInput() {
    Credentials credentials = new Credentials("ana@example.com", "secret");
    AuthTokens tokens = new AuthTokens("Bearer", "access", 300L, "refresh", 1800L);
    when(authGateway.authenticate(credentials)).thenReturn(tokens);

    assertEquals(tokens, new LoginService(authGateway).execute(credentials));
    verify(authGateway).authenticate(credentials);

    assertThrows(InvalidInputException.class,
        () -> new LoginService(authGateway).execute(new Credentials("", "secret")));
    assertThrows(InvalidInputException.class,
        () -> new LoginService(authGateway).execute(new Credentials("ana@example.com", " ")));
  }
}
