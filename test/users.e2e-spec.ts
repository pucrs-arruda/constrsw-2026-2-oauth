import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { AppModule } from '../src/app.module';
import { OAuthExceptionFilter } from '../src/presentation/http/filters/oauth-exception.filter';
import { loginAs } from './utils/login';

/** Testes de integracao do CRUD de /users contra o Keycloak real. */
describe('Users (e2e)', () => {
  let app: INestApplication;
  let adminToken: string;
  let studentToken: string;
  let createdUserId: string | undefined;
  const uniqueSuffix = Date.now();
  const testEmail = `teste.integracao.${uniqueSuffix}@pucrs.br`;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleRef.createNestApplication();
    app.useGlobalPipes(
      new ValidationPipe({ whitelist: true, transform: true, forbidNonWhitelisted: false }),
    );
    app.useGlobalFilters(new OAuthExceptionFilter());
    await app.init();

    adminToken = await loginAs(app, 'admin@pucrs.br', 'a12345678');
    studentToken = await loginAs(app, 'student@pucrs.br', 'a12345678');
  });

  afterAll(async () => {
    // limpeza: garante que o usuario de teste fica desabilitado mesmo se
    // algum teste falhar antes do proprio teste de DELETE rodar
    if (createdUserId) {
      await request(app.getHttpServer())
        .delete(`/users/${createdUserId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .catch(() => undefined);
    }
    await app.close();
  });

  it('GET /users sem header Authorization retorna 400', async () => {
    const res = await request(app.getHttpServer()).get('/users');
    expect(res.status).toBe(400);
  });

  it('GET /users com token invalido/malformado retorna 401', async () => {
    const res = await request(app.getHttpServer())
      .get('/users')
      .set('Authorization', 'Bearer token-invalido-123');
    expect(res.status).toBe(401);
  });

  it('POST /users cria um usuario novo', async () => {
    const res = await request(app.getHttpServer())
      .post('/users')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        username: testEmail,
        password: 'SenhaTeste123!',
        'first-name': 'Teste',
        'last-name': 'Integracao',
      });

    expect(res.status).toBe(201);
    expect(res.body.username).toBe(testEmail);
    expect(res.body.enabled).toBe(true);
    expect(typeof res.body.id).toBe('string');
    createdUserId = res.body.id;
  });

  it('POST /users com o mesmo email retorna 409', async () => {
    const res = await request(app.getHttpServer())
      .post('/users')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        username: testEmail,
        password: 'outraSenha123',
        'first-name': 'Duplicado',
        'last-name': 'Teste',
      });

    expect(res.status).toBe(409);
  });

  it('POST /users com username que nao e um e-mail valido retorna 400', async () => {
    const res = await request(app.getHttpServer())
      .post('/users')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        username: 'nao-e-um-email',
        password: 'x',
        'first-name': 'Teste',
        'last-name': 'Teste',
      });

    expect(res.status).toBe(400);
  });

  it('POST /users com token de usuario sem permissao administrativa (student) retorna 403', async () => {
    const res = await request(app.getHttpServer())
      .post('/users')
      .set('Authorization', `Bearer ${studentToken}`)
      .send({
        username: `outro.${uniqueSuffix}@pucrs.br`,
        password: 'x',
        'first-name': 'Outro',
        'last-name': 'Teste',
      });

    expect(res.status).toBe(403);
  });

  it('GET /users lista usuarios e inclui o recem-criado', async () => {
    const res = await request(app.getHttpServer())
      .get('/users')
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(200);
    expect(res.body.some((u: { id: string }) => u.id === createdUserId)).toBe(true);
  });

  it('GET /users?enabled=true filtra somente usuarios habilitados', async () => {
    const res = await request(app.getHttpServer())
      .get('/users?enabled=true')
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(200);
    expect(
      (res.body as Array<{ enabled: boolean }>).every((u) => u.enabled === true),
    ).toBe(true);
  });

  it('GET /users/:id retorna o usuario criado', async () => {
    const res = await request(app.getHttpServer())
      .get(`/users/${createdUserId}`)
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(200);
    expect(res.body.id).toBe(createdUserId);
  });

  it('GET /users/:id com id inexistente retorna 404', async () => {
    const res = await request(app.getHttpServer())
      .get('/users/00000000-0000-0000-0000-000000000000')
      .set('Authorization', `Bearer ${adminToken}`);

    expect(res.status).toBe(404);
  });

  it('PUT /users/:id atualiza os atributos do usuario', async () => {
    const putRes = await request(app.getHttpServer())
      .put(`/users/${createdUserId}`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ 'first-name': 'Nome Atualizado' });
    expect(putRes.status).toBe(200);

    const check = await request(app.getHttpServer())
      .get(`/users/${createdUserId}`)
      .set('Authorization', `Bearer ${adminToken}`);
    expect(check.body['first-name']).toBe('Nome Atualizado');
  });

  it('PATCH /users/:id troca a senha do usuario', async () => {
    const res = await request(app.getHttpServer())
      .patch(`/users/${createdUserId}`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ password: 'NovaSenha456!' });

    expect(res.status).toBe(200);
  });

  it('DELETE /users/:id faz exclusao logica (usuario some das buscas com enabled=true)', async () => {
    const del = await request(app.getHttpServer())
      .delete(`/users/${createdUserId}`)
      .set('Authorization', `Bearer ${adminToken}`);
    expect(del.status).toBe(204);

    const check = await request(app.getHttpServer())
      .get(`/users/${createdUserId}`)
      .set('Authorization', `Bearer ${adminToken}`);
    expect(check.status).toBe(200);
    expect(check.body.enabled).toBe(false);
  });
});
