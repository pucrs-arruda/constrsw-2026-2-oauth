#!/usr/bin/env python3
"""
Smoke test for the oauth microservice — hits every endpoint, in the correct
dependency order, against a running stack (docker compose up -d).

Tests BOTH happy paths (200, 201, 204) and failure modes (400, 401, 403, 404, 409):
- Validation errors (400)
- Bad credentials & invalid/expired tokens (401)
- Non-admin RBAC restrictions & forbidden policies (403)
- Non-existent resource lookups (404)
- Uniqueness and conflict rejection (409)

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

    def section(self, title: str) -> None:
        s = self.s
        print()
        print(s.bold(s.magenta("═" * 72)))
        print(s.bold(s.magenta(f"  ► {title}")))
        print(s.bold(s.magenta("═" * 72)))

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
# Scenario: every endpoint, both happy paths and error cases
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
    print(s.bold(s.magenta("│ oauth microservice — full endpoint smoke test (happy & error paths) │")))
    print(s.bold(s.magenta("└" + "─" * 70 + "┘")))
    print(s.dim(f" target: {base_url}  |  run id: {run_id}  |  delay between steps: {delay}s"))

    # =========================================================================
    # Section 1: Health & Connectivity Checks
    # =========================================================================
    r.section("1. Health & Connectivity Checks")
    r.step("GET", "/", title="API root (alias of health)")
    r.step("GET", "/health", title="Container/orchestration healthcheck")
    r.step("GET", "/api/health", title="Public healthcheck alias")

    # =========================================================================
    # Section 2: Authentication & Failure Modes (401)
    # =========================================================================
    r.section("2. Authentication & Failure Modes")

    # 2.1 Negative: Login with invalid password -> 401 INVALID_CREDENTIALS
    r.step("POST", "/login", title="Negative: Login with invalid password (expect 401)",
           json_body={"username": "admin@pucrs.br", "password": "wrong_password"},
           expected=(401,),
           note="Expected 401 INVALID_CREDENTIALS — security check")

    # 2.2 Positive: Login as administrator -> 200
    username, password = SEED_USERS["administrator"]
    login = r.step("POST", "/login", title=f"Login as {username} (administrator)",
                   json_body={"username": username, "password": password})
    if login.status != 200 or not login.body:
        print(s.red("\nCannot continue without a valid login — aborting the rest of the scenario."))
        r.print_summary()
        return 1

    access_token = login.body["access_token"]
    refresh_token = login.body["refresh_token"]

    # 2.3 Positive: Fetch authenticated user's profile (/me) -> 200
    r.step("GET", "/me", title="Fetch the authenticated admin profile", token=access_token)

    # 2.4 Negative: Fetch profile with invalid/malformed token -> 401 INVALID_TOKEN
    r.step("GET", "/me", title="Negative: Fetch profile with invalid token (expect 401)",
           token="invalid-token-string", expected=(401,),
           note="Expected 401 INVALID_TOKEN — security validation")

    # =========================================================================
    # Section 3: Users Management: Happy Path, Validation (400) & Conflict (409)
    # =========================================================================
    r.section("3. User Management, Validation & Conflict Errors")

    # 3.1 Negative: Create user with empty payload -> 400 VALIDATION_ERROR
    r.step("POST", "/users", title="Negative: Create user with empty body (expect 400)",
           token=access_token, json_body={}, expected=(400,),
           note="Expected 400 VALIDATION_ERROR for missing required fields")

    # 3.2 Positive: Create throwaway demo user -> 201
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

    # 3.3 Negative: Create duplicate user with same email -> 409 USER_ALREADY_EXISTS
    r.step("POST", "/users", title="Negative: Attempt duplicate email creation (expect 409)",
           token=access_token,
           json_body={
               "username": demo_email, "email": demo_email,
               "firstName": "Duplicate", "lastName": "Attempt", "password": "a12345678",
           },
           expected=(409,),
           note="Expected 409 USER_ALREADY_EXISTS — uniqueness enforcement")

    # 3.4 Positive: List active users -> 200
    r.step("GET", "/users", title="List active users", token=access_token)

    # 3.5 Negative: Fetch user by non-existent UUID -> 404 USER_NOT_FOUND
    r.step("GET", "/users/00000000-0000-0000-0000-000000000000",
           title="Negative: Fetch non-existent user by UUID (expect 404)",
           token=access_token, expected=(404,),
           note="Expected 404 USER_NOT_FOUND")

    # 3.6 Positive: Fetch existing demo user by id -> 200
    if user_id:
        r.step("GET", f"/users/{user_id}", title="Fetch the demo user by id", token=access_token)
        r.step("PUT", f"/users/{user_id}", title="Update the demo user's attributes", token=access_token,
               json_body={"firstName": "Smoke", "lastName": "Updated", "email": demo_email})
        r.step("PATCH", f"/users/{user_id}", title="Update the demo user's password", token=access_token,
               json_body={"password": "a1234599"})
    else:
        print(s.yellow("     (no user id returned — skipping id-scoped user checks)"))

    # =========================================================================
    # Section 4: Roles Management: Happy Path, Validation (400) & Conflict (409)
    # =========================================================================
    r.section("4. Role Management, Validation & Conflict Errors")

    # 4.1 Negative: Create role with empty/blank name -> 400 VALIDATION_ERROR
    r.step("POST", "/roles", title="Negative: Create role with blank name (expect 400)",
           token=access_token, json_body={"name": "   "}, expected=(400,),
           note="Expected 400 VALIDATION_ERROR for empty role name")

    # 4.2 Positive: Create throwaway demo role -> 201
    role_name = f"smoke-role-{run_id}"
    created_role = r.step(
        "POST", "/roles", title=f"Create a throwaway demo role ({role_name})", token=access_token,
        json_body={"name": role_name, "description": "Created by smoke_test.py"},
        expected=(201,),
    )
    role_id = (created_role.body or {}).get("id")

    # 4.3 Negative: Create duplicate role name -> 409 ROLE_ALREADY_EXISTS
    r.step("POST", "/roles", title="Negative: Attempt duplicate role name (expect 409)",
           token=access_token, json_body={"name": role_name, "description": "Duplicate attempt"},
           expected=(409,),
           note="Expected 409 ROLE_ALREADY_EXISTS — uniqueness enforcement")

    # 4.4 Positive: List roles -> 200
    r.step("GET", "/roles", title="List roles", token=access_token)

    # 4.5 Negative: Fetch role by non-existent UUID -> 404 ROLE_NOT_FOUND
    r.step("GET", "/roles/00000000-0000-0000-0000-000000000000",
           title="Negative: Fetch non-existent role by UUID (expect 404)",
           token=access_token, expected=(404,),
           note="Expected 404 ROLE_NOT_FOUND")

    if role_id:
        r.step("GET", f"/roles/{role_id}", title="Fetch the demo role by id", token=access_token)
        r.step("PUT", f"/roles/{role_id}", title="Full update of the demo role", token=access_token,
               json_body={"name": role_name, "description": "Updated via PUT (full update)"})
        r.step("PATCH", f"/roles/{role_id}", title="Partial update of the demo role", token=access_token,
               json_body={"description": "Updated via PATCH (partial update)"})

    # =========================================================================
    # Section 5: Role Assignment & Inspection
    # =========================================================================
    r.section("5. Role Assignment & Inspection")
    if user_id and role_id:
        # Positive: Assign role as admin -> 200
        r.step("POST", f"/users/{user_id}/roles", title="Assign the demo role to the demo user",
               token=access_token, json_body={"roleId": role_id})
        # Positive: Inspect assigned roles -> 200
        r.step("GET", f"/users/{user_id}/roles", title="Inspect the demo user's assigned roles",
               token=access_token)

    # =========================================================================
    # Section 6: RBAC Policy Engine Authorization (/authorize)
    # =========================================================================
    r.section("6. Institutional Policy Matrix Authorization")
    r.step("POST", "/authorize", title="administrator requests 'rooms' — should be granted",
           token=access_token, json_body={"resource": "rooms"}, expected=(200,))
    r.step("POST", "/authorize", title="Negative: administrator requests 'courses' — outside matrix (expect 403)",
           token=access_token, json_body={"resource": "courses"}, expected=(403,),
           note="Expected 403 ACCESS_DENIED — resource outside role matrix")

    # =========================================================================
    # Section 7: Security Guard & Non-Admin Enforcement (403 Forbidden)
    # =========================================================================
    r.section("7. Security Guard & Non-Admin Enforcement (403 Forbidden)")

    # 7.1 Login as non-admin student
    student_user, student_pass = SEED_USERS["student"]
    student_login = r.step("POST", "/login", title=f"Login as regular student ({student_user})",
                           json_body={"username": student_user, "password": student_pass})
    student_token = (student_login.body or {}).get("access_token", "")

    if student_token and user_id and role_id:
        # 7.2 Non-admin attempts to assign role -> 403
        r.step("POST", f"/users/{user_id}/roles",
               title="Negative: Student attempts to assign role to user (expect 403)",
               token=student_token, json_body={"roleId": role_id}, expected=(403,),
               note="Expected 403 ACCESS_DENIED — non-admins cannot assign roles")

        # 7.3 Non-admin attempts to unassign role -> 403
        r.step("DELETE", f"/users/{user_id}/roles/{role_id}",
               title="Negative: Student attempts to unassign role from user (expect 403)",
               token=student_token, expected=(403,),
               note="Expected 403 ACCESS_DENIED — non-admins cannot unassign roles")

        # 7.4 Non-admin attempts to create a role in catalog -> 403
        r.step("POST", "/roles",
               title="Negative: Student attempts to create a role (expect 403)",
               token=student_token, json_body={"name": "forbidden-role"}, expected=(403,),
               note="Expected 403 ACCESS_DENIED — only admins can create roles")

        # 7.5 Non-admin attempts to create a user -> 403
        r.step("POST", "/users",
               title="Negative: Student attempts to create a new user (expect 403)",
               token=student_token,
               json_body={"email": "hacked@pucrs.br", "firstName": "H", "lastName": "K", "password": "pass"},
               expected=(403,),
               note="Expected 403 ACCESS_DENIED — only admins can create users")

        # 7.6 Non-admin attempts to delete a user -> 403
        r.step("DELETE", f"/users/{user_id}",
               title="Negative: Student attempts to delete another user (expect 403)",
               token=student_token, expected=(403,),
               note="Expected 403 ACCESS_DENIED — only admins can delete users")

        # 7.7 Non-admin attempts to update another user's profile -> 403
        r.step("PUT", f"/users/{user_id}",
               title="Negative: Student attempts to update another user (expect 403)",
               token=student_token, json_body={"firstName": "HackedName"}, expected=(403,),
               note="Expected 403 ACCESS_DENIED — non-admins cannot modify others")

        # 7.8 Non-admin attempts to change another user's password -> 403
        r.step("PATCH", f"/users/{user_id}",
               title="Negative: Student attempts to change another user's password (expect 403)",
               token=student_token, json_body={"password": "hackedPassword!"}, expected=(403,),
               note="Expected 403 ACCESS_DENIED — non-admins cannot change others' password")

        # 7.9 Non-admin student requests 'rooms' in RBAC matrix -> 403
        r.step("POST", "/authorize",
               title="Negative: Student requests 'rooms' resource (expect 403)",
               token=student_token, json_body={"resource": "rooms"}, expected=(403,),
               note="Expected 403 ACCESS_DENIED — student does not possess administrator role")

    # =========================================================================
    # Section 8: Session Renewal & Token Expiry
    # =========================================================================
    r.section("8. Session Renewal & Refresh Token Errors")

    # 8.1 Negative: Refresh with invalid refresh token -> 401 INVALID_TOKEN
    r.step("POST", "/refresh", title="Negative: Refresh with invalid token (expect 401)",
           json_body={"refresh_token": "bogus-refresh-token"}, expected=(401,),
           note="Expected 401 INVALID_TOKEN — security validation")

    # 8.2 Positive: Refresh with valid refresh token -> 200
    refreshed = r.step("POST", "/refresh", title="Renew the session using valid refresh token",
                        json_body={"refresh_token": refresh_token})
    if refreshed.status == 200 and refreshed.body:
        access_token = refreshed.body["access_token"]

    # =========================================================================
    # Section 9: Cleanup: Unassign & Soft-Delete (as Admin)
    # =========================================================================
    r.section("9. Cleanup: Unassign Roles & Soft-Delete Entities")
    if user_id and role_id:
        r.step("DELETE", f"/users/{user_id}/roles/{role_id}", title="Unassign the demo role from the demo user",
               token=access_token, expected=(204,))
    if role_id:
        r.step("DELETE", f"/roles/{role_id}", title="Soft-delete the demo role", token=access_token,
               expected=(204,))
    if user_id:
        r.step("DELETE", f"/users/{user_id}", title="Soft-delete the demo user (enabled=false)",
               token=access_token, expected=(204,))

    # =========================================================================
    # Section 10: Observability & Documentation
    # =========================================================================
    r.section("10. Observability & API Documentation")
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
