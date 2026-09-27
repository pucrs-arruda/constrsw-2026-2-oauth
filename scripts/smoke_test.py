#!/usr/bin/env python3
"""
Smoke test for the oauth microservice — hits every endpoint, in the correct
dependency order, against a running stack (docker compose up -d).

Zero dependencies beyond the Python 3 standard library (urllib/json), so it
runs anywhere Python 3 runs, no `pip install` needed before a demo.

Usage:
    python3 scripts/smoke_test.py
    python3 scripts/smoke_test.py --base-url http://localhost:8181 --delay 1.5
    python3 scripts/smoke_test.py --no-color --delay 0     # fast, CI-friendly
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass, field
from typing import Any, Optional


# --------------------------------------------------------------------------
# Terminal styling
# --------------------------------------------------------------------------

class Style:
    def __init__(self, enabled: bool):
        self.enabled = enabled

    def _wrap(self, code: str, text: str) -> str:
        return f"\033[{code}m{text}\033[0m" if self.enabled else text

    def bold(self, t: str) -> str: return self._wrap("1", t)
    def dim(self, t: str) -> str: return self._wrap("2", t)
    def green(self, t: str) -> str: return self._wrap("32", t)
    def red(self, t: str) -> str: return self._wrap("31", t)
    def yellow(self, t: str) -> str: return self._wrap("33", t)
    def cyan(self, t: str) -> str: return self._wrap("36", t)
    def magenta(self, t: str) -> str: return self._wrap("35", t)


# --------------------------------------------------------------------------
# HTTP helper (stdlib only)
# --------------------------------------------------------------------------

@dataclass
class Result:
    ok: bool
    status: Optional[int]
    elapsed_ms: float
    body: Any = None
    raw_text: str = ""
    content_type: str = ""
    error: Optional[str] = None


def http_call(method: str, url: str, *, token: Optional[str] = None,
              json_body: Optional[dict] = None, timeout: float = 10.0) -> Result:
    headers = {"Accept": "application/json"}
    data = None
    if json_body is not None:
        data = json.dumps(json_body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            status = resp.status
            raw = resp.read()
            content_type = resp.headers.get("Content-Type", "")
    except urllib.error.HTTPError as e:
        status = e.code
        raw = e.read()
        content_type = e.headers.get("Content-Type", "") if e.headers else ""
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        elapsed_ms = (time.perf_counter() - start) * 1000
        return Result(ok=False, status=None, elapsed_ms=elapsed_ms, error=str(e.reason if hasattr(e, "reason") else e))

    elapsed_ms = (time.perf_counter() - start) * 1000
    raw_text = raw.decode("utf-8", errors="replace")
    body = None
    if "application/json" in content_type:
        try:
            body = json.loads(raw_text) if raw_text.strip() else None
        except json.JSONDecodeError:
            body = None

    return Result(ok=True, status=status, elapsed_ms=elapsed_ms, body=body,
                   raw_text=raw_text, content_type=content_type)


# --------------------------------------------------------------------------
# Runner
# --------------------------------------------------------------------------

class Runner:
    def __init__(self, base_url: str, delay: float, s: Style):
        self.base_url = base_url.rstrip("/")
        self.delay = delay
        self.s = s
        self.total = 0
        self.passed = 0
        self.failed = 0
        self.summary: list[tuple[str, bool, str]] = []  # (label, ok, note)

    @staticmethod
    def _truncate_long_strings(value: Any, max_len: int = 48) -> Any:
        """Recursively shortens long string values (JWTs, tokens) so a terminal
        demo stays readable — full values are still real, just not dumped in full."""
        if isinstance(value, str):
            if len(value) > max_len:
                return f"{value[:max_len]}…[{len(value)} chars total]"
            return value
        if isinstance(value, list):
            return [Runner._truncate_long_strings(v, max_len) for v in value]
        if isinstance(value, dict):
            return {k: Runner._truncate_long_strings(v, max_len) for k, v in value.items()}
        return value

    def _print_body_preview(self, result: Result, max_lines: int = 14, max_list_items: int = 3) -> None:
        s = self.s
        if result.body is not None:
            preview = result.body
            note = None
            if isinstance(preview, list) and len(preview) > max_list_items:
                note = f"(+{len(preview) - max_list_items} more item(s), showing first {max_list_items})"
                preview = preview[:max_list_items]
            preview = self._truncate_long_strings(preview)
            text = json.dumps(preview, indent=2, ensure_ascii=False)
            lines = text.splitlines()
            if len(lines) > max_lines:
                lines = lines[:max_lines] + [s.dim(f"  … ({len(text.splitlines()) - max_lines} more lines)")]
            for line in lines:
                print(s.dim("   │ ") + line)
            if note:
                print(s.dim(f"   │ {note}"))
        elif result.raw_text.strip():
            content_kind = "text"
            if "text/plain" in result.content_type:
                content_kind = "prometheus exposition"
            elif "text/html" in result.content_type:
                content_kind = "html"
            lines = result.raw_text.splitlines()
            shown = lines[:max_lines]
            print(s.dim(f"   │ ({content_kind}, {len(result.raw_text)} bytes, first {len(shown)} of {len(lines)} lines)"))
            for line in shown:
                print(s.dim("   │ ") + line)
        else:
            print(s.dim("   │ (empty body)"))

    def step(self, method: str, path: str, *, title: str, token: Optional[str] = None,
              json_body: Optional[dict] = None, expected: tuple[int, ...] = (200,),
              note: str = "", show_body: bool = True) -> Result:
        self.total += 1
        s = self.s
        print()
        print(s.bold(s.cyan(f"[{self.total:02d}] {method} {path}")) + "  " + s.dim(title))
        if note:
            print(s.dim(f"     {note}"))

        result = http_call(method, f"{self.base_url}{path}", token=token, json_body=json_body)

        if not result.ok:
            print(s.red(f"   ✗ CONNECTION ERROR: {result.error}"))
            print(s.yellow("     Is the stack up? Try: docker compose up -d   (from the base repo root)"))
            self.failed += 1
            self.summary.append((f"{method} {path}", False, "connection error"))
            return result

        is_expected = result.status in expected
        status_label = f"HTTP {result.status}"
        timing = s.dim(f"{result.elapsed_ms:.0f}ms")
        if is_expected:
            print(s.green(f"   ✓ {status_label}") + f"  {timing}")
            self.passed += 1
        else:
            print(s.red(f"   ✗ {status_label}") + f"  {timing}  " + s.red(f"(expected {' or '.join(str(e) for e in expected)})"))
            self.failed += 1

        if show_body:
            self._print_body_preview(result)

        self.summary.append((f"{method} {path} — {title}", is_expected, status_label))

        if self.delay > 0:
            time.sleep(self.delay)

        return result

    def print_summary(self) -> None:
        s = self.s
        print()
        print(s.bold("═" * 72))
        print(s.bold(" SUMMARY"))
        print(s.bold("═" * 72))
        for label, ok, status in self.summary:
            mark = s.green("PASS") if ok else s.red("FAIL")
            print(f" [{mark}] {status:>9}  {label}")
        print(s.bold("─" * 72))
        total = self.passed + self.failed
        color = s.green if self.failed == 0 else s.red
        print(color(s.bold(f" {self.passed}/{total} checks passed")))
        print(s.bold("═" * 72))


# --------------------------------------------------------------------------
# Scenario: every endpoint, in dependency order
# --------------------------------------------------------------------------

SEED_USERS = {
    "administrator": ("admin@pucrs.br", "a12345678"),
    "coordinator": ("coordinator@pucrs.br", "a12345678"),
    "professor": ("professor@pucrs.br", "a12345678"),
    "student": ("student@pucrs.br", "a12345678"),
}


def run(base_url: str, delay: float, color: bool) -> int:
    s = Style(color)
    r = Runner(base_url, delay, s)
    run_id = uuid.uuid4().hex[:8]

    print(s.bold(s.magenta("┌" + "─" * 70 + "┐")))
    print(s.bold(s.magenta("│ oauth microservice — full endpoint smoke test" + " " * 23 + "│")))
    print(s.bold(s.magenta("└" + "─" * 70 + "┘")))
    print(s.dim(f" target: {base_url}  |  run id: {run_id}  |  delay between steps: {delay}s"))

    # 1-3. Health / root -----------------------------------------------------
    r.step("GET", "/", title="API root (alias of health)")
    r.step("GET", "/health", title="Container/orchestration healthcheck")
    r.step("GET", "/api/health", title="Public healthcheck alias")

    # 4-5. Authenticate -------------------------------------------------------
    username, password = SEED_USERS["administrator"]
    login = r.step("POST", "/login", title=f"Login as {username} (password grant)",
                    json_body={"username": username, "password": password})
    if login.status != 200 or not login.body:
        print(s.red("\nCannot continue without a valid login — aborting the rest of the scenario."))
        r.print_summary()
        return 1

    access_token = login.body["access_token"]
    refresh_token = login.body["refresh_token"]

    r.step("GET", "/me", title="Fetch the authenticated user's profile", token=access_token)

    # 6-10. Users --------------------------------------------------------------
    demo_email = f"smoke-{run_id}@pucrs.br"
    created_user = r.step(
        "POST", "/users", title=f"Create a throwaway demo user ({demo_email})", token=access_token,
        json_body={
            "username": demo_email, "email": demo_email,
            "firstName": "Smoke", "lastName": f"Test-{run_id}", "password": "a12345678",
        },
        expected=(201,),
    )
    user_id = (created_user.body or {}).get("id")

    r.step("GET", "/users", title="List active users", token=access_token)

    if user_id:
        r.step("GET", f"/users/{user_id}", title="Fetch the demo user by id", token=access_token)
        r.step("PUT", f"/users/{user_id}", title="Update the demo user's attributes", token=access_token,
               json_body={"firstName": "Smoke", "lastName": "Updated", "email": demo_email})
        r.step("PATCH", f"/users/{user_id}", title="Update the demo user's password", token=access_token,
               json_body={"password": "a1234599"})
    else:
        print(s.yellow("     (no user id returned — skipping id-scoped user checks)"))

    # 11-15. Roles ---------------------------------------------------------------
    role_name = f"smoke-role-{run_id}"
    created_role = r.step(
        "POST", "/roles", title=f"Create a throwaway demo role ({role_name})", token=access_token,
        json_body={"name": role_name, "description": "Created by smoke_test.py"},
        expected=(201,),
    )
    role_id = (created_role.body or {}).get("id")

    r.step("GET", "/roles", title="List roles", token=access_token)

    if role_id:
        r.step("GET", f"/roles/{role_id}", title="Fetch the demo role by id", token=access_token)
        r.step("PUT", f"/roles/{role_id}", title="Full update of the demo role", token=access_token,
               json_body={"name": role_name, "description": "Updated via PUT (full update)"})
        r.step("PATCH", f"/roles/{role_id}", title="Partial update of the demo role", token=access_token,
               json_body={"description": "Updated via PATCH (partial update)"})

    # 16. Assign the role to the demo user -----------------------------------
    if user_id and role_id:
        r.step("POST", f"/users/{user_id}/roles", title="Assign the demo role to the demo user",
               token=access_token, json_body={"roleId": role_id})

    # 17-18. Authorization / policy engine ------------------------------------
    r.step("POST", "/authorize", title="administrator requests 'rooms' — should be granted",
           token=access_token, json_body={"resource": "rooms"}, expected=(200,))
    r.step("POST", "/authorize", title="administrator requests 'courses' — outside the matrix, should be denied",
           token=access_token, json_body={"resource": "courses"}, expected=(403,),
           note="This 403 is the expected, correct response — not a failure of the API.")

    # 19. Refresh the session --------------------------------------------------
    refreshed = r.step("POST", "/refresh", title="Renew the session using the refresh token",
                        json_body={"refresh_token": refresh_token})
    if refreshed.status == 200 and refreshed.body:
        access_token = refreshed.body["access_token"]

    # 20-22. Cleanup: unassign + soft-delete -----------------------------------
    if user_id and role_id:
        r.step("DELETE", f"/users/{user_id}/roles/{role_id}", title="Unassign the demo role from the demo user",
               token=access_token, expected=(204,))
    if role_id:
        r.step("DELETE", f"/roles/{role_id}", title="Soft-delete the demo role", token=access_token,
               expected=(204,))
    if user_id:
        r.step("DELETE", f"/users/{user_id}", title="Soft-delete the demo user (enabled=false, not a hard delete)",
               token=access_token, expected=(204,))

    # 23-25. Observability & docs ------------------------------------------------
    r.step("GET", "/metrics", title="Prometheus exposition — request counters & durations")
    r.step("GET", "/docs", title="Swagger UI (HTML)")
    r.step("GET", "/docs/openapi.json", title="Code-generated OpenAPI spec")

    r.print_summary()
    return 0 if r.failed == 0 else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default="http://localhost:8181",
                         help="Base URL of the oauth API (default: %(default)s)")
    parser.add_argument("--delay", type=float, default=1.2,
                         help="Seconds to pause between steps, so a live audience can follow along (default: %(default)s)")
    parser.add_argument("--no-color", action="store_true", help="Disable ANSI colors (e.g. when piping to a file)")
    args = parser.parse_args()

    color = (not args.no_color) and sys.stdout.isatty()
    return run(args.base_url, args.delay, color)


if __name__ == "__main__":
    sys.exit(main())
