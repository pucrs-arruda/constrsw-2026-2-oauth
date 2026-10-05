import { Transform } from "class-transformer";

/**
 * Apara espaços antes de validar. Sem isso `"   "` passa por `@MinLength(1)` —
 * são três caracteres — e o usuário entra no realm com nome em branco.
 */
export function Trim(): PropertyDecorator {
  return Transform(({ value }) =>
    typeof value === "string" ? value.trim() : value,
  );
}
