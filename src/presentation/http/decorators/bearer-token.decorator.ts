import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { Request } from 'express';

export const BearerToken = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): string => {
    const request = ctx
      .switchToHttp()
      .getRequest<Request & { bearerToken: string }>();
    return request.bearerToken;
  },
);
