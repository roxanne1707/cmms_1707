const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require(process.env.TYPESCRIPT_MODULE ||
  "../frontend/node_modules/typescript");
const root = path.resolve(__dirname, "..");

function load(file, requireFn, globals = {}) {
  const source = fs.readFileSync(path.join(root, file), "utf8");
  const result = ts.transpileModule(source, {
    reportDiagnostics: true,
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
    },
  });
  assert.equal(
    (result.diagnostics || []).filter(
      (d) => d.category === ts.DiagnosticCategory.Error
    ).length,
    0
  );
  const module = { exports: {} };
  vm.runInNewContext(
    result.outputText,
    {
      module,
      exports: module.exports,
      require: requireFn,
      Headers,
      Date,
      Math,
      Map,
      Error,
      Promise,
      globalThis,
      ...globals,
    },
    { filename: file }
  );
  return module.exports;
}

function deferred() {
  let resolve, reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function authToken(user, revision = "initial") {
  return `${revision}.${Buffer.from(JSON.stringify({ sub: user })).toString(
    "base64url"
  )}.signature`;
}

function sessionFixture(handler) {
  const storage = new Map([
    ["accessToken", authToken("admin-A")],
    ["refreshToken", "refresh-A"],
  ]);
  const requests = [];
  const helper = load("frontend/src/utils/idempotentCommand.ts", () => {});
  const client = load(
    "frontend/src/utils/api.ts",
    (name) => {
      if (name === "../config") return { apiUrl: "http://127.0.0.1:28089/" };
      if (name === "./idempotentCommand") return helper;
      throw Error(name);
    },
    {
      atob: (value) => Buffer.from(value, "base64").toString(),
      localStorage: {
        getItem: (key) => storage.get(key) ?? null,
        setItem: (key, value) => storage.set(key, value),
        removeItem: (key) => storage.delete(key),
      },
      fetch: async (url, options) => {
        const request = { url, options, headers: new Headers(options.headers) };
        requests.push(request);
        return handler(request, storage, requests);
      },
    }
  );
  return { client, storage, requests };
}

const ok = (value) => ({ ok: true, json: async () => value });
const unauthorized = () => ({ ok: false, status: 401, json: async () => ({}) });
const changedSession = (error) => error.status === 401;

async function sessionRegressionTests() {
  let cases = 0;
  async function check(name, run) {
    await run();
    cases++;
    console.log(`PASS session: ${name}`);
  }

  await check(
    "parallel 401 requests share one refresh and preserve custom headers",
    async () => {
      const refresh = deferred(),
        started = deferred();
      let refreshCalls = 0;
      const f = sessionFixture((r) => {
        if (r.url.endsWith("/auth/refresh")) {
          refreshCalls++;
          started.resolve();
          return refresh.promise;
        }
        return r.headers.get("Authorization") ===
          `Bearer ${authToken("admin-A")}`
          ? unauthorized()
          : ok({ success: true });
      });
      const one = f.client.default.post(
        "reliable-events/event-1/replay",
        { reason: "parallel one" },
        { headers: { "X-Request-Trace": "trace-one" } }
      );
      const two = f.client.default.post("reliable-events/event-2/replay", {
        reason: "parallel two",
      });
      await started.promise;
      refresh.resolve(
        ok({
          accessToken: authToken("admin-A", "rotated"),
          refreshToken: "refresh-A-2",
        })
      );
      await Promise.all([one, two]);
      assert.equal(refreshCalls, 1);
      assert.equal(
        f.requests.filter((r) => r.url.endsWith("/replay")).length,
        4
      );
      const retried = f.requests
        .filter((r) => r.url.endsWith("/event-1/replay"))
        .at(-1);
      assert.equal(retried.headers.get("X-Request-Trace"), "trace-one");
      assert.equal(
        retried.headers.get("Authorization"),
        `Bearer ${authToken("admin-A", "rotated")}`
      );
    }
  );

  await check(
    "late 401 reuses the same-session token already refreshed by another request",
    async () => {
      const late = deferred();
      let refreshCalls = 0;
      let lateCalls = 0;
      const f = sessionFixture((r) => {
        if (r.url.endsWith("/auth/refresh")) {
          refreshCalls++;
          return ok({
            accessToken: authToken("admin-A", "rotated"),
            refreshToken: "refresh-A-2",
          });
        }
        if (r.url.endsWith("/late") && ++lateCalls === 1) return late.promise;
        return r.headers.get("Authorization") ===
          `Bearer ${authToken("admin-A")}`
          ? unauthorized()
          : ok({ success: true });
      });
      const pending = f.client.default.get("late");
      await f.client.default.get("early");
      late.resolve(unauthorized());
      await pending;
      assert.equal(refreshCalls, 1);
      assert.equal(lateCalls, 2);
    }
  );

  await check(
    "account switch before 401 blocks refresh and recovery replay",
    async () => {
      const response = deferred();
      const f = sessionFixture(() => response.promise);
      const result = f.client.default.post("reliable-events/event/replay", {
        reason: "switch check",
      });
      f.storage.set("accessToken", authToken("admin-B"));
      f.storage.set("refreshToken", "refresh-B");
      response.resolve(unauthorized());
      await assert.rejects(result, changedSession);
      assert.equal(f.requests.length, 1);
      assert.equal(f.storage.get("accessToken"), authToken("admin-B"));
    }
  );

  await check(
    "late refresh success cannot overwrite switched account or repeat its recovery",
    async () => {
      const refresh = deferred(),
        started = deferred();
      const f = sessionFixture((r) =>
        r.url.endsWith("/auth/refresh")
          ? (started.resolve(), refresh.promise)
          : unauthorized()
      );
      const result = f.client.default.post("reliable-events/event/replay", {
        reason: "switch while refreshing",
      });
      await started.promise;
      f.storage.set("accessToken", authToken("admin-B"));
      f.storage.set("refreshToken", "refresh-B");
      refresh.resolve(
        ok({
          accessToken: authToken("admin-A", "rotated"),
          refreshToken: "refresh-A-2",
        })
      );
      await assert.rejects(result, changedSession);
      assert.equal(f.storage.get("accessToken"), authToken("admin-B"));
      assert.equal(f.storage.get("refreshToken"), "refresh-B");
      assert.equal(
        f.requests.filter((r) => r.url.endsWith("/replay")).length,
        1
      );
    }
  );

  await check(
    "old refresh failure cannot clear new-account tokens or its in-flight refresh",
    async () => {
      const a = deferred(),
        b = deferred();
      let calls = 0;
      const f = sessionFixture(() => (++calls === 1 ? a.promise : b.promise));
      const old = f.client.refreshAccessToken();
      f.storage.set("accessToken", authToken("admin-B"));
      f.storage.set("refreshToken", "refresh-B");
      const fresh = f.client.refreshAccessToken();
      a.reject(new TypeError("connection lost"));
      assert.equal(await old, false);
      assert.equal(f.storage.get("accessToken"), authToken("admin-B"));
      const joined = f.client.refreshAccessToken();
      assert.equal(joined, fresh);
      assert.equal(calls, 2);
      b.resolve(
        ok({
          accessToken: authToken("admin-B", "rotated"),
          refreshToken: "refresh-B-2",
        })
      );
      assert.equal(await fresh, true);
      assert.equal(await joined, true);
      assert.equal(f.storage.get("refreshToken"), "refresh-B-2");
    }
  );

  await check(
    "logout before delayed 401 prevents all automatic retry",
    async () => {
      const response = deferred();
      const f = sessionFixture(() => response.promise);
      const result = f.client.default.post("reliable-events/event/replay", {
        reason: "logout check",
      });
      f.storage.delete("accessToken");
      f.storage.delete("refreshToken");
      response.resolve(unauthorized());
      await assert.rejects(result, changedSession);
      assert.equal(f.requests.length, 1);
      assert.equal(f.storage.size, 0);
    }
  );

  await check(
    "logout during refresh cannot be undone by its late success",
    async () => {
      const refresh = deferred();
      const f = sessionFixture(() => refresh.promise);
      const result = f.client.refreshAccessToken();
      f.storage.delete("accessToken");
      f.storage.delete("refreshToken");
      refresh.resolve(
        ok({
          accessToken: authToken("admin-A", "rotated"),
          refreshToken: "refresh-A-2",
        })
      );
      assert.equal(await result, false);
      assert.equal(f.storage.size, 0);
    }
  );

  await check(
    "same user logging in again is a new session, not a token refresh",
    async () => {
      const response = deferred();
      const f = sessionFixture(() => response.promise);
      const result = f.client.default.post("reliable-events/event/replay", {
        reason: "new login check",
      });
      f.storage.set("accessToken", authToken("admin-A", "new-login"));
      f.storage.set("refreshToken", "new-login-refresh");
      response.resolve(unauthorized());
      await assert.rejects(result, changedSession);
      assert.equal(f.requests.length, 1);
      assert.equal(f.storage.get("refreshToken"), "new-login-refresh");
    }
  );

  await check(
    "aborted request does not replay after shared refresh",
    async () => {
      const refresh = deferred(),
        started = deferred(),
        controller = new AbortController();
      const f = sessionFixture((r) =>
        r.url.endsWith("/auth/refresh")
          ? (started.resolve(), refresh.promise)
          : unauthorized()
      );
      const result = f.client.default.get("query", {
        signal: controller.signal,
      });
      await started.promise;
      controller.abort();
      refresh.resolve(
        ok({
          accessToken: authToken("admin-A", "rotated"),
          refreshToken: "refresh-A-2",
        })
      );
      await assert.rejects(result, (e) => e.name === "AbortError");
      assert.equal(
        f.requests.filter((r) => r.url.endsWith("/query")).length,
        1
      );
      assert.equal(
        f.storage.get("accessToken"),
        authToken("admin-A", "rotated")
      );
    }
  );

  await check(
    "refresh failure clears only the unchanged current session",
    async () => {
      const f = sessionFixture(() => unauthorized());
      assert.equal(await f.client.refreshAccessToken(), false);
      assert.equal(f.storage.size, 0);
    }
  );

  await check(
    "old-account successful response cannot leak into new-account consumers",
    async () => {
      const response = deferred();
      const f = sessionFixture(() => response.promise);
      const result = f.client.default.get("reliable-events/failures");
      f.storage.set("accessToken", authToken("admin-B"));
      f.storage.set("refreshToken", "refresh-B");
      response.resolve(ok({ events: [{ id: "private-A" }], deliveries: [] }));
      await assert.rejects(result, changedSession);
      assert.equal(f.requests.length, 1);
    }
  );

  await check(
    "account switch during refresh JSON parsing cannot overwrite new tokens",
    async () => {
      const body = deferred(),
        started = deferred();
      const f = sessionFixture(() => ({
        ok: true,
        json: () => {
          started.resolve();
          return body.promise;
        },
      }));
      const result = f.client.refreshAccessToken();
      await started.promise;
      f.storage.set("accessToken", authToken("admin-B"));
      f.storage.set("refreshToken", "refresh-B");
      body.resolve({
        accessToken: authToken("admin-A", "rotated"),
        refreshToken: "refresh-A-2",
      });
      assert.equal(await result, false);
      assert.equal(f.storage.get("accessToken"), authToken("admin-B"));
    }
  );
  for (const failure of ["network", "5xx"]) {
    await check(
      `first command after account switch retains its key after ${failure}`,
      async () => {
        let readingCalls = 0;
        const f = sessionFixture((request) => {
          if (!request.url.endsWith("/readings")) return ok({ success: true });
          readingCalls++;
          if (readingCalls === 1) {
            if (failure === "network")
              throw new TypeError("uncertain network outcome");
            return {
              ok: false,
              status: 503,
              json: async () => ({ message: "uncertain service outcome" }),
            };
          }
          return ok({ id: 42 });
        });
        // Establish the old session before external login/account-switch token writes.
        await f.client.default.get("session-probe");
        f.storage.set("accessToken", authToken("admin-B"));
        f.storage.set("refreshToken", "refresh-B");
        const body = { meter: { id: 152 }, value: 99 };
        await assert.rejects(f.client.default.post("readings", body));
        assert.equal((await f.client.default.post("readings", body)).id, 42);
        const commands = f.requests.filter((request) =>
          request.url.endsWith("/readings")
        );
        assert.equal(commands.length, 2);
        assert.ok(commands[0].headers.get("Idempotency-Key"));
        assert.equal(
          commands[0].headers.get("Idempotency-Key"),
          commands[1].headers.get("Idempotency-Key")
        );
        assert.equal(
          commands[1].headers.get("Authorization"),
          `Bearer ${authToken("admin-B")}`
        );
      }
    );
  }
  return cases;
}

async function main() {
  let cases = 0;
  for (const base of ["frontend/src/utils", "mobile/utils"]) {
    const helper = load(`${base}/idempotentCommand.ts`, () => {
      throw Error("Unexpected dependency");
    });
    const keys = [];
    await assert.rejects(
      helper.runIdempotentCommand("reading:1", async (key) => {
        keys.push(key);
        throw Error("connection reset after commit");
      })
    );
    await helper.runIdempotentCommand("reading:1", async (key) => {
      keys.push(key);
      return 1;
    });
    assert.equal(keys[0], keys[1]);
    cases++;
    await helper.runIdempotentCommand("reading:1", async (key) => {
      keys.push(key);
      return 2;
    });
    assert.notEqual(keys[1], keys[2]);
    cases++;
    await assert.rejects(
      helper.runIdempotentCommand("stock:2", async (key) => {
        keys.push(key);
        throw Object.assign(Error("conflict"), { status: 409 });
      })
    );
    await helper.runIdempotentCommand("stock:2", async (key) => {
      keys.push(key);
      return 1;
    });
    assert.notEqual(keys.at(-1), keys.at(-2));
    cases++;
    assert.equal(
      helper.isMaintenanceCommand("http://localhost/readings", "POST"),
      true
    );
    assert.equal(
      helper.isMaintenanceCommand(
        "http://localhost/readings/meter/1/histogram",
        "POST"
      ),
      false
    );
    assert.equal(
      helper.isMaintenanceCommand(
        "http://localhost/purchase-orders/2/respond?approved=true",
        "PATCH"
      ),
      true
    );
    cases++;
    assert.equal(
      fs.readFileSync(path.join(root, base, "idempotentCommand.ts"), "utf8"),
      fs.readFileSync(
        path.join(root, "frontend/src/utils/idempotentCommand.ts"),
        "utf8"
      )
    );
  }
  const storage = new Map();
  const payload = Buffer.from(JSON.stringify({ sub: "local-user" })).toString(
    "base64url"
  );
  storage.set("accessToken", `old.${payload}.signature`);
  storage.set("refreshToken", "test-refresh");
  const requests = [];
  const helper = load("frontend/src/utils/idempotentCommand.ts", () => {});
  let businessCalls = 0;
  const api = load(
    "frontend/src/utils/api.ts",
    (name) => {
      if (name === "../config") return { apiUrl: "http://127.0.0.1:28089/" };
      if (name === "./idempotentCommand") return helper;
      throw Error(name);
    },
    {
      atob: (data) => Buffer.from(data, "base64").toString(),
      localStorage: {
        getItem: (key) => storage.get(key),
        setItem: (key, value) => storage.set(key, value),
        removeItem: (key) => storage.delete(key),
      },
      fetch: async (url, options) => {
        requests.push({ url, headers: new Headers(options.headers) });
        if (url.endsWith("/auth/refresh"))
          return {
            ok: true,
            json: async () => ({
              accessToken: `new.${payload}.signature`,
              refreshToken: "new-refresh",
            }),
          };
        businessCalls++;
        return businessCalls === 1
          ? { ok: false, status: 401, json: async () => ({}) }
          : { ok: true, json: async () => ({ id: 7 }) };
      },
    }
  );
  assert.equal(
    (await api.default.post("readings", { value: 12, meter: { id: 1 } })).id,
    7
  );
  const commands = requests.filter((r) => r.url.endsWith("/readings"));
  assert.equal(commands.length, 2);
  assert.equal(
    commands[0].headers.get("Idempotency-Key"),
    commands[1].headers.get("Idempotency-Key")
  );
  assert.ok(commands[1].headers.get("Authorization").startsWith("Bearer new."));
  assert.equal(commands[1].headers.get("Content-Type"), "application/json");
  cases++;
  let globalConflicts = 0;
  const conflicts = load(
    "frontend/src/utils/api.ts",
    (name) => {
      if (name === "../config") return { apiUrl: "http://127.0.0.1:28089/" };
      if (name === "./idempotentCommand") return helper;
      throw Error(name);
    },
    {
      localStorage: {
        getItem: (key) => storage.get(key),
        setItem: (key, value) => storage.set(key, value),
        removeItem: (key) => storage.delete(key),
      },
      fetch: async () => ({
        ok: false,
        status: 409,
        json: async () => ({ message: "already replayed" }),
      }),
    }
  );
  conflicts.setConflictErrorHandler(() => globalConflicts++);
  await assert.rejects(
    conflicts.default.post(
      "reliable-events/test/replay",
      { reason: "local test" },
      { suppressConflictNotification: true }
    ),
    (error) => error.status === 409
  );
  assert.equal(
    globalConflicts,
    0,
    "Recovery page owns its non-repeat conflict guidance"
  );
  cases++;
  await assert.rejects(
    conflicts.default.post("unrelated-action", {}),
    (error) => error.status === 409
  );
  assert.equal(
    globalConflicts,
    1,
    "Existing global conflict behavior remains for other pages"
  );
  cases++;
  cases += await sessionRegressionTests();
  console.log(
    `PASS ${cases} client cases (idempotency, authenticated refresh and recovery conflict guidance)`
  );
}
main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
