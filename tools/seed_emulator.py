"""Seeds the Firebase emulators with one user and a little data, for trying the Android app.

Auth emulator on 9099, Firestore emulator on 8080, project demo-expensify (the
web repo's `npm run emulators`). The Firestore REST calls carry
`Authorization: Bearer owner`, which the emulator treats as an admin, so the
security rules don't apply here; the shapes still follow requirements.md §8.
"""
import json
import sys
import urllib.error
import urllib.request

AUTH = "http://localhost:9099/identitytoolkit.googleapis.com/v1"
FS = "http://localhost:8080/v1/projects/demo-expensify/databases/(default)/documents"
EMAIL, PASSWORD, USERNAME, NAME = "sita@example.com", "password123", "sita.r", "Sita Rai"
NOW = "2026-10-13T08:00:00Z"


def call(method, url, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            return json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as e:
        print(method, url, e.code, e.read().decode()[:300])
        raise


def s(v): return {"stringValue": v}
def d(v): return {"doubleValue": v}
def i(v): return {"integerValue": str(v)}
def b(v): return {"booleanValue": v}
def null(): return {"nullValue": None}
def ts(v=NOW): return {"timestampValue": v}
def arr(*vals): return {"arrayValue": {"values": list(vals)}}
def m(fields): return {"mapValue": {"fields": fields}}


def put(path, fields):
    call("PATCH", f"{FS}/{path}", {"fields": fields}, {"Authorization": "Bearer owner"})


def main():
    # 1. The login. If it exists already, just sign in to learn the uid.
    try:
        user = call("POST", f"{AUTH}/accounts:signUp?key=any", {"email": EMAIL, "password": PASSWORD, "returnSecureToken": True})
    except urllib.error.HTTPError:
        user = call("POST", f"{AUTH}/accounts:signInWithPassword?key=any", {"email": EMAIL, "password": PASSWORD, "returnSecureToken": True})
    uid, token = user["localId"], user["idToken"]
    call("POST", f"{AUTH}/accounts:update?key=any", {"idToken": token, "displayName": NAME, "returnSecureToken": True})

    # 2. Profile and username lookup.
    put(f"users/{uid}", {
        "name": s(NAME), "username": s(USERNAME), "email": s(EMAIL), "pendingEmail": null(), "photoUrl": null(),
        "preferences": m({"theme": s("system"), "notifications": m({
            "dailyReminder": b(False), "reminderTime": s("20:00"), "budgetAlerts": b(True), "billReminders": b(True)})}),
        "createdAt": ts(), "updatedAt": ts(),
    })
    put(f"usernames/{USERNAME}", {"uid": s(uid), "email": s(EMAIL), "pendingEmail": null()})

    # 3. A few categories (Appendix A).
    cats = [("food", "Food and dining", "expense", "category", "#EA580C", 0), ("transport", "Transport", "expense", "train", "#2456E6", 1),
            ("groceries", "Groceries", "expense", "cart", "#15803D", 2), ("salary", "Salary", "income", "money", "#15803D", 0)]
    for cid, name, typ, icon, color, order in cats:
        put(f"users/{uid}/categories/{cid}", {"name": s(name), "type": s(typ), "icon": s(icon), "color": s(color),
                                              "archived": b(False), "sortOrder": i(order), "createdAt": ts(), "updatedAt": ts()})

    # 4. Accounts: a bank, cash, a credit card with a limit, an archived wallet.
    accounts = [
        ("bank", "Everyday bank", "bank", 50000.0, 84250.75, None, True, False, 0, "bank", "#2456E6"),
        ("cash", "Wallet cash", "cash", 5000.0, 3450.0, None, True, False, 1, "money", "#36B37E"),
        ("visa", "Visa card", "credit_card", 0.0, -42000.0, 50000.0, True, False, 2, "card", "#AB20A9"),
        ("esewa", "Old eSewa", "wallet", 0.0, 0.0, None, True, True, 3, "wallet", "#00B8D9"),
    ]
    for aid, name, typ, opening, current, limit, inc, archived, order, icon, color in accounts:
        put(f"users/{uid}/accounts/{aid}", {
            "name": s(name), "type": s(typ), "currency": s("NPR"), "openingBalance": d(opening), "currentBalance": d(current),
            "creditLimit": d(limit) if limit else null(), "icon": s(icon), "color": s(color), "includeInTotal": b(inc),
            "archived": b(archived), "sortOrder": i(order), "createdAt": ts(), "updatedAt": ts()})

    # 5. Entries touching those accounts (balances above already include them).
    txs = [
        ("t1", "expense", 450.0, "cash", None, "food", "2026-10-13", "Lunch at Thamel", "2026-10-13T06:30:00Z"),
        ("t2", "expense", 1100.0, "cash", None, "transport", "2026-10-13", None, "2026-10-13T05:10:00Z"),
        ("t3", "income", 85000.0, "bank", None, "salary", "2026-10-01", "October salary", "2026-10-01T03:00:00Z"),
        ("t4", "transfer", 5000.0, "bank", "cash", None, "2026-10-12", "Cash for the week", "2026-10-12T09:00:00Z"),
        ("t5", "expense", 3200.0, "visa", None, "groceries", "2026-10-15", "Bhatbhateni", "2026-10-12T10:00:00Z"),
        ("t6", "expense", 749.25, "bank", None, "deleted-cat", "2026-10-11", None, "2026-10-11T10:00:00Z"),
    ]
    for tid, typ, amount, acc, to, cat, date, note, created in txs:
        put(f"users/{uid}/transactions/{tid}", {
            "type": s(typ), "amount": d(amount), "currency": s("NPR"), "accountId": s(acc),
            "toAccountId": s(to) if to else null(), "categoryId": s(cat) if cat else null(), "date": s(date),
            "note": s(note) if note else null(), "recurringRuleId": null(),
            "accountIds": arr(s(acc), s(to)) if to else arr(s(acc)),
            "createdAt": ts(created), "updatedAt": ts(created)})

    print(f"seeded uid={uid} login={USERNAME} / {EMAIL} password={PASSWORD}")


if __name__ == "__main__":
    sys.exit(main())
