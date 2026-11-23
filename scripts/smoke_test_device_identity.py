#!/usr/bin/env python3
"""Smoke-test the device-credential sign-up flow against a Supabase project.

Signs up a throwaway device user, signs in again as a second device, refreshes
both sessions in turn, checks the profile, the hook's rejections and
delete_my_data, and leaves no user behind.

    python3 scripts/smoke_test_device_identity.py <api-url> <publishable-key>
"""
import json
import secrets
import sys
import urllib.error
import urllib.request
import uuid

API, KEY = sys.argv[1].rstrip("/"), sys.argv[2]


def call(method, path, body=None, token=None):
    request = urllib.request.Request(
        API + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    request.add_header("apikey", KEY)
    request.add_header("Content-Type", "application/json")
    request.add_header("Authorization", f"Bearer {token or KEY}")
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None


def check(label, ok, status):
    print(f"{'PASS' if ok else 'FAIL'}  {label} (HTTP {status})")
    if not ok:
        sys.exit(1)


def device_credential():
    return {"email": f"{uuid.uuid4()}@device.voyage.invalid", "password": secrets.token_hex(32)}


def sign_in(credential):
    return call("POST", "/auth/v1/token?grant_type=password", credential)


def refresh(session):
    return call("POST", "/auth/v1/token?grant_type=refresh_token", {"refresh_token": session["refresh_token"]})


credential = device_credential()
status, first = call("POST", "/auth/v1/signup", credential)
check("first device signs up and gets a session", status == 200 and "access_token" in first, status)
user_id = first["user"]["id"]
second = None

try:
    status, second = sign_in(credential)
    check("second device signs in as the same user", status == 200 and second["user"]["id"] == user_id, status)

    for round_ in (1, 2, 3):
        for name, session in (("first", first), ("second", second)):
            status, fresh = refresh(session)
            check(f"round {round_}: {name} device refreshes", status == 200, status)
            session.update(fresh)

    status, rows = call("GET", "/rest/v1/profiles?select=id", token=first["access_token"])
    check("the user sees only their profile", status == 200 and rows == [{"id": user_id}], status)

    status, _ = call("POST", "/auth/v1/signup", {"email": "someone@example.com", "password": secrets.token_hex(32)})
    check("a real email is refused", status == 403, status)

    status, _ = call("POST", "/auth/v1/signup", {**device_credential(), "password": "too-short"})
    check("a short password is refused", status == 422, status)

    status, _ = call("POST", "/rest/v1/rpc/delete_my_data", {})
    check("delete_my_data needs a signed-in user", status == 401, status)
finally:
    status, _ = call("POST", "/rest/v1/rpc/delete_my_data", {}, token=first["access_token"])
    check("delete_my_data removes the user", status == 204, status)

if second:
    status, _ = refresh(second)
    check("the other device's session ends", status == 400, status)

status, _ = sign_in(credential)
check("the credential no longer signs in", status == 400, status)
