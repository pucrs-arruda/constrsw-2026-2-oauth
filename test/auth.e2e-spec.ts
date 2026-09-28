import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { AppModule } from '../src/app.module';
import { OAuthExceptionFilter } from '../src/presentation/http/filters/oauth-exception.filter';

/**
 * Testes de INTEGRACAO: sobem a aplicacao Nest de verdade (mesmos guards,
 * pipes e filtros globais do main.ts) e batem via HTTP real contra ela, que
 * por sua vez fala com o Keycloak de verdade (nao ha mock aqui - e isso que
 * torna esses testes "de integracao", diferente dos unitarios em src/**).
 *
 * Pre-requisito: `docker compose up -d` rodando na raiz do repo base.
 */
describe('Auth (e2e)', () => {
  let app: INestApplication;

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
  });

  afterAll(async () => {
    await app.close();
  });

  it('POST /login com credenciais validas retorna 201 e um access_token', async () => {
    const res = await request(app.getHttpServer())
      .post('/login')
      .field('username', 'admin@pucrs.br')
      .field('password', 'a12345678');

    expect(res.status).toBe(201);
    expect(res.body.token_type).toBe('Bearer');
    expect(typeof res.body.access_token).toBe('string');
    expect(res.body.access_token.length).toBeGreaterThan(0);
  });

  it('POST /login com senha errada retorna 401 no formato padrao de erro do T1', async () => {
    const res = await request(app.getHttpServer())
      .post('/login')
      .field('username', 'admin@pucrs.br')
      .field('password', 'senha-errada');

    expect(res.status).toBe(401);
    expect(res.body).toMatchObject({
      error_code: '401',
      error_description: 'username e/ou password invalidos.',
      error_source: 'OAuthAPI',
    });
    expect(Array.isArray(res.body.error_stack)).toBe(true);
  });

  it('POST /login com usuario inexistente tambem retorna 401', async () => {
    const res = await request(app.getHttpServer())
      .post('/login')
      .field('username', 'naoexiste@pucrs.br')
      .field('password', 'a12345678');

    expect(res.status).toBe(401);
  });

  it('POST /login sem username/password retorna 400', async () => {
    const res = await request(app.getHttpServer()).post('/login');

    expect(res.status).toBe(400);
    expect(res.body.error_code).toBe('400');
  });

  it('login funciona para os 4 usuarios de teste do realm oficial', async () => {
    const usuarios = [
      'admin@pucrs.br',
      'coordinator@pucrs.br',
      'professor@pucrs.br',
      'student@pucrs.br',
    ];

    for (const username of usuarios) {
      const res = await request(app.getHttpServer())
        .post('/login')
        .field('username', username)
        .field('password', 'a12345678');

      expect(res.status).toBe(201);
    }
  });
});
